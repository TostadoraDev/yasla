package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LocalizedText
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.TimingQuality

class LyricsDocumentExporterTest {
    private val exporter = LyricsDocumentExporter()

    @Test
    fun plainLrcPreservesUntimedTextAndReportsTimingLoss() {
        val document = document(
            lines = listOf(
                line(id = "one", order = 0, text = "First"),
                line(id = "two", order = 1, text = "Second"),
            ),
            syncType = SyncType.PLAIN,
        )

        val result = exporter.export(document, LyricsExportFormat.LRC)

        assertEquals("First\nSecond", result.content)
        assertFalse(result.isLossless)
        assertEquals(2, result.diagnostics.count { it.code == "LRC_LINE_TIME_MISSING" })
    }

    @Test
    fun enhancedLrcWritesWordMarkersWhenWordsReconstructLine() {
        val document = document(
            lines = listOf(
                line(
                    id = "one",
                    order = 0,
                    text = "Hello world",
                    start = 1_000L,
                    words = listOf(
                        word("w1", "Hello ", 1_000L, 1_500L),
                        word("w2", "world", 1_500L, null),
                    ),
                ),
            ),
            syncType = SyncType.WORD,
        )

        val result = exporter.export(document, LyricsExportFormat.ENHANCED_LRC)

        assertEquals("[00:01.000]<00:01.000>Hello <00:01.500>world", result.content)
        assertTrue(result.diagnostics.none { it.code == "LRC_WORD_TEXT_MISMATCH" })
    }

    @Test
    fun lrcReportsFeaturesThatCannotBeRepresented() {
        val line = line(
            id = "one",
            order = 0,
            text = LocalizedText(
                original = "Original",
                translations = mapOf("es" to "Traducción"),
                romanizations = mapOf("Latn" to "Original"),
            ),
            start = 1_000L,
            end = 1_750L,
            agent = "voice-a",
            group = "group-a",
        )
        val result = exporter.export(document(listOf(line), SyncType.LINE), LyricsExportFormat.LRC)

        val codes = result.diagnostics.map { it.code }.toSet()
        assertTrue("LRC_TRANSLATIONS_OMITTED" in codes)
        assertTrue("LRC_ROMANIZATIONS_OMITTED" in codes)
        assertTrue("LRC_AGENTS_OMITTED" in codes)
        assertTrue("LRC_GROUPS_OMITTED" in codes)
        assertTrue("LRC_LINE_ENDS_INFERRED" in codes)
    }

    @Test
    fun ttmlEscapesUntrustedTextAndRoundTripsRichContent() {
        val richLine = line(
            id = "one",
            order = 0,
            text = LocalizedText(
                original = "A < B & C",
                translations = mapOf("es" to "A < B y C"),
                romanizations = mapOf("en-Latn" to "A less B"),
            ),
            start = 1_000L,
            end = 2_000L,
            agent = "v\"<&",
            group = "g<&",
        )
        val original = document(listOf(richLine), SyncType.LINE)
        val exported = exporter.export(original, LyricsExportFormat.TTML)

        assertTrue(exported.content.contains("A &lt; B &amp; C"))
        assertTrue(exported.content.contains("ttm:agent=\"v&quot;&lt;&amp;\""))
        val parsed = TtmlLyricsParser().parse(
            exported.content,
            LyricsParseContext(
                documentId = "roundtrip",
                trackKey = "track",
                source = LyricsSource("roundtrip"),
            ),
        )

        assertTrue(parsed.isSuccess)
        val line = requireNotNull(parsed.document).lines.single()
        assertEquals("A < B & C", line.text.original)
        assertEquals("A < B y C", line.text.translations["es"])
        assertEquals("A less B", line.text.romanizations["en-Latn"])
        assertEquals("v\"<&", line.agentId)
        assertEquals("L1", line.groupId)
    }

    @Test
    fun ttmlKeepsExplicitBreaksButDiscardsFormattingWhitespace() {
        val original = document(
            lines = listOf(line("one", 0, "first\nsecond", start = 0L)),
            syncType = SyncType.LINE,
        )
        val exported = exporter.export(original, LyricsExportFormat.TTML)
        val parsed = TtmlLyricsParser().parse(
            exported.content,
            LyricsParseContext("parsed", "track", LyricsSource("test")),
        )

        assertEquals("first\nsecond", parsed.document?.lines?.single()?.text?.original)
    }

    @Test
    fun mismatchedWordModelFallsBackToCanonicalLineTextWithWarning() {
        val mismatched = line(
            id = "one",
            order = 0,
            text = "Canonical",
            start = 0L,
            words = listOf(word("word", "Different", 0L, 1_000L)),
        )

        val result = exporter.export(
            document(listOf(mismatched), SyncType.WORD),
            LyricsExportFormat.TTML,
        )

        assertTrue(result.content.contains(">Canonical</p>"))
        assertTrue(result.diagnostics.any { it.code == "TTML_WORD_TEXT_MISMATCH" })
    }

    private fun document(lines: List<LyricsLine>, syncType: SyncType) = LyricsDocument(
        id = "doc",
        trackKey = "track",
        source = LyricsSource(
            providerId = "local<&",
            remoteId = "remote\"id",
            attribution = "Artist & source",
            contentRights = "user supplied",
        ),
        syncType = syncType,
        lines = lines,
        languageTag = "en",
    )

    private fun line(
        id: String,
        order: Int,
        text: String,
        start: Long? = null,
        end: Long? = null,
        words: List<LyricsWord> = emptyList(),
        agent: String? = null,
        group: String? = null,
    ) = line(id, order, LocalizedText(text), start, end, words, agent, group)

    private fun line(
        id: String,
        order: Int,
        text: LocalizedText,
        start: Long? = null,
        end: Long? = null,
        words: List<LyricsWord> = emptyList(),
        agent: String? = null,
        group: String? = null,
    ) = LyricsLine(
        id = id,
        order = order,
        text = text,
        startTimeMs = start,
        endTimeMs = end,
        words = words,
        agentId = agent,
        groupId = group,
        timingQuality = if (start == null) TimingQuality.UNKNOWN else TimingQuality.EXPLICIT,
    )

    private fun word(id: String, text: String, start: Long, end: Long?) = LyricsWord(
        id = id,
        text = text,
        startTimeMs = start,
        endTimeMs = end,
        timingQuality = TimingQuality.EXPLICIT,
    )
}

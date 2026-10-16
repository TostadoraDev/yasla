package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LocalizedText
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWordSegment
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
        assertTrue(exported.content.contains("itunes:key=\"g&lt;&amp;\""))
        assertTrue(exported.content.contains("<ttm:agent type=\"person\" xml:id=\"v1\""))
        assertTrue(exported.content.contains("itunes:identifier=\"v&quot;&lt;&amp;\""))
        assertTrue(exported.content.contains("ttm:agent=\"v1\""))
        assertFalse(exported.content.contains("xml:id=\"v&quot;"))
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
        assertEquals("g<&", line.groupId)
    }

    @Test
    fun ttmlWritesAmllInlineVariantsAndGroupedBackgroundLine() {
        val foreground = line(
            id = "foreground",
            order = 0,
            text = LocalizedText(
                original = "Main line",
                translations = mapOf("es" to "Línea principal"),
                romanizations = mapOf("Latn" to "Main line"),
            ),
            start = 1_000L,
            end = 3_000L,
            words = listOf(
                word("main-1", "Main ", 1_000L, 1_500L),
                word("main-2", "line", 1_500L, 3_000L),
            ),
            agent = "lead",
            group = "chorus",
        )
        val background = line(
            id = "background",
            order = 1,
            text = LocalizedText(
                original = "Back",
                translations = mapOf("es" to "Fondo"),
            ),
            start = 1_250L,
            end = 2_500L,
            words = listOf(word("back-1", "Back", 1_250L, 2_500L)),
            agent = "backing",
            group = "chorus",
            isBackground = true,
        )

        val result = exporter.export(
            document(listOf(foreground, background), SyncType.WORD),
            LyricsExportFormat.TTML,
        )

        assertTrue(result.content.contains("itunes:timing=\"Word\""))
        assertTrue(result.content.contains("xml:lang=\"en\""))
        assertEquals(1, Regex("<p[\\s>]").findAll(result.content).count())
        assertTrue(result.content.contains("itunes:key=\"chorus\""))
        assertTrue(result.content.contains("ttm:role=\"x-translation\""))
        assertTrue(result.content.contains("ttm:role=\"x-roman\""))
        assertTrue(result.content.contains("ttm:role=\"x-bg\""))
        assertTrue(result.content.contains("begin=\"00:01.000\""))
        assertTrue(result.content.contains("begin=\"00:01.250\""))
        assertTrue(result.content.contains(">Main </span>"))
        assertTrue(result.diagnostics.none { it.code == "TTML_BACKGROUND_ORPHAN" })

        val agentIds = Regex("""<ttm:agent type="person" xml:id="([^"]+)"/>""")
            .findAll(result.content)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf("lead", "backing"), agentIds)
        assertEquals(agentIds.size, agentIds.distinct().size)
        assertTrue(agentIds.all { it.matches(Regex("""[A-Za-z_][A-Za-z0-9._-]*""")) })

        val parsed = TtmlLyricsParser().parse(
            result.content,
            LyricsParseContext("amll-roundtrip", "track", LyricsSource("roundtrip")),
        )
        assertTrue(parsed.isSuccess)
        val roundTrippedLines = requireNotNull(parsed.document).lines
        val roundTrippedForeground = roundTrippedLines.single { !it.isBackground }
        val roundTrippedBackground = roundTrippedLines.single(LyricsLine::isBackground)
        assertEquals("chorus", roundTrippedForeground.groupId)
        assertEquals("Main line", roundTrippedForeground.text.original)
        assertEquals("Línea principal", roundTrippedForeground.text.translations["es"])
        assertEquals("Main line", roundTrippedForeground.text.romanizations["Latn"])
        assertEquals(listOf("Main", "line"), roundTrippedForeground.words.map { it.text })
        assertEquals(listOf(1_000L, 1_500L), roundTrippedForeground.words.map { it.startTimeMs })
        assertEquals("lead", roundTrippedForeground.agentId)
        assertEquals("chorus", roundTrippedBackground.groupId)
        assertEquals("Back", roundTrippedBackground.text.original)
        assertEquals("Fondo", roundTrippedBackground.text.translations["es"])
        assertEquals(listOf("Back"), roundTrippedBackground.words.map { it.text })
        assertEquals(1_250L, roundTrippedBackground.startTimeMs)
        assertEquals(2_500L, roundTrippedBackground.endTimeMs)
        assertEquals("backing", roundTrippedBackground.agentId)
        assertTrue(roundTrippedBackground.isBackground)

    }

    @Test
    fun orphanBackgroundExportsAsOwnParagraphWithDiagnostic() {
        val orphan = line(
            id = "background",
            order = 0,
            text = "Echo",
            start = 0L,
            end = 500L,
            group = "missing",
            role = "harmony",
            isBackground = true,
        )

        val result = exporter.export(
            document(listOf(orphan), SyncType.LINE),
            LyricsExportFormat.TTML,
        )

        assertTrue(result.content.contains("<p begin=\"00:00.000\""))
        assertTrue(result.content.contains("ttm:role=\"x-bg harmony\""))
        assertTrue(result.diagnostics.any {
            it.code == "TTML_BACKGROUND_ORPHAN" && it.lineId == "background"
        })
        val parsed = TtmlLyricsParser().parse(
            result.content,
            LyricsParseContext("orphan", "track", LyricsSource("roundtrip")),
        )
        assertEquals("x-bg harmony", parsed.document?.lines?.single()?.role)
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

    @Test
    fun ttmlRoundTripPreservesAggregatedWordSegments() {
        val segmentedWord = LyricsWord(
            id = "word",
            text = "empiezas",
            startTimeMs = 743L,
            endTimeMs = 2_673L,
            timingQuality = TimingQuality.EXPLICIT,
            segments = listOf(
                LyricsWordSegment("em", 743L, 981L, TimingQuality.EXPLICIT),
                LyricsWordSegment("pie", 981L, 2_012L, TimingQuality.EXPLICIT),
                LyricsWordSegment("zas", 2_012L, 2_673L, TimingQuality.EXPLICIT),
            ),
        )
        val original = line(
            id = "segmented",
            order = 0,
            text = "empiezas",
            start = 743L,
            end = 2_673L,
            words = listOf(segmentedWord),
        )

        val exported = exporter.export(document(listOf(original), SyncType.WORD), LyricsExportFormat.TTML)
        val parsed = TtmlLyricsParser().parse(
            exported.content,
            LyricsParseContext("parsed", "track", LyricsSource("roundtrip")),
        )

        assertTrue(exported.content.contains("itunes:partOfWord=\"true\""))
        val word = requireNotNull(parsed.document).lines.single().words.single()
        assertEquals("empiezas", word.text)
        assertEquals(743L, word.startTimeMs)
        assertEquals(2_673L, word.endTimeMs)
        assertEquals(listOf("em", "pie", "zas"), word.segments.map { it.text })
    }

    @Test
    fun ttmlDoesNotInventAnAgentForSpeakerlessLyrics() {
        val result = exporter.export(
            document(listOf(line("one", 0, "Solo", start = 1_000L)), SyncType.LINE),
            LyricsExportFormat.TTML,
        )

        assertFalse(result.content.contains("<ttm:agent"))
        assertFalse(result.content.contains("ttm:agent="))
        val parsed = TtmlLyricsParser().parse(
            result.content,
            LyricsParseContext("speakerless", "track", LyricsSource("roundtrip")),
        )
        assertEquals(null, parsed.document?.lines?.single()?.agentId)
    }

    @Test
    fun ttmlRoundTripPreservesAgentsGroupsPresentationAndBackgroundPairing() {
        val first = line(
            id = "first",
            order = 0,
            text = "First",
            start = 1_000L,
            end = 2_000L,
            agent = "lead",
            group = "shared-duet",
            alignment = LyricsAlignment.LEFT,
            direction = LyricsTextDirection.LTR,
            role = "x-duet",
            region = "lower-third",
        )
        val firstBackground = line(
            id = "first-bg",
            order = 1,
            text = "First echo",
            start = 1_200L,
            end = 1_800L,
            agent = "backing",
            group = "shared-duet",
            alignment = LyricsAlignment.RIGHT,
            isBackground = true,
        )
        val second = line(
            id = "second",
            order = 2,
            text = "Second",
            start = 5_000L,
            end = 6_000L,
            agent = "lead",
            group = "shared-duet",
            alignment = LyricsAlignment.RIGHT,
            direction = LyricsTextDirection.RTL,
            region = "lower-third",
        )
        val secondBackground = line(
            id = "second-bg",
            order = 3,
            text = "Second echo",
            start = 5_200L,
            end = 5_800L,
            agent = "backing",
            group = "shared-duet",
            isBackground = true,
        )
        val result = exporter.export(
            document(listOf(first, firstBackground, second, secondBackground), SyncType.LINE),
            LyricsExportFormat.TTML,
        )
        val parsed = TtmlLyricsParser().parse(
            result.content,
            LyricsParseContext("structural", "track", LyricsSource("roundtrip")),
        )

        assertTrue(parsed.isSuccess)
        val lines = requireNotNull(parsed.document).lines
        assertEquals(
            listOf("First", "First echo", "Second", "Second echo"),
            lines.map { it.text.original })
        assertTrue(lines.all { it.groupId == "shared-duet" })
        assertEquals(listOf("lead", "backing", "lead", "backing"), lines.map { it.agentId })
        assertEquals(LyricsAlignment.LEFT, lines[0].alignment)
        assertEquals(LyricsTextDirection.LTR, lines[0].direction)
        assertEquals("x-duet", lines[0].role)
        assertEquals("lower-third", lines[0].region)
        assertEquals(LyricsAlignment.RIGHT, lines[2].alignment)
        assertEquals(LyricsTextDirection.RTL, lines[2].direction)
        assertEquals(listOf(false, true, false, true), lines.map { it.isBackground })
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
        alignment: LyricsAlignment = LyricsAlignment.AUTO,
        direction: LyricsTextDirection = LyricsTextDirection.AUTO,
        role: String? = null,
        region: String? = null,
        isBackground: Boolean = false,
    ) = line(
        id, order, LocalizedText(text), start, end, words, agent, group,
        alignment, direction, role, region, isBackground,
    )

    private fun line(
        id: String,
        order: Int,
        text: LocalizedText,
        start: Long? = null,
        end: Long? = null,
        words: List<LyricsWord> = emptyList(),
        agent: String? = null,
        group: String? = null,
        alignment: LyricsAlignment = LyricsAlignment.AUTO,
        direction: LyricsTextDirection = LyricsTextDirection.AUTO,
        role: String? = null,
        region: String? = null,
        isBackground: Boolean = false,
    ) = LyricsLine(
        id = id,
        order = order,
        text = text,
        startTimeMs = start,
        endTimeMs = end,
        words = words,
        agentId = agent,
        groupId = group,
        alignment = alignment,
        direction = direction,
        role = role,
        region = region,
        timingQuality = if (start == null) TimingQuality.UNKNOWN else TimingQuality.EXPLICIT,
        isBackground = isBackground,
    )

    private fun word(id: String, text: String, start: Long, end: Long?) = LyricsWord(
        id = id,
        text = text,
        startTimeMs = start,
        endTimeMs = end,
        timingQuality = TimingQuality.EXPLICIT,
    )
}

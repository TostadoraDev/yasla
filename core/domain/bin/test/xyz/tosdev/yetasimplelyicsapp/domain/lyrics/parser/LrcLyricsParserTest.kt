package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType

class LrcLyricsParserTest {
    private val parser = LrcLyricsParser()
    private val context = LyricsParseContext(
        documentId = "lrc-doc",
        trackKey = "artist|title",
        source = LyricsSource(providerId = "test"),
        languageTag = "en",
    )

    @Test
    fun multipleTimestampsAndGlobalOffsetProduceChronologicalLines() {
        val result = parser.parse(
            """
            [ar:Artist]
            [offset:+250]
            [00:01.00][00:02.500]Hello
            [00:04]World
            """.trimIndent(),
            context,
        )

        assertTrue(result.isSuccess)
        val document = requireNotNull(result.document)
        assertEquals(SyncType.LINE, document.syncType)
        assertEquals(listOf(1_250L, 2_750L, 4_250L), document.lines.map { it.startTimeMs })
        assertEquals(2_750L, document.lines[0].endTimeMs)
        assertEquals(listOf("Hello", "Hello", "World"), document.lines.map { it.text.original })
        assertTrue(LyricsCapability.LINE_SYNC in document.capabilities)
    }

    @Test
    fun enhancedLrcBuildsWordTimingAndShiftsRepeatedLineAliases() {
        val result = parser.parse(
            "[00:01.00][00:05.00]<00:01.00>Hello <00:01.50>world",
            context,
        )

        val document = requireNotNull(result.document)
        assertEquals(SyncType.WORD, document.syncType)
        assertEquals("Hello world", document.lines[0].text.original)
        assertEquals(listOf(1_000L, 1_500L), document.lines[0].words.map { it.startTimeMs })
        assertEquals(listOf(5_000L, 5_500L), document.lines[1].words.map { it.startTimeMs })
        assertEquals(5_000L, document.lines[0].endTimeMs)
        assertEquals(1_500L, document.lines[0].words[0].endTimeMs)
        assertTrue(LyricsCapability.WORD_SYNC in document.capabilities)
    }

    @Test
    fun negativeOffsetIsClampedAndReported() {
        val result = parser.parse(
            """
            [offset:-2000]
            [00:01.00]Too early
            """.trimIndent(),
            context,
        )

        assertEquals(0L, result.document?.lines?.single()?.startTimeMs)
        assertTrue(result.diagnostics.any { it.code == "LRC_NEGATIVE_TIME_CLAMPED" })
    }

    @Test
    fun plainLinesArePreservedWhileMalformedTagsAreDiagnosed() {
        val result = parser.parse(
            """
            A plain lyric
            [00:xx]broken
            """.trimIndent(),
            context,
        )

        assertNotNull(result.document)
        assertEquals(SyncType.PLAIN, result.document?.syncType)
        assertEquals("A plain lyric", result.document?.lines?.single()?.text?.original)
        assertTrue(result.diagnostics.any { it.code == "LRC_INVALID_TIMESTAMP" && it.line == 2 })
    }

    @Test
    fun emptyInputFailsWithoutThrowing() {
        val result = parser.parse("   ", context)

        assertFalse(result.isSuccess)
        assertNull(result.document)
        assertEquals("LRC_EMPTY", result.diagnostics.single().code)
    }

    @Test
    fun timestampParserAcceptsHoursAndFractionPrecisions() {
        assertEquals(3_723_400L, LrcLyricsParser.parseLrcTimestamp("1:02:03.4"))
        assertEquals(62_340L, LrcLyricsParser.parseLrcTimestamp("01:02.34"))
        assertEquals(62_345L, LrcLyricsParser.parseLrcTimestamp("01:02.3459"))
        assertNull(LrcLyricsParser.parseLrcTimestamp("01:75.00"))
    }
}

package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsModelsTest {
    @Test
    fun completeSemanticWordTimingIsReliable() {
        val document = document(
            line(
                id = "line-1",
                text = "empiezas ahora",
                words = listOf(
                    word("word-1", "empiezas", 0L, 800L),
                    word("word-2", "ahora", 800L, 1_400L),
                ),
            ),
        )

        assertTrue(document.hasReliableWordSync)
    }

    @Test
    fun lineTimingWithoutSemanticWordsIsNotReliable() {
        val document = document(line(id = "line-1", text = "Sólo una línea"))

        assertFalse(document.hasReliableWordSync)
    }

    @Test
    fun sparseWordTimingDoesNotPassCoverageThreshold() {
        val document = document(
            line(
                id = "line-1",
                text = "Primera línea",
                words = listOf(
                    word("word-1", "Primera", 0L, 400L),
                    word("word-2", "línea", 400L, 900L),
                ),
                start = 0L,
                end = 900L,
            ),
            line(id = "line-2", text = "Segunda línea", start = 900L, end = 1_800L),
        )

        assertFalse(document.hasReliableWordSync)
    }

    @Test
    fun incompleteWordReconstructionIsNotReliable() {
        val document = document(
            line(
                id = "line-1",
                text = "mientras cantas",
                words = listOf(word("word-1", "mientras", 0L, 700L)),
            ),
        )

        assertFalse(document.hasReliableWordSync)
    }

    private fun document(vararg lines: LyricsLine) = LyricsDocument(
        id = "doc",
        trackKey = "track",
        source = LyricsSource(providerId = "test"),
        syncType = SyncType.WORD,
        lines = lines.toList(),
        capabilities = setOf(LyricsCapability.LINE_SYNC, LyricsCapability.WORD_SYNC),
    )

    private fun line(
        id: String,
        text: String,
        words: List<LyricsWord> = emptyList(),
        start: Long = 0L,
        end: Long = 1_500L,
    ) = LyricsLine(
        id = id,
        order = id.substringAfterLast('-').toIntOrNull() ?: 0,
        text = LocalizedText(text),
        startTimeMs = start,
        endTimeMs = end,
        words = words,
        timingQuality = TimingQuality.EXPLICIT,
    )

    private fun word(id: String, text: String, start: Long, end: Long) = LyricsWord(
        id = id,
        text = text,
        startTimeMs = start,
        endTimeMs = end,
        timingQuality = TimingQuality.EXPLICIT,
        segments = listOf(
            LyricsWordSegment(
                text = text,
                startTimeMs = start,
                endTimeMs = end,
                timingQuality = TimingQuality.EXPLICIT,
            ),
        ),
    )
}

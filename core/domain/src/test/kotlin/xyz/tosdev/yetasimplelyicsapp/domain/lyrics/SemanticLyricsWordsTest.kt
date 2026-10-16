package xyz.tosdev.yetasimplelyicsapp.domain.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticLyricsWordsTest {
    @Test
    fun sixTimedSnapsBecomeOneSemanticWordWithoutLosingTiming() {
        val words = listOf("ex", "tra", "or", "di", "na", "rio").mapIndexed { index, text ->
            timedWord("snap-$index", text, index * 100L, (index + 1) * 100L)
        }

        val result = reconstructSemanticLyricsWords("extraordinario", words, "es")

        assertEquals(listOf("extraordinario"), result.map { it.text })
        assertEquals(listOf("ex", "tra", "or", "di", "na", "rio"), result.single().segments.map { it.text })
        assertEquals(0L, result.single().startTimeMs)
        assertEquals(600L, result.single().endTimeMs)
    }

    @Test
    fun realWhitespaceSeparatesWordsAndIsPreservedInTimedSegments() {
        val words = listOf(
            timedWord("snap-0", "te ", 0L, 200L),
            timedWord("snap-1", "quiero", 200L, 600L),
        )

        val result = reconstructSemanticLyricsWords("te quiero", words, "es")

        assertEquals(listOf("te", "quiero"), result.map { it.text })
        assertEquals("te ", result.first().segments.single().text)
    }

    @Test
    fun nonBreakingSpaceSeparatesWordsWithoutChangingSourceOffsets() {
        val words = listOf(
            timedWord("snap-0", "te\u00A0", 0L, 200L),
            timedWord("snap-1", "quiero", 200L, 600L),
        )

        val result = reconstructSemanticLyricsWords("te\u00A0quiero", words, "es")

        assertEquals(listOf("te", "quiero"), result.map { it.text })
        assertEquals("te\u00A0", result.first().segments.single().text)
    }

    @Test
    fun reconstructionIsIdempotentAndKeepsStableWordIds() {
        val source = listOf("cuan", "do").mapIndexed { index, text ->
            timedWord("snap-$index", text, index * 200L, (index + 1) * 200L)
        }

        val first = reconstructSemanticLyricsWords("cuando", source, "es")
        val second = reconstructSemanticLyricsWords("cuando", first, "es")

        assertEquals(first, second)
        assertEquals(listOf("snap-0"), second.map { it.id })
    }

    @Test
    fun punctuationAndUnicodeStayAttachedToTheSemanticWord() {
        val words = listOf("¡", "mí", "ra", "me! ", "dé", "jà").mapIndexed { index, text ->
            timedWord("snap-$index", text, index * 100L, (index + 1) * 100L)
        }

        val result = reconstructSemanticLyricsWords("¡mírame! déjà", words, "es")

        assertEquals(listOf("¡mírame!", "déjà"), result.map { it.text })
        assertEquals("me! ", result.first().segments.last().text)
    }

    @Test
    fun japaneseUsesLinguisticBoundariesInsteadOfOneWordPerTimedCharacter() {
        val words = listOf("私", "は", "学", "生").mapIndexed { index, text ->
            timedWord("snap-$index", text, index * 100L, (index + 1) * 100L)
        }

        val result = reconstructSemanticLyricsWords("私は学生", words, "ja")

        assertEquals("私は学生", result.joinToString(separator = "") { it.text })
        assertTrue(result.size < words.size)
    }

    private fun timedWord(id: String, text: String, start: Long, end: Long) = LyricsWord(
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

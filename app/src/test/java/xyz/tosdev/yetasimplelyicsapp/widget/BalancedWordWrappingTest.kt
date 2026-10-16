package xyz.tosdev.yetasimplelyicsapp.widget

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class BalancedWordWrappingTest {
    @Test
    fun quierePrefersThreeAndThreeEvenWhenFiveLettersFit() {
        val layout = WordLineBreaker.layoutWord(
            word = "quiere", languageTag = "es", fallbackLocale = Locale.ROOT,
            availableWidthDp = 50f, preferredFontSizeSp = 18f, minFontSizeSp = 18f,
            maxLines = 2, textMeasurer = WidgetTextMeasurer { text, _ -> text.length * 10f },
        )
        assertEquals("QUI\nERE", layout.text)
        assertFalse(layout.ellipsized)
    }

    @Test
    fun allRequestedWordsFitRealRectangleWithoutLosingLetters() {
        val words = listOf("QUIERE", "CUANDO", "MIENTRAS", "EXTRAORDINARIO",
            "ELECTROENCEFALOGRAFISTA", "THROUGH", "BEAUTIFUL", "UNDERSTANDING")
        val measurer = WidgetTextMeasurer { text, size -> text.length * size * 0.6f }
        for (word in words) for ((width, height) in listOf(48f to 64f, 140f to 100f, 280f to 160f)) {
            val layout = WordLineBreaker.layoutWord(word, "und", Locale.ROOT, width,
                preferredFontSizeSp = 32f, minFontSizeSp = 18f, maxLines = 8,
                textMeasurer = measurer, availableHeightDp = height)
            assertEquals("$word at $width x $height", word, layout.visualLines.joinToString(""))
            assertFalse(layout.ellipsized)
            assertFalse(layout.text.contains('…'))
            assertTrue(layout.visualLines.all { measurer.measureWidthDp(it, layout.fontSizeSp) <= width })
            assertTrue(layout.lineCount * layout.fontSizeSp * 1.25f <= height)
        }
    }

    @Test
    fun heightAndFontScaleArePartOfTheMeasurement() {
        val measurer = object : WidgetTextMeasurer {
            override fun measureWidthDp(text: String, fontSizeSp: Float) = text.length * fontSizeSp
            override fun measureLineHeightDp(fontSizeSp: Float) = fontSizeSp * 2f
        }
        val layout = WordLineBreaker.layoutWord("quiere", "es", Locale.ROOT, 40f,
            preferredFontSizeSp = 28f, minFontSizeSp = 18f, maxLines = 8,
            textMeasurer = measurer, availableHeightDp = 32f)
        assertTrue(layout.lineCount * measurer.measureLineHeightDp(layout.fontSizeSp) <= 32f)
        assertEquals("QUIERE", layout.visualLines.joinToString(""))
    }

    @Test
    fun emojiAndCombiningMarksRemainWholeAcrossEveryCut() {
        val clusters = listOf("A\u0301", "👨‍👩‍👧‍👦", "👍🏽", "🇲🇽", "1️⃣")
        assertEquals(clusters, WordLineBreaker.splitIntoGraphemes(clusters.joinToString("")))
        val word = clusters.joinToString("")
        val measurer = WidgetTextMeasurer { text, size ->
            WordLineBreaker.splitIntoGraphemes(text).size * size
        }
        val layout = WordLineBreaker.layoutWord(word, "und", Locale.ROOT, 20f,
            preferredFontSizeSp = 18f, minFontSizeSp = 18f, maxLines = 5,
            textMeasurer = measurer, availableHeightDp = 120f)
        assertEquals(word, layout.visualLines.joinToString(""))
        assertEquals(clusters, layout.visualLines.flatMap(WordLineBreaker::splitIntoGraphemes))
    }

    @Test
    fun resizeRecomputesThePartitionRatherThanReusingTheOldBreaks() {
        val measurer = WidgetTextMeasurer { text, size -> text.length * size }
        fun at(width: Float) = WordLineBreaker.layoutWord("QUIERE", "es", Locale.ROOT, width,
            preferredFontSizeSp = 18f, minFontSizeSp = 18f, maxLines = 3,
            textMeasurer = measurer, availableHeightDp = 90f)
        assertEquals("QUI\nERE", at(95f).text)
        assertEquals("QUIERE", at(120f).text)
        assertEquals("QUI\nERE", at(95f).text)
    }
}

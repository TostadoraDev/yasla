package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.components.TextOffsetRange
import xyz.tosdev.yetasimplelyicsapp.ui.components.KaraokeColors
import xyz.tosdev.yetasimplelyicsapp.ui.components.asTextAlign
import xyz.tosdev.yetasimplelyicsapp.ui.components.forTransparentOverlay
import xyz.tosdev.yetasimplelyicsapp.ui.components.groupSyllablesIntoWords
import xyz.tosdev.yetasimplelyicsapp.ui.components.karaokeProgressAt
import xyz.tosdev.yetasimplelyicsapp.ui.components.resolveCurrentWordContent
import xyz.tosdev.yetasimplelyicsapp.ui.components.resolveKaraokeLayerAlphas
import xyz.tosdev.yetasimplelyicsapp.ui.components.resolveTimedTextSegments
import xyz.tosdev.yetasimplelyicsapp.ui.components.revealBounds
import xyz.tosdev.yetasimplelyicsapp.ui.components.softRevealSlices
import xyz.tosdev.yetasimplelyicsapp.ui.components.splitTextRangeAcrossVisualLines
import xyz.tosdev.yetasimplelyicsapp.ui.components.unionGlyphBounds
import xyz.tosdev.yetasimplelyicsapp.ui.components.uppercaseForPresentation
import xyz.tosdev.yetasimplelyicsapp.ui.components.whiteKaraokeGradient
import java.util.Locale

class KaraokeGroupingTest {
    @Test
    fun groupsSyllablesBelongingToSameWord() {
        val words = listOf(
            KaraokeWordUi("1", "Super"),
            KaraokeWordUi("2", "estrella"),
        )
        val originalText = "Superestrella"

        val groups = groupSyllablesIntoWords(words, originalText)
        assertEquals(1, groups.size)
        assertEquals(listOf("Super", "estrella"), groups[0].syllables.map { it.text })
    }

    @Test
    fun groupsMultiWordSentenceCorrectly() {
        val words = listOf(
            KaraokeWordUi("1", "Me"),
            KaraokeWordUi("2", "ve"),
            KaraokeWordUi("3", "en"),
            KaraokeWordUi("4", "tran"),
            KaraokeWordUi("5", "do"),
        )
        val originalText = "Me ve entrando"

        val groups = groupSyllablesIntoWords(words, originalText)
        assertEquals(3, groups.size)
        assertEquals(listOf("Me"), groups[0].syllables.map { it.text })
        assertEquals(listOf("ve"), groups[1].syllables.map { it.text })
        assertEquals(listOf("en", "tran", "do"), groups[2].syllables.map { it.text })
    }

    @Test
    fun handlesBackgroundVocalsSeparatedByParentheses() {
        val words = listOf(
            KaraokeWordUi("1", "error"),
            KaraokeWordUi("2", "(Mi"),
            KaraokeWordUi("3", "pró"),
            KaraokeWordUi("4", "xi"),
            KaraokeWordUi("5", "mo"),
            KaraokeWordUi("6", "error)"),
        )
        val originalText = "error (Mi próximo error)"

        val groups = groupSyllablesIntoWords(words, originalText)
        assertEquals(4, groups.size)
        assertEquals(listOf("error"), groups[0].syllables.map { it.text })
        assertEquals(listOf("(Mi"), groups[1].syllables.map { it.text })
        assertEquals(listOf("pró", "xi", "mo"), groups[2].syllables.map { it.text })
        assertEquals(listOf("error)"), groups[3].syllables.map { it.text })
    }

    @Test
    fun karaokeProgressIsStableWhilePausedAndRespondsToSeeking() {
        val paused = karaokeProgressAt(1_500L, 1_000L, 2_000L, fallbackProgress = 0f)

        assertEquals(0.5f, paused, 0.001f)
        assertEquals(paused, karaokeProgressAt(1_500L, 1_000L, 2_000L, 0f), 0.001f)
        assertEquals(0f, karaokeProgressAt(500L, 1_000L, 2_000L, 0f), 0.001f)
        assertEquals(1f, karaokeProgressAt(2_500L, 1_000L, 2_000L, 0f), 0.001f)
    }

    @Test
    fun progressiveSegmentsExistOnlyForRealWordTiming() {
        val wordSynced = LyricLineUi(
            id = "word-line",
            originalText = "Hello world",
            words = listOf(
                KaraokeWordUi("w1", "Hello", startTimeMs = 1_000L, endTimeMs = 1_500L),
                KaraokeWordUi("w2", "world", startTimeMs = 1_500L, endTimeMs = 2_000L),
            ),
            isActive = true,
            startTimeMs = 1_000L,
            endTimeMs = 2_000L,
        )
        val lineSynced = wordSynced.copy(
            id = "line-line",
            words = listOf(KaraokeWordUi("untimed", "Hello world")),
        )

        val wordSegments = resolveTimedTextSegments(wordSynced)
        val lineSegments = resolveTimedTextSegments(lineSynced)

        assertEquals(2, wordSegments.size)
        assertEquals(LyricsTimingMode.WORD_SYNCED, wordSynced.timingMode)
        assertEquals(1_000L, wordSegments.first().startTimeMs)
        assertEquals(1_500L, wordSegments.last().startTimeMs)
        assertEquals(LyricsTimingMode.LINE_SYNCED, lineSynced.timingMode)
        assertEquals(emptyList<Any>(), lineSegments)
    }

    @Test
    fun rightAlignedLtrRevealUsesTextBoundsAndStillMovesLeftToRight() {
        val glyphBoundsAtRight = Rect(left = 80f, top = 4f, right = 100f, bottom = 24f)

        val ltrHalf = revealBounds(glyphBoundsAtRight, progress = 0.5f, isRtl = false)
        val rtlHalf = revealBounds(glyphBoundsAtRight, progress = 0.5f, isRtl = true)

        assertEquals(80f, ltrHalf.left, 0.001f)
        assertEquals(90f, ltrHalf.right, 0.001f)
        assertEquals(90f, rtlHalf.left, 0.001f)
        assertEquals(100f, rtlHalf.right, 0.001f)
    }

    @Test
    fun revealHasExactEmptyAndCompleteEndpointsForBothDirections() {
        val glyphBounds = Rect(left = 37f, top = 4f, right = 113f, bottom = 28f)

        assertEquals(Rect(37f, 4f, 37f, 28f), revealBounds(glyphBounds, 0f, false))
        assertEquals(glyphBounds, revealBounds(glyphBounds, 1f, false))
        assertEquals(Rect(113f, 4f, 113f, 28f), revealBounds(glyphBounds, 0f, true))
        assertEquals(glyphBounds, revealBounds(glyphBounds, 1f, true))
    }

    @Test
    fun softRevealNeverLeavesGlyphBoundsAndCompletionHasNoResidualFeather() {
        val glyphBounds = Rect(left = 80f, top = 4f, right = 180f, bottom = 28f)

        assertTrue(softRevealSlices(glyphBounds, 0f, isRtl = false, featherPx = 8f).isEmpty())
        for (rtl in listOf(false, true)) {
            val complete = softRevealSlices(glyphBounds, 1f, isRtl = rtl, featherPx = 8f)
            assertEquals(1, complete.size)
            assertEquals(glyphBounds, complete.single().bounds)
            assertEquals(1f, complete.single().alpha, 0f)

            val halfway = softRevealSlices(glyphBounds, 0.5f, isRtl = rtl, featherPx = 8f)
            assertTrue(halfway.size in 5..6)
            assertTrue(halfway.all { slice ->
                slice.bounds.left >= glyphBounds.left &&
                    slice.bounds.right <= glyphBounds.right &&
                    slice.bounds.top == glyphBounds.top &&
                    slice.bounds.bottom == glyphBounds.bottom &&
                    slice.alpha in 0f..1f
            })
            if (rtl) {
                assertEquals(130f, halfway.minOf { it.bounds.left }, 0.001f)
                assertEquals(180f, halfway.maxOf { it.bounds.right }, 0.001f)
            } else {
                assertEquals(80f, halfway.minOf { it.bounds.left }, 0.001f)
                assertEquals(130f, halfway.maxOf { it.bounds.right }, 0.001f)
            }
        }
    }

    @Test
    fun physicalAlignmentDoesNotChangeWithTextDirection() {
        assertEquals(TextAlign.Left, LyricsAlignment.LEFT.asTextAlign())
        assertEquals(TextAlign.Right, LyricsAlignment.RIGHT.asTextAlign())
        assertEquals(TextAlign.Center, LyricsAlignment.CENTER.asTextAlign())
    }

    @Test
    fun timedSegmentsCoverLeadingAndTrailingGlyphsAtCompletion() {
        val line = LyricLineUi(
            id = "punctuated",
            originalText = "«Hello, world!»",
            words = listOf(
                KaraokeWordUi("hello", "Hello", startTimeMs = 0L, endTimeMs = 500L),
                KaraokeWordUi("world", "world", startTimeMs = 500L, endTimeMs = 1_000L),
            ),
            isActive = true,
        )

        val segments = resolveTimedTextSegments(line)

        assertEquals(0, segments.first().startOffset)
        assertEquals(line.plainText.indexOf("world"), segments.first().endOffset)
        assertEquals(line.plainText.indexOf("world"), segments.last().startOffset)
        assertEquals(line.plainText.length, segments.last().endOffset)
    }

    @Test
    fun gradientBoundsAreTheUnionOfRealGlyphsRatherThanTheContainer() {
        val bounds = unionGlyphBounds(
            listOf(
                Rect(left = 72f, top = 4f, right = 82f, bottom = 24f),
                Rect(left = 84f, top = 4f, right = 100f, bottom = 24f),
            ),
        )

        assertEquals(Rect(72f, 4f, 100f, 24f), bounds)
    }

    @Test
    fun wrappedSegmentCreatesOneIndependentClipPerVisualLine() {
        val clips = splitTextRangeAcrossVisualLines(
            startOffset = 3,
            endOffset = 26,
            visualLines = listOf(
                TextOffsetRange(0, 10),
                TextOffsetRange(10, 20),
                TextOffsetRange(20, 30),
            ),
        )

        assertEquals(
            listOf(
                TextOffsetRange(3, 10),
                TextOffsetRange(10, 20),
                TextOffsetRange(20, 26),
            ),
            clips,
        )
    }

    @Test
    fun activeForegroundAlphaIsIndependentFromInactiveTextAlpha() {
        val layers = resolveKaraokeLayerAlphas(
            isActive = true,
            hasProgressiveTiming = true,
            inactiveTextAlpha = 0.45f,
            activeForegroundAlpha = 1f,
        )

        assertEquals(0.45f, layers.baseText, 0.001f)
        assertEquals(1f, layers.activeForeground, 0.001f)
    }

    @Test
    fun whiteGradientKeepsBothEndsBright() {
        val (start, end) = whiteKaraokeGradient()

        assertTrue(start.luminance() >= 0.9f)
        assertTrue(end.luminance() >= 0.75f)
    }

    @Test
    fun transparentOverlayDoesNotReuseDarkThemeTextForUnrevealedGlyphs() {
        val overlayColors = KaraokeColors(
            highlight = listOf(Color.White, Color.Black),
            inactive = Color.Black,
        ).forTransparentOverlay(KaraokeVisualStyle())

        assertTrue(overlayColors.highlight.last().luminance() >= 0.75f)
        assertEquals(Color.White.red, overlayColors.inactive.red, 0.001f)
        assertEquals(Color.White.green, overlayColors.inactive.green, 0.001f)
        assertEquals(Color.White.blue, overlayColors.inactive.blue, 0.001f)
        assertEquals(KaraokeVisualStyle().inactiveOpacity, overlayColors.inactive.alpha, 0.001f)
    }

    @Test
    fun currentWordUsesTheAggregatedWordAndFallsBackOnlyWhenConfigured() {
        val currentWord = KaraokeWordUi(
            id = "empiezas",
            text = "empiezas",
            startTimeMs = 743L,
            endTimeMs = 2_673L,
            segments = listOf(
                KaraokeSegmentUi("em", "em", 743L, 981L),
                KaraokeSegmentUi("pie", "pie", 981L, 2_012L),
                KaraokeSegmentUi("zas", "zas", 2_012L, 2_673L),
            ),
        )
        val wordLine = LyricLineUi(
            id = "word-line",
            originalText = "empiezas",
            words = listOf(currentWord),
            startTimeMs = 743L,
            endTimeMs = 2_673L,
            isActive = true,
        )

        val resolved = resolveCurrentWordContent(wordLine, "empiezas", fallbackToLine = true)
        assertEquals("empiezas", resolved?.line?.plainText)
        assertEquals(3, resolved?.line?.words?.single()?.segments?.size)

        val lineOnly = wordLine.copy(
            id = "line-only",
            words = listOf(KaraokeWordUi("untimed", "empiezas")),
        )
        assertEquals("empiezas", resolveCurrentWordContent(lineOnly, null, true)?.line?.plainText)
        assertEquals(null, resolveCurrentWordContent(lineOnly, null, false))
    }

    @Test
    fun currentWordUppercaseIsPresentationOnlyAndKeepsTimingIdentity() {
        val sourceWord = KaraokeWordUi(
            id = "istanbul",
            text = "istanbul",
            startTimeMs = 100L,
            endTimeMs = 900L,
            segments = listOf(
                KaraokeSegmentUi("is", "is", 100L, 300L),
                KaraokeSegmentUi("tanbul", "tanbul", 300L, 900L),
            ),
        )
        val source = resolveCurrentWordContent(
            activeLine = LyricLineUi(
                id = "line",
                words = listOf(sourceWord),
                originalText = "istanbul",
                isActive = true,
            ),
            activeWordId = sourceWord.id,
            fallbackToLine = true,
        )!!

        val display = source.uppercaseForPresentation(Locale.forLanguageTag("tr-TR"))

        assertEquals("istanbul", source.line.plainText)
        assertEquals("İSTANBUL", display.line.plainText)
        assertEquals(listOf("İS", "TANBUL"), display.line.words.single().segments.map { it.text })
        assertEquals(sourceWord.id, display.line.words.single().id)
        assertEquals(sourceWord.startTimeMs, display.line.words.single().startTimeMs)
        assertEquals(sourceWord.endTimeMs, display.line.words.single().endTimeMs)
    }

    @Test
    fun lexicalWordMayPreserveSeparatorWhitespaceWithoutLosingWordSync() {
        val word = KaraokeWordUi(
            id = "hello",
            text = "Hello ",
            segments = listOf(KaraokeSegmentUi("hello-segment", "Hello ", 0L, 500L)),
        )

        assertEquals(true, word.hasDetailedTiming)
    }
}

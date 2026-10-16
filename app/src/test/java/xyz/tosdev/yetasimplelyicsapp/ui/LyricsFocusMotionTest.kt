package xyz.tosdev.yetasimplelyicsapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment
import xyz.tosdev.yetasimplelyicsapp.ui.components.LYRICS_FOCUS_FRACTION
import xyz.tosdev.yetasimplelyicsapp.ui.components.isLargeLyricsJump
import xyz.tosdev.yetasimplelyicsapp.ui.components.lyricFocusOffsetPx
import xyz.tosdev.yetasimplelyicsapp.ui.components.lyricsRowAlignment

class LyricsFocusMotionTest {
    @Test
    fun activeLineFocusStaysInsideRequestedUpperViewportBand() {
        assertTrue(LYRICS_FOCUS_FRACTION in 0.25f..0.33f)
        val itemTop = lyricFocusOffsetPx(viewportHeightPx = 1_000, currentItemHeightPx = 120)
        assertEquals(210, itemTop)
        assertEquals(270, itemTop + 60)
        assertEquals(0, lyricFocusOffsetPx(0, 120))
        assertEquals(0, lyricFocusOffsetPx(-100, 120))
    }

    @Test
    fun nearbyLineChangesUseContinuousMotion() {
        assertFalse(isLargeLyricsJump(previousIndex = 8, currentIndex = 9, lyricsChanged = false))
        assertFalse(isLargeLyricsJump(previousIndex = 8, currentIndex = 10, lyricsChanged = false))
    }

    @Test
    fun seeksAndTrackChangesUseRepositionTransition() {
        assertTrue(isLargeLyricsJump(previousIndex = 8, currentIndex = 11, lyricsChanged = false))
        assertTrue(isLargeLyricsJump(previousIndex = 8, currentIndex = 9, lyricsChanged = true))
    }

    @Test
    fun backgroundLyricsUseThePrimaryLinesAnchor() {
        val primary = LyricLineUi(
            id = "primary",
            words = emptyList(),
            startTimeMs = 1_000L,
            alignment = LyricsAlignment.LEFT,
            sourceAlignment = LyricsAlignment.LEFT,
        )
        val background = LyricLineUi(
            id = "background",
            words = emptyList(),
            isBackground = true,
            startTimeMs = 1_000L,
            alignment = LyricsAlignment.CENTER,
            sourceAlignment = LyricsAlignment.CENTER,
        )

        assertEquals(
            LyricsAlignment.LEFT,
            lyricsRowAlignment(listOf(primary, background), index = 1, override = "AUTO"),
        )
    }
}

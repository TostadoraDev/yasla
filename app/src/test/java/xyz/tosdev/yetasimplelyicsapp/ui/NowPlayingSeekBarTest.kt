package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.screens.normalizedSeekProgress
import xyz.tosdev.yetasimplelyicsapp.ui.screens.remainingSeekAnimationDurationMs
import xyz.tosdev.yetasimplelyicsapp.ui.screens.seekBarAccentColor
import xyz.tosdev.yetasimplelyicsapp.ui.screens.seekDisplayProgress
import xyz.tosdev.yetasimplelyicsapp.ui.screens.shouldReleasePendingSeek

class NowPlayingSeekBarTest {
    @Test
    fun scrubbingAndPendingCommitAlwaysWinOverPlaybackUpdates() {
        assertEquals(
            0.78f,
            seekDisplayProgress(0.22f, 0.78f, isScrubbing = true, pendingProgress = null),
            0f,
        )
        assertEquals(
            0.78f,
            seekDisplayProgress(0.24f, 0.78f, isScrubbing = false, pendingProgress = 0.78f),
            0f,
        )
        assertEquals(
            0.24f,
            seekDisplayProgress(0.24f, 0.78f, isScrubbing = false, pendingProgress = null),
            0f,
        )
    }

    @Test
    fun pendingSeekReleasesOnlyAfterPlaybackAcknowledgesIt() {
        assertFalse(shouldReleasePendingSeek(0.30f, 0.80f))
        assertTrue(shouldReleasePendingSeek(0.791f, 0.80f))
        assertTrue(shouldReleasePendingSeek(0.809f, 0.80f))
        assertFalse(shouldReleasePendingSeek(0.78f, 0.80f))
    }

    @Test
    fun touchMappingMatchesTheVisibleTrackAndClampsAtItsEnds() {
        assertEquals(0f, normalizedSeekProgress(-20f, 1_000f, 10f), 0f)
        assertEquals(0f, normalizedSeekProgress(10f, 1_000f, 10f), 0f)
        assertEquals(0.5f, normalizedSeekProgress(500f, 1_000f, 10f), 0.0001f)
        assertEquals(1f, normalizedSeekProgress(990f, 1_000f, 10f), 0f)
        assertEquals(1f, normalizedSeekProgress(1_200f, 1_000f, 10f), 0f)
    }

    @Test
    fun continuousPlaybackAnimationUsesTheActualRemainingTrackTime() {
        assertEquals(5_000, remainingSeekAnimationDurationMs(0.5f, 10_000L))
        assertEquals(3_600_000, remainingSeekAnimationDurationMs(0f, 3_600_000L))
        assertEquals(1, remainingSeekAnimationDurationMs(1f, 3_600_000L))
        assertEquals(1, remainingSeekAnimationDurationMs(0.5f, 0L))
    }

    @Test
    fun artworkAccentIsLiftedOnlyWhenNeededForVisibility() {
        val darkBackground = Color(0xFF08090A)
        val darkAccent = Color(0xFF111820)
        val resolvedDark = seekBarAccentColor(darkAccent, darkBackground)
        assertTrue(contrast(resolvedDark, darkBackground) >= 2.4f)
        assertTrue(resolvedDark.luminance() > darkAccent.luminance())

        val lightBackground = Color(0xFFF7F6F2)
        val lightAccent = Color(0xFFFFF4E8)
        val resolvedLight = seekBarAccentColor(lightAccent, lightBackground)
        assertTrue(contrast(resolvedLight, lightBackground) >= 2.4f)
        assertTrue(resolvedLight.luminance() < lightAccent.luminance())

        val alreadyVisible = Color(0xFFFF745E)
        assertEquals(alreadyVisible, seekBarAccentColor(alreadyVisible, darkBackground))
    }

    private fun contrast(first: Color, second: Color): Float {
        val light = maxOf(first.luminance(), second.luminance()) + 0.05f
        val dark = minOf(first.luminance(), second.luminance()) + 0.05f
        return light / dark
    }
}

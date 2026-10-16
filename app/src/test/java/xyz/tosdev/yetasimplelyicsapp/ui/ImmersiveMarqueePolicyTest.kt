package xyz.tosdev.yetasimplelyicsapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.screens.marqueeTravelDurationMillis
import xyz.tosdev.yetasimplelyicsapp.ui.screens.shouldAnimateMetadataMarquee

class ImmersiveMarqueePolicyTest {
    @Test
    fun fullscreenChromeIsSuppressedOnlyForNowPlaying() {
        assertTrue(isImmersiveNowPlaying(AppDestination.NOW_PLAYING, fullscreenRequested = true))
        assertFalse(isImmersiveNowPlaying(AppDestination.NOW_PLAYING, fullscreenRequested = false))
        assertFalse(isImmersiveNowPlaying(AppDestination.LIBRARY, fullscreenRequested = true))
        assertFalse(isImmersiveNowPlaying(AppDestination.SETTINGS, fullscreenRequested = true))
    }

    @Test
    fun marqueeRequiresRealOverflowAndFullMotion() {
        assertTrue(shouldAnimateMetadataMarquee(
            hasOverflow = true,
            motionEnabled = true,
            motionReduced = false,
        ))
        assertFalse(shouldAnimateMetadataMarquee(
            hasOverflow = false,
            motionEnabled = true,
            motionReduced = false,
        ))
        assertFalse(shouldAnimateMetadataMarquee(
            hasOverflow = true,
            motionEnabled = false,
            motionReduced = false,
        ))
        assertFalse(shouldAnimateMetadataMarquee(
            hasOverflow = true,
            motionEnabled = true,
            motionReduced = true,
        ))
    }

    @Test
    fun marqueeTravelStaysSlowAndBounded() {
        assertEquals(4_000, marqueeTravelDurationMillis(0f))
        assertEquals(10_000, marqueeTravelDurationMillis(220f))
        assertEquals(18_000, marqueeTravelDurationMillis(10_000f))
    }
}

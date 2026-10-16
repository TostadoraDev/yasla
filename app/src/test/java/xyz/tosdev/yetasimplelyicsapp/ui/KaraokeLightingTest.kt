package xyz.tosdev.yetasimplelyicsapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllIlluminatedFraction
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllGlowStrength
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllKaraokeAlpha

class KaraokeLightingTest {
    @Test fun onlyPendingInkIsDarkenedAndCompletedInkRetainsItsBrightness() {
        assertEquals(0.22f, amllKaraokeAlpha(0.4f, 1f, 0f, true), 0.001f)
        assertEquals(1f, amllKaraokeAlpha(0.4f, 1f, 1f, true), 0f)
        assertEquals(0.61f, amllKaraokeAlpha(0.4f, 1f, 0.5f, true), 0.001f)
        assertEquals(0.2f, amllKaraokeAlpha(0.2f, 0.2f, 0f, false), 0f)
    }
    @Test fun revealLightFollowsMeasuredInkAndHasNoResidualOutsideItsFeather() {
        assertEquals(0f, amllIlluminatedFraction(0f, -20f, 20f), 0f)
        assertEquals(1f, amllIlluminatedFraction(0f, 40f, 20f), 0f)
        assertEquals(0.5f, amllIlluminatedFraction(50f, 40f, 20f), 0.001f)
        assertEquals(0f, amllIlluminatedFraction(70f, 40f, 20f), 0f)
        assertEquals(1f, amllIlluminatedFraction(100f, 100f, 20f), 0f)
    }

    @Test fun glowNeverLightsPendingTextOrInactiveRowsAndRespectsMotionPreference() {
        assertEquals(0f, amllGlowStrength(0f, true, MotionLevel.NORMAL), 0f)
        assertEquals(0f, amllGlowStrength(1f, false, MotionLevel.NORMAL), 0f)
        assertEquals(0f, amllGlowStrength(1f, true, MotionLevel.OFF), 0f)
        assertEquals(0f, amllGlowStrength(1f, true, MotionLevel.REDUCED), 0f)
        assertTrue(amllGlowStrength(1f, true, MotionLevel.NORMAL) > 0f)
        assertEquals(amllGlowStrength(1f, true, MotionLevel.NORMAL) * 0.4f,
            amllGlowStrength(0.4f, true, MotionLevel.NORMAL), 0.001f)
    }
}

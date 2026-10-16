package xyz.tosdev.yetasimplelyicsapp

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.theme.ArtworkAmbientColors

/** Real Compose/Android draw integration; these checks do not claim a particular refresh rate. */
@RunWith(AndroidJUnit4::class)
class AmbientBackgroundInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun constantArtworkKeepsDrawingWithoutRecomposingItsParent() {
        requireSystemAnimations()
        withBackground { activity ->
            settle(500L)
            val first = capturePanels(activity).first
            val compositions = activity.parentCompositionCount
            settle(10_000L)
            val second = capturePanels(activity).first
            settle(10_000L)
            val third = capturePanels(activity).first
            assertMoving("0-10 seconds", first, second)
            assertMoving("10-20 seconds", second, third)
            assertEquals("Ambient frames must not recompose their parent", compositions, activity.parentCompositionCount)
        }
    }

    @Test
    fun offKeepsTheRenderedBackgroundIdentical() {
        withBackground { activity ->
            instrumentation.runOnMainSync { activity.setMotionLevel(MotionLevel.OFF) }
            settle(1_500L)
            val first = capturePanels(activity).first
            settle(1_000L)
            val later = capturePanels(activity).first
            assertEquals("Off changed background pixels", 0.0, pixelDifference(first, later).meanDelta, 0.0)
        }
    }

    @Test
    fun changingArtworkKeepsMovingAfterItsColorTransitionCompletes() {
        requireSystemAnimations()
        withBackground { activity ->
            settle(500L)
            val warm = capturePanels(activity).first
            instrumentation.runOnMainSync {
                val palette = AmbientBackgroundTestActivity.WarmPalette
                activity.updatePalette(palette.copy(
                    cacheKey = 0x76543210,
                    ambient = ArtworkAmbientColors(
                        dominant = Color(0xFF287CE2),
                        secondary = Color(0xFF4057AB),
                        muted = Color(0xFF2C3D65),
                        dark = Color(0xFF090D18),
                    ),
                ))
            }
            settle(1_600L)
            val transitioned = capturePanels(activity).first
            assertTrue("Artwork color transition was not visible", pixelDifference(warm, transitioned).meanDelta > 1.0)
            val compositions = activity.parentCompositionCount
            settle(10_000L)
            val tenSecondsLater = capturePanels(activity).first
            settle(10_000L)
            val twentySecondsLater = capturePanels(activity).first
            assertMoving("first 10 seconds after artwork transition", transitioned, tenSecondsLater)
            assertMoving("second 10 seconds after artwork transition", tenSecondsLater, twentySecondsLater)
            assertEquals("Post-transition ambient frames must not recompose their parent",
                compositions, activity.parentCompositionCount)
        }
    }

    @Test
    fun changingArtworkIdentityDoesNotRestartAnOtherwiseContinuousClock() {
        requireSystemAnimations()
        withBackground { activity ->
            instrumentation.runOnMainSync(activity::showPairedPanels)
            settle(10_000L)
            val initial = capturePanels(activity)
            assertTrue("Paired clocks did not start together",
                pixelDifference(initial.first, requireNotNull(initial.second)).meanDelta < 0.10)
            instrumentation.runOnMainSync {
                val palette = AmbientBackgroundTestActivity.WarmPalette
                // Only the least significant bit of the energy byte changes. Its impact remains
                // below one 8-bit channel step; a phase restart produces a much larger delta.
                activity.updatePalette(palette.copy(cacheKey = palette.cacheKey xor 0x01000000))
            }
            settle(1_600L)
            val after = capturePanels(activity)
            val difference = pixelDifference(after.first, requireNotNull(after.second))
            assertTrue("Artwork identity appears to have restarted ambient phase: $difference",
                difference.meanDelta < 0.20)
            assertTrue("Paired clocks diverged beyond the small energy change: $difference",
                difference.overOneFraction < 0.005)
        }
    }

    @Test
    fun systemMotionIsReadAgainWhenTheLifecycleResumes() {
        withBackground { activity ->
            instrumentation.runOnMainSync(activity::showSystemMotionProbe)
            settle(150L)
            assertFalse("Initial disabled state was not read", requireNotNull(activity.observedProbeEnabled))
            instrumentation.runOnMainSync(activity::resumeProbeWithAnimationsEnabled)
            settle(150L)
            assertTrue("Re-enabling system motion remained cached as disabled", requireNotNull(activity.observedProbeEnabled))
        }
    }

    private fun requireSystemAnimations() {
        assumeTrue("Normal motion requires Android system animations enabled",
            Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
    }

    private fun withBackground(test: (AmbientBackgroundTestActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, AmbientBackgroundTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as AmbientBackgroundTestActivity
        try {
            instrumentation.waitForIdleSync()
            test(activity)
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }

    private fun settle(durationMs: Long) {
        instrumentation.waitForIdleSync()
        // Bounded wall-clock observation of the actual production frame clock, not a 60Hz loop.
        SystemClock.sleep(durationMs)
        instrumentation.waitForIdleSync()
    }

    private fun capturePanels(activity: AmbientBackgroundTestActivity): Pair<Bitmap, Bitmap?> {
        var first: Rect? = null
        var second: Rect? = null
        instrumentation.runOnMainSync {
            val screen = IntArray(2)
            val window = IntArray(2)
            activity.window.decorView.getLocationOnScreen(screen)
            activity.window.decorView.getLocationInWindow(window)
            fun screenBounds(bounds: Rect?) = bounds?.let { Rect(it).apply { offset(screen[0] - window[0], screen[1] - window[1]) } }
            first = screenBounds(activity.targetBounds)
            second = screenBounds(activity.referenceBounds)
        }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        fun crop(bounds: Rect): Bitmap {
            require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= screenshot.width && bounds.bottom <= screenshot.height)
            return Bitmap.createBitmap(screenshot, bounds.left, bounds.top, bounds.width(), bounds.height())
        }
        return crop(requireNotNull(first)) to second?.let(::crop)
    }

    private data class Difference(
        val meanDelta: Double,
        val changedFraction: Double,
        val overOneFraction: Double,
    )

    private fun assertMoving(window: String, first: Bitmap, second: Bitmap) {
        val difference = pixelDifference(first, second)
        assertTrue("The production background stayed visually static during $window: $difference",
            difference.meanDelta > 0.25)
        assertTrue("Too few background pixels changed during $window: $difference",
            difference.changedFraction > 0.10)
    }

    private fun pixelDifference(first: Bitmap, second: Bitmap): Difference {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        val count = first.width * first.height
        val before = IntArray(count)
        val after = IntArray(count)
        first.getPixels(before, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(after, 0, second.width, 0, 0, second.width, second.height)
        var total = 0L
        var changed = 0
        var overOne = 0
        for (index in before.indices) {
            val delta = maxOf(abs((before[index] ushr 16 and 255) - (after[index] ushr 16 and 255)),
                abs((before[index] ushr 8 and 255) - (after[index] ushr 8 and 255)),
                abs((before[index] and 255) - (after[index] and 255)))
            total += delta
            if (delta > 0) changed++
            if (delta > 1) overOne++
        }
        return Difference(
            meanDelta = total.toDouble() / count,
            changedFraction = changed.toDouble() / count,
            overOneFraction = overOne.toDouble() / count,
        )
    }
}

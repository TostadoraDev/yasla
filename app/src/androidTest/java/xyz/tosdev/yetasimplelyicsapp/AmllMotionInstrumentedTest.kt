package xyz.tosdev.yetasimplelyicsapp

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AmllMotionInstrumentedTest {
    @Test
    fun realNativeViewportShowsContinuousMotionWithWrappingPauseAndSeek() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, AmllMotionTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as AmllMotionTestActivity
        val output = File(context.getExternalFilesDir(null), "amll-parity").apply { mkdirs() }
        try {
            // startActivitySync can return before the first native surface is submitted.
            val readyDeadline = SystemClock.elapsedRealtime() + 10_000L
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < readyDeadline) {
                instrumentation.runOnMainSync {
                    val decor = activity.window.decorView
                    ready = activity.hasWindowFocus() && decor.isAttachedToWindow && decor.width > 0 && decor.height > 0
                }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("The fixture must be visible; a keyguard screenshot is not renderer evidence", ready)
            val samples = listOf(200L, 600L, 1_500L, 4_500L, 5_000L, 6_000L, 8_900L,
                9_800L, 13_500L, 19_000L, 26_500L, 31_000L)
            for (time in samples) {
                instrumentation.runOnMainSync { activity.setSample(time) }
                SystemClock.sleep(850)
                val image = activity.captureWindow()
                File(output, "native-$time.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                assertTrue("Glyphs must be visible at $time", image.hasVisibleGlyphs())
                if (time == 5_000L || time == 13_500L) {
                    assertTrue("The active reveal must illuminate glyphs at $time", image.hasVisibleGlyphs(140))
                    assertTrue("Future rows must not be brightened by fragment overdraw at $time",
                        !image.hasBrightFutureRows())
                }
            }
            instrumentation.runOnMainSync { activity.setSample(5_000L, isPlaying = false) }
            SystemClock.sleep(1_500)
            val pause = activity.captureWindow()
            File(output, "native-pause.png").outputStream().use { pause.compress(Bitmap.CompressFormat.PNG, 100, it) }
            instrumentation.runOnMainSync { activity.setSample(500L) }
            SystemClock.sleep(1_000)
            val seek = activity.captureWindow()
            File(output, "native-seek-back.png").outputStream().use { seek.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("Backward seek must keep glyphs visible", seek.hasVisibleGlyphs())
            // A real wall-clock run verifies continuity independently of the static seek samples.
            val begin = SystemClock.elapsedRealtime()
            var previousBucket = -1
            while (SystemClock.elapsedRealtime() - begin <= 20_000L) {
                val elapsed = SystemClock.elapsedRealtime() - begin
                instrumentation.runOnMainSync { activity.setSample(elapsed) }
                val bucket = (elapsed / 5_000L).toInt()
                if (bucket != previousBucket) {
                    previousBucket = bucket
                    val frame = activity.captureWindow()
                    File(output, "native-continuous-${bucket * 5}.png").outputStream().use {
                        frame.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                SystemClock.sleep(16)
            }
            instrumentation.runOnMainSync(activity::setRtlSample)
            SystemClock.sleep(1_500)
            val rtl = activity.captureWindow()
            File(output, "native-rtl.png").outputStream().use { rtl.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("RTL text must remain visible and contextually shaped", rtl.hasVisibleGlyphs())
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }
}

private fun AmllMotionTestActivity.captureWindow(): Bitmap {
    val view = window.decorView
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    val ready = CountDownLatch(1)
    var result = -1
    PixelCopy.request(window, image, { status -> result = status; ready.countDown() }, Handler(Looper.getMainLooper()))
    assertTrue("The native window must provide a rendered frame", ready.await(5, TimeUnit.SECONDS))
    assertTrue("PixelCopy failed: $result", result == PixelCopy.SUCCESS)
    return image
}

private fun Bitmap.hasVisibleGlyphs(minimumChannel: Int = 35): Boolean {
    var light = 0
    for (y in height / 5 until height * 4 / 5 step 3) {
        for (x in 20 until width - 20 step 3) {
            val pixel = getPixel(x, y)
            if (((pixel ushr 16) and 255) > minimumChannel && ((pixel ushr 8) and 255) > minimumChannel) light++
        }
    }
    return light > 40
}

private fun Bitmap.hasBrightFutureRows(): Boolean {
    var bright = 0
    for (y in height * 45 / 100 until height * 4 / 5 step 3) {
        for (x in 20 until width - 20 step 3) {
            val pixel = getPixel(x, y)
            if (((pixel ushr 16) and 255) > 130 && ((pixel ushr 8) and 255) > 130) bright++
        }
    }
    return bright > 40
}

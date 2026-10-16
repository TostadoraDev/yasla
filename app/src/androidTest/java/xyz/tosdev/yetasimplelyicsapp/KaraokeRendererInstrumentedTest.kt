package xyz.tosdev.yetasimplelyicsapp

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class KaraokeRendererInstrumentedTest {
    @Test
    fun wordSyncedWrappingDrawsGlyphsWithoutOpaqueRevealBlocks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, KaraokeRendererTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as KaraokeRendererTestActivity

        try {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(700L)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val artifact = File(
                instrumentation.targetContext.getExternalFilesDir(null),
                "karaoke-render-regression.png",
            )
            FileOutputStream(artifact).use { output ->
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            assertTrue(
                "The active foreground contains an opaque rectangular block instead of glyphs",
                !screenshot.containsOpaqueBrightBlock(),
            )
            assertTrue(
                "The renderer drew a large visible veil outside the glyphs",
                !screenshot.containsLargeVisibleVeil(),
            )
            val root = instrumentation.uiAutomation.rootInActiveWindow
            assertTrue("Current Word must be rendered in uppercase", root.containsText("EMPIEZAS"))
            assertTrue(
                "Current Word must not render a decorative label",
                !root.containsText("PALABRA ACTUAL")
            )
        } finally {
            instrumentation.runOnMainSync(activity::finish)
        }
    }
}

private fun android.view.accessibility.AccessibilityNodeInfo?.containsText(expected: String): Boolean {
    val node = this ?: return false
    if (node.text?.toString()?.contains(expected, ignoreCase = false) == true) return true
    for (index in 0 until node.childCount) {
        if (node.getChild(index).containsText(expected)) return true
    }
    return false
}

private fun Bitmap.containsOpaqueBrightBlock(): Boolean {
    // Large display typography can contain a fully white 20x14 patch inside a wide glyph.
    // A 64x36 window is still far smaller than the historical renderer rectangle while being
    // wider and taller than any legitimate stroke in the regression scene.
    val blockWidth = 64
    val blockHeight = 36
    return containsDenseBlock(
        blockWidth = blockWidth,
        blockHeight = blockHeight,
        horizontalInset = 20,
        verticalInset = 20,
        step = 4,
        minimumFraction = 0.88f,
    ) { pixel ->
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        red >= 245 && green >= 245 && blue >= 245
    }
}

private fun Bitmap.containsLargeVisibleVeil(): Boolean {
    return containsDenseBlock(
        blockWidth = 96,
        blockHeight = 48,
        horizontalInset = 24,
        verticalInset = 96,
        step = 12,
        minimumFraction = 0.86f,
    ) { pixel ->
        val red = pixel ushr 16 and 0xFF
        val green = pixel ushr 8 and 0xFF
        val blue = pixel and 0xFF
        red + green + blue >= 36
    }
}

private inline fun Bitmap.containsDenseBlock(
    blockWidth: Int,
    blockHeight: Int,
    horizontalInset: Int,
    verticalInset: Int,
    step: Int,
    minimumFraction: Float,
    crossinline matches: (Int) -> Boolean,
): Boolean {
    val right = width - blockWidth - horizontalInset
    val bottom = height - blockHeight - verticalInset
    if (right < horizontalInset || bottom < verticalInset) return false

    // Integral image keeps every sampled rectangle O(1). A direct nested scan makes the
    // instrumented visual regression test take minutes on a full-resolution framebuffer.
    val stride = width + 1
    val integral = IntArray(stride * (height + 1))
    val rowPixels = IntArray(width)
    for (y in 0 until height) {
        getPixels(rowPixels, 0, width, 0, y, width, 1)
        var rowMatches = 0
        val previousRow = y * stride
        val currentRow = (y + 1) * stride
        for (x in 0 until width) {
            if (matches(rowPixels[x])) rowMatches++
            integral[currentRow + x + 1] = integral[previousRow + x + 1] + rowMatches
        }
    }

    val requiredPixels = blockWidth * blockHeight * minimumFraction
    for (y in verticalInset..bottom step step) {
        val bottomRow = (y + blockHeight) * stride
        val topRow = y * stride
        for (x in horizontalInset..right step step) {
            val blockRight = x + blockWidth
            val matchesInBlock = integral[bottomRow + blockRight] -
                    integral[topRow + blockRight] -
                    integral[bottomRow + x] +
                    integral[topRow + x]
            if (matchesInBlock >= requiredPixels) return true
        }
    }
    return false
}

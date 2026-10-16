package xyz.tosdev.yetasimplelyicsapp

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import android.view.MotionEvent
import android.graphics.Rect
import android.content.ComponentName
import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import androidx.glance.appwidget.GlanceAppWidgetManager
import xyz.tosdev.yetasimplelyicsapp.widget.*

@RunWith(AndroidJUnit4::class)
class RefinementVisualTest {
    @Test
    fun fingerOwnsScrubbingAndSeekCommitsOnceWithoutChangingLyricsOffset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
            RefinementPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RefinementPreviewActivity
        try {
            SystemClock.sleep(1_000)
            clickLabel(activity, "Sonando")
            SystemClock.sleep(600)
            val progress = progressNode(activity)
            val bounds = progress.boundsInWindow
            val downTime = SystemClock.uptimeMillis()
            fun pointer(action: Int, fraction: Float) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    bounds.left + bounds.width * fraction, bounds.center.y, 0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                instrumentation.runOnMainSync { activity.window.decorView.dispatchTouchEvent(event) }
                event.recycle()
            }
            pointer(MotionEvent.ACTION_DOWN, 0.2f)
            pointer(MotionEvent.ACTION_MOVE, 0.7f)
            instrumentation.runOnMainSync { activity.reportPlaybackPosition(500) }
            SystemClock.sleep(100)
            assertEquals(0, activity.seekCount)
            val during = progressNode(activity).config[SemanticsProperties.ProgressBarRangeInfo].current
            assertEquals(0.7f, during, 0.04f)
            pointer(MotionEvent.ACTION_MOVE, 0.84f)
            pointer(MotionEvent.ACTION_UP, 0.84f)
            SystemClock.sleep(200)
            assertEquals(1, activity.seekCount)
            assertEquals(0.84f, activity.lastSeek, 0.04f)
            assertEquals(0L, activity.preview.offsetMs)
            clickLabel(activity, "Aumentar ajuste de letra 25 milisegundos")
            SystemClock.sleep(100)
            assertEquals(25L, activity.preview.offsetMs)
            assertEquals(1, activity.seekCount)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test
    fun widgetConfigurationKeepsEveryLoadedSettingAndRendersBothThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (dark in listOf(true, false)) {
            val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
                RefinementPreviewActivity::class.java).putExtra("screen", "widget").putExtra("dark", dark)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RefinementPreviewActivity
            try {
                SystemClock.sleep(1_500)
                saveWindow(activity, "widget-${if (dark) "dark" else "light"}.png")
                if (dark) {
                    clickLabel(activity, "Palabra actual")
                    SystemClock.sleep(300)
                    instrumentation.runOnMainSync {
                        assertTrue("Current Word preview must display the full semantic word",
                            nodes(activity).any { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)
                                .orEmpty().any { it == "QUIERE" } })
                    }
                    saveWindow(activity, "widget-current-word.png")
                }
                clickLabel(activity, "Guardar widget")
                SystemClock.sleep(100)
                assertEquals(if (dark) activity.initialWidgetConfig.copy(contentMode = LyricsWidgetContentMode.CURRENT_WORD)
                    else activity.initialWidgetConfig, activity.savedWidgetConfig)
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test
    fun existingWidgetInstancesStillRenderAndKeepTheirSavedConfiguration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LyricsWidgetReceiver::class.java))
        val manager = GlanceAppWidgetManager(context)
        val repository = PreferencesLyricsWidgetConfigurationRepository(context)
        for (id in ids) {
            val before = repository.load(id)
            LyricsWidget().update(context, manager.getGlanceIdBy(id))
            assertEquals(before, repository.load(id))
        }
        File(context.getExternalFilesDir(null), "refinement").mkdirs()
        File(context.getExternalFilesDir(null), "refinement/widget-instances.txt").writeText("Validated existing instances: ${ids.size}\n")
    }

    @Test
    fun captureProductionNowPlayingWithLocalDemoData() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, RefinementPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as RefinementPreviewActivity
        try {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    ready = activity.hasWindowFocus() && activity.window.decorView.isAttachedToWindow
                }
                if (!ready) SystemClock.sleep(100)
            }
            check(ready) { "Preview window never became visible" }
            clickLabel(activity, "Sonando")
            SystemClock.sleep(3_000)
            val bitmap = Bitmap.createBitmap(activity.window.decorView.width,
                activity.window.decorView.height, Bitmap.Config.ARGB_8888)
            val latch = CountDownLatch(1)
            var status = -1
            PixelCopy.request(activity.window, bitmap, { status = it; latch.countDown() }, Handler(Looper.getMainLooper()))
            check(latch.await(5, TimeUnit.SECONDS))
            assertEquals(PixelCopy.SUCCESS, status)
            File(context.getExternalFilesDir(null), "refinement").mkdirs()
            File(context.getExternalFilesDir(null), "refinement/now-playing.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun compositionRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            compositionRoot(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun nodes(activity: RefinementPreviewActivity): List<SemanticsNode> {
        val root = checkNotNull(compositionRoot(activity.window.decorView))
        root.measureAndLayoutForTest()
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { collect(it) }
        return collect(root.semanticsOwner.rootSemanticsNode)
    }

    private fun progressNode(activity: RefinementPreviewActivity): SemanticsNode {
        var found: SemanticsNode? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = nodes(activity).firstOrNull { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null }
        }
        return checkNotNull(found) { "No measured seek node in the rendered composition" }
    }

    private fun clickLabel(activity: RefinementPreviewActivity, label: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            var clicked = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val node = nodes(activity).firstOrNull {
                    val texts = it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } +
                        it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    texts.any { text -> text.contains(label) } && it.config.getOrNull(SemanticsActions.OnClick)?.action != null
                }
                clicked = node?.config?.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
            }
            if (clicked) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        assertTrue("Missing interactive label in Compose: $label", false)
    }

    private fun saveWindow(activity: RefinementPreviewActivity, name: String) {
        val bitmap = Bitmap.createBitmap(activity.window.decorView.width,
            activity.window.decorView.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var status = -1
        PixelCopy.request(activity.window, bitmap, { status = it; latch.countDown() }, Handler(Looper.getMainLooper()))
        check(latch.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, status)
        val output = File(activity.getExternalFilesDir(null), "refinement").apply { mkdirs() }
        File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

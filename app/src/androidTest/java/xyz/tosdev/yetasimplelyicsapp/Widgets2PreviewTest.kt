package xyz.tosdev.yetasimplelyicsapp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlinx.coroutines.flow.first
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.widget.*
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class Widgets2PreviewTest {
    @Test
    fun previewUpdatesWordsModesAndStatesInBothLanguages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previous = RefinementPreviewActivity.testLanguageTag
        try {
            for (tag in listOf("en", "es")) {
                RefinementPreviewActivity.testLanguageTag = tag
                val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
                    RefinementPreviewActivity::class.java).putExtra("screen", "widget")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RefinementPreviewActivity
                try {
                    click(activity, activity.getString(R.string.studio_current_word))
                    awaitContent(activity, "QUIERE")
                    assertFalse(texts(activity).any { it.contains("CONTIGO") })
                    instrumentation.runOnMainSync { activity.widgetSnapshot = activity.widgetSnapshot.copy(
                        activeWordId = "next-word", activeWord = "BEAUTIFUL", activeLine = "Real lyric content") }
                    awaitContent(activity, "BEAUTIFUL")
                    capture(activity, "preview-$tag-word.png")
                    click(activity, activity.getString(R.string.studio_current_line))
                    awaitContent(activity, "Real lyric content")
                    instrumentation.runOnMainSync { activity.widgetSnapshot = activity.widgetSnapshot.copy(
                        presentationState = WidgetPresentationState.SEARCHING_LYRICS) }
                    awaitContent(activity, activity.getString(R.string.widget_state_searching))
                    assertFalse(texts(activity).contains(activity.getString(R.string.widget_idle_image_description)))
                    instrumentation.runOnMainSync { activity.widgetSnapshot = LyricsWidgetSnapshot() }
                    awaitContent(activity, activity.getString(R.string.widget_idle_image_description))
                    capture(activity, "preview-$tag-idle.png")
                } finally { instrumentation.runOnMainSync { activity.finish() } }
            }
        } finally { RefinementPreviewActivity.testLanguageTag = previous }
    }

    @Test
    fun allFiveTabsRenderLocalizedLabelsAndSettingsLanguageChoices() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previous = RefinementPreviewActivity.testLanguageTag
        try {
            for (tag in listOf("en", "es")) {
                RefinementPreviewActivity.testLanguageTag = tag
                val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,
                    RefinementPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as RefinementPreviewActivity
                try {
                    for ((name, resource) in listOf("now-playing" to R.string.ui_nav_now_playing,
                        "library" to R.string.ui_nav_library, "search" to R.string.ui_nav_search,
                        "studio" to R.string.ui_nav_studio, "settings" to R.string.ui_nav_settings)) {
                        click(activity, activity.getString(resource))
                        SystemClock.sleep(300)
                        capture(activity, "$tag-$name.png")
                    }
                } finally { instrumentation.runOnMainSync { activity.finish() } }
            }
        } finally { RefinementPreviewActivity.testLanguageTag = previous }
    }

    @Test
    fun productionConfiguratorUsesTheActualSharedPlaybackSnapshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as LyricsApplication).container
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, LyricsWidgetReceiver::class.java))
        assumeTrue("No existing widget available for real-session preview", ids.isNotEmpty())
        // Instrumentation starts a fresh application process: wait for its async MediaSession /
        // database observers instead of treating the initial empty StateFlow as no playback.
        val ready = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(10_000) { container.widgetSnapshot.first {
                it.hasTrack && it.resolvedPresentationState == WidgetPresentationState.LYRICS_AVAILABLE
            } }
        }
        assumeTrue("No real song with available lyrics after observer startup", ready != null)
        val loadedConfig = kotlinx.coroutines.runBlocking { PreferencesLyricsWidgetConfigurationRepository(context).load(ids.first()) }
        val activity = instrumentation.startActivitySync(Intent(context, WidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, ids.first()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WidgetConfigurationActivity
        try {
            instrumentation.runOnMainSync {
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                }
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            click(activity, activity.getString(R.string.studio_current_line))
            val deadline = SystemClock.elapsedRealtime() + 8_000
            var matched = false
            while (!matched && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    val snapshot = container.widgetSnapshot.value
                    // A real track can change or pass through a timing gap while this test runs.
                    // Compare with the current shared state and respect the user's title fallback.
                    val expected = when (snapshot.resolvedPresentationState) {
                        WidgetPresentationState.NO_PLAYBACK -> activity.getString(R.string.widget_idle_image_description)
                        WidgetPresentationState.SEARCHING_LYRICS -> activity.getString(R.string.widget_state_searching)
                        WidgetPresentationState.LYRICS_UNAVAILABLE -> activity.getString(R.string.widget_state_unavailable)
                        WidgetPresentationState.ERROR -> activity.getString(R.string.widget_state_error)
                        WidgetPresentationState.LYRICS_AVAILABLE -> snapshot.activeLine.ifBlank {
                            if (loadedConfig.useTitleAsFallback) snapshot.fallbackLabel() else ""
                        }
                    }
                    val visible = textsOnMain(activity)
                    matched = visible.any { it == expected }
                    File(context.getExternalFilesDir(null), "widgets2-preview").apply { mkdirs() }
                        .resolve("real-session-trace.txt").writeText("state=${snapshot.resolvedPresentationState} titleFallback=${loadedConfig.useTitleAsFallback}\n" +
                            "expected=$expected\nvisible=${visible.joinToString(" | ")}\n")
                }
                if (!matched) SystemClock.sleep(100)
            }
            if (!matched) capture(activity, "real-session-failure.png")
            assertTrue("The real configurator must follow the actual widget snapshot", matched)
            assertFalse(texts(activity).any { it.contains("CONTIGO") })
            capture(activity, "real-session-preview.png")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test
    fun perAppLanguagePersistsAndSystemSelectionIsAuthoritative() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = AppLanguageManager.currentTag(context)
        try {
            for ((tag, expected) in listOf("en" to "Current Word", "es" to "Palabra actual")) {
                AppLanguageManager.apply(context, tag)
                AppLanguageManager.initialize(context)
                assertEquals(tag, AppLanguageManager.currentTag(context))
                assertEquals(expected, AppLanguageManager.localizedContext(context).getString(R.string.studio_current_word))
            }
            AppLanguageManager.apply(context, "")
            AppLanguageManager.initialize(context)
            assertEquals("", AppLanguageManager.currentTag(context))
        } finally { AppLanguageManager.apply(context, previous) }
    }

    private fun root(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) root(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun nodes(activity: ComponentActivity): List<SemanticsNode> {
        val owner = checkNotNull(root(activity.window.decorView))
        owner.measureAndLayoutForTest()
        fun collect(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::collect)
        return collect(owner.semanticsOwner.rootSemanticsNode)
    }
    private fun nodeTexts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    private fun textsOnMain(activity: ComponentActivity) = nodes(activity).flatMap(::nodeTexts)
    private fun texts(activity: ComponentActivity): List<String> {
        var result = emptyList<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = textsOnMain(activity) }
        return result
    }
    private fun awaitContent(activity: ComponentActivity, expected: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (texts(activity).contains(expected)) return
            SystemClock.sleep(100)
        }
        assertTrue("Missing content: $expected", texts(activity).contains(expected))
    }
    private fun click(activity: ComponentActivity, label: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var clicked = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                clicked = nodes(activity).firstOrNull { node -> nodeTexts(node).any { it.contains(label) } &&
                    node.config.getOrNull(SemanticsActions.OnClick)?.action != null }
                    ?.config?.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
            }
            if (clicked) return
            SystemClock.sleep(100)
        }
        fail("Missing interactive label: $label")
    }
    private fun capture(activity: ComponentActivity, name: String) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        var visible = false
        while (!visible && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                visible = activity.hasWindowFocus() && activity.window.decorView.isAttachedToWindow &&
                    activity.window.decorView.width > 0 && activity.window.decorView.height > 0
            }
            if (!visible) SystemClock.sleep(100)
        }
        check(visible) { "Capture window did not become visible" }
        SystemClock.sleep(300)
        val bitmap = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var status = -1
        PixelCopy.request(activity.window, bitmap, { status = it; latch.countDown() }, Handler(Looper.getMainLooper()))
        check(latch.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, status)
        File(activity.getExternalFilesDir(null), "widgets2-preview").apply { mkdirs() }
            .resolve(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

package xyz.tosdev.yetasimplelyicsapp

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.widget.*
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class Widgets2InstrumentedTest {
    @Test
    fun realFontWrapsBalancedWordsAndPreservesEmoji() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val measurer = currentWordTextMeasurer(context)
        val width = maxOf(measurer.measureWidthDp("QUI", 18f), measurer.measureWidthDp("ERE", 18f)) + 0.2f
        val layout = WordLineBreaker.layoutWord("QUIERE", "es", Locale.ROOT, width,
            preferredFontSizeSp = 18f, minFontSizeSp = 18f, maxLines = 2,
            textMeasurer = measurer, availableHeightDp = 90f)
        assertEquals("QUI\nERE", layout.text)
        assertEquals(listOf("👨‍👩‍👧‍👦", "👍🏽", "🇲🇽", "a\u0301"),
            WordLineBreaker.splitIntoGraphemes("👨‍👩‍👧‍👦👍🏽🇲🇽a\u0301"))
    }

    @Test
    fun remoteViewsKeepWholeWordsAtThreeSizesAndSwapIdleImageForLyrics() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, Widgets2HostActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as Widgets2HostActivity
        val output = File(context.getExternalFilesDir(null), "widgets2").apply { mkdirs() }
        try {
            for ((index, size) in listOf(96f to 88f, 180f to 128f, 300f to 180f).withIndex()) {
                for (word in listOf("QUIERE", "CUANDO", "MIENTRAS", "EXTRAORDINARIO",
                    "ELECTROENCEFALOGRAFISTA", "THROUGH", "BEAUTIFUL", "UNDERSTANDING")) {
                    val snapshot = LyricsWidgetSnapshot(trackKey = "widget-test", activeWordId = word,
                        activeWord = word, hasReliableWordSync = true, lyricsLanguageTag = "en")
                    showSnapshot(activity, snapshot, size)
                    waitForText(activity, word)
                    instrumentation.runOnMainSync {
                        val views = textViews(checkNotNull(activity.widgetView))
                        val rendered = renderWidgetWord(activity, word, "en", size.first - 8f, size.second - 8f,
                            1f, 0xfffff7ef.toInt())
                        assertEquals(word, rendered.textLayout.visualLines.joinToString(""))
                        assertEquals("StaticLayout must respect the selected cuts", rendered.textLayout.lineCount, rendered.renderedLineCount)
                        assertEquals(0, rendered.bitmap.getPixel(0, 0).ushr(24))
                        val pixels = IntArray(rendered.bitmap.width * rendered.bitmap.height)
                        rendered.bitmap.getPixels(pixels, 0, rendered.bitmap.width, 0, 0, rendered.bitmap.width, rendered.bitmap.height)
                        assertTrue("Glyphs must actually be visible", pixels.any { it.ushr(24) > 0 })
                        assertTrue("No launcher TextView may rewrap the word", views.isEmpty())
                        File(output, "final-metrics.txt").appendText("$index $word lines=${rendered.textLayout.text.replace("\n", "/")} " +
                            "font=${rendered.textLayout.fontSizeSp}sp actualLines=${rendered.renderedLineCount}\n")
                        rendered.bitmap.recycle()
                        saveView(checkNotNull(activity.widgetView), File(output, "word-$index-$word.png"))
                    }
                }
                if (index == 0) {
                    val snapshot = LyricsWidgetSnapshot(trackKey = "balanced-test", activeWordId = "quiere-large",
                        activeWord = "QUIERE", hasReliableWordSync = true, lyricsLanguageTag = "es")
                    showSnapshot(activity, snapshot, size, 2f)
                    waitForText(activity, "QUIERE")
                    val rendered = renderWidgetWord(activity, "QUIERE", "es", size.first - 8f, size.second - 8f,
                        2f, 0xfffff7ef.toInt())
                    assertEquals("QUI\nERE", rendered.textLayout.text)
                    assertEquals(2, rendered.renderedLineCount)
                    instrumentation.runOnMainSync {
                        saveView(checkNotNull(activity.widgetView), File(output, "balanced-QUIERE.png"))
                    }
                    rendered.bitmap.recycle()
                }
                showSnapshot(activity, LyricsWidgetSnapshot(), size)
                SystemClock.sleep(900)
                instrumentation.runOnMainSync {
                    assertTrue("Idle widget must contain no unnecessary text", textViews(checkNotNull(activity.widgetView)).all { it.text.isBlank() })
                    saveView(checkNotNull(activity.widgetView), File(output, "idle-$index.png"))
                }
                val searching = LyricsWidgetSnapshot(trackKey = "active-track", presentationState = WidgetPresentationState.SEARCHING_LYRICS)
                showSnapshot(activity, searching, size)
                waitForText(activity, xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager.localizedContext(context)
                    .getString(R.string.widget_state_searching))
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun photoCopyIsBoundedPrivateAndSurvivesRemovalOfThePickerSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "widget-picker-test.png")
        val bitmap = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(0x8088aacc.toInt()) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val store = WidgetIdleImageStore(context)
        var path: String? = null
        try {
            path = store.importFromPicker(android.net.Uri.fromFile(source), "test-photo")
            assertNotNull(path)
            assertTrue(File(checkNotNull(path)).canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath))
            assertTrue(source.delete())
            val loaded = WidgetIdleImageStore(context).load(path)
            assertNotNull(loaded)
            assertTrue(checkNotNull(loaded).width <= MAX_WIDGET_IMAGE_EDGE_PX)
            assertTrue(loaded.height <= MAX_WIDGET_IMAGE_EDGE_PX)
            assertTrue(loaded.getPixel(loaded.width / 2, loaded.height / 2).ushr(24) in 120..136)
            loaded.recycle()
            assertNull(store.load(File(context.cacheDir, "untrusted-path.png").path))
        } finally { store.delete(path); source.delete() }
    }

    private suspend fun showSnapshot(activity: Widgets2HostActivity, snapshot: LyricsWidgetSnapshot,
        size: Pair<Float, Float>, textScale: Float = 1f) {
        // The public Glance compose API creates a fake ID. Real MediaSessions can keep playing
        // without overwriting deterministic renderer samples or changing any launcher widget.
        val state = mutablePreferencesOf(
            stringPreferencesKey("content_mode") to LyricsWidgetContentMode.CURRENT_WORD.name,
            floatPreferencesKey("text_scale") to textScale,
            stringPreferencesKey("track_key") to snapshot.trackKey,
            stringPreferencesKey("active_word_id") to snapshot.activeWordId,
            stringPreferencesKey("active_word") to snapshot.activeWord,
            booleanPreferencesKey("reliable_word_sync") to snapshot.hasReliableWordSync,
            stringPreferencesKey("lyrics_language_tag") to snapshot.lyricsLanguageTag,
            stringPreferencesKey("presentation_state") to snapshot.presentationState.name,
        )
        val remote = LyricsWidget().compose(activity, size = DpSize(size.first.dp, size.second.dp), state = state)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.showRemoteViews(remote, size.first, size.second)
        }
    }

    private fun descriptions(view: View): List<String> = listOfNotNull(view.contentDescription?.toString()) +
        (if (view is ViewGroup) (0 until view.childCount).flatMap { descriptions(view.getChildAt(it)) } else emptyList())

    private fun textViews(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun waitForText(activity: Widgets2HostActivity, expected: String) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        var visible = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            var measured = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val root = checkNotNull(activity.widgetView)
                measured = root.width > 0 && root.height > 0 && root.isLaidOut
                visible = textViews(root).joinToString("") { it.text.toString() }.replace("\n", "")
                if (visible.isBlank()) visible = descriptions(root).firstOrNull { it == expected }.orEmpty()
            }
            if (visible == expected && measured) return
            SystemClock.sleep(100)
        }
        assertEquals("Widget host did not receive expected contents", expected, visible)
    }

    private fun saveView(view: View, file: File) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

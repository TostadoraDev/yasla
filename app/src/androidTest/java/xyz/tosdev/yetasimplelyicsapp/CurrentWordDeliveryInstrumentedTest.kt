package xyz.tosdev.yetasimplelyicsapp

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.widget.*

@RunWith(AndroidJUnit4::class)
class CurrentWordDeliveryInstrumentedTest {
    @Test fun fastDeliveryPreservesTheExactGlyphsOfTheGlanceRendererAtEveryWidgetSize() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_WORD)
        for (size in listOf(DpSize(96.dp, 88.dp), DpSize(180.dp, 128.dp), DpSize(300.dp, 180.dp))) {
            for (word in listOf("CUANDO", "QUIERE", "MIENTRAS", "INTERNACIONALIZACION", "ELECTROENCEFALOGRAFISTA")) {
                val snapshot = LyricsWidgetSnapshot(trackKey = "test", activeWordId = word, activeWord = word,
                    hasReliableWordSync = true, lyricsLanguageTag = "es", syncType = WidgetLyricsSyncType.WORD)
                val native = currentWordRemoteViews(context, config, snapshot, listOf(size))!!
                val glance = LyricsWidget().compose(context, size = size, state = preferencesOf(
                    stringPreferencesKey("track_key") to "test",
                    stringPreferencesKey("active_word") to word,
                    stringPreferencesKey("active_word_id") to word,
                    stringPreferencesKey("content_mode") to LyricsWidgetContentMode.CURRENT_WORD.name,
                    stringPreferencesKey("lyrics_language_tag") to "es",
                    booleanPreferencesKey("reliable_word_sync") to true,
                    stringPreferencesKey("sync_type") to WidgetLyricsSyncType.WORD.name,
                ))
                instrumentation.runOnMainSync {
                    val first = image(native.apply(context, FrameLayout(context)))!!
                    val second = image(glance.apply(context, FrameLayout(context)))!!
                    assertEquals(word, first.contentDescription.toString())
                    val firstBitmap = (first.drawable as BitmapDrawable).bitmap
                    val secondBitmap = (second.drawable as BitmapDrawable).bitmap
                    assertTrue("Visual parity for $word at $size", firstBitmap.sameAs(secondBitmap))
                    assertEquals(0, android.graphics.Color.alpha(firstBitmap.getPixel(0, 0)))
                }
            }
        }
    }

    private fun image(view: View): ImageView? {
        if (view is ImageView && view.contentDescription != null) return view
        if (view is ViewGroup) repeat(view.childCount) { index ->
            image(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}

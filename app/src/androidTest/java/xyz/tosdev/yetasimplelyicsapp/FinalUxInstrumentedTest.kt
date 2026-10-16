package xyz.tosdev.yetasimplelyicsapp

import android.content.Intent
import android.graphics.Rect
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.data.local.*
import xyz.tosdev.yetasimplelyicsapp.data.preferences.VisualPreferences
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.*
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import xyz.tosdev.yetasimplelyicsapp.overlay.LyricsOverlayController
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsDisplayMode
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel

/** Exercises the actual Activity, accessibility actions, measured text, and shared media owner. */
@RunWith(AndroidJUnit4::class)
class FinalUxInstrumentedTest {
    @Test fun fullscreenAndSizePreservePlaybackAndCanBeReversed() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val wideOnly = InstrumentationRegistry.getArguments().getString("wideOnly") == "true"
        val context = instrumentation.targetContext
        val container = (context.applicationContext as LyricsApplication).container
        val original = VisualPreferences(context).load()
        val overlayWasEnabled = LyricsOverlayController.isPersistentEnabled(context)
        val overlayConfig = LyricsOverlayController.loadConfig(context)
        val language = AppLanguageManager.currentTag(context)
        val phrase = "Every beautiful word remains readable across this long lyric line"
        val mediaId = "ux-owned-fixture"
        val trackKey = "${context.packageName}:$mediaId"
        val document = LrcLyricsParser().parse("[00:01.00]$phrase\n[00:08.00]The following line stays aligned",
            LyricsParseContext("ux-proof", trackKey, LyricsSource("local"))).document!!
        val dao = LyricsDatabase.getInstance(context).lyricsDao()
        RoomLyricsStore(dao).save(document, System.currentTimeMillis(), Long.MAX_VALUE,
            LyricsTrackMetadata(title = "A deliberately long title to verify measured overflow without changing the header",
                artists = listOf("An independently long artist name to read through the complete metadata"), durationMs = 20000))
        var activity: MainActivity? = null
        var session: MediaSession? = null
        val proof = File(context.getExternalFilesDir(null), if (wideOnly) "final-ux-proof-wide" else "final-ux-proof").apply { mkdirs() }
        fun node(label: String): AccessibilityNodeInfo? {
            fun visit(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (n == null) return null
                if (n.isVisibleToUser && (n.text?.toString() == label || n.contentDescription?.toString() == label)) return n
                for (i in 0 until n.childCount) visit(n.getChild(i))?.let { return it }
                return null
            }
            return visit(instrumentation.uiAutomation.rootInActiveWindow)
        }
        suspend fun await(label: String): AccessibilityNodeInfo = withTimeout(15000) {
            var found = node(label)
            while (found == null) { delay(50); found = node(label) }
            found
        }
        fun action(label: String): AccessibilityNodeInfo? {
            fun visit(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (n == null) return null
                if (n.isVisibleToUser && (n.text?.toString() == label || n.contentDescription?.toString() == label)) {
                    var parent: AccessibilityNodeInfo? = n
                    while (parent != null && !parent.isClickable) parent = parent.parent
                    if (parent != null) return parent
                }
                for (i in 0 until n.childCount) visit(n.getChild(i))?.let { return it }
                return null
            }
            return visit(instrumentation.uiAutomation.rootInActiveWindow)
        }
        suspend fun click(label: String) {
            android.util.Log.i("FinalUxProof", "click=$label")
            val button = withTimeout(15000) {
                var found = action(label)
                while (found == null) { delay(50); found = action(label) }
                found
            }
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        suspend fun scrollTo(label: String, needsAction: Boolean = true) {
            fun scrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (n == null) return null
                if (n.isScrollable) return n
                for (i in 0 until n.childCount) scrollable(n.getChild(i))?.let { return it }
                return null
            }
            withTimeout(12000) {
                while (if (needsAction) action(label) == null else node(label) == null) {
                    val view = scrollable(instrumentation.uiAutomation.rootInActiveWindow)
                    // A dismissing platform dialog can remain the active window for a frame.
                    view?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    delay(250)
                }
            }
        }
        fun screenshot(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(bitmap)
            File(proof, "$name.png").outputStream().use {
                bitmap!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap?.recycle()
        }
        try {
            // Stop directly: reconfiguring an enabled service and immediately stopping it can
            // interrupt its foreground startup. The renderer itself is outside this test's scope.
            LyricsOverlayController.stop(context)
            AppLanguageManager.apply(context, "en")
            instrumentation.runOnMainSync {
                android.util.Log.i("FinalUxProof", "isolatedOverlayPreviouslyEnabled=$overlayWasEnabled")
                container.settings(original.copy(lyricsDisplayMode = LyricsDisplayMode.FULL_LYRICS,
                    lyricsTextScale = 1f, keepScreenAwake = true, motionLevel = MotionLevel.NORMAL))
                container.refreshLocalizedSurfaces()
                session = MediaSession(context, "YASLA-owned-ux-proof").apply {
                    setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,
                        "A deliberately long title to verify measured overflow without changing the header")
                        .putString(MediaMetadata.METADATA_KEY_ARTIST,
                            "An independently long artist name to read through the complete metadata")
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, 20000).build())
                    isActive = true
                    setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,
                        3000, 0f, SystemClock.elapsedRealtime()).build())
                }
            }
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            withTimeout(15000) { while (container.widgetSnapshot.value.trackKey != trackKey ||
                container.state.value.nowPlaying.lyrics.none { it.plainText == phrase }) delay(50) }
            val enter = context.getString(R.string.ux_immersive_enter_lyrics)
            val exit = context.getString(R.string.ux_immersive_exit_lyrics)
            await(enter)
            delay(1500)
            screenshot("normal")
            val normalBounds = Rect().also { await(phrase).getBoundsInScreen(it) }
            val playbackBefore = container.media.state.value.active!!.playback
            click(enter)
            await(exit)
            delay(1000)
            assertNull("Navigation must disappear", node(context.getString(R.string.ui_nav_settings)))
            assertNull("Header must disappear", node(context.getString(R.string.ux_marquee_full_text,
                "A deliberately long title to verify measured overflow without changing the header")))
            await(phrase)
            if (wideOnly) {
                val immersiveBounds = Rect().also { await(phrase).getBoundsInScreen(it) }
                assertTrue("Immersive wide lyrics must reclaim the media panel and rail width",
                    immersiveBounds.width() > normalBounds.width())
                android.util.Log.i("FinalUxProof", "wideNormal=$normalBounds wideFullscreen=$immersiveBounds")
            }
            assertEquals(playbackBefore.trackKey, container.media.state.value.active!!.playback.trackKey)
            assertEquals(playbackBefore.reportedPositionMs, container.media.state.value.active!!.playback.reportedPositionMs)
            instrumentation.runOnMainSync { assertTrue(activity!!.window.attributes.flags and
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0) }
            screenshot("fullscreen")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await(enter)
            if (wideOnly) {
                delay(1000)
                val restored = Rect().also { val n = await(phrase); n.refresh(); n.getBoundsInScreen(it) }
                android.util.Log.i("FinalUxProof", "wideRestored=$restored original=$normalBounds")
                screenshot("restored")
                assertTrue("Wide layout must restore its media panel", restored.width() <= normalBounds.width() + 8)
                return@runBlocking
            }
            await(context.getString(R.string.ui_nav_settings))

            instrumentation.runOnMainSync { container.settings(container.state.value.settings.copy(lyricsTextScale = .75f)) }
            delay(1000)
            val small = Rect().also { val n = await(phrase); n.refresh(); n.getBoundsInScreen(it) }
            screenshot("small")
            instrumentation.runOnMainSync { container.settings(container.state.value.settings.copy(lyricsTextScale = 1.5f)) }
            delay(1500)
            val large = Rect().also { val n = await(phrase); n.refresh(); n.getBoundsInScreen(it) }
            android.util.Log.i("FinalUxProof", "small=$small large=$large stateScale=${container.state.value.settings.lyricsTextScale}")
            screenshot("large")
            assertTrue("Text must be remeasured, not just painted larger", large.height() > small.height())
            assertEquals(1.5f, VisualPreferences(context).load().lyricsTextScale, 0f)
            click(context.getString(R.string.ui_nav_settings))
            await(context.getString(R.string.ui_settings_description))
            delay(600)
            screenshot("settings")
            click(context.getString(R.string.ui_settings_theme))
            await(context.getString(R.string.ui_light))
            await(context.getString(R.string.ui_dark))
            click(context.getString(R.string.ux_settings_cancel))
            await(context.getString(R.string.ui_settings_description))
            scrollTo(context.getString(R.string.ui_settings_language))
            click(context.getString(R.string.ui_settings_language))
            click(context.getString(R.string.ui_spanish))
            val spanish = context.createConfigurationContext(android.content.res.Configuration(
                context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("es")) })
            // Recreating for locale restores the current scroll position; the top description
            // is correctly off-screen while the language row and navigation remain visible.
            await(spanish.getString(R.string.ui_nav_settings))
            assertEquals("es", AppLanguageManager.currentTag(context))
            screenshot("settings-spanish")
            val creditsLabel = spanish.getString(R.string.ux_settings_credits)
            scrollTo(creditsLabel, needsAction = false)
            await(creditsLabel).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            delay(250)
            val creditsBounds = Rect().also { await(creditsLabel).getBoundsInScreen(it) }
            screenshot("settings-about-spanish")
            assertTrue("Credits must keep a readable title instead of a vertical character strip",
                creditsBounds.width() > creditsBounds.height() * 2)
        } finally {
            instrumentation.runOnMainSync {
                if (overlayWasEnabled) LyricsOverlayController.start(context, overlayConfig)
                session?.release()
                container.settings(original)
            }
            // Let a restored foreground service establish its notification before Activity exit.
            if (overlayWasEnabled) delay(1000)
            instrumentation.runOnMainSync { activity?.finish() }
            AppLanguageManager.apply(context, language)
            instrumentation.runOnMainSync { container.refreshLocalizedSurfaces() }
            dao.deleteByTrackKey(trackKey)
        }
    }
}

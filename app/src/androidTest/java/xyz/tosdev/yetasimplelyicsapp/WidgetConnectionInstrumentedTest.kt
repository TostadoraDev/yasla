package xyz.tosdev.yetasimplelyicsapp

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.data.local.*
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.LyricsParseContext
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.TtmlLyricsParser
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import xyz.tosdev.yetasimplelyicsapp.media.MediaAccessState
import xyz.tosdev.yetasimplelyicsapp.media.MediaNotificationListenerService
import xyz.tosdev.yetasimplelyicsapp.widget.*
import java.io.File
import java.io.FileInputStream

/** Synthetic owned sessions only. Never sends transport commands to someone else's player. */
@RunWith(AndroidJUnit4::class)
class WidgetConnectionInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as LyricsApplication).container
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    }
    private suspend fun waitUntil(message: String, predicate: () -> Boolean) = withTimeout(15_000) {
        while (!predicate()) delay(25)
        assertTrue(message, predicate())
    }
    private fun sample(session: MediaSession, state: Int, position: Long, speed: Float = 1f) {
        instrumentation.runOnMainSync {
            session.setPlaybackState(PlaybackState.Builder().setActions(
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO
            ).setState(state, position, speed, SystemClock.elapsedRealtime()).build())
        }
    }
    private fun newSession(mediaId: String) = MediaSession(context, "YASLA-owned-test").apply {
        setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Synthetic widget test")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "YASLA tests")
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 900_000).build())
        isActive = true
    }

    @Test fun releasedSessionReconnectAndPermissionChangesDoNotDuplicateOrRetainCallbacks() = runBlocking {
        assertTrue("Listener permission required", container.media.hasNotificationAccess())
        var owned: MediaSession? = null
        val component = ComponentName(context, MediaNotificationListenerService::class.java).flattenToString()
        try {
            instrumentation.runOnMainSync { owned = newSession("connection-test") }
            sample(owned!!, PlaybackState.STATE_PLAYING, 1_000)
            waitUntil("Owned session connected") { container.media.state.value.active?.track?.mediaId == "connection-test" }
            val firstId = container.media.state.value.active!!.sessionId
            repeat(5) { container.media.onNotificationListenerConnected() }
            delay(250)
            instrumentation.runOnMainSync { assertEquals(1, container.media.debugState().controllerCallbacksAlive) }
            instrumentation.runOnMainSync { owned!!.release(); owned = newSession("connection-test") }
            sample(owned!!, PlaybackState.STATE_PAUSED, 7_000)
            waitUntil("Replacement token recovered") {
                container.media.state.value.active?.let { it.sessionId != firstId && it.playback.status == PlaybackStatus.PAUSED } == true
            }
            assertEquals(7_000L, container.media.state.value.active!!.playback.estimatedPositionAt(SystemClock.elapsedRealtime()))
            shell("cmd notification disallow_listener $component")
            waitUntil("Revocation propagated") { container.media.state.value.access == MediaAccessState.PERMISSION_REQUIRED }
            instrumentation.runOnMainSync {
                assertEquals(0, container.media.debugState().controllerCallbacksAlive)
                assertFalse(container.media.debugState().activeSessionsListenerAlive)
            }
            shell("cmd notification allow_listener $component")
            waitUntil("Grant recovered without Activity") { container.media.state.value.active?.track?.mediaId == "connection-test" }
            sample(owned!!, PlaybackState.STATE_PLAYING, 500)
            waitUntil("Seek and resume sampled") {
                container.media.state.value.active?.playback?.let {
                    it.status == PlaybackStatus.PLAYING && it.reportedPositionMs in 500L..2_500L
                } == true
            }
            instrumentation.runOnMainSync { assertEquals(1, container.media.debugState().controllerCallbacksAlive) }
        } finally {
            shell("cmd notification allow_listener $component")
            instrumentation.runOnMainSync { owned?.release() }
        }
    }

    @Test fun backgroundSemanticUpdatesReachARealHostWithoutAWordBacklog() = runBlocking {
        assertTrue(container.media.hasNotificationAccess())
        val mediaId = "semantic-widget-soak"
        val trackKey = "${context.packageName}:$mediaId"
        val dao = LyricsDatabase.getInstance(context).lyricsDao()
        val wordCases = listOf(listOf("ex", "tra", "or", "di", "na", "rio"), listOf("cuan", "do"),
            listOf("qui", "e", "re"), listOf("mien", "tras"), listOf("te"), listOf("qui", "ero"))
        val completeWords = wordCases.map { it.joinToString("") }.toSet()
        val spans = buildString {
            repeat(450) { index ->
                val pieces = wordCases[index % wordCases.size]
                pieces.forEachIndexed { part, text ->
                    append("<span begin=\"${index * 2000 + part * 1500 / pieces.size}ms\" end=\"${index * 2000 + (part + 1) * 1500 / pieces.size}ms\">$text</span>")
                }
                append(" ")
            }
        }
        val parsed = TtmlLyricsParser().parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word" xml:lang="es"><body><div><p begin="0ms" end="900000ms">$spans</p></div></body></tt>""",
            LyricsParseContext("widget-soak-doc", trackKey, LyricsSource("local"))).document!!
        assertEquals(450, parsed.lines.single().words.size)
        assertEquals(6, parsed.lines.single().words.first().segments.size)
        RoomLyricsStore(dao).save(parsed, System.currentTimeMillis(), Long.MAX_VALUE,
            LyricsTrackMetadata(title = "Synthetic widget test", artists = listOf("YASLA tests"), durationMs = 900_000))
        var owned: MediaSession? = null
        var host: AppWidgetHost? = null
        var activity: Widgets2HostActivity? = null
        var id = -1
        var received = 0
        var lastReceived = 0L
        val delays = mutableListOf<Long>()
        val submittedWords = mutableSetOf<Int>()
        val visibleWords = mutableSetOf<String>()
        try {
            activity = instrumentation.startActivitySync(Intent(context, Widgets2HostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as Widgets2HostActivity
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            instrumentation.runOnMainSync {
                host = object : AppWidgetHost(context, 130013) {
                    override fun onCreateView(context: android.content.Context, appWidgetId: Int,
                        appWidget: android.appwidget.AppWidgetProviderInfo): AppWidgetHostView =
                        object : AppWidgetHostView(context) {
                            override fun updateAppWidget(remoteViews: RemoteViews?) {
                                super.updateAppWidget(remoteViews)
                                received++
                                lastReceived = SystemClock.elapsedRealtime()
                            }
                        }
                }
                id = host!!.allocateAppWidgetId()
                assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id,
                    ComponentName(context, LyricsWidgetReceiver::class.java), Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 96)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 96)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 88)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 88)
                        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(96f, 88f)))
                    }))
                host!!.startListening()
                val view = host!!.createView(context, id, AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
                activity!!.showWidgetView(view, 96f, 88f)
                owned = newSession(mediaId)
            }
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
            LyricsWidgetUpdater.updateConfiguration(context, id, LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_WORD))
            sample(owned!!, PlaybackState.STATE_PLAYING, 0)
            waitUntil("Semantic word loaded") { container.widgetSnapshot.value.activeWord == "extraordinario" }
            instrumentation.runOnMainSync { container.setForeground(false) }
            val start = SystemClock.elapsedRealtime()
            val cpuStart = Process.getElapsedCpuTime()
            val seconds = InstrumentationRegistry.getArguments().getString("soakSeconds")?.toLong() ?: 20L
            var captureDone = false
            var homeShown = false
            var nextProgress = 60_000L
            while (SystemClock.elapsedRealtime() - start < seconds * 1_000) {
                val now = SystemClock.elapsedRealtime()
                val snapshot = container.widgetSnapshot.value
                assertTrue("Only complete semantic words: ${snapshot.activeWord}", snapshot.activeWord in completeWords)
                // AppWidgetHostView describes the provider itself; read the actual glyph view,
                // otherwise its accessibility label is concatenated with the semantic word.
                val visible = withContext(Dispatchers.Main) {
                    activity!!.widgetView!!.findViewById<android.widget.ImageView>(R.id.widget_word_glyphs)
                        ?.contentDescription?.toString().orEmpty()
                }
                completeWords.firstOrNull { visible == it.uppercase(java.util.Locale.forLanguageTag("es")) }
                    ?.let(visibleWords::add)
                val trace = WidgetDeliveryDiagnostics.lastSubmission.value
                if (trace?.appWidgetId == id && trace.wordIdHash != 0 && submittedWords.add(trace.wordIdHash)) {
                    delays += trace.submittedAtElapsedMs - trace.sampleAtElapsedMs
                    assertTrue("Stale Binder submission", now - trace.submittedAtElapsedMs < 4_000)
                }
                val hostHasWord = if (!captureDone) withContext(Dispatchers.Main) {
                    activity!!.widgetView?.let { it.width > 0 && it.height > 0 && descriptions(it).contains("EXTRAORDINARIO") } == true
                } else false
                if (!captureDone && hostHasWord) {
                    instrumentation.runOnMainSync {
                        val view = activity!!.widgetView!!
                        assertTrue("Complete semantic word must reach host", descriptions(view).contains("EXTRAORDINARIO"))
                        view.measure(View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY))
                        view.layout(view.left, view.top, view.left + view.measuredWidth, view.top + view.measuredHeight)
                        val bitmap = android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                        view.draw(android.graphics.Canvas(bitmap))
                        val pixels = IntArray(bitmap.width * bitmap.height)
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        assertTrue("Glyphs must actually be drawn", pixels.count { android.graphics.Color.alpha(it) > 0 } > 32)
                        assertEquals("Transparent corner", 0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                        File(context.cacheDir, "widget-sync-word.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    captureDone = true
                }
                if (captureDone && !homeShown) {
                    shell("input keyevent KEYCODE_HOME")
                    homeShown = true
                }
                val elapsed = SystemClock.elapsedRealtime() - start
                if (elapsed >= nextProgress) {
                    Log.i("WidgetSyncProof", "progressSeconds=${elapsed / 1000} semanticWords=${submittedWords.size} visibleWords=${visibleWords.size} hostUpdates=$received")
                    nextProgress += 60_000L
                }
                delay(100)
            }
            assertTrue("Semantic timer must keep advancing", submittedWords.size >= seconds / 3)
            assertTrue("Actual host still receives", SystemClock.elapsedRealtime() - lastReceived < 4_000)
            assertTrue("The displayed word must change too", visibleWords.size >= minOf(4, completeWords.size))
            // Snap boundaries must not cause six publications per word.
            assertTrue("Too many host updates: $received", received < seconds * 2 + 15)
            val ordered = delays.sorted()
            Log.i("WidgetSyncProof", "seconds=$seconds semanticWords=${submittedWords.size} visibleWords=${visibleWords.size} hostUpdates=$received " +
                "internalP50Ms=${ordered.getOrNull(ordered.size / 2)} internalMaxMs=${ordered.lastOrNull()} " +
                "cpuMs=${Process.getElapsedCpuTime() - cpuStart} wallMs=${SystemClock.elapsedRealtime() - start}")
            sample(owned!!, PlaybackState.STATE_PAUSED, 5_000)
            waitUntil("Pause freezes") { !container.widgetSnapshot.value.isPlaying }
            val pausedId = container.widgetSnapshot.value.activeWordId
            delay(600)
            assertEquals(pausedId, container.widgetSnapshot.value.activeWordId)
            val initialRasterWidth = withContext(Dispatchers.Main) {
                val image = activity!!.widgetView!!.findViewById<android.widget.ImageView>(R.id.widget_word_glyphs)
                (image.drawable as android.graphics.drawable.BitmapDrawable).bitmap.width
            }
            // Both widths remain COMPACT: only an explicit geometry refresh can update a paused
            // widget whose semantic word and render signature did not change.
            val platform = AppWidgetManager.getInstance(context)
            platform.updateAppWidgetOptions(id, platform.getAppWidgetOptions(id).apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 120)
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(120f, 88f)))
            })
            withTimeout(15_000) {
                while (!withContext(Dispatchers.Main) {
                    val image = activity!!.widgetView!!.findViewById<android.widget.ImageView>(R.id.widget_word_glyphs)
                    (image.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.width
                        ?.let { it > initialRasterWidth } == true
                }) delay(25)
            }
            assertEquals("Resize must not change the paused semantic word", pausedId, container.widgetSnapshot.value.activeWordId)
            sample(owned!!, PlaybackState.STATE_PLAYING, 0)
            waitUntil("Backward seek replaces current word") { container.widgetSnapshot.value.activeWordId != pausedId }
        } finally {
            instrumentation.runOnMainSync {
                owned?.release()
                if (id >= 0) host?.deleteAppWidgetId(id)
                host?.stopListening()
                activity?.finish()
            }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            dao.deleteByTrackKey(trackKey)
        }
    }
    private fun descriptions(view: View): String = buildString {
        view.contentDescription?.let { append(it) }
        if (view is ViewGroup) repeat(view.childCount) { append(descriptions(view.getChildAt(it))) }
    }
}

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
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.data.local.*
import xyz.tosdev.yetasimplelyicsapp.data.preferences.AppPreferences
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser.*
import xyz.tosdev.yetasimplelyicsapp.widget.*

/** Measures the real production path; this host is not evidence of a third-party launcher's FPS. */
@RunWith(AndroidJUnit4::class)
class RapidWidgetInstrumentedTest {
    @Test fun measures100To500MillisecondWordsWithoutReplayingABacklog() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val container = (context.applicationContext as LyricsApplication).container
        assertTrue(container.media.hasNotificationAccess())
        val intervals = listOf(100L, 150L, 200L, 300L, 500L)
        val terms = listOf("HELLO", "EVERYBODY", "HOW", "ARE", "YOU")
        val starts = mutableListOf<Long>()
        val textByPhase = mutableListOf<List<String>>()
        var end = 0L
        val xml = buildString {
            intervals.forEachIndexed { phase, interval ->
                val start = end + 1_000
                starts += start
                val words = (0 until 30).map { terms[it % terms.size] + phase + it }
                textByPhase += words
                words.forEachIndexed { index, word ->
                    append("<span begin=\"${start + index * interval}ms\" end=\"${start + (index + 1) * interval}ms\">$word</span> ")
                }
                end = start + words.size * interval
            }
        }
        val mediaId = "rapid-widget-benchmark"
        val trackKey = "${context.packageName}:$mediaId"
        // Isolate delivery latency from the production AMLL offset policy.
        val preferences = AppPreferences(context)
        preferences.setLyricsOffset(trackKey, 0)
        val document = TtmlLyricsParser().parse("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word" xml:lang="en"><body><div><p begin="0ms" end="${end + 1000}ms">$xml</p></div></body></tt>""",
            LyricsParseContext("rapid-benchmark", trackKey, LyricsSource("local"))).document!!
        assertEquals(textByPhase.flatten(), document.lines.single().words.map { it.text })
        val dao = LyricsDatabase.getInstance(context).lyricsDao()
        RoomLyricsStore(dao).save(document, System.currentTimeMillis(), Long.MAX_VALUE,
            LyricsTrackMetadata(title = "Fast word benchmark", artists = listOf("YASLA tests"), durationMs = end + 1000))
        var session: MediaSession? = null
        var host: AppWidgetHost? = null
        var activity: Widgets2HostActivity? = null
        var id = -1
        var measuring = -1
        val received = intervals.map { linkedSetOf<String>() }
        val drawn = intervals.map { linkedSetOf<String>() }
        val latencies = intervals.map { mutableListOf<Long>() }
        val submitted = intervals.map { linkedSetOf<Int>() }
        val wordHashes = document.lines.single().words.chunked(30).map { words -> words.map { it.id.hashCode() }.toSet() }
        try {
            activity = instrumentation.startActivitySync(Intent(context, Widgets2HostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as Widgets2HostActivity
            instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
            instrumentation.runOnMainSync {
                host = object : AppWidgetHost(context, 130014) {
                    override fun onCreateView(context: android.content.Context, appWidgetId: Int,
                        appWidget: android.appwidget.AppWidgetProviderInfo): AppWidgetHostView = object : AppWidgetHostView(context) {
                        override fun updateAppWidget(remoteViews: RemoteViews?) {
                            super.updateAppWidget(remoteViews)
                            if (measuring >= 0) {
                                findViewById<ImageView>(R.id.widget_word_glyphs)?.contentDescription?.toString()
                                    ?.takeIf { it in textByPhase[measuring] }?.let(received[measuring]::add)
                            }
                        }
                    }
                }
                id = host!!.allocateAppWidgetId()
                assertTrue(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id,
                    ComponentName(context, LyricsWidgetReceiver::class.java), Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 128)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 128)
                        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(180f, 128f)))
                    }))
                host!!.startListening()
                val view = host!!.createView(context, id, AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
                activity!!.showWidgetView(view, 180f, 128f)
                view.viewTreeObserver.addOnDrawListener {
                    if (measuring >= 0) view.findViewById<ImageView>(R.id.widget_word_glyphs)
                        ?.contentDescription?.toString()?.takeIf { it in textByPhase[measuring] }
                        ?.let(drawn[measuring]::add)
                }
                session = MediaSession(context, "YASLA-fast-owned-test").apply {
                    setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Fast word benchmark")
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, "YASLA tests")
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, end + 1000).build())
                    isActive = true
                    setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f,
                        SystemClock.elapsedRealtime()).build())
                }
                container.setForeground(false)
            }
            instrumentation.runOnMainSync {
                session!!.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,
                    starts[0], 0f, SystemClock.elapsedRealtime()).build())
            }
            withTimeout(15_000) {
                while (container.widgetSnapshot.value.trackKey != trackKey ||
                    container.widgetSnapshot.value.activeWord != textByPhase[0][0]) delay(25)
            }
            LyricsWidgetUpdater.updateConfiguration(context, id, LyricsWidgetConfig(contentMode = LyricsWidgetContentMode.CURRENT_WORD))
            for ((phase, interval) in intervals.withIndex()) {
                instrumentation.runOnMainSync {
                    measuring = phase
                    session!!.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,
                        starts[phase], 1f, SystemClock.elapsedRealtime()).build())
                }
                val deadline = SystemClock.elapsedRealtime() + 30 * interval + 300
                while (SystemClock.elapsedRealtime() < deadline) {
                    val trace = WidgetDeliveryDiagnostics.lastSubmission.value
                    if (trace?.appWidgetId == id && trace.wordIdHash in wordHashes[phase] &&
                        submitted[phase].add(trace.wordIdHash)) latencies[phase] += trace.submittedAtElapsedMs - trace.sampleAtElapsedMs
                    delay(5)
                }
                instrumentation.runOnMainSync { measuring = -1 }
                val sorted = latencies[phase].sorted()
                Log.i("RapidWidgetProof", "intervalMs=$interval expected=30 submitted=${submitted[phase].size} " +
                    "hostReceived=${received[phase].size} hostDrawn=${drawn[phase].size} " +
                    "internalP50Ms=${sorted.getOrNull(sorted.size / 2)} internalMaxMs=${sorted.lastOrNull()}")
                assertTrue("No fragments or stale words", received[phase].all { it in textByPhase[phase] })
            }
            assertTrue("No interval delivered anything", received.sumOf { it.size } > 0)
            instrumentation.runOnMainSync {
                session!!.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,
                    starts[0] + 25, 0f, SystemClock.elapsedRealtime()).build())
            }
            withTimeout(10_000) { while (container.widgetSnapshot.value.activeWord != textByPhase[0][0]) delay(25) }
            delay(500)
            assertEquals(textByPhase[0][0], container.widgetSnapshot.value.activeWord)
        } finally {
            instrumentation.runOnMainSync {
                measuring = -1
                session?.release()
                if (id >= 0) host?.deleteAppWidgetId(id)
                host?.stopListening()
                activity?.finish()
            }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            dao.deleteByTrackKey(trackKey)
            preferences.clearLyricsOffset(trackKey)
        }
    }
}

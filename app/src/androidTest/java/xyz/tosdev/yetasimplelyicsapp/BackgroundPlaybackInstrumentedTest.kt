package xyz.tosdev.yetasimplelyicsapp

import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import xyz.tosdev.yetasimplelyicsapp.media.MediaPlaybackMonitor
import xyz.tosdev.yetasimplelyicsapp.media.PlaybackMonitorDebugState

/** Requires listener access. Never pauses or controls an existing music player. */
@RunWith(AndroidJUnit4::class)
class BackgroundPlaybackInstrumentedTest {
    @Test fun openingLauncherKeepsPlaybackMonitorAndControllerCallbackAlive() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitor = MediaPlaybackMonitor.get(context)
        assertTrue("Enable listener access on the test emulator", monitor.hasNotificationAccess())
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var ownedSession: MediaSession? = null
        try {
            instrumentation.runOnMainSync {
                ownedSession = MediaSession(context, "lyrics-background-test").apply {
                    setMetadata(MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, "Before background")
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, "Instrumented test")
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "background-test-track")
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, 60_000L).build())
                    setPlaybackState(PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING, 1_000L, 1f).build())
                    isActive = true
                }
                monitor.refreshSessions()
            }
            withTimeout(15_000L) {
                monitor.state.first {
                    it.active?.let { active ->
                        active.track.title == "Before background" &&
                            active.playback.status == PlaybackStatus.PLAYING
                    } == true
                }
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {
                java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
            }
            instrumentation.waitForIdleSync()
            withTimeout(10000L) {
                while (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
                }) kotlinx.coroutines.delay(100L)
            }
            val stoppedStatus = arrayOfNulls<PlaybackMonitorDebugState>(1)
            instrumentation.runOnMainSync { stoppedStatus[0] = monitor.debugState() }
            assertTrue(stoppedStatus[0]!!.activeSessionsListenerAlive)
            assertTrue(stoppedStatus[0]!!.controllerCallbacksAlive > 0)

            instrumentation.runOnMainSync {
                checkNotNull(ownedSession).setMetadata(MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "After background")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Instrumented test")
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "background-test-track")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 60_000L).build())
            }
            val after = withTimeout(15_000L) {
                monitor.state.first {
                    it.active?.track?.title == "After background"
                }
            }
            assertEquals("After background", after.active?.track?.title)
            assertEquals(PlaybackStatus.PLAYING, after.active?.playback?.status)
        } finally {
            instrumentation.runOnMainSync {
                ownedSession?.release()
                activity.finish()
            }
        }
    }
}

package xyz.tosdev.yetasimplelyicsapp.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import xyz.tosdev.yetasimplelyicsapp.media.MediaPlaybackMonitor
import xyz.tosdev.yetasimplelyicsapp.media.TransportCommand

class LyricsNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LyricsNotificationCoordinator.ACTION_TRANSPORT -> {
                val command =
                    intent.getStringExtra(LyricsNotificationCoordinator.EXTRA_COMMAND)
                        ?.let { runCatching { TransportCommand.valueOf(it) }.getOrNull() }
                        ?: return
                MediaPlaybackMonitor.get(context).dispatch(command)
            }

            LyricsNotificationCoordinator.ACTION_SURFACE -> {
                val command =
                    intent.getStringExtra(LyricsNotificationCoordinator.EXTRA_SURFACE_COMMAND)
                        ?.let { runCatching { LyricsSurfaceCommand.valueOf(it) }.getOrNull() }
                        ?: return
                LyricsSurfaceActionBridge.dispatch(command)
            }
        }
    }
}

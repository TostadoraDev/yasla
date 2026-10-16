package xyz.tosdev.yetasimplelyicsapp.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import xyz.tosdev.yetasimplelyicsapp.R
import xyz.tosdev.yetasimplelyicsapp.localization.AppLanguageManager
import xyz.tosdev.yetasimplelyicsapp.media.ActivePlayback
import xyz.tosdev.yetasimplelyicsapp.media.TransportCommand

data class NotificationLyrics(
    val currentLine: String?,
    val nextLine: String?,
)

sealed interface NotificationPublishResult {
    data object Shown : NotificationPublishResult
    data object Cleared : NotificationPublishResult
    data object PermissionRequired : NotificationPublishResult
}

class LyricsNotificationCoordinator(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val manager = applicationContext.getSystemService(NotificationManager::class.java)

    private var lastRenderKey: List<Any?>? = null
    private var cleared = false

    init {
        ensureChannels()
    }

    @SuppressLint("MissingPermission")
    fun publish(
        lyrics: NotificationLyrics,
        playback: ActivePlayback?,
    ): NotificationPublishResult {
        if (lyrics.currentLine.isNullOrBlank() && playback == null) {
            clear()
            return NotificationPublishResult.Cleared
        }
        if (!canPostNotifications()) {
            return NotificationPublishResult.PermissionRequired
        }

        val renderKey = listOf(
            lyrics, playback?.sessionId, playback?.track?.title, playback?.track?.artist,
            playback?.playback?.status, playback?.capabilities,
            AppLanguageManager.currentTag(applicationContext),
        )
        if (!cleared && renderKey == lastRenderKey) return NotificationPublishResult.Shown

        val title = playback?.track?.title
            ?.takeIf(String::isNotBlank)
            ?: string(R.string.system_notification_synced_lyrics)
        val currentLine = lyrics.currentLine
            ?.takeIf(String::isNotBlank)
            ?: string(R.string.system_notification_waiting_line)
        val builder = notificationBuilder(LYRICS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(currentLine)
            .setSubText(playback?.track?.artist)
            .setStyle(
                Notification.BigTextStyle().bigText(
                    buildString {
                        append(currentLine)
                        lyrics.nextLine?.takeIf(String::isNotBlank)?.let {
                            append("\n")
                            append(it)
                        }
                    },
                ),
            )
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playback != null)
            .setShowWhen(false)
            .setContentIntent(openAppPendingIntent())

        playback?.let { active ->
            if (active.capabilities.canSkipPrevious) {
                builder.addAction(action(android.R.drawable.ic_media_previous, string(R.string.system_action_previous), TransportCommand.PREVIOUS))
            }
            if (active.playback.status == xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus.PLAYING) {
                if (active.capabilities.canPause) {
                    builder.addAction(action(android.R.drawable.ic_media_pause, string(R.string.system_action_pause), TransportCommand.PAUSE))
                }
            } else if (active.capabilities.canPlay) {
                builder.addAction(action(android.R.drawable.ic_media_play, string(R.string.system_action_play), TransportCommand.PLAY))
            }
            if (active.capabilities.canSkipNext) {
                builder.addAction(action(android.R.drawable.ic_media_next, string(R.string.system_action_next), TransportCommand.NEXT))
            }
        }

        builder.addAction(
            surfaceAction(
                android.R.drawable.ic_menu_view,
                string(R.string.system_action_overlay),
                LyricsSurfaceCommand.TOGGLE_OVERLAY,
            ),
        )
        builder.addAction(
            surfaceAction(
                android.R.drawable.ic_lock_lock,
                string(R.string.system_action_lock),
                LyricsSurfaceCommand.TOGGLE_OVERLAY_LOCK,
            ),
        )
        builder.addAction(
            surfaceAction(
                android.R.drawable.ic_media_rew,
                string(R.string.system_offset_back),
                LyricsSurfaceCommand.OFFSET_BACK_500_MS,
            ),
        )
        builder.addAction(
            surfaceAction(
                android.R.drawable.ic_media_ff,
                string(R.string.system_offset_forward),
                LyricsSurfaceCommand.OFFSET_FORWARD_500_MS,
            ),
        )

        manager.notify(LYRICS_NOTIFICATION_ID, builder.build())
        lastRenderKey = renderKey
        cleared = false
        return NotificationPublishResult.Shown
    }

    fun clear() {
        if (!cleared) manager.cancel(LYRICS_NOTIFICATION_ID)
        lastRenderKey = null
        cleared = true
    }

    fun refreshLocalization() {
        lastRenderKey = null
        ensureChannels()
    }

    fun buildOverlayServiceNotification(): Notification {
        ensureChannels()
        return notificationBuilder(OVERLAY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(string(R.string.system_floating_lyrics_active))
            .setContentText(string(R.string.system_tap_to_return))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppPendingIntent())
            .build()
    }

    @Suppress("DEPRECATION")
    private fun notificationBuilder(channelId: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, channelId)
        } else {
            Notification.Builder(applicationContext)
        }

    @Suppress("DEPRECATION")
    private fun action(
        icon: Int,
        title: String,
        command: TransportCommand,
    ): Notification.Action = Notification.Action.Builder(
        icon,
        title,
        PendingIntent.getBroadcast(
            applicationContext,
            command.ordinal + 100,
            Intent(applicationContext, LyricsNotificationActionReceiver::class.java)
                .setAction(ACTION_TRANSPORT)
                .putExtra(EXTRA_COMMAND, command.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    @Suppress("DEPRECATION")
    private fun surfaceAction(
        icon: Int,
        title: String,
        command: LyricsSurfaceCommand,
    ): Notification.Action = Notification.Action.Builder(
        icon,
        title,
        PendingIntent.getBroadcast(
            applicationContext,
            command.ordinal + 200,
            Intent(applicationContext, LyricsNotificationActionReceiver::class.java)
                .setAction(ACTION_SURFACE)
                .putExtra(EXTRA_SURFACE_COMMAND, command.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?: Intent().setClassName(
                applicationContext.packageName,
                "xyz.tosdev.yetasimplelyicsapp.MainActivity",
            )
        return PendingIntent.getActivity(
            applicationContext,
            1,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    LYRICS_CHANNEL_ID,
                    string(R.string.system_channel_current_lyrics),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = string(R.string.system_channel_current_lyrics_description)
                    setSound(null, null)
                },
                NotificationChannel(
                    OVERLAY_CHANNEL_ID,
                    string(R.string.system_channel_floating_lyrics),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = string(R.string.system_channel_floating_lyrics_description)
                    setSound(null, null)
                },
            ),
        )
    }

    private fun string(id: Int): String =
        AppLanguageManager.localizedContext(applicationContext).getString(id)

    companion object {
        const val LYRICS_CHANNEL_ID = "lyrics_now_playing"
        const val OVERLAY_CHANNEL_ID = "lyrics_overlay"
        const val LYRICS_NOTIFICATION_ID = 2_201
        const val OVERLAY_NOTIFICATION_ID = 2_202
        const val ACTION_TRANSPORT = "xyz.tosdev.yetasimplelyicsapp.action.TRANSPORT"
        const val ACTION_SURFACE = "xyz.tosdev.yetasimplelyicsapp.action.SURFACE"
        const val EXTRA_COMMAND = "transport_command"
        const val EXTRA_SURFACE_COMMAND = "surface_command"
    }
}

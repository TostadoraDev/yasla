package xyz.tosdev.yetasimplelyicsapp.notification

import android.content.Context
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import xyz.tosdev.yetasimplelyicsapp.domain.sync.SyncState
import xyz.tosdev.yetasimplelyicsapp.media.MediaPlaybackState
import xyz.tosdev.yetasimplelyicsapp.overlay.LyricsOverlayController
import xyz.tosdev.yetasimplelyicsapp.overlay.OverlayLyricsState
import xyz.tosdev.yetasimplelyicsapp.ui.LyricLineUi
import xyz.tosdev.yetasimplelyicsapp.ui.LyricsTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.SettingsUiState

data class SurfacePublishResult(
    val notification: NotificationPublishResult,
)

internal fun shouldPublishLyricsNotification(
    alwaysEnabled: Boolean,
    lockScreenFallbackEnabled: Boolean,
    isKeyguardLocked: Boolean,
): Boolean = alwaysEnabled || (lockScreenFallbackEnabled && isKeyguardLocked)

class LyricsSurfacePublisher(
    context: Context,
    private val notifications: LyricsNotificationCoordinator =
        LyricsNotificationCoordinator(context),
) {
    fun publish(
        sync: SyncState,
        media: MediaPlaybackState,
        notificationEnabled: Boolean,
        cues: Map<String, LyricLineUi> = emptyMap(),
        timeline: LyricsTimeline? = null,
        settings: SettingsUiState = SettingsUiState(),
    ): SurfacePublishResult {
        val active = media.active
        val activeLine = sync.activeLine
        val currentLine = activeLine?.text?.original
        val nextLine = sync.nextLine?.text?.original
        val currentCue = activeLine?.id?.let(cues::get)?.copy(isActive = true)
        val activeWordId = sync.activeWord?.id

        LyricsOverlayController.publish(
            OverlayLyricsState(
                trackTitle = active?.track?.title,
                artist = active?.track?.artist,
                artworkUri = active?.track?.artworkUri,
                previousLine = sync.previousLine?.text?.original,
                currentLine = currentLine,
                nextLine = nextLine,
                words = currentCue?.words?.map { it.text }.orEmpty(),
                activeWordIndex = currentCue?.words?.indexOfFirst { it.id == activeWordId }
                    ?.takeIf { it >= 0 },
                activeWordId = activeWordId,
                wordProgress = sync.wordProgress,
                lineProgress = sync.lineProgress,
                previousCue = sync.previousLine?.id?.let(cues::get),
                currentCue = currentCue,
                nextCue = sync.nextLine?.id?.let(cues::get),
                activeCues = sync.activeLines.mapNotNull { cues[it.line.id]?.copy(isActive = true) },
                timeline = timeline,
                motionLevel = settings.motionLevel,
                karaokeStyle = settings.karaokeStyle,
                offsetMs = sync.offsetMs,
                isPlaying = active?.playback?.status == PlaybackStatus.PLAYING,
            ),
        )
        val notificationResult = if (notificationEnabled) {
            notifications.publish(
                lyrics = NotificationLyrics(
                    currentLine = currentLine,
                    nextLine = nextLine,
                ),
                playback = active,
            )
        } else {
            notifications.clear()
            NotificationPublishResult.Cleared
        }
        return SurfacePublishResult(notification = notificationResult)
    }

    fun clear() {
        LyricsOverlayController.publish(OverlayLyricsState())
        notifications.clear()
    }

    fun refreshLocalization() {
        notifications.refreshLocalization()
    }
}

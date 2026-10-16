package xyz.tosdev.yetasimplelyicsapp.media

import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackSnapshot

enum class MediaAccessState {
    PERMISSION_REQUIRED,
    CONNECTING,
    CONNECTED,
    NO_ACTIVE_SESSION,
}

data class TrackMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val mediaId: String?,
    val artworkUri: String?,
    val isArtworkCached: Boolean = false,
    val durationMs: Long?,
) {
    val hasIdentity: Boolean
        get() = !title.isNullOrBlank() || !artist.isNullOrBlank() || !mediaId.isNullOrBlank()
}

data class TransportCapabilities(
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canSeek: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canStop: Boolean = false,
)

data class ActivePlayback(
    val packageName: String,
    val sessionId: String,
    val track: TrackMetadata,
    val playback: PlaybackSnapshot,
    val capabilities: TransportCapabilities,
)

data class MediaPlaybackState(
    val access: MediaAccessState = MediaAccessState.CONNECTING,
    val active: ActivePlayback? = null,
    val candidateCount: Int = 0,
)

enum class TransportCommand {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    STOP,
}

sealed interface TransportResult {
    data object Sent : TransportResult
    data object NoActiveSession : TransportResult
    data object Unsupported : TransportResult
    data class Failed(val reason: String) : TransportResult
}

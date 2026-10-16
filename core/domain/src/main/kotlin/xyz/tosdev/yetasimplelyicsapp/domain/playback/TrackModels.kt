package xyz.tosdev.yetasimplelyicsapp.domain.playback

data class TrackMetadata(
    val trackKey: String,
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
) {
    init {
        require(trackKey.isNotBlank()) { "trackKey must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(artists.isNotEmpty()) { "artists must not be empty" }
        require(durationMs == null || durationMs >= 0L) { "durationMs must be non-negative" }
    }

    val artistLabel: String
        get() = artists.joinToString(", ")
}

enum class PlaybackAction {
    PLAY,
    PAUSE,
    SEEK,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    STOP,
}

enum class PositionQuality {
    EXACT,
    EXTRAPOLATED,
    METADATA_ONLY,
    UNKNOWN,
}

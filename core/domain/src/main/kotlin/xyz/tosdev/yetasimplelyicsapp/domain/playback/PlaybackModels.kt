package xyz.tosdev.yetasimplelyicsapp.domain.playback

enum class PlaybackStatus {
    IDLE,
    PLAYING,
    PAUSED,
    BUFFERING,
    STOPPED,
}

/** Normalizes incomplete MediaSession speed reports without advancing paused sessions. */
fun effectivePlaybackSpeed(
    status: PlaybackStatus,
    reportedSpeed: Float?,
): Float {
    val validReportedSpeed = reportedSpeed?.takeIf { it.isFinite() && it > 0f }
    return validReportedSpeed ?: if (status == PlaybackStatus.PLAYING) 1f else 0f
}

data class PlaybackSnapshot(
    val sessionId: String,
    val generation: Long,
    val trackKey: String?,
    val status: PlaybackStatus,
    val reportedPositionMs: Long?,
    val reportedAtElapsedMs: Long,
    val speed: Float = 1f,
    val durationMs: Long? = null,
    val isSeekable: Boolean = false,
    val playerPackage: String? = null,
    val metadata: TrackMetadata? = null,
    val actions: Set<PlaybackAction> = emptySet(),
    val artworkUri: String? = null,
    val positionQuality: PositionQuality = PositionQuality.UNKNOWN,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(generation >= 0L) { "generation must be non-negative" }
        require(reportedPositionMs == null || reportedPositionMs >= 0L) {
            "reportedPositionMs must be non-negative"
        }
        require(reportedAtElapsedMs >= 0L) { "reportedAtElapsedMs must be non-negative" }
        require(speed >= 0f) { "speed must be non-negative" }
        require(durationMs == null || durationMs >= 0L) { "durationMs must be non-negative" }
    }

    fun estimatedPositionAt(elapsedRealtimeMs: Long): Long? {
        val reported = reportedPositionMs ?: return null
        val elapsed = (elapsedRealtimeMs - reportedAtElapsedMs).coerceAtLeast(0L)
        val estimate = if (status == PlaybackStatus.PLAYING) {
            reported + (elapsed * speed).toLong()
        } else {
            reported
        }
        return durationMs?.let { estimate.coerceIn(0L, it) } ?: estimate.coerceAtLeast(0L)
    }
}

fun interface MonotonicClock {
    fun nowElapsedMs(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowElapsedMs(): Long = System.nanoTime() / 1_000_000L
}

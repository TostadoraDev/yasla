package xyz.tosdev.yetasimplelyicsapp.media

import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus
import kotlin.math.max

data class SessionCandidate(
    val sessionId: String,
    val packageName: String,
    val status: PlaybackStatus,
    val updatedAtElapsedMs: Long,
    val hasTitle: Boolean,
    val hasArtist: Boolean,
    val hasMediaId: Boolean,
)

class PlaybackSessionArbitrator(
    private val continuityBonus: Int = 120,
) {
    fun select(
        candidates: List<SessionCandidate>,
        previousSessionId: String?,
        nowElapsedMs: Long,
    ): SessionCandidate? = candidates
        .maxWithOrNull(
            compareBy<SessionCandidate>(
                { score(it, previousSessionId, nowElapsedMs) },
                { it.updatedAtElapsedMs },
                { it.sessionId },
            ),
        )

    internal fun score(
        candidate: SessionCandidate,
        previousSessionId: String?,
        nowElapsedMs: Long,
    ): Int {
        val playbackScore = when (candidate.status) {
            PlaybackStatus.PLAYING -> 1_000
            PlaybackStatus.BUFFERING -> 760
            PlaybackStatus.PAUSED -> 520
            PlaybackStatus.STOPPED -> 120
            PlaybackStatus.IDLE -> 0
        }
        val ageMs = max(0L, nowElapsedMs - candidate.updatedAtElapsedMs)
        val freshnessScore = (300L - ageMs / 1_000L).coerceIn(0L, 300L).toInt()
        val metadataScore =
            (if (candidate.hasTitle) 40 else 0) +
                (if (candidate.hasArtist) 25 else 0) +
                (if (candidate.hasMediaId) 15 else 0)
        val continuity = if (candidate.sessionId == previousSessionId) continuityBonus else 0
        return playbackScore + freshnessScore + metadataScore + continuity
    }
}

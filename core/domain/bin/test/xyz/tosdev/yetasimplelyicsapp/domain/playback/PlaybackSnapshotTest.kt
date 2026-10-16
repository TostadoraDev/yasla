package xyz.tosdev.yetasimplelyicsapp.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSnapshotTest {
    @Test
    fun playingSnapshotUsesMonotonicElapsedTimeAndSpeed() {
        val snapshot = snapshot(
            status = PlaybackStatus.PLAYING,
            positionMs = 1_000L,
            reportedAtElapsedMs = 5_000L,
            speed = 1.5f,
        )

        assertEquals(4_000L, snapshot.estimatedPositionAt(7_000L))
    }

    @Test
    fun pausedSnapshotKeepsReportedPosition() {
        val snapshot = snapshot(
            status = PlaybackStatus.PAUSED,
            positionMs = 1_500L,
            reportedAtElapsedMs = 1_000L,
        )

        assertEquals(1_500L, snapshot.estimatedPositionAt(9_000L))
    }

    @Test
    fun estimateIsClampedToTrackDuration() {
        val snapshot = snapshot(
            status = PlaybackStatus.PLAYING,
            positionMs = 4_500L,
            reportedAtElapsedMs = 1_000L,
            durationMs = 5_000L,
        )

        assertEquals(5_000L, snapshot.estimatedPositionAt(9_000L))
    }

    private fun snapshot(
        status: PlaybackStatus,
        positionMs: Long,
        reportedAtElapsedMs: Long,
        speed: Float = 1f,
        durationMs: Long? = null,
    ) = PlaybackSnapshot(
        sessionId = "session",
        generation = 1L,
        trackKey = "track-a",
        status = status,
        reportedPositionMs = positionMs,
        reportedAtElapsedMs = reportedAtElapsedMs,
        speed = speed,
        durationMs = durationMs,
    )
}

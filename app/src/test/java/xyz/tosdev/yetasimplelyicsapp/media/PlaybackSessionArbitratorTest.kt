package xyz.tosdev.yetasimplelyicsapp.media

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus

class PlaybackSessionArbitratorTest {
    private val arbitrator = PlaybackSessionArbitrator()

    @Test
    fun playingSessionWinsOverPausedSessionRegardlessOfPackage() {
        val candidates = listOf(
            candidate("paused", "preferred-looking.package", PlaybackStatus.PAUSED, 9_900),
            candidate("playing", "unknown.player", PlaybackStatus.PLAYING, 9_800),
        )

        val selected = arbitrator.select(candidates, previousSessionId = "paused", nowElapsedMs = 10_000)

        assertEquals("playing", selected?.sessionId)
    }

    @Test
    fun continuityBreaksTieBetweenEquivalentSessions() {
        val candidates = listOf(
            candidate("one", "player.one", PlaybackStatus.PLAYING, 10_000),
            candidate("two", "player.two", PlaybackStatus.PLAYING, 10_000),
        )

        val selected = arbitrator.select(candidates, previousSessionId = "one", nowElapsedMs = 10_000)

        assertEquals("one", selected?.sessionId)
    }

    @Test
    fun recentMetadataRichSessionWinsEquivalentPlaybackState() {
        val sparse = candidate(
            id = "sparse",
            packageName = "player.sparse",
            status = PlaybackStatus.PAUSED,
            updatedAt = 5_000,
            hasMetadata = false,
        )
        val rich = candidate(
            id = "rich",
            packageName = "player.rich",
            status = PlaybackStatus.PAUSED,
            updatedAt = 9_900,
            hasMetadata = true,
        )

        val selected = arbitrator.select(listOf(sparse, rich), null, 10_000)

        assertEquals("rich", selected?.sessionId)
    }

    @Test
    fun selectsReplacementControllerWhenPreviousSessionDisappears() {
        val youtubeMusic = candidate(
            id = "youtube-music-session",
            packageName = "com.google.android.apps.youtube.music",
            status = PlaybackStatus.PLAYING,
            updatedAt = 10_000,
        )

        val selected = arbitrator.select(
            candidates = listOf(youtubeMusic),
            previousSessionId = "spotify-session",
            nowElapsedMs = 10_000,
        )

        assertEquals("youtube-music-session", selected?.sessionId)
    }

    private fun candidate(
        id: String,
        packageName: String,
        status: PlaybackStatus,
        updatedAt: Long,
        hasMetadata: Boolean = true,
    ) = SessionCandidate(
        sessionId = id,
        packageName = packageName,
        status = status,
        updatedAtElapsedMs = updatedAt,
        hasTitle = hasMetadata,
        hasArtist = hasMetadata,
        hasMediaId = hasMetadata,
    )
}

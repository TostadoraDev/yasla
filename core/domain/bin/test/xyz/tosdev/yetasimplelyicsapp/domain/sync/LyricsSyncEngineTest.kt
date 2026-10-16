package xyz.tosdev.yetasimplelyicsapp.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LocalizedText
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsCapability
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsLine
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.SyncType
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.TimingQuality
import xyz.tosdev.yetasimplelyicsapp.domain.playback.MonotonicClock
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackSnapshot
import xyz.tosdev.yetasimplelyicsapp.domain.playback.PlaybackStatus

class LyricsSyncEngineTest {
    private val clock = FakeClock(1_000L)
    private val engine = LyricsSyncEngine(clock)

    @Test
    fun selectsLineAndComputesWordProgress() {
        engine.updatePlayback(playback(positionMs = 1_500L))
        engine.updateLyrics(document())

        val state = engine.sample()

        assertEquals("line-1", state.activeLine?.id)
        assertEquals("world", state.activeWord?.text)
        assertEquals(0.5f, state.wordProgress, 0.001f)
        assertEquals(0.75f, state.lineProgress, 0.001f)
    }

    @Test
    fun positiveOffsetDelaysLyrics() {
        engine.updatePlayback(playback(positionMs = 5_000L))
        engine.updateLyrics(document())
        engine.updateOffset(2_500L)

        assertEquals(2_500L, engine.state.value.lyricsPositionMs)
        assertEquals("line-2", engine.state.value.activeLine?.id)
    }

    @Test
    fun pausedPlaybackDoesNotAdvanceDuringSampling() {
        engine.updatePlayback(
            playback(
                status = PlaybackStatus.PAUSED,
                positionMs = 1_500L,
            ),
        )
        engine.updateLyrics(document())
        clock.nowMs = 8_000L

        assertEquals(1_500L, engine.sample().lyricsPositionMs)
    }

    @Test
    fun replacementSnapshotActsAsSeekDiscontinuity() {
        engine.updatePlayback(playback(positionMs = 500L))
        engine.updateLyrics(document())
        engine.updatePlayback(playback(positionMs = 3_500L))

        assertEquals("line-2", engine.state.value.activeLine?.id)
    }

    @Test
    fun changingTrackClearsStaleLyrics() {
        val lyrics = document()
        engine.updatePlayback(playback(positionMs = 500L))
        engine.updateLyrics(lyrics)
        assertSame(lyrics, engine.state.value.document)

        engine.updatePlayback(playback(positionMs = 500L, trackKey = "track-b"))

        assertNull(engine.state.value.document)
        assertNull(engine.state.value.activeLine)
    }

    @Test
    fun zeroDurationCueNeverDividesByZero() {
        engine.updatePlayback(playback(positionMs = 1_000L))
        engine.updateLyrics(
            document(
                lines = listOf(
                    LyricsLine(
                        id = "instant",
                        order = 0,
                        text = LocalizedText("Now"),
                        startTimeMs = 1_000L,
                        endTimeMs = 1_000L,
                        timingQuality = TimingQuality.EXPLICIT,
                    ),
                ),
            ),
        )

        assertEquals("instant", engine.state.value.activeLine?.id)
        assertEquals(1f, engine.state.value.lineProgress, 0f)
    }

    @Test
    fun reindexesUnsortedLinesAndWordsWhenLyricsChange() {
        val first = document().lines.first().copy(
            words = document().lines.first().words.reversed(),
        )
        val unsorted = document(
            lines = listOf(
                document().lines[2],
                first,
                document().lines[1],
            ),
        )
        engine.updatePlayback(playback(positionMs = 1_500L))
        engine.updateLyrics(unsorted)

        assertEquals("line-1", engine.state.value.activeLine?.id)
        assertEquals("world", engine.state.value.activeWord?.text)
    }

    @Test
    fun retainsLineDuringGapBetweenExplicitLines() {
        val doc = document(
            lines = listOf(
                LyricsLine(
                    id = "line-1",
                    order = 0,
                    text = LocalizedText("Line 1"),
                    startTimeMs = 0L,
                    endTimeMs = 2_000L,
                    words = listOf(
                        LyricsWord("w1", "Line", 0L, 1_000L, TimingQuality.EXPLICIT),
                        LyricsWord("w2", "1", 1_000L, 2_000L, TimingQuality.EXPLICIT),
                    ),
                    timingQuality = TimingQuality.EXPLICIT,
                ),
                LyricsLine(
                    id = "line-2",
                    order = 1,
                    text = LocalizedText("Line 2"),
                    startTimeMs = 3_500L,
                    endTimeMs = 5_000L,
                    timingQuality = TimingQuality.EXPLICIT,
                ),
            ),
        )
        engine.updateLyrics(doc)
        engine.updatePlayback(playback(positionMs = 2_500L))

        val state = engine.sample()
        assertEquals("line-1", state.activeLine?.id)
        assertNull(state.activeWord)
        assertEquals(1f, state.lineProgress, 0f)
        assertEquals("line-2", state.nextLine?.id)
    }

    private fun playback(
        status: PlaybackStatus = PlaybackStatus.PAUSED,
        positionMs: Long,
        trackKey: String = "track-a",
    ) = PlaybackSnapshot(
        sessionId = "session",
        generation = 1L,
        trackKey = trackKey,
        status = status,
        reportedPositionMs = positionMs,
        reportedAtElapsedMs = clock.nowMs,
        durationMs = 6_000L,
        isSeekable = true,
    )

    private fun document(
        lines: List<LyricsLine> = listOf(
            LyricsLine(
                id = "line-1",
                order = 0,
                text = LocalizedText("Hello world"),
                startTimeMs = 0L,
                endTimeMs = 2_000L,
                words = listOf(
                    LyricsWord("word-1", "Hello", 0L, 1_000L, TimingQuality.EXPLICIT),
                    LyricsWord("word-2", "world", 1_000L, 2_000L, TimingQuality.EXPLICIT),
                ),
                timingQuality = TimingQuality.EXPLICIT,
            ),
            LyricsLine(
                id = "line-2",
                order = 1,
                text = LocalizedText("Second line"),
                startTimeMs = 2_000L,
                endTimeMs = 4_000L,
                timingQuality = TimingQuality.EXPLICIT,
            ),
            LyricsLine(
                id = "line-3",
                order = 2,
                text = LocalizedText("Third line"),
                startTimeMs = 4_000L,
                endTimeMs = 6_000L,
                timingQuality = TimingQuality.EXPLICIT,
            ),
        ),
    ) = LyricsDocument(
        id = "lyrics-a",
        trackKey = "track-a",
        source = LyricsSource(providerId = "test"),
        syncType = SyncType.WORD,
        lines = lines,
        capabilities = setOf(
            LyricsCapability.PLAIN_TEXT,
            LyricsCapability.LINE_SYNC,
            LyricsCapability.WORD_SYNC,
        ),
    )

    private class FakeClock(var nowMs: Long) : MonotonicClock {
        override fun nowElapsedMs(): Long = nowMs
    }
}

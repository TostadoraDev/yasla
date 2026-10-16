package xyz.tosdev.yetasimplelyicsapp

import org.junit.Assert.*
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.domain.playback.*
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsWord

class WidgetCueTimingTest {
    private val playback = PlaybackSnapshot("test", 1, "track", PlaybackStatus.PLAYING,
        reportedPositionMs = 1000, reportedAtElapsedMs = 10000, speed = 1f)

    @Test fun deadlinesAreAbsoluteAndIncludePositiveDelayExactlyOnce() {
        assertEquals(10100L, wordDeadlineElapsedMs(playback, 1100, 0))
        assertEquals(10250L, wordDeadlineElapsedMs(playback, 1000, 250))
        // A slow render cannot push the next deadline forward: no render time is an input.
        assertEquals(10200L, wordDeadlineElapsedMs(playback, 1200, 0))
        assertEquals(10050L, wordDeadlineElapsedMs(playback.copy(speed = 2f), 1100, 0))
    }

    @Test fun pausedOrUnknownPositionDoesNotInventADeadline() {
        assertEquals(-1L, wordDeadlineElapsedMs(playback.copy(status = PlaybackStatus.PAUSED), 1100, 0))
        assertEquals(-1L, wordDeadlineElapsedMs(playback.copy(reportedPositionMs = null), 1100, 0))
        assertEquals(-1L, wordDeadlineElapsedMs(playback, null, 0))
    }

    @Test fun lookaheadUsesWholeWordsAndStrictFutureStartsThroughGapsAndSeeks() {
        val words = listOf(LyricsWord("a", "CUANDO", 100, 180),
            LyricsWord("b", "MIENTRAS", 200, 280), LyricsWord("c", "EXTRAORDINARIO", 500, 900))
        assertEquals("a", nextSemanticWidgetWord(words, 0)?.id)
        assertEquals("b", nextSemanticWidgetWord(words, 100)?.id)
        assertEquals("b", nextSemanticWidgetWord(words, 190)?.id)
        assertEquals("c", nextSemanticWidgetWord(words, 205)?.id)
        assertEquals("a", nextSemanticWidgetWord(words, 0)?.id)
        assertNull(nextSemanticWidgetWord(words, 1000))
    }
}

package xyz.tosdev.yetasimplelyicsapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.components.AmllRowMotion
import xyz.tosdev.yetasimplelyicsapp.ui.components.AmllTimedWidth
import xyz.tosdev.yetasimplelyicsapp.ui.components.AmllVisualTimeline
import xyz.tosdev.yetasimplelyicsapp.ui.components.AmllVisualWindow
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllFrameDeltaSeconds
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllLyricsSourceKey
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllRevealDistanceAt
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllVisualIntervalMs
import xyz.tosdev.yetasimplelyicsapp.ui.components.amllVisualWindows

class AmllViewportContractTest {
    @Test
    fun viewportForwardsFullElapsedFrameTimeToAnalyticSprings() {
        assertEquals(0f, amllFrameDeltaSeconds(0L, 1_000_000_000L), 0f)
        assertEquals(0.2f, amllFrameDeltaSeconds(1_000_000_000L, 1_200_000_000L), 0.000001f)
    }

    @Test
    fun bufferedActivationRetainsShortOverlapUntilWholeBufferExpires() {
        val timeline = AmllVisualTimeline(
            listOf(
                AmllVisualWindow(0L, 5_000L),
                AmllVisualWindow(2_000L, 3_000L),
                AmllVisualWindow(6_000L, 7_000L),
            ),
        )

        assertEquals(setOf(0), timeline.update(1_000L).bufferedIndices)
        assertEquals(setOf(0, 1), timeline.update(2_500L).bufferedIndices)
        val afterShortLine = timeline.update(3_500L)
        assertEquals(setOf(0), afterShortLine.hotIndices)
        assertEquals(setOf(0, 1), afterShortLine.bufferedIndices)
        assertEquals(0, afterShortLine.scrollIndex)
        assertTrue(timeline.update(5_000L).bufferedIndices.isEmpty())
    }

    @Test
    fun newHotWindowPrunesFinishedBufferedRowsAndSeekResetsImmediately() {
        val timeline = AmllVisualTimeline(
            listOf(
                AmllVisualWindow(0L, 5_000L),
                AmllVisualWindow(2_000L, 3_000L),
                AmllVisualWindow(4_000L, 4_500L),
                AmllVisualWindow(6_000L, 7_000L),
            ),
        )

        timeline.update(2_500L)
        assertEquals(setOf(0, 1), timeline.update(3_500L).bufferedIndices)
        assertEquals(setOf(0, 2), timeline.update(4_100L).bufferedIndices)
        assertEquals(setOf(0), timeline.update(3_500L, seeking = true).bufferedIndices)

        val emptySeek = timeline.update(5_500L, seeking = true)
        assertTrue(emptySeek.bufferedIndices.isEmpty())
        assertEquals(3, emptySeek.scrollIndex)
    }

    @Test
    fun structuralSourceKeyTracksEveryImmutableDescriptorButIgnoresFrameProgress() {
        val source = listOf(
            LyricLineUi(
                id = "line",
                words = listOf(
                    KaraokeWordUi(
                        id = "word",
                        text = "hello",
                        progress = 0.1f,
                        startTimeMs = 100L,
                        endTimeMs = 900L,
                        segments = listOf(KaraokeSegmentUi("segment", "hel", 100L, 500L)),
                    ),
                ),
                originalText = "hello",
                translation = "hola",
                isBackground = false,
                isActive = false,
                isPast = false,
                startTimeMs = 100L,
                endTimeMs = 900L,
                alignment = xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment.LEFT,
                sourceAlignment = xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsAlignment.AUTO,
                agentId = "lead",
                direction = xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsTextDirection.LTR,
            ),
        )
        val frameUpdate = source.map { line ->
            line.copy(
                isActive = true,
                isPast = true,
                words = line.words.map { it.copy(progress = 0.9f) },
            )
        }

        assertEquals(amllLyricsSourceKey(source), amllLyricsSourceKey(frameUpdate))
        assertTrue(amllLyricsSourceKey(source) != amllLyricsSourceKey(
            source.map { it.copy(translation = "bonjour") },
        ))
        assertTrue(amllLyricsSourceKey(source) != amllLyricsSourceKey(
            source.map { line -> line.copy(words = line.words.map { it.copy(text = "changed") }) },
        ))
        assertTrue(amllLyricsSourceKey(source) != amllLyricsSourceKey(
            source.map { line -> line.copy(words = line.words.map { word ->
                word.copy(segments = word.segments.map { it.copy(endTimeMs = 600L) })
            }) },
        ))
        assertTrue(amllLyricsSourceKey(source) != amllLyricsSourceKey(
            source.map { it.copy(isBackground = true) },
        ))
    }

    @Test
    fun verticalStiffnessIntervalUsesOptimizedVisualWindowStarts() {
        val lines = listOf(
            LyricLineUi("a", listOf(KaraokeWordUi("wa", "A", startTimeMs = 0L, endTimeMs = 1_000L))),
            LyricLineUi("b", listOf(KaraokeWordUi("wb", "B", startTimeMs = 800L, endTimeMs = 1_500L))),
        )
        val windows = amllVisualWindows(lines)

        assertEquals(0L, windows[0].startMs)
        assertEquals(400L, windows[1].startMs)
        assertEquals(400L, amllVisualIntervalMs(lines, windows, 1))
    }

    @Test
    fun visualPrerollMovesFocusEarlyWithoutMutatingWordOrSourceTimestamps() {
        val lines = listOf(
            LyricLineUi("first", listOf(KaraokeWordUi("a", "Small", startTimeMs = 0, endTimeMs = 1_800))),
            LyricLineUi("second", listOf(KaraokeWordUi("b", "Light", startTimeMs = 4_000, endTimeMs = 7_800))),
        )
        val windows = amllVisualWindows(lines)
        assertEquals(0L, windows[0].startMs)
        assertEquals(3_400L, windows[1].startMs)
        assertEquals(4_000L, lines[1].words.single().startTimeMs)
        assertEquals(7_800L, windows[1].endMs)
    }

    @Test
    fun maskUsesMeasuredInkAndLinearTimingInsteadOfCharacterCountOrEase() {
        val pieces = listOf(AmllTimedWidth(0, 300, 80f), AmllTimedWidth(300, 1_000, 20f))
        val fade = 20f
        // The first control interval carries the lead feather; final interval carries the tail.
        assertEquals(-40f, amllRevealDistanceAt(0, pieces, fade), 0f)
        assertEquals(15f, amllRevealDistanceAt(150, pieces, fade), 0.0001f)
        assertEquals(70f, amllRevealDistanceAt(300, pieces, fade), 0.0001f)
        assertEquals(85f, amllRevealDistanceAt(650, pieces, fade), 0.0001f)
        assertEquals(100f, amllRevealDistanceAt(1_000, pieces, fade), 0f)
    }

    @Test
    fun multiSnapWordNeverResetsAtBoundariesAndGapHoldsTheFrontier() {
        val pieces = listOf(AmllTimedWidth(100, 300, 10f), AmllTimedWidth(300, 600, 40f),
            AmllTimedWidth(700, 1_000, 20f), AmllTimedWidth(1_000, 1_300, 30f))
        for (time in 1L..1_400) {
            assertTrue(amllRevealDistanceAt(time, pieces, 18f) >= amllRevealDistanceAt(time - 1, pieces, 18f))
        }
        assertEquals(amllRevealDistanceAt(600, pieces, 18f), amllRevealDistanceAt(650, pieces, 18f), 0f)
        assertEquals(100f, amllRevealDistanceAt(1_400, pieces, 18f), 0.0001f)
    }

    @Test
    fun repeatedFramesDoNotCancelOrRestartTheDelayedRowTarget() {
        val row = AmllRowMotion(0f)
        repeat(5) {
            row.retarget(100f, false, true, true, 1.6f, 170f, 2.2f * kotlin.math.sqrt(170f),
                0.05f, MotionLevel.NORMAL)
            row.step(0.016f, 0L, true, MotionLevel.NORMAL)
        }
        assertEquals(100f, row.position.target, 0f)
        assertTrue(row.position.position > 0f)
    }

    @Test
    fun pausedWordTimeFreezesWhileRowScaleContinuesSettling() {
        val row = AmllRowMotion(0f)
        row.retarget(0f, true, false, true, 0f, 170f, 29f, 0f, MotionLevel.NORMAL)
        repeat(120) { row.step(1f / 120, 500L, true, MotionLevel.NORMAL) }
        assertEquals(500L, row.wordPosition(500L))
        assertEquals(1f, row.frame.scale, 0.0001f)
        assertEquals(1f, row.frame.brightAlpha, 0.0001f)
    }
}

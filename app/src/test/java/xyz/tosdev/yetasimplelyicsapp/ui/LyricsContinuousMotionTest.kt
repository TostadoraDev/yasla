package xyz.tosdev.yetasimplelyicsapp.ui

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.components.*
import xyz.tosdev.yetasimplelyicsapp.ui.screens.resolvedLyricsLoadState

class LyricsContinuousMotionTest {
    @Test
    fun fiveTenAndTwentyConsecutiveCuesKeepPositionAndVelocityAt60And120Hz() {
        for (changes in listOf(5, 10, 20)) {
            for (hz in listOf(60, 120)) {
                val follower = LyricsScrollFollower()
                var position = 0f
                var target = 0f
                repeat(changes) { cue ->
                    target += listOf(72f, 152f, 240f)[cue % 3]
                    val before = follower.velocity
                    val fresh = LyricsScrollFollower().step(target - position, 1f / hz)
                    val retained = follower.step(target - position, 1f / hz)
                    assertTrue("retarget keeps momentum: $changes/$hz/$cue", retained >= fresh - 0.001f)
                    if (before > 1f) assertTrue(retained > fresh)
                    position += retained
                    repeat((hz * listOf(0.25f, 0.5f, 1.5f)[cue % 3]).toInt() - 1) {
                        val delta = follower.step(target - position, 1f / hz)
                        assertTrue("no reverse bounce", delta >= -0.001f)
                        position += delta
                        assertTrue("no overshoot", position <= target + 0.1f)
                    }
                }
                repeat(hz * 2) { position += follower.step(target - position, 1f / hz) }
                assertEquals(target, position, 0.2f)
                assertEquals(0f, follower.velocity, 0.5f)
            }
        }
    }

    @Test
    fun analyticalFollowerIsFrameRateIndependentAndSettlesWithoutRestarting() {
        fun sample(hz: Int): Pair<Float, Float> {
            val follower = LyricsScrollFollower()
            var position = 0f
            repeat(hz / 2) { position += follower.step(200f - position, 1f / hz) }
            return position to follower.velocity
        }
        val at60 = sample(60)
        val at120 = sample(120)
        assertEquals(at60.first, at120.first, 0.002f)
        assertEquals(at60.second, at120.second, 0.01f)
        assertTrue(at60.first in 175f..195f)
    }

    @Test
    fun seekAndTrackResetDiscardOldVelocityWhilePauseOnlySettles() {
        val follower = LyricsScrollFollower()
        var position = 0f
        repeat(12) { position += follower.step(200f - position, 1f / 60) }
        assertTrue(follower.velocity > 0f)
        // A frozen media position leaves the same target; it does not run a second media clock.
        repeat(120) { position += follower.step(200f - position, 1f / 60) }
        assertEquals(200f, position, 0.2f)
        follower.reset()
        assertEquals(0f, follower.velocity, 0f)
        assertTrue(lyricsPlaybackDiscontinuity(2_000L, 9_000L, 1f / 60))
        assertTrue(lyricsPlaybackDiscontinuity(2_000L, 1_000L, 1f / 60))
        assertFalse(lyricsPlaybackDiscontinuity(2_000L, 2_000L, 1f / 60))
        assertFalse(lyricsPlaybackDiscontinuity(2_000L, 2_017L, 1f / 60))
        assertFalse(lyricsPlaybackDiscontinuity(null, 10_000L, 1f / 60))
    }

    @Test
    fun anticipationIsBoundedAndDoesNotConsumeTheInstrumentalGap() {
        assertEquals(0f, lyricAnticipation(6_000L, 1_000L, 10_000L, MotionLevel.NORMAL), 0f)
        assertEquals(0f, lyricAnticipation(9_000L, 1_000L, 10_000L, MotionLevel.NORMAL), 0f)
        var previous = 0f
        for (time in 9_100L..10_000L step 10) {
            val lead = lyricAnticipation(time, 1_000L, 10_000L, MotionLevel.NORMAL)
            assertTrue(lead >= previous)
            val focusFraction = LYRICS_FOCUS_FRACTION - 0.02f * lead
            assertTrue(focusFraction in 0.25f..0.30f)
            previous = lead
        }
        assertEquals(1f, previous, 0f)
        assertEquals(0f, lyricAnticipation(9_900L, 1_000L, 10_000L, MotionLevel.OFF), 0f)
        assertEquals(0f, lyricAnticipation(9_900L, 1_000L, 10_000L, MotionLevel.REDUCED), 0f)
        assertEquals(0f, lyricAnticipation(1_000L, 1_000L, 1_000L, MotionLevel.NORMAL), 0f)
    }

    @Test
    fun anticipationUsesMeasuredNextBoundsWithoutLeavingTheFocusBand() {
        val currentError = 100f
        val nextError = 420f
        var previous = currentError
        for (step in 0..100) {
            val target = lyricsContinuousTargetError(
                currentErrorPx = currentError,
                nextErrorPx = nextError,
                anticipation = step / 100f,
                viewportHeightPx = 1_000,
            )
            assertTrue(target >= previous)
            assertTrue(target in currentError..120f)
            previous = target
        }
        assertEquals(120f, previous, 0f)
        assertEquals(currentError, lyricsContinuousTargetError(currentError, null, 1f, 1_000), 0f)
        assertEquals(currentError, lyricsContinuousTargetError(currentError, 80f, 1f, 1_000), 0f)
        assertEquals(currentError, lyricsContinuousTargetError(currentError, nextError, 0f, 1_000), 0f)
    }

    @Test
    fun paddingIsConvertedToViewportCoordinatesOnceForShortAndWrappedLines() {
        for (height in listOf(72, 152, 240)) {
            val top = lyricFocusOffsetPx(1_000, height)
            val padding = 270
            val scrollOffset = padding - top
            assertEquals(top, lyricViewportTop(-scrollOffset, -padding))
            assertEquals(270, lyricViewportTop(-scrollOffset, -padding) + height / 2)
        }
    }

    @Test
    fun spatialDepthIsContinuousAcrossFocusAndCueChanges() {
        val center = lyricsViewportPresentation(0f, 1_000, MotionLevel.NORMAL)
        assertEquals(1f, center.alpha, 0f)
        assertEquals(1f, center.scale, 0f)
        for (distance in -1_000..1_000) {
            val p = lyricsViewportPresentation(distance.toFloat(), 1_000, MotionLevel.ENHANCED)
            val next = lyricsViewportPresentation(distance + 1f, 1_000, MotionLevel.ENHANCED)
            assertTrue(abs(next.alpha - p.alpha) < 0.007f)
            assertTrue(p.scale in 0.97f..1f)
            assertEquals(0f, p.translationYDp, 0f)
        }
        assertEquals(1f, lyricsViewportPresentation(400f, 1_000, MotionLevel.REDUCED).scale, 0f)
    }

    @Test
    fun depthCurveMatchesRequestedLineHierarchyWithoutCueDirectionBias() {
        assertEquals(1f, lyricsDistancePresentation(0f, MotionLevel.NORMAL).alpha, 0f)
        assertEquals(0.72f, lyricsDistancePresentation(1f, MotionLevel.NORMAL).alpha, 0f)
        assertEquals(0.50f, lyricsDistancePresentation(2f, MotionLevel.NORMAL).alpha, 0f)
        assertEquals(0.34f, lyricsDistancePresentation(3f, MotionLevel.NORMAL).alpha, 0f)
        assertEquals(0.993f, lyricsDistancePresentation(1f, MotionLevel.NORMAL).scale, 0f)
        assertEquals(0.985f, lyricsDistancePresentation(2f, MotionLevel.NORMAL).scale, 0f)
        assertEquals(1f, lyricsDistancePresentation(2f, MotionLevel.REDUCED).scale, 0f)
        for (step in 0..399) {
            val before = lyricsDistancePresentation(step / 100f, MotionLevel.NORMAL)
            val after = lyricsDistancePresentation((step + 1) / 100f, MotionLevel.NORMAL)
            assertTrue(after.alpha <= before.alpha + 0.0001f)
            assertTrue(abs(after.alpha - before.alpha) < 0.008f)
        }
    }

    @Test
    fun offscreenCueUsesMeasuredCadenceUntilItsRealBoundsBecomeVisible() {
        val measured = listOf(
            LyricsItemGeometry(index = 4, offsetPx = 80, sizePx = 70),
            LyricsItemGeometry(index = 5, offsetPx = 160, sizePx = 72),
            LyricsItemGeometry(index = 6, offsetPx = 240, sizePx = 68),
        )
        val below = lyricTargetErrorPx(8, measured, viewportStartOffsetPx = -270, viewportHeightPx = 1_000)
        val above = lyricTargetErrorPx(2, measured, viewportStartOffsetPx = -270, viewportHeightPx = 1_000)
        assertNotNull(below)
        assertNotNull(above)
        assertTrue(below!! > 0f)
        assertTrue(above!! < below)

        val nowVisible = measured + LyricsItemGeometry(index = 8, offsetPx = 398, sizePx = 120)
        val refined = lyricTargetErrorPx(8, nowVisible, viewportStartOffsetPx = -270, viewportHeightPx = 1_000)
        assertNotNull(refined)
        assertTrue(abs(refined!! - below) < 80f)
    }

    @Test
    fun completeWordMotionDoesNotResetAtSyllablesAndRespectsLimits() {
        for (level in MotionLevel.entries) {
            for (time in 0L..1_000L) {
                val wordProgress = karaokeProgressAt(time, 0L, 1_000L, 0f)
                val p = refinedWordPresentationAt(wordProgress, level)
                assertTrue(p.opacity in 0.83f..1f)
                assertTrue(p.scale in 0.985f..1f)
                assertTrue(p.trackingScale in 0.995f..1f)
                assertTrue(p.translationYDp in 0f..3f)
                if (time > 0) {
                    val previous = refinedWordPresentationAt((time - 1) / 1_000f, level)
                    assertTrue(abs(p.translationYDp - previous.translationYDp) < 0.006f)
                }
                if (level == MotionLevel.REDUCED || level == MotionLevel.OFF) {
                    assertEquals(0f, p.translationYDp, 0f)
                    assertEquals(1f, p.scale, 0f)
                }
            }
            val done = refinedWordPresentationAt(1f, level)
            assertEquals(1f, done.opacity, 0f)
            assertEquals(0f, done.translationYDp, 0f)
            assertEquals(1f, done.scale, 0f)
            assertEquals(1f, done.trackingScale, 0f)
            assertEquals(done, refinedWordPresentationAt(0.2f, level, motionAmount = 0f))
        }
    }

    @Test
    fun shortWordsSuppressSecondaryMotionWhileLongWordsCanBreathe() {
        val short = refinedWordPresentationAt(0f, MotionLevel.NORMAL, durationMs = 200L)
        val medium = refinedWordPresentationAt(0f, MotionLevel.NORMAL, durationMs = 500L)
        val long = refinedWordPresentationAt(0f, MotionLevel.NORMAL, durationMs = 1_200L)

        assertTrue(short.opacity > medium.opacity)
        assertTrue(medium.opacity > long.opacity)
        assertTrue(short.translationYDp < medium.translationYDp)
        assertTrue(medium.translationYDp < long.translationYDp)
        assertTrue(short.scale > medium.scale)
        assertTrue(medium.scale > long.scale)
        assertTrue(wordMotionDurationFactor(200L) < wordMotionDurationFactor(500L))
        assertEquals(1f, wordMotionDurationFactor(1_200L), 0f)
    }

    @Test
    fun longWordMotionHasVisiblePresenceAndEvolvesAcrossTheWholeInterval() {
        val start = refinedWordPresentationAt(0f, MotionLevel.NORMAL, durationMs = 1_200L)
        val middle = refinedWordPresentationAt(0.5f, MotionLevel.NORMAL, durationMs = 1_200L)
        val nearEnd = refinedWordPresentationAt(0.9f, MotionLevel.NORMAL, durationMs = 1_200L)
        val end = refinedWordPresentationAt(1f, MotionLevel.NORMAL, durationMs = 1_200L)

        assertEquals(0.86f, start.opacity, 0.0001f)
        assertEquals(2.4f, start.translationYDp, 0.0001f)
        assertEquals(0.988f, start.scale, 0.0001f)
        assertTrue(start.opacity < middle.opacity)
        assertTrue(middle.opacity < nearEnd.opacity)
        assertTrue(start.translationYDp > middle.translationYDp)
        assertTrue(middle.translationYDp > nearEnd.translationYDp)
        assertEquals(WordPresentation(1f, 0f, 1f, 1f), end)
    }

    @Test
    fun lyricsAvailabilityStopsSearchingAfterProvidersFinish() {
        val searching = NowPlayingUiState(
            hasActiveSession = true,
            lyricsLoadState = LyricsLoadState.SEARCHING,
        )
        assertEquals(LyricsLoadState.SEARCHING, resolvedLyricsLoadState(searching))
        assertEquals(
            LyricsLoadState.NOT_FOUND,
            resolvedLyricsLoadState(searching.copy(lyricsLoadState = LyricsLoadState.NOT_FOUND)),
        )
        assertEquals(
            LyricsLoadState.ERROR,
            resolvedLyricsLoadState(searching.copy(lyricsLoadState = LyricsLoadState.ERROR)),
        )
        assertEquals(
            LyricsLoadState.IDLE,
            resolvedLyricsLoadState(NowPlayingUiState()),
        )
        assertEquals(
            LyricsLoadState.AVAILABLE,
            resolvedLyricsLoadState(
                searching.copy(
                    lyricsLoadState = LyricsLoadState.NOT_FOUND,
                    lyrics = listOf(
                        LyricLineUi(
                            id = "line",
                            words = listOf(KaraokeWordUi(id = "word", text = "Visible")),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun organicRevealIsNonLinearButKeepsEveryTimedAnchorAndMonotonicity() {
        val short = listOf(TimedSegmentWindow(0L, 200L, 8))
        val long = listOf(TimedSegmentWindow(0L, 1_200L, 8))
        fun at(time: Long, end: Long, segments: List<TimedSegmentWindow>) =
            segmentedWordProgressAt(time, 0L, end, 0f, segments, true).overallProgress

        assertEquals(0f, at(0L, 200L, short), 0f)
        assertEquals(1f, at(200L, 200L, short), 0f)
        assertTrue(at(100L, 200L, short) > 0.5f)
        assertTrue(at(600L, 1_200L, long) > at(100L, 200L, short))
        for (time in 1L..1_200L) {
            assertTrue(at(time, 1_200L, long) >= at(time - 1L, 1_200L, long))
        }

        val snapped = listOf(
            TimedSegmentWindow(0L, 300L, 4),
            TimedSegmentWindow(300L, 700L, 5),
            TimedSegmentWindow(700L, 1_000L, 3),
        )
        assertEquals(4f / 12f, at(300L, 1_000L, snapped), 0.000001f)
        assertEquals(9f / 12f, at(700L, 1_000L, snapped), 0.000001f)
        for (time in 1L..1_000L) {
            assertTrue(at(time, 1_000L, snapped) >= at(time - 1L, 1_000L, snapped))
        }
    }

    @Test
    fun organicRevealStaysMonotoneAcrossExtremeDurationAndWeightRatios() {
        val durations = listOf(80L, 200L, 500L, 1_200L)
        val weights = listOf(1, 2, 4, 8)
        for (firstDuration in durations) for (secondDuration in durations) {
            for (firstWeight in weights) for (secondWeight in weights) {
                val end = firstDuration + secondDuration
                val segments = listOf(
                    TimedSegmentWindow(0L, firstDuration, firstWeight),
                    TimedSegmentWindow(firstDuration, end, secondWeight),
                )
                var previous = 0f
                val step = (end / 240L).coerceAtLeast(1L)
                var time = 0L
                while (time <= end) {
                    val current = segmentedWordProgressAt(time, 0L, end, 0f, segments, true).overallProgress
                    assertTrue(
                        "non-monotone reveal for $firstDuration/$firstWeight -> $secondDuration/$secondWeight at $time",
                        current + 0.000001f >= previous,
                    )
                    previous = current
                    time += step
                }
                assertEquals(1f, segmentedWordProgressAt(end, 0L, end, 0f, segments, true).overallProgress, 0f)
            }
        }
    }

    @Test
    fun hermiteRevealKeepsEveryAmllTimestampAndContinuousBoundaryVelocity() {
        val segments = listOf(
            TimedSegmentWindow(0L, 200L, 2),
            TimedSegmentWindow(200L, 850L, 3),
            TimedSegmentWindow(850L, 1_000L, 3),
        )
        fun at(time: Long) = segmentedWordProgressAt(time, 0L, 1_000L, 0f, segments, true).overallProgress
        assertEquals(0f, at(0L), 0f)
        assertEquals(0.25f, at(200L), 0f)
        assertEquals(0.625f, at(850L), 0f)
        assertEquals(1f, at(1_000L), 0f)
        for (time in 1L..1_000L) assertTrue(at(time) >= at(time - 1))
        for (boundary in listOf(200L, 850L)) {
            assertEquals(at(boundary) - at(boundary - 1), at(boundary + 1) - at(boundary), 0.000025f)
        }
    }

    @Test
    fun revealPreservesGapsFastSegmentsAndDefaultSharedRenderer() {
        val segments = listOf(TimedSegmentWindow(0L, 50L, 1), TimedSegmentWindow(80L, 200L, 5))
        fun at(time: Long, smooth: Boolean) = segmentedWordProgressAt(time, 0L, 200L, 0f, segments, smooth)
        for (time in 50L..80L) assertEquals(1f / 6f, at(time, true).overallProgress, 0f)
        for (time in 1L..200L) assertTrue(at(time, true).overallProgress >= at(time - 1, true).overallProgress)
        assertEquals(0f, at(-10, true).overallProgress, 0f)
        assertEquals(1f, at(200, true).overallProgress, 0f)
        assertEquals(0.5f, at(25, false).activeSegmentProgress, 0f)
        assertEquals(at(25, false), segmentedWordProgressAt(25, 0L, 200L, 0f, segments))
        assertEquals(0.5f, segmentedWordProgressAt(100, 0L, 200L, 0f, emptyList(), true).overallProgress, 0f)
    }

    @Test
    fun previousLineRetainsCompletedGlyphsWithoutChangingSourceOrReadingPlaybackTime() {
        val source = LyricLineUi(
            id = "line", originalText = "cuando", isPast = true,
            words = listOf(KaraokeWordUi("word", "cuando", progress = 0.3f, startTimeMs = 0L, endTimeMs = 800L,
                segments = listOf(KaraokeSegmentUi("cuan", "cuan", 0L, 500L), KaraokeSegmentUi("do", "do", 500L, 800L)))),
        )
        val displayed = lyricsLineForDisplay(source)
        assertFalse(source.isActive)
        assertEquals(0.3f, source.words.single().progress, 0f)
        assertEquals(source.words.single().segments, displayed.words.single().segments)
        assertEquals(source.originalText, displayed.originalText)
        val semantic = resolveSemanticWordEntrances(displayed).single()
        val progress = segmentedWordProgressAt(null, semantic.startTimeMs, semantic.endTimeMs,
            semantic.fallbackProgress, listOf(TimedSegmentWindow(0L, 500L, 4), TimedSegmentWindow(500L, 800L, 2)), true)
        assertEquals(1f, progress.overallProgress, 0f)
        assertEquals(1f, progress.progressForSegment(0), 0f)
        assertEquals(1f, progress.progressForSegment(1), 0f)
        val current = source.copy(isPast = false, isActive = true)
        assertSame(current, lyricsLineForDisplay(current))
    }

    @Test
    fun exportTwentyTransitionMotionTrace() {
        val directory = System.getProperty("motion.validation.dir")?.let(::File) ?: return
        directory.mkdirs()
        val traces = listOf(60, 120).map { hz ->
            val follower = LyricsScrollFollower()
            var position = 0f
            var target = 0f
            var elapsed = 0f
            val samples = mutableListOf<FloatArray>()
            repeat(20) { cue ->
                target += listOf(72f, 152f, 240f)[cue % 3]
                repeat((hz * listOf(0.25f, 0.5f, 1.5f)[cue % 3]).toInt()) {
                    position += follower.step(target - position, 1f / hz)
                    elapsed += 1f / hz
                    samples += floatArrayOf(elapsed, target, position, follower.velocity)
                }
            }
            File(directory, "twenty-cues-${hz}hz.csv").writeText("seconds,targetPx,positionPx,velocityPxPerSecond\n" +
                samples.joinToString("\n") { sample -> sample.joinToString(",") })
            samples
        }
        val image = BufferedImage(1_200, 700, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(22, 23, 27); g.fillRect(0, 0, image.width, image.height)
        g.color = Color.WHITE
        g.drawString("20 cue changes | production follower model (not Android rendering)", 35, 30)
        val duration = traces.last().last()[0]
        fun plot(samples: List<FloatArray>, column: Int, maximum: Float, y: Int, color: Color) {
            g.color = color
            for (i in 1 until samples.size) {
                val a = samples[i - 1]; val b = samples[i]
                g.drawLine((35 + a[0] / duration * 1_130).toInt(), (y - a[column] / maximum * 230).toInt(),
                    (35 + b[0] / duration * 1_130).toInt(), (y - b[column] / maximum * 230).toInt())
            }
        }
        plot(traces[0], 1, 3_200f, 300, Color(85, 87, 98))
        plot(traces[0], 2, 3_200f, 300, Color(255, 160, 128))
        plot(traces[1], 2, 3_200f, 300, Color(112, 216, 219))
        plot(traces[0], 3, 1_100f, 630, Color(255, 160, 128))
        plot(traces[1], 3, 1_100f, 630, Color(112, 216, 219))
        g.color = Color.WHITE
        g.drawString("Position: target gray | 60 Hz salmon | 120 Hz cyan", 35, 330)
        g.drawString("Velocity: retargets retain momentum; long phrases settle", 35, 665)
        g.dispose()
        ImageIO.write(image, "png", File(directory, "twenty-cues-motion.png"))
    }

    @Test
    fun exportOrganicWordRevealTrace() {
        val directory = System.getProperty("motion.validation.dir")?.let(::File) ?: return
        directory.mkdirs()
        val durations = listOf(200L, 500L, 1_200L)
        val colors = listOf(Color(255, 160, 128), Color(255, 213, 112), Color(112, 216, 219))
        val image = BufferedImage(1_200, 760, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(22, 23, 27); g.fillRect(0, 0, image.width, image.height)
        g.color = Color.WHITE
        g.drawString("WORD_SYNCED organic reveal | gray = linear reference", 35, 30)

        g.color = Color(74, 76, 84)
        g.drawLine(50, 330, 570, 70)
        durations.forEachIndexed { index, duration ->
            val segments = listOf(TimedSegmentWindow(0L, duration, 8))
            g.color = colors[index]
            var previousX = 50
            var previousY = 330
            for (step in 1..240) {
                val time = duration * step / 240
                val progress = segmentedWordProgressAt(time, 0L, duration, 0f, segments, true).overallProgress
                val x = 50 + (520f * step / 240f).toInt()
                val y = 330 - (260f * progress).toInt()
                g.drawLine(previousX, previousY, x, y)
                previousX = x; previousY = y
            }
            g.drawString("${duration}ms", 70 + index * 130, 365)
        }

        val snapped = listOf(
            TimedSegmentWindow(0L, 260L, 2),
            TimedSegmentWindow(260L, 610L, 3),
            TimedSegmentWindow(610L, 1_000L, 4),
        )
        fun snappedAt(time: Long) = segmentedWordProgressAt(time, 0L, 1_000L, 0f, snapped, true).overallProgress
        g.color = Color(74, 76, 84)
        g.drawLine(640, 330, 1_160, 70)
        for (boundary in listOf(260, 610)) {
            val x = 640 + (520f * boundary / 1_000f).toInt()
            g.drawLine(x, 55, x, 350)
        }
        g.color = Color(189, 150, 255)
        var previousX = 640
        var previousY = 330
        val rows = ArrayList<String>(1_001)
        rows += "timeMs,progress,velocityPerSecond"
        for (time in 1L..1_000L) {
            val progress = snappedAt(time)
            val velocity = (progress - snappedAt(time - 1L)) * 1_000f
            rows += "$time,$progress,$velocity"
            val x = 640 + (520f * time / 1_000f).toInt()
            val y = 330 - (260f * progress).toInt()
            g.drawLine(previousX, previousY, x, y)
            previousX = x; previousY = y
        }
        g.drawString("3 timed segments: anchors remain exact", 660, 365)

        g.color = Color(74, 76, 84)
        g.drawLine(640, 690, 1_160, 690)
        g.color = Color(189, 150, 255)
        previousX = 640
        previousY = 690
        for (time in 1L..1_000L) {
            val velocity = (snappedAt(time) - snappedAt(time - 1L)) * 1_000f
            val x = 640 + (520f * time / 1_000f).toInt()
            val y = 690 - (velocity.coerceIn(0f, 2f) / 2f * 240f).toInt()
            g.drawLine(previousX, previousY, x, y)
            previousX = x; previousY = y
        }
        g.color = Color.WHITE
        g.drawString("Velocity stays continuous through both snap boundaries", 660, 725)
        g.dispose()
        File(directory, "word-reveal-organic.csv").writeText(rows.joinToString("\n"))
        ImageIO.write(image, "png", File(directory, "word-reveal-organic.png"))
    }
}

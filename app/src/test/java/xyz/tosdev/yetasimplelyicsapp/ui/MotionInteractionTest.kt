package xyz.tosdev.yetasimplelyicsapp.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.components.karaokeProgressAt
import xyz.tosdev.yetasimplelyicsapp.ui.components.lyricsLinePresentation
import xyz.tosdev.yetasimplelyicsapp.ui.components.resolveSemanticWordEntrances
import xyz.tosdev.yetasimplelyicsapp.ui.components.segmentedWordProgressAt
import xyz.tosdev.yetasimplelyicsapp.ui.components.TimedSegmentWindow
import xyz.tosdev.yetasimplelyicsapp.ui.components.wordPresentationAt
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.AMBIENT_PHASE_DURATIONS_MS
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.ambientCycleDurationMillis
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.ambientMotionProfile
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.ambientTrackDurationMillis
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.ambientTrackVariation
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.signalLiquidGlassTones
import androidx.compose.ui.graphics.Color

class MotionInteractionTest {
    @Test
    fun syllableTimedWordProducesOneSemanticEntrance() {
        val word = KaraokeWordUi(
            id = "empiezas",
            text = "empiezas",
            startTimeMs = 1_000L,
            endTimeMs = 2_000L,
            segments = listOf(
                KaraokeSegmentUi("em", "em", 1_000L, 1_200L),
                KaraokeSegmentUi("pie", "pie", 1_200L, 1_650L),
                KaraokeSegmentUi("zas", "zas", 1_650L, 2_000L),
            ),
        )
        val entrances = resolveSemanticWordEntrances(
            LyricLineUi(
                id = "line",
                originalText = "empiezas",
                words = listOf(word),
                isActive = true,
            ),
        )

        assertEquals(1, entrances.size)
        assertEquals("empiezas", entrances.single().id)
        assertEquals(1_000L, entrances.single().startTimeMs)
        assertEquals(2_000L, entrances.single().endTimeMs)
        assertEquals(0, entrances.single().startOffset)
        assertEquals("empiezas".length, entrances.single().endOffset)
    }

    @Test
    fun repeatedWordsKeepSequentialRangesAndSourceIdentity() {
        val line = LyricLineUi(
            id = "repeat",
            originalText = "go go",
            words = listOf(
                KaraokeWordUi("first", "go", startTimeMs = 0L, endTimeMs = 200L),
                KaraokeWordUi("second", "go", startTimeMs = 200L, endTimeMs = 400L),
            ),
            isActive = true,
        )

        val entrances = resolveSemanticWordEntrances(line)

        assertEquals(listOf(0, 1), entrances.map { it.sourceWordIndex })
        assertEquals(listOf(0, 3), entrances.map { it.startOffset })
        assertEquals(listOf(3, 5), entrances.map { it.endOffset })
    }

    @Test
    fun wordMotionSpansTheWholeTimedInterval() {
        val startProgress = karaokeProgressAt(1_000L, 1_000L, 2_000L, 0f)
        val middleProgress = karaokeProgressAt(1_500L, 1_000L, 2_000L, 0f)
        val endProgress = karaokeProgressAt(2_000L, 1_000L, 2_000L, 0f)

        val start = wordPresentationAt(startProgress, MotionLevel.NORMAL)
        val middle = wordPresentationAt(middleProgress, MotionLevel.NORMAL)
        val end = wordPresentationAt(endProgress, MotionLevel.NORMAL)

        assertEquals(0.94f, start.opacity, 0.001f)
        assertEquals(1.1f, start.translationYDp, 0.001f)
        assertEquals(0.996f, start.scale, 0.001f)
        assertEquals(1f, middle.opacity, 0.001f)
        assertTrue(middle.translationYDp in 0f..0.01f)
        assertEquals(1f, middle.scale, 0.001f)
        assertEquals(1f, end.opacity, 0.001f)
        assertEquals(-0.35f, end.translationYDp, 0.001f)
        assertEquals(1f, end.scale, 0.001f)
    }

    @Test
    fun amllSegmentedProgressRespectsEveryInternalSnap() {
        val segments = listOf(
            TimedSegmentWindow(1_000L, 1_350L, visualWeight = 4),
            TimedSegmentWindow(1_350L, 1_700L, visualWeight = 4),
        )

        val firstSnap = segmentedWordProgressAt(1_175L, 1_000L, 1_700L, 0f, segments)
        val secondSnap = segmentedWordProgressAt(1_525L, 1_000L, 1_700L, 0f, segments)

        assertEquals(0.25f, firstSnap.overallProgress, 0.001f)
        assertEquals(0.5f, firstSnap.progressForSegment(0), 0.001f)
        assertEquals(0f, firstSnap.progressForSegment(1), 0.001f)
        assertEquals(0.75f, secondSnap.overallProgress, 0.001f)
        assertEquals(1f, secondSnap.progressForSegment(0), 0.001f)
        assertEquals(0.5f, secondSnap.progressForSegment(1), 0.001f)
    }

    @Test
    fun wordWithoutInternalSegmentsUsesWordIntervalFallback() {
        val progress = segmentedWordProgressAt(
            positionMs = 1_500L,
            wordStartTimeMs = 1_000L,
            wordEndTimeMs = 2_000L,
            fallbackProgress = 0f,
            segments = emptyList(),
        )

        assertFalse(progress.usesSegments)
        assertEquals(0.5f, progress.overallProgress, 0.001f)
    }

    @Test
    fun amllSnapBoundaryFeedsOneContinuousWordMotion() {
        val segments = listOf(
            TimedSegmentWindow(1_000L, 1_350L, visualWeight = 4),
            TimedSegmentWindow(1_350L, 1_700L, visualWeight = 4),
        )
        val beforeBoundary = segmentedWordProgressAt(1_349L, 1_000L, 1_700L, 0f, segments)
        val atBoundary = segmentedWordProgressAt(1_350L, 1_000L, 1_700L, 0f, segments)
        val beforePresentation = wordPresentationAt(beforeBoundary.overallProgress, MotionLevel.NORMAL)
        val atPresentation = wordPresentationAt(atBoundary.overallProgress, MotionLevel.NORMAL)

        assertTrue(atBoundary.overallProgress >= beforeBoundary.overallProgress)
        assertTrue(atBoundary.overallProgress - beforeBoundary.overallProgress < 0.01f)
        assertTrue(kotlin.math.abs(atPresentation.translationYDp - beforePresentation.translationYDp) < 0.01f)
        assertTrue(kotlin.math.abs(atPresentation.scale - beforePresentation.scale) < 0.001f)
    }

    @Test
    fun ambientMotionLevelsRemainSubtleAndRespectOff() {
        val off = ambientMotionProfile(MotionLevel.OFF)
        val reduced = ambientMotionProfile(MotionLevel.REDUCED)
        val normal = ambientMotionProfile(MotionLevel.NORMAL)
        val enhanced = ambientMotionProfile(MotionLevel.ENHANCED)

        assertEquals(0f, off.driftFraction, 0f)
        assertEquals(0f, off.radiusBreath, 0f)
        assertEquals(0f, off.alphaBreath, 0f)
        assertEquals(0.003f, reduced.driftFraction, 0f)
        assertEquals(0.003f, reduced.radiusBreath, 0f)
        assertEquals(0.006f, reduced.alphaBreath, 0f)
        assertEquals(0.09f, normal.driftFraction, 0f)
        assertEquals(0.055f, normal.radiusBreath, 0f)
        assertEquals(0.065f, normal.alphaBreath, 0f)
        assertEquals(0.12f, enhanced.driftFraction, 0f)
        assertEquals(0.075f, enhanced.radiusBreath, 0f)
        assertEquals(0.09f, enhanced.alphaBreath, 0f)
        assertEquals(listOf(14_000, 18_000, 21_000, 31_500), AMBIENT_PHASE_DURATIONS_MS)
        assertEquals(126_000, ambientCycleDurationMillis(MotionLevel.NORMAL))
        assertEquals(126_000, ambientCycleDurationMillis(MotionLevel.ENHANCED))
        assertEquals(252_000, ambientCycleDurationMillis(MotionLevel.REDUCED))
        assertEquals(700, ambientTrackDurationMillis(MotionLevel.OFF, motionEnabled = false))
        assertEquals(800, ambientTrackDurationMillis(MotionLevel.REDUCED, motionEnabled = true))
        assertEquals(900, ambientTrackDurationMillis(MotionLevel.NORMAL, motionEnabled = true))
        assertEquals(1_100, ambientTrackDurationMillis(MotionLevel.ENHANCED, motionEnabled = true))
        assertEquals(0, ambientTrackDurationMillis(MotionLevel.NORMAL, motionEnabled = false))
    }

    @Test
    fun artworkKeyProducesBoundedDeterministicAmbientLayout() {
        val first = ambientTrackVariation(0x12345678)
        val same = ambientTrackVariation(0x12345678)
        val other = ambientTrackVariation(0x76543210)

        assertEquals(first, same)
        assertTrue(first.horizontalBias in -0.04f..0.04f)
        assertTrue(first.verticalBias in -0.03f..0.03f)
        assertTrue(first.radiusScale in 0.94f..1.06f)
        assertTrue(first.energy in 0.90f..1.08f)
        assertTrue(first != other)
    }

    @Test
    fun lyricDepthUsesSubtleMotionAndReducedModeKeepsOnlyOpacity() {
        val current = lyricsLinePresentation(4, 4, isActive = true, motionLevel = MotionLevel.NORMAL)
        val previous = lyricsLinePresentation(3, 4, isActive = false, motionLevel = MotionLevel.NORMAL)
        val next = lyricsLinePresentation(5, 4, isActive = false, motionLevel = MotionLevel.NORMAL)
        val reduced = lyricsLinePresentation(5, 4, isActive = false, motionLevel = MotionLevel.REDUCED)

        assertEquals(1f, current.alpha, 0f)
        assertEquals(1f, current.scale, 0f)
        assertEquals(0f, previous.translationYDp, 0f)
        assertEquals(0f, next.translationYDp, 0f)
        assertEquals(0.72f, previous.alpha, 0f)
        assertEquals(0.72f, next.alpha, 0f)
        assertEquals(0.993f, previous.scale, 0f)
        assertEquals(0f, reduced.translationYDp, 0f)
        assertEquals(1f, reduced.scale, 0f)
    }

    @Test
    fun liquidGlassStaysTranslucentAndRespondsToArtworkAccent() {
        val surface = Color(0xFF24272B)
        val warm = signalLiquidGlassTones(surface, Color(0xFFFF7043), dark = true,
            emphasized = true, selected = false)
        val cool = signalLiquidGlassTones(surface, Color(0xFF536DFE), dark = true,
            emphasized = true, selected = false)
        val light = signalLiquidGlassTones(Color(0xFFF4F2EE), Color(0xFF006B5F), dark = false,
            emphasized = false, selected = false)
        val subtleNavigation = signalLiquidGlassTones(surface, Color(0xFFFF7043), dark = true,
            emphasized = true, selected = false, subtle = true)

        assertNotEquals(warm.fill, cool.fill)
        assertTrue(warm.fill.alpha in 0.50f..0.70f)
        assertTrue(light.fill.alpha in 0.65f..0.82f)
        assertTrue(warm.specular.alpha > warm.edge.alpha)
        assertTrue(warm.accentWash.alpha < warm.fill.alpha)
        assertTrue(subtleNavigation.fill.alpha < warm.fill.alpha)
        assertTrue(subtleNavigation.edge.alpha < warm.edge.alpha)
    }

    @Test
    fun dynamicArtworkBackgroundBelongsOnlyToNowPlayingRoot() {
        assertTrue(usesDynamicArtworkBackground(AppDestination.NOW_PLAYING))
        assertFalse(usesDynamicArtworkBackground(AppDestination.OVERLAY))
        assertFalse(usesDynamicArtworkBackground(AppDestination.STUDIO))
    }

    @Test
    fun playbackToggleCapabilityUsesTheCurrentTransportState() {
        assertTrue(canTogglePlayback(isPlaying = false, canPlay = true, canPause = false))
        assertTrue(canTogglePlayback(isPlaying = true, canPlay = false, canPause = true))
        assertFalse(canTogglePlayback(isPlaying = true, canPlay = true, canPause = false))
        assertFalse(canTogglePlayback(isPlaying = false, canPlay = false, canPause = true))
    }
}

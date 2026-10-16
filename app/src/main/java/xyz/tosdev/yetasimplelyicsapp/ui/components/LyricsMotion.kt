package xyz.tosdev.yetasimplelyicsapp.ui.components

import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

/** Relative-coordinate critically damped follower. Retargeting does not discard velocity. */
internal class LyricsScrollFollower {
    var velocity = 0f
        private set

    fun reset() { velocity = 0f }

    fun step(errorPx: Float, elapsedSeconds: Float, reduced: Boolean = false): Float {
        val dt = elapsedSeconds.coerceIn(0f, 0.05f)
        if (dt == 0f) return 0f
        if (abs(errorPx) < 0.15f && abs(velocity) < 0.5f) {
            velocity = 0f
            return 0f
        }
        val omega = if (reduced) 24f else 8f
        val displacement = -errorPx
        val coefficient = velocity + omega * displacement
        val decay = exp(-omega * dt)
        val nextDisplacement = (displacement + coefficient * dt) * decay
        velocity = (velocity - omega * coefficient * dt) * decay
        return errorPx + nextDisplacement
    }
}

internal fun lyricAnticipation(
    positionMs: Long?, startMs: Long?, nextStartMs: Long?, level: MotionLevel,
): Float {
    if (positionMs == null || startMs == null || nextStartMs == null || nextStartMs <= startMs ||
        level == MotionLevel.OFF || level == MotionLevel.REDUCED) return 0f
    val window = ((nextStartMs - startMs) * 0.46f).coerceIn(180f, 900f)
    val progress = ((positionMs - (nextStartMs - window)) / window).coerceIn(0f, 1f)
    return smoothFocus(progress)
}

/**
 * Moves the current-line target toward the next measured line without leaving the focus band.
 * The caller keeps one follower alive, so a cue change retargets from the current position and
 * velocity instead of starting another animation.
 */
internal fun lyricsContinuousTargetError(
    currentErrorPx: Float,
    nextErrorPx: Float?,
    anticipation: Float,
    viewportHeightPx: Int,
): Float {
    if (nextErrorPx == null || viewportHeightPx <= 0 || anticipation <= 0f) return currentErrorPx
    val distanceToNext = (nextErrorPx - currentErrorPx).coerceAtLeast(0f)
    val desiredLead = distanceToNext * 0.18f * anticipation.coerceIn(0f, 1f)
    val boundedLead = desiredLead.coerceAtMost(viewportHeightPx * 0.02f)
    return currentErrorPx + boundedLead
}

internal fun lyricsViewportPresentation(
    distancePx: Float, viewportHeightPx: Int, level: MotionLevel,
): LyricsLinePresentation {
    val distanceInLines = abs(distancePx) /
        (viewportHeightPx * LYRICS_DEPTH_STEP_FRACTION).coerceAtLeast(1f)
    val depth = lyricsDistancePresentation(distanceInLines, level)
    return depth.copy(
        translationYDp = 0f,
    )
}

internal fun lyricsDistancePresentation(
    distanceInLines: Float,
    level: MotionLevel,
): LyricsLinePresentation {
    val distance = distanceInLines.coerceAtLeast(0f)
    val low = floor(distance).toInt().coerceIn(0, LYRICS_DEPTH_ALPHA.lastIndex)
    val high = (low + 1).coerceAtMost(LYRICS_DEPTH_ALPHA.lastIndex)
    val fraction = smoothFocus((distance - floor(distance)).coerceIn(0f, 1f))
    val alpha = lerp(LYRICS_DEPTH_ALPHA[low], LYRICS_DEPTH_ALPHA[high], fraction)
    val scale = if (level == MotionLevel.OFF || level == MotionLevel.REDUCED) 1f else {
        lerp(LYRICS_DEPTH_SCALE[low], LYRICS_DEPTH_SCALE[high], fraction)
    }
    return LyricsLinePresentation(alpha = alpha, translationYDp = 0f, scale = scale)
}

internal data class LyricsItemGeometry(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

/** Estimates an offscreen cue from the currently measured row cadence, then refines when visible. */
internal fun lyricTargetErrorPx(
    targetIndex: Int,
    visibleItems: List<LyricsItemGeometry>,
    viewportStartOffsetPx: Int,
    viewportHeightPx: Int,
    leadPx: Float = 0f,
): Float? {
    if (visibleItems.isEmpty() || viewportHeightPx <= 0) return null
    val sorted = visibleItems.sortedBy(LyricsItemGeometry::index)
    val exact = sorted.firstOrNull { it.index == targetIndex }
    val averageSize = sorted.map { it.sizePx.coerceAtLeast(1) }.average().toFloat().coerceAtLeast(1f)
    val measuredSpans = sorted.zipWithNext().mapNotNull { (first, second) ->
        val indexDelta = second.index - first.index
        if (indexDelta <= 0) null else (second.offsetPx - first.offsetPx).toFloat() / indexDelta
    }.filter { it > 0f }
    val span = if (measuredSpans.isEmpty()) averageSize
    else measuredSpans.average().toFloat().coerceAtLeast(1f)
    val estimatedTop = when {
        exact != null -> exact.offsetPx.toFloat()
        targetIndex < sorted.first().index ->
            sorted.first().offsetPx - span * (sorted.first().index - targetIndex)
        else -> sorted.last().offsetPx + span * (targetIndex - sorted.last().index)
    }
    val estimatedSize = exact?.sizePx ?: averageSize.roundToInt()
    return lyricViewportTop(estimatedTop.roundToInt(), viewportStartOffsetPx) -
        lyricFocusOffsetPx(viewportHeightPx, estimatedSize) + leadPx
}

/** LazyList offsets exclude before-content padding; viewportStartOffset is negative there. */
internal fun lyricViewportTop(itemOffset: Int, viewportStartOffset: Int): Int =
    itemOffset - viewportStartOffset

internal fun lyricsPlaybackDiscontinuity(previous: Long?, current: Long?, elapsedSeconds: Float): Boolean {
    if (previous == null || current == null) return false
    val delta = current - previous
    return delta < -80L || delta > maxOf(450f, elapsedSeconds * 3_000f).toLong()
}

/** Word motion uses the entire word interval; segment boundaries only affect the reveal. */
internal fun refinedWordPresentationAt(
    progress: Float,
    level: MotionLevel,
    motionAmount: Float = 1f,
    durationMs: Long? = null,
): WordPresentation {
    val p = progress.coerceIn(0f, 1f)
    val arrival = smoothFocus(p)
    val amount = motionAmount.coerceIn(0f, 1f) * wordMotionDurationFactor(durationMs)
    if (level == MotionLevel.OFF) return WordPresentation(1f, 0f, 1f, 1f)
    val depth = (1f - arrival) * amount
    val opacityDepth = when (level) {
        MotionLevel.REDUCED -> 0.10f
        MotionLevel.ENHANCED -> 0.17f
        else -> 0.14f
    }
    val opacity = 1f - opacityDepth * depth
    if (level == MotionLevel.REDUCED) return WordPresentation(opacity, 0f, 1f, 1f)
    val translationAmplitude = if (level == MotionLevel.ENHANCED) 3f else 2.4f
    val scaleDepth = if (level == MotionLevel.ENHANCED) 0.015f else 0.012f
    val trackingDepth = if (level == MotionLevel.ENHANCED) 0.005f else 0.004f
    return WordPresentation(
        opacity,
        depth * translationAmplitude,
        1f - scaleDepth * depth,
        1f - trackingDepth * depth,
    )
}

/** Very short words emphasize reveal and suppress secondary movement to avoid flicker. */
internal fun wordMotionDurationFactor(durationMs: Long?): Float {
    if (durationMs == null) return 1f
    val normalized = ((durationMs.coerceAtLeast(0L) - 120L) / 700f).coerceIn(0f, 1f)
    return lerp(0.38f, 1f, smoothFocus(normalized))
}

/** Monotone Hermite interpolation preserves every original timestamp/weight knot. */
internal fun smoothSegmentProgress(segments: List<TimedSegmentWindow>, index: Int, linear: Float): Float {
    val segment = segments[index]
    val duration = ((segment.endTimeMs ?: 0L) - (segment.startTimeMs ?: 0L)).coerceAtLeast(1L).toFloat()
    val rate = segment.visualWeight / duration
    fun rateOf(other: TimedSegmentWindow) = other.visualWeight /
        ((other.endTimeMs ?: 0L) - (other.startTimeMs ?: 0L)).coerceAtLeast(1L).toFloat()
    fun sharedRate(other: TimedSegmentWindow?, contiguous: Boolean): Float = when {
        other == null -> rate
        !contiguous -> 0f
        else -> 2f * rate * rateOf(other) / (rate + rateOf(other)).coerceAtLeast(0.000001f)
    }
    val previous = segments.getOrNull(index - 1)
    val next = segments.getOrNull(index + 1)
    val startSlope = sharedRate(previous, previous?.endTimeMs == segment.startTimeMs) / rate
    val endSlope = sharedRate(next, segment.endTimeMs == next?.startTimeMs) / rate
    val t = linear.coerceIn(0f, 1f)
    val t2 = t * t
    val t3 = t2 * t
    val anchored = ((t3 - 2f * t2 + t) * startSlope + (-2f * t3 + 3f * t2) +
        (t3 - t2) * endSlope).coerceIn(0f, 1f)

    // A symmetric sixth-order arc has zero velocity and acceleration at both timestamps. It makes
    // equal-rate segments feel musical instead of linear while preserving position and velocity
    // at every AMLL snap. Longer segments receive more expression; fast syllables stay restrained.
    val durationFactor = smoothFocus(((duration - 120f) / 700f).coerceIn(0f, 1f))
    val strength = lerp(0.014f, 0.075f, durationFactor)
    val inverse = 1f - t
    val musicalArc = 64f * t3 * inverse * inverse * inverse
    val offset = (strength * musicalArc).coerceAtMost((1f - anchored) * 0.45f)
    return (anchored + offset).coerceIn(0f, 1f)
}

private fun smoothFocus(value: Float): Float = value * value * value * (value * (value * 6f - 15f) + 10f)

private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

private val LYRICS_DEPTH_ALPHA = floatArrayOf(1f, 0.72f, 0.50f, 0.34f, 0.26f)
private val LYRICS_DEPTH_SCALE = floatArrayOf(1f, 0.993f, 0.985f, 0.979f, 0.975f)
private const val LYRICS_DEPTH_STEP_FRACTION = 0.14f

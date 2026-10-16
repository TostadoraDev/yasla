package xyz.tosdev.yetasimplelyicsapp.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel

internal data class AmllRowFrame(
    val topPx: Float,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val blurDp: Float = 0f,
    val brightAlpha: Float = 0.2f,
    val darkAlpha: Float = 0.2f,
    val exitMotionMs: Float = 0f,
)

/** One viewport clock updates visible rows; composables read frames only during drawing. */
@Stable
internal class AmllRowMotion(initialTopPx: Float) {
    val position = AmllSpring(initialTopPx, 0.9f, 90f, 15f)
    private val scale = AmllSpring(100f, 2f, 100f, 25f)
    var frame by mutableStateOf(AmllRowFrame(initialTopPx))
        private set
    var active = false
        private set
    private var wasActive = false
    private var lastWordTime: Long? = null
    private var exitTimeMs = 0f
    private var opacityFrom = 1f
    private var opacityTarget = 1f
    private var opacityTime = 0.4f
    private var blurFrom = 0f
    private var blurTarget = 0f
    private var blurTime = 0.4f
    private var requestedTop = initialTopPx
    private var previousStiffness = 90f
    private var previousDamping = 15f
    private var wordMotionTailMs = 0f

    fun retarget(
        top: Float,
        isActive: Boolean,
        isPlaying: Boolean,
        dynamic: Boolean,
        blurDp: Float,
        stiffness: Float,
        damping: Float,
        delay: Float,
        level: MotionLevel,
        motionTailMs: Float = 0f,
    ) {
        wordMotionTailMs = motionTailMs
        if (stiffness != previousStiffness || damping != previousDamping) {
            position.setParameters(0.9f, stiffness, damping)
            previousStiffness = stiffness
            previousDamping = damping
            requestedTop = Float.NaN
        }
        if (requestedTop.isNaN() || kotlin.math.abs(requestedTop - top) > 0.05f) {
            position.retarget(top, delay)
            requestedTop = top
        }
        if (isActive != active) {
            if (!isActive) exitTimeMs = 0f
            active = isActive
            wasActive = wasActive || isActive
        }
        val targetScale = if (isActive || !isPlaying || level == MotionLevel.OFF ||
            level == MotionLevel.REDUCED) 100f else 97f
        scale.retarget(targetScale)
        val opacity = if (isActive) 0.85f else if (dynamic) 1f else 0.2f
        if (opacity != opacityTarget) {
            opacityFrom = frame.opacity
            opacityTarget = opacity
            opacityTime = 0f
        }
        val blur = if (level == MotionLevel.OFF || level == MotionLevel.REDUCED) 0f else blurDp
        if (blur != blurTarget) {
            blurFrom = frame.blurDp
            blurTarget = blur
            blurTime = 0f
        }
    }

    fun step(seconds: Float, now: Long?, dynamic: Boolean, level: MotionLevel) {
        if (active && now != null) lastWordTime = now
        if (!active && wasActive) exitTimeMs = (exitTimeMs + seconds * 1_000f).coerceAtMost(wordMotionTailMs)
        val immediate = level == MotionLevel.OFF
        if (immediate) {
            position.snap(position.target)
            scale.snap(scale.target)
        } else {
            position.step(seconds)
            scale.step(seconds)
        }
        opacityTime = (opacityTime + seconds).coerceAtMost(0.4f)
        blurTime = (blurTime + seconds).coerceAtMost(0.4f)
        val opacityProgress = if (immediate) 1f else amllEase(opacityTime / 0.4f)
        val blurProgress = if (immediate) 1f else amllEase(blurTime / 0.4f)
        val alphaTargets = amllMaskTargets(scale.position / 100f, active && dynamic)
        frame = AmllRowFrame(
            topPx = position.position,
            scale = scale.position / 100f,
            opacity = opacityFrom + (opacityTarget - opacityFrom) * opacityProgress,
            blurDp = blurFrom + (blurTarget - blurFrom) * blurProgress,
            brightAlpha = if (immediate) alphaTargets.bright else
                amllApproachAlpha(frame.brightAlpha, alphaTargets.bright, seconds),
            darkAlpha = if (immediate) alphaTargets.dark else
                amllApproachAlpha(frame.darkAlpha, alphaTargets.dark, seconds),
            exitMotionMs = exitTimeMs,
        )
    }

    fun wordPosition(now: Long?): Long? = when {
        active -> now
        wasActive -> lastWordTime?.minus(exitTimeMs.toLong())
        else -> null
    }

    fun frozenWordPosition(): Long? = lastWordTime

    // Only the baseline float reverses on exit. Character emphasis keeps moving forward.
    fun emphasisPosition(now: Long?): Long? = when {
        active -> now
        wasActive -> lastWordTime?.plus(exitTimeMs.toLong())
        else -> null
    }

    fun baselineElapsed(now: Long?, startMs: Long, durationMs: Long): Long? = when {
        active -> now?.minus(startMs)
        // A finished WAAPI float stops at its own end time, not at the line's media time.
        wasActive -> lastWordTime?.minus(startMs)
            ?.coerceIn(0L, durationMs.coerceAtLeast(1_000L))?.minus(exitTimeMs.toLong())
        else -> null
    }

    fun resetWordClock() {
        active = false
        wasActive = false
        lastWordTime = null
        exitTimeMs = 0f
    }
}

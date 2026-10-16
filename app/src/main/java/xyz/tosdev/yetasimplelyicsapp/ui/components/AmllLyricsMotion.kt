package xyz.tosdev.yetasimplelyicsapp.ui.components

import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AmllMaskAlphas(
    val bright: Float,
    val dark: Float,
)

internal data class AmllWordPose(
    val translationXPx: Float,
    val translationYPx: Float,
    val scale: Float,
    val glowRadiusPx: Float,
    val glowAlpha: Float,
)

/** Analytic spring used by the native lyrics viewport. Retargeting keeps momentum. */
internal class AmllSpring(
    initialPosition: Float,
    mass: Float = 0.9f,
    stiffness: Float = 90f,
    damping: Float = 15f,
) {
    private var springMass = mass.toDouble()
    private var springStiffness = stiffness.toDouble()
    private var springDamping = damping.toDouble()

    private var segmentStart = initialPosition.toDouble()
    private var segmentVelocity = 0.0
    private var segmentTime = 0.0
    private var currentPosition = initialPosition.toDouble()
    private var currentVelocity = 0.0
    private var currentTarget = initialPosition.toDouble()

    private var queuedTarget: Double? = null
    private var queuedDelay = 0.0

    val position: Float
        get() = currentPosition.toFloat()

    val velocity: Float
        get() = currentVelocity.toFloat()

    val target: Float
        get() = currentTarget.toFloat()

    fun snap(position: Float) {
        val snapped = position.toDouble()
        currentPosition = snapped
        currentVelocity = 0.0
        currentTarget = snapped
        segmentStart = snapped
        segmentVelocity = 0.0
        segmentTime = 0.0
    }

    fun retarget(target: Float, delaySeconds: Float = 0f) {
        if (delaySeconds > 0f) {
            queuedTarget = target.toDouble()
            queuedDelay = delaySeconds.toDouble()
            return
        }

        queuedTarget = null
        activateTarget(target.toDouble())
    }

    fun setParameters(mass: Float, stiffness: Float, damping: Float) {
        springMass = mass.toDouble()
        springStiffness = stiffness.toDouble()
        springDamping = damping.toDouble()
        queuedTarget = null
        beginSegment()
    }

    fun step(seconds: Float): Float {
        val delta = seconds.toDouble().coerceAtLeast(0.0)
        if (delta == 0.0) return position

        segmentTime += delta
        val state = stateAt(segmentTime)
        currentPosition = state.position
        currentVelocity = state.velocity

        queuedTarget?.let { pending ->
            queuedDelay -= delta
            if (queuedDelay <= 0.0) {
                queuedTarget = null
                activateTarget(pending)
            }
        }
        return position
    }

    private fun activateTarget(target: Double) {
        currentTarget = target
        beginSegment()
    }

    private fun beginSegment() {
        segmentStart = currentPosition
        segmentVelocity = currentVelocity
        segmentTime = 0.0
    }

    private fun stateAt(seconds: Double): SpringState {
        val distance = currentTarget - segmentStart
        val naturalFrequency = sqrt(springStiffness / springMass)
        val dampingRatio = springDamping / (2.0 * sqrt(springStiffness * springMass))

        if (dampingRatio >= 1.0) {
            val linearCoefficient = naturalFrequency * distance - segmentVelocity
            val decay = exp(-naturalFrequency * seconds)
            val decayingDistance = distance + linearCoefficient * seconds
            return SpringState(
                position = currentTarget - decayingDistance * decay,
                velocity = (naturalFrequency * decayingDistance - linearCoefficient) * decay,
            )
        }

        val dampedRoot = sqrt(4.0 * springMass * springStiffness - springDamping * springDamping)
        val angularFrequency = dampedRoot / (2.0 * springMass)
        val decayRate = -springDamping / (2.0 * springMass)
        val sineCoefficient =
            (springDamping * distance - 2.0 * springMass * segmentVelocity) / dampedRoot
        val angle = angularFrequency * seconds
        val oscillation = distance * cos(angle) + sineCoefficient * sin(angle)
        val oscillationVelocity = angularFrequency *
            (-distance * sin(angle) + sineCoefficient * cos(angle))
        val decay = exp(decayRate * seconds)
        return SpringState(
            position = currentTarget - oscillation * decay,
            velocity = -(oscillationVelocity + decayRate * oscillation) * decay,
        )
    }

    private data class SpringState(val position: Double, val velocity: Double)
}

internal fun amllVerticalStiffness(intervalMs: Long): Float {
    val interval = intervalMs.coerceIn(100L, 800L)
    val inverseInterval = 1.0 - (interval - 100L).toDouble() / 700.0
    return (170.0 + 50.0 * inverseInterval.pow(0.2)).toFloat()
}

internal fun amllMaskTargets(scale: Float, progressive: Boolean): AmllMaskAlphas {
    val factor = ((scale - 0.97f) / 0.03f).coerceIn(0f, 1f)
    val dark = 0.2f + 0.2f * factor
    val bright = if (progressive) 0.2f + 0.8f * factor else dark
    return AmllMaskAlphas(bright = bright, dark = dark)
}

internal fun amllApproachAlpha(current: Float, target: Float, seconds: Float): Float {
    if (abs(target - current) < 0.001f) return target
    val rate = if (target > current) 50.0 else 7.0
    val factor = 1.0 - exp(-rate * seconds.toDouble().coerceAtLeast(0.0))
    return (current + (target - current) * factor).toFloat()
}

internal fun amllEase(fraction: Float): Float =
    cubicBezierAt(fraction.toDouble(), 0.25, 0.1, 0.25, 1.0).toFloat()

internal fun amllEaseOut(fraction: Float): Float =
    cubicBezierAt(fraction.toDouble(), 0.0, 0.0, 0.58, 1.0).toFloat()

internal fun amllLineBlur(
    index: Int,
    targetIndex: Int,
    latestActiveIndex: Int,
    isActive: Boolean,
    compact: Boolean,
): Float {
    if (isActive) return 0f
    val distanceBlur = if (index < targetIndex) {
        2f + abs(targetIndex - index)
    } else {
        1f + abs(index - max(targetIndex, latestActiveIndex))
    }
    return (if (compact) distanceBlur * 0.8f else distanceBlur).coerceAtMost(5f)
}

internal fun amllWordPose(
    elapsedMs: Long,
    durationMs: Long,
    fontSizePx: Float,
    text: String,
    isBackground: Boolean,
    isLastWord: Boolean,
    level: MotionLevel,
    characterIndex: Int = -1,
    characterCount: Int = text.codePointCount(0, text.length),
    baseElapsedMs: Long = elapsedMs,
): AmllWordPose {
    if (level == MotionLevel.OFF || level == MotionLevel.REDUCED) return IDENTITY_WORD_POSE

    val baseDuration = durationMs.coerceAtLeast(1_000L).toDouble()
    val baseProgress = (baseElapsedMs / baseDuration).coerceIn(0.0, 1.0)
    val baseRiseEm = if (isBackground) 0.1 else 0.05
    var translationXEm = 0.0
    var translationYEm = -baseRiseEm * amllEaseOut(baseProgress.toFloat())
    var scale = 1.0
    var glowRadiusEm = 0.0
    var glowAlpha = 0.0

    if (characterIndex >= 0 && amllCanEmphasize(text, durationMs)) {
        var emphasizeDuration = baseDuration
        var amountBase = emphasizeDuration / 2_000.0
        amountBase = if (amountBase > 1.0) sqrt(amountBase) else amountBase.pow(3.0)
        var blurBase = emphasizeDuration / 3_000.0
        blurBase = if (blurBase > 1.0) sqrt(blurBase) else blurBase.pow(3.0)

        var amount = amountBase * 0.6
        var blur = blurBase * 0.5
        if (isLastWord) {
            amount *= 1.6
            blur *= 1.5
            emphasizeDuration *= 1.2
        }
        amount = amount.coerceAtMost(1.2)
        blur = blur.coerceAtMost(0.8)

        val count = characterCount.coerceAtLeast(1)
        val characterDelay = emphasizeDuration / 2.5 / count * characterIndex
        val emphasizeProgress =
            ((elapsedMs - characterDelay) / emphasizeDuration).coerceIn(0.0, 1.0)
        val envelope = sampledAnimation(emphasizeProgress, ::emphasisEnvelope)
        scale = 1.0 + 0.1 * amount * envelope

        val centeredCharacter = count / 2.0 - characterIndex
        translationXEm = -envelope * 0.03 * amount * centeredCharacter * scale
        translationYEm += -envelope * 0.025 * amount * scale

        val floatProgress = ((elapsedMs - (characterDelay - 400.0)) /
            (emphasizeDuration * 1.4)).coerceIn(0.0, 1.0)
        val backgroundMultiplier = if (isBackground) 2.0 else 1.0
        translationYEm += sampledAnimation(floatProgress) { phase ->
            -sin(PI * phase) * 0.05 * backgroundMultiplier
        }

        glowRadiusEm = minOf(0.3, blur * 0.3)
        glowAlpha = envelope * blur
    }

    return AmllWordPose(
        translationXPx = (translationXEm * fontSizePx).toFloat(),
        translationYPx = (translationYEm * fontSizePx).toFloat(),
        scale = scale.toFloat(),
        glowRadiusPx = (glowRadiusEm * fontSizePx).toFloat(),
        glowAlpha = glowAlpha.toFloat(),
    )
}

internal fun amllCanEmphasize(text: String, durationMs: Long): Boolean {
    if (durationMs < 1_000L) return false
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.codePoints().allMatch(::isCjkCodePoint)) return true
    return trimmed.length in 2..7
}

private fun isCjkCodePoint(codePoint: Int): Boolean =
    codePoint in 0x0800..0x9FFC ||
        codePoint in 0x20000..0x2EE5F ||
        codePoint in 0x30000..0x323AF

private fun emphasisEnvelope(progress: Double): Double = if (progress < 0.5) {
    cubicBezierAt(progress / 0.5, 0.2, 0.4, 0.58, 1.0)
} else {
    1.0 - cubicBezierAt((progress - 0.5) / 0.5, 0.3, 0.0, 0.58, 1.0)
}

private fun sampledAnimation(progress: Double, valueAt: (Double) -> Double): Double {
    val phase = progress.coerceIn(0.0, 1.0)
    val firstOffset = 1.0 / EMPHASIS_SAMPLE_COUNT
    if (phase <= firstOffset) return valueAt(firstOffset)
    if (phase >= 1.0) return valueAt(1.0)

    val lowerIndex = floor(phase * EMPHASIS_SAMPLE_COUNT).toInt() - 1
    val lowerOffset = (lowerIndex + 1.0) / EMPHASIS_SAMPLE_COUNT
    val upperOffset = (lowerIndex + 2.0) / EMPHASIS_SAMPLE_COUNT
    val fraction = (phase - lowerOffset) / (upperOffset - lowerOffset)
    return valueAt(lowerOffset) + (valueAt(upperOffset) - valueAt(lowerOffset)) * fraction
}

/** Returns the CSS cubic-bezier output for a normalized time by solving its x component. */
private fun cubicBezierAt(
    progress: Double,
    x1: Double,
    y1: Double,
    x2: Double,
    y2: Double,
): Double {
    val target = progress.coerceIn(0.0, 1.0)
    if (target == 0.0 || target == 1.0) return target

    var parameter = target
    var useBisection = false
    repeat(8) {
        val error = bezierCoordinate(parameter, x1, x2) - target
        val slope = bezierSlope(parameter, x1, x2)
        if (abs(error) < 1e-9) return@repeat
        if (abs(slope) < 1e-8) {
            useBisection = true
        } else if (!useBisection) {
            val candidate = parameter - error / slope
            if (candidate in 0.0..1.0) parameter = candidate else useBisection = true
        }
    }

    if (useBisection || abs(bezierCoordinate(parameter, x1, x2) - target) >= 1e-9) {
        var lower = 0.0
        var upper = 1.0
        repeat(32) {
            parameter = (lower + upper) * 0.5
            if (bezierCoordinate(parameter, x1, x2) < target) lower = parameter else upper = parameter
        }
    }
    return bezierCoordinate(parameter, y1, y2)
}

private fun bezierCoordinate(parameter: Double, first: Double, second: Double): Double {
    val inverse = 1.0 - parameter
    return 3.0 * inverse * inverse * parameter * first +
        3.0 * inverse * parameter * parameter * second +
        parameter * parameter * parameter
}

private fun bezierSlope(parameter: Double, first: Double, second: Double): Double {
    val inverse = 1.0 - parameter
    return 3.0 * inverse * inverse * first +
        6.0 * inverse * parameter * (second - first) +
        3.0 * parameter * parameter * (1.0 - second)
}

private val IDENTITY_WORD_POSE = AmllWordPose(
    translationXPx = 0f,
    translationYPx = 0f,
    scale = 1f,
    glowRadiusPx = 0f,
    glowAlpha = 0f,
)

private const val EMPHASIS_SAMPLE_COUNT = 32.0

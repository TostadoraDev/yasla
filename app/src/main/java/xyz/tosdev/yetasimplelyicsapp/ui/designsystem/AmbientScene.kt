package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntSize
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt

internal val AMBIENT_PHASE_DURATIONS_MS = listOf(14_000, 18_000, 21_000, 31_500)
// Four-times-faster motion; all frequencies still close smoothly after 2.1 minutes.
internal const val AMBIENT_CYCLE_DURATION_MS = 126_000

@Immutable
internal data class AmbientMotionProfile(val driftFraction: Float, val radiusBreath: Float, val alphaBreath: Float)

@Immutable
internal data class AmbientTrackVariation(
    val horizontalBias: Float, val verticalBias: Float, val radiusScale: Float, val energy: Float,
)

internal fun ambientMotionProfile(level: MotionLevel) = when (level) {
    MotionLevel.OFF -> AmbientMotionProfile(0f, 0f, 0f)
    MotionLevel.REDUCED -> AmbientMotionProfile(0.003f, 0.003f, 0.006f)
    MotionLevel.NORMAL -> AmbientMotionProfile(0.09f, 0.055f, 0.065f)
    MotionLevel.ENHANCED -> AmbientMotionProfile(0.12f, 0.075f, 0.09f)
}

internal fun ambientTrackVariation(cacheKey: Int): AmbientTrackVariation {
    fun byteAt(shift: Int) = ((cacheKey ushr shift) and 0xFF) / 255f
    return AmbientTrackVariation(
        (byteAt(0) - 0.5f) * 0.08f, (byteAt(8) - 0.5f) * 0.06f,
        0.94f + byteAt(16) * 0.12f, 0.90f + byteAt(24) * 0.18f,
    )
}

internal fun ambientCycleDurationMillis(level: MotionLevel): Int =
    AMBIENT_CYCLE_DURATION_MS * if (level == MotionLevel.REDUCED) 2 else 1

internal fun ambientPhaseAt(index: Int, cycleFraction: Float): Float {
    val rotations = cycleFraction * (AMBIENT_CYCLE_DURATION_MS / AMBIENT_PHASE_DURATIONS_MS[index])
    return (rotations - floor(rotations)) * (2.0 * PI).toFloat()
}

/** Geometry density for this low-frequency field, independent of screen pixel density. */
internal fun atmosphereGridSize(width: Float, height: Float): IntSize {
    val aspect = (width.coerceAtLeast(1f) / height.coerceAtLeast(1f)).coerceIn(0.2f, 5f)
    return if (aspect >= 1f) IntSize((24 * aspect).roundToInt().coerceAtMost(64), 24)
    else IntSize(24, (24 / aspect).roundToInt().coerceAtMost(64))
}

/** Fixed full-viewport geometry; only its procedural vertex colors change each frame. */
internal class AtmosphereMesh(widthPx: Float, heightPx: Float, columns: Int, rows: Int) {
    init {
        require(columns in 2..64 && rows in 2..64)
        require(widthPx > 0f && heightPx > 0f)
    }

    val positions = FloatArray(columns * rows * 2).apply {
        for (y in 0 until rows) for (x in 0 until columns) {
            val index = (y * columns + x) * 2
            this[index] = x * widthPx / (columns - 1)
            this[index + 1] = y * heightPx / (rows - 1)
        }
    }
    val indices = ShortArray((columns - 1) * (rows - 1) * 6).apply {
        var index = 0
        for (y in 0 until rows - 1) for (x in 0 until columns - 1) {
            val topLeft = y * columns + x
            this[index++] = topLeft.toShort()
            this[index++] = (topLeft + 1).toShort()
            this[index++] = (topLeft + columns).toShort()
            this[index++] = (topLeft + 1).toShort()
            this[index++] = (topLeft + columns + 1).toShort()
            this[index++] = (topLeft + columns).toShort()
        }
    }
}

package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel

@Immutable
data class SignalMotionScheme(
    val enabled: Boolean,
    val reduced: Boolean,
    val microMillis: Int,
    val contentMillis: Int,
    val lyricsMillis: Int,
    val navigationMillis: Int,
    val immersiveMillis: Int,
    val ambientMillis: Int,
)

@Composable
fun rememberSignalMotion(level: MotionLevel): SignalMotionScheme {
    val systemAllowsMotion = remember {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }
    return remember(level, systemAllowsMotion) {
        when {
            !systemAllowsMotion || level == MotionLevel.OFF -> SignalMotionScheme(
                enabled = false,
                reduced = true,
                microMillis = 0,
                contentMillis = 0,
                lyricsMillis = 0,
                navigationMillis = 0,
                immersiveMillis = 0,
                ambientMillis = 0,
            )
            level == MotionLevel.REDUCED -> SignalMotionScheme(
                enabled = true,
                reduced = true,
                microMillis = 110,
                contentMillis = 220,
                lyricsMillis = 260,
                navigationMillis = 280,
                immersiveMillis = 360,
                ambientMillis = 500,
            )
            level == MotionLevel.ENHANCED -> SignalMotionScheme(
                enabled = true,
                reduced = false,
                microMillis = 170,
                contentMillis = 320,
                lyricsMillis = 840,
                navigationMillis = 430,
                immersiveMillis = 680,
                ambientMillis = 1_100,
            )
            else -> SignalMotionScheme(
                enabled = true,
                reduced = false,
                microMillis = 145,
                contentMillis = 290,
                lyricsMillis = 720,
                navigationMillis = 380,
                immersiveMillis = 570,
                ambientMillis = 900,
            )
        }
    }
}

object SignalMotionSpecs {
    private val naturalEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val editorialEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    fun <T> fast(scheme: SignalMotionScheme) = micro<T>(scheme)

    fun <T> standard(scheme: SignalMotionScheme) = content<T>(scheme)

    fun <T> micro(scheme: SignalMotionScheme) = tween<T>(
        durationMillis = scheme.microMillis,
        easing = FastOutSlowInEasing,
    )

    fun <T> content(scheme: SignalMotionScheme) = tween<T>(
        durationMillis = scheme.contentMillis,
        easing = naturalEasing,
    )

    fun <T> lyrics(scheme: SignalMotionScheme) = tween<T>(
        durationMillis = scheme.lyricsMillis,
        easing = editorialEasing,
    )

    fun <T> navigation(scheme: SignalMotionScheme) = tween<T>(
        durationMillis = scheme.navigationMillis,
        easing = naturalEasing,
    )

    fun <T> immersive(scheme: SignalMotionScheme) = tween<T>(
        durationMillis = scheme.immersiveMillis,
        easing = naturalEasing,
    )

    fun <T> ambient(scheme: SignalMotionScheme) = tween<T>(
        durationMillis = scheme.ambientMillis,
        easing = editorialEasing,
    )

    fun <T> ambientTrack(
        scheme: SignalMotionScheme,
        level: MotionLevel,
    ) = tween<T>(
        durationMillis = ambientTrackDurationMillis(level, scheme.enabled),
        easing = editorialEasing,
    )

    fun <T> emphasized(scheme: SignalMotionScheme) = immersive<T>(scheme)

    fun <T> exit(scheme: SignalMotionScheme) = tween<T>(
        durationMillis = (scheme.contentMillis * 0.72f).toInt(),
        easing = FastOutLinearInEasing,
    )

    fun <T> settle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    fun <T> microSpring(scheme: SignalMotionScheme): FiniteAnimationSpec<T> =
        if (!scheme.enabled) snap()
        else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )

}

internal fun ambientTrackDurationMillis(
    level: MotionLevel,
    motionEnabled: Boolean,
): Int = when {
    level == MotionLevel.OFF -> 700
    !motionEnabled -> 0
    level == MotionLevel.REDUCED -> 800
    level == MotionLevel.ENHANCED -> 1_100
    else -> 900
}

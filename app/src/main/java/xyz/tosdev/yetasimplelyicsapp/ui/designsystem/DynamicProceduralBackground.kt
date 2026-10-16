package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.theme.LyricsPalette

/** A sibling behind the entire Now Playing frame, before safe-area padding. */
@Composable
fun DynamicProceduralBackground(
    palette: LyricsPalette,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val systemMotionEnabled = rememberAmbientAnimationsEnabled(LocalLifecycleOwner.current.lifecycle) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    }
    // Refresh only the preference snapshot, not the animation's composition identity.
    val motion = key(systemMotionEnabled) { rememberSignalMotion(motionLevel) }
    val profile = ambientMotionProfile(if (motion.enabled) motionLevel else MotionLevel.OFF)
    val colors = remember(palette.ambient) { proceduralAtmospherePalette(palette.ambient) }
    val colorSpec = SignalMotionSpecs.ambientTrack<Color>(motion, motionLevel)
    // Read animated values only during drawing, never while creating cached buffers.
    val dominant = animateColorAsState(colors.dominant, colorSpec, label = "ambient dominant")
    val secondary = animateColorAsState(colors.secondary, colorSpec, label = "ambient secondary")
    val muted = animateColorAsState(colors.muted, colorSpec, label = "ambient muted")
    val accent = animateColorAsState(colors.accent, colorSpec, label = "ambient artwork accent")
    val dark = animateColorAsState(colors.dark, colorSpec, label = "ambient base")
    val target = remember(palette.cacheKey) { ambientTrackVariation(palette.cacheKey) }
    val trackSpec = SignalMotionSpecs.ambientTrack<Float>(motion, motionLevel)
    val x = animateFloatAsState(target.horizontalBias, trackSpec, label = "ambient track x")
    val y = animateFloatAsState(target.verticalBias, trackSpec, label = "ambient track y")
    val radius = animateFloatAsState(target.radiusScale, trackSpec, label = "ambient track radius")
    val energy = animateFloatAsState(target.energy, trackSpec, label = "ambient track energy")
    // This clock has no artwork key. Changing songs retargets the existing animation.
    val cycle = rememberAmbientCycle(if (motion.enabled) motionLevel else MotionLevel.OFF)
    Box(modifier.graphicsLayer().drawWithCache {
        val field = ProceduralAtmosphereCanvas(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        onDrawBehind {
            // The clock invalidates drawing even after every track transition has settled.
            // Only this isolated drawing layer observes the clock and palette interpolation.
            field.draw(drawContext.canvas.nativeCanvas, dominant.value, secondary.value, muted.value, accent.value,
                dark.value, cycle.value, profile, x.value, y.value, radius.value, energy.value)
        }
    })
}

/** System settings can change while the activity is stopped; never pin their initial value. */
@Composable
internal fun rememberAmbientAnimationsEnabled(lifecycle: Lifecycle, readEnabled: () -> Boolean): Boolean {
    val currentRead by rememberUpdatedState(readEnabled)
    var enabled by remember(lifecycle) { mutableStateOf(readEnabled()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = currentRead()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return enabled
}

@Composable
private fun rememberAmbientCycle(level: MotionLevel): State<Float> {
    if (level == MotionLevel.OFF) {
        return remember { mutableFloatStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "ambient light clock")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(ambientCycleDurationMillis(level), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "ambient continuous cycle",
    )
}

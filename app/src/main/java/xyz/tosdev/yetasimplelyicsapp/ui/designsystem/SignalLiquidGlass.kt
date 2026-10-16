package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel

@Immutable
internal data class SignalLiquidGlassTones(
    val fill: Color,
    val accentWash: Color,
    val specular: Color,
    val edge: Color,
)

/**
 * A cheap, blur-free material fallback. It keeps the live background visible while a tonal
 * artwork wash and an asymmetric one-pixel specular edge make the surface read as glass.
 */
internal fun signalLiquidGlassTones(
    surface: Color,
    accent: Color,
    dark: Boolean,
    emphasized: Boolean,
    selected: Boolean,
    subtle: Boolean = false,
): SignalLiquidGlassTones {
    val baseAccentMix = when {
        selected -> 0.18f
        emphasized -> 0.12f
        else -> 0.07f
    }
    val accentMix = if (subtle) baseAccentMix * 0.55f else baseAccentMix
    val fillAlpha = when {
        subtle && dark && emphasized -> 0.34f
        subtle && dark -> 0.30f
        subtle && emphasized -> 0.48f
        subtle -> 0.42f
        dark && emphasized -> 0.62f
        dark -> 0.54f
        emphasized -> 0.78f
        else -> 0.70f
    }
    val fill = lerp(surface, accent, accentMix).copy(alpha = fillAlpha)
    val washAlpha = if (selected) 0.14f else if (emphasized) 0.085f else 0.045f
    val wash = accent.copy(alpha = if (subtle) washAlpha * 0.55f else washAlpha)
    val specular = Color.White.copy(alpha = when {
        subtle && dark -> 0.14f
        subtle -> 0.28f
        dark -> 0.22f
        else -> 0.48f
    })
    val edge = lerp(Color.White, accent, 0.28f).copy(alpha = when {
        subtle && dark -> 0.08f
        subtle -> 0.14f
        dark -> 0.14f
        else -> 0.24f
    })
    return SignalLiquidGlassTones(fill, wash, specular, edge)
}

@Composable
fun Modifier.signalLiquidGlass(
    shape: Shape,
    motionLevel: MotionLevel,
    emphasized: Boolean = false,
    selected: Boolean = false,
    interaction: Float = 0f,
    subtle: Boolean = false,
): Modifier {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val motion = rememberSignalMotion(motionLevel)
    val dark = scheme.background.luminance() < 0.5f
    val target = signalLiquidGlassTones(
        surface = scheme.surfaceContainerHigh,
        accent = scheme.primary,
        dark = dark,
        emphasized = emphasized,
        selected = selected,
        subtle = subtle,
    )
    val fill by animateColorAsState(target.fill, SignalMotionSpecs.standard(motion), label = "glass fill")
    val wash by animateColorAsState(target.accentWash, SignalMotionSpecs.standard(motion), label = "glass wash")
    val specular by animateColorAsState(target.specular, SignalMotionSpecs.fast(motion), label = "glass specular")
    val edge by animateColorAsState(target.edge, SignalMotionSpecs.standard(motion), label = "glass edge")
    val response by animateFloatAsState(
        targetValue = if (motion.enabled) interaction.coerceIn(0f, 1f) else 0f,
        animationSpec = SignalMotionSpecs.microSpring(motion),
        label = "glass response",
    )
    val elevation = if (subtle) 2.dp else if (emphasized || selected) 7.dp else 4.dp

    return this
        .shadow(elevation, shape, clip = false)
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val glassField = Brush.linearGradient(
                colors = listOf(
                    fill,
                    lerp(fill, wash, 0.48f + response * 0.20f),
                    fill,
                ),
                start = Offset(size.width * (0.04f + response * 0.04f), 0f),
                end = Offset(size.width * (0.94f - response * 0.03f), size.height),
            )
            val rim = Brush.linearGradient(
                colors = listOf(
                    specular.copy(alpha = specular.alpha * (1f + response * 0.18f)),
                    edge,
                    Color.Transparent,
                    edge.copy(alpha = edge.alpha * 0.52f),
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            onDrawBehind {
                drawGlassOutline(outline, brush = glassField)
                drawGlassOutline(outline, brush = rim, style = Stroke(width = 1.dp.toPx()))
            }
        }
}

private fun DrawScope.drawGlassOutline(
    outline: Outline,
    brush: Brush,
    style: DrawStyle = Fill,
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(
            brush = brush,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            style = style,
        )
        is Outline.Rounded -> {
            val path = Path().apply { addRoundRect(outline.roundRect) }
            drawPath(path = path, brush = brush, style = style)
        }
        is Outline.Generic -> drawPath(path = outline.path, brush = brush, style = style)
    }
}

@Composable
fun SignalLiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    motionLevel: MotionLevel,
    emphasized: Boolean = false,
    selected: Boolean = false,
    interaction: Float = 0f,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .signalLiquidGlass(
                shape = shape,
                motionLevel = motionLevel,
                emphasized = emphasized,
                selected = selected,
                interaction = interaction,
            )
            .padding(contentPadding),
        content = content,
    )
}

package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.tosdev.yetasimplelyicsapp.ui.MotionLevel
import xyz.tosdev.yetasimplelyicsapp.ui.components.decodeArtworkBitmap
import xyz.tosdev.yetasimplelyicsapp.ui.theme.LyricsPalette
import xyz.tosdev.yetasimplelyicsapp.ui.theme.PaletteEngine
import xyz.tosdev.yetasimplelyicsapp.ui.theme.PaletteRequest
import xyz.tosdev.yetasimplelyicsapp.ui.theme.PaletteSource

@Composable
fun rememberArtworkPalette(
    artworkUri: String?,
    darkTheme: Boolean,
    highContrast: Boolean,
    motionLevel: MotionLevel,
): LyricsPalette {
    val context = LocalContext.current
    val engine = remember { PaletteEngine() }
    val fallback = remember(darkTheme, highContrast) {
        engine.defaultSignalPalette(darkTheme, highContrast)
    }
    val resolved by produceState(fallback, artworkUri, darkTheme, highContrast) {
        val bitmap = withContext(Dispatchers.IO) {
            decodeArtworkBitmap(context, artworkUri)
        }
        value = engine.resolve(
            context = context,
            request = PaletteRequest(
                source = if (bitmap == null) PaletteSource.PRESET else PaletteSource.ARTWORK,
                dark = darkTheme,
                highContrast = highContrast,
            ),
            artwork = bitmap,
        )
    }
    val motion = rememberSignalMotion(motionLevel)
    val paletteSpec = SignalMotionSpecs.ambientTrack<androidx.compose.ui.graphics.Color>(
        scheme = motion,
        level = motionLevel,
    )
    val primary by animateColorAsState(resolved.primary, paletteSpec, label = "palette primary")
    val secondary by animateColorAsState(resolved.secondary, paletteSpec, label = "palette secondary")
    val tertiary by animateColorAsState(resolved.tertiary, paletteSpec, label = "palette tertiary")
    val background by animateColorAsState(resolved.background, paletteSpec, label = "palette background")
    val surface by animateColorAsState(resolved.surface, paletteSpec, label = "palette surface")
    val onBackground by animateColorAsState(resolved.onBackground, paletteSpec, label = "palette foreground")
    val onSurfaceMuted by animateColorAsState(resolved.onSurfaceMuted, paletteSpec, label = "palette muted")
    return resolved.copy(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = background,
        surface = surface,
        onBackground = onBackground,
        onSurfaceMuted = onSurfaceMuted,
    )
}

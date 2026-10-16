package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SignalSpacing(
    val micro: Dp = 4.dp,
    val tiny: Dp = 8.dp,
    val small: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
    val huge: Dp = 48.dp,
)

@Immutable
data class SignalRadii(
    val small: Dp = 10.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val hero: Dp = 32.dp,
    val pill: Dp = 999.dp,
)

@Immutable
data class SignalOpacity(
    val disabled: Float = 0.38f,
    val muted: Float = 0.68f,
    val subtle: Float = 0.12f,
    val glass: Float = 0.86f,
)

@Immutable
data class SignalElevation(
    val flat: Dp = 0.dp,
    val floating: Dp = 4.dp,
    val navigation: Dp = 6.dp,
)

@Immutable
data class SignalBlur(
    val none: Dp = 0.dp,
    val ambient: Dp = 18.dp,
    val floating: Dp = 28.dp,
)

@Immutable
data class SignalExtendedColors(
    val canvas: Color,
    val surfaceLow: Color,
    val surfaceRaised: Color,
    val surfaceInteractive: Color,
    val surfaceGlass: Color,
    val signal: Color,
    val signalSoft: Color,
    val textMuted: Color,
    val scrim: Color,
)

val LocalSignalSpacing = staticCompositionLocalOf { SignalSpacing() }
val LocalSignalRadii = staticCompositionLocalOf { SignalRadii() }
val LocalSignalOpacity = staticCompositionLocalOf { SignalOpacity() }
val LocalSignalElevation = staticCompositionLocalOf { SignalElevation() }
val LocalSignalBlur = staticCompositionLocalOf { SignalBlur() }
val LocalSignalColors = staticCompositionLocalOf {
    SignalExtendedColors(
        canvas = Color(0xFF111315),
        surfaceLow = Color(0xFF171A1D),
        surfaceRaised = Color(0xFF1D2024),
        surfaceInteractive = Color(0xFF262A2F),
        surfaceGlass = Color(0xE62A2E33),
        signal = Color(0xFFFF7A64),
        signalSoft = Color(0x33FF7A64),
        textMuted = Color(0xFFAEB5BC),
        scrim = Color(0xB3000000),
    )
}

object SignalDesignSystem {
    val spacing: SignalSpacing
        @Composable get() = LocalSignalSpacing.current
    val radii: SignalRadii
        @Composable get() = LocalSignalRadii.current
    val opacity: SignalOpacity
        @Composable get() = LocalSignalOpacity.current
    val elevation: SignalElevation
        @Composable get() = LocalSignalElevation.current
    val blur: SignalBlur
        @Composable get() = LocalSignalBlur.current
    val colors: SignalExtendedColors
        @Composable get() = LocalSignalColors.current
}

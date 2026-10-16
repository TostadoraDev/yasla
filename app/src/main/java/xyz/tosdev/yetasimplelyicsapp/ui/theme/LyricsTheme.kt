package xyz.tosdev.yetasimplelyicsapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.LocalSignalColors
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.LocalSignalBlur
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.LocalSignalElevation
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.LocalSignalOpacity
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.LocalSignalRadii
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.LocalSignalSpacing
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalExtendedColors
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalBlur
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalElevation
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalOpacity
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalRadii
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.SignalSpacing

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A64),
    onPrimary = Color(0xFF24100B),
    primaryContainer = Color(0xFF52241D),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE6C98E),
    onSecondary = Color(0xFF261B08),
    secondaryContainer = Color(0xFF473A20),
    onSecondaryContainer = Color(0xFFFFE8B7),
    tertiary = Color(0xFF9BD5C2),
    onTertiary = Color(0xFF082019),
    tertiaryContainer = Color(0xFF173E33),
    onTertiaryContainer = Color(0xFFB6F2DD),
    background = Color(0xFF111315),
    onBackground = Color(0xFFF4F6F7),
    surface = Color(0xFF1D2024),
    onSurface = Color(0xFFF4F6F7),
    surfaceVariant = Color(0xFF262A2F),
    onSurfaceVariant = Color(0xFFAEB5BC),
    outline = Color(0xFF747B83),
    outlineVariant = Color(0xFF343940),
    surfaceTint = Color(0xFFFF7A64),
    inverseSurface = Color(0xFFE9EAEC),
    inverseOnSurface = Color(0xFF1D2024),
    inversePrimary = Color(0xFF9C3A2B),
    surfaceDim = Color(0xFF0D0F11),
    surfaceBright = Color(0xFF30353B),
    surfaceContainerLowest = Color(0xFF0D0F11),
    surfaceContainerLow = Color(0xFF171A1D),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF22262B),
    surfaceContainerHighest = Color(0xFF292E34),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C3A2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0802),
    secondary = Color(0xFF6B572D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DEAA),
    onSecondaryContainer = Color(0xFF231A04),
    tertiary = Color(0xFF316B5B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F0DC),
    onTertiaryContainer = Color(0xFF002117),
    background = Color(0xFFF8F7F3),
    onBackground = Color(0xFF191B1E),
    surface = Color.White,
    onSurface = Color(0xFF191B1E),
    surfaceVariant = Color(0xFFF0F0EC),
    onSurfaceVariant = Color(0xFF5D6268),
    outline = Color(0xFF767B80),
    outlineVariant = Color(0xFFDADDDC),
    surfaceTint = Color(0xFF9C3A2B),
    inverseSurface = Color(0xFF2E3034),
    inverseOnSurface = Color(0xFFF1F1F1),
    inversePrimary = Color(0xFFFFB4A4),
    surfaceDim = Color(0xFFE5E4E0),
    surfaceBright = Color(0xFFFFFBFF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F4F0),
    surfaceContainer = Color(0xFFEFEEEA),
    surfaceContainerHigh = Color(0xFFE9E8E4),
    surfaceContainerHighest = Color(0xFFE3E2DE),
)

private val SignalTypography = Typography(
    displayLarge = signalTextStyle(52, 58, FontWeight.SemiBold, -1.6f),
    displayMedium = signalTextStyle(38, 44, FontWeight.SemiBold, -1.1f),
    displaySmall = signalTextStyle(31, 38, FontWeight.SemiBold, -0.7f),
    headlineLarge = signalTextStyle(30, 37, FontWeight.SemiBold, -0.5f),
    headlineMedium = signalTextStyle(25, 32, FontWeight.Medium, -0.25f),
    headlineSmall = signalTextStyle(22, 29, FontWeight.Medium),
    titleLarge = signalTextStyle(20, 26, FontWeight.SemiBold),
    titleMedium = signalTextStyle(16, 22, FontWeight.SemiBold, 0.1f),
    titleSmall = signalTextStyle(14, 20, FontWeight.Medium, 0.1f),
    bodyLarge = signalTextStyle(17, 25, FontWeight.Normal, 0.1f),
    bodyMedium = signalTextStyle(15, 22, FontWeight.Normal, 0.15f),
    bodySmall = signalTextStyle(13, 18, FontWeight.Normal, 0.2f),
    labelLarge = signalTextStyle(13, 18, FontWeight.Medium, 0.35f, FontFamily.Monospace),
    labelMedium = signalTextStyle(11, 16, FontWeight.Medium, 0.65f, FontFamily.Monospace),
    labelSmall = signalTextStyle(10, 14, FontWeight.Medium, 0.7f, FontFamily.Monospace),
)

private fun signalTextStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
    family: FontFamily = FontFamily.SansSerif,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private val SignalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun ColorScheme.withSignalCanvas(darkTheme: Boolean): ColorScheme = if (darkTheme) {
    copy(
        background = blend(Color(0xFF111315), primary, 0.035f),
        surface = blend(Color(0xFF1D2024), primary, 0.025f),
        surfaceDim = Color(0xFF0D0F11),
        surfaceBright = Color(0xFF30353B),
        surfaceContainerLowest = Color(0xFF0D0F11),
        surfaceContainerLow = blend(Color(0xFF171A1D), primary, 0.018f),
        surfaceContainer = blend(Color(0xFF1D2024), primary, 0.022f),
        surfaceContainerHigh = blend(Color(0xFF22262B), primary, 0.028f),
        surfaceContainerHighest = blend(Color(0xFF292E34), primary, 0.035f),
    )
} else {
    copy(
        background = blend(Color(0xFFF8F7F3), primary, 0.018f),
        surface = Color.White,
        surfaceDim = Color(0xFFE5E4E0),
        surfaceBright = Color.White,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F4F0),
        surfaceContainer = Color(0xFFEFEEEA),
        surfaceContainerHigh = Color(0xFFE9E8E4),
        surfaceContainerHighest = Color(0xFFE3E2DE),
    )
}

private fun extendedColors(scheme: ColorScheme, darkTheme: Boolean): SignalExtendedColors =
    SignalExtendedColors(
        canvas = scheme.background,
        surfaceLow = scheme.surfaceContainerLow,
        surfaceRaised = scheme.surfaceContainer,
        surfaceInteractive = scheme.surfaceContainerHigh,
        surfaceGlass = scheme.surfaceContainerHigh.copy(alpha = if (darkTheme) 0.90f else 0.94f),
        signal = scheme.primary,
        signalSoft = scheme.primary.copy(alpha = 0.18f),
        textMuted = scheme.onSurfaceVariant,
        scrim = if (darkTheme) Color(0xB3000000) else Color(0x66000000),
    )

@Composable
fun LyricsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    palette: LyricsPalette? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val rawScheme = when {
        palette != null -> palette.asColorScheme(darkTheme)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = if (palette == null) rawScheme.withSignalCanvas(darkTheme) else rawScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SignalTypography,
        shapes = SignalShapes,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colorScheme.onBackground,
            LocalSignalSpacing provides SignalSpacing(),
            LocalSignalRadii provides SignalRadii(),
            LocalSignalOpacity provides SignalOpacity(),
            LocalSignalElevation provides SignalElevation(),
            LocalSignalBlur provides SignalBlur(),
            LocalSignalColors provides extendedColors(colorScheme, darkTheme),
            content = content,
        )
    }
}

private fun blend(from: Color, to: Color, amount: Float): Color {
    val fraction = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
        alpha = from.alpha + (to.alpha - from.alpha) * fraction,
    )
}

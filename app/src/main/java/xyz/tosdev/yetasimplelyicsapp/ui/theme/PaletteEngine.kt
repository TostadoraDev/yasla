package xyz.tosdev.yetasimplelyicsapp.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

enum class PaletteSource {
    MANUAL,
    MATERIAL_YOU,
    ARTWORK,
    PRESET,
}

@Immutable
data class PaletteRequest(
    val source: PaletteSource,
    val dark: Boolean,
    val highContrast: Boolean = false,
    val manualPrimary: Color = Color(0xFFFF765F),
    val presetId: String = "signal",
)

@Immutable
data class LyricsPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val onSurfaceMuted: Color,
    val source: PaletteSource,
    val cacheKey: Int,
    val ambient: ArtworkAmbientColors = ArtworkAmbientColors.Neutral,
) {
    fun asColorScheme(dark: Boolean): ColorScheme {
        val onPrimary = readableForeground(primary, minimumRatio = 4.5f)
        val onSecondary = readableForeground(secondary, minimumRatio = 4.5f)
        return if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                onSecondary = onSecondary,
                tertiary = tertiary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onBackground,
                surfaceVariant = blend(surface, onBackground, 0.08f),
                onSurfaceVariant = onSurfaceMuted,
                surfaceDim = blend(background, Color.Black, 0.10f),
                surfaceBright = blend(surface, onBackground, 0.10f),
                surfaceContainerLowest = background,
                surfaceContainerLow = blend(background, onBackground, 0.035f),
                surfaceContainer = surface,
                surfaceContainerHigh = blend(surface, onBackground, 0.065f),
                surfaceContainerHighest = blend(surface, onBackground, 0.11f),
                primaryContainer = blend(primary, background, 0.68f),
                onPrimaryContainer = readableForeground(blend(primary, background, 0.68f), 4.5f),
                secondaryContainer = blend(secondary, background, 0.72f),
                onSecondaryContainer = readableForeground(blend(secondary, background, 0.72f), 4.5f),
                outline = blend(onBackground, background, 0.58f),
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                onSecondary = onSecondary,
                tertiary = tertiary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onBackground,
                surfaceVariant = blend(surface, onBackground, 0.07f),
                onSurfaceVariant = onSurfaceMuted,
                surfaceDim = blend(background, onBackground, 0.055f),
                surfaceBright = blend(surface, Color.White, 0.45f),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = blend(background, onBackground, 0.025f),
                surfaceContainer = surface,
                surfaceContainerHigh = blend(surface, onBackground, 0.055f),
                surfaceContainerHighest = blend(surface, onBackground, 0.09f),
                primaryContainer = blend(primary, background, 0.78f),
                onPrimaryContainer = readableForeground(blend(primary, background, 0.78f), 4.5f),
                secondaryContainer = blend(secondary, background, 0.80f),
                onSecondaryContainer = readableForeground(blend(secondary, background, 0.80f), 4.5f),
                outline = blend(onBackground, background, 0.54f),
            )
        }
    }
}

class PaletteEngine(
    private val maxCacheEntries: Int = 24,
) {
    private val cache = object : LinkedHashMap<Int, LyricsPalette>(
        maxCacheEntries,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Int, LyricsPalette>?,
        ): Boolean = size > maxCacheEntries
    }

    suspend fun resolve(
        context: Context,
        request: PaletteRequest,
        artwork: Bitmap? = null,
    ): LyricsPalette = withContext(Dispatchers.Default) {
        val artworkHash = artwork?.let(::artworkHash) ?: 0
        val key = 31 * request.hashCode() + artworkHash
        synchronized(cache) {
            cache[key]?.let { return@withContext it }
        }

        val raw = when (request.source) {
            PaletteSource.ARTWORK -> artwork?.let {
                fromArtwork(it, request.dark, key)
            } ?: fromPreset(request.presetId, request.dark, key, PaletteSource.PRESET)
            PaletteSource.MATERIAL_YOU -> fromMaterialYou(context, request.dark, key)
            PaletteSource.MANUAL -> fromSeed(
                seed = request.manualPrimary,
                dark = request.dark,
                key = key,
                source = PaletteSource.MANUAL,
            )
            PaletteSource.PRESET -> fromPreset(
                id = request.presetId,
                dark = request.dark,
                key = key,
                source = PaletteSource.PRESET,
            )
        }
        val resolved = if (request.highContrast) raw.withEnhancedContrast() else raw
        synchronized(cache) { cache[key] = resolved }
        resolved
    }

    fun clear() = synchronized(cache) { cache.clear() }

    fun cachedEntries(): Int = synchronized(cache) { cache.size }

    fun defaultSignalPalette(dark: Boolean, highContrast: Boolean = false): LyricsPalette {
        val palette = fromPreset(
            id = "signal",
            dark = dark,
            key = if (dark) 0x5349474E else 0x4C495447,
            source = PaletteSource.PRESET,
        )
        return if (highContrast) palette.withEnhancedContrast() else palette
    }

    private fun fromArtwork(
        bitmap: Bitmap,
        dark: Boolean,
        key: Int,
    ): LyricsPalette {
        val palette = Palette.from(bitmap)
            .maximumColorCount(20)
            .clearFilters()
            .generate()
        val primary = Color(
            palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: 0xFFFF765F.toInt(),
        )
        val secondary = Color(
            palette.mutedSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: 0xFFE8C887.toInt(),
        )
        return fromPair(primary, secondary, dark, key, PaletteSource.ARTWORK).copy(
            ambient = artworkAmbientColors(palette.swatches.map { AmbientSwatch(Color(it.rgb), it.population) }),
        )
    }

    private fun fromMaterialYou(
        context: Context,
        dark: Boolean,
        key: Int,
    ): LyricsPalette {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return fromPreset("signal", dark, key, PaletteSource.PRESET)
        }
        val primary = Color(context.getColor(android.R.color.system_accent1_500))
        val secondary = Color(context.getColor(android.R.color.system_accent2_400))
        return fromPair(primary, secondary, dark, key, PaletteSource.MATERIAL_YOU)
    }

    private fun fromSeed(
        seed: Color,
        dark: Boolean,
        key: Int,
        source: PaletteSource,
    ): LyricsPalette {
        val secondary = blend(seed, if (dark) Color(0xFFE9C98B) else Color(0xFF6F5319), 0.58f)
        return fromPair(seed, secondary, dark, key, source)
    }

    private fun fromPreset(
        id: String,
        dark: Boolean,
        key: Int,
        source: PaletteSource,
    ): LyricsPalette {
        val pair = when (id) {
            "sea-glass" -> Color(0xFF35C4A4) to Color(0xFF8EC9FF)
            "mono" -> Color(0xFFE8E2D8) to Color(0xFFA5A1A0)
            "ember" -> Color(0xFFFF7048) to Color(0xFFFFC65C)
            else -> Color(0xFFFF765F) to Color(0xFFE8C887)
        }
        return fromPair(pair.first, pair.second, dark, key, source)
    }

    private fun fromPair(
        primary: Color,
        secondary: Color,
        dark: Boolean,
        key: Int,
        source: PaletteSource,
    ): LyricsPalette {
        val baseBackground = if (dark) Color(0xFF111315) else Color(0xFFF8F7F3)
        val baseSurface = if (dark) Color(0xFF1D2024) else Color(0xFFFFFFFF)
        val tintAmount = if (dark) 0.075f else 0.035f
        val background = blend(baseBackground, primary, tintAmount)
        val surface = blend(baseSurface, secondary, tintAmount * 0.72f)
        val onBackground = if (dark) Color(0xFFF4F6F7) else Color(0xFF191B1E)
        return LyricsPalette(
            primary = tuneAccent(primary, dark),
            secondary = tuneAccent(secondary, dark),
            tertiary = blend(primary, secondary, 0.48f),
            background = background,
            surface = surface,
            onBackground = onBackground,
            onSurfaceMuted = blend(onBackground, background, if (dark) 0.28f else 0.45f),
            source = source,
            cacheKey = key,
        )
    }

    private fun LyricsPalette.withEnhancedContrast(): LyricsPalette = copy(
        primary = ensureContrast(primary, background, 3.5f),
        secondary = ensureContrast(secondary, background, 3.5f),
        tertiary = ensureContrast(tertiary, background, 3.5f),
        onSurfaceMuted = ensureContrast(onSurfaceMuted, surface, 5f),
    )

    private fun artworkHash(bitmap: Bitmap): Int {
        var hash = 17
        hash = 31 * hash + bitmap.width
        hash = 31 * hash + bitmap.height
        hash = 31 * hash + bitmap.generationId
        if (bitmap.width == 0 || bitmap.height == 0) return hash
        for (yStep in 1..4) {
            val y = min(bitmap.height - 1, bitmap.height * yStep / 5)
            for (xStep in 1..4) {
                val x = min(bitmap.width - 1, bitmap.width * xStep / 5)
                hash = 31 * hash + bitmap.getPixel(x, y)
            }
        }
        return hash
    }
}

private fun tuneAccent(color: Color, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    val amount = when {
        dark && color.luminance() < 0.22f -> 0.35f // More boost for dark
        !dark && color.luminance() > 0.66f -> 0.28f
        else -> 0.1f // Always add a bit of target to make it "lit"
    }
    return blend(color, target, amount)
}

private fun ensureContrast(
    foreground: Color,
    background: Color,
    minimumRatio: Float,
): Color {
    if (contrastRatio(foreground, background) >= minimumRatio) return foreground
    val toward = if (background.luminance() < 0.5f) Color.White else Color.Black
    var result = foreground
    for (step in 1..20) {
        result = blend(foreground, toward, step / 20f)
        if (contrastRatio(result, background) >= minimumRatio) return result
    }
    return toward
}

private fun readableForeground(
    background: Color,
    minimumRatio: Float,
): Color {
    val whiteRatio = contrastRatio(Color.White, background)
    val blackRatio = contrastRatio(Color.Black, background)
    val preferred = if (whiteRatio >= blackRatio) Color.White else Color.Black
    if (max(whiteRatio, blackRatio) >= minimumRatio) return preferred
    return preferred
}

private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = max(a.luminance(), b.luminance())
    val darker = min(a.luminance(), b.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
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

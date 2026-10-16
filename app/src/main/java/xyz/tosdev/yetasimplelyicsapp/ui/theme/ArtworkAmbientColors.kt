package xyz.tosdev.yetasimplelyicsapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.sqrt

/** Raw artwork swatches, independent of the brighter accents used for controls. */
@Immutable
data class ArtworkAmbientColors(
    val dominant: Color,
    val secondary: Color,
    val muted: Color,
    val dark: Color,
    val accent: Color = secondary,
) {
    companion object {
        val Neutral = ArtworkAmbientColors(
            Color(0xFF454545), Color(0xFF363636), Color(0xFF292929), Color(0xFF101010),
        )
    }
}

internal data class AmbientSwatch(val color: Color, val population: Int)

internal fun artworkAmbientColors(swatches: List<AmbientSwatch>): ArtworkAmbientColors {
    val positive = swatches.filter { it.population > 0 }
    if (positive.isEmpty()) return ArtworkAmbientColors.Neutral
    val totalPopulation = positive.sumOf { it.population.toLong() }.toDouble()
    // Relative coverage rejects isolated pixels without rejecting small legitimate palettes.
    val populated = positive.filter { it.population / totalPopulation >= 0.015 }
        .sortedByDescending { it.population }
        .ifEmpty { listOf(positive.maxBy { it.population }) }
    val dominant = populated.first().color
    // Black/white borders must not suppress the colored light in the actual cover.
    val chromatic = populated.filter { saturation(it.color) > 0.18f && it.color.luminance() > 0.008f }
    val light = if (saturation(dominant) < 0.10f || dominant.luminance() <= 0.008f) {
        chromatic.firstOrNull()?.color ?: dominant
    } else dominant
    val neutralSupport = populated.firstOrNull { saturation(it.color) <= 0.18f && it.color.luminance() >= 0.012f }?.color
    val secondary = distinctArtworkTone(chromatic, listOf(light))
        ?: neutralSupport
        ?: light
    val muted = neutralSupport ?: populated.filter { it.color.luminance() in 0.025f..0.45f }
        .minByOrNull { saturation(it.color) }?.color ?: secondary
    val accent = distinctArtworkTone(chromatic, listOf(light, secondary, muted))
        ?: distinctArtworkTone(chromatic, listOf(light, secondary))
        ?: secondary
    return ArtworkAmbientColors(light, secondary, muted, populated.minBy { it.color.luminance() }.color, accent)
}

/** Retain the cover's hue while adapting it into a readable fullscreen light field. */
internal fun ambientLightColor(color: Color): Color {
    val value = peak(color)
    if (value == 0f) return Color.Black
    // A monochrome cover stays neutral. The same grey admixture across channels preserves
    // chromatic hue while avoiding isolated, highly saturated patches in the fog.
    val chroma = ((saturation(color) - 0.12f) / 0.23f).coerceIn(0f, 1f)
    val neutral = (color.red + color.green + color.blue) / 3f
    val softness = if (value <= 0.10f) 0f else 0.22f * chroma
    val red = color.red + (neutral - color.red) * softness
    val green = color.green + (neutral - color.green) * softness
    val blue = color.blue + (neutral - color.blue) * softness
    val peakLimit = 0.34f + (0.80f - 0.34f) * chroma
    val maximumScale = minOf(1f, peakLimit / maxOf(red, green, blue))
    fun scaled(scale: Float) = Color(red * scale, green * scale, blue * scale, 1f)
    val toned = scaled(maximumScale)
    val luminanceLimit = 0.10f + 0.14f * chroma
    if (toned.luminance() <= luminanceLimit) return toned
    // sRGB luminance is nonlinear. A bounded search enforces the actual luminance limit,
    // including bright green/yellow, instead of approximating gamma with sqrt(luminance).
    var lower = 0f
    var upper = maximumScale
    repeat(18) {
        val middle = (lower + upper) * 0.5f
        if (scaled(middle).luminance() <= luminanceLimit) lower = middle else upper = middle
    }
    return scaled(lower)
}

private fun distinctArtworkTone(swatches: List<AmbientSwatch>, used: List<Color>): Color? {
    if (swatches.isEmpty()) return null
    val chromaticUsed = used.filter { saturation(it) > 0.18f }
    val largestPopulation = swatches.maxOf { it.population }.toFloat()
    return swatches.mapNotNull { swatch ->
        val separation = chromaticUsed.minOfOrNull { hueSeparation(swatch.color, it) } ?: 180f
        // Ignore brightness-only variants and tiny hue shifts within the same artwork tone.
        if (separation < 18f) null else {
            val coverage = sqrt(swatch.population / largestPopulation)
            swatch to separation * (0.72f + coverage * 0.28f)
        }
    }.maxByOrNull { it.second }?.first?.color
}

private fun hueSeparation(a: Color, b: Color): Float {
    val difference = abs(hue(a) - hue(b))
    return minOf(difference, 360f - difference)
}

private fun hue(color: Color): Float {
    val maximum = peak(color)
    val delta = maximum - minOf(color.red, color.green, color.blue)
    if (delta <= 0.00001f) return 0f
    val sector = when (maximum) {
        color.red -> (color.green - color.blue) / delta
        color.green -> (color.blue - color.red) / delta + 2f
        else -> (color.red - color.green) / delta + 4f
    }
    return (sector * 60f + 360f) % 360f
}

private fun peak(color: Color) = maxOf(color.red, color.green, color.blue)
private fun saturation(color: Color): Float {
    val peak = peak(color)
    return if (peak == 0f) 0f else (peak - minOf(color.red, color.green, color.blue)) / peak
}

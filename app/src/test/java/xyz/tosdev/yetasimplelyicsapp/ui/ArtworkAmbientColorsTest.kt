package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.theme.AmbientSwatch
import xyz.tosdev.yetasimplelyicsapp.ui.theme.ArtworkAmbientColors
import xyz.tosdev.yetasimplelyicsapp.ui.theme.ambientLightColor
import xyz.tosdev.yetasimplelyicsapp.ui.theme.artworkAmbientColors

class ArtworkAmbientColorsTest {
    @Test
    fun blackBordersDoNotHideTheCoversDistinctColors() {
        val gray = Color(0xFF777777)
        val source = listOf(
            AmbientSwatch(Color.Black, 1_000),
            AmbientSwatch(Color.Red, 400),
            AmbientSwatch(Color.Blue, 300),
            AmbientSwatch(Color.Green, 200),
            AmbientSwatch(gray, 150),
        )
        val palette = artworkAmbientColors(source)

        assertEquals(Color.Red, palette.dominant)
        assertEquals(Color.Blue, palette.secondary)
        assertEquals(Color.Green, palette.accent)
        assertEquals(gray, palette.muted)
        assertEquals(Color.Black, palette.dark)
        assertTrue(palette.colors().all { selected -> source.any { it.color == selected } })
    }

    @Test
    fun brightnessVariantsDoNotDisplaceADifferentArtworkHue() {
        val palette = artworkAmbientColors(listOf(
            AmbientSwatch(Color(0xFFFF0000), 600),
            AmbientSwatch(Color(0xFFBB0000), 550),
            AmbientSwatch(Color(0xFF660000), 450),
            AmbientSwatch(Color.Blue, 180),
        ))

        assertEquals(Color.Red, palette.dominant)
        assertEquals(Color.Blue, palette.secondary)
    }

    @Test
    fun hueComparisonWrapsAcrossRedInsteadOfTreatingItAsAnotherColor() {
        val red = Color(0xFFFF0004)
        val palette = artworkAmbientColors(listOf(
            AmbientSwatch(red, 600),
            AmbientSwatch(Color(0xFF800300), 500),
            AmbientSwatch(Color.Blue, 200),
        ))

        assertEquals(red, palette.dominant)
        assertEquals(Color.Blue, palette.secondary)
    }

    @Test
    fun aLightlyTintedNeutralDoesNotBlockRealChromaticAccents() {
        val neutral = Color(0xFF998C85)
        val palette = artworkAmbientColors(listOf(
            AmbientSwatch(neutral, 300),
            AmbientSwatch(Color.Blue, 60),
            AmbientSwatch(Color.Green, 40),
        ))

        assertEquals(neutral, palette.dominant)
        assertEquals(Color.Blue, palette.secondary)
        assertEquals(Color.Green, palette.accent)
    }

    @Test
    fun isolatedPixelsCannotBecomeAnAccentOrDarkAnchor() {
        val source = listOf(
            AmbientSwatch(Color.Red, 500),
            AmbientSwatch(Color.Blue, 350),
            AmbientSwatch(Color.Green, 250),
            AmbientSwatch(Color.Magenta, 1),
            AmbientSwatch(Color.Black, 1),
        )
        val palette = artworkAmbientColors(source)

        assertTrue(palette.colors().none { it == Color.Magenta || it == Color.Black })
        assertEquals(palette, artworkAmbientColors(source.map { it.copy(population = it.population * 10) }))
    }

    @Test
    fun monochromeAndInvalidInputsNeverInventColoredAccents() {
        val source = listOf(AmbientSwatch(Color.Black, 800), AmbientSwatch(Color.White, 400), AmbientSwatch(Color.Gray, 200))
        val palette = artworkAmbientColors(source)
        palette.colors().forEach { color ->
            assertEquals(color.red, color.green, 0f)
            assertEquals(color.red, color.blue, 0f)
            assertTrue(source.any { it.color == color })
        }
        assertEquals(ArtworkAmbientColors.Neutral, artworkAmbientColors(emptyList()))
        assertEquals(ArtworkAmbientColors.Neutral, artworkAmbientColors(listOf(AmbientSwatch(Color.Red, 0), AmbientSwatch(Color.Blue, -2))))
        assertEquals(Color.Blue, ArtworkAmbientColors(Color.Red, Color.Blue, Color.Gray, Color.Black).accent)
    }

    @Test
    fun blackAndWhiteCoverStillProvidesNeutralLightWithoutGraySwatches() {
        val palette = artworkAmbientColors(listOf(AmbientSwatch(Color.Black, 800), AmbientSwatch(Color.White, 200)))
        assertEquals(Color.Black, palette.dominant)
        assertEquals(Color.White, palette.secondary)
        assertEquals(Color.White, palette.muted)
        assertTrue(ambientLightColor(palette.secondary).luminance() in 0.05f..0.10f)
    }

    @Test
    fun artworkRetainsItsHueWithSoftSaturationAndBoundedLuminance() {
        val levels = listOf(0f, 0.15f, 0.35f, 0.65f, 1f)
        for (red in levels) for (green in levels) for (blue in levels) {
            val source = Color(red, green, blue)
            val toned = ambientLightColor(source)
            assertTrue("Source $source produced excessive luminance ${toned.luminance()}", toned.luminance() <= 0.24f)
            assertTrue(maxOf(toned.red, toned.green, toned.blue) <= 0.801f)
            assertTrue(maxOf(toned.red, toned.green, toned.blue) <= maxOf(source.red, source.green, source.blue) + 0.002f)
        }
        for (source in listOf(Color.Red, Color.Blue, Color.Magenta, Color(0xFFFF7020))) {
            val toned = ambientLightColor(source)
            assertTrue("Artwork hue disappeared: $toned", maxOf(toned.red, toned.green, toned.blue) > 0.55f)
            assertTrue(saturation(toned) < saturation(source))
            assertTrue(saturation(toned) > saturation(source) * 0.55f)
            // Equal-channel desaturation retains channel ordering (hue family).
            assertEquals(source.red.compareTo(source.blue), toned.red.compareTo(toned.blue))
            assertEquals(source.green.compareTo(source.blue), toned.green.compareTo(toned.blue))
        }
    }

    @Test
    fun whiteAndGrayStayRestrainedAndAlmostBlackIsNeverLifted() {
        for (source in listOf(Color.White, Color.LightGray, Color.Gray)) {
            val toned = ambientLightColor(source)
            assertTrue(toned.luminance() <= 0.10f)
            assertTrue(toned.red < source.red)
            assertEquals(toned.red, toned.green, 0f)
            assertEquals(toned.red, toned.blue, 0f)
        }
        for (source in listOf(Color.Black, Color(0xFF080506), Color(0xFF101010))) {
            val toned = ambientLightColor(source)
            assertEquals(source.red, toned.red, 0.00001f)
            assertEquals(source.green, toned.green, 0.00001f)
            assertEquals(source.blue, toned.blue, 0.00001f)
        }
    }

    private fun ArtworkAmbientColors.colors() = listOf(dominant, secondary, muted, dark, accent)

    private fun saturation(color: Color): Float {
        val maximum = maxOf(color.red, color.green, color.blue)
        return if (maximum == 0f) 0f else (maximum - minOf(color.red, color.green, color.blue)) / maximum
    }
}

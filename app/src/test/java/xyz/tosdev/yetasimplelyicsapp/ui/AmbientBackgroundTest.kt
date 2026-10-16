package xyz.tosdev.yetasimplelyicsapp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.tosdev.yetasimplelyicsapp.ui.designsystem.*
import xyz.tosdev.yetasimplelyicsapp.ui.theme.*

class AmbientBackgroundTest {
    private val fixtures = linkedMapOf(
        "Orange-brown" to listOf(0xFF100C09, 0xFFDB7732, 0xFF8E4230, 0xFF573B2C),
        "Blue" to listOf(0xFF090D18, 0xFF287CE2, 0xFF4057AB, 0xFF2C3D65),
        "Purple navy" to listOf(0xFF100926, 0xFF5D36AE, 0xFF233C86, 0xFF77619A),
        "Red" to listOf(0xFF180A0A, 0xFFF03832, 0xFF9A3553, 0xFF593132),
        "Green" to listOf(0xFF0A120D, 0xFF36CB74, 0xFF328C72, 0xFF354A36),
        "Very bright" to listOf(0xFFFAFAFA, 0xFFF6F3ED, 0xFFEDECEF, 0xFFD6D3D1),
        "Almost black" to listOf(0xFF040404, 0xFF0B0B0B, 0xFF111111, 0xFF171717),
        "Multicolor" to listOf(0xFF131119, 0xFFDF6691, 0xFF3DA3C4, 0xFFC48E42),
        "Black and white" to listOf(0xFF050505, 0xFFF5F5F5, 0xFF999999, 0xFF444444),
        "Olive" to listOf(0xFF373C1F, 0xFF85866B, 0xFFB1AF96, 0xFF203323),
    ).mapValues { (_, colors) ->
        artworkAmbientColors(colors.mapIndexed { index, rgb -> AmbientSwatch(Color(rgb), 400 - index * 90) })
    }
    private val track = AmbientTrackVariation(0f, 0f, 1f, 1f)

    @Test
    fun gridFollowsPortraitAndLandscapeWithoutAllocatingScreenSizedBuffers() {
        val portrait = atmosphereGridSize(1_080f, 1_920f)
        val landscape = atmosphereGridSize(1_920f, 1_080f)
        assertEquals(portrait.width, landscape.height)
        assertEquals(portrait.height, landscape.width)
        assertEquals(24, minOf(portrait.width, portrait.height))
        assertTrue(abs(portrait.width.toFloat() / portrait.height - 1_080f / 1_920f) < 0.03f)
        for ((width, height) in listOf(0f to 0f, 1f to 1f, 1f to 10_000f, 10_000f to 1f, 4_000f to 4_000f)) {
            val grid = atmosphereGridSize(width, height)
            assertTrue("Invalid grid for $width x $height: $grid", grid.width in 2..64 && grid.height in 2..64)
        }
    }

    @Test
    fun meshCoversTheViewportWithTwoTrianglesPerCell() {
        val mesh = AtmosphereMesh(100f, 60f, 3, 2)

        assertArrayEquals(floatArrayOf(
            0f, 0f, 50f, 0f, 100f, 0f,
            0f, 60f, 50f, 60f, 100f, 60f,
        ), mesh.positions, 0f)
        assertArrayEquals(shortArrayOf(
            0, 1, 3, 1, 4, 3,
            1, 2, 4, 2, 5, 4,
        ), mesh.indices)
        assertEquals((3 - 1) * (2 - 1) * 6, mesh.indices.size)
        assertEquals((0 until 6).toSet(), mesh.indices.map { it.toInt() }.toSet())
    }

    @Test
    fun renderReusesItsVertexColorBufferAndPaintsEveryVertexOpaque() {
        val field = ProceduralAtmosphereField(24, 43)
        val colors = fixtures.getValue("Orange-brown")
        val first = renderInto(field, colors, 0L)
        val before = first.copyOf()
        assertSame(field.vertexColors, first)
        val later = renderInto(field, colors, 30_000L)
        assertSame(first, later)
        assertEquals(24 * 43, later.size)
        assertTrue("Reused buffer must receive the new frame", difference(before, later).mean > 0.05)

        for ((name, palette) in fixtures) for ((width, height) in listOf(2 to 2, 24 to 43, 43 to 24)) {
            val vertexColors = renderInto(ProceduralAtmosphereField(width, height), palette, 20_000L)
            assertTrue("Transparent vertex: $name/$width/$height", vertexColors.all { it ushr 24 == 255 })
        }
    }

    @Test
    fun allPalettesKeepWhiteTextReadableAcrossTheMotionCycle() {
        val variations = listOf(track) + listOf(-0.04f, 0.04f).flatMap { x ->
            listOf(-0.03f, 0.03f).map { y -> AmbientTrackVariation(x, y, 1.06f, 1.08f) }
        }
        var worstContrast = Float.POSITIVE_INFINITY
        var maximumLuminance = 0f
        var brightest = ""
        for ((name, colors) in fixtures) for (variation in variations) {
            val field = ProceduralAtmosphereField(24, 43)
            for (time in 0L..AMBIENT_CYCLE_DURATION_MS.toLong() step 21_000L) {
                for (pixel in renderInto(field, colors, time, MotionLevel.ENHANCED, variation)) {
                    val luminance = Color(pixel).luminance()
                    val contrast = 1.05f / (luminance + 0.05f)
                    if (luminance > maximumLuminance) {
                        maximumLuminance = luminance
                        brightest = "$name at ${time / 1_000}s with $variation"
                    }
                    worstContrast = minOf(worstContrast, contrast)
                }
            }
        }
        println("CPU fallback pixel model: minimum white contrast=$worstContrast:1; maximum luminance=$maximumLuminance; $brightest")
        assertTrue("Unreadable white text: $worstContrast; $brightest", worstContrast >= 4.5f)
        assertTrue("Atmosphere exceeds its readable color-field envelope: $maximumLuminance; $brightest", maximumLuminance <= 0.19f)
    }

    @Test
    fun offIsIdenticalAndReducedChangesLessThanNormal() {
        for ((name, colors) in fixtures) {
            assertArrayEquals("Off changed: $name", frame(colors, 0L, MotionLevel.OFF).pixels,
                frame(colors, 40_000L, MotionLevel.OFF).pixels)
        }
        val colors = fixtures.getValue("Orange-brown")
        var normal = 0.0
        var reduced = 0.0
        for (start in listOf(0L, 17_000L, 47_000L)) {
            normal += difference(frame(colors, start).pixels, frame(colors, start + 30_000L).pixels).mean
            reduced += difference(frame(colors, start, MotionLevel.REDUCED).pixels,
                frame(colors, start + 30_000L, MotionLevel.REDUCED).pixels).mean
        }
        assertTrue("Normal field never evolves: $normal", normal > 0.15)
        assertTrue("Reduced must materially attenuate motion: reduced=$reduced normal=$normal", reduced < normal * 0.6)
    }

    @Test
    fun everyTwentySecondWindowEvolvesAndOneSecondMotionStaysBounded() {
        val report = StringBuilder("artwork,windowStartSeconds,meanChannelSteps20s,meanChannelSteps1s,required20sFloor\n")
        val frozen = mutableListOf<String>()
        val flashes = mutableListOf<String>()
        for ((name, colors) in fixtures) {
            val requiredChange = when (name) {
                "Almost black", "Black and white", "Olive" -> 0.8
                else -> 2.0
            }
            for (start in listOf(0L, 20_000L, 40_000L, 60_000L, 80_000L, 100_000L, 240_000L)) {
                val initial = frame(colors, start).pixels
                val twenty = difference(initial, frame(colors, start + 20_000L).pixels)
                val one = difference(initial, frame(colors, start + 1_000L).pixels)
                report.append("$name,${start / 1_000},${twenty.mean},${one.mean},$requiredChange\n")
                if (one.mean >= 10.0) flashes += "$name/${start / 1_000}s: ${one.mean}"
                if (twenty.mean < requiredChange) {
                    frozen += "$name/${start / 1_000}s: ${twenty.mean} < $requiredChange"
                }
            }
        }
        System.getProperty("ambient.validation.dir")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "atmosphere-temporal-motion.csv").writeText(report.toString())
        }
        assertTrue("Frozen 20-second atmosphere windows: ${frozen.joinToString()}; " +
            "one-second flashes: ${flashes.joinToString()}", frozen.isEmpty() && flashes.isEmpty())
    }

    @Test
    fun phaseWrapsAreContinuousAtEightAndSeventeenMillisecondIntervals() {
        val colors = fixtures.getValue("Multicolor")
        val discontinuities = mutableListOf<String>()
        for (level in listOf(MotionLevel.NORMAL, MotionLevel.ENHANCED, MotionLevel.REDUCED)) {
            val field = ProceduralAtmosphereField(24, 43)
            val first = renderPhase(field, colors, 0f, level).copyOf()
            val closed = renderPhase(field, colors, 1f, level).copyOf()
            assertArrayEquals("Whole cycle did not close: $level", first, closed)
            val speed = ambientCycleDurationMillis(level) / AMBIENT_CYCLE_DURATION_MS
            for (period in AMBIENT_PHASE_DURATIONS_MS + AMBIENT_CYCLE_DURATION_MS) for (interval in listOf(8L, 17L)) {
                val boundary = period.toLong() * speed
                val before = frame(colors, boundary - interval, level).pixels
                val after = frame(colors, boundary + interval, level).pixels
                val step = difference(before, after)
                if (step.mean >= 0.60 || step.maximum > 1) discontinuities += "$level/$period/$interval: $step"
            }
        }
        assertTrue("Phase discontinuities: ${discontinuities.joinToString()}", discontinuities.isEmpty())
    }

    @Test
    fun retargetingColorsUsesTheRequestedPhaseInsteadOfRestartingItsField() {
        val field = ProceduralAtmosphereField(24, 43)
        renderInto(field, fixtures.getValue("Orange-brown"), 30_000L)
        val retargeted = renderInto(field, fixtures.getValue("Blue"), 30_000L).copyOf()
        val independent = renderInto(ProceduralAtmosphereField(24, 43), fixtures.getValue("Blue"), 30_000L)
        assertArrayEquals(independent, retargeted)
    }

    @Test
    fun adaptedPalettePreservesArtworkCharacterWithoutMutatingRawSwatches() {
        for ((name, raw) in fixtures) {
            val before = raw.copy()
            val adapted = proceduralAtmospherePalette(raw)
            assertEquals("Raw artwork changed: $name", before, raw)
            for ((role, pair) in paletteColors(raw).zip(paletteColors(adapted)).withIndex()) {
                val (source, toned) = pair
                assertTrue("Palette exceeds readable envelope: $name/$toned", toned.luminance() <= 0.1751f)
                assertEquals(1f, toned.alpha, 0f)
                if (role == 4) continue // The dark anchor also incorporates the primary tone.
                val original = listOf(source.red, source.green, source.blue)
                val result = listOf(toned.red, toned.green, toned.blue)
                for (a in original.indices) for (b in original.indices) {
                    if (abs(original[a] - original[b]) > 0.02f) {
                        assertEquals("Artwork hue family changed: $name/$source/$toned",
                            original[a].compareTo(original[b]), result[a].compareTo(result[b]))
                    }
                }
                if (original.max() - original.min() > 0.20f) {
                    val sourceSaturation = (original.max() - original.min()) / original.max()
                    val tonedSaturation = (result.max() - result.min()) / result.max()
                    assertTrue("Chromatic artwork became neutral: $name/$toned",
                        tonedSaturation >= sourceSaturation * 0.60f)
                }
            }
        }
        val warm = frame(fixtures.getValue("Orange-brown"), 0L).pixels
        val blue = frame(fixtures.getValue("Blue"), 0L).pixels
        assertTrue("Different artwork produced the same background", difference(warm, blue).mean > 10.0)
        assertTrue("Warm artwork lost its warm identity", warm.sumOf { (it ushr 16) and 255 } > warm.sumOf { it and 255 })
        assertTrue("Blue artwork lost its cool identity", blue.sumOf { it and 255 } > blue.sumOf { (it ushr 16) and 255 })
    }

    @Test
    fun animatedPaletteTransitionsRemainReadableAndFieldMixturesStayConvex() {
        val adapted = fixtures.mapValues { (_, raw) -> proceduralAtmospherePalette(raw) }
        val field = ProceduralAtmosphereField(24, 43)
        var minimumContrast = Float.POSITIVE_INFINITY
        for ((fromName, from) in adapted) for ((toName, to) in adapted) {
            for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val colors = interpolatePalette(from, to, fraction)
                val envelope = paletteColors(colors)
                val bounds = listOf<(Color) -> Float>({ it.red }, { it.green }, { it.blue }).map { channel ->
                    envelope.minOf(channel) * 255f to envelope.maxOf(channel) * 255f
                }
                for (phase in listOf(0f, 0.07f, 0.31f, 0.62f)) {
                    for (pixel in renderAdapted(field, colors, phase, MotionLevel.ENHANCED,
                        AmbientTrackVariation(0.04f, -0.03f, 1.06f, 1.08f))) {
                        val color = Color(pixel)
                        val contrast = 1.05f / (color.luminance() + 0.05f)
                        minimumContrast = minOf(minimumContrast, contrast)
                        assertTrue("Unreadable palette transition: $fromName->$toName/$fraction/$phase: $contrast",
                            contrast >= 4.5f)
                        for ((channel, shift) in listOf(16, 8, 0).withIndex()) {
                            val value = (pixel ushr shift) and 255
                            assertTrue("Field escaped convex palette envelope: $fromName->$toName/$fraction",
                                value >= bounds[channel].first - 1f && value <= bounds[channel].second + 1f)
                        }
                    }
                }
            }
        }
        println("CPU fallback palette transitions: minimum white contrast=$minimumContrast:1")
    }

    @Test
    fun sixtyAndOneHundredTwentyHertzStepsStayContinuousDuringOrdinaryMotion() {
        for ((name, colors) in fixtures) for (level in listOf(MotionLevel.NORMAL, MotionLevel.ENHANCED, MotionLevel.REDUCED)) {
            val field = ProceduralAtmosphereField(24, 43)
            for (start in 0L..120_000L step 1_000L) {
                val before = renderInto(field, colors, start, level).copyOf()
                for (interval in listOf(8L, 17L)) {
                    val step = difference(before, renderInto(field, colors, start + interval, level))
                    assertTrue("Frame discontinuity: $name/$level/$start/$interval: $step",
                        step.mean < 0.60 && step.maximum <= 1)
                }
            }
        }
    }

    @Test
    fun atmosphereHasBroadTwoDimensionalCurvatureWithoutHardSpatialEdges() {
        val sample = frame(fixtures.getValue("Orange-brown"), 0L)
        val rows = (0 until sample.height).map { y -> (0 until sample.width).map { x -> sample.pixels[y * sample.width + x] } }
        val columns = (0 until sample.width).map { x -> (0 until sample.height).map { y -> sample.pixels[y * sample.width + x] } }
        assertTrue("No horizontal atmosphere", rows.maxOf(::channelRange) >= 4)
        assertTrue("No vertical atmosphere", columns.maxOf(::channelRange) >= 4)
        val curvature = (rows + columns).maxOf { line ->
            line.indices.maxOf { index -> linearDeviation(line[index], line.first(), line.last(), index.toFloat() / line.lastIndex) }
        }
        assertTrue("Field collapsed to a linear gradient: $curvature", curvature >= 2f)

        val image = raster(sample, 288, 512)
        var maximumEdge = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (x > 0) maximumEdge = maxOf(maximumEdge, channelDelta(image.getRGB(x, y), image.getRGB(x - 1, y)))
            if (y > 0) maximumEdge = maxOf(maximumEdge, channelDelta(image.getRGB(x, y), image.getRGB(x, y - 1)))
        }
        assertTrue("Hard spatial edge in CPU fallback model: $maximumEdge", maximumEdge <= 2)
    }

    @Test
    fun monochromeArtworkDoesNotCreateColoredAtmosphere() {
        val pixels = frame(fixtures.getValue("Black and white"), 20_000L).pixels
        for (pixel in pixels) {
            val channels = listOf((pixel ushr 16) and 255, (pixel ushr 8) and 255, pixel and 255)
            assertTrue("Invented color on monochrome artwork", channels.max() - channels.min() <= 1)
        }
    }

    @Test
    fun exportOptionalAtmosphereEvidenceFromTheProductionVertexField() {
        val directory = System.getProperty("ambient.validation.dir")?.let(::File) ?: return
        directory.mkdirs()
        val paletteJson = fixtures.entries.joinToString(",\n", prefix = "[\n", postfix = "\n]\n") { (name, raw) ->
            val palette = proceduralAtmospherePalette(raw)
            fun rgb(color: Color) = "[${color.red},${color.green},${color.blue}]"
            "  {\"name\":\"$name\",\"dominant\":${rgb(palette.dominant)}," +
                "\"secondary\":${rgb(palette.secondary)},\"muted\":${rgb(palette.muted)}," +
                "\"accent\":${rgb(palette.accent)},\"base\":${rgb(palette.dark)}}"
        }
        File(directory, "palettes.json").writeText(paletteJson)
        val width = 288
        val height = 512
        val labelHeight = 44
        val columns = 3
        val rows = (fixtures.size + columns - 1) / columns
        val gallery = BufferedImage(width * columns, (height + labelHeight) * rows + 30, BufferedImage.TYPE_INT_RGB)
        val graphics = gallery.createGraphics()
        graphics.color = java.awt.Color(18, 18, 20)
        graphics.fillRect(0, 0, gallery.width, gallery.height)
        graphics.color = java.awt.Color.WHITE
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        graphics.drawString("CPU FALLBACK MODEL / NOT ANDROID", 12, 20)
        fixtures.entries.forEachIndexed { index, (name, colors) ->
            val image = raster(frame(colors, 0L), width, height)
            val x = (index % columns) * width
            val y = (index / columns) * (height + labelHeight) + 30
            graphics.drawImage(image, x, y, null)
            graphics.color = java.awt.Color.WHITE
            graphics.drawString(name, x + 12, y + height + 19)
            graphics.color = java.awt.Color(166, 166, 170)
            graphics.drawString("CPU FALLBACK MODEL / NOT ANDROID", x + 12, y + height + 35)
            ImageIO.write(labelled(image, "$name | CPU FALLBACK MODEL / NOT ANDROID"), "png",
                File(directory, "atmosphere-${name.lowercase().replace(' ', '-')}-model.png"))
        }
        graphics.dispose()
        ImageIO.write(gallery, "png", File(directory, "atmosphere-palettes-model.png"))
        val timelineTimes = listOf(0L, 5_000L, 10_000L, 15_000L, 20_000L)
        val timelineWidth = 144
        val timelineHeight = 256
        val timeline = BufferedImage(timelineWidth * timelineTimes.size, timelineHeight, BufferedImage.TYPE_INT_RGB)
        val timelineGraphics = timeline.createGraphics()
        for ((index, time) in timelineTimes.withIndex()) {
            val image = labelled(raster(frame(fixtures.getValue("Orange-brown"), time), timelineWidth, timelineHeight),
                "CPU FALLBACK MODEL / NOT ANDROID | ${time / 1_000}s")
            timelineGraphics.drawImage(image, index * timelineWidth, 0, null)
        }
        timelineGraphics.dispose()
        ImageIO.write(timeline, "png", File(directory, "atmosphere-warm-0-20s-contact-sheet-model.png"))
        for (time in timelineTimes) {
            val image = raster(frame(fixtures.getValue("Orange-brown"), time), 432, 768)
            ImageIO.write(labelled(image, "CPU FALLBACK MODEL / NOT ANDROID | ${time / 1_000}s"), "png",
                File(directory, "atmosphere-warm-${time / 1_000}s-model.png"))
        }
        if (System.getProperty("ambient.validation.motion") == "true") {
            exportMotionPreview(File(directory, "atmosphere-motion-model.gif"))
        }
    }

    private data class Frame(val width: Int, val height: Int, val pixels: IntArray)
    private data class Difference(val mean: Double, val maximum: Int)

    private fun paletteColors(colors: ArtworkAmbientColors) =
        listOf(colors.dominant, colors.secondary, colors.muted, colors.accent, colors.dark)

    private fun interpolatePalette(from: ArtworkAmbientColors, to: ArtworkAmbientColors, fraction: Float): ArtworkAmbientColors {
        // Compose lerp follows the Oklab conversion used by animateColorAsState.
        fun mix(a: Color, b: Color) = lerp(a, b, fraction)
        return ArtworkAmbientColors(mix(from.dominant, to.dominant), mix(from.secondary, to.secondary),
            mix(from.muted, to.muted), mix(from.dark, to.dark), mix(from.accent, to.accent))
    }

    private fun frame(colors: ArtworkAmbientColors, timeMs: Long, level: MotionLevel = MotionLevel.NORMAL): Frame {
        val size = atmosphereGridSize(288f, 512f)
        val field = ProceduralAtmosphereField(size.width, size.height)
        return Frame(size.width, size.height, renderInto(field, colors, timeMs, level).copyOf())
    }

    private fun renderInto(
        field: ProceduralAtmosphereField, colors: ArtworkAmbientColors, timeMs: Long,
        level: MotionLevel = MotionLevel.NORMAL, variation: AmbientTrackVariation = track,
    ): IntArray {
        val duration = ambientCycleDurationMillis(level)
        return renderPhase(field, colors, (timeMs % duration).toFloat() / duration, level, variation)
    }

    private fun renderPhase(
        field: ProceduralAtmosphereField, colors: ArtworkAmbientColors, phase: Float,
        level: MotionLevel, variation: AmbientTrackVariation = track,
    ): IntArray = renderAdapted(field, proceduralAtmospherePalette(colors), phase, level, variation)

    private fun renderAdapted(
        field: ProceduralAtmosphereField, colors: ArtworkAmbientColors, phase: Float,
        level: MotionLevel, variation: AmbientTrackVariation = track,
    ): IntArray = field.render(
        colors.dominant, colors.secondary, colors.muted, colors.accent, colors.dark,
        phase, ambientMotionProfile(level), variation,
    )

    private fun raster(frame: Frame, width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val mesh = AtmosphereMesh(width.toFloat(), height.toFloat(), frame.width, frame.height)
        for (triangle in mesh.indices.indices step 3) {
            val a = mesh.indices[triangle].toInt()
            val b = mesh.indices[triangle + 1].toInt()
            val c = mesh.indices[triangle + 2].toInt()
            rasterTriangle(image, mesh.positions, a, b, c, frame.pixels[a], frame.pixels[b], frame.pixels[c])
        }
        return image
    }

    private fun rasterTriangle(
        image: BufferedImage, positions: FloatArray, a: Int, b: Int, c: Int,
        colorA: Int, colorB: Int, colorC: Int,
    ) {
        val ax = positions[a * 2]; val ay = positions[a * 2 + 1]
        val bx = positions[b * 2]; val by = positions[b * 2 + 1]
        val cx = positions[c * 2]; val cy = positions[c * 2 + 1]
        val denominator = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        val minX = floor(minOf(ax, bx, cx)).toInt().coerceAtLeast(0)
        val maxX = ceil(maxOf(ax, bx, cx)).toInt().coerceAtMost(image.width - 1)
        val minY = floor(minOf(ay, by, cy)).toInt().coerceAtLeast(0)
        val maxY = ceil(maxOf(ay, by, cy)).toInt().coerceAtMost(image.height - 1)
        for (y in minY..maxY) for (x in minX..maxX) {
            val sampleX = x + 0.5f
            val sampleY = y + 0.5f
            val weightA = ((by - cy) * (sampleX - cx) + (cx - bx) * (sampleY - cy)) / denominator
            val weightB = ((cy - ay) * (sampleX - cx) + (ax - cx) * (sampleY - cy)) / denominator
            val weightC = 1f - weightA - weightB
            if (weightA >= -0.0001f && weightB >= -0.0001f && weightC >= -0.0001f) {
                image.setRGB(x, y, interpolateColor(colorA, colorB, colorC, weightA, weightB, weightC))
            }
        }
    }

    private fun interpolateColor(
        a: Int, b: Int, c: Int, weightA: Float, weightB: Float, weightC: Float,
    ): Int {
        fun channel(shift: Int) = (((a ushr shift and 255) * weightA) +
            ((b ushr shift and 255) * weightB) + ((c ushr shift and 255) * weightC))
            .toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun labelled(image: BufferedImage, label: String): BufferedImage = image.apply {
        val graphics = createGraphics()
        val lines = label.split(" | ")
        graphics.color = java.awt.Color.BLACK
        graphics.fillRect(0, 0, width, lines.size * 18)
        graphics.color = java.awt.Color.WHITE
        graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, if (width <= 144) 7 else if (width < 250) 9 else 11)
        lines.forEachIndexed { index, line -> graphics.drawString(line, 6, 13 + index * 18) }
        graphics.dispose()
    }

    private fun exportMotionPreview(file: File) {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val size = atmosphereGridSize(216f, 384f)
        val field = ProceduralAtmosphereField(size.width, size.height)
        val colors = fixtures.getValue("Orange-brown")
        try {
            ImageIO.createImageOutputStream(file).use { output ->
                writer.output = output
                writer.prepareWriteSequence(null)
                // A fixed-palette real-time 20-second excerpt, not a sped-up animation or full-cycle loop.
                // No looping extension: restarting this excerpt would introduce a false jump.
                for (index in 0 until 400) {
                    val timeMs = index * 50L
                    val pixels = renderInto(field, colors, timeMs)
                    val image = labelled(raster(Frame(field.width, field.height, pixels), 216, 384),
                        "CPU FALLBACK MODEL / NOT ANDROID | ${timeMs / 1_000f}s")
                    val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), writer.defaultWriteParam)
                    val format = metadata.nativeMetadataFormatName
                    val root = metadata.getAsTree(format) as IIOMetadataNode
                    val control = root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
                    control.setAttribute("disposalMethod", "none")
                    control.setAttribute("userInputFlag", "FALSE")
                    control.setAttribute("transparentColorFlag", "FALSE")
                    control.setAttribute("delayTime", "5")
                    control.setAttribute("transparentColorIndex", "0")
                    metadata.setFromTree(format, root)
                    writer.writeToSequence(IIOImage(image, null, metadata), writer.defaultWriteParam)
                }
                writer.endWriteSequence()
            }
        } finally {
            writer.dispose()
        }
    }

    private fun difference(first: IntArray, second: IntArray): Difference {
        assertEquals(first.size, second.size)
        var total = 0L
        var maximum = 0
        for (index in first.indices) {
            val delta = channelDelta(first[index], second[index])
            total += delta
            maximum = maxOf(maximum, delta)
        }
        return Difference(total.toDouble() / first.size, maximum)
    }

    private fun channelDelta(first: Int, second: Int): Int = listOf(16, 8, 0).maxOf { shift ->
        abs(((first ushr shift) and 255) - ((second ushr shift) and 255))
    }

    private fun channelRange(pixels: List<Int>): Int = listOf(16, 8, 0).maxOf { shift ->
        pixels.maxOf { (it ushr shift) and 255 } - pixels.minOf { (it ushr shift) and 255 }
    }

    private fun linearDeviation(pixel: Int, start: Int, end: Int, fraction: Float): Float = listOf(16, 8, 0).maxOf { shift ->
        val from = (start ushr shift) and 255
        val to = (end ushr shift) and 255
        abs(((pixel ushr shift) and 255) - (from + (to - from) * fraction))
    }
}

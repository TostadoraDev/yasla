package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/** Canvas-native drawing. Neither backend samples an image or allocates a frame bitmap. */
internal class ProceduralAtmosphereCanvas(private val width: Float, private val height: Float) {
    private val grid = atmosphereGridSize(width, height)
    private val field = ProceduralAtmosphereField(grid.width, grid.height)
    private val mesh = AtmosphereMesh(width, height, grid.width, grid.height)
    private val paint = Paint(Paint.DITHER_FLAG).apply { color = android.graphics.Color.WHITE }
    // Hardware drawVertices is supported from API 29. Older Android keeps the same clock
    // and palette transitions with three very broad, transformed procedural light washes.
    private var legacy: LegacyAtmosphereWashes? = null
    private val gpu = if (Build.VERSION.SDK_INT >= 33) AtmosphereGpu(width, height) else null

    fun draw(
        canvas: Canvas, dominant: Color, secondary: Color, muted: Color, accent: Color, base: Color,
        cycleFraction: Float, profile: AmbientMotionProfile,
        horizontalBias: Float, verticalBias: Float, radiusScale: Float, energy: Float,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated) {
            requireNotNull(gpu).draw(canvas, dominant, secondary, muted, accent, base,
                cycleFraction, profile, horizontalBias, verticalBias, radiusScale, energy)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || !canvas.isHardwareAccelerated) {
            val colors = field.render(dominant, secondary, muted, accent, base, cycleFraction, profile,
                horizontalBias, verticalBias, radiusScale, energy, width / height)
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, mesh.positions.size, mesh.positions, 0,
                null, 0, colors, 0, mesh.indices, 0, mesh.indices.size, paint)
        } else {
            val washes = legacy ?: LegacyAtmosphereWashes(width, height).also { legacy = it }
            washes.draw(canvas, dominant, secondary, muted, accent, base, cycleFraction, profile,
                horizontalBias, verticalBias, radiusScale, energy)
        }
    }
}

/** One full-viewport shader pass. Compile once; subsequent frames update only uniforms. */
@RequiresApi(33)
private class AtmosphereGpu(private val width: Float, private val height: Float) {
    private val shader = RuntimeShader(PROCEDURAL_ATMOSPHERE_SHADER).apply {
        setFloatUniform("resolution", width, height)
    }
    private val uniforms = AtmosphereUniforms()
    private val paint = Paint(Paint.DITHER_FLAG).apply { shader = this@AtmosphereGpu.shader }

    fun draw(canvas: Canvas, dominant: Color, secondary: Color, muted: Color, accent: Color,
        base: Color, cycle: Float, profile: AmbientMotionProfile,
        xBias: Float, yBias: Float, radius: Float, energy: Float,
    ) {
        uniforms.update(cycle, profile)
        shader.setFloatUniform("drift", uniforms.drift)
        shader.setFloatUniform("evolution", uniforms.evolution)
        shader.setFloatUniform("variation", xBias, yBias, radius, energy)
        // These are sRGB numeric vectors, intentionally without layout(color): the CPU
        // fallback and shader interpolate the same bounded sRGB field.
        shader.setFloatUniform("dominant", dominant.red, dominant.green, dominant.blue)
        shader.setFloatUniform("secondary", secondary.red, secondary.green, secondary.blue)
        shader.setFloatUniform("muted", muted.red, muted.green, muted.blue)
        shader.setFloatUniform("accent", accent.red, accent.green, accent.blue)
        shader.setFloatUniform("base", base.red, base.green, base.blue)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

/** Compatibility path: oversize overlapping shoulders, no clipped circles or blur passes. */
internal class LegacyAtmosphereWashes(private val width: Float, private val height: Float) {
    private val paint = Paint(Paint.DITHER_FLAG)
    private val matrix = Matrix()
    private val shaders = arrayOfNulls<RadialGradient>(3)
    private val colorKeys = IntArray(3)
    private val stops = floatArrayOf(0f, 0.22f, 0.48f, 0.74f, 1f)

    fun draw(
        canvas: Canvas, dominant: Color, secondary: Color, muted: Color, accent: Color, base: Color,
        cycle: Float, profile: AmbientMotionProfile,
        horizontalBias: Float, verticalBias: Float, radiusScale: Float, energy: Float,
    ) {
        val a = ambientPhaseAt(0, cycle)
        val b = ambientPhaseAt(1, cycle)
        val c = ambientPhaseAt(2, cycle)
        val d = ambientPhaseAt(3, cycle)
        val drift = profile.driftFraction
        val flow = (drift / 0.09f).coerceAtMost(1.25f)
        val breadth = radiusScale * (1f + profile.radiusBreath * sin(c))
        canvas.drawColor(base.toArgb())
        for (index in 0..2) {
            val color = when (index) {
                0 -> lerp(dominant, secondary, 0.38f)
                1 -> lerp(secondary, muted, 0.36f)
                else -> lerp(dominant, accent, 0.24f)
            }.toArgb()
            if (shaders[index] == null || colorKeys[index] != color) {
                colorKeys[index] = color
                // Smooth alpha shoulders; RGB remains the artwork color through the tail.
                val rgb = color and 0x00FFFFFF
                shaders[index] = RadialGradient(0f, 0f, 1f,
                    intArrayOf(rgb or (255 shl 24), rgb or (230 shl 24), rgb or (140 shl 24),
                        rgb or (40 shl 24), rgb), stops, Shader.TileMode.CLAMP)
            }
            val phase = when (index) { 0 -> a; 1 -> b; else -> d }
            val centerX = 0.46f + horizontalBias + (index - 1) * 0.08f +
                drift * (sin(phase) + 0.35f * cos(c))
            val centerY = 0.44f + verticalBias + (index - 1) * 0.10f +
                drift * (cos(phase) + 0.4f * sin(d))
            matrix.setScale(width * (1.12f + index * 0.12f) * breadth,
                height * (0.62f + index * 0.09f) * breadth)
            matrix.postRotate((index - 1) * 19f + flow * 16f * sin(phase))
            matrix.postTranslate(width * centerX, height * centerY)
            val shader = requireNotNull(shaders[index])
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            val opacity = when (index) { 0 -> 0.38f; 1 -> 0.22f; else -> 0.12f }
            paint.alpha = (255f * opacity * energy * (1f + profile.alphaBreath * sin(phase))).toInt()
                .coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width, height, paint)
        }
    }
}

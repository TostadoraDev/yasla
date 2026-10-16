package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import xyz.tosdev.yetasimplelyicsapp.ui.theme.ArtworkAmbientColors
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Palette adaptation happens once per artwork, not per pixel/frame. */
internal fun proceduralAtmospherePalette(source: ArtworkAmbientColors): ArtworkAmbientColors {
    fun tone(color: Color): Color {
        val peak = maxOf(color.red, color.green, color.blue)
        // Even near-black art has a restrained neutral light floor; no invented hues.
        val scale = if (peak > 0f) maxOf(peak, 0.22f) / peak else 1f
        val lifted = if (peak == 0f) Color(0.22f, 0.22f, 0.22f) else
            Color(color.red * scale, color.green * scale, color.blue * scale)
        val gray = (lifted.red + lifted.green + lifted.blue) / 3f
        val soft = lerp(lifted, Color(gray, gray, gray), 0.08f)
        // Convex color mixtures cannot exceed this luminance envelope in sRGB.
        var low = 0f
        var high = 1f
        repeat(18) {
            val amount = (low + high) * 0.5f
            val test = Color(soft.red * amount, soft.green * amount, soft.blue * amount)
            if (test.luminance() <= 0.175f) low = amount else high = amount
        }
        return Color(soft.red * low, soft.green * low, soft.blue * low)
    }
    val primary = tone(source.dominant)
    val dark = lerp(tone(source.dark), primary, 0.32f)
    return ArtworkAmbientColors(primary, tone(source.secondary), tone(source.muted),
        Color(dark.red * 0.34f, dark.green * 0.34f, dark.blue * 0.34f), tone(source.accent))
}

/** Mutable uniform buffers shared by the GPU and CPU evaluators; no per-frame allocations. */
internal class AtmosphereUniforms {
    val drift = FloatArray(4)
    val evolution = FloatArray(4)

    fun update(cycle: Float, profile: AmbientMotionProfile) {
        val a = ambientPhaseAt(0, cycle)
        val b = ambientPhaseAt(1, cycle)
        val c = ambientPhaseAt(2, cycle)
        val d = ambientPhaseAt(3, cycle)
        val strength = profile.driftFraction / 0.09f * 0.50f
        drift[0] = (sin(a)*0.82f + cos(c)*0.31f)*strength
        drift[1] = (cos(b)*0.76f + sin(d)*0.27f)*strength
        drift[2] = (cos(c)*0.68f - sin(b)*0.37f)*strength
        drift[3] = (sin(d)*0.73f + cos(a)*0.29f)*strength
        evolution[0] = sin(c)*0.57f*strength
        evolution[1] = cos(d)*0.63f*strength
        evolution[2] = 1f + profile.radiusBreath*sin(b)
        evolution[3] = 1f + profile.alphaBreath*sin(c)
    }
}

/** Compatibility path samples the same domain-warped field, not a screenshot of the shader. */
internal class ProceduralAtmosphereField(val width: Int, val height: Int) {
    init { require(width in 2..64 && height in 2..64) }
    val vertexColors = IntArray(width * height)
    private val uniforms = AtmosphereUniforms()

    fun render(
        dominant: Color, secondary: Color, muted: Color, accent: Color, base: Color,
        cycle: Float, profile: AmbientMotionProfile, track: AmbientTrackVariation,
    ) = render(dominant, secondary, muted, accent, base, cycle, profile,
        track.horizontalBias, track.verticalBias, track.radiusScale, track.energy)

    fun render(
        dominant: Color, secondary: Color, muted: Color, accent: Color, base: Color,
        cycle: Float, profile: AmbientMotionProfile,
        xBias: Float, yBias: Float, radius: Float, energy: Float,
        aspectRatio: Float = (width - 1f) / (height - 1f),
    ): IntArray {
        uniforms.update(cycle, profile)
        val d = uniforms.drift
        val e = uniforms.evolution
        val ax = maxOf(1f, aspectRatio).coerceAtMost(2.4f)
        val ay = maxOf(1f, 1f / aspectRatio).coerceAtMost(2.4f)
        for (y in 0 until height) for (x in 0 until width) {
            val px = (x.toFloat()/(width-1)-0.5f)*ax*1.35f/(radius*e[2])+xBias+3.7f
            val py = (y.toFloat()/(height-1)-0.5f)*ay*1.35f/(radius*e[2])+yBias+8.3f
            val wx = noise(px*0.72f+d[0]+2.1f,py*0.72f+d[1]+0.7f)-0.5f
            val wy = noise(px*0.68f+d[2]+8.4f,py*0.68f+d[3]+3.2f)-0.5f
            val qx = px + wx*1.1f
            val qy = py + wy*1.1f
            val tint = noise(qx*0.92f+d[2]+1.3f,qy*0.92f+d[3]+5.8f)
            val light = noise(qx*0.78f+d[0]+6.2f,qy*0.78f+d[1]+1.7f)
            val undertone = noise(qx*1.12f+e[0]+4.5f,qy*1.12f+e[1]+9.1f)
            val blend = smooth(0.16f,0.84f,tint)
            val accentWeight = (1f-tint)*undertone*0.42f
            val density = ((0.38f+0.62f*smooth(0.16f,0.86f,light*0.65f+undertone*0.35f))*energy*e[3]).coerceIn(0f,1f)
            val r = mix(base.red,mix(mix(mix(dominant.red,secondary.red,blend),muted.red,undertone*0.2f),accent.red,accentWeight),density)
            val g = mix(base.green,mix(mix(mix(dominant.green,secondary.green,blend),muted.green,undertone*0.2f),accent.green,accentWeight),density)
            val b = mix(base.blue,mix(mix(mix(dominant.blue,secondary.blue,blend),muted.blue,undertone*0.2f),accent.blue,accentWeight),density)
            vertexColors[y*width+x] = (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
        return vertexColors
    }

    private fun hash(x: Float, y: Float): Float {
        var a = fract(x*0.1031f)
        var b = fract(y*0.1030f)
        val dot = a*(b+33.33f)+b*(a+33.33f)
        a += dot; b += dot
        return fract((a+b)*a)
    }
    private fun noise(x: Float, y: Float): Float {
        val ix = floor(x); val iy = floor(y)
        val fx = fract(x); val fy = fract(y)
        val u = fx*fx*fx*(fx*(fx*6f-15f)+10f)
        val v = fy*fy*fy*(fy*(fy*6f-15f)+10f)
        return mix(mix(hash(ix,iy),hash(ix+1f,iy),u),mix(hash(ix,iy+1f),hash(ix+1f,iy+1f),u),v)
    }
    private fun fract(value: Float) = value-floor(value)
    private fun smooth(a: Float, b: Float, value: Float): Float {
        val t = ((value-a)/(b-a)).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
    private fun mix(a: Float,b: Float,t: Float) = a+(b-a)*t
    private fun channel(value: Float) = (value*255f+0.5f).toInt().coerceIn(0,255)
}

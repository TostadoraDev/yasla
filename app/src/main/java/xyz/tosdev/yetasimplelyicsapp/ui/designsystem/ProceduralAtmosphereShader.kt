package xyz.tosdev.yetasimplelyicsapp.ui.designsystem

/**
 * Analytic color field. No input shader, texture, image, blur or radial light primitive.
 * Low spatial frequencies and quintic noise interpolation make the light intrinsically diffuse.
 * Keep the CPU compatibility evaluator in ProceduralAtmosphereField equivalent to this source.
 */
internal const val PROCEDURAL_ATMOSPHERE_SHADER = """
uniform float2 resolution;
uniform float4 drift;
uniform float4 evolution;
uniform float4 variation;
uniform float3 dominant;
uniform float3 secondary;
uniform float3 muted;
uniform float3 accent;
uniform float3 base;

float hash21(float2 p) {
    p = fract(p * float2(0.1031, 0.1030));
    p += dot(p, p.yx + 33.33);
    return fract((p.x + p.y) * p.x);
}
float fieldNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f*f*f*(f*(f*6.0-15.0)+10.0);
    return mix(mix(hash21(i), hash21(i+float2(1,0)), u.x),
               mix(hash21(i+float2(0,1)), hash21(i+float2(1,1)), u.x), u.y);
}
half4 main(float2 coord) {
    float2 uv = coord / resolution;
    // Bounded aspect compensation keeps a broad atmosphere in both orientations.
    float2 aspect = resolution / min(resolution.x, resolution.y);
    aspect = min(aspect, float2(2.4));
    float2 p = (uv-0.5)*aspect*1.35 / (variation.z*evolution.z);
    p += variation.xy + float2(3.7, 8.3);
    float2 warp = float2(fieldNoise(p*0.72+drift.xy+float2(2.1,0.7)),
                         fieldNoise(p*0.68+drift.zw+float2(8.4,3.2)))-0.5;
    float2 q = p + warp*1.1;
    float tint = fieldNoise(q*0.92+drift.zw+float2(1.3,5.8));
    float light = fieldNoise(q*0.78+drift.xy+float2(6.2,1.7));
    float undertone = fieldNoise(q*1.12+evolution.xy+float2(4.5,9.1));
    float3 color = mix(dominant, secondary, smoothstep(0.16,0.84,tint));
    color = mix(color, muted, undertone*0.20);
    color = mix(color, accent, (1.0-tint)*undertone*0.42);
    // Light fills the viewport. No center/radius mask that could become a discrete blob.
    float density = (0.38+0.62*smoothstep(0.16,0.86,light*0.65+undertone*0.35));
    density = clamp(density*variation.w*evolution.w,0.0,1.0);
    color = mix(base,color,density);
    // Sub-byte, stable dither avoids large-area color banding, never animated grain.
    color += (hash21(coord)-0.5)/255.0;
    return half4(clamp(color,0.0,1.0),1.0);
}
"""

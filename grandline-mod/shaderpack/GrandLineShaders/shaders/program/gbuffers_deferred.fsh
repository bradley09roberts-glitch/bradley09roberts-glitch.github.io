// Opaque geometry, fragment: writes albedo, normal + lightmap and material data for the deferred lighting pass.
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"

uniform sampler2D gtexture;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 tint;
in vec3 normalW;
in float emissionHint;
flat in int matId;

/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormalLight;
layout(location = 2) out vec4 outMaterial;

float emissiveMask(int mat, vec3 c, float hint) {
    float m = maxOf(c);
    float l = luminance(c);
    if (mat == MAT_EMISSIVE) return 0.15 + 0.85 * smoothstep(0.25, 0.75, l);
    if (mat == MAT_LIGHT_SOURCE) return smoothstep(0.60, 0.88, m);
    if (mat == MAT_FIRE || mat == MAT_LAVA) return 0.6 + 0.4 * smoothstep(0.3, 0.8, l);
    if (mat == MAT_PORTAL) return 0.8;
    if (mat == MAT_GLOW_BODY) return 0.35 + 0.65 * smoothstep(0.3, 0.8, m); // paper lanterns: the whole shade glows
    // any other block that emits light (e.g. lanterns of the mod's building kit): only its bright pixels glow
    if (hint > 0.0) return smoothstep(0.65, 0.92, m) * hint;
    return 0.0;
}

void main() {
    vec4 albedo = texture(gtexture, texcoord);
#if PROG_ID == 10
    albedo.rgb *= tint.rgb;           // biome tint (separateAo: alpha carries vanilla ambient occlusion)
    float ao = tint.a;
#else
    albedo *= tint;
    float ao = 1.0;
#endif
    if (albedo.a < 0.1) discard;
#if PROG_ID == 11
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif

    vec3 N = normalize(normalW);
    if (matId == MAT_PLANT || matId == MAT_PLANT_LOWER || matId == MAT_PLANT_UPPER || matId == MAT_SEAGRASS || matId == MAT_KELP) {
        N = normalize(N * 0.25 + vec3(0.0, 1.0, 0.0)); // cross-shaped plants are lit like the ground they stand on
    }

    float emissive = emissiveMask(matId, albedo.rgb, emissionHint);

    outAlbedo = vec4(albedo.rgb, 1.0);
    outNormalLight = vec4(encodeNormal(N), lmcoord);
    outMaterial = vec4(float(matId) / 255.0, emissive, ao, 1.0);
}

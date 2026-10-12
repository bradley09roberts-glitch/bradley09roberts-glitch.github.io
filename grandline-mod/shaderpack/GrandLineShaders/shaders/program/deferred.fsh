/*
 * Deferred lighting: sun/moon with filtered shadows, sky and block light from the lightmap, material highlights,
 * emissive glow, and the procedural sky behind everything. Water and other translucents are added afterwards.
 */
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/waves.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"
#if HAS_SUN
    #define SHADOW_LOOKUP 1
    uniform sampler2DShadow shadowtex1;
    uniform sampler2D shadowtex0;
    uniform sampler2D shadowcolor0;
#endif
#include "/lib/shadows.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA16;
const int colortex2Format = RGBA8;
const int colortex3Format = RGBA16;
const int colortex4Format = RGBA16F;
const int colortex5Format = RGBA16F;
const int colortex6Format = RGBA8;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex3ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex4ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const vec4 colortex6ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const bool colortex5Clear = false;
*/

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;

in vec2 uv;
flat in vec3 sunDir;
flat in vec3 moonDir;
flat in vec3 lightDir;
flat in vec3 directCol;
flat in vec3 ambientCol;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

// water absorption per block (red goes first: turquoise shallows, blue depths)
vec3 waterAbsorption() { return vec3(0.40, 0.085, 0.060) / WATER_TRANSPARENCY; }

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float depth = texelFetch(depthtex0, px, 0).r;
    vec4 base = texelFetch(colortex0, px, 0);

    vec3 viewPos = screenToView(uv, depth);
    vec3 playerPos = viewToPlayer(viewPos);
    vec3 V = normalize(playerPos);

    if (depth >= 1.0) {
#if DIM == DIM_END
        outColor = vec4(base.rgb, 1.0);  // the End keeps its vanilla sky texture
#else
        vec3 camPos = anchoredWorldPos(vec3(0.0));
        outColor = vec4(fullSky(V, sunDir, moonDir, camPos, 4, true), 1.0);
#endif
        return;
    }

    vec4 md = texelFetch(colortex2, px, 0);
    if (md.w < 0.5) {
        outColor = vec4(base.rgb, 1.0); // already shaded by a forward program
        return;
    }

    vec3 albedo = toLinear(base.rgb);
    vec4 nl = texelFetch(colortex1, px, 0);
    vec3 N = decodeNormal(nl.xy);
    vec2 lm = nl.zw;
    int mat = int(md.x * 255.0 + 0.5);
    if (mat == MAT_CLOUD) albedo *= 0.78; // cloud blocks: keep some shading instead of flat white
    float emissive = md.y;
    float ao = md.z;

#ifdef HANDHELD_LIGHT
    float held = float(max(heldBlockLightValue, heldBlockLightValue2));
    lm.x = max(lm.x, clamp((held - length(playerPos)) / 15.0, 0.0, 1.0) * 0.95);
#endif

    float rough, f0, metal, sss;
    materialParams(mat, rough, f0, metal, sss);

    // rain: open, upward-facing surfaces get darker and glossier
    float wet = wetness * smoothstep(0.86, 0.97, lm.y) * smoothstep(0.4, 0.9, N.y);
    albedo *= 1.0 - 0.28 * wet;
    rough = mix(rough, 0.12, wet * 0.8);
    f0 = max(f0, 0.02 + 0.03 * wet);

    float NdotL = dot(N, lightDir);
    float diffuse = max(NdotL, 0.0);
    if (sss > 0.0) diffuse = mix(diffuse, clamp(NdotL * 0.5 + 0.5, 0.0, 1.0), sss * 0.65);

    vec3 shadow = vec3(1.0);
    vec3 waterTint = vec3(1.0);
#if HAS_SUN
    float skyGate = smoothstep(0.0, 0.15, lm.y);
    if (mat == MAT_HAND) {
        shadow = vec3(smoothstep(0.3, 0.9, lm.y));
    } else if (maxOf(directCol) > 0.0) {
        float waterDepth, fade;
        vec3 sh = shadowLight(playerPos, N, lightDir, gl_FragCoord.xy, SHADOW_SAMPLES, waterDepth, fade);
        shadow = mix(vec3(smoothstep(0.3, 0.9, lm.y)), sh, fade) * skyGate;
        if (waterDepth > 0.0) {
            // sunlight that crossed water: absorbed on the way, focused into moving caustics
            waterTint = exp(-waterAbsorption() * waterDepth);
    #ifdef CAUSTICS
            vec3 wp = anchoredWorldPos(playerPos);
            float c = causticPattern(wp.xz - lightDir.xz / max(lightDir.y, 0.2) * waterDepth);
            waterTint *= mix(1.0, 0.35 + c, smoothstep(0.0, 1.5, waterDepth));
    #endif
        }
    }
#endif

    // stylisation: gentle light banding and a crisper shadow terminator
    diffuse = mix(diffuse, softBands(diffuse, 3.0), STYLISATION);
    shadow = mix(shadow, smoothstep(vec3(0.2), vec3(0.8), shadow), STYLISATION);

    vec3 direct = directCol * diffuse * shadow * waterTint;
    if (sss > 0.0) {
        // light through thin leaves when looking toward the sun
        direct += directCol * shadow * waterTint * pow(clamp(dot(V, lightDir), 0.0, 1.0), 6.0) * sss * 0.6;
    }

    vec3 skyAmb = ambientCol * skyLightCurve(lm.y) * (0.72 + 0.28 * N.y);
    vec3 blockAmb = blockLightColor() * blockLightCurve(lm.x);
    float aoTerm = mix(1.0, ao, 0.85);
    vec3 lighting = direct + (skyAmb + blockAmb + minimumLight()) * aoTerm;

    vec3 color = albedo * lighting * (1.0 - metal * 0.55);

    // highlights: the sun on glossy materials, and the sky in smooth or wet ones
    float NdotV = clamp(dot(N, -V), 0.0, 1.0);
    vec3 specTint = mix(vec3(1.0), albedo * 1.6, metal);
    float F = fresnelSchlick(f0, NdotV);
    vec3 F3 = mix(vec3(F), specTint * (0.4 + 0.6 * F), metal);
    vec3 spec = directCol * shadow * waterTint * max(NdotL, 0.0) * specularLobe(N, -V, lightDir, rough) * F3;
    color += spec * 0.35 * SPECULAR_STRENGTH;
    if (rough < 0.66) {
        vec3 R = reflect(V, N);
        vec3 env = skyGradient(normalize(vec3(R.x, max(R.y, 0.03), R.z)), sunDir) * skyLightCurve(lm.y);
        color += env * F3 * sq(1.0 - rough) * 0.8 * SPECULAR_STRENGTH * aoTerm;
    }

    color += albedo * emissive * 6.0 * EMISSIVE_STRENGTH;

    outColor = vec4(color, 1.0);
}

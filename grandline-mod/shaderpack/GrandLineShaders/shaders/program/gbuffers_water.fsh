/*
 * Translucent terrain, fragment.
 * Water writes nothing visible here: it stores its wave normal, sky light and biome tint (colortex3 / colortex6) and
 * the composite pass shades it with the opaque scene behind it (refraction, depth colour, foam, reflections).
 * Glass, ice, slime and the Fish-Man Island bubble membrane are shaded forward with light, shadows and reflections.
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

uniform sampler2D gtexture;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 tint;
in vec3 normalW;
in vec3 playerPos;
in vec3 anchoredPos;
flat in int matId;

/* RENDERTARGETS: 0,3,6 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outWater;
layout(location = 2) out vec4 outWaterTint;

void main() {
    vec3 Ng = normalize(normalW);

    if (matId == MAT_WATER) {
        float dist = length(playerPos);
        float fade = smoothstep(24.0, 180.0, dist);
        vec3 N;
        if (Ng.y > 0.5) {
            N = waterNormal(anchoredPos, fade);
        } else if (Ng.y < -0.5) {
            N = waterNormal(anchoredPos, fade) * vec3(1.0, -1.0, 1.0); // underside of the surface, seen from below
        } else {
            // side faces (waterfalls, the walls of an air dome): a little ripple only
            float r = rippleNoise(anchoredPos.xz + anchoredPos.yy * 0.7) - 0.5;
            N = normalize(Ng + vec3(r, r * 0.5, -r) * 0.08 * (1.0 - fade));
        }
        outColor = vec4(0.0);                                   // alpha 0: the scene behind stays as it is
        outWater = vec4(encodeNormal(N), lmcoord.y, 1.0);
        outWaterTint = vec4(tint.rgb, lmcoord.x);
        return;
    }

    vec4 tex = texture(gtexture, texcoord) * tint;
    if (tex.a < 0.02) discard;
    outWater = vec4(0.0);      // something translucent in front of any water: no water shading here
    outWaterTint = vec4(0.0);

    vec3 albedo = toLinear(tex.rgb);
    vec3 V = normalize(playerPos);
    vec3 N = Ng;
    if (dot(N, V) > 0.0) N = -N; // two-sided panes
    float NdotV = clamp(dot(N, -V), 0.0, 1.0);
    vec3 sunDir = worldSunDir();
    vec3 L = worldLightDir();
    vec2 lm = lmcoord;
#if defined(HANDHELD_LIGHT) && PROG_ID == 50
    float held = float(max(heldBlockLightValue, heldBlockLightValue2));
    lm.x = max(lm.x, clamp((held - length(playerPos)) / 15.0, 0.0, 1.0));
#endif

    vec3 direct = directLightColor(sunDir);
    vec3 shadow = vec3(1.0);
#if HAS_SUN
    float wDepth, sFade;
    vec3 sh = shadowLight(playerPos, N, L, gl_FragCoord.xy, 4, wDepth, sFade);
    shadow = mix(vec3(smoothstep(0.1, 0.6, lm.y)), sh, sFade);
#endif
    float NdotL = max(dot(N, L), 0.0);
    vec3 light = ambientLightColor(sunDir) * skyLightCurve(lm.y)
               + blockLightColor() * blockLightCurve(lm.x)
               + minimumLight()
               + direct * NdotL * shadow;

    float F = fresnelSchlick(0.04, NdotV);
    vec3 R = reflect(V, N);
    vec3 env = skyGradient(normalize(vec3(R.x, max(R.y, 0.02), R.z)), sunDir) * skyLightCurve(lm.y);
    vec3 spec = direct * shadow * specularLobe(N, -V, L, 0.08) * 0.04 * SPECULAR_STRENGTH;

    float a = tex.a;
    vec3 body = albedo * light;
    vec3 refl = env * F + spec;

    if (matId == MAT_BUBBLE) {
        // soap-bubble membrane: mostly clear, a pale blue body, thin-film colours toward the rim
        float hue = NdotV * 1.6 + anchoredPos.y * 0.05 + frameTimeCounter * 0.03;
        vec3 film = 0.5 + 0.5 * cos(TAU * (hue + vec3(0.0, 0.33, 0.67)));
        a = 0.10 + 0.45 * pow(1.0 - NdotV, 2.0);
        body = mix(vec3(0.55, 0.80, 1.0), film, 0.35) * (light * 0.6 + 0.15);
        refl = env * mix(0.06, 1.0, pow(1.0 - NdotV, 4.0)) + spec * 2.0;
    } else if (matId == MAT_PORTAL) {
        body = albedo * 3.0 * EMISSIVE_STRENGTH + albedo * light;
    } else if (matId == MAT_ICE || matId == MAT_SLIME) {
        a = max(a, 0.5);
    }

    float aOut = clamp(a + F * (1.0 - a) * 0.6, 0.0, 1.0);
    vec3 rgb = (body * a + refl) / max(aOut, 0.02);
    outColor = vec4(rgb, aOut);
}

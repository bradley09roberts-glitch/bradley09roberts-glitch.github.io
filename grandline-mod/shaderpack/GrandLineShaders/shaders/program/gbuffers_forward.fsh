// Forward-shaded geometry, fragment (see gbuffers_forward.vsh for the program list).
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"

uniform sampler2D gtexture;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 tint;
in vec3 normalW;
in vec3 playerPos;

/* RENDERTARGETS: 0,2 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outMaterial;

// cheap lighting without shadows for particles, text, leashes and similar small things
vec3 simpleLight(vec2 lm, vec3 N) {
    vec3 sunDir = worldSunDir();
    vec3 L = worldLightDir();
    float skyGate = smoothstep(0.05, 0.5, lm.y);
    vec3 light = ambientLightColor(sunDir) * skyLightCurve(lm.y)
               + blockLightColor() * blockLightCurve(lm.x)
               + minimumLight()
               + directLightColor(sunDir) * (0.35 + 0.35 * max(dot(N, L), 0.0)) * skyGate;
    return light;
}

void main() {
    outMaterial = vec4(0.0, 0.0, 0.0, 1.0); // "already shaded": the deferred pass leaves these pixels alone
    vec4 c = vec4(0.0);

#if PROG_ID == 20
    // basic: lines, leashes, debug shapes (no texture)
    c = tint;
    if (c.a < 0.01) discard;
    c.rgb = toLinear(c.rgb) * (simpleLight(lmcoord, vec3(0.0, 1.0, 0.0)) * 0.6 + 0.2);
#elif PROG_ID == 23
    // weather: soft, translucent rain and snow lit by the sky around them
    c = texture(gtexture, texcoord) * tint;
    if (c.a < 0.02) discard;
    vec3 sunDir = worldSunDir();
    vec3 amb = ambientLightColor(sunDir) * skyLightCurve(lmcoord.y) + directLightColor(sunDir) * 0.25 + minimumLight();
    // keep the streaks light and soft: mostly white, a hint of the texture's blue
    c.rgb = mix(vec3(luminance(toLinear(c.rgb))), toLinear(c.rgb), 0.35) * amb * 1.3;
    c.a *= 0.32;
#elif PROG_ID == 24 || PROG_ID == 25 || PROG_ID == 27
    // beacon beam, glowing eyes, lightning: emissive
    #if PROG_ID == 27
        c = tint;
    #else
        c = texture(gtexture, texcoord) * tint;
    #endif
    if (c.a < 0.01) discard;
    c.rgb = toLinear(c.rgb) * 4.0 * EMISSIVE_STRENGTH;
#elif PROG_ID == 26
    // enchantment glint (additive)
    c = texture(gtexture, texcoord) * tint;
    c.rgb = toLinear(c.rgb) * 1.4;
#elif PROG_ID == 30
    // vanilla clouds, restyled: soft sunlit shading, translucent, fading with distance
    #if CLOUD_MODE == 1
        c = texture(gtexture, texcoord) * tint;
        if (c.a < 0.01) discard;
        vec3 sunDir = worldSunDir();
        float shadeTerm = clamp(luminance(tint.rgb), 0.0, 1.0);
        vec3 lit = directLightColor(sunDir) * 0.55 + ambientLightColor(sunDir) * 1.3;
        c.rgb = toLinear(c.rgb) * mix(lit * 0.6, lit, shadeTerm);
        float dist = length(playerPos.xz);
        c.a *= 0.8 * (1.0 - smoothstep(far * 2.0, far * 4.0, dist));
    #else
        discard;
    #endif
#elif PROG_ID == 40 || PROG_ID == 41
    // vanilla sky geometry: replaced by the procedural sky in the deferred pass, except in the End
    #if DIM == DIM_END
        #if PROG_ID == 41
            c = texture(gtexture, texcoord) * tint;
        #else
            c = tint;
        #endif
        c.rgb = toLinear(c.rgb);
    #else
        discard;
    #endif
#else
    // textured / textured_lit: particles, text and other small lit things
    c = texture(gtexture, texcoord) * tint;
    if (c.a < 0.1) discard;
    c.rgb = toLinear(c.rgb) * simpleLight(lmcoord, normalize(normalW));
#endif

    outColor = c;
}

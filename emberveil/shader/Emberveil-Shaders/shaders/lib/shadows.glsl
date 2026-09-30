// Shadow-map distortion and filtering (original, Emberveil Shaders).
// Distortion follows the widely used "radial distortion" idea: more texels near the player.
#ifndef EMBERVEIL_SHADOWS
#define EMBERVEIL_SHADOWS

const float SHADOW_DISTORT = 0.85;
float shadowDistortFactor(vec2 p) { return mix(1.0, length(p), SHADOW_DISTORT); }
vec3 distortShadow(vec3 p) { return vec3(p.xy / shadowDistortFactor(p.xy), p.z * 0.2); }

#ifdef SHADOW_SAMPLING
uniform sampler2DShadow shadowtex1;
#ifdef COLORED_SHADOWS
uniform sampler2DShadow shadowtex0;
uniform sampler2D shadowcolor0;
#endif

const vec2 POISSON[24] = vec2[](
    vec2(-0.613, 0.617), vec2(0.170, -0.040), vec2(-0.299, 0.791), vec2(0.645, 0.493),
    vec2(-0.651, 0.718), vec2(0.421, 0.027), vec2(-0.817, -0.271), vec2(-0.705, -0.668),
    vec2(0.977, -0.108), vec2(0.063, -0.843), vec2(0.203, 0.500), vec2(-0.321, -0.332),
    vec2(0.450, -0.700), vec2(-0.180, 0.180), vec2(0.790, 0.220), vec2(-0.950, 0.100),
    vec2(0.320, 0.910), vec2(-0.460, -0.880), vec2(0.600, -0.300), vec2(-0.050, 0.520),
    vec2(0.880, 0.560), vec2(-0.560, 0.250), vec2(0.140, -0.450), vec2(-0.250, -0.650));

// Returns sun visibility (rgb for coloured shadows) for a view-space position.
vec3 sampleShadow(vec3 viewPos, vec3 normalV, float NdotL) {
    vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos + gbufferModelViewInverse[3].xyz;
    float dist = length(playerPos);
    float fade = smoothstep(shadowDistance * 0.82, shadowDistance * 0.98, dist);
    if (fade >= 1.0) return vec3(1.0);

    // Normal-offset bias, scaled with distance and shadow resolution.
    vec3 normalP = mat3(gbufferModelViewInverse) * normalV;
    float texelWorld = (2.0 * shadowDistance) / float(shadowMapResolution);
    playerPos += normalP * texelWorld * (1.2 + 3.0 * (1.0 - sat(NdotL))) * (0.4 + dist / shadowDistance);

    vec3 sp = (shadowProjection * vec4((shadowModelView * vec4(playerPos, 1.0)).xyz, 1.0)).xyz;
    float df = shadowDistortFactor(sp.xy);
    sp = distortShadow(sp) * 0.5 + 0.5;
    sp.z -= 0.00015;
    if (any(lessThan(sp, vec3(0.0))) || any(greaterThan(sp, vec3(1.0)))) return vec3(1.0);

    float angle = ign(gl_FragCoord.xy + float(frameCounter % 8) * 7.0) * 2.0 * PI;
    mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    float texel = 1.0 / float(shadowMapResolution);
#if SHADOW_FILTER == 0
    const int TAPS = 4; float radius = 0.8;
#elif SHADOW_FILTER == 1
    const int TAPS = 12; float radius = 1.4;
#else
    const int TAPS = 24; float radius = 2.4;
#endif
    radius *= SHADOW_SOFTNESS * texel;
    float vis = 0.0;
    for (int i = 0; i < TAPS; i++) {
        vec2 o = rot * POISSON[i] * radius;
        vis += texture(shadowtex1, vec3(sp.xy + o, sp.z));
    }
    vis /= float(TAPS);
    vec3 result = vec3(vis);
#ifdef COLORED_SHADOWS
    // Where translucent casters (stained glass) sit in front, tint the light by their colour.
    float solid = texture(shadowtex0, sp);
    if (solid < vis) {
        vec4 tint = texture(shadowcolor0, sp.xy);
        result = mix(vec3(vis), tint.rgb * vis, (vis - solid));
    }
#endif
    return mix(result, vec3(1.0), fade);
}
#endif
#endif

/*
 * Shadow map distortion and filtering.
 * shadowtex1 (opaque casters only) is sampled with hardware comparison and a rotated golden-angle PCF kernel;
 * shadowtex0 + shadowcolor0 tell whether light reaches a point through water (absorption + caustics) or tinted glass.
 * Requires: settings, uniforms, common. The caller declares: sampler2DShadow shadowtex1, sampler2D shadowtex0,
 * sampler2D shadowcolor0 (and includes waves.glsl for caustics).
 */

float shadowDistortFactor(vec2 p) {
    return length(p) * SHADOW_DISTORTION + (1.0 - SHADOW_DISTORTION);
}

vec3 distortShadow(vec3 clipPos) {
    return vec3(clipPos.xy / shadowDistortFactor(clipPos.xy), clipPos.z);
}

#if defined(SHADOW_LOOKUP)
// world size of one shadow texel around a point with distortion factor f
float shadowTexelWorld(float f) {
    return (2.0 * shadowDistance / float(shadowMapResolution)) * f * f / (1.0 - SHADOW_DISTORTION);
}

/*
 * Light visibility (rgb tint) at a camera-relative position. 'waterDepth' returns how many blocks of water the light
 * crossed (0 when none). 'fade' returns 1 inside the shadow map, 0 beyond it.
 */
vec3 shadowLight(vec3 playerPos, vec3 normalW, vec3 lightDir, vec2 fragCoord, int samples, out float waterDepth, out float fade) {
    waterDepth = 0.0;
    float NdotL = clamp(dot(normalW, lightDir), 0.0, 1.0);
    vec3 shadowView = (shadowModelView * vec4(playerPos, 1.0)).xyz;
    vec3 clip0 = (shadowProjection * vec4(shadowView, 1.0)).xyz;
    float f = shadowDistortFactor(clip0.xy);
    float texelW = shadowTexelWorld(f);

    // normal offset (more at grazing angles) keeps acne away without peter-panning at contact points
    vec3 offsetPos = playerPos + normalW * (0.015 + texelW * (1.2 + 1.8 * (1.0 - NdotL)));
    shadowView = (shadowModelView * vec4(offsetPos, 1.0)).xyz;
    vec3 clip = (shadowProjection * vec4(shadowView, 1.0)).xyz;
    vec3 coord = distortShadow(clip) * 0.5 + 0.5;

    float edge = max(abs(clip.x), abs(clip.y));
    fade = 1.0 - smoothstep(0.85, 0.98, edge);
    if (fade <= 0.0 || coord.z >= 1.0) { fade = 0.0; return vec3(1.0); }

    float depthPerBlock = abs(shadowProjection[2][2]) * 0.5;
    coord.z -= texelW * 0.35 * depthPerBlock;

    float rot = ign(fragCoord) * TAU;
    // ~0.07 block penumbra in world space, converted to shadow-map uv at this distortion, kept between 0.6 and 4 texels
    float radius = 0.07 * SHADOW_SOFTNESS * 0.5 / shadowDistance * (1.0 - SHADOW_DISTORTION) / (f * f);
    radius = clamp(radius, 0.6 / float(shadowMapResolution), 4.0 * SHADOW_SOFTNESS / float(shadowMapResolution));
    float vis = 0.0;
    for (int i = 0; i < samples; i++) {
        float r = sqrt((float(i) + 0.5) / float(samples));
        float a = float(i) * 2.39996323 + rot;
        vec2 o = vec2(cos(a), sin(a)) * r * radius;
        vis += texture(shadowtex1, vec3(coord.xy + o, coord.z));
    }
    vis /= float(samples);
    vec3 light = vec3(vis);

    // translucent casters between the point and the light
    if (vis > 0.0) {
        float d0 = texture(shadowtex0, coord.xy).r;
        if (d0 < coord.z - 1e-5) {
            vec4 sc = texture(shadowcolor0, coord.xy);
            if (sc.a > 0.95) {
                waterDepth = (coord.z - d0) / depthPerBlock;
            } else {
                // stained glass and the like: tint by its colour and opacity
                light *= mix(vec3(1.0), sc.rgb, clamp(sc.a / 0.9, 0.0, 1.0));
            }
        }
    }
    return light;
}
#endif

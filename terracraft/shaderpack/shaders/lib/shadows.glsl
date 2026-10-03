// Soft, coloured shadows. shadowtex0 holds every caster, shadowtex1 only opaque ones and shadowcolor0 the colour of
// translucent casters (stained glass, water), so light through glass is tinted.

uniform sampler2D shadowtex0;
uniform sampler2D shadowtex1;
uniform sampler2D shadowcolor0;

vec3 shadowCoords(vec3 playerPos) {
    vec4 clip = shadowProjection * (shadowModelView * vec4(playerPos, 1.0));
    return distortShadow(clip.xyz) * 0.5 + 0.5;
}

/** Visibility of the sun/moon (rgb tinted by translucent casters) for a surface in player space. */
vec3 sampleShadow(vec3 playerPos, vec3 worldNormal, float NdotL) {
    float dist = length(playerPos);
    if (dist > shadowDistance) {
        return vec3(1.0);
    }
    // normal offset grows with distance and grazing angles (fights acne without peter-panning up close)
    vec3 offsetPos = playerPos + worldNormal * (0.03 + dist * 0.0025) * (1.6 - NdotL);
    vec4 clip = shadowProjection * (shadowModelView * vec4(offsetPos, 1.0));
    float warp = length(clip.xy) * 0.9 + 0.1;
    vec3 s = distortShadow(clip.xyz) * 0.5 + 0.5;
    if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0 || s.z >= 1.0) {
        return vec3(1.0);
    }
    // penumbra of ~0.08 blocks (x softness) in world space; 1 block = 0.5 / shadowDistance in uv before warping
    float radius = SHADOW_SOFTNESS * 0.08 * (0.5 / shadowDistance) / warp;
    radius = max(radius, 0.75 / float(shadowMapResolution));
    float angle = ign(gl_FragCoord.xy) * 2.0 * PI;
    mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    float bias = 0.00004;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < SHADOW_SAMPLES; i++) {
        // Vogel disk
        float r = sqrt((float(i) + 0.5) / float(SHADOW_SAMPLES));
        float theta = float(i) * 2.39996323;
        vec2 uv = s.xy + rot * vec2(cos(theta), sin(theta)) * r * radius;
        float lit0 = step(s.z - bias, texture(shadowtex0, uv).r);
        float lit1 = step(s.z - bias, texture(shadowtex1, uv).r);
        vec3 tint = texture(shadowcolor0, uv).rgb;
        sum += mix(tint * lit1, vec3(1.0), lit0);
    }
    vec3 vis = sum / float(SHADOW_SAMPLES);
    // fade out at the edge of the shadow distance
    return mix(vis, vec3(1.0), smoothstep(shadowDistance * 0.85, shadowDistance, dist));
}

/** One hard sample of opaque casters only (used by volumetric light). */
float shadowOpaque(vec3 playerPos) {
    vec3 s = shadowCoords(playerPos);
    if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0) {
        return 1.0;
    }
    return step(s.z - 0.0001, texture(shadowtex1, s.xy).r);
}

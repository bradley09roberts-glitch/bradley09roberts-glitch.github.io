/*
 * Light colours per dimension and time of day, lightmap curves and material parameters.
 * Requires: settings, uniforms, common, sky.
 */

vec3 worldSunDir() { return normalize(mat3(gbufferModelViewInverse) * sunPosition); }
vec3 worldMoonDir() { return normalize(mat3(gbufferModelViewInverse) * moonPosition); }
vec3 worldLightDir() { return normalize(mat3(gbufferModelViewInverse) * shadowLightPosition); }

// Direct light from the shadow-casting body (sun by day, moon by night); fades to zero at the swap point
vec3 directLightColor(vec3 sunDir) {
#if HAS_SUN
    float sunUp = smoothstep(0.0, 0.18, sunDir.y);
    float moonUp = smoothstep(0.0, 0.18, -sunDir.y);
    float tw = twilightFactor(sunDir);
    #if DIM == DIM_SKY
        vec3 sunCol = vec3(1.0, 0.95, 0.86) * 3.0;
    #else
        vec3 sunCol = vec3(1.0, 0.92, 0.80) * 2.7;
    #endif
    sunCol = mix(sunCol, vec3(1.0, 0.52, 0.24) * 2.1, tw * 0.85);
    vec3 moonCol = vec3(0.42, 0.53, 0.85) * 0.24;
    vec3 c = sunCol * sunUp + moonCol * moonUp;
    return c * (1.0 - rainStrength * 0.82) * SUN_STRENGTH;
#else
    return vec3(0.0);
#endif
}

// Sky (ambient) light at full sky exposure; gently cooler than the sun
vec3 ambientLightColor(vec3 sunDir) {
#if HAS_SUN
    float day = dayFactor(sunDir);
    float tw = twilightFactor(sunDir);
    #if DIM == DIM_SKY
        vec3 dayAmb = vec3(0.50, 0.68, 1.05) * 0.95;
    #else
        vec3 dayAmb = vec3(0.45, 0.62, 1.00) * 0.85;
    #endif
    vec3 nightAmb = vec3(0.10, 0.14, 0.30) * 0.16;
    vec3 c = mix(nightAmb, dayAmb, day);
    c = mix(c, vec3(0.62, 0.46, 0.52) * 0.55, tw * 0.6);
    float grey = luminance(c);
    c = mix(c, vec3(grey) * vec3(0.88, 0.93, 1.0) * 0.85, rainStrength * 0.7);
    return c * AMBIENT_STRENGTH;
#elif DIM == DIM_DEEP
    return vec3(0.06, 0.17, 0.30) * 0.55 * AMBIENT_STRENGTH;
#elif DIM == DIM_INTERIOR
    return vec3(0.34, 0.29, 0.24) * 0.32 * AMBIENT_STRENGTH;
#elif DIM == DIM_NETHER
    return toLinear(fogColor) * 0.9 + vec3(0.30, 0.16, 0.10) * 0.25;
#else
    return vec3(0.30, 0.22, 0.42) * 0.35 * AMBIENT_STRENGTH;
#endif
}

vec3 blockLightColor() {
#if DIM == DIM_DEEP
    return vec3(1.0, 0.70, 0.38) * 1.45 * BLOCKLIGHT_STRENGTH; // lanterns read warm against the blue
#else
    return vec3(1.0, 0.62, 0.32) * 1.15 * BLOCKLIGHT_STRENGTH;
#endif
}

// Lightmap curves: softer than vanilla so rooms with a window or a lantern stay readable
float skyLightCurve(float s) {
#if HAS_SUN
    return s * s * 0.72 + s * 0.28;
#else
    return 1.0; // no sky light in these dimensions: the ambient colour above already is the room/sea light
#endif
}

float blockLightCurve(float b) {
    return b * b * 0.9 + pow(b, 5.0) * 1.4;
}

vec3 minimumLight() {
    return vec3(0.050, 0.055, 0.065) * MIN_LIGHT + vec3(0.30) * nightVision;
}

// roughness, F0, metalness, subsurface (thin foliage / cloud)
void materialParams(int mat, out float rough, out float f0, out float metal, out float sss) {
    rough = 0.85; f0 = 0.03; metal = 0.0; sss = 0.0;
    if (mat == MAT_METAL) { rough = 0.35; f0 = 0.6; metal = 1.0; }
    else if (mat == MAT_POLISHED || mat == MAT_PONEGLYPH) { rough = 0.38; f0 = 0.045; }
    else if (mat == MAT_SEASTONE) { rough = 0.5; f0 = 0.04; }
    else if (mat == MAT_GLASS || mat == MAT_STAINED_GLASS || mat == MAT_ICE) { rough = 0.08; f0 = 0.04; }
    else if (mat == MAT_TILE) { rough = 0.52; f0 = 0.04; }
    else if (mat == MAT_WOOD) { rough = 0.62; f0 = 0.035; }
    else if (mat == MAT_PLASTER) { rough = 0.80; f0 = 0.03; }
    else if (mat == MAT_CLOTH || mat == MAT_CLOTH_SWAY) { rough = 0.95; f0 = 0.02; }
    else if (mat == MAT_LEAVES || mat == MAT_PLANT || mat == MAT_PLANT_LOWER || mat == MAT_PLANT_UPPER || mat == MAT_VINE) { rough = 0.55; f0 = 0.03; sss = 0.6; }
    else if (mat == MAT_SEAGRASS || mat == MAT_KELP) { rough = 0.5; f0 = 0.03; sss = 0.5; }
    else if (mat == MAT_SNOW) { rough = 0.6; f0 = 0.03; sss = 0.25; }
    else if (mat == MAT_CLOUD) { rough = 0.9; f0 = 0.02; sss = 0.8; }
    else if (mat == MAT_SAND) { rough = 0.9; f0 = 0.03; }
}

float fresnelSchlick(float f0, float cosTheta) {
    return f0 + (1.0 - f0) * pow(1.0 - clamp(cosTheta, 0.0, 1.0), 5.0);
}

// Normalised Blinn-Phong highlight for a given roughness (0 = mirror, 1 = matte)
float specularLobe(vec3 N, vec3 V, vec3 L, float rough) {
    vec3 H = normalize(L + V);
    float NdotH = max(dot(N, H), 0.0);
    float r2 = max(rough * rough, 0.02);
    float n = 2.0 / (r2 * r2) - 2.0;
    return (n + 8.0) / (8.0 * PI) * pow(NdotH, n);
}

// Surface lighting: sun/moon + sky ambient + ember-toned block light + cave floor light.
#ifndef EMBERVEIL_LIGHTING
#define EMBERVEIL_LIGHTING

// Torch / lantern light: warm "ember" curve. Quadratic-ish falloff keeps light pools readable.
vec3 blockLightColor(float bl) {
    vec3 warm = mix(vec3(1.0, 0.82, 0.62), vec3(1.0, 0.60, 0.30), TORCH_WARMTH * 0.8);
    float c = pow(bl, 2.6) * 2.2 + pow(bl, 8.0) * 0.8;
    return warm * c * BLOCKLIGHT_STRENGTH;
}

// Light that is always present, so caves stay navigable. Follows the Brightness slider.
vec3 minimumLight() {
    return vec3(0.030, 0.034, 0.042) * (0.35 + 1.3 * screenBrightness) * MIN_LIGHT;
}

float handLight(vec3 viewPos) {
#ifdef HANDHELD_LIGHT
    float held = float(max(heldBlockLightValue, heldBlockLightValue2)) / 15.0;
    if (held <= 0.0) return 0.0;
    float d = length(viewPos);
    return held * sat(1.0 - d / (held * 15.0));
#else
    return 0.0;
#endif
}

#if defined DIM_NETHER
vec3 dimensionAmbient() { return mix(vec3(0.30, 0.16, 0.10), toLinear(fogColor) * 1.6, 0.5) * 0.55; }
#elif defined DIM_END
vec3 dimensionAmbient() { return vec3(0.22, 0.17, 0.30) * 0.75; }
#endif

// albedo is linear. shadowVis is the (possibly tinted) sun visibility. subsurface is 0..1 (foliage).
vec3 shadeSurface(vec3 albedo, vec3 normalV, vec3 viewPos, vec2 lm, vec3 shadowVis, float subsurface, float emission) {
    float bl = max(lm.x, handLight(viewPos));
    vec3 light = blockLightColor(bl) + minimumLight();
#if defined DIM_NETHER || defined DIM_END
    float up = dot(normalV, normalize(upPosition));
    light += dimensionAmbient() * (0.75 + 0.25 * up);
#else
    vec3 L = normalize(shadowLightPosition);
    float NdotL = dot(normalV, L);
    float diffuse = max(NdotL, 0.0);
    // Foliage lets some light through: wrap lighting, so backlit leaves are not flat black.
    diffuse = mix(diffuse, 0.35 + 0.65 * abs(NdotL), subsurface * 0.8);
    float skyMask = smoothstep(0.05, 0.45, lm.y);
    light += directLightColor() * diffuse * shadowVis * skyMask;
    float up = dot(normalV, normalize(upPosition));
    light += ambientSkyColor() * (0.62 + 0.38 * up) * pow(lm.y, 2.2);
#endif
    light += vec3(0.45) * nightVision;
    light *= 1.0 - 0.75 * darknessLightFactor;
    return albedo * light + albedo * emission;
}
#endif

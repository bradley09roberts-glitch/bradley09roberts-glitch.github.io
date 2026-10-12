/*
 * Exposure, tone mapping and colour grading.
 * The curve is applied to the brightest channel and the colour is scaled with it, so saturated roofs, painted walls
 * and blue water keep their hue instead of clipping; only genuine highlights are eased toward white.
 * Requires: settings, uniforms, common, sky (dayFactor/nightFactor), lighting (worldSunDir).
 */

float sceneExposure() {
    float e = EXPOSURE * 0.88;
#if HAS_SUN
    vec3 sunDir = worldSunDir();
    float night = nightFactor(sunDir);
    // under a roof or in a cave: open the exposure so interiors stay readable
    float outdoor = smoothstep(0.15, 0.85, float(eyeBrightnessSmooth.y) / 240.0);
    e *= mix(1.4, 1.0, outdoor);
    // night opens the exposure outdoors only: a lantern-lit room at night should still read as night-time indoors
    e *= mix(1.0, 1.9, night * outdoor);
    #if DIM == DIM_SKY
        e *= 0.85;
    #endif
#elif DIM == DIM_DEEP
    e *= 1.5;
#elif DIM == DIM_INTERIOR
    e *= 1.35;
#elif DIM == DIM_NETHER
    e *= 1.45;
#else
    e *= 1.2;
#endif
    return e;
}

float filmicCurve(float x) {
    // linear toe, smooth exponential shoulder that never clips
    return 1.0 - exp(-x * 1.08);
}

vec3 tonemap(vec3 c) {
    float m = maxOf(c);
    if (m <= 0.0) return vec3(0.0);
    float mt = filmicCurve(m);
    vec3 huePreserving = c * (mt / m);
    vec3 perChannel = vec3(filmicCurve(c.r), filmicCurve(c.g), filmicCurve(c.b));
    // ease only the brightest values toward white (sun, emissive cores), keep the rest hue-true
    float w = smoothstep(0.75, 1.0, mt) * 0.6;
    return mix(huePreserving, perChannel, w);
}

vec3 colourGrade(vec3 c) {
    // vibrance: lift weak saturation more than strong (keeps already-vivid paint from going neon)
    float l = luminance(c);
    float sat = (maxOf(c) - min(c.r, min(c.g, c.b))) / max(maxOf(c), 1e-4);
    float boost = (SATURATION - 1.0) * (1.4 - sat);
    c = max(mix(vec3(l), c, 1.0 + boost), 0.0);
    // gentle S-curve contrast around mid grey
    c = clamp(c, 0.0, 1.0);
    vec3 s = c * c * (3.0 - 2.0 * c);
    c = mix(c, s, (CONTRAST - 1.0) * 1.5);
    // split tone: warm highlights, a hint of cool in the shadows
    float lum = luminance(c);
    c *= mix(vec3(0.97, 0.99, 1.04), vec3(1.03, 1.0, 0.96), smoothstep(0.15, 0.8, lum));
    return clamp(c, 0.0, 1.0);
}

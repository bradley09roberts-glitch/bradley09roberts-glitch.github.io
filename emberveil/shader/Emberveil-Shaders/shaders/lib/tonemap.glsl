// Tone mapping and grading (original). Extended Reinhard keeps a linear toe so dark
// caves and nights do not crush to black, and compresses highlights smoothly.
#ifndef EMBERVEIL_TONEMAP
#define EMBERVEIL_TONEMAP

vec3 tonemapEmber(vec3 x) {
    x *= 1.6 * EXPOSURE;
    const float W = 7.0;
    return x * (1.0 + x / (W * W)) / (1.0 + x);
}

vec3 gradeEmber(vec3 c) {
    float l = luma(c);
    // Split-tone: cool, slightly teal shadows; warm amber highlights.
    vec3 shadowTint = vec3(0.96, 1.00, 1.05);
    vec3 highTint = vec3(1.05, 1.00, 0.93);
    vec3 tint = mix(shadowTint, highTint, smoothstep(0.1, 0.7, l));
    c *= mix(vec3(1.0), tint, GRADE_STRENGTH);
    c = mix(vec3(luma(c)), c, SATURATION);
    return max(c, 0.0);
}

vec3 applyContrast(vec3 g) {
    // Contrast in display space around mid-grey, gentle so shadows keep detail.
    return clamp((g - 0.45) * CONTRAST + 0.45 + (CONTRAST - 1.0) * 0.02, 0.0, 1.0);
}
#endif

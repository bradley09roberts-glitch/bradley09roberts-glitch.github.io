/*
 * Water waves in world space.
 * Swell + chop: directional wave trains whose wave vectors are whole multiples of 2*pi/4096 on both axes, so they tile
 * exactly with anchoredWorldPos(). Ripples: two drifting octaves of tileable value noise (normals only).
 * The same height function drives the vertex displacement in gbuffers_water and the shadow pass.
 */

const float WAVE_TILE = 4096.0;

// (nx, nz, amplitude, phase); wavelength = 4096 / length(n)
const vec4 WAVES[7] = vec4[7](
    vec4(58.0, 31.0, 0.110, 0.0),     // swell, ~62 blocks
    vec4(-44.0, 67.0, 0.075, 1.7),    // swell, ~51 blocks
    vec4(83.0, -36.0, 0.050, 4.1),    // swell, ~45 blocks
    vec4(230.0, 140.0, 0.024, 2.3),   // chop, ~15 blocks
    vec4(-175.0, 290.0, 0.017, 5.2),  // chop, ~12 blocks
    vec4(372.0, -205.0, 0.011, 0.9),  // chop, ~10 blocks
    vec4(-410.0, -333.0, 0.008, 3.3)  // chop, ~8 blocks
);

float waveTime() { return frameTimeCounter * WAVE_SPEED; }

// How "ocean-like" a water surface at height y is: full waves at sea level, gentle ones for pools, ponds and canals
float seaFactor(float worldY) {
#if DIM == DIM_OVERWORLD
    return mix(0.12, 1.0, 1.0 - smoothstep(0.75, 2.5, abs(worldY - SEA_SURFACE_Y)));
#else
    return 0.12;
#endif
}

float stormFactor() { return 1.0 + rainStrength * 0.6; }

// Height (blocks) and its x/z gradient for the swell + chop trains
vec3 waveHeightGrad(vec2 xz, float amp) {
    float t = waveTime();
    float h = 0.0;
    vec2 g = vec2(0.0);
    for (int i = 0; i < 7; i++) {
        vec2 k = WAVES[i].xy * (TAU / WAVE_TILE);
        float kl = length(k);
        // deep-water dispersion, slowed down for a calm, readable sea
        float w = sqrt(9.8 * kl) * 0.38;
        float th = dot(k, xz) - w * t + WAVES[i].w;
        float s = 0.5 + 0.5 * sin(th);
        float sh = pow(s, 1.6);                   // sharper crests, flatter troughs
        h += WAVES[i].z * (2.0 * sh - 1.0);
        float dsh = 1.6 * pow(max(s, 1e-4), 0.6) * cos(th); // d(2*sh-1)/dth = 2*1.6*s^0.6*0.5*cos
        g += WAVES[i].z * dsh * k;
    }
    return vec3(h, g) * amp;
}

float waveHeight(vec2 xz, float amp) { return waveHeightGrad(xz, amp).x; }

// Vertical displacement used by both gbuffers_water and the shadow pass (identical inputs give identical results)
float waterDisplacement(vec3 anchoredPos) {
#ifdef WATER_DISPLACEMENT
    float amp = WAVE_STRENGTH * seaFactor(anchoredPos.y) * stormFactor();
    return waveHeight(anchoredPos.xz, amp) * 0.85;
#else
    return 0.0;
#endif
}

float rippleNoise(vec2 p) {
    float t = waveTime();
    // periods: 4096 blocks * scale (0.625 and 1.25 cells per block) = whole cells
    float a = vnoise(p * 0.625 + vec2(t * 0.31, t * 0.17), 2560.0);
    float b = vnoise(p * 1.25 + vec2(-t * 0.23, t * 0.41), 5120.0);
    return a * 0.65 + b * 0.35;
}

// World-space normal of the water surface. 'distanceFade' tames detail far away to avoid shimmering.
vec3 waterNormal(vec3 anchoredPos, float distanceFade) {
    float sea = seaFactor(anchoredPos.y);
    float amp = WAVE_STRENGTH * sea * stormFactor();
    vec3 hg = waveHeightGrad(anchoredPos.xz, amp);
    vec2 grad = hg.yz * mix(1.0, 0.35, distanceFade);

    // ripples: finite differences of the noise, present on every water surface (pools included)
    float e = 0.18;
    float r0 = rippleNoise(anchoredPos.xz);
    float rx = rippleNoise(anchoredPos.xz + vec2(e, 0.0));
    float rz = rippleNoise(anchoredPos.xz + vec2(0.0, e));
    float rippleAmp = 0.055 * WAVE_STRENGTH * (0.6 + 0.4 * sea) * (1.0 + rainStrength * 1.2) * (1.0 - distanceFade * 0.85);
    grad += vec2(rx - r0, rz - r0) / e * rippleAmp;

    return normalize(vec3(-grad.x, 1.0, -grad.y));
}

// Animated caustic pattern (sharp bright lines) for sunlit terrain under water
float causticPattern(vec2 xz) {
    float t = waveTime();
    // scales 0.5 and 0.75 cells per block -> 2048 and 3072 cells per 4096-block tile
    float a = vnoise(xz * 0.5 + vec2(t * 0.25, t * 0.11), 2048.0);
    float b = vnoise(xz * 0.75 + vec2(-t * 0.19, t * 0.27) + 7.0, 3072.0);
    float ra = 1.0 - abs(a * 2.0 - 1.0);
    float rb = 1.0 - abs(b * 2.0 - 1.0);
    return pow(ra * rb, 3.0) * 3.2;
}

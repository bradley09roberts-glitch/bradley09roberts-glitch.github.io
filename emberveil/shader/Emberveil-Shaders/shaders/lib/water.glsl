// Procedural water surface (original): sum of directional waves + low-frequency noise modulation.
#ifndef EMBERVEIL_WATER
#define EMBERVEIL_WATER

float waterHeight(vec2 p, float t) {
    float h = 0.0;
    h += sin(dot(p, vec2(0.62, 0.78)) * 1.05 + t * 1.25) * 0.50;
    h += sin(dot(p, vec2(-0.83, 0.55)) * 1.70 + t * 1.65) * 0.30;
    h += sin(dot(p, vec2(0.21, -0.97)) * 2.90 + t * 2.05) * 0.16;
    h += sin(dot(p, vec2(-0.50, -0.86)) * 4.70 + t * 2.85) * 0.08;
    h += sin(dot(p, vec2(0.95, 0.30)) * 7.30 + t * 3.60) * 0.04;
    return h;
}

// World-space (y-up) normal of the wave field at horizontal position p.
vec3 waterNormal(vec2 p, float t, float strength) {
    const float e = 0.06;
    float h0 = waterHeight(p, t);
    float hx = waterHeight(p + vec2(e, 0.0), t);
    float hz = waterHeight(p + vec2(0.0, e), t);
    float k = 0.11 * strength * WAVE_STRENGTH;
    return normalize(vec3(-(hx - h0) / e * k, 1.0, -(hz - h0) / e * k));
}

float fresnelSchlick(float cosTheta, float f0) {
    return f0 + (1.0 - f0) * pow(1.0 - sat(cosTheta), 5.0);
}
#endif

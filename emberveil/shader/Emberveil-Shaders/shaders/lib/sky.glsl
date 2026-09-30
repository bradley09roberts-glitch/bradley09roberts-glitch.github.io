// Sky, celestial light colours and time-of-day factors (original, Emberveil Shaders).
#ifndef EMBERVEIL_SKY
#define EMBERVEIL_SKY

float sunElevation() { return dot(normalize(sunPosition), normalize(upPosition)); }

// 1 at full day, 0 at night; smooth through dawn and dusk.
float dayFactor(float e) { return smoothstep(-0.12, 0.18, e); }
// Peaks around sunrise/sunset ("golden hour").
float duskFactor(float e) { return exp(-pow2((e - 0.04) * 6.5)); }

vec3 sunLightColor(float e) {
    vec3 golden = vec3(1.00, 0.50, 0.22);
    vec3 noon   = vec3(1.00, 0.93, 0.84);
    float t = smoothstep(0.02, 0.45, e);
    return mix(golden, noon, t) * mix(0.75, 2.6, smoothstep(0.0, 0.35, e)) * SUN_BRIGHTNESS;
}

vec3 moonLightColor() {
    float phase = 1.0 - abs(float(moonPhase) - 4.0) / 4.0; // 1 at full moon, 0 at new moon
    return vec3(0.55, 0.68, 1.00) * (0.07 + 0.05 * phase) * NIGHT_BRIGHTNESS;
}

// Colour of the direct (shadow-casting) light: sun by day, moon by night, faded near the swap.
vec3 directLightColor() {
    float e = sunElevation();
    vec3 c = mix(moonLightColor(), sunLightColor(e), step(0.0, e));
    c *= smoothstep(0.0, 0.07, abs(e));
    c *= 1.0 - 0.88 * rainStrength;
    return c;
}

vec3 ambientSkyColor() {
    float e = sunElevation();
    vec3 dayA   = vec3(0.42, 0.55, 0.80) * 0.60;
    vec3 duskA  = vec3(0.62, 0.44, 0.40) * 0.34;
    vec3 nightA = vec3(0.30, 0.40, 0.72) * 0.07 * NIGHT_BRIGHTNESS;
    vec3 a = mix(nightA, dayA, dayFactor(e));
    a = mix(a, duskA, duskFactor(e) * 0.55);
    vec3 gray = vec3(luma(a)) * vec3(0.95, 1.0, 1.05);
    return mix(a, gray * 0.85, rainStrength * 0.7);
}

// Sky radiance in a view-space direction. Used for the sky dome, fog colour and water reflections.
vec3 skyRadiance(vec3 dirV) {
    vec3 up = normalize(upPosition);
    vec3 sunV = normalize(sunPosition);
    float e = dot(sunV, up);
    float h = dot(dirV, up);
    float sunDot = dot(dirV, sunV);
    float day = dayFactor(e), dusk = duskFactor(e);

    vec3 zenithDay = vec3(0.16, 0.32, 0.72) * 1.25;
    vec3 horizonDay = vec3(0.56, 0.68, 0.86) * 1.35;
    vec3 zenithDusk = vec3(0.16, 0.18, 0.34);
    vec3 horizonDusk = vec3(0.95, 0.46, 0.24);
    vec3 zenithNight = vec3(0.006, 0.010, 0.026) * NIGHT_BRIGHTNESS;
    vec3 horizonNight = vec3(0.018, 0.026, 0.050) * NIGHT_BRIGHTNESS;

    // Blend the biome's own sky tint in lightly so deserts/swamps still differ.
    vec3 biome = toLinear(skyColor);
    zenithDay = mix(zenithDay, biome * 1.3, 0.3);

    vec3 zenith = mix(zenithNight, zenithDay, day);
    vec3 horizon = mix(horizonNight, horizonDay, day);
    zenith = mix(zenith, zenithDusk, dusk * 0.6);
    // Dusk colour concentrates on the sun's side of the horizon.
    float sunSide = 0.35 + 0.65 * pow(max(sunDot * 0.5 + 0.5, 0.0), 2.0);
    horizon = mix(horizon, horizonDusk, dusk * sunSide);

    float hh = max(h, 0.0);
    vec3 col = mix(horizon, zenith, pow(hh, 0.55));
    // Below the horizon fade towards a darker ground-bounce tone.
    col = mix(col, horizon * 0.35, smoothstep(0.0, -0.35, h));

    // Forward scattering glow around the sun, strongest at golden hour.
    float glow = pow(max(sunDot, 0.0), 10.0) * (0.25 + 1.4 * dusk) + pow(max(sunDot, 0.0), 90.0) * 1.2;
    col += sunLightColor(e) * glow * day * (1.0 - rainStrength);
    // Faint moon halo.
    float moonDot = dot(dirV, normalize(moonPosition));
    col += vec3(0.4, 0.5, 0.8) * pow(max(moonDot, 0.0), 40.0) * 0.08 * (1.0 - day);

    vec3 overcast = vec3(luma(col)) * vec3(0.92, 0.96, 1.02) * 0.8;
    col = mix(col, overcast, rainStrength * 0.8);
    return col;
}
#endif

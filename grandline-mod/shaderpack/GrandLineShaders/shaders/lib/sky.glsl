/*
 * Sky, sun, moon, stars, painted clouds and the per-dimension backdrops. Not a physical scattering model: an artistic
 * gradient tuned for a bright, saturated "animated adventure" sky that tone-maps without clipping.
 * Requires: settings, uniforms, common.
 */

// time-of-day factors from the sun direction (world space)
float dayFactor(vec3 sunDir) { return smoothstep(-0.10, 0.25, sunDir.y); }
float twilightFactor(vec3 sunDir) { return exp(-sq((sunDir.y - 0.02) / 0.17)); }
float nightFactor(vec3 sunDir) { return 1.0 - smoothstep(-0.25, -0.02, sunDir.y); }

// Sky gradient without sun disc, moon, stars or clouds (also used as the haze colour)
vec3 skyGradient(vec3 dir, vec3 sunDir) {
#if DIM == DIM_NETHER
    return toLinear(fogColor) * 0.9;
#elif DIM == DIM_END
    return vec3(0.035, 0.025, 0.055);
#elif DIM == DIM_DEEP
    // deep sea: dark ocean above, near-black below, a little teal where the light comes from
    float up = smoothstep(-0.6, 1.0, dir.y);
    return mix(vec3(0.002, 0.008, 0.020), vec3(0.018, 0.085, 0.140), up);
#elif DIM == DIM_INTERIOR
    return vec3(0.016, 0.012, 0.010);
#else
    float day = dayFactor(sunDir);
    float tw = twilightFactor(sunDir);
    float night = nightFactor(sunDir);

    #if DIM == DIM_SKY
        // high altitude: deeper zenith, whiter and brighter horizon
        vec3 zenithDay = vec3(0.09, 0.30, 1.30);
        vec3 horizonDay = vec3(1.05, 1.40, 1.85);
    #else
        vec3 zenithDay = vec3(0.13, 0.38, 1.25);
        vec3 horizonDay = vec3(0.85, 1.25, 1.75);
    #endif
    vec3 zenithNight = vec3(0.006, 0.010, 0.030);
    vec3 horizonNight = vec3(0.020, 0.032, 0.066);

    vec3 zenith = mix(zenithNight, zenithDay, day);
    vec3 horizon = mix(horizonNight, horizonDay, day);

    // twilight: warm on the sun side, lilac on the other
    vec2 dh = normalize(dir.xz + vec2(1e-5));
    vec2 sh = normalize(sunDir.xz + vec2(1e-5));
    float sunSide = pow(0.5 + 0.5 * dot(dh, sh), 2.0);
    vec3 twHorizon = mix(vec3(0.55, 0.38, 0.62), vec3(2.3, 0.95, 0.32), sunSide);
    zenith = mix(zenith, vec3(0.16, 0.20, 0.52), tw * 0.75);
    horizon = mix(horizon, twHorizon, tw * 0.85);

    float y = dir.y;
    float hw = pow(1.0 - clamp(y, 0.0, 1.0), 4.0);
    vec3 col = mix(zenith, horizon, hw);
    if (y < 0.0) {
        // below the horizon: a slightly deeper, bluer band (in an ocean world this is the far sea)
        col = mix(horizon, horizon * vec3(0.55, 0.70, 0.95), smoothstep(0.0, -0.35, y));
    }

    // broad sun glow, warm at sunrise and sunset
    float g = max(dot(dir, sunDir), 0.0);
    vec3 glowCol = mix(vec3(1.0, 0.90, 0.75), vec3(1.7, 0.75, 0.30), tw);
    col += glowCol * (pow(g, 6.0) * 0.35 + pow(g, 48.0) * 0.8) * smoothstep(-0.18, 0.05, sunDir.y);

    // overcast
    float grey = luminance(col);
    col = mix(col, vec3(grey) * vec3(0.82, 0.88, 0.98) * mix(0.55, 0.32, thunderStrength), rainStrength * 0.85);
    return col;
#endif
}

// Distance-haze colour. Above the horizon: the low part of the sky in that direction; below it: exactly what the sky
// shows there, so terrain and sea fading out at the edge of the loaded world melt into the backdrop without a seam.
vec3 hazeColor(vec3 dir, vec3 sunDir) {
    vec3 d = normalize(vec3(dir.x, dir.y > 0.0 ? dir.y * 0.4 : dir.y, dir.z));
    vec3 c = skyGradient(d, sunDir);
#if DIM == DIM_SKY
    // below the horizon the sky dimension shows its sunlit cloud floor
    float day = dayFactor(sunDir);
    vec3 floorCol = mix(vec3(0.05, 0.06, 0.10), mix(vec3(1.0, 0.98, 0.94), vec3(1.7, 0.9, 0.5), twilightFactor(sunDir)) * 1.7, day);
    c = mix(c, floorCol, smoothstep(0.0, -0.12, dir.y) * 0.8);
#endif
    return c;
}

// --------------------------------------------------------------------------- painted clouds
float cloudFbm(vec2 uv, int octaves) {
    float n = 0.0, a = 0.55, s = 1.0, period = 16.0;
    for (int i = 0; i < 4; i++) {
        if (i >= octaves) break;
        n += a * vnoise(uv * s + float(i) * 7.0, period * s);
        a *= 0.5;
        s *= 2.0;
    }
    return n / (octaves >= 4 ? 1.0 : (octaves == 3 ? 0.96 : 0.82));
}

// rgb premultiplied colour, a coverage; camPos is anchoredWorldPos(vec3(0)) (the camera)
vec4 paintedClouds(vec3 dir, vec3 camPos, vec3 sunDir, int octaves) {
#if CLOUD_MODE == 2 && HAS_SUN
    #if DIM == DIM_SKY
        float cloudY = 330.0;
    #else
        float cloudY = 235.0;
    #endif
    float hgt = cloudY - camPos.y;
    if (dir.y < 0.01 || hgt <= 0.0) return vec4(0.0);
    float t = hgt / dir.y;
    vec2 p = camPos.xz + dir.xz * t + vec2(1.0, 0.35) * frameTimeCounter * 2.2;
    vec2 uv = p / 256.0; // 16 cells per 4096 blocks
    float n = cloudFbm(uv, octaves);
    float cov = CLOUD_COVERAGE + rainStrength * 0.35;
    float density = smoothstep(1.0 - cov - 0.05, 1.0 - cov + 0.30, n);
    if (density <= 0.001) return vec4(0.0);

    // self shadowing: look a little toward the sun
    float n2 = cloudFbm(uv + sunDir.xz * 0.08, min(octaves, 3));
    float occl = smoothstep(1.0 - cov, 1.0 - cov + 0.45, n2);

    float day = dayFactor(sunDir);
    float tw = twilightFactor(sunDir);
    vec3 sunTint = mix(vec3(1.0, 0.97, 0.92), vec3(1.8, 0.85, 0.45), tw);
    vec3 lit = mix(vec3(0.05, 0.06, 0.10), sunTint * 1.9, day);
    vec3 shade = skyGradient(vec3(0.0, 1.0, 0.0), sunDir) * 0.9 + lit * 0.18;
    vec3 col = mix(lit, shade, occl * 0.75);
    float g = max(dot(dir, sunDir), 0.0);
    col += sunTint * pow(g, 10.0) * (1.0 - density) * 1.5 * day; // silver lining
    col = mix(col, vec3(luminance(col)) * 0.7, rainStrength * 0.6);

    float fade = smoothstep(0.01, 0.16, dir.y) * exp(-t / 9000.0);
    float a = density * fade * 0.92;
    return vec4(col * a, a);
#else
    return vec4(0.0);
#endif
}

#if DIM == DIM_SKY
// The white sea of the sky dimension: a sunlit cloud floor below the floating islands
vec4 cloudSea(vec3 dir, vec3 camPos, vec3 sunDir) {
    float seaY = 58.0;
    float hgt = camPos.y - seaY;
    if (dir.y > -0.004 || hgt <= 0.0) return vec4(0.0);
    float t = hgt / -dir.y;
    vec2 p = camPos.xz + dir.xz * t + vec2(0.6, 0.2) * frameTimeCounter * 1.5;
    float n = cloudFbm(p / 128.0, 4);
    float n2 = cloudFbm(p / 128.0 + sunDir.xz * 0.05, 3);
    float density = smoothstep(0.22, 0.55, n);
    float day = dayFactor(sunDir);
    float tw = twilightFactor(sunDir);
    vec3 sunTint = mix(vec3(1.0, 0.98, 0.94), vec3(1.7, 0.9, 0.5), tw);
    vec3 lit = mix(vec3(0.05, 0.06, 0.10), sunTint * 2.0, day);
    vec3 col = mix(lit, lit * vec3(0.62, 0.70, 0.90), smoothstep(0.3, 0.8, n2) * 0.7);
    float fade = exp(-t / 6000.0);
    float a = density * fade;
    return vec4(col * a, a);
}
#endif

// --------------------------------------------------------------------------- sun, moon, stars
vec3 sunDisc(vec3 dir, vec3 sunDir) {
    float d = dot(dir, sunDir);
    float disc = smoothstep(0.99955, 0.99972, d);
    float tw = twilightFactor(sunDir);
    vec3 col = mix(vec3(42.0, 36.0, 28.0), vec3(40.0, 20.0, 8.0), tw);
    return col * disc * (1.0 - rainStrength * 0.95) * smoothstep(-0.03, 0.01, dir.y);
}

vec3 moonDisc(vec3 dir, vec3 moonDir) {
    float d = dot(dir, moonDir);
    const float radius = 0.030; // ~1.7 degrees
    if (d < 0.99) return vec3(0.0);
    vec3 u = normalize(cross(moonDir, vec3(0.0, 0.0, 1.0)) + vec3(1e-4, 0.0, 0.0));
    vec3 v = cross(u, moonDir);
    vec3 off = dir - moonDir * d;
    vec2 p = vec2(dot(off, u), dot(off, v)) / radius;
    float r = length(p);
    float disc = 1.0 - smoothstep(0.92, 1.0, r);
    vec3 col = vec3(0.0);
    if (disc > 0.0) {
        float ph = float(moonPhase) * (TAU / 8.0);
        float s = moonPhase < 4 ? 1.0 : -1.0;
        float limb = sqrt(max(1.0 - p.y * p.y, 0.0));
        float lit = smoothstep(-0.06, 0.06, s * p.x + cos(ph) * limb);
        float craters = 0.82 + 0.18 * vnoise(p * 3.0 + 11.0, 4096.0);
        col = mix(vec3(0.02, 0.025, 0.04), vec3(2.6, 2.8, 3.1) * craters, lit) * disc;
    }
    col += vec3(0.25, 0.30, 0.45) * pow(max(d, 0.0), 900.0) * 0.6; // soft halo
    return col * (1.0 - rainStrength * 0.9) * smoothstep(-0.03, 0.01, dir.y);
}

vec3 starField(vec3 dir, float night) {
#ifdef STARS
    if (night <= 0.0 || dir.y <= 0.0) return vec3(0.0);
    vec3 p = dir * 170.0;
    vec3 cell = floor(p);
    float h = hash12(vec2(cell.x + cell.z * 397.0, cell.y + 1000.0));
    if (h < 0.9965) return vec3(0.0);
    vec3 c = cell + 0.5 + (vec3(hash12(cell.xy + 31.0), hash12(cell.yz + 57.0), hash12(cell.zx + 83.0)) - 0.5) * 0.6;
    float d = length(p - c);
    float star = smoothstep(0.16, 0.0, d) * (h - 0.9965) * 280.0;
    float twinkle = 0.7 + 0.3 * sin(frameTimeCounter * (2.0 + h * 9.0) + h * 100.0);
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.9, 0.75), hash12(cell.xz + 5.0));
    return tint * star * twinkle * night * smoothstep(0.0, 0.25, dir.y) * (1.0 - rainStrength);
#else
    return vec3(0.0);
#endif
}

// Full sky for a direction: gradient, sun, moon, stars and clouds (octaves = cloud detail, 0 = no clouds)
vec3 fullSky(vec3 dir, vec3 sunDir, vec3 moonDir, vec3 camPos, int cloudOctaves, bool discs) {
    vec3 col = skyGradient(dir, sunDir);
#if HAS_SUN
    float night = nightFactor(sunDir);
    if (discs) col += starField(dir, night) + sunDisc(dir, sunDir) + moonDisc(dir, moonDir);
    if (cloudOctaves > 0) {
        vec4 c = paintedClouds(dir, camPos, sunDir, cloudOctaves);
        col = col * (1.0 - c.a) + c.rgb;
    }
    #if DIM == DIM_SKY
        vec4 sea = cloudSea(dir, camPos, sunDir);
        vec3 haze = hazeColor(dir, sunDir);
        float hazeMix = smoothstep(-0.20, -0.004, dir.y) * 0.7; // the far cloud floor melts into the horizon
        col = col * (1.0 - sea.a) + mix(sea.rgb, haze * sea.a, hazeMix);
    #endif
#elif DIM == DIM_DEEP
    // faint wavering light from far above
    float shaft = vnoise(vec2(dir.x * 6.0 + frameTimeCounter * 0.05, dir.z * 6.0), 4096.0);
    col += vec3(0.01, 0.05, 0.07) * smoothstep(0.5, 1.0, dir.y) * shaft;
#endif
    return col;
}

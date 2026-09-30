// Atmospheric fog, valley mist, underwater/lava murk and far-edge blending (original).
#ifndef EMBERVEIL_FOG
#define EMBERVEIL_FOG

vec3 applyFog(vec3 color, vec3 viewPos, bool isSky) {
    vec3 dirV = normalize(viewPos);
    float dist = length(viewPos);
    vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos;
    float worldY = playerPos.y + cameraPosition.y;

    // Status effects override everything: blindness and the Warden's darkness.
    float effect = max(blindness, darknessFactor * 0.8);
    if (effect > 0.0) {
        float f = 1.0 - exp(-dist * (0.45 * blindness + 0.16 * darknessFactor));
        return mix(color, vec3(0.0), f * effect);
    }

    if (isEyeInWater == 1) {
        float eyeSky = float(eyeBrightnessSmooth.y) / 240.0;
        vec3 waterFog = vec3(0.020, 0.095, 0.120) * (0.15 + 0.85 * eyeSky) * (0.4 + 0.6 * dayFactor(sunElevation()));
        waterFog += vec3(0.05, 0.03, 0.01) * float(heldBlockLightValue) / 15.0;
        vec3 absorb = vec3(0.30, 0.075, 0.055) / UNDERWATER_CLARITY;
        color *= exp(-absorb * min(dist, 64.0) * 0.35);
        float f = 1.0 - exp(-dist * 0.045 / UNDERWATER_CLARITY);
        return mix(color, waterFog, f);
    }
    if (isEyeInWater == 2) return mix(color, vec3(1.2, 0.35, 0.05), 1.0 - exp(-dist * 0.9));
    if (isEyeInWater == 3) return mix(color, vec3(0.75, 0.82, 0.90), 1.0 - exp(-dist * 0.7));

#if defined DIM_NETHER
    vec3 fc = toLinear(fogColor) * 1.3;
    float f = 1.0 - exp(-dist * 0.010 * FOG_DENSITY);
    f = max(f, smoothstep(far * 0.55, far * 0.95, dist));
    return mix(color, fc, isSky ? 0.0 : f);
#elif defined DIM_END
    vec3 fc = vec3(0.10, 0.07, 0.14);
    float f = 1.0 - exp(-dist * 0.004 * FOG_DENSITY);
    f = max(f, smoothstep(far * 0.7, far, dist));
    return mix(color, fc, isSky ? 0.0 : f);
#else
    if (isSky) {
#ifdef VALLEY_MIST
        // A hint of mist hugging the horizon at dawn, dusk and in rain.
        float e = sunElevation();
        float mistTime = duskFactor(e) * 0.6 + rainStrength * 0.8;
        float h = dot(dirV, normalize(upPosition));
        return mix(color, skyRadiance(normalize(dirV + normalize(upPosition) * 0.05)) * 0.9,
                   mistTime * MIST_STRENGTH * 0.3 * exp(-max(h, 0.0) * 12.0));
#else
        return color;
#endif
    }
    float e = sunElevation();
    vec3 fogCol = skyRadiance(normalize(dirV * vec3(1.0) + normalize(upPosition) * 0.02));
    // Aerial perspective: distant land takes on the sky colour.
    float density = 0.0010 * FOG_DENSITY * (1.0 + 3.0 * rainStrength);
    float f = 1.0 - exp(-dist * density);
#ifdef VALLEY_MIST
    // The veil: dense, low-lying mist in valleys at dawn/dusk and in rain; thin at midday.
    float mistTime = 0.10 + duskFactor(e) * 0.85 + rainStrength * 1.2 + (1.0 - dayFactor(e)) * 0.30;
    float heightFalloff = exp(-max(worldY - 58.0, 0.0) * 0.045);
    float eyeFalloff = exp(-max(eyeAltitude - 58.0, 0.0) * 0.012);
    float mist = 1.0 - exp(-dist * 0.008 * heightFalloff * mistTime * MIST_STRENGTH * mix(0.6, 1.0, eyeFalloff));
    vec3 mistCol = mix(fogCol, vec3(luma(fogCol)) * vec3(1.02, 1.0, 0.98), 0.25) * 0.92;
    color = mix(color, mistCol, sat(mist) * 0.85);
#endif
    // Blend to sky at the render-distance edge so chunk borders never show.
    float edge = smoothstep(far * 0.72, far * 0.98, dist);
    f = max(f, edge);
    return mix(color, fogCol, sat(f));
#endif
}
#endif

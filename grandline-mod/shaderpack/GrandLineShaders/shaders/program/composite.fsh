/*
 * Composite: water shading (above and below the surface), distance haze, underwater/lava/snow fog and the bloom
 * bright-pass. Reads the lit opaque scene + forward translucents in colortex0 and the water data from gbuffers_water.
 */
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/waves.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"
#include "/lib/tonemap.glsl"
#if HAS_SUN
    #define SHADOW_LOOKUP 1
    uniform sampler2DShadow shadowtex1;
    uniform sampler2D shadowtex0;
    uniform sampler2D shadowcolor0;
#endif
#include "/lib/shadows.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex3;
uniform sampler2D colortex6;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;

in vec2 uv;
flat in vec3 sunDir;
flat in vec3 moonDir;
flat in vec3 lightDir;
flat in vec3 directCol;
flat in vec3 ambientCol;

/* RENDERTARGETS: 0,4 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outBloom;

vec3 waterAbsorption() { return vec3(0.40, 0.085, 0.060) / WATER_TRANSPARENCY; }

bool insideScreen(vec2 p) { return all(greaterThan(p, vec2(0.002))) && all(lessThan(p, vec2(0.998))); }

// Light available inside the water body near the surface
vec3 waterBodyLight(float skyLM, float blockLM, float sunVis) {
    vec3 l = ambientCol * skyLightCurve(skyLM) * 0.9
           + directCol * sunVis * clamp(lightDir.y, 0.0, 1.0) * 0.30
           + blockLightColor() * blockLightCurve(blockLM) * 0.6
           + minimumLight();
    return l;
}

vec3 underwaterFogColor() {
#if DIM == DIM_DEEP
    vec3 base = vec3(0.006, 0.040, 0.075);
#else
    vec3 base = vec3(0.020, 0.150, 0.210);
#endif
    float sky = float(eyeBrightnessSmooth.y) / 240.0;
    vec3 light = ambientCol * skyLightCurve(sky) + directCol * 0.12 * sky + minimumLight() * 2.0
               + blockLightColor() * blockLightCurve(float(eyeBrightnessSmooth.x) / 240.0) * 0.3;
    vec3 biome = toLinear(fogColor);
    return mix(base, base * 0.5 + biome * 0.35, 0.35) * light;
}

#if REFLECTION_QUALITY == 2
// Screen-space reflection, only where the ray stays on screen and hits something that really is above the water
vec4 screenSpaceReflection(vec3 viewPos, vec3 R, float surfaceY, float jitter) {
    float stepLen = 0.35 + 0.025 * length(viewPos);
    vec3 p = viewPos + R * stepLen * jitter;
    for (int i = 0; i < SSR_STEPS; i++) {
        p += R * stepLen;
        stepLen *= 1.16;
        vec3 sp = projectAndDivide(gbufferProjection, p) * 0.5 + 0.5;
        if (!insideScreen(sp.xy) || sp.z >= 1.0) break;
        float d = texture(depthtex1, sp.xy).r;
        if (d >= 1.0) continue;
        vec3 scene = screenToView(sp.xy, d);
        float diff = scene.z - p.z;
        if (diff > 0.0 && diff < stepLen * 2.5 + 0.3) {
            // refine
            vec3 a = p - R * stepLen, b = p;
            for (int j = 0; j < 5; j++) {
                vec3 m = (a + b) * 0.5;
                vec3 ms = projectAndDivide(gbufferProjection, m) * 0.5 + 0.5;
                float md = texture(depthtex1, ms.xy).r;
                if (screenToView(ms.xy, md).z > m.z) b = m; else a = m;
            }
            vec3 hs = projectAndDivide(gbufferProjection, b) * 0.5 + 0.5;
            float hd = texture(depthtex1, hs.xy).r;
            vec3 hitPlayer = viewToPlayer(screenToView(hs.xy, hd));
            if (hitPlayer.y < surfaceY - 0.6) break;              // that is underwater: not a valid reflection
            vec2 e = min(hs.xy, 1.0 - hs.xy);
            float fade = smoothstep(0.0, 0.07, min(e.x, e.y)) * (1.0 - float(i) / float(SSR_STEPS));
            return vec4(texture(colortex0, hs.xy).rgb, fade);
        }
    }
    return vec4(0.0);
}
#endif

float shoreFoam(float vertThick, float dist, vec3 wp, float sea) {
    float t = waveTime();
    float edge = 1.0 - smoothstep(0.0, 0.55, vertThick);
    if (edge <= 0.0) return 0.0;
    float n = vnoise(wp.xz * 1.5 + vec2(t * 0.40, -t * 0.30), 6144.0) * 0.6
            + vnoise(wp.xz * 3.0 + vec2(-t * 0.50, t * 0.20), 12288.0) * 0.4;
    float pulse = 0.5 + 0.5 * sin(t * 1.3 - vertThick * 7.0 + n * 3.0);
    float foam = smoothstep(0.30, 0.70, edge * (0.55 + 0.45 * pulse) + n * 0.35 - 0.15) * edge;
    foam *= 1.0 - smoothstep(28.0, 96.0, dist);
    return foam * (0.45 + 0.55 * sea);
}

vec3 shadeWaterFromAbove(vec3 sceneColor, vec3 N, float skyLM, vec4 tintData, float depth0, vec3 viewPos0, vec3 playerPos0, vec3 V, float dist0) {
    vec3 wp = anchoredWorldPos(playerPos0);
    float sea = seaFactor(wp.y);
    float blockLM = tintData.a;

    // refraction: offset along the view-space normal, never into pixels that are not behind this water
    vec3 Nv = mat3(gbufferModelView) * N;
    float d1 = texture(depthtex1, uv).r;
    vec3 bed0 = viewToPlayer(screenToView(uv, d1));
    float thick0 = d1 >= 1.0 ? 32.0 : length(bed0 - playerPos0);
    vec2 offset = Nv.xy * 0.045 * WATER_REFRACTION * clamp(thick0 / 3.0, 0.0, 1.0) / (1.0 + dist0 * 0.03);
    vec2 ruv = uv + offset;
    if (!insideScreen(ruv) || texture(colortex3, ruv).w < 0.5 || texture(depthtex1, ruv).r <= depth0) ruv = uv;
    float d1r = texture(depthtex1, ruv).r;
    vec3 behind = texture(colortex0, ruv).rgb;
    bool open = d1r >= 1.0;
    vec3 bed = viewToPlayer(screenToView(ruv, d1r));
    float viewThick = open ? 48.0 : length(bed - playerPos0);
    float vertThick = open ? 48.0 : max(playerPos0.y - bed.y, 0.0);

    float sunVis = 1.0;
#if HAS_SUN
    float wd, sf;
    vec3 sv = shadowLight(playerPos0, vec3(0.0, 1.0, 0.0), lightDir, gl_FragCoord.xy, 2, wd, sf);
    sunVis = mix(smoothstep(0.5, 0.95, skyLM), sv.r, sf);
#endif
    vec3 bodyLight = waterBodyLight(skyLM, blockLM, sunVis);
    // keep the water's own hue under tinted (sunset, overcast) light: half of the light colour is neutralised
    bodyLight = mix(bodyLight, vec3(luminance(bodyLight)), 0.5);

    // depth colour: readable turquoise shallows -> rich offshore blue
    vec3 T = exp(-waterAbsorption() * viewThick);
    vec3 shallowCol = vec3(0.10, 0.66, 0.60);
    vec3 deepCol = vec3(0.020, 0.135, 0.330);
#if DIM == DIM_DEEP
    deepCol = vec3(0.006, 0.05, 0.11);
#endif
    vec3 scatterCol = mix(shallowCol, deepCol, smoothstep(0.8, 14.0, max(vertThick, viewThick * 0.5)));
    vec3 biome = toLinear(tintData.rgb);
    scatterCol = mix(scatterCol, scatterCol * biome / max(luminance(biome), 0.05) * 0.6, 0.18);
    vec3 refracted = behind * T + scatterCol * bodyLight * 0.55 * (1.0 - T);

    // reflection: sky (stable fallback) and, on High, screen-space hits
    float NdotV = clamp(dot(N, -V), 0.0, 1.0);
    float F = fresnelSchlick(0.02, NdotV);
    vec3 R = reflect(V, N);
    R.y = abs(R.y) + 0.01;
    R = normalize(R);
#if REFLECTION_QUALITY >= 1
    vec3 camPos = anchoredWorldPos(vec3(0.0));
    vec3 skyRefl = fullSky(R, sunDir, moonDir, camPos, 2, false);
#else
    vec3 skyRefl = skyGradient(R, sunDir);
#endif
    // a pool under a roof reflects its dim surroundings, not a bright sky
    vec3 refl = mix(bodyLight * 0.25, skyRefl, smoothstep(0.35, 0.95, skyLM));
#if REFLECTION_QUALITY == 2
    vec3 Rv = mat3(gbufferModelView) * R;
    vec4 hit = screenSpaceReflection(viewPos0, Rv, playerPos0.y, ign(gl_FragCoord.xy));
    refl = mix(refl, hit.rgb, hit.a);
#endif

    // sun glint, restrained
    // sun glint: a sharp sparkle plus a broader, dimmer glitter path (strongest low over the horizon)
    float FH = fresnelSchlick(0.02, max(dot(normalize(lightDir - V), -V), 0.0));
    vec3 spec = directCol * sunVis * FH * SPECULAR_STRENGTH
              * (specularLobe(N, -V, lightDir, 0.07) * 0.6 + specularLobe(N, -V, lightDir, 0.30) * 0.9);

    vec3 col = mix(refracted, refl, F) + spec;

    // foam at the shoreline and around things standing in the water; whitecaps in storms
    float foam = shoreFoam(vertThick, dist0, wp, sea);
    float crest = smoothstep(0.14, 0.30, waveHeight(wp.xz, WAVE_STRENGTH * sea * stormFactor())) * rainStrength * sea * 0.7;
    float breakup = vnoise(wp.xz * 0.5 + vec2(waveTime() * 0.2, -waveTime() * 0.1), 2048.0) * 0.6
                  + vnoise(wp.xz * 2.0 + waveTime() * 0.4, 8192.0) * 0.4;
    // streaky, broken caps rather than solid patches
    float streak = vnoise(wp.xz * 3.0 + waveTime() * 0.6, 12288.0);
    crest *= smoothstep(0.5, 0.8, breakup) * smoothstep(0.35, 0.75, streak) * (1.0 - smoothstep(40.0, 120.0, dist0));
    foam = clamp(max(foam, crest) * FOAM_INTENSITY, 0.0, 1.0);
    vec3 foamCol = (ambientCol * skyLightCurve(skyLM) + directCol * sunVis * 0.5 + blockLightColor() * blockLightCurve(blockLM) + minimumLight()) * 0.85;
    col = mix(col, foamCol, foam);
    return col;
}

vec3 shadeWaterFromBelow(vec3 N, vec3 V, vec4 tintData, float skyLM) {
    vec3 Nd = N.y > 0.0 ? -N : N;            // facing down, toward the camera
    float cosI = clamp(dot(-V, Nd), 0.0, 1.0);
    float sinT2 = 1.7689 * (1.0 - cosI * cosI); // (1.33)^2
    vec3 inside = underwaterFogColor();
    if (sinT2 >= 1.0) return inside;          // total internal reflection
    float cosT = sqrt(1.0 - sinT2);
    float F = fresnelSchlick(0.02, cosT);
    vec3 Nv = mat3(gbufferModelView) * Nd;
    vec2 ruv = uv - Nv.xy * 0.03 * WATER_REFRACTION;
    if (!insideScreen(ruv)) ruv = uv;
    vec3 above = texture(colortex0, ruv).rgb;
    // Snell's window: the world above, softened toward its edge
    float window = smoothstep(0.0, 0.25, cosT);
    return mix(inside, above * vec3(0.85, 0.97, 1.0), window * (1.0 - F));
}

vec3 atmosphericHaze(vec3 color, vec3 playerPos, vec3 V, float dist) {
#if HAS_SUN
    float worldY = cameraPosition.y + playerPos.y * 0.5;
    float heightFalloff = exp(-max(worldY - SEA_SURFACE_Y, 0.0) / 90.0);
    #if DIM == DIM_SKY
        heightFalloff *= 0.6;
    #endif
    float density = (0.0012 + 0.0060 * rainStrength + 0.0012 * twilightFactor(sunDir)) * HAZE_STRENGTH;
    float f = 1.0 - exp(-dist * density * heightFalloff);
    vec3 hc = hazeColor(V, sunDir);
#elif DIM == DIM_DEEP
    float f = (1.0 - exp(-dist * 0.010)) * HAZE_STRENGTH;
    vec3 hc = vec3(0.008, 0.045, 0.080);
#elif DIM == DIM_INTERIOR
    float f = (1.0 - exp(-dist * 0.004)) * HAZE_STRENGTH;
    vec3 hc = vec3(0.016, 0.012, 0.010);
#elif DIM == DIM_NETHER
    float f = (1.0 - exp(-dist * 0.012)) * HAZE_STRENGTH;
    vec3 hc = toLinear(fogColor) * 0.9;
#else
    float f = (1.0 - exp(-dist * 0.004)) * HAZE_STRENGTH;
    vec3 hc = vec3(0.035, 0.025, 0.055);
#endif
    // hide the edge of the loaded world
    float border = smoothstep(far * 0.66, far * 0.92, length(playerPos.xz));
    // the mod's own fog banks shorten the vanilla fog distance: follow them
    float modFog = 1.0 - smoothstep(0.55, 0.92, fogEnd / max(far, 1.0));
    float bank = smoothstep(fogEnd * 0.1, fogEnd, dist) * modFog;
    f = clamp(max(max(f, border), bank), 0.0, 1.0);
    return mix(color, hc, f);
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float depth0 = texelFetch(depthtex0, px, 0).r;
    vec3 color = texelFetch(colortex0, px, 0).rgb;

    vec3 viewPos0 = screenToView(uv, depth0);
    vec3 playerPos0 = viewToPlayer(viewPos0);
    vec3 V = normalize(playerPos0);
    float dist0 = length(playerPos0);

    vec4 wdata = texelFetch(colortex3, px, 0);
    if (wdata.w > 0.5 && depth0 < 1.0) {
        vec3 N = decodeNormal(wdata.xy);
        vec4 tintData = texelFetch(colortex6, px, 0);
        if (isEyeInWater == 1) color = shadeWaterFromBelow(N, V, tintData, wdata.z);
        else color = shadeWaterFromAbove(color, N, wdata.z, tintData, depth0, viewPos0, playerPos0, V, dist0);
    }

    if (isEyeInWater == 1) {
        // underwater: red fades first, then everything sinks into the water colour
    #if DIM == DIM_DEEP
        float density = 0.020 * UNDERWATER_FOG; // the deep sea is travelled under water: keep it readable
    #else
        float density = 0.040 * UNDERWATER_FOG;
    #endif
        float d = depth0 >= 1.0 ? far : dist0;
        float f = 1.0 - exp(-d * density);
        f = max(f, (1.0 - inWater) * 0.55);  // the first moment after diving in is murky with bubbles
        color *= exp(-vec3(0.20, 0.05, 0.03) * d * 0.5);
        color = mix(color, underwaterFogColor(), f);
    } else if (isEyeInWater == 2) {
        color = mix(color, vec3(1.6, 0.35, 0.04), 1.0 - exp(-dist0 * 1.5));
    } else if (isEyeInWater == 3) {
        color = mix(color, vec3(0.75, 0.82, 0.92) * ambientCol, 1.0 - exp(-dist0 * 0.9));
    } else if (depth0 < 1.0) {
        color = atmosphericHaze(color, playerPos0, V, dist0);
    }

    // blindness / darkness effects
    float dark = max(blindness, darknessFactor);
    if (dark > 0.0) color *= mix(1.0, exp(-dist0 * 0.35), dark);

    outColor = vec4(color, 1.0);

    // bloom source: only what is genuinely bright after exposure
    vec3 exposed = color * sceneExposure();
    float m = maxOf(exposed);
    const float threshold = 4.5;
    const float knee = 1.2;
    float soft = clamp(m - threshold + knee, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee);
    float contribution = max(soft, m - threshold) / max(m, 1e-4);
    outBloom = vec4(min(exposed * contribution, vec3(64.0)), 1.0);
}

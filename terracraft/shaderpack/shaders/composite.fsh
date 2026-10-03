#version 330 compatibility
// Sky (atmosphere, sun, moon, stars, clouds), water reflections, distance fog, underwater fog and volumetric light.
#include "/lib/common.glsl"
#include "/lib/shadows.glsl"

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;

in vec2 uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 viewFromDepth(vec2 coord, float depth) {
    vec4 v = gbufferProjectionInverse * vec4(vec3(coord, depth) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

vec3 stars(vec3 dir, vec3 sun) {
    float night = 1.0 - smoothstep(-0.25, 0.05, sun.y);
    if (night <= 0.0 || dir.y < -0.05) {
        return vec3(0.0);
    }
    vec3 q = floor(dir * 260.0);
    float h = hash13(q);
    float star = smoothstep(0.9975, 1.0, h);
    float twinkle = 0.65 + 0.35 * sin(frameTimeCounter * (2.0 + h * 6.0) + h * 40.0);
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.85, 0.7), hash13(q + 7.0));
    return tint * star * twinkle * night * 2.5 * (1.0 - rainStrength);
}

vec3 sunAndMoon(vec3 dir, vec3 sun) {
    float cosSun = dot(dir, sun);
    vec3 col = vec3(0.0);
    float disk = smoothstep(0.99955, 0.99975, cosSun);
    col += directLightColor(sun) * disk * 25.0 * smoothstep(-0.05, 0.02, sun.y);
    vec3 moon = -sun;
    float cosMoon = dot(dir, moon);
    float moonDisk = smoothstep(0.99935, 0.99955, cosMoon);
    if (moonDisk > 0.0) {
        // a little crater shading
        vec3 local = dir - moon;
        float crater = noise2(local.xy * 900.0 + local.z * 300.0);
        col += vec3(0.75, 0.82, 1.0) * moonDisk * (0.65 + 0.35 * crater) * 1.6;
    }
    col += vec3(0.5, 0.6, 0.9) * pow(max(cosMoon, 0.0), 400.0) * 0.25;
    return col * (1.0 - rainStrength * 0.9);
}

/** Drifting cumulus layer: the view ray hits a plane at CLOUD_HEIGHT, lit from the sun with a silver lining. */
vec4 clouds(vec3 dir, vec3 sun) {
#ifdef CLOUDS
    float eyeY = cameraPosition.y;
    if ((dir.y <= 0.01 && eyeY < CLOUD_HEIGHT) || (dir.y >= -0.01 && eyeY > CLOUD_HEIGHT)) {
        return vec4(0.0);
    }
    float t = (CLOUD_HEIGHT - eyeY) / dir.y;
    if (t > 6000.0) {
        return vec4(0.0);
    }
    vec2 p = (cameraPosition.xz + dir.xz * t) * 0.0016 + vec2(frameTimeCounter * 0.004, frameTimeCounter * 0.0015);
    float coverage = CLOUD_COVERAGE + rainStrength * 0.3;
    float d = fbm(p) + (fbm(p * 3.1 + 5.0) - 0.5) * 0.25;
    float density = smoothstep(1.0 - coverage, 1.0 - coverage + 0.18, d);
    if (density <= 0.0) {
        return vec4(0.0);
    }
    // self shadowing: thicker cloud toward the sun means a darker underside
    float toward = fbm(p + sun.xz * 0.05) + (fbm((p + sun.xz * 0.05) * 3.1 + 5.0) - 0.5) * 0.25;
    float thickness = smoothstep(1.0 - coverage, 1.0 - coverage + 0.3, toward);
    float lit = mix(1.0, 0.35, thickness);
    vec3 sunCol = directLightColor(sun);
    vec3 amb = ambientLightColor(sun) * 1.1;
    float silver = pow(max(dot(dir, sun), 0.0), 6.0) * (1.0 - density * 0.6) * 1.5;
    float edge = 1.0 - smoothstep(0.0, 0.6, density);
    vec3 col = amb * (0.8 + 0.4 * density) + sunCol * (lit * 0.42 + silver * edge + edge * 0.08);
    col = mix(col, vec3(luma(col)) * 0.6, rainStrength * 0.7);
    float fade = exp(-t * 0.00028) * smoothstep(0.0, 0.10, abs(dir.y));
    return vec4(col, min(density * 1.3, 1.0) * fade);
#else
    return vec4(0.0);
#endif
}

vec3 fullSky(vec3 dir, vec3 sun) {
    vec3 col = skyColor(dir, sun) + stars(dir, sun) + sunAndMoon(dir, sun);
    vec4 cl = clouds(dir, sun);
    return mix(col, cl.rgb, cl.a);
}

#ifdef WATER_REFLECTIONS
/** Screen-space reflection; falls back to the sky (dimmed where the sky is not visible). */
vec3 reflection(vec3 viewPos, vec3 viewNormal, float skyLight, vec3 sun) {
    vec3 V = normalize(viewPos);
    vec3 R = reflect(V, viewNormal);
    vec3 fallback = skyColor(mat3(gbufferModelViewInverse) * R, sun) * skyLight * skyLight;
    if (R.z > 0.0 && dot(R, -V) < 0.2) {
        return fallback;   // reflecting back toward the camera: nothing on screen
    }
    vec3 stepVec = R * (0.4 + length(viewPos) * 0.025);
    vec3 p = viewPos + stepVec * ign(gl_FragCoord.xy);
    for (int i = 0; i < 40; i++) {
        p += stepVec;
        vec4 clip = gbufferProjection * vec4(p, 1.0);
        vec3 sc = clip.xyz / clip.w * 0.5 + 0.5;
        if (sc.x < 0.0 || sc.x > 1.0 || sc.y < 0.0 || sc.y > 1.0 || clip.w < 0.0) {
            break;
        }
        float sceneDepth = texture(depthtex1, sc.xy).r;
        vec3 scenePos = viewFromDepth(sc.xy, sceneDepth);
        float diff = scenePos.z - p.z;
        if (diff > 0.0 && diff < length(stepVec) * 2.5) {
            // refine between the last two steps
            vec3 a = p - stepVec, b = p;
            for (int j = 0; j < 5; j++) {
                vec3 m = (a + b) * 0.5;
                vec4 mc = gbufferProjection * vec4(m, 1.0);
                vec3 ms = mc.xyz / mc.w * 0.5 + 0.5;
                if (viewFromDepth(ms.xy, texture(depthtex1, ms.xy).r).z > m.z) {
                    b = m;
                } else {
                    a = m;
                }
            }
            vec4 bc = gbufferProjection * vec4(b, 1.0);
            vec2 hit = bc.xy / bc.w * 0.5 + 0.5;
            if (texture(depthtex1, hit).r >= 1.0) {
                return fallback;
            }
            vec2 edge = smoothstep(0.0, 0.08, hit) * smoothstep(1.0, 0.92, hit);
            return mix(fallback, texture(colortex0, hit).rgb, edge.x * edge.y);
        }
        stepVec *= 1.12;
    }
    return fallback;
}
#endif

void main() {
    vec3 color = texture(colortex0, uv).rgb;
    float depth0 = texture(depthtex0, uv).r;
    vec3 viewPos = viewFromDepth(uv, depth0);
    vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos;
    vec3 dir = normalize(playerPos);
    float dist = length(playerPos);
    vec3 sun = sunDirection();
    vec3 L = lightDirection();

    if (depth0 >= 1.0) {
        color = fullSky(dir, sun);
        dist = far * 1.5;
    } else {
        vec4 material = texture(colortex1, uv);
#ifdef WATER_REFLECTIONS
        if (material.z > 0.1 && isEyeInWater == 0) {
            vec3 worldN = decodeNormal(material.xy);
            vec3 viewN = normalize(mat3(gbufferModelView) * worldN);
            float cosV = clamp(dot(-normalize(viewPos), viewN), 0.0, 1.0);
            float f0 = material.z > 0.9 ? 0.02 : 0.04;
            float fresnel = f0 + (1.0 - f0) * pow(1.0 - cosV, 5.0);
            float strength = material.z > 0.9 ? 1.0 : 0.6;
            color = mix(color, reflection(viewPos, viewN, material.w, sun), fresnel * strength);
        }
#endif
        // aerial perspective and a soft edge at the render distance
        if (isEyeInWater == 0) {
            float fogAmount = 1.0 - exp(-dist * 0.0011 * FOG_DENSITY * (1.0 + rainStrength * 4.0));
            float border = smoothstep(far * 0.72, far * 0.98, dist);
            float skyLight = eyeBrightnessSmooth.y / 240.0;
            vec3 fogCol = skyColor(dir, sun);
            color = mix(color, fogCol, max(fogAmount * 0.55 * skyLight, border));
        }
    }

    if (isEyeInWater == 1) {
        // underwater: blue-green absorption and murk
        vec3 murk = vec3(0.02, 0.09, 0.12) * (ambientLightColor(sun) + 0.05) * 3.0;
        color *= exp(-vec3(0.45, 0.12, 0.08) * min(dist, 64.0) * 0.12);
        color = mix(color, murk, 1.0 - exp(-min(dist, 96.0) * 0.045));
    } else if (isEyeInWater == 2) {
        color = mix(color, vec3(1.2, 0.35, 0.05), 1.0 - exp(-dist * 0.6));
    }

#ifdef VOLUMETRIC_LIGHT
    // god rays: march toward the surface, counting where the sun/moon reaches
    float marchDist = min(dist, shadowDistance);
    float skyLightEye = eyeBrightnessSmooth.y / 240.0;
    if (skyLightEye > 0.05 && isEyeInWater != 2) {
        float dither = ign(gl_FragCoord.xy);
        float lit = 0.0;
        for (int i = 0; i < VL_STEPS; i++) {
            float t = (float(i) + dither) / float(VL_STEPS);
            t = t * t;   // more samples close to the camera
            lit += shadowOpaque(dir * t * marchDist);
        }
        lit /= float(VL_STEPS);
        float g = 0.72;
        float cosT = dot(dir, L);
        float phase = (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * cosT, 1.5));
        float density = (0.0025 + rainStrength * 0.004) * (isEyeInWater == 1 ? 12.0 : 1.0);
        float amount = lit * (1.0 - exp(-marchDist * density)) * (phase + 0.02);
        vec3 tint = isEyeInWater == 1 ? vec3(0.35, 0.75, 0.9) : vec3(1.0);
        color += directLightColor(sun) * tint * amount * VL_STRENGTH * skyLightEye;
    }
#endif

    outColor = vec4(color, 1.0);
}

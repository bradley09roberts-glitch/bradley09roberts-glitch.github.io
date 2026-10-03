// Shared fragment stage of every gbuffers program: forward lighting into HDR colortex0.
// Water also writes its normal and material into colortex1 for the reflection pass.
#include "/lib/common.glsl"

#if !defined PROGRAM_BASIC && !defined PROGRAM_WEATHER && !defined PROGRAM_EMISSIVE
#define LIT
#include "/lib/shadows.glsl"
#endif

uniform sampler2D gtexture;
uniform vec4 entityColor;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 playerPos;
in vec3 worldNormal;
flat in int blockId;

#ifdef PROGRAM_WATER
/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outMaterial;
#else
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;
#endif

#ifdef PROGRAM_WATER
float waterHeight(vec2 p) {
    float t = frameTimeCounter;
    float h = 0.0;
    h += sin(p.x * 0.80 + t * 1.10) * 0.050;
    h += sin(p.y * 0.95 - t * 1.30 + p.x * 0.30) * 0.040;
    h += sin((p.x + p.y) * 1.70 + t * 1.90) * 0.020;
    h += (noise2(p * 1.6 + vec2(t * 0.35, -t * 0.25)) - 0.5) * 0.06;
    h += (noise2(p * 3.7 - vec2(t * 0.6, t * 0.4)) - 0.5) * 0.025;
    return h;
}

vec3 waterNormal(vec3 worldPos, vec3 flatNormal) {
    vec2 p = worldPos.xz;
    float e = 0.08;
    float h0 = waterHeight(p);
    float hx = waterHeight(p + vec2(e, 0.0));
    float hz = waterHeight(p + vec2(0.0, e));
    vec3 n = normalize(vec3((h0 - hx) / e, 1.0, (h0 - hz) / e));
    // only bend the top faces; sides keep their own normal
    return flatNormal.y > 0.5 ? n : flatNormal;
}
#endif

void main() {
#ifdef PROGRAM_BASIC
    vec4 albedo = glcolor;
#else
    vec4 albedo = texture(gtexture, texcoord) * glcolor;
#endif
    if (albedo.a < 0.1) {
        discard;
    }
#ifdef PROGRAM_ENTITIES
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif

    vec3 srgb = albedo.rgb;
    vec3 base = pow(srgb, vec3(2.2));

#ifdef PROGRAM_EMISSIVE
    // beacon beams, spider eyes, glowing eyes: unlit and bright
    outColor = vec4(base * 4.0 * EMISSIVE_STRENGTH + base, albedo.a);
    return;
#endif

#ifdef PROGRAM_WEATHER
    vec3 sunW = sunDirection();
    outColor = vec4(base * (ambientLightColor(sunW) * 1.5 + BLOCKLIGHT_COLOR * lmcoord.x * 0.6), albedo.a * 0.5);
    return;
#endif

    vec3 sun = sunDirection();
    vec3 n = normalize(worldNormal);
    vec3 L = lightDirection();
    bool foliage = blockId == ID_LEAVES || blockId == ID_PLANT;

#ifdef PROGRAM_WATER
    bool isWater = blockId == ID_WATER;
    if (isWater) {
        n = waterNormal(playerPos + cameraPosition, n);
        // tint the vanilla water texture toward a deep clear blue
        base = mix(base, vec3(0.02, 0.10, 0.16), 0.55);
        albedo.a = 0.62;
    }
#endif

    float NdotL = dot(n, L);
    float lightFacing = foliage ? max(NdotL, 0.0) * 0.6 + 0.4 : max(NdotL, 0.0);

    vec3 visibility = vec3(0.0);
#ifdef LIT
    if (lightFacing > 0.0) {
        visibility = sampleShadow(playerPos, foliage ? vec3(0.0, 1.0, 0.0) : n, clamp(NdotL, 0.0, 1.0));
    }
#endif
    // no direct light where the sky cannot reach (caves beyond the shadow map, inside buildings)
    float skyAccess = smoothstep(0.0, 0.35, lmcoord.y);

    vec3 direct = directLightColor(sun) * lightFacing * visibility * skyAccess;
    vec3 ambient = ambientLightColor(sun) * (lmcoord.y * lmcoord.y) * (0.62 + 0.38 * n.y);
    vec3 blockLight = BLOCKLIGHT_COLOR * pow(lmcoord.x, 2.6) * 1.9;
    vec3 color = base * (direct + ambient + blockLight + vec3(0.010));

    // subsurface glow through backlit leaves and grass
    if (foliage) {
        float back = pow(max(dot(normalize(playerPos), L), 0.0), 4.0);
        color += base * directLightColor(sun) * back * 0.6 * skyAccess * visibility;
    }

#if defined PROGRAM_TERRAIN || defined PROGRAM_HAND || defined PROGRAM_BLOCK
    if (blockId == ID_EMISSIVE) {
        float glow = smoothstep(0.35, 0.95, luma(srgb));
        color += base * glow * 3.5 * EMISSIVE_STRENGTH;
    } else if (blockId == ID_ORE) {
        // ore specks sparkle: saturated pixels catch a little light of their own
        float sat = max(srgb.r, max(srgb.g, srgb.b)) - min(srgb.r, min(srgb.g, srgb.b));
        color += base * smoothstep(0.25, 0.6, sat) * 0.8 * EMISSIVE_STRENGTH;
    }
#endif

    // sun glint on water and glass
#ifdef PROGRAM_WATER
    vec3 V = normalize(-playerPos);
    vec3 H = normalize(L + V);
    float spec = pow(max(dot(n, H), 0.0), isWater ? 900.0 : 300.0) * (isWater ? 18.0 : 6.0);
    color += directLightColor(sun) * spec * visibility * skyAccess;
    float material = isWater ? 1.0 : (blockId == ID_GLASS ? 0.5 : 0.25);
    outMaterial = vec4(encodeNormal(n), material, lmcoord.y);
#endif

    outColor = vec4(color, albedo.a);
}

// Lit world geometry: terrain, entities, block entities, hand, particles, destroy overlay.
// Variant chosen by PROGRAM_* defines in the wrapper files. Original code, Emberveil Shaders.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"

#if !defined DIM_NETHER && !defined DIM_END && !defined PROGRAM_DAMAGED
#define SHADOW_SAMPLING
#endif

#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"

// ============================================================================ vertex
#ifdef VSH
#ifdef PROGRAM_TERRAIN
in vec4 mc_Entity;
in vec2 mc_midTexCoord;
#include "/lib/waving.glsl"
#endif

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalV;
out vec3 viewPos;
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
    glcolor = gl_Color;
    normalV = normalize(gl_NormalMatrix * gl_Normal);
    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
    matId = 0;
#ifdef PROGRAM_TERRAIN
    matId = int(mc_Entity.x + 0.5);
#if defined WAVING_PLANTS || defined WAVING_LEAVES
    if (matId >= MAT_PLANT && matId <= MAT_UNDERWATER_PLANT) {
        vec3 playerPos = (gbufferModelViewInverse * vp).xyz;
        bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
        playerPos += waveOffset(playerPos + cameraPosition, matId, top, lmcoord.y);
        vp = gbufferModelView * vec4(playerPos, 1.0);
    }
#endif
#endif
    viewPos = vp.xyz;
    gl_Position = gl_ProjectionMatrix * vp;
}
#endif

// ============================================================================ fragment
#ifdef FSH
#include "/lib/shadows.glsl"
uniform sampler2D gtexture;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalV;
in vec3 viewPos;
flat in int matId;

float emissionFor(int id, vec3 texSrgb) {
    float l = luma(texSrgb);
    if (id == MAT_TORCHLIKE)    return smoothstep(0.55, 0.85, l) * 2.2;  // flames/glass glow, the stick does not
    if (id == MAT_LAVA)         return 1.4 + l;
    if (id == MAT_LIGHT_BLOCK)  return 0.8 + smoothstep(0.45, 0.9, l) * 1.2;
    if (id == MAT_FIRE)         return 0.6 + 1.8 * l;
    if (id == MAT_FAINT_GLOW)   return smoothstep(0.4, 0.85, l) * 0.9;
    return 0.0;
}

/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outMaterial;

void main() {
    vec4 tex = texture(gtexture, texcoord);
    vec4 base = tex * glcolor;
    if (base.a < alphaTestRef) discard;

#ifdef PROGRAM_DAMAGED
    // Block-breaking cracks: multiplied over the block by the vanilla blend mode.
    outColor = vec4(toLinear(base.rgb), base.a);
    outMaterial = vec4(0.0);
    return;
#endif

    vec3 albedo = toLinear(base.rgb);
    vec3 n = normalize(normalV);
    float emission = 0.0, subsurface = 0.0, code = CODE_NONE;

#ifdef PROGRAM_TERRAIN
    if (matId >= MAT_PLANT && matId <= MAT_UNDERWATER_PLANT) { subsurface = 1.0; code = CODE_FOLIAGE; }
#ifdef EMISSIVE_BLOCKS
    emission = emissionFor(matId, base.rgb);
#endif
#endif

#ifdef PROGRAM_ENTITIES
    // Hurt flash / creeper swell tint supplied by the game.
    albedo = mix(albedo, toLinear(entityColor.rgb), entityColor.a);
    code = CODE_ENTITY;
#ifdef EMISSIVE_BLOCKS
    if (entityId == ENT_EMISSIVE) emission = smoothstep(0.5, 0.85, luma(tex.rgb)) * 1.6;
    if (currentRenderedItemId >= MAT_TORCHLIKE && currentRenderedItemId <= MAT_FAINT_GLOW) emission = emissionFor(currentRenderedItemId, base.rgb);
#endif
#endif

#ifdef PROGRAM_HAND
    code = CODE_HAND;
#ifdef EMISSIVE_BLOCKS
    if (currentRenderedItemId >= MAT_TORCHLIKE && currentRenderedItemId <= MAT_FAINT_GLOW) emission = emissionFor(currentRenderedItemId, base.rgb);
#endif
#endif

#ifdef PROGRAM_BLOCK
#ifdef EMISSIVE_BLOCKS
    emission = emissionFor(blockEntityId, base.rgb);
#endif
    if (blockEntityId == 10040) {
        // End portal / gateway: a slow, layered starfield instead of the vanilla shader.
        vec2 sp = gl_FragCoord.xy / vec2(viewWidth, viewHeight);
        vec3 c = vec3(0.01, 0.02, 0.04);
        for (int i = 1; i <= 3; i++) {
            vec2 q = sp * (6.0 + float(i) * 5.0) + vec2(frameTimeCounter * 0.01 * float(i), float(i) * 3.7);
            float star = pow(sat(1.0 - length(fract(q) - 0.5) * 2.4), 18.0) * step(0.8, fract(sin(dot(floor(q), vec2(12.9898, 78.233))) * 43758.5453));
            c += vec3(0.35, 0.8, 0.75) * star * (0.8 / float(i));
        }
        outColor = vec4(c * 3.0, 1.0);
        outMaterial = vec4(0.6, 0.0, 1.0, 1.0);
        return;
    }
#endif

#ifdef PROGRAM_PARTICLES
    // Particles face the camera; fully bright ones (flames, lava pops, glyphs) are treated as glowing.
    n = -normalize(viewPos);
    subsurface = 0.6;
    emission = step(0.99, lmcoord.x) * smoothstep(0.5, 0.85, luma(base.rgb)) * 1.0;
#endif

    vec3 shadowVis = vec3(1.0);
#ifdef SHADOW_SAMPLING
    float NdotL = dot(n, normalize(shadowLightPosition));
    if (lmcoord.y > 0.02 && (NdotL > 0.0 || subsurface > 0.0)) {
        shadowVis = sampleShadow(viewPos, NdotL >= 0.0 ? n : -n, abs(NdotL));
    }
#endif

    vec3 col = shadeSurface(albedo, n, viewPos, lmcoord, shadowVis, subsurface, emission);
    outColor = vec4(col, base.a);
    outMaterial = vec4(sat(emission * 0.45), code / 255.0, lmcoord.y, 1.0);
}
#endif

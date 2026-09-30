// Shadow map pass: distorted projection, matching foliage motion, water excluded. Original code.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/shadows.glsl"

#ifdef VSH
in vec4 mc_Entity;
in vec2 mc_midTexCoord;
#include "/lib/waving.glsl"
out vec2 texcoord;
out vec4 glcolor;
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    matId = int(mc_Entity.x + 0.5);
    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
#if defined WAVING_PLANTS || defined WAVING_LEAVES
    if (matId >= MAT_PLANT && matId <= MAT_UNDERWATER_PLANT) {
        float sky = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy).y;
        vec3 playerPos = (shadowModelViewInverse * vp).xyz;
        bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
        playerPos += waveOffset(playerPos + cameraPosition, matId, top, sky);
        vp = shadowModelView * vec4(playerPos, 1.0);
    }
#endif
    vec4 clip = gl_ProjectionMatrix * vp;
    clip.xyz = distortShadow(clip.xyz);
    gl_Position = clip;
}
#endif

#ifdef FSH
uniform sampler2D gtexture;
in vec2 texcoord;
in vec4 glcolor;
flat in int matId;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 shadowColorOut;

void main() {
    // Water does not block sunlight, so shallows and seabeds stay lit.
    if (matId == MAT_WATER) discard;
    vec4 c = texture(gtexture, texcoord) * glcolor;
    if (c.a < 0.1) discard;
    shadowColorOut = c;
}
#endif

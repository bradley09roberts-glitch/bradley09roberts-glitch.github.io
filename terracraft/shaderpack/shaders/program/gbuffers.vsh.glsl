// Shared vertex stage of every gbuffers program. The including file defines PROGRAM_<NAME>.
#include "/lib/common.glsl"

out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 playerPos;
out vec3 worldNormal;
flat out int blockId;

#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER
in vec4 mc_Entity;
in vec4 mc_midTexCoord;
#endif

#ifdef WAVING_PLANTS
vec3 wave(vec3 worldPos, float strength) {
    float t = frameTimeCounter * 1.6;
    float wind = 0.6 + 0.4 * sin(t * 0.21 + worldPos.x * 0.02);
    vec3 offset;
    offset.x = sin(t + worldPos.x * 0.7 + worldPos.z * 0.3) * 0.6 + sin(t * 2.3 + worldPos.z * 1.3) * 0.3;
    offset.z = cos(t * 0.9 + worldPos.z * 0.6 + worldPos.x * 0.2) * 0.6 + cos(t * 2.1 + worldPos.x * 1.1) * 0.3;
    offset.y = sin(t * 1.7 + worldPos.x + worldPos.z) * 0.15;
    return offset * strength * wind * (1.0 + rainStrength);
}
#endif

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    lmcoord = clamp((lmcoord - 1.0 / 32.0) * 16.0 / 15.0, 0.0, 1.0);
    glcolor = gl_Color;
    blockId = 0;

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    playerPos = mat3(gbufferModelViewInverse) * viewPos.xyz + gbufferModelViewInverse[3].xyz;
    worldNormal = normalize(mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal));

#if defined PROGRAM_TERRAIN || defined PROGRAM_WATER
    blockId = int(mc_Entity.x + 0.5);
    #ifdef WAVING_PLANTS
    if (blockId == ID_LEAVES || blockId == ID_PLANT) {
        bool top = gl_MultiTexCoord0.t < mc_midTexCoord.t;
        float strength = blockId == ID_LEAVES ? 0.035 : (top ? 0.09 : 0.0);
        playerPos += wave(playerPos + cameraPosition, strength * lmcoord.y);
        viewPos = gbufferModelView * vec4(playerPos, 1.0);
    }
    #endif
#endif

    gl_Position = gl_ProjectionMatrix * viewPos;
}

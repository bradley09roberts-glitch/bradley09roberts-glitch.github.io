// Opaque geometry that is lit later in the deferred pass: terrain (10), entities (11), block entities (12), hand (13).
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/motion.glsl"

#if PROG_ID == 10
in vec4 mc_Entity;
in vec4 at_midBlock;
#endif

out vec2 texcoord;
out vec2 lmcoord;
out vec4 tint;
out vec3 normalW;
out float emissionHint;
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
    tint = gl_Color;
    normalW = mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal);
    emissionHint = 0.0;

#if PROG_ID == 10
    matId = materialGroup(mc_Entity.x);
    emissionHint = clamp(at_midBlock.w / 15.0, 0.0, 1.0);
    if (isMoving(matId)) {
        vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
        vec3 playerPos = (gbufferModelViewInverse * viewPos).xyz;
        playerPos += foliageOffset(matId, anchoredWorldPos(playerPos), at_midBlock);
        gl_Position = gl_ProjectionMatrix * (gbufferModelView * vec4(playerPos, 1.0));
    } else {
        gl_Position = ftransform();
    }
#elif PROG_ID == 13
    matId = MAT_HAND;
    gl_Position = ftransform();
#else
    matId = MAT_ENTITY;
    gl_Position = ftransform();
#endif
}

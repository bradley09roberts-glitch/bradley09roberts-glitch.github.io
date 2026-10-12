// Translucent terrain (50: water, stained glass, ice, slime, the bubble membrane...) and translucent hand items (51).
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/waves.glsl"

#if PROG_ID == 50
in vec4 mc_Entity;
in vec4 at_midBlock;
#endif

out vec2 texcoord;
out vec2 lmcoord;
out vec4 tint;
out vec3 normalW;
out vec3 playerPos;
out vec3 anchoredPos;
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
    tint = gl_Color;
    normalW = mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal);
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    playerPos = (gbufferModelViewInverse * viewPos).xyz;
    anchoredPos = anchoredWorldPos(playerPos);

#if PROG_ID == 50
    matId = materialGroup(mc_Entity.x);
    if (matId == MAT_WATER && at_midBlock.y < -1.0) {
        // only the upper vertices of water quads move, by the same function as in the shadow pass
        playerPos.y += waterDisplacement(anchoredPos);
        gl_Position = gl_ProjectionMatrix * (gbufferModelView * vec4(playerPos, 1.0));
    } else {
        gl_Position = ftransform();
    }
#else
    matId = MAT_STAINED_GLASS;
    gl_Position = ftransform();
#endif
}

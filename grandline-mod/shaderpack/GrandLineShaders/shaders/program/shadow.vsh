// Shadow pass, vertex. Water displacement and foliage sway use exactly the same functions and inputs as the
// gbuffers programs, so shadows follow the moving geometry.
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/waves.glsl"
#include "/lib/motion.glsl"
#include "/lib/shadows.glsl"

in vec4 mc_Entity;
in vec4 at_midBlock;

out vec2 texcoord;
out vec4 tint;
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    tint = gl_Color;
    matId = materialGroup(mc_Entity.x);

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    vec3 playerPos = (shadowModelViewInverse * viewPos).xyz;
    vec3 wp = anchoredWorldPos(playerPos);
    if (matId == MAT_WATER) {
        if (at_midBlock.y < -1.0) playerPos.y += waterDisplacement(wp);
    } else if (isMoving(matId)) {
        playerPos += foliageOffset(matId, wp, at_midBlock);
    }
    gl_Position = gl_ProjectionMatrix * (shadowModelView * vec4(playerPos, 1.0));
    gl_Position.xyz = distortShadow(gl_Position.xyz);
}

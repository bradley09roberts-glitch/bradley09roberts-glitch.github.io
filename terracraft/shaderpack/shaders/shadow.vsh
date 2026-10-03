#version 330 compatibility
#include "/lib/common.glsl"

out vec2 texcoord;
out vec4 glcolor;
flat out int blockId;

in vec4 mc_Entity;
in vec4 mc_midTexCoord;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    blockId = int(mc_Entity.x + 0.5);
    vec4 pos = ftransform();
    gl_Position = vec4(distortShadow(pos.xyz), pos.w);
}

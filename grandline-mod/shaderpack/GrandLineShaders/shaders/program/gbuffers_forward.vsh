// Forward-shaded geometry: basic (20), textured (21), textured_lit/particles (22), weather (23), beacon beam (24),
// spider eyes (25), armour glint (26), lightning (27), vanilla clouds (30), sky (40/41), damaged block (60).
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"

out vec2 texcoord;
out vec2 lmcoord;
out vec4 tint;
out vec3 normalW;
out vec3 playerPos;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = clamp(((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy - 1.0 / 32.0) * (16.0 / 15.0), 0.0, 1.0);
    tint = gl_Color;
    normalW = mat3(gbufferModelViewInverse) * normalize(gl_NormalMatrix * gl_Normal);
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    playerPos = (gbufferModelViewInverse * viewPos).xyz;
    gl_Position = ftransform();
}

// Full-screen passes (deferred, composite, composite1, final): uv plus per-frame light values computed once per vertex.
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"

out vec2 uv;
flat out vec3 sunDir;
flat out vec3 moonDir;
flat out vec3 lightDir;
flat out vec3 directCol;
flat out vec3 ambientCol;

void main() {
    gl_Position = ftransform();
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    sunDir = worldSunDir();
    moonDir = worldMoonDir();
    lightDir = worldLightDir();
    directCol = directLightColor(sunDir);
    ambientCol = ambientLightColor(sunDir);
}

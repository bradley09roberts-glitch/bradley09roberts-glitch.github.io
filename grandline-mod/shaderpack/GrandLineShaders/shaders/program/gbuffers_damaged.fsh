// Block-breaking overlay: blended (multiplied by the vanilla blend state) into the albedo only.
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"

uniform sampler2D gtexture;

in vec2 texcoord;
in vec2 lmcoord;
in vec4 tint;
in vec3 normalW;
in vec3 playerPos;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

void main() {
    vec4 c = texture(gtexture, texcoord) * tint;
    if (c.a < 0.01) discard;
    outColor = c;
}

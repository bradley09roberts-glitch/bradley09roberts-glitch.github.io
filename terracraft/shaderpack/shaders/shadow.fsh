#version 330 compatibility
#include "/lib/common.glsl"

uniform sampler2D gtexture;

in vec2 texcoord;
in vec4 glcolor;
flat in int blockId;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 shadowColor;

void main() {
    vec4 albedo = texture(gtexture, texcoord) * glcolor;
    if (blockId == ID_WATER) {
        // water lets most light through, tinted blue
        shadowColor = vec4(0.55, 0.80, 0.95, 1.0);
        return;
    }
    if (albedo.a < 0.1) {
        discard;
    }
    // stained glass tints the light by its colour; opaque casters only use the depth
    shadowColor = vec4(mix(vec3(1.0), albedo.rgb, albedo.a), 1.0);
}

#version 330 compatibility
// Bloom prefilter: the bright part of the image, read back through colortex2's mipmaps by the final pass.
#include "/lib/common.glsl"

uniform sampler2D colortex0;

in vec2 uv;

/* RENDERTARGETS: 2 */
layout(location = 0) out vec4 outBloom;

void main() {
    vec3 color = texture(colortex0, uv).rgb;
    float l = luma(color);
    // soft knee around 1.2 (HDR) so only lights, the sun and glowing blocks bloom
    float knee = smoothstep(0.8, 2.5, l);
    // cap very bright pixels (the sun disk) so their glow stays a halo instead of veiling the whole frame
    vec3 bright = color * knee * min(1.0, 4.0 / max(l, 1e-4));
    outBloom = vec4(bright, 1.0);
}

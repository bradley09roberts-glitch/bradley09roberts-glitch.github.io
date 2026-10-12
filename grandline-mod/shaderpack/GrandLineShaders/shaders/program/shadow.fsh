// Shadow pass, fragment. shadowcolor0 alpha 1.0 marks water (absorption + caustics are computed by the receiver),
// alpha <= 0.9 is the opacity of tinted glass.
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"

uniform sampler2D gtexture;

in vec2 texcoord;
in vec4 tint;
flat in int matId;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 shadowOut;

void main() {
    if (matId == MAT_WATER) {
        shadowOut = vec4(1.0);
        return;
    }
    vec4 c = texture(gtexture, texcoord) * tint;
    if (c.a < 0.1) discard;
    if (matId == MAT_STAINED_GLASS || matId == MAT_ICE || matId == MAT_BUBBLE || matId == MAT_SLIME || matId == MAT_PORTAL) {
        shadowOut = vec4(c.rgb, clamp(c.a, 0.0, 0.9));
    } else {
        shadowOut = vec4(c.rgb, 0.9);
    }
}

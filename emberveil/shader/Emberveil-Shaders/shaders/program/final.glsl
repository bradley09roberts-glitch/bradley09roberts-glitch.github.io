// final: tone mapping, ember grade, contrast, subtle vignette, dithering. Original code.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/tonemap.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA8;
const int colortex2Format = RGBA16;
const int colortex3Format = RGBA16F;
*/
const bool colortex3Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);

#ifdef VSH
out vec2 texcoord;
void main() {
    texcoord = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
#endif

#ifdef FSH
in vec2 texcoord;
uniform sampler2D colortex0;
layout(location = 0) out vec4 fragColor;

void main() {
    vec3 c = texture(colortex0, texcoord).rgb;
    c = tonemapEmber(c);
    c = gradeEmber(c);
    vec3 g = applyContrast(toGamma(c));
    vec2 d = texcoord - 0.5;
    g *= 1.0 - VIGNETTE * pow(sat(length(d) * 1.35), 2.5);
    g += (ign(gl_FragCoord.xy) - 0.5) / 255.0;   // de-banding dither (not film grain)
    fragColor = vec4(g, 1.0);
}
#endif

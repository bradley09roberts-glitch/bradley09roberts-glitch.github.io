// composite1: bloom prefilter into colortex3; composite2: mip-chain gather and add. Original code.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"

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
uniform sampler2D colortex1;
uniform sampler2D colortex3;

#ifdef BLOOM_PREFILTER
/* RENDERTARGETS: 3 */
layout(location = 0) out vec4 outBloom;
void main() {
#ifdef BLOOM
    vec3 c = texture(colortex0, texcoord).rgb;
    float emissive = texture(colortex1, texcoord).r;
    float l = luma(c);
    // Glowing materials contribute strongly; other very bright pixels (sun glints) only a little.
    vec3 b = c * sat(emissive * 1.8) + c * smoothstep(1.4, 5.0, l) * 0.3;
    outBloom = vec4(min(b, vec3(24.0)), 1.0);
#else
    outBloom = vec4(0.0);
#endif
}
#endif

#ifdef BLOOM_GATHER
const bool colortex3MipmapEnabled = true;
/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 tentSample(float lod) {
    vec2 px = exp2(lod) / vec2(viewWidth, viewHeight);
    vec3 s = textureLod(colortex3, texcoord, lod).rgb * 4.0;
    s += textureLod(colortex3, texcoord + vec2(px.x, 0.0), lod).rgb * 2.0;
    s += textureLod(colortex3, texcoord - vec2(px.x, 0.0), lod).rgb * 2.0;
    s += textureLod(colortex3, texcoord + vec2(0.0, px.y), lod).rgb * 2.0;
    s += textureLod(colortex3, texcoord - vec2(0.0, px.y), lod).rgb * 2.0;
    s += textureLod(colortex3, texcoord + px, lod).rgb;
    s += textureLod(colortex3, texcoord - px, lod).rgb;
    s += textureLod(colortex3, texcoord + vec2(px.x, -px.y), lod).rgb;
    s += textureLod(colortex3, texcoord + vec2(-px.x, px.y), lod).rgb;
    return s / 16.0;
}

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
#ifdef BLOOM
#if BLOOM_QUALITY == 0
    const int LEVELS = 4;
#elif BLOOM_QUALITY == 1
    const int LEVELS = 6;
#else
    const int LEVELS = 7;
#endif
    vec3 bloom = vec3(0.0);
    float wsum = 0.0;
    for (int i = 1; i <= LEVELS; i++) {
        float w = 1.0 / (0.6 + float(i) * 0.5);
        bloom += tentSample(float(i)) * w;
        wsum += w;
    }
    color += bloom / wsum * 0.35 * BLOOM_STRENGTH;
#endif
    outColor = vec4(color, 1.0);
}
#endif
#endif

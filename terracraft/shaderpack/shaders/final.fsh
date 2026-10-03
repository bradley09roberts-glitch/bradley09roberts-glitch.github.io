#version 330 compatibility
// Bloom, exposure, filmic tone mapping, colour grading and vignette.
#include "/lib/common.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA16;
const int colortex2Format = RGBA16F;
const bool colortex2MipmapEnabled = true;
const bool colortex1Clear = true;
const vec4 colortex1ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
const int shadowcolor0Format = RGBA8;
const bool shadowcolor0Nearest = true;
const bool shadowtex0Nearest = true;
const bool shadowtex1Nearest = true;
*/

uniform sampler2D colortex0;
uniform sampler2D colortex2;

in vec2 uv;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

vec3 bloomAt(float lod) {
    vec2 texel = exp2(lod) / vec2(viewWidth, viewHeight);
    vec3 b = textureLod(colortex2, uv, lod).rgb * 4.0;
    b += textureLod(colortex2, uv + vec2(texel.x, 0.0), lod).rgb * 2.0;
    b += textureLod(colortex2, uv - vec2(texel.x, 0.0), lod).rgb * 2.0;
    b += textureLod(colortex2, uv + vec2(0.0, texel.y), lod).rgb * 2.0;
    b += textureLod(colortex2, uv - vec2(0.0, texel.y), lod).rgb * 2.0;
    b += textureLod(colortex2, uv + texel, lod).rgb;
    b += textureLod(colortex2, uv - texel, lod).rgb;
    b += textureLod(colortex2, uv + vec2(texel.x, -texel.y), lod).rgb;
    b += textureLod(colortex2, uv + vec2(-texel.x, texel.y), lod).rgb;
    return b / 16.0;
}

void main() {
    vec3 color = texture(colortex0, uv).rgb;

    vec3 bloom = vec3(0.0);
    float weight = 0.0;
    for (int i = 1; i <= 7; i++) {
        float w = 1.0 / float(i);
        bloom += bloomAt(float(i)) * w;
        weight += w;
    }
    color += bloom / weight * 0.35 * BLOOM_STRENGTH;

    // exposure: brighter in caves and at night so they stay readable, like the eye adapting
    vec3 sun = sunDirection();
    float sky = eyeBrightnessSmooth.y / 240.0;
    float exposure = EXPOSURE * mix(2.2, 1.0, sky * mix(0.35, 1.0, dayFactor(sun)));
    color *= exposure;

    color = acesFilm(color);
    color = pow(color, vec3(1.0 / 2.2));

    // grading: saturation and a gentle warm lift
    color = mix(vec3(luma(color)), color, SATURATION);
    color = color * vec3(1.02, 1.0, 0.97);

    // vignette
    vec2 c = uv - 0.5;
    color *= 1.0 - VIGNETTE * pow(dot(c, c) * 2.2, 1.2);

    // dither away banding
    color += (ign(gl_FragCoord.xy) - 0.5) / 255.0;
    outColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}

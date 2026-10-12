// Final: bloom, exposure, tone mapping, grading, optional stylisation, the water transitions, dithering.
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"
#include "/lib/tonemap.glsl"
#include "/lib/bloomtiles.glsl"

uniform sampler2D colortex0;
#ifdef BLOOM
uniform sampler2D colortex5;
#endif
uniform sampler2D depthtex0;

in vec2 uv;

layout(location = 0) out vec4 outColor;

#ifdef BLOOM
vec3 sampleBloom(vec2 p) {
    vec3 sum = vec3(0.0);
    vec2 halfTexel = 0.5 / vec2(viewWidth, viewHeight);
    for (int i = 0; i < BLOOM_TILES; i++) {
        vec4 r = bloomTileRect(i);
        vec2 q = clamp(r.xy + p * r.zw, r.xy + halfTexel * 2.0, r.xy + r.zw - halfTexel * 2.0);
        sum += texture(colortex5, q).rgb * (1.0 + float(i) * 0.15);
    }
    return sum / (float(BLOOM_TILES) * 1.4);
}
#endif

float linearDepth(float d) {
    return (2.0 * near * far) / (far + near - (d * 2.0 - 1.0) * (far - near));
}

void main() {
    vec2 p = uv;
    // underwater the view wavers a little; just after surfacing, water runs off the "lens"
    if (isEyeInWater == 1) {
        p += vec2(sin(p.y * 22.0 + frameTimeCounter * 2.1), cos(p.x * 18.0 + frameTimeCounter * 1.7)) * 0.0012;
    }
    float lens = isEyeInWater == 1 ? 0.0 : wetLens;
    if (lens > 0.001) {
        float n = vnoise(vec2(p.x * 9.0, p.y * 5.0 + frameTimeCounter * 0.8), 4096.0);
        float n2 = vnoise(vec2(p.x * 9.0 + 0.7, p.y * 5.0 + frameTimeCounter * 0.8), 4096.0);
        p += vec2(n - n2, n - 0.5) * 0.02 * lens;
    }
    p = clamp(p, vec2(0.0), vec2(1.0));

    vec3 color = texture(colortex0, p).rgb * sceneExposure();
#ifdef BLOOM
    color += sampleBloom(p) * BLOOM_STRENGTH;
#endif

    color = tonemap(color);

    if (OUTLINE_STRENGTH > 0.0) {
        // optional, subtle: darken only strong depth breaks (off by default)
        vec2 px = 1.0 / vec2(viewWidth, viewHeight);
        float d = linearDepth(texture(depthtex0, p).r);
        float dn = min(min(linearDepth(texture(depthtex0, p + vec2(px.x, 0.0)).r), linearDepth(texture(depthtex0, p - vec2(px.x, 0.0)).r)),
                       min(linearDepth(texture(depthtex0, p + vec2(0.0, px.y)).r), linearDepth(texture(depthtex0, p - vec2(0.0, px.y)).r)));
        float edge = smoothstep(0.06, 0.25, (d - dn) / max(d, 1e-3)) * (1.0 - smoothstep(far * 0.4, far * 0.8, d));
        color *= 1.0 - edge * OUTLINE_STRENGTH;
    }

    color = colourGrade(color);

    if (STYLISATION > 0.0) {
        // clean colour separation: values settle into soft steps, colour stays continuous
        float l = luminance(color);
        float lb = softBands(l, 7.0);
        color *= mix(1.0, lb / max(l, 1e-4), STYLISATION * 0.45);
        color = clamp(color, 0.0, 1.0);
    }

    float v = length((uv - 0.5) * vec2(1.0, 0.8)) * 1.3;
    color *= 1.0 - VIGNETTE * pow(clamp(v, 0.0, 1.0), 2.6);
    color = mix(color, color * vec3(0.86, 0.96, 1.06), lens * 0.5);

    color = toGamma(color);
    color += (ign(gl_FragCoord.xy) - 0.5) / 255.0;
    outColor = vec4(color, 1.0);
}

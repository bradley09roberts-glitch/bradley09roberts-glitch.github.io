// Bloom, step 2: blur several mip levels of the bright-pass into a tile atlas (colortex5). Disabled when BLOOM is off.
#include "/lib/settings.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/common.glsl"
#include "/lib/bloomtiles.glsl"

const bool colortex4MipmapEnabled = true;

uniform sampler2D colortex4;

in vec2 uv;

/* RENDERTARGETS: 5 */
layout(location = 0) out vec4 outTiles;

void main() {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < BLOOM_TILES; i++) {
        vec4 r = bloomTileRect(i);
        vec2 local = (uv - r.xy) / r.zw;
        if (any(lessThan(local, vec2(-0.02))) || any(greaterThan(local, vec2(1.02)))) continue;
        float lod = float(i + 2);
        vec2 texel = exp2(lod) / vec2(viewWidth, viewHeight);
        // 3x3 tent-gaussian on this mip level
        vec3 c = vec3(0.0);
        float wsum = 0.0;
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float w = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0);
                vec2 p = clamp(local + vec2(x, y) * texel * 1.5, vec2(0.0), vec2(1.0));
                c += textureLod(colortex4, p, lod).rgb * w;
                wsum += w;
            }
        }
        sum = c / wsum;
        break;
    }
    outTiles = vec4(sum, 1.0);
}

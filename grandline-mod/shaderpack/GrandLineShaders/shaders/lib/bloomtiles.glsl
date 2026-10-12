// Layout of the bloom tile atlas in colortex5: tile i holds mip level i+2 of the bright-pass, side by side on one row.
const int BLOOM_TILES = 6;

vec4 bloomTileRect(int i) {
    float pad = 12.0 / viewWidth;
    float padY = 12.0 / viewHeight;
    float x0 = pad;
    for (int j = 0; j < 6; j++) {
        if (j >= i) break;
        x0 += exp2(-float(j + 2)) + pad;
    }
    float s = exp2(-float(i + 2));
    return vec4(x0, padY, s, s);
}

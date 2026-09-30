// Gentle foliage motion (original). Only top vertices of plants move, so bases stay planted.
#ifndef EMBERVEIL_WAVING
#define EMBERVEIL_WAVING

vec3 waveOffset(vec3 worldPos, int id, bool topVertex, float skyLight) {
    vec3 o = vec3(0.0);
    float t = frameTimeCounter * WAVING_SPEED;
    // Wind is stronger in the open and during rain; nothing moves in sealed caves.
    float wind = WAVING_AMOUNT * (0.55 + 0.45 * rainStrength) * smoothstep(0.1, 0.6, skyLight);
    vec3 p = worldPos;
    float gust = 0.6 + 0.4 * sin(t * 0.37 + p.x * 0.02 + p.z * 0.03);
#ifdef WAVING_PLANTS
    if ((id == MAT_PLANT && topVertex) || id == MAT_PLANT_UPPER) {
        // Upper halves of tall plants: their bottom edge follows the lower half's tips, so no seam.
        float amp = (id == MAT_PLANT_UPPER && topVertex ? 0.14 : 0.07) * wind * gust;
        o.x += sin(t * 1.9 + p.x * 0.55 + p.z * 0.35) * amp;
        o.z += sin(t * 1.6 + p.z * 0.60 - p.x * 0.20) * amp * 0.8;
    }
    if (id == MAT_HANGING) {
        float amp = 0.04 * wind;
        o.x += sin(t * 1.3 + p.y * 0.8 + p.x * 0.4) * amp;
        o.z += sin(t * 1.1 + p.y * 0.7 + p.z * 0.4) * amp;
    }
    if (id == MAT_UNDERWATER_PLANT && topVertex) {
        float amp = 0.06 * WAVING_AMOUNT;
        o.x += sin(t * 1.1 + p.x * 0.4 + p.y * 0.3) * amp;
        o.z += sin(t * 0.9 + p.z * 0.4 + p.y * 0.2) * amp;
    }
#endif
#ifdef WAVING_LEAVES
    if (id == MAT_LEAVES) {
        float amp = 0.028 * wind * gust;
        o.x += sin(t * 1.7 + p.x * 0.9 + p.y * 0.4) * amp;
        o.y += sin(t * 2.1 + p.y * 0.8 + p.z * 0.6) * amp * 0.5;
        o.z += sin(t * 1.5 + p.z * 0.9 + p.x * 0.3) * amp;
    }
#endif
    return o;
}
#endif

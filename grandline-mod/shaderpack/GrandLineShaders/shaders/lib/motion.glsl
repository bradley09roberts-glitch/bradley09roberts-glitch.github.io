/*
 * Vertex motion for correctly classified blocks only: leaves, plants, vines, underwater plants and (optional) hanging
 * cloth. Anchored plants use Iris' at_midBlock (vertex offset from the block centre, x64) so the base never moves.
 * Logs, planks, beams and every unlisted block get no motion at all.
 */

// spatial frequencies are whole multiples of 2*pi/4096 so the field tiles with anchoredWorldPos()
const float WIND_F = 6.28318531 / 4096.0;

vec3 windWave(vec3 wp, float t, float speed) {
    float gust = 0.65 + 0.35 * sin(t * 0.37 + (wp.x * 14.0 + wp.z * 11.0) * WIND_F);
    float a = sin(t * 1.9 * speed + (wp.x * 398.0 + wp.z * 241.0) * WIND_F);
    float b = sin(t * 2.7 * speed + (wp.x * -280.0 + wp.z * 463.0) * WIND_F + 1.3);
    float c = sin(t * 1.3 * speed + wp.y * 0.9 + wp.x * 130.0 * WIND_F);
    return vec3(a + 0.4 * b, 0.25 * c, 0.6 * b - 0.3 * a) * gust;
}

// wp: anchored world position of the vertex; midBlock: at_midBlock (xyz = (blockCentre - vertex) * 64)
vec3 foliageOffset(int mat, vec3 wp, vec4 midBlock) {
    float strength = FOLIAGE_MOTION * (1.0 + rainStrength * 0.8);
    if (strength <= 0.0) return vec3(0.0);
    float t = frameTimeCounter;
    // height of the vertex inside its block: 0 at the bottom face, 1 at the top face
    float h = clamp(0.5 - midBlock.y / 64.0, 0.0, 1.0);

    if (mat == MAT_LEAVES) {
        return windWave(wp, t, 0.8) * 0.035 * strength;
    } else if (mat == MAT_PLANT) {
        vec3 w = windWave(wp, t, 1.0);
        return vec3(w.x, 0.0, w.z) * 0.085 * h * strength;
    } else if (mat == MAT_PLANT_LOWER || mat == MAT_PLANT_UPPER) {
        float hh = (mat == MAT_PLANT_UPPER ? 1.0 + h : h) * 0.5;
        vec3 w = windWave(wp - vec3(0.0, mat == MAT_PLANT_UPPER ? 1.0 : 0.0, 0.0), t, 0.9);
        return vec3(w.x, 0.0, w.z) * 0.12 * hh * strength;
    } else if (mat == MAT_VINE) {
        vec3 w = windWave(wp, t, 0.7);
        return vec3(w.x, 0.0, w.z) * 0.03 * strength;
    } else if (mat == MAT_SEAGRASS) {
        vec3 w = windWave(wp, t * 0.6, 0.6);
        return vec3(w.x, 0.0, w.z) * 0.10 * h * FOLIAGE_MOTION;
    } else if (mat == MAT_KELP) {
        // multi-block stacks (kelp, sugar cane): a continuous field so neighbouring segments never separate
        vec3 w = windWave(vec3(wp.x, 0.0, wp.z), t * 0.6, 0.6);
        return vec3(w.x, 0.0, w.z) * 0.05 * FOLIAGE_MOTION;
    }
#ifdef CLOTH_SWAY
    if (mat == MAT_CLOTH_SWAY) {
        // hanging cloth is anchored at the top of its block
        vec3 w = windWave(wp, t, 1.2);
        return vec3(w.x, 0.0, w.z) * 0.06 * (1.0 - h) * strength;
    }
#endif
    return vec3(0.0);
}

bool isMoving(int mat) {
    bool m = (mat >= MAT_LEAVES && mat <= MAT_KELP);
#ifdef CLOTH_SWAY
    m = m || mat == MAT_CLOTH_SWAY;
#endif
    return m && FOLIAGE_MOTION > 0.0;
}

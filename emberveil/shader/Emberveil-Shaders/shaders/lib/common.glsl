// Shared constants and helpers (original code, Emberveil Shaders).
#ifndef EMBERVEIL_COMMON
#define EMBERVEIL_COMMON

const float PI = 3.14159265;

// Material ids written by block.properties / entity.properties (mc_Entity.x / entityId)
#define MAT_WATER       10001
#define MAT_PLANT       10002
#define MAT_LEAVES      10003
#define MAT_HANGING     10004
#define MAT_PLANT_UPPER 10005
#define MAT_UNDERWATER_PLANT 10006
#define MAT_TORCHLIKE   10010
#define MAT_LAVA        10011
#define MAT_LIGHT_BLOCK 10012
#define MAT_FIRE        10013
#define MAT_FAINT_GLOW  10014
#define MAT_GLASS       10030
#define MAT_ICE         10031
#define ENT_EMISSIVE    20001

// Material codes stored in colortex1.g (value / 255)
#define CODE_NONE    0.0
#define CODE_WATER   1.0
#define CODE_FOLIAGE 2.0
#define CODE_HAND    3.0
#define CODE_ENTITY  4.0
#define CODE_GLASS   5.0

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 toGamma(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }
float sat(float x) { return clamp(x, 0.0, 1.0); }
float pow2(float x) { return x * x; }

// Interleaved gradient noise (Jimenez 2014) - used only for dithering / sample rotation.
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

vec3 projectAndDivide(mat4 m, vec3 p) { vec4 h = m * vec4(p, 1.0); return h.xyz / h.w; }

// Vanilla lightmap coordinates arrive in [1/32, 31/32]; remap to 0..1.
vec2 normalizeLightmap(vec2 lm) { return clamp((lm - 1.0 / 32.0) * (32.0 / 30.0), 0.0, 1.0); }

// Normal encoding for colortex2 (octahedral, RG16).
vec2 encodeNormal(vec3 n) {
    n /= (abs(n.x) + abs(n.y) + abs(n.z));
    vec2 e = n.z >= 0.0 ? n.xy : (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
    return e * 0.5 + 0.5;
}
vec3 decodeNormal(vec2 e) {
    e = e * 2.0 - 1.0;
    vec3 n = vec3(e, 1.0 - abs(e.x) - abs(e.y));
    float t = max(-n.z, 0.0);
    n.x += n.x >= 0.0 ? -t : t;
    n.y += n.y >= 0.0 ? -t : t;
    return normalize(n);
}
#endif

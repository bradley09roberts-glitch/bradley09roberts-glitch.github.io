// Shared constants and helpers. Included once per program, after settings.glsl and uniforms.glsl.

// Dimension codes (set by the stub files in each dimension folder: #define DIM n)
#define DIM_OVERWORLD 0
#define DIM_NETHER 1
#define DIM_END 2
#define DIM_SKY 3
#define DIM_DEEP 4
#define DIM_INTERIOR 5

// Dimensions that get the full sun, sky and shadow treatment
#if DIM == DIM_OVERWORLD || DIM == DIM_SKY
    #define HAS_SUN 1
#else
    #define HAS_SUN 0
#endif

// Material groups (block.properties ids; the mod's own blocks use 100 + group, see block.properties)
#define MAT_WATER 10
#define MAT_LAVA 11
#define MAT_LEAVES 20
#define MAT_PLANT 21
#define MAT_PLANT_LOWER 22
#define MAT_PLANT_UPPER 23
#define MAT_VINE 24
#define MAT_SEAGRASS 25
#define MAT_KELP 26
#define MAT_GLASS 30
#define MAT_STAINED_GLASS 31
#define MAT_ICE 32
#define MAT_BUBBLE 33
#define MAT_SLIME 34
#define MAT_METAL 40
#define MAT_PLASTER 41
#define MAT_WOOD 42
#define MAT_CLOTH 43
#define MAT_CLOTH_SWAY 44
#define MAT_POLISHED 45
#define MAT_SAND 46
#define MAT_STONE 47
#define MAT_SNOW 48
#define MAT_TILE 49
#define MAT_EMISSIVE 50
#define MAT_LIGHT_SOURCE 51
#define MAT_FIRE 52
#define MAT_PORTAL 53
#define MAT_GLOW_BODY 54
#define MAT_CLOUD 60
#define MAT_SEASTONE 61
#define MAT_PONEGLYPH 62
#define MAT_ENTITY 90
#define MAT_HAND 91

const float PI = 3.14159265;
const float TAU = 6.28318531;
const float SEA_SURFACE_Y = 63.89; // the ocean world fills water up to y 63; that block's surface sits at 63 + 8/9

// block.properties id -> material group (ids 100..199 are the grandline: copies of the same groups)
int materialGroup(float entityX) {
    int id = int(entityX + 0.5);
    if (id >= 100 && id < 200) id -= 100;
    return id;
}

float luminance(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float maxOf(vec3 c) { return max(c.r, max(c.g, c.b)); }
float sq(float x) { return x * x; }
float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3 saturate(vec3 x) { return clamp(x, 0.0, 1.0); }

vec3 toLinear(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 toGamma(vec3 c) { return pow(max(c, 0.0), vec3(1.0 / 2.2)); }

// Octahedral normal encoding into [0,1]^2
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

// Integer hash -> [0,1). Inputs are integer-valued floats.
float hash12(vec2 p) {
    uvec2 q = uvec2(ivec2(p)) * uvec2(1597334673u, 3812015801u);
    uint n = (q.x ^ q.y) * 1597334673u;
    n ^= n >> 15u;
    n *= 2246822519u;
    n ^= n >> 13u;
    return float(n) * (1.0 / 4294967296.0);
}

// Tileable value noise: 'period' is the tile size in cells (every world-anchored noise tiles at 4096 blocks)
float vnoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(mod(i, period));
    float b = hash12(mod(i + vec2(1.0, 0.0), period));
    float c = hash12(mod(i + vec2(0.0, 1.0), period));
    float d = hash12(mod(i + vec2(1.0, 1.0), period));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Interleaved gradient noise for per-pixel dithering / sample rotation
float ign(vec2 fragCoord) {
    return fract(52.9829189 * fract(dot(fragCoord, vec2(0.06711056, 0.00583715))));
}

/*
 * World position wrapped to [0,4096) on x/z, built from the exact integer camera position so world-anchored effects
 * (waves, ripples, caustics, clouds) stay precise and stable far from the origin and under camera motion. Every
 * function fed with it is periodic in 4096 blocks, so the wrap is seamless.
 */
vec3 anchoredWorldPos(vec3 playerPos) {
    ivec2 ci = cameraPositionInt.xz & 4095;
    vec2 xz = vec2(ci) + cameraPositionFract.xz + playerPos.xz;
    return vec3(xz.x, float(cameraPositionInt.y) + cameraPositionFract.y + playerPos.y, xz.y);
}

vec3 projectAndDivide(mat4 m, vec3 p) {
    vec4 h = m * vec4(p, 1.0);
    return h.xyz / h.w;
}

vec3 screenToView(vec2 uv, float depth) {
    return projectAndDivide(gbufferProjectionInverse, vec3(uv, depth) * 2.0 - 1.0);
}

vec3 viewToPlayer(vec3 viewPos) {
    return mat3(gbufferModelViewInverse) * viewPos + gbufferModelViewInverse[3].xyz;
}

// Smooth light banding used by the stylisation option: soft steps instead of a continuous ramp
float softBands(float x, float steps) {
    float s = x * steps;
    float f = fract(s);
    float stepped = floor(s) + smoothstep(0.35, 0.65, f);
    return stepped / steps;
}

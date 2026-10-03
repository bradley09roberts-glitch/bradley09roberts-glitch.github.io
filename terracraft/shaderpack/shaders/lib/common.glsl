// TerraCraft Radiance - shared settings, uniforms and helpers.

// ------------------------------------------------------------------------------------------------ settings
#define SHADOW_SAMPLES 24       // [8 16 24 32 48]
#define SHADOW_SOFTNESS 1.0     // [0.5 1.0 1.5 2.0 3.0]
#define VOLUMETRIC_LIGHT
#define VL_STEPS 32             // [16 24 32 48 64]
#define VL_STRENGTH 1.0         // [0.25 0.5 0.75 1.0 1.5 2.0]
#define WATER_REFLECTIONS
#define WAVING_PLANTS
#define CLOUDS
#define CLOUD_COVERAGE 0.50     // [0.30 0.40 0.50 0.60 0.70]
#define CLOUD_HEIGHT 330.0      // [260.0 300.0 330.0 380.0 450.0]
#define BLOOM_STRENGTH 1.0      // [0.0 0.5 1.0 1.5 2.0 3.0]
#define EXPOSURE 1.0            // [0.6 0.8 1.0 1.2 1.4 1.6 2.0]
#define SATURATION 1.15         // [0.80 0.90 1.00 1.10 1.15 1.20 1.30]
#define EMISSIVE_STRENGTH 1.0   // [0.0 0.5 1.0 2.0 3.0]
#define FOG_DENSITY 1.0         // [0.0 0.5 1.0 1.5 2.0]
#define VIGNETTE 0.25           // [0.0 0.15 0.25 0.4]

const int shadowMapResolution = 4096;   // [2048 4096 8192]
const float shadowDistance = 160.0;     // [96.0 128.0 160.0 224.0 320.0]
const float sunPathRotation = -25.0;    // [-40.0 -30.0 -25.0 -15.0 0.0 15.0 25.0]
const float ambientOcclusionLevel = 1.0;
const float shadowDistanceRenderMul = 1.0;
const float eyeBrightnessHalflife = 2.0;

// ------------------------------------------------------------------------------------------------ uniforms
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 cameraPosition;
uniform vec3 sunPosition;
uniform vec3 shadowLightPosition;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform float viewWidth;
uniform float viewHeight;
uniform float near;
uniform float far;
uniform int isEyeInWater;
uniform ivec2 eyeBrightnessSmooth;
uniform int worldTime;

const float PI = 3.14159265359;

// ------------------------------------------------------------------------------------------------ helpers
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

/** Interleaved gradient noise: a stable per-pixel dither. */
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(vec3(i, 0.0)), b = hash13(vec3(i + vec2(1, 0), 0.0));
    float c = hash13(vec3(i + vec2(0, 1), 0.0)), d = hash13(vec3(i + vec2(1, 1), 0.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise2(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        a *= 0.5;
    }
    return v;
}

/** Shadow-map distortion: more resolution close to the player. Shared by the shadow pass and every lookup. */
vec3 distortShadow(vec3 p) {
    float f = length(p.xy) * 0.9 + 0.1;
    return vec3(p.xy / f, p.z * 0.2);
}

vec3 sunDirection() { return normalize(mat3(gbufferModelViewInverse) * sunPosition); }
vec3 lightDirection() { return normalize(mat3(gbufferModelViewInverse) * shadowLightPosition); }

float dayFactor(vec3 sun) { return smoothstep(-0.12, 0.22, sun.y); }

/** Colour of direct sunlight (warm at sunrise and sunset) or moonlight. */
vec3 directLightColor(vec3 sun) {
    vec3 sunCol = mix(vec3(1.0, 0.36, 0.10), vec3(1.0, 0.93, 0.83), smoothstep(0.0, 0.42, sun.y)) * 3.3 * smoothstep(-0.04, 0.10, sun.y);
    vec3 moonCol = vec3(0.40, 0.55, 0.95) * 0.20 * smoothstep(0.0, -0.12, sun.y);
    return (sunCol + moonCol) * (1.0 - rainStrength * 0.85);
}

vec3 ambientLightColor(vec3 sun) {
    return mix(vec3(0.030, 0.042, 0.080), vec3(0.40, 0.53, 0.78), dayFactor(sun)) * (1.0 - rainStrength * 0.45);
}

const vec3 BLOCKLIGHT_COLOR = vec3(1.0, 0.60, 0.30);

/** Sky colour along a world-space direction: blue gradient, sunset glow toward the sun, halo, night. */
vec3 skyColor(vec3 dir, vec3 sun) {
    float day = dayFactor(sun);
    float up = max(dir.y, 0.0);
    vec3 zenith = mix(vec3(0.002, 0.004, 0.014), vec3(0.055, 0.17, 0.62), day);
    vec3 horizon = mix(vec3(0.014, 0.020, 0.045), vec3(0.42, 0.60, 0.92), day);
    vec3 col = mix(horizon, zenith, 1.0 - exp(-up * 3.5));
    vec2 flatDir = normalize(dir.xz + 1e-5);
    vec2 flatSun = normalize(sun.xz + 1e-5);
    float toward = pow(max(dot(flatDir, flatSun), 0.0), 2.0);
    // golden hour: a deep orange band hugging the horizon, strongest toward the sun, with a rose glow above it
    float sunset = smoothstep(0.42, 0.0, abs(sun.y + 0.02));
    col *= mix(1.0, 0.55, sunset * (1.0 - up));
    vec3 band = mix(vec3(1.0, 0.20, 0.02), vec3(1.0, 0.42, 0.06), toward);
    vec3 above = vec3(0.80, 0.30, 0.42);
    float bandW = exp(-up * 5.0) * (0.35 + 0.65 * toward);
    float aboveW = exp(-up * 2.2) * 0.45;
    col = mix(col, above * (0.35 + 0.35 * day), sunset * aboveW);
    col = mix(col, band * (0.45 + 0.75 * toward), sunset * bandW);
    float cosT = dot(dir, sun);
    col += vec3(1.0, 0.70, 0.40) * pow(max(cosT, 0.0), 8.0) * 0.45 * day;
    col += vec3(1.0, 0.82, 0.60) * pow(max(cosT, 0.0), 150.0) * 1.8 * day;
    col *= mix(1.0, 0.35, smoothstep(0.0, -0.35, dir.y));
    col = mix(col, vec3(luma(col)) * 0.55, rainStrength * 0.8);
    return col * 1.25;
}

/** ACES filmic tone mapping (Narkowicz fit). */
vec3 acesFilm(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

/** Octahedral normal encoding into [0, 1]^2. */
vec2 encodeNormal(vec3 n) {
    n /= abs(n.x) + abs(n.y) + abs(n.z);
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

// block ids from block.properties
const int ID_LEAVES = 10001;
const int ID_PLANT = 10002;
const int ID_WATER = 10003;
const int ID_EMISSIVE = 10004;
const int ID_ORE = 10005;
const int ID_GLASS = 10006;

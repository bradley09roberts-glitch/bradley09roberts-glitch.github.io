/*
 * Grand Line Shaders - user options.
 * Iris builds the option screen from the value lists in the trailing comments; the screen layout, sliders and
 * profiles live in shaders.properties, names and tooltips in lang/en_us.lang. Defaults = the BALANCED profile.
 */

// ---------------------------------------------------------------- shadows
const int shadowMapResolution = 2048; // [1024 2048 3072 4096]
const float shadowDistance = 128.0; // [64.0 80.0 96.0 128.0 160.0 192.0 224.0 256.0]
const float shadowDistanceRenderMul = 1.0;
const float entityShadowDistanceMul = 0.5;
const float sunPathRotation = -20.0; // [-40.0 -30.0 -20.0 -10.0 0.0 10.0 20.0 30.0 40.0]
const bool shadowHardwareFiltering1 = true;
#define SHADOW_SAMPLES 8 // [4 8 12 16 24]
#define SHADOW_SOFTNESS 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define SHADOW_DISTORTION 0.85 // [0.75 0.80 0.85 0.90]

// ---------------------------------------------------------------- water
#define WAVE_STRENGTH 1.0 // [0.0 0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#define WAVE_SPEED 1.0 // [0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#define WATER_DISPLACEMENT
#define WATER_TRANSPARENCY 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define WATER_REFRACTION 1.0 // [0.0 0.5 1.0 1.5 2.0]
#define REFLECTION_QUALITY 1 // [0 1 2]
#define SSR_STEPS 24 // [12 16 24 32 48]
#define FOAM_INTENSITY 1.0 // [0.0 0.25 0.5 0.75 1.0 1.25 1.5]
#define CAUSTICS
#define UNDERWATER_FOG 1.0 // [0.5 0.75 1.0 1.25 1.5]

// ---------------------------------------------------------------- lighting
#define SUN_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5]
#define AMBIENT_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define BLOCKLIGHT_STRENGTH 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define MIN_LIGHT 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]
#define EMISSIVE_STRENGTH 1.0 // [0.0 0.5 1.0 1.5 2.0 3.0]
#define SPECULAR_STRENGTH 1.0 // [0.0 0.5 1.0 1.5 2.0]
#define HANDHELD_LIGHT

// ---------------------------------------------------------------- sky
#define CLOUD_MODE 2 // [0 1 2]
#define CLOUD_COVERAGE 0.45 // [0.2 0.3 0.45 0.6 0.75]
#define HAZE_STRENGTH 1.0 // [0.0 0.5 0.75 1.0 1.5 2.0]
#define STARS

// ---------------------------------------------------------------- motion
#define FOLIAGE_MOTION 1.0 // [0.0 0.5 1.0 1.5 2.0]
//#define CLOTH_SWAY

// ---------------------------------------------------------------- image
#define BLOOM
#define BLOOM_STRENGTH 0.25 // [0.05 0.1 0.15 0.25 0.35 0.5 0.75 1.0]
#define EXPOSURE 1.0 // [0.5 0.6 0.7 0.8 0.9 1.0 1.1 1.2 1.35 1.5 1.75 2.0]
#define SATURATION 1.10 // [0.8 0.9 1.0 1.05 1.10 1.15 1.2 1.3 1.4]
#define CONTRAST 1.05 // [0.9 1.0 1.05 1.1 1.15 1.2]
#define STYLISATION 0.15 // [0.0 0.1 0.15 0.25 0.35 0.5 0.75 1.0]
#define OUTLINE_STRENGTH 0.0 // [0.0 0.15 0.3 0.5]
#define VIGNETTE 0.20 // [0.0 0.1 0.2 0.3 0.4]

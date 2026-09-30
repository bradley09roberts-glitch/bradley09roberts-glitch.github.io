// ============================================================================
//  Emberveil Shaders - user options
//  Values in [brackets] become choices in the Iris shader-options menu.
//  Profiles (Performance / Balanced / High) are defined in shaders.properties;
//  the defaults below ARE the Balanced profile.
// ============================================================================

// ---- Shadows -----------------------------------------------------------------
const int   shadowMapResolution = 2048;   // [1024 1536 2048 3072 4096]
const float shadowDistance = 128.0;       // [64.0 80.0 96.0 128.0 160.0 192.0 256.0]
const float shadowDistanceRenderMul = 1.0; // [-1.0 1.0]
const bool  shadowHardwareFiltering = true;
const float sunPathRotation = -25.0;      // [-40.0 -30.0 -25.0 -20.0 -10.0 0.0 10.0 20.0 30.0]
const float ambientOcclusionLevel = 1.0;  // [0.0 0.25 0.5 0.75 1.0]

#define SHADOW_FILTER 1          // [0 1 2] 0 = 4 taps, 1 = 12-tap Poisson, 2 = soft penumbra (24 taps)
#define SHADOW_SOFTNESS 1.0      // [0.5 0.75 1.0 1.5 2.0]
//#define COLORED_SHADOWS        // Stained glass tints sunlight (High profile)

// ---- Lighting ----------------------------------------------------------------
#define SUN_BRIGHTNESS 1.0       // [0.6 0.8 1.0 1.2 1.4]
#define NIGHT_BRIGHTNESS 1.0     // [0.5 0.75 1.0 1.25 1.5 2.0]
#define MIN_LIGHT 1.0            // [0.0 0.5 1.0 1.5 2.0 3.0] Cave floor light (also follows the in-game Brightness slider)
#define TORCH_WARMTH 1.0         // [0.0 0.5 1.0 1.5]
#define BLOCKLIGHT_STRENGTH 1.0  // [0.6 0.8 1.0 1.2 1.5]
#define HANDHELD_LIGHT           // Held torches/lanterns light the area around you
#define EMISSIVE_BLOCKS          // Torches, lava, glowstone etc. glow and feed bloom

// ---- Atmosphere --------------------------------------------------------------
#define FOG_DENSITY 1.0          // [0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#define VALLEY_MIST              // Low-lying morning/evening and rain mist (the "veil")
#define MIST_STRENGTH 1.0        // [0.5 0.75 1.0 1.5 2.0]
//#define VOLUMETRIC_LIGHT       // Light shafts through fog (High profile)
#define VL_STRENGTH 1.0          // [0.5 0.75 1.0 1.5 2.0]
#define VL_STEPS 10              // [6 8 10 12 16 24]

// ---- Water -------------------------------------------------------------------
#define WATER_WAVES
#define WAVE_STRENGTH 1.0        // [0.25 0.5 0.75 1.0 1.5 2.0]
#define WATER_REFLECTIONS 1      // [0 1 2] 0 = sky tint only, 1 = sky + sun glints, 2 = + screen-space reflections
#define WATER_ALPHA 0.62         // [0.4 0.5 0.62 0.75 0.85]
#define UNDERWATER_CLARITY 1.0   // [0.5 0.75 1.0 1.5 2.0]

// ---- Foliage -----------------------------------------------------------------
#define WAVING_PLANTS
#define WAVING_LEAVES
#define WAVING_SPEED 1.0         // [0.5 0.75 1.0 1.5 2.0]
#define WAVING_AMOUNT 1.0        // [0.25 0.5 0.75 1.0 1.5]

// ---- Post --------------------------------------------------------------------
#define BLOOM
#define BLOOM_STRENGTH 1.0       // [0.0 0.25 0.5 0.75 1.0 1.25 1.5 2.0]
#define BLOOM_QUALITY 1          // [0 1 2] number of blur levels gathered (4 / 6 / 7)
#define EXPOSURE 1.0             // [0.6 0.7 0.8 0.9 1.0 1.1 1.2 1.4 1.6]
#define SATURATION 1.06          // [0.8 0.9 1.0 1.06 1.12 1.2]
#define CONTRAST 1.04            // [0.9 0.96 1.0 1.04 1.08 1.12]
#define GRADE_STRENGTH 1.0       // [0.0 0.5 1.0 1.5] Ember highlights / cool shadows split-tone
#define VIGNETTE 0.18            // [0.0 0.1 0.18 0.3 0.45]

// ---- Deliberately absent -----------------------------------------------------
// No motion blur, film grain, chromatic aberration or depth of field are
// implemented in this pack: readability first.

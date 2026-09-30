// Uniforms supplied by Iris (names verified against Iris 1.8.12). Unused ones are optimised away.
#ifndef EMBERVEIL_UNIFORMS
#define EMBERVEIL_UNIFORMS
uniform mat4 gbufferModelView;
uniform mat4 gbufferModelViewInverse;
uniform mat4 gbufferProjection;
uniform mat4 gbufferProjectionInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform mat4 shadowProjection;
uniform mat4 shadowProjectionInverse;
uniform vec3 cameraPosition;
uniform vec3 sunPosition;
uniform vec3 moonPosition;
uniform vec3 shadowLightPosition;
uniform vec3 upPosition;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform vec4 entityColor;
uniform float sunAngle;
uniform float rainStrength;
uniform float wetness;
uniform float frameTimeCounter;
uniform float viewWidth;
uniform float viewHeight;
uniform float near;
uniform float far;
uniform float nightVision;
uniform float blindness;
uniform float darknessFactor;
uniform float darknessLightFactor;
uniform float screenBrightness;
uniform float eyeAltitude;
uniform float alphaTestRef;
uniform int isEyeInWater;
uniform int worldTime;
uniform int moonPhase;
uniform int frameCounter;
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
uniform int entityId;
uniform int blockEntityId;
uniform int currentRenderedItemId;
uniform int renderStage;
uniform ivec2 eyeBrightnessSmooth;
#endif

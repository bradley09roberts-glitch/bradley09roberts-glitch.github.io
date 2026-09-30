// Unlit / special geometry: basic (lines, leads), textured fallback, spider eyes, beacon beams,
// enchantment glint, rain/snow, sun & moon, clouds. Original code, Emberveil Shaders.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"

#ifdef VSH
out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 viewPos;
out vec3 normalV;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
    glcolor = gl_Color;
    normalV = normalize(gl_NormalMatrix * gl_Normal);
    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
    viewPos = vp.xyz;
    gl_Position = gl_ProjectionMatrix * vp;
}
#endif

#ifdef FSH
uniform sampler2D gtexture;
in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 viewPos;
in vec3 normalV;

/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outMaterial;

void main() {
#ifdef PROGRAM_BASIC
    // Block outline, leads, debug lines: keep vanilla colour so selection stays readable.
    vec4 c = glcolor;
    if (c.a < 0.01) discard;
    outColor = vec4(toLinear(c.rgb), c.a);
    outMaterial = vec4(0.0, CODE_NONE, 1.0, c.a);
#else
    vec4 tex = texture(gtexture, texcoord);
    vec4 base = tex * glcolor;
    if (base.a < 0.004) discard;
    vec3 lin = toLinear(base.rgb);
    float emissive = 0.0;
    float code = CODE_NONE;

#if defined PROGRAM_EYES
    // Spider / enderman / phantom eyes glow.
    lin *= 2.2; emissive = 0.9;
#elif defined PROGRAM_BEACON
    lin *= 2.6; emissive = 1.0;
#elif defined PROGRAM_GLINT
    lin *= 1.1;
#elif defined PROGRAM_WEATHER
    // Rain and snow pick up the ambient light so they are visible at night but not glaring.
    vec3 amb = ambientSkyColor() * 1.6 + blockLightColor(lmcoord.x) + minimumLight();
    lin = mix(lin, vec3(luma(lin)), 0.4) * amb;
    base.a *= 0.7;
#elif defined PROGRAM_SKYTEXTURED
    float e = sunElevation();
    if (renderStage == MC_RENDER_STAGE_SUN) {
        lin *= mix(vec3(1.0, 0.55, 0.3), vec3(1.0, 0.95, 0.85), smoothstep(0.0, 0.3, e)) * 7.0 * (1.0 - rainStrength);
        emissive = 1.0;
    } else if (renderStage == MC_RENDER_STAGE_MOON) {
        lin *= vec3(0.8, 0.9, 1.1) * 1.4 * NIGHT_BRIGHTNESS * (1.0 - rainStrength);
        emissive = 0.3;
    } else {
        lin *= 1.5; // custom sky textures from resource packs
    }
#elif defined PROGRAM_CLOUDS
    // Clouds catch the sun: bright tops by day, warm at dusk, dim silver at night.
    vec3 n = normalize(normalV);
    float NdotL = dot(n, normalize(shadowLightPosition));
    vec3 light = directLightColor() * (0.55 + 0.45 * max(NdotL, 0.0)) + ambientSkyColor() * 1.4;
    lin = vec3(luma(lin)) * light * 0.9;
    base.a *= 0.92;
    code = 6.0;
#else
    // Generic textured fallback: light with the lightmap only.
    lin *= ambientSkyColor() * pow(lmcoord.y, 2.0) + blockLightColor(lmcoord.x) + minimumLight() + directLightColor() * 0.4 * lmcoord.y;
#endif
    outColor = vec4(lin, base.a);
    outMaterial = vec4(emissive, code / 255.0, lmcoord.y, base.a);
#endif
}
#endif

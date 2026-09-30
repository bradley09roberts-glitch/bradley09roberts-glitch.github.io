// Translucent terrain: water (waves, fresnel sky reflection, sun glints) and glass/ice/portals.
// Also used for translucent held items (hand_water). Original code, Emberveil Shaders.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"

#if !defined DIM_NETHER && !defined DIM_END
#define SHADOW_SAMPLING
#endif

#include "/lib/sky.glsl"
#include "/lib/lighting.glsl"
#include "/lib/water.glsl"

#ifdef VSH
in vec4 mc_Entity;
out vec2 texcoord;
out vec2 lmcoord;
out vec4 glcolor;
out vec3 normalV;
out vec3 viewPos;
flat out int matId;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = normalizeLightmap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
    glcolor = gl_Color;
    normalV = normalize(gl_NormalMatrix * gl_Normal);
#ifdef PROGRAM_HAND_WATER
    matId = 0;
#else
    matId = int(mc_Entity.x + 0.5);
#endif
    vec4 vp = gl_ModelViewMatrix * gl_Vertex;
    viewPos = vp.xyz;
    gl_Position = gl_ProjectionMatrix * vp;
}
#endif

#ifdef FSH
#include "/lib/shadows.glsl"
uniform sampler2D gtexture;
in vec2 texcoord;
in vec2 lmcoord;
in vec4 glcolor;
in vec3 normalV;
in vec3 viewPos;
flat in int matId;

/* RENDERTARGETS: 0,1,2 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outMaterial;
layout(location = 2) out vec4 outNormal;

void main() {
    vec4 tex = texture(gtexture, texcoord);
    vec4 base = tex * glcolor;
    vec3 n = normalize(normalV);
    vec3 V = normalize(-viewPos);

    if (matId == MAT_WATER) {
        vec3 playerPos = mat3(gbufferModelViewInverse) * viewPos + gbufferModelViewInverse[3].xyz;
        vec3 worldPos = playerPos + cameraPosition;
        vec3 nWorldGeo = mat3(gbufferModelViewInverse) * n;
#ifdef WATER_WAVES
        if (abs(nWorldGeo.y) > 0.5) {
            vec3 wn = waterNormal(worldPos.xz, frameTimeCounter * 0.9, 1.0);
            wn.y *= sign(nWorldGeo.y);
            n = normalize(mat3(gbufferModelView) * wn);
        }
#endif
        float NdotV = max(dot(n, V), 0.0);
        float fres = fresnelSchlick(NdotV, 0.02);
        vec3 tint = toLinear(glcolor.rgb);                  // biome water colour
        vec3 L = normalize(shadowLightPosition);
        vec3 shadowVis = vec3(1.0);
#ifdef SHADOW_SAMPLING
        shadowVis = sampleShadow(viewPos, normalize(normalV), max(dot(normalize(normalV), L), 0.0));
#endif
        // The water body scatters a little of the incoming light in its own colour.
        vec3 bodyLight = shadeSurface(tint * 0.16 + vec3(0.004, 0.010, 0.012), n, viewPos, lmcoord, shadowVis, 0.0, 0.0);
        vec3 col;
        float alpha;
        if (isEyeInWater == 1) {
            // Seen from below: mostly transmission, tinted.
            col = bodyLight * 1.3;
            alpha = 0.45;
        } else {
            vec3 R = reflect(-V, n);
            vec3 refl = skyRadiance(R) * pow(lmcoord.y, 3.0) + blockLightColor(lmcoord.x) * 0.25;
#if WATER_REFLECTIONS >= 1
            // Sun (or moon) glints: a tight specular lobe, shadowed.
            float spec = pow(max(dot(R, L), 0.0), 380.0) * 7.0 + pow(max(dot(R, L), 0.0), 60.0) * 0.25;
            refl += directLightColor() * spec * shadowVis * smoothstep(0.1, 0.6, lmcoord.y);
#endif
            col = mix(bodyLight, refl, fres);
            alpha = mix(WATER_ALPHA, 1.0, fres * 0.85);
        }
        outColor = vec4(col, alpha);
        outMaterial = vec4(0.0, CODE_WATER / 255.0, lmcoord.y, 1.0);
        outNormal = vec4(encodeNormal(n), 0.0, 1.0);
        return;
    }

    // Stained glass, ice, slime, honey, nether portal, translucent items.
    if (base.a < 0.004) discard;
    vec3 albedo = toLinear(base.rgb);
    float emission = 0.0;
    if (matId == 10041) emission = 1.2 + luma(base.rgb);   // nether portal glows
    vec3 shadowVis = vec3(1.0);
#ifdef SHADOW_SAMPLING
    float NdotL = dot(n, normalize(shadowLightPosition));
    if (NdotL > 0.0 && lmcoord.y > 0.02) shadowVis = sampleShadow(viewPos, n, NdotL);
#endif
    vec3 col = shadeSurface(albedo, n, viewPos, lmcoord, shadowVis, 0.0, emission);
    // A faint sky reflection on glass so windows read as glass.
    float fres = fresnelSchlick(max(dot(n, V), 0.0), 0.04);
    col = mix(col, skyRadiance(reflect(-V, n)) * pow(lmcoord.y, 3.0), fres * 0.35);
    outColor = vec4(col, base.a);
    outMaterial = vec4(sat(emission * 0.45), CODE_GLASS / 255.0, lmcoord.y, 1.0);
    outNormal = vec4(encodeNormal(n), 0.0, 1.0);
}
#endif

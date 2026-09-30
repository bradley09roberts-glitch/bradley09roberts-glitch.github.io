// composite: fog / mist / underwater, optional light shafts and screen-space water reflections.
// Original code, Emberveil Shaders.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/sky.glsl"
#include "/lib/shadows.glsl"
#include "/lib/water.glsl"
#include "/lib/fog.glsl"

#ifdef VSH
out vec2 texcoord;
void main() {
    texcoord = gl_MultiTexCoord0.xy;
    gl_Position = ftransform();
}
#endif

#ifdef FSH
/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA8;
const int colortex2Format = RGBA16;
const int colortex3Format = RGBA16F;
*/
uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
#if defined VOLUMETRIC_LIGHT && !defined DIM_NETHER && !defined DIM_END
uniform sampler2DShadow shadowtex1;
#endif
in vec2 texcoord;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 outColor;

#if WATER_REFLECTIONS == 2
// Screen-space reflection for water: march the reflected ray against opaque depth.
vec3 waterSSR(vec3 color, vec3 viewPos, vec3 n, float skyLight) {
    vec3 V = normalize(viewPos);
    vec3 R = reflect(V, n);
    if (R.z > -0.02) return color;
    float fres = fresnelSchlick(max(dot(-V, n), 0.0), 0.02);
    float stepLen = 0.6 + length(viewPos) * 0.035;
    vec3 p = viewPos + R * stepLen * ign(gl_FragCoord.xy);
    for (int i = 0; i < 28; i++) {
        p += R * stepLen;
        stepLen *= 1.07;
        vec3 sc = projectAndDivide(gbufferProjection, p) * 0.5 + 0.5;
        if (any(lessThan(sc.xy, vec2(0.0))) || any(greaterThan(sc.xy, vec2(1.0)))) break;
        float d = texture(depthtex1, sc.xy).r;
        if (d >= 1.0) continue;
        vec3 hitView = projectAndDivide(gbufferProjectionInverse, vec3(sc.xy, d) * 2.0 - 1.0);
        float diff = hitView.z - p.z;                    // positive when geometry is in front of the ray
        if (diff > 0.0 && diff < stepLen * 2.5) {
            vec2 edge = smoothstep(vec2(0.0), vec2(0.08), sc.xy) * smoothstep(vec2(0.0), vec2(0.08), 1.0 - sc.xy);
            vec3 hit = texture(colortex0, sc.xy).rgb;
            vec3 skyRefl = skyRadiance(R) * pow(skyLight, 3.0);
            return color + (hit - skyRefl) * fres * edge.x * edge.y * 0.9;
        }
    }
    return color;
}
#endif

#if defined VOLUMETRIC_LIGHT && !defined DIM_NETHER && !defined DIM_END
// Light shafts: single-scattering march through the shadow map (Henyey-Greenstein phase).
vec3 lightShafts(vec3 viewPos) {
    float e = sunElevation();
    vec3 lightCol = directLightColor();
    if (dot(lightCol, vec3(1.0)) < 0.001 || isEyeInWater > 1) return vec3(0.0);
    float maxDist = min(length(viewPos), min(shadowDistance * 0.9, far));
    vec3 dirP = mat3(gbufferModelViewInverse) * normalize(viewPos);
    vec3 origin = gbufferModelViewInverse[3].xyz;
    float dither = ign(gl_FragCoord.xy + float(frameCounter % 16) * 13.0);
    float lit = 0.0;
    for (int i = 0; i < VL_STEPS; i++) {
        float t = (float(i) + dither) / float(VL_STEPS);
        vec3 p = origin + dirP * (t * t * maxDist);      // denser samples near the camera
        vec3 sp = (shadowProjection * vec4((shadowModelView * vec4(p, 1.0)).xyz, 1.0)).xyz;
        sp = distortShadow(sp) * 0.5 + 0.5;
        lit += texture(shadowtex1, vec3(sp.xy, sp.z - 0.0005));
    }
    lit /= float(VL_STEPS);
    float g = 0.65;
    float cosT = dot(normalize(viewPos), normalize(shadowLightPosition));
    float phase = (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * cosT, 1.5));
    float eyeSky = float(eyeBrightnessSmooth.y) / 240.0;
    float density = (0.0012 + 0.004 * duskFactor(e) + 0.004 * rainStrength) * FOG_DENSITY;
    float amount = lit * (1.0 - exp(-maxDist * density)) * phase * 4.0 * VL_STRENGTH * mix(0.35, 1.0, eyeSky);
    if (isEyeInWater == 1) amount *= 0.4;
    return lightCol * amount;
}
#endif

void main() {
    vec3 color = texture(colortex0, texcoord).rgb;
    float depth = texture(depthtex0, texcoord).r;
    vec4 mat = texture(colortex1, texcoord);
    int code = int(mat.g * 255.0 + 0.5);
    vec3 viewPos = projectAndDivide(gbufferProjectionInverse, vec3(texcoord, depth) * 2.0 - 1.0);
    bool isSky = depth >= 1.0;

#if WATER_REFLECTIONS == 2
    if (code == int(CODE_WATER) && isEyeInWater == 0) {
        vec3 n = decodeNormal(texture(colortex2, texcoord).rg);
        color = waterSSR(color, viewPos, n, mat.b);
    }
#endif

    if (code == 6) {
        // Clouds: gentle haze only, never swallowed by the render-distance edge fog.
        float f = 1.0 - exp(-length(viewPos) * 0.0012 * FOG_DENSITY * (1.0 + 2.0 * rainStrength));
        color = mix(color, skyRadiance(normalize(viewPos)), sat(f) * 0.7);
    } else {
        color = applyFog(color, viewPos, isSky);
    }

#if defined VOLUMETRIC_LIGHT && !defined DIM_NETHER && !defined DIM_END
    if (isEyeInWater == 0 || isEyeInWater == 1) color += lightShafts(isSky ? normalize(viewPos) * far : viewPos);
#endif
    outColor = vec4(color, 1.0);
}
#endif

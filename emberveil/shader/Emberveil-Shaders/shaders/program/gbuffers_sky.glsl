// Sky dome (gbuffers_skybasic): per-pixel gradient, dusk glow, vanilla stars kept. Original code.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"
#include "/lib/uniforms.glsl"
#include "/lib/sky.glsl"

#ifdef VSH
out vec4 glcolor;
void main() {
    glcolor = gl_Color;
    gl_Position = ftransform();
}
#endif

#ifdef FSH
in vec4 glcolor;
/* RENDERTARGETS: 0,1 */
layout(location = 0) out vec4 outColor;
layout(location = 1) out vec4 outMaterial;

void main() {
    if (renderStage == MC_RENDER_STAGE_STARS) {
        float night = 1.0 - dayFactor(sunElevation());
        outColor = vec4(toLinear(glcolor.rgb) * 2.2 * night * (1.0 - rainStrength), glcolor.a);
        outMaterial = vec4(0.0);
        return;
    }
    // The vanilla sunrise fan is replaced by the glow computed in skyRadiance().
    if (renderStage == MC_RENDER_STAGE_SUNSET) discard;
#if defined DIM_END
    outColor = vec4(vec3(0.03, 0.02, 0.05), 1.0);
#elif defined DIM_NETHER
    outColor = vec4(toLinear(fogColor), 1.0);
#else
    vec3 ndc = vec3(gl_FragCoord.xy / vec2(viewWidth, viewHeight), 1.0) * 2.0 - 1.0;
    vec3 dirV = normalize(projectAndDivide(gbufferProjectionInverse, ndc));
    outColor = vec4(skyRadiance(dirV), 1.0);
#endif
    outMaterial = vec4(0.0);
}
#endif

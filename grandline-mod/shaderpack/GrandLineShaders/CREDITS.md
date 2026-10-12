# Credits

Grand Line Shaders was written from scratch for Grand Line Odyssey. No code was copied, renamed or adapted from
another shader pack, and the pack contains no third-party source files or textures.

Standard, widely published techniques and formulas are used in our own implementation; they are listed here so the
origin of each idea is clear:

| Technique | Where | Origin |
|---|---|---|
| Octahedral normal encoding | `lib/common.glsl` | Cigolle et al., "A Survey of Efficient Representations for Independent Unit Vectors", JCGT 2014 |
| Interleaved gradient noise (constants 0.06711056, 0.00583715, 52.9829189) | `lib/common.glsl` | Jorge Jimenez, "Next Generation Post Processing in Call of Duty: Advanced Warfare", SIGGRAPH 2014 |
| Schlick's Fresnel approximation | `lib/lighting.glsl` | Christophe Schlick, 1994 |
| Normalised Blinn-Phong highlight ((n+8)/8pi) | `lib/lighting.glsl` | common real-time rendering practice (e.g. Akenine-Moller et al., Real-Time Rendering) |
| Golden-angle (Vogel) disc for shadow filtering | `lib/shadows.glsl` | Helmut Vogel, 1979 |
| Radial shadow-map distortion | `lib/shadows.glsl` | the general idea used by OptiFine/Iris shader packs; our own formula and bias |
| Deep-water wave dispersion (w = sqrt(g k)) | `lib/waves.glsl` | textbook linear wave theory |

The Iris shader-pack format (program names, `shaders.properties`, `block.properties`, `dimension.properties`, buffer
directives) follows the Iris documentation and the Iris source code (LGPL-3.0), which were read for reference only.

Minecraft is a trademark of Mojang Studios. Iris and Sodium are separate projects by their authors and are not
included in this pack.

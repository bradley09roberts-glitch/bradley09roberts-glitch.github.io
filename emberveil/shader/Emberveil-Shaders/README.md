# Emberveil Shaders 1.0.0

Original shader pack made for the Emberveil modpack (Minecraft 1.21.1, Iris 1.8.x).

Look: warm golden-hour sunlight, cool blue shadows, ember-orange torchlight, low valley mist at dawn,
dusk and in rain, clear teal water with sun glints, and restrained bloom on glowing blocks.
Readability comes first: caves keep a floor light that follows the Brightness slider, and nights
are dark but never black. There is no motion blur, film grain, chromatic aberration or depth of field.

## Install
Put this ZIP in `.minecraft/shaderpacks/` (the Emberveil modpack already does). In game: Options >
Video Settings > Shader Packs (or press **O**), select **Emberveil-Shaders**, Apply. Press **K** to
toggle shaders on/off. Profiles: Shader Pack Settings > Profile (Performance / Balanced / High).

## Profiles
| | Performance | Balanced (default) | High |
|---|---|---|---|
| Shadow map | 1024 | 2048 | 3072 |
| Shadow distance | 80 | 128 | 192 |
| Shadow filter | 4 taps | 12 taps | 24 taps |
| Tinted glass shadows | off | off | on |
| Light shafts | off | off | on (12 steps) |
| Water reflections | sky + sun glints | sky + sun glints | + screen-space |
| Bloom levels | 4 | 6 | 7 |

## Features
Directional shadows with Poisson PCF and distance fade; sun/moon light that changes smoothly through
the day (moon brightness follows the moon phase); per-pixel sky with dusk glow; aerial-perspective fog
and height-based valley mist; rain dims the sun, flattens light and thickens haze; underwater absorption
and fog; lava/powder-snow murk; blindness/darkness handled; waving grass, flowers, crops, leaves, vines
and seagrass (plant bases stay put, shadows match); water waves, fresnel reflection, sun glints;
emissive torches, lava, glowstone, fire, froglights, glow berries and glowing mob parts with bloom;
held-light illumination; End portal starfield; Nether/End specific lighting and fog; extended-Reinhard
tone mapping with a split-tone "ember" grade.

Not implemented: ray tracing, path tracing or global illumination, parallax/PBR material support.

# TerraCraft Radiance (shader pack)

A shader pack made for TerraCraft, in the standard Iris/OptiFine format (`shaders/` folder).

**Lighting and shadows**
- Soft sun/moon shadows (Vogel-disk filtering, up to 8192 shadow maps).
- Stained glass casts coloured light, and water tints the light under it.
- God rays: volumetric light marched through the shadow map, warm by day and blue by moonlight.
- Glow for light sources and TerraCraft's glowing blocks (Hellstone, Life Crystals, Shadow Orbs, Crimson Hearts, Meteorite, the Hellforge and Hardmode forges, Larva). Ores sparkle.
- Waving leaves and grass, and backlit foliage that glows when the sun is behind it.

**Sky and water**
- Its own sky: blue gradient, golden-hour band and rose dusk, sun halo, twinkling stars, moon, and drifting cumulus clouds with shaded undersides.
- Water with animated waves, screen-space reflections (sky fallback), Fresnel and a sun glint. Underwater is a blue-green murk.

**Camera**
- Bloom, eye adaptation (caves and nights brighten), ACES filmic tone mapping, saturation and vignette.

## Install

1. A shader loader is required. TerraCraft runs on Forge, so use **Iris for Forge** for Minecraft 26.2. Official Iris supports Fabric/NeoForge only.
2. Put `TerraCraft-Radiance-Shaders.zip` (unextracted) in `.minecraft/shaderpacks/`.
3. In game: Options → Video Settings → Shader Packs → select it.
4. Profiles are under Shader Settings: Medium / High (default) / Ultra / Extreme. Extreme is meant for an RTX 4090/5090: 8192 shadows, 320-block shadow distance, 48 shadow samples, 64 god-ray steps.

Every effect can be tuned or switched off in Shader Settings (Lighting & Shadows, Sky & Atmosphere, Water, Camera & Colour).

## Developing

- `DISPLAY=:99 python3 tools/test/check_shaders.py` compiles and links every program, with each optional feature on and off.
- `tools/test/preview_sky.py out.png` renders the sky and post-processing at several times of day without Minecraft.

Block ids live in `shaders/block.properties` and are named in `shaders/lib/common.glsl`.

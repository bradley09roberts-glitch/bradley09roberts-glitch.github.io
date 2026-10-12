# Grand Line Shaders

An original shader pack for **Grand Line Odyssey** (mod id `grandline`), written for Iris on NeoForge 1.21.1.
The look is "realistic but cartoony": bright, rich, cinematic colour in the spirit of an animated adventure, with
readable Minecraft geometry. Saturated roofs, painted walls and blue water keep their colour through the tone curve;
there are no outlines by default.

- Pack source: `grandline-mod/shaderpack/GrandLineShaders/` (the folder itself is a working pack)
- Installable zip: `grandline-mod/dist/GrandLineShaders-1.0.0.zip` (built by `python3 tools/pack_shaders.py`)
- Evidence screenshots: `grandline-mod/docs/shaders/`

## Install

1. Install NeoForge 21.1.x for Minecraft 1.21.1 and the Grand Line Odyssey mod.
2. Download **Sodium** and **Iris** for NeoForge 1.21.1 and put both jars in `mods/`
   (they are not redistributed with this pack):
   - Sodium 0.6.13: <https://modrinth.com/mod/sodium/version/Pb3OXVqC>
     (`sodium-neoforge-0.6.13+mc1.21.1.jar`)
   - Iris 1.8.12: <https://modrinth.com/mod/iris/version/t3ruzodq>
     (`iris-neoforge-1.8.12+mc1.21.1.jar`)
3. Put `GrandLineShaders-1.0.0.zip` in the game's `shaderpacks/` folder (do not unzip it).
4. In game: Options → Video Settings → Shader Packs → select **GrandLineShaders-1.0.0.zip** → Apply.
   "Shader Pack Settings" opens the option screen described below.

Which Iris goes with which Sodium: Iris 1.8.12 for NeoForge 1.21.1 declares Sodium `mc1.21.1-0.6.13-neoforge` as its
required dependency on Modrinth; the jar's `neoforge.mods.toml` asks for Sodium `[0.6,)` and its message says
"Please install Sodium 0.6". The newer Sodium 0.8.x line for 1.21.1 pairs with Iris 1.8.14-beta.1 ("Updates to Sodium
0.8"), which was not tested here.

## Features

### Water (the priority)
- **World-space waves**: three long, slow swell trains (45–62 blocks) and four shorter chop trains (8–15 blocks) with
  sharpened crests, plus two drifting octaves of ripple noise. All of them are anchored to the world through Iris'
  exact integer camera position and tile every 4096 blocks, so they stay stable when the camera moves or turns and stay
  precise 50,000 blocks from the origin.
- **Animated normals** from the analytic wave slopes plus the ripples, softened with distance to avoid shimmer.
- **Fresnel reflections** of the sky (gradient, sun/moon glow and the painted clouds) with a stable fallback:
  reflections never point below the horizon, and water with little sky light (a pool under a roof) reflects its dim
  surroundings instead of a bright sky. **Screen-space reflections** of docks, ships and islands on the High profile
  only, with guards: the ray must stay on screen, hits must be real geometry (not sky, not the water itself), hits
  below the water surface are rejected, and the result fades toward screen edges and the end of the ray.
- **Refraction** that bends the view of the sea floor along the wave normal, scaled by water thickness; the offset is
  only used when the shifted pixel is still water, still behind the surface and on screen, otherwise it falls back to
  the unshifted pixel (no bleeding of things above the water into it).
- **Depth colour**: light is absorbed per channel with the water thickness between the surface and the terrain behind
  it (Beer-Lambert), and scattered light goes from readable turquoise over sand to a rich offshore blue with depth.
  The biome water colour tints it slightly.
- **Shore and contact foam** from the vertical depth of water at that pixel: a thin, noisy, pulsing band where water
  meets a beach, a post or a hull; objects deeper than about half a block below the surface get none, and foam fades
  out beyond ~30–100 blocks. Wave-crest foam appears only in rain/storms.
- **Underwater**: absorption that removes red first, a water fog coloured by the light where you are (eye-level sky
  and block light) and the biome water fog colour; looking up you see Snell's window (the world above through the
  surface) with total internal reflection outside it. Entering water starts murky and clears in ~0.35 s; after
  surfacing, water runs off the "lens" for ~1.6 s (smoothed custom uniforms `inWater` / `wetLens`).
- **Time of day and weather**: the water's light follows the sun, twilight and night colours; rain adds ripples and
  roughness; storms add whitecaps.
- **Sun glint**: a sharp sparkle plus a broader, dim glitter path, restrained so it blooms only a little.
- **Optional vertex displacement** (on by default in Balanced/High): only the upper vertices of water quads move, by
  the same function in `gbuffers_water` and the shadow pass. Amplitude is a fraction of a block (sum of the swell and
  chop heights, about ±0.25 block at default strength) and is cut to 12 % for any water surface not within ~2.5 blocks
  of sea level (y 63), so pools, canals and lakes on islands barely move while the open sea swells.
- **Caustics**: sunlit terrain under water gets moving caustic lines; sunlight under water is absorbed by the depth
  of water it crossed (from the shadow map: the distance between the water surface and the receiver along the sun).

### Lighting
- Sun/moon **shadow mapping** with radial distortion (focus slider), normal-offset bias, and a rotated golden-angle
  PCF with hardware comparison (4/8/16 samples by profile). Light through stained glass is tinted.
- **Warm direct sun** and gently cooler sky shade; twilight turns the sun orange and the shade lilac; night is a
  readable moonlit blue (exposure opens up at night and under roofs, driven by `eyeBrightnessSmooth`).
- **Interiors stay readable**: softer sky-light curve, warm torch/lantern light from the block lightmap, a minimum
  light level, vanilla ambient occlusion on ambient light only (`separateAo`), optional handheld light.
- **Material response** from `block.properties`: metal (tinted, glossy, reflects the sky), glass and ice, polished stone
  and the poneglyph, roof tiles (glazed sheen), wood, plaster, cloth, sand, snow, leaves (wrap lighting and light
  through leaves against the sun), cloud blocks (soft wrap lighting). Rain makes open, upward-facing surfaces darker
  and glossier.
- **Emissive** blocks glow: sea lanterns, glowstone, froglights, shroomlight, lit redstone lamps and copper bulbs
  (fully); torches, lanterns, campfires, lit candles and furnaces (their bright pixels); fire and lava; the mod's
  paper lanterns (whole shade) and brass lantern. Any other block with a light level glows on its brightest pixels,
  using the light level Iris passes per vertex (`at_midBlock.w`).
- **Bloom** only on what is genuinely bright after exposure (soft threshold), built from six blurred mip levels.
- **Tone mapping**: an exponential curve applied to the brightest channel with the colour scaled along, so hues are
  preserved and nothing clips; only the brightest highlights are eased toward white. **Grading**: vibrance-style
  saturation, gentle S-curve contrast, warm highlights / slightly cool shadows, vignette, dithering.
- **Stylisation slider** (default 0.15): soft light banding and a crisper shadow terminator, plus clean value steps in
  the final image. **Outlines** exist as an option but are off by default.

### Sky and atmosphere
- Artistic sky gradient (deep zenith, bright horizon), warm sunrise/sunset with a lilac opposite sky, blue nights with
  stars, a sun disc with glow and a moon with phases. Overcast and storms grey and darken it.
- **Painted clouds**: a soft, drifting 2D cloud layer with self-shadowing and silver linings (also in reflections);
  alternatively vanilla clouds restyled.
- **Distance haze** that is thicker near sea level, in rain and at twilight, and blends into exactly the colour the sky
  shows in that direction, so the edge of the loaded world disappears. The mod's own fog banks (which shorten the
  vanilla fog distance) are followed.
- **Dimensions** (`dimension.properties`, Iris syntax `dimension.<folder> = <namespace:path> ...`):
  - `world0`: `minecraft:overworld` and the fallback `*` for any unlisted dimension: the full look.
  - `sky`: `grandline:sky`: higher, brighter sun, deeper zenith, less haze, and a sunlit **cloud sea** drawn below the
    horizon under the floating islands.
  - `deep`: `grandline:deep_sea` (and `grandline:deep`): no sun or shadow pass; dark blue sea light, a deep-sea
    backdrop, warmer lantern light, lighter underwater fog so the deep sea stays travellable.
  - `interior`: `grandline:ship_interiors`: no sky, no shadow pass, warm dim room light.
  - `world-1` (Nether): fog-coloured ambient and haze; `world1` (End): the vanilla End sky, purple ambient and haze.

### Foliage
- Wind sway for leaves (small, whole block), grass, flowers, saplings and crops (anchored at the base), two-block
  plants (lower/upper halves move as one), vines, sea grass, kelp and sugar cane (continuous, so stacked segments never
  part). Logs, planks, beams, thatch and every unlisted block never move. The shadow pass sways identically.
- Optional **cloth sway** (off by default) for the mod's awnings (group 44), anchored at the top. Vanilla banners
  already wave on their own.

## Settings

| Screen | Options |
|---|---|
| Water | Wave Strength, Wave Speed, Wave Geometry (displacement), Water Clarity, Refraction, Reflections (Sky / Sky + Clouds / Screen Space), Reflection Steps, Foam, Caustics, Underwater Fog |
| Lighting | Sunlight, Sky Light, Torch and Lantern Light, Minimum Light, Glow, Highlights, Handheld Light |
| Shadows | Shadow Resolution, Shadow Distance, Shadow Samples, Shadow Softness, Shadow Focus, Sun Angle |
| Sky | Clouds (Off / Vanilla, restyled / Painted), Cloud Cover, Distance Haze, Stars |
| Motion | Foliage Motion, Cloth Sway |
| Image | Exposure, Saturation, Contrast, Bloom, Bloom Strength, Stylisation, Outlines, Vignette |

Names and tooltips are in `shaders/lang/en_us.lang`. Depth of field and motion blur are not implemented.

### Profiles

| | LOW | BALANCED (default) | HIGH |
|---|---|---|---|
| Shadow map | 1024² | 2048² | 4096² |
| Shadow distance | 80 blocks | 128 blocks | 192 blocks |
| Shadow filter samples | 4 | 8 | 16 |
| Water reflections | sky gradient | sky + clouds + sun | + screen-space (32 steps) |
| Wave geometry (displacement) | off | on | on |
| Caustics | off | on | on |
| Bloom | off (pass disabled) | on | on |
| Clouds | vanilla, restyled | painted | painted |

## Material groups (`shaders/block.properties`)

Vanilla blocks use ids 10–62; the mod's blocks use **100 + the same group** in a separate, marked section at the end,
so they can be extended without touching the vanilla lines. Iris needs explicit ids and a second line with the same
key replaces the first, so to classify a new `grandline:` block append its id to the matching `block.1xx` line, e.g. a
new lantern to `block.151` (bright pixels glow) or `block.154` (the whole shade glows). Blocks that emit light glow on
their bright pixels even when unlisted. Already classified: fruit bloom (plant), bubble membrane (glassy membrane with a
thin-film rim), island cloud (soft, never sways), seastone, poneglyph, ship hatch, and the architectural kit (plasters,
timber frames, beams and planks as wood, roof tiles, thatch, masonry, columns, shutters, mullion window as glass,
lattice window and fishing net as non-moving cutouts, awnings as optional hanging cloth, paper and brass lanterns).

## Files

```
shaderpack/GrandLineShaders/
  README.md  CREDITS.md
  shaders/
    shaders.properties  block.properties  dimension.properties  lang/en_us.lang
    lib/        settings, uniforms, common, waves, motion, sky, lighting, shadows, tonemap, bloom tiles
    program/    the program bodies (shadow, gbuffers_*, deferred, composite, composite1, final)
    world0/ world-1/ world1/ sky/ deep/ interior/   per-dimension stubs: #define DIM + #include of the body
tools/pack_shaders.py      builds dist/GrandLineShaders-1.0.0.zip (checks the dimension folders first)
tools/gen_shader_stubs.py  regenerates the per-dimension stubs
tools/check_shaders.py     offline GLSL check of every program and profile with glslangValidator (optional)
```

Buffers: colortex0 RGBA16F scene, colortex1 RGBA16 normal + lightmap, colortex2 RGBA8 material/emission/AO,
colortex3 RGBA16 water normal + sky light + flag, colortex4 RGBA16F bloom source (mipmapped), colortex5 RGBA16F bloom
tiles, colortex6 RGBA8 water tint + block light. Passes: shadow, gbuffers, deferred (lighting + sky), composite (water,
haze, fog, bright-pass), composite1 (bloom tiles), final (tone map, grade).

## Tested versions

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.259 (development run through ModDevGradle 2.0.148, Java 21) |
| Iris | 1.8.12 for NeoForge 1.21.1 — `iris-neoforge-1.8.12+mc1.21.1.jar` (Modrinth version `t3ruzodq`; reports itself as `1.8.12-snapshot+mc1.21.1-local`), sha512 `57b8026a3c3c433c…` |
| Sodium | 0.6.13 for NeoForge 1.21.1 — `sodium-neoforge-0.6.13+mc1.21.1.jar` (Modrinth version `Pb3OXVqC`), sha512 `ce58f34d05d96c0a…` |
| Grand Line Odyssey | 0.1.0 (branch tip `969ff3f` plus this work), GeckoLib 4.9.3 |
| Renderer | Mesa llvmpipe (LLVM 20.1.2), OpenGL 4.5 core, under Xvfb, 4 CPU cores — **software rendering** |

## How it was verified

The mod's automated client harness gained a shader tour (`GL_CLIENTTEST=shaders`, and `shaders_quick` for the
per-profile runs; `ClientSelfTest.buildShaderTour`). Iris and Sodium were put in `run-client/mods/`, the zip in
`run-client/shaderpacks/`, `iris.properties` set to `enableShaders=true` / `shaderPack=GrandLineShaders-1.0.0.zip`,
and the client joined a dedicated server (`GL_SELFTEST=client`) that grants op. The tour hides the HUD, freezes the
day cycle, and visits fixed views with `/time`, `/tp`, `/execute in <dimension>`, `/grandline island tp|here`, the
mod's storm finder and a harness-only `/gltest_dock` (server half, `ClientTestSupport`) that frames the nearest
settlement dock. It builds a small pool (surface 4 blocks above sea level) and a closed room lit by torches,
lanterns, a campfire and the mod's paper and brass lanterns on a stone platform at sea. Comparison shots switch the
pack off and on again through the Iris API (`IrisApi.getConfig().setShadersEnabledAndApply`). The window is resized
to 960x540 for one shot and back, and during every chunk wait it is rendered at 480x270 and restored.
Profiles were selected through `run-client/shaderpacks/GrandLineShaders-1.0.0.zip.txt` (Iris logged
`Profile: LOW`, `Profile: HIGH` and `Profile: BALANCED (+0 options changed by user)`).

<!-- VERIFICATION -->

## Known limitations and honest notes

- **Only software rendering was available.** Everything above ran on llvmpipe; nothing here says how the pack performs
  or looks on NVIDIA, AMD or Intel drivers (driver-specific GLSL strictness, precision, real frame rates). The frame
  rates logged by the harness are llvmpipe numbers and mean nothing for a real GPU.
- **Approximations**: the sky is an artistic gradient, not atmospheric scattering; water colour is per-channel
  Beer-Lambert absorption plus an artistic scatter colour; refraction and SSR are screen-space (nothing off screen
  can be refracted or reflected; the sky gradient is the fallback); waves are a sum of directional wave trains and
  noise ripples, not an FFT ocean; displacement moves block-corner vertices only (1-block resolution); caustics are a
  procedural pattern, not traced from the waves; light under water is absorbed by the water depth read from the shadow
  map, so it only works within the shadow distance; foam comes from the depth buffer, so it needs the contact to be on
  screen; emissive masks are chosen by pixel brightness; exposure follows the eye's sky light and the time of day, it
  is not a measured auto-exposure.
- **Wave strength by height, not by water body**: anything within ~2.5 blocks of sea level waves like the sea (a
  lake or canal at sea level included); any water clearly above or below it gets 12 % of the swell plus ripples.
- No shadow pass in the deep sea, ship interiors, Nether and End (no sun there); the deep sea uses a fixed sea light.
- Not supported or not tried: Distant Horizons, OptiFine, Iris 1.8.14-beta with Sodium 0.8.x, Vulkan-based renderers.
- **Harness finding (not a pack problem)**: with Iris 1.8.12 + Sodium 0.6.13 and a camera that stays perfectly still
  after a teleport, chunks that finished loading later were missing from the view until the camera moved (the open
  sea ended a few chunks out; Sodium's statistics at the first sea view: 48 visible of 5088 loaded sections with the
  pack on, 106 of 5784 with it off a little later). Asking for a terrain update did not help; turning the camera by
  0.05° back and forth for two seconds before each shot did, so the tour does that. In play the camera always moves.


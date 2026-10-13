# Kingdoms of the Long Night - modpack verification (v0.1.0)

Date: 12 Oct 2026, 22:30-23:45 UTC. Everything below was run in the cloud build container
(4 vCPU Xeon @ 2.8 GHz, 15 GB RAM, **no GPU** - Mesa llvmpipe software OpenGL 4.5, OpenJDK 21.0.12).
This machine says nothing about the player's PC; timings are for comparison between runs only.

The first round of runs below (22:30-23:45 UTC) was made **before the `longnight` jar existed**, so it checks the
modpack alone. The runs **with `longnight`** came later in the night and are summarised in the next section.

## With the longnight core mod (13 Oct 2026, 00:15-02:30 UTC)

| Check | Result |
|---|---|
| Dedicated server, performance preset + `longnight`, fresh hardcore world, **watchdog on** (`server_test.py --with-longnight yes`) | **PASS**: 40 mod ids, 0 ERROR lines, 0 exceptions. The world's history (14 places, ~220 people, 8 realms) is written on a background thread in ~38 s with Tectonic + Terralith terrain while the server keeps ticking; `/longnight admin validate` -> `VALIDATE OK`. (The first attempt, with history written on the server thread, was killed by the watchdog after 60 s: that is why generation now runs in the background.) |
| Real client, performance preset + `longnight`, installed from the built `.mrpack` (`LONGNIGHT_AUTOSHOT=1 client_test.py`) | **PASS**: joined the world, flew to the largest town (a Dunmar town of 38-39 buildings and 35 people among Terralith giant spruces), waited for it to be raised, screenshots of the town, a person, night and the road to the next town (`tools/test/screenshots/fullpack-*.png`). 0 fatal; the same 2 cosmetic ERROR lines as without longnight. |
| Village disabling with Terralith present | `longnight` loads after Terralith and empties the village structure set, so no vanilla or Terralith villages generate; only longnight's people exist. |

| Dedicated server, **rich** preset + `longnight` (final release, 13 Oct 04:05 BST) | **PASS**: 41 mod ids (incl. Distant Horizons), 0 ERROR lines, 0 exceptions; history 14 settlements, 207 people, 8 realms in 48 s (background); 1 simulated day, `VALIDATE OK`. |
| Performance preset + `longnight`, final release | server PASS (2 simulated days, `VALIDATE OK`), real client PASS (a 40-building Dunmar town raised in 1,318 ticks). |

Not run with `longnight`: the rich preset's **client** (shaders + Distant Horizons in software GL are very slow here;
the rich client was verified without longnight below, and longnight adds no rendering beyond its citizen model).

## Result summary

| Check | Performance preset | Rich preset |
|---|---|---|
| Metadata check (`check_metadata.py`, client + server sets) | **OK** (37 jars / 48 mod ids client, 30 / 37 server) | **OK** (40 / 51 client, 31 / 38 server); 1 KNOWN note (Iris, below) |
| Dedicated server boot, fresh hardcore world | **PASS**: `Done` after **48.0 s** wall (server-reported 24.7 s) | **PASS**: **49.0 s** wall (server-reported 26.5 s) |
| Mods loaded by FML on the server | 39 mod ids (incl. minecraft, neoforge and jar-in-jar libs) | 40 (+ distanthorizons) |
| Far-terrain probe (2 x 256 chunks at +/-4000) | generated in 72 s, no errors | 98 s (Distant Horizons server-side active), no errors |
| `/place` of every modded structure (53 ids) | 46 generated, 7 skipped, **0 failed** | 47 generated, 6 skipped, **0 failed** |
| Terralith fortified villages disabled | yes (`/locate` finds none) | yes |
| ERROR lines in server log | **0** | **0** |
| Headless client: install from the built `.mrpack`, load, join world | **PASS**: title screen 55 s, in world 82 s, 3 min in-world | **PASS**: title 75 s, in world 108 s, shader + DH active, 3 min in-world |
| ERROR lines in client log | 2 cosmetic (see below) | same 2 cosmetic |
| Crash reports / mixin failures / missing deps | none | none |
| `.mrpack` builds deterministic | yes: two consecutive builds byte-identical | yes |

Current release files (with the company start, 13 Oct 2026 ~13:15 BST; these are the files in `dist/`; they differ from the ~13:00 build only in the dev-only screenshot pilot, which is off unless started with -Dlongnight.autoshot):
- `KingdomsOfTheLongNight-0.1.0-performance.mrpack` `9bb177714d347adb36d94d9a1d48275c1ccff2e949bbf6cccd35f505a12a5d5f`
- `KingdomsOfTheLongNight-0.1.0-rich.mrpack` `0bebad0bec43658582087bb2cd874076d60919d08f7ce570f1014b01787df6b5`
- `longnight-0.1.0.jar` `180b45e82aab07b1b7fec1eed64d39875ed14f04feb33c7ce3efae53ba4a096e`

Earlier `.mrpack` sha256 (built without the core mod, for the first round of runs below):
- `KingdomsOfTheLongNight-0.1.0-performance.mrpack` `e2232070111249f67d7ce6494add85a8a9bbb7ed9be0b7c56711d388d59d558c`
- `KingdomsOfTheLongNight-0.1.0-rich.mrpack` `cfeee0c45d15fb996a3488d8369c64c27698895696371f726f15bc024fe73357`

## What exactly was tested

### 1. Selection and metadata (`resolve_manifest.py`, `check_metadata.py`)
- Every project and version came from `api.modrinth.com/v2` (`/project/{slug}` and
  `/project/{slug}/version?loaders=["neoforge"]&game_versions=["1.21.1"]`). The newest *release* was
  taken, else the newest *beta*; alpha builds were never used. Exceptions are explicit pins with reasons
  (Supplementaries 3.9.9, Iris 1.8.14-beta.1). Hashes, sizes, URLs and licences are recorded in
  `modpack/manifest.json`.
- Every downloaded file was checked against the Modrinth sha512 **and** sha1 and its size.
- `check_metadata.py` reads each jar's `META-INF/neoforge.mods.toml` (plus jar-in-jar mods) and checks
  required/optional/incompatible dependencies and version ranges with a port of Maven's
  `VersionRange` (the library FML uses; cross-checked against the real `maven-artifact-3.8.5.jar`),
  separately for the client set and the server set of each preset.

Problems this found and how they were fixed:
1. **Ars Nouveau does not declare its dependencies on Modrinth.** Its mods.toml requires GeckoLib and
   Curios -> both added explicitly to `selection.json`.
2. **Supplementaries declares Sodium < 0.8.12-beta.1 incompatible.** So the pack must use the Sodium 0.8
   line (0.8.13), which rules out Iris 1.8.12 (built for Sodium 0.6) -> Iris pinned to
   **1.8.14-beta.1**, the only Iris for 1.21.1 NeoForge that targets Sodium 0.8. Verified working in
   the client test.
3. **Iris declares `minecraft [1.21,1.21.1)`**, which by Maven rules excludes 1.21.1. NeoForge 21.1.259
   nevertheless loads it and the shader renders (client test). Recorded as a KNOWN exception in
   `check_metadata.py`; re-verify whenever Iris or NeoForge changes.
4. (Side experiment only) IceAndFire CE's library Uranus requires Architectury, which Modrinth does not
   declare either.

### 2. Dedicated server boot (`server_test.py`)
Command (per preset): `python3 tools/pack/server_test.py --preset performance` (and `--preset rich`).
What it does:
- `java -jar neoforge-21.1.259-installer.jar --installServer <dir>` (installer from maven.neoforged.net).
- Copies every file of the preset whose `env.server` is not `unsupported` (performance: 30 jars,
  rich: 31 incl. Distant Horizons) plus the pack's `config/` overrides.
- TEST ENVIRONMENT ONLY: `eula.txt` with `eula=true`; `server.properties` with `online-mode=false`,
  `server-port=25599`, default `level-type` and `max-tick-time`, `hardcore=true`, `difficulty=hard`,
  random seed, a freshly deleted `world/`. JVM: `-Xmx4G -Xms1G`, started with the same
  `@user_jvm_args.txt @libraries/.../unix_args.txt nogui` that `run.sh` uses.
- Waits for `Done (`; then a **world-gen probe**: force-loads two 16x16-chunk areas around
  (4000, 4000) and (-4000, -4000) in 8x8 batches (so no single tick approaches the 60 s watchdog),
  waits until they are fully loaded, runs `/place structure <id> 4128 80 4128` for **every** structure
  shipped in the mods (53 ids: YUNG's, Terralith, Ars Nouveau, Supplementaries), runs
  `/locate structure terralith:fortified_village`, `save-all flush`, then `stop`.
- Scans `logs/latest.log` for missing dependencies, mod-loading errors, mixin failures, registry /
  world-gen errors, exceptions, crash reports, and every ERROR line.

Structures *skipped* by `/place` (not errors): the three Ars Nouveau Wilden dens, YUNG's small *nether*
dungeon, Supplementaries road sign and (performance run) galleon reported "Failed to place structure" at
the probe spot (they need specific terrain such as the Nether/ocean/road). `terralith:fortified_village`
can still be placed by command in some runs (the structure stays registered) but never generates
naturally because its structure set is emptied. All other 46-47 structures generated.

Loaded on the dedicated server (performance; FML mod list):
apollib 1.2.0, appleskin 3.0.9+mc1.21, ars_nouveau 5.13.3, betterdeserttemples 4.1.5, betterdungeons 5.1.4,
bettermineshafts 5.1.1, betterstrongholds 5.1.3, codecui 1.4.7, curios 9.5.1, emi 1.1.24, farmersdelight 1.3.4,
ferritecore 7.0.3, geckolib 4.9.3, handcrafted 4.0.3, infiniverse 2.0.1.0, jade 15.10.6, lambdynlights_api 4.5.1,
lithium 0.15.4, lithostitched 1.8.0, mcwbridges 3.1.2, mcwdoors 1.1.5, mcwfences 1.2.1, mcwpaths 1.1.1,
mcwroofs 2.3.2, mcwwindows 2.4.2, minecraft 1.21.1, modernfix 5.27.26, moonlight 3.7.2, neoforge 21.1.259,
nuggets 1.1.0.48, resourcefullib 3.0.12, sablecompanion 1.6.0, supplementaries 3.9.9, tectonic 3.0.28,
terralith 2.6.2, xaerolib 1.7.3, xaerominimap 26.6.0, xaeroworldmap 1.47.0, yungsapi 5.1.9.
Rich adds distanthorizons 3.3.3. (apollib, codecui, infiniverse, lambdynlights_api, nuggets, sablecompanion
and xaerolib are jar-in-jar libraries of Handcrafted/Ars Nouveau/Supplementaries/Xaero's.)

Boot times over four clean boots: performance 46.9 / 48.0 s wall (26.8 / 24.7 s reported),
rich 43.6 / 49.0 s wall (23.5 / 26.5 s reported).

### 3. Headless client (`client_test.py`) - covers the client-only mods
Command: `python3 tools/pack/client_test.py --preset rich --mrpack dist/KingdomsOfTheLongNight-0.1.0-rich.mrpack --world <server world> --seconds 180`
- Installs the NeoForge client with the official installer, downloads vanilla libraries and assets
  (sound files skipped), then builds the instance **from the built `.mrpack` exactly like a launcher**:
  reads `modrinth.index.json`, downloads every client file from its cdn.modrinth.com URL, verifies
  sha1 + sha512 + size, extracts `overrides/`.
- Copies the world generated by the server test into `saves/`, starts the game under Xvfb with Mesa
  llvmpipe (`--quickPlaySingleplayer`), plays 3 minutes, takes screenshots, scans the log.
- Result: all 37 / 41 files installed and verified; Sodium, Entity Culling, ImmediatelyFast, AmbientSounds,
  Particle Rain, Mouse Tweaks, Xaero's maps, EMI, Jade loaded (performance); rich additionally loaded Iris
  (`Using shaderpack: ComplementaryReimagined_r5.9.3.zip`, profile HIGH, shaders compiled with only
  driver warnings), Distant Horizons (Iris integration bound, LOD renderer created) and Sound Physics
  Remastered. Screenshots show the Complementary-shaded world with the Xaero minimap and Jade tooltip.
  Limits: the container has no audio device (OpenAL off), so AmbientSounds/Sound Physics were only
  loaded, not heard; llvmpipe frame rates are meaningless; the player stood still (no long-distance travel).
- The pack's config overrides survived first launch: `iris.properties` keeps the shader,
  `DistantHorizons.toml` keeps radius 128 / auto-updater off and gets the remaining keys filled in,
  `supplementaries-common.toml` keeps `red_merchant_spawn_multiplier = 0.0` (NeoForge logs
  "is not correct. Correcting" and writes a `.bak` of our 3-line file - expected).
- Opening a world whose dimension data comes from datapacks (Tectonic/Terralith) shows vanilla's one-time
  **"Worlds using Experimental Settings are not supported"** screen; the test clicks
  "I know what I'm doing!" (documented for players in INSTALL.md).

## Warnings and known cosmetic messages (all reviewed)
- `Trying to mark a block for PostProcessing ... not supported` (~1,500-2,300 lines) and
  `Ignoring unknown attribute 'forge:entity_gravity' / 'forge:step_height_addition'` (~25 lines):
  **only after the probe starts** - caused by `/place structure` into already-generated chunks and by
  mob NBT saved in YUNG's structure templates in the Forge era. None during boot or natural generation.
- `Block-attached entity at invalid position: BlockPos{x=-67|-111, y=67, z=-316}` (ERROR, seen in 3 of 5
  completed server runs, always the same template coordinates): vanilla 1.21 logs this when a structure template
  stores an item frame/painting with absolute coordinates from its author's world; it came from placing
  YUNG's Better Desert Temple. Cosmetic; `server_test.py` lists it as KNOWN.
- `Method overwrite conflict ... ModernFix ... previously written by Lithium` (2 lines): both mods
  overwrite the same two methods with equivalent optimisations; known and harmless.
- Mixin `Reference map ... could not be read` (~25 lines): normal for mojmap NeoForge mods.
- Client ERROR `GeckoLib: Unable to parse animation: swim_charge` - one Ars Nouveau animation; cosmetic.
- Client ERROR `xaero.map.WorldMap: ... Online mod data expired` - Xaero's update check offline; cosmetic.
- Client: Iris warns that Complementary r5.9.3 uniforms `inPaleGarden`, `inSulfurCaves`, `endFlashFactor0`
  cannot resolve - they reference biomes/features that do not exist in 1.21.1; those effects are simply off.
- Client: Ars Nouveau prints a `NoSuchFileException: config/ars_nouveau/doc_data.json` stack trace on the
  first world join (it then creates the file).

## Client-only mods: metadata review and concerns
| Mod | Declared ranges | Concern |
|---|---|---|
| Sodium 0.8.13 | minecraft 1.21.1, neoforge >= 21.1.82 | none |
| Iris 1.8.14-beta.1 | minecraft [1.21,1.21.1) (!), no Sodium dependency declared | **beta**; pinned; range quirk tolerated by FML (verified). Re-test when Iris ships a release for Sodium 0.8. |
| Entity Culling 1.11.3 | minecraft [1.21.1], neoforge >= 20.2 | none |
| ImmediatelyFast 1.6.14 | minecraft [1.21,1.21.1], neoforge >= 21.0.0 | none |
| AmbientSounds 6.3.10 (+CreativeCore 2.13.51) | minecraft [1.21.1,1.22), creativecore >= 2.13.37 | 85 MB download (bundled sounds) |
| Particle Rain 4.0.2 | minecraft [1.21.1] | 4.0.x line is days old (released 7-10 Oct) |
| Mouse Tweaks 2.26.1 | (no deps) | built for 1.21, listed for 1.21.1; loads fine |
| Sound Physics Remastered 1.4.10 | minecraft [1.21,1.21.1], cloth_config/voicechat optional | **beta** (newer 1.5.x are alpha); rich preset only |
| Distant Horizons 3.3.3 | minecraft [1.21],[1.21.1] | heavy CPU/RAM while generating LODs on Tectonic terrain; radius capped at 128 chunks |
| Xaero's Minimap / World Map | client required, server optional | full edition (entity radar/cave map); Fair-Play has no 1.21.1 NeoForge build |

## Decisions that came out of testing
- **Terralith ships "fortified villages" full of villagers** (`terralith:rare_village` structure set).
  Because longnight owns all NPCs, `config/terralith.json` sets `custom_structures: false` (this also
  turns off Terralith's non-NPC structures: mage towers, spires, rubble, cabins, Terralith dungeons).
  Verified: `/locate structure terralith:fortified_village` finds nothing.
  **For the longnight core mod:** to get Terralith's other structures back, ship
  `data/terralith/worldgen/structure_set/rare_village.json` with `"structures": []` (Terralith's own
  disable mechanism) and flip `custom_structures` back to `true`. Note also that Terralith **adds its
  biomes to the vanilla `#minecraft:has_structure/village_*` tags**, so longnight's village-disabling tag
  overrides must use `"replace": true` and load after Terralith (declare an optional `terralith`
  dependency with `ordering = "AFTER"`), or override `minecraft:villages` structure set instead.
- **Supplementaries' Red Merchant** (wandering NPC trader) disabled: `red_merchant_spawn_multiplier = 0.0`.
  Its "Plunderer" pirates in pillager raids remain (illagers stay as threats, D3).
- **Distant Horizons' self-updater disabled** (a modpack must not update its own mods).
- **Supplementaries pinned to 3.9.9**: 4.0.0 and 4.0.1 were published hours before this run.
- **Dragons**: IceAndFire CE 2.0 was boot-tested as a side experiment (with Uranus, Jupiter and the
  undeclared Architectury): boots in 56 s, all its structures place; only first-run "config file missing"
  errors. Still excluded (fresh major, save-corruption warning on version swaps, griefing dragons, heavy
  world-gen). Recorded as: *not clearly stable - longnight will provide dragons later*.

## How to re-run (all Python 3 stdlib + Java 21; Linux for the client test)
```
cd kingdoms-of-the-long-night
python3 tools/pack/resolve_manifest.py          # only to change/upgrade mods (re-queries Modrinth)
python3 tools/pack/gen_mods_md.py               # refresh MODS.md from the manifest
python3 tools/pack/check_metadata.py            # offline dependency/range check, both presets, both sides
python3 tools/pack/build_mrpack.py 0.1.0        # -> dist/*.mrpack (picks up mod/build/libs/longnight-*.jar)
python3 tools/pack/server_test.py --preset performance --with-longnight yes   # PASS/FAIL + log path
python3 tools/pack/server_test.py --preset rich --with-longnight yes
python3 tools/pack/client_test.py --preset rich --mrpack dist/KingdomsOfTheLongNight-0.1.0-rich.mrpack \
    --world <cache>/server-test/rich/world                  # needs Xvfb + Mesa; ~5 min
```
Downloads and test servers live in `$KOTLN_CACHE` (default `$TMPDIR/kotln-pack-cache`). `server_test.py`
also accepts `--extra-command "<console command>"` (repeatable), `--keep-running SECONDS`,
`--no-worldgen-probe`, `--seed`, `--port`, `--xmx`. Each run writes `server_test_summary.json` /
`client_test_summary.json` next to the logs. `--with-longnight auto` (default) includes the core-mod jar
when it exists and prints a warning when it does not.

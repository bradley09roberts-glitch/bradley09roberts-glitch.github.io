# Pale Meridian — Technical reference

> Spoiler level: this file names places, systems and quest ids, but does not tell the story.
> Plot details live in `docs/spoilers/`.

## 1. Architecture in one page

```
tools/ (Python, build time)                        mod/ (Java + resources, run time)
 layout.json ──────────────┐                        ┌─ ValleyTerrain / ValleyDensityFunction / ValleyBiomeSource
  (valley geometry)        ├──► palemeridian_layout.json ─┤   (seed-independent designed valley)
 pmgen/worldgen.py ────────┼──► data/…/worldgen  ──────────┤─ FixedStructurePlacement / SiteStructure / SitePiece
 pmgen/sites/*.py ─────────┼──► data/…/structure/*.nbt ────┤   (authored sites at fixed coordinates)
   (+ POIs: generated/poi.json)                            ├─ ValeRoadsFeature / LampPosts (roads, lamp posts)
 pmgen/dp/engine.py ───────┤                               ├─ Restoration (lifts the fog district by district)
 pmgen/dp/core.py          ├──► data/palemeridian/function ├─ WorldSetup (spawn, world-type guard)
 pmgen/dp/content/*.py ────┤    advancement, dialog,       ├─ PMState (reads/writes campaign flags)
 pmgen/dp/encounters.py    │    predicate, loot_table,     └─ AdminCommand (/pmadmin)
 pmgen/art.py ─────────────┴──► assets/palemeridian (skins, sprites, icon)
```

- **The campaign is a data pack** embedded in the mod (always enabled). It owns all story state and
  logic: quests, dialogs, NPCs, puzzles, encounters, the journal.
- **Java does what a data pack cannot**: the designed valley terrain and biome layout, fixed-position
  structures, roads and lamp posts, converting biomes when a district is restored, spawn setup, and
  operator diagnostics.
- **Everything generated is reproducible**: `python3 tools/gen_all.py` rebuilds all data, structures,
  art and the POI table from `tools/`. Generated trees are deleted and rebuilt on every run.

## 2. Building and checking

| Command | What it does |
|---|---|
| `python3 tools/gen_all.py` | Regenerates `mod/src/main/resources/{data,assets}`, `tools/generated/*`, `docs/spoilers/QUEST_GRAPH.md` and the game-test fixture. Downloads the pinned 26.2 jars once into `tools/.cache` (SHA-1 verified) to read vanilla data. |
| `python3 tools/simulate.py` | Logic simulation of the generated functions (§12). |
| `cd mod && ./gradlew build` | Builds `build/libs/palemeridian-1.0.0.jar` (reproducible). **Never launches Minecraft.** |
| `./gradlew offlineCheck -PcheckMode=validate` | Loads vanilla + the pack through the game's own registry, function, advancement, loot and dialog loaders, offline; decodes template items/texts; fails on any logged error. |
| `./gradlew offlineCheck -PcheckMode=sites` | Evaluates the terrain generator on 3 seeds: site plateaus, cave holes, the mine's cave-free zone, and that every site starts in a biome its structure accepts. |
| `./gradlew offlineCheck -PcheckMode=map` / `heightmaps` | Renders a biome/height map; exports design heights used by the site builders. |
| `./gradlew runGameTest -Ppm_accept_eula=true` | Runs the runtime game tests in a headless server. **Starts Minecraft**: only run it if you accept the Minecraft EULA. |
| `python3 tools/lock_pack.py [--check]` | Resolves/verifies the pinned mod files (see `STACK_AND_LOCK.md`). |
| `python3 tools/build_dist.py` | Builds `dist/` (client `.mrpack`, server zip, checksums). |

## 3. Campaign state model (save format)

**Single source of truth: the world scoreboard.** Nothing campaign-related is stored in player files
except per-player preferences.

| Objective | Holder(s) | Meaning |
|---|---|---|
| `pm.q` | quest id, e.g. `c1.round` | 0 locked, 1 active, 2 done |
| `pm.qp` | quest id | progress counter shown as n/max |
| `pm.world` | fake players `#…` | world facts: `#schema`, `#init`, `#chapter`, `#rev`, `#ending` (0/1/2), `#r.<district>` (restored), `#req.<district>` (request to Java, consumed), `#k.<n>`/`#k.count`/`#k.all` (keepsakes), `#set.chill`, `#set.difficulty`, encounter state `#enc.<id>`, `#boss`, cooldowns `#cool.*`, puzzle state (`#round.step`, `#fen.drained`, …), `#seconds` clock |
| `pm.tmp` | fake players | scratch values within one function call |
| `pm.talk`, `pm.ui` | players (trigger) | dialog choice codes / journal buttons (non-operators can use triggers) |
| `pm.dctx` | players | which dialog is open (choices are refused unless they belong to it) |
| `pm.joined`, `pm.seen`, `pm.leave`, `pm.death`, `pm.kit` | players | lifecycle: first join, recap needed, rejoin, death, starting kit given |
| `pm.optbar`, `pm.optfx`, `pm.optwp` | players | personal comfort settings |
| `pm.chill` | players | seconds in the fog without light |
| `pm.bread`, `pm.gift2`, `pm.glass` | players | once-a-day gifts, reward given |

Command storage `palemeridian:poi` holds every point of interest (rebuilt on each load),
`palemeridian:npc` the NPC skin state, `palemeridian:state` the waypoint's forceloaded chunk.

**Schema and migration.** `#schema` is set to 1 on first load. `core/load` (runs on every server
start and `/reload`) calls `core/migrate`, the place for future `1_to_2` steps, and warns operators
if a world was saved by a newer schema. `q/_init` gives every quest a state if it has none (new
quests added by an update start locked) and never changes existing progress.

**Idempotency.** Every state change goes through `q/<id>/activate` (only from 0) and
`q/<id>/complete` (only from 1). Repeated or late triggers, two players finishing a step together,
or a reload in the middle of a step are harmless. `q/_advance` activates every quest whose
prerequisites are done and runs after every completion.

**Softlock prevention.** Each quest has at least one completion path and every consumable
requirement has a recovery path (§9). Operators have non-destructive repair (`admin/refresh`) and a
last-resort `admin/force {q:"…"}`.

## 4. The engine (`tools/pmgen/dp/engine.py`)

Content modules register specs with `R` (the registry); `engine.generate()` writes everything.

| Spec | Generated | Notes |
|---|---|---|
| `Quest` | `q/<id>/activate`, `complete`, `hud`; `q/_advance`, `q/_init`, `hud/refresh`; journal advancements `journal/<chapter>/<quest>`; objective dialogs `journal/obj/<id>` | HUD = boss bar + locator-bar waypoint for the latest active main quest |
| `Dlg` + `Choice` | `dialog/<id>.json`, `dlg/show/<id>`, `dlg/c/<code>`, `dlg/handle`, `dlg/_call` | choices send `/trigger pm.talk set <code>`; codes are validated against `pm.dctx` |
| `NPC` | `npc/<id>/{spawn_at,place,_at,talk,despawn,apply_skin,maintain}`, `npc/_clicked`, `trigger/npc_click` | body = mannequin (skin from the mod's textures, faded/restored), click target = interaction entity, props = text display + interaction. Fixed UUIDs make duplicates impossible; spawned only when a player is within 72 blocks; placement follows campaign state |
| `Blueprint` + `Part` | `bp/<id>/{check,done}`, block tags `bp/<id>_<n>` | glowing ghost block displays for missing parts; checked once a second only near players |
| `Area` | predicates `area/<id>`, location hooks `loc/h<n>` | location objectives, checked once a second, gated by quest state |
| block use | advancements `trigger/use/<id>` (`any_block_use` at an exact position) | the bell puzzle |

`_check_references()` fails generation if a quest target, NPC place or NPC dialog is missing.

## 5. Content modules (`tools/pmgen/dp/content/`)

| Module | Quests | Site builder(s) |
|---|---|---|
| `prologue.py` | `p.letter`, `p.camp`, `p.lamp`, `p.road`, `p.chill` | `sites/landing.py` |
| `chapter1.py` | `c1.odile`, `c1.names`, `c1.round`, `c1.lamp`, `c1.surge`, `c1.home` | `sites/hollin.py` |
| `chapter2.py` | `c2.arrive`, `c2.brannoc`, `c2.memories`, `c2.hearts`, `c2.lamp` | `sites/aldercross.py` |
| `chapter3.py` | `c3.arrive`, `c3.log`, `c3.tamsin`, `c3.remind`, `c3.memorial`, `c3.kiln`, `c3.lamp`, `c3.surge` | `sites/glassworks.py`, `sites/deepcut.py` |
| `chapter4.py` | `c4.crossing`, `c4.keeper`, `c4.lens`, `c4.unlooked`, `c4.chart`, `c4.causeway`, `ep.complete`, `ep.spoken`, `ep.kept` | `sites/meridian.py` |
| `keepsakes.py` | `s.eleven` | keepsakes are placed by the site builders |
| `fen.py` | `s.fen` | `sites/fen.py` |
| `bench.py` | `s.bench` | posts placed by every site builder (`houses.benchmark`) |
| `journal_pages.py` | — | recap and people pages |

The full dependency graph (spoilers) is generated into `docs/spoilers/QUEST_GRAPH.md`.

## 6. World generation (`mod/src/main/java/net/palemeridian/world/`, `tools/pmgen/worldgen.py`)

- **`ValleyLayout`** loads `palemeridian_layout.json` (copied from `tools/layout.json`): valley radius
  and heights, lake, north cliffs, fen, sites (centre, floor height, flat radius, falloff), roads,
  district seeds, district groups, spawn, and *quiet zones*.
- **`ValleyTerrain`** computes the designed height field from a *fixed internal noise seed*, so the
  valley is identical in every world; the world seed only varies the land beyond the rim.
  Sites get exact plateaus (verified on 3 seeds); `caveGuard` suppresses natural caves under sites,
  roads and quiet zones (the mine).
- **`ValleyDensityFunction`** (`palemeridian:valley`, modes `surface`, `height`, `guard`) replaces the
  overworld's `sloped_cheese` / `preliminary_surface_level` and guards the cave branches in the
  generated noise settings `palemeridian:vale`. The `minecraft:normal` world preset is overridden to
  use it with **`ValleyBiomeSource`**, which assigns each district a Pall biome or its clear twin.
- **Biomes** (`<district>_pall` / `_clear`, 20 in all): Pall variants carry the fog, sky, particles,
  silence and `creaking_active`; clear variants carry music, colour and animals. Lava lakes/springs
  are removed valley-wide; the mine district also drops water springs, dungeons and geodes.
- **Sites** are fixed structures (`palemeridian:site` + `palemeridian:fixed` placement) built from
  templates generated by `tools/pmgen/sites/*.py`. Large sites set an explicit anchor so every piece
  is within the 8-chunk structure reach (checked at generation time).
- **Roads and lamp posts**: `ValeRoadsFeature` paves the layout's roads and places lamp posts
  (`LampPosts`, every 26 blocks) during `surface_structures`.

## 7. Restoration (`Restoration.java`)

The data pack sets `#r.<group>` and `#req.<group>` when a district is restored. Each server tick the
service consumes requests and starts a job that converts the group's Pall biomes to clear biomes in an
expanding ring (7 blocks/tick, up to 640 blocks) and lights the group's lamp posts, resending biomes
to clients. Chunks that load later are converted on load (block changes deferred to the next tick).
Groups: landing, hollin, aldercross, glassworks, deepcut, mere, fen, wilds.

## 8. Atmosphere, encounters, the chill

- **World clocks/timelines** (`atmosphere.py`): `palemeridian:pall` (a paused clock moved to fixed
  milestones as districts clear) drives a valley-wide haze via environment-attribute timelines;
  `palemeridian:surge` runs only during encounters and pulses the fog.
- **Surges** (`encounters.py`): arena encounters with Watchers (unbound creakings) that scale with
  players present and the encounter setting; lamps (copper bulbs or candles) are snuffed and relit via
  interaction props (or flint and steel for candles); optional time limit; clean reset after 15 s
  with nobody inside, a cooldown, and a reset on server restart.
- **The final encounter** (`chapter4.py`, `c4/boss/*`): a scaled creaking with scripted phases,
  relay lamps, adds and a telegraphed snuff; health scales with players; boss bar; resets when the
  arena is empty for 10 s or on restart.
- **The chill** (`core.py`, `player/chill*`): seconds in a Pall biome without a light source in either
  hand (or a lit companion within 6 blocks, or warm blocks) build up slowness, then optional darkness
  pulses, then mining fatigue; switchable per world.

## 9. Recovery paths (why nothing can be lost for good)

| Requirement | Recovery |
|---|---|
| Prologue lamp materials | camp chest refills bricks, chain, nuggets and torches while the lamp is unbuilt |
| The letter | the noticeboard always offers a copy |
| Field Book | Field Journal → *Replace a lost Field Book* |
| Chapter 1 lamp | cradle chest refills the lamp if it is neither built nor carried; bells repair themselves; the tower hatches reopen on load |
| Chapter 2 lamp / honeycomb | the orchard keeper gives the lamp again; the apiary shed refills honeycomb |
| Chapter 3 items | the notes prop gives a new copy; the office chest refills the instrument; kiln levers repair themselves; the kiln hatch refills the lamp and the key item until they are used; the workshop refills glass and bars |
| Chapter 4 key item | the kiln hatch keeps another until it is set |
| Encounters | reset cleanly and can be retried; operators can `admin/reset_encounters` |
| Anything else | `admin/refresh` (non-destructive), `admin/force` (last resort) |

## 10. Multiplayer

World-level state, per-player journals and settings; every dialog is per player; encounters scale
with players present; late joiners get a recap and journal sync; world settings can be restricted
to a host (`admin/host`). Tested design size: 1–4 players.

## 11. Performance budget

| Where | Budget / measure |
|---|---|
| Every tick | 7 lines: selector checks for triggers, first join, rejoin, death |
| Every second | `core/second`: ~65 lines, each gated by quest state and/or player proximity; NPC maintenance returns early unless a player is within 72 blocks; the objective marker is re-asserted every 10 s |
| Entities | NPCs: 1 mannequin + 1 interaction (+1 text display for props) each, spawned near players only; ghost displays only for unbuilt parts |
| Encounters | at most 8 Watchers in a Surge; the final encounter adds at most players+1 |
| Restoration | ring conversion of loaded chunks only, ~5 s per district; chunk-load conversion is one pass over the chunk's biome cells |
| Client | Sodium, ImmediatelyFast, Entity Culling in the pack; no shaders |

## 12. Verification tooling

- **`OfflineCheck`** (`mod/src/devtools/…/OfflineCheck.java`): bootstraps the game registries without
  starting a server or world and loads vanilla + the pack through the game's own loaders (functions,
  advancements, dialogs, loot tables, predicates, tags, worldgen). Instantiates every macro function
  with sample arguments, decodes template items/text with the game's codecs, fails on any warning/error
  that mentions the pack; probes terrain on 3 seeds.
- **`tools/simulate.py`**: executes the generated functions with a small interpreter (scoreboard,
  execute conditions and stores, functions with macros and storage, return, schedule) and checks the
  campaign end to end. World-dependent conditions use a fixed policy (no entities / all or no blocks).
  It is a logic simulation, not the game.
- **Game tests** (`mod/src/gametest/java/…/CampaignGameTests.java`): the same scenario plus NPC
  uniqueness and keepsake recording, in a real headless server. Only run with explicit EULA opt-in.

## 13. Feature → file map

| Feature | Files |
|---|---|
| Mod entrypoint, event wiring | `mod/src/main/java/net/palemeridian/PaleMeridian.java` |
| Valley terrain, biomes, sites, roads | `…/world/Valley*.java`, `FixedStructurePlacement.java`, `SiteStructure.java`, `SitePiece.java`, `PMWorldgen.java`, `ValeRoadsFeature.java`, `LampPosts.java`; `tools/layout.json`; `tools/pmgen/worldgen.py` |
| Restoring districts | `…/world/Restoration.java`; data pack flags set in `tools/pmgen/dp/content/*.py` |
| Spawn and world-type guard | `…/world/WorldSetup.java` |
| Operator commands | `…/admin/AdminCommand.java` (`/pmadmin`); `tools/pmgen/dp/core.py` (`admin/*` functions) |
| Site buildings, chests, keepsake placement, POIs | `tools/pmgen/sites/*.py`, `tools/pmgen/houses.py`, `tools/pmgen/buildkit.py`, `tools/pmgen/structure.py`; output `tools/generated/poi.json` |
| Quest/dialog/NPC/blueprint engine | `tools/pmgen/dp/engine.py` |
| Lifecycle, players, HUD, journal UI, chill, settings | `tools/pmgen/dp/core.py` |
| Encounters | `tools/pmgen/dp/encounters.py`; final encounter in `content/chapter4.py` |
| Atmosphere (haze, surge pulse) | `tools/pmgen/dp/atmosphere.py`; biome attributes in `worldgen.py` |
| Story content per chapter | `tools/pmgen/dp/content/*.py` |
| Books and story items | `tools/pmgen/dp/items.py`, content modules |
| Journal recap/people/keepsake pages | `tools/pmgen/dp/ui_pages.py`, `content/journal_pages.py`, `content/keepsakes.py` |
| Chest loot | `tools/pmgen/dp/loot_tables.py` |
| Skins, sprites, icon | `tools/pmgen/art.py` |
| Site preview images | `tools/pmgen/render.py` → `docs/previews/` |
| Lock, client pack, server package | `tools/lock_pack.py`, `pack/lock.json`, `tools/build_dist.py`, `server/*` |
| Checks | `mod/src/devtools/…/OfflineCheck.java`, `tools/simulate.py`, `mod/src/gametest/…` |

# Grand Line Odyssey — Progress Log

Durable log of the overnight build. Newest entries at the bottom of each section. Times are UTC.
Every "verified" claim names the check that verified it; anything not listed as verified has not been tested.
Per-item status (planned / partial / playable / tested) lives in [INVENTORY.md](INVENTORY.md), generated from the code.

## How things are verified

| Check | How to run | What it proves |
|---|---|---|
| Build | `./gradlew build` | Compiles, packages `build/libs/grandline-<ver>.jar` (copied to `dist/` by `./gradlew dist`) |
| Server self-test, phase 1 | `rm -rf run/world run/selftest-*.txt && GL_SELFTEST=1 ./gradlew runServer` | Boots a **dedicated server** on a fresh ocean world and runs scripted gameplay checks; writes `run/selftest-phase1.txt` and `run/inventory.json`, then stops the server |
| Server self-test, phase 2 | `GL_SELFTEST=2 ./gradlew runServer` (same world, after phase 1) | Restart persistence: fruit ledger, NPCs, ships, ship registry; writes `run/selftest-phase2.txt` |
| Client test | start the server with `GL_SELFTEST=client ./gradlew runServer`, then `GL_CLIENTTEST=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient` | A real client (software OpenGL under Xvfb) joins the dedicated server, creates a character, fights NPCs, uses fruits/Haki, sails a ship, visits set pieces and saves `run-client/screenshots/clienttest_*.png` plus `run-client/clienttest-report.txt` |
| Inventory | `python3 tools/gen_inventory.py` (after phase 1) | Regenerates `docs/INVENTORY.md` from `run/inventory.json` and the self-test reports |

## Milestones

### M0–M1 — Skeleton and core (done)
- NeoForge 21.1.259 / MC 1.21.1 project, GeckoLib 4.9.3 dependency, config, data attachments, network payloads,
  admin commands. Verified by build and by every self-test run since.

### M2 — Ocean world (done)
- Custom chunk generator and biome source replacing the Overworld (`level-type=grandline:ocean_world`).
- 71 canon/starter islands at fixed positions plus ~150 generated originals (220+ total), Red Line walls,
  Reverse Mountain canals, Calm Belts, four Blues; settlements with furnished interiors, docks, shipwrights,
  landmark set pieces (23 hand-built, the rest placeholder plinths at 00:57 — being replaced, see below).
- Verified: phase 1 checks world active, island count, terrain heights, Red Line, canal, settlement and Baratie blocks.

### M3 — Characters and races (done)
- 21 races (13 canon incl. 10 playable, 8 original) with stats, scale, reach, step, camera height, race parts.
- Creation screen (name, race, height/build, colours, marks, home Blue, path), permadeath with knockdown/revive,
  graves, arrest/robbery outcomes.
- Verified: phase 1 creates a Giant character and checks scale/attributes, NBT round trip, permadeath archive.
  Client test (01:30) showed the creation screen, a Fish-Man character with race parts, HUD (stamina, level, Beli),
  knockdown by NPCs, robbery by a pirate captain and arrest + jailing by a Marine captain on a real client.

### M4–M6 — Combat, Haki, Devil Fruits (done, expanding)
- Combat Mode, light/heavy chains with timing, guard/parry, dodge i-frames, stamina, poise/stagger, lock-on,
  over-the-shoulder camera; 25 weapon classes, 19 styles; Observation/Armament/Conqueror's with advanced forms;
  8 fruits implemented at 00:57 (Mera, Hie, Goro, Gomu, Bara, Ope, Wolf Zoan, Kaze), official fruit rules.
- Verified: phase 1 executes all registered abilities (169/169 at 01:20), NPC duel, Logia intangibility vs Haki,
  sea weakness, fruit ledger. Client test showed Combat Mode HUD/camera, character screen with fruit/Haki/loadout.

### M7 — Ships (done, 01:20)
- Six classes (rowboat → flagship) with walkable decks, helm/wind sailing, cannons, sectional damage, sinking,
  repairs, below-deck interiors (pocket dimension), shipyard purchase/upgrades/recovery, NPC raider ships,
  Reverse Mountain canal ride.
- Verified: phase 1 launches a sloop at a dock, checks the deck, builds and exits the interior, sails a galleon
  1,602 blocks into newly generated chunks with an NPC carried on deck; phase 2 finds the ship where it was left
  after a restart. Client test (01:31) rendered the galleon, its sails and the ship HUD and sailed it under player
  control.

### Client verification (01:29–01:35)
First run of a real client against the dedicated server. Result: no client crash or render exception;
26 screenshots. Problems it found:
- NPC skins and most item/block models are missing (magenta checkerboard) — textures are being produced by the
  asset agent; must be re-checked after merge.
- Baratie generated on a small sand island instead of the open sea → fixed (new `SEA` island shape).
- After an outlaw robbed a downed player the "knocked down" animation replayed after "get up", leaving the player
  visually prone → fixed (outcome now runs after the knockdown animation).
- `/grandline test ability` could not parse ids containing `/` → fixed (greedy argument).

### Merges from parallel agents (01:40–02:10)
- **Devil Fruit wave 2** (agent): 16 more fruits (Pika, Magu, Suna, Moku, Yami, Gura, Ito, Hana, Bari, Phoenix,
  Bison, Leopard + originals Shio, Kagami, Uzu, Kane) → 24 implemented, 262 abilities. Verified: phase 1 "all 262
  abilities execute — 262/262" and "every implemented fruit damages the dummy — 24 fruits", plus mechanics checks.
- **Asset pipeline** (agent): `tools/gen_assets.py` generates every item/block texture and model, 34 NPC skins,
  projectile textures, race-part texture, block loot, recipes and tags; `tools/check_assets.py` checks coverage.
  Chest loot tables now give themed loot, style scrolls/eternal poses with real targets and Beli pouches.
- **Animations** (agent): `humanoid.animation.json` with 116 clips generated by `tools/gen_animations.py`.
  Not yet seen in a client after merge (client re-run pending).
- **Story, cast and crew** (agent): "The Saltfire Saga" (docs/STORY.md), 111 original named characters spawning on
  their home islands, 156 quests across 42 islands with faction variants, an 18-member recruitable crew system.
  Verified by its own self-test steps (all pass in the merged run).
- Fixed: `.gitignore` had been hiding the `world/build` source package (the pushed tree did not compile from a clean
  clone before 01:45); animation issues the animation agent found (first-person arm leak, off-hand double transform,
  states that never cleared, root motion not scaled by body size); quest objectives for arrests; a Loguetown quest
  that needed a non-existent NPC.

### Island content (01:35–01:45)
- Every island now carries secrets (buried treasure under an X, hidden cellars under boulders, lost shrines,
  shipwrecks on the coastal shelf). Verified: "455 secrets on 211/216 islands", buried chest generated under its X,
  all 12 chest loot tables roll items.
- Bigger settlements (cities 36–60 buildings); ring/atoll islands now get their hamlets.

### Sky and deep-sea dimensions (01:50–02:25)
- `grandline:sky` — the White Sea: Sea Cloud (white-tinted water) over an Island Cloud floor; Heaven's Gate, Angel
  Island (Skypiea town of Sky People), Upper Yard (jungle, Shandora ruins with a poneglyph and sealed vault, the Giant
  Jack beanstalk with the golden bell, a sacrificial altar), Weatheria's lab, the Cloud End, drifting cloud islets.
- `grandline:deep_sea` — 10,000 m down: water to y 250; Fish-Man Island in an air bubble (membrane holds the sea back,
  people and ships pass through), Ryugu Palace, Mermaid Cove, Gyoncorde Plaza, the Sea Forest in its own bubble,
  sunken ruins and reefs. Locals are Fish-Men and Merfolk.
- Routes: the Knock Up Stream off Jaya erupts every 10 minutes and throws ships to the White Sea; the Cloud End drops
  them back east of Jaya; a ship coated at Sabaody (new shipwright upgrade) dives at the dive point and sails the deep
  sea past Fish-Man Island, surfacing in the New World. Crews travel with their ships; the coating bubble keeps them
  breathing and keeps fruit users from being weakened.
- Verified (GL_SELFTEST=dims, 22/22 twice in a row): dimensions load; White Sea terrain and biome; Angel Island and its
  NPC markers; deep sea water column; Fish-Man Island bubble with membrane; no leak after fluid ticks; Knock Up
  Stream → White Sea with crew on deck; registry records the dimension; Cloud End → Blue Sea; coated dive → deep sea;
  crew breathing in the bubble; ascent → New World and coating spent. Phase 2: a ship left on the White Sea is still
  there after a restart.

### Weather (02:25–)
- Local sea weather replaces the global cycle in the ocean world: deterministic drifting storm cells per zone (rain in
  the Blues, storms/fog/snow squalls in Paradise, cyclones in the New World, nothing in the Calm Belt), winter islands
  snow, desert islands stay dry; storms gust and cyclones swirl the wind, batter hulls under full sail and throw
  lightning; a navigator in the crew spots weather from far off. The client renders it with vanilla rain/snow/thunder
  plus fog banks.
- Crew role bonuses at sea (shipwright repairs, helmsman speed, cook, doctor, musician, sniper cannons, lookout).

## Work in flight (background agents)
- Hand-built set pieces for the 34 placeholder landmarks.
- Devil Fruit wave 3: the remaining 19 canon fruits; 20 more original fruits (7 on the roster + 13 new designs).
- Sea Kings and Calm Belt nests, Zoan full-beast models (GeckoLib), island wildlife.

## Known gaps (next priorities)
- Client re-run with the merged textures, animations and new dimensions (screenshots).
- Multiplayer with two real clients at once has not been run (server-authoritative design; dedicated server verified).
- Large flat surfaces show regular terrain contour steps (cosmetic).

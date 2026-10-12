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

## Work in flight (background agents, merged when they report)
- Humanoid combat animations (keyframe JSON for every anim name the code plays).
- Devil Fruit wave 2 (Pika, Magu, Suna, Moku, Yami, Gura, Ito, Hana, Bari, Bison, Phoenix…).
- Textures and data assets (NPC skins, item/block models, fruit textures, GUI).
- Original story, named cast and recruitable crew.
- Hand-built set pieces for every placeholder landmark (34).

## Known gaps (next priorities)
- Sky dimension (Skypiea via Knock Up Stream) and deep-sea dimension (Fish-Man Island) — not started.
- Sea Kings, Calm Belt monsters, weather visuals — not started.
- Fruit roster: 50 defined, 8 implemented at 01:20; target ~38 canon + ~40 original.
- Zoan full-form models (GeckoLib) — not started; Zoans currently use stat forms on the humanoid rig.
- Large flat surfaces show regular terrain contour steps (cosmetic).

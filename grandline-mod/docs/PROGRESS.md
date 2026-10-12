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

### Two real clients on one ship
- Two clients (`GL_CLIENTTEST=mp_a` captain and `mp_b` mate) joined the same dedicated server. The captain launched
  a galleon and sailed it about 90 blocks with the mate standing on deck; the mate stayed aboard, seen from both the
  mate's own view and the captain's (screenshots `mp_a_sailing_with_mate`, `mp_b_deck_while_sailing`). Fixes it
  needed: test characters are civilians (Marine raids derailed it), no raids inside harbours, and a ship render
  fallback so a ship in not-yet-compiled chunk sections is still drawn.

### Ferries and the Sea Train (03:00–03:50)
- Dockworkers sell passage to discovered islands in the same sea; station masters on the Water 7 line run the Sea
  Train. Both are real ships that sail through the world: they round islands on the way (waypoints from the world
  plan), work their way out of harbour round the home island, sweep the hull's width for docks and rocks, and land
  passengers on the destination dock. You pay only once you are aboard. Ships no longer lose way while waiting for
  a chunk ahead to load.
- Verified (GL_SELFTEST=dims, 111/111): 6 routes offered at Shiokaze Isle; all six ferries launched with a
  passenger; 6/6 cleared the harbour and closed 150 blocks within 532 ticks at real ferry speed; the first landed
  its passenger on the destination's dock. (NeoForge's FakePlayer refuses to ride, so townsfolk are the test
  passengers.)

### Merges (03:20–04:10)
- **Landmarks** (agent): the 34 placeholder set pieces are now hand-built; `LandmarkCheck` builds every landmark in
  all four rotations and checks footprint, floors and headroom for NPC markers, chests and interiors. It caught four
  of mine (sky chapel, weather lab, Mermaid Cove, Punk Hazard lab) which are fixed.
- **Race powers** (agent): 82 racial abilities (3–4 for each of the 21 races), racial passives (swim speed, evasion,
  damage traits), a fourth "Race" ability set, NPCs fight with their race's moves. Its own run: 448/448 abilities
  execute, race checks pass.
- **Story** (agent): the Skypiea arc "The Storm Throne" and the Fish-Man Island arc "The Lantern in the Abyss"
  (8 quests + faction variants each), a local story on every canon island (71/71 now have a quest), 44 more
  characters (155) and 61 more quests (217 story quests), `collect` objectives; story characters live in the sky
  and the deep sea too.
- Fixed after the story merge: role givers offered faction-variant quests wrongly (now `QuestRegistry.canOffer`);
  Mary Geoise (a point on top of the Red Line) was never built, peopled, protected or discoverable — point-sized
  places are now included everywhere except terrain sampling (self-test "Mary Geoise is built on the Red Line,
  protected, peopled and discoverable").

### Painted art (04:00–04:20)
- Higgsfield (agreed "mix of code and higgsfield"): mod logo, wanted poster (now opens a poster screen with the
  wanted player's face, name, bounty and faction), parchment sea chart, character-creation backdrop and ten
  paintings of the seas that hang from the vanilla painting item. Sources in [ART.md](ART.md).
- Not yet seen in a real client (client test extended with poster, chart and painting shots; run pending).

### Server load test (agent, merged 05:00)
- `GL_SELFTEST=load`: 20 simulated players join a dedicated server (town walkers, sailors into new chunks, fighters
  using techniques and fruits, players in storms) for 3 minutes; the report has tick time (avg/p95/max), per-subsystem
  and per-entity-type cost, chunk generation, heap and GC, and the slowest ticks with their causes.
- Fixed: the server thread froze for 2–6 s at a time waiting for chunks still generating (notable spawning, island
  discovery at join, race traits, the at-sea check, shipyard water search, encounters, storm lightning) — these now
  only read fully loaded chunks and try again later (worst ticks 4.2–5.8 s → 35–112 ms); every entity query tested
  every ship hull part in the level (now indexed); the ocean world built and threw away natural monsters every tick
  (that category is skipped).
- Not proven: 20 TPS with 20 players. The test machine was shared by several agents (load average 15–58, memory
  kills), so its wall-clock numbers are not representative; it needs a run on a dedicated machine. Give the server at
  least 4 GB.
- After the merge, the tests had to generate their test areas before looking for water (game code no longer blocks on
  chunks); dims suite 112/112 again.

### Extras (04:20–05:00)
- Captain's Handbook (written book, 10 translated pages naming the player's own key bindings) for every new character.
- 43 new projectile sprites (every fruit and race projectile now has its own).
- Racial passives (swim speed, status passives) also for humanoid NPCs.
- Paintings: merchants sell three per island; treasure chests and wrecks may hold one.

## Work in flight (background agents)
- Sea Kings and Calm Belt nests, Zoan full-beast models (GeckoLib), island wildlife.
- Naval combat and ship customisation.
- World polish and missing canon islands.
- Devil Fruit wave 4: 15 more original fruits (originals 25 → 40).

## Known gaps (next priorities)
- Client re-run with the merged textures, animations and new dimensions (screenshots).
- Full phase 1/2 runs keep getting OOM-killed (exit 137) while the agents' test servers share the container's 15 GB;
  they are re-run with a capped heap.
- Large flat surfaces show regular terrain contour steps (cosmetic).

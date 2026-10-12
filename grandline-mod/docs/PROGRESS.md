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

### Server load test (agent, merged 04:30)
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

### World polish and 10 more canon islands (agent, merged 06:05)
- Terrain: theme-aware surface patches (coarse dirt, moss, podzol, red sand, calcite/diorite, basalt/tuff/magma),
  rock on slopes and banded cliff faces, about a block of micro-relief (no more long contour lines on gentle slopes;
  pads, coasts and towns untouched), snow layers on snow and cloud islands, forests restored around towns.
- Decoration on a deterministic grid by theme: palms, boulders, outcrops, logs and driftwood, flowers and bushes,
  ruins, cacti, ice spikes, basalt columns, dead trees, Wano lanterns, graves, candy, cloud puffs; Fish-Man Island
  coral gardens; Skypiea houses coloured to stand out on the cloud.
- New canon islands with hand-built set pieces and a local job each: Tequila Wolf (400-block endless bridge), Germa
  Kingdom (castle flagship and escorts at sea), Rommel, Oykot, God Valley (ruined crater), Kyuka (resort), Lulusia
  (sea-filled blast crater), Prodence, Karai Bari (mercenary guild hall), Mogaro. Existing worlds keep every old
  island's seed, coastline and town (checked by an A/B run and a self-test).
- Verified by the agent: phase 1 343/343 on the merged tree; client shots of the new terrain and islands.

### Real client, full run (05:40–06:05)
- After fixing the test script (the tester had been killed in the NPC line-up: first suffocated in a wall, then
  finished by five elite NPCs at once), the whole solo script ran on a real client against the dedicated server:
  58 screenshots, no client exception. Seen working: creation backdrop, Captain's Handbook in hand, wanted poster with
  the player's face, parchment sea chart, the ten paintings on a wall, combat and fruit effects, the galleon at helm
  and under sail, set pieces, Heaven's Gate and Upper Yard, all three Sea Kings around the galleon, the 11 Zoan beasts
  for players and NPCs, the four wildlife creatures, figureheads, sail emblems, gun crews, grappled boarding and the
  shipyard customise page. Camera positions fixed afterwards for the Red Line and Wano shots (the camera was inside
  the wall / beyond render distance).

### Devil Fruit wave 4 (agent, merged 05:32)
- 15 more original fruits (originals 25 → 40, 78 fruits, 96 new abilities), each with an awakening-only ability:
  Logia Hai (ash), Yako (glow), Shinku (vacuum); Paramecia Kai (dials), Fusen (balloon), Shinju (pearl), Kagi (key,
  locks techniques), Hanabi (fireworks), Hata (flag territory), Taru (barrels); Zoans Bakekujira (mythical ghost
  whale), Raiju (mythical thunder beast), Jaekelopterus (ancient sea scorpion), Meganeura (ancient griffinfly),
  Hippopotamus. Designs in [FRUITS_ORIGINAL.md](FRUITS_ORIGINAL.md).
- Verified by the agent: fixed-seed phase 1 264/0 including "all 664 abilities execute" and "every implemented fruit
  damages the dummy (78)"; 32 wave-4 checks (Logia intangibility vs Haki and their own weaknesses, Zoan forms and
  form-only refusals, 15/15 awakening abilities refused before and working after, Kagi locks, Fusen floats, Kai
  soaks and releases, Shinju halves, Hata brands).
- Not yet seen in a client: the new effects, sprites in flight and hybrid body parts.

### Ferries, second pass (04:40–05:35)
- Full runs showed ferries could still get stuck in some harbours (pinned against a quay, holed by repeated bumps,
  circling a destination whose dock lies close in). Ferries now lie at anchor in open water beyond the home island's
  waters, out along the dock (a launch rows you out), pole off when pinned, are never holed, and work round the
  destination's coast to the dock point.

### Naval combat and ship customisation (agent, merged 05:26)
- Boarding: grappling hooks haul two ships side by side (or tow a prize); NPC raiders grapple and send boarding
  parties; your crew follow you across; beat a ship's crew and stand on its deck to take it as a prize (a shipwright
  buys or refits it; taking a Marine ship raises your bounty).
- Ramming (closing speed and size; iron ram bow upgrade), gun crews who man cannons and lead moving targets, orders
  (V fire at will / hold, N shot type, `/ship fire|hold|ammo|cut|status`), chain shot (tears rigging, slows) and grape
  shot; Marine squadrons in formation, vice-admiral squadrons near the Marine headquarters for big bounties.
- Customisation: 7 figureheads, 8 emblems on flag and sails, flag/emblem/sail colours and hull paint on a new shipyard
  page with a live preview; saved on the ship and in the registry.
- Verified by the agent on the merged tree: phase 1 272/273 (the one failure was the ferry check fixed since),
  phase 2 9/9 (look, registry copy and captured prize survive a restart); naval client shots 41–47 read by eye.
- Not built: NPC ships fighting each other; hired NPC gunners outside your recruited crew.

### Sea Kings, Zoan beasts, wildlife (agent, merged 05:15)
- Sea Kings: three variants (serpent, maw, horned) in three tiers (25–80 blocks long, 180–1000 HP), the head is the
  entity and 14 body segments follow its path (cannons, fruits and melee hit anywhere on the body). They swim deep,
  surface near prey, circle ships, bite hulls and slam their tails; Conqueror's Haki knocks out or scares the weaker
  ones. Calm Belt (windless) is their home with nests every 3,000 blocks; rare in the Grand Line, very rare in the
  Blues; never near Reverse Mountain, the Red Line or settled harbours.
- Zoan full forms: 11 GeckoLib beasts (wolf, leopard, bison, phoenix, elephant, allosaurus, pteranodon, spinosaurus,
  seiryu, orochi, kraken) for players and NPCs, with the beast's hitbox; wildlife: Kung-Fu Dugongs, Lapahns,
  Banana-gators, Sea Cats by island theme.
- Verified by the agent: its phase 1 89/89 (Sea King finds a galleon in the Calm Belt and takes its hull 760 → 460,
  a cannonball on a body segment kills it, Conqueror's Haki knocks out a tier 1 and not a tier 3, wolf form hitbox
  0.60×1.80 → 1.44×1.52 and back); client shots of all three Sea Kings and all 11 beasts.

### Extras (04:10–04:40)
- Captain's Handbook (written book, 10 translated pages naming the player's own key bindings) for every new character.
- 43 new projectile sprites (every fruit and race projectile now has its own).
- Racial passives (swim speed, status passives) also for humanoid NPCs.
- Paintings: merchants sell three per island; treasure chests and wrecks may hold one.

### The living sea (agent, merged 06:42)
- NPC ships fight each other with nobody watching: Marine ships engage the pirate ships they sight (180 blocks),
  pirates take on Marines or bounty hunters only when their side is at least 80% as strong (otherwise they run, and
  fight only when cornered), bounty hunters (new `bounty_hunter` crews and ships) run down pirate ships and wanted
  players, pirates fall on merchantmen. They use the existing gun crews, rams and boarding parties; a chaser yaws off
  every few seconds to bring a broadside to bear. Crews keep to their deck (no more walking off into the sea after a
  foe on another ship; hands who fall overboard climb back), boarders leap home once their prize is beaten, and
  shipmates never turn on each other. A beaten hulk founders after 15 s unless a player is close enough to claim her,
  and every NPC ship that goes down leaves a salvage boat (a chest boat with `chests/wreck`, `chests/marine` or
  `chests/warehouse` loot, sneak-use to open, rots after a day) plus floating wreckage.
- Players may join either side: firing on (or hooking, ramming, striking the crew of) a Marine ship raises your bounty
  once per ship; helping beat a pirate ship earns Marine reputation and a Beli reward; helping beat Marines earns
  pirate standing.
- Merchant traffic: merchantmen sail between settled islands of one sea with the ferries' course-keeping (now a
  shared `Ferries.helm`), never across the Calm Belt or a Red Line; in the Grand Line some sail as convoys with Marine
  escorts on their quarters. A merchant heaves to for anyone alongside or aboard; her captain (`sea_merchant`) sells
  her cargo. Attacking her is piracy (bounty, civilian and Marine reputation); a boarder has her crew to fight, a
  stronger ship makes her strike her colours (crew surrender, cargo over the side as salvage, she can be taken as a
  prize), otherwise she runs or fights back; escorts turn on the pirate.
- Harbour raids: now and then a pirate ship sails into a settled harbour near a player (not starter islands, Marine
  or pirate strongholds), anchors off the dock and lands raiders on the quay. Guards and Marines in town go for them;
  with too few defenders a Marine detachment turns out. After two minutes the survivors row back with their plunder.
  Defenders earn the island's thanks (+40 island reputation, Beli); talking to a raider lets you join the raid
  (bounty, island reputation down, a share of the plunder). The raid ship never fires on the town; nothing breaks
  blocks.
- Fairness and cost: decisions every 5 s per player (a cheap roll first), events spawn inside the simulation distance
  on loaded chunks only, never in the Calm Belt, on the Red Line, near Reverse Mountain, in a harbour (raids aside) or
  in the sky/deep dimensions; at most one event per player every 90 s; every NPC ship counts against the one
  server-wide cap (4 + 1 per player; Marine squadrons now respect it too). NPC ships and crews are transient: they
  despawn with no player within 320 blocks and are refused when a chunk reloads them (a prize you take is kept). Sea
  crews no longer vanish by the vanilla 128-block despawn rule while their ship sails on. `livingSea` config toggle;
  `/ship sea <merchant|battle|hunt|plunder|convoy|raid|status>` for operators.
- Verified (`test/LivingSeaTests`, GL_SELFTEST=livingsea 24/24; full phase 1 329/0 and phase 2 10/0 with
  `level-seed=20261012`): a Marine and a pirate brigantine 60 blocks apart sight each other at once and the battle is
  decided in 400–1000 ticks with nobody near (one sunk or her crew beaten), the loser leaves a salvage boat whose loot
  rolls; bounty hunters chase a pirate sloop (she runs); a merchantman bound for a port behind Shiokaze Isle rounds
  it without ever passing over land; hailing her captain sells cargo; striking her from her deck raises the bounty and
  turns her crew; three shots from a galleon make a sloop strike her colours (crew surrender, cargo boat) and she can
  be taken as a prize; a raid ship sails into a village harbour and lands raiders, holds her fire, guards respond (and
  with no guards a Marine detachment turns out), a joining player takes a bounty and is spared, beating the raiders
  repels the raid and the defender gains 40 island reputation; an NPC ship with nobody near despawns with her crew;
  forced spawn rounds put nothing in the Calm Belt, a harbour or the sky but do on the open sea; 8 forced rounds for 3
  players stop at the cap of 7; a convoy sails with two Marine escorts; phase 2: the merchant and her crew are not
  restored after a restart while a plain rowboat beside them is.
- Not seen in a real client yet (skins, salvage boat, the flow of a battle at 120 blocks).

### Multiplayer fixes (06:20–06:45)
- Two real clients again on the full tree: when the captain logged out the ship vanished under the mate (vanilla logs
  a vehicle out with its only rider) — ships now stay in the world; on a slow client a mate standing still could
  creep across the deck and over the stern — an idle player is now held at their spot in ship coordinates. Verified:
  the mate stayed on deck for ~240 blocks at full sail and after the captain left (`mp_a`/`mp_b`).
- Lunarian, Buccaneer and Snakeneck now have NPC roles (Corrida Colosseum gladiators, a Snakeneck lancer at the Kuja
  gate), so every race appears in the world.

### Island stories and notice boards (agent, merged 07:40)
- Every original island (140+) has its own 2–4 step story generated from its seed by one of 20 templates chosen by
  sea, tier, theme, settlement, dock, hidden spots, landmarks and controlling faction (bandit toll, beast of the bay,
  missing child, crooked officer, two houses, cursed shrine, contraband, lost heirloom, rookie crew, fever season,
  festival, Warlord's due, castaway, the nest, under the black flag, hidden cell, deserter, dead man's map, who did
  it?, stones that speak), with named people (sea-flavoured names) who live on the island, gossip, faction versions
  (Marines arrest, bounty hunters collect, pirates take a cut) and tiered rewards. About 2,600 quests and 740 people
  per world, rebuilt from the seed at start (~300 ms); only progress and who is dead are saved.
- Notice boards open with a right click: letters to other islands, escorts with an ambush at the destination dock,
  three named outlaws per board with wanted posters saying where they hide, beast nests or bandit camps, wreck
  salvage, fish/meals/timber. Townsfolk get sea-flavoured names.
- Verified by the agent: phase 1 386/386, phase 2 11/11, `GL_SELFTEST=stories` 73/73 — all 20 templates played end
  to end (and Marine/pirate versions), determinism across plans, every objective progressable, posters and payouts.
- Not done: the escorted merchant is not a real follower (waits at the destination behind the ambush); the board
  screen and named quest titles have not been seen in a real client.

### Production install (07:05–07:25)
- The built jars were installed on a clean NeoForge 21.1.259 dedicated server (`--installServer`, `mods/`,
  `user_jvm_args.txt`, `run.sh`): no errors, world plan built, "Done" in ~5 s. The real client (Xvfb) then ran the
  whole solo script against that production server: 69 screenshots, no timeout, no server exception.
### Sea activities: treasure maps, fishing, cooking (agent, merged 07:50)
- Treasure maps (`activity/TreasureMaps`): each points at one real island secret (buried treasure, hidden cache, lost
  shrine, shipwreck), chosen deterministically from the world plan near where the map turns up, unlooted secrets
  first; in chest loot (house, tavern, ruin, wreck, cache, shrine, treasure), sold by barkeeps and by merchants on
  pirate/outlaw islands, quest rewards (`ActivityQuests.treasureMap()`); a painted map screen (island, X, compass,
  rough bearing and distance), X marks on the sea chart; opening or breaking the target chest solves it (XP, quest
  objective `treasure`); two torn halves (fishing, wrecks, caches) piece together into a grand map.
- Fishing (`activity/SeaFishing`): a loot modifier on vanilla fishing turns fish into the catch of the sea under the
  bobber (11 catches: Blues small fry, Paradise exotics, Calm Belt/New World giants, deep-sea and White Sea fish, rare
  Sea King fry); island ponds stay vanilla; storms and a crew navigator raise giants and fry, a crew cook and the
  fishing skill (mastery `activity/fishing`) raise quality.
- Cooking (`activity/Meals`): 8 dishes (4 signature) from the smoker/campfire/furnace and the crafting grid; meal buffs
  in the combat buff system (stamina, +15% strength, Haki, swimming, cold and heat resistance; prime dishes 25%
  stronger); dish quality from ingredients, cooking skill and a crew cook, who also runs a galley at sea.
- Fixed on the way: `CrewBonuses.activeRoles` looked crews up by NPC id and so never found a player's crew (crew
  ship bonuses never applied to players); the navigator's storm warning had the same lookup and now uses it too.
- Verified: `GL_SELFTEST=activities` 34/34; full phase 1 377/377 and phase 2 10/10 on the same world.
- Not seen in a client yet: the treasure map screen, chart marks and the new sprites in game.

### The journey: advancements, Den Den Mushi, Buster Call (agent, merged 08:25)
- Advancements (`journey/Journey`, `journey/MilestoneTrigger`; data from `tools/gen_journey.py` into
  `data/grandline/advancement/journey/`): 78 vanilla advancements in their own tab with a code-drawn sea background
  and mod-item icons. Every one is granted through one custom criterion, `grandline:milestone` with an id, fired from
  the hook where the thing happens (one line each in Navigation, FruitManager, Hits.train, Awakening, Haki, Bounties,
  Shipyard, NavalCombat.capture, SeaKing.die, CrewManager.recruit, Quests.finish, SeaFishing, Meals, TreasureMaps,
  CharacterManager.create, ShipEntity helm and Reverse Mountain canal; arrival in the White Sea by the dimension
  event); entering a sea rides Navigation's existing two-second check. Tree: root → the route (Set Sail → Landfall
  → the four Blues and All Four Blues; Reverse Mountain → Paradise → Alabasta → Water 7 → Sabaody → coating →
  Fish-Man Island → New World → Laugh Tale, Sabaody → Holy Land, Paradise → Calm Belt and the Knock Up Stream);
  ships under Set Sail (buy → upgrade → customise, prize, Sea King); Devil Fruit (eat → mastery 50 → awaken); Haki
  (Observation → Future Sight, Armament → Emission → Internal Destruction, Conqueror's → Infusion); bounty (poster
  1M → 10M → 100M → 1B, and Survived the Buster Call); crew (first mate → five → every post filled: navigator, cook,
  doctor, shipwright, musician, sniper, helmsman, swordsman or swordswoman, archaeologist); the saga (Parts I–IV, the
  five endings, hidden); sea activities (treasure map, giant catch, signature dish); Den Den Mushi (call → crew,
  black snail); races (Born of the Sea → one per playable race, 18, and Many Lives for three).
- Advancements belong to the player account: a permadeath keeps them and each new life can add its race. On login
  (and when a character is made) the saved data is credited retroactively: discovered islands in every sea's plan,
  fruit, mastery and awakening, Haki, bounty (also of fallen characters, read from the permadeath archive), story
  quests (any faction variant) and ending flags, the crew, and the account's ships in the registry (bought, upgraded,
  coated, customised, prizes).
- Den Den Mushi (`journey/DenDenMushi`, four snail items; crafted from bone/kelp/copper/string or bought from general
  stores and merchants; `/den`): a personal snail calls anyone online who carries one (a bell rings for 20 s; answer
  or decline with clickable buttons, `/den accept|decline` or the snail); while connected everything either party types
  in chat goes only to the other, prefixed with a snail glyph (a bitmap font, U+E000..E003). A crew snail speaks to
  the player's crew (`/crew invite|join|leave|list` now set the `crewId` the friendly-fire rules already used), a Baby
  Den Den carries one message to anyone and is used up, a black snail (dropped by Marine officers, captains, Vice
  Admirals and Admirals; half the Marine prizes hold one) mutters Marine and bounty-hunter ship positions, headings and
  patrols on foot about once a minute (`/den listen` every 30 s) and overhears Marine reports and Buster Call orders.
  All server-side: no client code.
- Buster Call (`ship/BusterCall`, config section `busterCall`, `/bustercall status|issue|cancel`): a player whose
  bounty passed 500M can be reported by a crime (any bounty raise) within 300 blocks of a Marine-held island or by a
  Vice Admiral/Admiral who traded blows with them and is alive 20 s later; a report becomes a call with 35% chance,
  one call at a time, 60 min cooldown per player, ocean world only. Warning: server-wide announcement, title, horn,
  countdown (60 s). Then up to five warships with a Vice Admiral each (never more than the room under the shared NPC
  ship cap; paddle wheels, AI captained by the call) appear 170 blocks out, converge to stations off the target's
  ship or island and lob mortar shells for 90 s. Shells hurt everyone in the blast (never Marines, World Government,
  the story cast, a downed or jailed player) and hole every ship but the fleet's own, and crater only the wild (outside every
  settlement's bounds, through TerrainChanges, never under a plant, sand or water so every change reverts). Ends:
  survived the bombardment, sank/beat the fleet, or slipped away (advancement + bounty raise of 10%, at least 50M);
  knocked down (the Vice Admiral's shell: the Marines jail you) or gone. The fleet then withdraws and despawns.
  The fleet spawns inside the simulation distance (like the living sea's ships) and keeps reinforcing for 30 s where
  the first look found too little open water.
- Shared files touched (one call each unless noted): `GrandLine` (registry), `GLConfig` (busterCall section),
  `Navigation` (2), `FruitManager`, `Awakening`, `Hits.train`, `Haki.announce`, `Bounties.raise` (2), `Shipyard` (3),
  `NavalCombat.capture` (2), `SeaKing.die`, `CrewManager.recruit`, `Quests.finish`, `SeaFishing`, `Meals`,
  `TreasureMaps`, `CharacterManager.create`, `ShipEntity` (helm, canal), `Shops` (snail offers), `SelfTest` (3),
  `tools/gen_assets.py`, `tools/assetgen/source.py`, README.
- Verified (`test/JourneyTests`, 38 checks; quick run `GL_SELFTEST=journey` 38/38): all 78 advancement files load
  (counted against the resource listing), translated, in one tab under the root with the sea background, mod-item
  icons, only the milestone criterion, and every id the game fires has an advancement; the real hooks grant them
  (creation, Navigation at Orange Town and Alabasta, eating a fruit, mastery 50 via Hits.train, the Awakening Trial,
  Haki unlocks, Bounties.raise 12M/162M/1.16B, the shipwright buying/upgrading/customising a sloop, capturing a beaten
  sloop, a Sea King's death, recruiting Odile, logue_3 and laughtale_2 finishing, a giant catch, a signature dish, a
  solved treasure map; not for a small fish or a plain dish); a permadeath keeps them and a third race grants Many
  Lives; the real login event credits a doctored old character (38 milestones from islands, fruit, Haki, bounty,
  quests and variants, an ending flag, a coated customised ship in the registry, a fallen Mink) and nothing unearned;
  calls between three account players (fake players with real advancement stores, outside the player list): no snail
  there, decline, accept, chat cancelled and routed only to the other party, hang up, ring-out after 20 s, crew snail
  reaches only the crewmate, Baby Den Den used up, black snail names a Marine caravel 21 m off (nothing without one);
  Buster Call refused below the threshold and when disabled, issued by a crime near Shells Town (one at a time) and by
  a Vice Admiral who lived (not by one who died), then a full call at a village dock: warning with no ships, 4
  warships (room 4 under the cap 4, a Vice Admiral each), converge 125 → 88 blocks, 21 shells/18 bursts (the pirate
  bystander hurt, the moored rowboat sunk), survived → advancement and bounty 700M → 770M, fleet withdrawn and gone,
  0 of 1,331 town blocks broken; direct bursts hurt a pirate (35 → 21) and a sloop (140 → 95) but not a Marine,
  change no block in town, and crater the wild (34 blocks) which TerrainChanges restores exactly. Phase 2: the
  account's advancements are read back after the restart. Full run on one fresh world: phase 1 482/482, phase 2
  14/14.
- Not done / not verified: nothing seen in a real client yet (the tab and its background, toasts, item sprites, the
  snail glyph in chat, the clickable buttons, the bombardment); the helm (Set Sail), Reverse Mountain canal and Knock
  Up Stream hooks are not driven by the tests (fake players cannot ride), only credited from saved data; the ring is
  a note-block bell (no custom sound); NPC crew members only count as "hands" for the crew snail; Buster Call ships
  fire their own mortar shells (no gun crews, rams or boarding) and a server restart mid-call drops the call.

### Empty towns and the full run after the journey (08:10–08:40)
- A few generated islands were given a settlement but their first town layout placed nothing (archipelagos, crags):
  such islands now search the island on a grid for buildable ground and lay the town out once more ("every settled
  island has buildings": 178/178). Other towns are unchanged (the first pass draws the same random numbers).
- Ferries also land their passengers when pinned near the dock or after half a minute in the destination's waters.
- Full run on the merged tree with the journey: phase 1 483/483, phase 2 14/14, no exceptions; inventory regenerated
  from it (240 tested, 373 playable, 0 partial).

### Endgame boss fights (agent, merged 09:45)
- Framework (`boss/`): the top of the cast are now boss fights. `BossManager` tracks boss entities (notables listed
  in `Bosses`) and starts a `BossFight` as soon as one is engaged. A fight has a vanilla boss bar (`ServerBossEvent`)
  for the players within 48 blocks and everyone engaged; three phases split at 70% and 35% health: damage that would
  cross a threshold stops on it and starts a 2.5 s invulnerable transition (a roar, or the boss's Conqueror's Haki:
  weak wills black out briefly, equals reel), then the move set changes, phase powers run (existing fruit abilities:
  Orochi regrowth, phoenix rebirth flames, barrier ball, Zoan forms, Haki hardening and black-lightning infusion) and
  the arena changes. Signature attacks are telegraphed (`Telegraph`): a red ground marker (circle at the target,
  slam around the boss, ring with a safe centre, cone, line, rain of circles on every foe, cross), the boss rooted
  in its wind-up animation with a sound and the attack's name on the action bar, markers turning "hot" for the last
  half second; the hit lands on whoever is still inside (dodge i-frames, Observation and guard still apply; most
  are unblockable). Enrage after 6 minutes (+50% damage, faster wind-ups, darkened bar). Haki: Logia bosses are
  intangible to blows without Armament, through the combat system and vanilla melee/arrows alike; bosses with
  advanced Observation (80+) read blows (~30-50%, even unblockable ones) until a stagger breaks their foresight for
  8 s; no boss can be knocked out (Conqueror's, sleep), harmful effects are cut to 2 s and amplifier 1, staggers to
  1 s, knockback resistance 0.75. Players with Conqueror's Haki clash with the boss's (at a phase roar, or when
  they unleash it nearby): holding 70% of its pressure makes the boss reel and breaks its foresight. Scaling for 1-4
  players in the fight: incoming damage divided by 1 / 1.75 / 2.5 / 3.25 (health is capped at 1024), +10% damage
  and faster attacks per extra player, and rain attacks mark every foe. Fair permadeath: while another player still
  fights, a boss neither finishes, robs nor arrests a player it knocked down (it turns to the next foe; the boss and
  its arena cannot hurt the downed player); the usual outcome applies once nobody is left standing. A boss's
  awakened fruit powers are no longer part of its random technique use: they only come as telegraphed attacks.
- 24 bosses (`Bosses`), 3 signature attacks each (phase 1 uses two, phase 2 swaps one for a stronger one, phase 3
  all three), arena scripts and rewards: the Four Emperors (Hrolf's giant shockwaves; Kurogane's tentacles and ink,
  turning Kraken; Barnum's toy bombs and Toy Kingdom; Aurelia's falling stars and supernova), the Fleet Admiral and
  the Three Compass Admirals (Constance's whirlpools, Zenith's light pillars, Nadir's spreading darkness, Meridian
  turning the ground or the sea around his ship into a burning lava crust and raining volcanic bombs), the Seven
  Warlords (Sterling's tilted scales, Hisui's venom pools, Jinpachi's lingering cuts, Leonhart's thunderstorm and
  Sulong, Saltmarsh's salt flats, Lucrezia's reflected beams, Pipistrello's drills), the final arcs (Nox's gas
  clouds, Vespera's closing birdcage, Sigrun's blue flames, Hagane's storm) and the Five Stewards (pockets of slowed
  time, Zeus and Prometheus, tremors and an island tilt, barrier walls, the spreading swamp). Lines per phase, move
  names and relic names in `lang/en_us/bosses.json`.
- Rewards (`BossRewards`, `BossRecords`): each boss's relic, a named, unbreakable, stronger stack of a weapon tagged
  with its boss, drops once per world where it fell (death, yield of a rival, or arrest: seastone cuffs only close on
  a boss at a quarter of its health or yielded); everyone in the fight gets a style scroll and a new Haki scroll
  (`haki_scroll`: awakens that Haki or trains it), once per player per boss, and a large bounty (150 M for the early
  Warlords up to 1.5 B for an Emperor; Marines, agents and bounty hunters who bring down an outlaw are paid instead).
  The existing kill / yield / arrest credit for story objectives is untouched, and the whole group is credited too.
- Client: a "bosses" block at the end of `ClientFx` (markers, hits per palette, roar, enrage, broken foresight,
  Conqueror's clash) with one hook line in `play()`; the Haki scroll sprite in `tools/assetgen/misc_items.py`.
- Shared files touched (hook lines): `GrandLine` (item registration), `NotableHooks.hostility` (everyone engaged is
  the boss's foe), `GLNpc.onKnockedDownPlayer` (deferred outcome), `NpcCombatGoal.tick` (the ordinary AI waits during
  a wind-up), `Haki.tryObservationDodge` (foresight), `HumanoidNpc.techniques` (no unannounced awakened powers or
  random Conqueror's bursts for bosses), `SelfTest` (phase 1, phase 2, quick phase), `ClientFx` (the block at the end
  and its `play()` line), `tools/assetgen/source.py` (item id) and `misc_items.py` (the scroll sprite).
- Verified (`test/BossTests`): `GL_SELFTEST=bosses` 61/61 — the roster and its translations, the bar, both
  thresholds with the invulnerable roar and the move-set swap and arena change, a telegraph that hits the one who
  stayed and misses the one who left, marker shapes, the boss rooted in its wind-up and, left to its AI, using its
  signature attacks, Logia vs plain/vanilla/Armament blows, no unannounced awakened powers or Conqueror's bursts,
  1-vs-3-player scaling (exactly 2.5x), the reset when everyone leaves, the deferred finishing blow and arrest (the
  damage event itself is cancelled while another player fights and not once nobody does), knock-out immunity and
  effect caps, foresight (28/60 dodged, 0/60 once staggered), the enrage, a Conqueror's clash, death (relic, scrolls,
  +1.5 B bounty for both players, `oni_2` completed for the killer and the group, DEFEATED), a rival's yield, cuffs
  refused then an arrest, the Haki scroll, and all 24 bosses' 168 phase/attack runs plus their arena and phase moves
  with no exception and damage from every boss. Full phase 1 with the final game code, three runs: 504/504, 503/504
  and 503/505 (the last also with the final tests; all 61 boss checks passed every time). The FAILs are existing
  timing-dependent tests that passed in the other runs: "crew disbands" (2 of 3 runs: Petra's entity is not yet
  tracked when the test disbands the crew if the Shells Town chunk loads slowly) and the ferry landing within 3000
  ticks (1 of 3, on a heavily loaded container). Phase 2 on the last run's world: 15/15 (relic records and the
  defeated boss persist).
- After the merge (the crew test now waits up to 30 s for the Shells Town area to load before it recruits): full
  phase 1 on the merged tree 544/544, phase 2 16/16, no exception in either server log (09:20–09:36). The
  inventory was regenerated from that run (261 tested, 350 playable, no partial or planned rows).
  `/grandline test boss <id>` spawns a boss next to the caller for manual fights; the client run's shot 35g uses it.
- Not done: nothing has been seen in a real client (markers, hit effects, boss music and the bar colours are only
  checked as packets/state); bosses fight on their home islands as notables, there are no purpose-built arenas or
  ship encounters that bring an Admiral to the player at sea (the sea arena behaviours trigger when a fight happens
  near water); relics are named stacks of existing weapons rather than items with their own models.

### Sound effects and music, synthesised (09:45–10:40)
- Everything is original and computed: `tools/audio/synth.py` is a small numpy synthesiser (additive and
  Karplus-Strong instruments, drums, FFT filters, a convolution reverb with a synthetic room, a bus mixer and master
  chain), `tools/audio/music.py` composes nine tracks as code (melodies in a bar-checked notation, chord
  progressions, accompaniment patterns), `tools/audio/sfx.py` designs 31 effects, and `tools/gen_sounds.py` renders
  them to OGG Vorbis with ffmpeg and writes `sounds.json` (mono effects, stereo streamed music). No samples, no
  transcriptions: the melodies are new.
- Music (about 10 minutes, 6.5 MB): "Wind in the Sails" (a jig-time shanty, the Blues at sea), "Bound for the Grand
  Line" (march, Paradise and the title screen), "Into the New World", "Harbour Town" (calypso, on islands), "Clash on
  the Waves" (battle), "The Emperor's Wrath" (bosses), "Above the Clouds" (Lydian waltz, sky islands), "Beneath the
  Waves" (deep sea, Fish-Man Island) and "Land of Wano" (koto, shakuhachi, shamisen and taiko on the miyako-bushi
  scale). Loudness matches the newer vanilla tracks (about -18 LUFS); effects match vanilla effects (-14 to -19
  LUFS), measured with ffmpeg's EBU R128 meter against the game's own assets.
- `client/GLMusic` (NeoForge `SelectMusicEvent`): a boss bar that asks for boss music plays the boss theme, a fight
  (you hit or were hit by a foe, or a cannon, boss roar or Sea King call within 48 blocks, in the last 12 s) the
  battle theme; otherwise the sea zone from the biome, the island you stand on (the server sends 0 sea / 1 island /
  2 Wano from `Navigation`), the sky or the deep sea. Other dimensions keep vanilla music.
- Effects: `client/FxSounds` layers a sound onto ~90 fruit, Haki and boss visual effects (rate-limited per kind);
  server-side, ship cannons, the Den Den Mushi ring and pick-up, the Buster Call horn, ferry bells, island
  discovery, quest completion, a new bounty poster, knockdowns, Sea King spawns, Armament Haki and boss
  roars/phase changes/wind-ups/enrage use the new sounds. Subtitles in `lang/en_us/sounds.json`.
- Music discs: four tracks (east_blue, grand_line, wano, boss) as jukebox songs with mono copies, in treasure and
  wreck chests and the creative tab.
- Sea ambience (`client/SeaAmbience`, Ambient slider): a seamless 20 s surf loop that swells with the share of open
  water within 32 blocks, a storm-wind loop with the rain/thunder level, gulls by day along shores, and creaking
  timbers aboard a ship (more under way). Ocean world only, above water.
- Verified: `check_assets.py` checks every registered event against `sounds.json`, the files and the subtitles;
  phase 1 checks every event is registered and every disc resolves to its jukebox song. Real client (run 10:10–10:23,
  73 shots, no failures): the game's own Vorbis decoder read all 61 files (11.6 minutes of audio), 40/40 events
  resolved, and the music picked was right at six places (island town, at sea in East Blue, Wano, the sky, the
  deep sea, the boss fight). The client's actual audio output was captured with OpenAL Soft's wave-file device
  (`ALSOFT_CONF` with `drivers = wave`) and matched against the tracks by cross-correlation: the Grand Line theme
  on the title screen and later, the East Blue shanty at sea, the battle theme starting 0.2 s after cannon fire,
  the boss theme in the Emperor fight, and the discovery jingle, bounty fanfare and cannon effects. The regional
  tracks for islands, Wano, sky and deep sea were picked but not heard in that run because another track was still
  playing (regional tracks do not cut in, like vanilla); entering the sky or the deep sea now switches at once.

### Crew bases on claimed islands (agent, merged 10:45)
- Claiming (`base/CrewBases`, `base/CrewFlagItem`): a crew flag (new block + item; crafted from sticks and leather,
  sold by shipwrights and general stores) planted at the heart of an uninhabited original island claims it for the
  player's crew (`CharacterData.crewId`, or the character alone as a crew of one). Refused: canon and starter islands,
  islands with a settlement, the shore (outside the inner 70% of the radius; 95% for rings and atolls), within reach
  of the island's story places (set pieces, island secrets, the local story's sites and people), with a hostile within
  24 blocks ("clear the threat"), anywhere but the ocean world's overworld (never the sky or the deep sea), an island
  someone holds, and more than `claimsPerCrew` (1) per crew. Claims are a SavedData (`grandline_bases`); the claim is
  announced server-wide and every sea chart shows a flag with the crew's name (an anvil-named flag names the crew;
  `/base name`), the viewer's own in gold. `/base abandon` and `/base transfer <player>` (founder, captain or op).
- Building (`CrewBases.onBreak/onPlace/onUseBlock/onExplosion`): inside the claim (a circle of `claimRadius` 64 round
  the flag, story places and secrets left open) only the crew may break and place blocks, open containers, pour
  buckets or light fires, and blasts by outsiders leave the blocks; the flag cannot be broken by hand (an op in
  creative strikes it). `TownProtection` is untouched and still applies everywhere.
- The base: the base chest (new block) opens one 54-slot warehouse per base, shared by the crew, saved with the claim;
  the dock (nearest open water, found at claim time or set with `/base dock`) repairs crew ships within 40 blocks for
  free (5% hull and rigging every 6 s). `/base` (or using the flag) is a clickable chat menu; upgrades cost Beli plus
  materials from the pack and then the warehouse: watchtower (raid ships sighted far out, every crew member online
  warned, raiders glow), dojo (style/Haki training inside the base x1.5, through `Hits.train`), galley (dishes cooked
  inside +1 quality, through `Meals.onCooked`; a hot meal every minute), medical bay (a heart every 2 s), shipyard
  slip (ship upgrades for your ship at the dock at 75%, no coating) and quarters (up to four hired hands: guard,
  sailor, sword master, existing humanoid roles who live at the flag while crew are near, never turn on the crew,
  fight raiders; a dead hand is gone).
- Raids (`ship/BaseRaids`, after `HarbourRaids`/`BusterCall`): every 5 s, a base with a crew member within 160 blocks,
  past its cooldown (40 min; 20 min after the claim) may draw a raid (chance 0.002 per check; config `bases`): a pirate
  ship, or for a wanted/pirate crew a Marine ship, within the shared NPC ship cap, never in the Calm Belt, on open water
  off the shore nearest the flag, captained from here (sails in, anchors) and landing at most six raiders at the
  base's edge. Beaten: repelled, the crew members who fought are paid. Raiders by the flag with no defender (crew
  member up and about, or hand) within 16 blocks for `captureSeconds` (20): the flag is taken (server-wide), retake it
  within `graceSeconds` (60) or the claim is removed (flag down, half the warehouse carried off, the rest spilled).
  Still standing after 3 min: they plunder a few stacks and leave. Raids live in memory (a restart drops one).
- Shared files touched: `GrandLine` (registry line), `GLConfig` (`bases` section), `Navigation` (chart data split out
  as `chartData`, bases list), `ChartScreen` (flags), `Hits.train` (1), `Meals.onCooked` (1), `GLNpc.isHostileTo` (1
  hook), `Shops` (1), `SelfTest` (3), `tools/gen_assets.py`, `tools/assetgen/source.py` (+ new `assetgen/base_items.py`),
  README, lang fragment `bases.json`.
- Verified (`test/BaseTests`, 30 checks in phase 1 and 2 in phase 2; quick run `GL_SELFTEST=bases` 30/30), with
  fake players counted as online: refusals on a canon island, a village island, a story place, the shore, the sky and
  with a bandit near (allowed once it is gone); the real flag item, anvil-named, claims the island (block placed, item
  used, announced, on a rival's chart under the crew's name, a dock found); a crewmate cannot claim a second island, a
  rival cannot claim a held one and a transfer to a crew at its limit is refused; the real block events: captain and
  mate break and place, the rival cannot, nor open a chest, nobody breaks the flag, outside the claim the rival may,
  an Orange Town plot stays protected for the base's captain too; the warehouse is shared (the mate sees the captain's
  gold through a chest menu) and refused to the rival; the dojo upgrade refused poor and short, then built from the
  pack's iron and the warehouse's planks (Beli 100,000 -> 65,000); style and Haki training +15 instead of +10 inside
  only (weapon +10); a dish quality 1 inside, 0 outside; medical bay 8 -> 10 hp inside only; the hot meal; a crew
  sloop at the dock 70 -> 140 hull for free, the rival's untouched; the slip fits fine sails for 9,000; four hands
  hired, the fifth and the rival refused, none hostile to the crew, a killed hand is struck off; no raid without room
  under the ship cap, then exactly one ship; it sails in and lands two raiders inside the base, who fight the crew and
  the hands (and the hands them); killed, the raid is repelled and the captain who fought is paid (the mate not); a
  Marine raid with a watchtower warns both crew members (not the rival), its raiders glow; undefended, the flag is
  taken after 397 ticks (announced), retaken when the captain returns, taken again, and when the grace runs out the
  claim is removed, the flag block gone, the gold carried off, off the chart; a claim transferred to another crew
  (who may build there, the old crew not) and abandoned by its new founder (a non-member refused). Phase 2: the
  fixture claim (crew, flag block, name, dojo and quarters, 7 diamonds in the warehouse, a sailor) and its chart flag
  are back after the restart. Full run on one fresh world: phase 1 513/513, phase 2 16/16, no exceptions.
- Not done / not verified: nothing seen in a real client (flag model and textures, the chart flag, the chat menu);
  the menu is chat buttons, not a screen; the boats rowing in and the march up from a distant beach are not played out
  (the party appears at the base's edge); fruit terrain changes and Buster Call craters inside a base are not blocked
  (they revert anyway); hands get a new name each time they are put down; crew ships are repaired at the dock but not
  otherwise kept (no recall or mooring); a claim whose founder and captain are both gone needs an operator to transfer.

### Faction careers (agent)
- Every starting path is a career with ranks (`career/`: `CareerTrack` ladders, `CareerState` inside `CharacterData`,
  `Careers` rules and hooks, `CareerPerks`, `Squads`, `Warlords`, `Missions`, `CareerWorldData` seats and territory,
  `/career`). The rank is the character's `factionRank` (unused until now); the career belongs to the character, so a
  permadeath starts it over and frees any seat or island it held (the real Clone event).
- **Marines** (Seaman Recruit, Seaman, Petty Officer, Chief Petty Officer, Ensign, Lieutenant, Commander, Captain,
  Commodore, Rear Admiral, Vice Admiral, Admiral, Fleet Admiral): merit from arrests (more for notables, and for the
  bounty brought in), pirates beaten, sea battles won against pirate ships (LivingSea's defeat hook), story quests
  (more for the Marine variant), the enlistment path quest and board jobs; plus a level per rank. Gates from Ensign
  up: the enlistment (path quest), sea victories (Captain 1, Commodore 3), Part II (Rear Admiral), Observation or
  Armament (Vice Admiral), Part III and both Haki (Admiral, three seats per world), Part IV and Conqueror's (Fleet
  Admiral, one seat). Once qualified the Marine is told to report; the promotion talk is a dialogue option with a
  Marine officer senior enough (tier 4 up to Captain, 5 to Vice Admiral, 8 for the Admirals), one rank per talk.
  Perks: rank pay once a day on login or at the turn of the day (200 + 300 per rank); any Marine NPC is a
  quartermaster for Marines (coat, cuffs, ammunition, snails, black snail from Lieutenant, seastone jitte from Ensign,
  a fine sword from Captain; prices through the shop's tier and reputation rules); rest at a Marine base; issued kit
  (the Marine coat at Seaman, a named seastone jitte at Ensign, a named rank sword at Captain, a black snail at Vice
  Admiral); a squad from Commander (`/career squad`: 2–6 Marines, officers and a Vice Admiral at the top; they follow,
  take up the leader's fight, never turn on the leader; five minutes, ten-minute cooldown); a warship from Commodore
  (`/career ship`: an NPC Marine ship of the rank's class on open water nearby, under the NPC ship cap); a Buster Call
  request from Vice Admiral (`/career bustercall <player>`: granted through `BusterCall.report` when the target is
  eligible; once an hour). New ships bought by a Marine fly the gull on white. Crimes: striking a Marine or a
  civilian (first blow, per NPC) and anything that raises the bounty earn demerits; every ten cost a rank (merit cut
  back to the new rank); a Seaman Recruit who keeps at it deserts. The old rule (standing below -300) still makes a
  deserter. Desertion: a pirate with a bounty of 5M per rank held (announced), seats lost, squad sent home, the gull
  struck from their ships, titled "Deserter" until the pirate makes a name.
- **Pirates** (Rookie, Supernova, Emperor of the Sea; Warlord of the Sea as a status): Supernova at 100M with an island
  of Paradise discovered and two deeds (named foes beaten, killed or yielded; Marine ships beaten; the Parts of the
  saga; the path quest); Emperor at 1B, level 50 and an Emperor-tier foe beaten (an Emperor or tier 12) or territory
  held. Promotions are automatic and announced. A Supernova who has not finished the war is visited by a World
  Government envoy (a passive `wg_envoy` NPC, once a day on land; declining keeps them away three days): the letter
  of marque (seven seats) freezes the bounty and makes Marine NPCs, bases and warships stand down (GLNpc, NpcShipAi,
  NavalCombat); duty: three pirates (a pirate ship counts three) every three days, paid with a 10,000 Beli share;
  revoked for a missed quota, any crime or striking the law (the bounty comes back, at least 50M; no second letter);
  the end of the war abolishes it, an Emperor renounces it. Perks: generic pirates respect a Supernova and join their
  banner in conversation (3, a Warlord 4, an Emperor 8; twenty minutes); on pirate-held islands any pirate with a
  poster is welcome and the fence sells supplies (port of call); a Supernova claims an island of the Grand Line with
  `/career claim` (settled, not Marine-held, no defenders near; one, an Emperor three): tribute 4,000 a day and the
  title "Emperor of <island>" (settled islands; crew bases are the uninhabited ones); an Emperor's ships fly their
  Jolly Roger in gold on crimson. An Emperor fought in a boss fight counts for everyone the fight rewards (read from
  the boss records, never an earlier life's).
- **Revolutionaries** (Cell Member, Revolutionary Officer, Army Commander, Chief of Staff — one per world): merit from
  missions, blows against the Government, Marine ships beaten, story deeds; promotion in person with a revolutionary
  (Chief of Staff with a named one); gates: the contact path quest, missions (2, 6, 15), Part III. Missions in turn
  from any revolutionary: incite (talk to three people of a non-Marine island, then its elder), sabotage (three
  Marines near a base, or a Marine ship), liberate (two guards of the Sabaody auction house; three captives freed).
  Perks: safe houses (rest; the law looks away for five minutes), contacts from Officer (supplies, the nearest Marine
  base), funding 250 a rank a day, a cell of three or four from Commander.
- **Bounty hunters** (Apprentice, Licensed, Bronze, Silver, Gold, Black licence) by Beli collected (outlaws arrested,
  half for killed ones, pirate ships beaten); the licence needs the path quest (or 50,000 collected), Gold one notable
  head brought in alive, Black five. Perks: 10% more per licence tier on every outlaw brought in alive; a wanted
  poster used from Bronze tells how far, which way and near which island its target is (exact from Gold; Marines from
  Lieutenant/Commodore read it too); a hunter ship from Gold.
- **Civilians** (Townsfolk, Apprentice, Journeyman, Master, Grand): points in the best of three trades — islands
  charted (explorer, 10 each), poneglyphs read (scholar, 40 each), Beli traded in shops (merchant, 1 per 500); the
  title names the trade ("Master Cartographer"). Perks: the Gazette buys each new chart (150 a rank) and rubbing
  (1,000 a rank and XP), a merchant pays 3% less a rank everywhere, a retainer from Journeyman, a purse on each rank.
- Titles: before the name in chat (`PlayerEvent.NameFormat`) and the tab list (`TabListNameFormat`), on name tags
  (sent with the visuals, applied by the client's NameFormat), on the character sheet with the next rank and the
  progress toward it, and in `/career` with what is still missing and the perks. Big promotions are announced to the
  server. 19 journey advancements (A Calling, then six Marine ranks, Supernova, Warlord, Emperor, three revolutionary,
  three hunter and three civilian ranks) are fired from the promotions and credited from saved data on login.
- Items: the Marine coat (a light chest piece with its own armour material and layer texture: open front, the mark of
  justice on the back, gold epaulettes, blue cuffs) and the letter of marque; sprites from `tools/assetgen/career_items.py`.
  A Captain's Handbook page on careers. Text in `src/main/lang/en_us/careers.json`.
- Shared files touched (one call each unless noted): `CharacterData` (the career field, 3 lines), `CharacterManager`
  (creation, visuals), `GrandLine` (registry), `Bounties.raise` (3), `NpcOutcomes.npcArrested`, `QuestEvents` (4),
  `Quests.finish`, `LivingSea.defeated`, `GLNpc.isHostileTo`, `Dialogues` (2), `Shops` (offers per player, price,
  trade), `WantedPosterItem`, `NpcShipAi`, `NavalCombat`, `Shipyard`, `Archetypes` (the envoy), `Journey` (2),
  `Handbook` (page count; page 11 moved to 12 in handbook.json), `ClientNet`, `CharacterScreen`, `SelfTest` (3),
  `tools/gen_journey.py`, `tools/gen_assets.py`, `tools/assetgen/source.py`, README.
- Verified (`test/CareerTests`, 38 checks in phase 1 and 1 in phase 2; quick run `GL_SELFTEST=careers`: 38/38): ladders and translations; the coat
  (chest armour with its layer texture) and letter; 19 career advancements, every rank milestone an advancement;
  a Marine: Seaman Recruit in chat and tab names, merit from three real arrests, told to report, the talk with an
  officer makes a Seaman (coat, title, milestone; a plain Marine cannot promote); arrests and a kill climb to Chief
  Petty Officer one talk at a time; Ensign refused until the path quest (enlisted), then the jitte; Captain waits for
  a sea battle (a pirate sloop sunk through the hull hook: +30 merit), then announced with the sword; a squad of
  three (loyal, cooldown, dismissed); 2,300 Beli rank pay on login, once a day; the supply shop (coat bought; none
  for a pirate); a junior officer cannot promote a Commodore, Part II/III and Haki gates, Admiral and Fleet Admiral
  seats (a second Marine finds the seat taken); a Buster Call request granted for a 600M pirate (refused for a
  Captain, then on cooldown); a warship called at sea. Crimes: three blows on Marines demote a Petty Officer, more
  crimes make a Seaman Recruit, then a deserter (bounty, announcement, "Deserter", the gull struck); standing below
  -300 deserts with 5M per rank. A pirate: not Supernova at 120M until Navigation at Alabasta and two rivals yield;
  banner and port of call; the envoy's letter (Marines stand down), duty paid, a crime revokes it (bounty back, seat
  freed, hostile again, no second letter); Emperor from a boss fight's record (gold on crimson); a claimed island
  (title, tribute); boss records credit this life only. A revolutionary: path quest, incite, sabotage (a Marine
  ship sunk), liberation at the Sabaody auction house, Officer and Army Commander in person, a cell of three, the
  safe house, contacts, funding. A hunter: licence, +10% payout, a poster tracking a player 1,500 blocks east,
  Gold for a notable head, a hunter ship. A civilian: Apprentice Cartographer from charts, chart and rubbing fees, a
  Grand Merchant's 12% discount. Persistence: two players written to the world's playerdata and read back by the
  player list after the restart (rank, merit, tallies, seat, territory, title). Permadeath through the real Clone
  event frees the Admiral's seat and leaves no career. Full run on one fresh world of the tree merged with the boss fights, sounds and crew
  bases: phase 1 614/614, phase 2 19/19, no exceptions. (A full run before that merge showed 3 harbour-raid FAILs —
  the raid ship still 98 blocks off the dock after 1,799 ticks on a loaded machine; the living-sea quick run on the
  same code passed all 24, and the merged full run passed them.)
- Not done / not verified: nothing seen in a real client yet (titles on name tags, the coat on a player model, the
  character sheet lines, the letter's tooltip); the envoy's daily visit, the duty period, squads following in real
  movement and called ships fighting are driven only in the server test; the Marine base rest and the black snail
  shop entry are untested; balance (merit and pay) needs real play; NPC ships do not follow a called ship's caller.

## Work in flight (background agents)
- Sea jobs: a real escort follower, "sink the ship" and convoy board jobs, quest hooks into sea battles.

## Known gaps (next priorities)
- 20 TPS with 20 players is not proven on dedicated hardware (the load test ran on a heavily shared machine; the
  server-thread stalls it found are fixed). Give the server at least 4 GB.
- The treasure map and notice board screens and the boss bar are now seen in a real client (shots 14d, 14e, 35g);
  crew bases (flag, chart marks, chat menu) are verified only on the server so far.
- The escorted merchant of a board escort job is not a real follower (it waits at the destination behind an ambush).
- Living sea: convoys never cross the Calm Belt; harbour raids plunder only Beli; no quests hook into sea battles.
- NPC ships never fight from hired NPC gunners on player ships (only your recruited crew man the guns).
- Balance (damage, bounty, rewards, spawn rates) is tuned only as far as the automated tests; it needs real play.
- Sound: the music and effects are synthesised, judged by measurement (tuning, loudness, spectra) and by the game
  decoding and playing them, not by ear; nobody has listened to them yet. Many abilities still use vanilla sounds.
- Everything client-visual is checked under software OpenGL at render distance 7: lighting, distance and frame rate
  on a real GPU are untested.

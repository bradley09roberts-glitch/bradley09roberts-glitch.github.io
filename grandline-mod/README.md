# Grand Line Odyssey

An original One Piece RPG mod for **Minecraft Java Edition 1.21.1** on **NeoForge**. The world, its seas, races,
factions and powers follow the source; every character and the whole storyline are original ("The Saltfire Saga").
Private distribution.

- Design decisions: [docs/DESIGN.md](docs/DESIGN.md)
- Progress log and how each feature was verified: [docs/PROGRESS.md](docs/PROGRESS.md)
- Inventory of islands, races, fruits, weapons, styles, ships and characters (each planned / partial / playable /
  tested): [docs/INVENTORY.md](docs/INVENTORY.md)
- Story bible: [docs/STORY.md](docs/STORY.md)
- Screenshots from the automated real-client test: [docs/screenshots](docs/screenshots/README.md)
- Art sources (Higgsfield): [docs/ART.md](docs/ART.md)
- **0.3.0 gameplay overhaul** (combat, fairness, balance, story, crew): the audit ledger with every finding and fix
  [docs/OVERHAUL_AUDIT.md](docs/OVERHAUL_AUDIT.md); controls [docs/CONTROLS.md](docs/CONTROLS.md); balance table
  [docs/BALANCE.md](docs/BALANCE.md); story and recruits [docs/STORY_OVERHAUL.md](docs/STORY_OVERHAUL.md); crew
  [docs/CREW.md](docs/CREW.md); install and upgrade [docs/INSTALL.md](docs/INSTALL.md); saves
  [docs/SAVE_COMPAT.md](docs/SAVE_COMPAT.md); validation [docs/validation/OVERHAUL_VALIDATION.md](docs/validation/OVERHAUL_VALIDATION.md);
  a one-hour test route [docs/TEST_ROUTE.md](docs/TEST_ROUTE.md)
- **0.2.0 building, texture and shader overhaul**: what changed, install, rollback and the validation report:
  [docs/OVERHAUL.md](docs/OVERHAUL.md). Structure ledger: [docs/STRUCTURE_AUDIT.md](docs/STRUCTURE_AUDIT.md); block kit:
  [docs/BUILDING_KIT.md](docs/BUILDING_KIT.md); art direction: [docs/ART_DIRECTION.md](docs/ART_DIRECTION.md); asset list:
  [docs/ASSET_LIST.md](docs/ASSET_LIST.md)

## Install (client)

**Easiest:** import `dist/Grand-Line-Odyssey-0.3.0.mrpack` into the Modrinth App or Prism Launcher (mod, GeckoLib,
Sodium, Iris and the shaders, ready to play). Details: [docs/INSTALL.md](docs/INSTALL.md). Manual install:

1. Install **Java 21** (the Minecraft launcher bundles it).
2. Install **NeoForge 21.1.259 or newer for Minecraft 1.21.1** with the installer from https://neoforged.net
   (choose "Install client").
3. Copy both jars from `dist/` into your `.minecraft/mods` folder:
   - `grandline-0.3.0.jar` — the mod (it replaces `grandline-0.2.0.jar` / `0.1.0`: remove the old jar; back up your
     world first — upgrading is covered in [docs/INSTALL.md](docs/INSTALL.md) and [docs/SAVE_COMPAT.md](docs/SAVE_COMPAT.md))
   - `geckolib-neoforge-1.21.1-4.9.3.jar` — required library (animated creature/beast models)
4. Start the NeoForge 1.21.1 profile and create a new world. **World Type** already shows **Grand Line Ocean World**
   (leave it; a world made with "Default" is ordinary Minecraft and cannot be converted). The mod's ocean world
   replaces the normal Overworld; the Nether and End stay vanilla.
5. On first join you create your character (name, race, height/build, colours, marks, home Blue and path). You start
   with the **Captain's Handbook**, a book that explains the controls (with your own key bindings) and the systems.

Optional shaders: put **Sodium 0.6.13** and **Iris 1.8.12** for NeoForge 1.21.1 in `mods/` and
`dist/GrandLineShaders-1.0.0.zip` in `shaderpacks/` (details and links in [docs/OVERHAUL.md](docs/OVERHAUL.md) and the
[pack README](shaderpack/GrandLineShaders/README.md)).

Recommended: 8–12 GB RAM allocated for a smooth time in big cities; render distance 12–16 on a high-end PC.

## Install (dedicated server, LAN)

1. Run the NeoForge 21.1.259 installer with "Install server" into an empty folder (Java 21), or headless:
   `java -jar neoforge-21.1.259-installer.jar --installServer .`
2. Make a `mods` folder there and put both jars from `dist/` into it.
3. Create `server.properties` with `level-type=grandline\:ocean_world` **before the first start** (an existing vanilla
   world keeps its old generator), and `eula.txt` with `eula=true`.
4. Put `-Xmx4G` (at least 4 GB) in `user_jvm_args.txt`, then start with `run.sh` (Linux/macOS) or `run.bat`
   (Windows). The load test measured a 0.8–1.2 GB live heap with 20 players; 1.8 GB thrashes the garbage collector.
   Every player needs the same two jars. Sized for ~20 players.

These exact steps were run against the built jars on a clean NeoForge 21.1.259 server install: it starts in a few
seconds, builds the world plan and logs no errors, and the automated real-client script (69 screenshots: creation,
combat, fruits, Haki, ships, set pieces, sky and deep sea, Sea Kings, Zoans, naval combat, the living sea) played
through on it with no server exception.

Server options live in `config/grandline-server.toml` (permadeath, knockdown time, Conqueror's chance, PvP, town
protection, fruit terrain changes and reversion, number of generated islands, NPC simulation radius, etc.).

## Controls (rebindable under Options → Controls → Grand Line Odyssey; `/controls` in game)

| Key | Action |
|---|---|
| Left click (tap / hold) | Strike / heavy blow — on anyone you look at, in or out of Combat Mode |
| R | Combat Mode on/off: guard, techniques on 1–9, lock-on |
| Right click (hold, Combat Mode) | Guard; start just before a blow lands to parry |
| Left Alt | Dodge |
| 1–9 (Combat Mode) | Techniques; Tab or the mouse wheel for the next set |
| Middle mouse (Combat Mode) | Lock on (only someone in sight) |
| H / J / K | Observation / Armament / Conqueror's Haki |
| G | Zoan transform |
| C | Character sheet (stats, styles, fruit, Haki, PvP flag, Controls) |
| U | Quest log |
| M | Sea chart (the yellow trail leads to your tracked quest) |
| Z | Minimap on/off |
| V | Crew roster and orders (at a ship's helm: gun orders) |
| N | Crew quick order: focus the foe you look at, else regroup (at the helm: shot type) |
| At a ship's helm: W/S, A/D, X, B | Sails, rudder, anchor, Coup de Burst |
| At a cannon: left click | Fire |
| Sneak + use on a downed ally | Revive (hold use on a downed companion) |
| `/quests`, `/controls`, `/spar` | Active quests; every control; a training bout |

The target frame (top left) shows who you face: rank, name, role and faction, health, how they stand towards you and
how dangerous they are for you. Details: [docs/CONTROLS.md](docs/CONTROLS.md).

## Playing in brief

- **Permadeath with a knockdown**: being defeated knocks you down for 30 s; an ally can revive you, outlaws rob you,
  Marines jail you (break out of the cell to escape). If nobody helps, your story ends: the character is gone, your
  gear stays in a grave and your fruit returns to the world. Create a new character in the same world.
- **The seas**: four Blues → Reverse Mountain → Paradise → Calm Belt (no wind, Sea Kings) → Sabaody → coated dive to
  Fish-Man Island → the New World → Laugh Tale. The Knock Up Stream off Jaya throws ships up to the White Sea
  (Skypiea, Upper Yard, Weatheria); the Cloud End brings them back down. Weather is local: storms, fog, snow
  squalls and cyclones roam the Grand Line.
- **Ships**: buy at any shipwright; walk the deck while it sails; go below deck through the hatch; upgrade, repair,
  recover sunk ships; get a coating at Sabaody to dive.
- **Bounties**: crimes raise your bounty; at each threshold you get your wanted poster (use it to look at it).
- **Paintings**: ten painted scenes of the seas; merchants sell three on each island and treasure hides more.
- **Stories and jobs**: canon islands have their arcs; every original island has its own local story with named
  people (generated from the world seed). Right-click a town's notice board for jobs: letters, escorts, wanted
  outlaws (the poster says where they hide), nests, wrecks, fish, meals and timber.
- **Crew**: recruit named characters through their quests; they follow you, fight with you, revive you and give ship
  bonuses (shipwright, helmsman, cook, doctor, navigator, musician, sniper...).
- **Treasure, fishing, cooking**: treasure maps (chest loot, shady dealers, quests; two torn halves make a grand map)
  lead to a real island secret: use one to see the island and its X, which the sea chart marks; opening the chest
  solves it. A rod cast at sea lands that sea's own fish (a fishing skill raises their quality; storms and a navigator
  bring up giants and Sea King fry). Cook them at a campfire, smoker or furnace, or into dishes in the crafting grid,
  for meal buffs (stamina, strength, Haki, swimming, cold/heat resistance); a crew cook cooks better and feeds you at sea.
- **The journey**: an advancement tab tracks your voyage (seas, fruit, Haki, bounty, ships, crew, saga, endings, races); it belongs to your account and survives a permadeath.
- **Den Den Mushi**: use a snail to call anyone carrying one (`/den`); crew snails talk to your crew (`/crew invite`), Baby Den Den carry one message, black snails from Marine officers overhear the Marines.
- **Music and sound**: nine original tracks follow where you are and what you do (a shanty in the Blues, a march in
  Paradise, the New World, island towns, Wano, the sky islands, the deep sea, battles and bosses); Haki, Devil Fruits,
  cannons, the Den Den Mushi and bosses have their own sounds. Four tracks are music discs hidden in treasure and
  wrecks. All of it is synthesised by `tools/gen_sounds.py`.
- **Buster Call**: past 500M Beli, a crime by a Marine base or a Vice Admiral who lived may bring a rare, announced fleet bombardment; outlast, outrun or sink it (config `busterCall`).
- **Sea jobs**: an escorted merchant walks at your side, rides your ship and must reach the destination's dock alive
  (talk to them there); "Sink the ..." jobs name a pirate ship last seen in a stretch of open sea (sink her or take her;
  Marines get a prize bonus); convoys need your ship beside a merchantman until she makes port, pirates fall on her
  partway; the Privateer's Commission pays for any pirate ship beaten. `/jobs` lists your jobs, `/jobs abandon <id>`
  drops one. Your sea chart (M) marks where the named ship was last seen, the convoy's port and her merchantman, and
  the escort's destination.
- **Hired gunners**: a shipwright or dockworker signs on gunners for your ship (talk to them, or `/gunners` beside
  them) for a one-off fee; they man your cannons and, while your order is fire at will (V) and you are aboard or near
  to hand out shot, fire on pirate ships (and on Marines while you are wanted), never on merchants or players.
  `/gunners dismiss` sends them ashore.
- **Harbour raids**: raiders who land carry off goods from the town's market (its shops run short until restocked a
  day later); beat them and they drop what they took.
- **Crew bases**: plant a crew flag (shipwrights sell it) at the heart of an uninhabited original island to claim it for your crew (on everyone's sea chart); only your crew builds there, a base chest is your shared warehouse, the dock repairs your ships for free, `/base` builds a watchtower, dojo, galley, medical bay, shipyard slip and quarters for hired hands; raiders may come while you are near, and an undefended flag can be captured (config `bases`).
- **Careers** (`/career`): every path has ranks and a title shown in chat and the tab list. Marines earn merit (arrests, pirates brought in, sea battles, the saga) and report to an officer for promotion, Seaman Recruit to Fleet Admiral (pay, supplies, the coat, a squad, a warship, Buster Call requests; crimes demote, then desert); pirates rise from Rookie to Supernova and Emperor with bounty and deeds, may accept the Government's letter of marque (Warlord) and claim territory; revolutionaries run missions; bounty hunters earn licences (better payouts, poster tracking); civilians make a name charting, reading and trading.

## Admin and test commands (op)

`/grandline island list|here|tp <id>`, `/grandline fruit give|set|remove|awaken|ledger`, `/grandline char info|level|
mastery|style|haki|conqueror|beli|reset`, `/grandline npc <role> [tier]`, `/grandline quest <id>`,
`/grandline terrain revert`, `/grandline test env|ship <class> [helm]|sail <0-3>|anchor <bool>|ability <id>|storm|
inventory|calm <bool>`, `/grandline test place <type> <style> [rotation]|showcase <style>|kit|view <target> <n>` (these
four build into the world where you stand: use a test world), `/bustercall status|issue <player>|cancel <player>`, `/base raid|remove <island>`, `/career admin merit <n>|rank <n>|envoy|seats`.

## Building from source

Requires JDK 21. From this folder:

```
./gradlew build          # builds build/libs/grandline-<version>.jar
./gradlew dist           # copies the jar into dist/
python3 -I tools/gen_lang.py      # regenerate language fragments after adding content
python3 -I tools/gen_assets.py    # regenerate textures, models, loot tables, recipes and tags
python3 -I tools/gen_animations.py
python3 tools/gen_sounds.py       # re-render the music and sound effects (needs numpy and ffmpeg with libvorbis)
```

Generated files (textures, models, data, sounds) are committed, so a plain `./gradlew build` needs only the JDK.

### Automated verification

| What | Command |
|---|---|
| Dedicated-server gameplay self-test | `rm -rf run/world run/selftest-*.txt && GL_SELFTEST=1 ./gradlew runServer` → `run/selftest-phase1.txt` |
| Restart persistence | then `GL_SELFTEST=2 ./gradlew runServer` → `run/selftest-phase2.txt` |
| Sky/deep-sea quick test | `GL_SELFTEST=dims ./gradlew runServer` |
| Treasure maps, fishing, cooking quick test | `GL_SELFTEST=activities ./gradlew runServer` → `run/selftest-phaseactivities.txt` |
| Careers quick test | `GL_SELFTEST=careers ./gradlew runServer` → `run/selftest-phasecareers.txt` |
| Sea jobs quick test (escort follower, named pirate ships, convoys) | `GL_SELFTEST=seajobs ./gradlew runServer` → `run/selftest-phaseseajobs.txt` |
| Sea polish quick test (hired gunners, raid plunder, chart marks, convoy cap) | `GL_SELFTEST=seapolish ./gradlew runServer` → `run/selftest-phaseseapolish.txt`; then `GL_SELFTEST=seapolish2` for its restart check |
| Real client (software GL) | server: `GL_SELFTEST=client ./gradlew runServer`; client: `GL_CLIENTTEST=1 xvfb-run -a ./gradlew runClient` → `run-client/screenshots/` |
| Two clients on one ship | as above with `GL_CLIENTTEST=mp_a ./gradlew runClient` and `GL_CLIENTTEST=mp_b ./gradlew runClient2` |
| Server load (20 simulated players) | `GL_SELFTEST=load ./gradlew runServer` (`GL_LOADTEST_PLAYERS`, `GL_LOADTEST_SECONDS`) → `run/loadtest-report.txt` |
| Inventory document | after phase 1: `python3 tools/gen_inventory.py` |
| Every settlement building (type × style × rotation) and every landmark | `GL_SELFTEST=landmarks ./gradlew runServer` → `run/selftest-phaselandmarks.txt` |
| Structure dumps for the audit and offline renders | `GL_SELFTEST=structures ./gradlew runServer` (`GL_DUMP_ONLY`, `GL_DUMP_TOWNS`) → `run/structdump/`; then `python3 -I tools/render_structure.py run/structdump -o <dir>` |
| Architecture captures in the real client | server `GL_SELFTEST=client`; client `GL_CLIENTTEST=arch GL_ARCH_TARGETS="name=town:<island>@0;..."` |

`run/server.properties` must contain `level-type=grandline\:ocean_world` (the test harness uses port 25570).

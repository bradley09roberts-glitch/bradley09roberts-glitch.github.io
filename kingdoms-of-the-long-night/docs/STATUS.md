# Status of v0.1.0 (first playable release)

Built overnight, 12-13 October 2026. This file says plainly what is **working**, what is **partial / simplified**, and what
is only **planned**. "Working" means implemented *and* exercised by at least one of the tests listed at the bottom.

Versions: Minecraft **1.21.1**, NeoForge **21.1.259**, Java 21, mod `longnight` **0.1.0**.

## Working

### World and history
- **World generation of a living world** (`WorldBuilder`, `SiteFinder`): on a new world the mod surveys terrain from
  generator noise (no chunk generation), picks sites by flatness, water and biome (looking further out, ring by ring,
  when a sea-heavy seed offers too few dry sites near spawn), and creates 7 established
  settlements grouped into realms, 2 small camps of settlers who start with only tents, 2 outlaw bands, and 3 ruins
  of lost kingdoms. Settlements have backdated founding years; realms have rulers, heirs, predecessors and one
  remembered war. Typical result: ~190-210 people, 6 realms (incl. outlaw bands).
- **Eight cultures** (Valemen, Tidefolk, Aszari, Eldrin elves, Dunmar dwarves, Burrowfolk, Ashborn orcs, Skrell
  goblins), data-driven: building palettes, roof styles, biome preferences, faith mixes, temperaments, body sizes
  (dwarves/smallfolk smaller, elves taller), lifespans (elves ~260, orcs ~55), name sets with distinct family styles
  (surname, patronymic, clan, house, place).
- **Eight faiths** (data-driven) with clergy titles, festivals, burial customs that change the grave (sapling,
  cold pyre, candle, tomb stone, amethyst), and marriage strictness.
- **Ruins** are raised as decayed buildings (holes, moss, cracked stone) with a chest of lore books.
- **33 lore books** (common to forbidden) generated as real written books; the Ender Dragon's nature is
  contradicted between books on purpose. World bible: [WORLD_BIBLE.md](WORLD_BIBLE.md).
- **Vanilla villages disabled** (structure set frequency 0 + biome tags) so that every person belongs to one society.

### People (identity owned by `longnight`)
- Persistent person records: name, family name per culture, sex, orientation, culture, faith, age, temperament
  (7 traits), needs (food, rest, safety, social), health and illness, skills per profession (improve with practice),
  profession and workplace, home, coins, gear tiers, title, prestige, loyalty, relationships (opinion and
  familiarity, max 32), memories (24 most significant), what they are doing and why they are stuck.
- **Bodies only near players**: `CitizenEntity` is spawned from the record when a player is within ~112 blocks of
  the settlement, and discarded when they leave. Bodies are never saved to chunks, so a person exists once, and the
  dead cannot come back through a save/reload. Caps: 40 bodies per settlement, 160 total (configurable).
- **Layered appearance**: body (20 skin/face variants), clothes tinted by culture/trade colour (10 outfits), hair
  tinted by personal colour (10 styles, greying with age), mail/plate overlays for soldiers, children scaled by age.
- **Daily routine** (`CitizenBrain`): sleep in their own bed at night (real beds), work in the day, gather at the
  tavern/well/market in the evening, children play, guards patrol (half of them on night watch), non-fighters flee
  monsters and raiders and shelter at home.
- **Blocked tasks are explained**, never silent: "I have no stone to work with", "The stores are full; we need
  another storehouse", "I can't find a way to the quarry", "There are no more trees near the lumber camp that we may
  fell", "My workplace is damaged or gone". Shown in conversation and on the notice board.
- **Conversation** (chat UI): greeting depends on their mood, your standing and their memories; topics News (only
  what has reached them), Work, Family, Life (friends, rivals, opinion of the ruler), Town.

### Work and economy
- **20 ledger commodities**, **27 professions** with production recipes (farmer, lumberjack, quarrier, miner,
  fisher, hunter, shepherd, miller, baker, brewer, carpenter, blacksmith (iron, tools, weapons, armour), weaver ...)
  and service roles (merchant, innkeeper, priest, healer, teacher, scholar, gravedigger, steward, guard, soldier,
  builder, ruler).
- Chains: grain -> flour -> bread; logs -> planks/fuel; ore + fuel -> iron -> tools/weapons/armour; wool -> cloth.
- Productivity = skill x tools (tools wear out and are restocked from the ledger) x mood x health x season.
- **Single source of truth**: each settlement's ledger. Embodied workers produce through the *same* recipe in 8
  "work cycles" a day; the daily update works, for each person, only the cycles their body did not work (unwatched,
  walking, stuck). Watching a settlement neither adds to nor takes from what its people make, and nothing is
  produced twice.
- **Storage limits** (storehouses, granary), spoilage of perishables, food consumption with variety, ale.
- **Prices from supply and demand** (targets from population, construction bills and workers); buy at 125%,
  sell at 75%; treasury limits what a settlement can buy from you.
- **Coins** (penny, mark = 10p, crown = 100p) enter the world only through trade; exact change; no minting.
- **Caravans** between settlements: carry surplus to shortages, are paid at the destination, carry news both ways,
  can be robbed by outlaws on roads near their camps. When a caravan's position on its route comes within ~96 blocks
  of a player, its real leader appears walking the road with a pack mule (bodies vanish again out of sight).
- **Roads**: every settlement is linked to its two nearest neighbours; roads are laid lazily as chunks load near
  players (three-wide dirt path over natural ground only, never through claims or anything built; rivers and
  shallow water are crossed on a railed plank bridge, deep lakes and the sea are not), with a signpost at each
  settlement's edge naming the destination and distance. Worlds created before roads existed get
  their roads planned on the next start.
- **Taxes** from settlements to realms; soldiers cost upkeep; working people earn a little coin.

### Settlements and construction
- **30 building types**, generated parametrically in each culture's style (timber-framed Valemen houses, slate-roofed
  Tidefolk, flat-roofed Aszari sandstone, Eldrin birch and teal, Dunmar stone with iron-barred windows, turf-roofed
  Burrowfolk burrows, Ashborn mud-brick and bone, Skrell mossy shacks): houses, farms (real farmland, water and
  crops), lumber camp, quarry, mine shaft, fishery, hunting lodge, pasture (with sheep), mill, bakery, carpentry,
  smithy, weavery, tavern, storehouse, granary, market (stalls + bell), shrine and temple (faith-specific altar and
  stained glass), watchtower (ladder, bell), barracks, town hall, school, library, house of healing, graveyard,
  well (with notice board), builder's yard, campsite. Signboards carry names ("The Drowsy Kettle").
- **Camps bootstrap themselves**: until they have a lumber camp and a quarry, idle hands gather deadwood, fieldstone
  and kindling; camps build a lumber camp and houses first. (Found by the 200-day test: camps used to stall forever.)
- **What to build is decided from needs**: housing, food, materials, storage, faith, safety (watchtowers after
  raids), then institutions as the population grows; the ruler's agenda shifts priorities. Tier grows from camp to
  hamlet, village, town and city when population and institutions allow.
- **Progressive, visible construction**: builders walk to the site and place blocks one at a time. **Every blueprint
  step is paid exactly once** (tested), in the distance or in front of you; the world catches up with distant
  progress when you arrive.
- **Respect for players**: plots are refused if anything built by a player stands on or above them; builders never
  overwrite non-natural blocks; nothing is built, cleared or felled inside a Boundary Stone claim; lumberjacks
  only fell natural trees (trunk on soil, crowned by naturally grown leaves) and replant.
- Terrain handling: plots are pre-checked from generator noise, terraced foundations on slopes, stone footings on
  shorelines, unusable plots relocate, settlements spread outward when crowded.
- **Repair**: when a builder has no project, they look for blocks missing from finished buildings (broken by
  raiders, creepers or players) and walk over to put them back, paying the materials again from the ledger (the
  block really was lost). If the stores lack them, they say so ("We need materials to mend the tavern"). Distant
  settlements mend raid damage abstractly for a fifth of the building's bill. Never inside a claim.

### Life, death and families
- Daily: friendships and rivalries from compatibility (temperament, faith, culture, kindness), courtship, proposals,
  weddings (same-sex couples exist; faith may be adopted on marriage), births (opposite-sex couples, fertility by
  lifespan, food and housing), children apprenticed to a parent or master and learning their trade, coming of age,
  illness (healers help), starvation, old age.
- **Death is single and final** (`WorldSim.kill`, idempotent; tested): spouses, children, parents and friends
  remember; coins are inherited; offices pass on; the body is removed; a **gravestone** with name, dates, cause,
  faith rite and epitaph is placed in the burial ground, with the faith's mark beside it.
- Generational pacing is configurable (`daysPerYear`, default 8: one in-game day is one month).
- **Funerals**: the day after a death, family, friends, the priest and the gravedigger gather at the burial ground
  around midday ("mourning Aldric").
- **Festivals**: each faith keeps its holy day in its month (the Lantern Vigil, the Day Without Shadow, Greenvow,
  Hall Night, the Great Burning ...) and every settlement keeps the Harvest Home in Reaping. A feast uses the
  stores (a poor town keeps a thin feast), lifts spirits, is remembered, and draws people to the shrine or temple.
  The Veiled Choir keeps its rites in secret.

### Politics, threats and war
- Realms: monarchy, lordship, council, clan, republic, theocracy, outlaw band; rulers' temperaments become agendas
  (fortify, expand, conquer, trade, tax, piety, recover); succession by government type (eldest child; council
  choice; strongest; richest; most devout); disputed successions can split a realm; low loyalty and heavy taxes can
  cause a settlement to rebel; relations drift with culture, proximity and trade; trade pacts and alliances.
- **Wars** need an aggressive ruler, bad relations and a strength advantage (rare by design); battles every other day
  kill or wound real soldiers and levies; war score leads to ceded settlements, reparations or a weary peace;
  conquered people lose loyalty.
- **Outlaw raids with warning**: rumours (days ahead), scouts and smoke the day before (earlier with a watchtower),
  the bell and horn at dusk. Near a player the real band members walk in from the direction of their camp and fight
  guards, who fight back; raiders steal from the stores; the raid ends when they are dead, routed or at dawn. Far
  away it is resolved abstractly with real casualties. No raid before day 5 (configurable).
- Monsters hunt townsfolk who are outdoors at night; natural monster spawns are suppressed inside settlements
  (lit, watched streets).
- **Crime**: witnesses matter. Assault and vandalism cost standing; murder makes the watch hunt you for days and
  becomes news; killing outlaws earns gratitude. Warrants are saved with the world, so a restart is no pardon.

### News and knowledge
- Every event is news at its origin; travellers spread important news to neighbours (taverns help); caravans carry
  everything their town knew; royal messengers carry grave news across a realm and to allies. People only retell
  what has reached their settlement, with a delay by sociability. Your journal only lists places you have visited
  or heard of (you learn of places by asking for news).

### Player paths
- **Onboarding**: a new player gets the Traveller's Journal and *A Traveller's Primer*, a short in-world book that
  explains talking, trading, contracts, raids, claims, founding and wards. Looking at a person shows their name,
  trade and current activity on the action bar.
- Stranger, trader (storehouse market), benefactor (gifts), citizen (`Join` at standing 20), contractor (notice-board
  contracts), bounty hunter (outlaw bounties), land-holder (Boundary Stone), founder and lord of a new settlement
  (Founding Charter + Hearthstone; three settlers arrive; `/longnight plan`, `/longnight rename`).

### Magic and the Long Night (first layer)
- **Hearth-witchery wards**: Ward Stone + Ward Chalk stop natural night-spawn within 24 blocks for 3 days.
- **The Long Night arc**: omens (stage 1) and spreading blight that lowers harvests (stage 2) come with time; the
  later stages advance only if **Nahire** (the Ender Dragon) is slain, which every star-reader feels. Realms turn to
  fortification. Arcane magic for players comes from **Ars Nouveau** in the pack.

## Partial / simplified (honest list)
- **UI is chat-based** (clickable lines). No custom screens yet.
- **Workshop work is "stand at the station and work"** with arm swings and a produced-goods sparkle; only farmers
  (harvest/replant real crops), lumberjacks (fell and replant real trees) and builders (place real blocks) act on
  the world. Miners, quarriers, fishers etc. do not dig or fish physically.
- **Hauling is mostly abstract**: the ledger is shared by the whole settlement; labourers walk between stores and
  sites but carry nothing.
- **Battles between realms and distant raids are abstract** (no armies on the march yet). Caravan bodies follow the
  straight route toward their destination; roads are straight lines (no curves around hills; gaps over deep water
  and the sea). Roads and bridges are laid by the world, not built by people, and cost the ledger nothing.
- **No task planner** beyond per-profession routines; multi-step needs are handled by the economy (e.g. a smith
  without ore reports it; the settlement plans a mine) rather than by an individual planning a chain.
- **Pathfinding** uses vanilla navigation with waypoints for long trips; if someone is truly boxed in for ~40 s they
  step over the obstacle (a short teleport) rather than starve. Some "I can't find a way" messages still occur on
  very rugged terrain.
- **Diplomacy** is automatic between NPC realms; the player cannot yet negotiate, take titles or serve a ruler.
- **Player realm**: the founder steers by giving materials and requesting buildings; there are no laws, taxes or
  appointments to set yet.
- **Religion** affects burial, marriage, festivals, shrines/temples and buildings; pilgrimages, conversion
  campaigns and schisms are not simulated yet.
- **Long Night stages 3-5** (undead hosts, failing sun) have news only; their world effects are planned.
- **Culture "style" of player-founded settlements** is taken from the nearest realm.

## Planned (designed, not implemented)
See [ROADMAP.md](ROADMAP.md) and [DESIGN.md](DESIGN.md): proper GUI screens; embodied haulers and armies on the march;
individual task planning; walls, gates and building upgrades; pilgrimages and schisms; titles, fiefs, laws, trials and
player diplomacy; dragons and great beasts; runesmithing, druidry, ember-shamanism and dark powers with costs;
Long Night late stages; optional continue-as-heir dynasty mode (separate from strict hardcore); optional
bring-your-own-key AI dialogue that only rephrases (never decides).

## Tests run (and what each proves)

| Test | Command | Result |
|---|---|---|
| Compilation | `./gradlew build` | passes |
| JUnit simulation tests (9): content loads; generation consistent; stockpile atomic; construction charged exactly once; construction stops and explains when short; 60-day run keeps all invariants (births, deaths, marriages, trade, raids); kill is final and idempotent and conserves coin; save data round-trips (including warrants) and keeps running; work a body did in front of a player is not produced again by the daily update | `./gradlew test` | **9/9 pass** (60 days: ~300 alive, 46 dead, 63 born, 301 news) |
| GameTests (7, in a real server world): builder pays once per block and the blocks exist; never builds over a player's chest; trade conserves goods and coin and refuses impossible purchases; the dead stay dead, are buried under their name and get no body; wards block natural spawns; every blueprint for every culture generates inside its footprint; a player founds a settlement (charter consumed, 3 settlers, player realm never gets an NPC ruler, the well avoids the hearthstone); a broken block of a finished building is found and repaired at its block cost, nothing else is reported missing, and a hole inside a player's claim is neither reported nor charged | `./gradlew runGameTestServer` | **7/7 pass** |
| Long-run test: 200 simulated days (25 years at default pacing) | `./gradlew test --tests com.longnight.LongRunTest` | **pass**: population ~250 -> ~510 (no collapse/explosion), ~110 children grew up, ~200 married, 200+ buildings completed by the settlements themselves, ~15 new camps founded (several became hamlets, some as breakaway realms), 1-2 wars, invariants hold every 25 days, save ~470 KiB compressed, reloads cleanly |
| Dedicated server scenario (RCON, fresh hardcore world, watchdog on): boot; world history; invariants; observed settlement materializes, bodies spawn, pre-existing buildings raised; invariants with bodies (one body per person); **tick time with a town embodied**; **cost of a simulated day**; 4 simulated days; a physical raid; save; **restart**; identical people/death/settlement counts; invariants after reload; no mod errors in logs | `python3 -I tools/test/server_scenario.py` | **17/17 pass** (runs 11, 12 and 13; run13 on the final code). Run10 failed 3/17 and caught a real bug (people getting a second body when chunks load asynchronously), fixed in run11. |
| Performance on this container (4 vCPU Xeon 2.8 GHz, no GPU), dedicated server, one town of 24 embodied people | scenario + `/longnight admin perf` | average tick **3.2-5.8 ms** over runs 11-15 on two different towns (P95 7-18 ms, P99 10-24 ms; 20 TPS needs < 50); longnight's share 1.1-3.0 ms (citizens 0.05-0.11 ms each, world handler 0.02 ms). Path-finding dominates: before rationing path requests (run9) citizens cost 0.33 ms each; on a rugged town (Ilvaiath) they still cost 0.3 ms each until citizens who make no progress were made to re-plan less often (run15: 7.9 -> 3.0 ms for 26 people). **Worst single longnight tick: 92 ms** (run13), down from 6.9 s when a town was first raised (it read blocks in chunks that were still generating; found with the slow-tick log and stack sampling, fixed). A simulated day of the whole world (~200 people, 14 places): 0.06-0.18 s. |
| Headless dev client (Xvfb + Mesa llvmpipe GL 4.5, real Minecraft client with longnight only): loads the scenario's saved world, flies to the largest settlement, waits for it to materialize, screenshots day/street/person/night | `xvfb-run -a ./gradlew runClientAutoshot` | an Eldrin town materialized (36 bodies, 38 buildings); screenshots `tools/test/screenshots/dev-client-*.png` |
| **Full modpack + longnight, dedicated server** (performance preset, Tectonic+Terralith terrain, watchdog on) | `tools/pack/server_test.py --with-longnight yes` | **PASS** (three runs, final release included): 40 mod ids loaded, 0 ERROR lines, 0 exceptions; world history (14 places, ~215 people, 8-9 realms) written in the background in 38-52 s while the server stayed responsive; `VALIDATE OK`. One random seed (325732621701581716, mostly sea) first gave only 4 settlements; after the survey fix the same seed gives 14 settlements, 214 people, 9 realms. |
| **Full modpack + longnight, real client** (installed from the built .mrpack like a launcher; Xvfb + Mesa software GL) | `LONGNIGHT_AUTOSHOT=1 tools/pack/client_test.py --mrpack ...` | **PASS** (five runs): joined the world; a 39-building Dunmar town among Terralith giant spruces, a 32-building Skrell town on a mangrove coast and a 34-building town among giant mushrooms materialized with 28-35 people; Jade names citizens and the look-at hint shows "name, trade - activity" (`fullpack-lookhint-jade.png`); screenshots in `tools/test/screenshots/fullpack-*.png`; 0 fatal, 2 known-cosmetic ERROR lines (GeckoLib animation, Xaero's offline version check). The slow-tick log found three real stalls in longnight on the way (blocking chunk reads while raising a town, while laying roads, and terrain-noise sampling during relocation), all fixed: the worst longnight tick while raising a town went 6.9 s -> 0.9 s -> 0.5 s -> **0.24 s** (final run, no tick over 250 ms). The integrated server shares 4 vCPUs with software rendering here, so it runs slower than on a PC with a GPU. |
| Modpack without longnight: both presets boot a dedicated server and a client | `tools/pack/server_test.py`, `client_test.py` | PASS, see [modpack/VERIFICATION.md](../modpack/VERIFICATION.md) |

Tests **not** possible here: real-time human play, multiplayer with several real players, performance on your
hardware (this container has no GPU: rendering used Mesa software GL), and long campaigns (dozens of hours).

## Known bugs and limitations
- First approach to a large town raises it over several seconds to a couple of minutes depending on server speed
  (it places ~10,000 blocks at 48 per tick once its chunks are loaded).
- A few citizens on very rugged terrain report "I can't find a way to ..." and wait or step over the obstacle.
- Courtship only happens within a settlement; cross-settlement marriages need travellers (planned).
- Wars are rare by design: about one or two in 200 simulated days in the long-run test, none in most 60-day runs.
- Stone is the usual bottleneck for young camps and growing towns ("Waiting for stone"): a natural pacing limit,
  and what notice-board contracts ask players for.
- A new world's history is written in the background after the server starts (~15 s vanilla terrain, ~40 s with
  Tectonic/Terralith); players who join meanwhile are told so and greeted when it is ready.
- On a sea-heavy seed the survey may reach ~4,000 blocks from spawn to find enough sites, so settlements can be far
  apart (roads only link neighbours within 1,400 blocks) and the first world takes up to a minute to write.
- Raising a town is rationed (~10 ms of surveying and building a tick, 20 ms of terrain-noise sampling), so on a slow
  machine a large town can take a few minutes to stand complete; a building may move once more when a plot that
  could not be surveyed in time turns out unusable on the ground (it is always checked before a block is placed).
- Settlements in dense forests (e.g. Terralith giant-spruce taiga, giant mushrooms) keep the trees between their plots until
  lumberjacks fell them, so such towns look very wooded.
- Sound engine errors in the test logs come from the container having no audio device.

# Hardcore Friends

A Fabric mod for Minecraft Java **26.3** that adds nine human companions to your Hardcore world. Each has their own skin, personality and job: Fern (farmer), Oak (builder), Flint (miner), Scout (explorer), Spark (redstone inventor), Aegis (warrior), Sage (strategist), Terra (landscaper) and Rowan (forager).

They choose useful work on their own, cooperate through a shared supply chest, grow a Unity bond, and build a camp into a settlement using real materials. In 2.0 they also live on while you are away, go on trips, recruit newcomers you meet in the world, follow Sage's plan through diamonds, enchanting and the Nether, and help you beat the dragon. In 3.0 the camp grows into a village of their own: they lay out streets and build proper houses, take up trades and keep shops, make friends, fall in love, marry and raise children, find their way better, and defend the village together, all without orders from you. Player Hardcore rules are never changed.

> **3.0 has not been played yet.** It compiles and every part was reviewed, but nobody has tried it in game: you are the first. Test in a copy of your world first. Each part's honest limits are at the end of its page in [docs/v3](docs/v3). Earlier test notes are in `release/` (`TESTING-2.0.md`, and 1.0's results in `TEST-RESULTS.md`).

## 3.0 at a glance

| Part | What it adds | Commands | Main settings |
|---|---|---|---|
| [Better builds](docs/v3/architecture.md) | Proper buildings from 54 plans kept as data files; new materials; scaffolding | `/friends builds` | `fancyCampBuildings` |
| [Finding the way](docs/v3/navigation.md) | Paths that avoid caves, running water and big drops; hearing; getting unstuck; sprinting | `/friends senses` | `rescueStuckFriends` |
| [Living together](docs/v3/people.md) | Friendships, romance, weddings, children, family names | `/friends family`, `/friends couples`, `/friends relationships` | `romance`, `children` |
| [A proper village](docs/v3/village.md) | Town plan, streets, a house for every household, real beds, a daily routine, Town and City stages | `/friends village`, `/friends home` | `villageHomes` |
| [Shops and trades](docs/v3/market.md) | 15 trades, at their own workplaces as the village builds them; shops that trade with you for emeralds | `/friends trades`, `/friends shops` | `villageTrades`, `playerShops` |
| [Village life](docs/v3/life.md) | Calendar, market day, feasts, music, birthdays, funerals and graves, the Village Chronicle | `/friends calendar`, `/friends chronicle` | `villageLife` |
| [Defending the village](docs/v3/defence.md) | Alarm bell, taking cover, guards, raids, fire watch | `/friends defence` | `villageDefence` |
| [Pets and maps](docs/v3/pets.md) | Cats and dogs for friends and children; Scout's maps | `/friends pets`, `/friends maps`, `/friends map` | `pets`, `scoutMaps` |
| [Skins](docs/v3/people.md#skins) | An 82-skin medieval village set, tagged by trade and place; add your own | | |

Every setting lives in `config/hardcorefriends.json`; each page lists the rest.

## Features

- **Nine friends**, each with their own skin, personality, speaking voice and speciality.
- **Everyone pitches in.** Any friend can do any job. Specialists go first and work fastest; when a specialist is missing, the others stand in, their second interest first.
- **Needs and mood, Sims-style.** Hunger, energy, social, fun and comfort. Friends eat real food from their backpack or the supply chest, sleep at night, chat with each other, take short breaks for a pastime and warm up by the fire, all on their own. Their mood speeds up or slows down their work and shows in what they say.
- **Safe nights.** At nightfall work stops and friends go to bed, while a rota keeps someone on watch (Aegis first, then a rested, armed friend). The watch raises the alarm when a monster comes into camp, sleepers wake, and armed friends come running, so nobody fights alone.
- **Real materials.** Everything they build or craft uses real items from the supply chest, and their tools wear out.
- **A growing camp**, from campsite to settlement in five stages. 3.0 adds two more, the Town (650 Unity, 14 people, 5 houses) and the City (800 Unity, 22 people, 9 houses), which also need some of the village's civic buildings.
- **A Unity bond** that grows with time together, teamwork, chats and a happy team, and unlocks modest bonuses.
- **Hardcore stays hardcore.** Friends can die. Starving hurts them down to one heart. Nothing changes your own death.
- **Bounded, logged world edits** that never touch your builds.
- **Commands that work without cheats**, including `/friends needs` to see everyone's needs as bars.

New in 2.0 (details in [GUIDE.md](GUIDE.md) sections 15-20 and [docs/v2](docs/v2)):

- **Independence.** The camp keeps running while you are online anywhere; Scout explores far afield; friends trade at villages, build shelters when caught out at night, level ground to make room for a building, and get more skilled with practice.
- **Gear and fighting.** Armour, shields and swords for everyone, a smith, bows with a safe line of fire, emergency healing, teamwork.
- **Newcomers.** Strangers in villages, at new survivor camps and on the road, each with their own name and look, who join after a small request.
- **Sage's plan to beat the game.** A deep diamond mine with lava safety, obsidian, a library with an enchanting table, enchanting, an anvil and brewing.
- **Expeditions you lead.** Parties that follow you through portals, bartering, blaze hunting, finding the stronghold with eyes of ender, filling the End portal, and the dragon fight.
- **Several players.** An owner and trusted players, bonds, a job board, mourning and keeping a fallen player's things safe, notes, mailbox deliveries and opt-in siege nights.

New in 3.0 (details in [docs/v3](docs/v3)):

- **Proper buildings.** The camp's cabin is now a cottage with a log frame, a stone plinth and a gable roof, and the storehouse and watchtower are built the same way (a building already started in an older world keeps its old plan). The village builds from a library of 54 plans (19 houses, shops, workplaces, a town hall, school, tavern, chapel, walls and a gate, farms, street decoration), picked to suit the land and the wood in the chest. Friends make or gather the stairs, stone bricks, glass, wool and beds the plans need, and put up dirt or cobblestone pillars to reach roofs, then take them down. A data pack can add plans.
- **Better at finding their way.** Paths keep out of caves on a walk across the surface, away from running water, waterfalls and drops of more than three blocks. Friends hear monsters through walls. A stuck friend hops aside, swims up for air or to the best shore, climbs out of a pit on a block or two or digs a staircase out, or walks out of a cave; as a last resort they are brought home. Placing and digging blocks to get out only happens in the Overworld, never inside the camp, never by children, and only with `allowWorldEditing` on. Friends sprint on long walks, 30% faster.
- **A village of their own.** At the Village stage they lay out a town plan of streets and plots round the camp and build a house for every household, by themselves. Everyone helps build their own home in their spare time. They sleep in their own beds, have breakfast and lunch at their own table and spend evenings at home with family and neighbours. The village adds a well, town hall, market, tavern, school, chapel, farms, lamp posts and in the end a town gate, and two new stages follow the Settlement: the Town and the City.
- **Trades and shops.** As the village builds workplaces, grown-ups take up 15 trades besides their speciality (baker, shopkeeper, butcher, fishmonger, tailor, blacksmith, fisher, mason, beekeeper, carpenter, doctor, shepherd, farmer, teacher, innkeeper), each at its own workplace with real materials. Before there are workplaces, a Hamlet with six or more grown-ups has a cook at the campfire, a fisher on the bank (if there is open water nearby), a stallholder at the supply chest and a shepherd at the pen (with two or more sheep in it). At least three grown-ups (`friendsWithoutTrade`) always stay without a trade. Friends keeping a shop (the shopkeeper or stallholder, baker, butcher, fishmonger, tailor, and the blacksmith at the smith's shop) trade with you at their counter by day through the game's trading screen, for emeralds, at fixed prices, selling only what the village really has. Shops shut at dusk and in thunderstorms.
- **Families.** Friendships grow from chats, work and meals together; some couples fall in love, go on evening dates, get engaged and marry in front of everyone. Married couples with room at home have babies, who play, learn from their parents (or at school, once the village has a teacher) and grow up into members of the team with a trade. Everyone gets a family name. All gentle and family friendly.
- **Village life.** A calendar with weeks and seasons, market day every Saturday, three feasts a year, music on a note block some evenings and at feasts, birthdays, funerals and graves at a cemetery the friends lay out, and the Village Chronicle, the village's history, which you can always read with `/friends chronicle`. It is also written out as a real book (on the town hall's lectern, or in the supply chest before there is a town hall) once you put a book and quill in the supply chest, or an ink sac and a feather with a book (or three paper and a leather); the friends do not gather ink sacs or feathers themselves.
- **Defending the village.** When monsters close in at night, a creeper gets into the village or raiders come, someone rings the bell (the friends cannot make bells: they use one already in the village, such as one you hung, or one you put in the supply chest; with no bell they just shout the alarm). Children and anyone not fit to fight go indoors and shut the door, and the fighters take their posts. Once the village has a watchtower, gate or walls, guards stand there at night; raids are fought off together, and fires near the houses are put out by hand.
- **Pets and maps.** Friends and children adopt stray cats and wild wolves with fish and bones from the chest, name them and settle them at home for the night. Scout draws maps of the camp and of her trips, hangs them in the town hall, and copies one for you (onto your own empty map, or from the camp's stock).
- **82 new skins.** A medieval village set (62 grown-ups and 20 children, half wide-armed and half slim), tagged by trade and place, so newcomers tend to dress for their work and the land they come from. You can add more.
- **Still no cheats.** The new commands work at permission level 0 with cheats off and never hand out free items; all of them only show things, except `/friends map`, which asks Scout for a copy made from the camp's stock.

## Documentation

- **Players:** read [GUIDE.md](GUIDE.md): installing, the nine friends, and everything up to 3.0 (sections 21 to 28 are new in 3.0). Testing 3.0? Start with [release/TESTING-3.0.md](release/TESTING-3.0.md).
- **3.0, part by part** (what each part does, its commands, its settings and its honest limits):
  - [docs/v3/architecture.md](docs/v3/architecture.md): better builds, the 54 plans, new materials, scaffolding, and how to write your own plans.
  - [docs/v3/navigation.md](docs/v3/navigation.md): paths, senses, sprinting, getting out of caves and away from flowing water, and the rescue.
  - [docs/v3/people.md](docs/v3/people.md): friendship, romance, weddings, children, families, the skin list and skin tags.
  - [docs/v3/village.md](docs/v3/village.md): the town plan, streets, houses, real beds, the daily routine, the Town and City stages.
  - [docs/v3/market.md](docs/v3/market.md): the 15 trades, workplaces, and shops trading with players for emeralds.
  - [docs/v3/life.md](docs/v3/life.md): the calendar, market day, feasts, music, birthdays, funerals and graves, the Village Chronicle.
  - [docs/v3/defence.md](docs/v3/defence.md): the alarm bell, taking cover, guards, raids, the fire watch.
  - [docs/v3/pets.md](docs/v3/pets.md): cats and dogs, and Scout's maps.
  - [docs/v3/skin-prompt.md](docs/v3/skin-prompt.md): how to get more skins made and add them.
- **2.0, part by part:** [docs/v2](docs/v2) (`combat.md`, `survival.md`, `settler.md`, `progress.md`, `expedition.md`, `town.md`).
- **Developers:** read [docs/DEVELOPING.md](docs/DEVELOPING.md) and [docs/DESIGN.md](docs/DESIGN.md) (section 15 shows how the 3.0 packages fit together).

## Build

Java 25 is required.

```bash
./gradlew build                      # mod JAR in build/libs/, runs the server game tests
xvfb-run -a ./gradlew runClientGameTest   # in-game client tests with screenshots (Linux/headless)
python3 tools/package_release.py     # release/ JAR, CurseForge import ZIP and checksums
python3 tools/add_skins.py           # number new skin PNGs into skins.json (docs/v3/people.md, "Adding skins")
```

## Versions

| Component | Version |
|---|---|
| Hardcore Friends | 3.0.0 |
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.162.0+26.3 |
| Java | 25 |
| Loom | 1.18 |
| Gradle | 9.7.1 |

- **3.0.0:** a village of their own: better builds, finding the way and sprinting, families, a proper village with houses and real beds, trades and shops, village life, defence, pets and maps, and 82 village skins. Not yet played in game.
- **2.0.0:** independence, gear and fighting, newcomers, Sage's plan to beat the game, expeditions you lead, and several players.
- **1.0.0:** the nine friends, everyone pitching in, needs and mood, the camp from campsite to settlement, and the Unity bond.

## Layout

| Path | Contents |
|---|---|
| `src/main/java/.../companion` | The companion entity, backpack, speech and dialogue, needs and mood, specialities, and the friend roster |
| `src/main/java/.../ai` | Reflex goals, the task scheduler, shared upkeep tasks, needs jobs, and each role's routines |
| `src/main/java/.../camp` | Camp data, supply chest, crafting, settlement plan, blueprints, needs |
| `src/main/java/.../world` | `WorldEditGuard` (bounded, build-preserving block changes) and natural tree detection |
| `src/main/java/.../unity` | The Unity bond |
| `src/main/java/.../combat` | 2.0: gear, the smith, bows, shields, healing, teamwork |
| `src/main/java/.../survival` | 2.0: keeping the camp loaded, trips, trading, levelling, shelters, skills |
| `src/main/java/.../settler` | 2.0: newcomers, survivor camps (world generation), recruiting |
| `src/main/java/.../progress` | 2.0: Sage's plan, deep mining, obsidian, books, enchanting, anvil, brewing |
| `src/main/java/.../expedition` | 2.0: following through portals, the Nether, the stronghold, the End |
| `src/main/java/.../town` | 2.0: trust, bonds, the job board, mourning, notes, mailboxes, siege nights |
| `src/main/java/.../civic` | 3.0: the four shared lookups the packages answer for each other (homes, families, trades, the building library) |
| `src/main/java/.../architecture` | 3.0: the building library, building plans on chosen sites, styles, new materials, scaffolding |
| `src/main/java/.../navigation` | 3.0: the friends' own paths, senses, getting unstuck and the rescue, sprinting |
| `src/main/java/.../people` | 3.0: friendships, romance, weddings, children, family names, the skin list |
| `src/main/java/.../village` | 3.0: the town plan, plots, households and houses, beds, the daily routine, streets, the Town and City stages |
| `src/main/java/.../market` | 3.0: trades, workplaces, shops and trading with players |
| `src/main/java/.../life` | 3.0: the calendar, feasts, market day, music, birthdays, funerals and graves, the Village Chronicle |
| `src/main/java/.../defence` | 3.0: the alarm bell, taking cover, posts and guards, raids, the fire watch |
| `src/main/java/.../pets` | 3.0: cats and dogs, Scout's maps |
| `src/main/resources/data/hardcorefriends/blueprints` | 3.0: the 54 library plans (a data pack can add or replace them) |
| `src/main/resources/hardcorefriends/camp_plans` | 3.0: the camp's own buildings, in the same plan format |
| `src/main/resources/assets/hardcorefriends` | Skins: `skins.json` and the village set in `textures/entity/people/` |
| `src/client/java` | Renderer (wide-armed or slim-armed player model, to match the skin, with all outer layers) |
| `src/gametest` | Server game tests and in-game client tests (Hardcore worlds) |

# Hardcore Friends: design specification

This is the behaviour and architecture contract for the mod. The code is the source of truth.
This document explains why the code is shaped the way it is and what each part promises.

- **Target:** Minecraft Java **26.3** (newest stable release on 2026-10-06), **Fabric Loader 0.19.5**, **Fabric API 0.162.0+26.3**, Java 25.
- **Mod id:** `hardcorefriends`. **Root package:** `io.github.bradley09roberts.hardcorefriends`.
- **Names:** Minecraft 26.x ships unobfuscated, so the code uses Mojang's official names. For example, `Identifier` replaces `ResourceLocation`.

## 1. Hard rules (never violate)

1. **Player Hardcore stays untouched.** No mixin, event or command may change player death, respawn, game mode, difficulty, the hardcore flag or totems. Nothing may cancel or soften player death, and nothing may add extra lives. We never register `ALLOW_DEATH` for players.
2. **Companions are mortal.** They take normal damage, can die permanently from that encounter, and on death drop their backpack (an item holding all their items and gear).
3. **No free items and no cheats.** Every command works at permission level 0 with cheats off. No command may give items, teleport players, change time, weather or game rules, or edit blocks. Recruitment costs **2 common food items**. Emeralds are never required.
4. **Real materials.** Every block a companion places comes out of a backpack, and the backpack is filled from the world or the supply chest. Every crafted item consumes real ingredients. Tools and weapons lose durability and break.
5. **Bounded, preserving world edits.** Every block change goes through `world/WorldEditGuard`, which enforces zones, allow-lists, the player-build detector, no block entities, and no fluid breaches. It also logs every change. A companion never breaks a block that looks player-built, unless the mod itself placed it and recorded it in `CampData.placedBlocks`.
6. **Server authority and performance.** All AI runs on the server. Scans are throttled to once every ≥20 ticks and kept within bounded radii. No scan is larger than 48 blocks horizontally, and large scans are spread over ticks.

## 2. The nine friends

Each friend's role is their **speciality**; every friend can do every role's work (section 5).

| Id | Name | Role | Skin file | Personality | Chat colour | Starter tool |
|---|---|---|---|---|---|---|
| FERN | Fern | Farmer | `fern.png` | patient, caring | `#6BBF59` | wooden hoe |
| OAK | Oak | Builder | `oak.png` | practical, methodical | `#C08A4E` | wooden axe |
| FLINT | Flint | Miner | `flint.png` | cautious, dry humour | `#9AA3AD` | wooden pickaxe |
| SCOUT | Scout | Explorer | `scout.png` | curious, adventurous | `#3FB0AC` | wooden sword |
| SPARK | Spark | Redstone inventor | `spark.png` | clever, excitable | `#E0533D` | wooden pickaxe |
| AEGIS | Aegis | Warrior | `aegis.png` | calm, protective | `#6F95D6` | wooden sword |
| SAGE | Sage | Strategist | `sage.png` | thoughtful, observant | `#B39DDB` | (none) |
| TERRA | Terra | Landscaper | `terra.png` | creative, tidy | `#C27BA0` | wooden shovel |
| ROWAN | Rowan | Forager | `rowan.png` | resourceful, generous | `#8DB255` | wooden axe |

Skins are the user's supplied 64×64 Classic/Steve PNGs. They are copied byte-for-byte and rendered on the wide-arm player model with every outer layer visible.

Personality parameters (`companion/FriendId`):

- `chattiness`: 0–1, the minimum gap between casual lines (25 s at 1.0, up to 90 s at 0).
- `retreatFraction`: run to safety below this fraction of max health. Flint 0.6 (cautious), Aegis 0.25, others 0.4–0.5.
- `bravery`: reserved for future tuning; currently unused (see `canStandAndFight`).
- `generosity`: how readily they hand spare items to friends and players. Rowan 1.0, Fern 0.9.
- `roam`: maximum work distance beyond the camp radius. Scout 96, Flint 48 (mine), others 0–48.
- `speedBonus`: Scout +10%.
- **Stats:** Aegis has 24 max HP and 2 base armour. Everyone else has 20 HP. Base attack is 1 (+ weapon).

## 3. Lifecycle

- **Recruit:** `/friends recruit <name>` consumes exactly two items from the `#hardcorefriends:recruit_food` tag (any mix), spawns the friend on a safe spot next to the player, and gives them their starter wooden tool.
  - **Refused when** the friend is already alive, is inside the post-death waiting period, or the player has fewer than 2 qualifying foods.
  - Recruiting also works by right-clicking with food is **not** used. Commands are the single, clear path.
- **Death:** the friend drops `hardcorefriends:backpack`, named "<Name>'s Backpack". It holds every backpack item plus equipped gear (tool and armour) and never despawns. Then:
  - A message goes to all players with the cause and coordinates.
  - The ledger records the death, the Unity bond drops by 80, and the surviving friends say a short grief line.
  - Config `deadFriendsReturnAfterDays` (default 3 in-game days; −1 = never) controls whether the same person can be recruited again later. A returning friend comes back as a newcomer with no backpack.
- **Dismiss:** `/friends dismiss <name>` drops the backpack the same way, removes the entity, and costs 20 Unity.
- **Modes:** `WORK` (default, autonomous), `FOLLOW` (stay with the commanding player, help fight, carry loot), and `STAY` (hold this spot, defend themselves).

## 4. Brain architecture

Vanilla `GoalSelector` handles reflexes. Our own utility scheduler, which runs inside one `WorkGoal`, handles jobs.

Goals, in priority order (lower number = higher priority):

| Prio | Goal | Notes |
|---|---|---|
| 0 | `FloatGoal` | swim |
| 1 | `RetreatGoal` | health ≤ retreatFraction, or on fire, or drowning. Moves away from threats toward camp, Aegis or a player; eats from the backpack once safe; announces once. Keeps 20 blocks from any archer in sight (12 from other threats) and does not drift home while an archer stands within 18 blocks of camp. |
| 1 | `AvoidDangerGoal` | creepers within 7 blocks (10 if hissing), except a healthy Aegis facing a quiet one. Non-fighters also back away from hostiles within 8 blocks unless healthy and holding a tool (`canStandAndFight`), and leave the line of fire of an archer aiming at them (to 20 blocks). Flees toward protectors. |
| 3 | `CompanionMeleeGoal` | uses the best weapon from the backpack; only when there is a target |
| 4 | `FollowLeaderGoal` / `StayGoal` | mode-dependent |
| 5 | `WorkGoal` | runs `TaskScheduler` (MOVE + LOOK flags) |
| 3 | `OpenDoorGoal` | friends open and close wooden doors |
| 6 | `LookAtPlayerGoal` | idle polish |
| 7 | `RandomLookAroundGoal` | idle polish |

Target goals: `HurtByTargetGoal` (fight back) and, for Aegis, `DefendFriendsTargetGoal` (hostiles within 16 blocks of any player, companion or the camp). In FOLLOW mode every armed friend also defends the leader. `MutualDefenceTargetGoal`: friends not in STAY join fights against hostiles that are going for them, a friend or a player within 8 blocks, when `canStandAndFight` (healthy above max(50%, retreat fraction + 10%) and holding a tool; against an archer a non-fighter only stands its ground within 3 blocks, because chasing a skeleton with a hoe just gets them shot).

Out of combat (no damage for 10 s, no target, not burning) and with hunger above 10, friends recover 1 health every 4 s.

**Monsters fight back too.** On entity load, zombies, skeletons, spiders, illagers and witches gain a target goal for companions (config `monstersTargetCompanions`). Without this the companions would be untouchable, which contradicts Hardcore.

### Tasks (`ai/task`)

```java
public interface CompanionTask {
    String id();                         // "fern.harvest"
    String describe();                   // "harvesting crops" (status display)
    double score(CompanionEntity c);     // 0 = not applicable; ~1–100. Evaluated at most every 20 ticks.
    boolean start(CompanionEntity c);    // false = could not start (goes on cooldown)
    TaskStatus tick(CompanionEntity c);  // RUNNING / SUCCESS / FAILURE
    void stop(CompanionEntity c);        // always called after SUCCESS/FAILURE/preemption
    default int failureCooldown() { return 200; }
    default int successCooldown() { return 0; }
    default int maxTicks() { return 20 * 120; }
}
```

The scheduler re-scores every 20 ticks. It switches when idle, when the current task finishes, or when another task outscores the current one by ≥ 25. It enforces `maxTicks`, so a stuck task fails.

`TaskRegistry.create(id)` gives every friend the same job list: the common upkeep jobs (`ai/task/common`), the needs jobs (`ai/task/needs`), and every role's jobs, their own role first, each wrapped in a `SpecialityTask` for its role (section 5). The only jobs a friend never gets from another role are `TaskRegistry.SPECIALIST_ONLY`: Aegis's gear and guard duty, Sage's observing and stores review, and Scout's report.

Score bands:

| Score | Meaning |
|---|---|
| 70–89 | Urgent upkeep: missing tool, full backpack, night return |
| 40–69 | Main role work |
| 20–39 | Secondary help: sharing, tidying, crafting surplus |
| 1–19 | Idle and social |

`CampNeeds` (recomputed every 30 s) supplies a 0–1 multiplier for food, wood, stone, dirt, torches, ore and build materials. Sage being alive adds a 10% planning bonus to tasks that address the top need.

### Actions (`ai/action/Actions`)

Shared, per-tick helpers on the companion:

- `walkTo(pos, speed, reach)`: re-paths every 20 ticks and reports stuck after 100 ticks without progress.
- `mine(pos, reason)`: emulates player mining time with the held tool, shows crack progress and swings the arm. Breaks only through the guard; drops go into the backpack (overflow goes on the ground).
- `place(pos, state, item, reason)` and `transform(pos, newState, reason)` (till, path): both go through the guard.
- `equipBest(toolTag)` puts the best matching tool in the main hand and damages it on every use, so tools break.

## 5. Specialities: everyone can do every job

`companion/Speciality` and `ai/task/SpecialityTask`.

Each friend has a speciality (their role) and one **interest**, `Speciality.interest(id)`:

| Friend | Speciality | Interest |
|---|---|---|
| Fern | Farmer | Forager |
| Oak | Builder | Landscaper |
| Flint | Miner | Redstone inventor |
| Scout | Explorer | Forager |
| Spark | Redstone inventor | Miner |
| Aegis | Warrior | Explorer |
| Sage | Strategist | Farmer |
| Terra | Landscaper | Farmer |
| Rowan | Forager | Builder |

- **Keenness** (`Speciality.affinity`): 1.0 for their speciality, 0.8 for their interest, 0.6 for anything else.
- **Skill** (`Speciality.skill`): 1.2 for their speciality, 1.0 for their interest, 0.85 for anything else, and 1.0 for work of no particular kind. A world edit's `Reason` names the kind of work (`Speciality.roleFor`: FARM is farming, BUILD building, INVENT redstone, LANDSCAPE landscaping, MINE mining, GATHER_WOOD and GATHER_EARTH foraging).
- **Work speed** (`CompanionEntity.workSpeed(reason)`) = skill × mood (section 6) × the Unity work rhythm. `Actions.mine` multiplies the block-breaking rate by it.

**`SpecialityTask`** wraps one role job. For a job whose own score is `s`:

| Situation | Score |
|---|---|
| The friend's own speciality | `s`, unchanged: a specialist does their own work exactly as they would alone |
| Standing in: nobody of that speciality is working in this world (dead, not recruited, following or staying) | keenness × min(`s`, 90) × 0.5: at most 36 for their interest and 27 for other work, below their own main work (40 and up) and above idling |
| Lending a hand: the specialist is working | a quarter of the stand-in score, at most 9: only when there is nothing else at all to do |
| Personal jobs (`PERSONAL`: delivering what the friend carries, smelting carried ore) | `s` × keenness, whoever is around |

- **Exclusive jobs** (`EXCLUSIVE`: building and repair, contraptions, the farm layout and tilling, the mine, the quarry, felling, paths, fencing, planting, exploring, the furnace, the animal pen and each livestock job, section 12) run for one friend at a time; the others score them 0 while someone is on them. A specialist coming back to work asks for their shared job back, and a stand-in on it fails out at once and hands it over.
- **Claims last only while the job runs.** A claim counts only while its holder is working with that job in hand. A friend who dies or is unloaded lets go of their claims at once, so a friend who leaves mid-job (a camp unloading, a world closed and reopened) never keeps the others off it. A request to hand a job back lapses after 3 s, or as soon as the friend who asked has gone. Claims, requests and chat invitations are forgotten whenever a server starts or stops.
- A friend starting a job outside their speciality may say `HELPING_OUT` with the job's description.

## 6. Needs and mood

`companion/Needs` (the numbers), `ai/task/needs` (the jobs that meet them), `companion/MoodPassives` (mood in speech and displays).

Each friend has five needs, each from 0 (desperate) to 100 (met). New friends start at hunger 80, energy 90, social 70, fun 70 and comfort 70. The needs are saved with the entity. Every second (`Needs.tickSecond`):

| Need | Drift per second | Notes |
|---|---|---|
| Hunger | −100/1500, ×1.3 while working | About one loaf of bread's worth a day |
| Energy | −70/840 while awake | Not while asleep; the sleep job refills it |
| Social | +0.15 with company, −0.1 alone | Company is another friend, or a player who is not spectating, within 6 blocks |
| Fun | −100/1100 while working | |
| Comfort | towards the surroundings' comfort, at most 0.5 a second | Base 55; +20 under a roof; +25 near a lit campfire (5 blocks); −30 in rain in the open; −15 dark in the open; −20 below half health |

"Working" means following a player, or on any job other than a needs job (`needs.*`) or idling.

**Meeting needs.** The needs jobs score higher the lower their need: a desperate need is urgent upkeep (70–89) and beats any work; a mild one sits in the main or secondary band and waits for the job in hand to end. Their ids start with `needs.`, which counts as time off.

- `needs.eat`: eats one real food item (`#hardcorefriends:companion_food`), from the backpack first, otherwise taken from the supply chest, choosing the item that best fits the hunger. `CompanionEntity.eat(stack)` heals by the food's nutrition, fills hunger by nutrition × 6 (`hungerValue`), and keeps bowls and bottles. With no food anywhere the friend says `NO_FOOD`.
- `needs.sleep`: at night tired friends lie down in their own place (side by side in the cabin, or around the camp centre) and regain energy, faster under a roof. They wake at dawn, when hurt, or when a monster comes close. Exhausted friends nap by day. Aegis keeps the first watch.
- `needs.socialize`: a lonely friend walks over to another friend who is awake and not in trouble (or a player in camp) and they chat (`CHAT`, `CHAT_REPLY`). Both feel better and Unity gains 1 (at most 30 a day).
- `needs.leisure`: a bored friend spends a short while on a pastime that suits them (`LEISURE`). Pastimes change no block.
- `needs.cosy`: a chilly friend warms up by a lit campfire or in the cabin (`COSY`).

Needs jobs only run in WORK mode, like every job. A friend who is following or staying eats from the backpack only when hurt (`RetreatGoal`).

A player handing food to a friend who is hurt, or whose hunger is below 60, has them eat it at once (it heals and fills hunger); otherwise it goes into the backpack.

**Starving.** At hunger 0 a friend takes 1 starvation damage every 4 s while above 2 health (one heart), as a player does on Normal difficulty. Starving never kills on its own. Health only regenerates while hunger is above 10.

**Mood.** `Needs.moodValue()` is the weighted average of the needs (hunger 0.30, energy 0.25, social, fun and comfort 0.15 each):

| Mood value | Mood |
|---|---|
| below 25 | miserable |
| 25–45 | low |
| 45–65 | okay |
| 65–85 | good |
| 85 and up | great |

- **Work speed:** `Needs.workSpeed()` = 0.8 + 0.3 × mood value / 100, from 0.8 (miserable) to 1.1 (everything met).
- **Speech:** every 15 s (`MoodPassives`), a friend who is awake and not fighting says `STARVING` at hunger 0 (unless already eating). Otherwise, unless falling back, they say `MOOD_LOW` naming their worst need ("hunger", "energy", "social", "fun" or "comfort") when the mood is low or miserable (unless that need's job is already running), or `MOOD_GREAT` when it is great. The lines' cooldowns (`STARVING` 1 min, `MOOD_LOW` 5 min, `MOOD_GREAT` 15 min) and the friend's chattiness keep this occasional.
- **Team spirit:** every in-game hour (1000 ticks), if the mood of the team's average needs (all loaded friends) is great, Unity gains 1, at most 12 a day (`Unity.teamSpirit`, category `spirit`). A low mood never costs Unity.
- **Displays:** `/friends needs` shows the five needs as bars, the mood and what the friend is doing about their lowest need; `/friends list` and the right-click status line show the mood (and the worst need when the mood is low).

## 7. World editing rules (`WorldEditGuard`)

These rules apply to every reason:

- The master switch is `allowWorldEditing`.
- The chunk must be loaded.
- No block entity may be touched.
- Never touch blocks in `#hardcorefriends:never_touch`.
- Never edit within 1 block of a player's feet.
- Breaking is allowed only when no lava or water is adjacent (farming harvests excepted).
- Blocks in `#hardcorefriends:build_markers` count as player-built. They are never broken, and no new structure is placed within 2 blocks of them, unless the position is in `CampData.placedBlocks`.

| Reason | Where | May break | May place / transform |
|---|---|---|---|
| FARM | camp radius | mature crops, berry harvest (age reset) | seeds onto farmland; till grass/dirt next to water; water from a bucket at a farm site; open or shut a fence gate the friends placed (the animal pen's) |
| BUILD / INVENT | camp radius | replaceable plants, snow layers, own placed blocks (upgrades) | blueprint blocks into air or replaceable plants only |
| LANDSCAPE | camp radius | replaceable plants, snow layers | grass/dirt to dirt path; saplings and flowers; torches; dirt into 1-deep holes |
| MINE | mine box (24×24 around the mine entrance, outside the camp core) plus exposed ores within camp radius + `resourceRadius` | `#hardcorefriends:mineable_natural` only | torches in tunnels |
| GATHER_WOOD | camp radius + `resourceRadius` | logs of natural trees only, with ≥ 3 non-persistent leaves touching the crown and all logs within reach | saplings at the stump |
| GATHER_EARTH | quarry boxes (5×5, max 2 deep) in the resource ring | dirt, grass, sand, gravel, natural stone | — |

The camp radius starts at `campRadius` (24) and grows by 4 per stage, up to 40.

Each change is appended to `CampData.editLog` (the last 256 entries; see `/friends log`) and counted per companion. Each companion is limited to 1 edit per 8 ticks.

## 8. Shared camp and settlement

`/friends camp set` stores the camp centre and dimension. `/friends chest` links the chest or barrel the player is looking at as the **shared supply chest**. If no chest is linked, Oak builds one from 8 planks at the camp and links it.

Stages are gated by completed structures **and** the Unity score:

| Stage | Name | Unity | Builds (real materials) |
|---|---|---|---|
| 0 | Campsite | 0 | supply chest (Oak, if none), campfire (3 logs, 3 sticks, 1 coal/charcoal) |
| 1 | Camp | 0 | crafting table, furnace (8 cobblestone), 4 torch posts, Fern's farm plot (16 farmland around a water source; needs a water bucket if no water nearby). The plot site needs a level, sealed hole for the water and at least 20 level, tillable tiles in the 9×9 square around it; bumps and dips elsewhere in the square are fine |
| 2 | Hamlet | 100 | Cabin (7×7 planks/logs, door, glass panes, slab roof); Terra's paths; Spark's automatic door (pressure plates); Terra's animal pen (optional: never holds the stage back; section 12) |
| 3 | Village | 250 | Storehouse shed with extra chest; Aegis's watchtower (cobblestone, ladder); lantern posts; Spark's drop-off hopper on the supply chest; fenced farm |
| 4 | Settlement | 500 | Second cabin; Spark's auto-smelter (chests, hoppers, furnace); night lamp posts (inverted daylight detector + redstone lamp) once nether materials exist; flower gardens |

**Blueprints** (`camp/Blueprints`) are code-defined lists of `(dx, dy, dz, MaterialSpec, state tweaks)`, placed bottom-up with attachments last.

- A `MaterialSpec` accepts any item from a tag (any planks, any log, any wooden slab or door), so whatever wood the team has is used.
- `SiteFinder` looks for a flat site near the preferred offset. Ground variation must be ≤ 1, filled with cobblestone or dirt foundation blocks from real stock. Every footprint block must be air or a replaceable plant, with no build markers within 2 blocks. Sites never overlap other reserved sites.

## 9. Supplies

- **Backpack:** 9 slots, 18 at Unity 100 and 27 at Unity 500. It is saved with the entity. Sneak + right-click with an empty hand opens it as a chest UI.
- **Deposit:** when ≥ 80% full or carrying surplus (anything outside the role's keep-list), a companion walks to the supply chest and deposits it.
- **Restock:** a missing role tool, food below 2, or materials for the current job are fetched from the chest.
  - A friend covering a speciality nobody else is working also keeps that speciality's tool. They fetch or make it in spare time, and only when it fits without putting anything away: into an empty hand or a free backpack slot. Their own tool is never moved to the chest to make room for it.
- **Crafting** (`camp/Crafting`, hard-coded recipes with real consumption): logs to planks, planks to sticks, coal or charcoal + stick to torches, tools (wood, stone, iron), bread, chest, crafting table, furnace, doors, slabs, fences, pressure plates, hopper, redstone torch, daylight detector, redstone lamp, ladder, lantern. Anything beyond the 2×2 recipes needs a crafting table within 6 blocks.
- **Smelting:** raw iron, copper or gold plus fuel go into the camp furnace; outputs are collected later.
- **Sharing:** a companion holding what a friend's job needs walks over and hands it across (+2 Unity). Generous friends give food to hungry players and companions.

## 10. Unity bond

Score 0–1000, saved in `CampData`.

**Levels:**

| Level | Score |
|---|---|
| Strangers | 0 |
| Acquaintances | 100 |
| Companions | 250 |
| Close Friends | 500 |
| Family | 800 |

**Gains** (with daily caps):

- **Time together:** +1 per minute per friend within 24 blocks of a player (cap 120/day).
- **Teamwork:** a deposit or delivery +1 (cap 60/day); a direct hand-off +2 (cap 40/day); a friend landing the killing blow on a hostile (`Enemy`; butchering or hunting an animal earns nothing) +3 (cap 60/day); each finished camp improvement +30; each finished contraption +15; a player feeding or gifting a friend +2 (cap 20/day).
- **Friendship:** two friends chatting +1 (category `chat`, cap 30/day); team spirit, while the team's average mood is great, +1 per in-game hour (category `spirit`, cap 12/day; section 6).

**Losses:** a friend's death −80; a dismissal −20. A low mood costs nothing.

**Bonuses:**

| Score | Bonus |
|---|---|
| 100 | 18-slot backpacks; friends feed hungry players |
| 250 | *Work rhythm*: +15% work speed when another friend is within 12 blocks; *Careful hands*: 20% chance a use costs no durability |
| 500 | 27-slot backpacks; an extra 1 HP every 4 s while at camp with no target (on top of normal out-of-combat healing); Scout's warnings make the warned mob glow for 8 s |
| 800 | *Rally*: when a player drops below 6 HP with ≥ 2 friends within 16 blocks, the player gets Regeneration I for 5 s and the friends target the attacker (10-minute cooldown). This does not prevent death. |

## 11. Role routines (summary)

These are each role's jobs, named after the specialist. Through `SpecialityTask` (section 5) any friend can run them, except the specialist-only duties noted in section 4.

- **Fern:** harvests mature crops and replants immediately from the drops; replants empty farmland; tills more farmland next to water (capped per stage); bakes bread; uses bone meal; feeds hungry players; keeps livestock: brings wild animals to the pen, breeds them, butchers the surplus, cooks meat on the campfire, shuts the pen gate, hunts in the gathering ring when food is short (section 12).
- **Oak:** builds the next blueprint for the stage; crafts planks, sticks, doors, slabs and torches from stock; repairs missing blocks of finished structures; builds lantern posts.
- **Flint:**
  - mines exposed ores (pickaxe tier checked);
  - digs a bounded staircase mine with branch tunnels, placing torches;
  - avoids fluids;
  - smelts ore at the camp furnace;
  - returns at night.
- **Scout:**
  - explores a spiral around camp by day;
  - records ore, tree and hazard points of interest for Flint and Rowan;
  - warns nearby players about creepers, approaching hostiles, nightfall, storms and lava;
  - comes back at dusk.
- **Spark:** builds working redstone contraptions (automatic door, drop-off hopper, auto-smelter, night lamps); keeps the torch supply up.
- **Aegis:** guards players and the camp; patrols at night (on the watchtower once built); equips the best weapon, armour and shield from the chest.
- **Sage:** recomputes camp needs and announces the team focus; gives contextual Hardcore survival advice (health, food, darkness, phantoms, night, armour, tool durability, mining depth); `/friends advice` and `/friends plan` show the report.
- **Terra:**
  - turns grass into dirt paths between camp features;
  - plants saplings and flowers;
  - lights dark spots in camp with torches (spawn-proofing);
  - tidies dropped items into the chest;
  - fills 1-deep holes;
  - builds farm fences;
  - builds and mends the animal pen (section 12).
- **Rowan:** fells natural trees (only fully reachable ones) and replants; quarries dirt and stone in bounded 5×5 pits; forages berries, apples and saplings; delivers materials to the builder first (Oak, or whoever is building in his place: when the build is short of something she carries 16 or more of, the delivery outranks every gathering job).

## 12. Livestock (`ai/role/ranch`)

Wheat alone cannot feed nine friends (bread is 30 hunger for three wheat; cooked beef or pork is 48 for one animal drop), so from the Hamlet stage the farmer keeps animals. All of it is farmer work (Fern's speciality; others help or stand in through `SpecialityTask` as usual).

**The pen** (`Structures.ANIMAL_PEN`, stage 2, owner LANDSCAPER, optional). `Blueprints.ANIMAL_PEN` is a 9×9 ring of 31 wooden fences and one fence gate (local (4, 0, 0), the middle of the front) round a 7×7 paddock, built by `terra.pen` (`ai/role/terra/PenTask`, a `BlueprintTask`, reason LANDSCAPE, score 42) through the ordinary `BuildJob`: fences and the gate come from the chest or are crafted at a table (`Stock.FENCE_GATE`: 2 planks + 4 sticks), dips are filled with foundations, and `SiteFinder` sites it like any building (level natural ground inside the camp, clear of other sites and 2 blocks from any build marker), turned so the gate faces the camp centre (preferred offset (11, 11)). The gate is built open so the builder is never shut in. Once finished, a missing fence or gate is put back by the same job (score 50). The reserved site is the pen's record: `ai/role/ranch/Pen` derives the paddock box, the gate, the spot outside it and the spots inside from `CampData.site(ANIMAL_PEN)`. Terra's paths lead to the gate rather than a corner, and Fern never tills inside the pen.

**The gate.** Friends open and shut only a gate they placed, through `WorldEditGuard.canTransform` with reason FARM (same block, `isPlacedByFriends`). Pen jobs walk through it (`Pen.enter` / `Pen.leave`): to the gate, animals within 2 blocks of it are first sent to the back of the paddock (so none slips out, or is jostled out, while it is open), open, through, shut. It is never shut on anything standing in the gateway, on a friend still in the paddock, or while a player is in or within 3 blocks of the pen; a gateway that does not clear within 5 s is left open. `fern.shut_gate` (45) shuts it when it stands open with animals inside and nobody in the paddock. `common.leave_pen` (85, every friend, outside `SpecialityTask`) takes any friend found in the paddock without a pen job in hand (called away mid-job, wandered in) out through the gate, so nobody is ever penned in.

**Jobs** (scores are the specialist's; livestock work is weighted by the camp's FOOD need and stays in the main band: `min(69, base × CampNeeds.weight(FOOD))`):

| Id | Base | What |
|---|---|---|
| `fern.bring_animal` | 46 | By day: the nearest wild cow, pig, sheep or chicken (`Wildlife.mayLead`) of a kind with fewer than 2 grown animals in the pen. With a lead (backpack or chest) it is tied on (`setLeashedTo(friend)`; the lead item is used up and given back when untied in the paddock; a snapped lead drops as in vanilla). Otherwise the friend holds the animal's tempt food (`Animal.isFood`: wheat, carrot/potato/beetroot, seeds), not used up, and steers it each tick like vanilla tempting (looks at the friend, walks after them within 10 blocks, stops 2.5 blocks short); a lagging animal is gone back for. Led to a back corner, let go and sent to the other back corner, gate shut behind. Success only if it is still in the pen. |
| `fern.breed` | 44 | Two ready adults of a kind (age 0, not in love) and room for one more (at most 6 of a kind, 12 in all): two of their food from backpack or chest, into the paddock (gate shut behind), one fed to each (consumed; `setInLove`), out. Vanilla `BreedGoal` makes the young one. |
| `fern.butcher` | 42 | A kind with more than 4 grown animals (`Livestock.KEEP_ADULTS`, never below a pair): with the best sword or axe (borrowed from the chest if need be) the surplus is killed, at most 2 a visit, never a young animal or one in love; the drops (meat, leather, wool, feathers) near the body are gathered. |
| `fern.cook` | 48 | Raw beef, pork, mutton, chicken, rabbit, cod or salmon (backpack or chest) on the camp's lit campfire (the `CAMPFIRE` site, else the nearest lit campfire within 10 blocks of the centre), up to its free slots, via `CampfireBlockEntity.placeFood`; the friend tends it and picks up as many cooked pieces as they put on (never a player's), then deposits them in the chest. If the fire is full: the camp furnace, only one the friends placed, with coal or planks; its output is collected by `flint.collect_smelted`. |
| `fern.hunt` | 40 | Only when FOOD need ≥ 0.4, by day, healthy (above max(0.5, retreat fraction + 0.1) of max health) and armed (sword or axe): the nearest animal `Wildlife.mayHunt` allows; drops gathered. Every rule is checked again every second and before every blow (the block checks afresh); any failure calls the hunt off. |
| `fern.shut_gate` | 45 (flat) | See the gate. |

`bring_animal`, `breed`, `butcher` and `shut_gate` are the pen jobs (`Pen.JOBS`): besides each being exclusive, none starts while another friend is on any of them, so one friend at a time is at the gate. `cook` and `hunt` are exclusive too (one cook at the fire, one hunter).

**Safety rules** (`Wildlife`). An animal counts as somebody's, and is never led or hunted, when it has a custom name, is tamed or owned (`OwnableEntity` with an owner), is leashed, rides or is ridden, stands within 4 blocks of anything `WorldEditGuard.looksPlayerBuilt` sees (build markers and block entities the friends did not place: fences, gates, walls, crafted blocks; barriers count), or stands inside a player's enclosure (a fence, wall, gate or other player-made block within 24 blocks in each of the four directions; the friends' own blocks do not count). Every job also skips young animals, animals in the pen, animals outside the gathering zone (camp + `resourceRadius`) and anything within 24 blocks of where a friend died in the last three days (`CampData.nearDanger`). Hunting additionally never happens at dusk or night, never inside the camp (camp animals are the pen's), and never takes one of the last two grown animals of a kind within 24 blocks.

**Performance.** One scan of the camp and gathering ring (at most 48 blocks beyond the camp edge) for cows, pigs, sheep, chickens and rabbits serves the whole team (`TeamCache`), at most every 100 ticks. Block-reading checks (player builds, enclosures) run only for animals that pass every cheap check, at most once per animal per scan. Candidates are re-planned every 2–3 seconds; with no animals around, scoring costs a list lookup.

**Talk.** `LEADING_ANIMAL`, `BRED_ANIMALS`, `BUTCHERING`, `COOKING` and `HUNTING`, in every friend's own voice. Killing an animal earns no Unity (only hostiles do); stats: `animals_penned`, `animals_bred`, `animals_butchered`, `animals_hunted`, `meals_cooked`.

## 13. Commands (permission level 0, no cheats)

`/friends help | list | needs [name|all] | recruit <name> | dismiss <name> | follow <name|all> | stay <name|all> | work <name|all> | where <name> | backpack <name> | camp | camp set | chest | unity | advice | plan | log | chatter <quiet|normal|chatty>`

`/friends needs` (default `all`) lists, for each loaded friend, a heading with their mood and current activity, then one line per need: a ten-block bar (`Needs.bar`), the need's name and value, and on the lowest need what the friend is doing about it (seeing to it now, fine for now, waiting until back at work, no food anywhere, or will see to it soon).

## 14. Known limits (stated honestly to the user)

- Friends only act while their chunks are loaded (near a player). They do not work while you are far away.
- Building follows fixed blueprints adapted to the available wood. They do not design new buildings.
- Pathfinding is vanilla mob pathfinding. Friends can get stuck on complex terrain; stuck tasks time out and are retried later.
- Mining is limited to one staircase mine with branch tunnels plus exposed ores near camp. Tree felling is limited to trees they can fully reach.
- Contraptions are a fixed set of vanilla redstone builds.
- Dialogue is pre-written lines chosen by situation and personality, not free conversation. Chats between friends are an opening line and a reply.
- Stand-ins work on other specialities only in their spare time and more slowly; a camp missing several specialists grows more slowly.
- Needs are five numbers met by a fixed set of jobs (eat, sleep, chat, pastime, warm up). Friends sleep on the spot they lie down on, not in beds.
- Needs jobs run only in WORK mode: a friend following or staying does not eat (except from the backpack when hurt), sleep or rest.
- Livestock is cows, pigs, sheep and chickens in one pen (rabbits are only hunted); no shearing, milking or eggs. A lured animal can wander off before the pen and is fetched again later. An unnamed animal of the player's standing loose with nothing player-built nearby looks wild. The camp's food planning (`CampNeeds`) counts cooked meat, not raw meat, as food.

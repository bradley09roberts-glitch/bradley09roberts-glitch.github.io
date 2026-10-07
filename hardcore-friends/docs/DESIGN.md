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

- `chattiness`: 0–1, the minimum gap between casual lines (≈ 25 s at 1.0, up to 90 s at 0.2).
- `retreatFraction`: run to safety below this fraction of max health. Flint 0.6 (cautious), Aegis 0.25, others 0.4–0.5.
- `bravery`: whether they fight back when cornered, and how close they let hostiles come.
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
| 1 | `RetreatGoal` | health ≤ retreatFraction, or on fire, or drowning. Moves away from threats toward camp, Aegis or a player; eats from the backpack once safe; announces once. |
| 1 | `AvoidCreeperGoal` | any creeper within 7 blocks, or a swelling one within 10 |
| 2 | `AvoidHostileGoal` | non-warriors: a hostile within `fleeDistance` that is targeting them or near them. Flees toward protectors. |
| 3 | `CompanionMeleeGoal` | uses the best weapon from the backpack; only when there is a target |
| 4 | `FollowLeaderGoal` / `StayGoal` | mode-dependent |
| 5 | `WorkGoal` | runs `TaskScheduler` (MOVE + LOOK flags) |
| 6 | `LookAtPlayerGoal`, `RandomLookAroundGoal` | idle polish |

Target goals: `HurtByTargetGoal` (fight back) and, for Aegis, `DefendFriendsTargetGoal` (hostiles within 16 blocks of any player, companion or the camp). In FOLLOW mode every armed friend also defends the leader.

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

## 5. World editing rules (`WorldEditGuard`)

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
| FARM | camp radius | mature crops, berry harvest (age reset) | seeds onto farmland; till grass/dirt next to water; water from a bucket at a farm site |
| BUILD / INVENT | camp radius | replaceable plants, snow layers, own placed blocks (upgrades) | blueprint blocks into air or replaceable plants only |
| LANDSCAPE | camp radius | replaceable plants, snow layers | grass/dirt to dirt path; saplings and flowers; torches; dirt into 1-deep holes |
| MINE | mine box (24×24 around the mine entrance, outside the camp core) plus exposed ores within camp radius + `resourceRadius` | `#hardcorefriends:mineable_natural` only | torches in tunnels |
| GATHER_WOOD | camp radius + `resourceRadius` | logs of natural trees only, with ≥ 3 non-persistent leaves touching the crown and all logs within reach | saplings at the stump |
| GATHER_EARTH | quarry boxes (5×5, max 3 deep) in the resource ring | dirt, grass, sand, gravel, natural stone | — |

The camp radius starts at `campRadius` (24) and grows by 4 per stage, up to 40.

Each change is appended to `CampData.editLog` (the last 256 entries; see `/friends log`) and counted per companion. Each companion is limited to 1 edit per 8 ticks.

## 6. Shared camp and settlement

`/friends camp set` stores the camp centre and dimension. `/friends chest` links the chest or barrel the player is looking at as the **shared supply chest**. If no chest is linked, Oak builds one from 8 planks at the camp and links it.

Stages are gated by completed structures **and** the Unity score:

| Stage | Name | Unity | Builds (real materials) |
|---|---|---|---|
| 0 | Campsite | 0 | supply chest (Oak, if none), campfire (3 logs, 3 sticks, 1 coal/charcoal) |
| 1 | Camp | 0 | crafting table, furnace (8 cobblestone), 4 torch posts, Fern's farm plot (9×9 around a water source; needs a water bucket if no water nearby) |
| 2 | Hamlet | 100 | Cabin (7×7 planks/logs, door, glass panes, slab roof); Terra's paths; Spark's automatic door (pressure plates) |
| 3 | Village | 250 | Storehouse shed with extra chest; Aegis's watchtower (cobblestone, ladder); lantern posts; Spark's drop-off hopper on the supply chest; fenced farm |
| 4 | Settlement | 500 | Second cabin; Spark's auto-smelter (chests, hoppers, furnace); night lamp posts (inverted daylight detector + redstone lamp) once nether materials exist; flower gardens |

**Blueprints** (`camp/Blueprints`) are code-defined lists of `(dx, dy, dz, MaterialSpec, state tweaks)`, placed bottom-up with attachments last.

- A `MaterialSpec` accepts any item from a tag (any planks, any log, any wooden slab or door), so whatever wood the team has is used.
- `SiteFinder` looks for a flat site near the preferred offset. Ground variation must be ≤ 1, filled with cobblestone or dirt foundation blocks from real stock. Every footprint block must be air or a replaceable plant, with no build markers within 2 blocks. Sites never overlap other reserved sites.

## 7. Supplies

- **Backpack:** 9 slots, 18 at Unity 100 and 27 at Unity 500. It is saved with the entity. Sneak + right-click with an empty hand opens it as a chest UI.
- **Deposit:** when ≥ 80% full or carrying surplus (anything outside the role's keep-list), a companion walks to the supply chest and deposits it.
- **Restock:** a missing role tool, food below 2, or materials for the current job are fetched from the chest.
- **Crafting** (`camp/Crafting`, hard-coded recipes with real consumption): logs to planks, planks to sticks, coal or charcoal + stick to torches, tools (wood, stone, iron), bread, chest, crafting table, furnace, doors, slabs, fences, pressure plates, hopper, redstone torch, daylight detector, redstone lamp, ladder, lantern. Anything beyond the 2×2 recipes needs a crafting table within 6 blocks.
- **Smelting:** raw iron, copper or gold plus fuel go into the camp furnace; outputs are collected later.
- **Sharing:** a companion holding what a friend's job needs walks over and hands it across (+2 Unity). Generous friends give food to hungry players and companions.

## 8. Unity bond

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
- **Teamwork:** a deposit or delivery +1 (cap 60/day); a direct hand-off +2; a hostile killed near a friend or player +3; a stage built +30; a contraption built +15; a player feeding or gifting a friend +2 (cap 20/day).

**Losses:** a friend's death −80; a dismissal −20.

**Bonuses:**

| Score | Bonus |
|---|---|
| 100 | 18-slot backpacks; friends feed hungry players |
| 250 | *Work rhythm*: +15% work speed when another friend is within 12 blocks; *Careful hands*: 20% chance a use costs no durability |
| 500 | 27-slot backpacks; friends regenerate 1 HP every 4 s at camp; Scout's warnings make the warned mob glow for 8 s |
| 800 | *Rally*: when a player drops below 6 HP with ≥ 2 friends within 16 blocks, the player gets Regeneration I for 5 s and the friends target the attacker (10-minute cooldown). This does not prevent death. |

## 9. Role routines (summary)

- **Fern:** harvests mature crops and replants immediately from the drops; replants empty farmland; tills more farmland next to water (capped per stage); bakes bread; uses bone meal; feeds hungry players.
- **Oak:** builds the next blueprint for the stage; crafts planks, sticks, doors, slabs and torches from stock; repairs missing blocks of finished structures; upgrades torches to lanterns.
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
- **Spark:** builds working redstone contraptions (automatic door, drop-off hopper, auto-smelter, night lamps); keeps the torch supply up; smelts.
- **Aegis:** guards players and the camp; patrols at night (on the watchtower once built); equips the best weapon, armour and shield from the chest.
- **Sage:** recomputes camp needs and announces the team focus; gives contextual Hardcore survival advice (health, food, darkness, phantoms, night, armour, tool durability, mining depth); `/friends advice` and `/friends plan` show the report.
- **Terra:**
  - turns grass into dirt paths between camp features;
  - plants saplings and flowers;
  - lights dark spots in camp with torches (spawn-proofing);
  - tidies dropped items into the chest;
  - fills 1-deep holes;
  - builds farm fences.
- **Rowan:** fells natural trees (only fully reachable ones) and replants; quarries dirt and stone in bounded 5×5 pits; forages berries, apples and saplings; delivers materials to Oak first.

## 10. Commands (permission level 0, no cheats)

`/friends help | list | recruit <name> | dismiss <name> | follow <name|all> | stay <name|all> | work <name|all> | where <name> | backpack <name> | camp | camp set | chest | unity | advice | plan | log | chatter <quiet|normal|chatty>`

## 11. Known limits (stated honestly to the user)

- Friends only act while their chunks are loaded (near a player). They do not work while you are far away.
- Building follows fixed blueprints adapted to the available wood. They do not design new buildings.
- Pathfinding is vanilla mob pathfinding. Friends can get stuck on complex terrain; stuck tasks time out and are retried later.
- Mining is limited to one staircase mine with branch tunnels plus exposed ores near camp. Tree felling is limited to trees they can fully reach.
- Contraptions are a fixed set of vanilla redstone builds.
- Dialogue is pre-written lines chosen by situation and personality, not free conversation.

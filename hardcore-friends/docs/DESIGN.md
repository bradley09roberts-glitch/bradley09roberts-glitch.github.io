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
| SAGE | Sage | Strategist | `sage.png` | thoughtful, observant | `#B39DDB` | wooden sword |
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
| 1 | `RetreatGoal` | health ≤ retreatFraction, or on fire, or drowning. Moves away from threats toward camp, Aegis or a player; eats from the backpack once safe; announces once. Keeps 20 blocks from any archer in sight (12 from other threats) and does not drift home while an archer stands within 18 blocks of camp. A friend too hungry to heal (hunger ≤ 10) with nothing in the backpack falls back only from danger (within 16 blocks): resting cannot heal them, so their jobs take over (they eat, or rest at camp, section 6). |
| 1 | `AvoidDangerGoal` | creepers within 7 blocks (10 if hissing), except a healthy Aegis facing a quiet one. Non-fighters also back away from hostiles within 8 blocks unless healthy and holding a tool (`canStandAndFight`), and leave the line of fire of an archer aiming at them (to 20 blocks). Flees toward protectors. |
| 3 | `CompanionMeleeGoal` | uses the best weapon from the backpack; only when there is a target |
| 4 | `FollowLeaderGoal` / `StayGoal` | mode-dependent |
| 5 | `WorkGoal` | runs `TaskScheduler` (MOVE + LOOK flags) |
| 3 | `OpenDoorGoal` | friends open and close wooden doors |
| 6 | `LookAtPlayerGoal` | idle polish |
| 7 | `RandomLookAroundGoal` | idle polish |

Target goals: `HurtByTargetGoal` (fight back) and, for Aegis, `DefendFriendsTargetGoal` (hostiles within 16 blocks of any player, companion or the camp). In FOLLOW mode every armed friend also defends the leader. `MutualDefenceTargetGoal`: friends not in STAY join fights against hostiles that are going for them, a friend or a player within 8 blocks, when `canStandAndFight` (healthy, `isHealthy`: above max(50%, retreat fraction + 10%), holding a tool, and the threat within their reach, `meleeReach`; against an archer a non-fighter only stands its ground within 3 blocks, because chasing a skeleton with a hoe just gets them shot).

**The rally inside the camp.** Inside the camp friends stand together more widely. An armed friend (sword or axe) at work in the camp joins a fight against a hostile going for anyone within 16 blocks (`MutualDefenceTargetGoal.RALLY_RANGE`) and goes for any hostile the night watch raised the alarm about; the friend on watch takes on any hostile that comes into the camp. For them `meleeReach` against a hand-to-hand threat inside the camp is 24 blocks (`CompanionEntity.CAMP_REACH`, so the target is not dropped on the way), otherwise 8. Inside the camp no line of sight is needed, for Aegis's guard goal too, as long as the hostile is on the camp's own ground (within 4 blocks up or down, not in a cave below): they path to it, and a hostile beyond 8 blocks with no path to it is left alone for 10 s. Any friend at work who has neither landed a blow nor been hit for 10 s while their target is out of arm's reach gives it up for 30 s (`hasGivenUpOn`), so nobody stands all night staring at a zombie behind a wall.

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

The scheduler re-scores every 20 ticks. It switches when idle, when the current task finishes, or when another task outscores the current one by ≥ 25. It enforces `maxTicks`, so a stuck task fails. Four rules keep a friend's own needs safe from work:

- **Desperate needs come first.** A job scoring `TaskScheduler.URGENT` (140) or more takes over at once from any job scoring less, without the margin. Only desperate needs score that high (section 6), and no work can: the most is 132 (building, 60, at the camp's full need for materials, weight 2.0, with Sage's 10% bonus).
- **Work never wakes a sleeper.** While a friend lies asleep, only another needs job can take over (a starving friend gets up to eat); danger still wakes them (the sleep job and the reflexes).
- **Too weak to work.** A friend badly hurt (health ≤ retreatFraction) and too hungry to heal (`CompanionEntity.tooWeakToWork`) only takes on needs jobs and `TaskScheduler.FIT_WHEN_WEAK`: going home, the camp chest, and growing and baking food in the camp, so a starving camp can still feed itself. A job outside that list is stopped, so nobody on one heart goes back into the mine, the woods or the wilds.
- **The night is for sleep.** At night (`Camp.isNight`) a friend only takes on needs jobs and `TaskScheduler.NIGHT_JOBS`: coming home, the watch (`common.watch`, Aegis's guard and gear), Terra's lighting of a dark camp, feeding a hurt friend or a hungry player, fetching a lost weapon or food from the chest, and idling. Any other job is put down at nightfall and waits for the morning. Bedtime itself (section 6) then only has to beat pastimes, chats and warming up, not work: in the recorded run sleep (45–55) lost to camp work (60–130) all night, and friends hardly slept.

`TaskRegistry.create(id)` gives every friend the same job list: the common upkeep jobs (`ai/task/common`), the needs jobs (`ai/task/needs`), and every role's jobs, their own role first, each wrapped in a `SpecialityTask` for its role (section 5). The only jobs a friend never gets from another role are `TaskRegistry.SPECIALIST_ONLY`: Aegis's gear and guard duty, Sage's observing and stores review, and Scout's report.

Score bands:

| Score | Meaning |
|---|---|
| 140 and up | Desperate needs (`TaskScheduler.URGENT`): starving with food at hand (150), exhausted at night (140) |
| 70–89 | Urgent upkeep: missing tool, full backpack, night return, bedtime (75–82), keeping watch (75), lighting a dark camp at night (80) |
| 40–69 | Main role work (raised by the camp's needs, up to 132) |
| 20–39 | Secondary help: sharing, tidying, crafting surplus |
| 1–19 | Idle and social |

`CampNeeds` (recomputed every 30 s) supplies a 0–1 need for food, wood, stone, dirt, torches, ore and build materials. Jobs that meet a need multiply their score by 1 + need (up to 2); Sage being alive adds a 10% planning bonus to tasks that address the top need. Food is counted as food: in loaves' worth of hunger, wheat as the bread it bakes into (three to a loaf), never seeds or raw potatoes, and not the carrots a farmer keeps for planting; the camp wants four days of food for the team (about two loaves a friend).

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
| Hunger | −100/9000, ×1.3 while working | About half a loaf of bread's worth a day (see "Food economy" below) |
| Energy | −70/840 while awake | Not while asleep. Asleep, it rises by the in-game time slept (`CompanionEntity.settleSleep`): +100/500 a second under a roof, +65/500 in the open, so a night in the cabin is about +100. A night the players sleep through (the clock jumps to morning) counts in full, for friends at work whether already asleep or still on their way to bed |
| Social | +0.15 with company, −0.1 alone | Company is another friend, or a player who is not spectating, within 6 blocks |
| Fun | −100/1100 while working | |
| Comfort | towards the surroundings' comfort, at most 0.5 a second | Base 55; +20 under a roof; +25 near a lit campfire (5 blocks); −30 in rain in the open; −15 dark in the open; −20 below half health |

"Working" means following a player, or on any job other than a needs job (`needs.*`) or idling.

**Meeting needs.** The needs jobs score higher the lower their need: a desperate need scores 140 or more (`TaskScheduler.URGENT`), above anything work can score, and takes over from the job in hand at once (section 4); a pressing one is urgent upkeep (70–89); a mild one sits in the main or secondary band and waits for the job in hand to end. Their ids start with `needs.`, which counts as time off.

- `needs.eat`: eats one real food item (`#hardcorefriends:companion_food`), from the backpack first, otherwise taken from the supply chest, choosing the item that best fits the hunger. `CompanionEntity.eat(stack)` heals by the food's nutrition, fills hunger by nutrition × 6 (`hungerValue`), and keeps bowls and bottles. Below 70 (peckish) it scores 42–60, below 25 (hungry) 80, and below 15 (starving) 150, before anything. With no food in the backpack or the supply chest it scores 0, so it never breaks off work only to find nothing; the friend asks for food instead (`NO_FOOD`, said by `MoodPassives`).
- `needs.sleep`: at night everyone not on watch lies down in their own place (side by side in the cabin, or around the camp centre) and regains energy, faster under a roof. Bedtime scores 75 + (100 − energy) × 0.1 (75–82): above every pastime, chat and warm-up (at most 66), and work is not on offer at night (section 4). Supper comes first for a friend below 50 hunger with food at hand (bed scores 40), warming up by the campfire first for one below 30 comfort with no cabin to sleep in (30), and the trip home first for one outside the camp (0); one exhausted (energy below 30) goes to bed before anything but a starving meal (140). Aegis does not turn in with a monster within 24 blocks. Work never wakes a sleeper; they wake at dawn, when hurt, when a monster comes within 8 blocks or a friend within 16 is fighting one, when the watch raises an alarm after they lay down, and when their own watch begins. Exhausted friends nap by day (below 15 energy); Aegis and anyone who kept a watch last night nap below 40.
- `needs.rest`: a friend too weak to work (badly hurt and too hungry to heal, as a starving friend soon is) comes home and rests by the lit campfire, or at their sleeping place, until there is food; at night they sleep. Meanwhile they take on only needs jobs and `TaskScheduler.FIT_WHEN_WEAK` (going home, the camp chest, growing and baking food in camp), never the mine, felling or exploring, and they eat the moment food turns up.
- `needs.socialize`: a lonely friend walks over to another friend who is awake and not in trouble (or a player in camp) and they chat (`CHAT`, `CHAT_REPLY`). Both feel better and Unity gains 1 (at most 30 a day).
- `needs.leisure`: a bored friend spends a short while on a pastime that suits them (`LEISURE`). Pastimes change no block.
- `needs.cosy`: a chilly friend warms up by a lit campfire or in the cabin (`COSY`).

Needs jobs only run in WORK mode, like every job. A friend who is following or staying eats from the backpack only when hurt (`RetreatGoal`) or hungry (below 25).

A player handing food to a friend who is hurt, or whose hunger is below 60, has them eat it at once (it heals and fills hunger); otherwise it goes into the backpack.

**Starving.** At hunger 0 a friend takes 1 starvation damage every 4 s while above 2 health (one heart), as a player does on Normal difficulty. Starving never kills on its own. Health only regenerates while hunger is above 10 (`Needs.canHeal`), and that includes the Close Friends healing at camp.

**Food economy.** The numbers are tuned so a reasonably run camp feeds itself:

- **What friends eat.** A day is 1200 s: about 650 s of daylight, at work (×1.3), and 550 s of night, asleep. That is 650 × 0.0144 + 550 × 0.0111 = 15.5 hunger (`Needs.typicalDailyHunger`), about half a loaf of bread (30): a loaf every two days, a little more for a friend at work all day (17.3) and a little less at rest (13.3). Nine friends eat 140 hunger a day: 4.7 loaves, or 14 wheat. A friend eating to 100 lasts six days before starving; from peckish (70) it is four and a half.
- **What a farm grows.** A field of one crop grows at half speed (vanilla slows a crop with the same crop beside it on a diagonal): one harvest every 2.4 days, 0.42 harvests per farmland a day (17.6 random ticks a day, a 1 in 6 chance each, 42 for seven stages). A wheat harvest gives one wheat (a third of a loaf, 10 hunger); a carrot harvest about 2.7 carrots, 1.7 left to eat once one is replanted (31 hunger). So a farmland feeds 4.2 hunger a day in wheat and 12.9 in carrots.
- **The starter camp.** Fern's first 16–24 farmland in wheat grow 67–101 hunger a day, half to three quarters of what nine friends eat; the player's starter food, Rowan's berries and apples, and bone meal cover the rest while the camp is new, and the farm doubles to 48 farmland at stage 1 (202 a day, 144%). Fern sows carrots first whenever she has some (`Crops.bestSeed`): 16 farmland of carrots already grow 206 a day. She keeps only 16 carrots and 16 potatoes for planting (`KeepList.SEED_CROPS_KEPT`); the rest go to the chest to be eaten. Potatoes come last: friends cannot eat them raw and nobody in camp bakes them.

**Mood.** `Needs.moodValue()` is the weighted average of the needs (hunger 0.30, energy 0.25, social, fun and comfort 0.15 each):

| Mood value | Mood |
|---|---|
| below 25 | miserable |
| 25–45 | low |
| 45–65 | okay |
| 65–85 | good |
| 85 and up | great |

- **Work speed:** `Needs.workSpeed()` = 0.8 + 0.3 × mood value / 100, from 0.8 (miserable) to 1.1 (everything met).
- **Speech:** every 15 s (`MoodPassives`), a friend who is awake and not fighting and is hungry (below 25), unless already eating, asks for food (`NO_FOOD`) when there is none in the backpack or the supply chest, or says `STARVING` at hunger 0 once they have asked. Otherwise, unless falling back, they say `MOOD_LOW` naming their worst need ("hunger", "energy", "social", "fun" or "comfort") when the mood is low or miserable (unless that need's job is already running), or `MOOD_GREAT` when it is great. The lines' cooldowns (`NO_FOOD` 4 min, `STARVING` 1 min, `MOOD_LOW` 5 min, `MOOD_GREAT` 15 min) and the friend's chattiness keep this occasional.
- **Team spirit:** every in-game hour (1000 ticks), if the team is in high spirits (`MoodPassives.highSpirits`: the mood of the team's average needs, all loaded friends, is at least 75, and no friend's mood is low or miserable), Unity gains 1, at most 12 a day (`Unity.teamSpirit`, category `spirit`). A low mood never costs Unity. A well-run camp gets there: fed (eating from 70 up to about 100, so hunger averages about 85), rested, together, by day about 0.30 × 85 + 0.25 × 72 + 0.15 × (90 + 65 + 60) = 76. A great mood (85) is a peak, such as a fed friend waking rested, and is what `MOOD_GREAT` celebrates.
- **Displays:** `/friends needs` shows the five needs as bars, the mood and what the friend is doing about their lowest need; `/friends list` and the right-click status line show the mood (and the worst need when the mood is low).

### The night watch (`camp/NightWatch`)

In the recorded two-day run Aegis guarded until midnight and went to bed; a zombie came in, he fought it alone down to one heart, and a second one killed him at the camp centre while everyone slept. Sage, with nothing to fight with, could only run, and was killed. So the camp keeps a watch, raises an alarm and stands together.

- **Two watches.** The first runs from dusk to midnight (time of day 18000), the second from midnight to dawn (`NightWatch.watch`). A night runs from one noon to the next (`nightIndex`), so the morning after still belongs to it.
- **The rota** (`NightWatch.watcher`, worked out once per tick and remembered in `CampData.memory("night_watch")`, so it survives a reload). Aegis keeps the first watch whenever he is fit for it. Any other watch goes to a friend holding a tool to fight with, ranked: healthy (`isHealthy`); did not keep a watch last night (the duty rotates night by night, so nobody misses sleep every night while anyone else can stand in); best weapon (`bestWeaponRank`: any sword before any axe before any other tool, sturdier first); most health; roster order (so the choice is deterministic). Tonight's first watcher never keeps the second watch too, and a friend alone at the camp (other than Aegis on his first watch) keeps none: there is nobody to watch over. A watcher stays on watch until it ends unless they stop being fit (dead, unloaded, out of WORK mode, too weak to work, or below 15 energy, `TOO_TIRED`), and then the next friend takes over: the rota works with Aegis dead or away. `keptWatchRecently` lets watchers nap sooner the next day.
- **Keeping watch.** `common.watch` (`WatchTask`, every friend has it; 75 while it is their watch, in 30 s rounds so a pressing need gets a look in) stands by the lit campfire, or the camp centre, stepping round it every 10 s and looking out. Aegis keeps his watch with his guard duty (`GuardTask` scores 75 at night only while it is his watch, and ends its run when his watch does; away from any camp he guards all night as before). A sleeper whose watch begins gets up (`SleepTask` ends).
- **The alarm.** Every 10 ticks (`NightWatch.tick`) the friend on watch, if awake, looks out: a hostile within 24 blocks of them, inside the camp and within 12 blocks up or down, that they can see, that is within 12 blocks, or that is already going for someone, goes on the alarm list and they shout `ALARM`. A new alarm wakes every sleeper inside the camp (`alarmRaisedSince`); armed friends in the camp go for the hostiles on the list (`alarmed`) while those live and stay in the camp (section 4, the rally).

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
- **Sharing:** a companion holding what a friend's job needs walks over and hands it across (+2 Unity). Generous friends give food to hungry players and companions; feeding a hurt friend who has no food (below 60% health) scores 125, before any gathering even at the camp's full need for it.

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
- **Friendship:** two friends chatting +1 (category `chat`, cap 30/day); team spirit, while the team is in high spirits, +1 per in-game hour (category `spirit`, cap 12/day; section 6).

**Losses:** a friend's death −80; a dismissal −20. A low mood costs nothing.

**Bonuses:**

| Score | Bonus |
|---|---|
| 100 | 18-slot backpacks; friends feed hungry players |
| 250 | *Work rhythm*: +15% work speed when another friend is within 12 blocks; *Careful hands*: 20% chance a use costs no durability |
| 500 | 27-slot backpacks; an extra 1 HP every 4 s while at camp with no target (on top of normal out-of-combat healing, and like it never while starving); Scout's warnings make the warned mob glow for 8 s |
| 800 | *Rally*: when a player drops below 6 HP with ≥ 2 friends within 16 blocks, the player gets Regeneration I for 5 s and the friends target the attacker (10-minute cooldown). This does not prevent death. |

## 11. Role routines (summary)

These are each role's jobs, named after the specialist. Through `SpecialityTask` (section 5) any friend can run them, except the specialist-only duties noted in section 4.

- **Fern:** harvests mature crops and replants immediately from the drops; replants empty farmland, carrots first (the most food per farmland); tills more farmland next to water (capped per stage); bakes bread; uses bone meal; feeds hungry players; keeps livestock: brings wild animals to the pen, breeds them, butchers the surplus, cooks meat on the campfire, shuts the pen gate, hunts in the gathering ring when food is short (section 12).
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
- **Aegis:** guards players and the camp; keeps the first night watch, walking his posts (on the watchtower once built), and sleeps the second half of the night; equips the best weapon, armour and shield from the chest.
- **Sage:** carries a sword (her role tool, since planning needs none) to stand with the others; recomputes camp needs and announces the team focus; gives contextual Hardcore survival advice (health, food, darkness, phantoms, night, armour, tool durability, mining depth); `/friends advice` and `/friends plan` show the report.
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

**The gate.** Friends open and shut only a gate they placed, through `WorldEditGuard.canTransform` with reason FARM (same block, `isPlacedByFriends`). Pen jobs walk through it (`Pen.enter` / `Pen.leave`): to the gate, animals within 2 blocks of it are first sent to the back of the paddock (so none slips out, or is jostled out, while it is open), open, through, shut. It is never shut on anything standing in the gateway, on a friend still in the paddock, or while a player is in or within 3 blocks of the pen (`Pen.playerNear`); a gateway that does not clear within 5 s is left open. So the jobs that open it (`bring_animal`, `breed`, `butcher`) score 0 while a player is that close. `fern.shut_gate` (45) shuts it when it stands open with animals inside and nobody in the paddock. `common.leave_pen` (85, every friend, outside `SpecialityTask`) takes any friend found in the paddock without a pen job in hand (called away mid-job, wandered in) out through the gate, so nobody is ever penned in.

**Jobs** (scores are the specialist's; livestock work is weighted by the camp's FOOD need and stays in the main band: `min(69, base × CampNeeds.weight(FOOD))`):

| Id | Base | What |
|---|---|---|
| `fern.bring_animal` | 46 | By day: the nearest wild cow, pig, sheep or chicken (`Wildlife.mayLead`) of a kind with fewer than 2 grown animals in the pen, or one inside the camp of a kind already penned with room left (below 6), so strays are re-penned. Every rule is checked again, afresh, before the lead goes on or the animal starts to follow (`Wildlife.mayLeadNow`). With a lead (backpack or chest) it is tied on (`setLeashedTo(friend)`; the lead item is used up and given back when untied in the paddock; a snapped lead drops as in vanilla). Otherwise the friend holds the animal's tempt food (`Animal.isFood`: wheat, carrot/potato/beetroot, seeds), not used up, and steers it each tick like vanilla tempting (looks at the friend, walks after them within 10 blocks, stops 2.5 blocks short); a lagging animal is gone back for. At the gate: pen animals by it are shooed to the back (`Pen.shooFromGate`), the gate opens, the friend walks to the paddock centre with the animal behind and shuts the gate as soon as both are in and clear of the gateway (if the animal will not come clear within 3 s, or the gateway will not clear, the gate is left for the way out). An animal lagging outside is fetched with the gate shut behind the friend (out via `Pen.leave`, back to HOME). Led to a back corner, let go and sent to the other back corner, out via `Pen.leave`. A gate the job opened is shut on stop when safe. Success only if it is still in the pen. |
| `fern.breed` | 44 | Two ready adults of a kind (age 0, not in love) and room for one more (at most 6 of a kind, 12 in all): two of their food from backpack or chest, into the paddock (gate shut behind), one fed to each (consumed; `setInLove`), out. Vanilla `BreedGoal` makes the young one. |
| `fern.butcher` | 42 | A kind with more grown animals than `Livestock.keepAdults(penned, kind)`: 4 (`KEEP_ADULTS`) while the pen has room for a young one; a full pen (12) keeps `max(2, min(4, 12 / kinds − 1))` of each kind present (2 with four kinds, 3 with three), so a full pen always has a surplus and the room a cull frees lets `breed` run again (without this, 3/3/3/3 or 4/4/4 stalled for good). Surplus = min(adults − kept, takeable), where takeable excludes young animals, animals in love and anything `Wildlife.isSomebodys` (named, leashed, riding/ridden, saddled, wearing body armour, owned): those count among the kept and are never struck, so a pen whose only surplus is the player's animals scores 0. With the best sword or axe (borrowed from the chest if need be) the surplus is killed, at most 2 a visit, rechecked before every blow; never with the gate open or missing (`Pen.gateShut`: a gate that would not shut on entering, or that someone opens, sends the friend out without a blow). The drops (meat, leather, wool, feathers) near the body are gathered. |
| `fern.cook` | 48 | Raw beef, pork, mutton, chicken, rabbit, cod or salmon (backpack or chest) on the camp's lit campfire (the `CAMPFIRE` site, else the nearest lit campfire within 10 blocks of the centre), up to its free slots, via `CampfireBlockEntity.placeFood`. The friend remembers which slots they filled (the very stack placed) and, tending the fire, notices each one empty; only then is one cooked piece owed, of that raw item's cooked form (`Livestock.cookedFrom`), and only a freshly dropped one (≤ 40 ticks old, no player thrower) is picked up. A player's food on the same fire and cooked food already on the ground are never taken; owed pieces someone else picks up first are let go. Then the pieces are deposited in the chest. If the fire is full: the camp furnace, only a plain `FURNACE` the friends placed (a blast furnace cannot cook food), with its output empty. Fuel is fetched for the meat (coal 8 items, planks 1.5, as `SmeltTask.halfSmeltsPer`), and only as much meat goes in as the fuel slot plus the fuel carried can cook (at most 8), so raw meat is never left in it unfuelled (that would block `flint.smelt`). A friend-placed furnace that went out with raw meat its fuel cannot cook is refuelled the same way. `meals_cooked` counts only pieces the fuel covers. Its output is collected by `flint.collect_smelted`. |
| `fern.hunt` | 40 | Only when FOOD need ≥ 0.4, by day, healthy (above max(0.5, retreat fraction + 0.1) of max health) and armed (sword or axe): the nearest animal `Wildlife.mayHunt` allows; drops gathered. Every rule is checked again every second and before every blow (the block checks afresh); any failure calls the hunt off. |
| `fern.shut_gate` | 45 (flat) | See the gate. |

`bring_animal`, `breed`, `butcher` and `shut_gate` are the pen jobs (`Pen.JOBS`): besides each being exclusive, none starts while another friend is on any of them, so one friend at a time is at the gate. `cook` and `hunt` are exclusive too (one cook at the fire, one hunter).

**Safety rules** (`Wildlife`). An animal counts as somebody's, and is never led or hunted, when it has a custom name, is tamed or owned (`OwnableEntity` with an owner), is leashed, rides or is ridden, wears a saddle or body armour (`Wildlife.isSomebodys`; the same test keeps the player's animals in the pen from being butchered), stands within 4 blocks of anything `WorldEditGuard.looksPlayerBuilt` sees (build markers and block entities the friends did not place: fences, gates, walls, crafted blocks; barriers count), or stands inside a player's enclosure (`Wildlife.enclosed`: in each of the four directions a fence, wall or fence gate within 48 blocks, or another build marker within 24; each column is checked from just below the animal and the local surface (`MOTION_BLOCKING_NO_LEAVES` heightmap) up to whichever is higher, at most 8 blocks either way, so a fence line on a slope above or below the animal counts; the friends' own blocks do not count). Every job also skips young animals, animals in the pen, animals outside the gathering zone (camp + `resourceRadius`) and anything within 24 blocks of where a friend died in the last three days (`CampData.nearDanger`). Hunting additionally never happens at dusk or night, never inside the camp (camp animals are the pen's), and never takes one of the last two grown animals of a kind within 24 blocks.

Items inside the pen are not tidied by `common.collect_items` unless the friend is in the paddock (an egg behind the shut gate is out of reach), and `terra.plant` never picks flower spots in the pen's footprint.

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
- Needs are five numbers met by a fixed set of jobs (eat, sleep, chat, pastime, warm up, rest). Friends sleep on the spot they lie down on, not in beds.
- Friends cook raw meat and fish on the campfire or in their own furnace (section 12), but do not bake potatoes; raw potatoes are not their food.
- Needs jobs run only in WORK mode: a friend following or staying does not eat (except from the backpack when hurt or hungry), sleep or rest.
- Livestock is cows, pigs, sheep and chickens in one pen (rabbits are only hunted); no shearing, milking or eggs. A lured animal can wander off before the pen and is fetched again later. An unnamed animal of the player's standing loose with nothing player-built nearby looks wild. The camp's food planning (`CampNeeds`) counts cooked meat, not raw meat, as food.

- The night watch sees what is in sight, close by or already attacking, inside the camp; not a hostile in a cave below it. The alarm wakes sleepers inside the camp only. A daytime thunderstorm is as dark as night (`isDarkOutside`), so friends head home and rest through it.

## 15. 3.0: a village of their own

3.0 adds eight feature packages and a ninth, `civic`, that holds only the interfaces they answer each other through. Each package has a page in `docs/v3/` with its behaviour, settings, commands, a "for other packages" section and its honest limits; this section is the map of how they fit together. Nothing in 3.0 has been run in game yet.

### 15.1 The packages

| Package | Owns | Provides | Page |
|---|---|---|---|
| `architecture` | The building library (plans as data files under `data/<ns>/blueprints/`, reloaded with `/reload`; `camp.Blueprints` reads the camp's own plans, in the same format, from `/hardcorefriends/camp_plans/` inside the mod), `Construction` (building a plan on a chosen site), `Styles`, scaffolding, the material jobs (firing at the furnace, digging sand and clay, shearing), `/friends builds` | `civic.BlueprintLibrary` | `architecture.md` |
| `navigation` | Every friend's paths (`FriendNavigation`, from `CompanionEntity.createNavigation`), `Senses`, `Routes`, `Wayfinder` and `WayOutGoal` (getting unstuck, the rescue), `Sprint`, `/friends senses` | | `navigation.md` |
| `people` | Friendship and romance, dates, weddings, births, children and growing up, family names, the skin list (`skins.json`), `/friends family`, `couples`, `relationships` | `civic.Families` | `people.md` |
| `village` | The town plan (streets and plots), households and houses, real beds, the daily routine, streets, water and fields, the Town and City stages (`VillageGrowth`), `/friends village`, `home` | `civic.Homes`, `VillagePlan` | `village.md` |
| `market` | The 15 trades, assigning them, workplaces, shops and the trading screen, `/friends trades`, `shops` | `civic.Professions` | `market.md` |
| `life` | The calendar, feasts, market day, music, birthdays, grief, funerals and graves, the Village Chronicle, `/friends calendar`, `chronicle` | `VillageLife.chronicle` | `life.md` |
| `defence` | The alarm and the bells, taking cover, posts, the guard rota, raids, the fire watch, `/friends defence` | `Alarm.isActive` | `defence.md` |
| `pets` | Adopting and keeping cats and dogs, Scout's maps and their frames, `/friends pets`, `maps`, `map` | | `pets.md` |
| `civic` | Interfaces only: `Homes`, `Families`, `Professions`, `BlueprintLibrary` | | |

Each package keeps what it must remember in its own saved data (`VillageData`, `PeopleData`, `MarketData`, `LifeData`, `PetsData`); building sites stay in `CampData` like the camp's own (village site keys start with `village.`, graves' with `life.grave.`).

### 15.2 The civic facades

Every facade has the same shape: a nested `Provider` interface, a do-nothing default, a `volatile` current provider, `provide(p)` (called once, from the owning package's `init()`) and `get()`. Callers always go through `get()`, so any package, and the core, works more plainly without the owner: nobody has a house, everyone is single, nobody holds a trade, there are no library plans. People are named by entity UUID, which stays the same when a friend changes dimension.

| Facade | Provided by | Answers | Asked by |
|---|---|---|---|
| `Homes` | `village.HomesProvider` | `homeOf`, `bedFor` (the foot of the friend's own bed), `homes`, `roomForOneMore`, `moveIn`, `moveOut`. Default: no homes, never room for one more, moving in or out does nothing | The sleep and rest jobs (own bed: `SleepTask`, `Spots.ownBed`); people (room for a baby, a newborn or a married household moving in, someone who left moving out, a child's way home); defence (shelters); pets (a pet's home and its night spot by the bed) |
| `Families` | `people.FamiliesProvider` | `partnerOf` (the living husband or wife), `parentsOf`, `childrenOf`, `householdOf`, `familyName`. Default: everyone single, a household of one | Village (households to house); life (who mourns, names in the Chronicle); pets (who inherits a pet, a parent adopting for a child, full names); `CompanionEntity.nearestProtector` (a child runs to a parent first) |
| `Professions` | `market.ProfessionsProvider` | `professionOf`, `workplaceOf`, `title`. Default: no trades | The status line ("Mabel (Forager, Baker)"); people (the school's teacher); life (market-day stallholders, the musician, the feast cook); defence (a `guard` trade, should one ever exist); `/friends jobs` |
| `BlueprintLibrary` | `architecture.PlanLibrary` (a data reload listener) | `get(id)`, `byKind(kind)` (exact, or every sub-kind of a kind without a colon), `ids()`. Default: no plans | `camp.Blueprints` (a site's plan by id), `architecture.Styles` and `/friends builds`, the village planner, growth and `VillagePlan`, the market planner |

### 15.3 `VillagePlan` and `Construction`

`village.VillagePlan` is not a facade but the village's own public API, all static, server thread only and cheap (no world scans):

- `requestBuilding(server, kind, reason[, count])` asks for library buildings of a kind (`shop:bakery`, `workplace:fisher`, `civic:school`; a kind without a colon means any of its sub-kinds). Asking again changes nothing; it returns false only when the library has no plan of that kind. The village chooses the plot and the moment.
- `buildingsOfKind` (standing, oldest first), `allOfKind` (being built too) and `building(server, siteKey)` return `Building` records: site key, kind, plan id, name, dimension, origin, rotation, `built`, and every marker in world positions (`marker("counter")`, `first("job")`). `isBuilt`, `isSiteBuilt`, `isRequested`.
- `population` and `populationFull` (the team against `maxPopulation`).

Asked by the market (`VillageLink` asks for workplaces; the market then finds finished ones through `Construction`), life (the tavern, the market square's stalls, the town hall and its lectern, the chapel for the cemetery, the bakery's shelves for a birthday cake), defence (the town hall's and the school's bells, the watchtower, gate and walls as guard posts, houses and civic buildings as shelters) and the settler package (no newcomer joins once the population is full).

The village planner (`village.Planner`, run from the server tick) builds in this order: a house for each household with none, a bigger house for a household that has outgrown its own, a house for a grown-up child still living with their parents, the well in the square, the buildings asked for through `requestBuilding`, the stage's own civic buildings, then decoration. At most `villageBuildsAtOnce` buildings are under way, plus two pieces of decoration, and one plot search runs at a time. A building counts as standing when finished, or 95% built with every bed and chest in. A request that finds no plot waits five minutes.

Underneath, `architecture.Construction` builds any library plan on a site key of the caller's choosing (never a camp structure id): `check`, `reserve`, `job` (a `camp.BuildJob`, run through the ordinary building code and the edit guard), `isFinished`, `progress`, `markers`, `release`, and the `FINISHED` listeners. The town hall's site key contains `town_hall` and is marked completed in `CampData` once it stands, which is how the wedding and the map maker find it.

### 15.4 How the packages plug in

`HardcoreFriends.onInitialize` calls each package's `init()` once, after the 2.0 packages, in this order: architecture, navigation, people, village, market, life, defence, pets. Each registers its civic provider, where it has one, and its hooks there; the few places where the core calls into 3.0 directly are listed at the end of this section.

**The scheduler.**

- **Jobs** come through `TaskRegistry.PACKS`, so every friend carries every job. A speciality's work is wrapped in a `SpecialityTask` for that role (section 5): architecture's scaffolding clean-up and kiln (builder), sand and clay (forager) and shearing (farmer); the village's buildings (builder), decoration and streets (landscaper) and water and fields (farmer); defence's putting up a bell (builder). Building one's own home is `PERSONAL`; the one-at-a-time jobs (a pillar, the furnace, the sand, the sheep, the streets, the grounds, the bell) are `EXCLUSIVE`. Every other 3.0 job scores 0 for anyone it is not for (a trade's work, a guard's shift, a child's games). Job ids start with the package's name (`village.`, `people.`, `market.`, `life.`, `defence.`, `pets.`), except architecture's, which are named after the specialist like the core's (`oak.kiln`, `oak.scaffold`, `rowan.dig_sand`, `fern.shear`), and the needs jobs below. Navigation adds no jobs: it works through a goal and the friend's tick.
- **Time off.** The village's evening and meals (`needs.evening`, `needs.meal`) and life's gatherings, market visits and grave visits (`needs.festival`, `needs.market`, `needs.remember`) are needs jobs, so they count as time off and may run at night (section 4).
- **`TaskScheduler.JOB_FILTERS`**: people (a child only takes `needs.*` jobs, any `navigation.*` job and `People.childJobs()`; `People.allowChildJob` adds defence's taking cover and pets' adopting and feeding); market (a keeper serving a player does only `market.keep_shop` and their needs; once a blacksmith is at work, `combat.smith` is theirs alone); life (the feast cook keeps the feast's food until the feast); defence (keeps the guards on duty, the fighters during an alarm, and those taking cover whose bed is out in the open, out of bed).
- **`NIGHT_JOBS`**: defence's bell, taking cover, posts, guard duty and fire watch; pets' copying a map for a player waiting in the camp. **`FIT_WHEN_WEAK`**: taking cover.
- **`TaskScheduler.JOB_DONE`**: the market counts finished trade work as practice in the trade's kind of work.

**Events and other hooks.**

| Hook | 3.0 uses |
|---|---|
| `CompanionEvents.GOALS` | architecture: coming down from scaffolding (goal, priority 0); navigation: `WayOutGoal` (goal, 2); people: a child running to a grown-up (goal, 2) and grown-ups protecting a child (target, 1); defence: who the defenders go for first (target, 2) |
| `CompanionEvents.TICK` | navigation (watching for stuck friends, sprinting); people (once a second: every team member is on record, and children keep near home and grow up when their time comes); village (a friend left lying in a bed without the sleep job gets up); life (mourning); pets (drawing maps, fussing a pet) |
| `CompanionEvents.DEATH`, `DISMISSED` | pets first (registered at index 0: the pet passes to the family before the people package lets the family go); people (the family); village (out of bed, `Homes.moveOut`); market (the trade is given up); life (grief, the funeral, the grave, the Chronicle); defence (the fire watch forgets them) |
| `CompanionEvents.INTERACT`, `HIT`, `HURT` | market's shop trading runs first (index 0), before the camp's trust check, so any player can trade; people: children taking food (`INTERACT`), fighting side by side (`HIT`); pets: an empty map handed to Scout (`INTERACT`), no harm to a friend from a camp pet (`HURT`) |
| `SocializeTask.CHATTED`, `ShareTask.SHARED` | people: chats and sharing raise friendship |
| `Construction.FINISHED` | village (a plot stands, people move in), market (workplaces are found again), life (a line in the Chronicle) |
| `CampNeeds.EXTRA` | architecture: what a building is short of raises the camp's needs, so gatherers fetch ahead |
| `KeepList.addCommonRule` | market: one fishing rod; pets: up to six maps and one item frame |
| `combat.Archery.HOLDS_POST` | defence: the guard at the watchtower lookout shoots rather than closing in |
| `FriendsCommand.EXTENSIONS`, `CAMP_STATUS` | every package's sub-commands; village, life and defence add a line to `/friends camp` |
| Fabric events | server tick: people, the village and market planners, life, defence, pets; server start and stop clear in-memory state; market closes open trading screens as the server stops (goods held for the visit go back); defence: `UseBlockCallback` (a player ringing a bell; only the owner and trusted players sound the alarm that way); pets: entity load and unload, `AFTER_DEATH`, and `ALLOW_DAMAGE` (section 15.5); architecture: the plans' data reload listener |

**Where the core asks 3.0.** A few core classes call the facades or the village directly, so they behave the same with or without a provider: the sleep and rest jobs use `Homes` for the friend's own bed; the status line shows a trade from `Professions`; a child's protector is a parent from `Families`; `CampProgress` asks `VillageGrowth.ready` before the Town and City stages (both added to the end of `Camp.STAGE_NAMES`, as saves store the stage number); settler recruiting asks `VillagePlan.populationFull`; `CompanionEntity` carries the child flag and builds the navigation package's path finder.

### 15.5 3.0 and the hard rules

- **No new edit reason.** Every block 3.0 changes goes through the existing reasons of `WorldEditGuard`: scaffolding, the village's buildings, beehives, graves, the square's bell, the musician's note block and the winter lights are BUILD; streets, fire put out and a torch at a dark guard post are LANDSCAPE; water, fields, the composter and the beehive's honey are FARM (the guard allows those two only on the friends' own blocks: the same composter, or the same hive facing the same way with less honey); the friends' own lectern and doors are INVENT; levelling plots is GRADE; getting out of a pit or cave is SURVIVAL. Children never change a block: the guard refuses every edit by a child. Item frames for maps are entities: the guard is asked as if a builder placed a block there, and the hanging is logged by hand.
- **Player death is untouched.** The pets package registers `ServerLivingEntityEvents.ALLOW_DAMAGE`, but it only ever looks at damage dealt by a camp pet, and lets that through only when the victim is a hostile that is neither a player nor a friend. It never touches player death; `ALLOW_DEATH` is still never registered for players.
- **No free items.** Shops only move the shop's own stock and takings at fixed prices, and goods held for a visit go back when the screen closes; a map copy uses the player's empty map or the camp's paper and compass. Every new command works at permission level 0 with cheats off.

### 15.6 What 3.0 changes in the limits above

Section 14 describes the 2.x camp. With 3.0:

- Buildings come from data files (the camp's own plans and the library's 54), chosen by style; friends still do not design buildings of their own.
- Paths are the friends' own (`navigation.FriendNavigation`), not plain mob pathfinding, and a stuck friend works through the navigation package's steps to get out, with a rescue as the last resort (`rescueStuckFriends`; its honest limits apply).
- A friend with a home sleeps in their own bed; without one they sleep as before.
- The camp grows past the Settlement into the Town and the City, and its radius grows with the village, up to `villageRadius`.

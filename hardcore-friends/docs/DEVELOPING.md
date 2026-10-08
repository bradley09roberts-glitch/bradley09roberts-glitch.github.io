# Developing Hardcore Friends

This guide covers building the mod, the APIs the core code provides, and the rules every contribution follows.
Read `docs/DESIGN.md` first for the behaviour contract.

## Build and test

```bash
export JAVA_HOME=/opt/jdk25/jdk-25.0.4.1+1; export PATH=$JAVA_HOME/bin:$PATH   # Java 25 is required
./gradlew --no-daemon compileJava compileClientJava compileGametestJava -q       # fast compile check
./gradlew --no-daemon build                                                       # compiles, then runs ALL server game tests
xvfb-run -a -s "-screen 0 1600x900x24" ./gradlew --no-daemon runClientGameTest    # in-game client tests + screenshots
```

- **Build output:** `build/libs/hardcore-friends-<version>.jar`.
- **Server game test log:** look for `GAME TESTS COMPLETE` and `required tests failed`.
- **Decompiled Minecraft 26.3 sources** (Mojang names): `/tmp/claude-0/-home-user-bradley09roberts-glitch-github-io/f6aee86f-f71d-5dd3-8880-a62ffc4c5fb5/scratchpad/mc-src/net/minecraft/...`
- **Fabric API sources:** `.../scratchpad/fabric-src/net/fabricmc/fabric/...`

Always confirm a method's exact signature with `grep` in these sources. Many signatures changed after 1.21.

## Minecraft 26.3 gotchas (verified)

- **Identifiers:** `Identifier`, not `ResourceLocation`. Build them with `Identifier.fromNamespaceAndPath(ns, path)` or `Identifier.withDefaultNamespace(path)`.
- **Entity saving** uses `ValueOutput` / `ValueInput`, not NBT compounds.
- **Arm swing:** `LivingEntity.swing(hand)` no longer exists. For friends, call `companion.swingArm()`.
- **Dirt tags:** `BlockTags.DIRT` is only dirt, coarse dirt and rooted dirt. Grass, podzol and mycelium are in `BlockTags.GRASS_BLOCKS`. Use `BlockTags.SUPPORTS_VEGETATION` for "plantable soil".
- **Time:** use `Camp.timeOfDay(level)` (0–23999), `Camp.isNight(level)` (`level.isDarkOutside()`), `Camp.isDusk(level)` and `level.getOverworldClockTime()`.
- **Moved packages:** monsters now live in sub-packages:
  - `world.entity.monster.zombie.Zombie`, `...skeleton.AbstractSkeleton`, `...spider.Spider`, `...illager.*`
  - `world.entity.monster.Creeper` and `world.entity.monster.Enemy` stay where they were
  - villagers are in `world.entity.npc.villager`
- **Interaction results:** `InteractionResult.SUCCESS`, `SUCCESS_SERVER`, `CONSUME`, `PASS`, `FAIL`.
- **Breaking blocks:**
  - `Block.getDrops(state, serverLevel, pos, blockEntityOrNull, entity, toolStack)`
  - `level.destroyBlock(pos, dropItems, entity)`
- **Tool wear:** `stack.hurtAndBreak(int, LivingEntity, EquipmentSlot)`. For friends, use `companion.damageMainHandTool(n)`.
- **Chests:**
  - `ChestBlock.getContainer(chestBlock, state, level, pos, true)` handles double chests.
  - `HopperBlockEntity.addItem(null, container, stack, null)` inserts and returns the remainder.
- **Item tags:** `ItemTags.PICKAXES`, `AXES`, `SHOVELS`, `HOES`, `SWORDS`, `PLANKS`, `LOGS`, `SAPLINGS`, `COALS`, `WOODEN_SLABS`, `WOODEN_DOORS`, `WOODEN_FENCES`, `FENCE_GATES`, `WOODEN_PRESSURE_PLATES`, `STONE_TOOL_MATERIALS`.
- **Data pack folders:** tag folders are singular (`data/<ns>/tags/item/`, `data/<ns>/tags/block/`).
- **New wood type:** `poplar` exists (`POPLAR_LOGS`).

## Core API

These are the pieces your routines should use. Look at the sources for details.

- **`companion.CompanionEntity`**
  - Identity and state: `friendId()`, `mode()`, `backpack()`, `actions()`, `scheduler()`, `homePos()` (camp centre, or recruit spot without a camp), `restPos()` (inside the cabin once built, else the camp centre), `isFighter()`, `isArmed()`, `isRetreating()`
  - Fighting: `canStandAndFight(threat)`, `isHealthy()`, `meleeReach(threat)` (8 blocks, 24 for an armed friend or the watcher inside the camp, 3 against archers), `bestWeaponRank()`, `hasGivenUpOn(threat)`
  - Needs: `needs()`, `isAsleep()` / `setAsleep(b)` (set by the sleep job; energy does not drain while asleep), `settleSleep()` (energy for the in-game time asleep, including a night the players slept through; called every tick), `badlyHurt()` (at or below the retreat fraction), `tooWeakToWork()` (badly hurt and too hungry to heal), `workSpeed(reason)` (skill × mood × Unity rhythm)
  - Items and food: `equipBestWeapon()`, `hasFood()`, `isEdible(stack)`, `eat(stack)` (eats an item already taken out of a backpack or chest: heals, fills hunger by `hungerValue(stack)`, keeps the bowl), `eatFromBackpack()` (only when hurt), `damageMainHandTool(n)`, `swingArm()`
  - Trees: `approveLogs(Collection<BlockPos>)` (pre-approve a checked tree before felling), `isApprovedLog(pos)`
  - Other: `nearestProtector(r)`, `statusLine()`, `openBackpack(player)`, `getRandom()`
- **`companion.Needs`:** `get/set/add(Need, value)` (0–100, 100 = met), `lowest()`, `moodValue()`, `mood()` (`MISERABLE` to `GREAT`, `word()`), `workSpeed()`, `canHeal()` (hunger above `TOO_HUNGRY_TO_HEAL`; every kind of healing checks it), `rest(c, ticks)`, `bar(value)`, `summary()`; statics `hungerDrain(working)`, `typicalDailyHunger()`, `hasCompany(c)`, `comfortOfSurroundings(c)`, `nearLitCampfire(level, pos, r)`.
- **`companion.MoodPassives`:** `voice(c)` (says how the friend feels, if anything), `word(need)` ("hunger"), `jobFor(need)` (the needs job's id), `currentJob(c)`, `seeingTo(c, need)`, `moodText(c)`, `teamMood(friends)`, `teamMoodValue(friends)`, `highSpirits(friends)`.
- **`companion.Speciality`:** `interest(id)`, `affinity(id, role)`, `skill(id, role)`, `roleFor(reason)`, `workName(role)`.
- **`ai.action.Actions`** (`companion.actions()`), all per-tick:
  - `walkTo(pos, reach)` returns true once within reach; check `isStuck()` to give up.
  - `canReach(pos)` is eye-distance ≤ 4.5.
  - `mine(pos, Reason)` returns `RUNNING`, `DONE` or `FAILED`. It uses real mining time, the best carried tool, wears the tool, and puts drops in the backpack.
  - `place(pos, state, itemPredicate, Reason)` takes one item from the backpack.
  - `transform(pos, newState, Reason, toolTagOrNull)` handles tilling, paths and berry picking, and wears the tool.
  - Equipment helpers: `equip(pred)`, `equipBestFor(state)`, `has(pred)`, `hasTool(tag)`, `reset()`.
- **`companion.Backpack`:** `count(pred|Item|Tag)`, `has`, `find`, `insert` (returns the remainder), `canFit`, `remove(pred, n)`, `take(pred, n)`, `stacks()`, `fullness()`, `freeSlots()`, `capacity()`.
- **`camp.SupplyChest`:**
  - `of(level)` returns the linked chest `Optional<Container>`; `at(level, pos)`
  - `count(container, pred)`, `withdraw(container, backpack, pred, max)`, `deposit(backpack, container, pred, max)`, `insert(container, stack)`
- **`camp.Camp`:**
  - `data(server)` returns the `CampData`
  - `center(level)`, `radius(data)`, `isCampLevel`, `day(level)`, `timeOfDay`, `isNight`, `isDusk`
  - `horizontalDistSqr`, `STAGE_NAMES`, `STAGE_UNITY`
- **`camp.CampData`** (persistent):
  - Camp layout: `campPos()`, `chestPos()`, `setChestPos`, `stage()`
  - Structures: `isCompleted(id)`, `markCompleted`, `site(id)`, `putSite`, `removeSite`
  - Placed blocks: `recordPlaced(pos)` / `isPlacedByFriends(pos)` (the guard calls these automatically)
  - Points of interest: `addPoi(type, pos, gameTime)`, `pois()`, `removePoi`
  - Per-routine persistent state: `memory(key)` returns a `CompoundTag`. Call `setDirty()` after changing it.
  - Stats and logs: `addStat(key, n)`, `ledger(id)`, `logEdit`
- **`camp.Structures`:** the settlement plan, with ids such as `Structures.CABIN` and `FARM_PLOT`. Use `forStage`, `nextFor(data, role)` and `missing`.
- **`camp.CampProgress`:**
  - `complete(maker, structureId)` marks an improvement done, announces it and adds Unity. Call it when your role finishes its improvement.
  - Stages advance automatically.
- **`camp.CampNeeds`:**
  - Reading needs: `need(Need)` (0–1), `weight(Need)` (score multiplier, including Sage's planning bonus), `focus()`, `stock(Need)`, `summary()`, `available(c, pred)`
  - Reporting shortages: `reportBuildShortage(level, map, text)`
  - Need types: `FOOD, WOOD, STONE, DIRT, TORCHES, ORE, SEEDS, BUILD`
- **`camp.Crafting`:**
  - `ensurePlanks(bp, n)`, `ensureSticks(bp, n)`, `ensureTorches(bp, n)`
  - `ensure(c, Item, count)` covers sticks, torches, crafting table, chest, furnace, campfire, ladder, glass pane, nuggets, lantern, hopper, redstone torch, daylight detector, redstone lamp, bread and cobblestone slab
  - `craftWood(c, WoodShape, n)`, `craftTool(c, toolTag)`, `affordableTier`, `planksFor(logStack)`, `nearCraftingTable(c)`, `findNearby(c, r)`
  - 3×3 recipes need a crafting table within 6 blocks.
- **`world.WorldEditGuard`:**
  - Every block change goes through it; `Actions` already does this.
  - Reasons: `FARM, BUILD, INVENT, LANDSCAPE, MINE, GATHER_WOOD, GATHER_EARTH`.
  - Checks: `canBreak/canPlace/canTransform(c, pos, ...)` return a `Verdict`.
  - Zones: `inCamp(c, pos)`, `inResourceZone(c, pos)`.
  - Helpers: `looksPlayerBuilt(level, pos, r, data)`, `isClearablePlant(state)`, `touchesFluid`.
- **`world.TreeFinder`:** `analyse(level, logPos)` returns an `Optional<Tree>` (`base`, sorted `logs`, `height`); also `nearest(level, centre, radius ≤ 24, maxHeight, allowedPredicate)` and `isNaturalTreeLog`.
- **`ai.goal.Threats`:** `isThreat`, `nearest(entity, r)`, `around(entity, r)`, `nearestCreeper`, `isTargeting`.
- **`companion.Speech`, `Line` and `Lines`:**
  - `Speech.say(c, Line.X, args...)` handles per-line cooldowns and chattiness. `Line` javadoc documents each line's arguments, and `Line.args()` says how many there are.
  - `Speech.tell(player, id, text)` and `Speech.announce(server, component)` send direct messages.
  - A new `Line` needs an argument count, generic wording in `Lines.generic()`, and two or more variants in each friend's own voice. `Lines.problems()` checks the whole table (own wording for every friend, length, placeholders, formatting); `MoodGameTest` fails if it finds anything.
- **`unity.Unity`:**
  - `add(level, category, amount, dailyCap)` with categories `DELIVERY, GIFT, HANDOFF, DEFENCE, BUILD, TIME, CHAT, SPIRIT`
  - `chat(level)` (+1 for two friends chatting, cap 30/day), `teamSpirit(server)` (hourly, +1 while the team is in high spirits, cap `SPIRIT_DAILY_CAP`)
  - `level(server)`, `workSpeed(c)`, `carefulHands(c)`, `scoutMarksThreats(server)`, `backpackSlots(server)`
- **`companion.Companions`:** `all()`, `in(level)`, `find(FriendId)`, `near(level, box)`.

### Tasks

Implement `ai.task.CompanionTask` (see its javadoc). Instances are per companion, so fields are fine.

Score bands:

| Score | Use for |
|---|---|
| 140 and up | Desperate needs only (`TaskScheduler.URGENT`): they take over from any job at once |
| 70–89 | Urgent upkeep |
| 40–69 | Main role work |
| 20–39 | Secondary work |
| 1–19 | Idle |

Multiply by `CampNeeds.weight(...)` when the task serves a need (up to 2.2: a base of 60 reaches 132, which must stay below `TaskScheduler.URGENT`). `score()` runs at most once a second, so keep scans bounded (≤ 24 blocks around the friend, or cache results and rescan every few seconds).

`tick()` returns `RUNNING`, `SUCCESS` or `FAILURE`. `stop()` must clean up; `actions().reset()` is called for you.

A task should do one useful unit of work and then return `SUCCESS`, for example "harvest up to 8 ripe crops" or "place 16 blueprint blocks". The scheduler then re-scores everything, so friends respond to new needs.

At night the scheduler offers only needs jobs and `TaskScheduler.NIGHT_JOBS` (coming home, the watch, lighting a dark camp, feeding, fetching a lost weapon or food, idling), and puts any other job down at nightfall: friends sleep. Add a job to `NIGHT_JOBS` only if it truly belongs to the night. The common "return home" task brings friends back, and `camp.NightWatch` says who is on watch (`isOnWatch`).

### Adding a speciality job

Every friend gets every role's jobs (`ai/task/TaskRegistry`), each wrapped in a `SpecialityTask` that scales the score for anyone who is not the specialist (see DESIGN.md, section 5). To add a job to a role:

1. Write the task as usual and add it to that role's list in `ai/role/<Name>Tasks.create()`. Score it as if the specialist were running it; `SpecialityTask` does the rest.
2. Do not assume the specialist is the one running it. Find "the builder" or "the farmer" by what they are doing, not by name; read shared progress from `CampData.memory`, not from fields on one friend; and leave complaints such as `NEED_TOOL` to the specialist, so stand-ins do not nag.
3. If it works on one shared thing (a building site, the mine, a tree being felled), add its id to `SpecialityTask.EXCLUSIVE` so only one friend runs it at a time and the specialist can take it back.
4. If it deals with what this friend carries (a delivery, their own ore), add its id to `SpecialityTask.PERSONAL`.
5. If only the specialist should ever do it (a duty that defines them), add its id to `TaskRegistry.SPECIALIST_ONLY`.
6. Edit the world with the `WorldEditGuard.Reason` that matches the kind of work, so the friend's skill (`Speciality.skill`) applies to their speed.
7. Share expensive scans between friends through `ai/role/TeamCache` so nine friends scoring the job cost no more than one.

### Adding a needs job

Needs jobs live in `ai/task/needs` and are listed in `NeedsTasks.create(id)`; every friend has them all.

1. Give it an id starting with `needs.` (that counts as time off, so hunger and fun do not drain faster while it runs) and add it to `MoodPassives.jobFor` if it is the job for one of the five needs.
2. Score it from the need: 0 while the need is fine (and 0 when it cannot be met, such as eating with no food anywhere, so it never breaks off work for nothing), rising as it falls, into the urgent band (70–89) when it is pressing, and to `TaskScheduler.URGENT` or more only when it is desperate, so a mild need waits for the job in hand to end. Work never wakes a sleeper, a friend too weak to work only takes needs jobs and `TaskScheduler.FIT_WHEN_WEAK`, and at night only needs jobs and `TaskScheduler.NIGHT_JOBS` run, so a needs job that should happen at night must outscore bedtime (75 to 82, `SleepTask`) or cap it, as supper and warming up do.
3. Change the need with `c.needs().add(Need.X, amount)`, and eat with `c.eat(stack)` so healing, hunger and bowls stay consistent. Sleep jobs call `c.setAsleep(true)` and must set it back to false in `stop()`.
4. Say what is happening with the needs lines (`HUNGRY`, `ATE`, `SLEEPY`, `CHAT`, `LEISURE`, `COSY` and so on). Every friend already has their own wording.
5. Pastimes and needs jobs do not change blocks. If one ever must, it goes through `WorldEditGuard` like any other edit.
6. Add a game test that sets the need low with `c.needs().set(...)` and checks the friend sees to it.

## Rules

1. Never change player death, respawn, game mode, difficulty or the hardcore flag. No free items. No teleporting players.
2. All block changes go through `WorldEditGuard` (via `Actions`). Never call `level.setBlock` or `destroyBlock` directly in routines. The only exceptions are blocks the guard already approved inside the guard itself, and test set-up code.
3. Real materials: placing consumes an item from the backpack, crafting consumes ingredients, tools wear.
4. Server-side only, and keep it cheap: no unbounded scans, and no scanning every tick.
5. Code style: tabs, javadoc on public types, British English in player-facing text, no `System.out` in mod code.
6. Only edit the files your work package owns. If you need a change in a core file, describe it in your final report instead of making it. Small additive helpers go in new files inside your own package.

## Server game tests

- **Where they go:** write tests in your work package's test class in `src/gametest/java/.../test/`. It is already registered in `src/gametest/resources/fabric.mod.json`.
- **Annotation:** use `@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_NN", maxTicks = ...)`. Each method uses its own `solo_NN` from your assigned range, so camp-dependent tests run one at a time.
- **Setup:** start every test with `TestSupport.resetCamp(helper, true)`, then use `TestSupport.spawnFriend(helper, FriendId.X, relPos)`, `TestSupport.placeChest(helper, relPos, stacks...)`, `TestSupport.give(c, stacks...)`, `TestSupport.growOak(helper, relBase)` and `TestSupport.setTime(helper, dayTime)`.
- **Plot layout:** the plot is 32×12×32. Grass is at relative y = 1, so friends stand at y = 2. The camp centre is relative (16, 2, 16). The camp radius is 24, so the whole plot is "in camp".
- **Gathering zone:** tests needing the outside-camp gathering ring should set a camp far away with `Camp.data(server).setCamp(absPos, dim)`, positioned so the plot lies in the ring. For example, set the camp centre 30 blocks west of the plot centre: radius 24, ring to 72.
- **Assertions:** use `helper.succeedWhen(() -> helper.assertTrue(cond, "message"))` for "eventually" checks and set `maxTicks` generously (for example 1200 = 1 minute of game time). Assertion messages are plain `String`s.
- **Determinism:** keep tests deterministic. Place everything the friend needs and do not rely on random mob spawns. Needs drift every second, so a test that depends on a need sets it first (`c.needs().set(...)`).
- **Commands:** run a command as a non-operator with `helper.makeMockServerPlayer(GameType.SURVIVAL)`, then `player.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL)` and `server.getCommands().performPrefixedCommand(source, "friends ...")`. To read the replies, pass `withSource(...)` a `CommandSource` that collects the messages (see `MoodGameTest`).

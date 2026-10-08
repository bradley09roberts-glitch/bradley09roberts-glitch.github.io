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
  - Identity and state: `friendId()`, `mode()`, `backpack()`, `actions()`, `scheduler()`, `homePos()` (camp centre, or recruit spot without a camp), `isFighter()`, `isArmed()`, `isRetreating()`
  - Items and food: `equipBestWeapon()`, `hasFood()`, `eatFromBackpack()`, `damageMainHandTool(n)`, `swingArm()`
  - Trees: `approveLogs(Collection<BlockPos>)` (pre-approve a checked tree before felling), `isApprovedLog(pos)`
  - Other: `nearestProtector(r)`, `statusLine()`, `openBackpack(player)`, `getRandom()`
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
- **`companion.Speech` and `Line`:**
  - `Speech.say(c, Line.X, args...)` handles per-line cooldowns and chattiness. `Line` javadoc documents each line's arguments.
  - `Speech.tell(player, id, text)` and `Speech.announce(server, component)` send direct messages.
- **`unity.Unity`:**
  - `add(level, category, amount, dailyCap)` with categories `DELIVERY, GIFT, HANDOFF, DEFENCE, BUILD, TIME`
  - `level(server)`, `workSpeed(c)`, `carefulHands(c)`, `scoutMarksThreats(server)`, `backpackSlots(server)`
- **`companion.Companions`:** `all()`, `in(level)`, `find(FriendId)`, `near(level, box)`.

### Tasks

Implement `ai.task.CompanionTask` (see its javadoc). Instances are per companion, so fields are fine.

Score bands:

| Score | Use for |
|---|---|
| 70–89 | Urgent upkeep |
| 40–69 | Main role work |
| 20–39 | Secondary work |
| 1–19 | Idle |

Multiply by `CampNeeds.weight(...)` when the task serves a need. `score()` runs at most once a second, so keep scans bounded (≤ 24 blocks around the friend, or cache results and rescan every few seconds).

`tick()` returns `RUNNING`, `SUCCESS` or `FAILURE`. `stop()` must clean up; `actions().reset()` is called for you.

A task should do one useful unit of work and then return `SUCCESS`, for example "harvest up to 8 ripe crops" or "place 16 blueprint blocks". The scheduler then re-scores everything, so friends respond to new needs.

At night most work routines should score 0 when outside the camp. The common "return home" task brings friends back.

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
- **Determinism:** keep tests deterministic. Place everything the friend needs and do not rely on random mob spawns.

# Kingdoms of the Long Night: Roadmap

This file orders the next implementation milestones, starting from v0.1 as it exists in `mod/` (sessions of 12 to 13 Oct 2026). The system designs are in `docs/DESIGN.md`; section numbers like "§2.4" refer to it. Lore canon is `docs/WORLD_BIBLE.md`.

**Ordering principle.** Make the loop the player already has deeper, fairer and more robust before adding breadth. That loop is: arrive, talk, trade, take contracts, found a settlement, watch it grow, survive raids. Concretely:
1. Data integrity and fairness first, because every later system writes to the same save.
2. Then the interfaces and the decision-making that every later system will use.
3. Then visible depth in settlements.
4. Then new threats and arcs.

**Sizes** are rough, for one experienced engineer: **S** about 1 week, **M** 2 to 3 weeks, **L** 4 to 6 weeks.

---

## Known gaps in v0.1

These are listed without softening, grouped by severity.

### A. Correctness bugs and likely bugs

1. **[FIXED in v0.1.0 final]** **Player realms appear to get an NPC ruler.** `game/Founding` creates a LORDSHIP faction with `playerRealm = true` and `ruler = null`. `Politics.daily` runs `succession()` for any realm whose ruler is null and does not skip player realms. So the most prestigious settler becomes "Lord of Lordship of X" with `profession = RULER`, and the realm's agenda (including CONQUER, which declares wars) follows that NPC's temperament. `diplomacy()` and `wars()` also treat the player realm as an ordinary AI realm. Only `rebellions()` excludes it.
2. **[FIXED in v0.1.0 final: `Building.decayed` is saved]** **Ruin chests re-roll on every restart.** `Placer.DECAYED` and `PREPARED` are in-memory sets. After a restart, `catchUp` decays every RUINED building again (more holes) and `Loot.ruinChest` re-places the chest with fresh loot. This is a repeatable loot exploit.
3. **[FIXED in v0.1.0 final: warrants live in `WorldSim.wanted`, saved with the world and expired on use]** **Wanted state is not persisted.** `Crime.WANTED` is a static map cleared on stop, so a murderer is forgiven by a restart.
4. **[MITIGATED in v0.1.0 final: a raid in progress at shutdown ends as a withdrawal and is not refought]** **Embodied raids are lost on restart.** `RaidManager.RAIDS` is in memory. A raid in progress during a stop vanishes without an outcome. Because `raidSource`/`raidDay` persist, the next day's `Threats.daily` re-schedules the same raid and it is fought again from the start. The interrupted raid's plunder and deaths still stand, so the village is effectively hit twice.
5. **[FIXED in v0.1.0 final: quarry kilns fire glass when there is fuel; lumber camps yield charcoal; beds no longer need wool; lanterns cost fuel, not iron]** **Glass deadlock.** Most culture palettes and every temple use glass panes. Nothing produces `GLASS`: it comes only from the initial stock of 10 units, trade between settlements that also cannot make it, or players. Construction of any building with windows stalls ("Waiting for glass.") once that stock is spent.
6. **Dead-end commodities.** `CLOTH` is produced by weavers but consumed by nothing. Flour, ale and cloth cannot be bought by players (`TradeGoods.itemFor` returns null).
7. **[FIXED in v0.1.0 final: abandoned settlements' buildings become decaying ruins]** **Abandoned settlements look inhabited.** `Growth.abandon` sets `kind = RUIN` but leaves completed buildings COMPLETE, so they never decay. Only buildings created as RUINED decay.
8. **[FIXED in v0.1.0 final for goods: each person's body-worked cycles are counted and the daily update works only the rest, so watching neither adds nor loses output; construction still uses the settlement-wide share]** **Production share is settlement-wide.** `embodiedFraction` counts work hours per settlement whenever a player is near, not per person. A body that never reaches its workplace (stuck, talking, blocked by a player's wall) produces nothing, and its abstract share is also removed. Watching a settlement can lower its output.
9. **Calendar does not match the bible.**
   - [FIXED] `Calendar.year` now opens in 2317 AB.
   - With the default 1 day per month (D8), the bible's Fairday and Moot-day cannot occur.
   - [PARTLY FIXED: festival names and months now follow the bible, e.g. the Lantern Vigil] Faith JSON clergy titles still differ from the bible (e.g. hearth "Hearth-Warden" vs Flamekeepers).
10. **Long Night numbering differs from the bible.**
    - Code stage 5 is "The Long Night has come", where the bible's stage 4 is the Long Night and stage 5 is Resolution.
    - The bible's triggers (first Seeker's Eye, End portal lit, Hush, 20 days without Rekindling) are not implemented.
    - After the dragon dies, stage 3 starts the next day, stage 4 about 20 days later and stage 5 about 12 days after that. There is no way back.
11. **[FIXED in v0.1.0 final: `doTraderSpawning` is turned off when a world is created]** **The vanilla wandering trader still spawns.** It is a second society, against D2.

### B. Simplifications presented honestly

- **Physical work is theatre over recipes.** Felled trees and harvested crops are destroyed without drops, and output comes from the recipe. Extractive trades produce from nothing but labour: a miner yields ore anywhere, a lumberjack has infinite logs abstractly.
- **Labourers "haul" without carrying anything.** In-settlement transport is instantaneous.
- **Caravans have no bodies, no roads and no cargo in the world.** They are records travelling in straight lines at 700 blocks/day. Ambush is a 12% daily roll near outlaw camps.
- **Wars have no armies.** Battles are a strength comparison with noise every other day. No war is ever fought where a player can see it; only outlaw raids are embodied.
- **Coin is minted daily.** Settlement "dues" create coin from population and morale, so money supply grows without bound.
- **NPC decisions are a fixed schedule plus hand-ordered rules.**
  - `CitizenBrain.decide` picks by time of day.
  - `Planner.next` is a 35-line priority list.
  - `Jobs.assign` is slot-filling.
  - "Blocked" is a free-text string.
  - There is no multi-step planning, no reservation of materials and no explanation of settlement choices.
- **Knowledge is per settlement, not per person.** There are no secrets, private knowledge or rumour distortion.
- **`Building.level` never changes** (the blueprint generator ignores it). `DAMAGED` is a flag with no physical effect. There are no walls or gates. *(Since v0.1.0 final: idle builders replace missing blocks of finished buildings at their block cost, distant settlements mend DAMAGED buildings abstractly for a fifth of the bill, and roads with plank bridges over rivers and shallow water link neighbouring settlements.)*
- **Families are thin.**
  - Births require a married male-female couple, with no adoption.
  - Inheritance moves coins only.
  - No households, divorce, funerals or grave tending.
  - Graves are placed only when the settlement is embodied and has a finished graveyard.
- **No law and no punishment.**
  - Crime is noticed only by bodies within 16 blocks, and only player crimes against citizens count.
  - There are no trials, no NPC crime and no sentences.
  - TRIBUTARY and OVERLORD stances exist but are never used.
  - Realm treasuries are never spent except on reparations.
- **Player governance consists of `/longnight plan` and `/longnight rename`.**

### C. Missing systems

These are designed in DESIGN.md but not started:
- Hush meter, Veiled Choir, undead, curses and the other dark powers.
- Dragons and great beasts (the `dragon_sighting` rumours exist; nothing emits them).
- Festivals, markets, Fairday and Moot-day.
- Magic traditions beyond ward stones, and any Ars Nouveau integration.
- Farmer's Delight mapping. `TradeGoods` hard-codes vanilla items.
- Bible ruin archetypes, battlefields, lost roads, loot injection into YUNG's structures.
- Last Ember tracking and Rekindling.
- Dynasty mode.

Registered items with **no behaviour**: `wedding_token`, `land_deed`, `trade_seal`, `letter`, `ember_relic`, `blighted_wheat`. `night_shard` and `star_chart` exist only as ruin loot.

### D. Presentation

- All UI is chat text with clickable commands (D7). `ledger_background.png` and `logo.png` are unused.
- All player-facing strings are hard-coded English literals, not lang keys.
- No Jade, EMI or Xaero integration.
- Distant Horizons shows unmaterialized settlements as bare terrain until visited.

### E. Performance and scale (unmeasured)

Nothing has been profiled on player hardware. Known hotspots:
- `NewsSystem.daily` is O(S² x K) with a linear `newsById`.
- `Economy.targets` is recomputed per worker, per price and per caravan pair.
- `Embodiment.buryDead` scans every person ever born.
- `Wards.blocks` and `settlementAt` are linear scans per spawn or query.
- All daily systems run in one tick, and up to 10 catch-up days run in one tick.
- One `SavedData` blob is rewritten on every autosave.
- The dead are never pruned. Every Person keeps up to 32 relations and 24 memories forever.

### F. Testing

Tests exist:
- 8 unit tests;
- 6 GameTests;
- one RCON server scenario (boot, generate, observe, simulate, raid, validate, restart, compare; 15/15 checks passed in the latest runs in `tools/test/results/`; the first run had failed invariants that were since fixed);
- a headless client screenshot pilot.

Missing:
- determinism (the shared RNG makes golden-master tests impossible);
- long soaks with metrics;
- performance thresholds;
- fairness tests for threats;
- multiplayer tests;
- tests of any client screen.

`modpack/VERIFICATION.md` states the pack was verified **without** the longnight jar. The full pack plus mod has not been through the client test.

---

## Milestone overview

| # | Milestone | Size | Depends on |
|---|---|---|---|
| M1 | Integrity, fairness and data foundations | M | none |
| M2 | Proper GUI screens v1 (+ Jade) | L | M1 |
| M3 | Task planner, reservations and blockers | L | M1 |
| M4 | Economy depth I: data-driven goods, new trades, Farmer's Delight | L | M3 |
| M5 | Roads and settlement layout v1 | M | M1 |
| M6 | Embodied haulers, carts and caravans | L | M3, M4, M5 |
| M7 | Building lifecycle: upgrades, damage and repair, walls and gates | L | M3, M5 |
| M8 | Calendar life: Fairday, Moot-day, festivals, weddings, funerals | M | M2, M4 |
| M9 | Player roles: contracts II, citizenship, service, land, player-lords | M | M2, M8 |
| M10 | Law, crime and trials | M | M8, M9 |
| M11 | Warfare II: armies, warning ladder, sieges, embodied battles | L | M5, M6, M7 |
| M12 | Exploration and lore delivery | M | M5, M7 |
| M13 | Graves, Hush and dark powers I | L | M8, M10 |
| M14 | Magic traditions and Ars Nouveau integration | M | M4, M13 |
| M15 | Dragons and great beasts | L | M7, M11 |
| M16 | Long Night stages 3 to 5, multiplayer polish and dynasty mode | L | M11, M13, M15 |

---

## M1: Integrity, fairness and data foundations (M)

**Goal.** Every later milestone writes to the same save and reads the same rules. Fix the bugs in Known gaps A, and lay the foundations that are cheap now and expensive later (DESIGN §1.2, §17).

**Scope.**
- **Player realm fix.**
  - `Faction.rulerKind {NPC, PLAYER}`. Migration: `playerRealm` maps to PLAYER.
  - `Politics.daily`, `updateAgenda`, `diplomacy` and `wars` skip or handle PLAYER realms. A player realm can be *targeted* only under DESIGN §6.8 fairness rules.
  - Revert any NPC installed as ruler of a player realm.
- **Persistence of transient state.**
  - `Building.decayed` and `Building.prepared` replace `Placer.DECAYED` and `PREPARED`.
  - The wanted map moves into `WorldSim.wanted` (per realm, with expiry).
  - `RaidManager` state moves into `Settlement` (`raidPhase`, raider ids). On restart, an interrupted raid resolves abstractly once.
- **Abandonment decay.** `Growth.abandon` marks all buildings RUINED, and decay runs once per building (persisted).
- **Glass deadlock.** A stop-gap `GLASSMAKER` recipe at the carpenter or smithy (sand from quarry output) until M4 replaces it.
- **Calendar fixes.** Year epoch set to the bible's AB. `Calendar.daysPerMonth`, `isFairday`, `isMootday` and day phases (DESIGN §15).
- **Long Night stage renumbering** to the bible's 0 to 5, with a save migrator mapping old stage values.
- **Save format.**
  - `WorldSim.FORMAT = 2` with a `migrate` package. Fixture test: v0.1 save to v2.
  - Shard SavedData into people, settlements, realms, news and archive, each with its own dirty flag.
- **Determinism.** A per-system, per-day `SplittableRandom` replaces the shared `rng` seeding.
- **Hotspots** (DESIGN §17.2):
  - news id map plus neighbour lists;
  - targets cache per settlement per day;
  - per-settlement unburied queue;
  - `SpatialIndex` for settlements, wards and claims.
- **Time-sliced daily work queue** (3 ms/tick budget). `admin skip` drains synchronously.
- **Archive of the dead** (DESIGN §17.4): `ArchiveRecord` after funeral plus 2 years. Prune relations to the archived.
- **Wandering trader.** Disable it via the pack default gamerule, plus a spawn cancel in `ServerEvents` as a guard.
- **Strings.** Move player-facing strings into `lang/en_us.json` as part of each touched file. Not all at once; start with Ui and Dialogue.

**Acceptance tests.**
- Unit:
  - founded realm keeps a PLAYER ruler and never gets an NPC ruler or a CONQUER agenda over 200 simulated days;
  - v0.1 fixture save loads and validates;
  - same seed plus same inputs give the same world hash after 60 days (golden master);
  - archive round-trip.
- GameTest:
  - ruin decays, then simulated reload, then no second decay and the chest contents are unchanged;
  - murder, then reload, then guards still hostile.
- Server scenario:
  - restart during a scheduled raid causes exactly one raid resolution;
  - the daily rollover never exceeds a 10 ms tick in the 30-day run (log the max tick time).
- Benchmark (new, headless): 60 settlements and 3,000 people over 100 days, with a per-system time report. Record the baseline numbers in NOTES.md.

**Risks.**
- Sharding and migration bugs can corrupt saves. Mitigate with fixture tests and a backup-before-migrate copy of the old file.
- The determinism refactor touches every system's random calls, so regressions are hard to spot without the golden master. Write that test first.

---

## M2: Proper GUI screens v1, plus Jade (L)

**Goal.** Replace chat walls with screens players actually use, keeping chat as fallback (D7 remains valid for servers and tests).

**Scope.**
- **Networking.** `net/` payloads via `RegisterPayloadHandlersEvent`:
  - S2C: `SettlementView`, `MarketView`, `PersonView`, `NoticesView`, `JournalView`.
  - C2S: `BuyRequest`, `SellRequest`, `AcceptContract`, `TalkTopic`, `JoinRequest`, `PlanRequest`.
  - The server validates distance, permission, rate and the current state version, and rejects stale requests with a reason.
- **Screens** (`client/screen/`):
  - Ledger book: Overview, Stores (with buy and sell slots), Projects (progress and current `problem` text, upgraded to blockers in M3), Contracts, Chronicle;
  - Person sheet: entity portrait, family, skills, mood, memories, temperament words;
  - Notice board; Dialogue (topic buttons); Journal (known places list plus compass).
  - Use the existing `ledger_background.png`.
- **Information hygiene.** View builders only include data the player plausibly knows: visited or heard-of places, the settlement they stand in, the person they talk to.
- **Jade plugin** (soft dependency, `compat/jade/`): citizen name, profession, activity and blocked line; building name and progress on blueprint blocks.
- Keep every chat command working. Screens call the same server logic as the commands.

**Acceptance tests.**
- Unit: view builders never include unknown settlements or other players' data.
- GameTest: a C2S `BuyRequest` from too far away, or with stale prices, is rejected without changing stock or coin.
- Headless client test (extend `tools/pack/client_test.py` / `AutoShot`): open each screen in a materialized settlement, screenshot it, and fail on missing textures or exceptions in the log.
- Manual checklist in NOTES.md for each screen on the performance and rich presets.

**Risks.**
- GUI work is time-consuming and easy to over-polish. Ship functional screens with the existing texture.
- Jade API changes between versions. Pin to the pack's Jade 15.10.6 and guard with `ModList.isLoaded`.

---

## M3: Task planner, reservations and blockers (L)

**Goal.** Make NPCs practically competent and explain themselves (DESIGN §2.4). This is the decision core that M4 to M16 extend.

**Scope.**
- `sim/plan/`: `Goal`, `Method`, `Action`, `Planner` (depth 6 or less, 64 or fewer expansions, version-stamp caching), `SimView`, `Blocker` (kinds and remedies from data), `Reservation` (on `Stockpile`: `reserved`, `available()`).
- **Domains:**
  - `CompleteBuilding` (site, plan, access, materials, place);
  - `MakeGoods(recipe)` with input sub-goals (smith: metal and fuel);
  - `KeepFed` (famine draft, already in v0.1, moves here);
  - `ProduceForTarget`.
  - `MusterArmy` is stubbed for M11.
- **Remedy ladder:** produce, substitute (`Construction.hew`), order trade (caravan purchase), post contract (wire into `Contracts`), wait, cancel with refund.
- **Settlement planner.** `Planner.next` becomes a utility scorer with reasons (`data/longnight/buildings/*.json` need functions). Reasons are shown in the M2 Projects tab.
- **Per-person execution records** replace the settlement-wide `embodiedFraction`, fixing gap A8. Partial cycles are credited on discard.
- **Embodied executors** (`entity/tasks/`): GoTo (uses the existing `goTo`), WorkAt, PlaceStep, Harvest, Fell. `CitizenBrain.decide` delegates WORK to the person's current plan step.
- `Person.blocked` becomes `List<Blocker>`, rendered by `Dialogue` and the M2 screens. The string version is kept for chat fallback.
- **Path-request token bucket** (8 per tick). Reachability cache per settlement.

**Acceptance tests.**
- Unit:
  - every goal, evaluated with an empty `SimView`, yields at least one blocker (lint);
  - reservations are released on death or move;
  - `reserved <= stock` invariant;
  - smith with ore and no fuel plans charcoal then forge;
  - smith with nothing yields an `OrderTrade` and then a contract.
- GameTest:
  - builder missing stone replans at most N times per day and posts a contract;
  - two builders never consume the same reserved step.
- Parity: the same settlement and seed for one day, abstract vs observed, with production per commodity within ±25%.
- Server scenario: a 30-day observed settlement has no unexplained idle adults (every idle adult has a blocker or a NO_DEMAND reason).

**Risks.**
- Over-engineering. Keep the planner hand-written Java with data parameters, not a general GOAP search.
- Performance regressions in T3. Measure with the M1 benchmark plus the TPS probe.

---

## M4: Economy depth I, data-driven goods, new trades and Farmer's Delight (L)

**Goal.** Close the production chains, give the requested professions real jobs, and integrate Farmer's Delight (DESIGN §3).

**Scope.**
- **Data.** `data/longnight/commodities/*.json` and `recipes/<profession>/*.json`. `Commodity` becomes a registry, with the enum kept as core ids during migration. `TradeGoods` is built from data with tags first.
- **New commodities:** sand, clay, bricks, cut_stone, coal, copper, gold, hides, tallow, salt, honey, wax, candles, meals, preserved_food, mead, herbs, medicine, clothes, pottery, furniture, saplings, feed.
- **New professions and workplaces** (blueprints in `BlueprintGen`):
  - forester (lodge), beekeeper and chandler (apiary), cook (kitchen at the tavern), butcher (butchery), mason (masonry), glassmaker (glassworks, replacing the M1 stop-gap), potter (pottery), leatherworker (tannery), tailor (weavery);
  - toolsmith, weaponsmith and armourer split from blacksmith (armoury);
  - stablehand (stable); librarian and bard as service roles.
  - Couriers, sailors and caravan guards are deferred to M6.
- **Physical coupling** (DESIGN §3.4): farm `pendingHarvest`, settlement `woodland` and `game` stocks, mine and quarry geology sampled at survey.
- **Storage classes and spoilage per commodity.** Pottery reduces spoilage.
- **Households, first cut:** comfort basket consumption (clothes, candles, ale, pottery). Unmet comforts lower mood.
- **Money.** The dues mint gets a declining multiplier. A coin-per-capita metric is added to the benchmark. Full mint-only money comes with M9 and M10 taxes.
- **Farmer's Delight.** The pack adds `longnight:commodity/*` item tags for FD crops and meals; verify the actual FD 1.3.4 ids against the jar. FD cooking pot and stove are tagged cook workstations. Culture crop lists gain FD crops where appropriate. No compile dependency.
- **Lint** at datapack load: every consumed commodity has a producer, a trade source or an `import_only` flag. The build fails otherwise.

**Acceptance tests.**
- Unit: chain lint passes; no orphan outputs (cloth is consumed); coin per capita over a 200-day headless run stays within ±30%.
- GameTest:
  - FD cabbage (if loaded) deposits as vegetables;
  - a glassmaker supplies a stalled building;
  - farm output tracks real harvested crops when embodied.
- Server scenario: a 200-day soak CSV (population, prices, stock, coin) with no settlement over 20 people starving without an attributed cause.

**Risks.**
- Balance. Many new numbers. Keep them in data, add the soak CSV and a balancing notebook (`tools/test/analyse_soak.py`).
- FD tag drift across FD versions. Pin and verify.

---

## M5: Roads and settlement layout v1 (M)

**Goal.** Settlements look planned and connected. Roads give caravans, armies and travellers a physical shape (DESIGN §4.3, §4.4).

**Scope.**
- **Road planner.** `world/RoadPlanner`: A* on an 8-block grid over noise heights, with costs for slope, water and biome, and claims as obstacles. `WorldSim.roads` (polylines and segments).
- **Road building.** Road segments are buildings of kind ROAD with parametric blueprints, built by crews (paid stone or gravel). Bridges of 12 blocks or less use the Macaw's Bridges palette with vanilla fallback. Milestones every 64 blocks on realm roads. Junction signposts use Supplementaries if present.
- **Layout skeleton.** Centre feature, main streets toward neighbours, ring lane, zoned plots facing streets, still allocated in 13-block cells. Layout data for valemen, dunmar and tidefolk first; the other cultures keep the v0.1 ring until M7 or later.
- **Palette fallback lists** (`"roof": ["mcwroofs:...", "minecraft:..."]`) and `longnight:cost/*` block tags for modded blocks in `Materials.cost`.
- **Usage.** Caravan travel time uses road length. `CitizenBrain.goTo` uses road waypoints. The journal lists roads to known places.

**Acceptance tests.**
- Unit: the road planner never crosses a claim, and reports a `CLAIMED` gap when blocked; planning cost is under 2 ms per route on the benchmark map.
- GameTest: road segment placement never replaces player blocks.
- Server scenario: after 30 days the starting realm's settlements are connected by roads, and screenshots via AutoShot show them.

**Risks.**
- Tectonic terrain has gorges and rivers, so routes may be long or absent. The wilderness fallback is acceptable.
- Visual quality of procedural roads. Iterate with screenshots.

---

## M6: Embodied haulers, carts and caravans (L)

**Goal.** Goods visibly move, and players can meet, escort, trade with or rob trade on the road (DESIGN §3.5).

**Scope.**
- **`InTransit` records.** Labourers carry reserved units (holding a matching item). If the carrier dies, the goods drop as items, recoverable by deposit, and conservation is checked.
- **`CartEntity`** (horse or mule drawn, 64 units) for bulk in-settlement and short-range routes. Spawned only in T3.
- **Caravan v2.** Route along roads, `pointAt(t)` position, guards (new profession: caravan guard), carts and pack animals. Embodied when a player is within range of its evaluated position.
- **Player interactions** with caravans: trade with the leader (local prices from origin), escort contract (reward; ambush odds drop with escort), robbery (crime against the caravan's realm, witnesses are the guards).
- **Ambush v2.** Outlaw camps near road segments. When embodied, the ambush plays out with the M3 executors and the outlaw band's real members (reusing the `RaidManager` pattern).
- **Couriers.** Letters (the `letter` item gains behaviour: delivery contract) and faster news along roads.

**Acceptance tests.**
- Unit: goods conservation including in-transit over a 100-day caravan-heavy run.
- GameTest:
  - kill a hauler, the dropped items are worth exactly the reserved units;
  - a cart crossing a chunk border unloads and resumes without duplication.
- Server scenario: an observer at a road junction sees caravans arrive at roughly the abstract-predicted times (±10%), and escorting one reduces ambush rate (statistical, many seeds).

**Risks.**
- Entity count and pathing cost. Haulers are the first bodies dropped under the cap. Carts use simple road-following steering, not full pathfinding.
- Horses and minecart-like physics are fiddly. Keep carts as a custom steering entity that does not collide with players.

---

## M7: Building lifecycle: upgrades, damage and repair, walls and gates (L)

**Goal.** Settlements visibly grow, get damaged and are rebuilt, and towns build walls (DESIGN §4.5, §4.6).

**Scope.**
- **Blueprint versioning.** `Building.bpVersion` and re-baselining for in-progress buildings. This must land before any `BlueprintGen` change in this milestone.
- **Levels L1 to L3** in `BlueprintGen` for house, large house, workshop types, tavern, storehouse and temple. `UpgradeJob` with delta blueprints and salvage refunds. The planner scores upgrades against new buildings.
- **Damage masks.** `Building.damaged: BitSet`. Raids, battles and fire mark steps. Physical removal on materialize. `Placer.verify` sampling detects player and explosion damage. Repair goals.
- **Walls.** Perimeter planner, `WALL_SEGMENT`/`TOWER`/`GATEHOUSE` parametric blueprints (palisade, stone), gaps at claims, water and steep ground. Gates close at dusk and raids. `Threats.defence` multipliers.
- **Embodied raiders** path to gates and breaches.
- **Culture layout data** for the remaining cultures (eldrin, aszari, burrowfolk, ashborn, skrell).

**Acceptance tests.**
- Unit:
  - re-baseline after a generator version bump charges nothing and places nothing twice;
  - upgrade delta is pay-once;
  - wall planner leaves gaps at a synthetic claim.
- GameTest:
  - damage then repair restores the exact blueprint state and charges exactly the damaged steps;
  - a player block inside a damaged area is left untouched.
- Server scenario: a town reaches stone walls in a long soak (with player deliveries scripted). A raid against a walled town routes raiders through the gate.

**Risks.**
- Plot sizes: L3 must fit the 13-block cell. Audit every type's L3 footprint first.
- Wall placement on Tectonic slopes. Accept gaps rather than ugly terraces.

---

## M8: Calendar life: Fairday, Moot-day, festivals, weddings, funerals (M)

**Goal.** The year has a rhythm the player can feel and plan around (DESIGN §15, §7.4, §7.8).

**Scope.**
- **`CalendarEvent` data and engine.** Phases, filters, effects, choreography, news.
- **Temporary decor registry** (persisted, removal only if the block is unchanged).
- **Fairday market.** Visiting merchant stalls from caravans present, household purchases, hiring fair, narrower spreads, player stall rental.
- **Festivals** from the bible calendar:
  - harvest (Last Sheaf), winter preparation goals, a performance night at taverns (bard);
  - per-faith holy days with faith JSONs aligned to the bible (fixes gap A9 data drift);
  - a canon lint test.
- **Weddings** as events with per-faith rites. Wedding tokens gain behaviour: witness gifting and invitation.
- **Funerals** as events. Per-faith grave templates replace the single gravestone-plus-mark. `GraveRecord` covers deaths while abstract, placed on materialize.
- **Households, second cut.** The `Household` record, hearth-tax unit, kin moving together (v0.1 behaviour formalised), orphan guardian chain.

**Acceptance tests.**
- GameTest:
  - event decor is removed at the end, and a player block placed in the decor area survives;
  - a funeral places the bible template for each of the 7 burial types.
- Unit:
  - Fairday and Moot-day are computed correctly for daysPerYear 8, 16 and 32;
  - every faith has its bible festivals (canon lint);
  - an orphan has a guardian within 1 day.
- Headless client: a screenshot of a market day and a wedding.

**Risks.**
- Choreography with many bodies is costly. Cap the number of event participants embodied.
- Decor cleanup after crashes. The persisted registry handles it.

---

## M9: Player roles: contracts II, citizenship, service, land, player-lords (M)

**Goal.** Give players meaningful, repeatable things to do for, and with, NPC society, and real tools to run their own settlement (DESIGN §5.10, §4.7).

**Scope.**
- **Contracts II** (extend `sim/Contracts`): escort caravan, courier letter, hunt beast, clear road, build-for-us (deliver plus place a structure from a blueprint ghost), investigate (from M10), pilgrimage (from M13). Contract chains and reputation tiers.
- **Players as Persons** (`isPlayer` persona records). Citizenship, titles and reputation stored on the persona.
- **Land deeds.** The `land_deed` item gains behaviour: buy a plot in a town at the town hall. The plot is treated as claimed and the planner routes around it.
- **Claim permissions** (roads and walls allowed) in a boundary stone screen.
- **Service.** Swear fealty or retainership to an NPC lord. Duties as contracts, pay and title rewards (knighthood or the cultural equivalent).
- **Player-lord governance tab** (M2 ledger): appoint steward (an NPC runs the settlement when the player is away), approve or veto plans, set the first laws (hearth-tax, refugees, levy), hear petitions (v1: generated from M3 blockers and grievances), name an heir.
- **Multiplayer roles.** Realm membership with offices (lord, marshal, steward) and permission checks by office.

**Acceptance tests.**
- GameTest:
  - a land deed plot is never built on by the settlement;
  - a non-office player cannot change laws (C2S rejected).
- Unit:
  - a petition from a `MATERIAL_MISSING` blocker appears and resolves when the player grants it;
  - a steward runs the settlement for 30 days without player input with no regression from v0.1 growth.
- Server scenario: two scripted players, one lord and one citizen, with permissions enforced.

**Risks.**
- Scope creep into a full politics game. Keep laws to 3 to 5 in this milestone.
- Persona migration for existing players: create it on next login.

---

## M10: Law, crime and trials (M)

**Goal.** Justice that works for and against players and NPCs (DESIGN §5.6, §5.2).

**Scope.**
- **Persistent `Crime` records** with evidence:
  - witness memories via `PersonalKnowledge`;
  - stolen-goods provenance via a data component on items withdrawn from ledgers or chests in settlements;
  - rumours (never convicting).
- **NPC crime:** theft by the hungry and greedy, brawls between rivals, rare murder after visible escalation.
- **Trials on Moot-day** with culture judges and sentences (fines, stocks block, labour, exile, death for NPCs).
- **Players:** summons, trial screen, plead or pay or present evidence; outlawry and exile. Never a game-executed death sentence.
- **Legitimacy and corruption meters** on realms. Bribery affects verdicts.
- **Wanted state** per realm (persisted since M1) is enforced by guards across all of that realm's settlements.
- **Investigate contracts:** players gather evidence (talk to witnesses, find items) for unsolved crimes. This also resolves "haunted object" hooks later (M13).

**Acceptance tests.**
- Unit: rumour-only cases never convict; evidence weights produce expected verdict rates over many seeds.
- GameTest: a player theft witnessed by a citizen produces a summons; not attending leads to wanted status across the realm.
- Server scenario: over a 100-day soak NPC crime rate correlates with hunger and corruption, as a metric.

**Risks.**
- Feels punitive. Tune witness ranges and show evidence openly.
- Provenance components on items could conflict with stacking. Store provenance only on items taken illegally.

---

## M11: Warfare II: armies, warning ladder, sieges, embodied battles (L)

**Goal.** Wars become visible, logistical and survivable, with fair warning (DESIGN §6).

**Scope.**
- **`Army` records** moving along roads (`pointAt`), mobilisation by levy law, equipment reservations, supplies and attrition, morale.
- **War goals** from data (seize, claim, punitive, tribute, liberation, rebellion). TRIBUTARY and OVERLORD treaties activated.
- **Warning ladder:** rumour, muster news, sighting (scouts and watchtowers), bell. Delivered to citizens anywhere.
- **Fairness invariants** (DESIGN §6.8):
  - no army against player settlements before day 40 and settlement age 20;
  - at least 3 rungs and at least 2 days of warning;
  - early strength cap.
- **Abstract battle engine** (Lanchester-style rounds, terrain and walls, wounds vs deaths). It replaces the dice in `Politics.battle`.
- **Embodied battles.** Formations, 24 bodies per side with waves, reserves resolved abstractly, switching both ways mid-battle, rout and surrender, captives with ransom.
- **Sieges.** Camp decor, starvation, engines and breaches (damage masks from M7), sallies, relief.
- **Consequences:** refugee columns, the veteran trait, occupation garrisons, reconstruction contracts, grudges, BATTLEFIELD sites (handed to M12).

**Acceptance tests.**
- Unit:
  - battle calibration: abstract vs embodied win rates within 10% over 100 seeded runs (embodied in GameTest batch);
  - fairness invariants hold under forced wars.
- GameTest: a 24 v 24 embodied battle completes, and every dead body corresponds to exactly one `kill()`.
- Server scenario:
  - player settlement founded day 1, wars forced, 60 days; every contact preceded by the ladder;
  - TPS above 18 during a 100 v 100 battle near an observer on 4 cores.

**Risks.**
- Biggest AI and performance risk in the roadmap. Prototype embodied formations early (a spike in M3's executors).
- Balance between player power (enchanted gear) and NPC armies. The NPC side is capped by the fairness rules, never by scaling to the player's gear.

---

## M12: Exploration and lore delivery (M)

**Goal.** The world's history becomes findable and layered (DESIGN §14).

**Scope.**
- **`Site` system** (RUIN, BATTLEFIELD, LOST_ROAD, LAIR, SHRINE), materialized once and persisted.
- **Bible ruin archetypes** as generators: Starwright lens tower, glade ring, Saramund watchkeep, Kingsway milestone, legion fort, Choir crypt, Watcher's cell, Varnkhar gate. Each carries its book set.
- **Battlefields** from real battles (M11) and pre-history (Red Ford, Cogsmire, Field of Lanterns).
- **Lost roads.** Kingsway fragments linking ruins, with milestones.
- **Loot modifiers** add longnight books to YUNG's dungeons, strongholds and desert temples by rarity rules.
- **`PlayerKnowledge`:** books read, layers per topic, a "What I know" journal page. NPC reactions to carried or read books.
- **Rumour markers** on the journal and map overlay (approximate).
- Give `night_shard`, `star_chart` and `ember_relic` behaviours tied to sites and knowledge (star chart reveals a site, ember relic is an Ashborn diplomatic gift).

**Acceptance tests.**
- GameTest: a site is placed once and persists through reload; never placed over player blocks.
- Unit:
  - forbidden books appear only in the allowed loot sources;
  - no text in the data states that the Dragon is Nahire (canon lint for the "never confirm" list).
- Headless client: screenshot each ruin archetype.

**Risks.** Archetype art quality needs iterations with screenshots. Budget time for review.

---

## M13: Graves, Hush and dark powers I (L)

**Goal.** The bible's connective tissue becomes systemic: graves remember, hunger feeds the Choir, and the dark has signs and counters (DESIGN §11).

**Scope.**
- **Grave block entity** gains `lastTended` and `unremembered`. Household grave-tending tasks. Players relight candles (+reputation).
- **`WorldSim.hush`** with inputs and outputs. The star-reading forecast band (M14 hook).
- **Blight v2.** Per-settlement infection spread by grain caravans and adjacency, the `blighted_wheat` crop stage on infected farms, quarantine law, burning fields.
- **Choir cells.** Secret membership, Quiet Table in famine, grave-robbing events, ledger anomalies (black candles) visible to stewards, investigation contracts (M10), trials.
- **Undead raids** (stage 2+) on settlements with unremembered graves, using the raid and battle engines.
- **Curses and haunted objects**, first cut: black-thread curses from grudges; grief-bound items from untried murders, resolved by M10 justice.
- **Faith charity** (almshouses, Waterhouses, Bowl-Right) reduces Choir recruitment. Conversion dynamics by Night stage.

**Acceptance tests.**
- Unit:
  - 400-day no-player soak keeps Hush below 40 in stages 0 to 2;
  - famine without charity raises cell formation probability as specified;
  - an unremembered grave requires a 1-year lapse.
- GameTest: undead rise only from unremembered graves and only at stage 2 or above.
- Server scenario: a blight spreads along a caravan route and is stopped by quarantine.

**Risks.** Punishing absent players. NPC kin tend graves by default; signs come weeks before effects.

---

## M14: Magic traditions and Ars Nouveau integration (M)

**Goal.** Magic is rare, costly and social, with Ars Nouveau as the scholastic path (DESIGN §10).

**Scope.**
- **Traditions data** (`data/longnight/traditions/*.json`). Practitioner Persons with tradition rank. Teachers via apprenticeship.
- **Player-facing longnight magic:**
  - hearthcraft threshold wards (doors) and nightmare calming;
  - runesmithing blocks (permanent ward, sealing rune);
  - druid seed-blessing and blight cure;
  - star-reading forecasts (Night stage, Hush band, wyrm stirring: warning rungs);
  - ember-shaman oath-binding.
- Ward lookup moves to the spatial index.
- **Ars Nouveau soft integration.** Lantern Colleges at TOWN+ libraries sell or teach AN entry items (ids verified against AN 5.13.3), reputation-gated. Social reaction to visible casting where sorcery is suspect, if AN exposes a cast event (verify; otherwise skip). Star-sickness counter for heavy night casting during the Long Night.
- **Witch-hunt events** under the Brands sect and the witch-hunt law.

**Acceptance tests.**
- Unit: lint, no tradition effect adds direct damage or armour; the faith-buff lint still passes.
- GameTest:
  - threshold ward blocks a "Thirsting" NPC from entering uninvited;
  - blessed seed farm is immune to blight for a season.
- Pack test: with AN absent, everything loads and Lantern Colleges sell nothing AN-specific.

**Risks.** AN API changes. Keep integration in `compat/ars/`, reflection-free but guarded by mod presence, and pin to the pack version.

---

## M15: Dragons and great beasts (L)

**Goal.** Wyrms as regional powers with territories, signs and choices, and lesser beasts as everyday threats (DESIGN §12).

**Scope.**
- **`Wyrm` records** (5 named, from the bible) with states, hunger, sleep timers and hoards. A sign scheduler at least 5 days before any attack. `dragon_sighting` news.
- **T2 world cues:** steam or frozen water near the lair, fleeing herds, smoke.
- **Tribute stone block and tribute treaty.** Wyrmhunt contract and expedition. Evacuation.
- **Entity:** one great wyrm (Cindermaw) first. Multipart, custom flight controller, strafing passes on settlement roofs, fire limited by `wyrmBlockDamage` (default `settlement_only`, never in claims). Decision on GeckoLib vs a hand-written model at prototype time.
- **Lair generation** (an M12 Site) with a hoard. Slayer succession claim. Hoard casus belli.
- **Lesser drakes** (fen, sky, fire) as mobs with spawn rules by biome and Night stage. Duskhounds and Stonebacks as the first great-beast variants.

**Acceptance tests.**
- Unit: no attack without at least 5 days of signs; tribute suppresses the hunt.
- GameTest: fire never placed on player blocks or in claims; the wyrm's death leaves exactly one hoard.
- Benchmark: a wyrm fight keeps the server at 20 TPS and the client within 10% FPS of baseline on llvmpipe-equivalent settings (relative measure).

**Risks.**
- Flight AI quality and griefing. Ship one wyrm, polish it, then add the rest.
- Art and animation cost.

---

## M16: Long Night stages 3 to 5, multiplayer polish and dynasty mode (L)

**Goal.** Complete the campaign arc and make the game robust for groups (DESIGN §13, §7.10).

**Scope.**
- **Stage machine** with bible triggers (Eye thrown, portal lit, dragon killed, 20 days without Rekindling, Hush 100) and stage JSONs. Time alone never passes stage 2 (D9 kept).
- **Night of Falling Stars, Last Ember tracking** (last known holder or position), faction doctrines (agenda overrides and actions per stage), High Crown claims, theft and envoy behaviour.
- **Stage 4:** Night Tides (battle engine, capped by fairness rules), crops only near light, Duskhounds, Lantern League diplomacy. Dim-sun client fog. Longer nights via a server day-time skip (config-gated, auto-off with `doDaylightCycle=false`). Iris and DH compatibility notes.
- **Resolution:** Rekindling detection, price event (elder's funeral, Maervaine Dimming, star-iron loss), Afterdawn. Unmaking or Gloaming as a config hard mode.
- **Dynasty mode.** Immutable world flag, non-hardcore only. Heir selection on death, persona switch, estate law.
- **Multiplayer polish:**
  - per-player knowledge in all screens;
  - shared realms with offices;
  - opt-in PvP wars (`pvpWars` off by default);
  - contract claiming fairness;
  - announcement routing (citizens anywhere vs nearby).
- **Xaero waypoint export and EMI recipe plugin** (soft).
- **Performance certification.** Full pack plus mod client test on both presets. Server TPS probe with 4 players in different settlements. Update `modpack/VERIFICATION.md` *with* the longnight jar.

**Acceptance tests.**
- Unit:
  - forced triggers walk 0 to 5 correctly; time alone stops at 2;
  - dynasty refused on a hardcore world;
  - a v0.1 save at old stage values migrates.
- Server scenario:
  - 30-day stage-4 soak with Night Tides never exceeding fairness caps;
  - Rekindling scripted via commands yields Afterdawn plus the price event.
- Headless client: fog and sky screenshots under performance and rich presets.

**Risks.**
- Day-length manipulation and fog are the most compatibility-sensitive features. Both stay behind config with safe defaults.
- Dynasty death-screen handling differs between singleplayer and servers. Test both.

---

## Not planned (explicitly)

- Ships as physical, sailable entities (sea lanes stay abstract).
- NPCs casting Ars Nouveau spells.
- Player-versus-player war by default (opt-in only).
- Any LLM feature beyond the off-by-default rephrasing layer in DESIGN §2.7.
- Explicit romantic content of any kind.

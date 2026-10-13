# Kingdoms of the Long Night: Systems Design

This document is the systems design for the whole game: what the `longnight` mod must become, starting from what v0.1 actually does. `docs/WORLD_BIBLE.md` is canon for lore (names, peoples, faiths, calendar, the Long Night). This document decides how that lore becomes data, code, entities and tests. `docs/ROADMAP.md` orders the work.

Audience: the engineers building the next milestones. Every section has the same shape:

- **(a) v0.1**: what exists, with class names. Where v0.1 is abstract, simplified or theatrical, this says so.
- **(b) Target design.**
- **(c) Data model changes.**
- **(d) Performance**: how it fits the two-level simulation (abstract everywhere, embodied near players).
- **(e) Connections** to other systems.
- **(f) Failure modes and tests.**

Status tags: **[v0.1]** implemented, **[partial]** some of it exists, **[design]** not started. Paths are relative to `mod/src/main/java/com/longnight/` unless they start with `data/` (meaning `mod/src/main/resources/data/longnight/`).

---

## 1. Architectural foundations

### 1.1 What exists [v0.1]

- **One authoritative world record.** `sim/WorldSim` is a single `SavedData` (`longnight_world`, `FORMAT = 1`) stored with the overworld. It holds every `Person`, `Settlement`, `Faction`, `NewsItem`, `Caravan`, `War`, player claim, ward stone and known-place set.
- **Abstract daily tick.** `WorldSim.tick()` detects a day change and calls `daily(day)`, which runs, in order: `Life`, `Economy` (which runs `Caravans`), `Planner`, `Threats`, `Politics`, `NewsSystem`, `Growth`, `Contracts`, `LongNightArc`. Missed days are replayed, up to `maxCatchUpDays` (10), all inside one server tick.
- **Embodiment.** `world/Embodiment` makes a settlement "active" when a player is within `embodyDistance + radius` (112 + radius by default) or an admin observes it. Active settlements are materialized once (`materialize()` sets the hearthstone and lets `Placer.catchUp` raise paid-for buildings at `catchUpBlocksPerTick` = 48). Residents get at most one `CitizenEntity` each, capped at 40 per settlement and 160 in total. Bodies are never saved (`shouldBeSaved() == false`) and are discarded when the settlement goes inactive.
- **Conservation invariants**, enforced by `command/Invariants`, unit tests and GameTests:
  1. The settlement `Stockpile` is the only store of goods. Items become units on deposit and units become items on withdrawal (`game/Trade`, `game/TradeGoods`).
  2. Construction is pay-once. `Building.progress` counts paid steps and `Building.placed` counts physically placed steps, with `placed <= progress <= total` (`sim/Construction`, `world/Placer`).
  3. `WorldSim.kill()` is the only way to die, and it is idempotent.
  4. A person has at most one body, and the dead have none.
- **Production parity (D10).** Embodied workers run the same recipes in 8 work cycles per day (`CitizenBrain.CYCLE_TICKS` = 1250 ticks of standing at the workplace). Abstract production is scaled by `1 - embodiedFraction(s)`. The fraction is tracked per settlement, not per person.
- **WorldHooks** (`sim/WorldHooks`, implemented by `world/ServerHooks`) is the narrow interface through which the simulation touches the world: graves, raids, announcements, terrain checks and body refresh. Unit tests use `WorldHooks.NONE`.

### 1.2 Target architecture [design]

**Detail tiers.** The two levels in D4 remain, with two tiers added at the edges:

| Tier | Who | Update | Notes |
|---|---|---|---|
| T0 Archive | The long dead, extinct households, fallen realms | never | Compact records for genealogy, graves and chronicles (§17.4). |
| T1 Abstract | Every living person, settlement, realm, army, caravan, wyrm | daily (time-sliced), plus lazily evaluated **phases** | Chunk-independent. This is v0.1's daily tick. |
| T2 Materialized | Settlements and sites within ~256 blocks of a player, or with Distant Horizons LOD in range | per second | The world is caught up (blocks, decor, damage). No bodies. |
| T3 Embodied | Settlements, caravans, armies and beasts within `embodyDistance` | per tick | Entities act out primitive actions of the same plans used in T1. |

**Day phases.** Several systems need sub-day timing that players can observe: caravan position on a road, raid at dusk, Fairday market in the morning, moot at dusk. Rather than ticking these, T1 stores *scheduled intervals* (start tick, end tick, route) and evaluates them when a player arrives. A caravan's position is `route.pointAt((now - depart) / (arrive - depart))`. That makes it cheap to meet, escort or rob a caravan that was abstract a second earlier.

**Time-sliced daily work.** `daily()` becomes a job queue. At day rollover each system enqueues per-settlement or per-realm jobs, and the queue drains under a per-tick budget (default 3 ms/tick, configurable). The day's results are committed in system order, so `Life` for settlement A always finishes before `Economy` for A. Catch-up days are queued the same way and never block a tick for more than the budget. Commands that need results at once (`admin skip`, tests) drain the queue synchronously.

**Simulation events.** Systems currently call each other directly (for example `kill()` writes memories, pays inheritance and triggers succession). The target is a small internal event bus of records: `PersonDied`, `BuildingCompleted`, `BattleFought`, `NewsCreated`, `CrimeWitnessed`, `ContractCompleted` and so on. Listeners (chronicle, memories, news, contracts, achievements, Hush) subscribe to these. `kill()` keeps its idempotence and publishes once. This is a refactor of how systems are wired, not a rewrite.

**Determinism.** v0.1 seeds one shared `sim.rng` with `day * K ^ people.size()`, so adding any random call anywhere changes everything downstream. The target gives each system and day its own stream: `SplittableRandom(worldSeed ^ hash(system) ^ day)`, split per settlement. This makes golden-master tests (§19) possible and stops unrelated changes from breaking each other's tests.

**Persistence.** `WorldSim.save()` writes one compound containing everything, and almost any change calls `setDirty()` (36 call sites), so every autosave rewrites the whole world. The target:

- `FORMAT` increments come with explicit migrators (`sim/migrate/Migration_N`).
- State is split into shard `SavedData` files: `longnight_people_<n>`, `longnight_settlements`, `longnight_realms`, `longnight_archive`, `longnight_news`. Each shard has its own dirty flag.
- Transient server-only state that matters for correctness moves into save data. In v0.1 this includes `Crime.WANTED`, `Placer.PREPARED`/`DECAYED` and `RaidManager.RAIDS` (see the Known gaps in ROADMAP.md).

**Content as data.** Cultures, faiths, names and lore are already datapack JSON loaded by `content/Content`. Commodities, recipes, professions' parameters, building archetypes, layout grammars, laws, unit types, calendar events, wyrms and Long Night stage effects move to data too (`data/longnight/<kind>/*.json`). Behaviour stays in Java and is keyed by id.

### 1.3 Invariants every new system must keep

1. Goods and coin are conserved. Every creation is a recipe output, a mint, or a declared world source (harvest, ore). Every destruction is consumption, spoilage, damage or loss with a reason.
2. Pay-once for anything built, repaired or upgraded.
3. A person exists once, has at most one body, and is dead exactly once.
4. Nothing longnight does destroys a player-placed block, and nothing is built inside a claim unless the claim allows it.
5. The same plan gives the same results abstractly and embodied, within a stated tolerance (§2.4).
6. Every threat that can kill a hardcore player arrives with warning (§6.8).
7. Faith is never a combat bonus (bible section 0).

---

## 2. People and NPC intelligence

### 2.1 v0.1

**`sim/Person` identity.**
- Name: `given`, `family`, `epithet`. `sex`, `orientation`, `culture`, `faith`.
- Life: `birthDay`, `deathDay`, `deathCause`, `buried`.
- Place: `settlement`, `home`, `workplace`.
- Work: `profession` (27 enum values), `skills` (`EnumMap<Profession, Float>`, 0 to 100), `mentor`, `title`, `prestige`.
- Seven temperament floats: bravery, diligence, sociability, ambition, piety, kindness, greed.
- Needs: food, rest, safety, social, health (0 to 1), `illnessDays`, `loyalty`.
- Family: mother, father, spouse, children, courting.
- Abstract possessions: `coins`, `weaponTier`, `armorTier`, `hasTools`.
- Up to 32 `Relation`s (opinion and familiarity) and 24 `Memory` records (day, kind, other, text, weight). The least salient are evicted.
- `activity` and `blocked`: free-text strings shown to players.

**Decisions that exist:**
- `Jobs.assign` fills worker slots by priority. Food workers get priority 10 in hunger, and low-priority workers can be poached for the fields during famine.
- `Life.apprentices` attaches children aged `adultAge - 6` and older to a parent or the best worker, and they gain that trade's skill.
- `Economy.work` picks the first recipe whose output is below 3x target and whose inputs exist. Otherwise it sets `blocked` ("I have no iron ore to work with.", "The stores are full...").
- `Construction.advance` stops with `problem = "Waiting for stone."`.

**Embodied behaviour (`entity/CitizenBrain`):**
- Nine modes: IDLE, SLEEP, WORK, SOCIAL, PLAY, SHELTER, PATROL, CAMP, RAID. `decide()` picks one every second from time of day, danger, adulthood and profession. This is a fixed schedule, not a planner.
- `goTo()` heads for waypoints when the target is more than 24 blocks away. It detects being stuck, records "I can't find a way to X", and after three failures teleports onto the target if it is within 64 blocks.
- Work routines: builders place paid steps, farmers harvest and replant real crops, lumberjacks fell natural trees (`world/Trees`) and replant them, and other trades stand at their workplace "work" mark. Labourers walk between the store and a site but carry nothing.

**Information limits:**
- Knowledge is stored per settlement (`Settlement.knownNews`, news id to day learned), not per person.
- `NewsSystem.knownBy` delays a person's knowledge by 0 to 2 days depending on sociability, and filters out news older than 30 days.
- Players know places they have visited or heard of (`WorldSim.knownPlaces`).

**Dialogue:** `game/Dialogue` builds lines from templates using mood, memories, work, family and news, plus `lore/rumours.json` templates. No external AI is involved.

### 2.2 Target: identity and needs

Add to `Person`:

| Field | Purpose |
|---|---|
| `traits: Set<String>` | Discrete traits from data, e.g. `omen_born`, `veteran`, `sleepless`, `devout`, `drunkard`, `lame`, `literate`, `witch_blooded`. Each has data-driven modifiers (productivity, opinion, needs). |
| `conditions: List<Condition>` | Wounds, illnesses (`fever`, `grey_cough`, `plague`), pregnancy, grief. Each has a severity and a remaining duration. Replaces the single `illnessDays`. |
| `needs` | Adds `warmth` (fuel in winter), `worship` (faith practice), `purpose` (useful work) and `comfort` (goods basket, §3.6) to the five existing needs. Each need has a decay rate, a satisfier list and a priority used by the planner. |
| `household: UUID` | See §7.5. |
| `knowledge: PersonalKnowledge` | Facts this person witnessed or was told privately (crimes, secrets, Choir membership of others), with source and confidence (§2.6). Public news stays at settlement level. |
| `goals: List<LifeGoal>` | Slow ambitions: become master, marry, own a house, avenge X, make pilgrimage, retake Varnkhar. Drives choices beyond the daily routine. |
| `secret: String` | E.g. `veiled_choir`, `thirsting`, `smuggler`. Hidden from all UI until discovered. |
| `isPlayer`, `playerId` | Players become Persons (§7.10). |

Needs follow an order the planner understands: survival (food, warmth, health, safety), then shelter and rest, then belonging (social, family, worship), then purpose and comfort, then ambition. The settlement-level `morale` remains a smoothed mean.

### 2.3 Target: competence

`Economy.productivity` already multiplies skill (0.55 to 1.45), tools (0.7/1.0), mood, health and season. Add:

- **Ranks.** Apprentice (<25), journeyman (25 to 60), master (60+), with titles in dialogue and the person sheet. Masters may take two apprentices. Some recipes need a minimum rank, e.g. armour needs journeyman or above.
- **Quality.** Outputs of GOODS-category recipes carry a quality tier (crude/sound/fine/masterwork), drawn from skill + tools + workplace level. Quality multiplies price, durability (tool wear chance) and military effectiveness. The ledger stores quality as up to four sub-counts per commodity, not per item.
- **Failure.** Below rank thresholds there is a chance a batch is wasted. Inputs are consumed, nothing is produced, and the reason is logged as a blocker ("I spoiled the bread again").
- **Decay and transfer.** Unused skills decay by 1%/year above 30. Related trades share 50% of gains (blacksmith to toolsmith), using a data table.
- **Temperament** affects error rates (diligence) and willingness to take dangerous jobs (bravery for soldier, scout, miner).

### 2.4 Target: practical decision making (task planner)

This is the core missing system. Each actor (person, building crew, army, caravan) gets an HTN-style planner. It is deliberately small, deterministic and explainable, and it is shared by T1 and T3.

**Concepts.**
- **Goal**: what an actor wants, with a utility. Examples: `KeepSettlementFed`, `CompleteBuilding(b)`, `MakeTools(n)`, `MusterArmy(war)`.
- **Method**: one way to achieve a goal. It has preconditions over a read-only `SimView` and an ordered list of subtasks. Methods are tried in priority order.
- **Action**: a primitive step. It has a duration in work cycles, consumes and produces resources through `Reservation`s, and has two executors: `runAbstract(view, cycles)` and an embodied `EmbodiedTask` (go there, swing, place, carry).
- **Blocker**: the record left behind when every method fails: `{kind, subject, need, qty, since, remedies[]}`.

**Example decompositions.**

```
CompleteBuilding(b)
  EnsureSite(b)        : site surveyed & legal  | else Relocate(b) | else Blocker(NO_SITE)
  EnsurePlan(b)        : blueprint version ok    | else Rebaseline(b)
  EnsureAccess(b)      : reachability known ok   | else BuildPath(store->site) | Blocker(NO_ACCESS)
  EnsureMaterials(b,k) : reserve next k steps' cost from stock
                         | else Substitute (hew logs->planks, v0.1 Construction.hew)
                         | else OrderProduction(commodity) -> MakeX goal for the producing trade
                         | else OrderTrade(commodity)      -> caravan purchase from neighbour
                         | else PostContract(commodity)    -> notice board (v0.1 Contracts)
                         | Blocker(MATERIAL_MISSING)
  HaulMaterials(b,k)   : (embodied only) carry reserved units store->site
  PlaceSteps(b,k)

MakeTools(n)                      (smith needs metal + fuel)
  EnsureWorkstation(SMITHY)       | Blocker(WORKPLACE_MISSING) -> petition / planner request
  EnsureMetal(IRON, 2n)           | Smelt(ORE)  -> EnsureOre: stock | OrderTrade | MineOre (if MINE)
  EnsureFuel(FUEL, n)             | MakeCharcoal(LOGS) | OrderTrade
  EnsureHandles(PLANKS, n)
  Forge(n)

MusterArmy(war)                   (army needs equipment + food + leadership)
  EnsureLeadership                : commander (ruler / marshal / best soldier) | Blocker(NO_LEADER)
  EnsureMen(n)                    : soldiers + levy by law | Blocker(NO_MEN)
  EnsureEquipment(n)              : weapons/armour/bows reserved | MakeWeapons | Buy | march under-equipped (morale -)
  EnsureFood(n * days)            : rations reserved | Requisition (unrest +) | Blocker(NO_FOOD)
  Assemble at rally point; announce (warning ladder §6.8)
```

**Planner mechanics.**
- Depth-first decomposition with at most 6 levels and 64 node expansions per plan. Plans are cached on the actor and stamped with the version counters of the inputs they read (ledger version, building state version, reservation version).
- A plan is replanned when a stamp changes, when an action fails, or at the next phase boundary. It is never replanned every tick.
- **Reservations** (`Settlement.reservations`): units set aside for a plan, so an embodied hauler and the abstract tick never spend the same stone twice. `available = stock - reserved`. Reservations expire after a configurable number of days and are released when the holder dies, leaves or cancels.
- **Abstract/embodied parity.** In T1 an action completes in its nominal cycles, scaled by productivity. In T3 the same action completes when its embodied task finishes, and travel time counts against it. Parity tolerance: embodied output within ±25% of abstract output over a day for an unobstructed worker. Per-person accounting replaces the settlement-wide `embodiedFraction`. Each action records whether it ran abstract or embodied, so nothing is double-produced and a body stuck behind a wall does not erase the abstract share of its neighbours.

**Blocked-task explanation and fallback.** Blockers replace the free-text `blocked`. Each blocker kind has:

| Blocker kind | Example text (from data, localisable) | Remedy ladder |
|---|---|---|
| `MATERIAL_MISSING` | "We need 12 more stone for the granary." | produce, then buy, then contract, then wait |
| `WORKPLACE_MISSING`/`DAMAGED` | "My forge is in ruins." | repair order, then change job temporarily |
| `NO_ACCESS` | "I can't reach the quarry face; the path is cut." | build path or ladder, then report, then relocate |
| `NO_SITE` | "No flat ground left inside the walls." | grow radius, terrace, then cancel with refund |
| `STORAGE_FULL` | "The stores are full to the rafters." | build storehouse, sell surplus, then stop production |
| `NO_TOOLS` | "My axe broke and the smith has no iron." | order tools, then work at 0.7x |
| `NO_RESOURCE` | "No trees we may fell within 28 blocks." | forester replants, move camp, or buy logs |
| `UNSAFE` | "Not with raiders on the road." | wait for guards or escort |
| `SEASON` | "Nothing grows in Deepnight." | switch to winter tasks |
| `NO_DEMAND` | "We have plenty already." | help elsewhere (haul, build) |
| `CLAIMED` | "That land belongs to another." | relocate (v0.1 `Planner.relocate`) |

Blockers surface in four places: dialogue (the existing yellow line), the ledger GUI's Projects tab, Jade tooltips, and automatically generated contracts and petitions.

**Settlement-level planning.** `Planner.next()` is a hand-ordered list of 35 rules. It becomes a utility scorer. Each `BuildingType` has a data-driven need function (housing deficit, food days, storage pressure, faith, defence threat, agenda weight, tier institutions) that returns a score and a reason ("Food lasts 4 days; a second farm"). The top-scoring candidate is planned. The reasons appear in the ledger GUI so players can see why the town is building a tavern and not a wall.

**Apprenticeship and occupation change.** The v0.1 behaviour (apprenticed to a parent or the best worker) remains. Add:
- Apprentice contracts with a master, recorded on both Persons.
- An occupation-change model with explicit triggers: workplace destroyed, sustained `NO_DEMAND`, famine draft (exists), injury (trait `lame` excludes soldier and miner), old age (retire to teaching, advising or minding grandchildren), and ambition (a skilled journeyman leaves to found a workshop elsewhere).
- Changing trade costs time: one week at 0.5x productivity in the new trade below 25 skill.

### 2.5 Target: memory and relationships

- **Structured memories.** Replace free text with `{kind enum, subjects[], place, day, weight, decayRate, flags}`. Text is rendered at display time from `data/longnight/memory_texts/*.json` (localisable). Salience is `|weight| x recency` with a floor for life events (birth, marriage, bereavement, crime), which are never evicted. The cap stays at 24 for the living. On death, memories collapse into the archive summary (§17.4).
- **Relations** gain tags: kin, friend, rival, lover, mentor, pupil, creditor/debtor, liege/vassal, sworn enemy. A **grudge** is a relation tag with a cause memory and a decay rule. Dunmar weigh-ledgers make grudges non-decaying until balanced (Debt-Day). Opinion becomes the sum of decaying modifiers (`{source, value, expires}`) plus a baseline from compatibility. This keeps the existing `opinionOf()` API and makes opinions explainable ("He insulted my mother at the Fairday").
- **Faith and culture opinion.** The bible 5.9 faith matrix (+, 0, -, x) becomes data and gives a starting opinion modifier between strangers. Successful mixed marriages slowly warm culture-pair modifiers in that settlement (bible 13.4).

### 2.6 Target: information limits

- **Public news** stays per settlement (v0.1). **Private knowledge** is per person: witnessed crimes, confided secrets, rumours with a confidence value.
- **Rumour distortion.** Each retelling hop may drop or blur a field (names become "someone", numbers grow) with probability proportional to the teller's sociability and inverse to their diligence. The original `NewsItem` is never altered. Distortion is a view.
- **Couriers and envoys** (new professions) carry letters and important news faster than travellers, and their deaths on the road delay news. Bards speed news inside a settlement.
- **Players** learn through the notice board, conversations, letters and their journal. Nothing in the GUI shows information the player has not plausibly learned. Admin and debug views are separate and permission-gated.

### 2.7 Optional LLM dialogue layer [design]

Off by default. It exists only to rephrase.

- **Contract.** The `Dialogue` system first produces an *utterance intent*: the exact template text it would show, plus a fact set (names, places, numbers, the speaker's mood word and culture style). The LLM may only rewrite that text in the speaker's voice. It never chooses topics, never answers free-form questions with new facts, and never triggers actions.
- **Validation.** The response is accepted only if every proper noun and number in it appears in the fact set, its length is within 2x the template, and it passes a profanity/modernism filter. Otherwise the template text is shown. Failure is invisible to the player.
- **Configuration.** Client-side config `llmDialogue.enabled=false`, `provider`, `endpoint`, `apiKeyEnvVar` (the key is read from an environment variable and never written to world or config files), `maxCallsPerMinute`, `timeoutMs = 1500`.
- **Placement.** It runs on the client, rephrasing lines it already received, so dedicated servers never hold keys and other players are unaffected. Each rephrase is cached by `(template hash, fact hash, speaker id)`.
- **Tests.** Dialogue tests run with the layer off. A validator unit test feeds adversarial completions (invented names, numbers, instructions) and asserts they are rejected.

### 2.8 Data model changes

- `Person`: the fields in §2.2, structured `Memory`, tagged `Relation` with modifier lists, `rank` derived from skill, `apprenticeOf`, `blockers: List<Blocker>` (max 3).
- `Settlement`: `reservations`, `ledgerVersion`.
- New: `sim/plan/{Goal, Method, Action, Planner, SimView, Reservation, Blocker}` and `entity/tasks/*` (embodied executors).

### 2.9 Performance

- **T1.** One plan evaluation per person per day for daily goals. Settlement goals are evaluated per settlement. Target cost: at most 0.2 ms per 50-person settlement per day, measured by the benchmark in §19.
- **T3.** Replanning happens only on failure or a phase change. Path requests go through a server-wide token bucket (default 8 new paths per tick), and `goTo` keeps its waypoint approach. Reachability results (site X reachable from store Y) are cached per settlement for a day and invalidated by block changes in the settlement radius (counted via the `BlockEvent` hooks already used for vandalism).

### 2.10 Connections

The planner is the hub:
- It consumes the economy (stock, recipes, reservations), construction (blueprint costs), politics (laws restricting actions) and threats (`UNSAFE`).
- It produces contracts (§3.1, ROADMAP M9), petitions (§5.5), caravan orders (§3.5) and dialogue lines.

### 2.11 Failure modes and tests

| Failure | Mitigation | Test |
|---|---|---|
| Plan thrash (replanning every tick) | Version stamps, phase boundaries, failure backoff | GameTest: builder with a missing material replans at most N times per day |
| Reservation leak (units never released) | Expiry, release on death or move, invariant check `reserved <= stock` | Unit: kill a hauler mid-carry, then assert reservations are released |
| Embodied/abstract double production | Per-action execution record | Unit + GameTest: one day observed vs unobserved, outputs within ±25% |
| Unexplained idleness | Every failed method must emit a blocker. Lint: a `Method` with no blocker on failure fails a unit test | Unit: enumerate all goals with an empty `SimView`, assert each produces a blocker |
| LLM invents facts | Validator, cache, off by default | Unit: adversarial outputs rejected |

---
## 3. Professions and economy

### 3.1 v0.1

- **20 commodities** (`sim/Commodity`), each with a base price in pennies, a category and a nourishment value:
  - Food: grain, flour, bread, meat, fish, vegetables, plus ale (luxury).
  - Materials: logs, planks, stone, glass, iron ore, iron, fuel, wool, leather.
  - Goods: tools, weapons, armour, cloth.
- **27 professions** (`sim/Profession`), 13 of which have daily recipes: farmer, lumberjack, quarrier, miner, fisher, hunter, shepherd, miller, baker, brewer, carpenter, blacksmith, weaver.
  - Extractive recipes create goods from nothing but labour, skill and season. For example a miner yields 3 ore, 2 fuel and 2 stone per day anywhere.
  - The physical work near players is theatre over the recipe. Felled trees and harvested crops are destroyed without drops (`destroyBlock(pos, false)`), and the ledger output comes from the recipe.
- **Prices.** `Economy.targets` sets per-commodity stock targets from population, workers and construction bills. Price is `base x clamp(sqrt((target+4)/(stock+4)), 0.35, 3)`. Players buy at 1.25x and sell at 0.75x (`game/Trade`); citizens get 10% off. Spoilage is 2%/day for meat, fish and bread (0.4% with a granary). Storage capacity is 150 plus each building's `storage`.
- **Money.** Settlement income ("dues") is created each day: `pop x (0.6 + morale) x (1.6 with a market)`. This is the only coin source apart from ruin chests and initial endowments. Realm tax takes a share, soldiers cost 1p/day, and workers get 1p/day when the treasury exceeds 4p per head. Coins are items: copper penny (1), silver mark (10), gold crown (100). Exact change, no minting recipes (D11).
- **Caravans** (`sim/Caravans`) are abstract records. A settlement with surplus sends up to 40 units of one good to the best-scoring neighbour with a shortage, within 2000 blocks, at 700 blocks/day. The destination pays 80% of its local price. Caravans carry news both ways and are ambushed with a 12% chance per day when an outlaw camp lies within 250 blocks of the straight line.
- **Contracts** (`sim/Contracts`, added late in v0.1) post delivery contracts generated from stalled buildings, shortages and Leaffall/Dimming fuel needs, plus bounties on outlaw camps. They pay a bonus from the treasury.
- **Gaps:**
  - Glass is consumed by most blueprints (window panes) but nothing produces it.
  - Cloth is produced but nothing consumes it.
  - Flour, ale and cloth cannot be bought by players.
  - Farmer's Delight is not mapped at all (`TradeGoods` hard-codes vanilla items).

### 3.2 Target: profession catalogue

Workplaces marked *new* need a `BuildingType` and blueprint. Outputs are per worker-day at average skill. Numbers are starting values for balancing, not final.

| Profession | Workplace | Recipe (inputs -> outputs) | Embodied work | Notes |
|---|---|---|---|---|
| Farmer | Farm | (field) -> grain 7, vegetables 3 | till, plant, harvest culture crop | Yield depends on harvested crop blocks when embodied (§3.4). Culture crops include FD cabbage, onion, tomato and rice |
| Forester | Forester's lodge *new* | saplings -> woodland stock | plant saplings in managed groves, mark trees | Keeps lumber sustainable. Eldrin tree-debt and Grove "seasons owed" |
| Lumberjack | Lumber camp | woodland stock -> logs 6 | fell marked natural trees, carry logs | Blocked when woodland stock is 0 |
| Miner | Mine | deposit -> ore (iron/copper/gold/coal by site geology) 3 | descend shaft, mine in owned shaft volume | Geology sampled once at site survey |
| Quarry worker | Quarry | -> stone 10, sand or clay by site | cut quarry face (owned volume) | Clay pits near rivers |
| Fisher | Fishery | -> fish 5 | rod at the shore mark | Sea and river variants; Tide-Wives' almanac yields bonus |
| Hunter | Hunting lodge | game stock -> meat 3, hides 1 | patrol hunting ground | Game stock regenerates; over-hunting depletes it |
| Shepherd | Pasture | flock -> wool 3, mutton 1 | herd and shear real sheep | Flock size from real sheep when embodied |
| Beekeeper | Apiary *new* | -> honey 2, wax 1 | tend beehives (vanilla hives) | Burrowfolk speciality. Candle chain (bible 13.2) |
| Miller | Mill | grain 8 -> flour 8 | | |
| Baker | Bakery | flour 6, fuel 1 -> bread 10 | | |
| Cook | Tavern / Kitchen *new* | meat, vegetables, grain, fuel -> meals (high nourishment, +comfort) | FD cooking pot / stove as workstation | Meals map to FD stews and pies |
| Brewer | Tavern / Brewhouse | grain 4 -> ale 6; honey -> mead | | |
| Butcher | Butchery *new* | livestock -> meat, hides, tallow | FD knife held | Leaffall slaughter peak |
| Carpenter | Carpentry | logs 4 -> planks 16; planks -> furniture | | Furniture maps to Handcrafted items |
| Mason | Masonry *new* | stone -> cut stone; clay + fuel -> bricks | | Stone-town blueprints consume cut stone |
| Builder | Builder's yard | (construction steps) | places blueprint steps | v0.1 |
| Blacksmith | Smithy | ore 3, fuel 2 -> iron 3 | | Smelting only |
| Toolsmith | Smithy | iron 2, fuel 1, planks 1 -> tools | | Split from v0.1 blacksmith |
| Weaponsmith | Smithy / Armoury *new* | iron, planks, leather -> weapons (by roster type) | | Bows are made by the fletcher variant (Eldrin and Valemen yew) |
| Armourer | Armoury *new* | iron 4, fuel, leather -> armour | | |
| Leatherworker | Tannery *new* | hides, bark/salt -> leather; leather -> saddles, straps | | |
| Tailor | Weavery / Tailor *new* | wool -> cloth; cloth -> clothes | | Clothes enter the comfort basket (§3.6). This fixes the cloth dead end |
| Potter | Pottery *new* | clay, fuel -> pottery | | Jars cut food spoilage by up to 50% |
| Glassmaker | Glassworks *new* | sand 2, fuel 1 -> glass 2 | | Fixes the v0.1 glass deadlock. Aszari and Tidefolk speciality |
| Chandler | Apiary / Chandlery *new* | wax, tallow -> candles | | Funerals, grave tending and the Lantern Vigil consume candles |
| Jeweller | Jeweller *new* | gold, gems -> jewellery | | Luxury, dowries, prestige, tribute |
| Merchant | Market | arbitrage, caravans | runs a stall on Fairday | v0.1 |
| Courier | Post / Town hall | carries letters and news | rides the roads | §2.6 |
| Stablehand | Stable *new* | feed -> horses (breeding) | tends real horses | Cavalry, carts, caravans |
| Sailor | Harbour *new* | crews ships | | Abstract sea lanes first (§3.5) |
| Caravan guard | Market / Barracks | escorts | walks with the caravan | Cuts ambush odds |

Community and service roles:

| Role | Workplace | Effect |
|---|---|---|
| Healer | House of Healing | Consumes herbs and medicine. Halves illness damage (v0.1 halves without consuming anything) |
| Teacher | School | Apprentice and literacy rate (v0.1 bonus +0.3) |
| Scholar | Library | Lore, omens, magic research. Interprets books for players |
| Librarian | Library | Makes and keeps books (paper, ink, leather). Lore delivery |
| Priest (per-faith titles) | Shrine / Temple | Rites, festivals, charity, worship need |
| Gravedigger | Graveyard | Burials and grave tending (Hush, §11) |
| Bard | Tavern | Mood, news speed, festival performances |
| Innkeeper | Tavern | Lodging for travellers and players, rumours |
| Steward | Town hall | Runs the ledger. Petitions. Taxes |
| Diplomat | Town hall | Envoy missions (§5.9) |
| Scout | Watchtower | Early warning (§6.8), sighting reports |
| Soldier, Guard | Barracks, Watchtower | v0.1 |

### 3.3 Target: commodities and item mapping

New commodities (minimum set needed for the chains above): `sand, clay, bricks, cut_stone, copper, gold, gems, coal` (fuel remains the generic burnable; coal and charcoal convert to fuel), `hides, tallow, salt, honey, wax, candles, meals, preserved_food, mead, herbs, medicine, paper, books, furniture, clothes, pottery, jewellery, horses, feed, saplings`.

Commodities become data. `data/longnight/commodities/<id>.json`:
```json
{ "base_price": 3, "category": "food", "nourishment": 1.2, "spoil_per_day": 0.02,
  "storage": "granary", "items": [ {"tag": "c:crops/cabbage", "units": 1}, {"item": "minecraft:carrot", "units": 1} ],
  "buy_item": "minecraft:carrot", "quality": false }
```
- `TradeGoods.of()` becomes a lookup built at datapack reload from these entries, with tags first and then items.
- Farmer's Delight integration is entirely through this data and tags:
  - FD crops map to `vegetables`.
  - FD meals map to `meals`.
  - Preserved items (e.g. FD's cured or salted foods, if present) map to `preserved_food`.
  - The pack provides a `longnight:commodity/<id>` item tag that FD items are added to. Before shipping, verify FD 1.3.4's exact tag ids against the jar; do not guess.
- No compile-time dependency on FD. When FD is absent, the entries simply match nothing.
- Food baskets per culture (bible section 4: Valemen bread, stew and pies; Dunmar mushroom stew and black bread) set which `meals` sub-types cooks prefer and which FD items NPC bodies hold and eat.

Recipes become data too. `data/longnight/recipes/<profession>/<id>.json` with inputs, outputs, cycles, minimum rank, workstation block tags, season and culture modifiers, and quality flag. `Profession.recipes()` is generated from these.

### 3.4 Target: coupling the ledger to the physical world

v0.1 decouples physical work from output. The target couples them *where it is cheap and visible*, and keeps a recipe fallback where it is not:

- **Farms.** When embodied, harvesting a mature crop adds its yield to a per-building `pendingHarvest`. The day's farm output is `max(recipe x season x blight x embodiedShare, pendingHarvest)`. When abstract, the recipe applies. Players who trample or steal crops reduce real output, and that shows in the ledger.
- **Woodland and game stocks.** Each settlement tracks `woodland` (trees available in its radius plus 64 blocks) and `game`. Abstract lumberjacks draw down `woodland`. Foresters and natural regrowth refill it. Embodied felling of a real tree also decrements it. Woodland is initialised from the site survey (forest biome counts) and refreshed by a cheap sampled count when the settlement is materialized.
- **Mines and quarries** own a volume of blocks (shaft or face). Embodied miners remove real blocks inside that volume, and only natural blocks. Abstract mining advances a depth counter. The volume grows deeper, never wider, so it never undermines houses.

### 3.5 Target: storage and transport

**Storage classes.**
- Granary: food.
- Storehouse: goods and materials.
- Woodyard and stoneyard: logs and stone, open-air with a high cap.
- Cellar: ale, preserved food.
- Treasury: coin, held in the town hall.

Each class has its own capacity, so a full storehouse no longer blocks bread production. Spoilage is per commodity and modified by storage class and pottery. Visible stock: storehouse blueprints get 4 to 8 "display slots" where decor blocks (Supplementaries sacks, vanilla barrels and hay) are placed and removed to reflect fill levels. This is T2 cosmetic and never touches player blocks.

**Transport, in three layers:**
1. **Haulers** (T3 only). Labourers carry reserved units between store, workshop and site as an `InTransit {commodity, qty, carrier}` record, holding a matching item for visuals. If a carrier dies, the goods drop as real items (and can be recovered by deposit) or are counted as lost. Abstract settlements have no haulers: in-settlement transport is free in T1 and slower in T3. The parity tolerance in §2.4 absorbs this.
2. **Carts** (T3). A `CartEntity` pulled by a horse or mule follows roads (§4.4) and carries up to 64 units. It is used for inter-building bulk and short inter-settlement runs. Carts are only spawned when embodied. In T1 they are capacity on the caravan record.
3. **Caravans.** These stay abstract records with routes along the road graph instead of straight lines. When a player is within embody range of the caravan's evaluated position, its leader, guards, carts and pack animals spawn as bodies on the road with real cargo reservations. Players can trade with, escort or (criminally) rob it. Ambush risk uses road segments near outlaw camps, scaled by the guard count.

Sea lanes (Tidefolk) are abstract only until a ship entity is worth building: a harbour-to-harbour route with storm risk tied to `tide` almanac events.

### 3.6 Target: households, consumption and prices

- **Households** (§7.5) own coin, a food share and a comfort basket. Wages flow from workshop revenue (sales to the settlement ledger at local price) to households, rather than "1p/day from the treasury". The settlement treasury is funded by taxes (hearth-tax per household, market dues on Fairday, tolls) instead of `income()` minting coin.
- **Money supply.** New coin enters only through realm mints. A capital with a mint converts gold into crowns, and silver marks come from silver/gold ore. Coin also enters through ruin hoards and wyrm hoards. Coin leaves through wear (an optional 0.1%/year) and hoarding in graves (skrell "keep their favourite shiny"). This replaces the per-day dues mint, which inflates without limit. Transition plan: keep dues for one milestone with a declining multiplier, and measure coin per capita in the 200-day benchmark.
- **Consumption baskets.** Necessities (food, fuel in winter), comforts (clothes, ale, pottery, candles) and luxuries (jewellery, wine/mead, books). Unmet comforts lower mood. Unmet necessities drive emigration and crime (§5.6).
- **Prices.** Keep the v0.1 formula as the instantaneous signal and add:
  - exponential smoothing, so prices don't jump with every trade;
  - a seasonal curve (grain cheapest in Reaping, dearest in late Deepnight, bible section 6);
  - player price impact. Each player sale shifts stock and therefore price, so dumping 500 cobblestone crashes the stone price locally.
  - a per-settlement daily coin budget for purchases from players. v0.1 already caps purchases by treasury.
- **Markets and Fairday.** On Fairday (§15) merchants from neighbouring settlements with active caravans set up stalls. Buy/sell spreads narrow to 1.15/0.85, and households buy their comfort baskets. Players may rent a stall plot to sell to NPC households at better prices than the ledger pays.
- **Trade agreements.** The realm stance `TRADE` (v0.1) becomes a treaty object (§5.9) with tariffs, toll exemptions and optional exclusive goods (e.g. Combe candles to one realm). War embargoes block caravans (v0.1 `canTrade` already checks war and relation > -40).

### 3.7 Data model changes

- `Commodity` becomes a registry of `CommodityDef`. The enum can remain for core ids during migration.
- `Stockpile`: per-class capacity, quality sub-counts, `reserved` map, `version` counter.
- `Settlement`: `woodland`, `game`, `geology`, `households`, `marketDay` state.
- `Building`: `pendingHarvest`, storage class.
- `Caravan`: `routeId`, `guards`, `carts`, `cargo` reservations, `escortPlayer`.

### 3.8 Performance

- `Economy.targets()` is recomputed for every worker, every price lookup and every caravan pair. Cache it per settlement per day, and invalidate it when buildings or population change.
- Caravan dispatch is O(S² x goods). Restrict it to settlements within 2000 blocks using a spatial grid (§17.3).
- Embodied haulers and carts count against body caps. Haulers are the first bodies dropped when the cap is reached.

### 3.9 Connections

- **Planner**: blockers, then orders. **Contracts**: shortages become player work.
- **Politics**: taxes, tolls, tariffs, mints. **War**: embargo, requisition, equipment.
- **Faith**: tithes, charity, candles. **Long Night**: price shocks from bible 12.3. **Calendar**: Fairday, seasonal curves.

### 3.10 Failure modes and tests

| Failure | Mitigation | Test |
|---|---|---|
| Deadlocked chains (the glass problem) | Lint: every consumed commodity has a producer recipe, a trade source, or is marked `import_only` with a contract fallback | Unit: dependency-graph check over all recipes and blueprints at datapack load (fails the build) |
| Starvation spiral | Famine draft (v0.1), Common Pot law, refugees, charity | 200-day soak: no settlement over 20 people starves without a cause such as war, blight or a raid |
| Inflation or deflation | Mint-only coin, coin-per-capita metric | 200-day benchmark stays within a ±30% band |
| Player exploit: buy low here, sell high next door forever | Price impact, smoothing, transport time | GameTest: 10 alternating trades shrink the margin to zero |
| Duplicate goods via haulers | `InTransit` is counted in the conservation check | Invariant: stock + inTransit + reserved accounted |
| Unknown items from other mods | Unmapped items are refused (v0.1 behaviour) | Unit: TradeGoods mapping table from data |

---

## 4. Settlements and construction

### 4.1 v0.1

**Site choice.** `world/SiteFinder.survey` samples 220 points within `worldRadius` (1200) of spawn using generator noise heights and biomes, without loading chunks. It rejects water and slopes steeper than a spread of 14 (22 for Dunmar), scores flatness, water and forest, and picks a culture per biome keyword (`Culture.biomeKeywords`). `WorldBuilder.generate` places 7 established settlements at least 260 blocks apart, grouped into realms of up to 3, plus 2 settler camps, 2 outlaw camps and 3 ruins. Leftover sites go to `spareSites` for future camps (`Growth.foundCamp`).

**Layout.**
- A square grid of 13-block cells (`Planner.CELL`) around the centre, searched in rings. Outer types (farm, pasture, lumber camp, quarry, mine, graveyard, fishery, hunting lodge) prefer ring 3. The well takes the centre cell.
- `findCell` rejects cells inside claims, cells overlapping other settlements, and cells that fail `ServerHooks.plotLooksBuildable` (noise-height spread at most 9 and less than half water).
- Rotation faces the centre. There are no roads, only a path ring around each building footprint.

**Buildings.**
- 30 `BuildingType`s with footprint, worker slots, housing and storage.
- `world/BlueprintGen` generates each blueprint procedurally from type, culture palette (`Culture.palette` roles), roof style (`gable`, `flat`, `turf`), faith (shrine block, temple glass colour) and seed. Results are cached (max 512). The `level` parameter is part of the cache key but is ignored by the generator.

**Construction.**
- `Construction.advance` charges each step's cost (`world/Materials.cost` maps block to commodity) exactly once.
- Builders place paid steps physically (`Placer.placeNext`). Abstract builders advance progress at `STEPS_PER_BUILDER_DAY = 260 x crew x (1 - embodiedFraction)`, and the world catches up when next loaded.
- Before the first step, `Placer.prepare` surveys the real plot: median height, spread at most 9, water, any non-natural block, and claims. It relocates the building if needed (`Planner.relocate`), then clears and founds the plot. Steps that hit a foreign block are skipped and still counted as placed, so player blocks are never replaced.

**Lifecycle.**
- `PLANNED -> CONSTRUCTING -> COMPLETE`. `DAMAGED` is a flag set by raids and battles with no physical effect and no repair.
- `RUINED` buildings in ruin settlements decay once (`Placer.decay`: holes, moss, cracked bricks, plus a lore chest in temples and watchtowers).
- Tiers come from population plus required institutions (`Planner.updateTier`). An empty settlement becomes a `RUIN` (`Growth.abandon`), but its completed buildings are not decayed.

**Claims.** Boundary stones (`game/Claims`) protect a 32-block radius. No building, felling or clearing happens inside.

### 4.2 Target: site choice

Extend `Site` scoring with:
- arable fraction (grass/dirt biome share);
- timber (`woodland`);
- stone and ore (geology from noise height, plus biome hints such as stony peaks);
- defensibility (hills, river bends);
- distance to the nearest same-culture realm (colonisation pressure) and to rival realms;
- road connectivity (estimated A* cost to the nearest settlement).

New settlements founded by NPCs (`Growth.foundCamp`) use the same scorer on `spareSites` plus a fresh survey ring, never within 96 blocks of a claim or 220 blocks of another settlement (v0.1 distances kept).

### 4.3 Target: layout grammars

Replace the pure ring grid with a two-stage layout:
1. **Skeleton.** The centre feature (well, green, ember-pit, quay) comes first. Then one main street per neighbouring settlement bearing (up to 3), plus a ring lane at 2 to 3 cells, all traced over noise heights with maximum gradient limits.
2. **Plots.** Plots are placed along the streets, facing them, in zones: core (market, temple, hall), residential, craft, outer (fields, pasture, quarry, graveyard). The 13-block cell remains the allocation unit for compatibility, but cells are now addressed along street segments.

Culture grammars (`data/longnight/layouts/<culture>.json`):

| Archetype | Culture default | Skeleton | Signature |
|---|---|---|---|
| Timber village | valemen | Linear street along a green, church/Kindlehall at the head | Timber frames, thatch, village green, hedged fields |
| Stone town | valemen (TOWN+), tidefolk inland | Crossroads market square, walls at TOWN tier | Stone-brick lower storeys, market cross |
| Mountain hold | dunmar | Terraces cut into slope, gatehouse facing the valley, halls into the hillside | Deepslate halls, rune panels, short squat roofs |
| Woodland settlement | eldrin | Glade rings around a great tree, no straight streets | Living-tree flets, calcite halls, lantern cages |
| Coastal port | tidefolk | Quay line, then narrow parallel lanes uphill, lighthouse on the point | Tall narrow houses, bell-house, nets |
| Desert city | aszari | Courtyard blocks around a waterhouse, Sunhouse on the east axis | Domes, flat roofs, awnings, water channels |
| Fortified capital | any (CITY tier + capital) | Inner keep cell cluster, concentric wall, gate per main street | Keep, curtain wall, towers |
| Hillside burrows | burrowfolk | Contour lines on a slope, gardens between | Turf mounds, round doors (v0.1 `turf` roof) |
| Ember camp/longhall | ashborn | Ring around ember-pit, palisade | Hide tents, then long-halls, bone totems |
| Stilt warren | skrell | Clusters on mangrove roots, plank walkways | Salvage, working traps (sparkwork) |

Terrain handling:
- Plots with a spread of up to 9 (v0.1) get terraces: retaining-wall steps paid like any other blueprint steps.
- Above 9, the cell is marked bad (v0.1 `badCells`).
- Dunmar may cut into the slope: a "cut" blueprint variant removes natural stone (free, it becomes stone stock) instead of filling.

### 4.4 Target: roads between settlements

- **Planning.** Run A* on a coarse grid (8-block cells) over generator noise heights. No chunk loading. Costs come from slope, water (a bridge cost if the crossing is 12 blocks or less, otherwise impossible) and biome (swamp and dense forest are expensive). The result is a polyline stored as a `Road {id, a, b, points[], segments[]}` in `WorldSim.roads`. Roads are computed when a trade pair first forms, between settlements of the same realm, and to the nearest neighbour.
- **Building.** Each 16-block road segment is a mini-blueprint: path blocks from the culture palette (`path` role), Macaw's Paths where installed, gravel fallback, and vanilla block fallback. Segments are built by a road crew (labourers and builders) when that end of the road is embodied. In T1 they advance like buildings, paid in stone or gravel. Bridges over 12 blocks or less use Macaw's Bridges blocks if present (palette fallback: planks and fences).
- **Furniture.** Milestones every 64 blocks on realm roads (a Kingsway nod: stone-brick pillar plus sign "IV leagues to X"). Signposts at junctions use Supplementaries' signpost if present. Lantern posts near towns.
- **Usage.**
  - Caravan travel time uses road length instead of straight-line distance (unpaved roads 0.8x speed, paved 1.0x, wilderness 0.5x).
  - Embodied travellers follow the polyline with waypoint navigation (v0.1 `goTo` waypoints, now taken from road points).
  - Raids and armies prefer roads.
  - The player journal and map overlay draw known roads.
- **Claims.** A road never crosses a claim. The router treats claims as obstacles. If no route exists, the road ends at the claim edge with a `CLAIMED` blocker, and the claim owner can grant passage (§4.7).

### 4.5 Target: building lifecycle

```
PLANNED -> CONSTRUCTING -> COMPLETE <-> DAMAGED -> RUINED -> (REBUILDING -> COMPLETE)
                               |
                               v
                           UPGRADING -> COMPLETE (level+1)
```

- **Levels.** `BlueprintGen` honours `level`:
  - L1 is the v0.1 building.
  - L2 adds a storey, attic or extension inside the same plot. Plots are sized for L3 from the start: blueprints use only part of the cell at L1.
  - L3 swaps materials (timber to stone lower storey) or adds a feature (forge chimney, bell tower).
  - Effects scale per level: housing (v0.1 already adds +2 per level), worker slots, storage, quality bonus.
- **Upgrade = delta blueprint.** `delta(Ln, Ln+1)` lists the steps whose state differs, including removals. Removal steps refund salvage (50% of the removed block's cost) and are placed as air. Progress over the delta uses the same pay-once rule and the same `progress`/`placed` counters on a separate `UpgradeJob`.
- **Damage mask.** `Building.damaged: BitSet` over step indices.
  - Abstract damage (raid, battle, fire, wyrm) picks steps by layer: roof first for fire, walls for siege.
  - When the building is materialized, damaged steps are removed physically. The block becomes air, or fire is placed for burning buildings in T3.
  - Players' and explosions' damage to a building is detected by `Placer.verify(b)`, which samples up to 16 random placed steps per settlement per second and marks mismatches. Player-placed replacements count as "foreign" and are left alone.
  - **Repair** re-pays and re-places damaged steps.
  - `usable()` requires the damaged fraction to be under 30% for most types, and under 10% for walls and gates.
- **Rebuild.** A `RUINED` building in a living settlement may be rebuilt over its old plot: the survey accepts its own blueprint's blocks as "self". Ruins of dead settlements are never rebuilt by the original culture. A new settlement may reuse the site.
- **Abandonment decays.** When a settlement is abandoned, every building becomes `RUINED`, and decay runs once when materialized. Decay must be persisted (`Building.decayed = true`), unlike v0.1's in-memory `DECAYED` set (which re-runs decay and re-rolls ruin chests after every restart).
- **Blueprint versioning.** Each `Building` stores `bpVersion`, the `BlueprintGen.VERSION` at plan time. If generator code changes, completed buildings keep their recorded version. The generator keeps old versions callable for at least one release, or freezes an as-built step hash. For an in-progress building with `placed > 0` the plan is re-baselined: compute the new blueprint, mark steps whose world block already matches as placed, and set `progress = max(progress, placed)` without charging. Without this, any change to `BlueprintGen` silently corrupts every half-built house in existing saves.

### 4.6 Target: walls and gates

- **Planning.** At TOWN tier, or VILLAGE tier with `threatened` (v0.1 notion) or the `FORTIFY` agenda, the planner proposes a perimeter:
  1. Take the union of building footprints plus a 4-block margin.
  2. Smooth it into a convex hull, or a rounded rectangle for grid cultures.
  3. Sample it every 8 blocks into segments.
  4. Place gates where roads cross, a tower every 4 to 5 segments and at corners.
- **Segments.** Each segment is a `Building` of a new kind, `WALL_SEGMENT` / `TOWER` / `GATEHOUSE`, with a parametric blueprint (length, heading, start and end ground height, material tier):
  - Palisade: logs plus Macaw's palisade blocks where available.
  - Stone wall: stone or cut stone plus Macaw's walls.
  - Gatehouse: Macaw's portcullis or barn doors, else vanilla fence gates and iron bars.
- **Exclusions.** Segments skip claims (leaving a gap, with the claim owner able to permit), water and cells steeper than 4 blocks between ends (steps or terraces are inserted, otherwise a gap).
- **Defence.** `Threats.defence` multiplies by 1.3 for a complete palisade and 1.6 for a complete stone wall, only while gaps (missing or damaged segments) make up less than 10% of the perimeter. Gates close at dusk and at raid start. Embodied raiders path to gates or breaches. Siege engines and sappers create breaches (damage masks).
- **Cost scale.** A 300-block stone perimeter costs about 1,500 stone, roughly 150 quarry-days. Walls are a multi-year project, planned in segment batches. Players can speed it up by delivering stone (contracts).

### 4.7 Target: player-claim respect and land

- v0.1 radius claims stay. Add chunk-aligned claims (the boundary stone claims the chunk it is in plus neighbours by tier) to make overlap checks O(1) via a claim chunk map.
- **Permissions** per claim: `allowRoads`, `allowWalls`, `allowCitizensToPass`. These are set by right-clicking the boundary stone in the GUI.
- **Land deeds** (`land_deed` item, registered in v0.1 but unused): bought at a town hall, they grant a plot (a cell or two) inside a settlement. The settlement treats the plot as claimed by the player, and its planner routes around it. Selling the deed back returns the plot. Deeds are the clean way for players to live inside NPC towns.

### 4.8 Target: progressive visible construction

v0.1 already places steps one at a time, bottom-up. Add:
- Scaffolding (`minecraft:scaffolding`, free, removed on completion) on the outside of walls over 3 high.
- Material piles at the site's "stage" mark, reflecting reservations.
- Builders hold the block they are placing.
- Construction noise via vanilla place sounds.
- The site sign shows "Granary, 42%".
- Catch-up placement (`catchUpBlocksPerTick`) skips scaffolding and piles: it builds the final state directly.

### 4.9 Data model changes

- `Building`: `bpVersion`, `damaged: BitSet`, `decayed`, `kind` (building / segment / road), `upgrade: UpgradeJob`, `params` (for parametric blueprints).
- `Settlement`: `layout` (skeleton polylines and zones), `wall` (perimeter definition and segment ids).
- `WorldSim`: `roads`, `claimChunks`.
- Data: `data/longnight/layouts/*.json`, `data/longnight/buildings/*.json` (footprint, slots, storage, level effects, zone, required tier), and optional block-id fallback lists in culture palettes:
  ```json
  "roof": ["mcwroofs:spruce_roof", "minecraft:spruce_stairs"]
  ```
  The first id that exists in the registry wins. Material costs for modded blocks come from block tags `longnight:cost/planks`, `longnight:cost/stone` and so on, extending `Materials.cost`.

### 4.10 Performance

- Layout and road planning use noise heights only (as `SiteFinder` does). Cap them at 2 ms per call and run them in the sliced daily queue.
- Placement stays budgeted (48 blocks/tick default), with lower priority for segments and roads than for houses.
- `verify()` sampling is limited to 16 checks per active settlement per second.

### 4.11 Connections

- **Economy** (costs, salvage), **planner** (blockers: `NO_SITE`, `CLAIMED`, `NO_ACCESS`).
- **Warfare** (walls, breaches, damage masks), **wyrms** (fire), **Long Night** (stage effects damaging untended buildings).
- **Exploration** (ruins). **UI** (projects, why).

### 4.12 Failure modes and tests

| Failure | Mitigation | Test |
|---|---|---|
| Relocation loops | `badCells`, radius growth limit (v0.1: 120 to 150), cancel with refund (v0.1) | GameTest: steep arena, building cancels within N tries with a refund |
| Overwriting player blocks | Survey plus per-step foreign check (v0.1). Claims | GameTest `neverBuildsOverPlayerBlocks` (v0.1), extended to roads and walls |
| Half-built house after a blueprint change | `bpVersion` re-baseline | Unit: change generator version and assert the re-baseline charges nothing and places nothing twice |
| Ruin chests re-rolled after restart | Persist `decayed` | GameTest: decay, then simulated reload, then decay is not re-run |
| Chunk-border placement | `areaLoaded` check (v0.1). Steps wait when unloaded | Server scenario: settlement across 4 chunks catches up |
| Wall cuts a road or a claim | Router obstacles, gap rules | Unit: wall planner with a synthetic claim leaves a gap and logs a blocker |

---
## 5. Politics

### 5.1 v0.1

**`sim/Faction`.**
- `Government` (MONARCHY, COUNCIL, CLAN, REPUBLIC, THEOCRACY, LORDSHIP, OUTLAWS) with titles and succession text.
- `Stance` (PEACE, TRADE, ALLIANCE, WAR, TRIBUTARY, OVERLORD). The last two are never set.
- `Agenda` (8 values) derived every 4 days from ruler temperament and circumstance in `Politics.updateAgenda`. It drives building priorities and war.
- Relations from -100 to 100, `taxRate` from 0.08 to 0.3 (it drifts up under the TAX agenda), a treasury that is never spent except on reparations, and player reputation.

**Succession.** `Politics.chooseHeir` works per government: eldest child, prestige plus age, bravery, coin, piety. An ambitious, prestigious rival may secede and start a war.

**Diplomacy.** Relations drift between realm pairs. Trade pacts form when relation > 25, alliances when > 60, and pacts lapse when relation drops below 0. War is declared only by a CONQUER-agenda ruler with relation < -35, strength above 1.3x the target's + 2, distance < 1100, and after day 12.

**Rebellion.** Loyalty decays under taxes above 0.2. A settlement that is not the capital, has 6 or more people and has average loyalty under 0.25 rises with a 5% daily chance and secedes.

**Players.**
- Settlement and faction reputation.
- Citizenship at reputation 20 or above (`/longnight join`), which gives a 10% discount.
- Founding (`game/Founding`) creates a LORDSHIP realm with `playerRealm = true`, `playerRuler = player`, but `ruler = null`. `Politics.daily` then runs `succession()` on that realm because the ruler is null, which appears to install the most prestigious NPC settler as the realm's "Lord", with an agenda that can reach CONQUER. **This must be fixed before anything below** (ROADMAP M1).
- Player governance consists of `/longnight plan <building>` and `/longnight rename`.

**Crime** (`game/Crime`):
- Witnesses are non-hostile bodies of that settlement within 16 blocks.
- Assault costs -6 reputation; murder costs -50 settlement and -30 realm reputation plus a 3-day wanted period; vandalism inside building footprints costs -3. Killing outlaws earns reputation.
- The wanted state is an in-memory map, lost on restart. There are no trials, no NPC crime and no sentences.

### 5.2 Target: offices, titles and legitimacy

- **Office** `{id, realm, title (culture data), holder, seat, duties, salary}`.
  - Per-culture office sets from bible 11.2. Valemen: King, Earl, Baron, Knight, Reeve. Tidefolk: Helm, Council of Hulls, Harbourmaster. Aszari: Qadhir, Vizar, Sunjudge, Mehira. And so on.
  - Generic duty roles map onto these: ruler, steward, marshal, judge, chaplain, envoy, reeve (settlement head).
  - Settlements get a **holder** (a lord or reeve), so a realm is a tree of holdings, not a flat list.
- **Legitimacy** (0 to 100, per realm) from lineage, faith confirmation (sun-touch, ancestor-lot), victories, justice served, full granaries and paid soldiers. It decays with famine, lost wars, unpaid soldiers, broken treaties and disputed succession. Low legitimacy raises claimant and rebellion chances.
- **Corruption** (0 to 100, per realm and per office holder) comes from greed temperament, bribes, nepotistic appointments and unpaid officials. It skims tax yield, makes trials go wrong and feeds banditry.
- **Titles for players**: Citizen, Freeholder (owns a land deed), Sworn (vassal or retainer), Knight or the cultural equivalent, Lord (founder or granted holding). Plus honorifics from deeds: Wyrmslayer, Bandit-breaker, Rekindler. Titles are stored on the player's Person (§7.10).

### 5.3 Target: laws

`data/longnight/laws/*.json` defines laws with options, cultural defaults and hook ids. A realm holds a `LawSet`. Laws change by decree (monarchy), council vote (republic, council), moot (burrowfolk) or challenge (ashborn), on Moot-day, with legitimacy and unrest effects.

| Law | Options | System hooks |
|---|---|---|
| Harvest tax | none / tenth sheaf / fifth | Reaping in-kind levy, moved by tax caravan |
| Hearth-tax | per household p | treasury, unrest |
| Tolls | none / roads / bridges | caravan cost, road funding |
| Levy | volunteers / one per household / all able | mobilisation size, unrest |
| Refugees | admit / admit kin only / bar | Growth, Hush (bible 13.3) |
| Faith tolerance | state faith only / tolerated / free | conversion, unrest, Choir detection |
| Witch-hunts | forbidden / tolerated | hearthcraft practitioners, Brands |
| Curfew / sleep law | none / curfew / sleep law (stage 2+) | night-mare pressure, crime |
| Sun law (aszari default) | trade stops at dusk | night markets, crime |
| Weapons in town | allowed / peace-bonded | player interactions, brawls |
| Common Pot (burrowfolk default) | none / reserve X% | famine buffer |

### 5.4 Target: taxes

- **Collection** happens in Reaping (bible calendar) and moves real goods. The settlement pays an in-kind levy, plus coin from the hearth-tax, to its holder. The holder pays a share to the realm capital by tax caravan, which can be robbed and which leaves news when it fails.
- **Spending.** The realm treasury pays soldiers and officials, building grants (the capital funds walls in border towns), famine relief and wars. A treasury that cannot pay soldiers produces Ember Code "lawful collection" by Ashborn mercenaries (bible 13.8) and desertion.

### 5.5 Target: petitions

Blockers and grievances become `Petition {settlement, petitioner, kind, subject, supporters, day, status}`. Examples: "Build a granary", "Lower the hearth-tax", "Justice for the murder of X", "Admit refugees", "Send soldiers against the outlaws". Petitions gather supporters by opinion and need, and are heard on Moot-day:
- NPC rulers decide by agenda, temperament and treasury.
- Player lords decide through the Petitions tab of the ledger GUI.

Ignored petitions lower loyalty. Granted ones raise it and create planner goals.

### 5.6 Target: crime and trials

- **Crime records** are persistent: `{id, kind, class (petty/grave/unforgivable, bible 11.1), perpetrator, victims, place, day, evidence[], status}`. Evidence includes witness memories (persons who saw it), found items (stolen goods carry a "provenance" data component when taken from a settlement ledger or chest), confessions and rumours. Rumours never convict.
- **NPC crime.**
  - Theft by the hungry or greedy, which reduces stock and leaves a witness chance.
  - Brawls between rivals, which wound.
  - Murder from sworn enmity: very rare, with a long buildup visible as quarrels.
  - Smuggling, poaching, grave robbing (Choir), cold-hearting.
  - Rates scale with poverty, corruption and unrest.
- **Trials** run on Moot-day by the culture's judge (Twelve Hands jury, Sunjudge, Rattle shouting-trial and so on). The verdict probability comes from the evidence weight, the judge's competence and corruption. Sentences come from the culture's preferred list: fine, stocks, labour, flogging, branding, exile, death. NPC sentences execute abstractly, or embodied if a player is present (stocks are a block at the market square).
- **Players** accused of crimes are summoned via the notice board and a guard approach.
  - Attend: a trial screen with the evidence shown and a chance to pay, plead or present counter-evidence.
  - Flee: wanted in that realm and exiled.
  - Death sentences for players are **never executed by the game**. Exile and outlawry are the maximum: in hardcore, death comes from combat the player chose to fight, not from a cutscene.
  - The wanted state is persisted per realm, with expiry.

### 5.7 Target: succession and inheritance

- Succession laws per culture: eldest-child-acclaimed, ancestor-lot, sun-touch confirmation at the Day Without Shadow, Ash-Moot challenge, Council election, Rattle-holding for 3 days. These replace the switch in `chooseHeir`.
- **Claims** are persistent records: `{claimant, title, strength, source}`. Sources: blood, wyrm-slaying, conquest, the Last Ember (High Crown). Several strong claims cause a succession crisis. The losing claimant may raise a faction, leading to civil war (§5.8).
- **Inheritance** passes the estate (coins, house ownership, workshop, deeds, titles, notable items) by culture law. Today only coins pass to the spouse or children (`WorldSim.kill`).

### 5.8 Target: unrest, rebellion and civil war

- `unrest` (0 to 100) per settlement comes from hunger, taxes versus prosperity, legitimacy, faith friction (ruler faith versus majority faith, under the tolerance law), war weariness, corruption, crime and ignored petitions.
- Stages:
  - 30: grumbling (rumours)
  - 50: petitions and boycotts (the burrowfolk Great Grumble, guild strikes cut production)
  - 70: riots (damage masks, looting, deaths)
  - 90: rising (secession, as v0.1 `Politics.secede`)
- **Civil war** is a war between the realm and a rebel faction carved from its settlements, with the claimant's war goal. Rebels who win found a realm (v0.1 behaviour kept).

### 5.9 Target: diplomacy

- **Treaties** are objects: `{kind, parties, terms, startDay, expiresDay, brokenBy}`. Kinds: trade pact (tariff, toll exemption), non-aggression, defensive alliance, offensive alliance, truce, tribute or vassalage (activates TRIBUTARY/OVERLORD), marriage alliance (requires a marriage between ruling houses), guest-right, and wyrm tribute (§12).
- **Envoys** are Persons who travel by road. News and treaty offers arrive with them, and killing an envoy is a casus belli.
- **Opinion** is a sum of decaying modifiers with causes (v0.1 has only a scalar), so the GUI can say why two realms hate each other.
- **Breaking** a treaty costs legitimacy and gives an opinion penalty with every realm that hears of it, via news propagation.

### 5.10 Target: player influence and player realms

- **Player-lord governance** (ledger GUI, only in settlements the player holds):
  - Set laws, from options their culture and government allow.
  - Appoint NPCs to offices (steward, marshal, reeve).
  - Approve building plans, or veto the planner's top choice.
  - Hear petitions and judge trials. Grant land deeds.
  - Swear fealty to an NPC realm, which gives protection, taxes and musters.
  - Declare independence, conduct diplomacy, name an heir.
- Without input, the appointed steward runs the settlement as v0.1 does now. A player can leave for weeks.
- **Service.** Players may swear service to an NPC lord: knight, retainer, envoy, mercenary captain. Duties come as contracts (answer the muster, escort the tax caravan, clear the road). Rewards are pay, titles and land deeds.
- **Multiplayer.** A realm may have several player members with roles (lord, marshal, steward). Permission checks are by office, not by "founder". Two player realms interact through the same treaty system as NPC realms. Player-versus-player war requires both lords to have opted into `pvpWars`, a server config, off by default.

### 5.11 Data model changes

`Faction`:
- adds `legitimacy`, `corruption`, `laws`, `offices`, `treaties` (ids), `claims`, `holdings` tree;
- adds `rulerKind` (NPC or player), replacing the implicit `playerRealm && ruler == null` case.

`Settlement`: adds `holder`, `unrest`, `petitions`.

New `WorldSim` collections: `crimes`, `treaties`, `claims`.

### 5.12 Performance

Politics is per realm (fewer than 30) and per settlement (fewer than 100). Daily cost is negligible. Watch two things:
- The O(R²) diplomacy pair loop. Fine up to about 50 realms.
- Trial evaluation over witnesses. Bounded by the evidence list.

### 5.13 Connections

- Economy (taxes, tolls), warfare (casus belli, levy).
- Faith (confirmation, tolerance), personal lives (marriage alliances, inheritance).
- Long Night (faction doctrines, High Crown), UI (petitions, laws, trials).

### 5.14 Failure modes and tests

| Failure | Mitigation | Test |
|---|---|---|
| Player realm hijacked by an NPC ruler (v0.1 bug) | `rulerKind = PLAYER` skips succession and agenda | Unit: founded realm keeps a null NPC ruler and a non-CONQUER agenda for 100 days |
| Cascade collapse (realms vanish in a week) | Rebellion cooldowns, legitimacy floors after succession | 300-day soak: realm count stays in a sane band; tracked as a metric |
| Unpunishable player | Persisted wanted state, exile enforcement by guards | GameTest: murder, then restart, then guards still hostile |
| Unfair trial against a player | Evidence weights shown, counter-evidence | Unit: rumour-only cases never convict |

---

## 6. Warfare

### 6.1 v0.1

- `WorldSim.War {attacker, defender, target, cause, startDay, score, battles}`.
- A battle every other day compares summed fighter strength: `1 + 0.4 weaponTier + 0.4 armorTier + 0.4 bravery`, with 0.75 to 1.25 noise and a 1.3x watchtower bonus. The winner gains 15 to 25 score. Casualties are real persons killed or wounded. Brave labourers are levied when a realm has fewer than 3 fighters.
- At score 50 the target is ceded. At -40 the attacker pays reparations (up to 200p). War weariness causes a "weary peace".
- Nothing moves on the map, and no war is ever fought in front of the player: only outlaw raids are embodied (`world/RaidManager`). Raids get rumours (day -2), scouts (day -1) and a horn at dusk. Raiders are the band's real members, and the raid ends at rout (two-thirds dead), all dead, or dawn.

### 6.2 Target: casus belli and war goals

`data/longnight/war_goals/*.json`. Each goal defines its score requirements and peace terms.

| War goal | Casus belli source | Peace terms on victory |
|---|---|---|
| Seize settlement | claim on a border settlement, old grudge (worldgen), tolls | settlement ceded (v0.1 `cede`) |
| Press claim | succession claim (§5.7) | claimant crowned, realm becomes tributary |
| Holy war | faith hostility (x in 5.9) plus zealot agenda (Burning Mirror) | conversion law, temple built, tribute |
| Reconquest | Dunmar Returners and Varnkhar, lost provinces | territory |
| Punitive raid | raid or envoy murder, broken treaty | reparations, hostages |
| Tribute | overlord demands, wyrm tribute shared | tributary treaty |
| Liberation | ally's occupied settlement | restore to ally |
| Hoard / Ember | slain wyrm hoard, the Last Ember (§13) | item transfer, High Crown claim |
| Rebellion / civil war | §5.8 | independence or restoration |

### 6.3 Target: mobilisation, armies and logistics

**Army record** `{id, faction, commander, units[], route (road ids plus progress), supplies, morale, state, goal}`:
- States: MUSTERING, MARCHING, CAMPED, BESIEGING, FIGHTING, RETREATING, DISBANDED.
- A unit is `{type, persons[], equipment level, training, morale}`.
- Persons are real. While mustered they are away from home, their workplaces are short-handed (blockers), and their households worry (memories).

**Unit types** (`data/longnight/units/*.json`) map each culture's bible roster onto generic roles:

| Role | Examples (bible rosters) | Equipment (commodities) | Embodied behaviour |
|---|---|---|---|
| Militia/levy | Levy Spearman, Pitchfork Militia | tools or spears | hold line, rout early |
| Infantry | Billman, Shieldbearer, Marine, Raider | weapons, armour | line, shield wall |
| Archers | Yeoman Archer, Sentinel Archer, Hedgewarden | bows, arrows | ranged from second line |
| Crossbowmen | Arbalester, Bolter | crossbows, bolts | ranged, slower volleys |
| Cavalry | Knight, Dune Rider, Tusk-Rider, Pony Rider | horses, weapons, armour | charge flanks, pursue |
| Scouts | Outrider, Warden, Delver, Wolf-Runner | light | early warning, sighting reports |
| Engineers | Siegewright, Sapper | tools, logs | build rams and ladders, breach walls |
| Siege crews | (rams, mangonels) | logs, iron | operate engines |
| Battle mages | Star-Singer, Shaman, Firetalker | rare practitioners | morale and terror effects, light |
| Healers | Healer, Tide-Wife | medicine | reduce deaths to wounds after battle |

**Mobilisation.**
- Muster size comes from the levy law and the population.
- Muster takes `2 + distance/700` days at the rally point. This is visible news, the first rung of the warning ladder.
- Equipment is reserved from the realm's stocks. Missing equipment lowers effectiveness and morale but does not block mustering. The planner (§2.4) explains it: "Half the levy marches with pitchforks".

**Logistics.**
- Supplies are food days carried plus forage, which depends on season and land, and is worse in Dimming and Deepnight.
- Supply lines are convoys (caravans tagged military).
- Out of supply: attrition (desertion, illness) and requisition from settlements passed, which adds unrest and grudges.
- The campaign season is Seedtide to Reaping (bible). Winter campaigns suffer 3x attrition.

### 6.4 Target: morale

Each unit has morale from 0 to 1. Inputs:
- commander bravery and skill, plus prestige;
- pay status, food, cause legitimacy (war goal versus faith and culture);
- war-songs from ember-shamans and bards. This is a morale effect, never stat buffs (bible section 7);
- recent losses, flanking, terror (wyrms, undead).

Rout at morale < 0.25, surrender when surrounded below 0.15. Routed units retreat along their route and may re-form.

### 6.5 Target: battles, abstract and embodied

**Abstract resolution** (T1): Lanchester-style rounds. Per round, side A's losses = `k x effectiveStrength(B) x modifiers / effectiveStrength(A)`, where the modifiers cover terrain, walls (×1.3/1.6 as §4.6), surprise, weather and night. Morale is checked each round. Losses are assigned to persons weighted by unit exposure: front-line infantry first, cavalry on charge. 40% of "casualties" are wounds, as v0.1 already does.

**Embodied battles** (T3), when a player is within embody range of the battle site at the start, or arrives during it:
1. **Cap.** At most `maxBattleBodiesPerSide` (default 24) bodies per side. The rest are reserves, fed in as waves at the side's entry edge when front-line strength drops.
2. **Formations.** A line (infantry), a second rank (ranged), wings (cavalry), skirmish screen (scouts) and a rear (healers, commander). Each formation slot is a target position the body's `goTo` holds. Melee bodies engage the nearest enemy within their sector.
3. **Abstract reserve exchange.** Every 5 seconds the reserves exchange losses abstractly at a reduced rate, so a 200-a-side battle is still mostly simulated.
4. **Switching.** If players leave, the current state (living bodies' health, positions, morale) is written back to the battle record and resolved abstractly. The reverse works the same way, so identity, wounds and deaths persist across the switch.
5. **Player participation.** Players may fight on a side (allied to their realm or by contract), or keep clear. Killing in a sanctioned battle is not murder. Attacking non-combatants is.

**Behaviours**:
- Patrols: guards (v0.1 `PATROL`), road wardens.
- Escorts: caravan guards follow the cart and engage ambushers.
- Retreat: move to the rally point, keep formation if morale > 0.15.
- Surrender: drop weapons, follow the captor. Captives are Persons with `captiveOf` and are ransomed by treaty, freed, or enslaved where cultures allow. The design keeps this to ransom and labour, no slavery.

### 6.6 Target: sieges

- An army at a walled settlement makes camp: a T2 decor camp of tents (v0.1 `CAMPSITE` blueprint) placed outside the walls on natural ground, removed later.
- Each day:
  - the defender consumes stock and has no trade;
  - engineers build engines (logs, iron) and attack segments (damage masks);
  - sappers mine Dunmar-style;
  - the defenders may sally;
  - relief armies may arrive.
- Assault happens when a breach exists, or by ladders at a heavy morale cost.
- A siege ends in capitulation (starvation at food < 0, or morale), assault, relief, or a lifted siege when attackers run out of supply.
- **Embodied.** A player inside sees the camp, engines and assaults as embodied battles at the breach. A player outside can harass the camp or break the supply line.

### 6.7 Target: consequences

| Consequence | Mechanism |
|---|---|
| Damage | Damage masks on buildings, fields burned (`pendingHarvest` zeroed, crops trampled when embodied) |
| Refugees | `Growth.immigrate` already prefers people from low-morale places. Add explicit refugee columns (households move with goods) |
| Veterans | Trait `veteran` (+bravery, nightmares memory), prestige, pensions under law |
| Occupation | Garrison unit stays, occupied settlement's loyalty and unrest drift, partisans |
| Reconstruction | Repair goals, contracts, realm grants |
| Resentment | Grudges against the faction (memory and relation tags), opinion modifiers that last generations, memorials |
| Graves and memorials | Battle sites become `BATTLEFIELD` sites (§14) with bones and banners, and a war memorial building option |

### 6.8 Target: hardcore warning signs and fairness

Invariants, each with a test (§19):
1. No hostile force larger than an outlaw raid targets a player-founded or player-citizen settlement before world day 40 **and** before that settlement has existed for 20 days.
2. Every force that will reach a player's settlement produces at least 3 warning rungs, at least 2 in-game days before contact, delivered to citizens wherever they are (chat or toast, notice board, journal):
   1. rumour
   2. muster news
   3. sighting of the army on the road (scouts, watchtower)
   4. the bell
3. The strength of an early threat is capped relative to the target's defence: at most 1.2x for the first 60 days of a settlement.
4. Threats come along roads and are visible on the map overlay once sighted. Players can always choose to evacuate.
5. Raids (v0.1) keep `firstRaidDay` (5) and `raidWarningDays` (2).

### 6.9 Data model changes

New collections in `WorldSim`: `armies`, `battles`, `sieges`, `captives`. `War` gains `goal`, `claims`, `participants` (allies), `score` per goal and a list of treaty terms.

### 6.10 Performance

- Armies are records that move along routes (`pointAt(t)` evaluated lazily).
- Abstract battles take O(rounds x units).
- Embodied battles are capped by bodies (24 a side, counted against the global body cap). When the cap would be exceeded, settlement townsfolk bodies far from the battle are the first discarded.

### 6.11 Connections

- Economy (equipment, supply, requisition), politics (war goals, levy law, legitimacy).
- Construction (walls, damage), personal lives (widows, orphans, veterans).
- Exploration (battlefields), UI (map overlay of armies), wyrms and Long Night (Night Tides reuse the battle engine with undead units).

### 6.12 Failure modes and tests

| Failure | Mitigation | Test |
|---|---|---|
| Unavoidable death of a hardcore player from an army | Invariants above | Scenario test: player settlement founded on day 1, run 60 days with wars forced, assert the warning ladder fired before every contact |
| Body explosion (lag) | Caps and waves | Server benchmark: 100 v 100 battle near an observer keeps TPS above 18 on 4 cores |
| Abstract/embodied outcome bias | Calibration: run the same battle 100x each way | Unit plus GameTest: win rates within 10% |
| Armies stuck (no route) | Road graph fallback to wilderness at 0.5x speed, disband after N days | Unit: unreachable target, army disbands with news |

---

## 7. Personal lives

### 7.1 v0.1

- **Social life.** `Life.socialise` runs 1 to 4 interactions a day, weighted to spouse and workmates. Opinion changes come from `compatibility` (temperament distance, faith, culture, kindness). Friends at opinion 40 and above and rivals at -40 and below create memories.
- **Courtship and marriage.**
  - Adults who are attracted (orientation) and unrelated (parents and siblings only) court for 3 to 6 days, then propose if opinion is 40 or above.
  - Weddings move the household into one home and may convert one spouse, with faith strictness.
  - There is news, a chronicle line and guest memories, but no ceremony.
- **Births** happen only to married male and female couples. The chance scales with culture lifespan, food and room. Children inherit a mix of temperament and the family name by `NameSet` style (surname, patronymic, house, clan, place).
- **Ageing and death.**
  - Ageing uses a culture lifespan (skrell 45 to eldrin 260).
  - Ages are in years with a default of 8 in-game days per year (D8, config 2 to 96), so a Valemen life is about 560 days.
  - Death comes from fever, starvation, old age and violence. Bereavement and grief memories are recorded, and coins go to the spouse or children.
- **Burial.** `Placer.bury` places a `gravestone` block entity (name, dates, cause, faith, rite text, epitaph from `lore/epitaphs.json`) plus one faith mark (candle, sapling, unlit campfire, blue candle, amethyst, chiseled bricks). This only happens when the settlement is embodied and has a completed graveyard.
- **Gaps.** The `wedding_token` item exists with no behaviour. There are no households, adoption, divorce, funerals or grave tending.

### 7.2 Target: friendships and rivalries

Relations already exist. Add events that move them:
- shared work and festivals;
- gifts (households spend comfort coin);
- insults at the tavern (low kindness plus ale);
- debts and favours (skrell);
- rivalry escalation: quarrel, then a brawl at the Fairday (NPC crime, petty), then a feud between households, then a grave crime at very low probability.

Reconciliation happens at festivals (Debt-Day, Calm Day), through priest mediation and through a death in the family. Rivalries carry over between generations through household memory, at a reduced weight.

### 7.3 Target: romance (non-explicit, adults only)

Courtship stages (interest, courting, betrothal, wedding) use the v0.1 opinion mechanics plus the faith matrix (bible 5.10):
- `sun` outsider converts;
- `ancestors` requires a favourable lot (weighted by clan prestige; the Unbroken Line refuses outsiders);
- `stars` requires chart compatibility (a gift can correct it);
- `ember` spouse joins the larger clan;
- `hearth` allows divorce ("splitting the ashes") after sustained negative opinion.

Remarriage of widows comes after a culture-specific mourning period. Representation is limited to words and gestures: walking together at dusk, sitting at the tavern, holding hands at a wedding. Nothing explicit, ever. A config `romance=true` exists only for servers that want no courtship content at all.

### 7.4 Target: weddings and wedding tokens

A **wedding** is a calendar event (§15) scheduled for the next Fairday (Blossomtide preferred) at the faith's site:
- Kindlehall hearth-joining, noon blessing, net-binding on the quay, tree-vows and a marriage-tree planted (a real sapling near the ring-grove), ancestor-lot in the hall, ember oath.
- Guests (opinion > 30) gather if embodied. The feast consumes ale, bread and meals from the ledger. Mood goes up and there is news.

**Wedding tokens** are the in-world marriage contract. Each wedding mints a pair recorded on both Persons.
- Players obtain tokens from a temple and give one to a couple before the wedding to be named as a witness, which brings reputation and an invitation.
- **Player marriage** (§7.10): a player may court an NPC whose opinion of them is 70 or above. The proposal uses a token. The NPC spouse joins the player's household and settlement, and has their own opinions, needs and possible refusal.
- **Player-player marriage** is ceremonial: both hold tokens at a temple on a Fairday, and the result is recorded in the hearth-rolls.

### 7.5 Target: households

`Household {id, head, members, home, coins, foodShare, possessions, faith, ancestors[]}`.

| Role | Effect |
|---|---|
| Tax unit | hearth-tax |
| Inheritance unit | estate |
| Mover | emigration moves the household, as v0.1 already moves families |
| Spender | comfort basket |
| Shared memory | feuds, ancestors |

Household tasks: cook, tend graves of kin (§11), keep the hearth on the Lantern Vigil, mind children. Orphans are taken in by kin, then by the faith's institution (Ember-children raised by the clan, Hearth almshouses, Clan Purse), then by the settlement. Adoption also opens parenthood to same-sex couples and to players.

### 7.6 Target: children and education

- **Age stages:** infant (0 to 3), child (3 to 10), youth (10 to adult), adult, elder (over 75% of lifespan).
- **Children** play (v0.1), do small chores (fetching, minding geese), and attend school if one exists. School gives literacy (a trait) and apprentice skill rate (+0.3 in v0.1). Lantern Schools (`stars` charity) teach letters free.
- **Youths** apprentice (v0.1). Omen-born children (stage 1 to 4, bible 13.9) are watched by Watchers and courted by the Choir.

### 7.7 Target: ageing and generational pacing

- `daysPerYear` stays configurable (D8 default 8). The world creation screen exposes three presets: Saga (8), Chronicle (16) and Lifetime (32, the bible's 4 days per month).
- Visual ageing:
  - grey hair (v0.1);
  - stoop and slower speed for elders;
  - children scale (v0.1 `SCALE`).
- Elders retire to advising and teaching, give prestige to their household, and hold the Long Remembering (eldrin).

### 7.8 Target: inheritance, funerals, graves and memorials

- **Estates** follow §5.7. Disputes between heirs become grudges or trials.
- **Funerals** are calendar events within 1 day of death:
  - a procession from home to burial site if embodied;
  - the rite per faith from bible section 5 (Last Candle, Sun-sealing, Giving to the Mother, tree-burial, stone-sealing, open-sky, pyre);
  - candles consumed, mourners' memories recorded.
- **Grave templates per faith** follow the bible (mound plus headstone plus candle, east-facing sandstone tomb, salt-cairn, sapling on podzol, sealed niche, sky bier, cremation ring). They become small blueprints in the graveyard (or in an ancestor hall for `ancestors`, or at the Drop for `tide`), replacing the single gravestone block.
- `GravestoneBlockEntity` gains `deathDay`, `lastTended` and `unremembered` (bible 13.1). Abstract graves (a person buried while unembodied) are recorded as `GraveRecord`s and placed physically when materialized. In v0.1 an unembodied death waits unburied until a player visits.
- **Memorials.** War memorials list the fallen (a sign wall from battle records), Dunmar name-stones, and family crypts for noble houses.

### 7.9 Target: family names, ancestry and hearth-rolls

- `NameSet` styles stay. Add name-returning (`ancestors` families reuse a dead grandparent's given name), Eldrin naming nights (the name is chosen at the first Blossomtide), and Ashborn earned adult names (rename after a deed: First Blood, battle).
- **Hearth-rolls** are the genealogy UI: a family tree screen from Persons plus archive records, with births, marriages and deaths. This is the in-world census for the hearth-tax (bible 13.13).

### 7.10 Target: players as Persons and dynasty mode

**Players as Persons.** On first login a Person record with `isPlayer = true` is created and linked to the player UUID. v0.1 already stores the player UUID in NPC `relations`. The record holds culture (chosen at first login from 8, cosmetic and social only), faith, titles, household, spouse, children, citizenships and reputation. It is excluded from abstract AI. This one change unifies marriage, inheritance, titles and dynasty.

**Dynasty mode**, strictly separated from hardcore:
- **Strict hardcore** (default, vanilla `hardcore=true`): player death ends the world as vanilla does. Dynasty mode cannot be enabled, and the option is greyed out with the reason.
- **Dynasty** (world option, requires `hardcore=false`, recorded as an immutable world flag `longnight:dynasty` at creation): on death, the death screen offers "Continue as heir".
  - The candidates are adult children, the spouse, and sworn retainers with opinion 50 or above.
  - The chosen heir's Person becomes the player's persona and the old persona dies (a real Person death: funeral, grave, estate).
  - The player respawns at the heir's home with the heir's possessions. The estate passes by law, and the dropped inventory is recoverable as normal.
  - Reputation is inherited at 50%. Titles follow succession law. A "Dynasty" badge shows in the journal and the realm screen.
- The flag cannot be changed after creation, so a hardcore world cannot be turned into a dynasty world by editing config. Tests check this.

### 7.11 Data model changes

- `Person`: `household`, `isPlayer`, `playerId`, `betrothed`, `marriageToken`, `stage`.
- New: `Household`, `GraveRecord`, `PersonaLink {player -> person}`.
- Grave block entity: new fields as listed in §7.8.

### 7.12 Performance

- Social interactions are O(n x talks) per settlement per day, cheap.
- Picking a workmate contact (`pickContact`) scans the residents list, O(n²) worst case. Index residents by workplace once per day.
- The dead move to the archive (§17.4).

### 7.13 Failure modes and tests

| Failure | Mitigation | Test |
|---|---|---|
| Incest or age-gap marriages | `related()` extended to grandparents, aunts, uncles and first cousins | Unit: generate 5 generations, assert no marriage within the forbidden degrees |
| Population explosion or extinction | Birth chance tied to food and housing (v0.1), emigration, founding of new camps | 500-day soak: population per settlement within bounds |
| Dynasty enabled on a hardcore world | Immutable flag, startup check | Unit: hardcore plus dynasty config, assert refusal |
| Orphans stranded | Guardian chain | Unit: kill both parents, assert a guardian within 1 day |

---
## 8. Cultures and peoples

**(a) v0.1.** Eight culture JSONs (`data/longnight_cultures/*.json`, parsed by `content/Culture`) define:
- people type and body scale (dwarf 0.82 and so on), lifespan (45 to 260);
- faith weights, government options, biome keywords;
- body, hair and cloth variants for the layered `CitizenRenderer`;
- a block palette by role (foundation, wall, roof, window, path, crop, sapling...), roof style, temperament means, banner colours.

`data/names/*.json` holds given, family, settlement, camp, realm and epithet names, and the family style. Mixed settlements arise from 12 to 18% foreign settlers and immigrants.

**(b) Target.** Culture data grows to carry everything bible sections 4 and 11.2 specify, so that cultures differ in *how they live*, not only in how they look:
- Layout grammar (§4.3) and building variants per type: Dunmar "hall" taverns, Ashborn long-halls.
- Cuisine basket mapped to commodities and FD items (§3.3).
- Customs as calendar events (§15) with culture filters.
- Unit roster (§6.3), law defaults and offices (§5.2/5.3), succession law (§5.7), preferred punishments.
- Speech style tags for `Dialogue` (formal or plain, humour weight) and rumour template variants.
- Interest groups (the bible's rifts: Old Blood and New Coin, Returners and Keepers, Red and Cold Ember, Dimming and Waking Houses). These are persistent `Faction`-lite blocs inside realms with membership from temperament and profession. They push agendas, petitions and, at worst, civil war.
- **Culture drift.** Children of mixed marriages take one parent's culture (v0.1), with bilingual and bicultural traits. Long-resident foreigners slowly adopt customs.

**(c) to (f).**
- Data: culture JSON schema v2, with `layout`, `cuisine`, `roster`, `laws`, `offices`, `succession`, `speech` and `interest_groups` keys. Old keys stay valid.
- Performance: none at runtime beyond lookups.
- Connects to: nearly every system as a parameter source.
- Failure mode: data drift from the bible. v0.1 faith JSONs already diverge (for example hearth clergy is "Hearth-Warden" and the festival "Kindling Night" in month 6, where the bible says Flamekeepers, Rekindling and the Lantern Vigil). Test: a data-lint unit test cross-checks ids and required keys. A canon table (`data/longnight/canon_check.json`) lists the bible names the data must contain.

---

## 9. Religions and institutions

**(a) v0.1.**
- Eight faith JSONs (`content/Faith`): name, adherent and clergy titles, burial type, burial text, festival name and month (unused), shrine block, `strictness` (used in courtship and conversion), `charity` (unused).
- Persons have one faith. Conversion happens only at marriage. Shrines and temples are built (`Planner.next` at pop 6 and later), named by the dominant faith, and staffed by priests whose work only raises their own skill.

**(b) Target.**
- **Institutions.** Each faith has a clergy hierarchy (bible section 5: Flamekeepers and Kindlers; Radiant, Sun-Readers and Sunjudges; Tide-Wives...) as offices on temples. A temple has a treasury funded by tithes (a realm law sets the rate) and alms. It runs:
  - **charity**: almshouse, Bowl-Right, Waterhouse, Widows' Net, Clan Purse. These feed the poor from the temple's stock, reduce Choir recruitment and raise faith legitimacy;
  - **records**: hearth-rolls;
  - **schools**: Lantern Schools.
- **Worship need** (§2.2) is satisfied by attending the shrine on holy days and keeping household rites (the candle niche).
- **Festivals** come from the bible calendar (§6 table) as `CalendarEvent` data (§15).
- **Pilgrimages** are a Person life goal and a player contract: Hearth of Hearths, Glass Plain at noon, Weeping Reef, Thousand-Year Wood, Gate of Varnkhar, Broken Lantern, Mouth of Fire. Pilgrims travel roads (caravan-like records) and return with prestige and a token item (a coal from the Hearth). Players completing one gain reputation with that faith's settlements plus a rare-layer lore book.
- **Conversion dynamics.** Yearly conversion weights move with marriage, charity received, Long Night stage (bible 13.15) and preaching (priest count). Each person's piety damps conversion.
- **Schisms and sects** (Ashen Reform, Brands, Twin-Dawners, Burning Mirror, Hungry Tide, Thornbound, Rotkin, Forgetters, Unbroken Line, New Kindlers, Ashmaw) are sect records within a faith. Each has doctrine flags such as `witch_hunting`, `refuse_mixed_marriage` or `crusade_hollow`, and a membership share per settlement. A sect above 30% in a realm pushes laws and agendas. The Brands produce witch-hunt events (trials of hearthcraft practitioners, some innocent). The Burning Mirror pushes crusade war goals in stage 2 and later.
- **Faith-not-a-buff rule.** No faith grants combat stats. Effects are social, economic and legal only. A data lint rejects any faith modifier whose target is attack, armour or health.

**(c) to (f).**
- Data: `Faith` v2 with festivals[], clergy offices, charity type, burial template id, marriage rule, sects[]. New `Temple` state on buildings. `Sect` records.
- Performance: per-settlement daily aggregation only.
- Connects to: personal lives (marriage, funerals), Hush (graves, charity), politics (confirmation, tolerance), calendar.
- Failure: faith monoculture collapse or oscillation. Test with a 300-day soak on faith share time series. The faith-buff lint also runs as a test.

---

## 10. Magic traditions

**(a) v0.1.**
- **Hearth-witchery wards** (`game/Wards`): right-clicking a ward stone with ward chalk kindles it. For 3 days natural hostile spawns are cancelled within 24 blocks. Materialized settlements also cancel 85% of natural hostile spawns inside radius + 8 (`Wards.blocks`, via `FinalizeSpawnEvent`).
- `night_shard`, `star_chart` and `ember_relic` are items without behaviour. The first two appear in ruin chests.
- **Ars Nouveau** 5.13.3 is in the pack as the player's arcane path, with no integration.

**(b) Target.** Magic is real, rare, costly and social (bible section 7). Two layers:
1. **Ars Nouveau is `star_wizardry`, the scholastic arcana of the Lantern Colleges.** Integration is soft (no compile dependency; registry lookups by id behind `ModList.isLoaded("ars_nouveau")`):
   - A **Lantern College** (a library at TOWN+ tier with scholars of the `stars` tradition) sells or teaches AN entry items. Which item ids to use must be verified against the AN jar; do not guess. Scholars grant access by reputation and fee.
   - **Social cost.** Casting is visible. In cultures where sorcery is suspect (Aszari after dark, Dunmar "loose magic", Ashborn "cold fire"), witnesses who see a player cast nearby lower reputation, using an AN spell-cast event if the API exposes one; verify. In the Long Night, star-sickness (insomnia that draws phantoms) applies to heavy casters via a longnight counter.
   - **Lore.** Lantern Colleges hold rare and forbidden books, and the Locked Gallery holds the Black Charts.
   - NPC battle mages do **not** cast AN spells. They use longnight's own small effect set (§6.3) to avoid coupling NPC AI to another mod's spell engine.
2. **longnight's own traditions**, each with practitioners (profession plus tradition id), teachers (apprenticeship), costs and reputation. Small, deterministic effects that plug into existing systems:

| Tradition | Player-facing actions | NPC effects | Costs and social |
|---|---|---|---|
| `hearthcraft` | Ward stones (v0.1), threshold wards on doors (vampires, curses), calming nightmares (phantom pressure down) | Healing and birth bonuses, finding lost goods | Witch "ages early" (lifespan -), tied to one hearth, witch-hunts in bad years |
| `runesmithing` | Rune-carved blocks: permanent ward, sealing rune (gate-scars), keen/unbreaking marks on gear via smithing | Dunmar holds' rune-wards (iron golems as `rune-ward` bodies) | Star-iron, months of work, "forgetfulness" (memory loss) for the smith, treason to sell abroad |
| `druidry` | Bless seed (blight-resistant farm for a season), cleanse Rotwood markers, weather plea (rain) | Grovewardens cure blight in settlements, judge boundaries | "Seasons owed": replanting duties, fasts, weak in cities |
| `ember_shamanism` | Oath-binding (contract breach becomes a curse), fire-walk trial (Ember Code) | War-songs: morale, not buffs; foretelling (warning rung) | Possession risk, must stay near the clan fire |
| `alchemy` | Vanilla brewing, plus longnight medicines (healer stock), firepots | Healers consume medicine | Burns, addiction to gilded elixirs |
| `star_reading` | Read star charts: forecast of the Night stage, the Hush level band and wyrm stirrings (warning rungs) | Watchers warn courts (agenda shift) | Insomnia, Sun-faith hostility |
| `tide_singing` | Fog guidance, find the drowned (lost items at sea) | Fishing yield, storm warnings for sea lanes | "The sea takes a tithe" |
| `sparkwork` | Skrell trap kits (redstone templates) | Skrell settlements build working traps | Explodes, called witchcraft |

**(c) to (f).**
- Data: `data/longnight/traditions/*.json` (teachers, costs, effects). Person `tradition`, `traditionRank`. Block entities for rune blocks.
- Performance: effects are event-driven (spawn checks, door use, daily aggregates). Ward lookup moves from a linear scan of all wards per spawn (v0.1) to a chunk-indexed map.
- Connects to: dark powers (countermeasures), warfare (morale), economy (medicine, runes as luxury), faith (witch-hunts).
- Failure: magic becomes a combat power curve. Mitigation: longnight magic never adds direct damage. Test: a lint over tradition effects.

---

## 11. Dark powers

**(a) v0.1.** Only lore books, the `blighted_wheat` item (unused) and the blight harvest factor (§13). No Hush meter, Choir, undead raids or curses.

**(b) Target.** Each power is a small state machine with a cost, signs and countermeasures (bible section 8). The visible signs come first.

| Power | State | Signs (ambient cue + rumour) | Countermeasures (player and NPC) |
|---|---|---|---|
| **Hush** (world meter 0 to 100) | Rises with unremembered graves, famine deaths, hidden cells, open gate-scars, hush-moss. Falls with tended graves, fed refugees, exposed cells, cleansed Rotwood | Star-readers' forecast band, "nobody dreams" rumours | All of the below |
| **Unremembered graves** | Grave `lastTended` older than 1 year | Unlit candles, cold spots (particles), dogs whining | Kin tend graves (a household task), players relight candles (+reputation), priests' rites |
| **Necromancy / Choir cells** | A cell is a hidden set of members (`Person.secret = veiled_choir`) in a settlement. Grows on the Quiet Table in famine | Black candles bought in bulk (ledger anomaly visible to stewards), missing bodies, hush-moss out of place, villagers who stop dreaming | Feed the hungry, gather evidence (witness memories, items), trial (§5.6) |
| **Undead raids** (stage 2+) | Settlements with unremembered graves spawn risen dead from those graves at night | Disturbed earth at grave blocks | Tend graves, burn bodies (cremation rites), walls and guards |
| **Curses** | Grudge-holder with low kindness, threshold item placed (black thread) | Soured milk (food spoils faster), small accidents (minor wounds) | Threshold wards, reconciliation (grudge resolved), burning the thread at a holy fire |
| **Haunted objects** | Created by untried murders and deaths without rites (bible 13.12) | The item is cold and whispers (tooltip, sound), returns to one place | Return it to the grave, solve the crime, priest rite |
| **Vampires** (the Thirsting) | A Person secret. Feeds at night on sleepers (health and rest drain) | Pale sleepers, a village that does not dream | Kindled thresholds, daylight, unmasking in court |
| **Liches** | Choirmaster record with a soul-vessel location (e.g. a child's grave) | Land where nothing rots or grows (crop growth 0 in an area) | Find and destroy the vessel (quest chain), then the body |
| **Demons** | Gate-scar (ruined portal) opened by a rite | Hand-shaped scorch marks, soul sand in the overworld, gold vanishing from the ledger | Close the scar (runesmith seal), ember-shaman bargain |
| **Rotwood** | Corruption markers at chunk granularity (abstract). Applied as block changes (grey leaves, cobwebs, fungus) only when loaded, and only to natural blocks | Grey leaves, silent birds, looping paths | Grovewarden cleansing, burn the heart-tree, replant blessed seed |
| **Blight** | Per-settlement infection 0 to 1, spread by caravans carrying grain and by adjacency. Embodied: `blighted_wheat` crop stage replaces wheat on infected farms | Grey ears in fields, grain price spikes, `shortage` rumours | Burn infected fields, quarantine grain (law), rotate crops, druid-blessed seed, open the Common Pot |
| **Nightmares** | Phantom pressure per player and settlement from sleeplessness (vanilla insomnia stat plus NPCs without beds) | Phantoms, the same-door dream | Sleep law, hearth-charms, finding the source (a Choir cell) |
| **Shadow magic, soul bargains** | Antagonist-only abilities of cell leaders | Lanterns guttering, no reflection | Many lights, destroy contract tokens |

**(c) to (f).**
- Data: `WorldSim.hush`, `Settlement.blight`, `Settlement.cells[]`, `GraveRecord.lastTended`, `CorruptionMap {chunkPos -> level}`, `Person.secret`. Data files: `dark_powers/*.json` (sign thresholds, rates).
- Performance: all daily and abstract. Block-level signs are only applied in T2 and capped at 8 block changes per settlement per second. Rotwood is capped at N chunks per region.
- Connects to: faith (rites, charity), politics (trials, laws), economy (grain prices, candles), Long Night (Hush to stage 4), exploration (choir crypts).
- Failure: punishing players for absent-minded play, e.g. graves going unremembered while the player is away. Mitigation: NPC kin tend graves by default; only extinct families' and war-dead graves lapse. Signs come weeks before effects. Test: a 400-day soak with no player, where Hush stays below 40 in stages 0 to 2.

---

## 12. Dragons and great beasts

**(a) v0.1.** None. No dragon mod is in the pack (MODS.md rejects IceAndFire CE and Dragon Mounts). The `dragon_sighting` rumour templates exist but nothing emits them. The bible's `bestiary_wyrms` book exists.

**(b) Target.** Wyrms are longnight entities and records.
- **Wyrm record** `{id, name, kind (cinder/rime/mire/dune/sea), lair, territory radius, state, hunger, hoard value, sleep timer, wounds, grievances[]}`.
  - States: DORMANT, STIRRING, HUNTING, SATED, WOUNDED, DEAD.
  - Five named great wyrms from bible section 9.1. They spawn as records at worldgen only if their biome exists within `worldRadius x 2`. Otherwise they lie in unexplored land "beyond the map".
- **Warning signs** (fairness invariant §6.8): STIRRING begins at least 5 days before any attack, with escalating signs inside the territory:
  1. `dragon_sighting` rumours;
  2. world cues in T2 (hot springs bubbling, frozen water in summer, fleeing animals: herds spawned moving away, smoke from old shafts);
  3. livestock losses from settlement ledgers;
  4. a sighting overhead (a flyover entity with no attack).
- **Choices** for realms and players: tribute (a tribute stone block; leave N livestock on it; the hunt is skipped), a **Wyrmhunt** (a contract, or a realm war-like expedition of NPC hunters plus players), or evacuation (households leave). Paying tribute costs legitimacy ("paying looks weak"). A slain wyrm's hoard is a casus belli. The slayer gains a succession claim.
- **Attack.**
  - Abstract: damage masks (fire on roofs), livestock and food taken, deaths.
  - Embodied: the entity makes strafing passes on settlement buildings using our building data to target roofs. Fire is placed only on flammable blocks of settlement buildings and natural vegetation, never inside claims. Players can be targeted anywhere.
  - Config `wyrmBlockDamage`: `settlement_only` (default), `none`, `full`.
- **Entity tech.** A multipart entity (vanilla EnderDragon-style parts) with a custom flight controller: no navigation, a waypoint spline plus a dive state machine.
  - Animated models: GeckoLib 4.9.3 is already in the pack as an Ars Nouveau dependency. Declaring a hard dependency on it is acceptable but couples our release cadence to it. The alternative is a hand-written `HierarchicalModel` with code animation. Decide at prototype time, measured by FPS on llvmpipe.
  - Health scales so that a well-prepared party (ballistae as `GameTest`-able blocks, many archers) can win.
- **Lairs.** The lair is generated on first approach by longnight (a carved cave or ruin variant plus a hoard chest from the ruin loot system), placed only on natural blocks. It is a `Site` (§14).
- **Lesser drakes** (fen, sky and fire drakes) are ordinary hostile mobs with spawn rules by biome and Night stage. They are the everyday dragon problem: they take sheep, burn ricks and appear as caravan ambushers.
- **Other great beasts**, each with a sign and a rule:
  - Stonebacks: cellar break-ins from below at night; signs are cracks in cellar floors.
  - Duskhounds: winter and stage 4 packs; signs are daytime howls.
  - Weavers: Rotwood spiders; signs are webbed paths.
  - Cairnwalkers: hill-giants, neutral unless cairns are disturbed.

**(c) to (f).**
- Data: `WorldSim.wyrms`, `data/longnight/wyrms/*.json`, a tribute stone block, wyrm and drake entities.
- Performance: one wyrm entity at most, embodied only within range. Records are daily.
- Connects to: economy (livestock prices, bible 12.3), politics (claims, hoard wars), warfare (battle engine for hunts), exploration (lairs).
- Failure: terrain griefing, unfair deaths, server lag from flight AI.
- Tests:
  - GameTest: fire placement never touches player blocks or claims.
  - Unit: no attack without at least 5 days of signs.
  - Benchmark: wyrm fight at 20 TPS on 4 cores.

---

## 13. The Long Night arc

**(a) v0.1** (`sim/LongNightArc`):
- `nightPressure` rises by 0.005 a day, capped at 0.40 until the Ender Dragon dies. The cap means stage 2 is the maximum without the kill (D9).
- Stage thresholds are 0.15, 0.35, 0.6, 0.85 and 1.0. Stage 1 arrives around day 30, stage 2 around day 70.
- `onDragonSlain` (from `LivingDeathEvent` on `EnderDragon`) adds 0.2 and sets the rate to 0.012 a day. Pressure jumps from 0.40 to 0.60, so stage 3 starts the next day; stage 4 follows about 3 weeks later and stage 5 about 2 weeks after that.
- Effects:
  - an omen news item per stage, known everywhere;
  - a blight harvest multiplier (1.0, 0.88, 0.75, 0.6);
  - realms switch to the FORTIFY agenda at stage 3.
- **Mismatches with the bible.** The code's stage 5 means "the Night has come", but the bible's stage 4 is the Long Night and stage 5 is Resolution. The bible's triggers are not used: first Eye thrown, portal lit, Hush 100, 20 days without Rekindling, dragon respawn.

**(b) Target.** The arc becomes an explicit state machine matching bible section 10. Pressure stays as the "omen intensity" that scales ambient effects:

| Stage | Trigger (any) | Systems switched on (data: `data/longnight/night_stages/<n>.json`) |
|---|---|---|
| 0 Lit Years | start | rumours only |
| 1 Omens | day 30; first Seeker's Eye thrown (`EntityJoinLevelEvent` for an eye of ender) | falling-star particles on clear nights, more Hollowed, `star_prophecy` uncommon, omen-born children, first blight seeds, Choir recruitment on |
| 2 Waning | End portal lit (portal block placement event); day 120 | nights about 15% longer (§13.1 below), slower crops in cold biomes, blight spreads via trade, undead raids on settlements with unremembered graves, Quiet Table, refugees |
| 3 Breaking | Ender Dragon killed | Night of Falling Stars (3 nights of meteors, all settlements know within a day), Lanterns dark, Last Ember tracked, stronger undead, untended graves rise, Rotwood and hush-moss spread, sun-fervour spike then collapse, Choir surge |
| 4 Long Night | 20 days after the Breaking without Rekindling; Hush 100 during stage 3 | dim sun and fog tint, short days, crops grow only near light, Night Tides every few nights (mass undead assaults using the battle engine), Herald possible via Choir rite, Duskhounds, famine wars, Lantern League diplomacy |
| 5 Resolution | Rekindling (4 end crystals placed and the dragon respawned); Unmaking (Choir brings the Ember to a Lightless Door, a config-gated hard mode) | Afterdawn (stage-1 effects permanently, price event, Rekindler title); or Gloaming (harsher nights, Choir realms) |

**Faction responses** come from data (bible section 10.2 tables), as agenda overrides and actions per stage:
- `stars` and House Maervaine warn and beg restraint;
- the Burning Mirror preaches crusade (war goal: march on the Hollow);
- Dunmar Returners want star-iron;
- Valemen kings claim the High Crown when holding the Ember;
- Tidefolk try to buy it, skrell try to steal it, burrowfolk stockpile (food targets ×1.5).

**Last Ember tracking.** The vanilla dragon egg is a block and an item. Track the "last known holder or position" via block place/break events, item pickup and drop events, container interactions (best-effort) and the player inventory check at logout. AI factions act on the last known position (theft contracts, raids on the holding settlement, envoys). Possession grants the High Crown claim.

**Rekindling** is detected by the dragon respawn sequence completing (`EnderDragon` spawned by the respawn process; verify the hook). The **price** is applied by data: the oldest elder of the rekindling faction's capital dies (a funeral event), House Maervaine enters the Dimming, or the Dunmar lose star-iron stocks.

### 13.1 Day-length and sky effects (risky; behind config)

- **Longer nights.** During night ticks, skip the daytime increment every 7th tick (≈15%) by adjusting `ServerLevel.setDayTime` before the vanilla advance. Sleeping still skips the night.
- `WorldSim.dayOf()` is based on `dayTime`, so day counting is unaffected.
- **Risks.** Interactions with mods reading `dayTime` (Distant Horizons, Iris sky) and with `doDaylightCycle`. Disabled automatically if `doDaylightCycle=false`.
- **Dim sun.** A client fog and sky tint from a synced stage value via `ViewportEvent.ComputeFogColor` and `RenderFog`. Shaders (Iris and Complementary in the rich preset) may override it; document that rich-preset players see a weaker effect.

**(c) to (f).**
- Data: `WorldSim.nightStage`, `hush`, `breakingDay`, `emberHolder`, `emberLastPos`, `resolution` (matching the bible section 14 hook list); stage JSONs.
- Performance: the stage checks are daily. Night Tides reuse the capped battle engine.
- Connects to: everything (it is the campaign spine).
- Failure modes and tests:
  - Stage skipped or regressed. Unit: forced triggers walk 0 to 5, and time alone never exceeds 2 (D9).
  - Unwinnable Night Tides. Tides are capped against settlement defence like §6.8; test with a 30-day stage-4 soak.
  - A save from v0.1 at code-stage 3 to 5 maps to bible stages via a migrator.

---

## 14. Exploration and lore delivery

**(a) v0.1.**
- `WorldBuilder` makes 3 ruins of "lost kingdoms" (temple, watchtower, two houses, partially built then decayed; `Placer.decay` adds moss, holes and cracked bricks).
- Temples and watchtowers get a lore chest (`Loot.ruinChest`): two books weighted by rarity (forbidden books only in ruins), silver marks, maybe a night shard, ward chalk or star chart.
- 33 lore books in `data/lore/*.json` (12 common, 11 uncommon, 5 rare, 5 forbidden) and rumour templates.
- YUNG's structures carry no longnight lore. Abandoned settlements become `RUIN` but do not decay.
- Ruin decay re-runs after a restart (the in-memory `DECAYED` set), re-rolling chests.

**(b) Target.**
- **Sites.** A general `Site {id, kind, pos, culture, era, payload, materialized}`. Kinds: RUIN (bible 3.6 archetypes as `BlueprintGen` generators: `starwright_lens_tower`, `thaevalis_glade_ring`, `saramund_watchkeep`, `kingsway_milestone`, `legion_fort`, `choir_crypt`, `watcher_cell`, `varnkhar_gate`...), BATTLEFIELD, LOST_ROAD, LAIR, SHRINE, GRAVE. Materialized once when within 96 blocks, on natural blocks only, and persisted.
- **Battlefields from real history.** Every abstract battle (§6.5) leaves a BATTLEFIELD site: bones, rusted weapons in item frames, banners in faction colours, a cairn with the fallen's names. Pre-history battlefields (Red Ford, Cogsmire, Field of Lanterns) come from worldgen.
- **Lost roads.** Kingsway fragments are generated as old road polylines between ruin sites, with milestones naming lost places ("IX leagues to Ostrevane"). Following them leads to ruins.
- **Layered revelation per player** (`PlayerKnowledge`): books read (detected when a written book with a longnight lore id component is opened), layer reached per topic (common to forbidden), rumours heard.
  - NPC dialogue options unlock with knowledge. A scholar comments on a book you carry. A Flamekeeper reacts with alarm to a forbidden text, which raises Brands suspicion.
  - The journal gets a "What I know" page that lists contradictions without resolving them (bible 10.3: never confirm that the Dragon is Nahire).
- **Loot injection.** A NeoForge global loot modifier adds longnight books to YUNG's dungeons, strongholds ("Sealed Keeps") and desert temples by rarity rules (forbidden only in crypts and strongholds), plus Terralith-compatible structures. Loot is persisted by vanilla chest rules (no re-roll).
- **Rumour-to-map.** Rumours that reference a site add an approximate marker (±64 blocks) to the journal and map overlay ("the old tower past the Grey Hills").

**(c) to (f).**
- Data: `WorldSim.sites`, `PlayerKnowledge` per player, a lore book id data component, `data/longnight/sites/*.json`, loot modifier JSONs.
- Performance: sites are T2-only placements, budgeted like construction.
- Connects to: magic (forbidden texts), dark powers (crypts), Long Night (four Lanterns knowledge), wyrms (lairs), warfare (battlefields).
- Failure: re-rolled loot, sites placed over player builds, spoilers. Tests:
  - GameTest: a site is placed once and persists across reload.
  - Unit: forbidden books never appear in a common loot table.

---

## 15. Daily life and routines

**(a) v0.1.**
- `CitizenBrain.decide` fixes the schedule: work 1000 to 11000, social at dawn and dusk (tavern, well, market, temple, campsite), sleep 12600 to 23400 in their own bed (beds are assigned by index), fighters split between patrol and sleep at night, children play.
- `Calendar` has 8 months (Brightwake to Deepnight) with a `harvestFactor` per month. One in-game day is one month (D8), so the bible's "Fairday = day 1, Moot-day = last day" cannot be expressed.
- `Calendar.year` offsets the year by 412, but the bible opens at about 2,317 AB.
- Contracts post fuel needs in Leaffall and Dimming. There are no festivals, markets or events.

**(b) Target.**
- **Calendar engine** (`sim/calendar`):
  - `daysPerMonth = daysPerYear / 8`, at least 1.
  - Fairday is the first day of each month, Moot-day the last. When `daysPerMonth == 1` they share the day: the market in the morning phase, the moot at dusk.
  - Fix the year offset to match the bible (start year 2317 AB, with the epoch named "After Binding").
  - Day phases: dawn, morning, midday, afternoon, dusk, night.
- **CalendarEvent data** (`data/longnight/events/*.json`): `{id, filter (faith/culture/tier), when (month, fairday/mootday/specific), phases, effects (consume goods, needs, opinions, prices), choreography (gather building type, decor set, roles), news template}`. Events include:
  - **Fairday market**: visiting merchants' stalls (temporary decor), household purchases, hiring fair (job changes), player stall rental.
  - **Moot-day**: petitions, trials, laws (§5).
  - **Harvest festival** (Last Sheaf): feast consumption, a sheaf-figure decor kept until spring, mood up.
  - **Winter preparation** in Leaffall and Dimming: planner goals for fuel, salting and repairs, plus fuel and salt contracts (v0.1 posts fuel).
  - **Grand Tourney** (Highsun, capitals): archery (temporary target blocks; NPC entrants by skill; players compete for prize and prestige) and blunted bouts. In hardcore, bouts end at yield or 50% health via a sanctioned-bout damage handler, and are opt-in by the player.
  - **Performances**: bards at the tavern on Fairday evenings (mood, news spread).
  - **Holy days** per the bible table (Rekindling, Dawnfeast, Blessing of the Boats, Hall Night, Lantern Vigil with a lantern in every window as temporary decor...).
  - **Family gatherings**: household dinners on holy days, the Long Remembering, Hall Night vigils.
- **Temporary decor registry.** Every block placed for an event is recorded `{pos, expected state, event id}` and removed when the event ends, but only if the block is still the expected state. It is never placed except on air. Persisted, so a crash mid-festival cleans up on load.
- **Routines** move from the fixed schedule to per-person schedules from profession, culture (sun law: trade stops at dusk; burrowfolk four meals) and events. They are executed by the planner (§2.4) as goals with time windows.

**(c) to (f).**
- Data: `Calendar` v2, `CalendarEvent` defs, `Settlement.activeEvents`, `TempDecor` list.
- Performance: events evaluate at phase boundaries. Choreography only runs in T3 and is capped by body caps.
- Connects to: economy (markets, consumption), faith, politics (moots), personal lives (weddings, funerals).
- Failure: decor left behind or overwriting player blocks. Test: GameTest places an event, a player block is placed in between, end of event leaves the player block alone and removes the rest.

---

## 16. Atmosphere and presentation

**(a) v0.1.**
- UI is chat-based (D7). `game/Ui` prints settlement, market, notices, people, chronicle and person views, with `[button]` click events running `/longnight ...` commands.
- `game/Journal` is opened by the ledger item. Hearthstone, storehouse and notice board blocks open views.
- `ledger_background.png` and `logo.png` exist but are unused.
- `CitizenRenderer` draws layered player-model textures (body, clothes, hair, armour, tinted). Signs name buildings.
- The raid horn plays. Ambience comes from the pack (AmbientSounds, Particle Rain; Sound Physics in rich).

**(b) Target.**
- **Networked screens.** Server-authoritative views and client presentation:
  - S2C snapshot payloads (`SettlementView`, `PersonView`, `RealmView`, `MarketView`, `ContractsView`) registered via `RegisterPayloadHandlersEvent`;
  - C2S intent payloads (`BuyRequest`, `AcceptContract`, `SetLaw`, `JudgeVerdict`) validated server-side for distance, permission, rate limit and current state;
  - chat UI kept as fallback (vanilla clients, tests, accessibility).

| Screen | Content |
|---|---|
| Ledger book (hearthstone, storehouse, item) | Tabs: Overview (tier, population, food days, morale, mood words), Stores (stock by class, prices and trends, buy/sell slots), Projects (buildings, progress, blockers, "why this building"), Contracts, Chronicle, Governance (player lords: laws, offices, petitions, trials) |
| Person sheet | Rendered portrait (entity in GUI), name, culture, faith, age, rank, skills, traits, needs, current activity and blocker, family tree link, relations (with reasons), recent memories |
| Hearth-rolls | Family tree across generations, including archive records |
| Realm map overlay | Known settlements, roads, realms coloured by banner, armies and caravans sighted, rumour markers. Drawn from `knownPlaces` and sightings, never the true world state |
| Notice board | News, needs, contracts with accept buttons |
| Dialogue | Topic buttons plus history, with lines from `Dialogue` (optionally LLM-rephrased, §2.7) |
| Trade | Item slots both ways with live price, contract bonus shown |

- **Mod integrations (optional, soft):**
  - **Jade plugin**: Person name, profession and rank, activity or blocker for citizens; building name, progress and blocker on blueprint blocks.
  - **EMI plugin**: profession recipes as a category, commodity mapping.
  - **Xaero**: waypoints for known settlements and markers, via its API if one is exposed, else skipped. The in-mod map overlay is the fallback.
- **Audio.** Positional work sounds at active workplaces (vanilla anvil, saw, crop break). Market chatter on Fairday (vanilla villager ambience pitch-shifted, or licensed OGGs). Bells (temple bell block) for raids and funerals. Event music only from assets we have the rights to.
- **Visual presets** (pack-level):
  - Iris and Complementary must render citizens correctly (test emissive and translucency layers).
  - Distant Horizons LOD shows unmaterialized settlements as bare terrain. Option `prematerializeRadius`: materialize settlements within DH range at low budget (T2 without bodies), off in the performance preset.

**(c) to (f).**
- Data: payload records, client screen classes, lang keys (all text through `en_us.json` instead of literals, which v0.1 hard-codes).
- Performance: snapshots are built on request and capped at 32 KB per payload. Map data is sent incrementally.
- Connects to: every system's read model.
- Failure: client/server desync, information leaks (sending true world state to the client). Tests:
  - Unit: payload builders only use player-known data.
  - Headless client screenshot test (AutoShot extension) opens each screen.

---

## 17. Performance budget

### 17.1 Targets

Assume the player's PC is unknown and target the pack's 6 GB RAM, 4-core recommendation.

| Budget | Target | v0.1 status |
|---|---|---|
| Server tick (TPS 20) | longnight under 5 ms/tick on average, under 10 ms worst-case excluding catch-up placement | not measured |
| Daily rollover | sliced, at most 3 ms per tick | all systems in one tick; up to 10 catch-up days in one tick |
| Bodies | 160 total, 40 per settlement (config) | as config |
| Battle bodies | 24 per side, counted in the total | n/a |
| Path requests | at most 8 new paths per tick server-wide | unbounded |
| Block placement | 48 per tick catch-up (config), events and decor at most 16 per tick | catch-up only |
| Abstract cost | at most 0.2 ms per 50-person settlement per day | not measured |
| Save | autosave of longnight data under 50 ms; growth under 2 MB per 100 days at default scale | single blob, unbounded growth |

Scale assumptions: v0.1 generates about 200 people in 9 to 14 settlements. The target design must hold at 60 settlements and 3,000 living people.

### 17.2 Known v0.1 hotspots (fix list)

1. `NewsSystem.daily`: O(S² x K) traveller loop, and `newsById` is a linear scan over up to 600 items. Replace with an id-indexed map and a neighbour list per settlement.
2. `Economy.targets()` is recomputed per worker, per price and per caravan pair. Cache per settlement per day.
3. `Embodiment.buryDead` iterates every person ever born, for each active settlement every 200 ticks. Keep a per-settlement unburied queue.
4. `Wards.blocks` scans every ward per spawn attempt, and `settlementAt` is linear. Use a chunk-keyed index.
5. `WorldSim.daily` runs at once. Slice it (§1.2).
6. Full-world `setDirty()` rewrites. Shard the data (§1.2).

### 17.3 Spatial index

A `SpatialIndex` (64-block grid buckets) for settlements, sites, claims, wards, roads and armies, rebuilt on change. All `settlementAt` and `nearest` queries go through it.

### 17.4 Save growth and archiving the dead

- **Estimate.** A living Person's NBT is roughly 45 scalar tags (~1 KB), up to 32 relations (~1.6 KB) and 24 memories (~2.5 KB): about 5 KB uncompressed and about 1.5 KB gzipped. 3,000 living people come to about 15 MB uncompressed. The dead currently keep everything forever.
- **Archiving.** After a dead person's funeral and 2 in-game years, they move to the archive as an `ArchiveRecord` of about 250 bytes: id, names, dates, culture, faith, parents, spouse, children, cause, grave position, settlement, a top-3 deed summary. Relations and memories are dropped. Living people's relations to archived ids are pruned (a relation to the dead becomes a memory).
- Settlement and realm chronicles keep their caps (60 and 80 lines), but rolled-off lines are summarised into the archive per year ("Year 2321: 3 births, 2 deaths, the granary was completed").
- News is pruned at 600 (v0.1) and becomes an archive digest.

### 17.5 Identity across detail levels

The body is never authoritative. Sync points:

| Event | Body to record | Record to body |
|---|---|---|
| Spawn (activate) | | position = home or work mark or `lastKnownPos`; health, appearance, equipment from tiers; current plan step |
| Every 100 ticks (v0.1 mirrors health) | health, `lastKnownPos`, activity, completed actions | |
| Discard (deactivate, `personMoved`) | in-transit goods back to the ledger (or dropped), partial work cycles credited proportionally (v0.1 loses `cycleTicks`), reservations kept | |
| Death | `kill()` once (v0.1) | body discarded (v0.1) |

Invariant checks (`Invariants.check`, v0.1) extend to reservations and in-transit goods.

---

## 18. Mod integration ownership

| Domain | Owner | Data authority | Integration approach |
|---|---|---|---|
| All persons, families, NPC entities | longnight | `WorldSim.people` | No other NPC mod (D2). Vanilla villages disabled (D3: biome tag `has_structure` override plus structure set). Terralith fortified villages disabled (config). Supplementaries Red Merchant disabled (config). **Vanilla wandering trader still spawns in v0.1**: ship `doTraderSpawning=false` in pack defaults or cancel its spawn |
| Settlements, construction, roads, walls | longnight | `Settlement`, `Building` | Blocks from vanilla plus Macaw's (roofs, windows, doors, fences/walls, paths, bridges), Supplementaries (sacks, jars, signposts, flags, lanterns), Handcrafted (furniture) via palette fallback lists. Costs via `longnight:cost/*` block tags |
| Economy, prices, coin | longnight | `Stockpile`, treasuries | Items mapped by `commodities/*.json` (tags first). FD crops and meals via tags. Coins are longnight items |
| Food | Farmer's Delight (items, blocks, cooking) / longnight (accounting) | ledger units | FD crops on culture farms, FD cooking pot and stove as cook workstations, FD knives held by butchers; no compile dependency |
| Player arcane magic | Ars Nouveau | AN's own data | Lantern Colleges sell and teach entry, social reaction to casting (§10). NPCs never run AN spells |
| Folk magic, wards, runes | longnight | `wards`, traditions | own blocks and items |
| Hostile mobs | vanilla (+ longnight drakes, undead variants) | | Illagers are the Forsworn. Wards and settlements filter natural spawns (v0.1) |
| Dragons and great beasts | longnight | `wyrms` | own entities (GeckoLib optional) |
| World generation | Tectonic + Terralith (terrain), YUNG's (dungeons) | | longnight surveys noise (no chunk loads). Loot modifiers inject lore into YUNG's chests |
| Maps | Xaero's (player) / longnight (knowledge) | `knownPlaces`, sightings | optional waypoint export, own overlay |
| Tooltips | Jade | | longnight plugin |
| Recipes | EMI | | longnight plugin for profession recipes |
| Rendering | Sodium / Iris / DH | | test citizen renderer and fog under each |

---

## 19. Testing strategy

**Layers.**
1. **Unit tests** (`mod/src/test`, JUnit via ModDevGradle `unitTest`). These exist: content loads, consistent generation, atomic stockpile, pay-once construction, stops-and-explains, 60 days consistent, kill is final, save round-trip. Add:
   - property tests: random seeds × N days, then `Invariants.checkData` plus new invariants (reservations, in-transit, coin conservation);
   - golden-master determinism (same seed and same inputs give the same world hash after 30 days, needs per-system RNG §1.2);
   - data lints (chains complete, bible canon names present, no faith or magic combat buffs, forbidden-book placement);
   - migration fixtures (v0.1 saves load into each new `FORMAT`).
2. **GameTests** (`gametest/LongNightGameTests`, 6 exist: pay-once, never over player blocks, trade conservation, dead stay dead and buried, ward stops spawn, every blueprint generates). Add per milestone: hauler conservation, upgrade delta pay-once, damage and repair, wall gap at claims, temporary decor cleanup, ruin decay persistence, wyrm fire constraints, sanctioned bout non-lethal, GUI action validation.
3. **RCON server scenarios** (`tools/test/server_scenario.py`: boot, generate, observe, simulate, raid, validate, restart, compare). Add scenarios: 200-day soak with metrics CSV (population, coin per capita, prices, realm count, Hush), war fairness (player settlement plus forced war, asserting warning rungs), Long Night walk (forced triggers 0 to 5), multiplayer (two RCON-scripted fake players via `/player`-like test hooks).
4. **Headless client** (`tools/pack/client_test.py`, `client/AutoShot` under Xvfb with llvmpipe): screenshots of a settlement by day, street and night. Extend: open each GUI screen and screenshot it, render a citizen crowd with shaders on (rich preset), and use a pixel-diff baseline only for gross failures (missing textures: magenta).
5. **Performance benchmarks**: a headless sim benchmark (no MC server; 60 settlements, 3,000 people, 100 days; time per system per day) run in CI with thresholds, plus a server TPS probe during an observed settlement with 160 bodies.

**Per-system matrix.**

| System | Unit | GameTest | Server scenario | Client |
|---|---|---|---|---|
| People and planner | blockers for every goal, reservations, parity | builder replans, hauler death | 30-day observed vs unobserved output | Jade tooltip |
| Economy | chain lint, coin conservation, price smoothing | trade exploits | 200-day metrics | trade screen |
| Construction | re-baseline, upgrade delta | over-player-blocks, walls, repair | catch-up across chunks | screenshots |
| Politics and law | player realm not hijacked, trial evidence rules | wanted persists | petitions resolve | governance tab |
| Warfare | battle calibration, fairness invariants | embodied battle caps | war-fairness scenario | battle screenshot |
| Personal lives | kinship rules, guardians | funeral placement per faith | generations soak | hearth-rolls |
| Faith, Hush, dark powers | no combat buffs, Hush bounds | grave tending, decor cleanup | 400-day Hush soak | |
| Wyrms | sign lead time | fire constraints | wyrm attack scenario | wyrm render |
| Long Night | stage machine | | forced walk | fog and sky under Iris |

---

## 20. Glossary

- **Abstract (T1)**: the daily, chunk-independent simulation of every settlement.
- **AB / the Reckoning**: years After Binding (bible). v0.1's `Calendar.year` offset needs correcting to this.
- **Blocker**: a structured record of why an action cannot proceed, with remedies.
- **Blueprint step**: one block of a building plan. *Progress* counts paid steps, *placed* counts built steps.
- **Body**: a `CitizenEntity`, the temporary embodiment of a Person. Never saved.
- **Catch-up**: placing paid-for steps in the world when a settlement is next loaded.
- **Commodity / unit**: an abstract ledger good, with items converted to and from units.
- **Contract**: paid work posted on a notice board, generated from real needs.
- **Damage mask**: the set of a building's steps currently destroyed.
- **Dynasty mode**: a non-hardcore world option to continue as an heir after death.
- **Embodied (T3)**: near a player, people have bodies and act physically.
- **Fairday / Moot-day**: the first and last day of a month: market, and courts and councils.
- **Household**: a family unit that is the tax, inheritance and consumption unit.
- **Hush**: the hidden world meter of the Choir's and the Unlit's progress.
- **Ledger / Stockpile**: a settlement's authoritative goods record.
- **Materialize (T2)**: create a settlement's physical presence (hearth, buildings) on first approach.
- **Night Stage**: Long Night progression 0 to 5. *nightPressure* is v0.1's omen intensity.
- **Pay-once**: the invariant that each step, repair or upgrade is charged exactly once.
- **Persona**: the Person record a player currently inhabits.
- **Realm / Faction**: a polity holding settlements. *Agenda*: its ruler's current aim. *Stance*: its relation type to another realm.
- **Reservation**: units set aside for a plan so that nothing is spent twice.
- **Site**: a non-settlement place: ruin, battlefield, lost road, lair, shrine.
- **Warning ladder**: the required sequence of signs before any lethal threat reaches a player.

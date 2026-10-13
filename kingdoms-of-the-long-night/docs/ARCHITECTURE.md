# Architecture of the `longnight` core mod

NeoForge 21.1.259 for Minecraft 1.21.1, Java 21. Package root `com.longnight` in `mod/src/main/java`.

## One society, one owner

`longnight` owns every person in the world: identity, occupation, family, possessions (abstract), faction, memories
and history. No other mod in the pack adds NPC societies (MineColonies, Millenaire, Guard Villagers, Recruits,
Towns & Towers, MCA were rejected for this reason, see `modpack/MODS.md`), and vanilla villages are disabled
(`data/minecraft/worldgen/structure_set/villages.json` with frequency 0, plus emptied `has_structure/village_*`
biome tags; the mod loads after Terralith). Pillagers, zombies etc. remain as monsters.

## Two levels of detail

```
             +-------------------- WorldSim (SavedData "longnight_world", overworld) -------------------+
             | people, settlements(+buildings, ledgers, contracts), factions, wars, caravans, news,      |
             | claims, wards, spare sites, Long Night state, what each player knows                     |
             +---------------------------------------------------------------------------------------------+
  daily (every in-game day, all settlements, no chunks needed)            near players (every tick / second)
  Life -> Economy(+Caravans) -> Planner -> Threats -> Politics            Embodiment: materialize settlement,
  -> NewsSystem -> Growth -> Contracts -> LongNightArc                    spawn/discard CitizenEntity bodies,
                                                                         Placer.catchUp (paid steps -> blocks),
                                                                         RaidManager (dusk raids), bury the dead
```

* **Abstract level** (`sim/`): pure simulation over records. Chunk-independent and cheap (a 60-day run of ~250
  people takes < 1 s in the unit test). Runs once per in-game day; if days were skipped (sleeping, server off) it
  catches up at most `maxCatchUpDays`.
* **Embodied level** (`world/`, `entity/`): when a player is within `embodyDistance` (default 112) of a settlement,
  it is materialized and its living residents get a `CitizenEntity` body (caps per settlement and global). Bodies
  read and write the same records (activity, blocked reason, health, skills) and do their work through the same
  recipes and construction ledger as the abstract level.
* **Identity across levels**: a body stores only `personId`/`settlementId`. `CitizenEntity.shouldBeSaved()` is
  false, so bodies never persist in chunks; `Embodiment` keeps a person -> entity map and never spawns a second body
  or a body for the dead. Death always goes through `WorldSim.kill` (idempotent).
* **Work accounting**: embodied workers produce one of 8 daily "work cycles" per 1250 ticks at their station
  (`CitizenBrain.progressCycle -> Economy.work`) and each cycle is counted per person in
  `WorldSim.workedCyclesToday`; `Economy.daily` then works only each person's remaining cycles. Construction still
  uses a settlement-wide share: `Embodiment` counts work-hour ticks a settlement spent embodied and `Planner.daily`
  simulates the rest abstractly. No double production, no lost production (JUnit
  `bodiesWorkReplacesTheDailyWorkInsteadOfAddingToIt`).

## Key invariants (checked by `command/Invariants`, the GameTests and the server scenario)

1. Every resident exists, is alive, and points back to the settlement; nobody lives in two places.
2. No dead person is a resident; no dead person has a body; nobody has two bodies.
3. For every building: `0 <= placed <= progress <= total`. **A blueprint step is charged exactly once**, when
   `progress` advances (`Construction.advance`); placing paid steps in the world (`Placer.placeNext`) is free.
4. Ledger amounts and treasuries are never negative; `Stockpile.takeAll` is atomic.
5. Trades move goods and coins between player and settlement without creating either (`Trade`, tested).
6. Marriages are symmetric; every dead person has a date of death.

## Packages

| Package | Responsibility |
|---|---|
| `sim` | Records (`Person`, `Settlement`, `Building`, `Faction`, `NewsItem`, `Stockpile`, `Contracts`) and daily systems (`Life`, `Jobs`, `Economy`, `Caravans`, `Planner`, `Construction`, `Threats`, `Politics`, `NewsSystem`, `Growth`, `LongNightArc`), world creation (`WorldBuilder`), `Calendar`, `SimRules` (config snapshot), `WorldHooks` (interface to the physical world). |
| `world` | Physical side: `BlueprintGen` (parametric culture-styled buildings, deterministic per seed), `Blueprint`, `Materials` (block -> commodity cost; natural-block test), `Placer` (plot survey, foundations, step placement, ruins decay, graves, signs), `Places` (marks -> positions, beds, crops), `Trees` (natural tree detection), `Embodiment`, `RaidManager`, `SiteFinder` (noise survey), `Arrival`, `Loot` (written lore books), `ServerHooks`. |
| `entity` | `CitizenEntity` (layered-appearance body, combat roles, crime hooks), `CitizenBrain` (schedule and profession routines with stuck detection and explanations), goals. |
| `game` | Player-facing rules: `Ui` (chat interface), `Dialogue`, `Trade`, `TradeGoods`, `Coins`, `Founding`, `Claims`, `Crime`, `Wards`, `Journal`. |
| `content` | Data-driven content loaded from datapacks: `Culture`, `Faith`, `NameSet`, lore books, epitaphs, rumour templates. |
| `command` | `/longnight` (`/ln`): every chat button is a command; admin/test tools (`status`, `list`, `inspect`, `observe`, `validate`, `skip`, `raid`, `tp`, `history`, `charter`). |
| `block`, `item`, `registry` | Hearthstone, storehouse ledger, market stall, gravestone (+block entity), boundary stone, notice board, ward stone; coins, charter, journal and lore items; registrations. |
| `client` | `CitizenRenderer` (body + tinted clothes + tinted hair + armour layers, scaled), `ClientSetup`, `AutoShot` (dev-only visual test pilot, inert unless `-Dlongnight.autoshot=true`). |
| `gametest` | In-world GameTests (`./gradlew runGameTestServer`). |

## Data-driven content (`mod/src/main/resources/data/longnight/`)

| Folder | Format | Notes |
|---|---|---|
| `longnight_cultures/<id>.json` | name, people, scale, lifespan, faith weights, governments, biome keywords, body/hair options and colours, building `palette` (foundation, floor, wall, frame, roof, roof_slab, roof_block, window, door, light, fence, gate, path, bed, accent, chimney, crop, log, sapling), `roof_style` (gable, flat, turf), temperament means, banner colours | 8 cultures; add a file to add a people (it will be used for new settlements whose biome matches). |
| `longnight_faiths/<id>.json` | name, adherent, clergy, description, burial (burial, cremation, sea, tree, tomb, sky), burial_text, festival, festival_month, shrine_block, forbidden, strictness, charity | 8 faiths. |
| `names/<culture>.json` | given names by sex, family names, family_style (surname, patronymic, clan, house, place), epithets, settlement name parts, realm name patterns, camp names | written for the setting. |
| `lore/<id>.json` | title, author, rarity, cultures, tags, pages | 33 books; `lore/epitaphs.json` and `lore/rumours.json` are special. |

Everything reloads with `/reload`. Building types and professions are enums in code (`BuildingType`,
`Profession`) because they carry behaviour; recipes live in `Profession.recipes()`.

## Configuration (`serverconfig/longnight-server.toml` per world)

Pacing (`daysPerYear` 8, `adultAge` 16, `birthRate`, `deathRate`, `maxCatchUpDays`), world creation
(`startingSettlements` 7, `startingCamps` 2, `startingBanditCamps` 2, `worldRadius` 1200, `safeSpawnRadius` 160),
performance (`embodyDistance` 112, `maxBodiesPerSettlement` 40, `maxBodiesTotal` 160, `catchUpBlocksPerTick` 48),
threats (`raids`, `firstRaidDay` 5, `raidWarningDays` 2, `monstersHuntCitizens`, `wars`).

## Performance notes

* Daily simulation cost is linear in people with small constants; relationships are capped at 32 per person,
  memories at 24, news at 600 items, known news at 120 per settlement.
* Bodies exist only near players and are capped; long walks use waypoints (vanilla pathfinding range 48).
  Path requests are rationed (at most every 10 ticks when a path is used up, else every 60), because a citizen with
  an unreachable target used to re-run A* every tick. Bed positions are cached per blueprint.
* `/longnight admin perf` reports the mod's own server cost per tick (the world handler: simulation, embodiment,
  raids, roads, caravans; and the citizens' entity ticks) over the last 100 ticks, from `world/Perf`.
* Materializing a settlement places blocks at `catchUpBlocksPerTick` only while its chunks are loaded.
* Terrain survey at world creation (~15-20 s once) uses generator noise only; no chunks are generated.
* Save size grows with the dead (people records are kept for graves and family trees). Archiving the long dead into
  chronicles is planned (see ROADMAP).

## Extending

* New culture/faith/book/name set: add JSON (datapack or mod resources).
* New building: add a `BuildingType` constant, a generator in `BlueprintGen`, a planner rule in `Planner.next`,
  and professions in `Jobs.professionsFor`.
* New profession: add to `Profession` with a workplace and recipes; `CitizenBrain.work` falls back to "work at the
  station" automatically, so only physical behaviours need code.

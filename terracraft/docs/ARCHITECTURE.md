# TerraCraft architecture

Target: Minecraft Java 26.2, Forge 65.1.0 (EventBus 7, ForgeGradle 7), Java 25. One mod jar, no dependencies.

## Guiding rules

* **Server authoritative.** All gameplay state (life/mana upgrades, mana, accessories, progression, crafting,
  damage) lives and changes on the logical server; clients get mirrors through packets and only predict
  movement (double jump, water walking), which the server validates.
* **Data over code.** Items declare Terraria stats in one line; recipes, mining requirements, station
  membership, tags and vanilla overrides are datapack JSON; progression gates use one condition language.
* **No god objects.** Each subsystem registers itself; extension points (stat sources, progression
  listeners, command extensions) let later stages add behaviour without editing core classes.
* **Performance.** Stats are recomputed when equipment changes (and every 10 ticks), never per damage event;
  crafting-station scans run only while the crafting screen is open; projectiles keep per-target cooldown maps.

## Packages (`com.terracraft`)

| Package | Responsibility |
|---|---|
| `TerraCraft` | Entry point: config, registries, network, game-bus handlers, client init. `TerraCraft.id(path)`. |
| `core` | `CommonSetup` (registers stat sources), `GameEventHandlers` (registers every listener class), `ServerEvents` (commands, game rules on server start). |
| `config` | `TerraConfig` (COMMON gameplay + CLIENT visuals), `ConfigCondition` (`terracraft:config` JSON load condition). |
| `registry` | DeferredRegisters: `ModItems`/`ModBlocks` (registration DSL + creative-tab filing), `ModEntities`, `ModMenus`, `ModEffects`, `ModDataComponents`, `ModCreativeTabs`, `ModConditions`. `registry.content.*` holds the actual content tables (one line per item). |
| `progression` | `ProgressionFlag(s)` catalogue, `WorldProgression` (SavedData: flags, counters, world variants), `ProgressionManager` (only mutation API: listeners, announcements, sync), `ProgressionCondition` (JSON predicate language), `WorldVariants` (evil + ore pair choice from seed), `ProgressionView` (shared by server data and client mirror). |
| `player` | `TerraPlayerData` (capability: crystals, fruit, mana, accessory inventory), `TerraCapabilities`, `PlayerEvents` (login/clone/respawn/tick/sync), `HealthManager` (Terraria life -> MAX_HEALTH), `ManaManager` + `LifeRegenManager` (Terraria formulas at 3 Terraria ticks per MC tick), `AttributeEffects` (stats -> vanilla attributes), `StarterKit`. |
| `player.stats` | `Stat`, `Ability`, `StatModifier`, `StatEffects` (bundle used by armor/accessories/set bonuses/buffs), `PlayerStats` (computed), `StatCalculator` (pluggable `StatSource`s: `EquipmentStatSources`, `BuffStatSource`, `WellFedSource`). |
| `combat` | `DamageClass`, `TerraHit` + `TerraDamageSource` (hit metadata through vanilla damage), `TerraDamageTypes`, `DamageCalc` (variance, crits, defense), `TerrariaDifficulty`, `CombatEvents` (the damage pipeline), `HasTerrariaDefense`. |
| `entity.projectile` | `ProjectileKind` (data definition), `ProjectileKinds` (registry), `TerrariaProjectile` (one generic entity: gravity, drag, pierce, bounce, homing, boomerang, explosion, ignite/debuff, local immunity), `TargetRules`. |
| `item` | `TerraItemStats` (data component `terracraft:stats`), `TerraRarity`, base classes `TerraItem`/`TerraBlockItem`. Sub-packages: `weapon` (melee/ranged/magic/thrown archetypes, ammo, `UsableWeapon`, `WeaponFiring`), `tool`, `armor` (`ArmorSet`, `TerrariaArmorItem`), `accessory`, `consumable` (potions, permanent upgrades), `coin`, plus `DevTabletItem`. |
| `block` | `CraftingStationBlock`. |
| `crafting` | `TerraRecipe` (JSON model), `TerraRecipeManager` (server load, client copy, validated crafting), `CraftingStations` (tag-based stations + reach scan), `CraftingLogic`. |
| `mining` | `MiningPower` (data-driven requirements, synced table), `ToolPowers`, `MiningEvents`, `MiningTags`. |
| `economy` | `Coins` (denominations, conversion, formatting), `EconomyEvents` (drops, compaction, softcore death). |
| `effect` | `TerraBuffEffect` (MobEffect carrying `StatEffects`). |
| `menu` | `AccessoryMenu`. |
| `world` | `VanillaSuppression`, `TerrariaLayer` (Space/Surface/Underground/Cavern/Underworld height bands). |
| `data` | `JsonDataLoader` (generic datapack directory loader), `DataEvents` (loaders + client sync), `BuiltInPacks` (pinned `vanilla_overrides` pack). |
| `network` | `TerraNetwork` (one SimpleChannel), `packet.*` records with `STREAM_CODEC` + `handle`. |
| `command` | `TerrariaCommand` (`/terraria ...`, extensible), `DevActions` (shared by dev menu and commands). |
| `client` | `TerraClient` (client wiring), `ClientState` (mirrors), `ClientPacketHandlers`, `ClientEvents` (keys, left-click firing, double jump, water walking, inventory buttons), `KeyBindings`, `ItemTooltips`, `hud.TerrariaHud`, `gui.*` screens, `renderer.TerrariaProjectileRenderer`. |

## Key flows

### Stats
`TerraPlayerData.markStatsDirty()` (equipment change) or every 10 ticks -> `StatCalculator.recompute`
runs each `StatSource` -> `PlayerStats` -> `HealthManager.applyMaxLife` + `AttributeEffects.apply`
-> `SyncPlayerStatsPacket` when anything visible changed (mana changes are throttled to every 2 ticks).

### Damage (`CombatEvents.onHurt`, LivingHurtEvent HIGH)
1. Attacker scaling: `TerraDamageSource` from players gets class multiplier, +/-15% variance, crit roll;
   vanilla melee/arrow damage from players gets class multiplier + variance (melee crits via `CriticalHitEvent`).
2. Vanilla damage against players: environment x5, vanilla mobs x5 (config).
3. Defense: players `damage - defense * (0.5/0.75/1.0)` then endurance; TerraCraft enemies `damage - defense/2`; min 1.
4. Resets the victim player's regeneration timer.

### Progression
Anything -> `ProgressionManager.set(server, flag, value)` -> SavedData -> listeners -> announcement ->
`SyncProgressionPacket` to all. Conditions in JSON (`"!hardmode_active"`, `{"any": [...]}`) evaluate against
`ProgressionView` on either side.

### Crafting
Client screen filters `TerraRecipeManager.client()` with `CraftingStations.nearby` + inventory counts ->
`CraftRecipePacket` -> server re-validates progression, stations and materials -> consumes and gives results.

### Vanilla overrides
`packs/vanilla_overrides` is registered by `BuiltInPacks` as a *required* datapack *fixed at the top*, so its
replacements (config-conditioned recipe overrides, empty structure tags) win over vanilla and Forge in every world.

## Adding content (cheat sheet)

* **Weapon**: one line in `registry/content/WeaponContent` (+ art entry in `tools/generate_assets.py`,
  recipe in `tools/generate_data.py`), regenerate assets/data.
* **Accessory**: `AccessoryContent.accessory(name, rarity, value, StatEffects...)`.
* **Armor set**: `ArmorContent.set(name, helmet, chest, legs, setBonus, value)` (+ armor colour in the generator).
* **Buff**: `ModEffects.buff(...)` with `StatEffects`.
* **Projectile**: `ProjectileKinds.register(ProjectileKind.builder(...))` + projectile art.
* **Progression gate**: use `ProgressionCondition` JSON in data, or `ProgressionManager.addListener`.
* **Crafting station**: add a block to `#terracraft:stations/<name>`; recipes reference `terracraft:<name>`.

## Known engine constraints (26.2)

* EventBus 7: classes registered with `BusGroup.register(lookup, Class)` must have at least two listeners;
  single listeners use `XEvent.BUS.addListener`. Cancellation = listener returns `true`.
* Forge registers items before data component types: component types are created eagerly
  (`ModDataComponents`), item default components are resolved lazily.
* Modded menus opened with Forge's extra-data `openMenu` can lose their initial contents on slow clients
  (different packet queues in 26.2); `AccessoryMenu` therefore opens through the vanilla path.
* Entity type constants live in `EntityTypes`; time is clock based (use `/time` through the dispatcher).

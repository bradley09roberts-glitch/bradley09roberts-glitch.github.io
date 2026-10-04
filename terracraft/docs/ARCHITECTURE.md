# TerraCraft architecture

Target: Minecraft Java 26.2, NeoForge 26.2.0.88 (ModDevGradle 2), Java 25. One mod jar, no dependencies.

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
| `entity` | `SpriteEntity` (anything drawn by the 2D sprite renderer). |
| `entity.mob` | `MobDefinition` (Terraria stats), `TerrariaMobs` (definitions by entity id), `TerrariaMob` (base: contact damage, defense, coins, scaling, life scale), AI families `SlimeMob`, `MotherSlimeMob`, `WalkerMob`, `ClimberMob`, `FlyerMob`, `WormMob` (segmented burrowers). |
| `entity.boss` | `TerrariaBoss` (boss bar, synced phase, scaling, despawn, announcements, flags), `KingSlime`, `EyeOfCthulhu`, `EaterOfWorlds` (segment group with shared boss bar), `BrainOfCthulhu` (+ Creepers), `BossSummoning`, `BossCommands`. |
| `npc` | `TownNpc` (entity), `TownNpcType`/`TownNpcs` (definitions + arrival rules), `HousingChecker` (3D housing rules), `NpcWorldData` (SavedData: records + house candidates), `NpcManager` (arrival/respawn/death, chat, shops, nurse, help), `NpcShops` (JSON shops), `NpcCommands`, `HousingQueryItem`. |
| `block` | `CraftingStationBlock`. |
| `crafting` | `TerraRecipe` (JSON model), `TerraRecipeManager` (server load, client copy, validated crafting), `CraftingStations` (tag-based stations + reach scan), `CraftingLogic`. |
| `mining` | `MiningPower` (data-driven requirements, synced table), `ToolPowers`, `MiningEvents`, `MiningTags`. |
| `economy` | `Coins` (denominations, conversion, formatting), `EconomyEvents` (drops, compaction, softcore death). |
| `effect` | `TerraBuffEffect` (MobEffect carrying `StatEffects`). |
| `menu` | `AccessoryMenu`. |
| `world` | `VanillaSuppression`, `TerrariaLayer` (Space/Surface/Underground/Cavern/Underworld height bands), `FallenStars`. |
| `world.spawn` | `SpawnRule` (JSON model), `TerrariaSpawner` (per-player caps/rates, weighted rule pick), `SpawnCommands`. |
| `world.evil` | `EvilZones` (seeded zone layout on land), `EvilBiomeFeature` (per-chunk conversion, chasms, orbs, altars), `OrbSmashing` (orb/heart treasure, counter, boss trigger). |
| `world.event` | `TerrariaEvent`/`TerrariaEvents` (catalogue), `EventState` (SavedData), `EventManager` (natural starts/ends, Slime Rain, kill goals, sync, commands). |
| `world.gen` | `PairedOreFeature`, `LootChestFeature`, `WorldgenVariants` (thread-safe variants for generation), `WorldgenCommands`. Features/biome modifiers are datapack JSON. |
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

### Enemies and spawning
`TerrariaSpawner` (player tick, once a second) -> cap check (`maxSpawns` by layer/time) -> candidate column
24-44 blocks away -> `SpawnContext` (biome, layer, day, sky) -> matching `SpawnRule`s -> weighted pick ->
group spawn. Enemy stats live in `MobContent` (`MobDefinition` builder); difficulty scaling is applied in
`finalizeSpawn`. Damage taken by enemies with more than 1000 life is divided by their life scale in
`CombatEvents`.

### 3D models
`CreatureRenderers` registers every creature renderer: `TerraModelRenderer` with a family model
(`SlimeBodyModel`, `TerraHumanoidModel`, `EyeModel`, `BatModel3D`; layers in `TerraModels`) and a texture
`textures/entity/model/<id>[_variant].png`, or the 2D sprite renderer when the client option `flatSprites` is on
(decided whenever renderers are rebuilt). Model textures are drawn by `generate_assets.py` (`MODEL_TEXTURES`,
box-UV helpers `box_faces`/`paint_box`).

### Sprites (optional flat mode)
`TerrariaMobRenderer` draws any `SpriteEntity` (or living entity) from `textures/entity/mob/<id>[_variant].png`
with `<id>.json` frame data (`MobSprites`, reloaded with resources). One renderer serves enemies, bosses and NPCs.

### Bosses
`TerrariaBoss` subclasses implement `customServerAiStep` as a small state machine (`aiState`, `aiTimer`,
synced `phase()` for visuals). Summoning goes through `BossSummoning.summon` (items and commands).

### Town NPCs
`NpcManager` runs every 5 s: living NPCs keep/lose/find houses; absent NPCs arrive by day when their
`TownNpcType.arrival` predicate holds and a free valid house exists (the Guide needs none). House
candidates come from player-placed doors/lights/furniture. Chat: right-click -> `OpenNpcChatPacket`
(dialogue key, services, filtered shop) -> `NpcChatScreen` -> `NpcActionPacket` (buy/sell/heal/help/close),
re-validated on the server.

### Vanilla overrides
`packs/vanilla_overrides` is registered by `BuiltInPacks` as a *required* datapack *fixed at the top*, so its
replacements (config-conditioned recipe overrides, empty structure tags) win over vanilla and NeoForge in every world.

## Adding content (cheat sheet)

* **Weapon**: one line in `registry/content/WeaponContent` (+ art entry in `tools/generate_assets.py`,
  recipe in `tools/generate_data.py`), regenerate assets/data.
* **Accessory**: `AccessoryContent.accessory(name, rarity, value, StatEffects...)`.
* **Armor set**: `ArmorContent.set(name, helmet, chest, legs, setBonus, value)` (+ armor colour in the generator).
* **Buff**: `ModEffects.buff(...)` with `StatEffects`.
* **Projectile**: `ProjectileKinds.register(ProjectileKind.builder(...))` + projectile art.
* **Progression gate**: use `ProgressionCondition` JSON in data, or `ProgressionManager.addListener`.
* **Crafting station**: add a block to `#terracraft:stations/<name>`; recipes reference `terracraft:<name>`.
* **Enemy**: one line in `MobContent` (AI family, hitbox, `MobDefinition`), a renderer line in
  `CreatureRenderers`, a model texture (`model_texture`) and a flat sprite (`mob_sprite`) in `generate_assets.py`, loot + name in `generate_data.py` (`MOBS`), spawn rules in `SPAWNS` (or any datapack).
* **Boss**: subclass `TerrariaBoss`, register in `MobContent`, add a summon item (`BossSummonItem`) and an
  entry in `BossCommands`.
* **Town NPC**: `NpcContent.npc(id)`, a `TownNpcType` in `TownNpcs` (names, arrival rule, attack, services),
  sprite, dialogue lines in `generate_data.py` and an optional shop JSON.

## Known engine constraints (26.2)

* Two buses: registration/lifecycle events (`IModBusEvent`) go on the mod bus (`TerraCraft.modBus()`), the rest on
  `NeoForge.EVENT_BUS`. Classes registered with `NeoForge.EVENT_BUS.register(Class)` need public static
  `@SubscribeEvent` methods. Cancel with `event.setCanceled(true)`; damage immunities and the damage pipeline both
  listen to `LivingIncomingDamageEvent` (immunities at HIGHEST priority).
* `registry.DeferredRegister`/`RegistryObject` are thin wrappers over NeoForge's `DeferredRegister`/`DeferredHolder`
  that also hand out the entry's `ResourceKey` before registration (items, blocks and entity types need it).
* Player data is a serializable data attachment (`TerraAttachments.PLAYER_DATA`, copied on death in `PlayerEvents`);
  packets are `CustomPacketPayload` records registered in `TerraNetwork` (protocol version in `PROTOCOL`).
* Registries are filled before data component types are needed: component types are created eagerly
  (`ModDataComponents`), item default components are resolved lazily.
* Armor models come from `IClientItemExtensions` registered in `client.TerraArmorClient`; the Terraria HUD replaces
  the `player_health` GUI layer and hides `food_level` (two vanilla `Hud` methods are opened by
  `META-INF/accesstransformer.cfg`).
* Health is real Terraria life: `TerrariaMob.raiseHealthCap()` lifts the max health attribute's 1024 limit (the
  `RangedAttribute.maxValue` field is opened by the access transformer) at mod construction on both sides. Saves from
  before kept health under 1000 with a `TerrariaLifeScale` tag and are converted on load.
* `client.hud.HealthBars`: boss/invasion bars are redrawn through `CustomizeGuiOverlayEvent.BossEventProgress` (life
  numbers come from the client entity whose health matches the bar); the focus bar picks the creature under the
  crosshair within 48 blocks each client tick. Both can be turned off in `terracraft-client.toml`.
* Modded menus opened with the extra-data `openMenu` can lose their initial contents on slow clients
  (different packet queues in 26.2); `AccessoryMenu` therefore opens through the vanilla path.
* Entity type constants live in `EntityTypes`; time is clock based (use `/time` through the dispatcher).

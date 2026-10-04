# TerraCraft development progress

Project: Terraria total conversion for Minecraft Java 26.2 / NeoForge 26.2 (single mod jar, modid `terracraft`).
The mod started on Forge 65.1.0 and was ported to NeoForge so Iris + Sodium (shaders) can run with it.
Read `docs/ARCHITECTURE.md` first when continuing work; `docs/TESTING.md` explains the headless test setup.
`docs/VISUAL_BACKLOG.md` lists model/visual upgrades saved for the end; add new entries as stages land.

## COMPLETED

### Stage 0: Foundation (verified on a dedicated server)
- MC 26.2 project, now NeoForge 26.2.0.88 (ModDevGradle 2, Gradle 9.5, Java 25 toolchain), builds one jar.
- Mod entry point, registry architecture (DeferredRegisters + content DSL + automatic creative tab filing).
- Networking: play-phase `CustomPacketPayload` records, versioned (`TerraNetwork.PROTOCOL`).
- Config: `terracraft-common.toml` (gameplay, vanilla suppression, economy, world) and `terracraft-client.toml` (HUD).
- Player data attachment (`terracraft:player_data`): crystals, fruit, mana, accessory inventory; saved, copied on death.
- World progression SavedData (`data/terracraft/world_progression.dat`): 50+ named flags (bosses, world,
  events, NPCs), derived flags (`mech_bosses_defeated`...), counters, world variants (evil type + ore pair
  choices from seed), change listeners, Terraria announcements, client sync, JSON condition language.
- Creative tabs (Blocks, Materials, Weapons, Tools & Armor, Accessories, Consumables, Developer).
- Developer menu (Developer Tablet / `/terraria devmenu`): toggle flags, day/night, heal, max/reset stats.
- `/terraria` commands: worldstate, progression list/set/reset, hardmode, evil, stats, set, give mana, heal,
  devmenu, biome debug, recipecheck; extension hook for later systems.
- Documentation: README, docs/ARCHITECTURE.md, docs/TESTING.md, this file, content checklist.

### Stage 1: Core Terraria systems (verified in a live client, see docs/TESTING.md)
- **Health**: 100 base life, Life Crystal (+20, max 15), Life Fruit (+5, max 20, needs all crystals);
  Terraria hearts HUD with golden hearts, "Life: x/y"; Terraria natural regeneration replaces food healing
  (5 s delay after damage, ramps up, faster when still); full food bar = Well Fed bonuses.
- **Mana**: 20 base, Mana Crystal (+20, max 9), Terraria regen formula (delay after casting, faster when
  still), mana stars HUD, mana potions + Mana Sickness, synced client/server.
- **Combat classes**: melee/ranged/magic/summon/generic; class damage and crit bonuses; Terraria crits
  (4% base, x2), +/-15% variance, defense (Classic/Expert/Master effectiveness), endurance, armor penetration,
  knockback scale; vanilla environmental and vanilla-mob damage scaled x5 against players.
- **Projectiles**: one generic entity + `ProjectileKind` registry (gravity, drag, pierce, bounce, homing,
  boomerang, explosion, ignite, debuffs, per-target cooldowns, trails, fullbright); billboard/crossed-quad
  renderer. 9 kinds (wooden/flaming arrow, musket ball, shuriken, throwing knife, wooden boomerang, spark,
  amethyst bolt, magic missile).
- **Weapons**: melee (attribute based), ranged with ammo (vanilla arrows = Wooden Arrows), magic (mana),
  thrown (consumable) and boomerang archetypes; Terraria-style hold-left-click firing; tooltips.
- **Coins**: copper/silver/gold/platinum, auto conversion at 100, enemy coin drops (75-125% of value),
  softcore death (keep items, drop half coins; config), formatted prices in tooltips.
- **Accessories**: 5 slots (config + per-player extra), equipment screen (R / inventory button) with armor,
  no duplicates; 18 accessories with stat/ability effects; double jump (Cloud in a Bottle), water walking,
  fall immunity, knockback immunity, fire/lava immunity, movement/jump/mining/reach via attributes.
- **Armor**: 9 Terraria armor sets (wood, copper, tin, iron, lead, silver, tungsten, gold, platinum) with
  defense, set bonuses, worn textures; vanilla armor converted to Terraria defense (x0.5).
- **Buffs/potions**: 12 buffs + Potion/Mana Sickness as MobEffects driving the stat system; 16 potions.
- **Crafting**: Terraria recipe JSON (73 recipes), station tags (Work Bench, Furnace, Anvil, Alchemy, Loom...),
  crafting screen (V) with station detection and craftable filter, server-validated crafting.
- **Ores**: Tin, Lead, Silver, Tungsten, Platinum ores (+deepslate), raw metal, bars, smelting; Copper/Iron/Gold
  reuse vanilla. World ore-pair choice stored per world (used by Stage 2 worldgen).
- **Pickaxe power**: Terraria powers on tools, vanilla pickaxes mapped (diamond 45), data-driven block
  requirements (Obsidian 55, synced to clients), break + harvest enforcement with message.
- **Vanilla removal**: villagers/zombie villagers/wandering traders/trader llamas blocked (and conversions),
  villages/pillager outposts/mansions/strongholds/ruined portals removed, natural vanilla hostile spawns
  denied (plus patrols/phantoms), Nether/End travel blocked, enchanting table and brewing stand disabled,
  diamond/netherite gear + enchanting table + brewing stand recipes disabled (pinned override datapack,
  config-conditioned).
- **Assets**: all textures generated by `tools/generate_assets.py` (124 items, blocks, projectiles, HUD,
  effect icons, armor layers); data by `tools/generate_data.py` (recipes, loot, tags, damage types, lang).

### Stage 2: Early game (verified in a live client)
- **Fix**: Cloud in a Bottle no longer fires on the ground jump (extra jumps need the key released in the air).
- **Enemy framework** (`entity.mob`): `TerrariaMob` (Terraria life/defense/contact damage/coins, Expert and
  Master scaling, life above the 1024 health cap via life scale, no fall damage, poof death), AI families
  `SlimeMob`, `MotherSlimeMob` (splits), `WalkerMob`, `FlyerMob` (chaser/erratic). 13 enemies: Green, Blue,
  Red, Purple, Yellow, Black, Baby and Mother Slime, Zombie, Demon Eye, Skeleton, Cave Bat, Servant of Cthulhu.
- **Sprite renderer**: enemies, bosses and NPCs are 2D camera-facing animated sprites (frame JSON, mirroring,
  flight tilt, spin, hurt flash, Terraria health bars). Re-skinnable by resource packs.
- **Spawning**: per-player Terraria spawner (caps and rates by layer/time, configurable), JSON spawn rules
  (`terracraft/spawns`: biome, layer, time, sky, placement, group size, progression condition).
- **Worldgen**: `terracraft:paired_ore` (world ore-pair choice; secondary ore at `secondaryOreFrequency`)
  replacing vanilla copper/iron/gold veins via NeoForge biome modifiers; Life Crystals on cave floors; wooden and
  underground loot chests with Terraria-style loot; Fallen Stars at night that vanish at dawn.
- **Bosses** (`entity.boss`): `TerrariaBoss` (boss bar, synced phases, Expert/Master + multiplayer life scaling,
  despawn rules, announcements, progression flags, potion drops, terrain immunity). King Slime (hops, high hops,
  teleport, shrinking, sheds slimes) and Eye of Cthulhu (servants, charges, spin transformation, mouth form,
  flees at dawn, Demonite/Crimtane drop by world evil). Slime Crown and Suspicious Looking Eye.
- **Town NPCs** (`npc`): `TownNpc` (friendly, player-proof, goes home at night, opens doors, defends itself
  with projectiles), 3D housing rules (`HousingChecker`: enclosed, size, door, light, comfort, table; tags
  `terracraft:housing/*`), house discovery from placed blocks, arrival/respawn/death rules, Guide (help tips),
  Merchant and Demolitionist (data-driven shops, sell held item for 1/5 value), Nurse (paid healing + debuff
  removal), chat/shop screen, Housing Query item.
- **Personal sprite pack** tool (`tools/terraria_resource_pack.py`) for items, enemies, bosses and NPCs.
- Commands: `/terraria spawns`, `killall`, `boss`, `npc`, `worldgen scan`, `star`.

### 3D creatures (after Stage 2)
- Enemies, bosses and town NPCs are 3D models by default (`client.model`): slime (translucent jelly with core
  and eyes, squash/stretch hops; Mother Slime with a baby inside; King Slime with crown and trapped ninja),
  humanoid (zombie with outstretched arms, thin-limbed skeleton, NPC skins with hats/beards), eyeball (Demon
  Eye, Servant, Eye of Cthulhu + mouth form, pitches toward its flight path, waving tendrils, spins while
  transforming) and bat (flapping wings). Textures are generated box-UV skins (`MODEL_TEXTURES`).
- `TerraModelRenderer` scales each model to its hitbox, keeps health bars, shows name tags only for named
  NPCs. Client option `flatSprites` switches back to the 2D sprite renderer (useful with the Terraria pack).

### Stage 3: Corruption/Crimson, evil bosses, events (verified in a live client)
- **Evil biomes** (`world.evil`): three seeded zones per world placed on land (biome-source land test, no
  oceans/mountains), generated per chunk by `EvilBiomeFeature`: Ebonstone/Crimstone, evil grass, Ebonwood/
  Shadewood trees, Demonite/Crimtane ore clusters, winding chasms with orb rooms and side pockets, Shadow
  Orbs/Crimson Hearts, Demon/Crimson Altars, Vile/Vicious Mushrooms. `/terraria worldgen evil` lists zones.
- **Orbs/Hearts**: hammer only; treasure loot tables (Musket, Vilethorn, Band of Starpower / The Undertaker,
  Panic Necklace...); world counter; every third summons Eater of Worlds / Brain of Cthulhu.
- **Altars**: Demon Altar crafting station (Worm Food, Bloody Spine); need hammer power 80 in Hardmode to break.
- **Spawning**: rules can require/exclude ground blocks (`ground`, `exclude_ground`) and events (`event`);
  evil tables (Eater of Souls, Devourer / Crimera, Face Monster, Blood Crawler); normal tables avoid evil ground.
- **Worm framework** (`WormMob`): burrowing head that steers only inside terrain, trailing segment entities,
  shared-life (Devourer, Giant Worm) or split (Eater of Worlds) worms; `ClimberMob` wall climbers.
- **Bosses**: Eater of Worlds (30 segments, per-segment life/defense/damage, splitting, one boss bar for all
  segments, per-segment Demonite + Shadow Scale drops), Brain of Cthulhu (Creeper shield, teleports, dash
  phase, Crimtane + Tissue Samples). Worm Food / Bloody Spine only work inside their biome.
- **Gear**: Light's Bane, Blood Butcherer, Demon/Tendon Bow, Musket, The Undertaker, Vilethorn, Nightmare/
  Deathbringer Pickaxe, War Axe of the Night, Blood Lust Cluster, The Breaker, Flesh Grinder, Shadow and
  Crimson armor sets (speed / regeneration set bonuses), Panic Necklace.
- **Events** (`world.event`): event framework (persisted state, natural starts at dusk/dawn, end at dawn/dusk
  or kill goal, spawn rate/cap multipliers, client sync). Blood Moon (red sky, Blood Zombie, Drippler, more
  zombies) and Slime Rain (slimes fall from the sky; 150 kills, or 75 after King Slime; summons King Slime).
  `/terraria event start|stop|status`.
- **NPCs**: Arms Dealer (moves in when someone carries a gun or bullets; sells ammo and guns) and Dryad (after
  a boss; sells Purification Powder, which cleanses evil blocks, saplings and the world's evil powder).
- 3D models: worm segments, flying maws, brain, Blood Crawler (vanilla spider shape), new humanoid skins.

### Stage 4a: Dungeon and Skeletron (verified in a live client)
- **Dungeon** (`world.dungeon`): one per world, placed from the seed 650-950 blocks from spawn on dry land away
  from the evil zones (`DungeonLayout`, pure function of seed + terrain height). Brick entrance tower with
  battlements, windows, doorway and porch facing spawn; ladder shaft to 4 levels of rooms (9x6x9) linked by
  corridors and ladder shafts (~49 halls). Built per chunk by `DungeonFeature` (2-block brick shell, then air,
  then furniture): Locked Gold Chests, dungeon chests (Golden Key, Water Bolt, potions), Spikes, bookshelves,
  hanging lanterns, cobwebs. Blue/Green/Pink Brick per world (65% pickaxe power, blast proof).
  `/terraria worldgen dungeon` prints the entrance; the Guide's help can point the way.
- **Old Man** (nameless town NPC) waits at the entrance until Skeletron is beaten (respawns if a fight is
  lost). By day he turns visitors away; at night his chat has a **Curse** button that turns him into Skeletron.
- **Dungeon Guardian**: entering the Dungeon (rooms/halls below the entrance) before Skeletron is defeated
  sends a wall-phasing, unkillable skull that kills in one or two hits; it leaves when the victim leaves.
- **Skeletron** (`entity.boss.Skeletron`): head (4400 life) hovers above the player and periodically spins and
  chases (double defense, more damage); two separate Hands (600 life each) swipe in turns; with both hands dead
  it spins more often and throws homing skulls. At daytime it enrages (9999 defense and damage, very fast).
  Drops Book of Skulls (1/7). Defeat lifts the curse: Old Man removed, Dungeon enemies spawn, Clothier can
  move in (sells cloth, leather, dyes).
- **Dungeon enemies** (spawn on dungeon bricks after Skeletron): Angry Bones, Dark Caster (`CasterMob`:
  teleports near the target and casts homing water spheres), Cursed Skull (`GhostFlyerMob`, passes through
  walls), Dungeon Slime (always drops a Golden Key). Normal spawn tables ignore dungeon bricks.
- **Items**: Golden Key (opens and is used up by one Locked Gold Chest), Shadow Key, Muramasa, Handgun,
  Aqua Scepter, Water Bolt (bouncing), Book of Skulls; locked chests hold one of Muramasa, Cobalt Shield, Aqua
  Scepter, Handgun, Magic Missile or Shadow Key.
- 3D models: `SkullModel` (Skeletron, Guardian, Cursed Skull; chattering jaw, spin), `BoneHandModel`.
- Fixes: boss bars are cleared when a boss's chunk unloads (previously a stale bar stayed); NPC chat closes
  when the NPC disappears.

### Stage 4b: Jungle and Queen Bee (verified in a live client)
- **Jungle** (`world.jungle.JungleFeature`, added to `#minecraft:is_jungle`): under jungle columns (decided from
  the biome source, chunk independent) dirt and most stone down to y=-24 become mud; mud facing air grows
  Jungle Grass and cave grass sprouts glowing Jungle Spores; Bee Hives (hive shell, honey pool, Larva) are
  buried in the underground jungle. Ivy chests (Anklet of the Wind, Feral Claws, Nature's Gift, Honey Comb).
- **Enemies**: Jungle Slime, Snatcher (surface) and Hornet (`HornetMob`: keeps its distance, poison stingers),
  Man Eater (`SnapperMob`: rooted plant lunging from its anchor), Jungle Bat (underground). Normal underground
  tables skip jungle ground; surface day slimes skip jungle biomes.
- **Queen Bee** (`entity.boss.QueenBee`, 3400 life): breaking a Larva or using an Abeemination in the Jungle.
  Cycles three dashes through the player, hatching bees, and stinger volleys; faster below half life; enraged
  (double damage) outside the Jungle. Drops Bee Gun / Bee Keeper / The Bee's Knees, Honey Comb (1/3), Bee Wax,
  honey blocks.
- **Gear**: Blade of Grass (poison on hit), Bee Keeper (bees on hit), The Bee's Knees (arrows become bee
  arrows), Bee Gun (homing bees), Honey Comb (bees + regeneration when hurt), Jungle armor (+mana, magic crit,
  -16% mana cost). Melee weapons can now carry on-hit effects; ranged weapons can convert their ammo.
- 3D bee model (Hornet, bee, Queen Bee); man eaters use the maw model.

### Stage 4c: Underworld, Wall of Flesh, Hardmode switch (verified in a live client)
- **Underworld** (`world.underworld.UnderworldFeature`, last generation step): below y=-40 the overworld becomes
  one huge cavern between an ash floor (y -62..-53) and an ash ceiling (-48..-40), from smooth seeded noise; a
  lava sea fills the low spots (y <= -57); Hellstone veins in the ash; lavafalls; ruined obsidian/hellstone
  brick houses with Hellforges and Shadow Chests (Shadow Key; Flamelash, Flower of Fire, Hellwing Bow).
  Ancient cities are removed (the deep dark sat in this space).
- **Enemies** (fire immune): Fire Imp (caster, homing fireballs through walls), Demon and Voodoo Demon
  (`ShooterFlyerMob`, demon scythes; Voodoo Demons always drop the Guide Voodoo Doll), Lava Slime, Hellbat,
  Bone Serpent (worm).
- **Wall of Flesh** (`entity.boss.WallOfFlesh`, 8000 life): a Guide Voodoo Doll (fire proof) dropped into
  Underworld lava while the Guide lives kills the Guide and summons it 32 blocks away. A 36x20 block wall of
  flesh crawls along one axis toward the player through terrain, faster as it weakens; its mouth tracks the
  player; touching it hurts and players behind it are dragged back. Two Eyes (sharing its life) fire lasers;
  The Hungry spawn on tethers. Loot is delivered to the nearest player (not into the lava): Pwnhammer, one
  emblem, Breaker Blade or Laser Rifle. Its death sets `hardmode_active` ("The ancient spirits of light and
  dark have been released."); the Pwnhammer's 80% hammer power then breaks altars.
- **Gear**: Hellstone (65% power) -> Hellstone Bar at the Hellforge; Molten Pickaxe (100%), Molten Hamaxe,
  Fiery Greatsword (ignites), Molten Fury (flaming arrows), Phoenix Blaster, Molten armor (+17% melee damage
  set), Demon Scythe, Flamelash, Flower of Fire, Hellwing Bow, Warrior/Ranger/Sorcerer/Summoner Emblems.
- Wall of Flesh renderer (tiled flesh sheet + mouth model, enlarged culling box); `ShooterFlyerMob` replaces
  the Hornet-only class; fire-immune enemy registration.

### Stage 4d: Goblin Army, Goblin Tinkerer, reforging (verified in a live client)
- **Invasions** in the event framework (`TerrariaEvent.invasion`): ignore the time of day, end at a kill goal of
  their own members, replace the normal surface spawns and show a progress boss bar. **Goblin Army**: starts
  at dawn (1 in 3; 1 in 30 once beaten) after a Shadow Orb/Crimson Heart was smashed and someone has 200+ max
  life, or with a Goblin Battle Standard (Tattered Cloth); 80 goblins: Peon, Thief, Warrior, Archer
  (`ArcherMob`), Sorcerer (chaos balls). Defeat sets `event_goblin_army_defeated`.
- **Goblin Tinkerer**: a Bound Goblin then waits tied up in a cave near an underground player
  (`npc.BoundNpcs`); talking to him frees him (`npc_goblin_tinkerer_rescued`) and he becomes a town NPC who sells
  the Tinkerer's Workshop, Toolbelt and Battle Standards and **reforges** the held item for coins.
- **Prefixes** (`item.modifier`): 72 Terraria prefixes (universal, melee, ranged, magic, accessory) stored on
  the stack; applying one rescales the stack's stats (damage, speed, crit, knockback, velocity, mana, value)
  and melee attack attributes, adds the prefix to the name ("Unpleasant Copper Broadsword") and green/red
  tooltip lines; accessory prefixes add defense, mana, crit, damage, movement or melee speed. Crafted weapons,
  tools and accessories get a random prefix (3 in 4).
- **Tinkerer's Workshop** combinations: Obsidian Horseshoe, Cloud in a Balloon, Obsidian Shield, Obsidian Water
  Walking Boots, Lava Waders, Mana Flower.

### Stage 4e: Meteorite (verified in a live client)
- `world.MeteorManager`: smashing an orb/heart has a 1 in 2 chance to send a meteor that night; afterwards
  meteors land on about 1 in 50 nights. It lands 60-110 blocks from a player (not in water), carving a crater
  lined with Meteorite ("A meteorite has landed!"); `/terraria meteor` drops one. Meteor Heads (wall-phasing
  flaming skulls) haunt the latest crater while a player is near.
- Meteorite (50% power) -> Meteorite Bar; Meteor armor (+7% magic damage per piece; set: free Space Gun);
  Space Gun (laser).

### Polish pass after Stage 4 (verified in a live client)
- **3D armor**: every set (Wood, the eight ore sets, Shadow, Crimson, Jungle, Molten, Meteor) has its own 3D model:
  domed helmets with nasal guards, visors and crests, crowns for Gold/Platinum, the Jungle Hat's leafy brim,
  Molten horns, the Meteor bubble helmet, Shadow spikes, the Crimson bone mask, pauldrons, bracers, knee cops and
  boots. `tools/armor_models.py` writes the cubes (`models/armor/<set>.json`) and paints the matching texture;
  `client.model.ArmorModels` builds the models (NeoForge `IClientItemExtensions#getHumanoidArmorModel`).
- **Dungeon Bookcases** replace vanilla bookshelves in the Dungeon: right-click (or break) to take 1-3 Books, with a
  6% chance of a Water Bolt (Water Bolts are no longer in dungeon chests). The emptied shelf stays as furniture.
- **Mob colours** checked against Terraria: Mother/Baby Slime are black, Cave Bat blue, Jungle Bat brown, Hornet
  orange/pink, Demon/Voodoo Demon red with grey wings, Fire Imp pink-red, Jungle Slime green, Dungeon Slime lavender,
  Skeleton (bone + blue rags) and Angry Bones (red), tan skulls, blue Demon Eye iris, green/olive goblins, etc.
- **No hunger**: the food bar is hidden and held just below full. Eating gives Well Fed, Plenty Satisfied or
  Exquisitely Stuffed (by how filling the food is, 7-30 minutes); Expert life regeneration still wants a food buff.
- **No boots slot**: Terraria armor is helmet/chest/greaves; the inventory's feet slot is disabled and hidden, and
  boots put on any other way are moved back to the inventory.
- **Wings** (`WingsItem`): hold jump in the air to fly for the wings' flight time, then glide; refills on landing,
  cancels fall damage, 3D wings on the back (folded, flapping, gliding) visible to all players. Fledgling Wings
  (underground chests), Angel/Demon Wings (Feathers + Souls), Leaf Wings. Hardmode souls: Night (underground in the
  evil biome), Light (underground elsewhere, until the Hallow exists), Flight (flying enemies above y=150).
- **Terraria biomes only**: new worlds use `TerrariaBiomeSource`, which maps every Minecraft-only biome onto its
  Terraria counterpart (plains/birch/cherry/swamp/savanna -> Forest, taiga/ice spikes -> Snow, badlands -> Desert,
  bamboo -> Jungle, cold oceans -> Ocean...). Set in the `normal`, `large_biomes` and `amplified` world presets.
- `/terraria equip <accessory>` puts an accessory in the first free slot (tests, showcase kits).

### Visual pass 2 (verified in a live client)
- **Armor redone from Terraria's sprites**: most helmets open-faced (Lead, Tungsten, Meteor visor and Molten's stone
  mask stay closed); Molten is grey stone with lava cracks; every chest has breast/belly/back plates, pauldrons,
  bracers, a belt with buckle and gems; every leg piece has thigh plates, knee cops, shin guards, boots and tassets.
- **Own models instead of reskins** (`tools/creature_models.py` -> `models/creature/*.json` -> `JsonCreatureModel`):
  Imp (hunched devil, ears, horns, tail, fireball), Demon and Voodoo Demon (winged, horned; the Voodoo Demon carries
  the Guide doll), Eater of Souls (segmented flyer with mandibles), Crimera (toothy flesh lump with tendrils), Face
  Monster (hunched ghoul with a huge mouth), Blood Crawler (eight-legged spider), Man Eater and Snatcher (snapping
  plant heads on vines), Meteor Head (rock skull trailing flames). Animated: walking, flapping, chomping, scuttling.
- **Skins** (`tools/skins.py`) in the style of good hand-made Minecraft skins: shaded faces with eye whites, irises,
  brows, nose and lips; strand hair; layered clothes (jackets, collars, buttons, straps, suspenders, aprons, robes),
  belts with buckles, rolled sleeves, scuffed knees, laced boots; zombies torn and bloodied. Goblins have ears.
- Items stay 2D. Tool icons face like Minecraft's: handle bottom-left, working end on the left (axes, hammers and
  hamaxes mirrored across the handle; bows turned to Minecraft's diagonal). The sprite-pack tool does the same.
- NPCs, zombies and goblins use the player-skin layout with the second (3D) layer: hair, coats, belts, cuffs and
  boot tops stand out from the body.
- Meteorite block recoloured to Terraria's maroon-purple rock with pink highlights.

### Stage 5a/5b: Hardmode world, ores and gear (verified in a live client)
- `world/hardmode/HardmodeWorld`: when Hardmode starts, two rays leave the spawn area in a V (angle from the seed,
  160-4000 blocks long, wavy ~34-block half-width). One becomes the **Hallow** (pearlstone, hallowed grass, pearlsand,
  pearlwood, hallowed leaves), the other the world's evil (ebonstone/crimstone, evil grass and wood). Chunks are
  converted from the surface down to y=-48 as they load (3 per tick, remembered in `hardmode_chunks.dat`).
- **Spread**: every second, 40 random blocks near each player turn into their neighbouring Hallow/evil blocks.
- **Altars**: smashing a Demon/Crimson Altar with the Pwnhammer in Hardmode blesses the world with the next ore tier
  (one of each pair, per world): "Your world has been blessed with Cobalt!". Veins go into every chunk (loaded and
  future ones), deeper and rarer per tier.
- **Ores, bars, stations**: Cobalt/Palladium (pickaxe power 100), Mythril/Orichalcum (110), Adamantite/Titanium
  (150); Mythril/Orichalcum Anvil (counts as anvil + hardmode anvil), Adamantite/Titanium Forge (furnace, Hellforge
  and hardmode forge). Angel/Demon Wings now need the Hardmode anvil.
- **Gear** for all six metals: pickaxe (110-190 power), sword, repeater and a 3D armor set with Terraria's set bonuses
  (Cobalt melee speed + ammo, Palladium regen, Mythril crit, Orichalcum damage + speed, Adamantite melee/move speed,
  Titanium damage reduction + damage).
- Fixed: NPC/zombie skins changed on every regeneration (Python's salted `hash`); now a stable CRC.

### Stage 5c: Hardmode enemies (verified in a live client)
- **Hallow** (spawn on Hallow ground): Pixie (day), Unicorn (day, charges), Gastropod (night, pink lasers), Illuminant
  Bat and Illuminant Slime (underground), Chaos Elemental (underground, teleports next to you every few seconds).
- **Hardmode Corruption/Crimson**: Corruptor (spits vile spit, causes Weakness), Slimer; Herpling, Crimslime,
  Floaty Gross (drifts through walls at night).
- **Everywhere in Hardmode**: Wraith, Possessed Armor and Werewolf at night; Wyverns in space (drop 4-6 Souls of
  Flight); Armored Skeleton, Giant Bat and the rare Mimic (Titan Glove) in caverns.
- New JSON models (`tools/creature_models.py`): pixie, unicorn, gastropod, chaos elemental, corruptor, slimer,
  herpling, floaty gross, wraith, possessed armor, werewolf, mimic (its lid snaps). Bats, slimes, the Wyvern and the
  Armored Skeleton reuse the shared bat/slime/worm/skeleton models with their own textures.
- New items: Pixie Dust, Unicorn Horn, Titan Glove (+100% knockback, auto-swing).
- Souls of Light now come from the underground Hallow and Souls of Night from the underground evil stripe or
  Corruption/Crimson (Terraria's rule); ordinary enemies no longer spawn on Hallow ground.

### Stage 5d: Mechanical bosses and Hallowed gear (verified in a live client)
- Summoned at night with items made at a Mythril/Orichalcum Anvil from Souls of Light/Night: Mechanical Worm,
  Mechanical Eye, Mechanical Skull. All three leave at daybreak.
- **The Twins** (`TheTwins`): Retinazer circles to one side firing lasers and charging; Spazmatism charges and
  spits cursed flames. At 40% life each transforms into its mechanical form (rapid laser volleys / a cursed-fire
  flamethrower and fast charges). The fight, "The Twins has been defeated!", Souls of Sight and Hallowed Bars come
  with the second death.
- **The Destroyer** (`Destroyer`): a 40-segment metal worm that tunnels through terrain. All damage goes to the
  shared life (one boss bar); body segments fire lasers and release Probes when hit. Drops Souls of Might.
- **Skeletron Prime** (`SkeletronPrime`): a steel skull with four separate arms (Cannon lobs bombs, Laser shoots,
  Saw and Vice lunge); the head spins after you, more often without arms, and is enraged by daylight. Drops Souls of
  Fright.
- Hallowed gear: Excalibur, Hallowed Repeater, Pickaxe Axe (power 200), Hallowed Mask/Plate Mail/Greaves (3D set,
  +15% melee and movement speed), plus Greater Healing Potions from Hardmode bosses.
- `/terraria boss spawn the_twins|destroyer|skeletron_prime`.

### Stage 5e: Hardmode NPCs, Clentaminator, showcase (verified in a live client)
- **Wizard**: in Hardmode a Bound Wizard waits tied up in the caverns near a player (like the Bound Goblin);
  talking to him frees him and he moves in. Sells Spell Tomes, Greater Mana Potions, Mana Potions, books.
- **Steampunker**: arrives after any mechanical boss. Sells the **Clentaminator** and Green/Blue/Purple/Red
  Solutions: the Clentaminator sprays the first solution in the inventory along your aim (16 blocks), purifying or
  spreading the Hallow/Corruption/Crimson. Purification Powder and Green Solution now also cleanse the Hallow.
- **Witch Doctor**: arrives after Queen Bee. Sells Leaf Wings (Hardmode, at night), stingers, jungle spores.
- `BoundNpcs` handles both bound NPCs; skins in `tools/generate_assets.py` (Wizard robe and beard, Steampunker
  goggles and coat, Witch Doctor mask and leaf apron).
- Showcase world: Hardmode enemies in the zoo, the mechanical bosses in the arena, a Hallow teleport, Hardmode
  Ores and Hallowed kits, the new NPCs and summons.
- Terraria sprite pack: 387 sprites including all Hardmode items, enemies and NPCs.

### Stage 5f: Queen Slime, Mechanic, Pirate Invasion, Frost Legion (verified in a live client)
- **Underground Hallow crystals** (`HardmodeWorld.growCrystals`, chunk flag `CRYSTALS`): Crystal Shards grow on the
  Hallow's cave walls, floors and ceilings (a glowing cluster block that is also the material), and about one chunk in
  eight gets a pink **Gelatin Crystal** on a cavern floor. Greater Healing Potion: 3 bottles + 3 Pixie Dust + Crystal Shard.
- **Queen Slime** (Gelatin Crystal, in the Hallow; 18000 life): hops at the player with a high leap and slam every
  fourth hop (Regal Gel bursts out in a ring); at half life she grows crystal wings, flies above the player firing gel
  volleys and dives in slams. Crystal, Bouncy (high-jumping) and Heavenly (flying, haloed) Slimes keep joining.
  Drops a **Crystal Assassin** armor piece (hood/shirt/pants: +10% damage, +5% crit each; set: +20% movement speed,
  +10% damage, higher jumps; 3D ninja model) and sometimes **Volatile Gelatin** (flings bouncing gel at a nearby
  enemy every two seconds).
- **Mechanic**: after Skeletron a Bound Mechanic waits in a Dungeon room 16-60 blocks from a player inside the
  Dungeon; freeing her makes her move in. She sells wiring - Minecraft's redstone parts (redstone, torches, levers,
  buttons, pressure plates, repeaters, comparators, observers, pistons, dispensers, lamps, rails...).
  Bound NPCs now really stay put (their navigation already moved them a little every tick).
- **Pirate Invasion** (Hardmode; a Pirate Map dropped 1 in 17 by enemies killed at the ocean or a beach, or on its
  own some mornings once an altar is smashed): Pirate Deckhands, Corsairs, Crossbowers, Deadeyes, Captains (they
  fire exploding cannonballs) and Parrots until 120 kills; once a third are beaten the **Flying Dutchman** (a ghost
  galleon, 10000 life) sails high above the player firing its four cannons in turn and counts for ten kills.
  Loot: Cutlass, Gold Ring (pulls coins in from 12 blocks), Lucky Coin (hits shake coins out of enemies), Discount
  Card (shops 20% cheaper), and the rare **Coin Gun** (coins are its ammo: copper 25, silver 50, gold 100, platinum
  200 damage). The **Pirate** NPC moves in afterwards: Cannonballs (thrown bombs), his costume (vanity, 3D model),
  spyglass, compass, maps, boats.
- **Frost Legion** (Hardmode; a Snow Globe from Presents): Presents drop from enemies (1 in 13 from Dec 15 to Jan 1,
  1 in 40 in snowy biomes in Hardmode, 1 in 150 elsewhere in Hardmode) and open into coins, treats, snow or potions,
  with a one-in-ten Snow Globe in Hardmode. Mister Stabby (knife), Snowman Gangsta (tommy gun) and Snow Balla
  (slowing snowballs) until 80 kills. Santa Claus is not in yet.
- Events can now list several entity prefixes for their kill count (`TerrariaEvent.isMember`).
- New 3D models: Queen Slime (tiara, crystal spikes, wings in phase 2), Heavenly Slime (halo, wings), Parrot,
  Flying Dutchman, three snowmen; skins for the five pirates, the Pirate and the Mechanic.
- Showcase world: Pirate Invasion and Frost Legion rooms, Queen Slime's minions in the Hallow room, Queen Slime and
  the Flying Dutchman in the Hall of Bosses (now two rows of six), event buttons, Queen Slime and Pirate kits, the
  Mechanic and the Pirate in the town NPC buttons. `/terraria worldgen dungeon rooms` lists Dungeon rooms.

### Real boss life and new health bars
- Enemies and bosses now have their real Terraria life as Minecraft health (Queen Slime 18,000, the Destroyer
  80,000...): the max health attribute's 1024 limit is raised to 10,000,000 at startup. Before, health stayed under
  1000 and damage was divided by a scale (the same fight length, but every number you saw was wrong). Old saves convert.
- **Boss bars**: framed bars in the boss's colour with gold end caps, the boss name above, real life inside
  ("15,272 / 18,000"), notches every 10% and a pale trail that shows the last hits before draining away.
- **Focus bar**: look at any creature within 48 blocks for a panel at the top with its name (red for enemies, green for
  others), its life and a green-yellow-red bar that flashes on hits; it fades out a couple of seconds after you look
  away. Options `enemyHealthBar` and `customBossBars` in `terracraft-client.toml`.

### Stage 6: Chlorophyte, Plantera, the Lihzahrd Temple and Golem (verified in a live client)
- **Chlorophyte** (`ChlorophyteContent`): in Hardmode, small veins grow in the underground jungle's mud
  (`HardmodeWorld.placeChlorophyte`, chunk flag `CHLOROPHYTE`); pickaxe power 200 (Pickaxe Axe or better). Bars at
  a Hardmode forge (5 ore). Chlorophyte Claymore (each swing also launches a slow piercing orb -
  `ProjectileSwordItem`), Chlorophyte Shotbow (three arrows at once), Chlorophyte Pickaxe (200) and armor (helmet,
  plate mail, greaves; set bonus **Leaf Crystal**: a crystal over your head shoots leaves at nearby enemies; 3D model).
  Volatile Gelatin and the Leaf Crystal now share one "auto-shoot" helper in `PlayerEvents`.
- **Plantera** (`entity.boss.Plantera`, 30000 life): **Plantera's Bulb** grows on jungle grass in the underground
  jungle near players once all three mechanical bosses are dead (one at a time within 120 blocks, remembered in
  `jungle_growth.dat`); **Life Fruit** plants grow there after the first mechanical boss (`JungleGrowth`).
  Breaking the bulb wakes Plantera right there. Three hooks shoot out on vines and grab the cave walls around the
  player; Plantera can only move as far as its vines reach from where they hold, and the hooks let go one at a time.
  First half: seeds, poison seeds and bouncing thorn balls. Second half: the petals open into a toothed mouth
  (alternate texture), eight tentacles grow around it on vines, it moves faster and fires drifting spores. Leaving
  the jungle enrages it (double speed and damage). Drops the **Temple Key** and one of Seedler (a vine sword that
  throws seeds), Venus Magnum (fast bullets) or Leaf Blower (razor leaves).
- **Tethers**: any creature can now be chained to another (`TerrariaMob.setTether`, synced); the renderer draws vine
  or chain segments between them (Plantera's hooks and tentacles, Golem's fists). `BossPart` is the shared base of
  boss pieces that follow their boss and vanish with it.
- **Hardmode jungle enemies**: Angry Trapper (a bigger Man Eater with a longer reach) and Derpling (a bouncing blue bug).
- **Lihzahrd Temple** (`world.temple`): found once per world (the nearest stretch of jungle 300+ blocks from spawn
  that covers the whole temple) and saved in `temple.dat`; built chunk by chunk as those chunks load, so it also
  appears in old worlds. A 64 x 64 block of Lihzahrd Brick at y -17 to -1: twelve rooms joined by narrow corridors in
  a maze around the central altar chamber, Super Dart Traps in the walls (they watch 12 blocks ahead and shoot poison
  darts at you), Wooden Spikes (60 damage), lamps, chests (60% hold a Lihzahrd Power Cell) and the **Lihzahrd
  Altar**. The way in is a locked **Lihzahrd Door** (Temple Key, used up), reached by a tunnel and a ladder shaft
  from the jungle above. Lihzahrd Brick needs pickaxe power 210 (only the Picksaw). Lihzahrds and Flying Snakes
  spawn on the bricks. `/terraria worldgen temple` says where it is.
- **Golem** (`entity.boss.Golem`, Power Cell in the altar; 39000 life): the stone body hops after the player; its
  head shoots fireballs, and eye lasers once the body is under 3/4 life; when the head's life runs out it breaks free
  (red-hot eyes), can no longer be hurt and flies over the player firing faster; two fists punch out on chains in
  turn. Out of the temple it gets angry. Drops one of Heat Ray (piercing beam), Possessed Hatchet (a homing
  boomerang axe), Sun Stone (by day: +4 defense, +10% damage and melee speed, +2% crit, +1 life regen) or Eye of the
  Golem (+10% crit), a one-in-three **Picksaw** (pickaxe 210, axe 125) and Beetle Husks.
- New 3D models: Plantera (both phases), her hooks and tentacles, Angry Trapper, Derpling, Lihzahrd, Flying Snake,
  Golem's body, head (both looks) and fists, Chlorophyte armor; blocks: Plantera's Bulb, Life Fruit, Lihzahrd Brick,
  Super Dart Trap, Wooden Spikes, Lihzahrd Door and Altar. `/terraria boss spawn plantera|golem`,
  `/terraria worldgen bulb` grows a bulb nearby.
- Fixes: the focus health bar no longer shows on parts that cannot be hurt; boss summons can now wake a boss at a
  fixed spot (`BossSummoning.summonAt`).

### Stage 7: Duke Fishron, the Dungeon after Plantera, the moons, Empress of Light, Martian Madness (verified in a live client)
- **Duke Fishron** (`entity.boss.DukeFishron`, 50000 life): a **Truffle Worm** (a critter in Hardmode mushroom
  biomes - right-click to catch it, `CritterMob`) in your pack while your fishing line sits in ocean water for a couple
  of seconds pulls the Duke out of the sea (`FishronContent`). He hovers beside you and charges in runs of dashes, blows
  rings of homing bubbles, and spits Sharknados - waterspouts that keep throwing Sharkrons at you. Below half life he
  turns furious (red eyes, longer and faster dash runs). Away from the ocean he is enraged (faster, double damage).
  Drops Tsunami (five arrows a shot), Razorblade Typhoon (homing blades), Bubble Gun, and sometimes Fishron Wings.
- **The Dungeon after Plantera** (`DungeonHardmodeContent`): Blue and Hell Armored Bones, Paladins (thrown hammers),
  Skeleton Snipers, Tactical Skeletons, Skeleton Commandos (rockets), Ragged Casters (homing lost souls) and
  Necromancers (bouncing shadowbeams) spawn on Dungeon bricks. Enemies killed in the Dungeon sometimes release a
  **Dungeon Spirit**, which drops **Ectoplasm**. Spectre Bars (Chlorophyte + Ectoplasm) make **Spectre** armor
  (magic; set bonus: magic hits heal you for 8% of the damage). Loot: Paladin's Shield and Hammer, Sniper Rifle,
  Shadowbeam Staff.
- **Pumpkin Moon and Frost Moon** (`world.event.MoonEvents`): wave events, called at night in Hardmode with the
  Pumpkin Moon Medallion or the Naughty Present (both crafted with Ectoplasm). Every kill is worth points (a Scarecrow 5,
  a Pumpking 100); enough points start the next wave, announced in chat with its new creatures, and the event bar
  shows "Pumpkin Moon: Wave 3". Tougher creatures and more bosses join each wave; the moon sets at dawn; reaching the
  last wave (15 / 20) marks it cleared. Pumpkin Moon: Scarecrow, Splinterling, Hellhound, Poltergeist, Headless
  Horseman, **Mourning Wood**, **Pumpking**. Frost Moon: Zombie Elf, Gingerbread Man, Elf Archer, Nutcracker, Yeti,
  Flocko, **Everscream**, **Santa-NK1**, **Ice Queen**. Loot: Stake Launcher, The Horseman's Blade, Bat Scepter,
  Candy Corn Rifle, Christmas Tree Sword, Razorpine, Chain Gun, Elf Melter, North Pole, Blizzard Staff, Spooky Wood.
- **Empress of Light** (`entity.boss.EmpressOfLight`, 70000 life): killing a **Prismatic Lacewing** (rainbow moths in
  the Hallow at night after Plantera) calls her. Prismatic Bolts, Ethereal Lances closing in from a ring around you,
  the turning Sun Dance, dashes, and in her second half the Everlasting Rainbow spiral. In daylight her attacks are
  lethal. Drops Nightglow, Starlight and sometimes the Empress Wings.
- **Martian Madness**: after Golem a **Martian Probe** may drift over a player on the surface by day; if it finishes
  its scan and flies away, martians invade (Gray Grunts, Ray Gunners, Brain Scramblers, Gigazappers, Officers, Drones,
  Scutlix) until 150 kills; once a third are down the **Martian Saucer** comes (lasers, rockets; counts for ten) and
  drops the Influx Waver, Laser Machinegun or Xenopopper.
- Events can now be wave events (`TerrariaEvent.waves`), and event summon items for night events only work at night.
- New 3D models: Duke Fishron (both looks), Sharkron, Truffle Worm, Dungeon Spirit, Poltergeist, Splinterling,
  Hellhound, Mourning Wood, Pumpking, Flocko, Everscream, Ice Queen, Prismatic Lacewing, Empress of Light, Martian
  Probe, Drone, Scutlix, Martian Saucer, Spectre armor; skins for 21 humanoids and skeletons; flat sprites for all.
- Showcase: three new Bestiary rooms, eight more bosses in the Hall of Bosses (now two rows of eleven), moon and
  martian event buttons, Duke Fishron and Empress buttons in the arena, four new kits, Spectre in the Armory.

## IN PROGRESS
- Nothing half-finished. Stage 7 is complete.

## NEXT
1. (done) Stage 5: Hardmode up to the mechanical bosses, Queen Slime, invasions.
2. (done) Stage 6: Chlorophyte, Plantera, Lihzahrd Temple, Golem.
3. (done) Stage 7: Duke Fishron, post-Plantera Dungeon, Pumpkin/Frost Moon, Empress of Light, Martian Madness.
4. Stage 8: Lunatic Cultist, the Celestial Pillars and Moon Lord (Luminite, endgame gear).

## DONE: Stage 4 plan (kept for reference)
1. ~~Dungeon and Skeletron~~ (done, 4a).
2. ~~Jungle and Queen Bee~~ (done, 4b). Original plan: Jungle mapping (vanilla jungles + underground jungle mud/moss, Jungle Spores, Hornets, Man Eaters),
   Queen Bee (bee hives, Abeemination), Jungle armor and gear.
3. ~~Underworld and Wall of Flesh~~ (done, 4c). Original plan: Underworld layer below the caverns (ash, hellstone with 65 power, lava lakes, ruined houses), Imps,
   Demons, Voodoo Demons, Fire Imp, Bone Serpent (worm framework); Hellforge, Molten gear, Hellstone bars.
4. Wall of Flesh (Guide Voodoo Doll, wall that spans the Underworld) and the Hardmode switch.
5. ~~Goblin Army, Goblin Tinkerer, Tinkerer's Workshop, reforging and modifiers~~ (done, 4d).
6. ~~Meteorite, Meteor armor and Space Gun~~ (done, 4e).

## KNOWN BUGS
- The Dungeon only appears in chunks generated after installing this version; in older worlds part of it may be
  missing where chunks already existed (use a new world or unexplored location).
- If every player leaves while Skeletron is alive, the boss stays in its unloaded chunk; the Old Man may
  return meanwhile, and Skeletron resumes (or despawns) once the area is loaded again.
- Prefixes change stats but not projectile size or melee reach ("size" prefixes are cosmetic for now).
- Only the latest meteorite crater spawns Meteor Heads.
- The Underworld is deadly for a fresh character (as in Terraria); tests need Obsidian Skin / fire resistance.
- Boss loot that falls into lava burns (except the Wall of Flesh, which hands its loot to the nearest player).
- Jungle conversion follows the (now merged) vanilla jungle biomes, which are smaller than Terraria's Jungle.
- Worlds created before the Terraria-biomes change keep their Minecraft biomes (the biome source is stored per world).
- Angel/Demon Wings use the normal anvil until Stage 5 adds the Mythril/Orichalcum Anvil; Leaf Wings have no source
  yet (Witch Doctor, Stage 5); Soul of Light drops anywhere underground until the Hallow exists.
- Armor models replace vanilla armor rendering only for TerraCraft armor; vanilla armor still looks vanilla.
- Creature model JSON is read when the game starts (geometry changes need a restart; textures reload with F3+T).
- Locked Gold Chest loot spreads stacks over many slots (vanilla chest loot behaviour).
- Chunks generated before a zone change keep their old layout (zone placement changed during Stage 3
  development; affects only test worlds).
- Evil biomes do not spread yet (pre-Hardmode spread is slow in Terraria; Hardmode spread comes with Stage 5).
- Houses built with commands (no player block placement) are only found after a `/terraria npc housing` or
  Housing Query check inside them.
- Sprites imported from the wiki are single frames (the wiki shows one frame per enemy); facing assumes
  left-facing art, a few Terraria sprites face right and appear mirrored.
- Ore block textures are regenerated with random noise each run of `generate_assets.py` (revert or ignore).
- Tooltips: items without a `.tooltip` lang entry show no flavor line (by design); vanilla tooltip lines
  for non-weapon TerraCraft items (e.g. potions' vanilla effect text) are not hidden yet.
- Water walking is client-predicted; on a strict multiplayer server the vanilla "floating" check could
  kick players who stand on water for long (not observed in testing).
- `/tp` into the Nether is also blocked while `disableNether` is on (blocks all dimension travel there).
- Vanilla mobs that existed before installing the mod (e.g. drowned in loaded chunks) remain until they despawn.

## TECHNICAL DEBT
- Item/art/recipe definitions are split across Java content tables and the two Python generators;
  `generate_assets.py` checks Java ids against its art table but recipes are not cross-checked.
- Station detection scans a 9x7x9 box each refresh while the crafting screen is open (cheap, but could
  cache per chunk section if stations multiply).
- `TerrariaProjectile` saves its kind as a string id; switch to registry-backed kinds if datapack kinds are added.
- Structure removal via biome tags is not config-controlled (tags cannot use load conditions).
- No automated GameTests yet; verification is manual through RCON + headless client scripts.
- Armor worn textures are simple generated layers; boss/mob models still to come.

## TECHNICAL DEBT (Stage 2)
- House candidates are capped at 256 per world and validated lazily (12 flood fills per 5-second pass).
- Bosses use vanilla boss bars; Terraria-style boss HUD and boss music are not implemented.
- Demonite/Crimtane exist only as Eye of Cthulhu drops until evil-biome worldgen (Stage 3).

## CONTENT STILL MISSING
Evil biomes and their bosses, Dungeon/Skeletron, Underworld/Wall of Flesh, Jungle/Queen Bee, most enemies,
most NPCs, events, hardmode transformation, modifiers/reforging, summon weapons/minions, wings/grappling
hooks/dashes, fishing. See TERRARIA_CONTENT_CHECKLIST.md.

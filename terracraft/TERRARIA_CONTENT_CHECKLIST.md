# Terraria content checklist

Legend: `[ ]` not started, `[-]` partial, `[x]` complete (implemented and usable in game).

## Core systems
- [x] Terraria life (100 base, Life Crystal, Life Fruit, hearts HUD, natural regeneration)
- [x] Mana (crystals, regeneration, costs, stars HUD, sync)
- [x] Damage classes (melee, ranged, magic, summon) with class bonuses and crits
- [x] Defense, endurance, armor penetration, difficulty-based defense
- [x] Projectile framework
- [x] Coins and currency conversion, enemy coin drops, softcore death penalty
- [x] Accessory slots + equipment screen
- [x] Armor sets with set bonuses
- [x] Buff framework (MobEffect + stat bundle)
- [x] Terraria crafting (stations, recipe JSON, crafting screen)
- [x] Pickaxe power
- [x] World progression flags, conditions, announcements
- [x] Vanilla progression bypass removal (configurable)
- [x] Enemy framework (AI archetypes: slime, walker, flyer chaser/erratic, splitting)
- [x] Spawn system (biome/depth/time/sky/progression; events pending)
- [x] Boss framework
- [x] NPC framework, housing, shops
- [-] Event framework (Blood Moon, Slime Rain done; invasions and moons pending)
- [x] Biome spread (Corruption/Crimson/Hallow; Hardmode only, near players)
- [x] Hardmode world transformation (Hallow + evil V-stripes, altar ore blessings)
- [ ] Modifiers / reforging
- [ ] Minions / sentries / whips
- [-] Wings (flight, glide, 3D wings; Fledgling, Angel, Demon, Leaf) done; grappling hooks, dashes, rocket boots pending
- [x] No hunger: food gives Well Fed / Plenty Satisfied / Exquisitely Stuffed; no boots slot
- [ ] Fishing

## Bosses
Pre-hardmode
- [x] King Slime
- [x] Eye of Cthulhu
- [x] Eater of Worlds
- [x] Brain of Cthulhu
- [x] Queen Bee
- [ ] Deerclops
- [x] Skeletron (Old Man curse, head + hands, day enrage)
- [x] Wall of Flesh (Guide Voodoo Doll in Underworld lava; starts Hardmode)

Hardmode
- [x] Queen Slime (Gelatin Crystal in the Hallow; flying second phase, Crystal/Bouncy/Heavenly Slimes, Crystal Assassin armor, Volatile Gelatin)
- [x] The Destroyer (Mechanical Worm; shared life, lasers, Probes)
- [x] The Twins (Mechanical Eye; Retinazer lasers, Spazmatism cursed flames, mechanical second forms)
- [x] Skeletron Prime (Mechanical Skull; Cannon, Saw, Vice, Laser arms)
- [x] Plantera (bulb in the underground jungle; hooks on vines, mouth phase with tentacles, seeds/thorn balls/spores, Temple Key)
- [x] Golem (Lihzahrd Altar + Power Cell; body, head that breaks free, chained fists; Picksaw, Heat Ray, Possessed Hatchet, Sun Stone, Eye of the Golem)
- [x] Duke Fishron (Truffle Worm bait in the ocean; dashes, bubbles, Sharknados, furious phase; Tsunami, Razorblade Typhoon, Bubble Gun, Fishron Wings)
- [x] Empress of Light (Prismatic Lacewing; bolts, lances, Sun Dance, Everlasting Rainbow, daylight fury; Nightglow, Starlight, Empress Wings)
- [x] Lunatic Cultist (Cultist Devotees at the Dungeon after Golem; fireballs, lightning, Ice Mist, Ancient Light, the Ritual with clones; drops the Ancient Manipulator)
- [x] Celestial Pillars (Solar, Vortex, Nebula, Stardust; shields worn down by killing their guards; fragments)
- [x] Moon Lord (Celestial Sigil or after the four pillars; two hand eyes and the head eye with the Phantasmal Deathray, then the exposed heart; Luminite, Meowmere, Star Wrath, S.D.M.G., Last Prism, Lunar Flare)

## Enemies
- [-] Slimes (Green, Blue, Red, Purple, Yellow, Black, Mother, Baby done; Jungle, Ice, Sand, Lava, Spiked... pending)
- [-] Zombie, Demon Eye, Servant of Cthulhu (variants pending)
- [ ] Flying Fish, Piranha, Shark, Crab
- [-] Jungle: Hornet, Man Eater, Snatcher, Jungle Bat, Jungle Slime, Angry Trapper, Derpling done; Piranha, Giant Tortoise, Moth pending
- [x] Lihzahrd Temple: Lihzahrd, Flying Snake
- [x] Pumpkin Moon (Scarecrow, Splinterling, Hellhound, Poltergeist, Headless Horseman, Mourning Wood, Pumpking) and Frost Moon (Zombie Elf, Gingerbread Man, Elf Archer, Nutcracker, Yeti, Flocko, Everscream, Santa-NK1, Ice Queen) with waves
- [x] Martian Madness (Martian Probe, Gray Grunt, Ray Gunner, Brain Scrambler, Gigazapper, Martian Officer, Drone, Scutlix, Martian Saucer)
- [x] Critters: Truffle Worm, Prismatic Lacewing
- [x] Celestial pillar guards: Selenian, Sroller, Corite (Solar); Storm Diver, Alien Hornet, Vortexian (Vortex); Nebula Floater, Brain Suckler, Predictor (Nebula); Star Cell, Flow Invader, Twinkle Popper (Stardust)
- [ ] Desert: Antlion, Antlion Charger/Swarmer, Vulture, Tomb Crawler
- [ ] Snow/Ice: Ice Slime, Ice Bat, Undead Viking, Snow Flinx
- [-] Caverns: Skeleton, Cave Bat done; Giant Worm, Granite/Marble, spiders pending
- [-] Corruption: Eater of Souls, Devourer done; Hardmode corruption enemies pending
- [-] Crimson: Crimera, Face Monster, Blood Crawler done; Hardmode crimson enemies pending
- [-] Dungeon: Angry Bones, Dark Caster, Cursed Skull, Dungeon Slime, Dungeon Guardian done; post-Plantera Armored Bones, Paladin, Skeleton Sniper/Tactical/Commando, Ragged Caster, Necromancer, Dungeon Spirit done
- [x] Underworld: Fire Imp, Demon, Voodoo Demon, Bone Serpent, Lava Slime, Hellbat
- [x] Hardmode surface/underground/Hallow/evil enemies (Pixie, Unicorn, Gastropod, Illuminant Bat/Slime, Chaos Elemental,
      Corruptor, Slimer, Herpling, Crimslime, Floaty Gross, Wraith, Possessed Armor, Werewolf, Wyvern, Armored Skeleton,
      Giant Bat, Mimic)
- [x] Goblin Army: Peon, Thief, Warrior, Archer, Sorcerer
- [x] Meteor Head
- [-] Event enemies: Pirates (+ Flying Dutchman) and Frost Legion done; Martians, Pumpkin/Frost Moon done; Solar Eclipse open

## NPCs
- [x] Guide, Merchant, Nurse, Demolitionist
- [-] Dryad, Arms Dealer done; Dye Trader, Angler, Zoologist, Painter, Golfer, Tavernkeep pending
- [x] Old Man, Clothier
- [x] Goblin Tinkerer (rescued in the caverns; reforging)
- [x] Wizard (found bound in Hardmode caverns), Witch Doctor (after Queen Bee; Leaf Wings in Hardmode)
- [-] Mechanic done (bound in the Dungeon after Skeletron, sells redstone wiring); Stylist, Party Girl open
- [x] Steampunker (after a mechanical boss; Clentaminator and solutions)
- [-] Pirate done (after the Pirate Invasion); Tax Collector, Truffle, Cyborg, Santa Claus, Princess open

## Weapons
Melee
- [x] Wooden Sword, Copper Shortsword
- [x] Copper/Tin/Iron/Lead/Silver/Tungsten/Gold/Platinum Broadsword
- [-] Light's Bane, Blood Butcherer, Muramasa done; Blade of Grass, Bee Keeper, Fiery Greatsword, Breaker Blade done; Night's Edge, Volcano, spears, flails, yoyos pending
- [-] Hardmode and endgame melee: ore swords, Excalibur, Chlorophyte Claymore, event and boss swords done; Solar Eruption, Daybreak, Meowmere, Star Wrath done; others pending
Ranged
- [x] Wooden/Copper/Iron/Gold Bow
- [x] Flintlock Pistol
- [-] Thrown: Shuriken, Throwing Knife (done); others not started
- [-] Demon/Tendon Bow, Musket, The Undertaker, Handgun, The Bee's Knees, Molten Fury, Phoenix Blaster, Hellwing Bow done; other bows/guns pending
Magic
- [x] Wand of Sparking, Amethyst Staff, Magic Missile
- [-] Vilethorn, Water Bolt, Aqua Scepter, Book of Skulls, Bee Gun, Space Gun, Flamelash, Flower of Fire, Demon Scythe, Laser Rifle done; other gem staves, tomes, magic guns pending
Summoner
- [ ] Summon staffs, sentries, whips (Stardust Dragon/Cell Staff exist as homing magic stand-ins until minions are built)
Boomerangs
- [x] Wooden Boomerang
- [ ] Enchanted Boomerang, Flamarang...

## Ammunition
- [x] Wooden Arrow (vanilla arrow), Flaming Arrow, Musket Ball
- [ ] Other arrows, bullets, rockets, darts

## Tools
- [x] Copper/Tin/Iron/Lead/Silver/Tungsten/Gold/Platinum Pickaxe and Axe
- [x] Copper Hammer, Iron Hammer
- [-] Nightmare/Deathbringer Pickaxe, War Axe of the Night, Blood Lust Cluster, The Breaker, Flesh Grinder done; Molten Pickaxe, Molten Hamaxe, Pwnhammer done; other Hardmode tools pending
- [ ] Grappling hooks

## Armour
- [x] Wood, Copper, Tin, Iron, Lead, Silver, Tungsten, Gold, Platinum
- [-] Shadow, Crimson, Jungle, Molten, Meteor done; Mining, Ninja, Fossil, Necro, Bee pending
- [-] Cobalt to Titanium, Hallowed, Crystal Assassin, Chlorophyte, Spectre, Solar Flare, Vortex, Nebula, Stardust done; Turtle, Beetle, Shroomite, Spooky pending

## Accessories
- [x] Hermes Boots, Cloud in a Bottle, Shiny Red Balloon, Lucky Horseshoe
- [x] Band of Regeneration, Band of Starpower, Mana Regeneration Band, Nature's Gift
- [x] Shackle, Aglet, Anklet of the Wind, Feral Claws, Obsidian Skull, Lava Charm, Cobalt Shield
- [x] Flipper, Water Walking Boots, Toolbelt, Panic Necklace, Honey Comb, Warrior/Ranger/Sorcerer/Summoner Emblem
- [-] Tinkerer's Workshop combinations: Obsidian Horseshoe, Cloud in a Balloon, Obsidian Shield, Obsidian Water Walking Boots, Lava Waders, Mana Flower done; wings, dashes and the rest pending

## Prefixes
- [x] Reforging at the Goblin Tinkerer, 72 prefixes (universal, melee, ranged, magic, accessory), random prefix on craft

## Ores and bars
- [x] Copper, Iron, Gold (vanilla ores/ingots)
- [x] Tin, Lead, Silver, Tungsten, Platinum (ore, deepslate ore, raw, bar)
- [x] World ore-pair choice (worldgen honours it)
- [x] Demonite/Crimtane ore (worldgen + bosses) and bars; Hellstone done (Hellforge); Meteorite done (craters); Obsidian gate done
- [x] Cobalt/Palladium, Mythril/Orichalcum, Adamantite/Titanium (ore, raw, bar, pickaxe, sword, repeater, armor)
- [x] Hallowed Bar (mechanical bosses): Excalibur, Hallowed Repeater, Pickaxe Axe, Hallowed armor; Souls of Might/Sight/Fright
- [x] Chlorophyte (underground jungle mud in Hardmode; Claymore, Shotbow, Pickaxe, armor)
- [x] Luminite (from Moon Lord; Luminite Bar at the Ancient Manipulator; fragment armors, picks, wings, weapons)

## Blocks
- [x] Work Bench, Iron Anvil, Lead Anvil, Life Crystal (block)
- [-] Ebonstone, Crimstone, evil grasses, Ebonwood, Shadewood done; Blue/Green/Pink Dungeon Brick, Spikes, Locked Gold Chest, Jungle Grass, Jungle Spores, Hive, Larva, Ash, Hellstone, Obsidian/Hellstone Brick, Hellforge, Shadow Chest done (Mud is vanilla); Pearlstone, Crystal Shards, Lihzahrd Brick, Super Dart Trap, Wooden Spikes, Lihzahrd Door/Altar, Plantera's Bulb, Life Fruit done

## Biomes
- [x] Terraria biomes only: Minecraft-only biomes are mapped onto Forest/Snow/Desert/Jungle/Ocean (`TerrariaBiomeSource`)
- [x] Forest (vanilla forest)
- [-] Desert (vanilla desert); Underground Desert pending
- [-] Snow (snowy plains and boreal snowy taiga); Ice caverns pending
- [x] Jungle / Underground Jungle (mud, jungle grass, spores; on vanilla jungle biomes)
- [x] Ocean (vanilla oceans and beaches)
- [x] Corruption / Crimson (worldgen, chasms, orbs/hearts, altars; spread pending)
- [-] Glowing Mushroom (vanilla mushroom fields; underground glowing mushroom caves pending)
- [x] Dungeon (one per world, seeded location; Golden Keys, locked chests)
- [x] Underworld (ash cavern below y=-40, lava sea, hellstone, ruined houses)
- [x] Hallow (pearlstone, hallowed grass, pearlsand, pearlwood, hallowed leaves, enemies)
- [ ] Space / floating islands
- [-] Terraria depth layers (`TerrariaLayer` height bands defined)

## Structures
- [x] Dungeon (entrance tower, 4 levels of rooms, halls and ladder shafts)
- [x] Jungle Temple (Lihzahrd Temple: maze of rooms, traps, locked door, altar chamber; built in old worlds too)
- [ ] Floating Islands
- [ ] Living Trees
- [-] Underground loot chests (cabins pending)
- [x] Bee hives (Larva summons Queen Bee)
- [ ] Pyramids
- [x] Underworld ruins (Hellforges, Shadow Chests)
- [ ] Granite/Marble caves, spider caves, enchanted sword shrines
- [x] Villages, outposts, mansions, strongholds removed

## Events
- [x] Blood Moon
- [x] Slime Rain
- [x] Goblin Army (invasion with progress bar; Goblin Battle Standard)
- [x] Pirate Invasion (Pirate Map; Flying Dutchman; Cutlass, Gold Ring, Lucky Coin, Discount Card, Coin Gun)
- [x] Frost Legion (Snow Globe from Presents)
- [ ] Solar Eclipse
- [x] Martian Madness
- [x] Pumpkin Moon
- [x] Frost Moon
- [x] Celestial Events (four pillars, then Moon Lord)
- [ ] Old One's Army (crossover, low priority)

## Potions
- [x] Lesser Healing, Healing, Lesser Mana, Mana (with Potion/Mana Sickness)
- [x] Ironskin, Swiftness, Regeneration, Mana Regeneration, Magic Power, Archery, Mining, Obsidian Skin,
      Water Walking, Endurance, Wrath, Rage
- [ ] Spelunker, Shine, Night Owl, Featherfall, Gravitation, Hunter, Thorns, Battle, Calming, Lifeforce...
- [-] Potion recipes (healing/mana done; herb-based buff recipes need herbs from Stage 2)

## Crafting stations
- [x] Work Bench, Furnace (vanilla furnace/blast furnace), Iron/Lead Anvil (+ vanilla anvils),
      Alchemy (brewing stand as Placed Bottle stand-in), Loom (vanilla loom)
- [x] Hellforge, Demon/Crimson Altar, Tinkerer's Workshop, Mythril/Orichalcum Anvil, Adamantite/Titanium Forge
- [x] Ancient Manipulator
- [ ] Sawmill, Placed Bottle, Crystal Ball...

## Materials
- [x] Gel, Lens, Fallen Star (falling at night), Amethyst, Life Crystals in caves
- [x] Bars and raw metals listed above
- [-] Souls, Ectoplasm, Celestial Fragments, Luminite done; herbs, mushrooms, other gems pending

## Consumables / items
- [x] Life Crystal, Life Fruit, Mana Crystal
- [x] Coins
- [-] Slime Crown, Suspicious Looking Eye, Worm Food, Bloody Spine, Housing Query, Purification Powder done; Demon Heart, keys, wormhole, recall pending

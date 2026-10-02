# TerraCraft

**Terraria rebuilt as a 3D Minecraft total conversion.** A single standalone Forge mod (no dependencies)
for **Minecraft Java 26.2 / Forge 65.1.0** that turns Minecraft into a Minecraft-style recreation of
Terraria: Terraria's progression, health/mana, combat classes, equipment, crafting stations, ores,
coins, NPCs, bosses, events and biomes, built inside Minecraft's 3D world.

All art is original: textures are generated procedurally by `tools/generate_assets.py`. No Terraria
assets are used or redistributed.

> Status: **Stage 0 (foundation) and Stage 1 (core Terraria systems) are complete and verified in-game.**
> See [DEVELOPMENT_PROGRESS.md](DEVELOPMENT_PROGRESS.md) and
> [TERRARIA_CONTENT_CHECKLIST.md](TERRARIA_CONTENT_CHECKLIST.md).

## Building

Requirements: JDK 25 (Gradle provisions it automatically through the foojay toolchain resolver), internet
access for the first build (Minecraft, Forge and assets are downloaded).

```bash
./gradlew build          # produces build/libs/terracraft-<version>.jar (the one mod jar)
./gradlew runClient      # development client
./gradlew runServer      # development dedicated server (run/)
```

Install: drop `build/libs/terracraft-*.jar` into the `mods` folder of a Forge 65.1.0 (MC 26.2) installation.

## Regenerating assets and data

Textures, models, item definitions, language file, recipes, tags, loot tables and vanilla overrides are
generated from Python tables (Pillow required: `pip install pillow`):

```bash
python3 tools/generate_assets.py   # textures + client JSON
python3 tools/generate_data.py     # datapack JSON + en_us.json (reads the cached MC client jar)
```

`generate_assets.py` fails if an item registered in Java has no art recipe, keeping both in sync.

## Playing: what is different from vanilla

* **Life**: you start with 100 life (Terraria numbers); Life Crystals add 20 (max 15), Life Fruit 5 (max 20).
  Hearts are Terraria hearts (20 life each). Vanilla food healing is replaced by Terraria's natural
  regeneration; a full food bar counts as *Well Fed*.
* **Mana**: 20 to start, Mana Crystals add 20 (max 9). Regenerates faster when standing still.
* **Combat**: melee, ranged, magic and summon damage classes; Terraria crits (4% base, x2); defense
  subtracts half its value from damage (Classic). Hold left click to fire ranged/magic/thrown weapons.
* **Equipment**: press **R** (or the *Equip* button in the inventory) for armor + 5 accessory slots.
  Armor sets grant set bonuses.
* **Crafting**: press **V** (or the *Craft* button) near crafting stations (Work Bench, Furnace, Anvil,
  Alchemy...) to see what you can craft with Terraria recipes. Vanilla crafting still exists for vanilla
  building blocks.
* **Mining**: pickaxes have Terraria pickaxe power; some blocks require more (Obsidian 55%).
* **Coins**: copper/silver/gold/platinum, auto-converting. Enemies drop coins; dying drops half your coins
  but you keep your items (softcore).
* **Removed bypasses** (all configurable in `config/terracraft-common.toml`): villagers, wandering
  traders, villages/outposts/mansions/strongholds, natural vanilla hostile spawns, Nether/End travel,
  enchanting, brewing, diamond and netherite gear.

## Developer tools

* `/terraria worldstate | progression set <flag> <bool> | hardmode <bool> | evil <corruption|crimson>`
* `/terraria stats [player] | set lifecrystals|lifefruit|manacrystals|accessoryslots <n> | give mana <n> | heal`
* `/terraria devmenu` or the **Developer Tablet** item (TerraCraft: Developer tab) opens a menu to toggle
  every progression flag, change time and edit player upgrades.
* `/terraria biome debug`, `/terraria recipecheck <id>`

Automated in-game testing (headless): see [docs/TESTING.md](docs/TESTING.md).

## Project layout

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

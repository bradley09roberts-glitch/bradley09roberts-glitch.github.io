# Install and upgrade — 0.3.0 (gameplay overhaul)

## What you need

- Minecraft Java Edition **1.21.1** with **NeoForge 21.1.259 or newer** (installer: https://neoforged.net).
- Java 21 (the Minecraft launcher bundles it).
- Two jars from `dist/`:
  - `grandline-0.3.0.jar` — the mod
  - `geckolib-neoforge-1.21.1-4.9.3.jar` — required library (unchanged since 0.1.0)

## New install (single player)

1. Run the NeoForge installer and choose **Install client**.
2. Put both jars in `.minecraft/mods`.
3. Start the NeoForge 1.21.1 profile, **Create New World**. World Type already shows *Grand Line Ocean World*; leave it.
4. Create your character. Type `/controls` any time for every key; the elder on your starter island teaches the
   basics.

Difficulty: the mod follows the world's difficulty (Easy = Adventure, Normal = Standard, Hard = Hard). To fix it
regardless, set `[balance] difficulty` in `saves/<world>/serverconfig/grandline-server.toml`.

## Upgrading from 0.1.0 or 0.2.0

1. **Back up your world** (copy `.minecraft/saves/<world>` somewhere safe). Keep the old jar too.
2. Remove `grandline-0.2.0.jar` (or `0.1.0`) from `mods/` and put `grandline-0.3.0.jar` in its place. GeckoLib stays.
3. Load the world. Foes already there are re-tuned as they load, your quests carry on from the equivalent step of the
   new chapters, and your crew keeps following (the first two active, the rest in reserve). Details:
   [SAVE_COMPAT.md](SAVE_COMPAT.md).

To go back, restore the copy and the old jar (`dist/previous/`).

## Dedicated server / LAN

1. `java -jar neoforge-21.1.259-installer.jar --installServer .` in an empty folder (or on the existing server after
   a backup).
2. Put both jars in `mods/`. For a new world, `server.properties` must say `level-type=grandline\:ocean_world` before
   the first start. `eula.txt`: `eula=true`.
3. `-Xmx4G` or more in `user_jvm_args.txt`; start with `run.sh` / `run.bat`. Every player needs the same two jars.

## Optional shaders

Unchanged from 0.2.0: Sodium 0.6.13 and Iris 1.8.12 for NeoForge 1.21.1 in `mods/`, and
`dist/GrandLineShaders-1.0.0.zip` in `shaderpacks/` (see [OVERHAUL.md](OVERHAUL.md)).

## Checking it works

Follow [TEST_ROUTE.md](TEST_ROUTE.md) (about an hour), or at least: `/controls` opens the controls page; looking at a
bandit shows the target frame; `/spar` starts a training bout; **U** shows the chapter in the journal.

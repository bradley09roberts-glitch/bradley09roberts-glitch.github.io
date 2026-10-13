# Kingdoms of the Long Night

A hardcore-survival fantasy-kingdom modpack for **Minecraft Java Edition 1.21.1 (NeoForge 21.1.259)**, built around a
custom simulation mod, **`longnight`**. You wake on a road in **the Haldmarch**, a land that already has history:
realms with rulers and old grudges, towns full of named people with families, trades and memories, outlaw bands on
the roads, ruins of kingdoms nobody remembers, and a night that is slowly getting longer.

It is still Minecraft: you build, explore, mine, craft and survive, with one life on Hardcore. The difference is
that the world's people live without you. They farm, fell trees, raise buildings block by block, marry, have
children, grow old, die and are buried under their own names. News travels by mouth and caravan. Rulers die and are
succeeded. Outlaws raid, with warning. You can be a stranger, a trader, a citizen, a bounty hunter or the founder of
your own settlement.

> **Status: first playable release (v0.1.0), built and tested overnight.** It is a real foundation, not the full
> vision. Read [docs/STATUS.md](docs/STATUS.md) for exactly what works, what is simplified and what is planned.

## What you get

| Path | What it is |
|---|---|
| `dist/KingdomsOfTheLongNight-0.1.0-performance.mrpack` | **Start here.** The modpack (37 mods + `longnight`), no shaders. 6 GB RAM. |
| `dist/KingdomsOfTheLongNight-0.1.0-rich.mrpack` | Same world mods plus Iris + Complementary Reimagined shaders, Distant Horizons, Sound Physics. 8 GB RAM, decent GPU. |
| `dist/longnight-0.1.0.jar` | The core mod alone (for adding to your own NeoForge 1.21.1 instance). |
| `mod/` | Source code of the core mod (Java 21, NeoForge ModDevGradle). |
| `modpack/` | Pack manifest, configs, [INSTALL.md](modpack/INSTALL.md), [MODS.md](modpack/MODS.md), [VERIFICATION.md](modpack/VERIFICATION.md). |
| `docs/` | [STATUS](docs/STATUS.md) (working / partial / planned, tests, known bugs), [DESIGN](docs/DESIGN.md) (the complete systems design), [ROADMAP](docs/ROADMAP.md), [WORLD_BIBLE](docs/WORLD_BIBLE.md) (setting and lore), [ARCHITECTURE](docs/ARCHITECTURE.md), [NOTES](docs/NOTES.md) (decisions and work log). |
| `tools/` | Pack builder, test harnesses (server scenario over RCON, GameTests, headless client screenshots), texture and resource generators. |

## Install and launch (short version)

Full step-by-step guide: [modpack/INSTALL.md](modpack/INSTALL.md).

1. Install **[Prism Launcher](https://prismlauncher.org)** (free, open source) or the **Modrinth App**, and sign in with your Microsoft account.
2. Make sure **Java 21** is available (Prism: Settings -> Java -> Auto-detect / Download).
3. **Add Instance -> Import** -> choose `KingdomsOfTheLongNight-0.1.0-performance.mrpack`. The launcher downloads
   Minecraft 1.21.1, NeoForge 21.1.259 and every mod from Modrinth (hash-checked). This is a separate instance:
   your existing Minecraft installs and saves are untouched.
4. Instance settings -> Memory: **6144 MB** (rich preset: 8192 MB).
5. Launch -> **Singleplayer -> Create New World -> Game Mode: Hardcore**, World Type: Default.
   Minecraft may warn about "Experimental Settings" (the terrain mods use data-driven worldgen): click
   "I know what I'm doing".
6. The first world takes a little longer to create: the mod surveys the land and writes the world's history.

## Your first hour

* You start with a **Traveller's Journal** and **A Traveller's Primer** (a short in-world guide to everything below).
  Right-click the journal: it lists the places you have heard of, with distance and direction. You know two
  settlements to begin with; you learn of more by asking people for news.
* **Look at a person** to see their name, trade and what they are doing on the action bar.
* Walk to a settlement (roads link neighbouring settlements; signposts at a town's edge name where each road goes).
  When you arrive you are told what it is, who rules it and how many souls live there.
* **Right-click people** to talk. Click the topics in chat: **News** (what they have heard, and only what they have
  heard), **Work** (their trade, what blocks them, what the town needs), **Family**, **Life**, **Town**.
* **Hearthstone** (the carved stone by the well): the settlement's overview, its chronicle and its people.
* **Storehouse ledger**: right-click to see stock and prices and buy; **use an item on it to sell**, **sneak-use to
  give** it freely. Prices follow supply and demand; nothing can be duplicated by trading back and forth.
* **Notice board**: local news, wanted goods, and **contracts** (deliveries a stalled build needs, winter fuel...) and
  **bounties** on outlaw bands. Click [Accept], then deliver through the storehouse.
* **Outlaws** raid settlements, but always with warning: rumours first, then scouts and smoke a day ahead, then the
  bells at dusk. Defend the town, or be elsewhere.
* Earn standing by trading, giving and defending; at "welcome" (20) you can ask to **Join** a settlement as a
  citizen (fair prices, news of its troubles).
* **Boundary Stone**: place it to claim 32 blocks around it. Settlements never build, fell trees or clear land inside
  a claim, and builders refuse any plot with something a player built on it.
* **Found your own settlement**: craft a **Founding Charter** (3 paper, gold ingot, ink sac, wool), carry it, and
  place a **Hearthstone** (8 stone bricks around a campfire) far from other towns. Three settlers answer the charter;
  more come if there is food and shelter. They plan and build for themselves; you steer with materials, coin and
  `/longnight plan <building>`.
* **Ward Stone** + **Ward Chalk** (bone meal + flint): kindled wards stop the dead and other night-spawn rising
  within 24 blocks for three days.

All chat buttons are ordinary commands (`/longnight ...`, alias `/ln`), so they work with commands disabled.
`/longnight help` lists the player commands.

## Settings and performance

Each world has its own settings file, `saves/<world>/serverconfig/longnight-server.toml` (created on first start;
edit it with the game closed). The useful ones: `daysPerYear` (default 8: one in-game day is a month; raise it for
slower lives), `raids` and `firstRaidDay`, `wars`, `embodyDistance` (how near you must be for townsfolk to have
bodies, default 112 blocks), `maxBodiesPerSettlement` (40) and `maxBodiesTotal` (160). On a slow PC lower the body
caps first. Settings that shape a new world (`startingSettlements`, `worldRadius`, ...) must be in place before
the world is created: copy the file into the instance's `defaultconfigs/` folder and every new world starts with it.
`/longnight admin perf` (operators) shows what the mod costs per server tick.

## Building from source

```
cd mod
./gradlew build                 # -> build/libs/longnight-0.1.0.jar
./gradlew test                  # JUnit simulation tests
./gradlew runGameTestServer     # in-world GameTests
./gradlew runServer / runClient # development runs
python3 -I ../tools/pack/build_mrpack.py 0.1.0       # rebuild both .mrpack files (picks up the jar)
python3 -I ../tools/test/server_scenario.py --days 4 # scripted dedicated-server scenario over RCON
```

Java 21 and network access to maven.neoforged.net / Mojang / Modrinth are required.

## Licences

`longnight` source, data, textures and documentation in this folder: MIT. Third-party mods are **not**
redistributed: the `.mrpack` files only reference them on Modrinth's CDN, each under its own licence (see
[modpack/MODS.md](modpack/MODS.md)).

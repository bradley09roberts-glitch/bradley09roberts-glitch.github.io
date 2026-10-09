# Hardcore Friends

A Fabric mod for Minecraft Java **26.3** that adds nine human companions to your Hardcore world. Each has their own skin, personality and job: Fern (farmer), Oak (builder), Flint (miner), Scout (explorer), Spark (redstone inventor), Aegis (warrior), Sage (strategist), Terra (landscaper) and Rowan (forager).

They choose useful work on their own, cooperate through a shared supply chest, grow a Unity bond, and build a camp into a settlement using real materials. In 2.0 they also live on while you are away, go on trips, recruit newcomers you meet in the world, follow Sage's plan through diamonds, enchanting and the Nether, and help you beat the dragon. Player Hardcore rules are never changed.

> **2.0 has not been played yet.** It compiles and every part was reviewed against the game's own code, but nobody has tried it in game. Test in a copy of your world first. 1.0's test results are in `release/TEST-RESULTS.md`.

## Features

- **Nine friends**, each with their own skin, personality, speaking voice and speciality.
- **Everyone pitches in.** Any friend can do any job. Specialists go first and work fastest; when a specialist is missing, the others stand in, their second interest first.
- **Needs and mood, Sims-style.** Hunger, energy, social, fun and comfort. Friends eat real food from their backpack or the supply chest, sleep at night, chat with each other, take short breaks for a pastime and warm up by the fire, all on their own. Their mood speeds up or slows down their work and shows in what they say.
- **Safe nights.** At nightfall work stops and friends go to bed, while a rota keeps someone on watch (Aegis first, then a rested, armed friend). The watch raises the alarm when a monster comes into camp, sleepers wake, and armed friends come running, so nobody fights alone.
- **Real materials.** Everything they build or craft uses real items from the supply chest, and their tools wear out.
- **A growing camp**, from campsite to settlement in five stages, built from fixed blueprints.
- **A Unity bond** that grows with time together, teamwork, chats and a happy team, and unlocks modest bonuses.
- **Hardcore stays hardcore.** Friends can die. Starving hurts them down to one heart. Nothing changes your own death.
- **Bounded, logged world edits** that never touch your builds.
- **Commands that work without cheats**, including `/friends needs` to see everyone's needs as bars.

New in 2.0 (details in [GUIDE.md](GUIDE.md) sections 15-20 and [docs/v2](docs/v2)):

- **Independence.** The camp keeps running while you are online anywhere; Scout explores far afield; friends trade at villages, build shelters when caught out at night, level ground to make room for a building, and get more skilled with practice.
- **Gear and fighting.** Armour, shields and swords for everyone, a smith, bows with a safe line of fire, emergency healing, teamwork.
- **Newcomers.** Strangers in villages, at new survivor camps and on the road, each with their own name and look, who join after a small request.
- **Sage's plan to beat the game.** A deep diamond mine with lava safety, obsidian, a library with an enchanting table, enchanting, an anvil and brewing.
- **Expeditions you lead.** Parties that follow you through portals, bartering, blaze hunting, finding the stronghold with eyes of ender, filling the End portal, and the dragon fight.
- **Several players.** An owner and trusted players, bonds, a job board, mourning and keeping a fallen player's things safe, notes, mailbox deliveries and opt-in siege nights.

## Documentation

- **Players:** read [GUIDE.md](GUIDE.md).
- **Developers:** read [docs/DEVELOPING.md](docs/DEVELOPING.md) and [docs/DESIGN.md](docs/DESIGN.md).

## Build

Java 25 is required.

```bash
./gradlew build                      # mod JAR in build/libs/, runs the server game tests
xvfb-run -a ./gradlew runClientGameTest   # in-game client tests with screenshots (Linux/headless)
python3 tools/package_release.py     # release/ JAR, CurseForge import ZIP and checksums
```

## Versions

| Component | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.162.0+26.3 |
| Loom | 1.18 |
| Gradle | 9.7.1 |

## Layout

| Path | Contents |
|---|---|
| `src/main/java/.../companion` | The companion entity, backpack, speech and dialogue, needs and mood, specialities, and the friend roster |
| `src/main/java/.../ai` | Reflex goals, the task scheduler, shared upkeep tasks, needs jobs, and each role's routines |
| `src/main/java/.../camp` | Camp data, supply chest, crafting, settlement plan, blueprints, needs |
| `src/main/java/.../world` | `WorldEditGuard` (bounded, build-preserving block changes) and natural tree detection |
| `src/main/java/.../unity` | The Unity bond |
| `src/main/java/.../combat` | 2.0: gear, the smith, bows, shields, healing, teamwork |
| `src/main/java/.../survival` | 2.0: keeping the camp loaded, trips, trading, levelling, shelters, skills |
| `src/main/java/.../settler` | 2.0: newcomers, survivor camps (world generation), recruiting |
| `src/main/java/.../progress` | 2.0: Sage's plan, deep mining, obsidian, books, enchanting, anvil, brewing |
| `src/main/java/.../expedition` | 2.0: following through portals, the Nether, the stronghold, the End |
| `src/main/java/.../town` | 2.0: trust, bonds, the job board, mourning, notes, mailboxes, siege nights |
| `src/client/java` | Renderer (wide player model with all outer layers) |
| `src/gametest` | Server game tests and in-game client tests (Hardcore worlds) |

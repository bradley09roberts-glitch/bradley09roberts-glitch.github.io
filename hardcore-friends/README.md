# Hardcore Friends

A Fabric mod for Minecraft Java **26.3** that adds nine human companions to your Hardcore world. Each has their own skin, personality and job: Fern (farmer), Oak (builder), Flint (miner), Scout (explorer), Spark (redstone inventor), Aegis (warrior), Sage (strategist), Terra (landscaper) and Rowan (forager).

They choose useful work on their own, cooperate through a shared supply chest, grow a Unity bond, and build a camp into a settlement using real materials. Player Hardcore rules are never changed.

## Features

- **Nine friends**, each with their own skin, personality, speaking voice and speciality.
- **Everyone pitches in.** Any friend can do any job. Specialists go first and work fastest; when a specialist is missing, the others stand in, their second interest first.
- **Needs and mood, Sims-style.** Hunger, energy, social, fun and comfort. Friends eat real food from their backpack or the supply chest, sleep at night, chat with each other, take short breaks for a pastime and warm up by the fire, all on their own. Their mood speeds up or slows down their work and shows in what they say.
- **Real materials.** Everything they build or craft uses real items from the supply chest, and their tools wear out.
- **A growing camp**, from campsite to settlement in five stages, built from fixed blueprints.
- **A Unity bond** that grows with time together, teamwork, chats and a happy team, and unlocks modest bonuses.
- **Hardcore stays hardcore.** Friends can die. Starving hurts them down to one heart. Nothing changes your own death.
- **Bounded, logged world edits** that never touch your builds.
- **Commands that work without cheats**, including `/friends needs` to see everyone's needs as bars.

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
| `src/client/java` | Renderer (wide player model with all outer layers) |
| `src/gametest` | Server game tests and in-game client tests (Hardcore worlds) |

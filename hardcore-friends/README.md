# Hardcore Friends

A Fabric mod for Minecraft Java **26.3** that adds nine human companions to your Hardcore world. Each has their own skin, personality and job: Fern (farmer), Oak (builder), Flint (miner), Scout (explorer), Spark (redstone inventor), Aegis (warrior), Sage (strategist), Terra (landscaper) and Rowan (forager).

They choose useful work on their own, cooperate through a shared supply chest, grow a Unity bond, and build a camp into a settlement using real materials. Player Hardcore rules are never changed.

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
| `src/main/java/.../companion` | The companion entity, backpack, speech and the friend roster |
| `src/main/java/.../ai` | Reflex goals, the task scheduler, shared upkeep tasks, and each role's routines |
| `src/main/java/.../camp` | Camp data, supply chest, crafting, settlement plan, blueprints, needs |
| `src/main/java/.../world` | `WorldEditGuard` (bounded, build-preserving block changes) and natural tree detection |
| `src/main/java/.../unity` | The Unity bond |
| `src/client/java` | Renderer (wide player model with all outer layers) |
| `src/gametest` | Server game tests and in-game client tests (Hardcore worlds) |

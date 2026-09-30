# Test report — Pale Meridian 1.0.0

Date of the recorded run: 2026-09-30. Environment: Linux container (Ubuntu 24.04), Eclipse Temurin
JDK 25.0.4.1+1, Python 3.11, Gradle 9.7.1, PowerShell 7.5.6 (test tool only). Evidence logs are in
`docs/test-evidence/`; the whole automated battery is re-runnable with `tools/verify_all.sh --network`.

**Result legend:** PASS = ran and succeeded · FAIL = ran and failed · NOT RUN = not executed (reason given).

## 1. What was generated, downloaded, booted and client-tested

| | |
|---|---|
| **Generated** | All data-pack files (703 functions, 53 advancements, 155 dialogs, 33 predicates, 5 loot tables, 20 biomes, worldgen), 8 structure templates, 32 textures, the POI table, the game-test fixture, the spoiler quest graph, the mod jar, the client `.mrpack`, the server zip, checksums. |
| **Downloaded** | Minecraft 26.2 client and server jars from Mojang (SHA-1 verified) — used only offline by the generators and the checker, never redistributed. Build toolchain (Gradle, Loom, Fabric artifacts). For tests only: the 10 pinned mods and the Fabric server launcher (all hash-verified), PowerShell 7.5.6 from Microsoft's package feed (SHA-256 verified). |
| **Booted** | **No Minecraft client was started. No dedicated server was started.** One *unintended* headless game-test run happened during a build (see §3). The offline checker bootstraps the game's registries and data loaders in-process without creating a server, a world or a network connection. |
| **Client-tested** | **Nothing.** No gameplay, visuals, audio, dialogs or controls were observed in a running game. |

The Minecraft EULA was never accepted on your behalf: every `eula.txt` produced here says
`eula=false`, and no command was run with the game-test opt-in (`-Ppm_accept_eula=true`).

## 2. Results

### Content and data

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Generator runs from a clean state and rebuilds every generated file | PASS | `01-generate.log` |
| 2 | Regenerating produces byte-identical files (deterministic generation) | PASS | `verify_all.sh` (git diff of generated trees) |
| 3 | Every generated file loads through the game's own loaders (offline): 703/703 functions, 53/53 advancements, 155/155 dialogs, 33/33 predicates, 5/5 loot tables, 20/20 biomes, all structures, structure sets, noise settings, density functions, world clocks, timelines, features | PASS | `03-offline-validate.log` |
| 4 | All 86 macro functions instantiate with sample arguments (their commands parse) | PASS | `03-offline-validate.log` |
| 5 | Structure templates: 37 container items and 337 text components decode with the game's codecs; all 23 loot-table references exist | PASS | `03-offline-validate.log` |
| 6 | Zero warnings/errors mentioning the pack during loading (the checker fails on any) | PASS | `03-offline-validate.log` |
| 6a | Negative control: a deliberately broken tag makes the checker FAIL | PASS | run during development (checker reported `FAIL … missing following references`) |
| 7 | No dangling references: every quest target, NPC location and NPC dialog exists (generation fails otherwise) | PASS | generator (`engine._check_references`) |
| 8 | Every quest has at least one activation path and one completion trigger | PASS | static audit during development; also exercised by #10 |

### Campaign logic (simulation, not the game)

| # | Check | Result | Evidence |
|---|---|---|---|
| 9 | Logic simulation of the generated functions: 49 checks — first-quest activation, idempotent completion, blueprint detection, bell puzzle (wrong order resets, right order solves), every main quest activating in order, district restoration flags, side-quest unlocks, both endings (TRUE/BLANK) and their world flags, second ending refused, epilogue, keepsakes counted once, reload keeps progress, dialog-choice guard (no choice without its dialog, no replay), a Surge won, abandoned and timed out, the final encounter started/reset/won, player join/rejoin/death, the per-second loop | PASS 49/49 | `02-simulate.log` |
| 9a | Negative control: the simulation with the quest-initialisation fix removed | FAIL 9/32 (as expected — this is how the bug in §4 was confirmed) | development run |
| 10 | Smoke execution of all 617 non-macro functions in 4 world states under the simulator (no model errors, no runaway recursion) | PASS | development run |

### World generation

| # | Check | Result | Evidence |
|---|---|---|---|
| 11 | 7 site plateaus have the exact designed floor height and no cave holes, on seeds 0, 12345, −987654321 (21 site×seed probes) | PASS | `04-worldgen-probe.log` |
| 12 | The mine's cave-free zone is solid rock (no air/fluid) on all three seeds (~26,500 blocks each) | PASS | `04-worldgen-probe.log` |
| 13 | The valley is seed-independent (same layout on every seed) | PASS | `04-worldgen-probe.log` (identical site heights across seeds) |
| 14 | Every structure piece is within the 8-chunk structure reach of its start | PASS | generator check (`export_site`) |

### Build, packaging, installers

| # | Check | Result | Evidence |
|---|---|---|---|
| 15 | Mod compiles and builds; the build no longer launches the game (`runGameTest SKIPPED`) | PASS | `05-build.log` |
| 16 | A clean rebuild produces a byte-identical jar (SHA-256 `13a7ecbf…`) | PASS | `06-reproducible-jar.log` |
| 17 | Release files build; checksums recorded | PASS | `07-dist.log`, `dist/SHA256SUMS.txt` |
| 18 | The lock matches live Modrinth/Fabric metadata (exact versions, ids, sizes, hashes) | PASS | `08-lock-check.log` |
| 19 | Every file referenced by the client `.mrpack` downloads from its URL and matches size, SHA-1 and SHA-512 (10/10) | PASS | `09-mrpack-downloads.log` |
| 20 | Linux server installer: downloads and verifies the launcher and all server mods, creates `server.properties` and `eula.txt` (`eula=false`) | PASS | `10-server-install-linux.log` |
| 21 | `start-server.sh` refuses to start while `eula=false` (exit 1, Java never launched) | PASS | `10-server-install-linux.log` |
| 22 | A wrong pinned hash stops the installer; nothing is installed for that file | PASS | `11-server-tamper.log` |
| 23 | Installer re-run is idempotent; a tampered jar is detected and replaced; `--with-tools` adds Chunky/spark | PASS | development run |
| 24 | `backup-world.sh` refuses while the server-running marker exists and writes a timestamped backup otherwise | PASS | development run |
| 25 | `update-server.sh` keeps a rollback copy of `mods/`, preserves `server.properties` and `eula.txt`, reinstalls | PASS | development run |
| 26 | Windows scripts parse (`install-server.ps1`, `backup-world.ps1`, `update-server.ps1`) | PASS | PowerShell 7.5.6 parser |
| 27 | Windows installer, backup and update logic run under PowerShell 7.5.6 on Linux (with a stand-in for `cmd /c`) | PASS | `12-server-install-pwsh.log` and development runs |
| 28 | The `.bat` launchers (`install-server.bat`, `start-server.bat`, `backup-world.bat`) | NOT RUN | no Windows `cmd.exe` in this environment; reviewed by hand |
| 29 | Windows PowerShell **5.1** specifically | NOT RUN | only PowerShell 7 was available; the scripts avoid 7-only features |

### Runtime (needs Minecraft running)

| # | Check | Result | How to run it yourself |
|---|---|---|---|
| 30 | Game tests: campaign state machine in a real server, NPC uniqueness, keepsake recording (`CampaignGameTests`, compiled) | NOT RUN | Starting Minecraft requires your own EULA acceptance. If you accept it: `cd mod && ./gradlew runGameTest -Ppm_accept_eula=true` |
| 31 | Dedicated server boots with the pack and generates the valley | NOT RUN | EULA. Follow `docs/SERVER_GUIDE.md`, then check the server log for errors mentioning `palemeridian` and run `/pmadmin status` (it should report a valley world) |
| 32 | Client: pack imports, world creation, visuals (fog, skins, dialogs, locator bar, boss bars), sound, controls | NOT RUN | EULA + a Minecraft account + a display. See `QUICKSTART.md` |
| 33 | Full playthrough, co-op with 2–4 players, balance of encounters, playtime | NOT RUN | needs real players |
| 34 | Performance measurements (TPS, FPS, memory) | NOT RUN | needs a running game; budget by design in `TECHNICAL.md` §11 |

## 3. Unintended game-test launch (disclosure)

During the first `./gradlew build`, Fabric Loom's lifecycle ran the `runGameTest` task, which started
Minecraft's **headless game-test server** for about six seconds. It reported one passing test (the
project had no game tests of its own at that point) and shut down. Nothing was played, no client
started, no network port was opened.

- The game's own `eula.txt` in that run said `eula=false`; nothing set it to `true`.
- Why it started anyway: Fabric API's game-test support makes the game's EULA check pass whenever its
  game-test runner is enabled (decompiled `MainMixin#isEulaAgreedTo`), so `eula.txt` is not consulted
  in that mode.
- I did not intend or want that launch, deleted its run directory (a build artifact) before reading
  its logs, and do **not** count it as evidence for anything in this report.
- Fix: `mod/build.gradle` now skips `runGameTest` and `runClientGameTest` unless you pass
  `-Ppm_accept_eula=true`; check #15 confirms builds no longer launch the game.

## 4. Defects found by verification and fixed

| Found by | Defect | Fix |
|---|---|---|
| Code review, then confirmed by the simulator | **Quests could never activate**: activation required `pm.q = 0`, but no quest score was ever initialised, so the campaign could not start. | `q/_init` gives every quest a state on each load and at world start, never touching existing progress. Simulator: 9/32 → 49/49. |
| Code review against the decompiled game | **The bell puzzle could never register a bell**: block-use triggers evaluate at the block's centre (`Vec3.atCenterOf`), but the position ranges were exact integers. The bed trigger had the same edge problem. | Ranges now cover the whole block (`[x, x+1]`). |
| Code review | The "warm by the fire" rule only checked the block at head height, so it almost never applied. | Checks the blocks around and under the player; more light/fire blocks count. |
| Play-flow review | New players start in the fog without a light and the opening letter takes long enough for the cold to set in. | Four torches in the starting kit, mentioned in the opening hint. |
| Installer test | Java version check read the wrong line when `JAVA_TOOL_OPTIONS` is set (Java prints an extra first line). | Both installers look for the `version "…"` line. |
| Build | The build launched the game-test server (§3). | Tasks gated behind an explicit opt-in. |
| Code review | Lighting lamp posts inside the chunk-load callback could reach into chunks still loading. | Deferred to the next tick; client-only block updates. |
| Offline checker | Invalid block tag reference (`#minecraft:carpets`); structure items decoded before item components were bound (checker setup). | Correct tag (`#minecraft:wool_carpets`); checker finalises the reload like the server does. |
| Worldgen review | Lava lakes/springs could generate beside authored tunnels. | Removed valley-wide; springs, dungeons, geodes also removed from the mine district. |
| Lock check | Modrinth returns one mod's dependency list in varying order, making `--check` flaky. | Dependencies sorted in the lock. |

## 5. What this means for your first session

The pack is complete and internally consistent as far as automated, offline checking can show, and
its campaign logic has been executed end to end in simulation. It has **not** been seen running.
Please treat the first session as the real test: `docs/KNOWN_ISSUES.md` lists what is most likely to
need adjustment, and the admin tools in `docs/SERVER_GUIDE.md` can recover any stuck step.

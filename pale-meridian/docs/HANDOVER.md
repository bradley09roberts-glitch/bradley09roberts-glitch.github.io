# Handover — Pale Meridian 1.0.0

Everything lives in `pale-meridian/` on branch `claude/wizardly-albattani-adqn50`. Spoiler-free except
where marked ⚠️.

| Deliverable | Where |
|---|---|
| Client pack (import into Modrinth App / Prism Launcher) | `dist/PaleMeridian-1.0.0.mrpack` |
| Server package with installers (Windows + Linux), start, backup, update | `dist/PaleMeridian-Server-1.0.0.zip` (sources in `server/`) |
| Checksums of the release files | `dist/SHA256SUMS.txt` |
| Complete source (generators, mod, scripts) | `tools/`, `mod/`, `server/`, `pack/` |
| Quick-start guide (Windows first) | `QUICKSTART.md` |
| Player README | `README.md` |
| Server guide (EULA step, private access, backups, updates, rollback, admin tools) | `docs/SERVER_GUIDE.md` |
| Lock manifest and compatibility report | `pack/lock.json`, `docs/STACK_AND_LOCK.md` |
| Attribution and licences | `docs/ATTRIBUTION.md`, `LICENSE` |
| Technical documentation, save-state model, feature → file map | `docs/TECHNICAL.md` |
| Test report (PASS / FAIL / NOT RUN with evidence) | `docs/TEST_REPORT.md`, `docs/test-evidence/` |
| Known issues and risks | `docs/KNOWN_ISSUES.md` |
| What is and isn't implemented | `docs/PROJECT_STATUS.md` |
| ⚠️ Story bible, quest graph, full walkthrough | `docs/spoilers/` |
| Site previews | `docs/previews/` |

## What was generated, downloaded, booted and client-tested

- **Generated:** all game content, the mod jar, the client pack, the server package (see `TEST_REPORT.md` §1).
- **Downloaded:** Minecraft's official 26.2 jars (read offline by the generators, never redistributed),
  the build toolchain, and — in test folders only — the pinned mods, the Fabric server launcher and
  PowerShell 7 for script testing. Every download was hash-checked.
- **Booted:** no Minecraft client and no dedicated server. One unintended headless game-test run
  happened during a build; it is disclosed in `TEST_REPORT.md` §3 and builds can no longer do that.
- **Client-tested:** nothing yet.

## Your decisions

- **The Minecraft EULA** is yours to accept: in the server's `eula.txt`, and implicitly whenever you
  start the game or run `./gradlew runGameTest -Ppm_accept_eula=true`.
- **Exposure:** the server defaults are private (whitelist, online mode, no remote administration).
  Whether and how friends reach it over the internet is your choice (see `SERVER_GUIDE.md` §4).
- **Publishing:** nothing has been published. This repository is a GitHub Pages site repository; if the
  `pale-meridian/` folder is merged into the published branch, its files (including the spoiler docs)
  would become publicly readable.

## Suggested first session

1. Read `KNOWN_ISSUES.md` §1.
2. If you accept the EULA: optionally run the game tests (`cd pale-meridian/mod && ./gradlew runGameTest -Ppm_accept_eula=true`).
3. Import the `.mrpack`, create a **Default** world, and play the prologue (about 15 minutes). If
   anything looks wrong, `/function palemeridian:admin/status` (with cheats on) shows the state.

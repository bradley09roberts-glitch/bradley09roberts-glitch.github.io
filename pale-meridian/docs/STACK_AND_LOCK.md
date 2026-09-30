# Stack and lock report

Everything Pale Meridian depends on is pinned to an exact version and verified by hash. Nothing
resolves to "latest". This file explains what is locked, why, and how to re-verify it.

## 1. Platform

| Component | Locked version | Why this one | Verified how |
|---|---|---|---|
| Minecraft: Java Edition | **26.2** | Current release line with the features the campaign is built on: dialogs and Quick Actions, mannequins, world clocks/timelines and environment attributes, the locator bar, creakings. | Official version manifest; the generators use the SHA-1-verified 26.2 jars (`tools/pins.json`) and its data reports. |
| Java | **25** (built with Eclipse Temurin 25.0.4.1+1) | Required by Minecraft 26.2. | Temurin archive SHA-256 `dbb69839…cf41e`. |
| Fabric Loader | **0.19.5** | Stable loader for 26.2 on Fabric's meta service. | `meta.fabricmc.net/v2/versions/loader/26.2` lists it as stable. |
| Fabric API | **0.161.0+26.2** | Events and resource loading used by the mod. | Modrinth, exact version, SHA-512 in the lock. |
| Fabric Loom (build only) | **1.18.2** | Gradle plugin for 26.x (unobfuscated game). | Gradle plugin portal. |
| Gradle (build only) | **9.7.1** | Wrapper with `distributionSha256Sum=acd53f1e…4d20a`. | Gradle verifies the distribution itself. |
| Fabric server launcher | installer **1.1.2** for 26.2 / 0.19.5 | Official way to run a Fabric server; it fetches Mojang's server jar itself (from Mojang, hash-checked). | Pinned by SHA-256 `f1d2bafd…91127` (downloads are byte-identical; checked twice). It embeds `game-version=26.2`, `fabric-loader-version=0.19.5`. |

## 2. Mods (the full lock is `pack/lock.json`)

All are Modrinth **release** versions for Fabric + 26.2, resolved by exact `version_number`.

| Mod | Version | Client | Server | Role | Why it's in |
|---|---|---|---|---|---|
| Pale Meridian | 1.0.0 | required | required | the campaign | This project. Ships in the pack (overrides) and the server package. |
| Fabric API | 0.161.0+26.2 | required | required | library | Required by Pale Meridian. |
| Lithium | mc26.2-0.25.3-fabric | optional | required | performance | Game-logic/tick optimisation, no gameplay changes. Helps encounter-heavy moments. |
| FerriteCore | 9.0.0-fabric | optional | required | memory | Lower memory use for block states and models. |
| Sodium | mc26.2-0.9.2-fabric | required | — | rendering | Large frame-rate gains in a fog-heavy, lamp-lit world. |
| ImmediatelyFast | 1.16.5+26.2-fabric | optional | — | rendering | Faster HUD/text rendering (the valley uses many text displays). |
| Entity Culling | 1.11.2 | optional | — | rendering | Skips entities you can't see (villages have many display entities). |
| AppleSkin | 3.0.10+mc26.2 | optional | optional | quality of life | Shows food/saturation; survival stretches between districts. |
| Mouse Tweaks | 26.2-2.31-fabric | optional | — | quality of life | Faster inventory handling while building lamps. |
| Chunky | 1.5.3 | — | optional tool | server | Pre-generating the valley on servers (`--with-tools`). |
| spark | 1.10.187-fabric | — | optional tool | server | Profiling lag on servers (`--with-tools`). |

Required-dependency closure is checked by `tools/lock_pack.py`: every required dependency of every
pinned mod must itself be pinned, or the lock is refused.

### Considered and left out

| Candidate | Decision |
|---|---|
| Mod Menu | Dropped: its 26.2 build requires an extra library (Text Placeholder API) for a purely cosmetic feature. |
| Iris (shaders) | Not included: the campaign's atmosphere is carried by vanilla fog/sky environment attributes; shader packs replace those and were not verified. |
| ScalableLux (lighting engine) | Not included: alpha-quality at lock time. |
| Any content/gameplay mods | Deliberately none: the story relies on vanilla mechanics behaving exactly as documented. |

## 3. Sides

- **Client needs:** Pale Meridian + Fabric API (required); Sodium (required by the pack for
  performance); the rest optional.
- **Server needs:** Pale Meridian + Fabric API; Lithium and FerriteCore recommended; AppleSkin syncs
  saturation to clients that have it; Chunky and spark optional.
- A vanilla client cannot join a Pale Meridian server (the mod is required on both sides: it
  provides the world generation, skins and data).

## 4. How the lock is enforced

| Where | How |
|---|---|
| Client (`.mrpack`) | `modrinth.index.json` lists each mod's official Modrinth CDN URL, size, SHA-1 and SHA-512; importing launchers verify them. Minecraft 26.2 and Fabric 0.19.5 are declared as dependencies. |
| Server | `server-files.tsv` (generated from the lock) drives `install-server.sh` / `install-server.ps1`; each download must match its SHA-512 (launcher: SHA-256) or the installer stops and installs nothing for that file. |
| Build | `mod/gradle.properties` pins Minecraft, Loader, Loom and Fabric API; the Gradle wrapper pins Gradle by SHA-256. Jars are reproducible (fixed timestamps and order). |

## 5. Re-verifying

```bash
python3 tools/lock_pack.py --check    # re-resolve every pin on Modrinth/Fabric meta; fails if anything differs
python3 tools/build_dist.py           # rebuilds dist/ and SHA256SUMS.txt from the lock
```

To upgrade anything, change the pin in `tools/lock_pack.py` (and `mod/gradle.properties` for build
dependencies), regenerate the lock, rebuild, and re-run the checks in `docs/TEST_REPORT.md`. Never
edit hashes by hand.

## 6. Compatibility notes

- Worlds must be created with the **Default** world type; the mod replaces its generator with the
  valley. Other world types still load, but show a warning and no campaign.
- The data pack is embedded in the mod and always enabled; there is nothing to add to a world.
- Save format: campaign state lives in the world's scoreboard and command storage and is versioned
  (`#schema` in objective `pm.world`); see `docs/TECHNICAL.md` §3.

# Known issues and risks — Pale Meridian 1.0.0

Spoiler-free. Ordered roughly by how likely they are to matter in your first session.

## 1. Not yet seen in a running game

Everything was checked offline and simulated (see `TEST_REPORT.md`), but no client or server has run
the pack, because starting Minecraft needs your own EULA acceptance. The areas most likely to need a
tweak after the first real session:

| Area | Why it's a risk | Where to adjust |
|---|---|---|
| NPC appearance | People are mannequins wearing skins shipped in the mod; the format matches the game's code but hasn't been seen rendered. | `tools/pmgen/art.py`, `engine.py` (NPC summon) |
| Dialog layout | Long lines wrap differently at large GUI scales. | dialog text in `tools/pmgen/dp/content/*.py` |
| Objective marker | The locator-bar marker uses an invisible marker entity in a force-loaded chunk. | `tools/pmgen/dp/core.py` (`hud/*`) |
| Encounter balance | Creature counts, timings and health were set by design, not tuned by play. The comfort menu has a gentler setting. | `encounters.py`, `content/chapter*.py` |
| The final encounter | Uses a much larger version of a vanilla creature; its movement on the arena and the "blink" landing spots are the least predictable part. It resets cleanly if something goes wrong. | `content/chapter4.py` (`c4/boss/*`) |
| Fog and haze | Values are chosen for mood; nights in the fog might be darker than intended. | `worldgen.py` (biome attributes), `atmosphere.py` |
| Performance | Budgeted by design and estimated in simulation, not measured in a running game. | `TECHNICAL.md` §11 |

## 2. Gameplay notes

- **Peaceful difficulty** removes the valley's creatures, including the ones encounters depend on. The
  story still completes, but without tension. Use Easy/Normal/Hard, or the comfort menu's *Story* setting.
- **World type must be Default.** Worlds made with other types (or existing worlds) don't contain the
  valley and show a warning; they can't be converted.
- **Normal Minecraft rules apply**: hostile mobs spawn at night and in dark places, including
  underground areas of the valley. Bring light and food.
- **Keepsakes** count the first time anyone picks them up; dropping one later doesn't undo it.
- **The final decision** is made by whichever player confirms it first; it's announced to everyone
  and applies to the whole world. Talk it over first.
- **PvP is off** by default (game rule `pvp`); an operator can turn it on.
- Players in Creative or Spectator mode aren't affected by some story boundaries (intended for admins).

## 3. World and technical

- **Site edges**: the land around each authored site follows the designed height within ±1 block in
  about 90% of samples; expect the odd one-block step or partly buried block at a site's rim.
- A brand-new world briefly prepares chunks around the world origin before moving the spawn to the
  starting waystation (one time, harmless).
- **Other world-generation mods** (or anything replacing the default world preset) will conflict.
- **Shader packs** aren't included or supported; the atmosphere relies on vanilla fog and sky settings.
- **Entity Culling** may briefly hide people or objects behind glass until they're in view (cosmetic).
- **Save compatibility**: saves carry schema 1; later versions will migrate them. Opening a newer save
  with an older version warns operators.

## 4. Not implemented from the original design

- The orchard keeper offers a **daily gift** instead of the villager-style trades in the design notes.

## 5. Packaging

- The Windows `.bat` launchers were reviewed but not executed (no Windows in the test environment); the
  PowerShell scripts were executed under PowerShell 7, not Windows PowerShell 5.1.
- The official Minecraft Launcher can't import `.mrpack` files; use the Modrinth App or Prism Launcher
  (see `QUICKSTART.md`).
- The runtime game tests use a game-test helper that Minecraft marks as deprecated (still present in 26.2).

## Reporting a problem

Useful information: what you were doing, the objective shown in the Field Journal, and for servers the
output of `/pmadmin status` and `/function palemeridian:admin/status`, plus `logs/latest.log`.

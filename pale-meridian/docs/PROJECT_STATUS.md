# Project status — Pale Meridian 1.0.0

Spoiler-light: systems and places are named, the story is not told.

**Implemented** means the content exists, loads through the game's own loaders offline and (for logic)
passes the simulation. **Nothing has been verified in a running game yet** (see `TEST_REPORT.md`).

## Implemented

| Area | Status |
|---|---|
| Stack lock (Minecraft 26.2, Java 25, Fabric 0.19.5, 10 pinned mods, server launcher) | Implemented; re-verifiable (`lock_pack.py --check`) |
| Designed valley: terrain, 10 districts × Pall/clear biomes, lake, cliffs, fen, rim, roads with lamp posts | Implemented; probed on 3 seeds |
| Authored sites: waystation, lakeside village, orchards, glassworks, mine, island observatory with causeway, fen chapel | Implemented (8 templates) |
| Campaign: prologue, chapters 1–4, epilogue; 36 quests (28 on the main line incl. the epilogue, 8 optional) | Implemented |
| Two endings with different world consequences, credits, free play | Implemented |
| Journal: current objective, recap, people, keepsake list, help, comfort settings; advancement tab | Implemented |
| NPCs with state-dependent conversations and faded/restored looks | Implemented (31 people and interactive objects; 43 including encounter props) |
| Puzzles: bells, kiln levers + fuel, fen sluices, lamp blueprints, a causeway repair | Implemented |
| Encounters: two arena encounters (lamps, candles) with scaling, time limit and reset; a scripted final encounter | Implemented |
| Districts visibly change when restored (biomes, lamps, flora, people) | Implemented |
| The fog's chill and light rules | Implemented (world toggle) |
| Optional content: the eleven keepsakes, eight survey benchmarks, the drowned chapel, a home plot, the causeway | Implemented |
| Recovery paths for every consumable requirement; operator tools | Implemented |
| Co-op: shared world state, per-player journal/settings, late-join recap, scaling | Implemented |
| Save schema and migration hook; reload-safe | Implemented |
| Client `.mrpack`, server package with Windows/Linux installers, backups, updates, rollback | Implemented and tested (installers executed; `.bat` not executed) |
| Documentation set | Implemented |
| Offline checker, logic simulator, runtime game tests | Implemented (game tests compiled, not run) |

## Not implemented / deferred

| Item | Note |
|---|---|
| Orchard trades | Replaced by a daily gift |
| Runtime verification | Waiting on a first run by you (EULA) |
| Measured performance and playtime | Needs real sessions |

## Content size

788 functions, 164 dialogs, 54 advancements, 33 predicates, 5 loot tables, 20 biomes, 8 structure
templates, 32 textures, 152 points of interest; about 7,500 lines of Python generator code and 2,300
lines of Java.

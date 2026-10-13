# Kingdoms of the Long Night — Durable Working Notes

Purpose: let work resume after interruptions/context limits. Update at every milestone.

## Session 1 (overnight 12–13 Oct 2026, cloud container)

### Environment facts (verified 22:25 UTC)
- Linux container, 4 cores, 15 GB RAM, no GPU. Java 21.0.12 (OpenJDK), Gradle 8.14.3, Python 3.13 (Pillow 12, numpy 2.5), Node 22.
- Xvfb + Mesa (llvmpipe) present -> client may be launchable headless with software GL.
- Network: Mojang (piston-meta, libraries, resources), maven.neoforged.net, maven.fabricmc.net, Modrinth API + CDN, Maven Central, Gradle plugin portal: OK. CurseForge: BLOCKED (403) -> Modrinth only.
- This container is NOT the user's gaming PC; its hardware says nothing about theirs.

### Key decisions
- D1 Minecraft 1.21.1 + NeoForge 21.1.259 (ModDevGradle 2.0.148). Evidence: Modrinth query showed Farmer's Delight, Iron's Spells, Ars Nouveau, Create, Supplementaries, EMI only reach 1.21.1 (not 26.x); Sodium/Iris/DH/Lithium/Jade/Tectonic/Terralith all support 1.21.1 NeoForge.
- D2 The custom core mod (`longnight`) OWNS every person: identity, occupation, family, inventory(abstract), faction, memories. No other NPC/colony mod (MineColonies, Millénaire, Guard Villagers, Recruits, Towns&Towers) is included, so there is one society, not several disconnected ones.
- D3 Vanilla villages are disabled (biome tag overrides) so vanilla villagers don't form a second, disconnected society. Illagers/pillagers remain as hostile threats.
- D4 Two-level simulation: abstract (all settlements, coarse ticks, chunk-independent) + embodied (citizen entities near players). Identity persists by person UUID; entity is a temporary "body".
- D5 Distribution: Modrinth .mrpack (references CDN URLs, no third-party jar redistribution) + our own mod jar in overrides. Launcher: Prism Launcher or Modrinth App.
- D6 Project lives in `kingdoms-of-the-long-night/` inside the user's repo on branch `claude/lucid-clarke-305qf7`; nothing is published elsewhere.

### Ownership (subagents)
- modpack/, tools/pack/ : modpack research+build agent
- mod/src/main/resources/assets/longnight/textures/, tools/art/ : art agent
- docs/WORLD_BIBLE.md, mod/src/main/resources/data/longnight/{names,lore}/ : lore agent
- everything else in mod/ : main session (core code)

### Progress log
- 23:30 BST env inspection, version research, decisions D1–D6.
- 00:20 BST core mod compiles (sim, world, entity, game, commands, client). Lore agent done (docs/WORLD_BIBLE.md: world = the Haldmarch;
  Ender Dragon = Nahire, the Sun's chained twin star). Art agent done (79 textures, tools/art). Modpack agent still working.
- Decisions D7: chat-based UI (clickable /longnight commands) for v0.1 instead of custom screens: works on dedicated servers, scriptable, testable.
  D8: 1 in-game day = 1 month, 8-day year by default (bible suggests 32; too slow for generations) - configurable daysPerYear.
  D9: The Long Night beyond stage 2 (blight) only advances after Nahire (Ender Dragon) is slain.
  D10: Embodied workers produce via the SAME recipes in 8 "work cycles" per day; abstract production is scaled by
  (1 - embodied work-hours fraction) so a watched settlement never double-produces.
  D11: No coin-exchange crafting recipes (9-slot grid vs 10p mark would mint money); trades give exact change.
- Test harness: tools/test/server_scenario.py (RCON-driven dedicated server, fresh hardcore world, restart+reload check);
  mod/src/test (JUnit via MDG unitTest: sim invariants, pay-once construction, 60-day run, save round-trip).
- 00:35 BST GameTests 6/6 after fixing: plot survey must see natural ground (barrier/roof would have been "ground").
- 00:45 BST scenario 2: RCON client fixed (vanilla RCON reads one packet per read; long replies fragment at 4096 B). 15/15.
- 00:55 BST BIG FIX: coastal/rugged towns placed only 15/40 buildings. Added generator-noise plot pre-check
  (WorldHooks.plotLooksBuildable), terraces (spread<=9), shoreline footings (<=50% water), outward growth,
  cancel+refund unplaceable projects, drop unplaceable historic buildings. Same seed now 35/35. Scenario 3: 15/15.
- 01:00 BST contracts (notice board) + outlaw bounties; vanilla villages disabled via structure_set frequency 0 + load after Terralith.
- 01:10 BST headless client (Xvfb + llvmpipe GL 4.5) loads the saved world; screenshots in tools/test/screenshots show an Eldrin town
  materialized (teal roofs, chimney smoke, farm, a citizen). AutoShot fixed to fly (creative falls) + re-aim after materialization.
- Modpack agent: 37-mod performance / 40-mod rich presets, server+client verified without longnight. Now testing with longnight jar.
- Design agent writing docs/DESIGN.md + docs/ROADMAP.md.
- 01:15 BST FULL PACK + longnight server test FAILED: watchdog killed the server (history generation >60 s on the server
  thread with Tectonic/Terralith noise). My scenario had max-tick-time=-1 which hid it. FIX D12: history is generated on a
  background thread into a temp WorldSim and merged on the server thread; players joining meanwhile are greeted when ready.
  Harness now keeps the watchdog on. Re-test: PASS (40 mod ids, 0 errors, history in 38 s in background, VALIDATE OK).
- Design agent's code review bugs fixed: player realm NPC ruler, ruin loot refill (Building.decayed saved), glass/iron/wool
  deadlocks, wandering trader (gamerule off at creation), interrupted raids, abandoned settlements -> ruins, 2317 AB calendar.
- 01:40 BST full pack real client: joined, Dunmar town materialized among Terralith giant spruces (tools/test/screenshots/fullpack-*).
- 01:50 roads (planned at creation, laid lazily near players, signposts) + embodied caravans (leader + pack mule on the road).
- 02:10 200-day LongRunTest found: camps deadlocked (no stone/logs/fuel) and zero wars. FIX: hand-gathering until lumber camp/quarry,
  camp build order, rope/stone instead of iron fittings, war conditions loosened (grudge + advantage). Now ~15 camps grow, 1-2 wars.
- 02:20 festivals (bible names/months, feasts from stores) + funerals (mourners gather next day); primer book + look-at hint.
- Test status at 02:30: JUnit 9/9, GameTests 7/7, scenario 15/15 (run7), full-pack server PASS.

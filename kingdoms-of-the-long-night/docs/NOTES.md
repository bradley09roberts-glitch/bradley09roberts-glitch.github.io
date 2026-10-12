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

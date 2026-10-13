# Kingdoms of the Long Night — morning handoff (13 October 2026)

Version **0.1.0**: a playable, tested foundation. It is **not** the full vision; see "Not built yet" below and
[docs/STATUS.md](docs/STATUS.md) for the complete honest list.

## Install

| File (in `dist/`) | What it is |
|---|---|
| `KingdomsOfTheLongNight-0.1.0-performance.mrpack` | **Start here.** The custom `longnight` mod + 37 other mods, no shaders. Give it 6 GB of RAM. |
| `KingdomsOfTheLongNight-0.1.0-rich.mrpack` | Same, plus shaders (Iris + Complementary Reimagined), Distant Horizons and Sound Physics. 8 GB, a decent graphics card. |
| `longnight-0.1.0.jar` | The custom mod on its own, for your own NeoForge 1.21.1 instance. |

Versions: **Minecraft 1.21.1, NeoForge 21.1.259, Java 21.** SHA-256 checksums are in
[modpack/VERIFICATION.md](modpack/VERIFICATION.md).

1. Install **Prism Launcher** (free) or the **Modrinth App** and sign in with your Microsoft account.
2. Make sure Java 21 is available (Prism: Settings → Java → Auto-detect / Download).
3. **Add Instance → Import** the performance `.mrpack`. The launcher downloads Minecraft, NeoForge and every mod from
   Modrinth. It is a separate instance: your existing installs and saves are untouched.
4. Instance settings → Memory: 6144 MB (rich: 8192 MB).
5. **Singleplayer → Create New World → Game Mode: Hardcore.** If Minecraft warns about "Experimental Settings",
   click "I know what I'm doing" (the terrain mods use data-driven world generation).
6. The first world takes up to a minute longer: the mod surveys the land and writes the world's history.

Full guide: [modpack/INSTALL.md](modpack/INSTALL.md). First hour of play: [README.md](README.md).

**Settings:** each world has `saves/<world>/serverconfig/longnight-server.toml` (pacing, raids, wars, how many
people get bodies). To shape new worlds, copy it into the instance's `defaultconfigs/` folder.
**Source:** `mod/` (build with `./gradlew build`).

## What works

- A new world gets a history: 7 settlements grouped into realms, settler camps, outlaw bands and ruins, about 200
  named people with families, trades, needs, relationships and memories. People have bodies only when you are near.
- They work, trade and build block by block (each block paid for once), repair damage, marry, have children, age and
  die. The dead stay dead, with a named grave and a funeral. Festivals follow each faith's calendar.
- Caravans and roads (with bridges over rivers) link towns; news spreads by word of mouth; raids always come with
  warning; realms have rulers, successions and occasional wars.
- You can talk to people, trade, take contracts and bounties, join a settlement, claim land, found your own
  settlement, and set wards against the night. The Long Night's later stages begin only if the Ender Dragon dies.

## Tests (each kind run separately, final runs on the released files)

| Kind | Result |
|---|---|
| Compilation | passes |
| Unit tests (JUnit) | **10/10**, including a 200-day run: no population collapse, rules hold, clean save/reload |
| In-world GameTests | **7/7**: builders pay once per block and repairs are charged correctly; nothing built over player blocks; trading neither duplicates nor destroys goods; the dead never respawn; wards work; founding a settlement works |
| Dedicated-server scenario | **17/17**: fresh hardcore world, townsfolk with bodies, a raid, simulated days, restart with identical counts, no person ever has two bodies |
| Full modpack, dedicated server | **pass** on both packs, 0 errors |
| Full modpack, real game client | **pass** on the performance pack, screenshots in `tools/test/screenshots/` |
| Not tested | real people playing, multiplayer, your hardware, the rich pack's client with the mod (no graphics card here) |

## Bugs found and fixed overnight

- Some people could get a **second body** in the world.
- The server **froze for about 7 seconds** when a town first appeared.
- Laying roads paused the server 1–2 s; checking building sites paused it 0.3–0.9 s.
- **Sea-heavy worlds** got only 4 towns instead of 14.
- Wanted status was forgotten on restart.
- Watching a town changed how much it produced.

## Performance (this machine: 4 cores, no graphics card)

- A town of 24–26 people nearby: average server tick **3–6 ms** (the server needs under 50 ms for full speed).
- Worst single slowdown from the mod: **77–285 ms**.
- Simulating a whole world-day: **under 0.2 s**.
- On a slow PC a big town can take a few minutes to finish appearing; lower `maxBodiesPerSettlement` and
  `maxBodiesTotal` first if the game struggles. `/longnight admin perf` shows what the mod costs per tick.

## Simplified, or not built yet

- **Simplified:** conversation is clickable chat lines, not proper screens; craft workers animate at their stations
  rather than working blocks; battles between realms happen off-screen.
- **Not built:** proper screens, dragons other than the Ender Dragon, the late Long Night's effects in the world,
  marching armies, laws and titles for you, the optional heir mode (it will be clearly separate from strict
  hardcore), optional AI dialogue (bring-your-own-key, never required).
- **Known limits:** on sea-heavy worlds towns can be up to about 4,000 blocks apart; towns in dense forest keep the
  trees between plots until lumberjacks fell them.

## Next

[docs/ROADMAP.md](docs/ROADMAP.md) has the prioritised milestones (proper screens first);
[docs/DESIGN.md](docs/DESIGN.md) is the complete systems design; [docs/NOTES.md](docs/NOTES.md) is the overnight
work log.

Nothing was bought, published or added that costs money. All tests used fresh worlds, separate from any existing
Minecraft install. Work stopped when the session ended; nothing keeps running in the background.

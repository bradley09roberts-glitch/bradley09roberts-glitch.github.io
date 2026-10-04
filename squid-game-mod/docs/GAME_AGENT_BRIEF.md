# Brief for game implementers (read first)

You implement **one game** of the Squid Game tournament mod end to end: pure rules + unit tests, server game logic, NPC
behaviour, networking, client UI/rendering, translations, docs and an in-world verification. Quality bar: a showcase that feels
like the show — clear rules, tension, juicy feedback (sound, particles, camera/HUD cues, announcements), challenging on
Hard/Extreme, and NPCs that look like they *decide* things.

## Read, in this order
1. `docs/ARCHITECTURE.md` (layers, lifecycle, `MiniGame`/`GameContext`, NPC rules, networking, testing).
2. Your design spec `docs/games/<game>.md` (rules and requirements; **overwrite it at the end** with the final documentation:
   rules as implemented, controls, difficulty table, NPC behaviour, limitations).
3. The arena contract for your arena in `docs/ARENA_MARKERS.md` and the common markers (waiting room, gate, spectator, exit).
4. The reference game: `game/redlight/*` (`RedLightGreenLightGame`, `RedLightNpcBehavior`), `core/redlight/*` and its tests,
   plus `entity/ContestantEntity`, `entity/NpcBehavior`, `tournament/TournamentManager` (only the parts you need) and
   `docs/ASSET_CONTRACT.md` (animations / sounds / blocks you can use: use what exists, do not invent asset names).

## Ownership (avoid merge conflicts: five games are developed in parallel in separate git worktrees)
You own and may create/edit freely:
* `src/main/java/com/squidgame/core/<pkg>/**` (pure rules) and `src/test/java/com/squidgame/core/<pkg>/**` (tests)
* `src/main/java/com/squidgame/game/<pkg>/**` (the game, its NPC behaviours, its `<Pkg>Net` payload registration)
* `src/client/java/com/squidgame/client/game/<pkg>/**` (screens, renderers, key bindings, `<Pkg>Client.register()`)
* `src/main/java/com/squidgame/build/placeholder/<Name>Placeholder.java` (your throw-away fixture arena, see below)
* `tools/lang/<game>.json` (translation fragment) and `docs/games/<game>.md`
Shared files: change them only when unavoidable, keep the change **small and additive** (new method / new enum constant /
new config field at a clearly separated spot), and list every such change in your final report. Never reformat or reorder
shared code. Candidates: `GameContext`, `SquidConfig` (your own `// ---- <game>` block), `Restrictions` (a hook), `ModEntities`,
`ModItems`, `ModBlocks`, `Activity`/`Anims` (only if the animation already exists in `animations/entity/*.json`).
Do **not** edit: `TournamentManager`, `Tournament`, `Roster`, `Contestant`, `Planner` (ask in your report instead - exception: the
FINAL game agent is asked to propose a Planner change in the report), other games' packages, `build/arena/**` (real arena
builders are being written by other agents), the generated `lang/en_us.json` (run `python3 tools/merge_lang.py` locally to
test, but do not commit that file), `ModSounds` (generated; use existing events), asset generators under `tools/assets/**`.

## Build, test, run (the machine has 4 cores / 16 GB shared by ~10 agents: be frugal)
* Always `tools/gradle.sh <tasks> -PskipArenas` (machine-wide lock; `-PskipArenas` excludes the arena builders that are still
  being written and would not compile). Examples: `tools/gradle.sh compileJava compileClientJava -PskipArenas -q`,
  `tools/gradle.sh test -PskipArenas`, `tools/gradle.sh build -x test -PskipArenas`.
* Pure logic first: write `core/<pkg>/*` + JUnit tests, run them until green, then the Minecraft side.
* In-world test with a dedicated server of your own (do not use another agent's directory or ports):
  `SQUID_SERVER_DIR=$PWD/run-server-<pkg> SQUID_PORT=<port> SQUID_RCON_PORT=<port+1> SQUID_HEAP=2G
   SQUID_TEMPLATE_DIR=/home/user/bradley09roberts-glitch.github.io/squid-game-mod/run-server SQUID_ACCEPT_EULA=1 BUILD=1
   GRADLE_ARGS=-PskipArenas tools/devserver.sh restart` (SQUID_ACCEPT_EULA=1 writes eula.txt: the project owner accepted the
   Minecraft EULA for local test servers), then `SQUID_RCON_PORT=<port+1> python3 tools/rcon.py "<command>"`.
  Typical loop: `squid build` (waits until the placeholder arenas are placed; watch `tools/devserver.sh log`), then
  `squid debug timescale 0.3` (shorter timers) and `squid debug play <game> 16 [normal|hard|extreme]` from RCON (no player
  => an NPC-only run: assert in the log that the game starts, NPCs act sensibly, eliminations/finish happen, no exceptions,
  `squid debug roster` shows the outcome). `squid skip` skips a phase. Observe NPCs with
  `execute in squidgame:arena run data get entity @e[type=squidgame:contestant,limit=1] Pos` etc.
* **Stop your server when idle** (`tools/devserver.sh stop`) and before you finish. Never leave background processes.
* No headless client is needed for server-side verification. If you want to check a client screen/renderer visually, run
  one client at a time under `flock /tmp/squid-client.lock` using `tools/xvfb-client.sh <your display number>` (see the script;
  its server port follows `SQUID_PORT`), take screenshots with `DISPLAY=:<n> import -window root x.png`, view them with Read,
  and always shut the client down afterwards. The integrator will also verify UIs visually later, so a screen that is
  written carefully but unseen is acceptable (say so in the report).

## Fixture arena (so you can test before the real arena exists)
Create `build/placeholder/<Name>Placeholder.java` (`<Name>` = the real builder's name without "Builder", e.g.
`DalgonaPlaceholder`; implements `ArenaBuilder`, public no-arg constructor, pure Java like the real builders; see
`build/PlaceholderBuilder` and `docs/BUILDING_GUIDE.md`). It must be a *small, flat, crude* arena with the exact marker/region
contract and the real geometry that your logic depends on (heights, lane positions, slot spacing) so that the game works
unchanged on the real arena later. `ArenaBuilders.get` uses the real builder when it exists, the fixture otherwise. The fixture
must use `version() == 0`. Check it with `tools/dump.sh` if you like.

## Non-negotiable behaviours (checklist for your final report)
* Server-authoritative; every client message validated; no hidden info leaks to NPCs or clients.
* Same rules for humans and NPCs; NPC decisions use only public information + their memory + personality.
* Works with 2..128 contestants (performance: O(1) work per NPC per tick, throttled searches), 0 humans (NPC only) and with
  humans mixed in; a human disconnecting mid-game (AI stand-in takes over: `onControllerChanged`), reconnecting, being
  eliminated by the tournament (admin/disconnect), two eliminations in the same tick, timeout, everybody failing, nobody left,
  an odd number of contestants, restart of the server in the middle of the game (`saveState/loadState`, `cleanup` idempotent).
* Difficulty (`ctx.difficulty()`) changes numbers meaningfully (use a table in `core/<pkg>`), not just the timer.
* All temporary entities/blocks are removed in `cleanup`; entities tagged `squidgame_temp`; no leftover tickets/schedules.
* Every user-visible string is a translation key (fragment `tools/lang/<game>.json`: title/instructions/objective/HUD/chat/
  elimination causes... keys follow `squidgame.game.<id>.*`); use the existing sound events from `ModSounds`.
* Unit tests for the pure rules (determinism, difficulty monotonicity, edge cases, NPC pass-rate sanity).
* Code style: match the surrounding code (Java 21, 4 spaces, brief Javadoc on non-obvious classes, no dead code).

## Deliverables and final report
Commit all your work in your worktree (`git add -A && git commit`, message ending with the two trailer lines
`Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and
`Claude-Session: https://claude.ai/code/session_012RjH3JK9ZDCDuvtbnDsxQE`; do not push, do not open PRs, do not change branches).
Final report (concise): what you built (files), what you verified and *how* (commands, observed results), what you could not
verify, shared-file changes, known limitations / suggestions, and anything the integrator must do (e.g. register something).

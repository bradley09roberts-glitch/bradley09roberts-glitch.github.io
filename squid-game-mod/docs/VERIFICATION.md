# Verification report

What was built, how it was checked, what was found and fixed on the way, and - just as important - what could **not** be checked.
Everything here was produced in one cloud container (4 vCPUs, 16 GB RAM, Linux, no GPU, no audio device); several test servers and
software-rendered test clients ran side by side, so timings are pessimistic and anything that needs sound, a real GPU or a human
hand could not be judged. "Integration" below means checks run on the merged mod by the integrator; "implementer" means checks run by
the author of one game inside their own worktree (their documents in `docs/games/` list them in detail).

## 1. Environment and method

| | |
|--|--|
| Minecraft / loader / libraries | 1.21.1, Fabric Loader 0.17.3 (dedicated server) and 0.19.3 (Loom dev client), Fabric API 0.116.17+1.21.1, GeckoLib 4.9.3, Java 21 |
| Dedicated server | the production-remapped `squidgame-1.0.0.jar` in `fabric-server-launch.jar` (`tools/devserver.sh`), RCON for control |
| Client | the Loom dev client under Xvfb + llvmpipe (about 3 - 7 fps), scripted with XTest (`tools/xinput.py`), screenshots with ImageMagick `import` |
| Single-player | `tools/spclient.sh`: a fresh world, the integrated server, the same headless client |
| Accelerated NPC-only runs | `/tick rate 100` plus `/squid debug simulate` or `/squid debug play <game> <npcs> <difficulty>` |

**Minecraft EULA.** A dedicated Minecraft server will not start without `eula=true`. The test servers in this environment (and the
throw-away server that creates the single-player test world) were started with `SQUID_ACCEPT_EULA=1`, i.e. the tooling wrote
`eula.txt` on the user's behalf. Nothing in the shipped bundle (`dist/`) accepts it - whoever installs the mod accepts
https://aka.ms/MinecraftEULA themselves.

## 2. Build and unit tests

* `./gradlew clean build --offline`: BUILD SUCCESSFUL (about 35 s with the Gradle build cache).
* `./gradlew cleanTest test --no-build-cache`: **483 unit tests in 44 classes, 0 failures** (the clean-build run was repeated after every merge) (`core/` is pure Java: difficulty tables,
  personalities, the Red Light judge, the Dalgona cookie simulation, Tug of War simulation and team planner, marbles rules,
  glass-bridge route/knowledge/queue and a whole-game simulator, the final's duel engine and knockout ladder, the planner ...).
* The jar contains the three expected layers (common, client, assets); the client mixin's production names were checked in the
  remapped class (`class_7196`, `method_57775`).

## 3. Whole tournaments (integration)

`tools/smoke.sh N difficulty` runs registration -> six games -> final winner at 100 ticks/s and fails on any server error. All runs
below ended with exactly one winner (or the documented outcome) and no exception, warning or `Tournament tick failed` in the log.

| Run | Survivors after game 1 / 2 / 3 / 4 / 5 / 6 |
|-----|--------------------------------------------|
| 100 NPCs, Normal (three runs) | 91 / 75 / 37 / 19 / 10 / **1**;  81 / 70 / 34 / 17 / 7 / **1** (real time);  75 / 62 / 31 / 16 / 9 / (final not yet merged) |
| 100 NPCs, Hard | 66 / 48 / 24 / 12 / 3 / **1** (the bridge with exactly 12 played bare); earlier build: 65 / 42 / 21 / 11 / (bridge skipped) / **1** |
| 100 NPCs, Extreme | 50 / 21 / 10 / 5 / **1** after the bridge (a field of 5 is shown the first rows; the lone crosser is the winner, no final needed) |
| 100 NPCs, Normal, server restarted in the middle of Tug of War | resumed at "phase GAME game 3 with 55 alive", replayed the game, then 27 / 14 / 6 / **1** |
| implementer's whole-tournament runs after the bridge small-field change | Hard 63 / 40 / 20 / 10 / 2 / **1**; Extreme 46 / 27 / 13 / 7 / 3 / **1** |

A real-time run (tick rate 20, 100 NPCs, Normal) is summarised in section 7 together with the memory and tick-time samples.

## 4. Single-player (integrated server, integration)

Fresh world created by a throw-away server, opened with `--quickPlaySingleplayer`:

* `/squid enter` generated the dormitory (1.17 M blocks) in 12 s; `/squid start normal 20` generated the six arenas (about 30 s,
  progress messages in chat), then registration -> instructions -> countdown -> Red Light ran.
* As the human contestant: registered (bib number, HUD), ran into a red light and was shot by a guard ("ELIMINATED - shot by a guard"),
  the results board listed survivors and eliminated with the player's own outcome, spectating continued into the next game's
  instructions (Dalgona), `/squid leave` returned to the overworld with the original (empty) inventory, Save & Quit saved cleanly, and
  re-opening the world went straight in (no experimental-settings prompt, see the mixin).
* No server-side exception; the client log contained only environment noise (no audio device, an invalid `fov` option value of the
  test options file, fixed afterwards).

## 5. Multiplayer behaviour (integration, two headless clients)

Two clients (`Contestant`, `Player2`) on the dedicated server:

* both registered (`/squid debug playopen`, `/squid join`), saw each other's game state with different bibs (056 / 130), independent
  HUDs; one moved on red and was eliminated while the other stayed alive and saw the elimination line; results screens were
  per player;
* **Marbles between two humans**: partnership offers from NPCs and between humans (right-click, modal, Enter to accept), "You and
  No. 425 (Contestant) are partners!", then a target-throw match with turns passing between the two human clients (charge bar,
  landing log `off-prediction 0.21`);
* **disconnect and rejoin** during a Red Light game (`/transfer` back to the same server): the AI stand-in played on and the player
  got the same number and an alive body back at the saved position, no duplicate body;
* **grace expiry**: kicked with `disconnectGraceSeconds 6` -> the contestant was eliminated (`DISCONNECTED`); returning after the
  tournament simply put the player in the hub;
* **late join**: connecting while a tournament runs -> spectator mode with "A tournament is in progress. You are watching as a
  spectator." and the current game's rules.

Not covered: more than two humans at once, and a human reconnecting while their own heat or match is still running (implementers
tested the stand-in and the reconnect separately).

## 6. Rules inside the arenas (integration, non-op player)

After `/squid enter` as a non-operator: switching the gamemode by command is reverted within a tick; forbidden items (a diamond sword, ender
pearls) vanish within 10 ticks; a 77-block teleport inside the complex is undone at once (`undid an unauthorised teleport`); containers
cannot be opened (code path covered by the same event hook, not exercised in-world); damage and `kill` respawn the player in the
dormitory. Bounds enforcement during games (warning, put back to the last safe spot, eliminated after about 8 s outside) is a pure rule
(`BoundsRule`, unit tested); in-world, the Red Light gate is closed behind the field (a human walking backwards stops at the door,
still inside the bounds) and the other arenas police the positions themselves (seats, plots, slots, the final's belt), so the
put-back path could not be provoked by a legal walk - a scripted teleport out of the field was judged `ILLEGAL` by the Red Light rules
first. Flying cannot be tested without a creative player (the rule resets abilities every tick).

A test artefact worth knowing: `tp` from RCON moves a player to the *overworld* (the source's level), which looked like "rules
not enforced"; inside the complex use `execute in squidgame:arena run tp ...`.

## 7. Performance and soak (integration)

`/squid debug perf`, Red Light on the field (shared 4-vCPU container, other servers running): 40 NPCs 0.7 ms NPC entities / 4.8 ms
whole tick; 128 NPCs 3.2 ms / 6.3 ms (worst 13.7 ms); 456 NPCs 11.4 ms / 15.4 ms (worst 35.3 ms, budget 50 ms). Game logic and NPC
behaviours stay below 1 ms; implementers measured 128-NPC games of Dalgona (2.0 - 2.4 ms total), Tug of War (4.9 ms whole tick),
Glass Bridge (entities 1.4 - 2 ms) and a 100-NPC Marbles game (logic 0.8 - 2.2 ms).

**Real-time soak** (tick rate 20, 100 NPCs, Normal, `squid debug simulate`, sampled every two minutes): registration to the
winner took **23 min 1 s** (81 / 70 / 34 / 17 / 7 / 1 survivors); the server's resident memory went from 804 MB to 830 MB over the
whole run (no leak), the average whole-server tick stayed between 1.9 and 4.4 ms (worst sample 23.8 ms at the start of the Red
Light game when the arena's chunks are forced), and the log contained **0** "Can't keep up" warnings and **0** errors. After the
winner the tournament cleaned up (RESTART phase, 0.3 ms ticks).

## 8. The games (what the implementers verified)

Each document in `docs/games/` ends with its own verification and limitations section. Summary of the claims (not re-run in full by
the integrator):

| Game | NPC-only in-world runs | Human path (headless client) | Notes |
|------|------------------------|------------------------------|-------|
| Red Light, Green Light | 100-NPC runs on all difficulties (survivors about 80 % / 60 % / 40-45 %), 456 NPCs, restart, reset | played by the integrator: run, elimination by guard, results, two humans | server-side movement judge shared by humans and NPCs |
| Dalgona | 128 NPCs: 85 % / 68 % / 44 % freed; everybody fails, timeout, restart mid-game | tin screen, carving to "FREE!", cracking, licks, Esc/reopen, time-out, disconnect/reconnect with stand-in | stroke validator is lag tolerant |
| Tug of War | 2v2 up to 128 (two 32v32 heats), restart mid-collapse (deck repaired, 840 blocks checked) | pulling, heaving on the beat (server counted hits/misses), bracing, win/lose views, spectator view | vanilla key conflicts (F, Left Shift) handled |
| Marbles | both variants, 2 - 128 contestants, odd counts (bye), restart mid-match | NPC offers, human offers, odd/even as holder and guesser, target throw, kick during a match | integrator: human-to-human pairing and throw turns |
| Glass Bridge | 16 / 40 / 100 NPCs on three difficulties (about 5,500 hops, max landing error 0.043), restart (route + order restored), timeout, stall, disconnect | scripted protocol client for the judging; real client screenshots of queue, overlay, stall warning, crack and fall | the hidden route was tested not to leak (11.5 M guesses at 50.00 %) |
| Final Squid Game | 2 - 12 finalists on three difficulties, ladders of 3, 6, 8, 9, 12, skip/admin/timeout cases, always exactly one survivor | every control reached the rules (lights, heavy, guard, parry, dodge, shove), a waiting human on the belt, kick mid-duel -> forfeit | integrator: 100-NPC tournaments with the ladder |

## 9. Problems found by verification (all fixed)

* NPCs far from every human ran at one third of their speed: direct moves were only renewed by the throttled behaviour tick while
  the vanilla move control consumes its target every tick (Red Light looked like a crawl and nobody crossed on Hard).
* Vanilla `Mob.setSpeed` also sets the forward input, making the pace quadratic in the attribute (NPCs crawled at about 1 block/s).
* `/squid reset` threw a `NullPointerException` when no tournament ran and left the manager inconsistent.
* The tournament's `.gitignore` hid a whole Java package (`build/`).
* The doll showed her face during green light (animation convention mismatch, regenerated).
* Several HUD overlaps: the "DON'T MOVE" banner over the elimination caption, counters over the objective panel, the traffic light
  over the timer bar, results board covered by a flood of elimination chat lines (now summarised after five lines).
* The anti-teleport undo used a teleport variant that does not send a position packet to the client (now the level variant).
* Marbles panel key and Tug of War pull key were both `R` (Minecraft keeps one binding per key): the panel is now `M`.
* Planner/bridge: with a handful of survivors the Glass Bridge could not be crossed at all (8 contestants cross 0.4 on average, so
  the default Hard and Extreme tournaments would have ended with nobody alive). First the planner skipped it below 12 survivors (and
  announces skipped games); then the bridge was changed so that a field of 11 or fewer is shown the first rows up front (public
  `SHOWN` events), which makes it playable from 4 contestants (somebody crosses in 72 - 96 % of simulated games). In small fields
  nobody crosses in 4 - 28 % of games; the tournament then ends without a winner, which is the specified all-fail outcome.
* Minecraft's "experimental settings" prompt on every load of a world with the mod (a fourth dimension): skipped by an optional
  client mixin.
* Test-tool pitfalls fixed along the way: `devserver.sh` kept the caller's pipe open, the single-player client skipped the arena
  builders, `xvfb-client.sh` options (`fov`).

## 10. Not verified and known limitations

* **Sound**: no audio device. Volumes, the doll's chant, music and ambience were designed and mixed by reasoning and spectrograms
  only; everything audible is unreviewed by a human ear.
* **Real hardware feel**: the client ran at 3 - 7 fps under software rendering, so animation smoothness, input feel with a real mouse,
  latency-sensitive timings (heave beats, parry windows, throw charge) and GPU cost of 100 animated NPCs are not judged.
* **Production client**: the client was run in the Loom development environment. The production jar's remapping was checked on the
  server (dedicated server) and statically for the client mixin; a real launcher install with only the three jars was not run.
* **Fabric Loader 0.16.10** (the declared minimum) was not tested; 0.17.3 (server) and 0.19.3 (client) were.
* **Operating systems**: Linux only. Windows and macOS were not tried.
* **More than two humans** in one game, human-to-human duels in the final, and a ladder with several humans were not exercised.
* **Large fields**: 456 contestants were simulated in Red Light only (performance); the arenas are sized for 128 (Dalgona seats 160;
  with more, seats are shared or surplus pairs get a bye).
* **Shared single-player CPU**: the integrated server shares the CPU with the client; very weak machines may want
  `npcCount`/`totalContestants` below the default 100.
* Dalgona needs a pointing device (no keyboard alternative); the glass-bridge hops were verified through the server's judging, not with
  real keyboard hops over the gaps (injected keys overshoot under software rendering).
* The Minecraft EULA was accepted by the test tooling for the dev servers (section 1); the shipped bundle does not do that.

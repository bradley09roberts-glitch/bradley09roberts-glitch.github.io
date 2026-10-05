# Game 2 - Dalgona (honeycomb carving) - as implemented

Game id `dalgona` (`GameKind.DALGONA`, elimination causes `BROKE_COOKIE` and `TIMEOUT`), arena `ArenaId.DALGONA`
(markers `dalgona.seat`, `dalgona.station`, `dalgona.front`, `dalgona.board`, region `dalgona.seating`). Test it with
`/squid debug play dalgona 16` (you take part) or `/squid debug simulate` / RCON `squid debug play dalgona 16` (NPCs only);
`/squid debug timescale 1` first if the dev server still runs with a reduced time scale.

Every contestant sits at a desk with four closed honeycomb tins. The tin is picked blind (the draw decides: **circle,
triangle, star or umbrella**, the umbrella being by far the hardest), then the shape has to be carved out of the cookie
with a needle without cracking it before time runs out. A cracked cookie or the clock running out is an elimination
(a guard fires; shots are staggered). Humans carve with the mouse on a purpose built screen, NPCs carve with the very same
cookie physics through a simulated needle hand.

## Code map

| Part | Files |
| --- | --- |
| Pure rules (no Minecraft types, unit tested) | `core/dalgona/`: `DalgonaShape` (outlines, fragility), `DalgonaRules` (difficulty table, shape draw), `CookieSim` (the cookie physics), `StrokeValidator` (what a client may send), `CrackPattern` (cracks the client grows, from a seed), `NpcCarver` (the NPC needle hand) |
| Server game | `game/dalgona/`: `DalgonaGame` (lifecycle, seating, HUD, board), `DalgonaNpcBehavior` (NPC body language), `DalgonaNet` (state payload), `SeatAnchor` (where a contestant sits) |
| Client | `client/game/dalgona/`: `DalgonaClient` (registration), `TinSelectScreen`, `DalgonaScreen` (the carving table), `CookieModel` (what the client knows of the cookie), `CookiePainter` + `Gfx` (all drawing, textures from `textures/gui/dalgona/`) |
| Fixture arena | `build/placeholder/DalgonaPlaceholder` (version 0 test hall; superseded by the real `DalgonaBuilder`, kept as a fallback fixture) |
| Translations | `tools/lang/dalgona.json` (merged into `en_us.json` by `tools/merge_lang.py`) |
| Tests | `src/test/java/com/squidgame/core/dalgona/*Test.java` (56 tests) |

Nothing outside these packages was modified: networking, screens and the client class are found by name
(`ModNetwork`, `ScreenRegistry.discoverGameClients`), sounds are existing events of `ModSounds`.

## Flow of a game

1. **Instructions** (six pages, `squidgame.game.dalgona.instruction.*`, filled with the difficulty's licks, time and success threshold).
2. **prepare**: guards spawn; every alive contestant gets a seat. Seats are the `dalgona.seat` markers (sorted by `slot`), the
   desk is the `dalgona.station` marker of the same slot. `SeatAnchor` understands both marker conventions: a free floor cell
   with a low bench slab behind it (the real hall, the contestant sits on the bench) and a marker standing on the seat itself
   (the written contract). A text display on `dalgona.board` shows "Free / Lost / Contestants" and is refreshed twice a second.
3. **placeContestants**: NPCs hover on the bench (no gravity, thighs on the slab) with the seated animation, facing the desk;
   humans are teleported and ride an invisible marker armor stand on the seat so they stay put and are seen sitting (re-mounted
   if they get off, pinned to the seat if no stand could be spawned).
4. **begin**: the shapes are drawn (`DalgonaRules.assignShapes`: from four contestants on every shape appears at least once, the
   rest follows per-difficulty weights, deterministic for the game seed). The tin selection is part of the game clock
   (10 s at time scale 1, at least 2 s): a human gets the tin screen, NPCs pick after one to eight seconds. The tin is a blind
   choice: whatever tin is clicked, the shape is the one the draw gave that contestant.
5. **carving**: the contestant's `CookieSim` is stepped every server tick; a human's screen is fed the authoritative state
   (at most every 3 ticks when something changed, every 10 ticks otherwise). Success ("free") needs 96 / 97 / 98 % of the
   groove carved (Normal / Hard / Extreme). The cookie cracks at stress 100.
6. **a crack** plays `dalgona.snap`, crumbs and, for a human, a red vignette; the guard fires 50 ticks later for a human (time to
   watch the cookie break) or 22 to 41 ticks later for an NPC, never closer than 8 ticks to the previous shot.
7. **end**: the game is finished as soon as nobody is selecting or carving any more. When the clock runs out the unfinished are
   marked "out of time" (humans see TIME'S UP). `conclude` returns the survivors (everyone whose shape is free) and lets the guards
   eliminate every other contestant within the 90 ticks of the eliminations phase (cracked cookies keep the shot they already
   have queued; a human who timed out gets 36 ticks to read the message first).
8. **cleanup** removes seats, the board and guards, closes the screens and gives the NPC bodies their gravity back.

Disconnects: when a human leaves, an AI stand-in takes the seat and carries on from the same cookie (`NpcCarver.resumeNear`);
when the human returns, the screen reopens with the current state of the cookie. A server restart replays an interrupted game from
its start (no saved state is needed). Eliminations by an admin, or an elimination while the screen is open, close the screens.

## Playing it (human)

* **Tin screen**: four tins; hover lifts one, click it or press `1`-`4`, the lid slides off and the carving table opens. A bar
  shows the selection time; when it runs out a tin is opened for you. `Esc` closes the screen (the clock keeps running).
* **Carving table** (opens with "You drew: star"; scaled to GUI scale and window, readable at GUI scale 2 to 4):
  * hold the **left mouse button** and trace the groove with the needle: the needle is the mouse cursor, carved parts turn into a dark
    furrow with crumbs and a glint where the needle just cut. The faint lane around the needle shows how far from the groove you may stray;
    amber ticks on the groove mark fragile stretches (thin tips, sharp corners), which tolerate less speed;
  * **right mouse button**, **`L`** or the lollipop button **licks** the cookie: stress drops, the needle is locked for 36 ticks,
    there is a cooldown (100 ticks) and a limited number of licks (5 / 3 / 2). Licking locks the needle: lift it first;
  * left gauge: **stress** (green, amber, red, pulsing near the top, red screen edges and heartbeat from 65, "IT IS ABOUT TO CRACK" from 80);
    right gauge: **carved** with a white notch at the success threshold; bar under the cookie: your **needle speed** against the safe
    speed of the spot you are on (the white mark is the limit), "SLOW DOWN" appears when you exceed it; the timer is on top;
  * hairline cracks grow with the stress (the same pattern every time for the same cookie), a hit leaves a bigger impact mark with a
    screen shake and a crack sound, a lick adds a sheen over the cookie;
  * success: the shape pops out of the cookie ("FREE!"), failure: the cookie shatters into pieces ("THE COOKIE CRACKED"), out of
    time: "TIME'S UP". The screen closes itself after a few seconds;
  * hovering the gauges and the lick button explains them (tooltips); `Esc` steps away from the table, **right-click the desk**
    (`dalgona_station` block) to come back (the state is restored from the server).
* The HUD (outside the screen) keeps the stress bar, licks left, carved percentage and "Cookies free: x / n".

## The rules, exactly

Canvas: the cookie is a square of 1024 x 1024 units, the outline is a closed polygon resampled every 8 units; the samples are the
unit of progress. Speeds are canvas units per server tick (20 per second).

| | Normal | Hard | Extreme |
| --- | --- | --- | --- |
| Time (carving + 10 s tin selection, x time scale) | 150 s | 120 s | 95 s |
| Tolerance around the groove (units) | 22 | 17.6 | 13.2 |
| Safe needle speed (units / tick) | 7.2 | 6.6 | 6.0 |
| Stress sensitivity (speed, wobble, cutting) | x 0.7 | x 1.0 | x 1.75 |
| Licks / relief per lick | 5 / 38 | 3 / 34 | 2 / 30 |
| Lick cooldown, needle lock (ticks) | 100, 36 | 100, 36 | 100, 36 |
| Stress decay per tick, resting / carving | 0.10 / 0.02 | 0.09 / 0.02 | 0.08 / 0.02 |
| Micro-fracture chance base (per tick) | 0.0006 | 0.0009 | 0.0013 |
| Free at carved fraction | 96 % | 97 % | 98 % |
| Shape weights circle / triangle / star / umbrella | .30 / .30 / .22 / .18 | .20 / .27 / .28 / .25 | .10 / .22 / .31 / .37 |
| NPC skill bonus (global difficulty) | +0 | +0.12 | +0.25 |

Brittleness scales the speed, wobble and cutting stress of a shape: circle 1.0, triangle 1.0, star 1.1, umbrella 1.5. The fragility of a spot
(1 = robust, at most 2.8) grows with the sharpness of the turns around it and with thin features (the star tips, the umbrella
handle and canopy points); the safe speed there is `safe / fragility^0.7`.

What a needle path does (`CookieSim`, evaluated every 5 units of path):

* within the tolerance of the groove the samples around the needle are carved and a little **wobble stress** accrues, growing with
  the square of the distance from the groove, per unit of path;
* further away the needle is **cutting into the cookie**: stress per unit grows with the distance beyond the tolerance (capped at
  three tolerances), the fragility of the spot, and is worse inside the figure than in the waste (x 1.0 against x 0.6);
* once per message **speed stress** `k (ratio - 1)^2 * dt` when the needle (or the rate at which new groove is carved: a leaky
  bucket with a 60 unit burst that refills with the safe speed, so single clicks cannot "teleport" along the groove) is faster than the
  local safe speed;
* a rare **micro-fracture** adds a spike of 6 to 13 stress; its chance grows with the stress already on the cookie and with rushing;
* stress decays slowly while carving and faster while the needle rests (3 ticks without a stroke); a lick removes the relief at once.

The groove must be carved within the tolerance; carving far off the groove (but inside the cookie) does not carve anything.

## Server-side validation of what the client sends

The client sends `dalgona.stroke` messages (about one per tick while the needle moves) with the points of that tick, the time they
took and start / end flags. `StrokeValidator` (one per human) does not trust any of it:

* a **token bucket** of 1.5 messages per tick (bursts of up to 100, so a server hiccup that delivers seconds of messages at once does
  not drop an honest client's strokes) stops floods; messages with no points, more than 40 points or a coordinate outside the canvas
  are dropped;
* the **time claim** is only credited as far as the server's own clock allows: credit accrues with the *real* time that passed on the
  server (not with game ticks, so a lagging server does not punish an honest client; at most 100 ticks can be banked), a claim is
  worth at most 12 ticks and a stroke starts with at most 4, so neither slow motion nor banked time can hide a fast stroke;
* a needle that is down may not move faster than **260 units / tick** (the path is cut off and the cookie takes a penalty), a lifted
  needle not faster than **700 units / tick** between two strokes (a teleport costs 25 stress);
* repeated violations (strikes decay over 100 ticks, six is the limit) are treated as tampering: the cookie cracks;
* only while the game runs, the contestant is alive, human controlled and their cookie is being carved; `dalgona.pick` is only
  accepted while selecting; the shape never comes from the client. The client's screen only predicts the carved samples (they are
  replaced by the server's set once nothing is in flight); stress, licks, cracks, success and time always come from the server.

The legitimate limit that matters is the safe speed: a client that moves faster simply cracks its cookie sooner.

## NPC contestants

`DalgonaNpcBehavior` + `NpcCarver`: the NPC uses exactly the same `CookieSim` and only "sees" what a contestant sees (the groove
and its fragile spots, what is carved, its stress, licks, the clock).

* Personality sets the **pace** (patient: slow and steady; risk-takers and the brave: fast; panic and a gamble when the clock runs
  low), the **hand** (tremor, how well it follows the groove, occasional slips that jerk the needle sideways; skill plus the
  difficulty's bonus) and **foresight** (how early it slows down for corners and thin spots).
* It licks when its stress passes a personal threshold, stops and lets stress drain when it gets too high, flinches after a
  mishap (pause for its reaction time, then a cautious pace for a while), goes back to touch up stretches it missed and may cut a
  corner when time is nearly up. It starts near the top of the figure like most people do.
* A **shot or a broken cookie nearby** startles NPCs within 12 blocks (cowardly ones more often): a flinch, a pause, a slower pace.
* Body language: `DALGONA_SIT` while waiting (glances at the board, the tin, neighbours; nervous contestants more often),
  `DALGONA_CARVE` with a needle in hand while the needle moves, back to sitting while it rests, the one-shot actions
  `dalgona_lick` and `dalgona_crack` (also for a hard jolt), `dalgona_success` when free, `DALGONA_FAIL` when the cookie breaks;
  scratching sounds at the desk (only sent when a player is within 14 blocks) and a crack sound for a spike.
* **Calibration** (Monte Carlo, 400 simulated contestants with random personalities per cell, fixed seeds; `NpcCarverTest`
  asserts the bands 72-92 % Normal, 52-76 % Hard, 32-58 % Extreme and the ordering of the shapes):

  | NPC success | circle | triangle | star | umbrella | overall |
  | --- | --- | --- | --- | --- | --- |
  | Normal | 97 % | 92 % | 77 % | 62 % | 82 % |
  | Hard | 85 % | 77 % | 49 % | 45 % | 64 % |
  | Extreme | 61 % | 58 % | 37 % | 32 % | 47 % |

  Faster NPCs fail more, the umbrella is clearly the hardest, nothing is 0 % or 100 %. Whole games on the dev server with 16 NPCs
  land in the same range (the numbers per game scatter with so few contestants).

## World

* Hall: the real `DalgonaBuilder` hall has 160 desks (`dalgona.seat` / `dalgona.station`), a chalkboard and a teacher's stage; the
  guards come from `ctx.spawnGuards()`. With more contestants than seats the surplus shares seats (shifted sideways), which the
  shipped arenas never need.
* The cookie on the desk is the `dalgona_station` block; right-clicking another contestant's desk is refused with a message.
* `DalgonaPlaceholder` is a small fixture hall using the documented marker contract (8 rows of 4 blocks of 5 seats, bench and desk
  slabs, stations, front / board / exit / spectator / guard posts). It only exists to test without the real builder.

## Networking

* Server to client: `OpenScreenPayload` OPEN / CLOSE for the screens `dalgona_tins` (data: `left`, `total` ticks, `seed`) and
  `dalgona` (data: `shape`, `difficulty`, `seed`, `intro`); `DalgonaNet.StatePayload` (stress, carved count, licks, lick
  cooldown / lock, flags cracked / done / timeout, events spike / lick / penalty, time left / total, the carved bits).
  The state has its own payload because the generic UPDATE would reopen a screen the player closed with `Esc`.
* Client to server: `ClientActionPayload` ids `dalgona.pick` (`tin`), `dalgona.stroke` (`p` packed points `x << 10 | y`, `t` claimed
  milliseconds, `f` flags 1 = needle down, 2 = needle up), `dalgona.lick`, `dalgona.sync` (asks for the current screen and state).

## Sounds and assets (all existing)

`needle.scratch`, `dalgona.crack`, `dalgona.snap`, `danger.heartbeat`, `ui.select`, `ui.confirm`, `ui.deny`, `ui.number_call`,
`game.end_buzzer`, `countdown.tick` / `countdown.beep` (a ticking clock for humans still carving in the last ten seconds, beeping
in the last three); textures `gui/dalgona/{cookie,needle,table,crack_0..3}.png`, `gui/hud/icon_lick.png`. Everything else (tins,
gauges, cracks, furrow, shards) is drawn procedurally.

## Tests and how it was verified

* Unit tests (`core/dalgona`, 56): shape geometry and fragility, cookie physics (speed / wobble / cutting / licks / rest / micro
  fractures, determinism), stroke validation (rate, bounds, time, speed, teleports, strikes), difficulty table and shape draw, crack
  pattern, NPC pass rates per difficulty and shape (umbrella hardest, never 0 / 100 %, faster NPCs fail more).
* In the world, NPC only: games with 16, 24, 40 and 128 NPCs on the real hall in all difficulties (no exceptions, about 3 ms per tick
  on average with 128; 128 NPCs on Normal: 83 % freed, umbrella 60 %, circle 97 %; 40 on Extreme: 45 %), a game in which everybody
  fails (the tournament carries on with nobody left), a game that runs out of time, a server restart in the middle of a game (the
  game is replayed from its start), several time scales (`squid debug timescale 0.05 .. 1`).
* In the world with a real client (headless, software rendering, scripted mouse and keys) as the human: the tin screen (hover, lid,
  automatic pick), carving a triangle, a star and the umbrella, stress and cracks growing, the lick (relief, sheen, cooldown), the
  cookie cracking and shattering, the shape coming free, the clock running out with the screen open, `Esc` and re-opening by
  right-clicking the desk, disconnecting mid-carve (an NPC took the seat and freed the star), the seated humans and NPCs seen from
  the bench, from the end of a row and in third person (leaning over the desks with the needle in hand), window sizes from 960 x 540
  to 1280 x 720 at GUI scale 3.

## Known limitations and notes

* Carving needs a mouse (or any pointing device); there is no keyboard alternative.
* The client draws the groove from the same shared polygon as the server, so both always agree; a client of a different mod
  version is not detected.
* The seated look of a human (invisible armor stand ride) is a vanilla riding pose; a contestant who gets off the seat (sneaking)
  is put back after a few ticks.
* NPC bodies hover on the bench with gravity off and are given gravity back when the game ends; a server crash in the middle
  of a game leaves them to the generic cleanup of the tournament.
* The real hall has 160 seats and the default contestant cap is 128; with more contestants (`maxContestants`) they share seats and
  sit side by side on the bench.
* Not checked: sound (the headless client has no audio device; volumes were set by reasoning, not by ear), GUI scales 2 and 4
  on large screens, the carving feel with a physical mouse, a human returning after a disconnect (the code path reopens the screen
  with the cookie as it is).

# Game 2 - Dalgona (honeycomb carving) - design spec

Package names: `core/dalgona/`, `game/dalgona/` (`DalgonaGame`), `client/game/dalgona/` (`DalgonaClient`), fixture
`build/placeholder/DalgonaPlaceholder`. Game id `dalgona` (`GameKind.DALGONA`, `EliminationCause.BROKE_COOKIE`), arena
`ArenaId.DALGONA` (markers: `dalgona.seat`, `dalgona.station`, `dalgona.front`, `dalgona.board`, region `dalgona.seating`, plus the
common markers). Test with `squid debug play dalgona 16`. Port range for your test server: 25610/25611.

## The game
Every contestant sits at a seat in the huge classroom and receives a honeycomb tin with a shape pressed into it: **circle,
triangle, star or umbrella** (hardest). They must carve the shape out *without cracking the cookie* before time runs out. A cracked
cookie or an expired timer = elimination (a guard shoots: `ctx.eliminateByGuard`, staggered so it reads well).

### Human experience
1. At the start (`begin`) a short **tin selection**: four closed tins are offered (screen); the pick is blind and the shape is
   assigned (weighted random: every shape appears; humans do not see which tin has what, the "luck" is part of the show).
2. Then the **carving screen** (`DalgonaScreen`, opened with `ctx.screen`; also re-openable by right-clicking the contestant's
   `dalgona_station` block via `MiniGame.onBlockUse`, and closable with Esc - the timer keeps running):
   * a large honeycomb cookie (drawn procedurally with `GuiGraphics`: amber disc, sugar speckles, the shape as a groove),
   * the player holds the mouse button and moves the needle along the outline; carved parts darken, the needle tip glows,
   * a **stress meter** (cookie integrity) with colour change, hairline cracks growing on the cookie as stress rises (jagged
     procedural lines, deterministic from a server-sent seed), tiny crumbs/particles when carving fast,
   * progress %, time left, **lick** button/key (a warmed lick softens the sugar: lowers stress; limited number, cooldown),
   * clear tooltips for the controls; everything readable at GUI scale 2-4.
3. Success: the shape "pops out" (animation + sound), the contestant is safe and watches the others (seated, relieved pose).
   Failure: crack sound, the cookie breaks visibly, guard shot.

### Server-authoritative simulation (`core/dalgona`, pure Java, unit tested)
* `DalgonaShape`: the four outlines as normalised polylines (canvas 0..1023 integer units), `outlineSamples(n)`, `distanceTo(x,y)`,
  per-point **fragility** (thin tips of the star, the umbrella's handle and canopy corners are fragile; circle is robust).
* `CookieSim`: state (stress 0..100, carved samples bitset, licks left, cracked flag, time) and `stroke(points, ticks)` /
  `lick()` / `tick()`. Per sample: distance to the outline (outside `tolerance` = cutting into the figure's body => stress),
  needle speed above the safe speed => stress (quadratic), fragility multiplier near fragile points, tiny random micro-fracture
  chance (rare, grows with stress and speed), slow stress decay while the needle rests. Progress counts outline samples passed
  within tolerance; success at >= 96-98 % (tune). Deterministic for a given seed/input.
* `DalgonaRules`: difficulty table (time limit ~150/120/95 s, tolerance via `toleranceScale`, stress sensitivity, licks 5/3/2
  via `resourceScale`, shape weights), NPC skill bonus.
* Validation of client strokes: max points per message (<= 40), max message rate, canvas bounds, max needle speed per tick
  (teleporting needle = penalty), only while the game runs and the contestant alive and unfinished. The client may animate
  locally but the server's `UPDATE` messages (stress, progress, cracks, finished/broken) are the truth.

### NPC contestants (`DalgonaNpcBehavior`)
Same simulation, same limits: the NPC "carves" by generating strokes along its outline at a speed set by personality
(patience -> slower and steadier, riskTolerance/courage -> faster, panic when time is short), precision noise by effective skill
(`Personality.effectiveSkill`), uses licks when stress passes a personal threshold, may restart a risky segment more slowly,
may give up and rush near the end. Body language: `Activity.DALGONA_SIT` while waiting, `DALGONA_CARVE` while working,
`DALGONA_FAIL` on a crack, relief/celebration on success. Sanity target (Monte-Carlo test with fixed seeds): average NPC success
roughly 75-85 % Normal, 55-70 % Hard, 35-50 % Extreme, umbrella clearly the hardest; never 0 % or 100 %; faster NPCs fail more.
Disconnected human => the stand-in NPC continues from the same cookie state.

### World and flow
* `prepare`: spawn the guards (`ctx.spawnGuards()`), the teacher/board text on `dalgona.board`, per-seat props if you like (e.g. a
  floating progress bar / number plate over each table updated at <= 2 Hz; keep entity counts sane with 128 seats).
* `placeContestants`: every alive contestant to its `dalgona.seat` (humans seated or locked in place so they cannot wander; NPCs
  `Activity.DALGONA_SIT`). Seat slots >= number of contestants (`slot=N`).
* `begin`: selection, then the clock starts. `isFinished`: everybody resolved (done or broken). `onTimeout`/`conclude`: whoever
  is unfinished is eliminated (cause `TIMEOUT` or `BROKE_COOKIE` as fits), via guard shots; two simultaneous breaks must both be
  handled; "everybody fails" must not crash (the tournament handles 0 survivors).
* HUD widgets: stress bar, licks counter, finished counter; objective text; translation keys in `tools/lang/dalgona.json`.
* Sounds (existing events only): needle scratch, crack, lick, success, ambient classroom, countdown, etc. - see the sound
  section of `docs/ASSET_CONTRACT.md` and `registry/ModSounds`.
* `cleanup`: remove props/entities, close client screens (`OpenScreenPayload.CLOSE`), clear state.

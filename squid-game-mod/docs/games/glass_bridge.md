# Game 5 - Glass Bridge

Game id `glass_bridge` (`GameKind.GLASS_BRIDGE`, arena `ArenaId.GLASS_BRIDGE`, causes `EliminationCause.FELL` / `TIMEOUT`).
Implemented in:

| Layer | Package / file |
|-------|----------------|
| pure rules (JUnit tested) | `core/bridge/`: `BridgeRoute`, `BridgeKnowledge`, `BridgeRules`, `BridgeQueue`, `BridgeLayout`, `HopPlanner`, `BridgeNpcRules` |
| server game | `game/bridge/`: `GlassBridgeGame` (the `MiniGame`), `BridgeNpcBehavior` + `BridgeMotor` (NPC brain and body), `BridgePublicView` (what NPCs may see), `PanelField` (panels in the world), `Crosser`, `BridgeMapPayload` + `BridgeNet` (public-knowledge packet) |
| client | `client/game/bridge/`: `BridgeClient` (registers the receiver, found by name), `BridgeOverlay` (row map) |
| fixture arena | `build/placeholder/GlassBridgePlaceholder` (version 0, real geometry) |
| translations | `tools/lang/glass_bridge.json` (merged into `en_us.json` by `tools/merge_lang.py`) |
| tests | `src/test/java/com/squidgame/core/bridge/` (`BridgeSim` is a whole-game simulator on the pure rules) |

Test it: `squid debug play glass_bridge 16 [normal|hard|extreme]` (no player online = NPC-only run; `/tick rate 100` makes
a long game pass 5x faster; `squid config debug true` prints the `[bridge]` log lines quoted below).

## The game

Eighteen rows of two glass panels span a pit. In every row one panel is **tempered** (holds you) and one is **fragile**
(shatters, you fall = eliminated). Nobody knows which is which. The contestants cross **one after another in a random
order drawn at the start**; the first ones gamble, the later ones learn from what they *saw* happen. Everybody who
reaches the far platform survives. Whoever is not across when the time runs out is eliminated.

## Arena contract as used

Geometry (`docs/ARENA_MARKERS.md`): panels are 2x2 blocks of `squidgame:bridge_glass` at deck height y = 40 (standing
height 41 above the arena origin), lane 0 at x -3..-2, lane 1 at x 1..2, row r at z 10 + 3r .. 11 + 3r (1 block between
rows, 2 blocks between lanes), start platform in front of row 0, end platform behind row 17, open pit down to y = -30.

Markers read by the game: 36 `bridge.panel` (`row=R,lane=L`), `bridge.queue` (`slot=N`, slot 0 at the gate), `bridge.gate`
(door marker, `w=7,h=5`), `bridge.finish_spawn`, `bridge.pit_floor`, optional `bridge.hanging_cage`; regions
`bridge.start`, `bridge.finish` (the platforms; they define where take-offs and finish checks happen). The panel marker is
interpreted as *a point on the first (lowest x, lowest z) block of the panel*: the footprint is the 2x2 blocks starting at
the block that contains it. That is the documented convention (x -2.5 / 1.5, z 10.5 + 3r) and a marker given at the
panel's centre (x -2 / 2, z 11 + 3r) gives the same footprint, so both builders' conventions work. If the markers are
missing or incomplete the game logs an error and ends immediately with everybody surviving.

Left / right: the HUD and the overlay name lanes **as the contestant sees them walking +Z**: lane 1 (x = +1.5) is the
*left* lane, lane 0 the *right* one (the arena document's "left/west" is the map view).

## Rules as implemented

1. **Route.** `BridgeRoute` holds one safe lane per row, generated from the tournament seed and game number in
   `prepare` (or restored from the saved game state, see below). It lives only in `GlassBridgeGame`; the NPC
   behaviours receive a `BridgePublicView` that has no reference to it, and `BridgeRoute.toString` prints `hidden`. The
   route never changes while the game runs (it is not re-drawn when somebody falls).
2. **Order and gate.** `placeContestants` draws a random crossing order (`BridgeQueue`) and stands the contestants on the
   `bridge.queue` slots, the first in line next to the gate. The gate door (`bridge_gate`) stays closed through the
   countdown. 3.5 s after the start the gate calls the first contestant (title YOUR TURN + chime for a human, the door
   slides open, the guard gestures). The next one is called when
   * nobody is still standing at the gate,
   * fewer than `maxCrossers` contestants are on the bridge,
   * `minReleaseSpacing` has passed since the last call, and
   * the previously called contestant is at least `releaseGapRows` rows into the bridge (or finished / gone).

   Nobody can skip the line: a queued human who walks through the open door out of turn is put back on their slot,
   and a finished contestant who walks back onto the glass is put back on the far platform. The next in line walks up to
   the gate while the previous one crosses (on-deck staging), so the gate does not wait for a walk across the platform.
   A called contestant who has not touched the first panel within `callLimit` is eliminated (`TIMEOUT`, "froze at the
   gate").
3. **One contestant per panel.** A panel is occupied from take-off to landing and can be reserved by an NPC about to
   hop onto it; nobody lands on an occupied or reserved panel. Two humans may be on the bridge at the same time (different
   rows or lanes).
   **Every row has to be stepped on.** A sprint jump that lands two rows ahead (or on the far platform from the last
   but one row) would dodge a row's gamble: such a landing is not accepted, nothing is judged and the contestant is put back
   on the last panel they stood on (or the platform) with a message ("You have to step on every row").
4. **Judging a panel (humans and NPCs alike, `GlassBridgeGame.arrive`).** A contestant "is on a panel" when their body is
   `onGround` on the glass surface and the 0.6 wide hit box overlaps the panel's 2x2 footprint. The first touch decides:
   * *tempered*: nothing happens; after `confirmTicks` (just longer than the shatter window) the row is publicly
     recorded as **held** (soft chime and a shimmer on the glass) and everybody who watched knows which lane is safe;
   * *fragile*: the panel **cracks at once** (glass-crack sound, shards, the vanilla crack overlay grows over the four
     blocks) and **shatters 6-10 ticks later** (`shatterDelayMin..Max` by difficulty; shards, cloud, loud shatter).
     The contestant is **condemned the moment they touch it, however fast they run on**: a human is frozen with fear
     (Slowness 10 so they cannot walk, the jump strength attribute zeroed so they cannot hop away - a negative Jump
     Boost is not an option, vanilla clamps the amplifier to 0..255 - plus a white flash and a danger pulse) and falls
     with the glass; an NPC is told its panel cracked and cowers. The row is publicly recorded as **broken** when the
     glass goes. Whoever else touches a cracking panel goes down with it.
   * A body that is more than 6 blocks below the deck has fallen: `EliminationCause.FELL`, "No. 087 fell at row 5", the
     spotlight rigs above the deck flash and the impact is heard from the pit floor 1.3 s later. Nobody is teleported:
     the body falls down the pit and the tournament removes it later. Safety net: a condemned contestant who is
     somehow still above the pit 26 ticks after the glass went is eliminated anyway, so nobody survives by clinging to
     an edge.
5. **Public knowledge.** Every *held* / *broken* event goes into the `BridgeKnowledge` log; a row is known when one
   panel has been seen holding or shattering (the other one is then the opposite). The log feeds the NPCs (through the
   public view), the HUD ("Row 7, left panel: shattered") and the client overlay. Nothing else about the route is ever
   published.
6. **Stall rule.** The stall clock of a contestant starts at the moment they land on a new row and runs while they stay
   on that row. It is **held while somebody stands on a panel of the next row** (waiting behind a slower contestant never
   counts). When it expires the glass under them cracks (8 ticks) and the contestant falls (`FELL`; "too long on one
   panel"). That panel was tempered, so the bridge must stay passable: it **re-forms 5 s after it
   shattered** (as soon as nothing is inside it). Humans see the stall bar and, in the last `stallWarn` seconds, a red
   pulsing screen edge with the heartbeat and a MOVE! banner.
7. **Timeout.** `timeLimit = max(base, fixed + perContestant * contestants)` (see the table), scaled by the global
   `timeScale`. When it runs out ("TIME'S UP") everybody not across is eliminated (`TIMEOUT`); the panel under anybody who
   is still on the bridge shatters. So the clock only matters for a big crowd: the gate needs about 7 / 8.5 / 11 s per
   contestant (Normal / Hard / Extreme: fewer simultaneous crossers and longer call spacing as it gets harder), and the
   allowance per contestant is 11.4 / 4.5 / 2.9 s, which is the difficulty ladder for crowds (below). A contestant who
   went back to the start platform after having entered the bridge and stands there for 6 s is eliminated the same way
   (so nobody can block the gate by standing off the glass).
8. **Finishing.** A contestant on the far platform (on the ground, inside `bridge.finish`) is safe: title "YOU MADE IT
   - across the bridge as number N", the finish rank is stored in the contestant's stats and the contestant is taken to
   its `bridge.finish_spawn` slot (NPCs walk there, celebrate or cry). The game ends as soon as everybody still alive is
   across. If everyone falls early it simply ends: nobody survives, no exception, the tournament carries on.
9. **Cleanup.** Every panel is restored, all crack overlays removed, the guards cleaned up, the client overlay cleared, the
   jump lock and the slowness removed. The bridge is also restored in `prepare`, so a game that starts after a crash or
   a restart always starts with an intact bridge.

### Difficulty table (`BridgeRules.params`)

| | Normal | Hard | Extreme |
|---|---|---|---|
| time limit, shortest (small crowds, the 16 of the show; it applies up to 20 / 33 / 34 contestants) | 330 s | 270 s | 210 s |
| time limit of a big crowd: fixed part + per contestant | 98 s + 11.4 s | 120 s + 4.5 s | 110 s + 2.9 s |
| (what a well-playing NPC field needs: fixed + per contestant, measured) | 62 s + 7.1 s | 34 s + 8.5 s | 11 s |
| stall limit (stay on one row) | 25 s | 18 s | 12 s |
| stall warning (HUD turns red) | last 8 s | last 6 s | last 5 s |
| shatter delay after the first touch | 8-10 ticks | 7-9 ticks | 6-8 ticks |
| "held" is announced after | 16 ticks | 15 ticks | 14 ticks |
| rows the previous contestant must be in before the next is called | 2 | 3 | 3 |
| contestants on the bridge at once | 5 | 4 | 3 |
| minimum time between calls | 2.0 s | 2.5 s | 3.0 s |
| time to step onto the first panel after being called | 33 s | 26 s | 20 s |
| NPC hesitation scale (smaller = more decisive) | 1.0 | 0.85 | 0.70 |
| NPC nerve: slip scale (chance to misstep on a row they know) | x1 | x3 | x7 |
| NPC skill (global `Difficulty.npcSkillBonus`, attention and slips) | +0 | +0.12 | +0.25 |

**The ladder** (whole-game simulation of an NPC-only crowd with the exact rules, 80 games per cell, share of the crowd that
gets across; `BridgeSimTest` asserts it): the survivors of the bridge are decided by the gamble of the first contestants
(about 10 fall whatever the difficulty: every row has a fragile panel somebody must find) and, for a big crowd, by the
clock.

| crowd | Normal | Hard | Extreme |
|---|---|---|---|
| 16 | 36 % | 33 % | 30 % |
| 24 | 57 % | 51 % | 28 % |
| 40 | 70 % | 45 % | 20 % |
| 64 | 79 % | 46 % | 21 % |
| 100 | 85 % | 48 % | 22 % |

So on Normal most of the crowd gets across (the clock never cuts anybody off), on Hard about half, on Extreme few. A crowd
of 16 is decided almost entirely by the gamble (the show's 16 also lost most of its players): the three difficulties then
differ by the shorter stall limit, the faster shatter, the slower and more careful gate and the nerve of the NPCs rather
than by the clock.

## Controls and UI for humans

No special controls: walk, sprint and jump. Crossing a row means jumping the 1 block gap; changing lane means a sprint
jump over the 2 block lane gap (a diagonal hop is 3.9 blocks and needs a run up from the edge of the panel). Server HUD
widgets (`hudWidgets`):

* waiting: "Players ahead of you" counter; "You are next! Stand by the gate" when on deck;
* called: YOUR TURN banner and the "step onto the bridge" countdown (`callLimit`);
* on the bridge: row counter `7/18`, the stall bar ("Reach the next row", blue "Waiting for the contestant ahead" while
  the clock is held), a MOVE! banner and red edge pulses when it runs low;
* everybody: last event line ("Row 7, left panel: shattered", shown for 11 s) and a status line (on the bridge / across /
  waiting); chat/action-bar lines for calls, falls, finishes and stalls.

Client overlay (`BridgeOverlay`, top right, only while this game is in its GAME phase): one cell per panel, near end at
the bottom, lanes as seen walking forward: grey = nobody knows, green = seen holding, dim red = known weak, black with a
red frame = shattered; your row is framed. It is built from the server's public-knowledge packet only (states of
`BridgeKnowledge.snapshot()`), so it can never show more than a spectator saw. The packet is sent to everyone in the
audience whenever the knowledge changes (and every 5 s as a keep-alive) and cleared in `cleanup`.

## NPC behaviour

Per NPC `BridgeNpcBehavior` (brain, throttled with the other NPC thinking) and `BridgeMotor` (body, stepped every
server tick). The NPC sees only `BridgePublicView`: the layout, who stands where, which glass is gone, the queue and the
gate, the public event log and its own stall clock. **What it knows it learned**: new public events are copied into the
NPC's own `NpcMemory` (`bridge.safe.<row>` = safe lane) with a small chance of missing one (attention 98.5-100 % by skill),
and the decision uses only that memory. A glaring hole in the glass is also "seen": an NPC that wants a panel that has
shattered concludes the other one holds, even if it missed the crash.

* **Queue**: stands on its slot, glances at the bridge, nervous ones shake their heads; the next in line steps up to the
  gate; when called it reacts (personality's reaction time), walks to the platform edge in line with the lane it chose.
* **Known row**: takes the safe lane. A *slip* (stepping on the wrong panel although it knows) happens with
  `slipChance`: 0.1 % to 1.3 % for a sharp / sloppy contestant, scaled by the stall pressure (fraction of the stall limit
  used) and by fear (witnessed falls), capped at 20 %.
* **Unknown row**: hesitates 1-7.5 s depending on caution (longer when frightened, shorter for skilled contestants and on
  harder difficulties, never more than 60 % of the stall limit), then guesses with a personal left / right bias and a
  superstition about repeating or alternating the previous lane (the route is uniform, so no strategy beats a coin flip;
  personality changes how it *looks*). Very low courage may freeze on an unrevealed row until the stall rule breaks the
  glass under it. While somebody stands on a row that is not yet revealed, followers wait for the verdict instead of
  gambling on the other lane.
* **Hopping**: the motor walks to the take-off spot (clamped inside the panel it stands on so it can never step off the
  edge), jumps with a planned up speed (12-13 ticks of flight) and re-aims its horizontal speed every tick at the landing
  point from the remaining flight time, so the touch down is exact and a bump is corrected; gravity and collisions are
  vanilla. Straight hops cover 2.2 blocks, the diagonal lane change 3.9. The `[bridge] hop ...` /
  `landed ... error ... blocks` debug lines show plan and result.
* **Fear**: witnessing a fall raises fear (sooner and more for the timid); it decays slowly and slows the NPC down /
  makes it slip more.
* **After landing**: settles for 0.3-1 s, then decides the next row. When the glass cracks under it the NPC cowers; when it
  is across it celebrates (fist, cheer) or sobs by personality.
* A stand-in that takes over from a disconnected human continues from wherever that human was (queue, gate or panel).

## Persistence and restart

`saveState` stores the route (as bits) and the crossing order; `loadState` (called by the tournament before `prepare`)
puts them back. After a server restart the tournament replays the interrupted game from its instructions with the same
game number and seed. The bridge is rebuilt intact in `prepare` (a half-broken bridge from before the restart does not
survive), the **hidden route is the saved one, and the crossing order is the saved one restricted to the contestants who
are still alive**: those eliminated before the restart stay eliminated, everybody else keeps their relative place in the
queue (a roster that no longer fits the saved order gets a fresh random order). What is **not** persisted is the public
knowledge: after a restart everybody starts again without having seen anything (the NPCs and the overlay start blank)
and the contestants who are still alive have to cross the same bridge again. Humans are treated as absent after a
restart and are taken over by stand-ins until they return (tournament behaviour).

## Edge cases (all handled, see the verification below)

everybody falls early (the game still ends cleanly); a human disconnects on the bridge (stand-in continues); a human or
NPC stalls (the panel breaks, the queue moves on, the panel re-forms); two contestants hop at the same time; a
contestant is eliminated by an admin (`squid debug eliminate`) or knocked off the deck (any body below the deck minus
6 blocks has fallen); a resumed game after a restart (same route, bridge rebuilt, queue restored); 2..128 contestants.

## Known limitations and suggestions

* **Small crowds cannot cross.** The bridge is only passable for a contestant who has seen the glass of their
  predecessors; with fewer than about 10 contestants nearly everybody falls (simulation, all NPC, Normal: 2-6 contestants
  0 % cross, 8 contestants 0.3 on average / 15 % chance that anybody crosses, 10: 1.1 / 52 %, 12: 2.3 / 81 %, 16: 6.0,
  24: 13.5, 40: 28, 100: 85). `GameKind.GLASS_BRIDGE.minParticipants` is 2, so the planner will send three
  survivors onto the bridge. Suggestion for the integrator: raise it to about 10 (or let the planner skip the bridge when the
  survivor count is below that).
* The route is a uniform coin flip per row (as in the show), so the *first* contestants are gambling by design; the order
  is the only luck-management there is.
* Lane naming left / right is relative to the direction of travel (see above), not the map view of the arena document.
* The panel judgement needs the contestant to be `onGround`; a hop that lands and takes off in the same tick is still
  judged (landing is detected before the next take-off), but a client that lies about `onGround` could in theory
  skip a panel: the fall check below the deck is the safety net.
* The overlay and the HUD are built carefully but were only checked on a headless client (see the verification notes).

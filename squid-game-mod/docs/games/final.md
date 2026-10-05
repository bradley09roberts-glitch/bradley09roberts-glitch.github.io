# Game 6 - The Final Squid Game

Two finalists, one court shaped like a squid, one coin toss. The **attacker** starts in the square and must reach the golden
circle in the head and hold it; the **defender** starts in the triangle and must stop them. Whoever has no health left, or
touches the white line, loses. With more than two survivors the finalists fight a **knockout ladder** of duels on the same
court.

Game id `final` (`GameKind.FINAL`, min 2 participants), arena `ArenaId.FINAL`, elimination causes `KNOCKED_OUT`,
`OUT_OF_BOUNDS`, `TIMEOUT` and `LOST_MATCH` (a lost capture race) - plus `DISCONNECTED` for a withdrawal.
Test commands: `squid debug play final 2` (NPC-only duel), `squid debug play final 6` (a ladder), `squid debug play final 1`
from a client (you against one NPC).

## 1. Where the code lives

| Package | What |
|---|---|
| `core/finale/` (pure Java, unit tested) | `FinaleRules` (every number), `Duel` (the rule engine, one `step()` per server tick), `CourtGeometry` / `SquidShape` (the court from the markers), `NpcBrain` (the NPC's mind), `Motion` (movement shared by the headless simulation and the live NPC bodies), `DuelSimulator` (a whole duel without a world, records events and the poses of both fighters), `Replay` (the time warp of a ceremony), `Ladder` (knockout bracket), `InputGate` (validation and rate limit of client messages) |
| `game/finale/` | `FinalSquidGame` (stages, ladder, ceremonies, results), `FightSession` (one live duel: bodies, inputs, effects, state for the clients), `FinalNpcBehavior`, `NpcBody` / `PlayerBody` (the two kinds of fighter body), `FightEffects` (sounds, particles, animations, camera tilt), `FinaleArena` (the markers), `FightStatePayload` + `FinaleNet` (the S2C overlay state) |
| `client/game/finale/` | `FinaleClient` (`register()`, found by `ScreenRegistry`), `FinaleInput` (controls), `FinaleOverlay` (bars, banner, coin toss, warnings, hit feedback), `FightClientState` |
| `build/placeholder/FinalPlaceholder` | fixture arena (version 0: the real `build/arena/FinalBuilder` replaces it) |
| `tools/lang/final.json` | all texts (`squidgame.game.final.*`, `key.squidgame.dash`, `key.squidgame.shove`, `key.categories.squidgame`) |

The server decides everything. The client only sends buttons (`ClientActionPayload`, ids `finale.atk`, `finale.guard`,
`finale.shove`, `finale.dodge`, data `d` = down/up, `k` = dodge direction) and draws the state it is sent. NPCs press exactly the
same buttons through the same `Duel` API.

## 2. The rules

All times are server ticks (20 per second), distances are blocks.

### Win and lose
* **Health 0**: knocked out (`KNOCKED_OUT`). Both in the same tick: the one with more health wins, then the one deeper inside
  the court, then luck.
* **The line**: a fighter whose centre is within 0.2 blocks of the outer edge of the painted line, or beyond it, loses
  (`OUT_OF_BOUNDS`). Knock-back and shoves are the way to do that to somebody; a dodge never carries you over it (it stops
  short), neither does your own walking in a fight you could still lose by it - the line is dangerous, so watch it.
* **The circle** (attacker only): stand within 3 blocks (the painted golden ring, 60 % of the circle's radius) of the head's
  centre for **40 ticks (2 s)** in a row. Leaving the ring resets the count. The loser of a lost capture race is eliminated
  with `LOST_MATCH`.
* **Time**: when the clock runs out the defender wins (`TIMEOUT`).

### Difficulty table (`FinaleRules.params`)
| | Normal | Hard | Extreme |
|---|---|---|---|
| time limit of a duel | 180 s | 150 s | 120 s |
| stamina pool | 100 | 88 | 75 |
| stamina regeneration (per tick, after a pause of ...) | 0.50 (24) | 0.44 (28) | 0.38 (32) |
| damage per hit | x 1.00 | x 1.12 | x 1.25 |
| dodge invulnerability | 6 ticks | 5 | 4 |
| parry window | 4 ticks | 3 | 2 |
| NPC skill (`Difficulty.npcSkillBonus`) | +0 | +0.12 | +0.25 |

### Actions
Health is 100 for everybody. Every action has a wind-up (ticks from the press to the impact), a recovery (ticks in which
nothing else can be started) and a stamina cost.

| Action | Control | Wind-up / recovery (ticks) | Stamina | Damage | Knock-back (blocks travelled) | Reach / cone |
|---|---|---|---|---|---|---|
| **Light strike** | tap left mouse button | 4 / 8 | 8 | 7 | 0.7 | 2.6 / 100 deg |
| **Heavy strike** | hold left button >= 10 ticks (sparks show the charge from tick 6), release; full charge at 20, released by itself at 26 | 5 / 14 | 20 (+0.2 per tick held) | 20-28 | 1.4-2.2 | 2.8 / 100 deg |
| **Guard** | hold right button | 3 ticks to raise, front +-75 deg | none, drains stamina per absorbed hit | -70 % damage, knock-back x 0.45 | | |
| **Dodge** | Dash key (default **V**) or double-tap A / D / S | i-frames 6 / 5 / 4 ticks, dash 6 ticks, cooldown 24 | 16 (6 back on a successful dodge) | | about 2.9 blocks in the pressed direction (backwards without keys) | |
| **Shove** | Shove key (default **R**) | 6 / 12 | 12 | 2 | 2.3 (65 % through a guard) | 2.1 / 84 deg |

How they interact (the whole combat triangle):
* A **guard** beats light strikes: 70 % of the damage is absorbed, the blocker pays stamina (9 per light strike, 30+ per heavy
  strike). A guard raised in the last 4 / 3 / 2 ticks before the impact is a **parry**: no damage, the attacker is staggered
  for 11 ticks and the parrier regains stamina.
* A **heavy strike** or a **shove** beats a guard: a heavy strike against a guard without stamina breaks it (guard break,
  18 ticks of stagger), a shove goes through the guard entirely and is how a turtle is moved towards the line.
* A **dodge** at the right moment beats everything the opponent has telegraphed: the strike lands in the i-frames and misses.
  Too early and the dodge is over before the heavy strike lands; the dodger cannot dodge again for 24 ticks.
* A **light strike interrupts a wind-up** (counter hit: x 1.25 damage, longer stagger): whoever strikes first wins a trade of
  lights, and whoever charges a heavy strike in range of a light strike gets hit.
* Every hit staggers (7 ticks for a light, 14 for a heavy strike) and pushes. A fighter who just recovered cannot be staggered
  again for 8 ticks (damage still applies) and a combo shortens the stagger: no stun locks.
* Simultaneous impacts both land.
* **Stamina**: every action costs; sprinting drains 0.3 per tick, charging 0.2 per tick; regeneration starts after a pause and is
  slower while acting or while the guard is up. At 0 the fighter is **exhausted**: no action (and no sprint) until 22 stamina
  are back - "denied" presses are shown on the bar. Stamina is the pacing of the whole fight: nobody can keep hitting.
* Inputs are buffered for 5 ticks, a press that arrives while the fighter is busy is not lost.

### Movement while fighting
Walking is vanilla (4.3 blocks/s, sprint 5.6). The jump is disabled for the fighters. What a fighter is doing slows it
(`Duel.speedFactor`): a raised guard to 55 %, a charge to 55 %, a light strike to 75 %, a heavy strike to 35 %, a shove to 50 %,
a stagger to 10 % (a broken guard to 0); an exhausted fighter loses another quarter. Players are slowed by a transient
movement-speed modifier that is never saved, NPC bodies use the same factor. Knock-back and the dash are velocity impulses with the
vanilla ground friction, so they feel like vanilla knock-back; the court is flat.

## 3. The court and the arena contract
The court is read from the markers of `docs/ARENA_MARKERS.md`: `final.boundary` (>= 16 vertices, clockwise, `i=` index) is the
polygon of the outer outline of the painted squid, `final.circle` (`r=`) the head, `final.neck` (`w=`), `final.attacker_spawn`,
`final.defender_spawn`, `final.audience` (>= 40 `slot=` spots for the NPCs that are not fighting), optional `final.podium`; regions
`final.court`, `final.attack_zone`, `final.defence_zone`. Nothing about the shape is hard-coded in the game. Without a usable
polygon the box of `final.court` is used; without the three required markers every duel is settled off the court.
`SquidShape` is the reference outline (the one of the real arena builder) for the tests and the fixture arena.

## 4. The flow of the game

```
prepare -> placeContestants -> (countdown) -> begin -> tick* -> isFinished -> conclude -> cleanup
```
* **Draw**: a `Ladder` of the survivors is drawn at random in `prepare`.
* **First duel**: its pairing is made when the tournament places the contestants (start of its countdown). The two fighters are put
  on their spawns, everybody else on the gallery (NPCs) or on the belt beside the court (humans, see below). The tournament's countdown does not tick the game, so the coin toss (an
  animated coin between the two numbers that lands on the attacker's, with a title for the fighters) is driven from scheduled
  callbacks until "GO".
* **Next duels**: a fade to black, the fighters are placed, 7.5 s of intro (coin, "READY", "FIGHT!"), the fight, a result title
  ("No. X wins", why) and 3.5 s of outro (the winner cheers, the loser has collapsed), then the next pairing.
* **Result of a duel**: the loser is eliminated with the cause of the ending, the winner advances. The body of the loser is
  taken away (with a puff) when the next fighters are placed.
* **End**: when the ladder has no pairing left. `conclude` puts the champion on the podium (`final.podium`) with fanfare,
  cheering finalists and fireworks and returns the survivors (one) and the eliminated; the headline names the winner.
* **Timeout of the whole game** (`Ladder.budgetTicks`, and `/squid skip`): the duel in progress is ended as a timeout, every
  remaining pairing is settled by the simulator on the spot, and exactly one survivor is left.

### More than two survivors: the knockout ladder
The planner may send any number N >= 2 of survivors (after the glass bridge: all that crossed). The rule:
* The finalists are drawn at random. Every round pairs them up; **an odd one out advances without a duel**, and nobody gets a
  second bye while somebody else has had none.
* The **loser of every duel is eliminated**, the winners go into the next round, the last duel is the final. N finalists need
  exactly N - 1 duels. The winner of the final is the only survivor.
* Roles are decided by a coin toss in every duel.
* A duel is **live** (really fought, with bars and inputs) when a human is one of the two, or when at most four finalists are
  left (the semi-finals and the final are always worth watching). Every other duel is **settled by the headless
  `DuelSimulator`** (the same rules, the same NPC minds) and shown as a **ceremony**: coin toss, then a replay of the simulated duel
  in about 5 s - both bodies take the poses the simulation recorded, with a time warp (`Replay`) that skips the walking and
  circling and shows every blow, block and dodge at about natural speed - the result, a short aftermath.
* Contestants who wait for their turn watch the duel. **NPCs** sit on the gallery (`final.audience`). **Humans** stand on the clear
  belt beside the court (two columns on either side, at least 2.4 blocks outside the painted line, looking at the middle of the
  court): the tournament eliminates a human who stays more than 3 blocks outside `arena.bounds` for about eight seconds, and the
  gallery of the real arena lies outside those bounds. A player who comes back from a disconnect while waiting is moved to the
  belt too. A contestant who is eliminated or disconnects
  while waiting simply leaves the ladder, one who leaves during a duel forfeits it (the opponent wins, cause `DISCONNECTED`
  for the leaver).
* The time budget of the game is `Ladder.budgetTicks`: every duel that is probably live gets intro + time limit + outro, the
  others a ceremony; it is generous (real duels take a fraction of their limit).

## 5. Controls and the client

| Input | Effect |
|---|---|
| left mouse button, tap | light strike |
| left mouse button, hold, release | heavy strike (the longer the hold, up to 20 ticks, the harder) |
| right mouse button, hold | guard |
| **V** (`key.squidgame.dash`) or a double tap of A / D / S | dodge in the direction of the movement keys held (backwards without) |
| **R** (`key.squidgame.shove`) | shove |

(Why R and not F: Minecraft allows one binding per key in its lookup and F is the swap-hands key.) The keys can be changed in the
Controls screen under "Squid Game"; the instructions in chat show the current ones.

* While a client is one of the two fighters of a live duel, Fabric's `ClientPreAttackCallback` cancels the vanilla attack (it
  fires every tick the button is down, with or without a target, so **strikes in the air work**) and `UseEntityCallback`
  stops entity interaction; the button states are sent instead. Held buttons send a heartbeat every 8 ticks and the server
  releases a button that has been quiet for 26 ticks, so a lost "up" message cannot leave a guard or a charge on.
* The server validates every message (`InputGate`): only the four ids, legal values (a dodge sector is 0..7), a token bucket of
  30 messages per second (burst 40), only while the sender is a living fighter of the duel in progress and a human is driving
  the body. A vanilla left click on an entity that reaches the server is treated as a light strike.
* **Overlay** (`FinaleOverlay`, fed by `FightStatePayload` - a custom codec of a few dozen bytes every tick to the fighters, every
  other tick to everybody else): both fighters' name tag, role, health and stamina bars (stamina flashes red when exhausted or
  when a press was denied), small flags (GUARD, DODGE, CHARGING, STAGGERED, EXHAUSTED), the role banner, the capture bar,
  a red vignette and "THE LINE!" when your edge distance is below 3 blocks, "HEAVY STRIKE - block or dodge!" while the opponent
  charges, a hit marker around the crosshair, floating words (damage numbers, BLOCK, PARRY!, GUARD BREAK, DODGE / MISS) and the
  coin toss before the duel. Hits also tilt the camera (the vanilla hurt animation) and tint the screen red edge briefly
  (`DangerPayload`).
* Presentation of a hit, never gore: impact sounds by weight (a heavy strike thumps and kicks up dust, a shove sends a puff
  of sand), sparks on a block, stars on a parry, a stagger and a "knocked back" animation on NPC bodies, the collapse of the
  loser is the tournament's usual one.

## 6. NPC finalists
`NpcBrain` is a pure policy; every tick it sees the two `FighterView`s (what a human can see: poses, health, stamina bars, act
and wind-up state) and answers with the buttons and the walk direction. Personality (`Personality`) and difficulty shape it:

* **Perception**: it reacts to a *change* of the opponent's state (a wind-up begins, a guard goes up) only after its own
  reaction delay (3-13 ticks from `reactionSpeed`, shortened by difficulty). A light strike (4 ticks) is faster than anybody's
  reaction, a heavy strike's charge (>= 10 ticks) can be answered by fast fighters; a slow one can be baited and counter-struck.
* **Modes** (chosen by weights from the personality): *poke* (a burst of light strikes, ending sometimes in a heavy strike),
  *bait* (hover just outside the opponent's reach until it whiffs, then punish), *guard press* (advance behind the guard, then a
  shove or a heavy strike), *heavy set-up* (close in, charge visibly), *reset* (back off and regain stamina when tired or
  losing, a cautious fighter keeps the guard up while doing so). A stalled fight raises the urgency (more pokes, the attacker
  runs out of patience as the clock runs down).
* **Always**: answers a telegraphed heavy strike or shove with a dodge (chance by skill and nerve) or a guard, pokes a charging
  opponent (the counter hit), shoves whenever that sends the opponent over the line ("lethal" shove), steps in with a strike or a
  shove at an opponent that runs at it, punishes a staggered or broken opponent (a heavy strike after a guard break),
  strafes so that the opponent's nearest edge is behind it, keeps itself away from the line (never walks over it, dashes
  only where there is room).
* **Attacker**: walks the course through the neck, sprints when the lane is open or the clock is short, fights its way past the
  defender or runs by when the defender is out of position, holds the circle once in it.
* **Defender**: waits at a *post* chosen by personality - bold fighters hold **the neck** (a 3 block wide choke point where a
  shove into the wall ends the duel), careful ones wait at the circle's mouth, timid ones inside the golden ring - and follows
  the fight only a limited distance from it; when the attacker gets past it sprints back, when the attacker threatens the
  circle it attacks whatever it costs.
* Measured over many simulated duels (Normal): the attacker wins about 55 %; endings are about 37 % knockouts, 33 % captures,
  30 % pushed over the line, practically no timeouts; the average duel lasts 40 s (7-140 s). Aggressive archetypes win more as
  attacker and finish faster, patient ones defend better, a nervous fighter blocks the most, a calculating one shoves
  most. All of this is asserted by `NpcDuelTest`.

A live NPC fighter is moved by the same `Motion` as in the simulation (written into the entity's velocity every tick; no
pathfinding is used on the open court). Far-away NPCs' reduced entity tick rate never matters: the game thinks for the fighters.

## 7. Disconnects and other edge cases
* A fighter that is eliminated by the tournament (disconnect after the grace period, `/squid debug eliminate`) forfeits: the
  opponent wins at once (`FORFEIT` in the log, `DISCONNECTED` for the leaver). A disconnected player is meanwhile replaced by an
  AI stand-in with the same number that fights on; when they come back they get their body (and the running duel state) back.
* No human in the game: everything runs, ceremonies for most duels, live from four finalists on.
* Both fighters out in the same tick: more health, then deeper inside, then luck.
* The arena without markers: every duel is settled by the simulator and no body is moved (logged as an error).
* `saveState` keeps the number of duels played so that a resumed tournament continues the numbering.

## 8. Tests and verification
* Pure unit tests (`src/test/java/com/squidgame/core/finale`, ~110 tests): every outcome of an exchange (timings, guard, parry,
  guard break, dodge window, shove, counter hit, simultaneous hits, stamina and exhaustion, stagger grace), the end conditions,
  determinism, geometry (signed edge distance, ray exit, capture ring), the shape, the ladder (N - 1 duels, byes, withdrawals),
  the input validation and rate limit, the difficulty table, the replay time warp, and NPC sanity (attacker win rate band on every
  difficulty, every ending occurs, no timeouts, duel length, the whole toolbox is used, nobody walks over the line alone,
  skill and personality show, the same results at x = 6000 as at 0).
* `FinaleArenaTest` builds the real arena builder and the fixture, reads them exactly like the game, checks the markers, the
  flatness and emptiness of the court and the painted lines and plays duels on it.
* In-world (dev server with the real arena): 2 NPCs and ladders of 3, 6, 9, 12 on all difficulties, `/squid skip` during a duel,
  a ceremony and a live duel (the game timeout path), admin elimination of a waiting and of a fighting contestant, and a
  headless client as a human (see the report of the implementation for what was and was not exercised).

## 9. Known limitations
* A duel between two NPCs that a human watches live is not paced for television: it plays at the speed of the rules.
* The ceremony replay moves NPC bodies by teleporting them along the recorded poses (clients interpolate); on a server with
  a very low tick rate it will look jerky.
* Controller input is mouse and keyboard only; touch or unusual keyboards may lack the double-tap dodge, the Dash key remains.
* Players with latency above ~150 ms will find parries and dodges (windows of 2-6 ticks) hard on Hard / Extreme; the windows
  are centred on the server's view of the press and are not lag compensated.

## 10. Proposed changes to shared code (not applied: the final needs none of them)
* **`Planner`**: no change is required. `FINAL` after the glass bridge with any number N >= 2 of survivors is already planned
  (`PlannerTest.finalIsPlayedWithSeveralSurvivorsAfterTheBridge`), `GameKind.FINAL.minParticipants` stays 2, and
  `FinalSquidGame` always ends with exactly one survivor (the ladder, and `settleAll` for a game that runs out of time), so
  `Planner.next(FINAL, ...)` returning `null` always ends the tournament with one winner. One optional tweak, a design choice for
  the integrator: let three survivors go straight to the final instead of playing an odd-sized round of the remaining games -
  `if (survivors == 2 && lastPlayed != null)` becomes `if (survivors <= 3 && lastPlayed != null)` (three finalists are two
  live duels: a bye, a duel, the final).
* **`TournamentManager` / `MiniGame`**: nothing is required. Two optional conveniences found while building this game:
  1. `tickCountdown` ticks no game code, so the coin toss of the first duel runs from `GameContext.schedule` callbacks. A default
     hook `default void tickCountdown(GameContext ctx) {}` in `MiniGame`, called from `TournamentManager.tickCountdown()` every
     tick, would make that a plain method.
  2. `TournamentManager.enforceBounds` eliminates a human who stays more than 3 blocks outside `arena.bounds`, and the real final
     arena's gallery (`final.audience`, `arena.exit`) lies outside its `arena.bounds` (x up to 90 against 50). The game works
     around it by seating humans on the clear belt beside the court; if the arena's bounds region were enlarged to cover the
     grandstand (`Guards.java` in `build/arena/finale`), humans could use the gallery like the NPCs.
* **Pitfall for every game**: `GameContext.eliminate(c, cause, delayTicks)` with a delay defers the elimination through the
  scheduler; the tournament reads the survivors right after `conclude()` returns, so a game must eliminate immediately from
  `conclude()`/`onTimeout()` (the final does).

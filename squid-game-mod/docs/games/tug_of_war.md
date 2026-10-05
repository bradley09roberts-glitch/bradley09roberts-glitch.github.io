# Game 3 - Tug of War

Package names: `core/tug/` (`TugRules`, `TugSim`, `TeamPlanner`, `TugNpcPolicy`, `BeatClock`, `TugInputGate`, `TugMatch`),
`game/tug/` (`TugOfWarGame`, `TugNpcBehavior`, `TugPlatform`, `TugHeat`, `TugNet`), `client/game/tug/` (`TugClient`, `RopeRenderer`,
`TugOverlay`, `TugInput`, `TugClientState`), arena `build/arena/TugOfWarBuilder` (`ArenaId.TUG_OF_WAR`), fixture
`build/placeholder/TugOfWarPlaceholder` (only used when the real builder is missing, `version() == 0`), entity `RopeEntity`.
Game id `tug_of_war` (`GameKind.TUG_OF_WAR`, min 4 participants). Test it alone with `/squid debug play tug_of_war 16`
(`4` = 2 v 2, `70` = two heats); as a player: `/execute as <name> run squid debug play tug_of_war 15`.

## Rules (as shown to the players in the instructions phase)
* Two teams stand single file on two platforms and pull a rope across a deep pit. The team whose end of the rope is pulled
  over its edge falls into the pit and is eliminated (`EliminationCause.LOST_TEAM`, or `FELL` for a contestant who walks or is
  pushed off the platform earlier); the winning team survives.
* **Pull** (hold `R`): pulls with full strength and drains stamina (empty after about 22 s on Normal); release it to recover.
* **Brace** (hold `Left Shift`): leaning back anchors the team against being dragged and costs only 30 % of the stamina of
  pulling; an exhausted contestant (stamina 0) can only brace until the stamina is back above 35 %.
* **Heave** (tap `F` on the beat): the whole heat shares one rhythm (a beat every 1.2 - 1.6 s, shown as a ring that closes
  onto the crosshair and heard as a tick). A tap inside the window around the beat is a burst of force, the closer the bigger;
  the more of the team heaves together the bigger every burst (up to x1.8). A mistimed tap only costs stamina.
* Four beats count in the start of every heat ("3, 2, 1" ring), then **PULL!**.
* The rope moves between -1 (team A's edge, RED) and +1 (team B's edge, BLUE); reaching a team's own edge wins at once. When the
  time is up (Normal 100 s, Hard 85 s, Extreme 70 s) the rope's side wins; an exact tie (|offset| < 0.02) is a 10 s sudden death
  with refilled stamina, then a coin flip.
* With more than 64 contestants alive the field plays `ceil(n / 64)` heats one after the other (humans are dealt round robin
  over the heats); whoever is not in the current heat waits in the gallery and sees the rope bar and a dim beat ring.

The key names in the instructions are the player's real bindings (`Component.keybind`); the bindings live in the vanilla
controls screen under "Squid Game". The defaults share keys with vanilla (`F` is "swap hands", `Left Shift` is "sneak"), and
Minecraft hands a key to only one of the bindings on it. `TugInput` therefore treats a binding as held when any binding on the same
key is held, and during a match counts a tap that went to another binding on the heave key as a heave (the swap of the hands does not
happen), so the defaults work as they are; rebind them in the controls screen if you prefer.

## Flow of a game (`TugOfWarGame`, one `TugHeat` at a time)
`prepare` (repairs a deck left broken by a restart, reads the markers, creates the two `TugPlatform`s and the `RopeEntity`,
spawns guards) - `placeContestants` (`TeamPlanner`, teams into their `tug.waiting_*` lobbies, the rest into `tug.spare`) - `begin`
(first heat) and then per heat: `WALK_IN` (announcement, teams march onto `tug.slot_*`: NPCs walk, front of the line first with a 3 tick
stagger; humans are faded and teleported to their slot; invisible walls fence the tips) - `COUNT_IN` (4 beats) - `MATCH` -
`FALL` (the losing side is dragged over the edge, the winners cheer) - `AFTERMATH` (70 ticks, deck repaired) - `BETWEEN` (5 s) -
next heat, or done. `timeLimitTicks` is the sum of the stage budgets of all heats, so the tournament's game clock never cuts
a healthy game; if it does (`onTimeout`/`conclude`), the running heat is decided by the rope and never-played heats are decided
by a simulation of their NPC policies (humans in them play as NPCs).

### Teams (`TeamPlanner`, pure)
* Sizes differ by at most one; the summed rating (strength + stamina + cooperation) is balanced greedily and improved by swaps of
  NPCs; humans are spread alternately over both teams and never swapped.
* The smaller team of an odd heat gets a force multiplier `1 + 0.7 (big/small - 1)` and the balance counts it (the smaller team
  does not also get the stronger members).
* Slot order: humans at the front (best view), then NPCs from the weakest to the strongest - the strongest is the anchor.
* At most 32 per team (the number of `tug.slot_*` markers).

## The simulation (`TugSim`, pure, deterministic, unit tested)
```
member pull   = strength * effort * staminaFactor            (staminaFactor 1.0 down to half stamina, 0.35 at zero)
member anchor = strength * (0.55 + 0.45 * staminaFactor)     (only while bracing; exhausted members can still brace)
heave burst   = strength * quality * 9 * envelope(age)       (quality 1.0 on the beat .. 0.2 at the edge of the window)
team force    = (sum pull + bursts * (1 + 0.8 * share of the team in a burst)) * handicap
raw           = (force B - force A) / unit - 0.07 * offset
net           = only the part of |raw| above 0.45 * anchor of the team being dragged
velocity'     = velocity + (net / weight - drag * velocity) / 3000;   offset' = offset + velocity
```
`unit` is the average team size (the same numbers work from 2 v 2 to 32 v 32), `envelope` a short rise, a kick of 2.2 and an
exponential decay over 18 ticks. `weight` is a rope inertia that is 1 from 8 members per team up and grows for small teams
(2.4 for 2 v 2, 1.5 for 4 v 4): a handful of members decide the rope with single heaves, and without it a 2 v 2 was over after one
clean heave. A member stands in one of four stances (`REST/PULL/BRACE/SPENT`); stamina drain is `0.0023` per tick of pulling,
`0.30 x` that while bracing, a heave costs `0.04`, resting recovers `1/400` per tick (all x `costScale / endurance`).
A heave is judged on the server tick it was pressed (`pressTick = arrival - round trip`), minus a forgiveness (humans: 1 tick),
against the window; a heave within 8 ticks of the previous one is ignored for free, a second heave for the same beat costs stamina and gives nothing.

Constants were tuned with Monte Carlo heats (`TugMatchTest`, harness in the session notes): equal NPC teams are fair (35-65 %),
decided by the rope in most heats with several lead changes, a heat takes about 30-60 s on Normal; a team that burns its stamina
early loses late; a human who heaves on the beat is worth about two extra average members, a novice who only holds the pull key
is a liability to his team (it wins well under half of its heats), and a button masher is worse than a novice.

### Difficulty (`TugRules.params`)
| | Normal | Hard | Extreme |
|---|---|---|---|
| heat time limit | 100 s | 85 s | 70 s |
| half width of the heave window | 3.0 ticks | 2.4 | 1.8 |
| stamina costs (`1 / resourceScale`) | x1.0 | x1.33 | x2.0 |
| NPC skill bonus (only for the NPC team that opposes a team with humans) | 0 | +0.12 | +0.25 |
| rope drag (tired teams pull a weaker rope, so the drag follows the costs) | 50 | 35 | 22 |
The skill bonus sharpens the opposing NPCs' timing in full and their strength by 40 % of it.

## NPC contestants (`TugNpcBehavior` + `TugNpcPolicy`)
The policy sees only what a human in the line sees: the rope's lead and how fast it moves, its own stamina, how many team mates and
opponents visibly strain (the poses show it), the shared beat and the public clock. It answers with the two inputs of a human (how
to pull, when to heave):
* **patience** sets the reserve of stamina, the length of pulling stints and how readily it braces or rests while the rope is stable;
* **aggression / risk tolerance** set the effort and the urge to surge early; **courage** decides what happens when losing: brave
  NPCs go all out, nervous ones panic (erratic effort, sloppy timing);
* **cooperation** is how often it heaves with the beat, **reaction speed** and **skill** how precisely (Gaussian jitter around the beat,
  1.3 - 4 ticks); they aim at the beat itself, not at the window.
Visible behaviour: they march onto the platform one after the other, line up (`ATTENTION`), take hold of the rope on the last two
beats of the count-in (`PULL_IDLE`), pull in the braced stance (`PULL_IDLE`) and the heavy straining stance (`PULL_STRAIN`) when low
on stamina, throw a heave stroke (`pull_heave`) on every heave, slip (`pull_slip`) when they run out of strength or when the rope
suddenly goes the wrong way, and rally (a heave stroke) when it turns for them; the winners cheer (`CELEBRATE`/`CELEBRATE_FIST`),
the losers stumble (`stumble`). O(1) work per NPC per AI step; an AI stand-in for a human who disconnected (`onControllerChanged`) carries on
with the same behaviour from where the human left off.

## The fall (`TugPlatform`)
When a team loses: title "X WINS / Y goes over the edge!", chat line, sounds, the flag flashes, the winners cheer and their
fence is removed. The losers (NPCs and humans alike) get a velocity towards the gap that grows from 2 to 10 blocks/s, and from
tick 16 on the walkway strip under them (5 wide, the three layers under the standing level) collapses column by column from the
tip back to the last member with block particles and the sound of the material; whoever is more than 5 blocks below the deck is
eliminated (`LOST_TEAM`, with a poof), at the latest after 9 s. The deck is repaired in the aftermath, between heats and in
`cleanup`. The intact strip (the first 36 columns of both decks, about 840 blocks) is remembered in `prepare` and written to the
game state (`saveState`), so a server stop at any moment of a collapse is repaired by the next `prepare` (the resumed game),
however much of the deck had been taken when the state was last saved.

## Networking and validation (`TugNet`, `TugInput`)
* S2C `tug_state` (`StatePayload`): every 2 ticks to the humans in the heat, every 4 ticks to everybody else: stage, role, server
  tick, beat epoch/period, window, rope offset/velocity/strain, own stamina/stance/exhaustion, team stamina, sync, members left,
  time left, danger and the verdict of the last heave (sequence number, result, error, quality).
* C2S through the existing `ClientActionPayload`: `tug.input` (byte tags `p` = pull, `b` = brace; sent on every change and refreshed every
  10 ticks, a held key that was not refreshed for 60 ticks counts as released) and `tug.heave` (a tap, polled every rendered frame).
* The server drops silently: anything outside the running `MATCH` stage, from anybody who is not an alive, human-controlled member of
  the heat, over 40 messages per second per player, heaves closer than 4 ticks, malformed tags. Nothing the client says can change
  stamina, strength or the rope directly; the client only says what the player does. The beat is public (clients draw it), the
  rest of the simulation is server only.
* HUD widgets of the tournament HUD: the teams line, the heat counter, the own team's stamina, the heat clock and a danger / sudden
  death banner. The overlay draws the rest from the snapshots (below).

## Client (`client/game/tug`)
* `RopeRenderer`: a four-sided textured tube from the rear-most hand of team A to the rear-most hand of team B at hand height; it sags
  in the gap (less the tauter it is), hums with the strain, bounces on every synchronized burst, its texture slides with the rope's
  offset, and a red flag with two ribbons rides on it; when a team loses the losing end drops into the pit.
* `TugOverlay`: the rope bar under the objective panel (team colours, the flag, chevrons for the rope's velocity, a thin strip of
  each team's stamina, "YOU" on your side), the beat ring around the crosshair (a ring that closes onto the target, the hit window as
  a band, a flash on the beat, the count-in number, popups PERFECT / GOOD / OK / TOO EARLY / TOO LATE / NO STAMINA), and a panel with
  your stamina (flashing when exhausted), the team's sync and the three keys as bound, lit while held (shown from the count-in to
  the end of the match). Spectators and waiting contestants get the rope bar and a dim ring.
* Beat alignment: the client estimates the server tick from the snapshots (minimum offset); the server subtracts the round trip, so
  the ring and the judgement agree on any connection.

## Arena contract and fixture
Markers/regions (docs/ARENA_MARKERS.md): `tug.slot_a/b` (data `slot=k`, nearest the gap first, yaw towards the gap), `tug.rope_a/b` and
`tug.rope_center` (y 42.2), `tug.waiting_a/b`, `tug.spare`, `tug.pit_floor`, regions `tug.edge_a/b` (their X extent gives the tip column
of the walkway) and `tug.pit`. The game derives every coordinate from them. `TugOfWarPlaceholder` is a small hall with the real
geometry (decks at y 40 along X over a pit, 14 block gap, 48 slots a side, spare gallery, shared waiting room); the real
`TugOfWarBuilder` always wins when present.

## Edge cases handled
Odd and tiny fields (2 v 2 up to 64), 0 humans, humans in either team, a human who disconnects mid-heat (stand-in), reconnects,
or is eliminated by the tournament (the sim drops them, a team without members forfeits), two eliminations in one tick, a team
without anybody left before the heat starts (the other side wins without a match), humans who wander off their slot (put back, with a
hint), a platform left broken by a restart (repaired), `cleanup` idempotent (decks restored, rope and fences removed, nothing left
scheduled), heats that were never played when the clock runs out.

## Tests
`core/tug` (JUnit): planner (balance, humans, odd heats, slot order, determinism), sim (win conditions, stamina, sync, anchors,
sudden death, forfeit, determinism), rules (difficulty monotonicity, envelopes, quality curve), beat clock and input gate, NPC policy
(pacing, panic, timing, O(1)), and Monte Carlo heats (`TugMatchTest`: fairness, decisiveness, pacing, value of a human, small and
huge teams, difficulty).

## Verification (dev server, headless client, merged branch)
* `./gradlew build` with all tests is green (planner, sim, rules, beat clock and gate, NPC policy, Monte Carlo balance, fixture arena).
* NPC-only runs with `tick rate 100`: 2 v 2 (Normal, Hard), 8 v 8 (Normal, Hard, Extreme), 70 contestants in two heats (Normal and
  Hard, 17 v 18 and 18 v 17), 128 contestants in two 32 v 32 heats at the normal tick rate: no exceptions, every heat decided over
  the edge, deck repaired between heats; `/squid debug perf` with 128 NPCs: game logic 0.18 ms, behaviours 0.17 ms, NPC entity
  ticks 2.6 ms, whole server tick 4.9 ms (budget 50 ms).
* Edge cases in the world: contestants eliminated by the operator during the walk-in, the count-in and the match (the heat is
  decided without them); the server stopped in the middle of a 32 v 32 collapse and started again (the resumed game repaired 840
  blocks); a human kicked in the middle of a match (the AI stand-in keeps heaving: 3 of 3 hits) and joined again.
* Human path with the headless client (7 frames per second under software rendering): instructions with the real key names, count-in
  ring, pulling (R: stamina drains, the key lights), tapping F on the beat (the log counts the human's heaves: 4 of 4 and 11 of 16
  judged as hits; the swap of the hands that F is bound to in vanilla is suppressed), bracing on Left Shift (lit, the sneak
  binding does not hide it), exhaustion (EXHAUSTED, NO STAMINA), the danger banner, the title and the winners' and losers' views
  of the fall, and a spectator camera (NPC lines leaning into the rope, strain and flag, the collapse of the losing deck).

## Limitations / ideas
* The slot count limits a team to 32 (heats beyond 64 contestants); a deck is a 5 wide strip, so a human cannot be pulled around
  other humans.
* Humans wear the vanilla pose: only NPCs (and AI stand-ins) show the pull animations; the heave/strain of a human is visible as
  the rope's behaviour only.
* The scoring rewards the team, not the individual: a human on a bad team has little to do but pace and time well.
* Heaves are polled per rendered frame: at very low frame rates the timing is quantized by the frame time (the window is +-3 ticks
  on Normal, +1 tick forgiveness).
* A 2 v 2 with a human who hits every beat is decided in seconds (each heave is half of the team); the rope inertia for small teams
  keeps NPC-only 2 v 2 heats at about half a minute but cannot hide a skill gap that large.
* Not observed in the world: a human who rejoins while his heat is still running (the stand-in and the reconnect each work; the
  heat ended before the client was back), and more than one human in one heat.

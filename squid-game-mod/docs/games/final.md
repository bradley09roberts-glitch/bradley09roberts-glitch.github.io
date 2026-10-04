# Game 6 - The Final Squid Game - design spec

Package names: `core/finale/`, `game/finale/` (`FinalSquidGame`, `FinalNpcBehavior`, `FinaleNet`), `client/game/finale/`
(`FinaleClient`, HUD overlay), fixture `build/placeholder/FinalPlaceholder`. Game id `final` (`GameKind.FINAL`, min 2), arena
`ArenaId.FINAL` (sand playground with the white painted squid: markers `final.boundary` polygon, `final.circle` (`r=`),
`final.triangle`, `final.neck` (`w=`), `final.attacker_spawn`, `final.defender_spawn`, `final.audience`, regions `final.court`,
`final.attack_zone`, `final.defence_zone`; `docs/ARENA_MARKERS.md`). Causes `KNOCKED_OUT`, `OUT_OF_BOUNDS`, `TIMEOUT`.
Port range 25650/25651. Test: `squid debug play final 2` and `... 6`.

## The game
Two finalists fight on the squid-shaped court (square tail - triangle body - circle head, joined by a narrow neck). One is the
**attacker** (starts in the square) and must reach and stand inside the **circle** (hold it ~1 s); the other is the **defender**
(starts in the triangle) and must stop them. Both must stay inside the painted lines (touching/leaving the boundary polygon =
`OUT_OF_BOUNDS`, you lose). A finalist whose health reaches 0 is knocked out (`KNOCKED_OUT`). The attacker wins by taking the
circle or by knocking the defender out; the defender wins by knocking the attacker out, pushing them out of the court, or by
**surviving until time runs out** (~180/150/120 s). Roles are drawn by a coin toss shown on screen (both are told the stakes).
The winner takes the prize.

Non-lethal presentation (this is a game, not gore): hits stagger, flash and make impact sounds, a defeated finalist collapses
(`Activity.KNOCKED_DOWN`, `ELIMINATED_*`) and is carried off; no blood.

### Combat model (`core/finale`, pure and tested)
`CombatSim` with fighter state: health (100), stamina (100), guard state, attack cooldowns/wind-up, dodge window,
knockback, position/velocity in court coordinates. Actions: **light strike**, **heavy strike** (wind-up, more damage and
knockback, costs stamina, can be blocked/dodged), **block** (hold; reduces damage by ~70 %, drains stamina per hit; guard break when
stamina is gone), **dodge/dash** (short invulnerability window, stamina cost), **shove** (low damage, big knockback, the key tool
to push someone out of bounds and to hold the neck), sprint costs stamina; stamina regenerates when not acting.
Hit detection: distance <= reach (~2.6 blocks) within a frontal cone (~100 deg); resolved by the server at a fixed rate.
Everything has a difficulty table (damage, stamina, NPC reaction time/skill bonus via `Difficulty`) and is unit tested
(determinism, block/dodge/guard-break outcomes, knockback into the boundary, timeouts, attacker circle capture timing).
`CourtGeometry` (pure): the boundary polygon from the `final.boundary` markers, point-in-polygon, circle capture, distance to the
boundary (for pushes), clamp helpers.

### Human controls
Left click = light strike (on mouse-down), **hold left click = heavy strike** (release to strike), right click hold = block,
double-tap a movement key or the **Dash** key (default Left Alt/V - choose, show it) = dodge, **Shove** key (default F). Client
sends compact actions via `ClientActionPayload` (validated: rate limits, phase, alive, cooldowns server side). Use the
existing `MiniGame.onPlayerAttack` hook for attacks that hit an entity and, if you need swings in the air, a client event or key
mapping that sends `ClientActionPayload` (check which Fabric API events exist in the 0.116.17 API jars: unzip/inspect them).
Client overlay: own and opponent health + stamina bars, role banner, boundary danger tint when near the lines (`DangerPayload`),
hit flash/shake, damage numbers optional.

### Contestants
* Both finalists humans, human vs NPC, NPC vs NPC (visible, same server-side combat sim; the NPC entity is driven through the
  `NpcBehavior` motor helpers + `triggerAction("...")` one-shots: strike/shove/block/dodge/hit-react animations are in the
  animation list of `docs/ASSET_CONTRACT.md`; `Activity.FIGHT_STANCE`, `BLOCK`, `SPRINT_ATTACK`, `KNOCKED_DOWN`).
* **More than two survivors reach the final** (the planner can send up to N): you must define and implement a clean rule, e.g. a
  knockout ladder of duels on the same court (live when a human is involved; NPC-vs-NPC duels may be resolved by the headless
  sim with a visible ceremony), until two or one remain; document it. Also propose (in your report) any `Planner` change you
  think is needed; do not edit `Planner` yourself.
* `FinalNpcBehavior` (attacker/defender policies): attacker approaches through the neck, uses feints, baits blocks, shoves
  towards the line when it helps, retreats and regains stamina when low (courage), uses the neck choke point; defender holds the
  circle approach, uses shoves to push the attacker out, counter-strikes after blocks, manages stamina; reaction time/accuracy of
  blocks and dodges from `Personality.reactionSpeed`/skill/difficulty; aggression changes tempo; they obey the same limits as
  humans (no perfect information: they react to visible wind-ups with a delay and can be baited).

### World and flow
Guards ring the court (`ctx.spawnGuards()`), floodlights/crowd (`final.audience`) with eliminated/finished contestants
spectating, drum/ambient music from existing sounds, an intro ceremony (coin toss, roles, "ready... fight") and a victory
ceremony handled via the normal `conclude` result; the loser is eliminated with the right cause; the winner remains alive (the
tournament crowns the survivors). Time-limit end: defender wins (announce why). Handle a finalist disconnecting (stand-in takes
over or forfeits after the grace), both knocked down in the same tick (the one with more health wins, then coin flip), and no
human present (spectators get a good camera view: nothing special needed).

# Game 4 - Marbles

Package names: `core/marbles/` (pure rules, unit tested), `game/marbles/` (`MarblesGame`, `PairingManager`, `OddEvenMatch`,
`ThrowMatch`, `MarblesNpcBehavior`, `MarblesNet`), `client/game/marbles/` (`MarblesClient`, `MarblesScreen`, `PairRequestScreen`,
`ThrowOverlay`), arena `build/arena/MarblesBuilder` (`ArenaId.MARBLES`, night village) with the fixture
`build/placeholder/MarblesPlaceholder` (used only when the real builder is excluded, e.g. `-PskipArenas=marbles`).
Game id `marbles` (`GameKind.MARBLES`, elimination cause `EliminationCause.LOST_MATCH`). Test it alone with
`/squid debug play marbles 16 [normal|hard|extreme]` (`/squid debug timescale 0.3` shortens every timer).

## Config: `marblesVariant`
| value | meaning |
|---|---|
| `mixed` (default) | every pair plays odd-or-even or the target throw, picked per pair from the tournament seed |
| `odd_even` (also `oddeven`, `odd-even`, `odd`) | everybody plays odd-or-even |
| `throw` (also `target_throw`, `target-throw`, `target`) | everybody plays the target throw |
Anything else means `mixed`. Set it with `/squid config marblesVariant <value>`; it is read when the game is prepared (the
instruction pages only list the rules of the variants in play) and saved with the tournament so a restarted game keeps it.

## Rules (as implemented)
**Phase 1, pairing (Normal 45 s, Hard 40 s, Extreme 35 s).** Everybody alive stands in the village square. The partner you pick
is the opponent you will have to beat: right-click another contestant (an NPC body or another player) to offer a partnership. An
NPC answers after a think time of 1 to 5 s (reaction speed and patience), a human gets the modal "No. X asks you to be their
partner" (Accept / Decline, 12 s, ignoring it counts as no). Agreements are final. Anti-spam: one outstanding offer per proposer,
one pending offer per target, 1.5 s between offers, a refused offer cannot be repeated to the same person for 12 s (25 s when a
human refused), and two contestants who ask each other at the same time are partners. The phase ends when the clock runs out or
3 s after everybody who can be paired has a partner (not before 6 s). Everybody without a partner is paired at random; with an odd
number one of them gets a **bye** (announced to all, safe, shown on their HUD - never a contestant who found a partner). Pairs
fade out and are moved to their plot (court); the plot order is fixed by the marker numbers, the variant of a pair is fixed by the
setting.

**The match.** Both partners start with the same stack (Normal 10, Hard 8, Extreme 5 marbles). The stack is public: HUD counters, the
marbles in hotbar slot 1 (humans; the tournament restores the real inventory afterwards) and a text hologram over the table
("023 : 7 : 9 : 235"). A match ends when
* a partner has no marble left (`OUT_OF_MARBLES`), or the round budget is used up (more marbles wins, `MORE_MARBLES`);
* the **time call** comes (when only the overtime reserve is left of the game's time limit: Normal 170 s, Hard 146 s, Extreme 122 s
  after the start of the game, pairing included): more marbles wins; with equal stacks the current unfinished round is dropped and
  a tie-break decides (below, `SUDDEN_DEATH`);
* the **hard deadline** (1.5 s before the game's time limit) decides every match still open: more marbles wins, a tie is a coin
  flip announced to both (`COIN_FLIP`);
* a partner leaves the tournament (admin removal, disconnect elimination): the other one wins (`FORFEIT`).
The winner is safe. The loser is announced, then shot by a guard after a short end sequence (3.5 s, `LOST_MATCH`), without
waiting for the other pairs (when the game's time limit cuts an end sequence short the loser is eliminated at once). The game is
over when every match is closed; the contestants who are not eliminated survive (winners, the bye).

**Variant A: odd or even.** The partners play at the same time and neither sees the other's choice before the reveal. The roles
alternate every round (the first holder is random).
* The **holder** puts 1..(own marbles) in the fist.
* The **guesser** wagers 1..min(own marbles, the holder's marbles) - nobody can bet what the other cannot pay - and calls odd or even.
* Reveal: the fist opens. A correct call wins the wager from the holder, a wrong call gives it to the holder.
* Decision timer Normal 25 s, Hard 20 s, Extreme 15 s; a partner who has not locked in when it ends gets a random legal move (holder:
  a random number of marbles, guesser: a wager of 1 and a random call). After the lock-in comes a 1 s pause, then the reveal: the
  fist shakes and opens (the verdict lands after about 2 s) and the result stays on screen for 3 more seconds.
* The revealed rounds (number of marbles, call, wager, who was right) are public history on both screens - the only information
  anybody gets about the other's habits.
* Round budget Normal 12, Hard 10, Extreme 8. Tie-break after the time call: one overtime round with a fixed wager of one marble
  (decision timer Normal 12 s, Hard 10 s, Extreme 8 s).

**Variant B: target throw.** Every round both partners throw one marble at the bullseye of their plot, the first thrower alternates.
The marble that rests closer to the exact centre wins marbles from the other; an exact draw moves nothing.
* The target is painted in the ground, 7 blocks beyond the throw line: a gold centre block ringed by red, white, blue, white and
  red blocks. The ring a marble rests on (the colour of that block) only decides how many marbles the win is worth; the winner is
  always the closer one.

  | ring | block | points |
  |---|---|---|
  | bullseye | gold (centre) | 6 |
  | 4 | red (adjacent) | 5 |
  | 3 | white (diagonal) | 4 |
  | 2 | blue (two away) | 3 |
  | 1 | white (knight move) | 2 |
  | 0 | red (outer corners) | 1 |
  | miss | any other block | 0 |

* Stake of a round = 1 + (points of the winning marble - points of the losing marble) / 2 (rounded down), at most Normal 4, Hard 3,
  Extreme 2 marbles.
* Throw timer Normal 20 s, Hard 16 s, Extreme 12 s per throw (never shorter than the charge time plus 2 s, whatever `timeScale` is).
  A partner who does not throw in time gets a weak random throw.
* Round budget Normal 8, Hard 7, Extreme 6. Tie-break after the time call: a sudden-death round, one throw each (timer 10 / 8 / 6 s),
  the closer marble wins the match, an exact draw repeats the round.
* Physics: the marble flies like a vanilla thrown item (gravity 0.03, drag 0.99) on a fixed 40 degree lob. It lands on the first
  upward face at floor level and ignores everything else - entities, laundry lines, walls - so the landing is exactly the
  predicted point; a marble that has not landed after about 7 s of real time counts as a miss at the thrower's feet.

## Controls
* **Pairing:** right-click the contestant you want as a partner. Answer an offer in the modal: click Accept / Decline, or press
  Enter (or Y) to accept and Esc (or N) to decline. The chat and the action bar tell you who accepted, refused or took somebody else.
* **Odd or even** (panel opens by itself at every decision): `-` / `+` (or the arrow keys) set the number of marbles in the fist or
  the wager, `Odd` / `Even` (or `O` / `E`) call, `Lock in` (or Enter / Space) confirms. The panel shows both stacks, the hand, the
  countdown bar and the last revealed rounds. Esc hides the panel for the rest of the decision, `M` (key binding "Marbles panel")
  brings it back. A locked-in choice cannot be changed; the panel shows whether the opponent has locked in too.
* **Target throw:** the marbles are in hotbar slot 1. On your turn the throw panel appears at the bottom of the screen: "YOUR
  THROW", the seconds left, the distance of the spot you look at, the power bar and the turn timer. Look at the spot where the
  marble should come down (a ring of dust shows it while you charge), hold the use key (right mouse button) to charge, release to
  throw. The gold notch on the bar is the release point that lands exactly on the spot you look at, the green zone is a good
  release (within 1.5 ticks). A release before 4 ticks is cancelled, not a weak throw. Even a perfect release is disturbed by a
  small tremor (see the table). You must stand on your pad: behind the throw line and within 4.5 blocks of it. The centre of the
  screen stays clear during a throw match (no banners), the last-marble warning is a red line in the top panel.
* Stay at your plot: whoever walks more than 3 blocks away from it is put back on the pad after about 4 s.

## Difficulty table
| | Normal | Hard | Extreme |
|---|---|---|---|
| marbles each | 10 | 8 | 5 |
| game time limit | 210 s | 180 s | 150 s |
| pairing phase | 45 s | 40 s | 35 s |
| time call (before the limit) | 40 s | 34 s | 28 s |
| odd/even: rounds / decision / overtime decision | 12 / 25 s / 12 s | 10 / 20 s / 10 s | 8 / 15 s / 8 s |
| throw: rounds / turn / sudden-death turn | 8 / 20 s / 10 s | 7 / 16 s / 8 s | 6 / 12 s / 6 s |
| throw: stake cap | 4 | 3 | 2 |
| throw: full charge (the window to hit) | 36 ticks | 30 ticks | 24 ticks |
| throw: tremor of the release (direction / power) | 1.0 deg / 2.0 % | 1.6 deg / 3.2 % | 2.4 deg / 4.5 % |
| NPC skill bonus | +0.00 | +0.12 | +0.25 |
Times are seconds at normal speed; `timeScale` scales them all (the physical charge ticks are not scaled).

## Server rules and robustness
Everything is decided on the server; a client only sends its own choice. Humans and NPCs use the same `submitHold` /
`submitGuess` / `submitThrow` methods, which check the phase, the role, the bounds (`OddEvenRound` is the single source of truth) and
that a choice is made once per round. Further checks: at most one action per player every 2 ticks; a partnership answer must refer
to an open offer; a throw is only accepted on the thrower's own turn, after at least 4 ticks of charge and from behind the throw
line within 4.5 blocks of the pad. Hidden information never leaves the server: the screen of a partner only gets the public
numbers plus that partner's own locked-in choice, and the NPCs read nothing but the public state.
* A human who disconnects is replaced by an NPC stand-in that plays on from the same ledger; when they come back the screen, the
  throw bar and the hotbar stack are sent again.
* A contestant removed by the tournament (admin, disconnect grace) forfeits: the partner wins the match. Two removals at the same
  moment leave nobody to play, the match is simply closed. A contestant removed while pairing breaks their agreement and cancels
  their offers.
* Every temporary entity is tagged `squidgame_temp` (table text, marbles on the floor) and removed in `cleanup`, which can run any
  number of times. Screens and the throw bar are closed on cleanup.
* 0 humans (NPC-only runs), 2 to 128 contestants, odd counts, a bye that is also the only human, hard deadline and tournament
  time-out all lead to a clean result (`GameResult`: winners and the bye survive, every loser is in the eliminated list).

## NPC contestants
`MarblesNpcBehavior` only uses what a human at the same place could know: positions in the square, who is paired, both stacks,
the revealed rounds of its own match - never the opponent's current fist, wager, call or throw. The pure decision functions
(`OddEvenStrategy`, `ThrowStrategy`, `PairingPlanner`) are unit tested.
* **Pairing:** cooperative types (team players) start asking at once, loners wait or never ask and decline most offers; an NPC walks
  up to the nearest candidate (with some personal taste), waves, waits for the answer with the "think" pose, nods or shakes its head.
  The chance to accept grows with cooperation, with how close the proposer stands and drops while the NPC waits for its own offer.
  Nobody asks someone who just refused.
* **Odd or even:** a patient contestant deliberates up to 40 % of the timer, an impulsive one locks in after about 7 %. The call
  mixes the structure of the game (a holder with one marble can only hide an odd number), the revealed habits of the opponent
  (recency-weighted frequency, streaks, strict alternation - the weight grows with skill and patience), a personal taste and
  noise. The wager turns confidence into a stake through risk tolerance and courage: cautious types bet one or two marbles,
  gamblers go big, especially when they are ahead or desperate. As holder it hides the parity the opponent has called least
  often and drops back to randomness once it was read twice in a row. While holding it shows a fist; after the reveal the winner
  nods or cheers, the loser sobs.
* **Throw:** a short moment to size up the throw, then a visible wind-up (`MARBLE_WINDUP`) as long as the charge it planned, then
  the same projectile code as a human throw. Aim error and release timing error shrink with skill and grow with nerves
  (behind on marbles, down to the last marbles, sudden death, little courage). An expert averages less than a block from the centre,
  a rookie more than a block (unit tested); on Hard and Extreme the skill bonus partly offsets the larger tremor.

## World
Night village: the pair spots are walled yards along the lanes (64 courts, `marbles.pair_a/b/line/target/table` with `k=N`,
`marbles.plot` regions in the same order), the contestants spawn in the square (`marbles.square_spawn`, 186 slots), guards stand on
the rooftops (`guard.post`). When a match ends the winner cheers, the loser sobs, the marbles fly across the table (item particles)
and a guard shoots the loser. Sounds and particles: a select tick when a decision starts, marble clicks at lock-in and reveal,
rolling marbles and a confirm / deny sound at the verdict, an alert chime at the time call, a danger sting when a match is lost, a
red vignette when you are down to two marbles, in overtime and after a big loss. HUD: pairing countdown, pairs formed and partner
while pairing; own / opponent marbles, the round line with the role or whose throw it is, the decision timer (odd or even; the
throw timer is part of the throw panel), a "last marble" warning (a banner in odd or even, a red line in throw matches) and the
number of matches still running. Fixture arena: 64 courts in the real geometry (pads at x = -1 / +2, line z = 2.5, bullseye 7
blocks beyond, 5x5 target) and a plain square, so the game runs unchanged on either arena.

## Testing
* Pure rules: `tools/gradle.sh test` (`core/marbles`: ledger, odd/even rounds and duels, throw scoring, physics and controls, throw
  duels, pairing planner, difficulty table, NPC strategies).
* NPC-only games: `/squid config marblesVariant <value>`, `/squid debug timescale 0.3`, `/squid debug play marbles <npcs> [difficulty]`
  from the console (no player = NPC-only), `/tick rate 100` runs a whole game at real timing in a fifth of the time,
  `/squid debug perf` prints the profiler. With `/squid config debug true` the log lists every pair, offer, odd/even reveal and
  throw (planned and landed position).
* Human path: a headless client (`tools/xvfb-client.sh`) joined as the only human, `execute as <name> run squid debug play marbles
  <npcs>` starts a game with it.

## Limitations
* The village has 64 plots: with more than 128 contestants the surplus pairs cannot play and are counted as byes (logged).
* NPCs do not aim with a camera: their throw leaves from in front of the chest towards the planned spot while their body faces the
  target. The marble flies with the vanilla projectile motion on both sides (the client draws a dust trail in the thrower's
  colour); the server decides where it came to rest.
* A match that is still undecided at the hard deadline is a coin flip. At very small `timeScale` values (debug runs) rounds are
  shorter than a human could play them; most matches then end by count at the time call.
* A partnership only decides who plays whom (agreements are final and nobody can break one); an offer to a human who does not react
  expires after 12 s.
* Right-clicking another *player* (two humans pairing up) goes through a Fabric `UseEntityCallback` in `MarblesNet`; the tournament
  only routes clicks on NPC bodies to the games. That path shares all the code of the NPC path but was not run with two real clients.
* After a server restart the interrupted game is replayed from the start (only the variant setting is kept); marbles are not
  carried over.

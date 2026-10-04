# Game 4 - Marbles - design spec

Package names: `core/marbles/`, `game/marbles/` (`MarblesGame`, `MarblesNpcBehavior`), `client/game/marbles/` (`MarblesClient`,
screens), fixture `build/placeholder/MarblesPlaceholder`. Game id `marbles` (`GameKind.MARBLES`, `EliminationCause.LOST_MATCH`),
arena `ArenaId.MARBLES` (night village; markers `marbles.square_spawn`, region `marbles.square`, `marbles.pair_a/b`,
`marbles.pair_target`, `marbles.pair_line`, region `marbles.plot`, optional `marbles.table`, see `docs/ARENA_MARKERS.md`).
`MarbleProjectile` entity exists as a stub (ThrowableItemProjectile) and `ModItems.MARBLE` exists; hooks
`MiniGame.onInteractContestant` and `MiniGame.onMarbleRelease` are wired by the tournament. Config key `marblesVariant`
(`odd_even`, `throw`, `mixed`; add it to your docs; the field exists in `SquidConfig`). Port range 25630/25631.
Test: `squid debug play marbles 16`.

## The game
Contestants choose (or are given) a **partner**, go to their own spot in the village and play a two-player match for each
other's marbles. Everyone starts with the same number of marbles (Normal 10, scaled by `resourceScale`: Hard 8, Extreme 5-6). A
contestant who loses all marbles - or who has fewer marbles when time runs out - is eliminated; the winner of every pair
survives. (Betrayal and bluffing are the point; keep it tense.)

### Phase 1: pairing (about 45 s, timeScale-aware) in the square
Humans right-click another contestant (`onInteractContestant`) to propose a partnership; the target answers: NPCs decide after a
think delay by personality (cooperation, courage, who they are standing near, already partnered or not), humans get a modal
screen (Accept / Decline). Proposals have cooldowns/anti-spam. NPCs also pair among themselves by proximity/friendliness.
Unpaired contestants are paired randomly at the end; with an odd number one contestant gets a **bye** (announced, safe, shown on
HUD). Pairs then walk/teleport to their plot: partner A at `marbles.pair_a[k]`, B at `pair_b[k]` (facing each other).

### Phase 2: the match - two variants (config `marblesVariant`; `mixed` = each pair gets one by seeded rng)
**A. Odd or even** (wagering). Rounds alternate roles. Holder secretly puts some marbles (1..own count) in a fist; the guesser
wagers n marbles (1..min(own, holder's count... = their own count)) and guesses odd or even; on reveal a correct guess wins n
marbles from the holder, a wrong guess gives n to the holder; roles swap. Decision timers (~25 s, timeScale-aware; auto-random
move on expiry), clear animations/sounds, a result banner. `MarblesScreen` shows both marble counts (visual piles), the hand,
the selector (slider/buttons), odd/even buttons, timer, history of the last reveals (public information).
**B. Target throw** (skill). Players throw marbles alternately at their pair's target (`marbles.pair_target[k]`, concentric rings
radius 2) from the throw line (`marbles.pair_line[k]`): hold use with the marble item to charge a power bar (client shows it),
release -> `onMarbleRelease(chargedTicks)` -> `MarbleProjectile` flies on a ballistic arc (power + aim direction), lands, the
server scores by distance to the bullseye (rings 5..0). Each round both throw once; the closer marble wins marbles from the other
(stake = 1 + ring-difference bonus, capped), best-of rounds until a player is out of marbles or the round budget ends. NPC
throws use the *same* projectile code with aim error from skill and a power choice (a visible wind-up `MARBLE_WINDUP` then throw).
The pure scoring/physics helper (ring score, landing point from velocity) lives in `core/marbles` and is unit tested.

Tie handling when time runs out: the contestant with more marbles wins; equal => sudden-death single throw (variant B) or one
extra odd/even round (variant A); if still unresolved, a coin flip announced to both. A pair may also end early when one side
reaches 0.

### Server structure (`core/marbles`, pure and tested)
`MarbleLedger` / `OddEvenRound` (rules, validation of holds/wagers/guesses, outcome), `ThrowScoring`, `PairingPlanner` (random /
preference based pairing, bye selection, determinism), `MarblesRules` (difficulty table: marbles 10/8/5, decision timers,
total time ~210/180/150 s, throw error scaling, NPC bluff/skill).
Per-match state machine in the game (`Match` objects) driven by tick; humans and NPCs act through the **same** `Match` API
(`submitHold`, `submitGuess`, `submitThrow`); client messages are validated (phase, role, bounds, one submission per round).

### NPC behaviour (`MarblesNpcBehavior`)
Odd/even: wager fraction from risk tolerance and courage (cautious NPCs bet 1-2, gamblers go big when ahead), guess strategy mixes
pattern reading of the opponent's *revealed* past counts (observable history only; better with skill/patience), a personal bias
and some randomness; they think for a personality-dependent delay; reactions on win/loss (cheer, sob `Activity.SOB`,
`THINK`). Throw: aim point with error ~ f(skill, nerves), power selection, wind-up animation. Pairing: see above. Never read the
opponent's hidden hold/guess before the reveal. Performance: matches are independent; per tick only active matches do work.

### World and flow
Guards (`ctx.spawnGuards()`) walk the lanes; lantern/ambient sounds; when a pair finishes, the loser is eliminated with
`ctx.eliminateByGuard` (a muffled shot, quiet in the village); winners wait at their plot or in the square; HUD: own marbles /
opponent marbles, round info, timer, pairs remaining; the marble item count in the human's hotbar mirrors the ledger (the
tournament restores the real inventory afterwards). `isFinished` when all matches are resolved. `cleanup`: remove projectiles and
screens, restore anything changed. Handle: human disconnect mid-match (stand-in plays on from the same ledger), partner removed
by the tournament, both partners eliminated at once (time-out tie), 2 contestants only, 128 contestants (64 matches).

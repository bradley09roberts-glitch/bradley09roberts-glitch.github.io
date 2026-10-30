# Methodology

How NFL Props Lab turns public data into player-prop probabilities, how those are
evaluated, and what each number means. Decisions with dates are in
[../DECISIONS.md](../DECISIONS.md); data sources and gaps in [DATA_AUDIT.md](DATA_AUDIT.md).

## 1. Markets and statistic definitions

Core markets: QB passing yards, pass attempts, completions; QB/RB (and WR with at least
5 historical carries) rushing yards and rushing attempts; WR/TE/RB receptions and
receiving yards. Definitions follow nflverse player statistics, which "aim to match NFL
official box scores", reconciled against play-by-play on 2016-2025:

| Stat | Definition | Exact match with pbp counts |
|---|---|---|
| Pass attempts | completions + incompletions (interceptions included) + spikes; sacks and scrambles excluded | 99.91% of team-games |
| Completions | completed passes | 99.96% |
| Passing yards | sum of receiving yards on completions (sack yards excluded) | 99.98% |
| Rushing attempts | designed runs + scrambles + QB kneels | 99.96% |
| Rushing yards | yards on those plays (kneels usually -1 each) | 99.53% |
| Receiving yards | yards on completions to the player (laterals: small exceptions) | 97.86% |

Overtime is included (it is in the official totals). Two-point attempts and plays
nullified by penalty are excluded. Negative yardage is kept everywhere.

Documented exceptions: lateral plays can move a few yards between players; a stat
correction published after settlement creates a new settlement revision rather than
editing a past one.

## 2. Participation

A prop is usually void if the player does not take part. Each past player-game is labelled
`played` (a recorded stat, or on the field for at least one snap per nflverse
participation data), `inactive` (game-day inactive), `dnp` (active, never on the field),
`reserve`, `active_unverified` (active, no stat, and no on-field data yet: 2026 and
pre-2016) or `not_final`. A missing row is never a zero and a missing injury report is
never evidence of fitness. All headline probabilities are **conditional on taking part**;
`P(plays)` is reported separately.

## 3. Information available at the cutoff

A weekly slate (season, week) is forecast from games of earlier weeks only (D-005). No
model reads the target game's statistics, participation, snaps or inactive list;
full-season aggregates are never used; nflverse files released after a season
(participation) only label outcomes. Betting lines embedded in nflverse schedules and
play-by-play are removed into a separate table and are not readable by the feature code
(a test enforces this). Observed game-day weather is not used.

Live editions record the time each input was retrieved. Backtests reconstruct the same
cutoff, except that the availability-informed variant J1 must assume that the week's
final injury report (and, before 2025, the week's depth chart) was public at the cutoff,
because those files carry no publication timestamps.

## 4. Models

### J0 / J1: joint game simulation (primary family)

For each game, 10,000 joint draws (4,000 in backtests; Monte Carlo error is reported):

1. **Team volume.** Expected plays and dropback rate per team combine the offence's
   recency-weighted rate with the opponent's rate allowed (both shrunk to league average,
   multiplicative on plays and additive on the logit scale for rates). Game-level noise is
   a 4-dimensional normal draw (plays and dropback rate, both teams) whose covariance is
   estimated from within-season residuals in training data. Opponents' plays correlate at
   about -0.48 and pass rates at about -0.40, so the two teams are never simulated
   independently.
2. **Play types.** Dropbacks ~ Binomial(plays, rate); sacks ~ Binomial(dropbacks, sack
   rate); scrambles ~ Binomial(remaining, QB scramble rate); attempts = dropbacks -
   sacks - scrambles. Kneels are drawn from a rate that rises when the simulated script
   favours the team, and are removed from designed runs.
3. **Quarterbacks.** The starter is drawn from start probabilities (recent starts; J1 also
   uses the official report and the depth-chart QB1, with P(start) estimated on
   2016-2021: 0.95 if not listed, 0.64 if Questionable, 0 if Out/Doubtful). With the
   historical early-exit probability (about 6%) the starter leaves at a uniform point and
   the backup takes the rest; non-starting QBs can also take a cameo (holder, kneel),
   which counts as taking part with no passing volume.
4. **Availability.** Each non-QB on the week's game-day roster is available in a draw
   with his participation probability (recency-weighted history, shrunk to the prior for
   his position and experience; J1 caps it by injury-report status). Absent players'
   shares are redistributed proportionally among teammates in that draw, never by a
   guaranteed bump.
5. **Targets and runs.** Attempts are split among available receivers and an untargeted
   bucket (throwaways, about 3.2% of attempts) by a Dirichlet-multinomial on shrunk
   target shares; designed runs are split the same way among rushers (including QB
   designed runs). Concentrations are estimated from training data by method of moments.
6. **Efficiency.** Receptions ~ Binomial(targets, catch rate) with the catch rate shrunk
   towards the position rate and scaled by the opponent's completion factor. Yards are
   sums of per-play yards resampled from the player's most recent 80 catches or carries,
   mixed with the position pool in proportion n/(n+40), scaled by the opponent's
   yards-per-play factor. This keeps negative plays, zeros and long tails.
7. **Consistency.** QB completions and passing yards are the sums of the catches and
   yards he threw to receivers (split with the backup by binomial and hypergeometric
   thinning), so completions <= attempts, receptions <= targets, team targets <= team
   attempts and passing yards = receiving yards hold in every draw (tested).

Shrinkage and recency: shares and rates use exponential weights (half-life 6 player
games, 8 team games) with a 0.55 multiplier per season boundary, then shrink towards a
prior with explicit weight (3 effective games for shares, 8 for efficiency, 6-10 for team
and opponent rates). From v0.3, players with fewer than 6 games shrink towards the usage
of players with the same experience (0, 1-2, 3-5 games) at their position; established
players shrink towards the position median (experiment 2 showed band means hurt them).
From v0.4.2, when a running back on this week's roster (not listed Out or Doubtful) with at
least as big a share of designed runs missed some of a player's past games for his current
team, the player's designed-run share in those games counts at 70% (the games keep their full
weight). Targets are not adjusted: the same trim made target shares worse (exp11, D-031).

### A, C: baselines

Participation (v0.4): the official report adjusts P(plays) with rates measured on
2016-2021 (Out 0.2%, Doubtful 1.5%, Questionable 0.78 x the history-based probability).

* **A** recency-weighted game log (half-life 4 games) shrunk lightly to the position
  median; negative binomial for counts (dispersion by moments per market) and a
  discretised normal with a fitted mean-SD power law for yards.
* **C** role-tier climatology: the empirical outcome distribution of players at the same
  position and season-to-date opportunity tier.

### M: machine-learning challenger

LightGBM (Poisson objective for counts, squared error for yards) on the same pre-game
inputs as J0 plus expected-opportunity products and baseline-A means; distribution NB
(counts) or discretised normal (yards) with dispersion fitted on training residuals.
Refit at weeks 1, 5, 9, 13, 17 and 19 of each evaluated season on all earlier rows.

### X: stacked model

Per market, a linear pool w * J0 + (1 - w) * M of the two predictive distributions,
with w chosen on validation from {0, 0.25, 0.5, 0.75, 1}. A pool of two distributions is
a single valid distribution, so every line and milestone probability remains coherent
and monotone.

No model uses odds, spreads, totals, third-party projections or picks. A market-informed
challenger would need recorded prices and its own name and record; none exists.

### Exploratory scenarios

`/api/scenario/<game>` (Game page, server only) re-runs one game's joint simulation with
chosen players removed, using the edition's J1 inputs and a fixed seed. Removed players'
volume is shared among the remaining teammates in proportion to their shares; unknown
players and removing every quarterback are refused. Results are labelled exploratory,
never written to an edition, and show QB-receiver correlations from the joint draws (why
multiplying single-leg probabilities misstates a same-game combination).

### Team defence profiles (context, not a model input)

`npl.defense` (Defences page, game-page matchup, a line on each prop card) describes each
defence before the edition's week, regular season only. For every measure (yards per play,
net yards per dropback, sack rate, yards per designed run, QB rushing yards, and receiving
yards, targets, catch rate and yards per target allowed to WRs, TEs and RBs; position = the
receiver's latest roster position):

* **Opponent adjustment.** Each game is compared with what the same offence managed in its
  other games of the season before the week (the league rate if it has fewer than two).
* **Shrinkage measured, not guessed.** The difference is multiplied by n/(n+K), K per
  measure, fitted on 2012-2021 as the value that best predicts the same defence's
  difference over the rest of that season (cutoffs weeks 5, 8, 11, 14). Read as a
  normal-normal model, K = per-game noise variance / true-skill variance.
* **Labels.** "Likely strong/weak" when the posterior probability of being better (worse)
  than average is at least 80%; "can't tell yet" otherwise. Ranks 1-32 order the shrunk
  differences.

Checked out of sample on 2022-2025 (`reports/defense/STABILITY.md`): about 25-30% of an
overall or pass-defence difference seen after six games carries over to the rest of the
season; run defence about 20%; sack rate and QB rushing about 10%; receiving splits by
position 2-20%, with tight-end and running-back splits close to pure noise. Taken at face
value (unshrunk), every early difference predicts the rest of the season worse than
assuming the defence is average. The forecasts' own opponent factors are team-level (plays,
pass rate, sack rate, yards per completion, catch rate, yards per carry); position splits
are not added (D-017).

## 5. From a distribution to a line

All probabilities for a player-market come from one saved integer distribution:
half-point line `P(Over 54.5) = P(X >= 55)`, `P(Under) = 1 - P(Over)`; integer line
`P(Over 5) = P(X >= 6)`, `P(Push) = P(X = 5)`, `P(Under) = P(X <= 4)`; milestone
`50+ = P(X >= 50)`. Fair (break-even) decimal price with a push: `(p_win + p_loss) /
p_win`; without a push `1 / p`. A user-entered line is always labelled "manual line — no
verified price" and never called value.

## 6. Evaluation

* Splits fixed before evaluation (D-004): training/history 2012-2021 (fitting from 2016),
  validation 2022-2023, untouched test 2024-2025, prospective from 2026 week 7.
* Weekly walk-forward: re-estimate, forecast the whole slate, then score.
* Primary metric (fixed in advance): mean CRPS on the integer outcome, per market, on the
  common subset of player-games scored by every compared model, among players who took
  part. Raw scores of different markets are never pooled.
* Secondary: log score (floor 1e-4), MAE of the median, bias and RMSE of the mean,
  inclusive 50/80/90% interval coverage (discrete outcomes over-cover), randomised PIT
  histograms, Brier and log loss at fixed milestone thresholds (not offered lines).
* Uncertainty: 95% intervals for CRPS differences resample whole weeks (props in one week
  are correlated).
* Breakdowns: season, position, starter vs reserve, history depth, regular vs postseason.
* Every row not scored is counted by reason (inactive, DNP, not projected to play).
* Each run writes a ledger (one row per model x player-game x market) and a manifest
  (code commit, seeds, splits, model version, ledger SHA-256); every headline number is
  recomputed from the ledger (`evaluate.py`).

A retrospective backtest is research, not a published record. The author of this build
had general knowledge of past NFL seasons and the validation period was used for
several experiments (see reports/EXPERIMENTS.md); only the single frozen test run and the
prospective record are free of that selection.

## 7. Money (separate from probability)

Only real recorded prices count. A quote is eligible if it was observed at or before the
decision time and is fresher than the strategy's limit; the price used is a fixed
bookmaker or the best price available at the decision among verified UK-accessible books,
never one seen later. Singles are flat 1 unit; an illustrative GBP bankroll reserves
stakes for concurrent bets and refuses bets it cannot fund. Settlement follows each
bookmaker's encoded rules (participation, overtime, starts, corrections, exchange
commission); unreviewed books use a default profile marked unverified. Thresholds are
frozen on validation and a probability haircut is applied for model uncertainty. Without
recorded prices the result is the explicit state **ROI unavailable**.

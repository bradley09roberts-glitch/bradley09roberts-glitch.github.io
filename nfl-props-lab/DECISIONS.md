# Decisions log

Each entry: what was decided, why, and what would change it. Dates are UK dates.

## D-001 (25 Oct 2026) Separate project inside this repository, on the working branch
NFL Props Lab lives in `nfl-props-lab/` on branch `claude/amazing-carson-86b80f`. The
FightBase UFC site (`index.html` on `main`) is not touched, and no Touchline/football files
are added. Raw data, odds and the SQLite store are git-ignored (`data/`); published
editions, coverage reports and ledgers are committed. If the owner prefers a private
repository (as was done for the football project), the folder moves as-is.

## D-002 (25 Oct 2026) Public data: nflverse first; Pro Football Reference data not used for modelling
nflverse release files are downloaded directly (URL patterns of nflreadpy 0.1.5) into an
immutable raw store with SHA-256 and retrieval times. Sports Reference's terms (section 5,
updated May 2023) prohibit using its content to support machine-learning prediction, so
nflverse's `snap_counts` and `pfr_advstats` (sourced from Pro Football Reference) are
ingested for the coverage audit only and are not model inputs, features or labels.
Changes if: written permission, or a licensed snap-count source.

## D-003 (25 Oct 2026) Participation labels from on-field data; never as features
Whether a player took part in a past game comes from nflverse participation (NGS
2016-2022, FTN 2023-2025, CC-BY-SA 4.0, released after each season). Because those files
appear only after the season, they label outcomes and never feed a pre-game feature.
2026 has no participation file yet, so 2026 active players without a recorded stat are
`active_unverified`, not zero and not DNP.

## D-004 (25 Oct 2026) Chronological splits fixed before evaluation
Chosen from coverage before any result was inspected:
- history/training: 2012-2021 (features from 2012; parameters fitted on 2016+ because
  on-field participation labels start in 2016)
- validation (selection, calibration, strategy thresholds): 2022-2023
- untouched test: 2024-2025 (run once with frozen choices)
- prospective: 2026 week 7 onwards (genuine pre-kickoff editions)
2026 weeks 1-6 are a supplementary retrospective period. One debugging pass used three
validation weeks (2023 W5, W9, W14) before the first edition; it found and fixed a
backup-QB participation bug. That use of validation data is disclosed here.

## D-005 (25 Oct 2026) Weekly batch rule
A slate (season, week) is forecast with history strictly before that week. Same-week
earlier games (e.g. Thursday) are not used for the Sunday games of that week.
Parameters are re-estimated for every slate (weekly retraining).

## D-006 (25 Oct 2026) Primary metric and conditioning
Primary comparison metric, fixed before evaluation: mean CRPS of the integer outcome per
market, on the common subset of player-games scored by every compared model, among
players who took part (props on non-participants are void under the usual rules).
Secondary: log score, MAE of the median, bias, 50/80/90% interval coverage, PIT
calibration, and Brier/log loss at fixed milestone thresholds (not offered lines).

## D-007 (25 Oct 2026) Model family and variants
J0 = joint game simulation, participation from history only (cutoff-faithful in
backtests). J1 = J0 plus the week's official injury-report status and the depth-chart
QB1. Historical injury reports and pre-2025 depth charts carry no publication time, so in
backtests J1 assumes they were public at the early cutoff (Friday 4pm ET final report for
Sunday games under NFL policy). J1 is therefore a separately named availability-informed
variant; live editions use J1 with observed retrieval times. A and C are baselines.
No model uses odds, spreads, totals, third-party projections or picks as inputs.

## D-008 (25 Oct 2026) Which edition counts for the prospective record
Every edition is kept. For each game and model, the prospective record scores the last
edition published before that game's kickoff; earlier editions are scored separately.
Fixed now, before any 2026 week 7 result exists.

## D-009 (25 Oct 2026) Late (inactives) edition unavailable
Game-day inactive lists are published about 90 minutes before kickoff. There is no
permitted automated source for them here (NFL.com terms section 1.3 prohibit systematic
retrieval), so the late edition is marked unavailable rather than guessed.

## D-010 (25 Oct 2026) Odds: no key, no ROI
No odds API key is configured. Without real recorded prices there is no ROI, yield or
value claim anywhere. The Odds API documents NFL player props for US bookmakers; it does
not document them for UK bookmakers, and BetMGM UK is not in its UK list. US prices would
be research references only, never "UK-accessible".

## D-011 (25 Oct 2026) v0.2 rejected, v0.3 adopted
Experience-band priors for everyone (v0.2) cut bias but worsened CRPS for established
players. v0.3 keeps them only for players with fewer than 6 games and estimates the
depth-chart QB1 start probabilities from 2016-2021. Evidence: reports/EXPERIMENTS.md.

## D-012 (25 Oct 2026) Stacked primary model X; live headline X1
Per-market linear pools of J0 and the ML challenger M, weights frozen on validation
(experiment 5). X (J0-based) is the cutoff-faithful primary model for research claims; X1
applies the same weights to J1 and is the headline in live editions because it uses the
official report observed before publication (so a player listed Out is never shown with
a history-only participation probability). The joint simulation J remains the source of
game-level scenarios and cross-player coherence; X/X1 are per-market marginals.

## D-013 (25 Oct 2026) Test run executed once
exp06-TEST-frozen-v0.3 ran the frozen configuration (commit ab6a347) on 2024-2025 once.
No change was made because of its results. Further model changes must be validated on
2022-2023 and judged prospectively; the 2024-2025 seasons are no longer untouched.

## D-014 (25 Oct 2026) Ledgers in git only for headline runs
Full ledgers are committed for the selected validation run (exp05) and the test run
(exp06); other experiments commit summaries and manifests with the ledger SHA-256.

## D-015 (25 Oct 2026) v0.4 adopted: measured injury-report participation rates
Experiment 7 (validation): replacing the guessed caps (Out 2%, Doubtful 25%, Questionable
80%) with rates measured on 2016-2021 (Out 0.2%, Doubtful 1.5%, Questionable x0.78)
improves P(plays) for listed players (log loss 0.401 -> 0.385, Brier 0.132 -> 0.130) and
leaves conditional distributions within +/-0.2% CRPS. Only J1/X1 change. Not re-run on the
test seasons (D-013); judged prospectively from the next edition.

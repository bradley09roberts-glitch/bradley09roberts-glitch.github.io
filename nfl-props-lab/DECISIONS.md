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

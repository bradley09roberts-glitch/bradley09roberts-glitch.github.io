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

## D-016 (25 Oct 2026) v0.4.1 adopted: unavailable quarterbacks cannot enter a game
The 07:43 edition showed an Out quarterback with P(plays) 16% because the early-exit
replacement and the QB cameo ignored the non-starter's availability. Experiment 8
(validation): P(plays) log loss for listed players 0.385 -> 0.371, overall Brier 0.0765 ->
0.0759; conditional CRPS changes are within Monte Carlo noise (the J0-based X, which the fix
barely touches, moved by a similar amount). Earlier editions stay on file unchanged.

## D-017 (25 Oct 2026) Defence profiles are descriptive; position splits stay out of the model
Requested: per-team defence profiles (pass, run, each position, overall). Built as context
with opponent adjustment and per-measure shrinkage fitted on 2012-2021. The stability check
on 2022-2025 shows how little of most early-season splits persists (overall and pass about
25-30% after six games; TE and RB receiving splits 2-16%, out-of-sample skill near zero).
The planned experiment adding position-specific defence factors to the joint simulation
(exp09) was therefore not run: the shrunk factors are within a few per cent of 1 for TEs
and RBs and small for WRs, so the expected gain is below the Monte Carlo noise of a
validation backtest. It can be revisited with a multi-season prior for defences.

## D-018 (26 Oct 2026) Odds key configured; UK region has no NFL player props
The user supplied a free The Odds API key (stored only in the git-ignored `.env`). For the
Monday game (DAL at PHI) the `uk` region listed 20 bookmakers but only game markets
(match odds, spreads, totals): no player props. The `us` region listed core player props at
six books (450 quotes stored at 14:01 UTC, about 6.6% average margin). US quotes are
`research_reference`: they are used to compare the model with a market, never to claim
UK-realisable profit. Raw odds stay in `data/` (git-ignored; the provider's terms forbid
redistribution). The first live run exposed a linking bug (the fixture recurs every season,
so quotes matched every DAL-PHI game and lost their links); fixed and tested, and the
corrected links were appended to the new append-only `quote_links` table rather than
changing stored quotes. Credits used so far: 9 of 500 this month.

## D-019 (28 Oct 2026) NBA added at the user's request; data sources and splits
The original brief excluded the NBA; on 28 Oct the user asked for it explicitly, so the
exclusion is lifted for the NBA only (UFC, association football and college football stay
out). Data: SportsDataverse release assets (player and team box scores, schedules with
tip-off times, daily injury snapshots from 8 Sep 2026), CC BY 4.0 with attribution. Their
content originates from ESPN, whose terms forbid automated collection from ESPN itself; we
never access ESPN, only the openly licensed releases, the same footing as the nflverse data.
The NBA's own injury report is not used: NBA.com terms (sect. 9(iv)) bar use of NBA
statistics in connection with gambling. Splits: fit 2018-2023 (2016-2017 warm-up),
validation 2024-2025, test 2026 (one frozen run), prospective from 2027 (2026-27). Markets:
points, rebounds, assists, 3-pointers made, PRA. Injury reports have no history before
Sep 2026, so their effect can only be judged prospectively.

## D-020 (28 Oct 2026) NBA model v0.1 frozen (N1, empirical minutes)
Validation (2024-2025, 56,071 player-games, week-block 95% intervals): N1 beats the
exponentially weighted average (A, its dispersions tuned the same way) on every market:
points -2.7%, rebounds -2.2%, assists -1.7%, threes -1.1%, PRA -3.8% CRPS. Over/under
probabilities at book-like lines are calibrated within about 2 points. Parameters frozen in
`reports/nba/model/n1_params.json` before the single test run.

## D-021 (28 Oct 2026) NBA test run done; 2026 is no longer untouched
The frozen nba-0.1 was run once on 2025-26 (n03): it beat the recent average on all five
markets with intervals excluding zero (points -2.9%, PRA -4.1%). Further NBA changes are
validated on 2024-2025 and judged prospectively on 2026-27.

## D-022 (28 Oct 2026) Prop-research presentation; lines shown are never invented
The user asked for an app that looks like a commercial prop tool. The app adopts the
generic features (hit rates over recent games against a line, game-log charts, matchup
ranks, projections) under its own name and design; no branding, text, code or accuracy
claims are copied. The line on a card is, in order: the user's own, a stored US market
line (labelled research reference), or the model's fair 50/50 line. Hit rates are
descriptive (how often a player beat a line in the past), not forecasts: the L10 average
was the weakest NBA forecast tested (reports/nba/EXPERIMENTS.md).

## D-023 (28 Oct 2026) NBA defensive matchups: definitions and reliability
Requested: defence by shot type and court area, by opposing position, fouls and free throws,
with filters, a half-court map and player matchups. Each field-goal attempt is placed in one
area by its recorded type (2 or 3) and its coordinates (restricted area within 4 ft;
non-restricted paint; mid-range; left/right corner; left/right wing; top of the arc within
9 ft of centre), so areas never overlap; summaries (2PT, 3PT, paint, corners, above the
break) add areas. Free throws are separate. Positions are the single G/F/C listing per game;
PG/SG/SF/PF are shown as unavailable. Filters are applied to per-game rows for all teams
alike, so ranks compare like with like (1st = allows the least). Split-half reliability on
2023-2026 regular seasons is shown with every split: points allowed per 100 possessions
0.39 after 10 games and 0.84 after a full season; rim attempts allowed 0.89 for a season;
corner-3 percentage allowed is pure noise (0). These splits are context; none feeds the
NBA projections.

## D-024 (29 Oct 2026) Blowout (expected-margin) minutes term not adopted
Suggested by the user: big spreads mean blowouts, and blowouts cut starters' minutes. Tested
on 2024-2025 validation against frozen nba-0.1 (n04), with the margin estimated from team
form because historical spreads are not stored. Two versions (a fitted minutes shift; minutes
error distributions by margin class) both made CRPS slightly worse (points +0.09% to +0.15%,
PRA +0.11% to +0.17%, intervals excluding zero). Starters on big favourites do lose about 0.7
minutes more than in close games, but score about 2.5% more per minute, so their totals were
already forecast without bias. N1 stays nba-0.1, and live builds do not collect spreads. The
spread remains useful context on the slate, not a model input.


## D-025 (29 Oct 2026) Provisional NBA box scores from ESPN game feeds
SportsDataverse publishes box scores about 12 hours after games, so teams on the second night
of a back-to-back were forecast without their previous game, and early lines could not be
used. `npl nba update` now also fetches the ESPN game feed (the same feed SportsDataverse
is built from) for finished games of the last 3 days that SportsDataverse lacks, stores it
unchanged, and builds those games from it, labelled box_source = espn_provisional. As soon as
SportsDataverse has a game, its version is used instead. Check on four 27 Oct games present
in both: same players, starters, positions and statistics (981 values, no real differences).
Settlement may use provisional rows (they matched exactly); a later settlement run picks up the
SportsDataverse version once published.

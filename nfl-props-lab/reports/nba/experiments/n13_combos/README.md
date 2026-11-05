# n13: the two-stat combos (P+R, P+A, R+A): proxy lines, calibration and dispersion

RESEARCH ONLY. Nothing here was tested against a combo price, and nothing here is a bet, a tip or a
promise of profit. We hold **no past bookmaker lines** for points + rebounds, points + assists or
rebounds + assists.

**Result: no calibration was written.** The proxy-line idea failed the check fixed before the run,
so `calibration.json` has no `calibration_combo_proxy` block. Combo cards keep `p_cal_market` null
and `p_cal_basis` null: the app has no tested model view of a combo at any line. The combo
distributions run slightly too narrow. For P+R and R+A, about half the excess comes from
correlation between the parts that the model leaves out.

## Question and protocol

- **Proxy line.** For a player-game with main lines in the component markets, the proxy combo line
  is the sum of those lines, for example P+R = points line + rebounds line. Could proxy lines stand
  in for real combo lines when calibrating the combos?
- **Testing the idea on PRA first.** PRA is the only summed market with real lines, so the proxy
  was checked there before being used for anything.
- **Lines.** The n10 rows: The Odds API historical snapshots about 3 h before tip, US books, main
  lines, with the n10 consensus. That is the construction `calibration_v4` for nba-0.3 was fitted
  on.
- **Seasons.** Fit on 2024-25 (season key 2025). Test once on the 2025-26 sample (2026: every
  fourth date, 42 dates), the same split as n11 and n12.
- **Model.** nba-0.3 N1, walk-forward. `B.frame` uses only inputs from before each game. The rows
  are the same as in n12 (played, previous game, complete box score, game with stored quotes).
  `has_line` comes from the five original markets.
- **Calibration form.** Exactly `calibration_v4`: logit p_cal = a + b * logit(p_model at the line),
  per market, logit clip 1e-4, unpenalised maximum likelihood.
- **Intervals.** Date-block bootstrap, 2,000 resamples.
- **Criterion, fixed before any proxy result was seen.** The proxy counts as validated only if all
  three hold:
  1. |b_proxy - b_real| <= 0.10 on PRA.
  2. On the 2025-26 *real* PRA lines, the log loss of the proxy-fitted calibration minus that of
     the real-line-fitted one is <= 0.001 nats per prop.
  3. The upper end of that difference's 95% interval is < 0.002.

## Checks on the machinery

- **calibration_v4 reproduces.** Refitted from these rows: 71,922 fit props and 18,753 test props.
  Every coefficient is within 0.0008 of the stored one. Test log loss is 0.68803, the same as
  stored.
- **The five original markets do not move.** Their pmfs from the working tree (with the combos)
  are bit-identical to HEAD's pre-combo code on all 32,526 real backtest player-games (26,108 plus
  6,418). See `head_check.py`, run against a `git archive` of HEAD in scratch.
- **Drift from n12's stored arrays.** These forecasts differ from the arrays n12 stored on 3 Nov
  by at most 0.002 in any survival probability. The canonical NBA tables have been rebuilt since
  (D-046 box-score source change). I did not trace the exact cause; it is too small to matter here.

## 1. PRA: is the sum of the parts' lines close to the real PRA line?

Real PRA consensus line minus (points + rebounds + assists lines). All lines are half points, so
the difference is a whole number.

| | 2024-25 | 2025-26 |
|---|---:|---:|
| player-games with all four lines | 14,141 | 3,563 |
| mean (sd) | +0.43 (0.65) | +0.40 (0.66) |
| equal (within 0.5) | 49.1% | 50.2% |
| within 1.0 | 97.0% | 97.1% |
| real 1 higher / 1 lower | 42.6% / 5.3% | 41.0% / 5.9% |

The books set PRA about 0.4 above the sum of the parts. That is what skewed parts imply: the
median of a sum is above the sum of the medians.

**PRA calibration, fitted on proxy lines versus real lines** (2024-25, the same 14,141
player-games):

| fitted on | a | b | over rate at those lines |
|---|---:|---:|---:|
| real PRA lines | -0.0148 | 0.0017 | 49.6% |
| proxy PRA lines | +0.0728 | 0.0380 | 51.9% |
| (calibration_v4, all real PRA rows) | -0.0133 | 0.0006 | |

**Test on the 2025-26 real PRA lines** (3,563 props), log loss:

| | log loss |
|---|---:|
| coin flip | 0.69315 |
| market (margin-free consensus) | 0.69209 |
| raw model | 0.72350 |
| real-line fit | 0.69310 |
| proxy-line fit | 0.69401 |
| calibration_v4 | 0.69311 |

- **Proxy fit minus real fit:** +0.0009 [-0.0006, +0.0024].
- **Criterion:** (1) passes (b differs by 0.036). (2) passes narrowly (+0.0009 against 0.001).
  (3) **fails**: the upper end is 0.0024.
- **Why it fails.** The slope transfers: both fits say the model has almost no view at a PRA line.
  The level does not transfer. Because proxy lines sit about 0.4 below the books' lines, the
  proxy-fitted intercept puts the over chance about 2 points too high at a real line.
- **Even the real-line fit only ties a coin flip on PRA:** -0.00004 [-0.0003, +0.0002].
- **The rounding cannot be checked.** For two-stat combos the proxy is a whole number, because two
  half points add to an integer, and it must be rounded to a half point. PRA's proxy needs no
  rounding, so this step cannot be validated on any real data we hold.

## 2. The combos on proxy lines (descriptive only; NOT adopted, nothing written)

Computed because the fits were part of the plan. They are shown only to describe the model; the
criterion above failed. Two ways of rounding the whole-number proxy to a half point:

- **both:** L - 0.5 and L + 0.5, each with weight 1/2. This was the planned primary.
- **tilt:** one neighbour, on the side the parts' margin-free over chances lean.

| combo | rounding | a | b | test player-games | over rate | coin | raw | calibrated | calibrated minus coin [95%] |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| P+R | both | 0.023 | 0.094 | 3,836 | 50.4% | 0.69315 | 0.71356 | 0.69232 | -0.0008 [-0.0015, -0.0002] |
| P+A | both | 0.054 | 0.090 | 3,587 | 51.0% | 0.69315 | 0.71923 | 0.69253 | -0.0006 [-0.0017, +0.0005] |
| R+A | both | 0.010 | 0.397 | 3,572 | 50.1% | 0.69315 | 0.70205 | 0.68815 | -0.0050 [-0.0086, -0.0015] |
| P+R | tilt | 0.029 | -0.008 | 3,836 | 50.5% | 0.69315 | 0.71594 | 0.69318 | +0.0000 [-0.0004, +0.0005] |
| P+A | tilt | 0.065 | 0.014 | 3,587 | 51.2% | 0.69315 | 0.72174 | 0.69289 | -0.0003 [-0.0014, +0.0009] |
| R+A | tilt | 0.029 | 0.174 | 3,572 | 50.3% | 0.69315 | 0.71037 | 0.69242 | -0.0007 [-0.0025, +0.0010] |

Fit player-games: P+R 14,621, P+A 14,231, R+A 14,156.

**How to read this:**

- **The coefficients depend on an arbitrary rounding choice.** P+R's b is 0.094 with one rounding
  and -0.008 with the other; R+A's is 0.40 against 0.17. That alone rules out writing them.
- **The "both" rounding flatters the model** (found after the run). A line away from the market's
  own lets any forecaster "see" that the line is off centre. Moving the five markets' real lines 1
  point down and 1 point up raises the fitted b: points 0.05 to 0.29, rebounds 0.39 to 0.86,
  assists 0.42 to 0.90, threes 0.73 to 0.99, PRA 0.00 to 0.15. The "both" rows are offset by half a
  point and carry the same flaw.
- **Where the rounding does not hand the model a free signal**, the result matches the
  single-stat markets at real lines. For P+R and P+A the slope is about zero: no view at the line.
  For R+A it is small, consistent with rebounds and assists (0.39 and 0.42 at real lines).
- **The raw combo chances are poorly calibrated** at any of these lines: log loss 0.70-0.72, worse
  than a coin flip. Uncalibrated combo chances should not be read as probabilities at a line.

None of this was tested against a price, and none of it is an edge. Because the line sits near the
median, a slope near zero means the model has nothing to add there.

## 3. PIT and dispersion of the combo pmfs (2025-26 test sample; 6,418 played player-games)

A uniform PIT has variance 0.0833 and 10% of outcomes in each tenth. The variance ratio is the mean
squared error divided by the mean model variance: above 1 means too narrow. Intervals are
date-block.

| market | PIT var | lowest tenth | highest tenth | central 80% | mean bias | variance ratio [95%] |
|---|---:|---:|---:|---:|---:|---|
| pts | 0.0894 | 13.1% | 10.3% | 76.6% | -0.04 | 1.08 [0.98, 1.21] |
| reb | 0.0838 | 10.4% | 10.1% | 79.5% | -0.04 | 0.99 [0.93, 1.08] |
| ast | 0.0839 | 10.0% | 9.9% | 80.1% | +0.00 | 1.06 [0.96, 1.20] |
| pra | 0.0896 | 12.5% | 10.6% | 76.9% | -0.08 | 1.10 [1.00, 1.26] |
| **P+R** | 0.0896 | 13.0% | 10.4% | 76.6% | -0.08 | 1.10 [1.01, 1.24] |
| **P+A** | 0.0894 | 12.5% | 10.3% | 77.1% | -0.04 | 1.08 [0.98, 1.22] |
| **R+A** | 0.0855 | 10.7% | 10.4% | 78.8% | -0.04 | 1.06 [0.98, 1.20] |

On the 2024-25 rows (26,108) the direction is the same and the intervals are tighter: P+R 1.11
[1.07, 1.16], P+A 1.07 [1.04, 1.12], R+A 1.05 [1.02, 1.09].

**Does the conditional-independence convolution leave out correlation?** Given minutes, N1 treats
the parts as independent, so the only covariance it models comes from shared minutes. The table
compares the residual covariance with the model's minutes-only covariance:

| combo | excess covariance, test [95%] | excess, fit [95%] | residual correlation, observed v model | excess as share of combo variance |
|---|---|---|---|---:|
| P+R | +1.01 [+0.33, +1.98] | +1.11 [+0.80, +1.48] | 0.30 v 0.25 | 4.3% |
| P+A | +0.10 [-0.37, +0.77] | +0.01 [-0.20, +0.25] | 0.20 v 0.20 | 0.5% |
| R+A | +0.30 [+0.11, +0.58] | +0.24 [+0.16, +0.32] | 0.24 v 0.17 | 5.4% |

- **This is the reverse of the prior in the brief.** Points and assists show no correlation beyond
  shared minutes: the convolution is right for P+A, and its slight narrowness is inherited from
  points.
- **Rebounds correlate positively beyond minutes**, with points and with assists. P+R and R+A are
  too narrow by about 4-5% of their variance from this alone.
- **The rest is inherited from the parts**, as for PRA. Points' variance ratio is 1.08, and the
  low tail is heavy (13% of outcomes in the lowest tenth): short nights, early injuries, blowouts
  and foul trouble, as noted since n03.
- **None of this is fixed.** A fix would be a model change, needing validation first and a new
  version string; the five markets stay nba-0.3 and unchanged.

## What would change the answer

Only real combo lines would. Pulling them (`npl quotes --sport nba --combos`) costs about 3 extra
credits per game per pull. After a few thousand stored props, a real-line calibration could be
fitted and tested the way calibration_v4 was. That is the lead's call; nothing here pulls prices.

## Files

Run from the repo root. Raw prices and per-prop rows go to the git-ignored data/runs/n13.

    uv run python reports/nba/experiments/n12_audit/extract.py      # if data/runs/n12/data/q_*.parquet are missing
    uv run python reports/nba/experiments/n13_combos/pmfs.py
    NPL_HOME=$PWD/data PYTHONPATH=<git archive of HEAD>/src .venv/bin/python reports/nba/experiments/n13_combos/head_check.py
    uv run python reports/nba/experiments/n13_combos/analysis.py

- `pmfs.py`: walk-forward nba-0.3 pmfs for all eight markets, with PIT and moments.
- `head_check.py`: the bit-identity check against the pre-combo code.
- `analysis.py`: the proxy lines, the PRA validation (the criterion is in its docstring), the
  combo fits, the post-hoc offset check, and the PIT and correlation figures.
- `n13_summary.json`: aggregates only.

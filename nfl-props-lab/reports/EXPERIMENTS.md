# Experiment log

Every model experiment run in this project, in order, including the rejected ones. All
validation experiments use the same 44 slates (2022-2023 regular season and postseason)
and are scored on the common subset of player-games. Reports with full tables are in
`reports/backtests/<label>/`; each manifest records the code commit, seed, number of
simulations and the SHA-256 of the ledger (ledgers of non-headline runs stay in
`data/runs/`, reproducible from the commit and the call below).

| # | Label | What changed | Outcome |
|---|---|---|---|
| 0 | sanity-validation (3 weeks: 2023 W5, W9, W14) | first debug pass of v0.1 | found backup-QB bias: QBs who played without passing (holder, kneels) were forecast with full volume; fixed with the QB "cameo" state and depth-chart QB1 in J1. Not a reported result. |
| 1 | exp01-validation-v0.1 | v0.1 as first published (J1, J0, A, C) | J beats baseline A on 6 of 7 markets (95% week-block CIs exclude 0); receptions worse than A (+0.8%). Over-prediction concentrated in players with 0-2 games of history. |
| 2 | exp02-validation-v0.2-bandpriors | experience-band priors (mean usage) for every player | **Rejected.** Less bias but worse CRPS on receiving and rushing (band means pulled established depth players up and starters down). |
| 3 | exp03-validation-ml (+ merged with exp01) | ML challenger M (LightGBM + NB/normal) | M best on receptions and rush attempts; poor on yardage (normal shape wrong, PIT far from uniform). |
| 4 | exp04-validation-v0.3-lowhistory-priors | band priors only below 6 games; depth-chart QB1 P(start) estimated on 2016-2021 | **Adopted as v0.3.** Better than v0.1 on 5 of 7 markets for J0; rushing flat. |
| 5 | exp05-validation-v0.3-stacking | J0, J1, M and linear pools X25/X50/X75 of J0 and M | Pools beat every single model on all 7 markets. Frozen per-market weight on J0 (rest on M): pass att 0.5, completions 0.5, pass yds 0.5, rec yds 0.75, receptions 0 (pure M), rush att 0.25, rush yds 0.75 (commit ab6a347). |
| 7 | exp07-validation-v0.4-injury-rates (run after the test, validation seasons only) | injury-report participation adjustment measured on 2016-2021 instead of guessed | **Adopted as v0.4** for live editions: P(plays) log loss for listed players 0.401 -> 0.385; conditional CRPS within +/-0.2%. Not evaluated on the test seasons. |
| 8 | exp08-validation-v0.4.1-qb-availability (validation only) | QBs listed Out/Doubtful can no longer enter as replacement or cameo; replacement = most likely available QB | **Adopted as v0.4.1**: listed-player P(plays) log loss 0.385 -> 0.371; conditional CRPS within Monte Carlo noise. |
| 9 | defense-stability (descriptive, not a model change) | how much each defensive split seen before weeks 5/8/11/14 carries over to the rest of the season; K fitted on 2012-2021, checked on 2022-2025 | Overall/pass 25-30% carry-over after six games, run ~20%, sack rate and QB runs ~10%, TE/RB receiving splits near noise. Used to calibrate the Defences page; position factors not added to the model (D-017). `reports/defense/STABILITY.md` |
| 10 | exp10-validation-returning-teammate (share level, validation only) | down-weight a player's past games for his current team in which a key teammate now on the roster (same or bigger role, not listed out) did not play; weight 0.5 / 0.25 / 0.1 / 0 chosen on 2018-2021, judged on 2022-2023 by the log-likelihood of who got each team's designed runs and targets | **Not adopted** (D-029). Worse at every weight on both periods, intervals excluding zero: validation carries -0.004 to -0.021 and targets -0.005 to -0.015 per event (weight 0.5 to 0). Prompted by MarShawn Lloyd (W8 2026, model 7.1 carries, actual 3, Josh Jacobs back from reserve). Script and numbers: `reports/experiments/exp10_returning_teammate.py`, `exp10_summary.json`. Correction: this run did not in fact leave out teammates listed Out or Doubtful (they were treated as returning); exp11 re-ran it with them left out and the result is the same. |
| 11 | exp11-validation-backup-trim (share level 2018-2023, then full backtest on validation) | keep a player's past games without a returning bigger-role teammate at full weight but scale his share in them by a trim (0.9 to 0.5), separately for designed runs and targets; teammates listed Out/Doubtful are not counted as returning | **Adopted for designed runs as v0.4.2** (D-031), trim 0.7 chosen on 2018-2021. Share level, designed runs: +0.0020 log-likelihood per run on 2018-2021 and on 2022-2023 (intervals exclude zero); targets worse at every trim (not adopted). Full backtest on 2022-2023, same seed: X1 rushing attempts CRPS -0.19% (-0.24 to -0.14), rushing yards -0.29% (-0.43 to -0.16), J1 -0.79% and -0.38%; over/under thresholds for rushing attempts and yards better at all 14 tested (binary log loss); exact-count log score for rushing attempts slightly worse (X1 -0.0016, J1 -0.0043 per player-game); passing and receiving within noise. Scripts and numbers: `reports/experiments/exp11_backup_trim.py`, `exp11_summary.json`, `exp11_prop_level.py`, `exp11_prop_level.json`. |
| 12 | gap-check (descriptive, not a model change) | reference over gaps from the user's research board tested against our own forecast errors (NFL X1 v0.4.2 2022-2023, NBA N1 2024-2025), line = highest half-point at least the gap below our projection | Optimistic by construction (no bookmaker information in the line). Count markets clear 60% at the reference gaps; yardage does not: WR/TE receiving yards +7.5 won 50%, RB receiving yards +2.5 44%, QB rushing yards +5 47%, RB rushing yards +7.5 57%. Projection gaps are not used as pick triggers. `reports/thresholds/` |
| 6 | **exp06-TEST-frozen-v0.3** | single run of the frozen configuration on the untouched 2024-2025 seasons (X1, X, J1, J0, M, A, C) | see the test table below. Run once; nothing was changed afterwards. |

## Test result (2024-2025, 44 slates, 4,000 simulations per game, code ab6a347)

Mean CRPS on the common subset (lower is better); in brackets the difference from
baseline A with a 95% interval from resampling whole weeks.

| Market | n | X (primary) | X1 (live headline) | J0 | M | A |
|---|---|---|---|---|---|---|
| Pass attempts | 1,434 | 5.350 (-1.55 [-1.84, -1.25]) | 5.049 (-1.85 [-2.12, -1.58]) | 5.551 | 5.735 | 6.897 |
| Completions | 1,434 | 3.579 (-0.99 [-1.17, -0.81]) | 3.387 (-1.18 [-1.35, -1.01]) | 3.731 | 3.793 | 4.569 |
| Passing yards | 1,434 | 42.79 (-9.54 [-11.29, -7.60]) | 40.94 (-11.38 [-13.01, -9.57]) | 44.42 | 45.37 | 52.32 |
| Receiving yards | 13,309 | 9.644 (-0.454 [-0.513, -0.398]) | 9.647 (-0.451 [-0.516, -0.390]) | 9.688 | 10.079 | 10.098 |
| Receptions | 13,309 | 0.740 (-0.038 [-0.044, -0.033]) | 0.740 (same as X) | 0.773 | 0.740 | 0.778 |
| Rush attempts | 7,935 | 1.292 (-0.133 [-0.146, -0.120]) | 1.284 (-0.141 [-0.154, -0.128]) | 1.382 | 1.301 | 1.425 |
| Rushing yards | 7,935 | 7.585 (-0.557 [-0.613, -0.506]) | 7.543 (-0.599 [-0.663, -0.543]) | 7.678 | 8.082 | 8.142 |

Reading it honestly:
* The stacked models beat the simple baselines on every market with intervals that
  exclude zero, and the stacking gain over J0 seen in validation held out of sample.
* Baseline A is weak for quarterbacks (bias +1.9 attempts, +13.7 passing yards), so the
  large passing percentages partly reflect a weak baseline; the receiving and rushing gains
  (4-10%) are the more representative size of the improvement.
* For regular starting QBs (P(plays) >= 80%) the gain is much smaller: pass-attempt CRPS
  4.890 (X) vs 5.110 (A), about 4%. Most of the headline passing gain comes from reserve
  and uncertain QBs, where baseline A fails (`by_usage.csv`).
* Known weakness: receivers whose previous game was for another team are forecast slightly
  worse than baseline A on receiving yards (CRPS 6.92 vs 6.69, n = 325; `by_role_change.csv`).
  Shares are carried across teams too literally; a new-team prior is the next fix to test
  (on validation data only).
* Postseason games (n = 63 QB games) behave like the regular season (`by_season_type.csv`).
* X1 and J1 use injury reports and pre-2025 depth charts whose publication times are
  assumed, so their test advantage over X/J0 is not fully cutoff-faithful.
* These are statistical results only. Beating these baselines says nothing about beating
  bookmaker prices, which are far sharper; no price data exist here, so ROI is unavailable.

Calls (from `nfl-props-lab/`, `uv run python -c ...`):

```python
from npl import backtest, ml, evaluate
from npl.features import load_lake
lake = load_lake(); g = lake["games"]
val = sorted({(int(s), int(w)) for s, w in zip(g.season, g.week) if 2022 <= s <= 2023})
backtest.run(val, label="exp04-validation-v0.3-lowhistory-priors", n_sims=4000, seed=7, lake=lake)
ml.run_backtest(val, label="exp03-validation-ml", lake=lake)
backtest.run(val, label="exp05-validation-v0.3-stacking", n_sims=4000, seed=7, lake=lake,
             with_ml=True, mix_grid=(0.25, 0.5, 0.75))
evaluate.write_report("<run dir>", ["J1", "J0", "M", "X25", "X50", "X75", "A", "C"], baseline="A")
from npl import defense
defense.write_stability(defense.stability(lake, range(2012, 2022), range(2022, 2026)))
```

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
| 5 | exp05-validation-v0.3-stacking | J0, J1, M and linear pools X25/X50/X75 of J0 and M | see below; per-market weights frozen from this run |

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
```

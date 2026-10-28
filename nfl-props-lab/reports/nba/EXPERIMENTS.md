# NBA experiment log

Every NBA experiment, in order, including rejected ones. Tuning used regular-season games of
2019, 2022 and 2023 (every third calendar day); validation is every game of 2024 and 2025
(2023-24 and 2024-25, regular season, play-in and playoffs). Distributions are scored
conditional on playing; players without a previous NBA game (n = 205) and box scores with
team minutes under 235 (n = 44) are excluded and counted. Baselines: A = exponentially
weighted per-game average (half-life 10 games) with a negative binomial whose dispersion was
tuned like N1's; C = plain last-10 average ("L10"), the number most prop sites show.

| # | Label | What changed | Outcome |
|---|---|---|---|
| n00 | tuning (training seasons only) | half-lives, prior strength, dispersions, minutes spread, opponent and pace on/off | minutes half-life 3 games, rate half-life 15, pace and opponent each help slightly |
| n01 | n01-validation-N1 | first validation of N1 (normal minutes) | beats A on all markets (points -2.2%, PRA -3.2%) but **biased**: points +0.38, PRA +0.54. Cause: opponent and pace factors measured against the 2018-2023 league level (scoring has risen since) and minutes truncated at 1 for low-minute players. Not adopted. |
| n02 | n02-validation-N1-empirical-minutes | minutes from the empirical error distribution by expected-minutes band (fitted 2018-2023); opponent and pace relative to the league level at the time; dispersions re-tuned on training seasons | **Adopted as nba-0.1** (D-020). Bias points +0.03, PRA +0.00. CRPS v A: points -2.7% [-3.1, -2.3], rebounds -2.2%, assists -1.7%, threes -1.1%, PRA -3.8%; all week-block 95% intervals exclude zero. C (L10 average) is worst on every market. P(plays) from recent appearances: Brier 0.128 v 0.168 for the share of the last 10 games played. |

| n03 | **n03-TEST-frozen-nba-0.1** | single run of the frozen configuration (commit f9c3116, clean) on the untouched 2026 season (2025-26), 28,303 player-games | see below; run once, nothing changed afterwards |

## Test result (2025-26, frozen nba-0.1)

Mean CRPS conditional on playing (lower is better); in brackets the 95% interval for N1
minus A from resampling whole weeks.

| Market | N1 | A (recent average) | C (last-10 average) | N1 v A |
|---|---|---|---|---|
| Points | 3.237 | 3.334 | 3.371 | -2.9% [-3.4, -2.4] |
| Rebounds | 1.300 | 1.332 | 1.359 | -2.4% [-2.9, -1.9] |
| Assists | 0.907 | 0.925 | 0.943 | -1.9% [-2.3, -1.6] |
| 3-pointers made | 0.581 | 0.588 | 0.602 | -1.2% [-1.5, -0.9] |
| Pts + Reb + Ast | 4.277 | 4.460 | 4.493 | -4.1% [-4.9, -3.4] |

P(plays): Brier 0.132 (share of last 10 games played: 0.174). Bias of N1's mean: points
-0.11, PRA -0.11, rebounds +0.03.

Data note (28 Oct, after n03): one 2025-26 season file stores athlete ids as floats, and
33 of its rows have no id; those 33 rows were grouped as a single "nan" player in n01-n03
(0.1% of 2025-26, every other id consistent across seasons). Ids are now normalised and
id-less rows dropped. The effect on the reported numbers is negligible, so n03 stands.

Reading it honestly:
* The gains are modest. An NBA player's recent average is already a strong forecast; most
  of N1's edge comes from modelling minutes and their uncertainty.
* At a half-point line set near the forecast mean, N1's over/under log loss is only
  slightly below a coin flip (points 0.689 v 0.693). Lines a bookmaker sets near the right
  number leave little room, and no claim about beating them is made.
* Points and PRA still have heavier tails than forecast (about 16% of outcomes in the
  lowest tenth): short nights from early injuries, blowouts and foul trouble.
* No injury information is used in these backtests (none exists with publication times
  before Sep 2026). Live forecasts use the daily injury snapshot; that effect can only be
  judged prospectively.

Calls (from `nfl-props-lab/`):

```python
from npl.nba import source, canon, backtest as B, model as M
from npl import db
source.fetch(db.connect()); canon.build()
x, lg = B.frame(M.load_params())
B.run(B.VALIDATION, "n02-validation-N1-empirical-minutes", p=M.load_params(), x=x, lg=lg)
```

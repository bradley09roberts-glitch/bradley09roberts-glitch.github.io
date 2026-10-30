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
| n04 | n04-validation-blowout | blowout risk: expected margin from team form (EW point differential, half-life 15, +/-2 home court) moves the minutes forecast. S = shift fitted by least squares; R = minutes error distribution by expected-minutes band and margin class (big underdog / close / big favourite at +/-7). Both fitted 2018-2023, scored against frozen nba-0.1 on the same 56,047 rows | **Not adopted** (D-024). Both slightly worse: S points +0.15% [+0.08, +0.21], PRA +0.17% [+0.09, +0.25], assists +0.07%; R points +0.09% [+0.04, +0.13], PRA +0.11% [+0.05, +0.17]; rebounds and threes unchanged within noise. Worse again in big-margin games (27% of rows). |
| n05 | n05 / n05b absent-teammate hand-out (validation) | when a regular (15+ expected minutes) is out, hand a share alpha of his minutes not yet absorbed by recent form (0.5 ** games missed / 3) to teammates, by expected minutes (n05) or towards rotation players and his position (n05b), plus a per-minute usage boost gamma; absences treated as known before tip; scored on the 67,888 (2021-2023) and 45,929 (2024-2025) player-games in team-games with an absent regular | **Not adopted** (D-034). Usage boosts and full hand-outs worse everywhere (alpha 1: points +0.9%, rebounds +2.1% on validation). Best, half the minutes by expected minutes: validation points -0.58% [-1.09, -0.04], assists -0.46%, threes -0.41%, rebounds +0.15% (n.s.), PRA -0.58% (n.s.); on 2021-2023 rebounds +0.32% [+0.01, +0.63]. Reason found: N1's minutes depend on roster context both ways. With every regular available it over-predicts minutes by 1.8 and points by 1.0 (recent games mixed in absences); with a regular out it is about right (+0.35 min). A one-sided hand-out overshoots. Scripts and numbers: `reports/nba/experiments/n05_absence.py`, `n05b_absence_shape.py`, `n05_summary.json`, `n05b_summary.json`. |
| n06 | n06 zone matchup (validation, test of a feature, not a model change) | where a player shoots (8 court zones, his previous games, half-life 40) against the opponent's field-goal % allowed in those zones (previous team games, half-life 20, shrunk with 150 attempts), relative to an average player's shot mix; checked against N1's points misses on 21,958 player-games of 2024-2025 | **Not used** (D-035). Small (index s.d. 1.5% of points per shot, about 0.2 points a game) and pointing the wrong way: slope of misses on the index -0.99 [-1.60, -0.33]; players in the best-matchup fifth scored 0.08 below N1, the worst fifth 0.41 above. Defences' shooting % allowed by zone is mostly noise. `reports/nba/experiments/n06_zone_matchup.py`, `n06_summary.json`. |
| n07 | n07 lineup-aware minutes (validation) | expected minutes move with teammate minutes missing tonight minus those missing in the games behind his recent-form minutes (same team, players listed this season, all / same position group), pooled least squares on 2018-2023 (5 coefficients), minutes residual distributions refitted; judged on all 56,047 player-games of 2024-2025 | **Adopted as nba-0.2** (D-036). Absences known before tip: minutes error 5.00 -> 4.71; CRPS points -1.70% [-2.12, -1.37], rebounds -1.66%, assists -1.17%, threes -0.75%, PRA -2.51% [-3.11, -2.05]. Pessimistic (a player's first missed game a surprise): points -0.47% [-0.57, -0.37], rebounds -0.42%, assists -0.34%, threes -0.15%, PRA -0.69%. Absences guessed from recent appearances alone: about -0.05% (n.s.), so the gain comes from knowing who is out (live: ESPN injury report). Full-roster minutes bias -1.4 -> -0.1. `reports/nba/experiments/n07_lineup_minutes.py`, `n07_summary.json`, `n07_summary_pessimistic.json`. |

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

## Queued experiments (not adopted; each must improve 2024-25 validation before use)

- Lineup-aware usage (after n07): minutes now react to who is in or out; shots per minute do not yet (n05's one-sided usage boost failed).

1. Season-start minutes. Early in a season the exponentially weighted minutes still carry
   last season's (and playoff) minutes; on 28 Oct 2026 this put Jaylon Tyson at 16.1
   expected minutes against 21.7 this season and Jeremiah Fears at 28.8 against 22.7.
   Try down-weighting games from the previous season once a player has 2-3 games in the
   new one.
2. Blowout risk from the expected margin (suggested by the user, 29 Oct). **Tested as n04 on
   29 Oct: not adopted** (D-024). The minutes effect is real, but the model already gets
   these games right in total. On validation (2024-2025, starters expected to play 28+
   minutes):

   | margin class | rows | minutes v forecast | points per minute, actual | model rate |
   |---|---:|---:|---:|---:|
   | big favourite (+7 or more) | 3,867 | -0.97 | 0.581 | 0.563 |
   | close | 12,074 | -0.26 | 0.543 | 0.539 |
   | big underdog (-7 or less) | 3,525 | -0.67 | 0.514 | 0.506 |

   Big favourites' starters lose about 0.7 minutes more than in close games but score
   about 2.5% more per minute (weaker opponents; the opponent factor is heavily shrunk), so
   their totals come out as forecast. Bench players gain minutes in lopsided games (+1.1
   for big underdogs, +0.4 in close games), which the band errors already absorb on
   average. Cutting minutes alone over-corrects. A joint minutes-and-rate term might not;
   it would be a new experiment with little room to gain, since N1's bias in big-margin
   games is already small (points -0.03, PRA -0.10). Script:
   `reports/nba/experiments/n04_blowout.py`; numbers:
   `reports/nba/backtests/n04-validation-blowout/summary.json`.

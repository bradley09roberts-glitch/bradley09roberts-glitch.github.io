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
| n08 | n08 simple baseline S (validation + three nights of lines) | S = season average (blended with last season as 5 games) x opponent allowed per game relative to the league (shrunk, 10 games) x rest factor (days off 0/1/2/3+, fitted 2018-2023) x minutes trend (last 5 / season, 0.75-1.25); a line-gap rule bets the side S favours when it beats the line by g | **Kept as a display and tracking list, not a pick rule** (D-038). Point accuracy on 56,010 player-games of 2024-2025: mean absolute error points S 4.609, N1 4.587, A 4.618; PRA 6.117 / 6.075 / 6.150; rebounds, assists between N1 and A; threes S 0.873, N1 0.874. Against 957 eligible props with US lines (28-30 Oct): the gap rule won 49.5-51.1% at every gap (0.5 to 2 points), -10 to -38 units at best US prices; the pick rules 84 of 144 (58.3%, +16.2); every prop on N1's side 518 of 957 (54.1%, +34.0); pick rules where S agrees 54 of 101 (53.5%, -1.9). Back-to-back factor for points 1.05 (players who play the second night score slightly more than projected). `src/npl/nba/simple.py`, `reports/nba/experiments/n08_simple_baseline.py`, `n08_summary.json`. |
| n09 | n09 player results v top-10 defences relative to projection (descriptive; regular season 2024-2027 ledgers; independently verified) | opponent's season-to-date allowed per 100 possessions in that stat (shrunk 10 games) ranked before tip; residual = actual - N1 mean; does a player's top-10-minus-rest residual persist across seasons? | **Not used per player** (D-039). Persistence 2024 v 2025: pts 0.018, reb 0.006, ast -0.004, threes 0.025, PRA 0.002 (330 players); differential split-half 0.01-0.06. Variance components (independent recompute): persistent spread at most about 0.25 points (upper 0.46), so a shrunk adjustment is about 0.1-0.3 points. N1 removes about 81% of the defence gradient a season average leaves (points residual best-5 defences +0.04 v worst-5 +0.21; season-to-date average +0.04 v +0.93). Hypothesis to test out of sample: top-10%-projected scorers hold up better v top-10 points defences than N1 expects (+0.59 [0.15, 1.04], found after the fact). Playoffs: N1 about 1 point high on points (separate issue). `reports/nba/experiments/n09_vs_top_defence.py`, `n09_summary.json`. |
| n10 | n10 N1 v real bookmaker lines (2024-25 full season; 2025-26 sample; independently verified by four separate checks) | The Odds API historical snapshots 3 h before tip, US books, main lines; consensus line, margin-free over chance; frozen nba-0.1 forecasts for players who played; flat stakes at the best US price and at BetMGM | **No edge** (D-041). 2024-25, 71,922 props / 1,230 games: log loss market 0.6856 v N1 0.7052 (difference CI [0.016, 0.023]); best weight on N1's deviation 0.08 (blend gain about 0.0002). Pick band 10-15: 8,972 bets, 51.2%, ROI -2.2% at the best price (week-block CI [-4.3, -0.2]); every gap band -0.7% to -2.8%; BetMGM-only -2.3% to -5.0%; PRA -7.6%. 2025-26 sample, 18,753 props: log loss 0.6872 v 0.7058; pick band -0.9%; PRA -11%. Holdout search (1,182 rules defined before 1 Feb 2025): top 5 lost -2.9% after; nothing significant. N1 is biased low on props (mean P(over) 0.459 v 0.489 actual) and overconfident (calibration slope 0.30), so 80-86% of its big-gap picks are unders. nba-0.2 narrows the gap but its frozen version leaks (observed absences include coach's-decision DNPs that track the final margin); refitted on pre-game-knowable absences it closes 27% of the gap, pick band +0.4% [-1.8, +2.5]. Scripts: `reports/nba/experiments/n10_hist_lines.py`; aggregates `n10_summary_2025.json`, `n10_summary_2026.json`. |
| n11 | n11 fixes for the low-over bias and overconfidence (real lines; fit 2024-25, one test on the 2025-26 sample; NFL fit weeks 1-11 / test 12-22 of 2023) | four families run independently: post-hoc calibration (12 variants), N1 distribution changes (215), market-anchored stack (64), NFL (21); one frozen betting rule (EV >= 2%) at the best US price and at BetMGM | **Bias and overconfidence fixed; no edge** (D-043). NBA calibration (per-market logistic): mean P(over) 0.459 -> 0.490 (actual 0.485), slope 0.29 -> 0.97, log loss 0.7058 -> 0.6879 v market 0.6872 (CI of difference [-0.0005, +0.0019]). Calibrated slopes show the model's view carries almost nothing for points (0.06) and PRA (0.01), some for rebounds, assists, threes (0.39, 0.42, 0.73). Distribution fixes only work at unrealistic widths. Market-anchored stack (95% market): log loss 0.68695 v 0.68723 (CI includes 0); EV rule +4.1% [-2.8, +10.4] best price, +9.1% [-4.4, +22.5] at BetMGM on 263 bets: not significant; tracked on paper. NFL: X1 calibrated 0.694 v market 0.689; every NFL rule lost (best price -3% to -7%, CIs below zero). Nothing claimed an out-of-sample edge, so no verification was needed. `reports/nba/experiments/n11_fixes/`. |
| n12 | n12 audit (stage 2: each fix tested on the real lines, fit 2024-25 / one test on the 2025-26 sample, then checked by an independent verifier; reports/AUDIT_2026-11.md) | consensus rebuilt (one vote per book at its main line, duplicates and conflicting books dropped, regulated best prices, BetMGM only at its own line); the tracked rule backtested as coded; N1 fixes (fractional minutes centre, line-player rate factors, NB1 points, league drift, minutes-rate link, dispersions, opponent weights, healthy minutes table); absences from the official NBA injury reports published before the snapshot; market-only rules R1-R6 | **nba-0.3 adopted; BetMGM-only tracked rule; no edge** (D-044). nba-0.3 v nba-0.1 on the 2025-26 sample: raw log loss 0.7058 -> 0.7027 (-0.0031 [-0.0046, -0.0017]), mean P(over) - actual -0.026 -> -0.003, points CRPS -0.8%; after calibration 0.68815 -> 0.68803, anchored stack v market -0.00028 [-0.00059, +0.00004]. Injury-report absences (L25): raw -0.0052 [-0.0090, -0.0022] but nothing after calibration or v the market; not adopted. Tracked rule as coded (nba-0.3): 686 bets +8.6% [+0.9, +15.8] (2024-25, in-sample) and 220 bets +11.5% [-1.4, +23.6] (2025-26, examined before); the old fallback leg lost at regulated prices in both seasons. Market-only rules: none significant after Holm (smallest adjusted p 0.41); White Reality Check p 0.40 (fit) and 0.84 (31 rule-thresholds on 2025-26). `reports/nba/experiments/n12_audit/` (aggregates only). |

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

- Star scorers v top points defences (after n09): fix tiers on 2024-25, test on 2025-26, then track live; until then a top defence is not a reason for points unders on top-10%-projected scorers.
- N1 points level in 2026-27: the live ledger runs about 1 point high on points so far (704 player-games); recheck after two weeks.
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

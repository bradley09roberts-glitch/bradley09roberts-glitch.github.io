# Machine learning that keeps learning

_Written 22 October 2026. Every number comes from the walk-forward harness
(`footy.modeling.walkforward`, `footy.modeling.runner`) or from replays of it on recorded
out-of-sample forecasts, five leagues pooled, D-025 composite (lower is better; the frequency
baseline scores 1)._

## What was asked, and where we started

The owner asked to "add machine learning so it learns stuff and hopefully gets better".

- **Every model here already learns from results.** Each one is refitted at every decision
  batch (about twice a week) on all results up to that day, so it updates as the season goes on.
- **The project already had a machine-learning family**, `ml_gbm`: Poisson gradient boosting
  and stacking on leakage-safe engineered features (rolling shots on target, corners, rest
  days, Elo and more). Its best variant scored 0.9564 on validation and lost to the models of
  record.
- **What was missing** is a learner that sits on top of the models and learns from their own
  track record: which model to trust, and how to correct the combination of models and market,
  updated after every match.

Targets (D-026, D-027):

| Fixtures | Model of record | Validation 2023/24 | Test 2024/25 + 2025/26 |
| --- | --- | --- | --- |
| With prices | `market_tilt_shots` | 0.9449 | 0.9555 |
| Without prices | `congestion_context_on_elo` | 0.9517 | 0.9661 |

## What was built

### 1. An out-of-sample record of every model's forecasts (`footy.modeling.lab.ml_stream`)

A learner that combines models must learn from what those models said *before* each match,
never from their in-sample fits. `MemberStream` produces that inside a walk-forward run.

- **Each match gets its members' forecasts as at its own decision time** (00:00 UK on its D-012
  collection day). The members are fitted only on matches dated before that day, exactly as the
  runner would fit them.
- **Warm-up.** On its first fit the stream walks forward through the last 365 (or 730) days of
  the history the runner passes, one decision batch at a time. The runner passes a three-year
  window, so the members always have at least one year of history.
- **Both divisions.** The runner loads a league with its second tier, so the learner also
  learns from second-tier matches (about 60% more matches).
- **Prices.** For the model + market candidates each member's grid is also tilted onto the
  pre-closing consensus by `MarketTilt`'s own code (weights 1 / 1), so the same availability
  rules apply: a price is used only if it was quoted by the match's collection deadline and
  before kickoff. Closing prices are never read.
- **Engineered features** come from `ml_gbm.FeatureScan`, computed as at each decision day.

On 2022/23 the recorded forecasts reproduce the saved ledgers of `shots_xg_sot_joint_rw` and
`dixon_coles` to within 0.0002 (warm starts differ), and the replayed composites match the
documented ones: shots + market 0.9608, Dixon-Coles + market 0.9615, Dixon-Coles 0.9744.

### 2. Online learning: mixture weights updated after every match (`ml_online`)

The forecast is one mixture of the members' score grids, `sum_k w_k grid_k`. A mixture of
score distributions is itself a score distribution, so every market stays coherent.

- **The weights learn after every settled match.** Each match moves them by one
  exponentiated-gradient step (EG, the Hedge / multiplicative-weights update for a convex loss)
  on that match's D-025 composite loss: 1X2 RPS and the log losses of O/U 1.5, 2.5, 3.5 and
  BTTS, each divided by the frequency baseline's training-season mean.
- **Why EG.** The loss is convex in the weights, so EG tracks the best fixed mixture in
  hindsight, and it reacts when the members' relative form changes.
- **Coverage.** A match updates the weights only once it is final and in the history passed to
  fit, and only if both teams have three matches of history (the runner's own rule).
- **Options searched.** Pooled weights (one vector for the league and its second tier) or one
  vector per division, and a fixed share that keeps every member's weight above a floor.
- **With prices,** the members' grids are first tilted onto the pre-closing consensus. Every
  member then has the market's 1X2 and O/U 2.5, and the weights choose the grid's shape
  (O/U 1.5 and 3.5, BTTS and the scorelines).

### 3. A learned meta-model on top of models and market (`ml_stack`)

For each market a ridge-regularised logistic model learns to correct a reference forecast:

- 1X2 by multinomial logistic;
- O/U 1.5, 2.5, 3.5 and BTTS by binary logistic;
- each with the reference's log probabilities (or log odds) as offset.

With every coefficient at zero the forecast is the reference, and the ridge pulls towards it.

- **Inputs.** The reference's own log odds (so it can sharpen or flatten them), each other
  member's disagreement with the reference and, with prices, how far the reference model was
  from the market before the tilt. Optionally ml_gbm's engineered team features (14 of them).
- **Reference.** With prices, `market_tilt_shots` itself. Without prices,
  `congestion_context_on_elo`.
- **Training.** Every recorded match final before the cutoff (warm-up included), refitted at
  every decision batch.
- **One grid.** The six learned probabilities (home, draw, over 1.5, 2.5 and 3.5, BTTS) are
  made one coherent score grid by tilting the reference grid onto all of them at once
  (`ml_stream.tilt_markets`). This is the minimum-information change, as MarketTilt makes for
  three of them, solved by damped Newton on the dual over the 15 indicator patterns. Where the
  six targets can't all be reached, the reference grid is kept.
- **Solver.** The ridge fits use an exact Newton solver. An earlier L-BFGS version gave the same
  numbers to six decimals in the replays, but was slow when BLAS threads competed.

## Tuning: training seasons only

All tuning used 2021/22 and 2022/23, never 2023/24 or later.

- **Recording.** For each of the five leagues, the stream was driven exactly as the runner
  drives a model over a training season, with a 730-day warm-up, and every record was saved
  (in the scratchpad, never in the repository).
- **Replay.** Each setting was then replayed walk-forward on those records.
- **Same code.** The replay calls the modules' own update and fit functions. Run through the
  real runner on Premier League 2022/23, the four registered candidates reproduce the replay:
  - the stackers fixture for fixture, exactly;
  - the mixtures to within 0.0001 per fixture, from warm starts after a different warm-up.

How many settings were tried, so the selection's optimism can be judged:

| Family | Settings on 2022/23 | Also on 2021/22 | Searched over |
| --- | ---: | ---: | --- |
| `ml_online` | 600 (896 replays) | 168 | six member sets, eta 0.003–100, share 0 / 0.002 / 0.01, pooled or per division, warm-up 365 / 730 |
| `ml_stack` | 102 (156 replays) | 54 | ridge 0.001–1e6, features none / 14, disagreement per member or pooled, decay 0 / 0.001, five or four markets, warm-up 365 / 730 |

What the search found:

- **Light ridge overfits badly.** With ridge 0.001 to 0.1 and a one-season warm-up, the stacker
  scored 0.962–0.986 on 2022/23 with prices (its reference: 0.9608) and 0.968–0.992 without
  (reference 0.9668).
- **Engineered features always hurt,** by 0.004 to 0.018 at the same settings. With about
  2,000 training matches only a handful of inputs can be learned.
- **The mixture's curves are flat around the chosen learning rate** (with prices eta 10–100,
  without prices 0.2–0.5). Per-division weights and the fixed share never helped.

Chosen settings (the four registered candidates) and their training-season scores:

| Candidate | Setting | 2021/22 | 2022/23 | Reference 2021/22 | Reference 2022/23 |
| --- | --- | ---: | ---: | ---: | ---: |
| `ml_online_market` | shots, ctx, GLM booster (tilted), eta 20, pooled | 0.95506 | 0.96014 | 0.95568 | 0.96077 |
| `ml_stack_market` | ref shots + market; ctx and GLM booster; ridge 1; decay 0.001; warm-up 730 | 0.95581 | 0.96027 | 0.95568 | 0.96077 |
| `ml_online_pure` | shots, ctx, Dixon-Coles, GLM booster, eta 0.3, pooled | 0.96067 | 0.96532 | 0.96349 | 0.96677 |
| `ml_stack_pure` | ref ctx; shots and GLM booster; ridge 0.3; warm-up 730 | 0.96258 | 0.96579 | 0.96349 | 0.96677 |

The reference columns are `market_tilt_shots` (with prices) and `congestion_context_on_elo`
(without), replayed on the same fixtures.

- **The stackers are the best of their search** by the mean of the two seasons.
- **The mixtures are near-ties, not the best.** In the last mixture grid, with prices, all six
  members at eta 100 or 50 had a lower two-season mean (0.95759) than the chosen setting
  (0.95760). Without prices, eta 0.5 was ahead of 0.3 by 0.00001. The setting chosen is within
  0.00002 of the best in both cases, far inside the noise. No validation data was involved:
  the recorded forecasts end on 1 July 2023.
- **The members' own settings were not all chosen before these seasons.** Their fitted
  parameters are out of sample for every record, but some of their settings are not:
  - the shots model's settings were chosen on 2022/23, as were MarketTilt's weights;
  - `congestion_context_on_elo`'s base (`elo_goals_gd`) was tuned on the mean of 2022/23
    and 2023/24 (D-027).

  So on 2022/23 these members look better than they would out of sample, and on validation
  ctx does too. A learner that trusts whichever member has done best recently inherits that,
  and the search could have favoured ctx for the same reason.

What the weights learned on the training seasons (average over the season's forecasts):

| Mixture | Season | Shots | Ctx | Dixon-Coles | GLM booster |
| --- | --- | ---: | ---: | ---: | ---: |
| With prices | 2021/22 | 0.07 | 0.52 | – | 0.41 |
| With prices | 2022/23 | 0.00 | 0.38 | – | 0.62 |
| Without prices | 2021/22 | 0.23 | 0.34 | 0.10 | 0.34 |
| Without prices | 2022/23 | 0.06 | 0.38 | 0.02 | 0.54 |

With prices the learning rate is high (eta 20), so the weights swing towards whichever tilted
grid has had the better recent shape. In 2021/22 the shots model's weight ranged from 0 to 1.

## Validation 2023/24: four candidates, one run

The four registered candidates and both targets were run once through the runner (`run_models`,
five leagues, 1,752 fixtures; ledgers in `data/backtests/ml_*__validation__2324`, table in
`data/lab/ml_validation__2324.json`). The re-run targets reproduce their saved ledgers exactly
(largest difference 2e-16), so today's data gives the same evidence as before.

| Candidate | Composite | Target | Target composite | Difference (95% CI) | Test run |
| --- | ---: | --- | ---: | --- | --- |
| `ml_online_market` | **0.94455** | `market_tilt_shots` | 0.94488 | −0.00033 (−0.00116 to +0.00055) | yes: selected |
| `ml_stack_market` | 0.94484 | `market_tilt_shots` | 0.94488 | −0.00003 (−0.00130 to +0.00120) | no: not the lowest (D-025 tests only the selected candidate) |
| `ml_stack_pure` | 0.95386 | `congestion_context_on_elo` | 0.95174 | +0.00212 (+0.00010 to +0.00402) | no: worse than its target |
| `ml_online_pure` | 0.95421 | `congestion_context_on_elo` | 0.95174 | +0.00247 (−0.00013 to +0.00502) | no: worse than its target |

Intervals are the repository's block bootstrap (`metrics.block_bootstrap_ci`, 2,000
resamples of whole decision batches) on the paired per-fixture difference of composite
contributions (each fixture's mean of metric ÷ baseline mean).

| Validation 2023/24 | RPS | LL O/U 1.5 | LL O/U 2.5 | LL O/U 3.5 | LL BTTS | Acc 1X2 | Acc BTTS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `ml_online_market` | 0.1874 | 0.5028 | 0.6615 | 0.6095 | 0.6757 | 55.0% | 57.0% |
| `ml_stack_market` | 0.1876 | 0.5026 | 0.6614 | 0.6100 | 0.6761 | 55.3% | 56.9% |
| `market_tilt_shots` | 0.1875 | 0.5029 | 0.6614 | 0.6096 | 0.6764 | 55.0% | 56.6% |
| `congestion_context_on_elo` | 0.1929 | 0.5047 | 0.6646 | 0.6101 | 0.6776 | 53.6% | 57.2% |
| `ml_stack_pure` | 0.1934 | 0.5060 | 0.6661 | 0.6117 | 0.6784 | 53.5% | 57.2% |
| `ml_online_pure` | 0.1930 | 0.5067 | 0.6668 | 0.6123 | 0.6783 | 53.6% | 56.7% |

- **With prices, the gain is in the grid's shape,** as expected: 1X2 and O/U 2.5 are the
  market's for every priced fixture. `ml_online_market` gains 0.0007 in BTTS log loss and
  0.0002 in O/U 3.5. By league it is better in the Premier League, La Liga and Ligue 1, and
  worse in Serie A and (by 0.0002) the Bundesliga.
- **One validation fixture had no usable prices:** Newcastle v Nottingham Forest on 26 Dec
  2023 kicked off at 12:30 UTC, before the 13:00 collection deadline. It is the only reason the
  two models' 1X2 scores differ on validation. Its share of the composite difference is
  +0.000006.
- **All four candidates use ctx,** whose base was tuned partly on 2023/24 (D-027). Their
  validation scores share some of ctx's flattering, not only their pure target's.
  `ml_online_market` gave ctx 0.46 of its weight on validation (0.33 on test).
- **Without prices both learners lose to `congestion_context_on_elo`,** mainly on the totals:
  +0.0013 to +0.0023 in each O/U log loss. That target's base was partly tuned on 2023/24 (D-027),
  so its validation score is flattering. But the learners also lost in 8 of 11 months, so this
  is not one bad patch.
- **The stackers' six-market tilt never had to fall back:** every learned set of
  probabilities was reached exactly, in every league.

What `ml_online_market` actually learned is to choose a shape. Its learning rate is high, so
in most weeks one tilted member holds nearly all the weight:

| Average weight (five leagues) | Shots + market | Ctx + market | GLM booster + market |
| --- | ---: | ---: | ---: |
| Validation 2023/24 | 0.01 | 0.46 | 0.53 |
| Test 2024/25 + 2025/26 | 0.12 | 0.33 | 0.55 |

It hardly ever picked the shape of the model of record. Ctx's shape is the one
`market_tilt_ctx` used: that model won validation in round 4 and then lost on test (D-027).

## Test 2024/25 + 2025/26: one run, `ml_online_market` only

Run once, with nothing changed after validation (`data/backtests/ml_online_market__test__2425-2526`,
`data/lab/ml_test__2425-2526.json`). The re-run target reproduces its saved test ledger exactly.

| Test (3,503 fixtures) | RPS | LL O/U 1.5 | LL O/U 2.5 | LL O/U 3.5 | LL BTTS | Composite | Acc 1X2 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `market_tilt_shots` (model of record) | 0.1959 | 0.5256 | 0.6725 | 0.5962 | 0.6840 | **0.95546** | 53.4% |
| `ml_online_market` | 0.1959 | 0.5257 | 0.6725 | 0.5965 | 0.6844 | 0.95574 | 53.4% |

- **Difference: +0.00028 (95% CI −0.00024 to +0.00079): worse,** though not significantly.
  RPS and O/U 2.5 are identical (every test fixture had prices). It loses on O/U 1.5, O/U 3.5
  and BTTS.
- **By season:** 2024/25 0.9504 against 0.9498 (worse); 2025/26 0.9612 against 0.9612 (level).
- **By league:** slightly better in the Premier League (0.9660 against 0.9664) and Ligue 1
  (0.9595 against 0.9596), worse in La Liga, Serie A and the Bundesliga.

Under D-027 a challenger must be at least as good on test, so it does not replace
`market_tilt_shots`.

The test seasons were run twice more, with nothing changed and nothing chosen from them:

- once to read `ml_online_market`'s weights for the table above;
- once by the audit, on the Bundesliga, which gave forecasts identical to the saved ledger.

## Learning curve (`data/reports/ml_learning_curve.json`)

For a future website panel: month by month over validation and test, the composite of each
ML candidate and of its target, monthly and cumulative, on the fixtures both predicted that
month.

- **Test months exist only for the one candidate that had its test run.** The protocol does
  not allow the others a test run, so their test months are left out rather than computed.
  The targets' test months come from their saved ledgers.
- `ml_online_market` was better than `market_tilt_shots` in 7 of 11 validation months and 9 of
  20 test months.
- **Its cumulative gap narrows as the run goes on.** On test it is +0.0026 after August 2024
  and +0.0003 at the end. Both splits start badly in August.
- **This pattern was noticed after the test run.** It is a lead for future work on the training
  seasons, not evidence, and nothing was changed because of it.

A website summary of this note is in `data/reports/ml_selection.json` (metrics only; no prices,
D-013 / D-024).

### For the website

| Request | What it gives |
| --- | --- |
| `GET /api/reports/ml_learning_curve` | This file as saved: per candidate, monthly and cumulative composites of the candidate and its target |
| `GET /api/reports/ml_selection` | The saved summary: validation and test tables, paired differences with 95% intervals |
| `GET /api/ml/summary` | D-028 in short (`footy.ml_decision`): each candidate's target, validation and test composites against its target's, what happened to it, the verdict (`"no switch"`) and why |

`/api/ml/summary` reads every number from `ml_selection.json`; only the candidates' roles and
the reasons that aren't numbers are D-028's words. The static snapshot carries all three.

```json
{"decision": "D-028", "status": "proposed", "verdict": "no switch", "switch_to": [],
 "models_of_record": {"with_prices": "market_tilt_shots", "without_prices": "congestion_context_on_elo"},
 "candidates": [{"model": "ml_online_market", "fixtures_with_prices": true, "target": "market_tilt_shots",
   "validation": {"fixtures": 1752, "composite": 0.944549, "target_composite": 0.944876,
                  "difference": -0.000327, "difference_ci95": [-0.001157, 0.000546], "beat_target": true},
   "test": {"run": true, "fixtures": 3503, "composite": 0.955744, "target_composite": 0.955464,
            "difference": 0.00028, "difference_ci95": [-0.000241, 0.00079], "at_least_as_good": false},
   "outcome": "not adopted: worse than its target on test (D-027)"}, "..."],
 "why": ["ml_online_market beat market_tilt_shots on validation (0.94455 against 0.94488) and was the one candidate tested; on test it scored 0.95574 against 0.95546, so under D-027 it does not replace it.", "..."],
 "sources": {"selection": "/api/reports/ml_selection", "learning_curve": "/api/reports/ml_learning_curve",
             "write_up": "docs/research/machine-learning.md"}}
```

## Leakage

- **Shared test.** Every registered candidate, the four new ones included, passes the
  repository's test (`tests/modeling/test_no_future_leakage.py`): scrambling all results from a
  date on leaves every earlier forecast identical.
- **New tests on synthetic matches and synthetic prices:**
  - `tests/modeling/test_ml_stream.py`:
    - every recorded forecast was made by members fitted only on matches before the record's
      own decision day, at its own cutoff, and `predict()` never receives a result column;
    - scrambling later results leaves earlier records identical;
    - a history that reaches the cutoff day is refused;
    - a price quoted one minute after its deadline is not used, and that fixture keeps its own
      grid;
    - a record's engineered features equal a fresh scan of the history before its decision day.
  - `tests/modeling/test_ml_online.py` and `test_ml_stack.py`: with prices, scrambling every
    result **and every price** from a split date on leaves every earlier forecast identical,
    while the prices are really used.
- **The learners only ever train on the stream's records.** Each record is a match final before
  the cutoff, forecast at its own decision time.
- **Fixed in the audit: weights carried back in time.** When a mixture is fitted at an earlier
  cutoff than its last fit, its stream starts again, but its weights used to stay as learned
  from the later matches. The runner and `footy.live` only move forward (live builds a new model
  each run), so no reported number was affected. The weights now start again with the stream.
  `test_weights_learned_later_are_forgotten_when_time_goes_backwards` failed before the fix.

## Runtime and cost

All runs used two worker processes with one BLAS thread each (`runner.limit_threads`).

| Run | Time |
| --- | --- |
| Recording the training-season forecasts (six members, prices and features) | 105–195 s per league and season; 10 recordings |
| Replaying about 700 settings on recorded forecasts | under half an hour in total, one core |
| Validation: four candidates, two targets and the baseline, five leagues | 291 s |
| Test: one candidate, the target and the baseline, two seasons | 123 s |
| A live fit (warm-up included) | not measured; a one-season backtest including the warm-up took 30–52 s per league |

## Conclusion

**Recommendation: no switch.** `market_tilt_shots` stays the model of record for fixtures
with prices, and `congestion_context_on_elo` for fixtures without (D-028, proposed).

- **The learning works as designed.** It learns only from forecasts made before each match, it
  updates after every result, and on the two training seasons it gained steadily:
  - with prices, −0.0006 against the model of record in both seasons;
  - without prices, −0.0015 to −0.0028 against the fallback.
- **Out of sample the gain with prices did not hold.** It was −0.0003 on validation and +0.0003
  on test, both within noise. Once the market fixes 1X2 and O/U 2.5, all that is left to learn
  is the grid's shape (O/U 1.5 and 3.5, BTTS). The members' shapes are too similar for the
  choice between them to matter reliably, and the shape of the shots model, which the online
  learner almost never chose, did best on test.
- **Without prices the learners lost on validation.** Their target's validation score is
  flattering (D-027), but they also lost in most months.
- **More data, not more learners, is what could move this.**
  - Historical prices for O/U 1.5, 3.5 and BTTS would give the shape markets a market of their
    own. None are available (D-015).
  - Lineups and injuries are also missing (STATUS).
  - Members whose shapes differ more would help too: the bivariate and dispersion family was
    recorded, but the search did not choose it.

## Audit (22 October 2026)

An independent check of this note against the code, the scratch scripts and the saved ledgers.
The recommendation stands.

- **Reproduction.** Through the real runner, with today's code (including the fix above):
  - `ml_online_market`, validation, all five leagues (1,752 fixtures), matched the saved
    ledger in every probability and score (largest difference 4e-16). Its average weights
    were those in the table above.
  - `ml_stack_market`, validation, Bundesliga (306 fixtures): identical, with no fallback of the
    six-market tilt.
  - `ml_online_market`, test, Bundesliga (611 fixtures): identical.
  - On Premier League 2022/23, the runner and the replay agree exactly for the stackers and to
    within 0.00008 per fixture for the mixtures. The recorded forecasts match the saved
    shots and Dixon-Coles ledgers to within 0.00004 and 0.00018.
- **Coherence.** Every market of every forecast is read off one score grid: a mixture of grids,
  or a tilt of the reference grid. The runner refuses any batch whose grid does not sum to 1
  (within 1e-6). The saved ML ledgers have no gaps, 1X2 sums within 7e-16 of 1, over-lines that
  never increase, and BTTS never above over 1.5.
- **Leakage.** None found in the reported runs:
  - prices are pre-closing only, quoted by each match's deadline and before kickoff, and
    published on its collection day;
  - recorded matches carry their own deadline, so a live run's decision time never applies to
    them;
  - the weights and meta-models learn only from matches in the history the runner passes, which
    is final before each cutoff.

  One latent leak was fixed (see Leakage).
- **Protocol.**
  - Every setting came from 2021/22 and 2022/23: the recorded forecasts end on 1 July 2023.
  - Four named candidates had one validation run, and only `ml_online_market` was run on test
    for the evidence. The two later re-runs are listed under Test.
  - Three things are added as caveats above: the mixtures' settings are near-ties, some
    members' settings were tuned on the search season or on validation, and the weights
    collapsed in two leagues.

## Caveats

- **Size of the search.**
  - Four named candidates were compared on validation, alongside about 50 pure models and five
    model + market candidates in earlier rounds (D-027).
  - About 700 inner settings were replayed on the training seasons.
  - The best validation score is therefore optimistic. `ml_online_market`'s validation margin
    (0.0003) is well inside its interval.
- **D-027's first condition.** Strictly, `ml_online_market` was never the lowest validation
  composite of all candidates ever compared: `market_tilt_ctx`, `_elo` and `_ens` scored
  0.9441–0.9444 in round 4. Here the test gate was "beats its target on validation", as asked.
  The point is moot because the test failed.
- **`ml_stack_market` also edged its target on validation,** by 0.00003. D-025 tests only the
  selected candidate, so it has no test result.
- **Slightly shorter windows for recorded forecasts.** A recorded forecast is fitted on the
  window of the current fit (the cutoff minus three years), cut at the record's own day. So it
  is fitted on slightly less history than a fresh walk-forward of that match would use.
- **Warm starts.** Members are refitted at more cutoffs than in a plain run (the second tier's
  midweek batches too), so their warm starts differ: up to 0.0002 in probability.
- **A very high learning rate.** The mixture with prices (eta 20) behaves like "follow the
  recent leader", and with no fixed share a member's weight can collapse for a whole season
  (the shots model's did in the 2022/23 replay). On validation it collapsed in two leagues:
  in the Premier League and Ligue 1 the GLM booster held all but 1e-15 of the weight at every
  batch. There the learner was in effect the GLM booster's shape tilted onto the prices, and
  did not keep learning in any useful sense.
- **The members' settings.** The shots model and ctx had settings chosen on 2022/23, and ctx's
  base also on 2023/24 (see Tuning). The search seasons and validation are therefore not fully
  out of sample for those members, which favours learners that trust them. Test is clean.
- **Live use is untested.** The model + market candidates implement what `footy.live` expects
  (`decision_time`, `last_used`, `provenance`) and use `MarketTilt`'s price rules. They have
  never been run live.

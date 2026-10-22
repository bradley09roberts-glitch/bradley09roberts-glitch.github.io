# Model results (generated 2026-10-21T06:06:00+00:00)

> **Note added by hand, 22 Oct 2026 (audit L4, M2; `footy final-report` rewrites this page).**
> This is the report of 21 Oct, 06:06 UTC, before round 4 of the model lab. It compares 6 of
> the 59 models with validation ledgers, and `congestion_context_on_elo`, the pure fallback of
> record since 10:52 UTC that day, is not on it. The current models of record and the later
> rounds are in STATUS.md and DECISIONS.md (D-026 to D-028, and the corrections of 22 Oct).
> `market_tilt_shots`'s match-result and over/under 2.5 figures are the bookmakers'
> pre-closing consensus with the margins removed, so its 53.4% of results right is what
> backing the bookmakers' favourite gives (53.5% on the same games); the pure fallback scores
> 52.5%. The test seasons were consulted more than once while choosing (D-027), so the test
> figures below helped choose. The pure models against the closing price are added under
> "Market benchmark".

Selection rule: Lowest composite on validation 2023/24 (mean of model/frequency-baseline over 1X2 RPS and log losses of O/U 1.5, 2.5, 3.5 and BTTS), five leagues pooled on identical fixtures.

Selected model: **market_tilt_shots**. Best composite on validation: market_tilt_shots; best 1X2 accuracy on validation: market_tilt_dc.

## Validation 2023/24

| Model | Fixtures | RPS | LL O/U 1.5 | LL O/U 2.5 | LL O/U 3.5 | LL BTTS | Composite | Acc 1X2 | Acc O/U 2.5 | Acc BTTS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| market_tilt_shots | 1752 | 0.1875 | 0.5029 | 0.6614 | 0.6096 | 0.6764 | 0.9449 | 0.550 | 0.610 | 0.566 |
| market_tilt_dc | 1752 | 0.1875 | 0.5029 | 0.6615 | 0.6102 | 0.6767 | 0.9452 | 0.550 | 0.610 | 0.568 |
| shots_xg_sot_joint_rw | 1752 | 0.1938 | 0.5065 | 0.6664 | 0.6125 | 0.6799 | 0.9552 | 0.537 | 0.592 | 0.562 |
| dixon_coles | 1752 | 0.1948 | 0.5092 | 0.6684 | 0.6165 | 0.6825 | 0.9598 | 0.526 | 0.584 | 0.559 |
| baseline_poisson | 1752 | 0.1964 | 0.5115 | 0.6698 | 0.6198 | 0.6862 | 0.9646 | 0.527 | 0.586 | 0.551 |
| baseline_frequency | 1752 | 0.2287 | 0.5147 | 0.6814 | 0.6285 | 0.6854 | 1.0000 | 0.431 | 0.566 | 0.553 |

## Test 2024/25 + 2025/26

| Model | Fixtures | RPS | LL O/U 1.5 | LL O/U 2.5 | LL O/U 3.5 | LL BTTS | Composite | Acc 1X2 | Acc O/U 2.5 | Acc BTTS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| market_tilt_shots | 3503 | 0.1959 | 0.5256 | 0.6725 | 0.5962 | 0.6840 | 0.9555 | 0.534 | 0.583 | 0.560 |
| dixon_coles | 3503 | 0.2019 | 0.5321 | 0.6817 | 0.6025 | 0.6889 | 0.9693 | 0.523 | 0.572 | 0.549 |
| baseline_poisson | 3503 | 0.2029 | 0.5318 | 0.6807 | 0.6031 | 0.6889 | 0.9700 | 0.515 | 0.571 | 0.546 |
| baseline_frequency | 3503 | 0.2313 | 0.5356 | 0.6874 | 0.6101 | 0.6884 | 1.0000 | 0.430 | 0.547 | 0.548 |

## Market benchmark (test)

- bench_1X2: model RPS 0.1961, market 0.1955, difference +0.0006 (95% CI -0.0002 to +0.0013), n=3392
- bench_OU_2.5: model log loss 0.6738, market 0.6703, difference +0.0035 (95% CI +0.0011 to +0.0058), n=3374
- avg_1X2: model RPS 0.1959, market 0.1953, difference +0.0006 (95% CI -0.0001 to +0.0013), n=3502
- avg_OU_2.5: model log loss 0.6725, market 0.6691, difference +0.0034 (95% CI +0.0011 to +0.0055), n=3503

Gate: Model does not beat the closing market; positive ROI is not expected.

Added by hand, 22 Oct 2026 (audit M2, recomputed from the test ledgers): every model against
the same closing benchmark (Shin de-vigged; 1X2 n=3392, O/U 2.5 n=3374). A positive
difference means worse than the closing price.

| Model | 1X2 RPS difference (95% CI) | O/U 2.5 log loss difference (95% CI) |
| --- | ---: | ---: |
| market_tilt_shots (model + market) | +0.0006 (−0.0002 to +0.0013) | +0.0035 (+0.0011 to +0.0058) |
| congestion_context_on_elo (best pure model) | +0.0048 (+0.0030 to +0.0066) | +0.0120 (+0.0084 to +0.0155) |
| dixon_coles | +0.0067 | +0.0127 |
| baseline_poisson | +0.0076 | +0.0117 |
| baseline_frequency | +0.0362 | +0.0182 |

No pure model comes near the closing price; the model + market model's figures for these two
markets are the pre-closing consensus itself.

## Profit simulation (flat 1 unit, Bet365 pre-closing)

**Model does not beat the closing market; positive ROI is not expected.**

| Strategy | Split | Markets | Bets | Hit rate | Avg odds | Profit (units) | Yield | Max drawdown (units) |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| unfiltered_ev_positive | validation | 1X2 | 0 | – | – | – | – | – |
| unfiltered_ev_positive | validation | OU_2.5 | 0 | – | – | – | – | – |
| unfiltered_ev_positive | validation | 1X2+OU_2.5 | 0 | – | – | – | – | – |
| unfiltered_ev_positive | test | 1X2 | 1 | 0.000 | withheld | withheld | withheld | withheld |
| unfiltered_ev_positive | test | OU_2.5 | 0 | – | – | – | – | – |
| unfiltered_ev_positive | test | 1X2+OU_2.5 | 1 | 0.000 | withheld | withheld | withheld | withheld |
| unfiltered_one_per_fixture | validation | 1X2 | 0 | – | – | – | – | – |
| unfiltered_one_per_fixture | validation | OU_2.5 | 0 | – | – | – | – | – |
| unfiltered_one_per_fixture | validation | 1X2+OU_2.5 | 0 | – | – | – | – | – |
| unfiltered_one_per_fixture | test | 1X2 | 1 | 0.000 | withheld | withheld | withheld | withheld |
| unfiltered_one_per_fixture | test | OU_2.5 | 0 | – | – | – | – | – |
| unfiltered_one_per_fixture | test | 1X2+OU_2.5 | 1 | 0.000 | withheld | withheld | withheld | withheld |

O/U 1.5, O/U 3.5 and BTTS: ROI unavailable (no historical prices).

Withheld: with fewer than 20 bets, figures computed from prices could reveal an individual price (football-data terms, D-013).

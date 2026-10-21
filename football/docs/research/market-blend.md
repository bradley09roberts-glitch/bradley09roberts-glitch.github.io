# Model + market: using bookmakers' prices

_Written 21 October 2026. Every number below comes from the walk-forward harness
(`footy.modeling.walkforward`), on fixtures every compared model predicted._

## Why

football-data's pre-closing prices are collected at the D-012 deadline (Friday 17:00 or
Tuesday 13:00 UK), before an early-edition prediction is made. On validation 2023/24
(five leagues, 1,752 fixtures) they beat every model built here by a wide margin:

| Forecast | RPS | 1X2 accuracy |
| --- | --- | --- |
| Pre-closing Bet365, Shin de-vigged | 0.1873 | 55.3% |
| Pre-closing market average | 0.1876 | 55.0% |
| Closing Pinnacle (the benchmark) | 0.1869 | 55.9% |
| Best lab model so far (`dynamic_ratings_blend3`) | 0.1928 | 53.8% |
| Dixon-Coles (current primary) | 0.1948 | 52.6% |

So the most accurate forecast available before kickoff uses the prices.

## Method (`footy.modeling.lab.market_blend.MarketTilt`)

1. **Prices.** For each fixture, Bet365 and the market average pre-closing 1X2 and O/U 2.5
   prices, Shin de-vigged and averaged. Pinnacle is left out because `fixtures.csv` no
   longer carries it, so a live run sees the same books as a backtest. Closing prices are
   never read. A price is used only if it was quoted no later than the prediction (the
   fixture's collection deadline in backtests; the time of the run when live) and before the
   kickoff.
2. **Targets.** target = w × market + (1 − w) × model, with one weight for 1X2 and one for
   O/U 2.5.
3. **Tilt.** The base model's score grid is moved to the targets with the smallest possible
   change in information: an exponential tilt on three indicators (home win, draw, over 2.5),
   solved per fixture by Newton's method. The tilted grid's 1X2 and O/U 2.5 equal the targets
   exactly. O/U 1.5 and 3.5, BTTS and the scorelines come from the same grid, so every market
   stays coherent. A fixture without usable prices keeps the base grid.

## Choosing the weights (inner season 2022/23 only)

The base models were run walk-forward over 2022/23 and 2023/24 once, with every fixture's
grid recorded. The weights were then searched (w_1x2 from 0 to 1 in steps of 0.05, w_ou from
0 to 1 in steps of 0.1) on 2022/23 by the D-025 composite. 2023/24 is reported, not used.

| Base | Chosen weights | Composite 2022/23 | Composite 2023/24 | RPS 2023/24 | 1X2 accuracy 2023/24 |
| --- | --- | --- | --- | --- | --- |
| Dixon-Coles alone | – | 0.9744 | 0.9598 | 0.1948 | 52.6% |
| Dixon-Coles + market | 1.0 / 1.0 | 0.9615 | 0.9452 | 0.1875 | 55.0% |
| Shots model alone | – | 0.9688 | 0.9552 | 0.1938 | 53.7% |
| Shots model + market | 1.0 / 1.0 | 0.9608 | 0.9449 | 0.1875 | 55.0% |

The market gets the full weight for both 1X2 and O/U 2.5 with every base tried. The models add
nothing to those two markets once the prices are known. What the base still contributes is the
shape of the grid, which sets O/U 1.5, O/U 3.5 and BTTS.

The official harness run (`uv run footy lab-validate market_tilt_dc dixon_coles`) gives the
same numbers: composite 0.9452, RPS 0.1875 (95% CI 0.1818 to 0.1932), accuracy 55.0%.

## Live use

`uv run footy fd-fixtures` fetches football-data's `fixtures.csv` (D-024) into the raw store.
`footy predict-domestic --model market_tilt_dc --only-with-market` publishes only the fixtures
with usable prices, so a later run with fresh prices can still publish the rest. Each record
stores the consensus probabilities used and the raw file's SHA-256 in `features`. The prices
themselves are not stored there.

Prices arrive twice a week: Tuesday for midweek fixtures, Friday afternoon for the weekend.
Until then a fixture has only the pure model's record.

## What this does and does not show

- It beats the pure models, but it is still a little behind the closing price it is
  benchmarked against (RPS 0.1875 against 0.1869 on 2023/24). That is expected: closing
  prices know about team news and lineups that pre-closing prices don't.
- The pure-model records continue, so the website can still show how a model without prices
  compares with the market.
- The profit simulation has to treat this model with care: its probabilities are mostly the
  same prices it would bet against, so it will find few value bets, and any it finds come
  from the base model's shape (totals and BTTS) or from differences between books.

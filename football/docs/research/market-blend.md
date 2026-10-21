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
   fixture's collection deadline in backtests; the time of the run when live), before the
   kickoff, and published no earlier than 00:00 UK on the fixture's own collection day (the
   staleness rule below).
2. **Targets.** target = w × market + (1 − w) × model, with one weight for 1X2 and one for
   O/U 2.5.
3. **Tilt.** The base model's score grid is moved to the targets with the smallest possible
   change in information: an exponential tilt on three indicators (home win, draw, over 2.5).
   Each cell is multiplied by a factor that depends only on its result and on whether it has
   over 2.5 goals, so the tilt rakes a 3 × 2 table of block masses to the targets: the
   result rows are scaled exactly and the one remaining unknown is found by bisection, which
   always converges. The tilted grid's 1X2 and O/U 2.5 equal the targets exactly. O/U 1.5 and 3.5, BTTS and the scorelines come from the same grid, so every market
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
stores the consensus probabilities used and the raw file's SHA-256 in `features`. From
21 October it also stores how many books each consensus averaged (`n_books_1x2`,
`n_books_ou`): `books` names the books asked for, and one of them may lack a market. The
prices themselves are not stored there.

Prices arrive twice a week: Tuesday for midweek fixtures, Friday afternoon for the weekend.
Until then a fixture has only the pure model's record.

A model + market candidate always publishes this way, with or without the flag (a dry run
still lists every fixture). A record under its name without prices would just be the base
model's forecast, and because a fixture's record is never replaced it would block the real
one once the prices arrive. The base model keeps its own records under its own name.

### Staleness rule (added 21 October 2026)

A live price is used only if its file was published on or after 00:00 UK on the fixture's
own D-012 collection day: the Tuesday for a Tuesday-to-Thursday fixture, the Friday for a
Friday-to-Monday fixture. That is the start of the decision batch the fixture would belong to
in a backtest, so a live run uses prices of the same vintage as the backtest it was validated
on. For example, Tuesday's file still on the site on Friday morning is never used for a
Saturday fixture. In effect, prices are never more than four days older than the kickoff
(Friday 00:00 to a late Monday kickoff).

- **Publication time.** This is the file's HTTP `Last-Modified` time, kept with the raw
  file. It is capped at the first retrieval (a later header is clock skew).
- **No readable `Last-Modified`: the file is not used live** (changed 21 October 2026).
  Until then the retrieval time stood in for a missing header. It can't: a file may sit on
  the site for days before it is fetched, so Tuesday's prices retrieved on Friday would have
  passed as Friday's. Now a missing, empty or unparseable header (or one without a time zone)
  leaves the publication time unknown, and every price from that file is refused for live
  predictions.
  - `fd_fixtures.latest_snapshot` logs a warning naming the raw file and the reason, and puts
    the reason in the snapshot's report (`live_use_refused`). `footy fd-fixtures` saves that
    report, with `published_at: null`, in its job run, so it shows in the refresh history.
  - In `market_blend.consensus`, an unknown publication time stays unknown: it is no longer
    filled in with the quote time, and a fixture with any such price has none. The
    staleness rule refuses it, and `predict-domestic --dry-run` lists the fixture with
    `market_refused: "publication time unknown (no readable Last-Modified)"`. A model +
    market candidate then publishes nothing for it.
  - Season-file prices are unaffected: they are published when quoted (stamped at their
    D-012 deadline). On the real database plus the stored `fixtures.csv` (which has a
    header), the consensus table is identical before and after the change (45,398
    fixtures, every column).
  - The file of 21 October was published on Tue 20 Oct at 10:57 UTC and first retrieved on
    Wed 21 Oct at 03:06 UTC.
  - The quote time used for the leakage checks stays the first retrieval, which is a safe
    upper bound.
- **Backtests are unaffected.** A season file's pre-closing price is stamped at its own
  deadline, which is always on its collection day, so the rule never removes one.
- **Clock changes.** The day boundary is computed in Europe/London. Fixtures on Sun 25 and
  Mon 26 Oct 2026, after the clocks go back, still have Fri 23 Oct as their collection day,
  and it starts at Thu 22 Oct 23:00 UTC (BST).
- **Moved fixtures.** A price is matched to a fixture by division, date and teams. If a
  fixture moves to another date after the file was published, it simply finds no price.

## Audit of the market code (21 October 2026)

An adversarial review of `market_blend`, `fd_fixtures`, `predict_domestic` and the
pan-European model's `market_alpha`. Every number comes from the database or from synthetic
stress tests.

### Leakage

No leak was found.

- **Closing prices.** Only `phase = 'pre_closing'` rows and the `fixtures.csv` pre-closing
  columns are read.
- **Backtest deadline.** Every walk-forward fixture frame carries `deadline_utc` (from
  `load_matches`), and a price is used only if it was quoted by that time and before the
  kickoff.
- **One refused fixture on 2023/24.** Of the 1,752 validation fixtures, 1,751 used prices.
  The other, Newcastle v Nottingham Forest on Tue 26 Dec 2023, kicked off at 12:30 UTC,
  before that day's 13:00 deadline, so its prices were refused (a D-012 exclusion).
- **Kickoffs before the deadline, all ten divisions:**

  | Season | 2019/20 | 2020/21 | 2021/22 | 2022/23 | 2023/24 | 2024/25 | 2025/26 |
  | --- | --- | --- | --- | --- | --- | --- | --- |
  | Matches | 5 | 36 | 18 | 14 | 14 | 17 | 33 |

  Most are Championship fixtures on Good Friday and Boxing Day; the rest are early Friday or
  Tuesday kickoffs, mainly in Serie B. All of them keep the base grid.
- **New tests.** `tests/modeling/test_no_future_leakage.py` now runs the tilt on a
  synthetic league with a synthetic price table.
  - Scrambling the results and prices of later fixtures leaves every earlier prediction
    identical.
  - A price quoted one minute after the deadline is ignored, and so is a price for a fixture
    that kicks off before its deadline.
  - With either guard removed in memory, the second test fails, as it should.

### Fixed

1. **The tilt could silently miss its targets (medium).** The previous solver, an undamped
   Newton iteration, diverged when the targets were far from the base grid. It then returned
   a wrong grid without raising an error.
   - It converged on every real 2023/24 fixture: the tilted 1X2 and O/U 2.5 equal the
     consensus to within 1e-11.
   - On synthetic stress grids it often failed:

     | Synthetic stress grid | Cases | Old solver missed its targets |
     | --- | --- | --- |
     | Extreme targets | 37,240 | 68% |
     | Realistic pairs of base grid and target | 26,460 | 27% |
     | Targets within ±0.10 of the base grid | 29,186 | 307 |
     | Targets within ±0.20 of the base grid | 17,993 | 1,388 |

     The misses were up to 0.98 in probability, mostly where the base grid was extreme.
   - The new solver (raking plus bisection) never missed: the worst error was 8e-16. Where
     the old one had converged, the two agree to 7e-12, and the new one is about nine times
     faster.
   - A market whose target is not strictly between 0 and 1 now keeps the base model's value
     for that market, instead of crashing or producing a wrong grid.
   - **Re-run of `lab-validate market_tilt_dc dixon_coles` with the new solver.**
     - Unchanged: composite 0.945155, RPS 0.187451 (95% CI 0.1818 to 0.1932), and every
       probability to within 1.1e-11.
     - The tilted 1X2 and O/U 2.5 now equal the consensus to within 4e-16.
     - Accuracy: 55.02% (964 of 1,752) instead of 54.97% (963). The single change is
       Lyon v Monaco (28 Apr 2024), whose consensus had exactly equal home and away
       probabilities (0.3738), so the old solver's rounding noise had chosen the pick.
2. **Stale live prices.** Nothing stopped an older file's prices being used. This is now the
   staleness rule above.
3. **A database fixture could take the snapshot's quote time.** If a fixture was both in the
   database and in the latest `fixtures.csv`, its prices were mixed across the two sources,
   and the later retrieval time replaced the deadline stamp. In a 2026/27 backtest the
   fixture's price would then have been refused. The database's prices now take precedence.
4. **Unpriced records under the market model's name.** These are now prevented, as
   described above.
5. **An unreadable date in `fixtures.csv` crashed the parse,** and with it every prediction
   run. Such rows are now skipped and listed in the report, and a two-digit year is
   accepted.

### Checked, no change needed

- **Shin de-vig.** It was run on 8,994 synthetic two-way and 9,035 three-way price sets
  across the accepted overround range (0.95 to 1.30), with no solver failure; every result
  sums to 1. An underround (below 1) is normalised in proportion.
- **Consensus with a missing market.** When one book lacks O/U 2.5, the over/under is
  averaged over the books that quote it (`n_books_ou` says how many).
- **Duplicated rows in `fixtures.csv`.** The first complete set of each book is used.
- **Venue-local and UK dates.** openfootball gives the venue's local date, football-data the
  UK date. They differ only for a kickoff between 23:00 and 00:00 UK, which none of the five
  leagues schedule, and a mismatch finds no price rather than a wrong one.
- **Terms (D-013, D-024).** The only stored record so far that used prices holds the
  consensus probabilities, the books' names, the quote time and the raw file's SHA-256,
  retrieval time and id. It does not hold the prices.
  - The same applies to the `predictions` table. The API passes on only those whitelisted
    fields.
  - `fd-fixtures` logs counts, unmapped names and overrounds, not prices.

### Reported, not changed

- **`/api/import-issues` showed invalid cell values** (fixed 21 October). It returned
  import-issue details such as `B365H='0'` or `AvgC>2.5='0.42'`, raw cell values from the
  season files (16 rows). It now sends the column and the reason only, e.g. `B365H: not a
  valid price (value withheld)`. The stored issue is unchanged. `implausible_overround`
  details still give the set's overround (e.g. `Avg 1X2 closing 0.837`), a derived sum
  from which no single price can be recovered.
- **A reverted file would not be picked up.** `latest_revision` picks the highest revision.
  If `fixtures.csv` reverted to byte-identical earlier content, the store would keep
  pointing at the newer revision. This is unlikely, since the dates change every week.
- **`default_prices()` is cached per process.** A long-running process that fetches a new
  `fixtures.csv` would keep the old table. The CLI runs one command per process, so this
  does not arise today.
- **`market_xg` (another strand's file).** The fixed rho for the 1X2-only solve is a median
  over every match, later seasons included. That is a small look-ahead in the pan-European
  model's market-trained targets, already noted in the module. It touches only matches
  without closing O/U prices.

## What this does and does not show

- It beats the pure models, but it is still a little behind the closing price it is
  benchmarked against (RPS 0.1875 against 0.1869 on 2023/24). That is expected: closing
  prices know about team news and lineups that pre-closing prices don't.
- The pure-model records continue, so the website can still show how a model without prices
  compares with the market.
- The profit simulation has to treat this model with care: its probabilities are mostly the
  same prices it would bet against, so it will find few value bets, and any it finds come
  from the base model's shape (totals and BTTS) or from differences between books.

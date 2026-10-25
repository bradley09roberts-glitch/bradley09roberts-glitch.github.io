# Status (Sunday 25 October 2026)

## What works

| Area | State |
|---|---|
| Public data | nflverse 2012-2026 (through the Thursday Week 7 game) in an immutable raw store with hashes, retrieval times, retries, caching and failure records; coverage matrix in `reports/coverage/COVERAGE.md` |
| Identities | GSIS-keyed players; cross-ids only from id columns; conflicts flagged, never merged; relocations mapped; weekly team membership (trades, elevations, IR) |
| Participation | played / inactive / DNP / reserve / active-unverified / not final, from rosters and on-field data (outcome labels only) |
| Models | joint game simulation (J0, J1), baselines (A, C), gradient-boosting challenger (M), stacked primary X and live headline X1 |
| Evaluation | weekly walk-forward on fixed splits; 7 experiments logged (`reports/EXPERIMENTS.md`), including rejected ones; one frozen test run; ledgers and manifests reproduce every headline (tested) |
| Prospective record | two genuine pre-kickoff editions for 2026 Week 7 (03:01 and 04:01 UTC, 13 games each), committed and pushed before the first kickoff (13:30 GMT) |
| Settlement | append-only revisions; record scoring by model and market once results are final |
| Money | settlement, rule profiles, no-look-ahead quote eligibility, bankroll simulation and metrics, all tested on synthetic fixtures; **ROI unavailable** (no recorded prices) |
| Odds adapter | The Odds API client, parser, UK/research classification, audited player linking: implemented, **not validated** (no key) |
| App | FastAPI + phone-first web UI (Today, Props, Player, Game, Shortlist, Backtest lab, Saved record, Data & method), CSV/JSON exports, manual-line tool; static snapshot published as a private Artifact |
| Tests | 63 automated tests (unit + integration on the real lake); `npl verify` |

## Results

Validation and test tables: `reports/EXPERIMENTS.md`. On the untouched 2024-2025 test
seasons the stacked models beat baseline A on all seven markets with week-block 95%
intervals excluding zero; for regular starters the improvement is modest (pass attempts
about 4%, receiving and rushing 4-10%), and receivers who changed teams are a known weak
spot. No claim about beating bookmakers is possible without prices.

## Missing or blocked (needs you)

1. **Odds key** (`ODDS_API_KEY` in `.env`): current odds are free (500 credits/month); a
   1-credit `markets` call per event with `regions=uk` would show whether any UK book
   offers NFL props through The Odds API. Historical prop archives need a paid plan (from
   USD 30/month, archive from 3 May 2023). Nothing was purchased.
2. **Game-day inactives** for a late edition: no permitted automated source (NFL.com terms).
3. **A scheduler**: none installed. The suggested schedule is in README.md.
4. **Repository**: this project sits on a branch of the public github.io repository. Raw
   odds must never be committed here (The Odds API terms forbid redistributing raw data);
   moving to a private repository like the football project is a one-step change.

## Known limitations

* Historical injury reports and pre-2025 depth charts have no publication times: J1/X1
  backtests assume they were public at the cutoff.
* 2026 games have no on-field participation data until after the season, so active players
  without a recorded stat stay `active_unverified` and their props cannot settle as zero.
* QB efficiency does not depend on the receiver's quarterback (a backup's lower completion
  rate is only partly captured); receivers who changed teams carry old shares too literally.
* Backup-QB cameos are simulated with zero stats.
* Weather, offensive-line availability and coaching changes are not modelled.
* Touchdowns, interceptions, combined yards and longer-term markets are not built (core
  first, as specified).

## Smallest sensible next step

Add an odds key and run `npl quotes` before next week's games to learn, for 1 credit per
event, whether UK-region NFL prop prices exist at all. Everything else (record, settlement,
validation) continues without it.

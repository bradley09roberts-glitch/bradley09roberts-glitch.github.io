# Status (Wednesday 28 October 2026)

## Latest (25 Oct 07:40-08:00 UTC check-in)

nflverse updated injuries, rosters, depth charts and schedules between 06:01 and 07:06 GMT
(Lamar Jackson Questionable -> Out; DeVonta Smith and Dontayvion Wicks Out and Saquon
Barkley Questionable for Monday; several activations and reserve moves). A fourth Week 7
edition was published at 07:43 UTC. It exposed a bug: a quarterback listed Out could still
enter a simulated game as the replacement or for a cameo (Jackson showed P(plays) 16%). Fixed
as v0.4.1 and validated (experiment 8); the fifth edition (08:00 UTC, v0.4.1) is the one
that counts for kickoff under D-008. `npl update` now re-checks files older than 30 minutes
(the 6-hour cache had hidden the 07:00 refresh).

Correction on the 08:00 edition's note: it says the inputs were "re-checked before
publishing", but that check was answered from the 18-minute-old cache (no network request).
A forced network re-check at 08:00:36 UTC, seconds after publication, found injuries,
rosters, depth charts and schedules byte-identical (same SHA-256 as the 07:42 retrieval),
so the edition used current inputs. Before publishing, use `npl ingest --force` for those
files.

## NBA and redesign (28 Oct, on request)

* **NBA added** (D-019 to D-021): SportsDataverse box scores 2015-16 onwards, schedules
  and daily injury snapshots. Model nba-0.1 (minutes x per-minute rates, opponent and
  pace, empirical minutes spread) beat a tuned recent average on the untouched 2025-26
  test season: points -2.9%, rebounds -2.4%, assists -1.9%, threes -1.2%, PRA -4.1% CRPS
  (all week-block intervals exclude zero). Modest, and no claim about bookmakers.
* **Live NBA editions**: one after the morning box-score update, a final one after the
  ~18:40 UTC injury snapshot, both committed before the first tip-off. Injury effects
  (Out 1%, game-time decision x0.65) are assumptions; a newly-out teammate's minutes are
  not redistributed yet (flagged on cards).
* **App redesign ("Props Lab")**: NFL/NBA switch; prop cards with a fair line or your own
  line, over/under split, L5/L10/L20/season/head-to-head hit rates, last-10 chart against
  the line, matchup and pace ranks; stored US market lines shown as a research reference.
  Own name and design, not a copy of any commercial product's branding.

## NBA defensive matchups (28 Oct, on request)

New NBA "Defence" section: league table (sortable), and for each team a half-court map of
points, attempts or percentage allowed by area (restricted area, paint, mid-range, both
corners, both wings, top of the arc), filters (season, last 5/10/20, home/away, regular
season or playoffs, per game or per 100 possessions, opposing position), defence against
guards, forwards and centres with points by area and position, and fouls and free throws.
Player pages compare the player's shot profile with what tonight's opponent allows to his
position, with plain-English advantages and difficulties and how much signal each carries.
Individual positions (PG/SG/SF/PF) and fouls drawn by position are not available in the
source and are marked as such (D-023). Props board has a hot/cold filter.

## Week 7 record complete (27 Oct)

All 13 games saved before kickoff are settled (Monday: DAL 38, PHI 21). On the common
subset the headline X1 beat baseline A on every market (pass attempts -20%, receiving
yards -5%), better on 55% of 907 player-markets; one week is far too small to conclude
anything. For the Monday game, 12 model leans against US lines were saved before kickoff
(`data/runs/`, not committed: they contain odds): 8 landed (7.3 expected); over 34 props the
model's log loss was 0.646 against the market's 0.693. One game, correlated props: not
evidence of an edge.

## Odds (26 Oct)

Odds key configured (`.env`, never committed). UK bookmakers on The Odds API list no NFL
player props for the Monday game, only match odds, spreads and totals (D-018). US prop
prices (six books) were stored before kickoff as a research reference for comparing the
model with a market; nothing about them is claimable as UK profit. Early finding from that
comparison: the headline receptions forecast (X1 uses the ML model alone for receptions,
weight from experiment 5) does not react to teammates ruled Out, while the game simulation
does; for Philadelphia, with two receivers Out, J1 sits near the market and X1 well below
it. A validation experiment is needed before any change (D-013).

## Team defences (25 Oct, on request)

New Defences page (phone: More > Defences): every defence ranked 1-32 overall, against the
pass, pass rush, the run, QB runs and receiving yards to WRs, TEs and RBs, with a team page
for each and a matchup panel on every game page; prop cards show the opponent's relevant
ranks. Numbers are opponent-adjusted and shrunk by how much each split actually carried over
in past seasons, which turned out to be little for most splits (`reports/defense/STABILITY.md`):
after Week 6, 25 of 32 defences have no clear strength or weakness. The splits are context,
not forecast inputs (D-017). Also fixed: the game page's simulated team-volume table was
empty since X1 became the headline (it now shows the J1 simulation inside X1).

## What works

| Area | State |
|---|---|
| Public data | nflverse 2012-2026 (through the Thursday Week 7 game) in an immutable raw store with hashes, retrieval times, retries, caching and failure records; coverage matrix in `reports/coverage/COVERAGE.md` |
| Identities | GSIS-keyed players; cross-ids only from id columns; conflicts flagged, never merged; relocations mapped; weekly team membership (trades, elevations, IR) |
| Participation | played / inactive / DNP / reserve / active-unverified / not final, from rosters and on-field data (outcome labels only) |
| Models | joint game simulation (J0, J1), baselines (A, C), gradient-boosting challenger (M), stacked primary X and live headline X1 |
| Evaluation | weekly walk-forward on fixed splits; 7 experiments logged (`reports/EXPERIMENTS.md`), including rejected ones; one frozen test run; ledgers and manifests reproduce every headline (tested) |
| Prospective record | five genuine pre-kickoff editions for 2026 Week 7 (03:01 v0.1, 04:01 v0.3, 04:35 v0.4, 07:43 v0.4 with refreshed inputs, 08:00 v0.4.1 UTC; 13 games each), committed and pushed before the first kickoff (13:30 GMT); under D-008 the 08:00 edition counts |
| Settlement | append-only revisions; record scoring by model and market once results are final |
| Money | settlement, rule profiles, no-look-ahead quote eligibility, bankroll simulation and metrics, all tested on synthetic fixtures; **ROI unavailable** (no recorded prices) |
| Odds adapter | The Odds API client, parser, UK/research classification, audited player linking: validated live on 26 Oct (US props stored; UK region has none) |
| App | FastAPI + phone-first web UI (Today, Props, Player, Game, Shortlist, Backtest lab, Defences, Saved record, Data & method), CSV/JSON exports, manual-line tool, exploratory "what if" scenarios (server only); static snapshot published as a private Artifact: https://claude.ai/artifact/JikfB26yYTb8Qf5qu5jHeN |
| Tests | 72 automated tests (unit + integration on the real lake); `npl verify` passes (lint, tests, edition hashes, API routes); 28-row manual reconciliation (`docs/RECONCILIATION.md`) |
| Follow-ups | check-ins scheduled in this session: 25 Oct 07:40 UTC (refresh inputs, new edition only if they changed), 26 Oct 11:00 UTC (settle Sunday games), 27 Oct 11:00 UTC (settle Monday game). No recurring schedule has been created. |

## Results

Validation and test tables: `reports/EXPERIMENTS.md`. On the untouched 2024-2025 test
seasons the stacked models beat baseline A on all seven markets with week-block 95%
intervals excluding zero; for regular starters the improvement is modest (pass attempts
about 4%, receiving and rushing 4-10%), and receivers who changed teams are a known weak
spot. No claim about beating bookmakers is possible without prices.

## Missing or blocked (needs you)

1. **UK prop prices**: the free odds key works, but UK books list no NFL player props
   through The Odds API (checked for one game, 26 Oct). Options: keep collecting US prices
   as a research reference, or enter UK prices by hand in the app (manual lines). Historical
   prop archives need a paid plan (from USD 30/month); nothing was purchased.
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
* Team play volume does not react to personnel in scenarios (removing a star changes how
  volume is shared and how efficient it is, not how much there is).
* Live editions' injury-report participation rates (v0.4) were validated on 2022-2023 only;
  the 2024-2025 test predates them (D-013, D-015).
* Weather, offensive-line availability and coaching changes are not modelled.
* Touchdowns, interceptions, combined yards and longer-term markets are not built (core
  first, as specified).

## Smallest sensible next step

Add an odds key and run `npl quotes` before next week's games to learn, for 1 credit per
event, whether UK-region NFL prop prices exist at all. Everything else (record, settlement,
validation) continues without it.

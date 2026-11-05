# Props Lab (NFL and NBA)

The folder keeps its original name. NBA support was added on 28 Oct 2026 (D-019); its
model, validation and test are in `reports/nba/` and `src/npl/nba/`.

NFL player-prop forecasting and chronological backtesting: QB passing yards, attempts and
completions; QB/RB rushing yards and attempts; WR/TE/RB receptions and receiving yards.
It produces full predictive distributions per player and game from public data, evaluates
them walk-forward against baselines, and keeps a genuine pre-match record of forecasts
saved before kickoff. It makes no profit claims: with no recorded bookmaker prices the
money side reports **ROI unavailable**.

This folder is a separate project. The FightBase site in the repository root is not
touched.

## What is where

| Path | Contents |
|---|---|
| `src/npl/` | pipeline (Python 3.12+) |
| `web/` | phone-first web app (no build step) served by `npl serve` |
| `records/editions/` | published, immutable forecast editions (+ SHA-256) |
| `reports/coverage/` | measured data coverage (`COVERAGE.md`) |
| `reports/backtests/` | backtest summaries; headline runs include their full ledger |
| `reports/record/` | prospective record scores |
| `reports/defense/` | defence stability study and the calibration the Defences page uses |
| `docs/` | `DATA_AUDIT.md`, `METHODOLOGY.md`, cited research notes |
| `DECISIONS.md` | dated decisions; `reports/EXPERIMENTS.md` lists every experiment |
| `data/` (git-ignored) | raw immutable downloads, SQLite store, lake, runs |

## Setup

```bash
cd nfl-props-lab
uv sync                      # pinned by uv.lock
cp .env.example .env         # optional: ODDS_API_KEY (never commit .env)
```

## Commands

```bash
uv run npl ingest                 # download nflverse data (cached, retried, hashed)
uv run npl audit                  # coverage matrix -> reports/coverage/
uv run npl build                  # canonical lake tables
uv run npl update                 # daily: ingest current season + audit + build
uv run npl publish --season 2026 --week 8   # immutable edition (refuses uncommitted code)
uv run npl settle                 # append settlement revisions, score the record
uv run npl quotes                 # odds snapshot (reports BLOCKED without ODDS_API_KEY)
uv run npl money --ledger reports/backtests/<run>/ledger.csv.gz
uv run npl nba update             # NBA: SportsDataverse box scores, schedule, injuries
uv run npl nba publish --date 2026-10-28 --kind final   # NBA edition (US date)
uv run npl nba settle             # NBA pre-tip record -> reports/nba/record/
uv run npl quotes --sport nba --regions us --hours-ahead 24   # NBA prop prices + game lines (credits)
uv run npl quotes --sport nba --dry-run [--combos]   # markets and credit estimate only, no call
uv run npl close --sport nba --within 30 --dry-run   # plan a closing snapshot of tracked props
uv run npl serve                  # web app at http://127.0.0.1:8765
uv run npl snapshot --min-p-play 0.9 --nba-min-p-play 0.7   # offline copy of the app (no server)
uv run npl verify                 # lint, tests, edition hashes, API smoke test
```

NBA combo markets (Pts + Reb, Pts + Ast, Reb + Ast; keys pr, pa, ra, added 4 Nov 2026) are
forecast in every edition, built like PRA from the same points, rebounds and assists forecasts.
We hold no past bookmaker lines for them, so nothing about them has been tested against prices,
and they are in neither tracked paper rule. A props pull does not ask for their prices unless
told to: `npl quotes --sport nba --combos` adds the provider's three combo markets (about 3 more
credits per game; `--dry-run` prints the markets and the estimate without any call). The
default pull asks for the five markets only, and `npl close` prices tracked markets only.
A proxy calibration (summed part lines) failed its check on PRA (n13), so the app shows no
tested model view for a combo (D-048).

Odds calls cost credits. Every pull opens with the provider's free events call, makes no paid
call that would leave fewer than `NPL_ODDS_FLOOR` credits (default 400) and lists any call it
skipped. `npl close` without `--dry-run` spends about one credit per tracked market per game;
`--rule RULE` (repeatable) limits it to the props of some rules. It uses the provider's start
time from the free events call: a game already under way, or starting within 2 minutes, is
skipped and listed (its prices would be in-play), and a start more than 10 minutes from the
tip-off recorded with the tracked props is reported as a warning. Just before each paid call it
checks that game's start time again (free) with the same rule. Every stored odds response is
also logged in the pull log (`odds_pulls`), which readers use to rebuild each pull's board;
`uv run npl backfill-pulls` logs responses stored before the log existed (counts only).

The snapshot embeds US bookmaker prices, so it is written to
`data/snapshots/nfl-props-lab.html` (git-ignored; never commit it) unless `--out` says
otherwise. The thresholds shown are the defaults: NBA cards, player pages and photos for players
at least 70% likely to play, NFL player pages and photos for players at least 90% likely to play
(every NFL card is kept). A page over 16,000,000 bytes, the host's limit, is refused unless
`--allow-oversize` is given. Measured on 3 Nov 2026 with the 2 Nov NBA edition (15 games) and
the Week 8 NFL edition: about 14.76 MB at these defaults (1,410 NBA cards, 352 with a
bookmaker line; builds differ by a few dozen bytes because they carry timestamps). The NBA default was raised from 60% (the props board's filter) that day, for
slates with more lines: on that edition a card with a line takes about 1.05 KB more than one
without (its line and factor tabs), so the same 15 games with about 900 lined cards would come
to about 15.34 MB at 70% but 15.86 MB at 60%, over the 15.5 MB we aim to stay under. At 60% the
page measured about 15.28 MB (about 15.44 MB before the factor tabs' labels were kept once,
D-047). These estimates count cards only: a slate with stored game lines and a larger NFL edition
add a little more. If a build is refused as too large, raise `--nba-min-p-play` (or
`--min-p-play` for the NFL pages) and build again.

The server gzips responses of 1 KB or more. NBA cards carry four factor tabs (D-047: research
context from rules that, when tested, made no money at the best regulated price in either
season and did not measurably improve on the market's own forecast), and
`/api/nba/parlay?legs=PLAYER_ID:MARKET:SIDE,...&price=4.5` combines card legs at their
margin-free chances, as the app's parlay check does (arithmetic only; legs from one game are
flagged as not independent).

Backtests and the ML feature cache are run from Python (`npl.backtest.run`,
`npl.ml.build_cache`, `npl.evaluate.write_report`); `reports/EXPERIMENTS.md` lists the
exact calls behind each committed report.

### Suggested schedule (UK time; no scheduler has been installed)

| When | Command | Why |
|---|---|---|
| Daily 09:00 | `npl update` | nflverse rosters, injuries and depth charts refresh at 07:00 UTC |
| Day before kickoff, after the update | `npl ingest --datasets injuries,rosters_weekly,depth_charts,schedules --seasons 2026 --force`, commit code, then `npl publish` | early edition (the final injury report is out); repeat on game morning after nflverse's 07:00 UTC refresh |
| Tuesday 12:00 | `npl update && npl settle` | Monday night results; nflverse recommends Thursday's pbp for stat corrections, so re-run `npl settle` on Thursday too |

Jobs are idempotent: re-running `ingest` stores nothing new if the content is unchanged,
`settle` appends a revision only when a value or status changed, and `publish` never
overwrites an edition.

## Status

See the delivery notes in `STATUS.md`.

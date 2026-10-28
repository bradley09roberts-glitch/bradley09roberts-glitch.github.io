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
uv run npl quotes --sport nba --regions us --hours-ahead 24   # NBA prop prices (credits)
uv run npl serve                  # web app at http://127.0.0.1:8765
uv run npl snapshot --out app.html  # self-contained offline copy of the app (no server)
uv run npl verify                 # lint, tests, edition hashes, API smoke test
```

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

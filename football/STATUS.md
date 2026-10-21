# STATUS

_Last updated: 2026-10-21, 08:30 UK. Every session starts from this file._

## Where things stand

On 21 Oct the owner asked for as much as possible overnight, with the Champions League first
and "whatever has the best accuracy". M2 to M6 were built in that push and M7 is running;
none of it has had the owner's review yet.

| Milestone | State |
| --- | --- |
| M0 Setup | Done, reviewed |
| M1 Data | Done; extended on 21 Oct (European leagues, repeat meetings, prices for 26 divisions) |
| M2 Baselines | Done: walk-forward harness, ledgers, leakage tests, frequency/Elo/Poisson baselines |
| M3 Score model | Done: Dixon-Coles, then a model lab of 8 families (lab still running, see below) |
| M4 Five leagues | Done: every model is run and selected on the five leagues pooled |
| M5 Profit simulation | Done: `footy final-report` (one test run, market benchmark, profit) |
| M6 Website core | Done: fixtures, match, past, performance, lab, data, status pages; QA in `docs/m6/qa.md` |
| M7 Live operations | Running: genuine pre-match records are being published and settled; see dependencies |

## Models of record (D-025, D-026)

Chosen by the lowest composite on validation 2023/24 and run once on test 2024/25 + 2025/26.
`src/footy/reference/primary_models.json` holds them as periods with start times.

| Group | Model of record | Test composite | Against |
| --- | --- | --- | --- |
| League games, five top leagues | `market_tilt_shots`, falling back to `dixon_coles` where a fixture has no prices yet (from 21 Oct, 07:01 UTC) | 0.9555 | `dixon_coles` 0.9693 |
| UEFA competitions | `pan_euro_mix_mxg` (from 21 Oct, 04:30 UTC) | 0.9666 | earlier `pan_euro_mix` 0.9686 |

- **`market_tilt_shots`** moves the shots model's score grid onto football-data's pre-closing
  Bet365 and market-average prices (D-024, [docs/research/market-blend.md](docs/research/market-blend.md)).
  It beats every pure model in every league and both test seasons. It does not beat the
  closing price: level on 1X2, slightly worse on O/U 2.5. Full report:
  [docs/results/domestic.md](docs/results/domestic.md).
- **`pan_euro_mix_mxg`** is the pan-European Dixon-Coles + Elo mixture, with top-five domestic
  matches fitted partly to the expected goals their closing prices implied. Its market-xG
  scope is pinned (`top5_exact`), so it reproduces its published records exactly.

## Published predictions (genuine pre-match, `records/predictions/2026-10-21.jsonl`)

| Model | Records | Fixtures |
| --- | --- | --- |
| `pan_euro_mix_mxg` | 45 | UCL 21 Oct (9), UEL and UECL 22 Oct (36) |
| `pan_euro_mix` | 45 | the same fixtures, published earlier the same night |
| `dixon_coles` | 64 | league games 21–27 Oct (E0, SP1, I1, D1, F1, E1) |
| `market_tilt_dc`, `market_tilt_shots` | 1 each | Levante v Athletic Club, 21 Oct |

Nothing is settled yet. Scheduled check-ins (this session, `send_later`):

| When (UTC) | What |
| --- | --- |
| Wed 21 Oct 12:30 | Deadline check before the UCL kickoffs (already published) |
| Wed 21 Oct 22:20 | Settle the Champions League |
| Thu 22 Oct 22:20 | Settle the Europa League and Conference League |
| Fri 23 Oct 12:20 | Fetch the weekend's prices (`footy fd-fixtures`), publish `market_tilt_shots` records before the 19:00 kickoffs |
| Mon 26 Oct 08:10 | Settle the weekend's league games |
| Tue 27 Oct 11:50 | Midweek prices and records (Serie A on 27 Oct) |

## Data (21 Oct 2026)

- 64,693 matches in 26 divisions (the core ten plus 16 European leagues, D-022), 2018/19 to
  date; 181 excluded from models (play-offs, bare and awarded results).
- 1,473,193 prices: closing 1X2 for every division, O/U 2.5 where football-data has it,
  pre-closing prices for the main-layout files.
- 4,537 UEFA matches in `euro_matches` (4,234 played, 297 scheduled, 4 awarded, 2 cancelled),
  from openfootball and Wikipedia (D-023). UEFA.com is not used (terms §6.2).
- Repeat meetings in multi-round leagues are stored since migration 0005 (4,815 recovered).
- Coverage report: [docs/coverage/coverage.md](docs/coverage/coverage.md). Its European
  section still describes prices as top-five only (to regenerate).

## Model lab (workflow still running)

Each family is built on seasons before 2023/24 and compared on validation; the composites below
are the builders' own and still wait for the lab's adversarial audits and its ensemble.

| Family | Best candidate | Validation composite |
| --- | --- | --- |
| Goal-based Elo | `elo_goals_gd` | 0.9528 |
| Dynamic ratings | `dynamic_ratings_blend3` | 0.9540 |
| Bivariate / dispersion | `bivariate_dispersion_sx_birth_time` | 0.9544 |
| Goal calibration | `goal_calibration_tuned_level` | 0.9548 |
| Shots as xG | `shots_xg_sot_joint_rw` | 0.9552 |
| Dixon-Coles tuning | `dc_tuning_tilt` | 0.9560 |
| Gradient boosting | `ml_gbm_glm_boost_dc` | 0.9564 |
| Congestion and context | building | – |
| Dixon-Coles (reference) | `dixon_coles` | 0.9598 |

When the lab finishes: pick the best audited pure model (or the ensemble), test it once, and
use it as the pure fallback and as the model + market base if it improves the shape markets.

## Resume commands

```bash
cd football
uv sync
uv run footy fd-update                    # results and prices (polite, cached; --scope core|europe)
uv run footy euro-update --fetch-wikipedia
uv run footy fd-fixtures                  # this week's prices (Tue/Fri), D-024
uv run footy predict-domestic --model market_tilt_shots --divisions E0,SP1,I1,D1,F1,E1 --days 4 --dry-run
uv run footy predict-euro --model pan_euro_mix_mxg --days 2 --dry-run
uv run footy settle
uv run footy final-report --selected <model> --candidates <a,b>
uv run footy set-model-of-record --group domestic --models <a,b> --from now
make check
uv run footy serve; cd frontend && npm ci && npm run dev
```

`data/` (database, raw store, ledgers) is git-ignored; `fd-update` and `euro-update` rebuild it.
The capture loop (`data/logs/capture_loop.sh`, every 5 minutes) runs only while this
container lives.

## Known problems and open dependencies

1. **Always-on host** (D-009): publishing, settling and odds capture run from this session's
   check-ins and a loop inside the container. When the container is reclaimed they stop
   until a session resumes them.
2. **Sportmonks free plan** covers only the Scottish Premiership and Danish Superliga; its
   capture job has stored no snapshots yet (no fixture inside its windows so far). The live
   prices for the five leagues come from football-data's `fixtures.csv` instead (D-024).
3. **UEFA.com raw files** (D-023): 147 pages fetched before the terms were checked sit in the
   local raw store, never committed or used. The owner decides whether to purge them.
4. **No lineups or injuries**: only early editions exist.
5. **Data fingerprint**: `ledger.data_revision` hashes the raw football-data files only, so it
   doesn't change when parsers, UEFA sources or imported prices change.
6. **Profit reports**: with 20 or more bets, the equity curve and one-bet rows would reveal
   individual prices (D-013); aggregate them before a model that bets often is reported.
7. **Harness gaps**: E1 has no entry in the runner's league map (`run_models(leagues=["E1"])`
   fails), and `save()` run ids carry no league.
8. **`alembic check`** fails about 1 run in 15: two unique constraints on `raw_files` get the
   same generated name.
9. **The fixed rho** for 1X2-only market-xG solves is a median over all seasons (a small
   look-ahead, measured as negligible; the UEFA model of record doesn't use it).
10. **Lab files**: references to the selection rule as "D-022" in lab modules should read
    D-025 once the lab stops editing them.

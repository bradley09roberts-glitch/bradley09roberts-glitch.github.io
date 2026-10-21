# STATUS

_Last updated: 2026-10-19 (end of M1). Every session starts from this file._

## Current milestone

**M1 Data: complete, awaiting owner review.** Next: M2 Baselines for the Premier League (do not
start until M1 is approved).

| Milestone | State |
| --- | --- |
| M0 Setup | Done (reviewed: owner replied "Do what's best" to the open decisions) |
| M1 Data | Done, awaiting review |
| M2 Baselines (Premier League) | Not started |
| M3 Score model (Premier League) | Not started |
| M4 Remaining four leagues | Not started |
| M5 Profit simulation | Not started |
| M6 Website core | Not started |
| M7 Live operations | Not started |

## Where the code lives

- Repo `bradley09roberts-glitch/bradley09roberts-glitch.github.io`, branch
  `claude/football-predictor`, directory `football/` (the brief's "repo root"; see D-001).
- Not merged to `main`: `main` publishes the FightBase site via GitHub Pages.

## Resume commands

```bash
cd football
uv sync                         # Python 3.12 env from uv.lock
uv run footy fd-update          # migrate, download (polite, cached) and import football-data
uv run footy coverage --markdown docs/coverage/coverage.md
uv run footy spot-check -n 20 --markdown docs/m1/spot-check.md
make check                      # lint, format check, pytest, frontend typecheck + build
uv run footy serve              # API: /api/health /api/coverage /api/refresh-history /api/import-issues
cd frontend && npm ci && npm run dev    # site http://127.0.0.1:5173
```

`data/` (database and raw store) is git-ignored. A fresh session rebuilds it with
`fd-update`: 90 files at one request every 2 s, about 3 minutes, then about 45 s to import.

## Data coverage (19 Oct 2026)

Full tables: [docs/coverage/coverage.md](docs/coverage/coverage.md).

- 90 season files (10 divisions × 2018/19–2026/27), 31,347 matches, 433,184 prices.
- 28 matches excluded from models as bare or awarded results (D-018); 1 row rejected (no score).
- Earliest and latest matches, top divisions:

  | League | Earliest | Latest |
  | --- | --- | --- |
  | Premier League | 2018-08-10 | 2026-10-12 |
  | La Liga | 2018-08-17 | 2026-10-12 |
  | Serie A | 2018-08-18 | 2026-10-12 |
  | Bundesliga | 2018-08-24 | 2026-10-11 |
  | Ligue 1 | 2018-08-10 | 2026-10-11 |

- Every completed season has the full round-robin count, except:
  - Ligue 1 and Ligue 2 2019/20 (cut short);
  - Ligue 2 2023/24 (Troyes v Valenciennes absent from the source);
  - Ligue 2 2025/26 (one row with no score).
- 2018/19 has no kickoff times and no closing prices except Pinnacle's (burn-in season only).

Price coverage for the market benchmark (D-014):

| Closing prices | 2023/24 | 2024/25 | 2025/26 | 2026/27 |
| --- | --- | --- | --- | --- |
| Pinnacle 1X2 | 100% | 100% | 49–55% | 0% |
| Betfair Exchange 1X2 | 0% | 100% | 92–95% | 100% |

## European leagues (21 Oct 2026, D-022)

Sixteen extra domestic leagues, results only, for rating Champions League clubs from outside
the big five. They cover 2018/19 (or 2018) to date.

- **Main layout:** Scotland, Netherlands, Belgium, Portugal, Turkey and Greece (54 season
  files).
- **Extra-league files:** Austria, Denmark, Switzerland, Norway, Sweden, Poland, Romania,
  Russia, Finland and Ireland (10 files, one per league with every season).

**Totals:** 28,432 matches stored and 153 excluded from models. 394 new teams.

**Gap:** 4,815 repeat meetings (the same home and away teams twice in a season) are held back,
because `matches` allows one row per pairing per season. This affects split and multi-round
leagues: Scotland, Switzerland, Ireland, Romania, Austria, Denmark, Greece, Finland, Belgium
and Poland. Their past seasons stop early. A migration is needed (D-022 has the fix).

Commands:

- `uv run footy fd-update` covers everything.
- `--scope core` or `--scope europe` narrows it.

## M1 acceptance results (run 2026-10-19)

| Test | Result |
| --- | --- |
| Spot-check of 20 matches against source | 874 values (teams, date, kickoff, score, half-time, stats, every stored price) re-read from the raw CSVs with a separate code path: **0 mismatches** ([docs/m1/spot-check.md](docs/m1/spot-check.md)). Against independent public sources: **20/20 dates and scores confirmed, 18/18 recorded kick-offs match the scheduled UK time** ([docs/m1/independent-check.md](docs/m1/independent-check.md)) |
| Re-running imports creates no duplicates | Plain re-run imported nothing; a forced re-parse of all 90 files changed 0 matches and 0 prices; table counts identical. Also covered by `test_import_then_rerun_creates_no_duplicates` |
| `uv run pytest` | 76 passed, 1 skipped (Sportmonks contract test: no recorded responses until a key exists) |
| Lint, format, typecheck, build | Clean |
| App on real data | `/api/health` reports 31,347 matches, 10 divisions; phone (390 px) and desktop screenshots, no horizontal scroll |

## Known problems and open dependencies

1. **Sportmonks key** (D-010): the adapter and capture job are built and unit-tested, but they
   are unverified against live responses and capture nothing until the owner adds a key.
2. **Always-on host** (D-009): the capture job is meant to run every 5 minutes from cron on
   it; it can't run in these coding sessions.
3. **2026/27 is under way.** The genuine pre-match record starts only when M7 is live; earlier
   2026/27 matches are retrospective.
4. **Pinnacle's 2025/26 gaps** are handled by D-014 (Betfair Exchange closing from 23 Jul 2025).
   Betfair Exchange prices don't exist in the data before 2024/25.
5. **Referees** are only in English files; **xG** only in 2026/27 (undocumented; not used).
6. **European leagues: repeat meetings held back** (D-022). The fix needs a migration: a
   `matches.meeting` column in the unique key, then a re-import.

## M2 plan (for the next session, after review)

1. Walk-forward harness for the Premier League:
   - burn-in 2018/19–2019/20, training through 2022/23, validation 2023/24, test 2024/25 and
     2025/26;
   - predictions in matchweek batches, using only results final before each batch's D-012
     cutoff.
2. Baselines: league frequency, Elo with home advantage and a draw model, and independent
   Poisson.
3. Per-fixture ledger (CSV and JSON) and run manifest. Metrics: RPS for 1X2, log loss for
   O/U 1.5 / 2.5 / 3.5 and BTTS, plus Brier score, accuracy and calibration.
4. A leakage test for every guard in the brief. Acceptance: headline metrics recompute exactly
   from the exported ledger.

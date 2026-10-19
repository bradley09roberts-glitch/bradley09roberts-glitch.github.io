# STATUS

_Last updated: 2026-10-19 (end of M0). Every session starts from this file._

## Current milestone

**M0 Setup: complete, awaiting owner review.** Next: M1 Data (do not start until M0 is
approved).

| Milestone | State |
| --- | --- |
| M0 Setup | Done, awaiting review |
| M1 Data | Not started |
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
uv sync                    # Python 3.12 env from uv.lock
uv run footy migrate       # creates/updates data/footy.sqlite3 (git-ignored)
make check                 # lint, format check, pytest, frontend typecheck + build
uv run footy serve         # API http://127.0.0.1:8000/api/health
cd frontend && npm ci && npm run dev    # site http://127.0.0.1:5173
```

## Data coverage

None imported yet. The database has only the baseline schema (Alembic revision `0001`).

## M0 acceptance results (run 2026-10-19)

| Test | Result |
| --- | --- |
| `uv run pytest` | 6 passed (health ok after migration; degraded when unmigrated; no data/provider reported; secrets never rendered; provider needs matching key; `.env.example` lists every setting) |
| `ruff check` / `ruff format --check` | Clean |
| `npm run typecheck` / `npm run build` | Clean |
| App boots | `footy serve` + `npm run dev`; `/api/health` returned `status: ok`, schema `0001`, both direct and through the Vite proxy |
| Visual check | Playwright screenshots at 390 px and 1280 px: no horizontal scroll; API-down error state renders with a retry button |
| CI | `football-ci` passed on GitHub Actions for commit `924fcc9` (lint, format, pytest, typecheck, build) |
| Provider research | Written: DECISIONS.md D-009 to D-015, `docs/research/providers.md`, `docs/research/historical-data.md`. Sportmonks verified from its own pages; API-Football unverifiable from here |

## M1 plan (for the next session, after review)

1. Raw store: `data/raw/<source>/` files kept with source, URL, retrieval time, SHA-256 and
   revision; never overwritten.
2. football-data importer for E0, SP1, I1, D1, F1 and E1, SP2, I2, D2, F2, seasons 1819 to
   2627, reading from `data/inbox/football-data/` (D-013); idempotent re-runs.
3. Team mapping table with tests; coverage report per league, season and field, including
   Pinnacle and Betfair Exchange columns by season (D-014).
4. Provider adapter interface plus a Sportmonks implementation, tested against recorded real
   responses once a free-plan key exists (D-010); odds-capture job built and unit-tested, run
   only on an always-on host (D-009).
5. Acceptance: manual spot-check of 20 matches against source; re-import creates no duplicates.

## Known problems and open dependencies

The "Open: needs the owner" table at the top of DECISIONS.md is the master list. Summary:

1. **football-data.co.uk terms** (D-013): its data page says use is "intended for private
   individuals only, NOT commerical or data training products using automated
   bots/scrapers/AI". Until the owner decides, M1 imports from a local drop folder the owner
   fills by hand; the automatic downloader stays off.
2. **Pinnacle unreliable from 23 Jul 2025** and absent from 2026/27 files (D-014): proposed
   Betfair Exchange closing as the benchmark from that date; needs owner sign-off.
3. **Live provider key** (D-010): Sportmonks recommended, starting with its free plan.
   API-Football could not be verified (HTTP 403 to every fetch method).
4. **Always-on host for scheduled jobs** (D-009): coding sessions are ephemeral and GitHub
   Pages is static, so neither can run odds capture or settlement.
5. **2026/27 has already started.** football-data's 2026/27 files, probed (not imported) on
   19 Oct 2026, already hold 60 Premier League, 78 La Liga, 60 Serie A, 45 Bundesliga and
   54 Ligue 1 matches, the latest on 11–12 Oct 2026. The genuine pre-match record can only
   begin when M7 is live; earlier 2026/27 matches are retrospective and will be labelled so.

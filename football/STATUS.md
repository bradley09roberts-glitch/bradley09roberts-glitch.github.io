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
| CI | Workflow added (`.github/workflows/football-ci.yml`); first run happens on push |
| Provider research | Written: DECISIONS.md D-010 and `docs/research/` |

## Known problems and open dependencies

See the "Open dependencies" section of DECISIONS.md. Summary:

1. **Live provider key**: owner to choose and supply (D-010).
2. **Always-on host for scheduled jobs** (odds capture, refreshes, settlement): cloud coding
   sessions are ephemeral and GitHub Pages is static, so neither can run them (D-009).
3. **2026/27 has already started.** football-data's 2026/27 files, probed (not imported) on
   19 Oct 2026, already hold 60 Premier League, 78 La Liga, 60 Serie A, 45 Bundesliga and
   54 Ligue 1 matches, the latest on 11–12 Oct 2026. The genuine pre-match record can only
   begin when M7 is live; earlier 2026/27 matches are retrospective and will be labelled so.

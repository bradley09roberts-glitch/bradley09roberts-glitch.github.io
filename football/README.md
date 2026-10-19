# Football model

Prediction and backtesting for the Premier League, La Liga, Serie A, Bundesliga and Ligue 1,
covering match result (1X2), total goals (O/U 1.5 / 2.5 / 3.5) and both teams to score.

> **Status: M0 (setup).** No data has been imported and no model has been fitted. The site
> shows no predictions, accuracy or profit figures, and will not until those numbers come
> from real data through the milestones in [STATUS.md](STATUS.md).

## What this is (and isn't)

- Measured, reproducible probabilities and walk-forward backtests, compared against simple
  baselines and against de-vigged closing odds on the same fixtures.
- Not a tipping service. No win rate or profit is promised; if the model doesn't beat the
  closing market, the site says so first.

## Layout

| Path | What |
| --- | --- |
| `src/footy/` | Python package: config, database, API (more arrives per milestone) |
| `migrations/` | Alembic migrations (SQLite by default, Postgres via `FOOTY_DATABASE_URL`) |
| `tests/` | pytest suite |
| `frontend/` | React + Vite + TypeScript site (mobile-first) |
| `docs/research/` | Source and provider research with links and retrieval dates |
| `STATUS.md` | Current milestone, coverage, known problems, exact resume commands |
| `DECISIONS.md` | Every open choice in the brief, the decision taken and why |

## Run it

Needs Python 3.12, [uv](https://docs.astral.sh/uv/) and Node 20+.

```bash
cd football
cp .env.example .env            # optional; defaults work without it
uv sync                         # Python deps into .venv
uv run footy migrate            # create/upgrade data/footy.sqlite3
uv run footy serve              # API on http://127.0.0.1:8000  (GET /api/health)

cd frontend
npm ci
npm run dev                     # site on http://127.0.0.1:5173, proxies /api to :8000
```

## Checks

```bash
cd football
make check        # ruff lint + format check, pytest, frontend typecheck + build
```

## Data sources

Historical backbone: [football-data.co.uk](https://www.football-data.co.uk/) season CSVs.
Live provider: chosen in [DECISIONS.md](DECISIONS.md); key supplied by the owner.
Details, coverage and limits: [docs/research/](docs/research/).

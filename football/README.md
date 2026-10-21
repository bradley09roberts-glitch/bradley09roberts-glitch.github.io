# Football model

Prediction and backtesting for the Premier League, La Liga, Serie A, Bundesliga and Ligue 1,
covering match result (1X2), total goals (O/U 1.5 / 2.5 / 3.5) and both teams to score.

> **Status (21 Oct 2026): live.** Genuine pre-match predictions are published and saved
> before kickoff: league games use a model + market blend, UEFA competitions a pan-European
> model. Both were chosen on 2023/24 and tested once on 2024/25 and 2025/26. The league model
> is clearly more accurate than the pure models but does not beat the closing price, so no
> betting edge is claimed. Details and open items: [STATUS.md](STATUS.md); results:
> [docs/results/domestic.md](docs/results/domestic.md).

## What this is (and isn't)

- Measured, reproducible probabilities and walk-forward backtests, compared against simple
  baselines and against de-vigged closing odds on the same fixtures.
- Not a tipping service. No win rate or profit is promised; if the model doesn't beat the
  closing market, the site says so first.

## Layout

| Path | What |
| --- | --- |
| `src/footy/` | Python package: config, database, API, ingest, providers, capture |
| `src/footy/reference/` | Team mapping tables (`teams.csv`, `team_aliases.csv`) |
| `docs/coverage/` | Generated coverage report (counts only) |
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
uv run footy fd-update          # migrate, download (polite, cached) and import football-data
                                #   (core ten + European leagues; --scope core|europe)
uv run footy euro-update        # UEFA competitions into euro_matches (openfootball + Wikipedia;
                                #   --fetch-wikipedia to refresh 2026/27 first)
uv run footy euro-crosscheck    # openfootball against Wikipedia's match boxes (report only)
uv run footy coverage           # coverage per division, season and field
uv run footy serve              # API on http://127.0.0.1:8000  (GET /api/health)
uv run footy capture-odds       # forward odds snapshots (needs a provider key; cron every 5 min)
uv run footy fd-fixtures        # this week's football-data prices (Tue/Fri) for model + market
uv run footy predict-domestic --model market_tilt_shots --dry-run   # league predictions
uv run footy predict-euro --model pan_euro_mix_mxg --dry-run        # UEFA predictions
uv run footy settle             # settle published predictions whose results are in

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
UEFA club competitions: [openfootball](https://github.com/openfootball/champions-league) (CC0)
and English Wikipedia (CC BY-SA 4.0); see [docs/europe/uefa-matches.md](docs/europe/uefa-matches.md).
Live provider: chosen in [DECISIONS.md](DECISIONS.md); key supplied by the owner.
Details, coverage and limits: [docs/research/](docs/research/).

# Football model

Prediction and backtesting for the Premier League, La Liga, Serie A, Bundesliga and Ligue 1,
covering match result (1X2), total goals (O/U 1.5 / 2.5 / 3.5) and both teams to score.

> **Status (22 Oct 2026): live.** Genuine pre-match predictions are published and saved
> before kickoff. For league games with prices, the match result and over/under 2.5 are the
> bookmakers' pre-closing consensus with the margins removed, and the model shapes the other
> markets; without prices a pure model stands in. UEFA competitions use a pan-European model
> with no price input. Both models were chosen on validation 2023/24 and checked on 2024/25
> and 2025/26, a test set that was consulted more than once while choosing. The league model
> beats each of the three pure models tested, but no model here beats the closing price, so
> no betting edge is claimed. Details and open items: [STATUS.md](STATUS.md); results:
> [docs/results/domestic.md](docs/results/domestic.md); the audit of 22 Oct:
> [docs/audit/2026-10-22-audit.md](docs/audit/2026-10-22-audit.md).

## What this is (and isn't)

- Measured, reproducible probabilities and walk-forward backtests, compared against simple
  baselines and against de-vigged closing odds on the same fixtures.
- Not a tipping service. No win rate or profit is promised. Over the two test seasons no
  model beat the closing price: the Fixtures and Performance pages say so in one line, and the
  Lab gives the figures.

## Layout

| Path | What |
| --- | --- |
| `src/footy/` | Python package: config, database, API, ingest, providers, capture |
| `src/footy/reference/` | Team mapping tables (`teams.csv`, `team_aliases.csv`) |
| `docs/coverage/` | Generated coverage report (counts only) |
| `migrations/` | Alembic migrations (SQLite by default, Postgres via `FOOTY_DATABASE_URL`; the write lock that keeps records and settlements single is SQLite-only, so publish on SQLite) |
| `records/predictions/` | The genuine pre-match records, one JSON line each, append-only (`footy import-records` loads them into a new database) |
| `deploy/` | Server kit for an always-on host ([docs/server.md](docs/server.md)) |
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
uv run footy euro-import-results FILE --dry-run   # UEFA results from news reports, two
                                #   publishers each, while Wikipedia is unavailable (D-029)
uv run footy coverage           # coverage per division, season and field
uv run footy serve              # API on http://127.0.0.1:8000  (GET /api/health)
uv run footy capture-odds       # forward odds snapshots (needs a provider key; cron every 5 min)
uv run footy fd-fixtures        # this week's football-data prices (Tue/Fri) for model + market
uv run footy predict-domestic --model market_tilt_shots --dry-run   # league predictions
uv run footy predict-euro --model pan_euro_mix_mxg --dry-run        # UEFA predictions
uv run footy publish-shadows --group all --dry-run   # every other listed model's own records
                                #   (docs/shadow-records.md; --days, --models a,b)
uv run footy settle             # settle published predictions whose results are in
uv run footy doctor             # health check (read-only): jobs, failed downloads, unsettled
                                #   records, records files against the database
uv run footy import-records     # a new or rebuilt database: load records/predictions/*.jsonl
                                #   (ids kept, sha256 checked), then settle; publishing
                                #   refuses until the database holds every published record

cd frontend
npm ci
npm run dev                     # site on http://127.0.0.1:5173, proxies /api to :8000
```

## Static snapshot (a read-only copy for the phone)

With no always-on host, the site can be published as a private, self-contained copy: the
frontend built in snapshot mode plus `snapshot.json` and its shard files, the API's real
responses at one moment (every request the pages can make; a shard is fetched only when a page
needs it). It needs no API server; a banner on every page gives the time it was taken. Re-run
both steps to refresh it. Files, sizes and checks: [docs/snapshot.md](docs/snapshot.md).

```bash
cd football/frontend && npm run build:snapshot     # -> frontend/dist-snapshot (relative paths)
cd .. && uv run footy export-snapshot --out <folder>
# or: make snapshot OUT=<folder>
```

The export reads the database read-only and refuses to write anything if a price-like field
or a secret from `.env` would appear in any file, or if the files break the publishing host's
limits (16 MB a text file, 64 MB and 255 files in all; 12 MB a shard). `<folder>/index.html`
holds only body content (a stylesheet link, the root element and the module script), for
hosts that wrap the page in their own document. Ledger downloads are not included.

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

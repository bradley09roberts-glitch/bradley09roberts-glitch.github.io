# DECISIONS

Every choice the brief leaves open, with the reason. "Owner" marks the decisions that are
yours to make; everything else is decided and the build carries on with it.

## Open: needs the owner

| ID | Question | Default until you decide | Blocks |
| --- | --- | --- | --- |
| D-013 | Do football-data.co.uk's terms allow this use? | Importer reads files you download yourself; automatic download off | Nothing in M1 if you drop the files in; the downloader stays off until you say |
| D-014 | Market benchmark after Pinnacle went unreliable (amends a fixed decision) | Pinnacle before 23 Jul 2025, Betfair Exchange closing after | M3 benchmark gate result for 2025/26 |
| D-010 | Live provider and key | Sportmonks; start with its free plan | M1 adapter tests against real responses; forward odds capture |
| D-009 | Where scheduled jobs run | None chosen | Forward odds capture, M7 |
| D-015 | Pay to test historical prices for O/U 1.5 / 3.5 / BTTS? | No: those markets stay "ROI unavailable", as the brief says | Nothing |

---

## D-001 Project location (2026-10-19)

**Decision:** build in `football/` inside `bradley09roberts-glitch.github.io`, on branch
`claude/football-predictor`. `football/` is the brief's "repo root" (STATUS.md, DECISIONS.md
live here). Not merged to `main`.

**Why:** it is the only repository this session can reach. The root already holds the
FightBase site, which GitHub Pages serves from `main`; a FastAPI backend can't run on Pages,
and merging would publish the source next to FightBase. `data/` and `.env` are git-ignored, so
no downloaded data or keys are ever committed. It can move to its own repo with history
intact: `git subtree split --prefix football -b football-only`.

## D-002 Python toolchain

uv with a committed `uv.lock`; Python pinned to 3.12 (`.python-version`); src layout; ruff for
lint and format, including the `DTZ` rules that reject naive datetimes. **Why:** one command
(`uv sync`) reproduces the exact environment in a fresh session.

## D-003 Migrations from day one

Alembic, with batch mode on SQLite. **Why:** append-only tables and the no-update/no-delete
triggers on published predictions are DDL that must be versioned and tested, and the Postgres
switch needs the same migration history.

## D-004 Frontend

React 19, Vite 8, strict TypeScript, exact versions pinned. Visual checks use Playwright
screenshots at 390 px and 1280 px; component tests are deferred to M6, when there are real
components. **Why:** type-checking catches API-shape drift between backend and frontend early.

## D-005 Time handling

All timestamps are stored as timezone-aware UTC and converted to Europe/London only for
display. football-data's `Date`/`Time` columns will be checked in M1 against a few known
kickoffs before being treated as UK local time.

## D-006 Secrets

Keys only in environment variables (`.env`, git-ignored; `.env.example` committed), held as
`SecretStr` so they never render in logs, reprs or API responses. A test asserts this, and
another keeps `.env.example` in step with the settings.

## D-007 CI

GitHub Actions (`.github/workflows/football-ci.yml`) runs lint, format check, pytest,
typecheck and build on any change under `football/`.

## D-008 Season keys

Displayed as `2025/26`; stored as `2526`, matching football-data's URL scheme.

## D-009 Where scheduled jobs run (Owner)

Forward odds capture (48h / 24h / 2h / 15 min before kickoff), refreshes and settlement need a
machine that is always on. Neither this coding environment (containers are reclaimed when idle)
nor GitHub Pages (static only) can do it.

| Option | Fit |
| --- | --- |
| Small always-on server (VPS) | Best: exact timing, the SQLite file stays put |
| Your own always-on machine (e.g. a Raspberry Pi) | Good, if it stays up and online |
| GitHub Actions cron | Weak: scheduled runs can start late or be skipped under load, which breaks the 15-minute snapshot, and the database would have to live somewhere else |

**Recommendation:** a small VPS or your own always-on machine. Not needed until a provider key
exists.

## D-010 Live provider (Owner: key)

**Recommendation: Sportmonks**, Starter plan (any five leagues) plus the Standard Odds
add-on. Comparison with sources: [docs/research/providers.md](docs/research/providers.md).

- It is the only one of the two whose documentation and pricing could be read. API-Football's
  site returned HTTP 403 to every fetch method, including a real headless browser, so none of
  its facts could be checked.
- Standard odds cover 1X2, goals over/under and BTTS, with bet365 and Pinnacle among the books,
  and give each quote's `latest_bookmaker_update` time. They keep only the latest price, so our
  own append-only snapshots become the record, which is what the brief wants anyway.
- Premium odds (price history, 7 days only, from €129/month and not on Starter) aren't needed.
- Lineups are checked every 5 minutes in the final hour; injuries and suspensions are included.
- **Start with the free plan** (Scottish Premiership, Danish Superliga; no card). With that key
  the adapter and capture job can be built and tested against real responses, including whether
  the 1.5 and 3.5 lines actually come back, before paying for the five leagues.

Published prices on 2026-10-19: Starter €29/month excluding VAT; Standard Odds add-on
"€14-€69 depending on your base plan" (the Starter figure isn't stated). Re-check before buying.

**What I need from you:** create the account yourself, then put the token in `.env` as
`SPORTMONKS_API_TOKEN` and set `FOOTY_LIVE_PROVIDER=sportmonks`. I won't create accounts or buy
anything.

## D-011 xG

Not used in the backtested model. FBref's xG was deleted in January 2026, Understat's
robots.txt disallows all automated access, and no other free source covers 2023–26. The model
uses shots and shots on target. football-data's new, undocumented 2026/27 `HxG`/`AxG` columns
will be recorded in coverage but not used. Details:
[docs/research/historical-data.md](docs/research/historical-data.md).

## D-012 Historical decision time

football-data collects pre-closing odds on Fridays "not later than 17:00" UK time for weekend
fixtures, and on Tuesdays "not later than 13:00" for midweek ones.

**Rule:** fixtures from Friday to Monday take the preceding Friday as their collection day;
Tuesday to Thursday fixtures take the preceding Tuesday.

- **Model cutoff:** 00:00 UK time on the collection day. Only results final before then are
  used. This is stricter than the collection time, so the model can't use a result the prices
  hadn't seen.
- **Odds time:** pre-closing prices are stamped at the documented deadline (17:00 or 13:00).
- **Exclusions:** fixtures whose kickoff falls before their collection deadline, or whose date
  moved after collection, are excluded from staking with a reason.
- **Closing prices** are used only for the benchmark and closing-line value.

## D-013 football-data.co.uk terms (Owner)

The data page says: "its use is intended for private individuals only, NOT commerical or data
training products using automated bots/scrapers/AI." There is no fuller licence.

A personal, non-commercial model built by a private individual looks like the intended use
("development and analysis of football betting systems"). But two things are for you to judge:
that a script, built by an AI agent, downloads the files, and that the results go on a website.

**Default until you decide:** M1's importer reads CSVs from a local drop folder
(`data/inbox/football-data/`), which you fill by downloading the files yourself from the site.
An automatic downloader will exist but stays off (`FOOTY_FD_DOWNLOAD=false`) until you enable
it. Downloaded files are never committed or published (`data/` is git-ignored).

**Disclosure:** for research on 19 Oct 2026, this session fetched football-data's notes, data
and fixtures pages, the five 2026/27 top-division CSVs and two earlier Premier League CSVs. It
used only their column headers and row counts, imported nothing, and has deleted the CSVs.

**Options:**
1. Download the files yourself.
2. Confirm the use is personal and non-commercial, and let the downloader run, politely and
   cached.
3. Email the site's owner for permission.

## D-014 Market benchmark after 23 July 2025 (Owner: amends a fixed decision)

football-data reports that since 23/07/2025 Pinnacle's odds "are systematically out of date
relative to odds for other bookmakers, including both the pre-closing and closing odds", and
the 2026/27 files no longer carry Pinnacle at all. Comparing the model with de-vigged Pinnacle
closing prices across 2025/26 would compare it against stale prices and flatter it.

**Proposed:**
- Fixtures before 23 Jul 2025 (validation 2023/24 and test 2024/25): de-vigged **Pinnacle
  closing**, as the brief fixes.
- Fixtures from 23 Jul 2025 (test 2025/26, and 2026/27): de-vigged **Betfair Exchange
  closing** (`BFEC*`, present in 2025/26 and 2026/27 files) as the primary benchmark. Pinnacle
  is shown alongside with football-data's warning.
- Everywhere: market-average closing (`AvgC*`) as a robustness check.
- For live 2026/27 under D-010: the 15-minute-before-kickoff Pinnacle snapshot captured from
  Sportmonks is a candidate near-closing benchmark, labelled as such.

M3 computes all of these either way. Your decision fixes which one is the headline.

## D-015 Historical prices for O/U 1.5, 3.5 and BTTS (Owner, optional)

The brief's pre-declaration stands: these markets get accuracy backtests, with ROI shown as
unavailable until forward-captured odds exist. Two possible ways to fill the gap, not pursued
without you:

1. **The Odds API** (paid): has historical `btts` and `alternate_totals` market keys after May
   2023 and permits storage and display. Whether it holds them for these leagues and UK/EU books
   is unverified; one paid test query would settle it. Three seasons would cost roughly 100k
   credits per snapshot time (my estimate).
2. **Betfair Exchange historical data**, BASIC tier (free, needs your Betfair account):
   1-minute last-traded prices since 2016. Market coverage and licence couldn't be checked
   from here. ROI from it would be labelled as exchange-price ROI net of commission, never as
   bookmaker ROI.

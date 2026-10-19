# DECISIONS

Every choice the brief leaves open, with the reason. "Owner" marks the decisions that are
yours to make; everything else is decided and the build carries on with it.

## Open: needs the owner

On 2026-10-19 the owner replied "Do what's best" to the five questions raised at the end of
M0. D-013, D-014, D-010 (provider) and D-015 were then decided as recommended. What is left
needs something only the owner can do:

| ID | What's needed | Blocks |
| --- | --- | --- |
| D-010 | Create a Sportmonks account (free plan first) and put the token in `.env` | Adapter contract test against real responses; forward odds capture |
| D-009 | An always-on machine (small VPS or your own) to run scheduled jobs | Forward odds capture; M7 |

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
display.

**Checked in M1:** football-data's `Time` column is the scheduled kick-off in UK local time.
All 18 timed matches in the spot-check agreed with independent sources
([docs/m1/independent-check.md](docs/m1/independent-check.md)). It is the scheduled time, not
the actual start: Brighton v Liverpool on 21 Mar 2026 was listed for 12:30 and kicked off at
12:45. "Closing at kickoff" therefore means closing at the scheduled kickoff.

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

## D-010 Live provider (decided 2026-10-19: Sportmonks; Owner: key)

**Decision: Sportmonks**, Starter plan (any five leagues) plus the Standard Odds
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

## D-013 football-data.co.uk terms (decided 2026-10-19 under the owner's delegation)

**Decision:** option 2. This is a private individual's personal, non-commercial model, which is
the use the site describes ("development and analysis of football betting systems"). It is
not a commercial or data-training product. The downloader therefore runs, with these
safeguards:

- It fetches only the published season CSVs, at most one request every 2 seconds, with a
  plain identifying User-Agent and no contact details.
- Completed seasons are downloaded once and then served from the local raw store; the current
  season is re-fetched at most once every 12 hours unless forced.
- Raw files and anything containing football-data rows or prices are never committed or
  published. Committed reports contain counts only.
- The site stays personal. A public deployment would need the raw prices stripped from
  exports, or the site owner's permission; it is not planned.

`FOOTY_FD_DOWNLOAD=false` switches the downloader off; the importer can always read files from
`data/inbox/football-data/` instead.

**Original analysis:** the data page says: "its use is intended for private individuals only, NOT commerical or data
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

## D-014 Market benchmark after 23 July 2025 (decided 2026-10-19 under the owner's delegation; amends a fixed decision)

**Decision:** adopted as proposed below. The cut-over is by kickoff date: fixtures before
2025-07-23 use Pinnacle closing, fixtures on or after it use Betfair Exchange closing.

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

## D-015 Historical prices for O/U 1.5, 3.5 and BTTS (decided 2026-10-19: no paid test)

**Decision:** neither option below is pursued. Both need the owner's money or account, and the
brief already plans for these markets to show ROI as unavailable. Revisit only if the owner
wants to fund it.

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

## D-016 Which prices are parsed (M1)

Prices are parsed for the five top divisions only; second tiers are used for results and shots
(promoted-team priors). Bookmakers parsed: Bet365 (`B365`, the staking book), Pinnacle (`PS`),
Betfair Exchange (`BFE`) and the market average (`Avg`; Betbrain's `BbAv` before 2019/20,
recorded as `Avg` with the source column kept). Markets: 1X2 and O/U 2.5, pre-closing and
closing. Maximum prices are not stored, because the brief rules out best-price shopping. Asian
handicap is out of scope. The raw files keep everything, so adding a bookmaker later means
re-parsing, not re-downloading.

## D-017 Data-quality rules at import (M1)

Every rejection or flag is written to `import_issues` with its file, row and reason, and is
shown by `/api/import-issues` and the coverage report.

| Rule | Action |
| --- | --- |
| Missing score, unparseable date, missing team, wrong division | Row dropped |
| `FTR` disagrees with the goals | Row dropped (the source contradicts itself) |
| Same pairing twice in one file | Second row dropped |
| Half-time goals above full-time | Half-time values dropped, row kept |
| Price missing, zero, ≤ 1.0 or unparseable | That price dropped |
| A complete set whose implied probabilities sum outside 0.95–1.30 | The whole set dropped (16 sets on 19 Oct 2026, mostly Betfair Exchange O/U 2.5 with sums of 1.4–1.85) |
| Kickoff not after the documented collection time | Kept, flagged; M5 will not stake on it |
| Shots on target above shots | Kept, flagged |

## D-018 Awarded and bare results (M1)

A row with a score but no half-time score and no match statistics, in a file where those
columns exist, is flagged `result_without_stats`. The match gets
`model_exclusion = "result_without_stats"` and is left out of model fitting and evaluation.
It is still shown in coverage, and still counts for anything that needs the official result.
There were 28 such rows on 19 Oct 2026. Their pattern fits results decided off the pitch:
21 bare 1–0 / 0–1 scores in Segunda 2018/19, consistent with Reus Deportiu's expulsion, plus
Serie B and Ligue 2 forfeits and Union Berlin 0–2 Bochum (14 Dec 2024). The Bochum match is
independently confirmed as awarded: it finished 1–1 on the pitch, and the DFB sports court
awarded it 0–2 ([docs/m1/independent-check.md](docs/m1/independent-check.md)).

## D-019 Corrections and revisions (M1)

- When football-data changes a file, the new content becomes a new raw revision. The old
  revision stays on disk and in `raw_files`.
- The importer applies the new values and writes each change to `data_corrections` (old value,
  new value, both revisions). A price that disappears is logged as corrected to nothing.
- Re-importing the same revision changes nothing. Issues are rewritten on each import, because
  they describe what the current parser did.
- Disclosure for the site: historical data was downloaded on 19 Oct 2026 and may include
  corrections made after the matches.

## D-020 Odds-capture schedule semantics (M1)

- **Windows.** Targets are 48 h, 24 h, 2 h and 15 min before kickoff. Each target's window runs
  from its time to the next target's time (to kickoff for the last). Cron runs every 5 minutes,
  and lateness is stored with each attempt.
- **Missed and errors.** A window that closes without a capture is recorded as `missed`.
  Errors are recorded and retried while the window is open. An empty response (`no_odds`) is
  final for that target.
- **Never after kickoff.** Nothing is captured at or after kickoff.
- **Kickoff changes.** Each snapshot stores the kickoff as known at capture time, and kickoff
  changes are listed in the job log. A capture taken before a postponement therefore stays
  attached to the old time. M7 decides how postponed fixtures are treated.

## D-021 Sportmonks adapter details (M1)

Written from the documentation fetched on 19 Oct 2026, not yet against live responses.

- **Auth:** the token goes in the `Authorization` header, never the URL.
- **Fixtures:** `fixtures/between/{start}/{end}` (at most 100 days), filtered with
  `fixtureLeagues`.
- **Odds:** `odds/pre-match/fixtures/{id}/markets/{1,80,14}`.
- **Paging and limits:** cursor pagination. Rate-limit waits over 120 s fail the run rather
  than block cron.
- **League IDs:** Premier League 8, La Liga 564, Serie A 384, Bundesliga 82, Ligue 1 301,
  from the docs' coverage list.
- **What is kept:** half-goal lines 0.5–4.5 only. Stopped prices, quarter lines and unknown
  labels are skipped and counted.
- **Contract test:** `tests/test_sportmonks_contract.py` runs against real responses saved in
  `tests/recorded/sportmonks/`. It is skipped until the owner's key exists.

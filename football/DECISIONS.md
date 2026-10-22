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
| D-023 | Keep or purge the 147 UEFA.com files in the raw store. UEFA's terms (§6.2) forbid scripted collection and using its content to build models; don't run `euro-update --fetch-uefa` meanwhile ([uefa-data-check.md](docs/research/uefa-data-check.md#uefacom-terms-owner-decision)) | Nothing: German Wikipedia covers the one venue that came from UEFA.com |

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

_Amended 21 Oct 2026: prices are now imported for every division (D-022, "Prices")._

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

## D-022 European domestic leagues (2026-10-21)

**Why:** a pan-European club model needs league form for Champions League clubs from outside
the big five. These divisions have `scope = "europe"` in `reference.py`; the core ten have
`scope = "core"` and nothing about them changes (`TOP_DIVISIONS` is still the five core top
divisions, and prices are still parsed for those five only, D-016). _Amended 21 Oct 2026:
prices are now imported for every division; see "Prices" below._

**What is imported (results only until the "Prices" amendment below; 2018/19 or 2018
onwards):**

| Layout | Source | Divisions |
| --- | --- | --- |
| Main (same columns as E0, one file per season) | `mmz4281/{season}/{code}.csv` | SC0 Scotland, N1 Netherlands, B1 Belgium, P1 Portugal, T1 Turkey, G1 Greece |
| Extra leagues (one file per league, every season) | `new/{code}.csv` | AUT, DNK, SWZ (Switzerland), NOR, SWE, POL, ROU, RUS, FIN, IRL |

- The extra-league list is every European league linked from `all_new_data.php` on
  21 Oct 2026 (the others there are Argentina, Brazil, China, Japan, Mexico and the USA).
  Russia is included: its clubs left UEFA competitions in 2022, but its matches still rate
  them, and they faced European clubs until then.
- Extra-league columns: Country, League, Season, Date, Time, Home, Away, HG, AG, Res, then
  closing prices, which are left in the raw file. Main-layout files also give half-time
  scores and match statistics.
- Fetching follows D-013. robots.txt allows a generic User-Agent (it blocks named AI
  crawlers, which these requests are not). Each extra-league file always holds the current
  season, so it is re-fetched at most every 12 hours, with conditional requests. 64 files
  were downloaded on 21 Oct 2026; hand-downloaded copies can go in the inbox as
  `new_<CODE>.csv`.
- Division codes are football-data's. Countries are ISO 3166-1 alpha-3, except Scotland
  (SCO); SWZ is CHE. A club takes its league's country, so Vaduz (Liechtenstein, Swiss
  league) is `che-vaduz`, as Monaco is `fra-monaco`.
- `uv run footy fd-update` covers everything; `--scope core` or `--scope europe` narrows it.

**Season keys for calendar-year leagues** (NOR, SWE, FIN, IRL): the same four characters
with equal halves, so `2525` is the 2025 season, labelled "2025". Split-year keys always have
halves one apart (`2526`), so the two kinds can't collide, and `2525` sorts between 2024/25
and 2025/26.

**Kick-off times** are read as UK local time, as in the main layout (D-005). football-data
doesn't say so for these files, but the data fits it and nothing else. Austrian and Swiss
kick-off slots are the same in summer and winter (e.g. 16:00 and 13:30 for 17:00 and 14:30
local), so the times aren't UTC. Russian slots move by exactly one hour between summer and
winter (17:00 to 16:00), which is what fixed Moscow times (no daylight saving) do in UK time.
There was no independent check against published kick-offs: the Wikipedia API returned
HTTP 429 on 21 Oct 2026.

**Repeat meetings: the schema can't hold them yet (needs a migration).** `matches` is unique
on (division, season, home team, away team). In split leagues, three- and four-round formats
and play-offs, the same home and away teams meet more than once in a season. Those later
meetings are real matches, but they can't be stored. Each is held back with a `repeat_pairing`
error in `import_issues` and stays in the raw files. The same pairing on the same date is still
a `duplicate_fixture`.

On 21 Oct 2026, 4,815 rows were held back:

| Division | Held back | Share of rows |
| --- | ---: | ---: |
| SC0 | 720 | 39% |
| SWZ | 738 | 45% |
| IRL | 710 | 46% |
| ROU | 623 | 24% |
| AUT | 498 | 31% |
| DNK | 490 | 29% |
| G1 | 395 | 20% |
| FIN | 314 | 21% |
| B1 (from 2023/24) | 215 | 9% |
| POL (2018/19 and 2019/20) | 112 | 5% |

N1, P1, T1, NOR, SWE and RUS lose nothing.

What this means for the data:

- In the affected leagues, past seasons stop early. Scottish Premiership seasons end in
  January, for example.
- 2026/27 is complete so far in every split-year league.
- The calendar-year 2026 seasons are not: Finland (33 rows) and Ireland (80 rows) are already
  missing their most recent matches.

The fix needs a migration, which was out of scope for this task:

1. Add `matches.meeting`: the 1st, 2nd or 3rd meeting of the pairing in that season, in file
   order, default 1.
2. Make the unique key (division, season, home_team, away_team, meeting).
3. Let both parsers keep repeat meetings, then run
   `uv run footy fd-update --no-download --reimport --scope europe`.

The core ten would not change, since their meeting is always 1.

**Fixed on 21 Oct 2026 (migration 0005).** These are the changes:

- **Schema.** `matches.meeting` (default 1) is now part of the unique key (division, season,
  home_team, away_team, meeting). Meetings are numbered 1, 2, 3 in file order, which is date
  order for every repeated pairing in the files.
- **Meetings per division.** Each division has `max_meetings` in `reference.py`, set from the
  largest count in the raw files:
  - 3 for SC0, SWZ, AUT, ROU and FIN;
  - 2 for IRL, DNK, G1, B1 and POL;
  - 1 elsewhere, including the core ten. There, a second row of a pairing is still a
    `duplicate_fixture` (D-017).
- **Rows still rejected.** A meeting beyond the division's limit is held back as
  `repeat_pairing`. The same pairing on the date of a kept meeting is a `duplicate_fixture`.
  The importer counts meetings after names are mapped to teams, so a merged club's two
  spellings count as one pairing.
- **Re-import.** Parsers are now fd-4 and fdx-2. The re-import stored all 4,815 held-back rows
  (the table above), and none is held back now.
- **Core ten.** Match and price counts, data hashes, import issues and the 20-match
  spot-check are unchanged.
- **Coverage.** "Expected" is every ordered pairing once, plus each further meeting stored,
  plus play-off matches against visitors. Where pairs meet once, that is
  teams × (teams − 1). For a split season in progress it is a lower bound.
- **Settlement.** A prediction in a division with more than one meeting is settled only by the
  meeting on its own date.
- **Same-league European play-offs.** These were held back before only because they repeated
  a pairing; they are now stored like the ones that weren't. An example is the one third
  meeting in each Austrian season from 2018/19 to 2023/24, about a week before its last match.

**Play-off visitors.** The extra-league files also hold promotion and relegation play-offs
against lower-division clubs, and, in Ireland, the First Division's own play-offs. A visitor
is a club with at most 4 matches in a season, and at most a fifth of the median club's count,
in a season where the median club has played at least 10. Its matches are stored, flagged
`playoff_vs_outside_club` (a warning) and excluded from models
(`model_exclusion = "playoff_vs_outside_club"`). The reason is that the source doesn't say
whether play-off scores include extra time. 116 matches were flagged on 21 Oct 2026, and all
65 visitor club-seasons were checked by hand to be lower-division clubs.

Two limits remain:

- European play-offs between clubs of the same league can't be told apart from league
  matches, and they are kept. Examples are Austria until 2023/24, Denmark and Finland. Most of
  them are repeat meetings, so they are held back anyway.
- Two Swiss rows carry the League label "Challenge League" (the 2020/21 relegation play-off).
  This is a file-level warning only.

D-017 and D-018 apply unchanged otherwise. For example, 31 bare Süper Lig results in 2022/23
(matches awarded after Hatayspor and Gaziantep withdrew following the earthquake) are excluded
as `result_without_stats`.

**Team mapping.** There are 394 new teams and 401 football-data aliases, keyed
`<country>-<slug of the name>` as for the core leagues.

The near-duplicate review used difflib (ratio at least 0.6, or one name contained in the
other) within each country. That flagged 128 pairs. Each was checked against the fixtures:
did the two names ever meet, and did they appear in the same season?

Seven were merged as the same club:

| Names | Kept as | Reason |
| --- | --- | --- |
| Ham-Kam / HamKam | `nor-hamkam` | Spelling |
| Gornik Z. / Gornik Zabrze | `pol-gornik-zabrze` | Spelling; both appear in 2022/23 and never meet |
| Din. Bucuresti / Dinamo Bucuresti | `rou-dinamo-bucuresti` | Spelling; both appear in 2025/26 and never meet |
| U Craiova / Univ. Craiova | `rou-univ-craiova` | Name switches on 14 Feb 2021, mid-season |
| Viitorul Constanta / Farul Constanta | `rou-farul-constanta` | Viitorul took the Farul name in 2021 |
| Waasland-Beveren / Beveren | `bel-beveren` | Renamed SK Beveren in 2022 |
| Erzurum BB / Erzurumspor | `tur-erzurumspor` | Renamed Erzurumspor FK in 2022 |

Similar names kept apart as different clubs: U Craiova 1948 and Univ. Craiova (they met 6
times), Lausanne and Lausanne Ouchy, Wisla (Kraków) and Wisla Plock, Zaglebie (Lubin) and
Zaglebie Sosnowiec, AVS and Aves, HIFK and HJK, Oster and Ostersunds, Farense and Feirense,
Dundee and Dundee United, Cercle Brugge and Club Brugge, Dynamo Makhachkala and Anzhi.

A merged club can appear under both names in one season. Where that would repeat a pairing
of teams, the importer holds the row back as `repeat_pairing` too.

**Prices (21 Oct 2026, parsers fd-5 and fdx-3; amends D-016).** Prices are now imported for
every division, not only the five top divisions. The reason is market-implied expected goals
for the UEFA ratings (`footy.modeling.market_xg`).

- **What is imported.** The books and markets are the same as in D-016: Bet365, Pinnacle,
  Betfair Exchange and the market average. Maximum prices are not stored.
  - Main-layout files (the five core second tiers and SC0, N1, B1, P1, T1, G1) have the same
    columns as E0. Their pre-closing and closing 1X2 and O/U 2.5 prices are imported, with
    the D-012 quote times. The D-012 collection rule covers these files: football-data's
    collection note is about the main-layout download files, and the stored `fixtures.csv`
    carries E1, E2, E3 and SC1 too.
  - Extra-league files have closing 1X2 columns only (PSC, MaxC, AvgC, BFEC, B365C; Russia
    has no BFEC or B365C). They are stamped at kickoff. They have no pre-closing or O/U
    prices. A pre-closing column would be ignored even if one appeared, because the D-012
    collection times are not documented for these files.
- **Checks.** D-017 applies unchanged. The re-import dropped and logged 938 sets with
  implausible overrounds (925 main-layout, mostly Betfair Exchange pre-closing sets from
  thin lower-league markets; 13 extra-league market-average sets), plus 3 invalid prices.
  282 more fixtures were flagged `kickoff_before_collection`.
- **Rows added.** 1,038,569 price rows in total. Closing 1X2 now covers 49,999 matches outside
  the top five.

  | Divisions | Rows added | Of which closing 1X2 |
  | --- | ---: | ---: |
  | E1, SP2, I2, D2, F2 | 495,469 | 146,832 |
  | SC0, N1, B1, P1, T1, G1 | 413,170 | 122,828 |
  | AUT, DNK, SWZ, NOR, SWE, POL, ROU, RUS, FIN, IRL (closing 1X2 only) | 129,930 | 129,930 |

- **Top five unchanged.** Their match and price rows (ids included) and import issues hash
  the same before and after. The 20-match spot-check still finds 0 mismatches; its five
  second-tier matches now also compare their prices (1,034 values).
- **Revisions.** A plain import no longer applies a revision that is older than one already
  imported by the current parser version. After a `--reimport`, applying the older 2026/27
  revisions on their own would have put stale values back.
- **Market expected goals.** Every match with a closing 1X2 price (Pinnacle, Bet365 or the
  average) now gets market xG: 64,627 of 64,628. The exception is Sochi v Rostov, 2020, a
  10–1 against Rostov's youth team, whose 1X2 needs more goals than the solver allows.
  - Matches with O/U 2.5 keep the exact three-parameter solve (39,905).
  - The other 24,722 are solved from the 1X2 alone, with rho fixed at the median of the
    exact solves: −0.0797 over the 16 divisions with O/U prices, computed on each call. This group is the
    extra leagues, 2018/19 in the main layout and 27 matches whose O/U can't be matched.
  - On matches that have both markets, the 1X2-only solve gives the exact solve's goal
    difference to 0.014 goals (mean absolute error), and each side's expected goals to
    0.05–0.06. The total is the least certain part (0.11).
- **UEFA validation 2023/24** (364 fixtures; test seasons not run). The composite is
  0.9593 for `market_alpha` 0.25 and 0.9589 for 0, against 0.9613 for 0.25 with top-five
  prices only. The goals-only mixture is unchanged at 0.9624.

## D-023 UEFA club competitions (2026-10-21)

**Why:** a pan-European model needs every Champions League, Europa League and Conference
League result, with each club as the same team as in its domestic league. Details, counts and
check results: [docs/europe/uefa-matches.md](docs/europe/uefa-matches.md).

**Sources, in order of use** (all stored in the raw store before parsing; `uv run footy
euro-update` loads them as one idempotent job):

| Seasons | Source |
| --- | --- |
| 2018/19 to 2025/26 | openfootball/champions-league (CC0), from a local clone; source `openfootball`, keys `<season>/<file>` |
| 2025/26 UEL and UECL main tournaments | English Wikipedia league-phase, knockout-phase and final articles (openfootball has only their qualifying rounds) |
| 2026/27 | English Wikipedia, via `data/europe/uefa_2627.csv` (built from stored pages; [uefa-2627.md](docs/europe/uefa-2627.md)) |

Not available: the Europa League before 2020/21 and qualifying rounds before 2024/25.

**Rules.**

- Scores are regulation time. openfootball's `a.e.t. (r, h)` gives the regulation score as
  the first pair; on Wikipedia it is rebuilt from the goal minutes. Extra time and penalties
  are flags.
- openfootball times are CET/CEST, except the three 2025/26 qualifying files, which are UK
  time. The cross-check found these files exactly one hour off in 425 of 430 matches, and
  every other file agrees with CET/CEST.
- Awarded results: status `awarded`, `model_exclusion = "awarded"` (4). Cancelled fixtures
  (RB Leipzig v Spartak Moscow, 2022): status `cancelled`, no score, excluded (2). A knockout
  tie whose extra time or penalties contradict its scores: `tie_inconsistent` (none remain).
- Neutral: finals; the 2019/20 Lisbon final tournament; and any match whose Wikipedia stadium
  line names another country than the home club's (relocated Ukrainian, Belarusian and Israeli
  clubs, COVID-era moves). Welsh clubs at grounds just over the border are not neutral.

**Second source.** Every openfootball match (3,371) was paired with a Wikipedia match box.
Scores agree for all but 2, which openfootball listed without their extra time (PSV v
Juventus and Inter v Barcelona, 2025), and flags for all but 3. Where they disagree and
Wikipedia's box adds up, Wikipedia's regulation score, flags and date are taken; each change
is an `import_issues` warning (`corrected_from_wikipedia`). Kick-off times are not overridden.

**Team identity.** Names resolve only through `reference/team_aliases.csv` (sources
`openfootball` and `wikipedia`, each name with its country code). There is no fuzzy matching
at load time; an unknown name is reported and its match left out. Existing clubs keep their
keys (matched by a hand-checked list); 200 new clubs from countries without a football-data
league get `<country>-<slug>` keys. A club takes its league's country (Monaco `fra-monaco`,
Vaduz `che-vaduz`). `prt-torreense` is the one club of a football-data country with no
football-data alias (a second-division club in the 2026/27 Europa League).

## D-024 football-data `fixtures.csv` for live model + market predictions (2026-10-21)

**Why:** football-data's pre-closing prices (collected Friday 17:00 or Tuesday 13:00 UK,
D-012) predict results much better than any model built here (validation 2023/24, five
leagues: RPS 0.1873–0.1876 for Bet365, Pinnacle and the market average, against 0.1948 for
Dixon-Coles and 0.1928 for the best lab model). A model that blends them in can only be used
live if the same kind of price is available before kickoff. football-data publishes exactly
that in `fixtures.csv`, on the same page and under the same terms as the season files.

**Decision:** extend D-013 to this one file, with the same safeguards: the polite client,
fetched at most once every 6 hours unless forced (`uv run footy fd-fixtures`), stored once
in the raw store, never committed or published raw. A published prediction that used the
prices records the raw file's SHA-256, its retrieval time and the consensus probabilities
it used, not the prices themselves. The pure model keeps its own records, so the
model-against-market comparison stays meaningful.

**Note on the back-up domestic records of 21 October:** their `notes` field cites "D-020" for
the rule that the current primary domestic model publishes while the lab runs. That number
is wrong (D-020 is the odds-capture schedule); the rule is D-026 (models of record).
The records are immutable, so the note stays as written.

## D-025 Model selection: one composite on validation (2026-10-20, recorded 2026-10-21)

**Why:** the brief fixes the primary metrics (RPS for 1X2, log loss for the binary markets)
but not how to choose between models that win on some and lose on others.

**Decision:** one composite, pooled over the five leagues on the fixtures every compared model
predicted: the mean, over 1X2 RPS and the log losses of O/U 1.5, 2.5, 3.5 and BTTS, of
(model metric ÷ frequency-baseline metric). Lower is better; the frequency baseline scores 1.
The model with the lowest composite on validation (2023/24) is selected and then run once on
test (2024/25 and 2025/26). Accuracy is always reported alongside but does not decide. The
UEFA models use the same composite over UEFA matches. A builder's own search inside a model
family (step sizes, priors, decay) uses seasons before 2023/24 only. Variants that are put
forward as named candidates, such as different `market_alpha` or mixture weights, are
compared on validation like any other candidates; the one selected is the only one tested.
Every candidate compared on validation is listed with its number, so the size of the search
is visible.

**Numbering note:** the code and the lab modules written before this entry call this rule
"D-022". D-022 is the European domestic leagues. The references in the code were corrected
to D-025 on 21 Oct 2026; backtest manifests saved before then keep "D-022 composite" in
`selection_rule`.

## D-026 Models of record (2026-10-21)

**Rule:** each competition group has one model of record, named in
`src/footy/reference/primary_models.json`. It is chosen by D-025 on validation and confirmed
once on test, before the kickoff of any fixture it headlines. Other models' genuine
pre-match records stay saved and visible; they are never deleted or re-labelled. When a
better model is selected, it publishes its own records alongside, and the switch applies
only to fixtures whose records were all made before their kickoffs.

**UEFA competitions:** `pan_euro_mix_mxg` from 21 Oct 2026, 04:30 UTC. It is the 70/30 grid
mixture of pan-European Dixon-Coles and Elo, as before, but the Dixon-Coles part fits each
top-five domestic match to 0.25 × goals + 0.75 × the expected goals implied by its closing
prices (`footy.modeling.market_xg`), with a stronger team ridge (10).

| UEFA matches (all three competitions) | Validation 2023/24 composite | Test 2024/25+2025/26 composite | Test RPS | Test UCL RPS | Test 1X2 accuracy |
| --- | --- | --- | --- | --- | --- |
| `pan_euro_mix_mxg` | 0.9613 | 0.9666 | 0.2026 | 0.1964 | 55.5% |
| `pan_euro_mix` (previous) | 0.9624 | 0.9686 | 0.2030 | 0.1962 | 55.4% |
| Frequency baseline | 1.0000 | 1.0000 | 0.2313 | 0.2376 | 49.4% |

Both rows use the same data (after migration 0005). Six market-trained variants scored within
0.0005 of each other on validation, so the gain is small. It held on test, mostly in the
totals and BTTS. The previous model's records for 21–22 October stay saved.

**Domestic leagues:** `market_tilt_shots`, then `dixon_coles` where a fixture has no
prices yet, from 21 Oct 2026, 07:01 UTC (`dixon_coles` alone before). Both model + market
candidates (D-024) were validated officially; by D-025 the selected one is
`market_tilt_shots` (the shots model's grid tilted fully to the pre-closing consensus), and it
alone was tested. The fallback `dixon_coles` was tested in the same run.

| Five leagues | Validation 2023/24 composite | Test composite | Test RPS (95% CI) | Test 1X2 accuracy |
| --- | --- | --- | --- | --- |
| `market_tilt_shots` | 0.9449 | 0.9555 | 0.1959 (0.1917–0.1999) | 53.4% |
| `market_tilt_dc` (not tested) | 0.9452 | – | – | – |
| `shots_xg_sot_joint_rw` (not tested) | 0.9552 | – | – | – |
| `dixon_coles` | 0.9598 | 0.9693 | 0.2019 (0.1981–0.2057) | 52.3% |
| Frequency baseline | 1.0000 | 1.0000 | 0.2313 | 43.0% |

It beats `dixon_coles` in every league and both test seasons (paired block bootstrap on RPS:
−0.0060, 95% CI −0.0075 to −0.0045). It does not beat the closing price: 1X2 RPS +0.0006
(−0.0002 to +0.0013), O/U 2.5 log loss +0.0035 (+0.0011 to +0.0058). The profit simulation
finds almost nothing to bet (one test bet), as expected for a forecast made of the same prices.
Full report: [docs/results/domestic.md](docs/results/domestic.md).

After the test run, the model lab fixed an edge case in `shots_xg.py` (frozen parameters of a
statistic with no data). Re-running `market_tilt_shots` on Premier League 2025/26 with the
fixed code gives identical 1X2 and O/U 2.5, and O/U 1.5, 3.5 and BTTS within 0.00002, so the
tested model is the one in use. A record for Levante v Athletic Club (21 Oct) was published
under `market_tilt_shots` before the switch.

**UEFA, round 3 (21 Oct 2026).** `pan_euro_mix_mxg` stays the UEFA model of record. Its
market expected goals are pinned to `market_scope="top5_exact"` (the five top divisions, the
exact 1X2 + O/U 2.5 solve, Pinnacle, Bet365 and average closing on every date), the targets it
was selected and tested on: a read-only refit reproduces all 45 records of 21–22 Oct exactly.
Without the pin, all-league prices imported later would have moved its 1X2 by up to 0.031.
UEFA models now publish by name (`footy predict-euro --model pan_euro_mix_mxg`,
`footy.modeling.euro_registry`), and a name's configuration never changes once it has
records. A candidate trained on every league's market expected goals, with Pinnacle dropped
from 23 Jul 2025 (D-014), led on validation (best variant `pan_euro_mix_mxg_all_ma0_w0.8`:
0.9588 against 0.9613) but not on test (0.9672 against 0.9666; UCL RPS 0.1972 against
0.1964; worse on the composite in both test seasons), so nothing switches. The pinned scope
still averages Pinnacle's closing prices after 23 Jul 2025, which D-014 calls stale; dropping
them did not help on test, so this is accepted for this model.

**Effective-from times (21 Oct 2026).** Each group in `primary_models.json` is now a list of
periods, `{"from_utc": ..., "models": [...]}`, oldest first (`footy.models_of_record`). A
fixture takes the latest period starting at or before its kickoff (00:00 UK on its date if
the kickoff is unknown): the earliest kickoff saved with any of its genuine pre-match records,
so a kickoff brought forward after the first record still counts. Within that period,
the record of record is the first listed model with a genuine pre-match record. A plain name
or list is still read as one period from the start.

- **Converted file.** UEFA is `pan_euro_mix` until 2026-10-21T04:30:00Z, then
  `pan_euro_mix_mxg`; domestic is `dixon_coles` throughout. All 155 saved records have the
  same record-of-record flag as before (109 primary).
- **Switching.** Use `uv run footy set-model-of-record --group domestic --models
  market_tilt_shots,dixon_coles --from now` (names are checked against the registered models). It appends a period and refuses a start before now,
  or one not after the group's last period. A fixture that has already kicked off keeps its
  model of record, so a switch never changes the settled record or its accuracy.
- **What "from now" covers.** Fixtures that haven't kicked off yet take the new period,
  including ones whose records were published before the switch. A fixture on the day of the
  switch whose records have no kickoff time keeps the old period (00:00 UK stands in).

## D-027 Switching a model of record; the round-4 outcome (2026-10-21)

**Switching rule.** A challenger replaces a model of record only if it has the lowest D-025
composite on validation *and* is at least as good on test (composite, same fixtures) as the
model it would replace. Otherwise the incumbent stays. The test run still happens once per
model; this only stops a model that wins validation by chance from displacing one that has
already been confirmed. It is the rule applied to UEFA in round 3 (D-026).

**Size of the search, so the selection's optimism can be judged.** About 50 pure models have
validation ledgers (the lab's eight families and their variants, the ensembles, the
baselines). Five model + market candidates were compared (`market_tilt_dc`, `_shots`, `_ctx`,
`_elo`, `_ens`). Nine UEFA configurations were compared over rounds 2 and 3.

**Round 4 (model lab finished, every family audited):**

| Five leagues | Validation 2023/24 | Test 2024/25+2025/26 | Decision |
| --- | --- | --- | --- |
| `market_tilt_ctx` (base `congestion_context_on_elo`) | 0.9441 | 0.9559 | not adopted: worse on test |
| `market_tilt_shots` (model of record) | 0.9449 | 0.9555 | stays |
| `congestion_context_on_elo` (pure) | 0.9517 | 0.9661 | new fallback |
| `dixon_coles` (previous fallback) | 0.9598 | 0.9693 | last resort |

- `congestion_context_on_elo` against `dixon_coles` on test: RPS −0.0019 (95% CI −0.0029 to
  −0.0008), composite −0.0032 (−0.0068 to +0.0003). It is better in all five leagues and both
  seasons, and on the Championship 2023/24 (0.9815 against 0.9874, not used for selection).
- **Caveat:** its base, `elo_goals_gd`, was tuned on the mean of 2022/23 and 2023/24, against
  D-025's rule that a family's own search stays before 2023/24. Its validation margin is
  therefore flattering; the test result is the clean evidence, and there only the RPS gain is
  significant. Until mid-season its context features are inactive, so it forecasts exactly
  like `elo_goals_gd`.
- `primary_models.json`, domestic from 2026-10-21T10:52:18Z: `market_tilt_shots`, then
  `congestion_context_on_elo`, then `dixon_coles`. 69 fallback records were published first.

**Live history window (bug fixed).** Live league predictions had been fitted on every result
since 2018/19, while the backtests use the last three years (`walkforward.HISTORY_DAYS`), so
live forecasts were not exactly the tested models. Live runs now use the same window
(`walkforward.training_history`). The 64 `dixon_coles` records and the two Levante model +
market records published on 21 Oct before the fix used all history; they stay as published.
For the Levante records only the shape markets (O/U 1.5, 3.5, BTTS) could be affected, since
1X2 and O/U 2.5 come from the prices. UEFA models were unaffected (their fit applies its own
window, and the pinned model reproduces its records exactly).

**Migrations.** Unattended jobs (`capture-odds`, run every 5 minutes, and `fd-fixtures`, run by
scheduled check-ins) no longer migrate the database; they refuse to run until someone backs
up `data/` and runs `uv run footy migrate`. Migration 0006 (distinct names for the two
`raw_files` unique keys) had been applied by the capture loop before a backup was taken;
row counts, raw-file hashes, integrity and the spot-check were unchanged afterwards.

## D-028 Machine learning on top of the models; no switch (2026-10-22, proposed)

**Status: proposed.** For the lead and the owner to accept; nothing in
`primary_models.json` has changed.

**Why:** the owner asked to "add machine learning so it learns stuff and hopefully gets
better". Every model here is already refitted at each decision batch, and the `ml_gbm` family
(D-025 composite 0.9564 on validation) had lost to the models of record. What was missing
was a learner that learns from the models' own out-of-sample track record and keeps learning
as results arrive. Full write-up: [docs/research/machine-learning.md](docs/research/machine-learning.md).

**Built** (`footy.modeling.lab.ml_stream`, `ml_online`, `ml_stack`):

- **A stream of every member model's forecasts.** Each match's forecast is made at its own
  decision time, with prices only under `MarketTilt`'s rules. This is what the learners train
  on, inside the walk-forward run.
- **`ml_online_*`: mixture weights over the members' score grids,** updated by an
  exponentiated-gradient (Hedge) step on the D-025 loss after every settled match.
- **`ml_stack_*`: ridge logistic meta-models** (multinomial for 1X2) that correct a reference
  forecast, made one coherent grid by a six-market exponential tilt.

Every setting was chosen on 2021/22 and 2022/23 only: about 600 mixture and 100 stacker
settings. Four named candidates went to validation.

| Five leagues | Validation 2023/24 | Target on validation | Test 2024/25+2025/26 | Target on test |
| --- | --- | --- | --- | --- |
| `ml_online_market` (prices) | 0.94455 | `market_tilt_shots` 0.94488 | 0.95574 | 0.95546 |
| `ml_stack_market` (prices) | 0.94484 | 0.94488 | not run (not selected, D-025) | – |
| `ml_stack_pure` (no prices) | 0.95386 | `congestion_context_on_elo` 0.95174 | not run (lost validation) | – |
| `ml_online_pure` (no prices) | 0.95421 | 0.95174 | not run (lost validation) | – |

- **On validation,** `ml_online_market` beat its target by 0.00033 (95% CI −0.00116 to
  +0.00055), so it had the one test run.
- **On test it was worse by 0.00028** (95% CI −0.00024 to +0.00079): RPS identical, and worse
  on O/U 1.5, O/U 3.5 and BTTS. With prices, the learner can only choose the grid's shape. It
  mostly chose the shapes of `congestion_context_on_elo` and `ml_gbm_glm_boost`, and the shots
  model's shape did better on test (as with `market_tilt_ctx` in D-027).

**Recommendation under D-027: no switch.**

- **With prices:** `market_tilt_shots` stays. The challenger is not at least as good on test.
  Strictly, it was also never the lowest validation composite of every candidate compared,
  since `market_tilt_ctx`, `_elo` and `_ens` scored 0.9441–0.9444 in round 4.
- **Without prices:** `congestion_context_on_elo` stays. Neither learner beat it on validation.
- **What stays in the lab.** The four candidates remain registered, with their ledgers in
  `data/backtests/ml_*`. A month-by-month learning curve for a website panel is in
  `data/reports/ml_learning_curve.json`, with a summary in `data/reports/ml_selection.json`.

**Audit (22 Oct 2026).** The recommendation stands; every number above is confirmed.

- **Reproduced exactly.** The runner re-ran `ml_online_market` on validation in all five
  leagues and `ml_stack_market` in the Bundesliga: every forecast matched the saved ledgers
  (largest difference 4e-16). `ml_online_market` on the Bundesliga's test seasons did too.
- **No leakage in the reported runs.** Prices are pre-closing only, quoted by each match's
  deadline and before kickoff. The weights and meta-models learn only from matches final
  before each cutoff. Every market comes from one grid that sums to 1.
- **One latent leak fixed.** A mixture fitted at an earlier cutoff than its last fit kept the
  weights it had learned from later matches. Neither the runner nor `footy.live` goes back in
  time, so no number changed. A test now covers it.
- **Caveats added.**
  - **Member tuning:** all four candidates use `congestion_context_on_elo`, whose base was
    tuned on 2022/23 and 2023/24. The shots model was tuned on 2022/23. So the ML search
    seasons and validation flatter learners that trust these members. Test is clean.
  - **Chosen settings:** the two mixture settings are near-ties, within 0.00002 of the best
    two-season mean, rather than the best.
  - **Extra test runs:** the test seasons were run twice more, unchanged. One run read the
    weights and the other was the audit's reproduction.
  - **Collapsed weights:** on validation the mixture with prices put all its weight on the
    GLM booster in the Premier League and Ligue 1.

## D-029 UEFA results entered by hand from news reports, while Wikipedia is unavailable (2026-10-22)

**Why:** since 18:49 UTC on 21 Oct 2026 en.wikipedia.org has refused this machine (HTTP 403 in
`fetch_log`; audit M3), and Wikipedia is the only permitted source of 2026/27 UEFA results
(D-023: openfootball has no 2026/27 folder, UEFA.com's terms forbid its use). Nine Champions
League fixtures with published genuine pre-match records were left unsettled, and the Europa
League and Conference League followed on 22 Oct. Their final scores were already known from
news reports (`comparisons/results_2026-10-21.json`, three or more publishers per game), but
nothing let them reach `euro_matches`. A results source that settles our own records has to
be strict, because a wrong score would be settled for good (settlements are append-only).

**Decision:** `uv run footy euro-import-results FILE [--dry-run] [--correct]`
(`footy.europe.manual_results`; details in
[docs/europe/uefa-matches.md](docs/europe/uefa-matches.md)).

**Rules.**

- **Two independent publishers per score**, with different names on different web domains,
  each with the URL where the score was read. A source that states a different score refuses
  the entry. UEFA.com is never a source (D-023).
- **Only fixtures already in `euro_matches`**, kicked off at least two hours earlier, and not
  postponed, cancelled or awarded. The import never creates a fixture. League games are
  skipped (football-data settles them).
- **The score after 90 minutes plus stoppage time**, as everywhere in `euro_matches`; extra
  time and penalties are flags.
- **Provenance:** the file is stored in the raw store first (source `manual`). The row is
  marked `source = "manual"`, links that raw revision, and lists every publisher and URL in
  `source_ref`. Settlements made from it are marked `euro_matches:manual`.
- **Never a silent overwrite:** a different stored result is refused unless `--correct` is
  given, and then each changed field goes to `data_corrections` first.
- **Wikipedia has the last word:** when Wikipedia can be fetched again, `euro-update` confirms
  a manual result that agrees (the row becomes Wikipedia's) and replaces one that doesn't, with
  a `data_corrections` entry and a `manual_result_disagrees` error that makes the job
  `partial`. A settlement already made is not changed automatically (audit M7 covers
  corrective settlements).

**Sources used first:** the nine Champions League results of 21 Oct 2026 in
`comparisons/results_2026-10-21.json` (three to five publishers each, among them Sky Sports,
TNT Sports, beIN Sports, Bundesliga.com, Al Jazeera, NBC Sports, Yahoo Sports, Football
Italia, Sporza, RTE and CNN Portugal), all read on 21 Oct at about 23:00 UK. Only the
publishers' facts (the final score) are used; their text is not stored, beyond the URL.

**Not done:** a second automatic results provider (audit improvement); until there is one,
this import is the fallback, run by a person (or a supervised check-in) with `--dry-run`
first.

## Corrections of 2026-10-22 (from the audit of that day)

The entries above stay as written. These notes correct statements in them that the audit
([docs/audit/2026-10-22-audit.md](docs/audit/2026-10-22-audit.md)) found wrong or overstated;
each names the entry and the audit item.

- **Open table, D-010 (audit L9).** The Sportmonks token is already in `.env` (free plan), so
  nothing is waiting on the owner for the free plan. The capture job has stored one capture,
  88 quotes for Randers v Brøndby on 21 Oct, 5.4 hours late; STATUS's "no snapshots yet" was
  false. Its reliability problems are audit M18.
- **D-004 (audit section 5, D8).** "Type-checking catches API-shape drift" is false: the
  frontend's API types are written by hand and the API returns untyped dictionaries, so a
  change in a response's shape is not caught.
- **D-009 (audit L9).** "Not needed until a provider key exists" no longer holds: settling and
  publishing (M7) depend on scheduled jobs, and the key exists. The server kit
  ([docs/server.md](docs/server.md)) is ready; the owner's step is creating the server.
- **D-023 (audit M16).** Until 22 Oct the 147 stored UEFA.com pages were re-read on every
  rebuild and set one model input, the neutral flag of Union Saint-Gilloise v Hapoel Be'er
  Sheva (in Düsseldorf), against the terms quoted here. Since 22 Oct nothing reads them: the
  venue comes from German Wikipedia (`src/footy/europe/venue_overrides.csv`).
- **D-025 (audit M19).** The rule that "a builder's own search inside a model family uses
  seasons before 2023/24 only" was written at 03:43 UTC on 21 Oct, after most of the lab had
  been tuned under a brief that allowed tuning on validation. The searches that also used
  2023/24:
  - `dc_tuning`: about 90 configurations, by coordinate descent on 2022/23 and 2023/24 pooled;
  - `dynamic_ratings`: a search on 2021/22 to 2023/24 pooled, and the blend weights;
  - `elo_goals`: a search on the mean of 2022/23 and 2023/24 (D-027 disclosed it for
    `elo_goals_gd`);
  - `bivariate_dispersion`: its internal search was scored on 2023/24 as well;
  - `ml_gbm`: about 47 configurations were also run on 2023/24 (the chosen ones are best or
    near-best on the earlier seasons);
  - through their bases or members: `congestion_context_*` (`elo_goals_gd`),
    `goal_calibration_tuned_level` (`dc_tuning_tilt`; its own settings did use only 2021/22
    and 2022/23), and the ensembles (`elo_goals_wfe`, `congestion_context_on_elo`,
    `dynamic_ratings_ekf`);
  - the UEFA round-1 search (xi, rt, Elo K; see D-027 below).

  Not affected: `shots_xg` and `MarketTilt`'s weights (2022/23), `dixon_coles` and the
  baselines, so the league model of record's own settings are clean. This is selection bias,
  not leakage: no forecast saw a later result. Its size: `dc_tuning`'s gain over Dixon-Coles
  is −0.0038 on 2023/24 and −0.0016 to −0.0029 on the unused 2021/22, enough to reorder
  candidates about 0.001 apart. "Every candidate compared on validation is listed with its
  number" does not hold for these searches' evaluations.
- **D-026 (audit L1, L21, M20).**
  - "Chosen by D-025 on validation and confirmed once on test": the test seasons were
    consulted more than once (domestic test runs at 06:24 and 10:05 UTC on 21 Oct and 00:18 on
    22 Oct; UEFA at 01:58, 04:17 and 05:43 on 21 Oct). `market_tilt_shots` stays as the
    incumbent under D-027 although five later price candidates scored lower on validation; the
    quoted test figures therefore helped choose.
  - The 45 live `pan_euro_mix` records were published at 01:58 UTC on 21 Oct, before the 4,815
    repeat meetings were imported (03:14). Replayed with them, 1X2 moves by up to 0.0506 (Braga
    v Gent), O/U 3.5 by up to 0.0485 (median 0.0047), and two picks flip. The data
    fingerprint did not change, so only `job_runs` shows it. The records stay as published.
    "Both rows use the same data" refers to the backtest table and remains true.
  - "The profit simulation finds almost nothing to bet": all 72 threshold strategies made no
    bet on validation, `frozen_on_validation` is empty, and no pure model was simulated, so
    milestone M5 is only partly done.
- **D-027 (audit L2, L4, M19, M22, M5).**
  - "Nine UEFA configurations were compared over rounds 2 and 3": about 30 distinct
    configurations (33 run names) were compared over six rounds, all on the same 364
    validation fixtures, and round 1's search was on 2023/24. The deciding gap on validation
    is noise-sized (`pan_euro_mix_mxg` against `pan_euro_mix` −0.0011, 95% CI −0.0041 to
    +0.0018); the switch held on test (−0.0020, −0.0034 to −0.0006). The test set also has 458
    qualifiers that validation never had.
  - "About 50 pure models have validation ledgers": 59 models have composite ledgers on the
    same 1,752 fixtures, and the search evaluations behind them are not counted.
  - `congestion_context_on_elo` "is better in all five leagues and both seasons": true pooled
    within each league and within each season (barely in Ligue 1, 0.97478 against 0.97489).
    League by season its composite is worse in 3 of the 10 cells (D1 2024/25 0.9557 against
    0.9494, F1 2024/25 0.9706 against 0.9703, SP1 2024/25 0.9534 against 0.9518), and its RPS
    in one (E0 2025/26).
  - "On the Championship 2023/24 (0.9815 against 0.9874)": no ledger was saved, so the figure
    can't be regenerated. The Championship has no test ledger either, yet 12 of its
    fixtures have `congestion_context_on_elo` as record of record (audit L5).
  - The paired intervals here and in D-028 came from scratch scripts, not from code in the
    repository; the audit reproduced them exactly with its own code. The backtest manifests
    record only the commit at HEAD (the 06:24 test ran on a "WIP checkpoint" commit).
  - "Migrations": true for `capture-odds` and `fd-fixtures` (and now `import-records` and
    `euro-import-results`), but `fd-update`, `euro-update` and `serve` still migrate on their
    own, and the check-ins run the first two (audit M5, open).
- **D-028 (audit M19).** The member-tuning caveat names `congestion_context_on_elo` and the
  shots model. The GLM booster member (`ml_gbm_glm_boost`) comes from a family that also ran
  about 47 configurations on 2023/24.
- **Model descriptions (audit L19).** STATUS (until 22 Oct) and the site's model catalogue
  describe `congestion_context_on_elo` and `elo_goals_gd` as goal-based Elo. They also use
  shots on target (blend 0.4) and red cards: "Elo on goals and shots on target, with red
  cards, congestion and context".

## D-030 The project moves to a private repository (2026-10-22)

**Decision:** the project lives in the private repository `bradley09roberts-glitch/football-model`
(branch `main`, project in `football/`), not on a branch of the public
`bradley09roberts-glitch.github.io`. Supersedes D-001's location.

**Why:** the audit (M4) found the public repository exposed the records, the docs and the
owner's ChatGPT comparison data, and that D-013's publication rules had assumed a private home.
The owner chose to make it private (22 Oct). Making the github.io repository itself private
would take the FightBase site offline (GitHub Pages needs a public repository on a free plan).

**How:** the branch `claude/football-predictor` (68 commits, head `bc2cd069`) was pushed
unchanged to the private `main` and checked commit for commit; the server's bootstrap archive
(backtest ledgers, reports, lab results and the UEFA CSV: model outputs only, no prices) is on
the private branch `bootstrap-data`. This working copy now has `origin` = the private repository
(local branch `main`) and `public` = github.io (local branch `fightbase-main`). The public branch
is deleted by the owner (the session's permission check refused a destructive push). Anything
copied while the branch was public (19-22 Oct) can't be recalled; the audit found no secret in
the history.

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
divisions, and prices are still parsed for those five only, D-016).

**What is imported (results only, 2018/19 or 2018 onwards):**

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
is wrong (D-020 is the odds-capture schedule); the rule is the one in this entry's
"Primary models" section below. The records are immutable, so the note stays as written.

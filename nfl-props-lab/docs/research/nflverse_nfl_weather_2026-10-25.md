# nflverse / NFL / weather data documentation audit

- **Retrieval date:** 2026-10-25. Pages were fetched between about 02:32 and 02:40 UTC.
- **Method:** I read the pages with curl and converted them to text. I read the nflverse-data README and LICENSE through raw.githubusercontent.com, and the release `timestamp.json` files and sample data files through `github.com/nflverse/nflverse-data/releases/download/...`. github.com HTML pages, including release notes, returned 403 and were **not** read.
- **Labels:** "Doc says" means a quote from the source. "Observed" means I checked it myself in the downloaded data. "Inference" means my own reasoning.

---

## 0. Decision-relevant summary

1. **Licences**
   - The nflverse-data repo licence is **CC-BY 4.0** (`LICENSE.md`).
   - **FTN charting** and **participation data from 2023 onwards** are **CC-BY-SA 4.0**. They must be credited to "FTN Data via nflverse".
   - Participation for 2022 and earlier must be credited to "NFL NextGenStats via nflverse".
   - The nflreadr and nflreadpy code is MIT.
   - Upstream terms can still apply. Sports Reference's ToS (the source for snap counts, PFR advanced stats, and the pbp line, roof and weather columns) bans using its content to support ML prediction. See §2.4.
2. **Timing**
   - **FTN charting is in-season.** It is charted within 48 h of each game and pulled four times a day.
   - **Participation is post-season only.** The NGS source "died during the 2023 season". From 2023 on, FTN supplies participation "after all post-season games are completed". The 2025 file exists; the 2026 file does not.
3. **Depth charts and injuries**
   - Depth charts changed source after 2024, from NFL Data Exchange to ESPN. From 2025 they carry no week; each row has an ISO8601 `dt` timestamp instead.
   - The injuries data is still being updated (daily at 7 AM UTC in season, with 2026 weeks 1–7 present). Its stated source is only "an API".
   - The 2025 and 2026 injury files have **no `date_modified` column**, even though the dictionary lists one.
4. **Betting columns to exclude**
   - Schedules: `away_moneyline`, `home_moneyline`, `spread_line`, `away_spread_odds`, `home_spread_odds`, `total_line`, `under_odds`, `over_odds`.
   - pbp: `spread_line`, `total_line`, `vegas_wp`, `vegas_home_wp`, `vegas_wpa`, `vegas_home_wpa`.
   - The schedules docs do not state where the lines come from. The pbp dictionary says "closing … line (Source: Pro-Football-Reference)".
5. **NFL.com terms**
   - Section 1.3 says: "Systematic retrieval of data or other content from the Services, whether to create or compile, directly or indirectly, a collection, compilation, database, or directory, is prohibited absent our express prior written consent."
   - Section 11(f) separately bans spiders and robots used to collect information "for any commercial purpose".
6. **Weather**
   - Open-Meteo's Historical Forecast API **stitches the first few hours of each model run** into one series. It is archived forecast output, not reanalysis. It is close to an analysis of what happened, **not** a forecast made days before kickoff.
   - For forecasts with a real lead time, use the Previous Runs API or the Single Runs API.
   - The free tier is non-commercial only and requires CC-BY 4.0 attribution.

---

## 1. nflverse data update schedule

Source for every quote in this section: https://nflreadr.nflverse.com/articles/nflverse_data_schedule.html. The page was built from `vignettes/articles/nflverse_data_schedule.Rmd`, nflreadr dev version 1.5.1.9002. The raw Rmd is at https://raw.githubusercontent.com/nflverse/nflreadr/main/vignettes/articles/nflverse_data_schedule.Rmd.

Quotes from the page:

- **PBP:** "we update this on a nightly basis after each game day (and additionally at specific points on game days) during the season." It adds that `nflfastR::build_nflfastR_pbp()` parses raw JSON "usually available within 15 minutes after a game has ended". It also says: "it is recommended to update the data again during the night from Wednesday to Thursday in order to also receive the stat corrections that the NFL will incorporate from Monday to Wednesday at the latest. -> Thursday's load_pbp() is the cleanest data we have."
- **Player and team stats:** "computed on the same schedule as play-by-play data".
- **FTN charting:** "ftn charting data updates every day at 0, 6, 12, 18 UTC during the season. The actual availability of new data depends on the update schedule of FTN."
- **Participation:** "Participation data prior to 2023 is from NFL NGS. The data source died during the 2023 season. Participation data from 2023 onwards is courtesy of FTN and is provided after all post-season games are completed. It does not update during the season!"
- **Schedules:** "Game/Schedule data updates very 5 minutes during the season." The typo "very" is in the original.
- **Rosters:** "Roster data updates every day at 7AM UTC."
- **NGS:** "update every night (in the range of 3 am - 5 am ET) during the season. The actual availability of new data depends on the update schedule of NGS."
- **Snap counts (PFR):** "updates every day at 0, 6, 12, 18 UTC during the season. The actual availability of new data depends on the update schedule of PFR."
- **PFR advanced stats:** same wording as snap counts: 0, 6, 12, 18 UTC during the season.
- **Depth charts:** "Depth Charts update every day at 7AM UTC throughout the year. Please note that the data source has changed after the 2024 season. From 2025 onwards, depth charts are not assigned a week. Instead, each update provides a new ISO8601-formatted timestamp that users can assign to any point in time during the season. Each update is appended to the existing data with a new timestamp."
- **Injuries:** "Injury Data updates every day at 7AM UTC throughout the season."
- **Not covered:** The page gives **no cadence** for contracts, officials, ESPN QBR or players. Contracts and players do appear as rows in its "Automation Status" table. That table is filled in client-side, and its cells were empty in the HTML I fetched.

**Observed last-updated timestamps.** These come from `https://github.com/nflverse/nflverse-data/releases/download/<tag>/timestamp.json`, retrieved 2026-10-25:

| tag | last_updated |
|---|---|
| pbp | 2026-10-24 09:18:53 EDT |
| stats_player | 2026-10-24 09:21:03 EDT |
| rosters | 2026-10-24 07:59:56 EDT |
| weekly_rosters | 2026-10-24 07:59:53 EDT |
| players | 2026-10-23 10:17:10 EDT |
| snap_counts | 2026-10-23 07:32:00 EDT |
| pfr_advstats | 2026-10-23 12:36:01 EDT |
| nextgen_stats | 2026-10-24 07:45:22 EDT |
| injuries | 2026-10-24 07:57:44 EDT |
| depth_charts | 2026-10-24 07:58:03 EDT |
| ftn_charting | 2026-10-24 11:22:31 EDT |
| pbp_participation | **2026-02-10 13:54:06 EST** (post-season drop) |
| contracts | 2026-10-24 07:57:06 EDT |
| officials | **2026-09-02 07:48:27 EDT** (not updated since the season started) |
| espn_data (QBR) | 2026-10-24 09:00:50 EDT |
| schedules | 2026-10-24 22:16:19 EDT |

### Dataset table

The start season comes from each loader's reference page, or from its R source (`stopifnot(seasons >= ...)`) where the page is silent. R sources are at `https://raw.githubusercontent.com/nflverse/nflreadr/main/R/<file>.R`. Reference pages are at `https://nflreadr.nflverse.com/reference/<loader>.html`.

"Repo CC-BY 4.0" means the dataset has no licence note of its own, so only the repo-wide CC-BY 4.0 licence applies (see §2).

| Dataset | Loader (R / py) | Start season | Update cadence (doc) | Upstream source (doc) | Licence | Caveats | URL |
|---|---|---|---|---|---|---|---|
| Play-by-play | `load_pbp` / `nfl.load_pbp` | 1999 | Nightly after each game day, plus points on game days; re-pull Wed→Thu night for stat corrections | "The complete nflfastR dataset as returned by nflfastR::build_nflfastR_pbp()". The page does not literally name the NFL as source (see Unverified). | Repo CC-BY 4.0 | Has the betting-derived `spread_line`, `total_line`, `vegas_wp`, `vegas_home_wp`, `vegas_wpa`, `vegas_home_wpa`. `roof`, `temp` and `wind` are sourced from PFR. Issues go to nflverse-pbp. | https://nflreadr.nflverse.com/reference/load_pbp.html |
| Player stats (and team stats) | `load_player_stats`, `load_team_stats` (both R and py) | 1999 (R source `seasons >= 1999`; the page is silent) | Same as pbp | Computed by `nflfastR::calculate_stats()` (NEWS 1.5.0) | Repo CC-BY 4.0 | `stat_type` is deprecated. `summary_level` takes week, reg, post or reg+post. Output "aims to match NFL official box scores". | https://nflreadr.nflverse.com/reference/load_player_stats.html |
| Rosters (season) | `load_rosters` | 1920 | Daily 7AM UTC | Not stated | Repo CC-BY 4.0 | Defaults to `most_recent_season(roster = TRUE)` | https://nflreadr.nflverse.com/reference/load_rosters.html |
| Weekly rosters | `load_rosters_weekly` | 2002 | Daily 7AM UTC ("Roster data") | Not stated | Repo CC-BY 4.0 | The schedule page says "Roster data" without separating season and weekly. The weekly_rosters timestamp matches the 7AM UTC run. | https://nflreadr.nflverse.com/reference/load_rosters_weekly.html |
| Schedules / games | `load_schedules` / `nfl.load_schedules` | 1999 (observed in games.csv). The nfldata DATASETS.md still says "Data begins with the 2006 NFL season", which is stale. | Every 5 minutes during the season | "game/schedule information as maintained by Lee Sharpe". The R loader reads `nflverse/nfldata` `data/games.rds`; py 0.1.4+ reads the nflverse-data `schedules` release. | Repo CC-BY 4.0 (nfldata repo has no LICENSE file; 404) | **Has betting lines** (§5). `temp` and `wind` are recorded game conditions, which leak information if used pre-game. Issues go to nflverse/nfldata. | https://nflreadr.nflverse.com/reference/load_schedules.html |
| Snap counts | `load_snap_counts` | 2012 | 0/6/12/18 UTC during the season | "provided by Pro Football Reference" | Repo CC-BY 4.0; **upstream SR ToS** (§2.4) | Issues go to nflverse-pfr | https://nflreadr.nflverse.com/reference/load_snap_counts.html |
| Injuries | `load_injuries` | 2009 | Daily 7AM UTC throughout the season | "Data collected from an API for weekly injury report data." The API is not named. | Repo CC-BY 4.0 | One row per player-week (§4). 2025 and 2026 files lack `date_modified`. | https://nflreadr.nflverse.com/reference/load_injuries.html |
| Depth charts | `load_depth_charts` | 2001 | Daily 7AM UTC throughout the year | Source changed from NFL Data Exchange to **ESPN** after 2024 (NEWS 1.5.0) | Repo CC-BY 4.0 | 2025+ schema has `dt` (ISO8601) and no week (§4). The page title still says "Weekly". | https://nflreadr.nflverse.com/reference/load_depth_charts.html |
| Next Gen Stats | `load_nextgen_stats` | 2016 | Nightly, about 3–5 am ET, during the season | "provided by NFL Next Gen Stats" | Repo CC-BY 4.0 | "NGS will only provide data for players above a minimum number of pass/rush/rec attempts." `week == 0` is the regular-season summary. Three stat_types: passing, receiving, rushing. | https://nflreadr.nflverse.com/reference/load_nextgen_stats.html |
| Participation | `load_participation` | 2016 | **Post-season only** for 2023+; "does not update during the season" | NGS for 2016–2022; FTN from 2023 | **CC-BY-SA 4.0**. Credit "FTN Data via nflverse" (2023+) or "NFL NextGenStats via nflverse" (2022 and earlier). | 2025 file exists; 2026 file returns 404. Column fill rates differ by era (§3). | https://nflreadr.nflverse.com/reference/load_participation.html |
| FTN charting | `load_ftn_charting` | 2022 | 0/6/12/18 UTC during the season; "charted within 48 hours following each game" | FTNData.com | **CC-BY-SA 4.0**; credit "FTN Data via nflverse" | 2026 data is in-season (§3). Has a `date_pulled` column. | https://nflreadr.nflverse.com/reference/load_ftn_charting.html |
| PFR advanced stats | `load_pfr_advstats` | 2018 | 0/6/12/18 UTC during the season | Pro Football Reference | Repo CC-BY 4.0; **upstream SR ToS** | "some data is only available at the season level". py has it since 0.1.5. | https://nflreadr.nflverse.com/reference/load_pfr_advstats.html |
| Contracts | `load_contracts` | n/a; historical, no season argument | Not documented. Timestamp shows a daily-ish refresh (2026-10-24). | OverTheCap.com | Repo CC-BY 4.0 | Has nested `season_history` and `contract_history`. Issues go to nflverse/rotc. | https://nflreadr.nflverse.com/reference/load_contracts.html |
| Officials | `load_officials` | 2015 | Not documented. Last timestamp is 2026-09-02, so it may lag in season. | Not stated | Repo CC-BY 4.0 | One row per game per official | https://nflreadr.nflverse.com/reference/load_officials.html |
| ESPN QBR | `load_espn_qbr` (**R only**; no `load_espn_qbr` in nflreadpy) | 2006 | Not documented. espn_data timestamp is 2026-10-24. | ESPN via espnscrapeR | Repo CC-BY 4.0 | `summary_type` takes season or week. College QBR was removed in 1.4.1. | https://nflreadr.nflverse.com/reference/load_espn_qbr.html |
| Players | `load_players` | n/a; one row per player | Not documented. Timestamp is 2026-10-23. | IDs and bio data "mostly from GSIS". Draft info from PFR. IDs from NFL, PFR, PFF, OTC, ESB and ESPN. | Repo CC-BY 4.0 | v2 schema since nflreadr 1.5.0 is a breaking change | https://nflreadr.nflverse.com/reference/load_players.html |

---

## 2. Licensing

### 2.1 Repo licences

- **nflverse-data:** `LICENSE.md` is the full text of **Creative Commons Attribution 4.0 International** (https://raw.githubusercontent.com/nflverse/nflverse-data/master/LICENSE.md). There is no plain `LICENSE` file; that URL returns 404.
  - Note the CC text in that file: "Use of the licensed material may still be restricted for other reasons, including because others have copyright or other rights in the material." The licence covers only rights "that the Licensor has authority to license".
- **nflverse-data README:** The README (https://raw.githubusercontent.com/nflverse/nflverse-data/master/README.md and `README.Rmd`) contains **no licence or attribution text**. It only says the repo "holds automated data releases for nflverse projects (i.e. all of the data powered/scraped via GitHub Actions)" and points to the schedule page for automation status.
- **nflreadr:** MIT ("MIT + file LICENSE"; https://raw.githubusercontent.com/nflverse/nflreadr/main/DESCRIPTION, `LICENSE.md`).
- **nflreadpy:** MIT (https://raw.githubusercontent.com/nflverse/nflreadpy/main/pyproject.toml). Its homepage (https://nflreadpy.nflverse.com/) states the clearest summary of data licensing: "The majority of all nflverse data available (ie all but the FTN data as of July 2025) is broadly licensed as CC-BY 4.0, and the FTN data is CC-BY-SA 4.0 (see nflreadr docs for each main data file)."

### 2.2 Per-dataset licence and source notes in the loader docs

Each loader's "source" statement is quoted in the dataset table above.

**FTN charting** (https://nflreadr.nflverse.com/reference/load_ftn_charting.html):

> "FTN Data manually charts plays and has graciously provided a subset of their charting data to be published via the nflverse. Data is available from the 2022 season onwards and is charted within 48 hours following each game. This data is released under the CC-BY-SA 4.0 Creative Commons license and attribution must be made to FTN Data via nflverse"

The "Source" field is "FTNData.com". The console banner reads "please attribute to 'ftndata.com via nflverse'".

**Participation** (https://nflreadr.nflverse.com/reference/load_participation.html):

> "This data is released under the CC-BY-SA 4.0 Creative Commons license and attribution must be made to FTN Data via nflverse (from 2023 onwards) or NFL NextGenStats via nflverse (for 2022 and earlier)"

**Other loaders:** load_injuries, load_depth_charts, load_snap_counts, load_nextgen_stats, load_player_stats, load_rosters_weekly and load_schedules carry **no licence statement** on their reference pages. They state only the source and where to file issues.

### 2.3 FTN's own site

https://ftnfantasy.com/stats/sports-data is about FTN's commercial data product. It says: "Attribution isn't required unless otherwise specified in your agreement". This concerns paid agreements, **not** the nflverse-distributed subset. The nflverse docs remain the governing statement for that subset (CC-BY-SA 4.0).

### 2.4 Upstream terms risk: Sports Reference / PFR

This affects snap counts, PFR advanced stats, and the pbp `spread_line`, `total_line`, `roof`, `temp` and `wind` columns.

Sports Reference Terms of Use, Section 5 (https://www.sports-reference.com/termsofuse.html, "Last Updated: May 19, 2023") lists among prohibited uses:

> "copy or use any material or Content from the Site, including without limitation any statistics, data, text, graphics, or images, for purposes of training, fine-tuning, prompting, or instructing artificial intelligence models or technologies in any manner, including without limitation for purposes of (i) generating answers, text, scores, statistics, notes, graphics, images, or any other output; or (ii) supporting machine learning methods used to predict, classify, label, or score inputs into the models"

It also prohibits automated access "in a manner that adversely impacts site performance or access". The bot policy (https://www.sports-reference.com/bot-traffic.html) blocks more than 20 requests per minute on non-FBref sites.

The data-use page (https://www.sports-reference.com/data_use.html) says not to "use our data to train generative artificial intelligence models without our permission".

Whether these terms bind people who use PFR-derived data redistributed by nflverse, rather than people visiting the site, is a legal question. I did not resolve it (see Unverified).

---

## 3. FTN charting vs participation timing

### FTN charting: weekly, in-season

- **Docs:** "charted within 48 hours following each game" (load_ftn_charting page) and "updates every day at 0, 6, 12, 18 UTC during the season" (schedule page).
- **NEWS:** nflreadr NEWS for the version that added it (https://raw.githubusercontent.com/nflverse/nflreadr/main/NEWS.md) says: "This should automatically be updated when published by FTN, and the early indication is that it will be within 48 hours after a game has finished."
- **Observed:** `ftn_charting_2026.csv` holds 16,070 plays for weeks 1–6 (about 2,466–2,764 plays per week) and 166 plays for week 7 (the Thursday game). The release timestamp is 2026-10-24 11:22 EDT. The reference page example shows "Data updated: 2026-09-16" with week 1 2026 plays.

### Participation: post-season only

- **Correction to the brief.** The docs do not say the NGS source "stopped after 2023". They say:
  - "Participation data prior to 2023 is from NFL NGS. The data source died during the 2023 season." (schedule page)
  - Attribution for NGS applies "for 2022 and earlier" (load_participation page).
  - So NGS-sourced data covers **2016–2022**. FTN supplies **2023 onwards**: "provided after all post-season games are completed. It does not update during the season!"
- **NEWS 1.5.0:** "`load_participation()` is back, somewhat! FTNData.com has graciously offered to provide participation data after each season has ended, so we now have participation data from 2024 and 2023. Please note that this data is licensed under CC-BY-SA 4.0 and should be credited (for 2023 onwards) to **FTN Data via nflverse**." (#278)
- **Observed:**
  - `pbp_participation_2025.csv` exists. The release timestamp is 2026-02-10, after Super Bowl LX.
  - `pbp_participation_2026.csv` returns **404**.
  - Fill rates in 2025 (FTN era) vs 2022 (NGS era):

    | Column | 2025 (FTN) | 2022 (NGS) |
    |---|---|---|
    | `ngs_air_yards` | **0%** | 36% |
    | `time_to_throw` | 43% | 38% |
    | `route` | 42% | 36% |
    | `defense_coverage_type` | 49% | 38% |
    | `was_pressure` | 100% | 38% |
    | `offense_personnel` | 100% | 76% |
    | `players_on_play` | 100% | 91% |

  - 2025 adds `offense_names`, `defense_names`, `*_positions` and `*_numbers` columns.
- **Model implication (inference):** Participation can be used only as historical training data. It is not available for in-season features for 2026. FTN charting is the in-season source, and it lags by up to about 48 h.

---

## 4. Injuries and depth charts

### Injuries

- **Docs:** "Data collected from an API for weekly injury report data." (https://nflreadr.nflverse.com/reference/load_injuries.html). There is **no statement** of the source changing or the data being discontinued.
- **Cadence:** "updates every day at 7AM UTC throughout the season" (schedule page).
- **Active maintenance:** The latest dev NEWS entry says "`load_injuries()` no longer rejects an upcoming season during the practice week before the season opener. (#320)".
- **Dictionary** (https://nflreadr.nflverse.com/articles/dictionary_injuries.html) lists 17 fields:
  - `season`, `season_type`, `team`, `week`, `gsis_id`, `position`, `full_name`, `first_name`, `last_name`
  - `report_primary_injury`, `report_secondary_injury`, `report_status`
  - `practice_primary_injury`, `practice_secondary_injury`, `practice_status`
  - `date_modified`, described as "Date and time that injury information was updated"
- **Observed in 2025 and 2026 files:**
  - Columns match the dictionary plus `game_type`, but **`date_modified` is absent**.
  - Granularity is one row per player per week. There is no per-day practice column (Wed/Thu/Fri).
  - Values: `report_status` is one of Out, Questionable, Doubtful or blank. `practice_status` is one of "Full/Limited/Did Not Participate In Practice".
  - 2026 has REG weeks 1–7 (2,138 rows). 2025 runs through the SB (6,068 rows).
- **Inference:** The file appears to hold only the last practice status of the week. Without timestamps it cannot show what was known at a given time.

### Depth charts

- **Schedule page:** "Please note that the data source has changed after the 2024 season. From 2025 onwards, depth charts are not assigned a week. Instead, each update provides a new ISO8601-formatted timestamp that users can assign to any point in time during the season. Each update is appended to the existing data with a new timestamp."
- **Dictionary** (https://nflreadr.nflverse.com/articles/dictionary_depth_charts.html): "Please note that the data source has changed after the 2024 season. From 2025 onwards, the following variables and descriptions will apply."
  - The new fields are `dt`, `team`, `player_name`, `espn_id`, `gsis_id`, `pos_grp_id`, `pos_grp`, `pos_id`, `pos_name`, `pos_abb`, `pos_slot` and `pos_rank`.
  - `dt` is described as "The timestamp (ISO8601-formatted text) indicating when the data record was loaded."
- **NEWS 1.5.0:** "`load_depth_charts()` now provides depth charts by date (including the preseason), and defaults to `most_recent_season(roster = TRUE)` … the data structure has changed because the data source had to be changed from NFL Data Exchange to ESPN. (#275, #277)"
- **Observed:** `depth_charts_2026.csv` has 693,886 rows and 255 distinct `dt` values, from 2026-03-22T06:38:42Z to 2026-10-24T11:57:56Z. Several snapshots a day are possible.
- **Model implication (inference):** You have to pick the last `dt` before each kickoff yourself. The 2001–2024 data uses the old weekly schema (`club_code`, `week`, `depth_team`, ...), so the two schemas need harmonising.

---

## 5. Schedules: betting columns to exclude

**Sources:**
- https://nflreadr.nflverse.com/articles/dictionary_schedules.html. This page renders a DT widget; the same rows appear in https://raw.githubusercontent.com/nflverse/nflreadr/main/data-raw/dictionary_schedules.csv.
- The header of https://raw.githubusercontent.com/nflverse/nfldata/master/data/games.csv.

**Betting columns in schedules** (exact names and dictionary descriptions):

| Column | Dictionary description |
|---|---|
| `away_moneyline` | "Odds for away team to win the game." |
| `home_moneyline` | "Odds for home team to win the game." |
| `spread_line` | "The spread line for the game. A positive number means the home team was favored by that many points, a negative number means the away team was favored by that many points. This lines up with the result column." |
| `away_spread_odds` | "Odds for away team to cover the spread." |
| `home_spread_odds` | "Odds for home team to cover the spread." |
| `total_line` | "The total line for the game." |
| `under_odds` | "Odds that total score of game would be under the total_line." |
| `over_odds` | "Odds that total score of game would be over the total_ine." The typo is in the original. |

Related outcome columns are `result` ("Convenient for evaluating against the spread bets") and `total` ("Convenient for evaluating over/under total bets"). They are outcomes, so they are targets or leakage, not inputs.

**Where the lines come from:**
- **Schedules:** neither the schedules dictionary nor nfldata's DATASETS.md (https://raw.githubusercontent.com/nflverse/nfldata/master/DATASETS.md) states a source. DATASETS.md marks `spread_line` and `total_line` "NEW Feb 2020", with no source.
- **pbp:** the pbp dictionary (https://nflreadr.nflverse.com/articles/dictionary_pbp.html) does name one:
  - `spread_line`: "The closing spread line for the game. … (Source: Pro-Football-Reference)"
  - `total_line`: "The closing total line for the game. (Source: Pro-Football-Reference)"

**Observed in games.csv** (retrieved 2026-10-25):
- Seasons run 1999–2026.
- `spread_line` and `total_line` are filled from 1999. Moneylines and spread/total odds are filled from 2006.
- **27 of the 178 unplayed 2026 games already have lines.** Example: `2026_07_PIT_NO` spread −3, total 43.5.
- So schedule lines for upcoming games are pre-game values that may change. They cannot all be PFR closing lines yet. Their exact source and timing are unclear.

**Other betting-derived pbp columns to drop:** `vegas_wp`, `vegas_home_wp` ("incorporating pre-game Vegas line"), `vegas_wpa` and `vegas_home_wpa` ("spread_adjusted model"), plus the pbp `spread_line` and `total_line`.

**Leakage note (inference):** `temp` and `wind` in schedules and pbp (PFR source) and the pbp `weather` string are recorded game-day conditions. Do not treat them as pre-game forecasts.

---

## 6. nflreadpy

Docs: https://nflreadpy.nflverse.com/. Repo files: https://raw.githubusercontent.com/nflverse/nflreadpy/main/

### Version

- **PyPI:** the latest released version is **0.1.5**, uploaded 2025-11-19. Sources: https://pypi.org/pypi/nflreadpy/json and https://pypi.org/simple/nflreadpy/ (versions 0.1.0–0.1.5).
- **Main branch:** `pyproject.toml` says **0.1.7**, but `CHANGELOG.md` shows only a "nflreadpy devel" section after v0.1.5. The devel entries are:
  - load_participation week-level season logic
  - `team_abbr_mapping()`, `team_abbr_mapping_norelocate()` and `player_name_mapping()`
  - Wednesday-after-Labor-Day season start
- Treat 0.1.5 as the current release. 0.1.6 and 0.1.7 do not appear on PyPI.

### Python and dependencies

- **Python:** `requires-python = ">=3.10"`. Classifiers list 3.10, 3.11, 3.12 and 3.13. Status: "4 - Beta".
- **Dependencies on main:** `requests>=2.33.0`, `polars>=0.20.0`, `platformdirs>=3.0.0`, `tqdm>=4.64.0`, `pydantic>=2.0.0`, `pydantic-settings>=2.0.0`. Optional extra `pandas>=1.5.0`.
- **Dependencies in PyPI 0.1.5:** the same list, except `requests>=2.28.0`.
- **Polars:** loaders return Polars DataFrames. The docs say: "nflreadpy uses Polars instead of pandas. Convert to pandas if needed: pbp.to_pandas()".

### Loader functions

Sources: `src/nflreadpy/__init__.py` and https://nflreadpy.nflverse.com/api/load_functions/.

- **Loaders:** `load_pbp`, `load_player_stats`, `load_team_stats`, `load_rosters`, `load_rosters_weekly`, `load_schedules`, `load_teams`, `load_players`, `load_draft_picks`, `load_injuries`, `load_contracts`, `load_snap_counts`, `load_nextgen_stats`, `load_officials`, `load_participation`, `load_pfr_advstats`, `load_combine`, `load_depth_charts`, `load_trades`, `load_ftn_charting`, `load_ff_playerids`, `load_ff_rankings`, `load_ff_opportunity`.
- **Utilities:** `get_current_season`, `get_current_week`, `clear_cache`.
- **Mapping datasets:** `team_abbr_mapping`, `team_abbr_mapping_norelocate`, `player_name_mapping`.
- **Missing:** there is **no `load_espn_qbr`**, even though `downloader.py` has an `espnscraper` base URL.

### Cache and configuration

Sources: `src/nflreadpy/config.py` and https://nflreadpy.nflverse.com/api/configuration/.

| Setting | Env var | Default |
|---|---|---|
| `cache_mode` | `NFLREADPY_CACHE` | **`"memory"`**; options are `"memory"`, `"filesystem"`, `"off"` |
| `cache_dir` | `NFLREADPY_CACHE_DIR` | `platformdirs.user_cache_dir("nflreadpy")` |
| `cache_duration` | `NFLREADPY_CACHE_DURATION` | **86400 s** (24 h); 0 means always refresh |
| `verbose` | `NFLREADPY_VERBOSE` | False |
| `timeout` | `NFLREADPY_TIMEOUT` | 30 s |
| `user_agent` | `NFLREADPY_USER_AGENT` | `"nflverse/nflreadpy <version>"` |

- Settings can also be read from a `.env` file.
- The API is `update_config(**kwargs)`, `get_config()` and `reset_config()`.
- `clear_cache(pattern=None)` clears both the memory and filesystem caches (https://nflreadpy.nflverse.com/api/cache/).
- Downloads use parquet or csv. The `DataFormat` enum exists, but it is not exposed as a config field in the source I read.

### Other notes

- The homepage disclaimer says: "Most of the first version was written by Claude based on nflreadr, use at your own risk."
- **nflreadr for comparison:** version 1.5.1 (dev 1.5.1.9002) needs R >= 4.1.0. The `qs` file type was hard-deprecated because qs was removed from CRAN on 2026-01-17. `most_recent_season()` now rolls over on the Wednesday after Labor Day (NEWS).

---

## 7. Official NFL injury reports, inactives, NFL.com terms

### NFL.com injury report pages

- **URLs:**
  - Current week: https://www.nfl.com/injuries/ (canonical). On retrieval it showed "2026 NFL Injury Report — Injuries - WEEK 7".
  - Specific weeks follow `https://www.nfl.com/injuries/league/{season}/reg{week}`, and post-season rounds follow `.../post{n}`. I verified `league/2026/reg6` (Week 6), `league/2025/reg1` (Week 1) and `league/2025/post4` (Super Bowl).
  - The HTML `<title>` lags (it says "Week 7" on every URL), but the page body is correct.
  - Seasons back to 1965 are linked.
- **Content:**
  - Games are grouped by kickoff date and time, e.g. "THURSDAY, OCTOBER 22ND 8:15 PM EDT".
  - The table columns are **Player | Position | Injuries | Practice Status | Game Status**.
  - **The page shows no per-report publication timestamp** and only one practice status per player. By inference, this is the latest report of the week.

### Official reporting schedule

Source: 2026-2027 NFL Important Dates PDF, https://media.nfl.com/content/dam/communications/football-communications/2026/news/07%2007%2026%20-%20Important%20Dates.pdf. It is headed "DATES SUBJECT TO CHANGE" and is also linked from https://operations.nfl.com/calendar-events/nfl-important-dates.

Under "September 6-12":

> "In accordance with the Personnel (Injury) Report Policy, each club is required to file a Practice Report by 4:00 p.m., New York time, (or as soon as possible after the completion of practice) every … Wednesday, Thursday, and Friday for a Sunday game. On days that a team practices, an estimated injury report for practice status is not acceptable, regardless of what time practice is held."

> "Each club must also file a weekly regular season Game Status Report by 4:00 p.m., New York time (or as soon as possible after the completion of practice) on … Friday for a Sunday game. An update must be reported if there is any change in a player's condition after the initial Game Status Report is filed."

The full text also covers the other days:
- Practice reports: Mon–Wed for a Thursday game; Thu–Sat for a Monday game.
- Game Status Reports: Wednesday for a Thursday game; Saturday for a Monday game.

So the NFL defines due times (4:00 p.m. ET, or as soon as possible after practice). NFL.com's public page does not show the times at which reports were published.

### Inactives: 90 minutes before kickoff

Source: NFL Football Operations, "Countdown to Kickoff", https://operations.nfl.com/gameday/pre-game/countdown-to-kickoff:

> "90-minute officiating meeting: One hour and 30 minutes prior to kickoff, the Referee and other game officials meet … This critical meeting includes … exchange of Game Day Administration Reports (which includes a club's inactive list, …)"

The same page says the meeting "Allows club PR representatives to deliver the Game Day Administration Report to the Referee, which includes a club's inactive list".

It also bears on weather features: "90 minutes: … If the stadium has a retractable roof and/or wall, the home club notifies the referee or on-site NFL Football Operations representative whether the roof and/or wall will be open or closed during the game."

### NFL.com Terms and Conditions

Source: https://www.nfl.com/legal/terms/, "Updated: May 16, 2024". `/legal/terms` and `/help/terms` redirect there.

- **§1.3 Permitted Uses:**
  > "You may use the Services solely for your own individual non-commercial and informational purposes only. Any other use, including for any commercial purposes, is strictly prohibited without our express prior written consent. Systematic retrieval of data or other content from the Services, whether to create or compile, directly or indirectly, a collection, compilation, database, or directory, is prohibited absent our express prior written consent."
- **§11(f):**
  > "use or attempt to use any engine, software, tool, agent or other device or mechanism (including, browsers, spiders, robots, avatars or intelligent agents) to navigate or search the Services to harvest or otherwise collect information from the Services to be used for any commercial purpose;"
- **§11(d):** bans "any action that imposes an unreasonable or disproportionately large load on the infrastructure".
- **§1.1:** the NFL claims ownership of "data, analytics, algorithms … generated from the Services".
- **robots.txt** (https://www.nfl.com/robots.txt) disallows `/account/`, `/search/` and similar paths, but not `/injuries/`. The ToS still restricts systematic retrieval whatever robots.txt allows.
- **Inference:** Under §1.3, scraping NFL.com injury pages into a database is prohibited without written consent, **even for non-commercial use**.

---

## 8. Weather

### Open-Meteo Historical Forecast API

Source: https://open-meteo.com/en/docs/historical-forecast-api. The endpoint is `https://historical-forecast-api.open-meteo.com/v1/forecast`.

- **What it is: archived forecasts, not reanalysis.**
  - "Historical forecasts archived from the Weather Forecast API — same models, same parameters, same response format. Coverage starts around 2022. Each run's first few hours are stitched into a continuous hourly timeseries. To access the full forecast horizon of individual runs, use the Single Runs API."
  - "Historical Forecast API: A continuous hourly timeseries built by stitching the first hours of each successive model run. Closely tracks actual conditions because each run is initialised from real measurements. Coverage starts around 2021. Not suitable for long time series due to model version changes over time."
  - The reanalysis product is separate. "Historical Weather API: ERA5 reanalysis at 0.25° (~25 km) from 1940, ERA5-Land at 0.1° (~9 km) from 1950, and ECMWF IFS analysis at 9 km from 2017." (same page; https://open-meteo.com/en/docs/historical-weather-api)
- **Dates covered: the page is inconsistent.** It says variously "Coverage starts around 2022", "Past weather forecasts from 2022 onwards are available", "data is available starting from 2021 or 2022", and "Coverage starts around 2021". The per-model "Available Since" table lists, for example:
  - NOAA GFS: 2021-03-23
  - HRRR (US CONUS 3 km): 2018-01-01
  - NBM: 2024-10-08
  - NAM: 2025-09-01
  - ECMWF IFS HRES 9 km: 2017-01-01
  - IFS 0.25°: 2024-02-03
  - DWD ICON: 2022-11-24
  - JMA: 2016-01-01
- **Leakage caveat (inference from the stitching description):** Because each hour comes from a run started just before it, this series approximates observed conditions. It is not what a forecast available days before kickoff showed. For true pre-game forecasts there are two other APIs:
  - **Previous Runs API** (https://open-meteo.com/en/docs/previous-runs-api): "Data from past model runs is aligned to fixed lead-time offsets of 1–7 days … Most models are archived from January 2024. GFS 2 m temperature extends back to March 2021."
  - **Single Runs API:** "ECMWF IFS HRES at 9 km is archived from March 2024; all other models from 2nd of April 2026."

### Open-Meteo terms and licence

Sources: https://open-meteo.com/en/terms, https://open-meteo.com/en/licence and https://open-meteo.com/en/pricing.

- **Free API terms:**
  - "Less than 10'000 API calls per day, 5'000 per hour and 600 per minute."
  - "You may only use the free API services for non-commercial purposes."
  - "You accept to the CC-BY 4.0 licence, as specified in the licence conditions."
  - The pricing table adds a free-tier monthly limit of **300,000 calls**.
- **Definition of non-commercial:** it follows Creative Commons. Commercial examples include "Websites or apps that have subscriptions or display advertisements", "Commercial products or promotional activities" and "Undisclosed research at commercial entities".
- **Call counting:** "Requests for data covering more than 10 weather variables or extending over a period of more than 2 weeks for a single location are considered multiple API calls … a request for 2 weeks of data with 15 weather variables will be calculated as 1.5 API calls, while 4 weeks of data equals 3.0 API calls."
- **Licence:**
  - "API data are offered under Attribution 4.0 International (CC BY 4.0)". Attribution is required.
  - The server code is AGPLv3.
  - Upstream model data carries provider licences. For example, NOAA follows weather.gov/disclaimer, ECMWF and DWD are CC-BY, and UK Met Office is CC-BY-SA.

### api.weather.gov (NWS)

- **API docs** (https://www.weather.gov/documentation/services-web-api):
  - "All of the information presented via the API is intended to be open data, free to use for any purpose. … there are reasonable rate limits in place … The rate limit is not public information".
  - "A User Agent is required to identify your application … If you include contact information (website or email), we can contact you … This will be replaced with an API key in the future."
- **Disclaimer** (https://www.weather.gov/disclaimer): information is "in the public domain, unless specifically noted otherwise, and may be used without charge for any lawful purpose so long as you do not:
  1. claim it is your own …,
  2. use it in a manner that implies an endorsement or affiliation with NOAA/NWS, or
  3. modify its content and then present it as official government material."
- Under 17 U.S.C. § 403, a copyrighted work made mostly of NWS material must say so.

---

## Unverified / unclear

1. **GitHub release notes not read.** nflverse-data release pages on github.com returned 403, so I could not check per-release licence or attribution notes there.
2. **PBP upstream.** The load_pbp page does not literally say the data is derived from the NFL. It references `nflfastR::build_nflfastR_pbp()` parsing raw pbp JSON. That the NFL is the source is likely but **unquoted**.
3. **Schedule line source and timing.**
   - The schedules dictionary and nfldata DATASETS.md give no source.
   - Only the pbp dictionary says "closing … (Source: Pro-Football-Reference)".
   - Lines already appear for some unplayed 2026 games, so the schedule file's provider for upcoming games is unknown, and so is whether values are opening, current or closing.
4. **Injuries upstream.** "An API" is not named.
   - The `date_modified` column is in the dictionary and in the old 2020 example, but missing from the 2025 and 2026 files. I could not determine whether this changed on purpose.
   - No discontinuation notice was found.
5. **Cadence for contracts, officials, ESPN QBR and players.** None is documented. Only the observed timestamps are given. Officials has not updated since 2026-09-02.
6. **Weekly rosters cadence.** The schedule page says "Roster data … 7AM UTC" without separating season and weekly rosters. The observed timestamps are consistent with 7AM UTC.
7. **nflreadpy version.** `pyproject.toml` on main says 0.1.7, but PyPI's newest is 0.1.5 and the CHANGELOG lists only "devel". 0.1.6 and 0.1.7 may be unreleased.
8. **Licence scope.** The nflverse CC-BY 4.0 licence covers only rights nflverse holds. NFL, PFR/Sports Reference, ESPN, OTC and NGS terms may still restrict use; the SR ToS bans ML prediction use of its content. Whether those terms bind downstream users of nflverse files is a legal question I did not resolve.
9. **NFL.com page.** I inferred that the single "Practice Status" shown is the final report of the week. Per-day (Wed/Thu/Fri) practice history and publication times are not visible on the server-rendered page.
10. **NFL policy wording.** The reporting rules come from the 2026-27 Important Dates PDF, which is marked "DATES SUBJECT TO CHANGE". The full Personnel (Injury) Report Policy and Game Operations Manual are not public, and I did not read them.
11. **Open-Meteo start dates.** The docs give "around 2021", "around 2022", "2021 or 2022", and per-model dates as far back as 2016–2018. Use the per-model "Available Since" table for the chosen model.
12. **api.weather.gov history.** I did not check whether api.weather.gov offers any archive of past forecasts. It is primarily current forecasts, alerts and observations, which is an inference.
13. **Automation Status table.** It renders client-side and was empty in fetched HTML. I used `timestamp.json` assets instead.

## Source index

**nflreadr / nflverse**
- Schedule: https://nflreadr.nflverse.com/articles/nflverse_data_schedule.html
- Loader reference pages: https://nflreadr.nflverse.com/reference/{load_pbp, load_player_stats, load_team_stats, load_rosters, load_rosters_weekly, load_schedules, load_snap_counts, load_injuries, load_depth_charts, load_nextgen_stats, load_participation, load_ftn_charting, load_pfr_advstats, load_contracts, load_officials, load_espn_qbr, load_players}.html
- Dictionaries: https://nflreadr.nflverse.com/articles/{dictionary_schedules, dictionary_injuries, dictionary_depth_charts, dictionary_participation, dictionary_ftn_charting, dictionary_pbp}.html
- nflverse-data repo: https://raw.githubusercontent.com/nflverse/nflverse-data/master/{README.md, README.Rmd, LICENSE.md}
- nflreadr repo: https://raw.githubusercontent.com/nflverse/nflreadr/main/{DESCRIPTION, LICENSE.md, NEWS.md, R/*.R, data-raw/dictionary_schedules.csv}
- nfldata repo: https://raw.githubusercontent.com/nflverse/nfldata/master/{README.md, DATASETS.md, data/games.csv}
- Release files: https://github.com/nflverse/nflverse-data/releases/download/{tag}/timestamp.json, plus the injuries_2025/2026, depth_charts_2026, ftn_charting_2026 and pbp_participation_2022/2025 CSVs

**nflreadpy**
- Docs: https://nflreadpy.nflverse.com/, /api/load_functions/, /api/configuration/, /api/cache/
- Repo: https://raw.githubusercontent.com/nflverse/nflreadpy/main/{pyproject.toml, CHANGELOG.md, src/nflreadpy/__init__.py, config.py, cache.py, downloader.py}
- PyPI: https://pypi.org/pypi/nflreadpy/json

**NFL**
- Injury pages: https://www.nfl.com/injuries/, https://www.nfl.com/injuries/league/2026/reg6
- Terms and robots: https://www.nfl.com/legal/terms/, https://www.nfl.com/robots.txt
- Operations: https://operations.nfl.com/gameday/pre-game/countdown-to-kickoff
- Important Dates PDF: https://media.nfl.com/content/dam/communications/football-communications/2026/news/07%2007%2026%20-%20Important%20Dates.pdf

**Sports Reference**
- https://www.sports-reference.com/termsofuse.html, /data_use.html, /bot-traffic.html

**FTN**
- https://ftnfantasy.com/stats/sports-data

**Open-Meteo**
- https://open-meteo.com/en/docs/historical-forecast-api, /en/docs/historical-weather-api, /en/docs/previous-runs-api, /en/terms, /en/licence, /en/pricing

**NWS**
- https://www.weather.gov/documentation/services-web-api, https://www.weather.gov/disclaimer

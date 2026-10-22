# Team statistics across competitions

_Written 21 October 2026. Code: `src/footy/teams.py`, `src/footy/api/teams_routes.py`._

Every match a club has played, from the club's side, in one log, and a profile built from it:
how the club does in its league and in Europe, at home and away, season by season. Domestic
cups slot in when a cup source is added (see [Adding a cup](#adding-a-cup)).

Everything comes from the imported matches and the saved prediction records. Nothing is
estimated or filled in: a figure the source doesn't have is `null`, and every average says how
many matches it covers. Bookmakers' prices are never read (D-013, D-024).

## Sources

| Competition type | Table | Source | What each match has |
| --- | --- | --- | --- |
| `league` | `matches` | football-data.co.uk | Score, half-time score, shots, shots on target, corners, fouls, yellow and red cards, referee; football-data's own xG where it has one (2026/27 on) |
| `europe` | `euro_matches` | openfootball, Wikipedia (D-023) | Score, stage, extra-time and penalties flags, neutral venue. **No match statistics**: those fields are `null` |
| `cup` | none yet | | |

- The ten leagues in football-data's extra-league layout (D-022: AUT, DNK, SWZ, NOR, SWE, POL,
  ROU, RUS, FIN, IRL) have scores only, so their half-time scores and statistics are `null` too.
- `fd_xg_for` / `fd_xg_against` are football-data's own expected-goals figures (undocumented
  columns, 2026/27 onwards). They are labelled as football-data's in every response and are
  not used by any model.

## Rules

- **Regulation time.** Scores are 90 minutes plus stoppage time (D-023). A UEFA match that went
  to extra time or penalties counts with its score after 90 minutes, with `extra_time` and
  `penalties` set. That is a draw for a final, but not always for a second leg, which goes on
  when the aggregate is level (118 of the 190 such matches held on 22 October 2026).
- **Flagged matches.** A match with a `model_exclusion` (awarded, cancelled, bare results,
  play-offs against outside clubs; D-018, D-022, D-023) is in the log with `excluded` set to
  the reason, and is left out of every summary, form string, streak and the strength series.
  Each summary's `left_out` counts them.
- **Repeat meetings.** Every stored match is its own row, keyed `matches:<id>` or
  `euro_matches:<id>`. Second and third meetings of the same pairing in a season (D-022,
  `meeting`) are never merged, and head-to-head lists each one.
- **Dates.** League dates are UK dates; UEFA dates are the venue's local date. Kick-offs are
  sent in UTC (D-005); the CLI shows them in UK time.
- **Seasons.** Season keys as everywhere else (`2627` is 2026/27, `2626` the calendar year
  2026). "This season" is each league's latest season held and, for every UEFA competition,
  the latest UEFA season held (qualifying included).

## Competition type: one mapping

`footy.teams.competition_type(code)` is the only place a competition's type is decided:

- a football-data division code (`footy.reference.DIVISIONS`) is `league`;
- `UCL`, `UEL`, `UECL` and their qualifying codes `UCLQ`, `UELQ`, `UECLQ` are `europe`;
- a code in `CUP_COMPETITIONS` is `cup` (empty today).

### Adding a cup

1. Load the cup's matches into a table of their own (a migration and an importer; neither
   exists yet).
2. Add its codes and names to `CUP_COMPETITIONS`, e.g. `{"FAC": "FA Cup"}`.
3. Add a `MatchSource` to `MATCH_SOURCES`: the table, its per-team query, its whole-table
   query in the `INDEX_SQL` column order, and a row builder (copy `_euro_row` or
   `_league_row`).

Everything else (summaries by type, form and streaks by type, the strength series, the teams
list, the match page) picks the cup up from the mapping. A cup match moves the strength
rating with the league K (20).

## The match log

`GET /api/teams/{key}/matches` and `footy team-stats --csv` give one row per match:

| Field | Meaning |
| --- | --- |
| `ref`, `source` | `matches:<id>` or `euro_matches:<id>`; football-data, openfootball or wikipedia |
| `date`, `kickoff_utc` | See [Rules](#rules) |
| `competition`, `competition_name`, `competition_type` | e.g. `E0`, Premier League, `league` |
| `season`, `season_label`, `stage`, `meeting` | `stage` for UEFA (`League MD3`); `meeting` for leagues |
| `opponent`, `opponent_name`, `venue`, `listed_home` | `venue` H, A or N (neutral); `listed_home` says which side the source lists the club on |
| `status` | played, awarded or cancelled |
| `goals_for`, `goals_against`, `result` | Regulation time; W, D or L |
| `ht_goals_for`, `ht_goals_against`, `ht_result` | League only |
| `extra_time`, `penalties`, `neutral` | UEFA flags (`extra_time` and `penalties` are `null` for league rows) |
| `shots_*`, `shots_on_target_*`, `corners_*`, `fouls_*`, `yellow_cards_*`, `red_cards_*` | `_for` and `_against`; `null` where the source has none |
| `fd_xg_for`, `fd_xg_against` | football-data's figure, not ours |
| `referee` | League only |
| `excluded` | The match's `model_exclusion`, or `null` |
| `elo` | The descriptive strength before and after, and the opponent's before (counted matches only) |
| `prediction` | Our pre-match record of record for the match, if there is one (below) |

### Our pre-match record on each match

The record shown is the **record of record** (D-026): within the models-of-record period that
applies to the fixture, the first listed model with a genuine pre-match record, and its first
edition, exactly as `/api/performance` counts it. It is found by the rules settlement uses
(`footy.publish`): a league pairing that meets once a season takes its season's match even if
it was moved; where pairs can meet again (D-022) only the meeting on the record's date counts;
UEFA matches by date. It shows the model, basis, the probabilities of 1X2, over/under 2.5,
both teams to score and expected goals, the 1X2 pick (also as W/D/L for the club) and, once
settled, whether each pick was right. Never the prices behind a model + market record. The
records come from the `predictions` table, which `records/predictions/*.jsonl` mirrors.

## The profile

`GET /api/teams/{key}` and `footy team-stats` give:

- **`team`**: name, its media summary (see [Pictures and colours](#pictures-and-colours)),
  country, current division (the league of its latest league season; `current` is false if
  that league has since moved on without it, e.g. after relegation to a division not held) and
  the competitions it is in this season.
- **`summary`** and the splits **`by_type`** (each with `by_venue`), **`by_competition`**,
  **`by_season`** (every season, newest first, each split by type) and **`by_venue`** (H, A, N).
  A full summary has P W D L, goals for and against, goal difference, league points per game
  (league matches only), per-match averages for and against of goals, half-time goals, shots,
  shots on target, corners, fouls, yellow and red cards and football-data xG (each with the
  number of matches that had it), rates (clean sheets, failed to score, both teams scored, over
  2.5 goals) and half-time turnarounds (points won from behind at half time, points dropped from
  ahead).
- **Short summaries** (`SHORT_SUMMARY`: the counts only, P W D L, goals, goal difference,
  `left_out`, league matches, points and points per game) are used for every `by_season` entry
  (its `summary` and its `by_type`) and for each type's `by_venue`. Full ones would double a
  club's profile (about 76 KB for Arsenal on 22 October 2026, against 34 KB now); the full
  figures for a season or a venue are one filter away in the match log. `summary`, `by_type`,
  `by_competition` and the top-level `by_venue` stay full.
- **`form`**: the last 5 and last 10 results, overall and by type, oldest first (the latest is
  on the right).
- **`streaks`**: current runs (wins, unbeaten, without a win, losses, scored, clean sheets,
  failed to score), with the date each began.
- **`upcoming`**: stored UEFA fixtures and any fixture we have published a record for (domestic
  fixtures aren't stored until their result is imported), from a week back, with our record of
  record where there is one and the opponent's media summary (`opponent_media`).
  `awaiting_result` is true once the kickoff has passed with no result stored yet.
- **`strength`**: the descriptive rating series (below).

### Strength series (descriptive)

A goal-based Elo over every counted league and UEFA match held, in date order: World Football
Elo with a goal-difference multiplier, K 20 for league matches and 30 for UEFA matches, home
advantage 65 (none on neutral ground), and a starting level by where a club is first seen (1500
core top flights, 1450 other top flights, 1400 second tiers and UEFA-only clubs). These are the
update rule and default settings of `footy.modeling.euro.PanEuropeanElo`; the settings are
copied rather than imported (importing that module loads scipy, about a second), and a test
checks both the settings and that the ratings equal `PanEuropeanElo`'s. On the real database on
21 October 2026 all 862 ratings matched exactly.

It describes results; it is **not a model of record** and makes no forecast. `rank` is among
clubs with a counted match in the year before the latest match held.

**Thinned in the profile.** `series` holds the latest 60 counted matches in full
(`SERIES_FULL_MATCHES`) and, before them, one point a month: the rating after that month's last
counted match. Arsenal's 374 points become 130, which is more than a phone-width chart can
show, and the series drops from 22 KB to 8 KB. `peak` and `current` are taken from every point,
`series_points` says what was kept (`counted_matches`, `shown`, `every_match_from`, `rule`),
`GET /api/teams/{key}?series=full` sends every point, and the match log has every match's
rating before and after.

## Match page (`GET /api/predictions/{id}`)

The response gains a `teams` block; every existing field is unchanged.

- `home`, `away`: the team's key, name and media summary (`media`), last-10 form overall and by
  type, and per-match averages over each side's last 10 counted matches overall and of each
  type.
- `head_to_head`: the last 6 meetings in any competition, newest first, with the tally from this
  fixture's home side's view.
- `match_stats`: both sides' figures for this match, once it is played and has statistics
  (league matches only; `null` otherwise).

All of it uses only matches before the fixture's date (`as_of`), so a played match's page shows
what was known before it. If the match was played earlier than the record's date (a league
match brought forward keeps its record, as in settlement), `as_of` is the day it was played.

## Pictures and colours

Wherever these endpoints name a team they also send its media summary
(`footy.media.service.media_summary`; [docs/research/team-media.md](research/team-media.md)):

```json
{"crest_url": null, "badge": {"colours": ["#800032", "#6CABDD"], "short": "AVL"}}
```

- `crest_url`: `/media/teams/<key>/crest.png` only once a freely licensed crest has been
  downloaded (none yet), else `null`. Never the generated badge.
- `badge.colours`: the club's official colours from Wikidata (CC0) as hex, the badge's
  background first, then its band; `[]` when Wikidata has none (nothing is made up). Each is
  the shade drawn (`footy.media.badge.kit_shade`): Wikidata gives most clubs a plain named
  colour at its pure screen value (red `#FF0000`, blue `#0000FF`, green `#00FF00`), which is
  drawn in a kit shade of the same name (`#C8102E`, `#1D4F9C`, `#00843D`); any other hex is
  drawn as given. The background and band follow the order reviewed by hand in
  `reference/team_badges.csv` where a club has one (Real Madrid and Tottenham play in white,
  not their most saturated colour), else `pick_colours`.
- `badge.short`: the three-letter name the generated badge.svg draws, made on the server so
  the site's badges and the SVG agree: `footy.media.badge.short_name`, or the letters reviewed
  in `reference/team_badges.csv` ("MCI", "BAY", "ATM"), then made different for every two
  teams that share a competition's season (`footy.teams.badge_letters`: Celtic and Celta meet
  in the 2026/27 Europa League, so Celta is "CTA"). The team with more matches keeps its
  letters; the other takes its next choice (`short_candidates`).

| Endpoint | Where |
| --- | --- |
| `/api/fixtures`, `/api/past` | Each card's `home_media` and `away_media` |
| `/api/predictions/{id}` | `teams.home.media`, `teams.away.media`; each `recent` row's `opponent_team` (key) and `opponent_media`. The detail's top-level keys are unchanged |
| `/api/teams` | `media`: team key → summary for every listed team (each entry's keys are unchanged) |
| `/api/teams/{key}` | `team.media`; each `upcoming` fixture's `opponent_media` |
| `/api/teams/{key}/matches` | `media`: team key → summary for the team and every opponent on the page |

A summary costs one `stat` call per team without downloaded pictures (the facts file is read
once and re-read only when it changes; a manifest is re-read only when it changes).

## API

All three are read-only and follow the existing routes (404 `{"detail": "no such team"}` for an
unknown key, 422 for a bad parameter, UTC timestamps with `+00:00`).

`GET /api/teams?competition=E0,UCL&q=villa` (filters optional; `competition` repeatable or
comma-separated, matching this season's competitions; `q` matches every name held for a club
and its key, ignoring case and accents, each word anywhere: "spurs", "manchester city",
"athletic bilbao").

Names (`footy.teams`, "Names"): `name` is football-data's short name ("Ath Madrid", "Nott'm
Forest"), kept on cards, tables and logs. `display_name`, for headings, is the club's name on
Wikipedia's UEFA season pages ("Atlético Madrid"), else its Wikidata label without a club-type
word ("Nottingham Forest"), else `name`. `aliases` lists every other name held, for search:
openfootball's and Wikipedia's (`team_aliases`, country suffix removed), the Wikidata label and
the mapped item's English aliases ("Spurs"; `reference/team_wikidata_aliases.json`). Every name
is a source's own; none is made up. The team profile's `team` has `display_name` too.

```json
{"generated_at_utc": "2026-10-21T23:05:40+00:00", "latest_match_held": "2026-10-20",
 "filters": {"competition": ["E0", "UCL"], "q": "villa"}, "count": 2,
 "teams": [{"key": "eng-aston-villa", "name": "Aston Villa", "display_name": "Aston Villa",
   "aliases": ["Aston Villa F.C.", "..."], "country": "ENG",
   "division": {"code": "E0", "name": "Premier League", "tier": 1, "season": "2627",
                "season_label": "2026/27", "current": true},
   "competitions_this_season": [{"code": "E0", "name": "Premier League", "type": "league",
                                 "season": "2627", "season_label": "2026/27"}, "..."],
   "form_last_5": "LWDWW", "matches": 360,
   "matches_by_type": {"league": 319, "europe": 41, "cup": 0},
   "last_match_date": "2026-10-17", "elo": 1866.2, "elo_rank": 9}, "..."],
 "media": {"eng-aston-villa": {"crest_url": null, "badge": {"colours": ["#800032", "#6CABDD"], "short": "AVL"}},
           "esp-villarreal": {"crest_url": null, "badge": {"colours": ["#FCD116"], "short": "VIL"}}}}
```

`GET /api/teams/eng-arsenal` (trimmed):

```json
{"team": {"key": "eng-arsenal", "name": "Arsenal",
          "media": {"crest_url": null, "badge": {"colours": ["#C8102E", "#FFFFFF"], "short": "ARS"}},
          "country": "ENG", "division": {"code": "E0", "...": "..."}},
 "matches": {"logged": 374, "counted": 374, "left_out": 0, "first_date": "2018-08-12", "last_date": "2026-10-18"},
 "summary": {"played": 374, "won": 222, "drawn": 76, "lost": 76, "goals_for": 703, "goals_against": 368,
   "league_matches": 311, "league_points": 606, "points_per_game": 1.949,
   "per_match": {"shots": {"for": 14.016, "against": 10.617, "matches": 311}, "...": "..."},
   "rates": {"clean_sheets": {"matches": 140, "rate": 0.3743}, "...": "..."},
   "half_time": {"behind_at_half_time": {"matches": 63, "won": 8, "drawn": 17, "lost": 38, "points_won": 41}, "...": "..."}},
 "by_type": {"league": {"...": "..."}, "europe": {"...": "..."}, "cup": {"played": 0, "...": "..."}},
 "form": {"last_5": "WLWWW", "last_10": "DWWWWWLWWW", "by_type": {"europe": {"last_5": "DWDWW", "...": "..."}}},
 "upcoming": [{"date": "2026-10-24", "competition": "E0", "opponent_name": "Everton", "venue": "H",
   "opponent_media": {"crest_url": null, "badge": {"colours": ["..."], "short": "EVE"}},
   "prediction": {"model_name": "congestion_context_on_elo", "team": {"win": 0.587, "draw": 0.248, "loss": 0.165},
                  "pick_for_team": "W", "settled": false, "...": "..."}, "...": "..."}],
 "strength": {"current": 2018.7, "rank": 2, "ranked_teams": 613, "peak": {"rating": 2038.4, "date": "2026-04-07"},
   "series": [{"date": "2026-10-18", "competition": "E0", "rating": 2018.7}, "..."],
   "series_points": {"counted_matches": 374, "shown": 130, "every_match_from": "2025-08-31", "rule": "..."}}}
```

`GET /api/teams/eng-arsenal/matches?type=league&limit=1` (newest first, with `total`, `offset`,
`limit` and `notes`):

```json
{"ref": "matches:31374", "source": "football-data", "date": "2026-10-18",
 "kickoff_utc": "2026-10-18T15:30:00+00:00", "competition": "E0", "competition_type": "league",
 "season": "2627", "opponent_name": "Nott'm Forest", "venue": "A", "goals_for": 1,
 "goals_against": 0, "result": "W", "ht_goals_for": 0, "ht_goals_against": 0, "shots_for": 9,
 "shots_against": 14, "shots_on_target_for": 1, "corners_for": 6, "fouls_for": 8,
 "yellow_cards_for": 1, "red_cards_for": 0, "fd_xg_for": 1.14, "fd_xg_against": 0.9,
 "referee": "M Oliver", "excluded": null,
 "elo": {"before": 2014.2, "after": 2018.7, "opponent_before": 1730.6}, "prediction": null}
```

## CLI

```bash
uv run footy team-stats eng-arsenal                 # or a name: "Real Madrid"
uv run footy team-stats "aston villa" --type europe
uv run footy team-stats eng-arsenal --csv arsenal.csv   # the whole log, oldest first
```

A name that matches several clubs lists them and exits with 1. The command only reads.

## Speed

Per-team queries read only that club's rows. The strength series and the teams list need every
match, so they come from an in-process index per database, rebuilt only when a cheap data
fingerprint changes: row count and highest id of `matches`, the import log (a re-parse adds a
row), the correction log (a changed value adds one), and the content of `euro_matches` (small,
and updated in place by `euro-update`). A direct SQL edit of an existing `matches` row is not
noticed until the next import or restart.

On the real database (64,693 league and 4,537 UEFA matches), 21 October 2026, in this container
(timings vary by about a third from run to run):

| Step | Time |
| --- | --- |
| Profile (`team_profile`), warm, Arsenal / Real Madrid / Aston Villa | 30–45 ms |
| Match log, warm | 33–36 ms |
| Teams list, warm | 15 ms |
| `teams` block of a match page | 30–35 ms |
| Data fingerprint (every request that uses the index) | 8–12 ms |
| Index build (first request in a process, and after each data change) | 270–470 ms |
| End to end through the API (TestClient, JSON included), profile, warm | ~50 ms |

The `teams` block adds about 4.8 KB to each prediction's detail (1.1 MB over the 225 saved
records), which `footy export-snapshot` carries.

**At start-up.** The served app (`footy serve`, `footy.api.main:app`) builds the index in a
background thread as it starts (`warm_caches`), so the first team or match page doesn't pay for
it; a request that arrives during the build waits for it rather than starting another. Apps made
by `create_app()` for tests and for the snapshot export build it on the first request instead.

## Limitations

- UEFA matches have no statistics, half-time scores or referees in our sources, so a club's
  per-match statistics cover its league matches only (`matches` in each average says how many).
- No domestic cups yet; `cup` summaries are empty.
- Domestic fixtures appear in `upcoming` only when we have published a record for them: there
  is no stored domestic fixture list (football-data's `fixtures.csv` carries prices and isn't
  read here).
- "Current division" is the league of the club's latest league season held: a club relegated to
  a division we don't hold shows its last league with `current: false`.
- League dates are UK dates and UEFA dates local ones, so a late kick-off abroad can sort a day
  apart from the UK calendar.
- The strength series is descriptive and its rank compares clubs across leagues only through
  UEFA matches and promotion and relegation.
- The static snapshot carries the teams list and, for the teams it covers, each profile, the
  latest 100 matches of the log and the media ([docs/snapshot.md](snapshot.md)). Filters,
  older pages of the log and `series=full` are not in it.

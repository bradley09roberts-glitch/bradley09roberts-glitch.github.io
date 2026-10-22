# The static snapshot

_Written 22 October 2026. Code: `src/footy/snapshot.py` (export), `frontend/src/snapshot.ts`
(the site's side)._

A read-only copy of the website that needs no API server: the frontend built in snapshot mode
(`npm run build:snapshot`) answers every API request from JSON files written beside the page by
`uv run footy export-snapshot`. Every body in them is the API's own response at one moment.

```bash
make snapshot OUT=<folder>                         # the site: index.html, assets/, data files
make snapshot OUT=<folder> PAGE=<dir>/football-model.html   # also the one-file page
```

## Files

| File | What it answers | Fetched |
| --- | --- | --- |
| `snapshot.json` | What every page needs first: health, past results, performance, hit rates, the list of backtest runs, the saved reports and the D-028 summary, the two model leaderboards and the backtest explorer's models list; and the list of shard files with the request keys each answers | At start-up |
| `snapshot-fixtures-1.json` | The fixtures for the next 30 days (`/api/fixtures?days=30`: per fixture, the record of record and its partner with the other basis, and a count of every model's records; docs/model-comparison.md); the page takes every shorter window (1 to 30 days) from it, as the API would (`derived` in `snapshot.ts`) | The first time the fixtures or the Data page is opened (the default page, so on every first visit) |
| `snapshot-data-1.json` | The Data page's coverage, import issues and refresh history | The first time the Data page is opened |
| `snapshot-matches-<n>.json` | Match pages (`/api/predictions/{id}`, with their `teams` block) and each fixture's every-model list (`/api/fixtures/{ref}/models`), a fixture's pages and list always in one file | The first time a match page is opened |
| `snapshot-lab-<n>.json` | Backtest summaries (`/api/backtests/{run}/summary`), each run's in one file | The first time a run is opened in the lab |
| `snapshot-teams-1.json` | The teams list alone (`/api/teams`) | The first time the teams list is opened |
| `snapshot-teams-<n>.json` (n ≥ 2) | Per team: profile, match log and media (`/api/teams/{key}`, `/api/teams/{key}/matches`, `/api/teams/{key}/media`), a team never split across files | The first time one of those teams is opened |
| `snapshot-backtest-<n>.json` | The backtest explorer's matches (`/api/backtest-matches`): one file per league and split (a league's two test seasons together), one per UEFA split | The first time the explorer shows that league or UEFA split |
| `media/teams/<key>/crest.png`, `venue.jpg` | Downloaded, freely licensed pictures the carried teams' media responses link to | By the page's `<img>` |

Each data file has the same shape (`format`, `taken_at_utc`, `bodies`, `entries`: key →
`[status, body index]`; identical bodies stored once per file, ignoring the second each was
made at, `generated_at_utc`; and `parts`, below, when a body uses one). `snapshot.json` adds
`shards`: `[{"file", "bytes", "keys"}]`.

**Shared parts** (`footy-api-snapshot/3`). Every record of a fixture has its own match page,
and each page repeats the fixture's form and stats (`teams`), recent results (`recent`) and
list of records (`editions`): about 7 KB of an 11 KB page. With a model of record, its
fallbacks and some twenty shadow models per fixture (`footy publish-shadows`), that repetition
alone would outgrow the host's 64 MB. So in a match page each of those fields of 256 bytes or
more (`PART_FIELDS`, `PART_MIN_BYTES`) is written once per file, in the file's `parts` list,
and the page holds `{"$part": n}` in its place; identical values share one part, and a
fixture's pages are always in the same file. `snapshot.ts` (`resolveParts`) puts them back
before a page sees the body, so every body is still the API's own response. Nothing else
uses parts.

`frontend/src/snapshot.ts` looks a request up in `snapshot.json`, then in the shard that lists
its key, fetching that shard once (a failed load is retried by "Try again"); a key in no file
answers "Not in this snapshot". A snapshot from before shards existed
(`footy-api-snapshot/1`, no `shards`) or before parts (`footy-api-snapshot/2`) still loads.

A shard is closed once it holds 2 MB (`SHARD_TARGET_BYTES`), or 1 MB for team and match pages
(`PAGE_SHARD_TARGET_BYTES`), as either is often the first page opened from a shared link: on
22 October 2026 a team page loads `snapshot.json` (0.4 MB) and one team file (about 0.9 MB).
No shard may pass 12 MB (`SHARD_MAX_BYTES`). The files are plain JSON; a host that compresses
them (gzip or Brotli) cuts each to between a sixth and a tenth of its size.

## Every model's predictions (docs/model-comparison.md)

The exact requests the snapshot holds, which are the ones the pages must make:

| Request | Where |
| --- | --- |
| `/api/fixtures/{ref}/models`, `ref` encoded as `encodeURIComponent` does (`:` → `%3A`), for every fixture with a genuine pre-match record | With the fixture's match pages |
| `/api/model-leaderboard?group=domestic`, `/api/model-leaderboard?group=uefa` | `snapshot.json` |
| `/api/backtest-models` | `snapshot.json` |
| `/api/backtest-matches?division=<code>&limit=1000&season=<season>` for each `coverage` entry of `/api/backtest-models` (stored under this key: parameters sorted by name; the page may write them in any order) | `snapshot-backtest-<n>.json` |

The explorer filters by team and by model on the page, from the division-season response;
`team=`, `models=`, `offset=` or another `limit` answer "Not in this snapshot". A division and
season holding more than 1,000 matches (`FRONTEND_BACKTEST_LIMIT`; the most is 380) refuses
the export rather than carry a partial list.

## Which teams have pages

Every team on a fixture, past or match card the snapshot holds, every team in the Premier
League, La Liga, Serie A, Bundesliga, Ligue 1 and Championship this season, and every team in
the league phase of the latest UEFA season held (`SNAPSHOT_TEAM_LEAGUES`, `SNAPSHOT_TEAM_UEFA`).
On 22 October 2026 that is 190 teams: 182 on cards, 120 in the six leagues and 108 in the
2026/27 league phases, overlapping. The pages of any other team (an old opponent in a match
log, say) answer "Not in this snapshot".

Per team the snapshot holds exactly what the team page asks for: the profile as the API sends
it (thinned strength series, short season and venue splits; docs/teams.md), the match log's
default page (the latest 100 matches) and the media response. Filters, older log pages and
`series=full` are not held.

## Pictures

- The export collects every downloaded crest and venue photo a carried media response links
  to, crests first (they appear wherever the team does), then venue photos, and copies as many
  as the host's limits leave room for to `media/teams/<key>/`.
- Every picture URL in every body is made relative to the page
  (`/media/teams/x/crest.png` → `media/teams/x/crest.png`). A URL to a file the snapshot doesn't
  carry becomes `null`: a picture left out for the limits, and the generated `badge.svg`, which
  the API draws on request (the site draws its own badge from `badge`). This is the one change
  the export makes to a body.
- The licence, author, attribution text and source link stay in each team's media response,
  next to the picture's URL.
- The report lists `pictures.carried` and `pictures.left_out`. None are downloaded yet
  (docs/research/team-media.md), so the snapshot of 22 October 2026 has none.

## Checks (nothing is written if one fails)

- **No prices** (D-013, D-024): every JSON file, as written, is parsed and searched for
  football-data price columns, price-named keys with fractional values and quoted price cells.
- **No consensus in disguise**: no backtest explorer page may give a summary-only model a
  per-match cell, or two model + market models the same match-result figures on most matches
  (`consensus_cells`): those would be the bookmakers' de-vigged consensus match by match, which
  the price search can't see in plain arrays (docs/model-comparison.md).
- **No secrets**: every file (pictures included) is searched for secret-named `.env` values and
  the provider credentials in the settings, as written, URL-encoded and JSON-escaped.
- **The host's limits** (`HOST_LIMITS`, decimal megabytes): the page and each text file at most
  16 MB, each picture at most 15 MB, at most 255 files and 64 MB in one publish; and no shard
  over 12 MB. The site folder is checked by the export, the one-file page and its files again
  by `write_single_page`.

The site is written to a staging folder and swapped in only when it passes; an earlier
snapshot's shard files and pictures that the new one doesn't have are removed.

## Publishing

`--single-page <dir>/football-model.html` (Makefile `PAGE=`) writes the page with the bundle
inlined and copies `snapshot.json`, every shard and every picture beside it; the report's
`single_page` lists them, and their paths relative to `<dir>` are the published paths. Publish
the page with all of those files.

## Sizes on the real data (22 October 2026)

`make snapshot` on the real database at 13:47 UK time (about seven minutes; 3,489 requests,
all 200), after the first shadow records were published (fourteen models on most league
fixtures of 22–26 October, six on the UEFA ones), with the fixtures list carrying only each
fixture's record of record and its partner, and six model + market models summary only in the
explorer (docs/model-comparison.md):

| File | Bytes | Answers |
| --- | ---: | --- |
| `snapshot.json` | 506,831 | 73 first-screen requests (the two leaderboards and the backtest models list among them) and the shard list |
| `snapshot-fixtures-1.json` | 287,192 | The 30-day fixtures, 114 fixtures with one card each (every shorter window is taken from it). Carrying every shadow record made it 2,395,983 |
| `snapshot-data-1.json` | 495,369 | The Data page's coverage, import issues and refresh history |
| `snapshot-matches-1.json` … `-7.json` | 0.92–0.99 MB each, the last 541,650; 6,291,027 in all | 1,089 match pages and 114 every-model lists, with shared parts |
| `snapshot-lab-1.json` … `-4.json` | 1,975,086 + 1,991,029 + 1,917,249 + 553,964 | 1,608 backtest summaries |
| `snapshot-teams-1.json` | 503,627 | The teams list (873 teams with their names and media) |
| `snapshot-teams-2.json` … `-25.json` | 0.88–0.99 MB each, the last 759,993 | 190 teams × 3 pages |
| `snapshot-backtest-1.json` … `-10.json` | 0.27–0.35 MB each, 3,186,720 in all | The five leagues' explorer matches, validation and test (22 or 7 models a match, of which 6 or 3 summary only) |
| `snapshot-backtest-11.json`, `-12.json` | 155,143 + 646,753 | UEFA validation (3 competitions) and test (6, with qualifying) |
| `index.html`, `assets/*.js`, `assets/*.css` | 371 + 515,958 + 119,419 | The site |
| **Site folder** | **41,250,014 in 54 files** | |
| One-file page (`--single-page`) | 636,623 | Replaces `index.html` and `assets/`: **52 files, 41.25 MB** to publish |

The first visit (the Fixtures page) loads the page, `snapshot.json` and the fixtures file:
1.43 MB, of which 0.79 MB is data (2.9 MB before the fixtures list was slimmed).

In the first export of 22 October (10:11 UK time, before any shadow record), the every-model
additions cost 12 files and 4.69 MB (the explorer) plus 0.38 MB (the
fixture lists, inside the match shards); shared parts saved 0.83 MB on the match pages
(2.82 MB without them). The explorer's cells are three decimal places for probabilities, four
for the RPS, and models are columns (`columns`, `models`), not keys. Once a fixture's parts
are written, each further record of it costs about 3.3 KB of match page and 1.6 KB in the
fixture's every-model list (averages on 22 October), so twenty shadow records on each of about
60 league fixtures would add roughly 6 MB.

Per team: profile about 34 KB (Arsenal 33.6 KB, Aston Villa 35.6 KB), match log about 95 KB,
media 0.5 KB. Before shards the whole snapshot was one `snapshot.json` of 12.2 MB without team
pages; the first screen now loads 0.4 MB. No pictures are downloaded yet, so none are carried.

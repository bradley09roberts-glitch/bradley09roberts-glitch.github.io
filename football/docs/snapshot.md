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
| `snapshot.json` | What every page needs first: health, past results, performance, hit rates, the list of backtest runs, the saved reports and the D-028 summary; and the list of shard files with the request keys each answers | At start-up |
| `snapshot-fixtures-1.json` | The fixtures for the next 30 days (`/api/fixtures?days=30`); the page takes every shorter window (1 to 30 days) from it, as the API would (`derived` in `snapshot.ts`) | The first time the fixtures or the Data page is opened |
| `snapshot-data-1.json` | The Data page's coverage, import issues and refresh history | The first time the Data page is opened |
| `snapshot-matches-<n>.json` | Match pages (`/api/predictions/{id}`, with their `teams` block) | The first time a match page is opened |
| `snapshot-lab-<n>.json` | Backtest summaries (`/api/backtests/{run}/summary`), each run's in one file | The first time a run is opened in the lab |
| `snapshot-teams-1.json` | The teams list alone (`/api/teams`) | The first time the teams list is opened |
| `snapshot-teams-<n>.json` (n ≥ 2) | Per team: profile, match log and media (`/api/teams/{key}`, `/api/teams/{key}/matches`, `/api/teams/{key}/media`), a team never split across files | The first time one of those teams is opened |
| `media/teams/<key>/crest.png`, `venue.jpg` | Downloaded, freely licensed pictures the carried teams' media responses link to | By the page's `<img>` |

Each data file has the same shape (`format`, `taken_at_utc`, `bodies`, `entries`: key →
`[status, body index]`; identical bodies stored once per file, ignoring the second each was
made at, `generated_at_utc`). `snapshot.json` adds `shards`:
`[{"file", "bytes", "keys"}]`. `frontend/src/snapshot.ts` looks a request up in
`snapshot.json`, then in the shard that lists its key, fetching that shard once (a failed load
is retried by "Try again"); a key in no file answers "Not in this snapshot". A snapshot from
before shards existed (`footy-api-snapshot/1`, no `shards`) still loads.

A shard is closed once it holds 2 MB (`SHARD_TARGET_BYTES`), or 1 MB for team and match pages
(`PAGE_SHARD_TARGET_BYTES`), as either is often the first page opened from a shared link: on
22 October 2026 a team page loads `snapshot.json` (0.4 MB) and one team file (about 0.9 MB).
No shard may pass 12 MB (`SHARD_MAX_BYTES`). The files are plain JSON; a host that compresses
them (gzip or Brotli) cuts each to between a sixth and a tenth of its size.

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

`make snapshot` on the real database at 06:22 UK time (about two minutes; 2,478 requests, all
200):

| File | Bytes | Answers |
| --- | ---: | --- |
| `snapshot.json` | 424,564 | 70 first-screen requests and the shard list |
| `snapshot-fixtures-1.json` | 524,729 | The 30-day fixtures (every shorter window is taken from it) |
| `snapshot-data-1.json` | 490,090 | The Data page's coverage, import issues and refresh history |
| `snapshot-matches-1.json` … `-3.json` | 991,242 + 987,720 + 465,600 | 225 match pages |
| `snapshot-lab-1.json` … `-4.json` | 1,975,086 + 1,991,029 + 1,917,249 + 553,964 | 1,608 backtest summaries |
| `snapshot-teams-1.json` | 503,627 | The teams list (873 teams with their names and media) |
| `snapshot-teams-2.json` … `-25.json` | 0.88–0.99 MB each, the last 759,993 | 190 teams × 3 pages |
| `index.html`, `assets/*.js`, `assets/*.css` | 371 + 469,555 + 102,204 | The site |
| **Site folder** | **33,501,949 in 38 files** | |
| One-file page (`--single-page`) | 572,828 | Replaces `index.html` and `assets/`: **36 files, 33.5 MB** to publish |

Per team: profile about 34 KB (Arsenal 33.6 KB, Aston Villa 35.6 KB), match log about 95 KB,
media 0.5 KB. Before shards the whole snapshot was one `snapshot.json` of 12.2 MB without team
pages; the first screen now loads 0.4 MB. No pictures are downloaded yet, so none are carried.

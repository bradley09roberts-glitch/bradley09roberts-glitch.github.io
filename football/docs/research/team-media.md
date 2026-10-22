# Team pictures: crests, grounds and colours

_Built 21–22 October 2026. The owner asked for "photos for teams"; this is how the site gets
them using only freely licensed material, and what it shows when there is none._

## What the site shows

For every team, `GET /api/teams/{team_key}/media` returns:

| Field | What it is | Source |
| --- | --- | --- |
| `crest` | The club's crest, **only** if a freely licensed file has been downloaded from Wikimedia Commons (`kind: "crest"`). Otherwise our own generated badge (`kind: "generated"`) | Commons file named by Wikidata P154; or `footy.media.badge` |
| `venue` | The home ground's name, and its photo once downloaded (else `url: null`) | Wikidata P115 (home venue), its English label and P18 (image) |
| `colours` | The club's official colours, `{name, hex}`, the badge's background first | Wikidata P6364 (official colour) with P465 (sRGB hex) |

Every picture carries `licence`, `licence_url`, `author`, `attribution` (the text to
display) and `source_url` (the Commons file page). A downloaded picture is shown only while
it is of the Commons file `reference/team_media.json` names now: one left from an earlier
crest or venue, or from a Wikidata item since corrected, falls back to the generated badge
(or no photo) until the new file is downloaded. Example, before anything is downloaded:

```json
{
  "team_key": "eng-arsenal",
  "wikidata_url": "https://www.wikidata.org/wiki/Q9617",
  "crest": {"url": "/media/teams/eng-arsenal/badge.svg", "kind": "generated", "licence": null,
            "licence_url": null, "author": null,
            "attribution": "Generated badge, not the club's crest: colours from Wikidata (CC0)",
            "source_url": "https://www.wikidata.org/wiki/Q9617"},
  "venue": {"name": "Emirates Stadium", "url": null, "licence": null, "licence_url": null,
            "author": null, "attribution": null, "source_url": null},
  "colours": [{"name": "red", "hex": "#FF0000"}, {"name": "white", "hex": "#FFFFFF"}]
}
```

Pictures are served from `data/media/teams/<team_key>/` under `/media/teams/<team_key>/`:
`crest.png`, `venue.jpg` and the generated `badge.svg` (made on request, never stored). Only
those names can be fetched (sent with `X-Content-Type-Options: nosniff`, the badge also with
a Content-Security-Policy that allows no scripts); manifests, anything else under `data/` and
team folders that link outside `data/media/teams/` cannot. The Vite dev server proxies only
`/api`, so the frontend needs `/media` added to its proxy (not done here: `frontend/` belongs
to another change).

## Sources and licences

| Source | Used for | Licence | Access |
| --- | --- | --- | --- |
| Wikidata, through the Wikidata Query Service (`query.wikidata.org/sparql`) | Team-to-club mapping; crest file name, venue, venue photo file name, colours | CC0 (no conditions) | Batched SPARQL, one query every 2 s at most, the project's User-Agent with contact details, every response cached in `data/media/wdqs/` |
| Wikimedia Commons (`commons.wikimedia.org` API and `upload.wikimedia.org`) | The crest thumbnail (~128 px PNG) and venue photo (~640 px JPEG), with each file's licence metadata | Each file's own: only CC0, public domain, CC BY and CC BY-SA are kept | Not run yet (see [Running the download](#running-the-download)) |

Nothing comes from English Wikipedia, `www.wikidata.org/w/api.php` or UEFA.com (D-023).

### Why English Wikipedia's crests are not used

Most professional clubs' crests are protected by copyright and are not free. English
Wikipedia hosts them anyway, as **non-free files** uploaded locally (not to Commons) under
its non-free content criteria: one low-resolution copy, used only in the article about the
club, each use justified by a written fair-use rationale. That permission is Wikipedia's own
claim under US fair use; it gives nobody else a licence, and the UK's narrower fair-dealing
exceptions would not obviously cover a crest shown beside every fixture on another site.
So those files are never fetched.

Commons accepts only free files, and Wikidata's P154 must name a Commons file. A club
therefore has a P154 only when Commons judged its crest free, usually as a public-domain
text logo (too simple for copyright) or an old design. Manchester United, Manchester City,
Arsenal and Paris Saint-Germain have none: 75 of the 121 clubs in the five top leagues
since 2024/25 have no P154, and will show the generated badge. A free crest is still the
club's trademark: Commons records that as a restriction (`Restrictions` in its metadata),
which the downloader keeps in the manifest. Showing it to name the club on a personal,
non-commercial site is the use the crest exists for; it must not suggest endorsement.

### Licences kept and refused

The downloader reads each file's `LicenseShortName` and `License` code from the Commons API
(`imageinfo`, `extmetadata`) and keeps the file only if it is CC0, public domain
(including "PD-…" templates), CC BY or CC BY-SA, any version or national port. Anything
else (GFDL alone, "Copyrighted free use", NC or ND variants, anything unrecognised) is
refused, and so is a file whose two fields disagree when either says NC or ND. For a refused
file nothing is saved, and the refusal is logged in the team's `manifest.json` under
`refused` with the licence it found. A refused file is not asked for again.

### How attribution is shown

Each downloaded file's manifest entry holds the source URL (the Commons file page), the
licence and its URL, the author (`Artist`, HTML stripped), the credit, whether attribution is
required, the usage terms, any restrictions, the image's SHA-256 and the time it was fetched.
It also holds the text to display, built as title, author, licence and source:
`Emirates Stadium.jpg by Jane Doe, CC BY-SA 4.0, via Wikimedia Commons`. The API returns that
text with the author, the licence and its link (`licence_url`, which CC BY and BY-SA ask to
be given) and the file page link; the site should show it next to the picture (a caption or
tooltip) or in a credits list on the team page, linked to `source_url`, with the licence
name linked to `licence_url`. The thumbnails are
Wikimedia's own resized copies of the original, under the original's licence.

## The generated badge

`footy.media.badge` draws a plain roundel: the club's most saturated official colour as the
background, the colour that contrasts most with it as a diagonal band, and a three-letter
short name ("Aston Villa" → AVL, "Man United" → MUN, "Paris SG" → PSG) in white or near-black,
whichever reads better. Without Wikidata colours it uses one of five muted neutral pairs,
picked by the team key. The same inputs always give byte-identical SVG. It is deliberately
unlike any crest (no shield, no emblem), its title and accessible name say "generated badge,
not the club's crest", and the API marks it `kind: "generated"`.

## Mapping teams to Wikidata

`reference/team_wikidata.csv` (`team_key, qid, wikidata_label, method, note`) covers every
team with a league or UEFA match since 2024/25 (717 teams). Built by
`uv run footy team-media --map` (`footy.media.mapping`):

1. Our names for a team are its `teams.name` and every `team_aliases` alias (football-data,
   openfootball and Wikipedia spellings, without their `(ENG)` suffix).
2. For each Wikidata country of our countries (home nations share the UK; France includes
   Monaco, Switzerland Liechtenstein, the Faroes Denmark), two queries fetch every item that
   is an association football club (Q476028, its men's and amateur subclasses) with its
   English and language-neutral labels and English aliases (118 queries for 59 Wikidata
   countries), then a few batched queries for the candidates' facts (P31, P17, P118 league, P2094 competition class, P831
   parent club, P576 dissolved, sitelinks).
3. Names are compared strictly (case, accents, punctuation ignored; letters such as ħ, ð
   and þ spelt h, d and th), then, only if that finds nothing, loosely (legal-form words such
   as FC, AC, SV, Calcio also ignored). An item without sitelinks (no Wikipedia describes it)
   is dropped from both passes when another name match has some: Malta's first-team items
   ("Sliema Wanderers", part of the club's item "Sliema Wanderers F.C.") carry the bare club
   name and match strictly, but hold no crest, venue or league.
4. Rejected: women's, reserve, B, C and youth teams (by class, a parent club that is itself a
   football club, a women's or youth competition class, a women's league, or a label such
   as "Arsenal W.F.C.", "Sevilla FC C", "Real Madrid Castilla"), dissolved clubs, national
   teams, beach soccer, disambiguation pages, season items, and clubs of another country.
5. For a team in one of our divisions, the club's league (P118) must be one of that
   division's pyramid (its own league and the tiers next to it). Of several matches, the one
   the league confirms is taken. A short name must also single out one club: an alias or
   loose match is withdrawn when another club in those leagues carries the same words
   ("Zaglebie" matched Zagłębie Sosnowiec by alias; the Ekstraklasa club is Zagłębie Lubin).
6. One club left: `exact_label` (a label matched) or `alias`. Otherwise the team is left
   unmapped, `ambiguous` (candidates in the note) or `unresolved`.

Then every remaining team in the five top leagues, the Championship and the 2026/27 league
phases, and most others, was checked by hand against the same query results: 144 rows are
`manual` (142 then, 2 added in the review below), each with its evidence in the note (the
label, the league, and why the other candidates are other clubs). `--map` keeps manual rows.
Three automatic matches were wrong before the last two rules were added (CD Albacete for
Albacete Balompié, Zagłębie Sosnowiec for Zagłębie Lubin, and a women's club for Dnipro-1);
all three rules are now tested.

### Review (22 October 2026)

An independent review re-checked every `alias` and `manual` row (330) and all 372
`exact_label` rows against the query service (label, description, English Wikipedia article,
country, class, league, parent club, dissolution): right club, right country, not a women's,
reserve or B team. All but one were right. Changes:

| Team | Was | Now | Why |
| --- | --- | --- | --- |
| `mlt-sliema-wanderers` | Q127269055 "Sliema Wanderers" | Q1289495 Sliema Wanderers F.C. | The old item is the club's first-team item (part of Q1289495, P361): no sitelinks, crest, venue or league. Fixed in the code (the sitelinks rule in step 3), so `--map` gives the same |
| `mlt-hamrun-spartans` | unresolved | Q343030 Ħamrun Spartans F.C. | "Ħ" was dropped instead of read as "H"; fixed in the code (step 3) |
| `alb-dinamo-city` | unresolved | Q34120 (manual) | Its English Wikipedia article is "FC Dinamo City", the name Dinamo Tirana took; its English label still has the old name |
| `ukr-dnipro-1` | unresolved | Q30964198 SC Dnipro-1 (manual) | The club of the July 2024 qualifiers; its item is a defunct club (dissolved 2024), which the automatic pass skips |
| `deu-regensburg` | Q265570 | Q265570 | Unchanged club; the note no longer counts a duplicate item without sitelinks |

`kaz-yelimay` stays Q120081555, the club founded in 2022 that plays in the Premier League
(English Wikipedia's "FC Yelimay" article sits on the older Spartak Semey item). With the
rows above, `uv run footy team-media --map --offline` reproduces the CSV exactly; the four
new teams' facts were fetched on 22 October and merged into `team_media.json`.

### Coverage (22 October 2026, after the review)

| Group | Teams | Mapped (automatic + manual) | Unmapped | Crest file (P154) | Venue | Venue photo (P18) | Colours |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Premier League (E0) | 25 | 25 (25 + 0) | 0 | 1 | 25 | 25 | 25 |
| La Liga (SP1) | 26 | 26 (19 + 7) | 0 | 3 | 26 | 26 | 16 |
| Serie A (I1) | 24 | 24 (22 + 2) | 0 | 15 | 24 | 24 | 18 |
| Bundesliga (D1) | 23 | 23 (20 + 3) | 0 | 17 | 23 | 23 | 12 |
| Ligue 1 (F1) | 23 | 23 (20 + 3) | 0 | 10 | 23 | 21 | 13 |
| Championship (E1) | 34 | 34 (31 + 3) | 0 | 2 | 34 | 34 | 33 |
| Champions League 2026/27 league phase | 36 | 36 (34 + 2) | 0 | 15 | 36 | 35 | 27 |
| Europa League 2026/27 league phase | 36 | 36 (35 + 1) | 0 | 19 | 36 | 35 | 23 |
| Conference League 2026/27 league phase | 36 | 36 (33 + 3) | 0 | 12 | 36 | 34 | 14 |
| The 20 other league divisions | 416 | 409 (296 + 113) | 7 | 135 | 395 | 372 | 155 |
| UEFA matches only | 194 | 189 (168 + 21) | 5 | 42 | 171 | 146 | 52 |
| **All teams** | **717** | **705 (561 + 144)** | **12** | **207** | **673** | **624** | **289** |

A team counts in every division it played in since 2024/25 (a club promoted from the
2. Bundesliga is in both D1 and the other divisions). "Colours" counts clubs with at
least one official colour that has a hex value; Wikidata's colours are mostly plain named
colours ("red" is `#FF0000`), not exact kit shades.

### Unmapped (12)

| Team | Why |
| --- | --- |
| `ukr-kryvbas` | Ambiguous: two items both labelled "FC Kryvbas Kryvyi Rih" in the Ukrainian Premier League (the club dissolved in 2013 and its 2020 successor); not guessed |
| `irl-galway` | Ambiguous: "Galway FC" and "Galway United F.C." are both in the League of Ireland; which item is today's club is not clear from Wikidata |
| `esp-celta-b`, `esp-sociedad-b` | Reserve sides playing in Segunda: B teams are excluded by design |
| `ita-padova` | The only Padova item, Calcio Padova, is marked dissolved (2014); the refounded club has no clear item |
| `rou-gloria-buzau` | The only item is marked dissolved; the current club has none |
| `tur-erzurumspor` | The name match Q1366316 is marked dissolved (2010). The item with English Wikipedia's "Erzurumspor F.K." article, Q2426819, had the English label and description of Atlético Morelia (a Mexican club) on 22 October 2026, so its facts are not trusted until that is repaired |
| `smr-tre-penne` | Q704857, the item with English Wikipedia's "SP Tre Penne" article, had a vandalised English label and description on 22 October 2026; not taken until they are repaired |
| `rou-metaloglobus-bucharest`, `blr-ml-vitebsk`, `gib-magpies` | No club item with an English or "mul" label of that name in the country (FCB Magpies may be F.C. Magpies, Q19577058; not confirmed by Wikidata, so not guessed) |
| `alb-elbasani` | Two candidates, KS Elbasani (First Division) and AF Elbasani (Superliga); the July 2026 qualifiers don't say which; not guessed |

## Media facts

`reference/team_media.json` holds one entry per mapped team (705; the four teams changed in
the review were fetched on 22 October, the rest on 21 October): `qid`, `wikidata_label`,
`crest_file` (P154), `venue` (`qid`, `name`, `image_file`) and `colours` (`qid`, `name`,
`hex`). Of several logos or venues, a current one (no end time) is taken, preferred rank
first, then the latest start time. Built by `uv run footy team-media --facts` (15 queries,
batches of 150 clubs).

## Running the download

The download has **not been run**. On 21 October Wikimedia was answering this machine with
HTTP 429. The one probe allowed (`footy team-media --probe`, one Commons API request at
23:42 UTC) got **HTTP 403**, not 429: either Wikimedia is still refusing, or the network
proxy this session runs behind does not allow `commons.wikimedia.org`. The probe cannot tell
which, and it was not repeated.

When a probe answers `"ok": true`:

```bash
cd football
uv run footy team-media --probe          # one request; go on only if "ok": true
uv run footy team-media --download       # every mapped team; resumable
uv run footy team-media --download --team eng-liverpool   # or a few teams first
uv run footy team-media                  # summary: mapped, facts, downloaded, refused
```

- About 207 crests and 624 venue photos: two requests each (the API, then the thumbnail),
  one request per second in all, so about 28 minutes for a full run.
- The first HTTP 429 or 403 stops the whole run. The report gives `stopped_at` (team and
  URL) and how many teams finished; a 429's Retry-After is saved in
  `data/media/teams/_download_state.json`, and the next run refuses to start until it has
  passed. A 502/503/504 with a Retry-After of at most two minutes is waited out once.
- Resumable: a file already on disk under the same Commons file name is skipped, and so is
  one already refused for its licence. Just run the same command again.
- To refresh the mapping or the facts later (Wikidata only): `uv run footy team-media --map
  --facts`. Answers come from the query cache where the query is unchanged;
  `--offline` makes no requests at all.

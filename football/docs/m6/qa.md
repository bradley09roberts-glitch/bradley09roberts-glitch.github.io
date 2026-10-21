# M6 website QA

_Run on 21 October 2026, about 02:10–02:50 UK time._

All six pages were checked at 390 × 844 (phone) and 1280 × 800 (desktop), in light and dark
themes, against the real API. Pages that the real data can't fill yet were also checked with
synthetic test data (see [Method](#method)).

**Result:** every check passes after the fixes listed below. `npm run typecheck` and
`npm run build` are clean, and no npm dependencies were added.

## What the real data allows

The database holds 59,878 matches, 14 domestic backtest runs and 31 job runs. It has **no saved
predictions or settlements**, and the benchmark and model-selection reports don't exist yet. So
against the real API:

| Page | What it shows |
| --- | --- |
| Fixtures | Empty state: "No predictions for the next 8 days", with a button to look 14 days ahead |
| Match (`#/match/1`) | "No such prediction" (a real 404 from the API) |
| Past matches | Empty states for both the genuine and the retrospective record |
| Performance | "No pre-match predictions published yet", plus the separate betting-record box |
| Backtest lab | Fully populated from the 14 real runs: metrics, calibration chart, run table, exports. The market benchmark says "Not available yet" |
| Data and methodology | Fully populated: status, sources, method, coverage, exclusions and refresh history |

## Screenshots (real data only)

These are in [`screenshots/`](screenshots/), named `<page>-<width>.png` and taken in the light
theme from the production build (`vite preview`) against the real API:

- `fixtures`, `match`, `past`, `past-retrospective`, `performance`;
- `lab`, `lab-totals`, `lab-btts`;
- `data`, and `notfound` (an unknown route).

Each comes at both widths (390 and 1280). For a long page the viewport was stretched to the
page's full height, so the phone tab bar appears at the bottom of the image instead of floating
over the middle. One side effect: the coverage table's height limit is 70% of the viewport, so in
`data-*.png` the table appears in full. On a real phone it scrolls inside its box.

None of the synthetic screenshots are in the repository.

## Checks and results

| Group | What was checked | Result |
| --- | --- | --- |
| Real-data sweep | 10 routes × 2 widths × 2 themes = 40 page loads. On each: no horizontal page scroll (`scrollWidth` ≤ viewport width, and no element sticks out unless it sits inside its own scroll box); no button group with options cut off; no page errors; no URL requested twice in one load (React StrictMode is on in dev); the right nav tab highlighted; the right page title | 40 / 40 |
| Narrow phones | The same routes at 320 and 360 px, with real and synthetic data | 40 / 40 |
| Populated layouts (synthetic) | 12 routes per width, light and dark: Fixtures (all and one competition), an upcoming match and a settled one, Past (genuine, retrospective, 0.5 line with 80 rows), Performance, the three Lab tabs with a benchmark report, and Data with failed, partial and running jobs | All pass |
| Back navigation | `#/fixtures` → card → `#/match/:id` → browser Back: returns to `#/fixtures` at the same scroll position. Forward returns to the match. The in-app Back link does the same | Pass at both widths |
| Back navigation (real API) | `#/fixtures` → `#/match/1` (real 404) → Back, and the same via the in-app Back link. A directly opened match: Back link goes to Fixtures. `#/match/abc` is rejected without a request. Fixtures → Past → Performance → Lab → Data, then Back four times retraces them in order | Pass |
| Filters and history | Filters live in the hash. A run of filter changes is one history entry. Back from a match keeps the filtered view. "Show more" on Past keeps its count through Back. Past → match → Back restores the scroll position | Pass |
| Loading | Each page's main request was delayed by 2.5 s: a skeleton with screen-reader text shows, then goes | Pass on all 6 pages |
| Error | HTTP 500: "Couldn't load …" with the status and role=alert, and Try again recovers. A request that never reaches the API: "Can't reach the API" | Pass on all 6 pages |
| Empty | Fixtures, Past and Performance (both "nothing published" and "published, none settled"); Lab with no runs; Data with no coverage, issues or job runs. Each fits the width | Pass |
| Stale | Data loaded, clock moved 45 s on (past the 30 s reuse window), then the refresh fails: the earlier copy stays with "Showing the copy loaded at …", and Refresh clears it once the API answers. Fixtures also warns when `/api/health` reports a failed data refresh | Pass |
| Out-of-order responses | Fixtures: a slow 3-day response arriving after a fast 14-day one is ignored. Past: a slow Champions League response after a fast Premier League one is ignored. Lab: a slow E0 summary after a fast D1 one is ignored | Pass |
| Requests | Going back to a fixtures window loaded under 30 s ago sends no request. On Lab: one request per endpoint on load, cached filters reuse their copy, and tab or line changes send nothing | Pass |
| UK time | On a device set to New York time, a 16:45 UTC kickoff shows as 17:45. Naive UTC timestamps with microseconds, as the real API sends them, show correctly too | Pass |
| Lab and Data interactions | 26 checks per width: tabs and arrow keys, Back between tabs, reset, run picked from the table, unknown run id, summary error and retry, ledger CSV link (206 response), coverage filters, issue examples, problems filter | 26 / 26 at each width |
| Extras | Footer stays clear of the phone tab bar at the bottom of every page. "Also predicted by" opens the other model's record. A click anywhere on a card opens it. Keyboard: Tab reaches a fixture link with a visible focus ring, and Enter opens it. Focus then moves to the page, and the next Tab reaches the Back link | 44 / 44 |
| Betting edge | Synthetic cards with and without odds: with odds, "No qualifying bet"; without, "No odds available — no betting selection". The page-wide no-odds note shows only when no fixture has odds. Probabilities stay visible either way | Pass |
| Production build | The real-data sweep, the behaviour suite, the extras and the Lab/Data checks were run again against `vite preview` of `dist/` | All pass |

The browser console shows two expected 404s: `/api/reports/benchmark` and
`/api/reports/model_selection` don't exist yet, and the page says so. There are no other console
errors.

## Bugs found and fixed (frontend only)

1. **Cut-off options on the Data page at phone width.** On the coverage "Leagues" control,
   "Other European" was half hidden and "All" was off-screen: the group was 380 px wide in a
   322 px box and could only be scrolled sideways. Buttons in these groups can now wrap their
   labels, so all options fit at 320 px and up (`components/controls.css`). A new check flags any
   button group whose options are cut off.
2. **Wrong tab highlighted on the match page.** A match opened from Past matches (or
   Performance) highlighted Fixtures. The match page now highlights the section the reader came
   from. Opened directly, it still highlights Fixtures (`App.tsx`).
3. **Scroll position lost on Back after reloading the same address.** Loading the current
   address again (Enter in the address bar) replaced the history entry's saved state, so Back to
   it later opened at the top. In the test the Past list came back at 0 instead of 3,434 px. The
   router now restores the entry's key in that case (`router.ts`).
4. **No page heading on the match page** while it loads, after an error or for an unknown id. A
   hidden "Match" `h1` is now there until the match title replaces it (`pages/Match.tsx`).
5. **Settlement status shown as a raw code** ("postponed") on fixture cards, the match header and
   Past rows. It now reads "Postponed", "Void" and so on (`format.ts`, `Fixtures.tsx`,
   `Match.tsx`, `Past.tsx`).
6. **Robustness: microsecond timestamps.** Python's `isoformat()` can send six decimal places of
   a second, which some older browsers won't parse. `parseUtc` now keeps three (`format.ts`).
7. **Spacing:** the first benchmark heading ("Test") sat flush against the intro sentence
   (`pages/Lab.css`), and so did the first coverage control (`pages/Data.css`).

Some of these edits (`router.ts`, `App.tsx`, `controls.css`) were already in the WIP checkpoint
commit `6dadbd5`, which was made while QA was running. The rest are uncommitted.

## Not changed (for the API or later)

- **Populated pages on real data.** Fixtures, Match, Past and Performance can only be seen
  populated once predictions are published and settled. Their layouts were checked with
  synthetic data only.
- **`/api/import-issues` is about 1.0 MB.** The Data page shows 5 examples per reason. A summary
  or per-reason limit parameter would make the page much lighter on a phone. `/api/coverage` is
  about 180 kB.
- **Timestamps without a UTC offset.** Prediction and settlement timestamps come from SQLite as
  naive strings, which the site reads as UTC per D-005. Sending `+00:00` would remove the
  ambiguity.
- **Desktop layout.** At 1280 px every page is a single 760 px column. That is readable, but long
  fixture lists could use two columns on wide screens. This is a design choice, not a defect.

## Method

- **Servers.** API: `uv run footy serve` on port 8000. Site: `npm run dev` (Vite, port 5197),
  and `vite preview` of the production build (port 4197). Both proxy `/api` to the API.
- **Browser.** Playwright with Chromium (`/opt/node-tools/node_modules/playwright`).
- **Synthetic data.** Where the real API has nothing, Playwright intercepted the API calls and
  returned synthetic JSON. Every record is labelled `SYNTHETIC TEST DATA`, uses invented teams
  such as "Test Town" and "Mock FC" and models such as `test_model_A`, and comes from a script
  kept in the session scratchpad, outside the repository.
- **Nothing synthetic shipped.** A search of `frontend/src` and `frontend/dist` for `SYNTHETIC`,
  `Test Town`, `Mock FC` and `test_model` finds nothing. Nothing synthetic is in `screenshots/`.

## Model + market records (21 October 2026, about 05:15–05:30 UK time)

New tonight: model + market records (`market_tilt_dc`), which blend in football-data's
pre-closing prices (D-024), sit beside the pure model's records. This pass checked the site and
API support for them, on the real database only. No API calls were intercepted and no synthetic
data was used in the browser.

**What the real data holds.** 155 genuine pre-match records (64 `dixon_coles`, 45 `pan_euro_mix`, 45 `pan_euro_mix_mxg`, 1 `market_tilt_dc`). Levante v Ath Bilbao (La Liga, Wed
21 Oct, 19:00 UK) has both a `dixon_coles` record (#56) and a `market_tilt_dc` record (#110);
it is the only fixture with both. `primary_models.json` names `dixon_coles` (domestic) and
`pan_euro_mix_mxg` (UEFA) as plain strings, so #56 is Levante's record of record and the
Champions League cards lead with `pan_euro_mix_mxg`. Nothing is settled yet.

### What changed

- **API.** `primary_models.json` may give a list per group, e.g. `["market_tilt_dc",
  "dixon_coles"]`: per fixture, the record of record is the first listed model with a genuine
  pre-match record for it. A plain string still works. Cards carry `is_primary` and `basis`
  (`model_and_market` or `model_only`). `/api/predictions/{id}` adds `market` (the de-vigged
  consensus probabilities, books, collection time and provenance, never the prices) and
  `counterpart` (the same fixture's record with the other basis). `/api/performance` adds
  `by_basis` and `paired`. A `value_selection` placeholder appears only on records made with real
  prices, and it names no bet. Every API timestamp now has a `+00:00` offset.
- **Site.** A "Model + market" badge (solid accent) or a "Model only" badge (outlined) now
  appears on every fixture card and in the match header. Past rows show the badge for model +
  market records. On a fixture card the record of record leads, and the others are listed as
  "Also predicted by market_tilt_dc (model + market)".
  - **A model + market match page** gets a "Model + market" section: "Bookmakers' prices
    collected 21 Oct 2026, 04:06 (UK time) were blended in; the pure model said Levante win 37%,
    draw 27%, Ath Bilbao win 36%". It also has a side-by-side table (model + market, pure model,
    change in points), the consensus probabilities and the price file's SHA-256.
  - **A model-only match page** with a model + market counterpart links to it.
  - **Performance** shows the record of record per group, then the model + market and model-only
    records separately, with a like-for-like line.

### Checks and results

| Check | Result |
| --- | --- |
| `uv run pytest -q tests/test_api_data.py tests/test_health.py tests/test_api_records.py tests/test_publish.py` | 24 passed |
| `npm run typecheck`, `npm run build` (frontend; there is no `npm test`) | Clean |
| Browser sweep: production build (`vite preview`, port 4311) against `uv run footy serve --port 8011`, 6 views (Fixtures, Fixtures filtered to La Liga, UCL match #114, Levante #110 and #56, Performance), each at 320, 390 and 1280 px, in light and dark, with the device clock set to New York time | 222 / 222 checks pass |
| Per view, in that sweep | The right title; no horizontal scroll; no page or console errors |
| Fixtures | One basis badge per card. Levante card: "Model only", 19:00 UK, "Also predicted by market_tilt_dc (model + market)", "shown: dixon_coles". Bayern v Arsenal leads with `pan_euro_mix_mxg` at 20:00 UK. La Liga filter: 11 fixtures |
| Levante #110 (model + market) | Header badge; the explanation sentence above, word for word; table rows "Levante win 28% 37% −9" and "Ath Bilbao win 45% 36% +9"; SHA-256 prefix shown and "The prices themselves are not stored or shown"; "No selection published"; expected goals labelled as the pure model's |
| Levante #56 (model only) | "Model only" badge; a note and link to #110; "No odds available" |
| UCL #114 | "Model only", "Wed, 21 Oct, 20:00 (UK time)", no model + market section |
| Performance | Model + market: 1 published (`market_tilt_dc`). Model only: 109 (`dixon_coles`, `pan_euro_mix_mxg`). "Like for like: 1 fixture has both kinds of record. None of them has been settled yet." No £ figure: the betting record still says none yet |
| API scan of 15 endpoints on the real database | 1,343 timestamps, all ISO 8601 with `T` and `+00:00`. Before: `/api/coverage` sent 234 `retrieved_at` values naive, and #110's `quote_time_utc` used a space. No price fields anywhere: the only price-like key names are `market_prices_used` (derived probabilities), job counters and coverage percentages |

Screenshots (light theme, real data), in [`screenshots/`](screenshots/), each at 390 and 1280:

- `mm-fixtures`: the first screen only. The full list is 56,000 px tall at 390.
- `mm-fixtures-laliga`: the La Liga filter in full, including the Levante card.
- `mm-match-ucl`: Bayern Munich v Arsenal.
- `mm-match-levante-market`: #110, the model + market record.
- `mm-match-levante-model`: #56, the pure model's record.
- `mm-performance`.

Fix made during this pass: at 390 px the pure model's name wrapped mid-word in the comparison
table header ("dixon_cole / s"). The name now sits in the link under the table.

### Not changed (for other strands)

- **The saved `expected_goals` of a `market_tilt_*` record are its base model's** (1.28 and
  1.26 for both #56 and #110). `MarketTilt` passes the base model's means to
  `markets_from_grid` rather than the tilted grid's means. Read on their own, they disagree
  with the blended result (an away win at 45% while the home side "expects" more goals). The
  site and the API's explanation therefore label them as the pure model's averages, from before
  the blend. The fix belongs in `footy.modeling.lab.market_blend`. This pass doesn't touch it,
  and saved records can't change.
- **`quote_time_utc` is the time `fixtures.csv` was retrieved** (04:06 UK), not football-data's
  collection deadline (Tuesday 13:00). The record's field name stays; the site now says the
  prices were "available by" that time and gives the file's download time beside its source
  (verifier change, below).

### Verifier pass (21 October 2026, about 05:40–06:10 UK time)

Fixes made to this strand's files:

- **Change column.** It was rounded from the raw difference, so two rows contradicted the
  figures beside them (Draw 28% v 27% showed "0"; Over 1.5 74% v 72% showed "+1"). It is now the
  difference of the whole percentages shown (+1 and +2).
- **320 px.** "Change" in the comparison table header broke mid-word ("Chang / e"). Below
  380 px the label column is narrower; no header word breaks at 320, 340, 360, 390 or 1280 px.
- **Collection time wording**, as above.
- **`primary_models.json` validation.** An empty list was accepted and left the group with no
  record of record; it is now an error, like any other malformed value.
- **Club display names come from the teams table:** "Ath Bilbao" is football-data's name for
  Athletic Club.
- **`primary_models.json` was not edited.** With the list form (`["market_tilt_dc",
  "dixon_coles"]`), Levante's card would lead with #110, and the headline record would count it.
  Fixtures without prices would keep `dixon_coles`.

## Models of record, signal wording and live prices (21 October 2026, about 06:25–06:50 UK time)

Real database only in the browser; no API calls intercepted. Synthetic data was used only in
pytest, and is labelled there.

### What changed

- **Models of record with effective-from times** (D-026). `primary_models.json` now gives
  each group a list of periods. A fixture takes the period in force at its kickoff, so
  naming a new model of record never changes a fixture that has already been played.
  - The converted file: UEFA is `pan_euro_mix` until 2026-10-21T04:30:00Z, then
    `pan_euro_mix_mxg`; domestic is `dixon_coles` throughout.
  - The record-of-record flag of all 155 saved records is the same before and after
    (109 primary).
  - `/api/performance` adds `primary_model_periods`. `primary_models` is the list in force
    now. New command: `uv run footy set-model-of-record --group … --models … --from now`.
- **Betting edge on a model-only record whose match has a model + market record.** It no
  longer says "No odds available".
  - The match page says "See the model + market record — this one uses no prices", with a
    link and a note naming `market_tilt_dc`.
  - The fixture card has the same words, without a link: the card's "Also predicted by" line
    links to that record.
- **Confidence label on model + market records.** "Model confidence" becomes "Forecast
  confidence (bookmakers + model)", and the note says the probabilities are the bookmakers'
  consensus blended with the model. Model-only records keep "Model confidence". The Data
  page's description of the three signals says both.
- **`/api/import-issues`** no longer sends rejected price cells. All 16 `invalid_price`
  details now read like `B365H: not a valid price (value withheld)`. Only two kinds of
  detail still quote a cell value, and neither is a price: `no_result` (`FTHG=''`) and
  `other_league_label` (`League='Challenge League'`).

### Checks and results

| Check | Result |
| --- | --- |
| `uv run pytest -q` on `test_models_of_record`, `test_api_records`, `test_api_data`, `test_fd_fixtures`, `test_live_market`, `test_health`, `test_publish` and `modeling/test_lab_market_blend` | 110 passed |
| `uv run pytest -q` on `modeling/test_lab_market_blend`, `test_live_market` and `modeling/test_no_future_leakage` (run after the `market_blend` change) | 69 passed |
| `uv run ruff check` and `ruff format --check` on the 13 Python files changed | Clean |
| `npm run typecheck`, `npm run build` | Clean |
| Browser: production build (`vite preview`, port 4323) against `uvicorn footy.api.main:app` on port 8023, 390 px, light theme, device clock set to New York time | 18 / 18 checks pass |
| Levante #56 (model only) | "Model confidence"; no "No odds available"; "See the model + market record", whose link goes to `#/match/110`; the note names `market_tilt_dc`; kickoff 19:00 UK; no horizontal scroll; no page or console errors |
| Levante #110 (model + market) | "Forecast confidence (bookmakers + model)" and no "Model confidence"; "No selection published"; kickoff 19:00 UK; no horizontal scroll; no errors |
| Fixtures, La Liga filter | The Levante card (led by `dixon_coles`, model only) points to the model + market record. The other 10 La Liga cards, which have no prices, still say "No odds available". No horizontal scroll and no errors |

Screenshots (light theme, real data, 390 px, the viewport stretched to the full page height),
in [`screenshots/`](screenshots/):

- `wording-match-levante-model-390.png`: #56, the pure model's record;
- `wording-match-levante-market-390.png`: #110, the model + market record.

Both servers were stopped after the run.

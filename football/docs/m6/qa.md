# M6 website QA

_Run on 21 October 2026, about 02:00–02:45 UK time._

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

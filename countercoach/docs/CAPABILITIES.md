# CounterCoach — Integration capability investigation

- **Research date:** 2026-10-08
- **Deadlock client build observed in data:** `client_version 6763`, `server_version 6763`,
  `source_revision 11102088`, version date `Oct 07 2026 17:30:07`
  (source: `GET https://api.deadlock-api.com/v1/assets/steam-info`, retrieved 2026-10-08).
- **Latest patch announcements seen:** "Mind the Birds!" (Baba release, 2026-10-06),
  "Minor Update – 10-05-2026", "City Never Sleeps" (2026-09-29)
  (source: `GET https://api.deadlock-api.com/v2/patches`).
- **Investigation environment:** Linux cloud container (Ubuntu 24.04, no GPU, no Windows,
  no Steam, no Deadlock client). **Nothing in this report was tested inside a running
  Deadlock match.**

## 1. Decision

**Primary route: B — external Windows companion application** (Electron + React), with a
compact overlay window and a separate-window mode. It is an *external companion*, not a
native mod. It reads only explicit manual input, user-loaded scenario files, post-match replay
files and, when the player presses a hotkey, a local capture of their own screen (the
experimental screen reader, §2c).

Route A (native Panorama HUD addon packaged as a VPK) was evaluated and **not built**:

1. No documented Valve interface exposes match state to a HUD addon. Community in-game
   probes (2026-08-23, Predi-i/Deadlock-UI-Mods `HTML_TRANSPORT_HANDOFF.md`) report that
   the Deadlock Panorama script global scope contains only `Promise`, `JSON`,
   `globalThis`, `$` and `panorama`. `Game`, `Players`, `Entities`, `GameEvents`,
   `GameUI`, `CitadelUI` and `$.persistentStorage` are absent, and
   `$.AsyncWebRequest` logs "AsyncWebRequest has been removed".
2. The only reported way to read state natively is to scrape player-visible HUD label
   text (for example the `hudCurGoldLabel` panel bound to `{i:hud_cur_gold}` in
   `hud_gold_and_ap_container.xml`). That is fragile, undocumented and untested here.
3. The only reported route for an addon to receive external data is an embedded
   `CitadelHTMLPanel` browser used as a side channel. It is undocumented and not a
   sanctioned interface, so CounterCoach does not use it.
4. Loading addons requires editing `game/citadel/gameinfo.gi` to add `citadel/addons`
   to the search path. The task brief forbids weakening game validation to force an
   addon to load. No primary Valve statement permitting this was found.
5. Building a VPK needs the Windows-only Reduced CSDK 12 compiler, and validating it
   needs the game. Neither is available in this environment.

The recommendation engine (`packages/engine`) has no UI or Node dependencies. A future
native frontend could reuse it if a permitted, verified interface appears.

## 2. Capability table

Legend: **Verified** = demonstrated with evidence in this project. **Unavailable** =
evidence that the channel does not provide it, or use is excluded by policy.
**Untested** = plausible from community evidence but not demonstrated here.
**Implemented** = working in CounterCoach via the stated channel.

### 2a. Native UI access (Panorama HUD addon)

| Capability | Status | Evidence / notes |
|---|---|---|
| Own hero | Untested | No `Game`/`Players` API (probe). Would require HUD scraping. |
| Game mode, match time, phase | Untested | No documented API; HUD clock label scraping only. |
| Spendable souls | Untested | `hudCurGoldLabel` text `{i:hud_cur_gold}` exists in HUD layout (mntbliss/Deadlock-QoL-HUD-Mod `assets/panorama/layout/hud_gold_and_ap_container.xml`). Scraping not tested. |
| Owned items / inventory restrictions | Untested | Inventory panels exist; no item-ID API documented. |
| Unspent ability points, unlocks, tiers | Untested | AP container exists in HUD layout; no API. |
| Enemy/allied heroes and visible items | Untested | Top bar/scoreboard panels exist; no API. |
| Shop-open state | Untested | Community CSS reacts to shop open (QoL HUD README: "Shop (B) restores vanilla layout"). Not tested here. |
| Display a recommendation panel | Untested | `$.CreatePanel` works in HUD context (community probe). No supported way to deliver data into it. |
| Timestamps, update frequency, delay | Untested | `$.Schedule` polling is used by community mods (for example a 0.12 s poll in `mntbliss_stats_monitor.js`). |
| Installation | Unavailable (policy) | Requires `gameinfo.gi` edit. No primary Valve permission found. Excluded by the brief. |

### 2b. External data (Deadlock API, community project, not Valve)

| Capability | Status | Evidence / notes |
|---|---|---|
| Hero roster, abilities, level thresholds | **Verified / Implemented** | `GET /v1/assets/heroes`. 65 records, 40 `player_selectable && !disabled`. |
| Item catalogue, costs, tiers, components, properties | **Verified / Implemented** | `GET /v1/assets/items`. 250 upgrades, 173 shoppable and not disabled. |
| Ability point cost per tier | **Verified (derived)** | Not a field. Derived from `/v1/builds` `currency_changes` (1/2/5 points; unlock is a separate currency) and cross-checked against `level_info` (32 points = 4 × (1+2+5)). |
| Ability unlock-order statistics | **Verified / Implemented** (weak prior) | `GET /v1/analytics/ability-order-stats`, filtered to the current patch window. |
| Client build and patch freshness | **Verified / Implemented** | `GET /v1/assets/steam-info`, `GET /v2/patches`. |
| Active match list | Untested for this use | `GET /v1/matches/active` lists public matches. It does **not** give the current player's private, immediate match state. |
| Live match events | Unavailable for live advice | `deadlock-live-events` parses the **broadcast demo stream** (spectator data: every player's position, net worth and abilities). This is delayed spectator information beyond what a player sees. Allowed only in the separate replay/post-match adapter. |
| Rate limits | Verified | Response headers `ratelimit-limit: 200`, `ratelimit-period: 60`. Ingest caches results and uses bounded retries with backoff. |

### 2c. Visible-screen extraction

| Capability | Status | Notes |
|---|---|---|
| User-triggered capture of the screen the game is on | **Implemented** (experimental) | Hotkey (default Ctrl+Alt+R, 1 s delay so you can hold Tab) or button. Electron's documented `desktopCapturer` grabs the display under the mouse; the overlay is made transparent for that moment. Verified on Linux/Xvfb and on the Windows CI runner. Exclusive fullscreen is expected to capture black (detected and reported). |
| Reading item icons from a capture | **Implemented** (experimental); checked on crops of **one real capture** and on synthetic scoreboards | Pure-TypeScript template matching (`packages/engine/src/vision`) against each item's unique art, ignoring the tier-badge corner and using the badge as evidence. On the real capture both items read correctly and confidently; the first version did not work on it (see TEST_RESULTS.md, "What the first real capture showed"). Synthetic: 99.6% identified, 0 wrong confident reads. |
| Telling players apart | **Implemented**: once per match by the user, then by position | Deadlock's Tab view puts each player in a column; calibration detects that. Portraits only name a player when clearly confident, because skins change them (a real bot's Abrams did not match the API art). |
| Knowing *where* the scoreboard draws items | **Calibrated by the user, once** | Three boxes on their own capture: the item strip under the cards, one icon, optionally one card portrait. Stored as screen fractions; a different aspect ratio asks for recalibration; an oversized area triggers a warning. Calibration also learns every item slot it can see (one card per player, gaps filled, one spare row); later reads check only those slots, which is faster (about 0.2 s instead of 2–3 s) and can't pick up scenery. More slots can be added from later captures. |
| Reading again whenever the player holds Tab | **Implemented** (opt-in, off by default); verified on Linux/Xvfb with a simulated key | A passive global keyboard listener (`uiohook-napi` 1.5.5, a low-level keyboard hook, the mechanism push-to-talk apps use). It only observes: Tab still reaches the game, nothing is blocked, sent or injected, and no key other than Tab is acted on or stored. Holding Tab for 0.45 s (adjustable) captures once; a tap, Alt/Ctrl/Win+Tab, Tab while CounterCoach has focus, and presses within 1.5 s of the last read are ignored. A Tab read that finds no scoreboard changes nothing. On the Windows CI runner the packaged app's listener started and saw a Tab key sent by the script (run [37791995782](https://github.com/bradley09roberts-glitch/bradley09roberts-glitch.github.io/actions/runs/37791995782)). There is no Valve statement about keyboard listeners; it does not touch the game process. Not yet tried with Deadlock running. |
| Uncertain identifications | **Implemented** | Never auto-applied: the review screen shows the top three candidates for each uncertain icon and asks who each row belongs to. Machine-only reads are medium confidence and never override your own corrections. |
| Whether the real Tab scoreboard shows **enemy** items | **Unverified** | A Nov 2024 forum bug report says holding Tab stopped showing other players' items "by default" ([thread](https://forums.playdeadlock.com/threads/cannot-view-enemy-items.47536/)); a same-month patch made the shop's Recent Purchases log colour enemy vs ally purchases. The reader can only see what the game shows. |
| Privacy | **Implemented** | Captures are processed in memory and discarded. Saving copies is opt-in (last 20 kept) with a delete button. Nothing is uploaded. Templates are built on the user's PC from the item art (a one-time download from the same CDN as the in-app icons); no game art ships in the installer. |

How this differs from an in-game HUD mod (the approach of the earlier "Deadlock live adaptive
build assistant" project and of the community Item Assistant mod): those run JavaScript inside
the game's Panorama UI and read the HUD's own panels, which needs a mod installed through
`citadel/addons` and `gameinfo.gi`. CounterCoach stays outside the game and reads only pixels
already on the player's screen, when the player asks.

### 2d. Manual input and scenario/replay

| Capability | Status | Notes |
|---|---|---|
| Hero, roster, lane opponents, souls, items, ability tiers, observed threats | **Implemented** | Manual adapter. Every observation has source, time, game time and confidence. |
| Scenario playback | **Implemented** | Timestamped fixtures in `fixtures/scenarios`. |
| Post-match replay review | **Implemented** (file import) | Labelled `replay`. Never merged into live state. |

### 2e. Game rules not present in API data (each carries an evidence status in code)

| Rule | Status | Source |
|---|---|---|
| Universal item slots, 12 total | Community-reported, unverified on build 6763 | Liquipedia "Patch 2025-05-08" summary. User-configurable. |
| Component upgrade cost = cost − owned component cost; component consumed | Community-documented, unverified on build 6763 | Long-standing shop behaviour. Labelled in UI. |
| Sell-back value 50% (full refund before leaving shop) | Community-reported (pre-2025-rework wiki revision), unverified | Used only in replacement advice, labelled unverified. |
| Tier 5 ("Legendary", cost 9999 placeholder) items | Excluded from shop advice | Reported as Sandbox-only. Acquisition rule not verified. |
| Corrupted items (The Broker, Street Brawl) | Unsupported | Broker is reported as limited-time; per-match random penalties. |
| Game modes | Standard: supported. Ranked: assumed same in-match rules as Standard (unverified). Street Brawl: **unsupported** (item draft, rounds and corrupted items not modelled). | `generic-data.street_brawl`, Steam "Matchmaking Update" 2026-07-30. |

## 3. Evidence URLs

- https://api.deadlock-api.com/openapi.json (schema read 2026-10-08)
- https://api.deadlock-api.com/v1/assets/steam-info
- https://api.deadlock-api.com/v1/assets/heroes
- https://api.deadlock-api.com/v1/assets/items
- https://api.deadlock-api.com/v1/assets/generic-data
- https://api.deadlock-api.com/v2/patches
- https://api.deadlock-api.com/v1/builds?hero_id=6
- https://api.deadlock-api.com/v1/analytics/ability-order-stats
- https://github.com/deadlock-api/deadlock-api/blob/master/live-events/README.md
- https://github.com/Predi-i/Deadlock-UI-Mods (`HTML_TRANSPORT_HANDOFF.md`,
  `HUD-Dumper/README.md`, `HUD-Dumper/CLASS_ENUMERATION.md`; Apache-2.0; commit b143f5a,
  2026-10-04). Note: this repo also contains `Minimap-Cheat` and an automation proof of
  concept (`Rem-Bug-Abuse`). CounterCoach takes nothing from those.
- https://github.com/mntbliss/Deadlock-QoL-HUD-Mod (commit 2459d4c, 2026-10-03; README
  states install patches `gameinfo.gi`; no licence file found, so no code was reused).
- https://liquipedia.net/deadlock/Patch/Playtest/2025-05-08 (slot rework summary, via search
  result; direct fetch was rate-limited).
- https://store.steampowered.com/news/app/1422450/view/694273194214819790 ("City Never Sleeps").
- https://developer.valvesoftware.com/wiki/Counter-Strike:_Global_Offensive_Game_State_Integration
  (CS:GO GSI exists; **no Deadlock GSI documentation was found**, so none is assumed).

## 4. Things CounterCoach never does

No process injection, memory reading, packet interception, hidden enemy information,
anti-cheat interaction, automated purchases or ability allocation, game-file edits or
launch-flag changes. The optional read-on-Tab listener only observes the Tab key; it never
sends, blocks or replays input. It never treats spectator/broadcast data as live player observations.
It makes no claim that it is ban-proof or Valve-approved.

## 5. Final capabilities report (delivered build 0.1.0)

**Implemented** = code exists and is exercised by tests or screenshots. **Verified** = shown
working in this environment, with how. **Limited** = works with stated restrictions.
**Unavailable** = not delivered, with the reason.

| Feature | Status | Evidence / limits |
|---|---|---|
| Delivery route | External Windows companion (Electron 44.4.5 + React 19.3) | Not a native mod. No game files touched. |
| Native Panorama HUD addon | **Unavailable** | No permitted interface (§1). Not built; nothing was compiled or claimed. |
| Data ingest (heroes, items, costs, components, abilities, level schedule, tier costs) | Implemented, verified | Build 6763 snapshot validated; 116 tests; quality gate rejected a real degraded rebuild. |
| Versioning, content hash, last-known-good, diff, compatibility | Implemented, verified | `data.test.ts`, `ingest.test.ts`. |
| Patch invalidation (review stamps) | Implemented, verified | Changed items halved and flagged; curated-stale profiles; removed references fall back to auto. |
| Manual adapter (all fields, quick search, recent picks, keyboard) | Implemented, verified | Screenshots 01–07. |
| Scenario playback (10 fixtures) | Implemented, verified | Scenarios tab; tests. |
| Replay/post-match (decision logs, review ≤3 lessons) | Implemented, verified (unit) | Logs are opt-in and local; replay stores never mix with live state. |
| Live game-state adapter | **Unavailable** | No documented Deadlock interface. Spectator feeds are excluded from live use. |
| Screen reader (Tab view capture → your items and enemy items) | **Implemented, experimental** | Hotkey capture or (opt-in) every time Tab is held, one-time calibration that learns the item slots, players as columns, assign players once per match, auto-apply when everything is confident. Reads the real items on one real sandbox capture correctly after fixes; synthetic: 99.6% correct, 0 confident errors. **Not yet tested in a full match**, so enemy items under enemy cards are unconfirmed. |
| Recommender: BUY NOW / SAVE FOR / ALTERNATIVE with explanations | Implemented, verified | `scenarios.test.ts` (12 behaviours), screenshots. |
| Counter rules (17, threat → mechanic → responses → conditions → exceptions) | Implemented | Weights are heuristic; item mechanics come from data. See COUNTER_RULES.md. |
| Stability, pin / reject / defer | Implemented, verified | Tests §11 and features tests. |
| Ability planner (unlocks vs points, real costs, breakpoints, hold logic, legality) | Implemented, verified | `planner.test.ts`; tier costs 1/2/5 derived from data. |
| Hero coverage | Limited | All 40 playable heroes have a profile: **6 curated** (Abrams with 2 archetypes, Haze, Seven, Infernus, Dynamo, Lady Geist; AI-authored, awaiting expert review) and **34 basic data-derived**. See COVERAGE.md. |
| Threat panel, power spike, build repair, team utility, replacement, active hints, lane plan, what-if | Implemented, verified (unit + screenshots) | Power spike gives souls/points remaining, never an ETA. |
| Game modes | Limited | Standard supported. Ranked assumed to share the in-match rules (unverified). Street Brawl unsupported (no advice). |
| Unverified game rules (12 slots, component pricing, 50% resale) | Limited | Labelled in the UI and overridable in Settings. |
| Overlay (collapsed/expanded, scale, opacity, anchors/custom, multi-monitor, click-through, edit mode, no focus steal, hotkeys) | Implemented; verified on Linux/Xvfb | Placement unit-tested for DPI, ultrawide and negative origins. **Not tested over Deadlock or on Windows.** Exclusive fullscreen is not supported. |
| Separate-window mode | Implemented, verified (Linux) | The main window works without the overlay. |
| Settings persistence | Implemented, verified | Relaunch test in the screenshot run. |
| Background data refresh (non-blocking, validated, rollback) | Implemented | The refresh path uses the same tested ingest core; the in-app download was not exercised end to end in the UI. |
| Windows packages (NSIS installer + portable zip) | Built (unsigned); verified on a Windows CI runner | Launch smoke test and silent install/uninstall passed on `windows-latest` (run 37773343166); install details also checked under Wine. Not yet run on a gaming PC with Deadlock. |
| Performance (<100 ms local update) | Verified on Linux | p95 21 ms (Node); 8–12 ms in the Electron renderer. Windows not measured. |
| Statistical ranking from match data | Not used as a ranking model | Only labelled weak priors (pick rate, most-played ability order with sample size). |

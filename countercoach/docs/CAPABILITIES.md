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
native mod. It reads only explicit manual input, user-loaded scenario files and
post-match replay files.

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
| User-triggered capture of the game window | Untested / not shipped | No Windows or game in this environment and no real screenshots to validate against. The ambiguity-resolution flow it would use (candidates plus mandatory user confirmation) is implemented and tested in the engine. |

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
launch-flag changes. It never treats spectator/broadcast data as live player observations.
It makes no claim that it is ban-proof or Valve-approved.

# Test results

Run date: 2026-10-08. Environment: Linux container (Ubuntu 24.04, Node 22.22.0, no GPU, no
Windows, no Steam or Deadlock). Snapshot: client build 6763 (`snapshot-6763-cf9635cfbf62.json`).

**Nothing here was tested against a running Deadlock client.** Scenario fixtures are
simulations, not live-match demonstrations.

## Summary

| Check | Result |
|---|---|
| `pnpm typecheck` (engine, ingest, tests/scripts, desktop) | pass |
| `pnpm test` (Vitest, 8 files) | **89 / 89 pass** |
| Electron app launch on Linux (Xvfb), dev build | pass: main window visible; overlay always-on-top, non-focusable, positioned in the work area |
| Electron app launch, **packaged** (asar, Linux `dir` build of the same app) | pass |
| Settings persistence across relaunch | pass (expanded overlay and position restored) |
| Renderer console errors during the screenshot run | none |
| Windows NSIS installer + portable zip build (electron-builder 26.15.3 on Linux with Wine 9.0) | built, **unsigned** (no Authenticode data in the PE headers) |
| Silent install → shortcuts → uninstall registry entry → silent uninstall, **under Wine** | pass (details below) |
| App launch on Windows | **untested**. Under Wine the app process started and created its profile folder, but Wine could not render Electron windows (`DCompositionCreateDevice3 failed: Not implemented`), so no smoke report was written. |
| Overlay over Deadlock (borderless or fullscreen) | **untested** (no game available) |
| Live / screen-capture adapters | not shipped (not verifiable) |

To test on real Windows, run `.github/workflows/countercoach-windows.yml` (manual trigger). It
builds on `windows-latest`, runs the tests, smoke-launches the app (writes `smoke-report.json`),
silently installs and uninstalls, and uploads the installer and zip.

## Required behaviours (brief §9) → tests

| Behaviour | Test |
|---|---|
| Same hero, different defensive advice vs observed weapon vs ability builds | `scenarios.test.ts` §1 |
| Healing reduction prioritised when relevant and applicable; no redundant copy; ally coverage partial | §2 (4 tests) |
| Urgent threat may delay core; uncertain minor threat does not | §3 |
| Unaffordable item becomes SAVE FOR, never BUY NOW (checked across all fixtures) | §4 |
| Owned components and legal upgrades use the correct remaining cost (every multi-component item) | §5 |
| Full inventory: valid replacement, or a no-purchase explanation | §6 |
| Ability advice respects points, prerequisites, previous choices and useful saving | `planner.test.ts` (11 tests) |
| Weak or stale observations lower certainty; missing ≠ zero | §8 + `state.test.ts` |
| Patch change invalidates affected interactions; API outage uses the cache | `data.test.ts` §9, `ingest.test.ts` (outage against a local failing server) |
| OCR/manual corrections resolve ambiguous icons without auto-choosing | `state.test.ts` §10 |
| Stability suppresses trivial churn but allows an urgent change | §11 |
| A new match cannot inherit the previous match | §12 + `state.test.ts` |
| Degraded data cannot replace last-known-good | `ingest.test.ts` quality gate |

## Performance

| Measurement | Value | Where |
|---|---|---|
| `evaluate()` (full coach output) p50 / p95, 30 runs after warm-up | 15.4 ms / 21.2 ms | Node 22, Vitest, Linux container |
| In-app compute time shown after each input (5 samples) | 7.7–11.9 ms | Electron 44 renderer, Linux, Xvfb |
| Working set, all app processes (main, overlay, GPU (software), utility) | ≈ 895 MB total; main renderer ≈ 270 MB, overlay ≈ 134 MB | Electron `app.getAppMetrics()`, Linux |
| CPU while idle (5 s freshness tick) | ≈ 0% per process | same |

The sub-100 ms local update target is met on this machine. Windows numbers were not measured.
No continuous OCR runs; capture cost is zero because screen capture is not shipped.

## Wine install/uninstall run (final build)

```
install_exit=0
exe_installed=yes
uninstaller_present=yes
shortcuts=2
registry_entries=1
launch_profile_dir=created
smoke_report=none
uninstall_exit=0
exe_after_uninstall=removed
shortcuts_after=0
registry_after=0
userdata_kept=yes
```

`userdata_kept=yes` is by design (`deleteAppDataOnUninstall: false`). USAGE.md explains how to
remove it.

Package checksums (SHA-256):

```
35e64c9543fa23b0a95724f39319fc0df1024803d889eeeb4764ed4d9f2ad178  CounterCoach-Setup-0.1.0-x64.exe
a27d8a804f19e47c532d846dd38d14e84fd6cb713b86b7486828c1f88068590c  CounterCoach-0.1.0-win-x64.zip
```

The packages are build outputs and are not committed to git (both are over GitHub's 100 MB
file limit). Rebuild with `pnpm dist:win`.

## Screenshots (real running Electron app, Linux/Xvfb)

| File | Shows |
|---|---|
| `screenshots/01-first-run-1366x768.png` | First run: minimal required input (pick hero) |
| `screenshots/02-abrams-midgame-1920x1080.png` | Abrams mid-game demo: BUY/SAVE/ALT, hold-points ability advice, threats, team utility |
| `screenshots/03-overlay-collapsed.png` | Overlay, collapsed (real overlay window) |
| `screenshots/04-overlay-expanded.png` | Overlay, expanded, sized to content |
| `screenshots/05-settings-1920x1080.png` | Settings: overlay, hotkeys, unverified rules, data status, available inputs |
| `screenshots/06-haze-vs-healing-1920x1080.png` | Haze vs reported healing: Toxic Bullets, with Inhibitor deprioritised once bought |
| `screenshots/07-infernus-lane-2560x1080.png` | Ultrawide; laning; lane plan; unlock before points |
| `screenshots/08-what-if-1600x900.png` | What-if: one enemy item changes threat strength; advice stable |

Under Xvfb (no compositor), the transparent overlay background renders black.

## All tests

```
 ✓ packages/engine/test/features.test.ts > coach features > threat panel shows at most three threats and prefers observed evidence
 ✓ packages/engine/test/features.test.ts > coach features > lane threats come from lane opponents during laning
 ✓ packages/engine/test/data.test.ts > versioned snapshot > validates and carries provenance
 ✓ packages/engine/test/data.test.ts > versioned snapshot > content hash matches an independent SHA-256 and the stored value
 ✓ packages/engine/test/data.test.ts > versioned snapshot > counts come from data, not a remembered roster size
 ✓ packages/engine/test/data.test.ts > versioned snapshot > rejects malformed data instead of replacing the active snapshot
 ✓ packages/engine/test/features.test.ts > coach features > power spike shows souls/points remaining and no invented ETA
 ✓ packages/engine/test/features.test.ts > coach features > build repair keeps off-route purchases and drops overlapping route items but never fundamentals
 ✓ packages/engine/test/features.test.ts > coach features > team utility explains coverage and who should buy
 ✓ packages/engine/test/features.test.ts > coach features > active hints quote item behaviour and never claim enemy cooldowns
 ✓ packages/engine/test/features.test.ts > coach features > what-if: adding an enemy healing item changes the advice and explains why
 ✓ packages/engine/test/features.test.ts > coach features > what-if: changing souls or archetype is reflected
 ✓ packages/engine/test/features.test.ts > coach features > post-match review: at most three lessons, decision-time only, with a caveat
 ✓ packages/engine/test/data.test.ts > 9. patch changes invalidate affected interactions > diff detects changed costs/effects and removed items
 ✓ packages/engine/test/data.test.ts > 9. patch changes invalidate affected interactions > a changed item is flagged and its counter value is reduced until re-reviewed
 ✓ packages/engine/test/data.test.ts > 9. patch changes invalidate affected interactions > a curated profile depending on changed data is marked stale and lowers confidence
 ✓ packages/engine/test/data.test.ts > 9. patch changes invalidate affected interactions > a curated profile referencing a removed item falls back to the data-derived profile
 ✓ packages/engine/test/data.test.ts > 9. patch changes invalidate affected interactions > an outdated client build lowers confidence to low and explains why
 ✓ packages/engine/test/data.test.ts > 9. patch changes invalidate affected interactions > offline use is labelled unknown, not current
 ✓ packages/ingest/test/ingest.test.ts > ingest > falls back to the last cached response when the API fails (outage)
 ✓ packages/engine/test/features.test.ts > coach features > unsupported modes produce no purchase advice
 ✓ packages/engine/test/features.test.ts > coach features > pinned, rejected and deferred items are respected with reasons
 ✓ packages/engine/test/features.test.ts > performance (local scoring cost) > full evaluate() stays well under 100 ms after warm-up
 ✓ packages/ingest/test/ingest.test.ts > ingest > derives tier costs from build currency deltas and cross-checks the level schedule
 ✓ packages/ingest/test/ingest.test.ts > ingest > skips malformed items with warnings instead of inventing values
 ✓ packages/ingest/test/ingest.test.ts > quality gate > accepts an equivalent snapshot
 ✓ packages/ingest/test/ingest.test.ts > quality gate > rejects a valid but degraded snapshot (statistics missing) and keeps last-known-good
 ✓ packages/ingest/test/ingest.test.ts > quality gate > rejects an older client build
 ✓ packages/engine/test/scenarios.test.ts > 1. same hero, different defensive advice for observed weapon vs spirit builds > answers weapon damage with weapon-defence mechanics
 ✓ packages/engine/test/scenarios.test.ts > 1. same hero, different defensive advice for observed weapon vs spirit builds > answers spirit damage with spirit-defence mechanics
 ✓ packages/engine/test/scenarios.test.ts > 1. same hero, different defensive advice for observed weapon vs spirit builds > produces different purchases for the two builds
 ✓ packages/engine/test/scenarios.test.ts > 2. healing reduction: prioritised when relevant and applicable, not redundant > weapon Haze gets bullet-applied anti-heal against observed + reported healing
 ✓ packages/engine/test/scenarios.test.ts > 2. healing reduction: prioritised when relevant and applicable, not redundant > spirit Seven gets spirit-applied anti-heal for the same threat
 ✓ packages/engine/test/scenarios.test.ts > 2. healing reduction: prioritised when relevant and applicable, not redundant > does not recommend a second anti-heal when one is already owned
 ✓ packages/engine/test/scenarios.test.ts > 2. healing reduction: prioritised when relevant and applicable, not redundant > an ally's relevant anti-heal reduces (but does not zero) its priority in teamfights
 ✓ packages/engine/test/profiles.test.ts > hero profiles > every curated profile resolves against current data with no errors
 ✓ packages/engine/test/profiles.test.ts > hero profiles > covers every currently playable hero, distinguishing curated from data-derived
 ✓ packages/engine/test/profiles.test.ts > hero profiles > curated breakpoints quote text that exists in the current tier data
 ✓ packages/engine/test/profiles.test.ts > hero profiles > data-derived profiles never invent items: every fundamental exists and is purchasable
 ✓ packages/engine/test/scenarios.test.ts > 3. urgency: urgent threat may delay core, uncertain minor threat does not > roster-only threats keep the core route
 ✓ packages/engine/test/scenarios.test.ts > 3. urgency: urgent threat may delay core, uncertain minor threat does not > an urgent reported spirit burst promotes a spirit answer over core
 ✓ packages/engine/test/scenarios.test.ts > 4. unaffordable target becomes SAVE FOR > returns SAVE with souls remaining and no BUY NOW
 ✓ packages/engine/test/scenarios.test.ts > 4. unaffordable target becomes SAVE FOR > never offers an unaffordable BUY NOW across all fixtures
 ✓ packages/engine/test/scenarios.test.ts > 5. owned components reduce the remaining cost; upgrades are legal > every multi-component item deducts each owned component's cost and reuses its slot
 ✓ packages/engine/test/scenarios.test.ts > 5. owned components reduce the remaining cost; upgrades are legal > recommendation shows the reduced price when you own a component
 ✓ packages/engine/test/scenarios.test.ts > 5. owned components reduce the remaining cost; upgrades are legal > never recommends a component you already upgraded
 ✓ packages/engine/test/scenarios.test.ts > 5. owned components reduce the remaining cost; upgrades are legal > flags an impossible inventory (component together with its upgrade)
 ✓ packages/engine/test/scenarios.test.ts > 6. full inventory > proposes a valid replacement (sell the weakest item) late game
 ✓ packages/engine/test/scenarios.test.ts > 6. full inventory > explains no-purchase when every slot is locked core and nothing upgrades in place
 ✓ packages/engine/test/scenarios.test.ts > 8. weak or stale observations lower certainty instead of inventing state > souls entered long ago lower confidence and are labelled
 ✓ packages/engine/test/scenarios.test.ts > 8. weak or stale observations lower certainty instead of inventing state > missing souls is not treated as zero
 ✓ packages/engine/test/scenarios.test.ts > 8. weak or stale observations lower certainty instead of inventing state > unobserved enemy items do not count as evidence
 ✓ packages/engine/test/scenarios.test.ts > 8. weak or stale observations lower certainty instead of inventing state > stale enemy-item observations contribute less than fresh ones
 ✓ apps/desktop/test/desktop.test.ts > overlay placement > anchors inside the work area of the chosen display, scaled
 ✓ apps/desktop/test/desktop.test.ts > overlay placement > supports monitors with negative origins and falls back to primary when a display is gone
 ✓ apps/desktop/test/desktop.test.ts > overlay placement > keeps custom positions resolution-independent (ultrawide)
 ✓ apps/desktop/test/desktop.test.ts > overlay placement > clamps an off-screen custom offset and sizes to content when known
 ✓ apps/desktop/test/desktop.test.ts > settings > falls back field by field for invalid values
 ✓ apps/desktop/test/desktop.test.ts > settings > patches nested sections without dropping siblings and ignores unknown keys
 ✓ apps/desktop/test/desktop.test.ts > settings > persists atomically and reloads
 ✓ apps/desktop/test/desktop.test.ts > decision log store > appends validated entries and deletes everything on request
 ✓ apps/desktop/test/desktop.test.ts > overlay model > is schema-valid for every fixture
 ✓ packages/engine/test/scenarios.test.ts > 11. recommendation stability > keeps the previous BUY NOW when the new top is within the stability margin
 ✓ packages/engine/test/scenarios.test.ts > 11. recommendation stability > allows an urgent, well-supported change
 ✓ packages/engine/test/scenarios.test.ts > 12. a new match cannot inherit the previous match > clears roster, items and threats on a new match id
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > uses tier costs derived from data (unlock is a separate currency)
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > spends an available unlock before points, in plan order
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > recommends the next legal tier with exact cost and data text
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > never proposes a tier before its prerequisite or on a locked ability
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > holds points for a key breakpoint instead of spending them elsewhere
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > starts from the real state even if it diverges from the plan (no refunds assumed)
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > flags inconsistent state instead of guessing
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > asks for unspent points instead of assuming one point per level
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > reports nothing to do when everything is maxed
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > level schedule from data grants 4 unlocks and 32 points at max
 ✓ packages/engine/test/planner.test.ts > 7. ability planner > every playable hero gets a legal plan that can reach max with the granted points
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > drops out-of-order observations by game time
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > preserves a user correction against a lower-authority or not-clearly-newer observation
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > allows a clearly newer, high-confidence verified observation to supersede a correction
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > a deliberate new user correction replaces the old one
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > rejects replay/spectator data in a live store and live data in a replay store
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > reconnecting to the same match keeps state; a hero change clears hero-scoped fields
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > pausing stops the match clock extrapolation
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > marks values aging/stale with field-specific policies
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > changing the enemy roster removes item observations for heroes no longer present
 ✓ packages/engine/test/state.test.ts > observation conflict and freshness rules > ignores observations after the match ended
 ✓ packages/engine/test/state.test.ts > 10. ambiguous (OCR-style) observations require explicit user resolution > does not apply any candidate until the user chooses
 ✓ packages/engine/test/state.test.ts > 10. ambiguous (OCR-style) observations require explicit user resolution > applies exactly the chosen candidate (not the first) as a user correction
 ✓ packages/engine/test/state.test.ts > 10. ambiguous (OCR-style) observations require explicit user resolution > rejecting all candidates leaves state unchanged
```

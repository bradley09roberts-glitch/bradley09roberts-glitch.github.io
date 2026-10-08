# Architecture

```
countercoach/
├─ packages/engine        UI-independent TypeScript domain engine (only dependency: zod)
│   ├─ data/              snapshot schema + validation, SHA-256 content hash, diffing, compatibility
│   ├─ rules/             game rules with evidence status (slots, component pricing, resale, tiers, modes)
│   ├─ knowledge/         data-derived item/ability mechanics, counter rules, hero profiles, review stamps
│   ├─ state/             observation model, match-state reducer, adapters, decision-log schema
│   ├─ engine/            threat assessment, candidate generation/legality/scoring, selection, stability, coach facade
│   ├─ abilities/         stateful ability planner
│   └─ features/          threat panel, power spike, build repair, team utility, replacement,
│                         active hints, lane plan, what-if, post-match review
├─ packages/ingest        Node CLI + library: fetch → validate → normalise → quality-gate → snapshot
├─ apps/desktop           Electron shell + React renderer (one frontend; also runs in a browser for previews)
│   ├─ src/main           windows, overlay placement, IPC (validated), settings/log/data stores, hotkeys
│   ├─ src/preload        minimal contextBridge API (2 KB)
│   ├─ src/renderer       React UI; runs the engine locally
│   └─ src/shared         settings schema, IPC channels + schemas
├─ data/snapshots         versioned, validated game-data snapshots + manifest (last-known-good)
├─ data/knowledge         review stamps (fingerprints of reviewed items/abilities)
├─ fixtures/scenarios     timestamped scenario fixtures (tests, demos, playback)
└─ docs/                  capability report, usage, scoring, state, data, coverage, counter rules, tests
```

## Data flow

```
deadlock-api.com ──► ingest (zod raw validation ► normalise ► derive tier costs ► validate snapshot
                                                     ► quality gate vs last-known-good) ──► snapshot.json
                                                                                              │
manual input / scenario playback ──► MatchEvents ──► match-state reducer ──► MatchState       │
                                                                                 │            ▼
                                                     engine.evaluate(deps(snapshot, rules, profiles, review stamps), state, prefs)
                                                                                 │
                                       ┌──────────────── CoachOutput ────────────┴───────────────┐
                                       ▼                                                         ▼
                              main window (React)                                overlay model (≤ 2 KB, zod-validated)
                                                                                       ▼ IPC relay via main
                                                                                 overlay window (React)
```

- The engine is **deterministic and local**. There are no LLM or network calls per decision.
  A full evaluation takes about 10–20 ms (see TEST_RESULTS.md).
- Updates are **event-driven**: every observation re-evaluates. A 5-second tick only refreshes
  freshness labels and the extrapolated clock.
- The overlay runs no engine. It renders a compact, schema-validated model relayed by the main
  process.

## Security

- Renderer: `contextIsolation`, `sandbox`, no `nodeIntegration`, and a strict CSP (scripts
  from self; images from self or `https://assets-bucket.deadlock-api.com`; no remote
  connections).
- IPC: an explicit channel list. Every handler checks the sender is one of our windows and
  validates input with zod (settings patches, overlay model, log entries, log ids, resize
  heights).
- Navigation is blocked. New windows are denied, except whitelisted documentation links, which
  open in the system browser. All permission requests are denied. The app is single-instance.
- Remote data is parsed as JSON and validated. Text from the data is converted to plain text
  and always rendered as text, never as HTML.
- Settings and logs are written atomically (temp file, then rename). Logs are opt-in and can
  be deleted.
- There is no local server, so there is nothing listening on a port.

## Extending to another frontend

`packages/engine` has no DOM or Node dependencies (`types: []` in its tsconfig). A future native
frontend, if a permitted interface ever exists, could feed `MatchEvent`s into the same reducer
and call `evaluate()`. A future verified live or screen adapter only needs to emit
`observe` / `observe.ambiguous` events with `source: "live" | "screen"`. Freshness, authority
and correction rules then apply automatically.

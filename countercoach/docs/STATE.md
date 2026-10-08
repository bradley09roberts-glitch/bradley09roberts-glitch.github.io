# Match state and observations

All inputs become `MatchEvent`s, which are applied by a pure reducer
(`packages/engine/src/state/matchStore.ts`). Every adapter goes through the same rules.

## Observation fields

Every observation carries `value`, `source`, `observedAt` (wall clock), `gameTime` (match clock,
when known), `confidence`, `userCorrection` and a sequence number.

| Source | Meaning | Shipped |
|---|---|---|
| `manual` | your input | yes |
| `scenario` | fixture playback | yes |
| `replay` | post-match / spectator data | yes, in **replay-only stores**; a live store rejects it |
| `screen` | user-triggered local screen reader (scoreboard capture) | yes, **experimental** (verified on synthetic scoreboards only). Reads applied automatically are `medium` confidence; reads you reviewed are `high`. A scoreboard read marks enemy inventories *complete* (setting); a partial screen does not |
| `live` | verified live interface | no (none exists; see CAPABILITIES.md) |

Missing is not zero:

- If no souls are entered, there is no BUY NOW, the advice is SAVE, and you are asked for
  souls.
- An enemy item list is `{ items, complete }`. Unseen items are unknown, not absent, and add
  no evidence.

## Freshness (field-specific)

| Field | Aging after | Stale after |
|---|---|---|
| souls (manual) | 60 s | 180 s |
| souls (live/screen) | 10 s | 30 s |
| unspent points / unlocks | 120 s | 300 s |
| own items, abilities | 300 s | 900 s |
| enemy/ally items | 240 s | 600 s (stale counts ×0.6) |
| death recap | 120 s | 360 s (4-minute half-life in threat strength) |
| threat reports | — | 5-minute half-life in threat strength |
| roster, hero, phase, standing | never within a match | — |

Ages use match time when both observations have it, otherwise wall-clock time. The match clock
is extrapolated from your last clock entry, stops while paused, and is never earlier than the
latest game time any observation reported.

## Conflict rules

1. **Out of order:** an observation of an earlier game moment never replaces a later one
   (dropped and logged).
2. **User corrections are preserved** until you correct the field again, or until an
   observation that is *clearly newer* (≥ 10 s of game time) **and** *more authoritative*
   (live or screen, high confidence) arrives.
3. **Same moment:** the higher authority wins (`live > screen > manual > scenario`).
4. **Ambiguous observations** (`observe.ambiguous`, for example an icon that could be two items)
   are held as *pending* and never applied until you pick a candidate. That pick becomes a
   correction. *Reject* applies nothing.

## Match lifecycle

- `match.start` with a **new id** resets everything (roster, items, threats, pending). A new
  match never inherits the previous one.
- `match.start` with the **same id** (reconnect) keeps state.
- Changing **your hero** clears hero-scoped fields (items, abilities, points, souls, death
  recap).
- Changing the **enemy roster** drops item observations for heroes who left it.
- After `match.end`, live observations are ignored until a new match starts.
- `match.pause` / `match.resume` freeze clock extrapolation.

The renderer saves the current match locally, so restarting the app behaves like a reconnect.

## Replay and post-match

Decision logs (opt-in) record, for each meaningful change in advice: time, souls, owned items,
enemies, the top three threats with their evidence, the advice, and the information state.
`replayStoreFromLog()` loads a log into a `replay` store, which can only be labelled *Replay*.
`postMatchReview()` returns at most three lessons from decision-time information, with a
caveat that they say nothing about what would have won.

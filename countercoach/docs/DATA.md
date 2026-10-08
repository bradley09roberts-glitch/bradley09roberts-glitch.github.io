# Game data

## Source

[deadlock-api.com](https://api.deadlock-api.com/docs) is a community project, **not** an official
Valve API. Endpoints used (schema read 2026-10-08 from `/openapi.json`):

| Endpoint | Used for |
|---|---|
| `/v1/assets/steam-info` | client/server build, version date |
| `/v1/assets/heroes` | roster, playability flags, abilities (signature 1–4), level schedule (souls → unlock/point), popular items per phase |
| `/v1/assets/items` | shop items: tier, cost, slot, activation, components, properties (modifier types, conditional flags), descriptions, shop filters, corrupted-variant marker; ability tiers and tier text |
| `/v1/assets/generic-data` | price per tier, modes present (Street Brawl) |
| `/v2/patches` | latest patch-note title, date and link |
| `/v1/analytics/ability-order-stats` | most-played ability orders per hero in the last 14 days (weak prior, sample size kept) |
| `/v1/builds` | in-game build ability orders, used to **derive tier point costs** |

The API's rate limit (`ratelimit-limit: 200` per 60 s) is respected with a client-side limiter,
an on-disk cache, bounded retries (≤ 4) and exponential backoff.

## Snapshot

`data/snapshots/snapshot-<build>-<hash12>.json`, validated by `snapshotSchema` (zod):

- `meta`: schema version, API base, endpoints, retrieval time, client/server build, version
  date, latest patch, **SHA-256 content hash** (canonical JSON of heroes, items and ability
  costs), and counts.
- `heroes`: stable id and class name, name, playable flag, abilities with tier upgrades and
  tier text, level schedule, popular items, and ability-order statistics.
- `items`: stable id and class name, name, slot, tier, cost, activation, components, numeric
  properties, plain-text description and filters.
- `abilityCosts`: tier costs **1/2/5** derived from 291 builds (100% agreement). This is
  cross-checked against the level schedule: 32 points granted = 4 abilities × (1+2+5), and
  4 unlock tokens.

Counts come from data, never from memory: build 6763 has **65 hero records, 40 playable**
(`player_selectable && !disabled`), and **173 shop items** (156 at tier ≤ 4).

Retrieving data today does not prove it matches today's patch. Compatibility is evaluated at
runtime against the live client build (`/v1/assets/steam-info`): *current*, *outdated* (advice
capped at low confidence), or *unknown* (offline).

## Validation and last-known-good

`pnpm ingest` (and the in-app *Download & validate*) runs:

1. raw validation of every response (zod; unknown fields are allowed, missing required fields
   fail);
2. normalisation (markup becomes plain text; invalid items are skipped **with warnings**;
   nothing is invented);
3. snapshot schema validation, including duplicate ids, unknown components, and four abilities
   per playable hero;
4. a **quality gate** against the active snapshot: rejected if ability-order coverage drops
   more than 10%, the shop item count drops more than 15%, the playable roster drops by more
   than 3, the build goes backwards, or the cost cross-check fails;
5. only then is the new snapshot written and `manifest.json` updated (active and previous). A
   diff (`diff-<from>-to-<to>.json`) lists changed costs, properties, components and tiers,
   and removed or added items, abilities and heroes.

The quality gate caught a real failure during development. An offline rebuild with expired
statistics cache entries produced a valid but degraded snapshot (0/40 heroes with ability-order
statistics). It was rejected and the last-known-good snapshot was kept.

## Patch changes and invalidation

`data/knowledge/review-stamps.json` stores a fingerprint of every shop item and playable-hero
ability as it was when the knowledge base was last checked (`npx tsx scripts/stamp-review.ts`).
At runtime:

- an item whose fingerprint differs has its counter value **halved and flagged** ("changed
  since rules were reviewed") until it is re-stamped;
- a curated hero profile that references changed data becomes **curated-stale**, which lowers
  confidence and lists the changed references;
- a curated profile that references a **removed** item fails validation, and the data-derived
  profile is used instead;
- mechanic tags are always re-derived from current properties, so an effect removed in a patch
  stops counting immediately.

## Assets and licensing

Hero and item icons are loaded at runtime from `assets-bucket.deadlock-api.com`. They are
**not bundled**, because Valve's game art isn't ours to redistribute. If an image is
unavailable, initials are shown instead. Community HUD repositories were read for evidence
only; no code or assets were copied. One of them (Predi-i/Deadlock-UI-Mods) is Apache-2.0; the
other has no licence file.

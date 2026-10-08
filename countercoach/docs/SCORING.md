# Recommendation engine and scoring

The engine separates **candidate generation → legality → scoring → selection → presentation**.
Scores are a *ranking index*. They are never probabilities and are never shown as win rates.
All weights are in `packages/engine/src/engine/preferences.ts` (`DEFAULT_WEIGHTS`) and can be
overridden in code.

## 1. Candidates and legality

Each shop item is checked in order:

1. Mode supported (Street Brawl is rejected) and tier ≤ 4. Tier 5 has a 9999 placeholder cost
   and is reported to be Sandbox-only.
2. Hero restriction, if the data defines one.
3. Not owned, and not a component of an item you already own (`coveredByOwnedUpgrade`).
4. Not rejected by you, and not deferred until a later match time.
5. **Economics**: `remainingCost = cost − Σ(owned listed components)`. If a component is
   consumed, the upgrade reuses its slot. This is a community rule and is labelled as such.
6. **Full inventory**: an item that needs a new slot must replace the weakest unlocked item.
   Its score becomes `score − retainScore(weakest)`, and affordability includes the resale
   value (an unverified rule, labelled). If every item is locked, the item is not offered.
7. **Input consistency**: owning both an item and its component is flagged ("check your
   items"), never silently trusted.

## 2. Score

```
score = W.fit·fit + W.counter·counter + W.path·path + W.phase·phase + burden + slot + distance
```

| Part | How it is computed | Kind |
|---|---|---|
| **fit** | fundamental 1.0–1.4 (earlier = higher) · locked core 1.5 · current route 0.75–1.05 · other route 0.45 · avoid −1.2; plus 0.3 × stat alignment with the archetype's damage mix; plus 0.25 × phase pick rate (weak prior) | heuristic + statistical |
| **counter** | Σ over threats: `strength × evidenceFactor × selfRelevance × max(rule response)`, where a response = `weight × magnitude × reliability × exceptions`; × `urgentMultiplier` (1.7) for urgent threats; capped at 2.2 | data mechanics + heuristic weights |
| **path** | +0.4 if it builds toward a planned item; +0.2 if it upgrades an owned component | heuristic |
| **phase** | tier value by phase, e.g. lane `{T1 .3, T2 .25, T3 0, T4 −.3}`, late `{T1 −.4 … T4 .3}`; phase comes from you, else from the clock (<9:00 lane, <25:00 mid) | heuristic |
| **burden** | −0.35 per active item beyond your difficulty limit (simple 2 / standard 4 / complex 6) | preference |
| **slot** | −0.25 when it would need a sold slot; −0.2 for T1–T2 items when ≤ 2 slots are free after laning | heuristic |
| **distance** | −0.12 per 1,600 souls still missing (capped at 4 steps), so nearer targets are favoured | heuristic |

Default weights: `fit 1.0, counter 1.1, path 0.6, phase 0.5`.

### Counter terms

- **Mechanic presence** comes from current data (`knowledge/mechanics.ts`), never from item
  names. **Magnitude** normalises the property value: resist ÷ 25%, health ÷ 400, barrier
  ÷ 400; conditional properties ×0.6; clamped 0.15–1.6. Many items carry small innate stats,
  so they score low.
- **Reliability** (offensive responses only) depends on how the effect is applied
  (`itemApplication`): bullets `0.2 + 0.8·weapon share`, headshots `0.1 + 0.6·weapon`,
  spirit damage `0.2 + 0.8·spirit`, targeted active 0.75, area active 0.7, aura by range
  (0.9 / 0.7 / 0.45), reactive 0.9, parry 0.5. A weapon hero is not told to rely on
  spirit-applied anti-heal, and vice versa.
- **Exceptions** are multiplicative:
  - already owning the same response: offensive ×0.15, defensive ×0.6 (no additive stacking
    is assumed);
  - ally coverage: `1 − 0.5 × ally reliability` (never below 0.5), mid/late game only, so
    one ally owning an item never zeroes the threat;
  - target seen with CC immunity or cleanse: CC responses ×0.6–0.7.
- **Evidence factor**: roster-only 0.6, observed items 0.9, reported or death recap 1.0.
- **Self relevance**: silence matters by spirit share; bullet- or spirit-resist stacking
  matters by weapon or spirit share.
- **Changed since review**: if the item changed since the knowledge base was stamped, its
  counter value is halved and flagged.

## 3. Threat strength

For each threat kind, contributions are combined as `1 − Π(1 − aᵢ)`, giving an index in [0, 1):

- **Kit** (enemy profile signature): `0.3 × damage share × relevance`; burst +0.15; sustain
  low/medium/high = 0.04/0.12/0.25; listed control threats 0.08–0.15.
- **Relevance**: in lane, lane opponents count 1.0 and other enemies 0.3 (0.6 each if lanes
  are unknown).
- **Observed items**: lifesteal or heal amp +0.22 each (capped at 0.5), spirit or weapon power
  +0.07, resist stacking +0.15, and so on. Stale observations count ×0.6.
- **Reports**: minor 0.3, major 0.6, with a 5-minute half-life. **Death recap**:
  `0.55 × share`, plus burst 0.3 if one type is ≥ 70%, with a 4-minute half-life.
- **Evidence label**: taken from contributions ≥ 0.08 only.
- **Urgent** requires `strength ≥ 0.55`, non-roster evidence, **and** direct evidence ≥ 0.25.

## 4. Selection

- `best` is the highest-scoring positive candidate, or a pinned one.
- **BUY NOW** is the affordable item with the highest `buyRank = score + 0.15 (answers an
  urgent threat) + 0.25 (component of an unaffordable best) + 1 (pinned)`.
  - If `best` is affordable, buy the top `buyRank` item.
  - Otherwise buy only if it is a component of `best`, or scores ≥ threshold × best (0.8;
    0.65 on the stabilise route, 0.9 on the ambitious route), or answers an urgent threat.
    If none of these holds, the advice is **SAVE**, with the delay explained.
- **SAVE FOR** is chosen by *re-scoring after the hypothetical BUY NOW*, so a second
  anti-heal or redundant defence drops out. "Souls short" is counted after the purchase. A
  pinned item stays the target.
- **ALTERNATIVE** is the best candidate whose main threat or role differs, with a stated
  condition (for example "Better if weapon damage becomes your main problem (currently only
  inferred from heroes)").
- **Routes**: stabilise when you're behind, normal when even or unknown, ambitious when ahead
  (or chosen manually). All routes share the archetype's fundamentals.

## 5. Stability

When the previous BUY NOW is still affordable and positive, it is kept unless the new top beats
it by more than `stabilityMargin` (12%). An **urgent, well-supported** new threat overrides this.
The UI shows "Kept X…" or "Changed to Y: urgent…".

## 6. Confidence

Confidence starts at *high* and drops one level for each of the following:

- a basic (data-derived) profile;
- souls not entered, or entered more than 1 minute ago (aging/stale);
- no enemy roster;
- profile references changed since review;
- the top choice being driven by roster-only threats.

An outdated client build caps confidence at *low*. All reasons are listed, and no percentages
are invented.

## 7. Validation

The behaviour checks are in `packages/engine/test/scenarios.test.ts` and the fixtures in
`fixtures/scenarios/`. Weights were calibrated against those annotated scenarios, not against
win rates.

Historical pick and win rates appear only as weak, labelled priors: pick rate in `fit`, and the
most-played ability order in the planner. Raw item win rate is never used as a causal signal.

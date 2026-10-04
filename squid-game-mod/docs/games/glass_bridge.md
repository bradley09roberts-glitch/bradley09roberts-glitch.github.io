# Game 5 - Glass Bridge - design spec

Package names: `core/bridge/`, `game/bridge/` (`GlassBridgeGame`, `BridgeNpcBehavior`, `BridgeNet` if needed),
`client/game/bridge/` (`BridgeClient`), fixture `build/placeholder/GlassBridgePlaceholder`. Game id `glass_bridge`
(`GameKind.GLASS_BRIDGE`, `EliminationCause.FELL`/`TIMEOUT`), arena `ArenaId.GLASS_BRIDGE` (markers: 36 `bridge.panel`
`row=R,lane=L`, `bridge.queue`, `bridge.gate`, `bridge.finish_spawn`, regions `bridge.start/finish/pit/deck`, `bridge.pit_floor`;
18 rows x 2 lanes of 2x2 `squidgame:bridge_glass` blocks at deck height y=40, 1-block gaps between rows, 2-block gap between
lanes, open pit down to y=-30; `docs/ARENA_MARKERS.md`). Port range 25640/25641. Test: `squid debug play glass_bridge 16`.

## The game
Cross the bridge row by row. Every row has two panels: one **tempered** (holds) and one **weak** (shatters, you fall into the
pit = eliminated). Nobody knows which; the contestants cross one after another in a **random order drawn at the start**, so the
early ones gamble and the later ones can learn from what they *saw*. Reach the far platform before time runs out.

### Rules (`core/bridge`, pure and tested)
* `BridgeRoute`: 18 rows, one safe lane per row, generated from the tournament seed + game number at `prepare` and **retained**
  for the whole game (it never changes when someone falls; it is saved with `saveState`/`loadState` so a resumed game keeps the
  same hidden route). Never exposed to clients or NPC behaviours except through public events.
* `BridgeKnowledge` (public information, derived only from things everybody can see): a row's safe lane becomes *known* when a
  panel is seen shattering (the other lane is safe) or a contestant is seen standing safely on a panel for a moment. Used by
  NPCs and by an optional HUD hint for humans ("the glass at row 4 left is gone").
* `BridgeRules` difficulty table: total time (~330/270/210 s), **stall limit** (a contestant who stays on one panel longer
  than ~25/18/12 s - HUD countdown - breaks it: stops the queue being blocked), hop timing, number of simultaneous crossers
  (the next contestant is released from the gate when the previous one is >= k rows ahead or gone), NPC skill bonus.
* Order: the queue is a random permutation; a human sees their position and the number of people ahead; hesitation, shoving
  and queue-jumping are not possible (gate-controlled). Humans/NPCs released in order; late/slow contestants never skip ahead.
* Timeout: everyone still on the bridge or in the queue is eliminated (`TIMEOUT`); the panel under them shatters.
* Finishing: contestants who reach the finish platform are safe and gather at `bridge.finish_spawn`.

### World mechanics
* Detect "standing on a panel" server-side per contestant on the deck (feet position -> row/lane via the markers, `onGround`).
  Stepping on a weak panel: a crack sound and hairline particles immediately, shatter ~6-10 ticks later (suspense; `glass.crack`
  variants, block break particles, shards flying), the contestant falls (scream, `EliminationCause.FELL` when below deck - 6; make
  sure nobody can "survive" by clinging). A tempered panel gets a subtle confirmation (soft chime/tiny shimmer).
* Falling bodies: let physics take them down the pit; do not teleport them; the tournament removes bodies later.
* Restore broken panels in `cleanup` (and keep a list of what you changed). Panels are single `bridge_glass` blocks: both
  weak and tempered look identical.
* The gate (`bridge.gate`, `ctx.doors()`) stays closed until the countdown ended, then releases contestants one by one with a
  guard animation; the camera/HUD shows "N players ahead of you".
* Decorative drama: overhead cages/lights flicker on elimination, guard on the platform watching, `ctx.danger(...)` pulses for
  the human currently crossing when the stall timer runs low.

### NPC behaviour (`BridgeNpcBehavior`)
Stateful per NPC: waits in the queue (idle/anxious animations by courage), watches the bridge (memory of **observed**
outcomes only via `NpcMemory` - events delivered through a public view like RLGL's `PublicView`; never read the route), when
released crosses row by row: if the safe lane of the next row is known they take it (with a mistake chance by skill under
pressure: panic near the stall limit or when low on courage), otherwise they choose by personality (risk tolerance, left/right
bias, superstition) or *hesitate* (idle `BRIDGE_HESITATE`, balance pose) bounded by the stall limit; crossing is done with
careful hops using the entity's motor helpers (`leapToward`, short run-ups, `BRIDGE_BALANCE`) - verify that NPCs actually
make the 1-block row gap and the 2-block lane gap (including diagonal hops) without falling by accident; very low courage
NPCs may freeze and break after the stall limit. Learning: later NPCs clearly use what earlier ones revealed.
Performance: only the contestant(s) on the deck think; the queue idles cheaply.

### HUD / UX
Server HUD widgets: queue position / players ahead, row progress (e.g. `7/18`), stall countdown bar, survivors counter. Chat/
title lines when someone falls ("Contestant 087 fell at row 5"). Humans may get a tiny client overlay marking *known* safe/broken
rows if you implement it (optional; must use only public info). Sounds from the existing events (glass crack/shatter, hall
ambience, wind, heartbeat via `danger`).

### Edge cases
Everyone falls early (route not yet known: the game still ends cleanly), a human disconnects on the bridge (stand-in continues),
human stalls, two contestants hopping at the same time, a contestant knocked off the deck by another entity, a resumed game after a
server restart (same route, bridge rebuilt intact, queue reshuffled or restored - document which), 2..128 contestants, an
odd/even number irrelevant.

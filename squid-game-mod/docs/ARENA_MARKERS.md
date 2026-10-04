# Arena contract: layout, markers and regions

Game logic never hard-codes coordinates. Every arena builder (`src/main/java/com/squidgame/build/arena/`)
emits **markers** (named points with yaw and `k=v` data) and **regions** (named boxes) through the
`BuildContext` DSL; they are stored in the world and read by the games via `GameContext.markers(name)` /
`marker(name)` / `region(name)`. If a name below is marked **required**, `tools/dump.sh` reports a PROBLEM
when it is missing. Extra markers are always allowed (document them in your builder's Javadoc).

Conventions: +X east, +Z south, +Y up. Yaw 0 faces south (+Z), 90 west (-X), 180 north (-Z), -90 east (+X).
`marker(name, x, y, z, yaw, data)`: x/z are block-centre fractional coordinates (use `.5`), **y is the floor
level the entity stands on** (top face of the floor block, i.e. block y + 1). Local coordinates are relative to the
arena origin (world y = 64 + local y). Every standing position must have 2 free blocks above it.

## Common markers (every game arena)

| Name | Count | Meaning |
|------|-------|---------|
| `waiting.spawn` | **>= 128, required** | grid of standing spots inside the waiting room, `data="slot=N"` (N = 0..), yaw faces the gate; keep >= 1.2 blocks apart |
| `waiting.player_entry` | 1, required | first entry point of the waiting room (humans arriving) |
| `gate.door` | 1, required | the doorway between waiting room and arena: centre-bottom of the opening **on the door plane**, yaw = direction of travel into the arena, `data="w=<odd width>,h=<height>"`. The builder leaves the opening empty (air) with a proper frame; the server installs the sliding door panels and collision |
| `arena.spectator` | 1, required | viewpoint for eliminated players: high, safe, with a good overview; spectators fly (spectator mode) so it may be above the arena |
| `arena.exit` | 1, required | where survivors gather for the results (inside the arena or just beyond its goal) |
| `guard.post` | 6-30 | masked guard posts, `data="rank=circle|triangle|square"`; triangle = armed soldiers (the only ones that "fire"), circle = workers, square = managers. Place them so triangles overlook the whole play area |
| `guard.patrol` | optional | patrol route points, `data="route=a,i=0"` (route id + order) |
| region `arena.bounds` | 1, required | play area box; leaving it is handled by the games |
| region `waiting.bounds` | 1, required | the waiting room box |

## Waiting room prefab

`com.squidgame.build.arena.prefab.WaitingRoomPrefab.build(BuildContext, Spec)` builds a standard guard waiting
room (pink/white/black, benches, rules board, window onto the arena) and emits all the required common waiting
markers + `gate.door` + `waiting.bounds`. Place it with `ctx.at(dx, dy, dz, rotationSteps, () -> ...)`; the room's
gate is on its local **+Z** wall (so rotate it to face the arena). Use it unless your arena has a strong reason
for something custom (then still emit the same markers).

## Hub (`ArenaId.HUB`, one connected structure, origin world (0, 64, 0))

Four parts share one coordinate frame (local = hub local, floor y = 0). Parts live in
`build/arena/hub/` and implement `HubPart` (see `HubBuilder`); they are built in this order and later parts may
carve into earlier ones.

| Part | Owns | Hard interface |
|------|------|----------------|
| `DormitoryPart` | dorm hall + prize display + registration | interior x[-40,40] z[-32,32] y[0,36]; walls x=+-41..42, z=+-33..34, roof above y=37. **Exit door** in the north wall at z=-33: opening x[-3,3] y[0,5] (7 wide, 6 high) |
| `CorridorPart` | guard corridors C1 and side rooms | C1 starts at the dorm exit door (x=0, z=-34) and ends at the stairway-hall entrance (x=0, z=-81), interior 7 wide x 6 high; it may bend inside box x[-30,30] z[-80,-34] as long as both ends meet those exact openings; guard rooms, symbol doors, cameras |
| `StairwayPart` | stairway maze hall | interior x[-45,45] z[-170,-82] y[0,72]; entrance in the south wall at z=-81 (x[-3,3] y[0,5]); top landing at y=60 along the north wall with **six gates** |
| `ControlRoomPart` | control room east of the dorm | interior x[44,84] z[-18,18] y[12,32]; floor y=12; its west wall (x=43) is shared with the dorm: window opening x=41..43, z in [-16,-3] and [3,16], y[14,30] (the dorm part installs black-tinted glass in the dorm's east wall there) and a door opening z[-2,2], y[12,15] connected to a **catwalk stair** along the dorm's east wall (built by `DormitoryPart`) |

Hub markers (all required unless noted):

| Name | Count | Meaning |
|------|-------|---------|
| `dorm.player_spawn` | 1 | where humans arrive: in front of the registration terminal |
| `dorm.registration_terminal` | 1 | location of the `squidgame:registration_terminal` block (the part places the block) |
| `dorm.npc_spawn` | **>= 160** | standing spots beside each bunk (one per contestant slot, `data="slot=N"`; yaw faces the aisle). Rows of towering bunk beds |
| `dorm.exit_door` | 1 | the dorm exit door marker (same format as `gate.door`: on the door plane, yaw 180 = travelling north, `w=7,h=6`) |
| `hub.spectator` | 1 | spectator viewpoint (the control room window is ideal) |
| `hub.podium` | 1 | where winners celebrate (dorm centre, raised podium; yaw faces the dorm entrance side) |
| `prize.pig` | 1 | centre of the giant piggy bank hanging from the dorm ceiling |
| `prize.counter` | 1 | anchor for the prize amount text (the server spawns a text display here) |
| region `prize.fill` | **>= 8** | stacked empty interior boxes of the piggy bank from bottom (`data` is not available on regions: they are stored in order of creation) - the server fills them with `squidgame:cash_block` as the prize grows, so leave the interior empty (air) |
| region `gate.red_light`, `gate.dalgona`, `gate.tug_of_war`, `gate.marbles`, `gate.glass_bridge`, `gate.final` | 6 | the six gate doorways at the top of the stairway hall, each a 5 wide x 5 high region in the north wall plane (z=-170/-171), at x = -37, -22, -7, +7, +22, +37 (centres), y[60,64]. Stepping into one teleports a visitor to that arena (free tour) |
| `stairs.path` | optional | ordered waypoints (`data="i=0"`) of one valid walking route from the corridor entrance to the gate landing |
| `control.monitor` | optional | monitor wall positions |
| `guard.post` / `guard.patrol` | as common | around the dorm door, corridors, stairway landings |

## Red Light, Green Light (`RedLightBuilder`, origin world (1000, 64, 0))

Outdoor-feeling school playground inside huge painted-sky walls. The field runs along **+Z**: start line near
z=12, finish line at z=140, doll beyond it. Waiting room behind the start (negative z).

| Name | Meaning |
|------|---------|
| `redlight.start_spawn` | **>= 128 required**, `data="slot=N"`: grid inside the start zone (z 0..11, x -50..50, 1.4 spacing), yaw 0 (facing +Z) |
| region `redlight.start_zone` | the start area |
| region `redlight.start_line` | painted start line (thin box) |
| region `redlight.finish_line` | painted finish line, x[-52,52], z=140 (1-2 blocks wide), y[0,3]; crossing it (z >= 141) = safe |
| region `redlight.safe_zone` | z[141,175], where finishers stand safely |
| `redlight.doll` | **required**, the doll's feet position on her pedestal at x=0.5, z=147, `yaw` = direction the doll's **body front** faces (she faces the tree: yaw 0 = facing +Z) |
| `redlight.tree` | the big tree's trunk base (z about 158) |
| region `arena.bounds` | x[-56,56] z[-6,178] |

## Dalgona (`DalgonaBuilder`, origin world (2000, 64, 0))

Huge warm school-hall classroom. Rows of long wooden benches with a seat and a table spot per contestant,
facing the front where a teacher's desk and green chalkboard stand. Walls carry large murals of the four
shapes (circle, triangle, star, umbrella).

| Name | Meaning |
|------|---------|
| `dalgona.seat` | **>= 128 required**, `data="slot=N"`: standing position on the seat (the contestant sits there; seat surface height), yaw faces the table/front |
| `dalgona.station` | **>= 128 required**, `data="slot=N"`: the block position of the `squidgame:dalgona_station` the builder places in front of seat N (the marker is the block's standing-top position: x.5, y = block y + 1, z.5) |
| `dalgona.front` | the teacher's podium position (yaw faces the room) |
| `dalgona.board` | centre of the chalkboard (a text display shows instructions there) |
| region `dalgona.seating` | the benches area |

## Tug of War (`TugOfWarBuilder`, origin world (3000, 64, 0))

Tall industrial hall. Two high platforms (floor y = 40) face each other over a deep pit (pit floor y = -30)
along the **X axis**: platform A at x <= -7, platform B at x >= 7, gap x in (-7, 7). The rope runs along X at
y = 41.2, z = 0. Each platform is a long narrow walkway (width 5) so a team stands single file along the rope.

| Name | Meaning |
|------|---------|
| `tug.slot_a` | **>= 32 required**, `data="slot=N"`: team A standing spots in single file, N=0 nearest the gap (x = -8.5, -9.5, ...), z = 0.5, yaw -90 (facing east, toward the gap) |
| `tug.slot_b` | **>= 32 required**: team B spots, N=0 nearest the gap (x = 8.5, 9.5, ...), yaw 90 |
| `tug.rope_a`, `tug.rope_b` | **required**: the two rope anchor points at the platform edges (x = -7, x = 7), y = 41.2, z = 0.5 |
| `tug.rope_center` | rope middle (0.5, 41.2, 0.5) |
| region `tug.edge_a`, `tug.edge_b` | the last 2 blocks of each platform before the drop |
| region `tug.pit` | the whole pit volume below the platforms |
| `tug.pit_floor` | a point on the pit floor (x=0.5, z=0.5) |
| `tug.waiting_a`, `tug.waiting_b` | gathering points (>= 40 each, `slot=N`) behind each platform where contestants stand before the match (the platform ends at x = -60 / +60; the waiting rooms or lobbies are behind them) |
| `tug.spare` | >= 64 spots in a gallery for contestants not taking part |

For this arena the waiting room is a **single shared one** (use the prefab) behind platform A; the gate door leads onto platform A's rear end, and a bridge/stair connects round to platform B for the guards. `waiting.spawn` still >= 128.

## Marbles (`MarblesBuilder`, origin world (4000, 64, 0))

Night-time old Korean village alleys under a painted star-sky roof: stone and wood houses, tiled roofs, lanterns,
laundry lines, doors with numbers, narrow cobbled lanes, a pairing square in the middle.

| Name | Meaning |
|------|---------|
| `marbles.square_spawn` | **>= 128 required**, `slot=N`: standing spots in the central pairing square |
| region `marbles.square` | the pairing square |
| `marbles.pair_a`, `marbles.pair_b` | **>= 64 each, required**, `data="k=N"`: for pair spot N the standing positions of partner A and B, facing each other 3 blocks apart |
| `marbles.pair_target` | **>= 64**, `data="k=N"`: centre of a target (bullseye, 5 rings, radius 2) laid in the ground 7 blocks from the throw line (the builder paints the rings with concentric coloured concrete: red/white/blue/white/red, bullseye gold) for pair spot N, on the lane axis; the throw line is the marker `marbles.pair_line` |
| `marbles.pair_line` | **>= 64**, `data="k=N"`: a point on the throw line (a painted white line), yaw pointing at the target |
| region `marbles.plot` | one box per pair spot k (in order) used for NPC/entity containment |
| `marbles.table` | optional `data="k=N"`: a small table / step where the odd-even exchange is shown |

Pair spots must be spread so that 64 pairs fit (grid of plots about 10 x 8 blocks).

## Glass Bridge (`GlassBridgeBuilder`, origin world (5000, 64, 0))

Vast dark industrial hall. A start platform, then **18 rows x 2 lanes** of glass panels (block
`squidgame:bridge_glass`, 2x2 blocks each) at deck height y = 40 along **+Z**, over a pit down to y = -30; an
end platform with an exit door. Row pitch 3 (2 panel + 1 gap), lanes at x in [-3,-2] and [1,2] (1 gap block at
x = 0 plus 1 at each side is air). Row r occupies z = 10 + 3r .. 11 + 3r.

| Name | Meaning |
|------|---------|
| `bridge.panel` | **36 required**, `data="row=R,lane=L"` (R 0..17, L 0 = left/west x -2.5, 1 = right/east x 1.5): top-centre of each panel (y = deck height + 1 = standing height) |
| `bridge.queue` | **>= 128 required**, `slot=N`: queue spots on the start platform (N=0 right at the gate) |
| region `bridge.start` / `bridge.finish` | the two platforms |
| `bridge.gate` | door marker (same format as `gate.door`) of a gate at the front of the start platform held shut while the queue waits |
| `bridge.finish_spawn` | **>= 128 required**, `slot=N`: where finishers gather |
| region `bridge.pit` | the pit volume under the bridge |
| `bridge.pit_floor` | a point on the pit floor |
| `bridge.hanging_cage` | optional decorative marker |
| region `bridge.deck` | the box enclosing all panels (x[-4,3], z[9,65]) |

The waiting room is behind the start platform (prefab). Panels are *separate* 2x2 blocks with air gaps: do not
put anything at deck level between rows or lanes.

## Final Squid Game (`FinalBuilder`, origin world (6000, 64, 0))

Sand-covered elementary-school playground at dusk, painted sunset backdrop, floodlights, playground props
(swings, slide, see-saw, monkey bars), a low rim wall. A **white painted squid** (blocks of white concrete) on
sand: circle (head) at the top (-Z end), triangle body in the middle, square tail at the bottom (+Z end), narrow
neck gap between the square and the triangle; total about 24 wide x 48 long; line thickness 1.

| Name | Meaning |
|------|---------|
| region `final.court` | bounding box of the whole squid |
| `final.boundary` | **>= 16 required**: the polygon of the court outline, ordered, `data="i=N"` (x/z = the vertices of the outer outline of the squid shape as painted) |
| `final.circle` | **required**: centre of the head circle, `data="r=<radius>"` (the attacker wins by standing inside it) |
| `final.triangle` | centre of the triangle body, `data="..."` free |
| `final.neck` | centre of the neck gap, `data="w=<width>"` |
| `final.attacker_spawn` | **required**: inside the square (tail) |
| `final.defender_spawn` | **required**: inside the triangle |
| `final.audience` | >= 40 `slot=N` spots on a raised gallery behind the rim wall for spectators |
| region `final.attack_zone`, `final.defence_zone` | the square and the triangle+circle |

The waiting room (prefab) is behind the rim wall at the tail end; the gate leads to the square.

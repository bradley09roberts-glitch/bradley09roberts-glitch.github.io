# Better builds: proper buildings, plans as data, materials and scaffolding

Hardcore Friends 3.0 gives the friends real buildings instead of plank boxes. The camp's cabin is now a cottage with a
log frame, a stone plinth, a gable roof with eaves, windows and lights; the storehouse and the watchtower are rebuilt
the same way. Houses, shops and the village's other buildings come from a **building library** of plans kept as data
files, in several styles to suit the biome. The friends make what those plans need (stairs, stone bricks, glass,
beds...) from what they gather, and put up temporary scaffolding to reach roofs.

## What the friends build now

- **The cabin** (Hamlet stage): a cottage, 7 × 9, on a cobblestone plinth with a log frame, plank walls and a stair
  roof with eaves over the doorstep and the back. Porch lanterns (torches until the camp has iron), a lantern hanging
  from the ridge inside, two barrels, and five windows. Friends still sleep inside it, and Spark's automatic door
  still works: the door, the doorstep and the floor behind the door are where they always were.
- **The second cabin** (Settlement stage): the same cottage.
- **The storehouse** (Village stage): a timber-framed store with a gable roof, two chests and six barrels.
- **The watchtower** (Village stage): a stepped stone base, a stone and timber core with arrow slits and a ladder up
  the middle, and a railed lookout with a roof and lanterns. Aegis climbs it and keeps watch from the lookout as
  before.
- **Houses for the village** (built by the village part of 3.0): ten plans so far, see the list below.

Windows are left open until the camp can make glass, then filled in by the repair job; lanterns become torches when
there is no iron; flower pots wait for bricks. A building never stands half-finished for want of decoration.

**Old worlds.** A building that was started (or finished) before 3.0 keeps its old plan, so nothing ends up half one
plan and half another. New buildings use the new plans. Set `fancyCampBuildings` to `false` in
`config/hardcorefriends.json` to keep the old box buildings for new sites too.

## The house plans

| Plan | Beds | Size (with eaves) | Styles |
|---|---|---|---|
| oak cottage | 2 | 9 × 8, 7 high | oak, plains, forest |
| spruce cabin (a log cabin) | 2 | 9 × 8, 7 high | spruce, taiga, snowy |
| sandstone hut (flat roof) | 2 | 7 × 7, 6 high | sandstone, desert |
| oak family house | 4 | 11 × 9, 9 high | oak, plains, forest |
| spruce lodge (log walls on a stone course, porch) | 4 | 11 × 9, 9 high | spruce, taiga, snowy |
| stone house (cobblestone, stone-brick corners, dark oak roof) | 4 | 11 × 9, 9 high | stone, dark_oak, mountain, plains |
| birch house (entered through its gable end) | 4 | 11 × 9, 9 high | birch, forest |
| sandstone house (flat roof, parapet, canopy) | 4 | 9 × 9, 6 high | sandstone, desert |
| oak townhouse (two storeys, stone below, timber above) | 6 | 13 × 9, 12 high | oak, plains |
| dark oak manor (two storeys, dark frame, birch walls) | 6 | 13 × 9, 12 high | dark_oak, forest |

Every house has its beds, a chest, a crafting table or a kitchen (furnace, smoker), lanterns and torches, carpets,
potted flowers, windows with shutters, and a door. The two-storey houses have a staircase up to the bedrooms.

`/friends builds` lists every plan by kind; `/friends builds house` lists the houses; `/friends builds <id>` shows
one plan (size, styles, beds, what it is built from, its marked spots).

## Materials and how they are made

Plans can use, with vanilla recipes and real ingredients: planks, logs and stripped logs (a log and a swing of an
axe), wooden stairs, slabs, doors, trapdoors, fences, gates and pressure plates (all of one wood, the plan's wood
when the camp has it), cobblestone and its stairs, slabs and walls, stone, smooth stone and their slabs and stairs,
stone bricks and their stairs, slabs and walls, bricks and brick stairs and slabs, sandstone, cut and smooth sandstone
and sandstone stairs, slabs and walls, glass and glass panes, wool (from sheep, or four string), carpets (two wool of
one colour make three), beds (three wool of one colour and planks; three hay bales make four straw beds when there is
no wool), barrels, flower pots (with a flower in),
lanterns, torches, iron bars, iron chains, hay bales, bookshelves, chests, crafting tables, furnaces, smokers, blast
furnaces, smithing, fletching and cartography tables, looms, stonecutters, grindstones, composters, lecterns,
cauldrons, campfires, ladders, and a bell if one is in the supply chest (friends cannot make bells).

New jobs make the materials buildings are short of. Each job is a speciality's, but anyone helps:

- **Firing at the furnace** (the builder): glass from sand, stone from cobblestone, smooth stone from stone, bricks
  from clay and smooth sandstone from sandstone, with coal or charcoal (spare planks from the chest only when it has
  plenty, never the builder's own building planks). Only in the friends' own furnace, and only when its input is empty
  or holds the same thing.
- **Digging sand and clay** (the forager): by day, the top block of dry sand or clay in the gathering ring, never
  inside the camp, never next to water, never near anything you built. Only a block level with the ground on all four
  sides is taken, in every other column, so each spot is dug once and left as a dip one block deep that anyone can
  step out of: no pits. Never the block a friend or an animal is standing on.
- **Shearing sheep** (the farmer): wild sheep in the gathering ring, with shears from the chest (or a pair made from
  two iron ingots when the chest has six or more). Never a named, leashed, penned or owned sheep, or one by your
  builds. Turn it off with `friendsShearSheep`. Beds and carpets need their wool all of one colour, so odd wool of
  mixed colours does not count towards them: the shearing goes on until there is enough of one colour.

The builders also tell the camp what a whole building will need, so gatherers fetch wood, stone and earth ahead of
time and the materials arrive in batches.

## Building properly

- **Order:** foundations, then the structure layer by layer from the bottom (walls, floors, the roof last), then
  windows, doors, furniture and lights. Within a layer, the builder works round what is in reach first.
- **Two-part blocks:** doors, beds and tall flowers go down as two halves.
- **Scaffolding:** a wall or roof out of reach from the ground is reached from a temporary pillar of dirt or
  cobblestone (at most `maxScaffoldHeight`, 6 blocks), put up beside or inside the building the way a player does it,
  never in a spot the building uses, next to water or lava, or by anything you built. The builder takes dirt for it
  from the chest when there is some (it digs out in a moment by hand; cobblestone takes the best part of ten seconds a
  block without a pickaxe). The builder digs it out again when done up there, and gets the blocks back. A friend
  called away while up a pillar comes straight down first. A pillar left behind (say the world was closed mid-climb)
  is taken down by the builder later. Pillars have their own record, so they are always recognised and taken down,
  even after grass has grown over the dirt. A friend pushed or knocked off a pillar half-way up stops there; the
  clean-up job takes the stub down. If the camp has no dirt or cobblestone at all, the builder only asks for some once a
  pillar is actually needed. Turn scaffolding off with `allowScaffolding` (high parts are then skipped).
- **Decoration never holds a building up:** carpets, flowers and the like that cannot be had are left out of the
  batch, and the layers above them (door tops, lights, windows) go on being built. The repair job adds them later.
- **Big buildings** are built in batches of 24 blocks and saved block by block, so nothing is lost if the work is
  interrupted, and a builder hands back after a couple of minutes so other work gets a turn.

## Settings (`config/hardcorefriends.json`)

| Option | Default | What it does |
|---|---|---|
| `fancyCampBuildings` | `true` | New camp buildings use the 3.0 plans; `false` keeps the 2.x boxes. |
| `allowScaffolding` | `true` | Builders may put up (and take down) temporary pillars to reach high parts. |
| `maxScaffoldHeight` | `6` | Tallest pillar, 2 to 6 blocks. |
| `friendsShearSheep` | `true` | Friends shear wild sheep for the wool their builds need. |

Digging sand and clay follows `allowQuarrying`.

## Commands

- `/friends builds`: every plan by kind.
- `/friends builds <kind>`: the plans of one kind (`house`, `shop`, `shop:bakery`...). A kind without a colon also
  lists its sub-kinds.
- `/friends builds <plan id>`: one plan in detail.
- `/friends builds check`: plan files that were skipped or have warnings, and why.

All are read-only and work at permission level 0 with cheats off.

---

# For plan writers: the plan file format

Plans live in a data pack (or the mod) at `data/<namespace>/blueprints/<path>.json`. The plan's id is
`<namespace>:<path>`: `data/hardcorefriends/blueprints/house/oak_cottage.json` is `hardcorefriends:house/oak_cottage`.
Sub-folders are just part of the id. Plans load with the server's data and again on `/reload`; a data pack file with
the same id replaces the mod's. The camp's own buildings are in the mod at `/hardcorefriends/camp_plans/` in the same
format (they are not in the library and a data pack cannot replace them).

```json
{
  "name": "oak cottage",
  "kind": "house",
  "styles": ["oak", "plains", "forest"],
  "wood": "oak",
  "size": [9, 8],
  "front": "north",
  "foundations": true,
  "palette": {
    "C": "cobblestone",
    "P": "planks",
    "L": {"block": "log", "axis": "y"},
    "v": {"block": "stairs", "facing": "south", "wood": "spruce"},
    "D": {"block": "door", "facing": "south", "hinge": "left"},
    "B": {"block": "bed", "facing": "south", "colour": "red"},
    "g": {"block": "glass_pane", "optional": true},
    "k": {"block": "lantern", "hanging": true, "fallback": {"block": "wall_torch", "facing": "north"}}
  },
  "layers": [
    ["....s....", ".CCCCCCC.", "..."],
    ["...", "..."]
  ],
  "markers": {
    "door": [[4, 0, -1]],
    "bed": [[2, 1, 4], [6, 1, 4]],
    "chest": [[4, 1, 5]]
  },
  "meta": {"beds": 2, "capacity": 2, "size": "small"}
}
```

### Fields

| Field | Required | Meaning |
|---|---|---|
| `kind` | yes | What the plan is for: `house`, `shop:<type>`, `workplace:<profession>`, `civic:<type>`, `decor:<type>`, `farm:<type>`, `camp:<structure>`. Lower-case letters, digits and `_`, an optional `:` and sub-kind (which may contain `/`). |
| `name` | no | What friends call it in speech, 1–48 characters. Default: the file name with `_` as spaces. |
| `styles` | no | Plain lower-case words: woods (`oak`, `spruce`, `birch`, `dark_oak`, `acacia`, `jungle`, `cherry`, `mangrove`...), stones (`stone`, `sandstone`) and places (`plains`, `forest`, `taiga`, `snowy`, `desert`, `savanna`, `jungle`, `mountain`). The village picks plans whose styles match the biome and whose wood the camp has. |
| `wood` | no | The wood wooden parts would like (any wood is used when the camp has none of it). Default: the first style that is a wood. |
| `size` | yes | `[width, depth]`: x (east) and z (south), each 1 to 32. Keep plans within about 15 × 15 and 12 high. |
| `front` | no | Which side of the drawing the door is on: `north` (the first row, default), `south`, `east` or `west`. The plan is turned so its front faces north; sites then turn it to face the camp centre or the street. |
| `foundations` | no | `true` (default): one-block dips under the footprint are filled with dirt or cobblestone before building. |
| `palette` | yes | One character → a material, see below. `.` and space mean "leave as it is", `-` means "must be air" (plants or snow there are cleared). |
| `layers` | yes | Bottom layer first, at most 24. Each layer is a list of `depth` rows (north to south); each row is a string of `width` characters (west to east). Layer 0 sits on the ground: it is usually the floor. |
| `markers` | no | Named spots, each a list of `[x, y, z]` in the drawing (y = layer). One block outside the footprint is allowed (the spot outside a door). |
| `meta` | no | Free-form facts as plain values (`beds`, `capacity`, `profession`, `shop`, `size`, `storeys`...). |
| `camp` | camp plans only | `{"offsets": [[x, z], ...], "faces_centre": true}`: preferred spots from the camp centre. |

### Palette values

Either a material name (`"planks"`), or an object:

| Key | Meaning |
|---|---|
| `block` | The material name (required). |
| any block property | `facing`, `half`, `shape`, `axis`, `type`, `part`, `hinge`, `open`, `lit`, `hanging`... set on whatever block the friends build it from. Unknown properties or values are errors. |
| `wood` | For wooden materials: the wood it would like (`"spruce"`). Any wood is used if there is none of it. |
| `colour` (or `color`) | For wool, carpets and beds: the colour it would like (`"red"`). |
| `attach` | `true`: placed after the whole structure (doors, windows, furniture, lights). The default depends on the material: doors, beds, glass panes, torches, lanterns, ladders, trapdoors, carpets, flowers and pots, pressure plates, chests, barrels, crafting tables, furnaces and the other work stations are attachments; walls, floors, roofs, stairs, fences and logs are not. |
| `optional` | `true`: decoration. Left out while its material cannot be had, so it never holds the building up, and added later by the repair job. Default `true` for flowers, flower pots, carpets and bells. |
| `fallback` | Another palette value (a name, or an object with its own properties) built instead when the material cannot be had: a lantern falls back to a torch. Once built, the fallback counts as done. |

**Material names.** `planks`, `log`, `stripped_log`, `slab`, `stairs`, `door`, `trapdoor`, `fence`, `fence_gate`,
`pressure_plate` (wooden: `wooden_stairs`, `wooden_slab` and so on also work), `cobblestone`, `cobblestone_stairs`,
`cobblestone_slab`, `cobblestone_wall`, `stone`, `stone_stairs`, `stone_slab`, `smooth_stone`, `smooth_stone_slab`,
`stone_bricks`, `stone_brick_stairs`, `stone_brick_slab`, `stone_brick_wall`, `bricks`, `brick_stairs`, `brick_slab`,
`sandstone`, `cut_sandstone`, `smooth_sandstone`, `sandstone_stairs`, `sandstone_slab`, `sandstone_wall`, `glass`,
`glass_pane`, `wool`, `carpet`, `bed`, `flower`, `tall_flower`, `flower_pot`, `potted_flower`, `barrel`, `iron_bars`,
`chain`, `hay_bale`, `dirt`, `torch`, `wall_torch`, `lantern`, `ladder`, `chest`, `crafting_table`, `furnace`,
`smoker`, `blast_furnace`, `smithing_table`, `fletching_table`, `cartography_table`, `loom`, `stonecutter`,
`grindstone`, `composter`, `lectern`, `cauldron`, `bell`, `campfire`, `hopper`, `bookshelf`, `enchanting_table`,
`anvil`, `brewing_stand`, `redstone_lamp`, `daylight_detector`, `obsidian`, `air`.

A vanilla block id works too and keeps its wood or colour as the wish: `minecraft:spruce_stairs` is `stairs` wishing
for spruce, `red_bed` is `bed` wishing for red, `minecraft:stripped_birch_log` is `stripped_log` wishing for birch.
Wooden and wool blocks are recognised by name (a wood the game has planks for, or a dye colour, then the part:
`_planks`, `_log`, `_wood`, `_stem`, `_hyphae`, `_slab`, `_stairs`, `_door`, `_trapdoor`, `_fence`, `_fence_gate`,
`_pressure_plate`, `_wool`, `_carpet`), so a plan loads the same on a fresh server start as after `/reload`. Blocks the
friends cannot make (concrete, terracotta, copper, quartz, other mods' blocks) are errors.

### Two-part blocks

Draw a door's **lower half** only: its top is added above it (the cell above must be `.` or `-`). Draw a bed's
**foot** only, with its `facing` pointing from the foot to the head: the head is added there (that cell must be `.` or
`-`). Tall flowers are like doors. You may draw the second half yourself (`"half": "upper"`, `"part": "head"`) if you
prefer; it must then sit where it belongs.

### Markers the mod uses

| Marker | Where | Used by |
|---|---|---|
| `door` | Where to stand just outside the front door (usually one block outside the plan, on the ground) | village (going home, visiting) |
| `inside` | A spot just inside the front door | village |
| `bed` | The **foot** of each bed (must be on a bed) | village (who sleeps where) |
| `chest` | Each chest or barrel of the household (must be on one) | village |
| `job` | The work station of a workplace | market |
| `counter` | Where a shopkeeper stands | market |
| `table`, `sit` | A table and the seats round it | village (meals, visits) |
| `sign`, `light`, `crafting`, `stairs` | A sign spot, lights, the crafting table, the foot of the stairs | anyone |

**What a house must have:** a `door` marker, a `bed` marker on each bed's foot, and a `chest` marker on a chest or
barrel. `meta.beds` is set to the number of bed markers. A shop without a `counter` or a workplace without a `job`
marker gets a warning.

### Rotation

Plans are drawn once; every block state turns with the site (facing, axis, stairs and their shapes, doors and their
hinges, beds, trapdoors, chests, ladders, torches, fence and pane connections). Draw with real facings as you see the
drawing: `"facing": "south"` on a front door drawn with `"front": "north"` faces into the house.

### How to add a plan

1. Copy one of the mod's plans (`data/hardcorefriends/blueprints/house/oak_cottage.json`) into your data pack under
   `data/<your namespace>/blueprints/<kind>/<name>.json`.
2. Set `kind`, `name`, `styles` and `size`, and draw the layers bottom-up. Keep every row exactly `width` characters
   and every layer exactly `depth` rows.
3. Use only materials the friends can make (above), mark decoration `optional` and give lights a `fallback`, so the
   building never waits on something rare.
4. Leave headroom: the friends walk in through the door and reach high parts from the ground, from inside, or from a
   pillar up to 6 blocks; a building whose upper floor is only reachable by a ladder will have its top built from
   pillars outside. For two storeys draw a staircase of `stairs` (not a ladder) with a hole in the floor above it.
5. Add the markers your kind needs (see the table).
6. `/reload`, then `/friends builds check`: every problem is listed with its layer, row and column, and the file is
   skipped until it is fixed. `/friends builds <id>` shows what the plan will cost.

---

# For other packages: building library plans on chosen spots

`civic.BlueprintLibrary.get()` gives the plans: `get(id)`, `byKind(kind)` (exact, or every sub-kind of a kind
without a colon) and `ids()`. `architecture.Styles.pick(level, pos, kind, filter)` picks the plan of a kind that best
suits a spot (its biome and the wood and stone in the supply chest); `Styles.stylesAt(level, pos)` gives the spot's
styles.

`architecture.Construction` builds a plan on a spot of your choosing, under a site key of your own
(`village.house.3`; never a camp structure id):

- `check(level, plan, origin, rotation)`: why it cannot go there (ground not firm and level, something in the way,
  too close to a player's build, overlapping another site), or empty if it can. `origin` is the plan's local
  (0, 0, 0) on the ground floor (one above the ground); rotation 0–3 turns the front from north to east, south, west.
- `reserve(level, key, plan, origin, rotation, wood)`: reserves the site and remembers the plan (and the wood, null
  for the plan's own or the camp's).
- `job(friend, key, reason, repair)`: a `camp.BuildJob` for a friend's next run at it; tick it like
  `ai.role.build.BlueprintTask` does (a `SpecialityTask` around your own task is the usual way). Use reason `BUILD`.
- `isFinished`, `progress`, `planOf`, `markers(level, key, "bed")` (world positions), `sites(level)`,
  `release(level, key)` (forgets the site; the blocks stay), and `FINISHED` listeners told when a building is done.
  A run notices within a second that its site was released (or reserved afresh) and stops placing, coming down from
  any pillar first. Runs only work in the camp's dimension: a job handed to a friend elsewhere ends at once.

The builder's repair job also checks finished library buildings, two at a time, and mends them.

---

# Honest limits

- Nothing here has been run in a game yet. The plans were checked by a script for their shape, palettes, two-part
  blocks and markers, and the code compiles; how they look, and how well friends cope with the scaffolding and the
  stairs of two-storey houses, is untested.
- The game tests in `BuildingGameTest` still describe the 2.x cabin and watchtower (window and block counts); they
  compile but will need their expectations updated to the new plans.
- Scaffolding is a pillar the friend jumps up, like a player; reaching the middle of a wide roof relies on a pillar
  inside the building or on its upper floor, found by a small search. Spots it cannot reach are skipped and tried again
  next run. A friend knocked off a pillar takes fall damage like anyone else (at most six blocks).
- A pillar whose blocks cannot be dug back out (water has flowed in beside it, or a player stands right next to it)
  stays until the clean-up job can take it down; the friend on it waits a while, then is left to find their own way.
  Coming down gives up only after half a minute without getting a block lower, so a cobblestone pillar dug without a
  pickaxe (several seconds a block) still comes down in one go.
- Windows without glass are open holes; a lantern that became a torch stays a torch even once there is iron.
- Wool comes from wild sheep (and the pen's butchered surplus): no shearing in the pen. Clay is rare on dry land, so
  bricks and flower pots may never come; plans mark them as decoration. Friends cannot dye wool, so coloured odd wool
  that never makes up a set of one colour sits in the chest unused.
- Sand digging leaves a scatter of one-block dips (every other column) in the sand it takes from; they are easy to
  walk out of, but the ground is not filled back in.
- Plans are not checked against the camp's real terrain beyond the usual site search: very big plans need big, fairly
  level spots, and the camp grows to find room.
- Data packs can add and replace library plans but not the camp's own buildings.
- The camp's record of blocks the friends placed holds 20,000 positions; a large village may need it raised.
  Scaffolding does not depend on it (pillars keep their own record), so a full record never strands a builder.

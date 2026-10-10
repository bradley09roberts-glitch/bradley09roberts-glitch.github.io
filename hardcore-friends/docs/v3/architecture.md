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
- **The building library** (built by the village and market parts of 3.0): 54 plans, among them 19 houses in
  nine styles, shops, workplaces, a town hall, a school, a tavern, a chapel, walls and a gate, farms and street
  decoration. See *Library* below.

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

Nine more houses came with the building library (an oak hut for one, a plains farmhouse, a taiga cottage and longhouse,
a birch cottage, a dark oak cottage, a desert courtyard house, a two-storey stone townhouse and an acacia house): see
*Library* below.

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
  never in a spot the building uses, next to water or lava, or by anything you built. The builder prefers a low
  pillar close by; when the handiest spots are somewhere it cannot walk to (a ledge inside, the floor of a walled
  basin), it looks further round for one it can. It takes dirt for the pillar from the chest when there is some (it
  digs out in a moment by hand; cobblestone takes the best part of ten seconds a block without a pickaxe). The builder
  digs it out again when done up there, and gets the blocks back. A friend called away while up a pillar comes
  straight down first. A pillar left behind (say the world was closed mid-climb) is taken down by the builder later.
  Pillars have their own record, so they are always recognised and taken down, even after grass has grown over the
  dirt. A friend pushed or knocked off a pillar half-way up stops there; the clean-up job takes the stub down. If the
  camp has no dirt or cobblestone at all, the builder only asks for some once a pillar is actually needed. Turn
  scaffolding off with `allowScaffolding` (high parts are then skipped).
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
   Wherever the floor steps up (onto a stair, a slab or a block), the lower spot needs **three** blocks of air above
   it, not two: a friend's head rises before their feet are on the step. So the first stair is never right behind a
   two-high doorway; leave a level spot inside the door first. Keep anything a friend cannot walk to (a ledge, a
   walled basin) out of reach of the roof's middle, or the builder has to look further for a place to stand.
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

The builder's repair job also checks finished library buildings, two at a time, and mends them. It looks past gaps it
cannot fill yet (carpets without wool, windows without glass), so a torch knocked off a wall behind them is put back.

---

# Library

The building library is what the village and the market build from: **54 plans** in
`data/hardcorefriends/blueprints/`, 19 houses and 35 other buildings, so that a village has proper houses, shops,
workshops, a town hall, a school, a tavern, a chapel, walls and a gate, farms, and lamps, benches and gardens along its
streets. `/friends builds` lists them all; `/friends builds <kind>` (`house`, `shop`, `civic:tavern`...) and
`/friends builds <id>` show one kind or one plan with exactly what it costs.

## What every plan has

- **A front.** Every plan is drawn with its front (the door, the counter, the deck) to the north; the village turns it
  to face the street.
- **Real building.** Stone footings, log frames, plank or stone walls, roofs of stairs with eaves and overhangs, gables
  with windows, shutters, porches, canopies over doors, chimneys. No bare boxes.
- **Light everywhere.** Every room, porch and covered floor has a torch or a lantern (a lantern falls back to a torch
  until the camp has iron), so nothing can spawn inside a building at night. Roofs are stairs with a slab along the
  ridge, flat roofs are slabs behind a parapet and chimneys end in a wall block, so nothing spawns on top either.
- **Nothing rare holds a building up.** Glass, flowers and pots, carpets, bookshelves, bells, anvils, brewing stands,
  cauldrons, lecterns, iron bars, wool displays and hay stacks are decoration: the building is finished without them and
  the repair job adds them when the camp can make them. The few job blocks that need iron (the smithy's blast furnace,
  the smith's smithing table, the mason's stonecutter) are the exception: those buildings wait for the iron.
- **Room to walk.** Every marked spot (beds, chests, job blocks, counters, seats, lookouts) can be walked to from the
  front door, with two blocks of headroom, and three wherever the floor steps up (a stair, a slab, a step), as a
  grown friend's head rises before their feet are on the step; two-storey plans have a staircase of stairs (never a
  ladder) with a rail round the stairwell. Basins (the wells, the fountain) have a rim one block high, so a friend
  who ends up inside a dry one can climb out.
- **Safe upper floors.** Windows on upper floors have a fence rail across their bottom row (the glass above it), so
  nobody can step, or be knocked, out of an upper floor before the camp has glass for the windows.

## Kinds, and the spots they mark

| Kind | Plans | Spots (markers) |
|---|---|---|
| `house` | 19 | `door` (outside the front door), `inside`, `bed` (the foot of each bed), `chest`, `table` and `sit`, `crafting`, `stairs` (two storeys), `light` |
| `shop:bakery`, `shop:general`, `shop:butcher`, `shop:fishmonger`, `shop:tailor`, `shop:smith` | 7 | `counter` (where the shopkeeper stands, behind the counter, facing the customers), `customer` (where a customer stands, across the counter), `job` (the job block: smoker, barrel, loom, smithing table), `chest` (the stock), `display` (display barrels and wool), `sign` (an empty spot by the door for a sign), `door`, `inside` |
| `workplace:fisher`, `blacksmith`, `mason`, `beekeeper`, `carpenter`, `doctor`, `shepherd`, `farmer` | 8 | `job` (the work station), `chest`, plus the trade's own spots: `fishing` (casting spots on the deck edge), `hive` (where a beehive goes), `patient` (the foot of a patient's bed), `waiting`, `furnace`, `anvil`, `grindstone`, `smithing`, `fletching`, `bench`, `stockpile`, `woodpile`, `rack`, `composter` |
| `civic:town_hall`, `well`, `school`, `tavern`, `market`, `chapel`, `wall`, `gate`, `watchtower` | 10 | `bell` (the town and school bells), `table` and `sit` (council table, tavern tables, pews), `lectern`, `seat` and `teacher` (school), `job` and `counter` (the innkeeper's smoker and bar), `bed` (tavern guest beds), `stall`, `counter` and `customer` (market stalls), `altar`, `lookout` (watchtower), `gate` (the way through), `join` (where the next wall segment starts), `water` (a well or fountain basin) |
| `farm:wheat`, `farm:barn`, `farm:orchard` | 3 | `field` (two opposite corners of the soil), `water` (the channel), `job` (composter), `pen` (barn stalls), `trough`, `sapling` (orchard tree spots) |
| `decor:lamp`, `bench`, `garden`, `fountain`, `signpost` | 7 | `light`, `sit`, `water`, `sign` |

Facts in `meta`: `beds` and `capacity` (houses), `size` (`tiny`, `small`, `medium`, `family`, `large`), `storeys`,
`shop`, `profession` (the trade that works there: `baker`, `shopkeeper`, `butcher`, `fishmonger`, `tailor`,
`blacksmith`, `fisher`, `mason`, `beekeeper`, `carpenter`, `doctor`, `shepherd`, `farmer`, `teacher`, `innkeeper`),
`open_front` (no door: a stall or an open forge), `faces: water` (the fishing hut's deck should face the water),
`needs: water` (a well, the fountain and the wheat field are built dry: no job pours water yet, so the `water` spots
mark where a player, or a later job, should pour it),
`seats`, `stalls`, `hives`, `guest_beds`, `tables`, `length` (wall and gate segments).

Styles name the biome and the materials (`oak`, `spruce`, `birch`, `dark_oak`, `acacia`, `sandstone`, `stone`, `plains`,
`forest`, `taiga`, `snowy`, `desert`, `savanna`, `mountain`); a few town-centre plans also carry `town`, a hint for the
village (no biome gives it).

## The plans

Front views below are drawn from the north, roughly: `/` `\` `=` stairs, `_` slabs, `~` upside-down stairs or carpet,
`|` `-` logs, `#` planks, `%` stone, `o` glass, `D` door, `!` fences and walls, `*` lights, `:` shutters, `"` flowers
and earth, `B` bell, `+` furniture and work blocks.

### Houses

| Plan | Kind | Styles | Size (w × d, high) | Beds | Main materials |
|---|---|---|---|---|---|
| `house/oak_cottage` | `house` | oak, plains, forest | 9 × 8, 7 | 2 | wood 167, stone 23, glass pane 8, carpet 6 |
| `house/spruce_cabin` | `house` | spruce, taiga, snowy | 9 × 8, 7 | 2 | wood 167, stone 23, glass pane 8, carpet 6 |
| `house/sandstone_hut` | `house` | sandstone, desert | 7 × 7, 6 | 2 | sandstone 168, carpet 6, glass pane 6, lantern 3 |
| `house/oak_family_house` | `house` | oak, plains, forest | 11 × 9, 9 | 4 | wood 284, stone 29, glass pane 20, carpet 5 |
| `house/spruce_lodge` | `house` | spruce, taiga, snowy | 11 × 9, 9 | 4 | wood 263, stone 52, glass pane 14, bed 4 |
| `house/stone_house` | `house` | stone, dark_oak, mountain, plains | 11 × 9, 9 | 4 | wood 216, stone 99, glass pane 14, barrel 4 |
| `house/birch_house` | `house` | birch, forest | 11 × 9, 9 | 4 | wood 284, stone 29, glass pane 14, bed 4 |
| `house/sandstone_house` | `house` | sandstone, desert | 9 × 9, 6 | 4 | sandstone 258, glass pane 8, carpet 6, wood 5 |
| `house/oak_townhouse` | `house` | oak, plains | 13 × 9, 12 | 6 | wood 412, stone 109, glass pane 15, carpet 6 |
| `house/dark_oak_manor` | `house` | dark_oak, forest | 13 × 9, 12 | 6 | wood 412, stone 109, glass pane 15, carpet 6 |
| `house/oak_hut` | `house` | oak, plains, forest | 7 × 8, 8 | 1 | wood 141, stone 19, glass pane 5, lantern 3 |
| `house/plains_farmhouse` | `house` | oak, plains | 13 × 11, 10 | 3 | wood 360, stone 50, glass pane 11, wall torch 5 |
| `house/taiga_cottage` | `house` | spruce, taiga, snowy | 9 × 9, 12 | 2 | wood 259, stone 55, glass pane 6, carpet 4 |
| `house/taiga_longhouse` | `house` | spruce, taiga, snowy, stone | 13 × 11, 11 | 6 | wood 359, stone 102, glass pane 9, bed 6 |
| `house/birch_cottage` | `house` | birch, forest | 9 × 9, 9 | 2 | wood 184, stone 25, glass pane 7, barrel 4 |
| `house/dark_oak_cottage` | `house` | dark_oak, forest, stone | 11 × 11, 11 | 3 | wood 319, stone 107, glass pane 12, wall torch 4 |
| `house/desert_courtyard_house` | `house` | sandstone, desert | 11 × 12, 6 | 4 | sandstone 395, glass pane 8, wall torch 8, barrel 5 |
| `house/stone_townhouse` | `house` | stone, dark_oak, plains, forest, town | 9 × 11, 12 | 4 | wood 296, stone 103, glass pane 15, barrel 7 |
| `house/acacia_house` | `house` | acacia, savanna | 11 × 10, 9 | 2 | wood 293, stone 27, glass pane 8, wall torch 5 |

- **Oak cottage** (`house/oak_cottage`): The camp cottage's design as a house: oak frame on a cobblestone plinth, plank
  walls, a spruce gable roof with eaves, shuttered windows, two beds. (From the first set of houses.)
- **Spruce cabin** (`house/spruce_cabin`): A log cabin of spruce for two, on a cobblestone plinth, with a gable roof and
  shutters. (From the first set of houses.)
- **Sandstone hut** (`house/sandstone_hut`): A small flat-roofed sandstone hut for two with a parapet and a doorway
  canopy. (From the first set of houses.)
- **Oak family house** (`house/oak_family_house`): A larger oak house for four with a table and chairs, a kitchen corner
  and a spruce roof. (From the first set of houses.)
- **Spruce lodge** (`house/spruce_lodge`): Spruce log walls on a stone course with a porch, four beds and a table for
  four. (From the first set of houses.)
- **Stone house** (`house/stone_house`): Cobblestone walls with stone-brick corners under a dark oak roof, four beds.
  (From the first set of houses.)
- **Birch house** (`house/birch_house`): A birch house for four entered through its gable end. (From the first set of
  houses.)
- **Sandstone house** (`house/sandstone_house`): A sandstone house for four: flat roof, parapet, canopy over the door.
  (From the first set of houses.)
- **Oak townhouse** (`house/oak_townhouse`): Two storeys: a cobblestone ground floor with the kitchen and table, a
  timber upper floor with bedrooms for six. (From the first set of houses.)
- **Dark oak manor** (`house/dark_oak_manor`): Two storeys with a dark oak frame and birch walls, bedrooms for six
  upstairs. (From the first set of houses.)
- **Oak hut** (`house/oak_hut`): A one-room oak hut for one, with a gable to the front and a little porch under the
  roof.
- **Plains farmhouse** (`house/plains_farmhouse`): An L-shaped oak farmhouse: kitchen and parlour in the long wing, the
  bedroom in the short one, a stone chimney, a bench by the door and a woodpile under the back eaves.
- **Taiga cottage** (`house/taiga_cottage`): A snug spruce cottage with a steep roof for the snow: a cobblestone course,
  log walls, a tall front gable with a window over the door, and a stone chimney at the back.
- **Taiga longhouse** (`house/taiga_longhouse`): A long spruce hall on a cobblestone base for a big household: a feast
  table down the middle, beds round the walls, a kitchen by the door and a stone chimney at the east gable.
- **Birch cottage** (`house/birch_cottage`): A white birch cottage under a dark hipped roof with a little gable over the
  door, flower beds under the side windows and lanterns at the door.
- **Dark oak cottage** (`house/dark_oak_cottage`): A stone-brick cottage with a cross-gabled dark oak roof: four gables,
  each with a window, a canopy over the door, a dining corner and a bedroom corner for three.
- **Desert courtyard house** (`house/desert_courtyard_house`): A flat-roofed sandstone house built round a little
  courtyard garden open to the sky: kitchen down one side, beds down the other and along the back, a shaded doorway and
  a parapet round the roof.
- **Stone townhouse** (`house/stone_townhouse`): A narrow two-storey townhouse for the town centre: a stone-brick shop
  floor below and a jettied timber storey above that leans out over the street on stone brackets, under a tall front
  gable.
- **Acacia house** (`house/acacia_house`): A low acacia house for the savanna with a deep veranda under the front of its
  hipped roof, a bench by the door and two beds.

```
  oak hut             plains farmhouse    taiga cottage
                                              _
                                              # !
                      !                      /#\%
                      %____________          ###%
     _                %############         /###\
     #                =============         ##-##
    /#\               =============        /##o##\
   /#o#\              =============        ###o###
  /-----\             %|#*#*#|###|        /|-===-|\
  :!*o*!:             ::o:o:o::o:|:       :|-***-|:
   !#D#!              %|##D##|###|         |%%D%%|
   %#=#%              %%%%=%==%%%%         %%%=%%%
```

```
  taiga longhouse     birch cottage       dark oak cottage
  ____________!                           ___________
  ############%                           ###########
  =============           _               ====/#\====
  =============           #               ===/#|#\===
  =============          =_=              ==/##o##\==
  =============         ==#==             =/###|###\=
  =============        ==/o\==            =|-------|=
   |---*-*---|%       ===---===            |%%===%%|
  :|%:o:+:o:%|:       :|*o*o*|:           :|:o*%*o:|:
   |%%%%D%%%%|%       "|##D##|"            |%%%D%%%|
   %%%%%=%%%%%%       "%%%=%%%"            %%%%=%%%%
```

```
  desert courtyard house    stone townhouse           acacia house
                                _
                               /#\
                              /#o#\
                             /##o##\                      ===
                            /|-----|\                    =====
                             |o#|#o|                    =======
  !!!!!!!!!!!                |!#|#!|                   =========
  %%%%%%%%%%%                -------                  ===========
  %%%%___%%%%                ~*~%~*~                   !*!###!*!
  %%o%***%o%%               :%o%*%o%:                  !o!***!"!
  %%%%%D%%%%%                %%%D%%%                   !=!#D#!+!
  %%%%%=%%%%%                %%%=%%%                   ####=####
```

### Shops

| Plan | Kind | Styles | Size (w × d, high) | Trade | Main materials |
|---|---|---|---|---|---|
| `shop/bakery` | `shop:bakery` | oak, plains, forest | 11 × 9, 11 | baker | wood 265, stone 49, glass pane 14, dirt 4 |
| `shop/general_store` | `shop:general` | spruce, taiga, plains, forest | 13 × 11, 11 | shopkeeper | wood 408, stone 35, barrel 19, glass pane 12 |
| `shop/butcher` | `shop:butcher` | stone, oak, plains, mountain | 9 × 9, 10 | butcher | wood 144, stone 81, barrel 6, glass pane 6 |
| `shop/fishmonger` | `shop:fishmonger` | spruce, birch, taiga, plains | 9 × 7, 8 | fishmonger | wood 152, stone 16, barrel 4, wall torch 3 |
| `shop/tailor` | `shop:tailor` | birch, forest, plains | 9 × 10, 10 | tailor | wood 246, stone 27, glass pane 11, wall torch 7 |
| `shop/smith` | `shop:smith` | stone, mountain, plains, taiga | 9 × 9, 10 | blacksmith | stone 176, wood 50, barrel 4, iron bars 4 |
| `shop/desert_general_store` | `shop:general` | sandstone, desert | 11 × 10, 6 | shopkeeper | sandstone 292, carpet 18, barrel 11, wall torch 10 |

- **Bakery** (`shop/bakery`): A cottage bakery: a counter across the shop with bread barrels, an oven wall at the back
  (two furnaces round the smoker under a stone hood) with its chimney, hay bales for the flour and big front windows.
- **General store** (`shop/general_store`): A wide spruce general store with a deep porch under the front of its roof:
  shop windows, a long counter, and shelves of barrels and chests all round the back.
- **Butcher's shop** (`shop/butcher`): A small stone butcher's shop with an oak frame and a hipped roof: a smooth-stone
  counter, the smoker and a larder of barrels behind it, and a cauldron for washing up.
- **Fishmonger's stall** (`shop/fishmonger`): An open-fronted fish stall: a counter of barrels and slabs across the
  front under a deep spruce roof, the fishmonger behind it with the job barrel, a cauldron of water and the stock chest.
- **Tailor's shop** (`shop/tailor`): A birch tailor's shop with a front gable: the loom behind the counter, bolts of
  coloured wool on display, carpets underfoot and a fitting corner.
- **Smith's shop** (`shop/smith`): A sturdy stone-brick armoury: iron-barred windows, a stone roof, the smithing table
  and grindstone behind the counter and an anvil in the corner when the camp can make one.
- **Desert general store** (`shop/desert_general_store`): A sandstone general store for desert towns: a striped awning
  on pillars over the shop front, a flat roof behind a parapet, a long counter and shelves of barrels and chests.

```
  bakery            general store     butcher's shop
       !            _____________
  ___________       #############         _
  ###########       =============         #
  ===========       =============        ===
  ===========       =============       =====
  ===========       =============      =======
  ===========       =============     =========
   |oo*#*oo|         !*o!###!o*!       |o*%*o|
  :|oo#%#oo|:        !oo!*+*!o+!       |o%%%o|
   |""#D#""|         !==!#D#!++!       |%%D%%|
   %""%=%""%         !##!#=#!##!       %%%=%%%
```

```
  fishmonger's stall    tailor's shop         smith's shop
                            _                     _
                            #                     %
  _________                /#\                   ===
  #########               /#o#\                 =====
  =========              /##o##\               =======
  =========             /|-----|\             =========
  =========              |o###o|               %%*%*%%
   |*#***|              :|o***o|:              %!%%%!%
   |+__+#|               |##D##|               %%%D%%%
   %##=##%               %%%=%%%               %%%=%%%
```

```
  desert general store
   !!!!!!!!!
   ~~~~~~~~~
   _________
   !oo*%*oo!
   !%%%D%%%!
   !___=___!
```

### Workplaces

| Plan | Kind | Styles | Size (w × d, high) | Trade | Main materials |
|---|---|---|---|---|---|
| `workplace/fishing_hut` | `workplace:fisher` | spruce, taiga, plains, forest | 9 × 11, 8 | fisher | wood 163, stone 17, barrel 5, wall torch 5 |
| `workplace/smithy` | `workplace:blacksmith` | stone, mountain, plains, taiga, spruce | 11 × 9, 11 | blacksmith | wood 171, stone 129, wall torch 4, barrel 3 |
| `workplace/masons_yard` | `workplace:mason` | stone, mountain, plains | 11 × 11, 7 | mason | stone 116, wood 101, wall torch 4, barrel 3 |
| `workplace/apiary` | `workplace:beekeeper` | oak, plains, forest, birch | 11 × 11, 3 | beekeeper | wood 57, dirt 12, flower 10, lantern 5 |
| `workplace/carpenters_workshop` | `workplace:carpenter` | oak, plains, forest | 11 × 9, 10 | carpenter | wood 285, stone 29, glass pane 10, barrel 5 |
| `workplace/clinic` | `workplace:doctor` | birch, forest, plains | 11 × 11, 11 | doctor | wood 262, stone 57, glass pane 16, wall torch 5 |
| `workplace/shepherds_hut` | `workplace:shepherd` | spruce, taiga, plains, snowy | 9 × 8, 7 | shepherd | wood 156, stone 40, barrel 4, carpet 3 |
| `workplace/farm_shed` | `workplace:farmer` | oak, spruce, plains, forest, taiga | 7 × 6, 6 | farmer | wood 81, stone 21, hay bale 2, wall torch 2 |

- **Fishing hut** (`workplace/fishing_hut`): A spruce fishing hut with a deck out front for casting from (its front, the
  deck, should face the water), a barrel for the catch, rods on a rack, and lantern posts on the deck corners.
- **Smithy** (`workplace/smithy`): An open-fronted stone smithy: a forge wall at the back with the blast furnace between
  two furnaces under a stone hood and a tall chimney, the smithing table, grindstone and anvil out front, and a
  quenching cauldron.
- **Mason's yard** (`workplace/masons_yard`): A mason's yard behind a low stone wall: stockpiles of stone, cobblestone
  and stone bricks in the yard, a paved path, and a stone-built shed at the back with the stonecutter, a chest and
  barrels.
- **Apiary** (`workplace/apiary`): A fenced bee garden: flower beds in rows, four hive stands (a lit campfire under a
  bale of hay, so the bees stay calm when the honey is taken; the hives go on top, at the "hive" spots) and a little
  honey stand under a roof with the job barrel and a chest.
- **Carpenter's workshop** (`workplace/carpenters_workshop`): A timber-framed carpenter's workshop: the crafting table
  and fletching table at the back, a work bench and a sawhorse in the middle, a stack of logs inside and a woodpile
  under the back eaves.
- **Clinic** (`workplace/clinic`): A bright birch clinic under a hipped roof: a waiting bench by the door, two patient
  beds, the brewing stand on a counter (when the camp has one), a cauldron, shelves of supplies and plenty of light.
- **Shepherd's hut** (`workplace/shepherds_hut`): A shepherd's spruce hut: the loom by the window, chests and barrels
  for wool, a stack of fleeces, and a rack of fleeces drying under the gable outside.
- **Farm shed** (`workplace/farm_shed`): A farmer's open-fronted shed: the composter (the farmer's work station), a
  chest of seed and tools, barrels, hay bales and a water butt, under a little gable roof.

```
  fishing hut      smithy           mason's yard
                        !
                   ___________
                   ###########
      _            ===========
      #            ===========      ===========
     /#\           ===========      ===========
    /#o#\          ===========      ===========
   *|---|*          %%*%%%*%%        |%%%|%%%|
   !|***:!          %*%%%%%*%        |***|***|
   !/#D#!!          %+++%+++%        %+%%|%%+%
   ###=###          %%=%%%=%%       !!!%%_%%!!!
```

```
  apiary                  carpenter's workshop    clinic
                                                       _
                          ___________                  #
                          ###########                 ===
                          ===========                =====
                          ===========               =======
                          ===========              =========
                          ===========             ===========
                           |#|*#*|#|               |#o*#*o#|
         ___               |o|o*o|o|               |#o#|#o#|
  *"""# #!*!*              |#|#D#|#|               |%%%D%%%|
  !!!!!!!!!!!              %%%%=%%%%               %%%%=%%%%
```

```
  shepherd's hut    farm shed
  =========
  =========         =======
  =========         =======
  =========         =======
  ::o*#*o:           |#**|
   |%%D%%|~          |#++|
  #%%%=%%%!          %%%%%
```

### Civic buildings

| Plan | Kind | Styles | Size (w × d, high) | Trade | Main materials |
|---|---|---|---|---|---|
| `civic/town_hall` | `civic:town_hall` | oak, stone, plains, forest, town | 15 × 14, 11 |  | wood 444, stone 144, glass pane 28, wall torch 9 |
| `civic/well` | `civic:well` | stone, oak, spruce, plains, forest, taiga, town | 5 × 5, 6 |  | stone 41, wood 24, wall torch 2, lantern 1 |
| `civic/desert_well` | `civic:well` | sandstone, desert | 5 × 5, 6 |  | sandstone 41, wood 24, wall torch 2, lantern 1 |
| `civic/school` | `civic:school` | birch, oak, plains, forest, town | 13 × 11, 11 | teacher | wood 318, stone 63, glass pane 24, wall torch 7 |
| `civic/tavern` | `civic:tavern` | oak, spruce, plains, forest, taiga, town | 15 × 9, 12 | innkeeper | wood 412, stone 147, glass pane 27, wall torch 11 |
| `civic/market` | `civic:market` | oak, spruce, plains, forest, taiga, town | 13 × 13, 5 |  | wood 121, carpet 64, barrel 16, dirt 8 |
| `civic/chapel` | `civic:chapel` | stone, spruce, plains, forest, taiga, town | 9 × 15, 11 |  | wood 258, stone 197, glass pane 32, carpet 8 |
| `civic/wall` | `civic:wall` | stone, town, plains, mountain | 9 × 3, 5 |  | stone 69, wall torch 4 |
| `civic/gate` | `civic:gate` | stone, town, plains, mountain | 11 × 3, 8 |  | stone 178, lantern 4, iron bars 3, wall torch 2 |
| `civic/watchtower` | `civic:watchtower` | stone, spruce, town, plains, mountain, taiga | 7 × 7, 12 |  | stone 217, wood 58, lantern 4, wall torch 3 |

- **Town hall** (`civic/town_hall`): The town hall: a long timber hall on a stone-brick base with a gabled portico at
  the front where the town bell hangs, a council table down the middle with benches, a lectern at the head, shelves of
  records and tall windows.
- **Well** (`civic/well`): A village well: a stone-floored basin inside a cobblestone rim, two log posts with torches
  carrying a little roof, and a lantern hanging from the beam over the basin. The rim is one block high, so a friend
  who ends up in the dry basin can climb out. The plan cannot pour water: the basin's "water" spots wait for a bucket.
- **Desert well** (`civic/desert_well`): The well in sandstone for desert towns: a sandstone basin inside a cut
  sandstone rim, jungle-wood posts and roof, and a lantern over the basin. Dry until someone pours water at its "water"
  spots.
- **School** (`civic/school`): The school: one bright classroom under a hipped roof, two rows of benches with desks
  facing the teacher's lectern and board at the back, bookshelves, tall windows, and a bell by the door.
- **Tavern** (`civic/tavern`): The tavern and inn: two storeys, stone below and timber above. Downstairs a taproom with
  three tables, a bar with the innkeeper's spot behind it and the kitchen smoker, and barrels of ale; upstairs four
  guest beds behind railed windows. A canopy and lanterns over the door and a chimney at the east gable.
- **Market** (`civic/market`): A market square: four stalls round a central aisle, each with posts, a slab roof striped
  with carpet, a counter with a lantern and stock barrels behind, and a flower bed with a lamp in the middle.
- **Chapel** (`civic/chapel`): A stone-brick chapel: a tall nave with buttresses and tall windows, rows of pews either
  side of an aisle, an altar table with lights at the far end and a lectern, and a round window high in the front gable.
- **Town wall** (`civic/wall`): A length of town wall: stone bricks on a cobblestone footing, buttresses either side, a
  crenellated top and torches on both faces. Segments join end to end along their length (the "join" spots).
- **Town gate** (`civic/gate`): The town gate: two solid stone towers with crenellated tops and lanterns, an arch
  between them with a portcullis of iron bars raised in its crown, and a walkway over the arch. The way through is open.
- **Watchtower** (`civic/watchtower`): A stone watchtower: a stepped plinth, a level landing inside the door, then a
  spiral stair round a central pillar (stairs all the way, no ladder, three blocks of headroom on every step) up to a
  lookout with a parapet, lights on its corners and a slab roof on posts.

```
  town hall         well              desert well
  _______________
  =======_=======
  =======#=======
  ======/o\======
  =====/#o#\=====
  ====/-----\====   _____             _____
   |o#|%*B*%|#o|    =====             =====
   |o#|!###!|#o|    | * |             | * |
   %%%%!*+*!%%%%    |* *|             |* *|
   %%%%!%D%!%%%%    %%%%%             %%%%%
   %%%%%===%%%%%    %%%%%             %%%%%
```

```
  school            tavern            market
                    _______________
       ___          ===============
       ###          ===============
      =====         ===============
     =======        ===============
    =========        |oo|oo|oo|oo|%
   ===========       |!!|!!|!!|!!|%
  =============      -----===-----%    ~~~~   ~~~~
   |oo|B#*|oo|       %%%%%*%*%%%%%%    ____ * ____
   |oo|###|oo|       %%o%o%%%o%o%%%    !  ! ! !  !
   |%%|%D%|%%|       %%%%%%D%%%%%%%    !+ !"""!+ !
   %%%%%=%%%%%       %%%%%%=%%%%%%%   \!++!"""!++!/
```

```
  chapel           town wall        town gate
      _
      #
     /o\
    /ooo\                           !_!     !_!
   /%%o%%\                          %%%     %%%
  /|-----|\                         %%%!_!_!%%%
   %%%%%%%         !_!_!_!_!        %%%%%%%%%%%
  /%%%%%%%\        =%%%=%%%=        %%%~!*!~%%%
  %%%%o%%%%        %%*%%%*%%        %%%*   *%%%
  %%%%D%%%%        %%%%%%%%%        %%%     %%%
  %%%%=%%%%        %%%%%%%%%        %%%     %%%
```

```
  watchtower
  _______
   !   !
   ! * !
   !!!!!
   %%%%%
   %%%%%
   %%%%%
   %%%%%
   %%%%%
   %%%%%
   %%D%%
  =======
```

### Farms

| Plan | Kind | Styles | Size (w × d, high) | Trade | Main materials |
|---|---|---|---|---|---|
| `farm/wheat_field` | `farm:wheat` | oak, plains, forest, taiga, savanna | 13 × 11, 3 |  | wood 85, dirt 81, lantern 9, composter 1 |
| `farm/barn` | `farm:barn` | spruce, oak, plains, taiga, forest | 14 × 11, 12 |  | wood 492, stone 66, wall torch 8, glass pane 6 |
| `farm/orchard` | `farm:orchard` | oak, birch, plains, forest | 13 × 13, 3 |  | wood 84, dirt 10, lantern 7, flower 6 |

- **Wheat field** (`farm/wheat_field`): A big wheat field of raised beds: dirt beds edged with logs, two channels for
  water so every block of soil is in reach of it, a fence round it with lantern posts, a composter and a gap to walk in.
  The plan cannot pour water: the channel's "water" spots wait for the farmer's bucket, and the "field" spots mark the
  soil's corners.
- **Barn** (`farm/barn`): A big barn with a gambrel roof: double doors, a stone footing, stalls down both sides behind
  fence rails, hay stacked in the corners, feed barrels, a water trough and the farmer's composter at the back.
- **Orchard** (`farm/orchard`): A fenced orchard: four raised beds of earth for fruit trees (saplings go at the
  "sapling" spots), flower borders, a lamp in the middle with benches round it, and a fruit stand of barrels by the
  gate.

```
  wheat field      barn             orchard
                        ____
                       /####\
                      /######\
                     /########\
                     ##########
                    /####oo####\
                    ############
                   /|---=--=---|\
                    |##|*##*|##|
  *    ***    *     |##|####|##|          *
  !!!!!!!!!!!!!     |%%|%DD%|%%|    *+"  *!*  ""*
  |-----------|     %%%%%==%%%%%    !!!!!!=!!!!!!
```

### Decoration

| Plan | Kind | Styles | Size (w × d, high) | Trade | Main materials |
|---|---|---|---|---|---|
| `decor/lamp_post` | `decor:lamp` | oak, spruce, plains, forest, taiga, town | 1 × 1, 4 |  | wood 2, stone 1, lantern 1 |
| `decor/street_lamp` | `decor:lamp` | stone, oak, dark_oak, plains, forest, town | 2 × 1, 6 |  | wood 4, stone 2, lantern 2 |
| `decor/desert_lamp` | `decor:lamp` | sandstone, desert | 1 × 1, 4 |  | sandstone 3, lantern 1 |
| `decor/bench` | `decor:bench` | oak, spruce, plains, forest, town | 4 × 2, 1 |  | wood 4, potted flower 2 |
| `decor/flower_garden` | `decor:garden` | oak, birch, plains, forest, town | 7 × 7, 3 |  | wood 21, dirt 16, stone 13, flower 12 |
| `decor/fountain` | `decor:fountain` | stone, plains, forest, town, mountain | 7 × 7, 4 |  | stone 75, lantern 1 |
| `decor/signpost` | `decor:signpost` | oak, spruce, plains, forest, taiga, town | 3 × 3, 4 |  | wood 6, stone 1, lantern 1 |

- **Lamp post** (`decor/lamp_post`): A lamp post: a cobblestone foot, a fence post and a lantern on top (a torch until
  there is iron).
- **Street lamp** (`decor/street_lamp`): A street lamp with an arm: a stone-brick foot, a tall post with a lantern on
  top and another hanging from the arm over the street (when the camp has iron).
- **Sandstone lamp** (`decor/desert_lamp`): A sandstone lamp pillar for desert streets: a sandstone wall post with a
  lantern on top.
- **Bench** (`decor/bench`): A park bench: two stair seats with trapdoor arms and a potted flower at each end behind it.
- **Flower garden** (`decor/flower_garden`): A little formal garden: four raised flower beds edged with logs, stone
  paths between them crossing at a lamp, and tall flowers in the middle of each bed.
- **Fountain** (`decor/fountain`): A stone fountain: a square basin with a raised rim and a short column in the middle
  carrying a lantern, low enough to be hung from outside the rim. The plan cannot pour water: the basin's "water" spots
  wait for a bucket (until then it is a dry basin).
- **Signpost** (`decor/signpost`): A crossroads signpost: a post on a stone foot with plank boards pointing four ways at
  two heights and a lantern on top.

```
  lamp post         street lamp       sandstone lamp
                    *
                    !!
  *                 !*                *
  !                 !                 !
  !                 !                 !
  !                 %                 %
```

```
  bench            flower garden    fountain
                                       *
                      *                !
                    ""!""           %%%%%%%
  "=="             |--_--|          %%%%%%%
```

```
  signpost
   *
  :!:
   :
   !
```

## Library: honest limits

- None of these plans has been built in a game yet. Every file was checked outside the game two ways: by the mod's own
  plan reader (all 54 load, with no errors and no warnings), and by a script that also checks that every lantern, torch,
  door, carpet, flower and plate has something to hang from or stand on, that every covered floor is lit, and that every
  marked spot can be walked to from the door. A second script builds each plan in the friends' order with a grown
  friend's real size (three blocks of air to step up, a jump of one block, a drop of three at most), the builders'
  reach and the scaffolding rules: on level ground every block of every plan can be placed and every marked spot can
  be walked to. That script is a model, not the game. How they look in the world, and how the friends cope with the
  bigger ones, is untested.
- **Water.** The plan format cannot pour water, and no job pours it yet: the wells, the fountain and the wheat field's
  channels stay dry until a player pours it at the `water` spots; a dry fountain is just a stone basin, and the wheat
  field's soil is plain dirt until a farmer tills it next to water (it then dries out unless watered by hand).
- **Saplings and beehives** are not building materials: the orchard's four tree beds wait for someone to plant them
  (`sapling` spots), and the apiary's hive stands wait for the beekeeper to put hives on them (`hive` spots). The hive
  stands are a lit campfire under a hay bale, as the game requires for calm bees; nobody can stand on the campfires.
- **The fishing hut** sits on the bank, not over the water (a building needs firm ground under all of it), and only
  works if the village places it with its deck facing the water.
- **Iron.** The smithy cannot finish without its blast furnace (five iron), nor the smith's shop without its smithing
  table, nor the mason's yard without its stonecutter.
- **Big buildings are big.** The town hall (15 × 14), tavern, barn, chapel and school each take 400 to 660 blocks and a
  large, fairly level site. The tallest (12 high: the tavern, barn, stone townhouse, taiga cottage and watchtower; the
  town hall is 11) need the builders' scaffolding for the top of the roof; the ends of their ridges are within reach of
  a full-height pillar on level ground or on the upper floor, but not much more, so a sloping site can leave one out of
  reach. A block the builders cannot reach is skipped and tried again later, and until it is placed the building does
  not count as finished.
- **Open buildings.** The smithy, the fish stall, the farm shed, the market stalls, the mason's yard and the apiary have
  no door to shut. They are lit, so nothing spawns in them, but a mob can walk in at night.
- **Windows** are open holes until the camp can make glass, as for the first ten houses. They sit two blocks above the
  ground outside, so a grown mob cannot climb in, but a baby zombie could squeeze through one. Upper-floor windows
  have a fence rail across the bottom row, so nobody can climb or fall out of one.
- **The courtyard house** is open to the sky in the middle: a spider climbing the walls could drop into the courtyard.
- **Stairs.** The two two-storey plans (the stone townhouse and the tavern) and the watchtower's spiral stair (seven
  steps round a pillar, tight turns, each entered from its low side with three blocks of headroom) depend on the
  friends' pathfinding over stairs, which has not been tried in game. The watchtower's lookout has an open stairwell
  in one corner: a friend who steps into it drops at most three blocks onto the stair.
- **Walls and gates** are 9- and 11-block segments; the village has to line them up end to end using the `join` spots.
  The gate's towers are solid stone (nothing to spawn in).
- Some furniture is only the look of it: the school's board is a patch of dark planks, the signpost's boards are
  trapdoors, and `sign` spots are left empty (friends cannot make signs).

---

# Honest limits

- Nothing here has been run in a game yet. The plans were checked by a script for their shape, palettes, two-part
  blocks and markers, and the code compiles; how they look, and how well friends cope with the scaffolding and the
  stairs of two-storey houses, is untested.
- The game tests in `BuildingGameTest` still describe the 2.x cabin and watchtower (window and block counts); they
  compile but will need their expectations updated to the new plans.
- Scaffolding is a pillar the friend jumps up, like a player; reaching the middle of a wide roof relies on a pillar
  inside the building or on its upper floor, found by a small search of the 48 columns round the block (the three
  handiest spots get a path each, the rest are checked sixteen at a time, so a block nobody can reach costs at most six
  path searches per try). Spots it cannot reach are skipped and tried again next run. A friend knocked off a pillar
  takes fall damage like anyone else (at most six blocks).
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
  Scaffolding does not depend on it (pillars keep their own record), so a full record never strands a builder, and
  beds are always recorded, so the houses built after it fills still have beds their families may sleep in. Other
  blocks placed after it fills (a house's chests, say) are not known as the friends' own.

# Landmark audit: batch D1 (Final Sea set pieces and the later canon islands)

Files: `src/main/java/com/grandline/world/landmark/FinalSeaLandmarks.java` and `LandmarkBuildersExtra.java` were
rewritten. `LandmarksD1.java` is new. It holds package-private helpers shared by both files:

- Wano work: plaster walls with beam bays, half-pitch skirt roofs with upturned corners, irimoya hip-and-gable roofs
  with gold shachihoko, chidori dormers, battered ishigaki bases, dobei walls, toro lanterns and torii.
- General work: conical spires, merlons, rails, stair flights, and doorway and hanging-lantern helpers.

Renders are in `docs/audit/renders/<id>.png`. Each shows view 0 (south-east, back), view 2 (north-west, front, the
entrance side) and floor-plan cut-aways. The bridge also has `endless_bridge_detail.png`, a close render of its last
120 blocks.

The lines below are the LandmarkCheck results for every id in this batch, from the final run. All pass in all 4
rotations, and the world-stamp check for `flower_capital_castle` matches 100%.

The block and interior counts can move a little from run to run. Each run generates a new test world, and the
foundations and clearing follow the ground at the test site.

```
PASS landmark endless_bridge — 11x400 (sea level), 29494 blocks, 6 NPCs (6 reachable), 4 chests, 7 markers, interior 30355 cells, 4 rotations in footprint
PASS landmark flower_capital_castle — 45x45, 12206 blocks, 9 NPCs (9 reachable), 6 chests, 11 markers, interior 935 cells, 4 rotations in footprint
PASS landmark future_lab — 41x41, 7776 blocks, 6 NPCs (6 reachable), 2 chests, 8 markers, interior 1562 cells, 4 rotations in footprint
PASS landmark germa_fleet — 61x61 (sea level), 16294 blocks, 8 NPCs (8 reachable), 1 chests, 9 markers, interior 6802 cells, 4 rotations in footprint
PASS landmark god_valley_ruins — 45x45, 2916 blocks, 3 NPCs (3 reachable), 1 chests, 4 markers, interior 54 cells, 4 rotations in footprint
PASS landmark kyuka_resort — 33x27, 4420 blocks, 4 NPCs (4 reachable), 2 chests, 4 markers, interior 1072 cells, 4 rotations in footprint
PASS landmark lulusia_memorial — 15x15, 484 blocks, 1 NPCs (1 reachable), 1 chests, 2 markers, interior 6 cells, 4 rotations in footprint
PASS landmark mercenary_guild_hall — 31x25, 2895 blocks, 4 NPCs (4 reachable), 3 chests, 4 markers, interior 557 cells, 4 rotations in footprint
PASS landmark mogaro_royal_gate — 27x21, 2281 blocks, 3 NPCs (3 reachable), 1 chests, 3 markers, interior 197 cells, 4 rotations in footprint
PASS landmark oykot_palace — 35x29, 4608 blocks, 4 NPCs (4 reachable), 1 chests, 4 markers, interior 908 cells, 4 rotations in footprint
PASS landmark pirate_hive — 41x41, 8731 blocks, 9 NPCs (9 reachable), 8 chests, 12 markers, interior 1640 cells, 4 rotations in footprint
PASS landmark prodence_castle — 31x31, 5732 blocks, 4 NPCs (4 reachable), 1 chests, 4 markers, interior 394 cells, 4 rotations in footprint
PASS landmark revolutionary_hq — 41x41, 10504 blocks, 12 NPCs (12 reachable), 7 chests, 14 markers, interior 1267 cells, 4 rotations in footprint
PASS landmark rommel_keep — 29x29, 4974 blocks, 4 NPCs (4 reachable), 2 chests, 4 markers, interior 440 cells, 4 rotations in footprint
PASS landmark skull_dome — 51x51, 15615 blocks, 9 NPCs (9 reachable), 14 chests, 12 markers, interior 1105 cells, 4 rotations in footprint
PASS landmark waterfall_gate — 25x23, 4374 blocks, 4 NPCs (4 reachable), 3 chests, 6 markers, interior 296 cells, 4 rotations in footprint
PASS landmark flower_capital_castle stamped into the world — 5062/5062 sampled blocks match (100.0%)
```

Rules that apply to every entry below:

- **Footprints:** no `Landmarks.Spec` (size, sea-level flag, offsets) was changed for any id.
- **Gameplay markers:** every marker kind, NPC role and loot table of the old build is kept. Where counts changed,
  the entry says so.
- **Loot containers:** every one was checked offline to have an open face. A chest also needs a clear lid. One
  regression introduced during this rebuild (Skull Dome barrels) was caught this way and fixed.
- **Test signature blocks:** the blocks that `WorldPolishTests` looks for are kept at well above their minimums.
  The counts are listed in the table at the end.

---

## flower_capital_castle

- **Host:** wano_country (WANO). Set piece, and stamp-checked in the world.
- **Type/role:** the Shogun's castle of the Flower Capital: a tenshu keep, its front court and its gatehouse.
- **Footprint:** 45x45, unchanged.

**Old problems**
- The keep was a wide, squat two-storey black-roofed hall on a low stepped plinth. It had no height and none of
  the stacked, shrinking storeys of a Japanese tenshu.
- The roofs were plain dark stair blocks with no flare.
- The walls were white concrete with black planks.
- The gatehouse was a box. The court was a lawn with two trees.

**What changed**
- **Base and tiers:**
  - A battered fieldstone ishigaki base, 8 high, has a stair cut up its face and a parapet.
  - Four storeys step in on top of it. Each is plaster with dark beam bays and lattice windows.
  - Between the storeys are kawara-tile skirt roofs with upturned corners and brackets, plus chidori gable
    dormers.
  - On top is an irimoya roof with gold shachihoko. The keep now reaches y35.
- **Gate and court:**
  - A yagura gatehouse has an irimoya roof, a stair in its west pier, and a guard room with a lookout over the gate.
  - The front court has dobei tile-capped walls, raked gravel, a koi pond, cherry trees and toro lanterns.
- **Interiors:**
  - An audience hall with a dais and golden screens, plus an armoury, the lord's chamber and a lookout floor. They
    are linked by stair flights.
  - A vault inside the base is reached through a hatch and has a tunnel to a side door.

**Kit blocks used:** white_plaster, dark_beam, lattice_window, kawara roof tiles (block, stairs and slab),
fieldstone (block, stairs and wall), red_paper_lantern, plank_table, cargo_crate, cargo_sacks.

**Markers kept**
- NPCs: guard x6, resident, noble, master.
- Other markers: treasure, arc_anchor.
- Chests: marine, blacksmith x2, house x2, treasure. These are the same tables and counts as before.

**Status:** rebuilt.

**Limitations**
- The upper storeys are small rooms, as the stepped massing requires.
- The shachihoko are gold blocks, not modelled fish.

## waterfall_gate

- **Host:** wano_country (WANO).
- **Type/role:** a gate shrine at a Wano waterfall, with a river landing.
- **Footprint:** 25x23, unchanged.

**Old problems**
- A cube of stone with a flat-topped water pool.
- The "waterfall" was a flat blue face.
- A pink box shrine on top and a free-standing red frame for a torii.
- A plain hut beside it. Nothing read as a gorge or a falls.

**What changed**
- **Cliff and falls:**
  - A stepped mossy cliff terrace rises to y10.
  - A river runs across its top and drops as a falls into a plunge pool.
  - A large vermilion torii stands in the pool, the iconic shot.
  - A cave behind the falls holds the treasure.
- **Below the cliff:**
  - A bansho guard house with a hip roof.
  - A jetty with bollards and rope.
  - A stone stair up the west side of the cliff.
- **On the terrace:**
  - A red bridge across the river and a second, smaller torii.
  - A vermilion shrine with an offering counter.
  - A cherry tree and stone lanterns.

**Kit blocks used:** white_plaster, dark_beam, lattice_window, kawara roof tiles, fieldstone, weathered_beam,
dock_planks, bollard, rope_coil, red_paper_lantern, plank_table.

**Markers kept**
- NPCs: guard x2, dockworker, priest.
- Other markers: treasure, legend.
- Chests: treasure, house, shop.

**Status:** rebuilt.

**Limitations**
- Falling water is placed as source blocks. In the world it flows down from the river, which is intended.

## skull_dome

- **Host:** onigashima (WANO). Set piece.
- **Type/role:** Onigashima's Skull Dome, the fortress and banquet hall.
- **Footprint:** 51x51, unchanged.

**Old problems**
- A white hemisphere with one stubby horn.
- The "face" was two small holes on one side, with no jaw, nose or brow.
- There was no castle on top.
- The interior was one dark room.

**What changed**
- **The skull:**
  - The massing is a true skull built as a superellipsoid: a flat face, a brow over deep eye sockets lined with
    black glass, a nose hole, and a mouth gate with bone fangs.
  - Suture lines in tuff run across the cranium.
  - Two curved horns rise from the temples, built as Bezier tubes of bone with blackstone tips.
- **On the crown:**
  - A Wano castle stands on an ishigaki base on the flattened crown. It has two tiers and an irimoya roof with
    shachihoko.
- **Front:**
  - A torii and lantern posts line the approach.
- **The Live Floor:**
  - Banquet tables, a stage and a red carpet.
  - Hanging red paper lanterns on chains.
  - A gallery ring with a front balcony that looks out through the eyes, reached by two stair flights.
  - A throne deck over the stage.
  - A vault under the stage, reached through a hatch.
- **Fix found in the final check:** the sake barrels had been set into the skull shell, where they could not be
  opened. They now stand as two stacks in the back corners of the hall.

**Kit blocks used:** fieldstone (block and stairs), white_plaster, dark_beam, lattice_window, kawara roof tiles,
red_paper_lantern.

**Markers kept**
- NPCs: pirate (now x6, was x5), pirate_captain x2, lookout.
- Other markers: treasure (now x2, one per treasure chest), arc_anchor.
- Chests: tavern x12, treasure x2, the same as before.

**Status:** rebuilt.

**Limitations**
- The horns are 1–2 blocks thick and read as stylised.
- The castle on the crown is smaller than in canon, so that it fits in the 51x51 footprint.

## revolutionary_hq

- **Host:** baltigo. Set piece.
- **Type/role:** the Revolutionary Army headquarters, a grey stone fortress.
- **Footprint:** 41x41, unchanged.

**Old problems**
- A bright white quartz and calcite box castle with a flat roof and pepper-pot turrets, reading as a toy fort.
- The courtyard was empty.
- The keep had no storeys you could read and no windows of note.

**What changed**
- **Curtain wall and gate:**
  - A grey stone-brick wall with a wall-walk and merlons.
  - Corner towers, and a twin-towered gatehouse with a portcullis.
- **Yard:**
  - Training dummies and targets, a supply depot under a lean-to, and a well.
- **Keep:**
  - Three 6-high storeys.
  - The walls have pilasters and mullion windows with sills.
  - A signal tower on the roof has a ladder, mast and flag, and there is a balcony porch.
- **Rooms:** briefing room, infirmary, kitchen and mess, armoury, quarters, and a records room with the treasure.
  They are linked by stair flights.

**Kit blocks used:** mullion_window, slate_roof_tile_slab, goods_shelf, plank_table, cargo_crate, cargo_sacks,
rope_coil.

**Markers kept**
- NPCs: revolutionary x8, scholar x2, doctor, cook.
- Other markers: treasure, arc_anchor.
- Chests: house, blacksmith x2, marine x2, tavern, treasure.

**Status:** rebuilt.

**Limitations:** none known.

## future_lab

- **Host:** egghead (FUTURE). Set piece.
- **Type/role:** Egghead's Labophase, the futuristic labs.
- **Footprint:** 41x41, unchanged.

**Old problems**
- A white stepped igloo dome on a grey grid. It had none of the egg-and-cup silhouette and no rounded pods.
- The interior was a single domed room.

**What changed**
- **The egg tower:**
  - The main massing is an egg sitting in a cup-shaped drum.
  - The drum has a ribbon of glass, sky-blue plaster and a cyan rim, with a terrace railing round its top.
- **Pods and pad:**
  - Three rounded pods: a salmon greenhouse, an ochre power pod and a sky-blue observatory.
  - A glass vestibule with iron doors.
  - A landing pad.
- **Interior:**
  - Three floors joined by flights: lab benches, a data core, the office, and a vault with levers and the treasure.

**Kit blocks used:** white_plaster, sky_plaster, salmon_plaster, ochre_plaster, goods_shelf, plank_bench,
flower_planter.

**Markers kept**
- NPCs: guard x3, scholar x3.
- Other markers: treasure, arc_anchor.
- Chests: blacksmith, treasure.

**Status:** rebuilt.

**Limitations**
- The lower half of the egg is solid below y8, so only the upper shell shows over the cup.

## pirate_hive

- **Host:** hachinosu. Set piece.
- **Type/role:** Hachinosu, the pirate island town, with the skull rock at its heart.
- **Footprint:** 41x41, unchanged.

**Old problems**
- A stepped beehive cone banded in orange honeycomb on a blackstone plain. It read as a literal beehive, not as the
  skull-shaped rock.
- There was no harbour and no town.

**What changed**
- **The skull rock:**
  - A craggy grey stone skull with deepslate crevices.
  - Eye sockets rimmed in blackstone, a nose with a magma glow, and a mouth with a honeycomb rim and dripstone fangs.
  - The honeycomb survives as a nod to the hive name.
  - A timber cannon balcony runs across the face.
- **Harbour and town:**
  - Timber shanties cling to both flanks.
  - A harbour quay with bollards and cannons.
  - Market stalls with awnings, flags, and a bounty board on the plaza.
- **Inside the rock:**
  - A tavern, a black market, and a hold under a hatch.
  - Upstairs: barracks, the captain's lair and a lookout. A Jolly Roger flies on the crown.
- **Ground:** the plaza is cobblestone with gravel and andesite, softened in the final pass.

**Kit blocks used:** weathered_planks, dark_beam, harbour_stone, dock_planks, bollard, deck_cannon, rope_coil,
red_awning, yellow_awning, red_shutter, green_shutter, lattice_window, shop_counter, goods_shelf, plank_table,
cargo_crate, cargo_sacks, slate_roof_tile_slab.

**Markers kept**
- NPCs: pirate x4, bartender, merchant (now x2, one per market), pirate_captain, lookout.
- Other markers: bounty_board, treasure, arc_anchor.
- Chests: tavern x3, shop x2, blacksmith, treasure x2.

**Status:** rebuilt.

**Limitations**
- The skull is stylised. At 41 blocks the town is a fringe around it rather than streets.

## endless_bridge

- **Host:** tequila_wolf (placed in rotation 3, partly on land).
- **Type/role:** Tequila Wolf's endless bridge on pillars, built by forced labour.
- **Footprint:** 11x400, sea level, unchanged.

**Old problems**
- A flat stone-brick deck with wall-block parapets on square columns. It used 15 block types.
- There were no arches, no pier detail and no sense of an unfinished construction site.

**What changed**
- **The bridge:**
  - A real arcade: piers with cutwaters, semicircular arches with polished andesite rings, and corbels under the
    deck.
  - A parapet with pilasters and brass-lantern posts.
- **Along the deck:**
  - Two toll gates.
  - Three labourers' huts with slate roofs.
  - Ladders down to boat landings.
- **The unfinished end:**
  - A half-built pier and scaffolding.
  - Two timber cranes with chains and hanging stone blocks.

**Kit blocks used:** brass_lantern, weathered_planks, dark_beam, lattice_window, slate_roof_tile_slab, rope_coil,
cargo_crate, cargo_sacks.

**Markers kept**
- NPCs: villager x4, jailer x2.
- Other markers: landmark.
- Chests: warehouse x4.
- Test signature: polished andesite, 3268 placed (100 needed).

**Status:** rebuilt.

**Limitations**
- At 400 long the overview render is small. Use `endless_bridge_detail.png` for close inspection.

## germa_fleet

- **Host:** germa_kingdom (SEA).
- **Type/role:** the Germa Kingdom's fleet at anchor, with the flagship castle and escort snail-ships.
- **Footprint:** 61x61, sea level, unchanged.

**Old problems**
- Flat brown deck rafts with board sails.
- The castle was a grey box with blue blob domes.
- Nothing suggested the giant snails that pull the Germa ships, and the hulls had no shape.

**What changed**
- **Hulls:** shaped with sheer and a tapered bow and stern.
- **Snails:** each ship has a huge sea snail in front: a cream body, eye stalks, and a coiled ochre shell with a dark
  spiral under a blue harness.
- **The flagship:**
  - A pale-masonry castle with round towers and blue conical roofs.
  - A throne hall that holds the treasure.
- **Escorts:** stern cabins in ochre and cream plaster, masts with blue sails and cannons.
- **Access:**
  - Gangways with rails between the ships.
  - Boarding ladders from the water, so every NPC is reachable.

**Kit blocks used:** pale_masonry, marble_column, ochre_plaster, cream_plaster, dock_planks, deck_cannon,
rope_coil, cargo_crate, cargo_sacks.

**Markers kept**
- NPCs: guard x7, noble.
- Other markers: dock.
- Chests: treasure.
- Test signature: blue concrete, 1155 placed (30 needed).

**Status:** rebuilt.

**Limitations**
- The ships are moored as one cluster inside the footprint and are not separate entities.

## rommel_keep

- **Host:** rommel_kingdom (STONE_TOWN, SNOW).
- **Type/role:** the royal keep of the snowy kingdom.
- **Footprint:** 29x29, unchanged.

**Old problems**
- A grey box wall with lumpy dark deepslate "domes" on the towers.
- No snow, though it is a winter island.
- A flat-faced keep.

**What changed**
- **Walls and towers:**
  - A fieldstone curtain wall with stone-brick-wall battlements.
  - Round towers with slate cone roofs and snow finials.
  - A gatehouse, and a wall-walk with its own stair.
- **Keep:**
  - A steep slate gable with a snowy ridge, a chimney, mullion windows and red shutters.
- **Yard:** a snowy yard with a smithy.

**Kit blocks used:** fieldstone, slate roof tiles (block, stairs and slab), mullion_window, red_shutter, dark_beam,
brass_lantern, plank_table, cargo_crate, cargo_sacks, rope_coil.

**Markers kept**
- NPCs: guard x2, noble, blacksmith.
- Chests: treasure, warehouse.
- Test signature: stone brick wall, 114 placed (20 needed).

**Status:** rebuilt.

**Limitations:** none known.

## oykot_palace

- **Host:** oykot_kingdom.
- **Type/role:** the royal palace.
- **Footprint:** 35x29, unchanged.

**Old problems**
- A salmon-coloured box under one big teal hip roof.
- A single flat facade with no centre, no portico and no wings you could read.

**What changed**
- **Palace front:**
  - A symmetrical palace range with teal hip roofs.
  - A raised central pavilion with a pediment, and a quartz-pillar portico carrying a balcony.
  - A clock tower with a belfry.
- **Gardens:** formal gardens with fountains and planters.
- **Rooms:** throne room, library and dining room, joined by flights.

**Kit blocks used:** pale_masonry, pale_masonry_wall, white_plaster, marble_column, teal roof tiles, mullion_window,
blue_shutter, brass_lantern, flower_planter, goods_shelf, plank_table.

**Markers kept**
- NPCs: noble, guard x2, scholar.
- Chests: treasure.
- Test signature: quartz pillar, 52 placed (20 needed).

**Status:** rebuilt.

**Limitations:** none known.

## god_valley_ruins

- **Host:** god_valley.
- **Type/role:** the ruins left after God Valley was destroyed.
- **Footprint:** 45x45, unchanged.

**Old problems**
- A flat crater disc of scattered magma and blackstone.
- A few free-standing pillars and toppled logs, with no trace of the architecture that was lost.

**What changed**
- **The crater:** a sunken blast crater glazed with slag. The magma is rarer now, so it reads as cooling ground
  rather than a lava field.
- **The arena:** the broken arcade of the old arena, with piers, chiseled capitals, fallen arch segments and
  seating tiers.
- **The royal hall:** a ruined hall with an altar, lectern and candles, where the scholar works.
- **Around them:** dead trees, bones and cobwebs.

**Kit blocks used:** marble_column, cargo_sacks.

**Markers kept**
- NPCs: ruin_guardian x2, scholar.
- Other markers: landmark.
- Chests: ruin.
- Test signature: chiseled stone bricks, 58 placed (8 needed).

**Status:** rebuilt.

**Limitations**
- It is a ruin on purpose, so it has little roofed space. It is not a set piece.

## kyuka_resort

- **Host:** kyuka_island (VENETIAN).
- **Type/role:** a beach resort hotel.
- **Footprint:** 33x27, unchanged.

**Old problems**
- A plain salmon box with a flat brown roof.
- A pool and some umbrellas in front.

**What changed**
- **The hotel:**
  - Three storeys of salmon plaster.
  - White-terracotta string courses, and balconies on columns.
  - A low terracotta gable roof.
- **Inside:** a lobby with a desk, a stair hall, and rooms with beds.
- **Outside:**
  - The pool deck has a parasol row and palms.
  - A thatched pool bar has a counter and lanterns.

**Kit blocks used:** salmon_plaster, white_plaster, terracotta_roof_tile_slab, thatch, thatch_slab,
mullion_window, green_shutter, red_awning, shop_counter, goods_shelf, plank_bench, plank_table, flower_planter,
white_paper_lantern.

**Markers kept**
- NPCs: innkeeper, bartender, patron x2.
- Chests: tavern x2.
- Test signature: white terracotta, 385 placed (50 needed).

**Status:** rebuilt.

**Limitations:** none known.

## lulusia_memorial

- **Host:** lulusia_kingdom (BLAST).
- **Type/role:** a memorial in the crater where the kingdom was erased.
- **Footprint:** 15x15, unchanged.

**Old problems**
- A single black wall slab and some scattered debris.

**What changed**
- A fragment of the old city wall with a broken arch and crenels.
- A stair that now climbs into the air.
- A stepped plinth bearing a polished deepslate obelisk, with candles, flowers and soul lanterns.
- Scorched, glassy ground and rubble around them.

**Kit blocks used:** cargo_sacks.

**Markers kept**
- NPCs: keeper.
- Other markers: landmark.
- Chests: ruin.
- Test signature: polished deepslate, 17 placed (8 needed).

**Status:** improved. It is deliberately small and sombre.

**Limitations:** the footprint is tiny.

## prodence_castle

- **Host:** prodence_kingdom.
- **Type/role:** the royal castle with its training yard.
- **Footprint:** 31x31, unchanged.

**Old problems**
- Grey walls with lumpy red terracotta "mushroom" roofs.
- A keep with a flat brown cap.
- The yard was empty apart from a few targets.

**What changed**
- **Walls and gate:**
  - A fieldstone curtain wall.
  - Round towers with terracotta cone roofs, and gate turrets.
- **Keep:**
  - A terracotta hip roof and mullion windows with red shutters.
  - A stair tower with a ladder, and the treasure upstairs.
- **Training yard:** archery targets, dummies and a sparring ring where the master trains.

**Kit blocks used:** fieldstone, terracotta roof tiles (block, stairs and slab), mullion_window, red_shutter,
dark_beam, brass_lantern, plank_bench, plank_table.

**Markers kept**
- NPCs: guard x2, master, noble.
- Chests: treasure.
- Test signature: target, 8 placed (3 needed).

**Status:** rebuilt.

**Limitations:** none known.

## mercenary_guild_hall

- **Host:** karai_bari_island.
- **Type/role:** the mercenary guild hall, with a circus big top beside it.
- **Footprint:** 31x25, unchanged.

**Old problems**
- A cobble-and-stone shed with a plain dark roof.
- A small red-and-white blob for a tent.
- The bounty board was not visible.

**What changed**
- **The hall:**
  - Two storeys: fieldstone below, timber framing above.
  - A slate gable roof and a chimney.
- **Bounty board:** a full-width board on the front under a lean-to canopy.
- **Rooms:**
  - A ground-floor tavern with counter and fire.
  - The guild office upstairs, with the treasure, and a bunk room.
- **The big top:**
  - Red and white stripes, a scalloped valance, and a centre pole with a pennant.
  - A sand ring inside, and a north entrance flanked by cannons.

**Kit blocks used:** fieldstone, timber_frame_cross, dark_beam, slate roof tiles, mullion_window, red_shutter,
shop_counter, goods_shelf, plank_table, plank_bench, deck_cannon, cargo_crate, cargo_sacks, rope_coil.

**Markers kept**
- NPCs: bartender, merchant, guard x2.
- Chests: tavern x2, treasure.
- Test signature: red wool, 173 placed (30 needed).

**Status:** rebuilt.

**Limitations**
- The tent cone is a stepped voxel cone, so the stripes alternate block by block near its tip.

## mogaro_royal_gate

- **Host:** mogaro_kingdom (PALACE).
- **Type/role:** the royal gate and plaza.
- **Footprint:** 27x21, unchanged.

**Old problems**
- Two white towers with blue blob domes.
- A thin bridge between them and a bare plaza.

**What changed**
- **The arch:** a triumphal arch between two square towers. The towers are pale masonry with marble corner
  columns, gold bands and teal hip roofs.
- **Over the arch:**
  - A gold keystone and crown.
  - An attic guard room, reached by a ladder in the west tower.
- **East tower:** a store.
- **The plaza:**
  - Patterned paving and a crowned obelisk.
  - Twin fountains, lamp posts, planters and benches.

**Kit blocks used:** pale_masonry, marble_column, teal roof tiles, brass_lantern, flower_planter, plank_bench,
plank_table, cargo_crate, cargo_sacks.

**Markers kept**
- NPCs: guard x2, noble.
- Chests: shop.
- Test signature: gold block, 65 placed (10 needed).

**Status:** rebuilt.

**Limitations:** none known.

---

## Test signature blocks (`WorldPolishTests`)

These are counted between y-6 and y+34 from the placement point, in the final dumps.

| Landmark | Block | Placed | Needed |
|---|---|---|---|
| endless_bridge | polished andesite | 3268 | 100 |
| germa_fleet | blue concrete | 1155 | 30 |
| rommel_keep | stone brick wall | 114 | 20 |
| oykot_palace | quartz pillar | 52 | 20 |
| god_valley_ruins | chiseled stone bricks | 58 | 8 |
| kyuka_resort | white terracotta | 385 | 50 |
| lulusia_memorial | polished deepslate | 17 | 8 |
| prodence_castle | target | 8 | 3 |
| mercenary_guild_hall | red wool | 173 | 30 |
| mogaro_royal_gate | gold block | 65 | 10 |

## Outside this batch

The self-test reports `FAIL landmark sword_dojo`: 64 writes outside the footprint, in every rotation, at local
y5. That landmark is not in D1.

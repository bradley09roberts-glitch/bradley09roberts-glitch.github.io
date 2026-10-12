# Landmark audit, batch C

This batch covers the set pieces of the second half of Paradise (Water 7, Enies Lobby, Thriller Bark, Kuraigana, Sabaody,
Amazon Lily) and the first New World islands (Punk Hazard, Dressrosa, Green Bit, Zou, Whole Cake Island). The builders
live in `GrandLineLandmarks.java` and `NewWorldLandmarks.java`. The shared helpers are in the new `LandmarksC.java`:

- storeyed wall boxes with a plinth, posts and floor bands;
- window rows and entrances;
- straight flights and square spiral staircases;
- conical and square spires and eave rings;
- a 3×5 pixel font for painted numbers, flags and bubbles.

Every landmark was rebuilt from scratch. No footprint (`Landmarks.Spec`) changed, so `CanonIslands` needs no change.

The **Check** line for each landmark quotes `run/selftest-phaselandmarks.txt` (GL_SELFTEST=landmarks). Renders are in
`renders/<id>.png`: view 0 (from the south-east), view 2 (from the north-west), then the floor plans.

---

## galley_la_docks

**Host:** `water_7` (VENETIAN). **Role:** Galley-La Company's Dock One, the shipyard plus the company headquarters.
**Footprint:** 45×41 at sea level (y=0 is the sea surface, the quay deck is at y=1). Unchanged.

**Problems in the old build:**
- A grey andesite slab with a plain terracotta hip-roofed box: a white terracotta wall with posts, and no Water 7
  colour or plaster.
- Glass panes with no frames.
- A gantry made of two posts and a beam.
- A dry dock with a hull outline, but no dock gate, no number and no cranes.
- The lumber yard was a flat roof on posts.
- Inside, three identical floors with ladder-free stair holes and little storytelling.

**What changed:**
- **Massing:**
  - A harbour-stone quay is cut by the launch channel and a stepped slipway that runs down into the sea.
  - The numbered dock gate is the focal point: two pale-masonry piers, a round arch and a crest with a blue plaque
    carrying a big white "1", under a stepped pediment and a company flag. Its plank leaves are swung open against the
    channel walls.
  - Two gantry cranes: one over the slipway lifts a rib, one over the channel holds a crate on the hook.
  - The HQ is a three-storey salmon-stucco range with a four-storey centre pavilion. The pavilion has a hip roof, a
    flag, two marble columns carrying the third-floor balcony, and blue banners as the company sign band.
- **Roofs:** low-pitch terracotta gable on the range, hip on the pavilion, smoking chimneys, a terracotta gable on the
  open-fronted sawmill shed.
- **Façade:** white plaster quoins and floor bands, a harbour-stone plinth, green shutters and planters on every
  elevation (sides and back included), a portico lantern.
- **Shipyard:**
  - A hull on the stocks: planked forward and only ribs aft, on cradles and shores, with scaffolding towers.
  - Bollards, rope coils and cargo along the quay edges.
  - Ladders out of the water and lamp posts.
- **Interiors:**
  - Reception counter and benches.
  - The drafting hall (cartography tables, plan shelves).
  - A model workshop.
  - Offices, an archive and a board room.
  - The president's office. A bookcase door leads to the plans vault.
  - The lookout loft with a bell.
  - Three straight flights up the back of the pavilion, with railings.

**Kit used:** salmon and white plaster, harbour stone, wall and stairs, pale masonry, marble columns, terracotta roof
tiles, stairs and slabs, oak, dark and weathered beams, weathered and dock planks, green shutters, flower planters,
bollards, rope coils, cargo crates and sacks, shop counters, plank tables and benches, brass lanterns.

**Gameplay kept:**
- Markers: `ship_spawn` (in the channel), `dock`, `legend` (story galley_la_docks), `treasure`.
- NPCs: 4 shipwright, 2 dockworker, merchant, noble, guard.
- Containers:
  - chests: LOOT_SMITH, LOOT_SHOP, LOOT_TREASURE (vault) and LOOT_WAREHOUSE;
  - barrels: two LOOT_WAREHOUSE.

**Status:** rebuilt. **Limits:** the slipway hull is a static model; there are no letters on the sign band (banners

**Check:** PASS. 45x41 (sea level), 23181 blocks, 9 NPCs (9 reachable), 6 chests, 13 markers, interior 1402 cells, 4 rotations in footprint.
stand in for it).

## gates_of_justice

**Host:** `enies_lobby` (PALACE). **Role:** the colossal sea gate.
**Footprint:** 47×17 at sea level. Unchanged.

**Problems in the old build:**
- Two crenellated white quartz boxes with black bands, joined by a plain slab.
- The leaves were flat deepslate with white iron-block dots.
- The control room was reached by a 35-block ladder.
- The other floors were empty.
- It read as a generic double tower.

**What changed:**
- **Massing:**
  - Two pale-masonry piers on harbour-stone footings that rise out of the sea.
  - Marble pilasters, harbour-stone string courses at every floor, slit windows, a cornice and pyramid caps with gold
    finials.
  - The lintel carries the World Government emblem on both faces: a blue disc, a gold ring and a gold star cross.
  - A stepped crown with a gold finial sits on top.
- **Leaves:** deepslate-tile leaves in polished-blackstone frames with rails, horizontal iron chain straps, studs on
  the middle rail and gold knockers with chains. The leaves stand almost closed over the drag current, which is bubble
  columns over magma.
- **Interior:**
  - Each pier has a plank landing stage on pilings, with bollards and lamps, and an iron door with buttons.
  - Five floors, seven blocks apart, joined by straight stone flights along alternating walls, with iron-bar railings.
  - West pier: guard room, armoury, a winch room with a great drum and chains, records room, and the control room
    (levers, gear, windows to the sea).
  - East pier: barracks, stores, mess, signal room with a bell, lookout.

**Kit used:** pale masonry and stairs, harbour stone, marble columns, dock planks, weathered beams, bollards, rope
coils, brass lanterns.

**Gameplay kept:**
- NPCs: 4 marine, marine_officer.
- Containers: two LOOT_MARINE chests and two LOOT_WAREHOUSE barrels (one more barrel than before, in the west guard
  room).
- Marker: `arc_anchor` (gates_of_justice).

**Status:** rebuilt. **Limits:** the leaves are a static "almost closed" pose; the emblem is a stylised disc rather

**Check:** PASS. 47x17 (sea level), 17481 blocks, 5 NPCs (5 reachable), 4 chests, 6 markers, interior 1597 cells, 4 rotations in footprint.
than the exact World Government glyph.

## haunted_mansion

**Host:** `thriller_bark` (GOTHIC). **Role:** the master's mansion behind a graveyard.
**Footprint:** 45×45. Unchanged.

**Problems in the old build:**
- One big deepslate box under a single blackstone gable with a small cone spire.
- Windows were glass dots.
- The graveyard was a grid of identical walls.
- No turrets, no gothic silhouette, no porch.

**What changed:**
- **Massing:** a symmetrical composition.
  - A three-storey centre under the tallest steep slate gable, with a rose window as the focal point.
  - Two two-storey wings with their own front gables and small gable windows.
  - Round corner turrets with needle spires and lightning rods.
  - A gabled porch on dark beams.
  - Behind, a tall bell tower: soul-lantern pinnacles, belfry openings, a bell and a steep square slate spire.
- **Façade:** cobbled-deepslate plinth, dark beam posts and floor bands, tall purple windows with deepslate sills on
  every side, wall lanterns, smoking chimneys.
- **Grounds:**
  - Wrought-iron fence with blackstone piers, soul lanterns and a skull-topped gate arch.
  - A cobbled path and forecourt.
  - A graveyard of varied graves: headstones, crosses, mounds with candles, obelisks, slabs.
  - Dead trees with cobwebs.
  - A gothic mausoleum with a steep slate roof, a bier and candles.
- **Interiors:**
  - Entrance hall with a three-wide grand stair and railed gallery.
  - The surgeon's laboratory (operating tables, specimen jars, an ice wall hiding the frozen vault).
  - Banquet hall.
  - Library.
  - The master's chambers.
  - Wedding chapel with pews and altar.
  - The shadow throne hall under the roof.
  - A spiral stair up the bell tower to the belfry.

**Kit used:** slate roof tiles and stairs, dark beams, plank benches.

**Gameplay kept:**
- NPCs: 6 ruin_guardian (one more than before, now in the mausoleum), 2 pirate, pirate_captain.
- Containers:
  - chests: LOOT_RUIN, plus LOOT_TREASURE ×2 (frozen vault and chambers);
  - barrel: LOOT_TAVERN.
- Markers: `treasure`, `arc_anchor` (ghost_ship_island).

**Status:** rebuilt. **Limits:** the tower and turrets are solid shells apart from the stair tower.

**Check:** PASS. 45x45, 11613 blocks, 9 NPCs (9 reachable), 4 chests, 11 markers, interior 1480 cells, 4 rotations in footprint.

## ruined_castle

**Host:** `kuraigana_island` (DARK theme). **Role:** the swordsman's ruined castle.
**Footprint:** 41×41. Unchanged.

**Problems in the old build:**
- A flat-roofed two-storey box with a crenellated stub.
- Random broken walls.
- A cylinder tower with a diagonal cut.
- A ladder hatch to the cellar.
- No roof and no recognisable castle silhouette.

**What changed:**
- **Massing:**
  - The keep has three storeys of five under a steep slate gable whose east end has fallen in: a hole with bare
    rafters and a ridge beam over rubble on the top floor.
  - A tall round tower with a spiral stair to a windowed lookout under a slate spire.
  - A slender square tower with a steep spire.
  - A half-fallen gatehouse (one crenellated tower, one broken) with a portcullis.
  - The broken round tower and a ruined curtain wall with breaches and merlons where it still stands tall.
- **Façade:** deepslate-brick posts and floor bands, ruin-mottled walls, barred windows (some broken open), a carved
  lintel, a black banner, wall lanterns.
- **Interiors:**
  - Great hall with a long table, the master's seat and a brick hearth.
  - The master's rooms (bed, chest, a sword cabinet, desk, wine).
  - The library under the broken roof.
  - A straight stair down to the wine cellar (eleven loot barrels). Its cracked deepslate wall hides the old armoury.
- **Yard:** dummies with arms, slashed posts, a target, a grindstone and a bench. Dark oaks and dead trees grow through
  the ruins.

**Kit used:** slate roof tiles and stairs, plank benches.

**Gameplay kept:**
- NPCs: 4 ruin_guardian, master.
- Containers: chests LOOT_HOUSE and LOOT_TREASURE; LOOT_TAVERN barrels in the cellar.
- Markers: `treasure`, `legend` (ruined_castle).

**Status:** rebuilt. **Limits:** the cellar holds 11 tavern barrels (the old build had 7).

**Check:** PASS. 41x41, 8215 blocks, 5 NPCs (5 reachable), 13 chests, 7 markers, interior 760 cells, 4 rotations in footprint.

## mangrove_grove

**Host:** `sabaody_archipelago` (SWAMP). **Role:** Grove 13, a Yarukiman mangrove with its bar, coating yard and
stall.
**Footprint:** 37×37. Unchanged.

**Problems in the old build:**
- A thin straight log tube with a white stripe.
- A 30-block ladder.
- A square shed.
- No grove number.
- Roots were one-block lines.

**What changed:**
- **Massing:**
  - A flared trunk with a muddy-root foot, hollow inside, with glowing knots.
  - The grove number "13" painted in white on a flattened panel of its north face.
  - Eight arching two-block roots.
  - A wide mangrove canopy with branches.
  - A lookout balcony ring under the canopy, reached by a square spiral stair inside the trunk.
  - A framed door with a lamp.
- **Buildings:**
  - The rip-off bar has white plaster, oak frame, a teal gable, shutters and planters, a smoking chimney, a green
    awning and a hanging barrel sign. Inside: counter, tavern barrels, shelves, stools, a table and a patio.
  - The coating yard on dock planks: a ship model under a glass bubble film, resin vats, rope.
  - A bubble-souvenir stall with blue awnings.
- **Bubbles:** resin bubbles drift at many heights.
- **Cache:** a honey-sealed cache in the trunk wall.

**Kit used:** white plaster, oak beams, fieldstone, teal roof tiles, green shutters, flower planters, green and blue
awnings, shop counters, goods shelves, plank tables and benches, dock planks, rope coil, brass lantern.

**Gameplay kept:**
- NPCs: 2 resident, bartender, patron, shipwright, merchant.
- Containers:
  - chests: LOOT_TREASURE (cache) and LOOT_WAREHOUSE;
  - barrels: three LOOT_TAVERN and one LOOT_SHOP.
- Markers: `treasure`, `quest_board`, `legend` (mangrove_grove).

**Status:** rebuilt.

**Check:** PASS. 37x37, 7382 blocks, 6 NPCs (6 reachable), 6 chests, 9 markers, interior 480 cells, 4 rotations in footprint.

## auction_house

**Host:** `sabaody_archipelago`. **Role:** the Human Auctioning House.
**Footprint:** 33×35. Unchanged.

**Problems in the old build:**
- A red terracotta box with a hollow red-concrete half-ellipsoid on top and a flat quartz portico.
- The ladders up to the boxes were hidden behind the stage wings.

**What changed:**
- **Massing:** a cream hall with ochre pilasters, a harbour-stone base and a gold cornice, under a red-and-white
  striped big-top roof (terracotta and white plaster stairs radiating to a gold peak and flag) with a scalloped red
  awning valance. Bubbles drift past the roof.
- **Portico:**
  - Two steps.
  - Four marble columns on dressed sandstone.
  - An entablature.
  - An illuminated marquee: dark planks, a gold border, a red band and lamps.
- **Interiors:**
  - A raked auditorium with rows of seats and a carpeted aisle.
  - Nobles' side boxes reached by stairs from the foyer.
  - Chandeliers on chains from the tent.
  - The stage: footlights, curtain, wings, valance, podium and the lots on show.
  - Backstage: the barred cells and the strong room (iron door, three levers, one real).

**Kit used:** cream, ochre and white plaster, harbour stone, terracotta roof tiles and stairs, white plaster stairs,
marble columns, dressed sandstone, red awnings, brass lanterns.

**Gameplay kept:**
- NPCs: 3 guard, 3 patron, merchant, 2 noble.
- Markers: 3 `cell`, `treasure`, `arc_anchor` (auction_house; moved onto the portico).
- Containers: 2 LOOT_TREASURE chests.

**Status:** rebuilt.

**Check:** PASS. 33x35, 5749 blocks, 9 NPCs (9 reachable), 2 chests, 14 markers, interior 595 cells, 4 rotations in footprint.

## kuja_palace

**Host:** `amazon_lily` (TROPICAL). **Role:** the warrior empress's palace with its arena.
**Footprint:** 45×45. Unchanged.

**Problems in the old build:**
- One sandstone box under a warped-plank flared roof.
- A flat sand disc arena.
- Snake statues as small blobs.

**What changed:**
- **Arena:** a round fighting arena in front: sand ring, barrier, two rows of stands, an outer wall, red-column lantern
  posts and a gate frame with a teal roof.
- **Approach:**
  - A grand stair flanked by serpent statues leads up to a balustraded terrace with lantern piers, palms and planters.
  - The throne hall stands on the terrace: a veranda of red (mangrove) columns with an orange architrave, cream walls
    with lattice windows.
  - The roof is two-tier and flared, in teal tiles: a lower skirt, a clerestory with lattice windows, and the upper
    flared hip.
  - Glazed serpent heads sit on the flared tips and an orange ridge on top.
  - Two flared-roof pavilions flank the hall.
- **Interiors:**
  - Serpent-scale floor, red columns and a purple carpet.
  - The empress's dais and throne between serpent statues.
  - Paper lanterns from a ceiling under the clerestory.
  - The bath and bedchamber in the west pavilion.
  - The kitchen and the three-lever treasury in the east pavilion.

**Kit used:** teal roof tiles, stairs and slabs, cream plaster, dressed sandstone (and walls and stairs), red paper
lanterns, lattice windows, flower planters, plank tables, cargo sacks.

**Gameplay kept:**
- NPCs: 5 kuja_warrior, snakeneck_lancer, elder, cook.
- Containers: LOOT_TAVERN barrel; 2 LOOT_TREASURE chests.
- Markers: `treasure`, `arc_anchor` (warrior_isle).

**Status:** rebuilt.

**Check:** PASS. 45x45, 9172 blocks, 8 NPCs (8 reachable), 3 chests, 10 markers, interior 788 cells, 4 rotations in footprint.

## abandoned_lab

**Host:** `punk_hazard` (VOLCANIC/ICE). **Role:** the government research facility split by fire and ice.
**Footprint:** 41×35. Unchanged.

**Problems in the old build:**
- A white concrete box with a cyan stripe.
- Random black and ice speckle over every wall: noisy.
- No silhouette beyond the box.

**What changed:**
- **Massing:**
  - A two-storey lab block in white and sky plaster on a light-concrete frame, with cyan floor bands.
  - Ribbon windows on all four sides.
  - A flat roof with parapet and cornice, a glass skylight dome, vents, an antenna and a water tank.
  - An entrance wing under a cantilevered canopy on pillars.
  - A domed gas tank with a ladder and a pipe to the block.
  - Two banded industrial chimneys with smoke and pipes.
- **Damage as storytelling:**
  - Soot spreads from a burst north-west corner (a breach, rubble, fires, magma cracks, burnt trees).
  - Frost creeps up the east walls, with icicles under the cornice, ice spikes and snow on the roof.
- **Interiors:**
  - Lobby with a counter.
  - A corridor with stairs.
  - The burnt lab.
  - The sealed green-glass gas lab with an iron door.
  - The cryo lab.
  - Upstairs: archive, director's office, staff bunks.

**Kit used:** white and sky plaster (FUTURE material set), shop counters, plank benches and tables, cargo crate.

**Gameplay kept:**
- NPCs: 2 scholar, 3 pirate, pirate_captain.
- Containers:
  - chests: LOOT_RUIN and LOOT_TREASURE;
  - barrels: LOOT_WAREHOUSE and LOOT_HOUSE.
- Markers: `treasure`, `arc_anchor` (fire_and_ice).

**Status:** rebuilt.

**Check:** PASS. 41x35, 5752 blocks, 6 NPCs (6 reachable), 4 chests, 8 markers, interior 999 cells, 4 rotations in footprint.

## corrida_colosseum

**Host:** `dressrosa`. **Role:** the Corrida Colosseum.
**Footprint:** 51×51. Unchanged.

**Problems in the old build:**
- A sandstone drum with terracotta stripes and small square holes.
- Wool-block "flags".
- No gatehouse, no moat and no royal box.

**What changed:**
- **Massing:**
  - A salmon-stucco ring wall in three tiers: open arches with dressed-sandstone surrounds, recessed blind arches with
    pilasters, small windows.
  - A cornice and parapet with twelve red, pink and yellow flags.
  - A gatehouse on the north: a big arch, a balcony with iron rail, banners, mullioned windows, a terracotta hip
    roof and a flag.
- **Inside:**
  - Stands rise one row per ring, with stair seats in alternating sandstone and salmon and aisles every 30°.
  - A vaulted concourse under the stands, with four tunnels to the front walkway.
  - The royal box with marble columns, a canopy, awnings, planters and the throne.
  - The stone fighting ring inside its water moat (canon: fall in and you lose).
- **Underground:** gladiators' hall, a ramp up into the ring, cells, the champion's corner and the prize vault.
- **Plaza:** a striped ticket booth, lamps and planters.

**Kit used:** salmon and cream plaster (and salmon stairs and slabs), dressed sandstone (and stairs and slabs), harbour
stone, marble columns, terracotta roof tiles, mullioned windows, red awnings, shop counter, goods shelves, flower
planters, plank table, brass lanterns.

**Gameplay kept:**
- NPCs: 3 patron (on the stands), 3 guard, lunarian_warrior, buccaneer_brawler, merchant, 2 pirate, master.
- Markers: 4 `cell`, `treasure`, `arc_anchor` (colosseum_tournament).
- Containers:
  - barrels: LOOT_SHOP and LOOT_SMITH;
  - chests: LOOT_HOUSE and two LOOT_TREASURE.

**Status:** rebuilt.

**Check:** PASS. 51x51, 15810 blocks, 12 NPCs (12 reachable), 5 chests, 18 markers, interior 937 cells, 4 rotations in footprint.

## royal_plateau

**Host:** `dressrosa`. **Role:** the royal palace on its plateau, with the hidden factory beneath.
**Footprint:** 45×45. Unchanged.

**Problems in the old build:**
- A white terracotta box under a brick hip.
- Two cylinder towers with terracotta cones.
- A ladder-only lift.
- The cliff stair had no balustrade.

**What changed:**
- **Massing:**
  - A layered rock plateau (granite strata), with greenery draping down the cliff faces.
  - The palace in white stucco with salmon quoins and bands, under terracotta hip roofs.
  - Four round corner towers with banded drums, mullioned windows and tall cone roofs with gold finials.
  - A three-storey entrance pavilion with a gable, a flag and the king's balcony.
- **Approach:** the lift tower has a tiled roof, a cabin and chains. A balustraded cliff stair climbs to a landing.
  The forecourt has flower beds and lamps.
- **Plaza:** a fountain, a toy stall with a yellow awning, planters and flower beds.
- **Interiors:**
  - Throne room.
  - Guard hall.
  - Dining hall and kitchen.
  - Royal suite and library.
  - The balcony room.
  - The secret toy factory hollowed out of the rock: rails, furnaces, hoppers and crates, reached by a hatch behind
    the throne or a cracked door in the cliff.

**Kit used:** white and salmon plaster, harbour stone, dressed sandstone (and stairs), terracotta roof tiles and
stairs, green shutters, flower planters, mullioned windows, yellow awning, shop counters, plank table, cargo crates,
brass lanterns.

**Gameplay kept:**
- NPCs: merchant, child, resident, 3 guard, noble, cook, 2 pirate, pirate_captain.
- Containers:
  - chests: LOOT_MARINE, LOOT_HOUSE and LOOT_TREASURE;
  - barrels: LOOT_TAVERN and four LOOT_WAREHOUSE.
- Markers: `treasure`, `arc_anchor` (toy_kingdom).

**Status:** rebuilt. **Limits:** the lift is a ladder in an iron frame (a working lift needs redstone); the hatch to

**Check:** PASS. 45x45, 23606 blocks, 11 NPCs (11 reachable), 8 chests, 13 markers, interior 1683 cells, 4 rotations in footprint.
the factory is a ladder by design (a secret shaft).

## tontatta_village

**Host:** `green_bit` (TROPICAL). **Role:** the tiny people's kingdom at the roots of a great tree.
**Footprint:** 29×29. Unchanged.

**Problems in the old build:**
- Cottages were 2×2 lumps of planks with a terracotta or mushroom slab.
- A cylinder trunk.
- A ladder hatch to the store.

**What changed:**
- **Tree and cottages:**
  - A flared, buttressed trunk with six roots and a canopy of oak and flowering-azalea leaves on branches.
  - Thirteen doll's-house cottages, oriented to their paths, in mixed plaster colours on a fieldstone sill. Each has a
    terracotta, teal or sky-tile mini gable, a shutter door, a window, a chimney with a candle and a flower box. Some
    are built into the roots.
  - Candle-lit fence posts and packed-mud paths.
  - A stream with plank-slab bridges, a fenced vegetable patch and the toadstool house.
- **Interiors:** the royal hall in the trunk has a little throne, tables with candles, banners and a lectern. A
  straight stair leads down to the storehouse.

**Kit used:** cream, ochre, mint, salmon, sky and white plaster, fieldstone, terracotta, teal and sky roof tile stairs,
terracotta slabs, weathered plank slabs, flower planters, plank tables, cargo sacks.

**Gameplay kept:**
- NPCs: elder, 2 guard, child, farmer, resident.
- Containers: LOOT_TREASURE chest; two LOOT_HOUSE barrels.
- Markers: `treasure`, `legend` (dwarf_kingdom).

**Status:** rebuilt.

**Check:** PASS. 29x29, 4401 blocks, 6 NPCs (6 reachable), 3 chests, 8 markers, interior 130 cells, 4 rotations in footprint.

## giant_legs

**Host:** `zou` (GIANT_FOREST). **Role:** the great elephant's legs, with the gate of the Mokomo village.
**Footprint:** 41×41. Unchanged.

**Problems in the old build:**
- Plain columns in pinkish light-grey terracotta striped every three blocks.
- A 32-block ladder.
- A plank box lodge with a slab roof.

**What changed:**
- **Legs:** grey andesite columns with tuff creases and speckle, broad feet and white calcite toenails, merged into an
  arched belly. The back has a mossy walkway with rails and paper lanterns.
- **Gate:** a palisade with watch posts, a thatched lintel, flags and a lantern-lined path.
- **Stair:** a spiral stair inside the hollow west leg rises through the belly onto the back. The hidden alcove is
  kept.
- **Lodge:** the lord's lodge in a great tree has weathered planks on jungle-log posts, a thatch gable with brackets,
  lattice windows, paper lanterns and a stair up to the platform.
- **Mink houses:** the two mink houses now use the kit-based house design.

**Kit used:** thatch (and stairs and slabs), weathered planks, lattice windows, red paper lanterns, plank table.

**Gameplay kept:**
- NPCs: resident, 2 guard, elder, child, plus the house NPCs.
- Containers: chests LOOT_TREASURE and LOOT_HOUSE, plus the houses' containers.
- Markers: `treasure`, `legend` (great_legs).

**Status:** rebuilt.

**Check:** PASS. 41x41, 7691 blocks, 7 NPCs (7 reachable), 5 chests, 9 markers, interior 504 cells, 4 rotations in footprint.

## cake_chateau

**Host:** `whole_cake_island` (CANDY). **Role:** the cake-shaped castle.
**Footprint:** 51×51. Unchanged.

**Problems in the old build:**
- Three uniform tiers of red terracotta, sandstone and concrete stripes.
- Five white posts as "candles".
- No castle elements.
- A ladder to the roof.

**What changed:**
- **Tiers and decoration:**
  - Three flavoured tiers: vanilla sponge with strawberry jam, chocolate with cream, and strawberry with mint.
  - Cream drips of varied length, piped cream rims with strawberry dollops, and pink windows.
  - Six pastel turrets on the first terrace, with sky and teal needle roofs and gold finials (one over the door).
  - Five giant lit candles.
  - A pink keep with white bands under a sky-tile spire and flag.
- **Entrance and garden:** a candy-cane portico with a frosting arch and drips. The candy garden has lollipop trees,
  candy canes, cookies, gumdrops and a cookie path.
- **Interiors:**
  - Banquet hall with candy pillars and kitchen.
  - The throne room with its mirror vault.
  - The private chamber.
  - Stairs all the way up to the keep (the ladder is gone).

**Kit used:** salmon, cream, white and mint plaster (and white plaster slabs), sky and teal roof tiles and stairs,
plank tables, goods shelf, cargo sacks, flower planter.

**Gameplay kept:**
- NPCs: resident, child, waiter, 3 pirate, 2 cook, pirate_captain.
- Containers: three LOOT_TAVERN barrels; chests LOOT_TREASURE and LOOT_HOUSE.
- Markers: `treasure`, `arc_anchor` (tea_party).

**Status:** rebuilt.

**Check:** PASS. 51x51, 9063 blocks, 9 NPCs (9 reachable), 5 chests, 11 markers, interior 1474 cells, 4 rotations in footprint.

---

## Verification summary

- All 13 landmarks of the batch PASS `LandmarkCheck`. That covers every rotation inside the footprint, determinism,
  terrain-independent markers, known roles, standable NPCs, and for these canon set pieces: a loot chest, every NPC
  reachable on foot from outside, and an enterable roofed interior.
- `galley_la_docks` also passes the stamped-into-the-world sample: 100% of the sampled blocks match.
- One failure in the same run is not in this batch: `sword_dojo` (in `LandmarkBuilders`) writes 64 blocks outside
  its footprint in every rotation. Its WANO `Buildings.house` now has a flared roof that overhangs by 2, while the
  builder insets the house by only 1.


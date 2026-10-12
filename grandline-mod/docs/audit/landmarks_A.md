# Landmark audit, batch A

Batch A covers the 22 builders that were in `world/landmark/LandmarkBuilders.java`, plus `port_beacon`, which is an alias of the lighthouse. Every one of them was rebuilt.

**Where the code lives**

| File | Contents |
|---|---|
| `LandmarkBuilders.java` | The small and generic pieces, and thin delegations to the files below. The `placeholder` fallback is unchanged. |
| `LandmarkBuildersA.java` | Shared helpers and the two Marine pieces. |
| `LandmarkBuildersA2.java` | The Baratie, Arlong Park and Drum Castle. |
| `LandmarkBuildersA3.java` | Water 7, the Tower of Justice, Impel Down and Mary Geoise. |
| `LandmarkBuildersA4.java` | The two great trees and Laugh Tale. |

The shared helpers in `LandmarkBuildersA.java` are:
- `switchback`: a two-lane stair, four steps per storey, that cuts its own floor openings;
- `spiral`: a square spiral stair on a ring around a core;
- `skirt`: eave skirts for tiered roofs;
- `cornice`;
- `textNorthWall` / `textFloor`: a 3x5 pixel font for signs;
- `pixels`: crests and emblems.

**What did not change**
- No `Landmarks.Spec` (footprint) was changed.
- Every marker kind, NPC role and loot table is the same as before. Positions moved where the new layouts needed it.
- Marker positions never depend on terrain reads.

**Self-test:** all 23 ids PASS in `LandmarkCheck`. In the same run every other registered landmark passed too: 83 PASS, 0 FAIL. The result lines are at the end of this file.

**Renders:** final renders are in `renders/<id>.png`. Each shows view 0 (from the south-east) and view 2 (from the north-west, the entrance side), plus floor plans. The BEFORE images were made with the old renderer, which had the slab and stair culling bug, so their roofs may show false gaps.

## Facts found along the way that affect several pieces

**Mary Geoise sits at a fixed y of 301, and the world ends at y 319.**
- `ChunkSink` silently drops anything at y 320 or above.
- The old Mary Geoise had its gold dome at local y 25–33 and its towers at 31–33, so in the real world they were cut off. Only the stumps of the walls were ever generated.
- The new palace keeps every block at local y 18 or below.

**`Arch.dome` puts one extra shell block at r + 1 and its tip at r + 2.** Anything with a height cap has to budget for both.

**Sea-level pieces: local y 0 is the water surface block (sea level 63).**
- Decks therefore sit at y 1.
- Boarding from the water needs a block at y 0, which is what the Baratie's landing stage is.

## Marine Headquarters (`marine_headquarters`)

- **Host:** Marineford, and New Marineford (MARINE style, plateau).
- **Footprint:** 41x41, unchanged.
- **Role:** the canon seat of the Marines. It has the bounty board, the barracks, and the vice-admiral and admiral NPCs.

**Old problems**
- Four stacked white-concrete wall rings with a blue stripe every six blocks. There were no real floors except a slab at each tier top.
- A single 52-high ladder shaft.
- A light-blue concrete blob dome.
- A wall-block fence as the "plaza".
- No MARINE lettering, no bay, no roofs, no interiors.

**Changes**

*Massing and roofs*
- Four set-back tiers of two storeys each, then an open lookout pavilion:
  - x 4–36 at y 0–8;
  - x 7–33 at y 8–16;
  - x 11–29 at y 16–24;
  - x 15–25 at y 24–32.
- Each tier has a pale masonry or white plaster wall, blue concrete bands at every floor line, pale masonry quoins, and a two-course teal eave skirt that doubles as a terrace parapet. Together they give the canon tiered white fortress with blue bands and blue-green roofs.
- The crown is a marble-column pavilion with a teal hip roof, the bell, a flag mast with blue and white Marine banners, and a lightning rod.

*Signs*
- "MARINE" runs in blue across the second tier's front wall.
- It is also laid into a framed white plaza in front, 24 blocks wide. Both read correctly from the bay approach.

*Bay*
- The north edge is a harbour-stone quay with bollards.
- The encircling bay walls run along the west, east and south sides: pale masonry with a blue band, a wall walk with merlons, deck cannons on top, and corner bastions with cannons and lamps.
- Flag masts and lamp posts stand on the plaza.

*Entrance*
- A seven-high marble colonnade portico carries the first terrace, which is railed and connects to the second-tier door.
- Three dark-oak doors lead in, with the bounty board sign and a banner beside them.

*Interiors*
- An eight-flight switchback stair core climbs every floor.
- Ground floor:
  - a double-height entrance hall with marble columns, counters, a lectern and a gallery;
  - barracks with bunks;
  - the bounty office with counters, a cartography table and shelves;
  - the mess with long tables and a kitchen;
  - the armoury with cannons, cargo, an anvil and a grindstone.
- Upper base floor: officers' office, records room, dormitory and chart room.
- Second tier: the war room with its long table (vice-admiral), the quartermaster's store and the communications room, which has counters and note blocks; above them, the records library.
- Third tier: officers' quarters, then a lounge.
- Fourth tier: the strategy salon, then the Fleet Admiral's office, which holds the admiral and the chest.

**Kit blocks:** PALE_MASONRY (+ stairs, slab, wall), WHITE_PLASTER, HARBOUR_STONE, MARBLE_COLUMN, TEAL_ROOF_TILES (+ stairs, slab), BLUE_SHUTTER, MULLION_WINDOW, BOLLARD, DECK_CANNON, SHOP_COUNTER, PLANK_TABLE, GOODS_SHELF, BRASS_LANTERN, CARGO_CRATE and CARGO_SACKS (via cargo).

**Markers kept:** npc `marine` ×3 (plaza), `vice_admiral` (war room, y 9), `admiral` (top office, y 29); `bounty_board` (beside the doors); chest `chests/marine`.

**Status:** rebuilt.

**Limitations**
- The pavilion roof is a hip, not a pagoda finial.
- The second-tier "MARINE" frieze is partly behind the first skirt in steep views.

## Marine branch base (`marine_base`)

- **Host:** Shells Town and Loguetown.
- **Footprint:** 27x27, unchanged.

**Old problems**
- A white-concrete box wall.
- Four identical concrete storeys with ladders, a flat roof and an iron-bar "mast".
- Interiors were a lectern and a chest.

**Changes**
- A compound wall of pale masonry with a blue band and merlons.
- A gatehouse with two flag masts and lamp posts.
- A training yard with straw dummies, targets and a bench.
- A parade-ground flagpole and a cannon battery facing the gate.
- A two-storey barracks: masonry below, plaster above, a slate gable, windows with blue shutters, bunks, and a stair.
- An armoury with a flat parapet roof, cannons and cargo, and a supply yard behind it.
- The five-storey HQ tower: masonry base, white plaster storeys with blue bands, a cornice, a slate hip roof and the flag. It has a marble-column porch with a blue awning and the bounty board.
- The tower's switchback stair serves:
  - the front desk and the barred holding cell, with an iron door and a bed;
  - the officers' floor;
  - the mess;
  - records;
  - the captain's office with the chest.

**Kit blocks:** PALE_MASONRY (+ stairs, slab), WHITE_PLASTER, HARBOUR_STONE, MARBLE_COLUMN, SLATE roof (via Mats), BLUE_SHUTTER, MULLION_WINDOW, BLUE_AWNING, DECK_CANNON, SHOP_COUNTER, PLANK_TABLE, PLANK_BENCH, ROPE_COIL, cargo, BRASS_LANTERN.

**Markers kept:** npc `marine` ×3, `marine_officer` (y 5), `marine_captain` (y 17); `bounty_board`; `cell`; chest `chests/marine`.

**Status:** rebuilt.

## Baratie (`baratie`)

- **Host:** the Baratie sea point (SEA shape).
- **Footprint:** 23x45, at sea level, unchanged.

**Old problems**
- A lens-shaped plank hull.
- A white-terracotta box with a flat red slab roof.
- An orange cube "head" with two black dots.
- Fins as plank strips; tables made of fence posts and carpet.

**Changes**

*Hull and head*
- The hull is a fish in plan: a round head, a long body, a tapering stern and the tail stalk.
- Dark strakes below, teal tile "scales" at the waterline, an oak deck at y 1, and a dark oak rail.
- The raised fish head carries the forecastle deck. It has rounded teal edges, white and black eyes on both sides, gills, and a red-lipped mouth.
- On the forecastle: the mast with two yards, furled sails, a crow's nest, the black flag, two bow cannons, and stairs down to the bow deck.

*Fins, tail and boarding*
- The pectoral fins are dining decks with tables.
- A tail fin rises at the stern.
- A floating landing stage on the port quarter carries bollards, the dock marker and an opening in the rail.

*The restaurant*
- Cream plaster between dark beam posts, a dark beam floor-line band, and red shutters with planters.
- A big terracotta gable whose front gable, with a round window, faces the bow.
- A lookout turret through the ridge with a hip cap and the flag.
- An entrance under a red awning; a front balcony and a stern terrace on the ground floor's roof.

*Interior*
- The dining hall: tables with candles, a red carpet aisle, chandeliers, and a bar at the serving pass.
- The kitchen behind the pass: smokers, a furnace, a cauldron, a lit campfire, the loot barrel, counters and shelves.
- A stair to the upper floor, which holds the staff dorm and the owner-chef's cabin: bed, desk and bookshelves.

**Kit blocks:** TEAL_ROOF_TILES (+ stairs, slab), TERRACOTTA roof (+ stairs, slab), CREAM_PLASTER, DARK_BEAM, DOCK_PLANKS, WEATHERED_BEAM, MULLION_WINDOW, RED_SHUTTER, FLOWER_PLANTER, RED_AWNING, BRASS_LANTERN, DECK_CANNON, ROPE_COIL, BOLLARD, PLANK_TABLE, PLANK_BENCH, SHOP_COUNTER, GOODS_SHELF, CARGO_SACKS, cargo.

**Markers kept**
- npc `head_chef`, `cook` ×2 and `waiter`.
- `dock`, now on the landing stage.
- The loot barrel `chests/tavern`.
- The SelfTest "Baratie built on the water" check still holds: there is an oak plank deck at sea level + 1 across the hull centre.

**Status:** rebuilt.

**Limitations**
- No "BARATIE" lettering: it does not fit the 13-wide front.
- The head is solid, not an enterable room.

## Arlong Park (`arlong_park`)

- **Host:** Cocoyasi.
- **Footprint:** 31x31, unchanged.

**Old problems**
- A stone-brick box wall with wall-block crenels.
- A plain water rectangle.
- A grey concrete cylinder tower with a ladder and a cyan disc cap.
- Nothing said "Arlong".

**Changes**

*Walls and gate*
- A curtain wall with a dressed-sandstone base, a cream plaster body, sandstone pilasters and teal coping.
- Four corner turrets with teal caps and red paper lanterns.
- A gate between sandstone piers under the big "ARLONG" signboard, with blue letters on cream, a sandstone frame and a teal cap.

*Courtyard*
- The sea-water pool, split by a railed plank causeway. Its east half runs out under the wall, through an iron grate, as the channel to the sea.
- Loungers and parasols, and four palms.

*Throne and tower*
- The throne terrace in front of the tower, with Arlong's chair and red paper lantern posts.
- The five-storey central tower: sandstone corners and floor bands, cream walls, lattice windows, a teal eave skirt at mid-height, a flared teal hip roof and a crowning belvedere.
- The shark crest (blue-grey, white eyes and teeth, red mouth) is on the tower face.

*Wings*
- Hip-roofed wings either side: a storehouse with cargo, a cannon and a net, and the crew barracks.

*Tower interior, by switchback*
- The crew hall: long table, barrels, bones.
- The bunk room.
- The armoury and trophy room.
- Nami's map room: cartography tables, a lectern, shelves, a desk with candles, and the hoard chest.
- Arlong's quarters.

**Kit blocks:** CREAM_PLASTER, DRESSED_SANDSTONE (+ stairs), HARBOUR_STONE, FIELDSTONE, TEAL_ROOF_TILES (+ stairs, slab), LATTICE_WINDOW, DARK_BEAM, DOCK_PLANKS, RED_AWNING, RED_PAPER_LANTERN, WHITE_PAPER_LANTERN, DECK_CANNON, FISHING_NET, GOODS_SHELF, PLANK_TABLE, cargo.

**Markers kept:** npc `fishman_boss` (throne terrace), `fishman_brute` ×2, `fishman_guard` ×2; `arc_anchor` (fishman_occupation); chest `chests/treasure`.

**Status:** rebuilt.

**Limitations:** the crest is a front-on shark face. The sawshark nose of the real jolly roger is not drawn.

## Drum Castle (`drum_castle`)

- **Host:** Drum Island (SNOW, the drum-rock plateau).
- **Footprint:** 25x25, unchanged.

**Old problems**
- A stone-brick box with a solid flat lid.
- Four fat cylinder towers with blue terracotta lumps.
- A row of brewing stands on the floor; a ladder.

**Changes**

*Exterior*
- A square keep of stone bricks on a fieldstone plinth, with pale masonry floor bands, red-shuttered windows, a crenellated roof deck and a cornice.
- Four slim round corner towers with fieldstone feet and ice-blue sky-tile domes; their round bays open into the keep's rooms.
- The tall central tower rises from the roof deck. Its spiral stair leads to a lookout floor with windows, a lectern and a bench, under a large ice-blue dome.
- A gabled entrance porch, snow and packed ice on the court, snow on the roof, lamp posts, and pink petals at the door (Hiriluk's cherry blossoms).

*Interior, by switchback*
- Ground floor: the infirmary ward (beds, a water cauldron), the apothecary (brewing stands on counters, bookshelves, the chest) and the entrance hall with benches.
- First floor: the old doctor's study and library.
- Second floor: the store and the young doctor's room.
- The roof deck, with doors into the tower tops and the central tower.

**Kit blocks:** PALE_MASONRY, FIELDSTONE, SKY_ROOF_TILES (domes), SLATE roof (porch, via SNOW Mats), RED_SHUTTER, MULLION_WINDOW, DARK_BEAM, SHOP_COUNTER, PLANK_TABLE, PLANK_BENCH, BRASS_LANTERN, cargo.

**Markers kept:** npc `doctor`, `apprentice_doctor`; `arc_anchor` (winter_kingdom); chest `chests/treasure`.

**Status:** rebuilt.

## Impel Down (`impel_down`)

- **Host:** Impel Down island (FLAT, ROCKY).
- **Footprint:** 31x31, unchanged.

**Old problems**
- An empty deepslate cylinder with a dome.
- Five seastone rings, each with eight bar stubs.
- A 65-high ladder shaft as the only way down.

**Changes**

*Above the sea*
- A dark deepslate drum with a thicker battered foot.
- Seastone bands every six blocks, eight buttresses, a crenellated parapet with dripstone spikes, slit windows, and a watch drum with red glass and a dome on the roof.
- A gatehouse with a seastone trim and the World Government emblem (white and blue on dark stone) over a seven-wide gate. The raised portcullis and the iron gate leaves stand open.

*Reception hall*
- The warden's desk and chair, and the boiling bath (lava cauldrons).
- Confiscated cargo and a cannon, chains, soul lanterns and the WG banner.
- A railed opening onto the central spiral stair.

*Below ground*
- The spiral winds around a seastone core down past five levels, each 6 high, to Freezing Hell.
- Each level is a guard corridor around the shaft, ringed by eight barred cells divided by radial walls.
- Each level has its own floor and decor:
  - Crimson Hell: red nether brick, dripstone blades, red glass;
  - Beast Hell: coarse dirt, hay, cobwebs, bones;
  - Starvation Hell: smooth sandstone, sand and dead bushes;
  - Blazing Hell: blackstone, magma and lava cauldrons;
  - Freezing Hell: packed ice, blue ice and snow.

**Kit blocks:** SHOP_COUNTER, DECK_CANNON, cargo.
- This is a deliberately dark, non-kit palette (deepslate and seastone).

**Markers kept**
- npc `warden`.
- `jailer` (levels 1–2) and `jailer_beast` (levels 3–5), one per level.
- `cell` with level 1–5, one per level, inside the east cell.
- `prison`.
- There was never a loot chest.

**Status:** rebuilt.

**Limitations:** the drum above the reception hall (y 9–23) is a sealed mass. It is a fortress shell with no further floors.

## Tower of Justice (`tower_of_justice`)

- **Host:** Enies Lobby (PALACE style).
- **Footprint:** 17x17, unchanged.

**Old problems**
- Eight stacked quartz boxes with glass panes and ladders, and a lightning rod on top.

**Changes**

*Exterior*
- Three set-back white stages of five, three and two storeys.
- Pale masonry ground storey, white plaster above, marble corner pilasters, pale masonry bands and cornices, and gilded pinnacles on every set-back.
- The World Government emblem on a blue panel with a gold cross on the second stage.
- The balcony high on the top stage (the canon "I want to live" balcony), a balustraded roof terrace and the WG flag mast.
- A marble portico with a teal pediment and three doors.

*Interior: a ten-flight switchback past*
- the lobby (reception counter, benches, WG banners);
- the guard room;
- the archive;
- the interrogation cell;
- the Rokushiki training hall;
- the agents' bar;
- offices;
- the dormitory;
- the chief's office with the chest and the balcony door;
- his quarters;
- the roof.

**Kit blocks:** PALE_MASONRY (+ stairs, wall), WHITE_PLASTER, MARBLE_COLUMN, TEAL roof (via PALACE Mats), BLUE_SHUTTER, MULLION_WINDOW, SHOP_COUNTER, PLANK_TABLE, PLANK_BENCH, BRASS_LANTERN.

**Markers kept:** npc `cp_agent` ×3 (lobby, archive, bar), `cp_leader` (office); chest `chests/treasure`.

**Status:** rebuilt.

**Limitations:** the stages step back by only one block, which the 17-wide footprint forces, so the silhouette is slender rather than stepped.

## Water 7 fountain (`water7_fountain`)

- **Host:** Water 7 (VENETIAN).
- **Footprint:** 21x21, unchanged.

**Old problems**
- A quartz ring basin and a quartz pillar.
- Water placed loose on a disc at the top, which floods the plaza when the chunk updates.

**Changes**

*Plaza and canals*
- A harbour-stone plaza with a sandstone ring band and diagonal joints.
- Four canals leave the basin along the axes, sunk a block with prismarine beds.
- Each canal has a hump bridge with a balustrade, and a spout notch in the basin rim.

*Fountain tower*
- A white plaster tower with a sandstone-capped pedestal.
- Two bowls at y 6 and y 17. Each spills through notches in its rim as falling sheets into the water below, and every fall lands inside water, so there is no flooding.
- Lion-head spouts, a slim spire with a marble column and a brass lantern at y 30.

*Corners:* lamp posts, benches and planters.

**Kit blocks:** HARBOUR_STONE (+ stairs, slab, wall), DRESSED_SANDSTONE (+ stairs), WHITE_PLASTER (+ stairs), MARBLE_COLUMN, PLANK_BENCH, FLOWER_PLANTER, BRASS_LANTERN.

**Markers kept:** npc `canal_guide` (plaza corner); `town_center`.

**Status:** rebuilt.

**Limitations:** the canals are only three blocks long inside the plot. They suggest the city's network rather than show it.

## Mary Geoise (`mary_geoise`)

- **Host:** Mary Geoise, on the Red Line (y fixed at 301).
- **Footprint:** 41x41, unchanged.

**Old problems**
- A quartz cylinder to y 24 and a gold dome at y 25–33.
- Quartz pillar towers to y 30.
- In the world, everything above local y 18 was cut off by the build limit, so the domes never existed.
- No interior beyond one NPC.

**Changes**

*Exterior*
- The palace now fits under the cap: everything is at y 18 or below.
- A white palace with pale masonry below and white plaster above, marble pilasters every four blocks, gold floor-line bands, mullion windows with planters, a cornice and a balustrade.
- The great gold dome over the throne hall, ringed by marble posts.
- Four round corner towers with gold domes.
- A marble portico with a low teal pediment and three doors.
- The front court: a quartz avenue, a gold-tipped fountain, clipped azalea hedges, flower beds and lamps.
- Side gardens with trees, and a back terrace balustrade over the Red Line's edge.

*Interior*
- The entrance hall with twin stairs to the gallery floor.
- The Empty Throne on a quartz-and-gold dais under the dome, ringed by twenty swords planted in the floor, with a chain chandelier from the dome.
- The banquet hall and the salon in the wings.
- Bedrooms and the library upstairs, looking into the throne hall through barred openings.

**Kit blocks:** PALE_MASONRY (+ stairs, wall), WHITE_PLASTER, MARBLE_COLUMN, TEAL roof (via PALACE Mats), MULLION_WINDOW, FLOWER_PLANTER, PLANK_BENCH, PLANK_TABLE, BRASS_LANTERN.

**Markers kept**
- npc `celestial_guard`.
- `arc_anchor` (holy_land).
- No chest, as before.
- DimensionTests "Mary Geoise is built" still holds: there are more than 200 QUARTZ_BRICKS at y 301 and at least one NPC marker.

**Status:** rebuilt.

**Limitations:** the 18-block cap makes the palace broad and low. The dome is 6 high.

## Treasure Tree, Elbaf (`treasure_tree`)

- **Host:** Elbaf (GIANT).
- **Footprint:** 41x41, unchanged.

**Old problems**
- A uniform spruce cylinder with a helix of loose top-slabs as its stair, and no railings or landings.
- One ellipsoid of spruce leaves as the canopy.
- A bare plank disc on top.

**Changes**
- A flared bole of mixed spruce and dark bark streaks.
- Six buttress roots.
- A giant-sized doorway with lanterns.
- A ground hall with the hearth, a long table and barrels.
- A square spiral stair, three revolutions, up the hollow bole. On the way it passes two landings that open through the bark onto railed branch walks, each ending in a leaf crown with a lantern.
- The canopy is a broad crown plus four lower lobes, with limbs reaching into them.
- The elders' lodge sits in a carved hollow of the crown: a railed platform, a thatched pavilion on log posts, the hearth, benches, a table and the chest.

**Kit blocks:** THATCH (via GIANT Mats), PLANK_TABLE, PLANK_BENCH, BRASS_LANTERN.

**Markers kept:** npc `giant_elder` (lodge), `giant_warrior` (at the door); chest `chests/treasure`.

**Status:** rebuilt.

## Tree of Knowledge, Ohara (`tree_of_knowledge`)

- **Host:** Ohara (ruins).
- **Footprint:** 31x31, unchanged.

**Old problems**
- A cylinder with bookshelf rings and no floors or galleries.
- The door opened into the wall flare.

**Changes**
- The burnt hollow trunk: dark oak with char (blackstone and basalt).
- A jagged broken rim with charred dead limbs, and green azalea regrowth on the rim.
- A north door cut through the root flare.
- Three levels of wall bookshelves, some burnt out (coal and gaps).
- Two railed galleries reached by stairs.
- The open atrium holds the poneglyph on a chiseled dais, the scholars' table, a lectern, the salvage chest, cobwebs, an ash-soil floor, and an oak sapling coming up through it.

**Kit blocks:** CARGO_SACKS.
- This is a deliberately burnt natural palette.

**Markers kept:** `poneglyph` (historical); npc `scholar`; chest `chests/ruin`.

**Status:** rebuilt.

## Laugh Tale ruins (`laugh_tale_ruins`)

- **Host:** Laugh Tale.
- **Footprint:** 25x25, unchanged.

**Old problems**
- Stepped hollow walls.
- A 3x3x3 poneglyph cube with the chest balanced on top.

**Changes**
- A three-tier stepped temple of mossy, cracked and plain stone bricks, with gilded-blackstone edge bands and chiseled accents.
- A grand stair up the north face, with balustrades.
- The summit shrine: sandstone columns, a gilded entablature, a blackstone hip roof with a gold finial, and the laughing face laid in gold in its floor, with the treasure chest and candles.
- The inner chamber, reached by a tunnel from the south: the final Road Poneglyph monolith on a gilded base, sandstone columns, soul campfires and a soul lantern.
- Moss carpets, vines and jungle bushes.

**Kit blocks:** SANDSTONE_COLUMN, BRASS_LANTERN.

**Markers kept:** `poneglyph` (final); `arc_anchor` (final_island); chest `chests/treasure`. There are no NPCs, as before.

**Status:** rebuilt.

**Limitations:** canon shows little of Laugh Tale, so this is an original interpretation.

## Execution platform, Loguetown (`execution_platform`)

- **Host:** Loguetown (STONE_TOWN).
- **Footprint:** 13x13, unchanged.

**Old problems**
- A hollow dark-oak box with a deck and a two-post frame.
- A ladder inside the box.

**Changes**
- A cobbled square and a stone plinth with steps.
- A dark-timber scaffold: nine posts, two brace rings, a railed deck with a fascia.
- The gallows frame with the stocks and chain on top.
- A two-wide stair on fence trestles up the west side.
- Marine banners on the fascia and corner lamp posts.

**Kit blocks:** DARK_BEAM, FIELDSTONE, BRASS_LANTERN.

**Markers kept:** `legend` (story execution_platform) on the deck; npc `marine` ×2. There is no chest, as before.

**Status:** rebuilt.

## Twin Capes lighthouse (`twin_capes_lighthouse`) and port beacon (`port_beacon`)

- **Host:** Twin Capes, and the New World Gate (`port_beacon`).
- **Footprint:** 9x9 and 7x7 (one builder), unchanged.

**Old problems**
- A concrete-banded cylinder with a ladder.
- A glass ring around glowstone.

**Changes**
- One builder sizes itself to the plot: a rock plinth, plus a slab ring on the 9x9 plot.
- A red-and-white banded tower of red terracotta and white plaster: radius 3, narrowing to 2 above a pale masonry band.
- A spiral stair around a dark beam post, 18 steps, to the lantern.
- A railed iron gallery and a glazed lantern room with sea lanterns and glowstone.
- A verdigris copper hip cap with a lightning rod.
- The keeper's corner: barrel, cartography table, lantern.

**Kit blocks:** WHITE_PLASTER, FIELDSTONE (+ slab), PALE_MASONRY, DARK_BEAM, BRASS_LANTERN.

**Markers kept:** npc `lighthouse_keeper`; `log_pose_vendor`.

**Status:** rebuilt.

## Sea Train station (`sea_train_station`)

- **Host:** Water 7, San Faldo, St. Poplar and Pucci (VENETIAN).
- **Footprint:** 15x11, unchanged.

**Old problems:** a brick box with a flat plank lid, a rail line and a bell.

**Changes**
- The track on dark beam sleepers.
- A platform with a yellow edge, under a terracotta canopy on iron posts with blue awnings, lamps and benches.
- The stuccoed hall: low-pitched terracotta wings either side of a taller centre with the clock gable (a white face with hands, framed in dressed sandstone).
- Inside: the ticket counter with the station master behind it, timetable signs, the departure bell, waiting benches and luggage.

**Kit blocks:** HARBOUR_STONE, DRESSED_SANDSTONE, WHITE_PLASTER, VENETIAN plaster and TERRACOTTA roof tiles (via Mats), TERRACOTTA_ROOF_TILE_SLAB, GREEN/BLUE shutters (via Mats), BLUE_AWNING, PLANK_BENCH, SHOP_COUNTER, MULLION_WINDOW, DARK_BEAM, BRASS_LANTERN, cargo.

**Markers kept:** npc `station_master`; `sea_train_stop` (platform).

**Status:** rebuilt.

## Sword dojo (`sword_dojo`)

- **Host:** Shimotsuki Village, Kano Country and Karate Island.
- **Footprint:** 15x13, unchanged.

**Old problems:** it was the generic settlement house in WANO materials, with a dojo interior.

**Changes**
- A gravel court entered through a roofed gate between stone lanterns.
- The hall, raised on a stone plinth with a veranda all round.
- White plaster between dark posts, lattice screens and an open front.
- A flared kawara hip roof with a ridge cap.
- Bamboo.
- Inside: a pale training floor, calligraphy scrolls, the shrine shelf with candles and a lantern, sword racks, straw targets and practice mats.

**Kit blocks:** WHITE_PLASTER, DARK_BEAM, KAWARA_ROOF_TILES (+ stairs, slab), LATTICE_WINDOW, RED_PAPER_LANTERN.

**Markers kept:** npc `master`, `student`. There is no chest, as before.

**Status:** rebuilt.

## Windmill, Dawn Island (`windmill`)

- **Host:** Dawn Island (Foosha).
- **Footprint:** 13x11, unchanged.

**Old problems**
- A white-terracotta cylinder and a plank-disc cap.
- Four one-wool-thick sail lines.
- An empty interior with a log.

**Changes**
- A seven-wide octagonal lower stage (fieldstone, then plaster) carrying a railed reefing stage.
- A five-wide plaster upper stage banded with dark beams.
- A thatched boat cap.
- Four lattice sails with canvas on an axle and hub.
- An awning over the door.
- Inside: the millstone under the main shaft, sacks and barrels, and a ladder up the shaft to the loft, whose door opens onto the reefing stage.
- A flour shed with a lean-to; hay and a bench.

**Kit blocks:** WHITE_PLASTER, FIELDSTONE, DARK_BEAM, THATCH (+ stairs, slab), WEATHERED_PLANKS, WEATHERED_BEAM, MULLION_WINDOW, RED_AWNING, CARGO_SACKS, PLANK_BENCH, BRASS_LANTERN, cargo.

**Markers kept:** npc `miller`.

**Status:** rebuilt.

**Limitations:** the loft is reached by a ladder. The 3x3 upper stage has no room for a stair.

## Starter shrine (`starter_shrine`)

- **Host:** the four starter isles (Shiokaze, Fuyugane, Kirinoha, Hinata).
- **Footprint:** 9x9, unchanged.

**Old problems**
- Four logs with lanterns on top around a chiseled pad and a lectern.
- No roof.

**Changes**
- A fieldstone platform with an andesite floor.
- A timber pavilion: four posts, a beam ring and a hip roof in the island style's own tiles.
- An altar with the record book, candles and a flower.
- The elder's bench, the starter chest and hanging lanterns.
- A stone-lantern approach, flower beds and planters.

**Kit blocks:** FIELDSTONE (+ wall), DARK_BEAM (when the style has no timber frame), style roof tiles via Mats, PLANK_BENCH, FLOWER_PLANTER, BRASS_LANTERN.

**Markers kept:** `respawn_anchor`; npc `elder`; chest `chests/starter`.

**Status:** rebuilt.

## Ancient ruin (`ancient_ruin`)

- **Host:** random islands, and the Sunken Ruins.
- **Footprint:** 15x15, unchanged.

**Old problems:** six random-height cracked pillars on a mossy pad, with the poneglyph in the open.

**Changes**
- A raised temple platform with steps.
- A colonnade of 16 marble columns on chiseled bases: some whole with capitals and a stretch of architrave, some broken, a few fallen to rubble.
- One column lies fallen across the grass.
- The ruined cella, whose vaulted apse shelters the poneglyph.
- Moss, vines and rubble heaps; the chest is half buried at a corner.

**Kit blocks:** MARBLE_COLUMN.

**Markers kept:** `poneglyph` (road or historical, same roll); npc `ruin_guardian`; chest `chests/ruin`.

**Status:** rebuilt.

## Treasure cache (`treasure_cache`)

- **Host:** random islands, Island of Rare Animals and the deep-sea reefs.
- **Footprint:** 7x7, unchanged.

**Old problems:** a red-wool X on coarse dirt and a skull.

**Changes**
- A sandy, gravelly patch with a red terracotta X.
- A weathered marker post with a skull.
- A dropped lantern, a rope coil and rocks.
- The chest two blocks down, as before.

**Kit blocks:** WEATHERED_BEAM, ROPE_COIL, FIELDSTONE_SLAB.

**Markers kept:** `treasure`; chest `chests/treasure` (same position).

**Status:** improved. It is a hidden cache and is meant to stay small.

## Devil Fruit tree (`fruit_tree_grove`)

- **Host:** random islands.
- **Footprint:** 11x11, unchanged.

**Old problems:** a lone sphere tree with alliums.

**Changes**
- A gnarled jungle trunk with a root flare and a side branch.
- A round canopy flecked with flowering azalea.
- The fruit bloom hidden in it, as before.
- A ring of mossy stones and flowers.

**Kit blocks:** FIELDSTONE.

**Markers kept:** `fruit_spawn` (same position next to the trunk). It is marker-only by design.

**Status:** improved.

## Knock Up Stream marker (`knock_up_marker`)

- **Host:** off Jaya.
- **Footprint:** 5x5, at sea level, unchanged.

**Old problems:** a water disc over two magma blocks.

**Changes:** a churning ring of bubble columns over soul sand, around a down-drafting centre over the magma vent.

**Kit blocks:** none. It is a sea feature.

**Markers kept:** `knock_up_stream`.

**Status:** improved, and kept minimal by design: it is a current, not a building.

## LandmarkCheck results (batch A ids)

```
PASS landmark ancient_ruin — 15x15, 561 blocks, 1 NPCs (1 reachable), 1 chests, 2 markers, interior 6 cells, 4 rotations in footprint
PASS landmark arlong_park — 31x31, 5582 blocks, 5 NPCs (5 reachable), 1 chests, 6 markers, interior 611 cells, 4 rotations in footprint
PASS landmark baratie — 23x45 (sea level), 5660 blocks, 4 NPCs (4 reachable), 1 chests, 5 markers, interior 2854 cells, 4 rotations in footprint
PASS landmark drum_castle — 25x25, 3432 blocks, 2 NPCs (2 reachable), 1 chests, 3 markers, interior 566 cells, 4 rotations in footprint
PASS landmark execution_platform — 13x13, 750 blocks, 2 NPCs (2 reachable), 0 chests, 3 markers, interior 80 cells, 4 rotations in footprint
PASS landmark fruit_tree_grove — 11x11, 275 blocks, 0 NPCs (0 reachable), 0 chests, 1 markers, interior 36 cells, 4 rotations in footprint
PASS landmark impel_down — 31x31, 11241 blocks, 6 NPCs (6 reachable), 0 chests, 12 markers, interior 1319 cells, 4 rotations in footprint
PASS landmark knock_up_marker — 5x5 (sea level), 106 blocks, 0 NPCs (0 reachable), 0 chests, 1 markers, interior 41 cells, 4 rotations in footprint
PASS landmark laugh_tale_ruins — 25x25, 2700 blocks, 0 NPCs (0 reachable), 1 chests, 2 markers, interior 182 cells, 4 rotations in footprint
PASS landmark marine_base — 27x27, 3514 blocks, 5 NPCs (5 reachable), 1 chests, 7 markers, interior 471 cells, 4 rotations in footprint
PASS landmark marine_headquarters — 41x41, 10857 blocks, 5 NPCs (5 reachable), 1 chests, 6 markers, interior 2373 cells, 4 rotations in footprint
PASS landmark mary_geoise — 41x41, 5463 blocks, 1 NPCs (1 reachable), 0 chests, 2 markers, interior 500 cells, 4 rotations in footprint
PASS landmark port_beacon — 7x7, 465 blocks, 1 NPCs (1 reachable), 0 chests, 2 markers, interior 54 cells, 4 rotations in footprint
PASS landmark sea_train_station — 15x11, 663 blocks, 1 NPCs (1 reachable), 0 chests, 2 markers, interior 85 cells, 4 rotations in footprint
PASS landmark starter_shrine — 9x9, 191 blocks, 1 NPCs (1 reachable), 1 chests, 2 markers, interior 42 cells, 4 rotations in footprint
PASS landmark sword_dojo — 15x13, 729 blocks, 2 NPCs (2 reachable), 0 chests, 2 markers, interior 85 cells, 4 rotations in footprint
PASS landmark tower_of_justice — 17x17, 3849 blocks, 4 NPCs (4 reachable), 1 chests, 4 markers, interior 913 cells, 4 rotations in footprint
PASS landmark treasure_cache — 7x7, 85 blocks, 0 NPCs (0 reachable), 1 chests, 1 markers, interior 0 cells, 4 rotations in footprint
PASS landmark treasure_tree — 41x41, 22742 blocks, 2 NPCs (2 reachable), 1 chests, 2 markers, interior 585 cells, 4 rotations in footprint
PASS landmark tree_of_knowledge — 31x31, 5254 blocks, 1 NPCs (1 reachable), 1 chests, 2 markers, interior 197 cells, 4 rotations in footprint
PASS landmark twin_capes_lighthouse — 9x9, 530 blocks, 1 NPCs (1 reachable), 0 chests, 2 markers, interior 54 cells, 4 rotations in footprint
PASS landmark water7_fountain — 21x21, 1926 blocks, 1 NPCs (1 reachable), 0 chests, 2 markers, interior 380 cells, 4 rotations in footprint
PASS landmark windmill — 13x11, 579 blocks, 1 NPCs (1 reachable), 0 chests, 1 markers, interior 77 cells, 4 rotations in footprint
```

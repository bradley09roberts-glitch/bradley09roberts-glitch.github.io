# Landmark audit, batch B (Blues and early Paradise)

Fifteen hand-designed set pieces in `world/landmark/BluesLandmarks.java` and `world/landmark/ParadiseLandmarks.java`.
Shared helpers for this batch are in `world/landmark/LandmarksB.java`:

- block-state shortcuts for the kit;
- wall rings, beam rings, plinth and cornice mouldings;
- round towers with onion domes, and connected ring rails;
- pointed arches;
- desert blocks (plaster on a dressed-sandstone plinth, flat parapet roof with a cornice) and desert doors;
- awning rows, stair flights, counters, shelves and pendants;
- a mikan fruit tree.

No footprint (Spec) was changed. Every id, builder registration, marker kind, NPC role and loot table is unchanged.
All fifteen pass `LandmarkCheck`, and `alubarna_palace` and `white_city` also pass the stamped-into-the-world comparison.
The results are at the end.

Renders, in `docs/audit/renders/<id>.png`, show view 0, view 2 and the floor plans. The old builds are in the parent's
`scratchpad/before/`.

Abbreviations used in the kit lists:

- *plaster* = cream, ochre, white or sky plaster
- *dressed* = dressed sandstone (with its stairs, slab and wall)
- *pale* = pale masonry (with its stairs, slab and wall)

---

## alubarna_palace — Alabasta (55x55)
**Role:** the royal palace of the desert kingdom on the city square. It is the kingdom's arc anchor and holds the historical poneglyph in the royal tomb.

**Old build:** a generic sandstone castle.

- One flat crenellated box sat on a platform ringed by a crenellated curtain wall.
- Four identical corner towers carried white blobs as "domes".
- The plain square clock tower had a ladder.
- The dome sat behind crenels and read as a stepped pyramid.
- There was no façade depth: no plinth, pilasters, sills or cornice.
- The front was a blank wall with a 3-door hole.

**Rebuilt as Alubarna.**

Approach:

- The city square has a paving grid and the palace axis.
- An octagonal fountain with a lantern column stands in the square, with palms and two striped-awning market stalls.
- A grand 11-wide dressed-sandstone staircase climbs between two domed gate pylons that carry the kingdom's light-blue banners.
- The raised terrace has retaining walls with column pilasters and a balustrade.
- The courtyard has a processional way, two reflecting pools with curbs, palms and planters.

Palace:

- The block is cream plaster on a dressed plinth (plinth course, floor-line string course and cornice).
- Sandstone-column pilasters and lattice windows with sills run on all four elevations.
- The throne hall has tall rear windows.
- A desert merlon parapet tops the walls.

Focal point and silhouette:

- A tall dressed-sandstone pishtaq frames a sky-plaster pointed-arch panel, the balcony door and a lattice light. Its column pinnacles have gold finials.
- A six-column porch carries the royal balcony.
- The great dome is a white onion on a plaster drum with eight lattice lights and a sky-blue band, over the double-height throne hall.
- Four round corner towers have white onion domes (the front pair taller). Each opens into the rooms behind on both floors.
- The clock tower is plaster with dressed quoins and bands, clock faces on two sides, a belfry with pointed openings and a bell, and an onion dome. The hidden gunners' room sits behind the clock face, with a deck cannon.

Interior:

- Front hall with columns and a gallery above that looks into the throne hall.
- Throne hall with a carpet, ten columns, a dais, a gold throne and banners. The lit dome is above it.
- Barracks with an armoury and a stair, and the princess's apartment above (canopy beds, loom).
- Dining room and kitchen, with the library and study above.
- The royal tomb (sarcophagi, columns, hieroglyph wall, poneglyph) is reached by a hatch beside the dais.

**Kit:** cream, ochre, white and sky plaster; dressed sandstone stairs, slab and wall; sandstone columns; lattice windows; red and yellow awnings; shop counters; cargo sacks and crates; deck cannon; flower planters; plank tables and benches; brass lanterns.

**Markers kept:** the same 16 NPCs:

- merchant, resident and child on the square;
- clock-tower guard;
- two gate guards;
- lookout guard in the north-west tower;
- noble and two throne guards;
- barracks guard;
- princess (noble), cook, waiter, scholar and priest.

Also kept: `arc_anchor` desert_kingdom, `poneglyph` historical, and `treasure`.

Chests kept: the clock-tower LOOT_TREASURE; LOOT_MARINE; LOOT_HOUSE; two tomb LOOT_TREASURE; and the LOOT_TAVERN barrels.

**Status:** rebuilt.

**Limits:** the clock tower and lookout are reached by ladders, since a 5x5 shaft has no room for a stair. The corner towers hold a store room below and a sitting room above, opening off both floors.

## rainbase_casino — Alabasta (37x37)
**Role:** Rain Dinners, the casino with the trap basement. It is the desert_casino arc anchor.

**Old build:** three plain stacked sandstone boxes with thin yellow lines and a thin moat. The crocodile was a flat green bar.

**Rebuilt** as a stepped casino of cream plaster on dressed plinths:

- Each tier has dressed quoins with gold caps, sandstone-column pilasters, lattice windows with sills and a yellow frieze.
- Each tier has a dressed cornice, a balustraded terrace and gilded corner obelisks with lanterns.
- A gilded porch on columns has a red awning and the casino's gold-framed marquee board.
- A dressed-sandstone moat, bridge, forecourt and palms complete the approach.
- The crocodile is sculpted over the crown. Its jaws gape over the front with white teeth, a red mouth and yellow eyes. A scute ridge runs down its back, four legs grip the tier edges, and the tail slides down the back terraces and curls.

Interior:

- Gaming floor with a roulette wheel, four card tables, slot machines and a counter bar with stools and barrels. Gilded columns carry the VIP floor.
- VIP floor with the owner's office (desk, lectern, shelves), a lounge (sofas, bar) and the gold-floored vault behind an iron door and lever.
- Crown suite.
- Trap basement with three seastone cells behind bars and the banana-gator pool behind an iron railing, plus the thugs' storeroom.

**Kit:** cream plaster; dressed sandstone stairs and wall; sandstone columns; lattice windows; red awning; shop counters and goods shelf; plank tables and benches; cargo crates, rope coil; brass lanterns.

**Markers kept:**

- NPCs: bartender, three patrons, guard, pirate_captain, two VIP guards, two bandits.
- Three `cell` markers, `treasure`, and `arc_anchor` desert_casino.
- Six LOOT_TAVERN bar barrels, the VIP LOOT_TAVERN barrel, two LOOT_TREASURE in the vault, LOOT_HOUSE and two LOOT_WAREHOUSE.

**Status:** rebuilt.

**Limits:** the tiers are still rectangular (the canon building is a soft stepped mass). The crocodile is about 20 blocks long, which is legible but not huge.

## nanohana_port — Alabasta (41x35, sea level)
**Role:** the desert port, with a ship spawn and dock.

**Old build:**

- A sandstone slab quay with plank piers and wool-and-fence stalls.
- Three generic `Buildings.house` boxes with gable-like stepped tops.
- A square sandstone tower.

**Rebuilt:**

- The harbour-stone quay wall rises from the sea bed, with bollards and ladders. The quay surface is at sea level + 1.
- Two dock-plank jetties stand on weathered-beam pilings, with end fascias, bollards, lamp posts, rope coils, cargo and ladders.
- Three striped-awning market stalls have ridged canvas, counters and goods.
- A round minaret watch has a cantilevered balcony ring with a connected rail, a canopy band and a white onion dome.
- The street front is custom desert buildings that replace the `Buildings.house` calls:
  - an ochre two-storey spice shop with an awning and a shaded canvas roof terrace;
  - a two-storey cream tavern with a terrace awning and benches, merlon parapet and a white onion dome on a drum;
  - a tall white warehouse with a pointed-arch loading bay, a dark-beam hoist with a hanging crate and an awning.
- Palms and a water trough fill the east end.

Interiors:

- Shop: counter, goods shelves, sacks and crates, with a bedroom upstairs.
- Tavern: counter bar, kegs, tables, quest board, and guest rooms upstairs.
- Warehouse: cargo bays and shelves.

**Kit:** harbour stone; dock planks; weathered beams; dressed sandstone; cream, ochre and white plaster; lattice windows; red, yellow, blue and green awnings; shop counters; goods shelves; cargo crates and sacks; rope coil; bollards; fishing net; plank tables and benches; brass lanterns.

**Markers kept:**

- `ship_spawn`, `dock`, `treasure` (the smuggler's barrel under the east pier), `legend`, `quest_board`.
- NPCs: two dockworkers, three merchants, three marines (one on the minaret), child, shopkeeper, bartender, two patrons, and the warehouse dockworker.
- Loot as before: three stall LOOT_SHOP, two shop LOOT_SHOP, four tavern LOOT_TAVERN, two LOOT_WAREHOUSE, and the LOOT_TREASURE barrel.

**Status:** rebuilt.

**Limits:** the minaret is climbed by ladder.

## yuba_oasis — Alabasta (29x29)
**Role:** the oasis town buried by sandstorms.

**Old build:** a flat sand site with three sand heaps. The inn was a sandstone shell sunk in the ground, with only a slab lid showing. The well had a fence crank, and the camp had a wool tent.

**Rebuilt as a buried town:**

- Dunes drift over everything.
- The inn is ochre plaster on a dressed plinth with a merlon parapet. Its lower storey is buried, and its upper storey windows sit just above the sand, with sand drifted onto the roof and in through the west windows.
- A trench dug down to the lower door is shored with weathered planks and beams.
- The well has a dressed-wall rim, a crank beam and lantern, a rope coil, spoil heaps and the digger's tools.
- The cistern is ribbed with dressed arches.
- The digger has a canvas tent with a bedroll and a fire.
- A buried bath-house dome and an arcade's arch tops poke out of the sand. Dead palm stumps stand around one living palm.

Interior:

- Lower common room: counter, kegs, table and sand drifts. A store of cargo lies behind a door.
- A stair leads to the upper guest rooms.

**Kit:** ochre and white plaster; dressed sandstone stairs and wall; sandstone columns; lattice windows; weathered planks and beams; shop counters and goods shelf; cargo; rope coil; plank tables and benches; brass lanterns.

**Markers kept:** innkeeper, elder and merchant; LOOT_TAVERN x2, LOOT_HOUSE chest, camp LOOT_HOUSE barrel, cistern LOOT_TREASURE; `treasure`, `legend`.

**Status:** rebuilt.

## cricket_house — Jaya (21x19)
**Role:** the explorer's half house (the famous front-only façade), with the salvage crew's camp.

**Old build:**

- A two-storey birch-plank box only 5 deep, with a front-facing dark-oak gable.
- The cut back was random spruce planks.
- A wool tent, a glass sphere and a pool completed it.

**Rebuilt so that it reads as a grand villa from the front and as a slice from the side.**

Front façade:

- Sky-plaster walls on a pale masonry plinth, with white trim bands and quoins.
- A gabled entrance bay with a round lattice light in its pediment.
- A marble-column porch carrying a balcony.
- A round corner turret under a conical terracotta roof.
- Oak-shuttered mullion windows with planters, and a wall lantern.

From the side and back:

- The roof is a terracotta half-gable that rises from the front eave to the ridge exactly at the cut.
- The back is the cut itself: boards patched over the raw edge, gaps showing the rooms, floor joists sticking out and a blue tarp.
- Three timber shores prop the façade up.

Interior and yard:

- The slice holds a 2-deep parlour (logbook shelves, lectern, the old man's chair), a stair, and a bedroom and study upstairs.
- The salvage camp has a tent, a fire with benches, a diving bell, an anchor, an anvil, cargo and a fishing net.
- The dive pool is lined with harbour stone and has a dock-plank edge and a bollard.

**Kit:** sky and white plaster; pale masonry; marble columns; terracotta roof tiles, stairs and slab; mullion and lattice windows; oak shutters; flower planters; weathered planks and plank stairs; weathered and dark beams; blue awning; harbour stone; dock planks; bollard; rope coil; cargo crate and sacks; fishing net; plank tables and benches; brass lanterns.

**Markers kept:** elder and two dockworkers; LOOT_HOUSE chest and barrel, LOOT_WAREHOUSE, LOOT_TREASURE; `treasure`, `legend` half_house.

**Status:** rebuilt.

## welcome_arch — Whisky Peak (27x25)
**Role:** the bounty hunters' welcome gate and party hall, with their hidden base.

**Old build:**

- Two brown terracotta stacks for the cactus rocks.
- A spruce-log arch with a flat sign.
- An all-spruce-plank two-storey box under a plain gable, with the balcony bolted on.

**Rebuilt:**

- Green saguaro rock spires with moss ribs, rounded crowns, upturned arms and a rubble skirt.
- The arch is dark-beam towers on fieldstone plinths with a crossbeam and a little terracotta gable roof. Under it hang the striped sign board, bunting and red paper lanterns.
- The party plaza has two long plank-table rows with benches, keg stacks, a band stage, and strings of red and white paper lanterns between beam posts.
- The party hall has cream plaster below and braced timber frame above on a fieldstone plinth, with a dark-beam floor ring, corner and mid posts, and a terracotta gable with eave brackets and a smoking chimney.
- The hall's windows have red shutters and planters, and its banners hang by the doors. A plank balcony on posts with a railing overlooks the plaza.

Interior:

- Ground floor: counter bar with stools and kegs, tables, a fireplace and the quest board.
- Stair to three guest rooms off a corridor.
- Hidden cellar: wanted posters, a bounty board panel, a long table, armoury, deck cannon, bunks and the treasure chest.

**Kit:** cream plaster; braced timber frame; dark beams; fieldstone and its slab; ochre plaster; terracotta roof tiles; red and white paper lanterns; red shutters; flower planters; dock planks and slab; weathered planks and plank stairs; shop counters; goods shelf; plank tables and benches; cargo; deck cannon; brass lanterns.

**Markers kept:** four patrons, two residents, child, bartender and two bandits; `quest_board`, `bounty_board`, `treasure`, `legend`; all LOOT_TAVERN barrels, LOOT_HOUSE chest and barrel, LOOT_SMITH, LOOT_TREASURE.

**Status:** rebuilt.

**Footprint:** the Spec is unchanged. The hall is one block shallower (z 14..23 instead of 14..24), so that its roof overhang stays inside 27x25.

## giant_duel_ground — Little Garden (45x45)
**Role:** the century-long giants' duel arena. It is the little_garden_duel arc anchor.

**Old build:** spruce-log box huts with a plank gable, a fence for windows and a wall-banded roof. The tally stones were a flat brick line.

**Improved:**

- Both giant huts are rebuilt in the GIANT style:
  - a two-course fieldstone plinth and weathered-plank walls on spruce-log posts and log rings;
  - a 3-wide, 6-tall doorway under a log lintel and giant lattice windows;
  - a steep thatch gable facing the door, with a lattice light in the gable;
  - a fur bed, a cask table, a fire pit, and the owner's great weapon (a sword for one giant, an axe for the other).
- Each camp now has a giant bonfire ring with a drying rack and hides.
- The tally wall is a moss-grown fieldstone wall with chiselled tallies and a step.
- The arena, the beast skeleton, the buried cache and the jungle are kept.

**Kit:** fieldstone and its stairs; weathered planks; thatch; lattice windows; dark beam; fishing net; brass lanterns.

**Markers kept:** two giant_warrior and the scholar; `arc_anchor`, `treasure`, `legend`; hut LOOT_TREASURE and LOOT_HOUSE chests, LOOT_HOUSE and LOOT_TAVERN barrels, and the buried LOOT_TREASURE.

**Status:** improved; the huts are rebuilt and the rest is kept.

## volcano_vent — Little Garden (27x27)
**Role:** the volcano that signals each duel. It contains a lava-tube cave with a volcanologist.

**Old build:** a noisy cone of random basalt with a straight round taper. The tube mouth was a basalt frame.

**Improved:**

- The cone now has concave flanks and five buttress ridges.
- Cooled-lava veins of magma and obsidian run down two flanks.
- The tube is a timbered adit, with weathered-beam frames every three blocks, a blackstone portal, and supplies at the mouth.
- The camp sits under a yellow canvas awning on poles, with a plank table, sacks and a rope coil.
- The crater lava lake, the steam vents, the lava pocket behind glass and the sealed grotto are kept.

**Kit:** weathered beams and planks; yellow awning; plank table; cargo sacks; rope coil; cargo crate; brass lanterns.

**Markers kept:** scholar; LOOT_HOUSE, LOOT_TREASURE; `treasure`, `legend`.

**Status:** improved; it is a natural landmark.

## gray_terminal — Dawn Island (37x33)
**Role:** the garbage-heap shanty town under the kingdom's wall, with the brothers' treehouse.

**Old build:**

- A uniform grey stone-brick slab with crenels top and bottom and a barred hole.
- Shacks were cubes of randomly mixed planks.
- The junk heaps were low.

**Rebuilt:**

- The kingdom wall is pale masonry with:
  - a fieldstone footing course and buttresses with sloped caps;
  - a string course, a cornice and crenellated parapets on both faces with a wall walk;
  - two gate towers with their own merlons, windows and blue banners;
  - an arched gate passage with a half-raised portcullis;
  - a guardroom in its thickness, with an iron door, a ladder to the walk and passages through the towers.
- Junk mounds are 3–4 high, with smouldering fires on top and junk piled against the wall.
- The shacks have:
  - weathered-plank walls patched with odd planks and rusty sheet;
  - dark-beam corner posts on a rubble footing;
  - real rusty-tin shed roofs (exposed and weathered cut-copper slabs, with infilled sides);
  - barred windows and doors.
- A tarp lean-to stands against a mound.
- The junk trader's stall has a ridged green awning, counters and a goods shelf.
- The communal fire has benches.
- The treehouse sits on a taller oak, with a thatch gable, a front railing and the brothers' black flag. The cache is still hidden in the trunk.

**Kit:** pale masonry and its stairs; fieldstone and its stairs; harbour stone; weathered planks and beams; dark beams; dock planks; thatch, thatch stairs and slab; green and blue awnings; shop counters; goods shelf; cargo crate; rope coil; plank tables and benches; brass lanterns.

**Markers kept:**

- NPCs: three guards (wall walk, gate road, guardroom), two residents, two children, two bandits and the merchant.
- `treasure`, `legend`.
- Chests: guardroom LOOT_HOUSE, two shack LOOT_HOUSE, bandit LOOT_TREASURE and two LOOT_TAVERN, two stall LOOT_SHOP, treehouse LOOT_HOUSE, and the trunk LOOT_TREASURE.

**Status:** rebuilt.

## hilltop_mansion — Syrup Village (33x29)
**Role:** the heiress's mansion on the hill, with the butler's secret den.

**Old build:** a 23x16 box of white terracotta with log corner posts and a deepslate hip roof. It had evenly spaced bare panes, a quartz slab porch and a plain walled garden.

**Rebuilt** as a symmetrical white mansion:

- White plaster on a pale masonry plinth, with quoins, a string course and a moulded cornice.
- Tall mullion windows with blue shutters and planters on all sides.
- A slate hip roof with eave brackets, a pedimented centre bay (cross gable with a round light) and two smoking chimneys.
- A four-column marble portico carrying a balcony with a balustrade and planters.

Garden:

- A masonry wall with iron railing and capped piers, and gate piers with brass lanterns.
- A gravel drive between hedges, a fountain on the west lawn and flower beds on the east lawn.
- The old tree's branch reaches the bedroom window.

Interior:

- Hall with a 3-wide grand stair.
- Dining room with a long table and fireplace.
- Parlour, and kitchen.
- Upstairs: the heiress's bedroom (canopy bed) facing the tree, a guest room, and the study whose shelf wall (one chiselled panel) hides the den.

**Kit:** white plaster; pale masonry, its stairs, slab and wall; marble columns; slate roof tiles, stairs and slab; mullion windows; blue shutters; flower planters; plank tables and benches; goods shelf; cargo sacks; brass lanterns.

**Markers kept:** guard, farmer, waiter, cook, noble and doctor; LOOT_TAVERN x3, kitchen LOOT_HOUSE, bedroom LOOT_HOUSE, guest LOOT_HOUSE barrel, den LOOT_TREASURE; `treasure`.

**Status:** rebuilt.

## tangerine_grove — Cocoyasi (25x21)
**Role:** the mother's tangerine orchard and cottage, with her grave.

**Old build:** a white terracotta box with an acacia gable and bare panes. The orchard trees used orange concrete blocks, and the grave was a bare cross.

**Rebuilt:**

- The cottage is white plaster on a fieldstone footing, with an oak beam ring and posts.
- It has a terracotta gable with eave brackets, a smoking fieldstone chimney and green shutters with planters.
- A hooded door with a step has a bench and a wall lantern.
- A harvest shed with a lean-to roof stands against the cottage, full of crates of fruit.
- The fenced orchard has a gate, cross paths, two rows of round-canopy mikan trees, a ladder against a tree and the day's picking.
- The grave stands on a two-step rise, with flowers, orange candles and a lamp post.

Interior: kitchen, the family table, two children's beds and a tie beam with a lantern. The savings chest is set into the floor under the rug.

**Kit:** white plaster; fieldstone and its stairs; oak beams; terracotta roof tiles, stairs and slab; green shutters; flower planters; cargo crates and sacks; rope coil; plank table and bench; brass lanterns.

**Markers kept:** farmer, resident, child; orchard LOOT_HOUSE barrel, kitchen LOOT_HOUSE, floor LOOT_TREASURE; `treasure`, `legend`.

**Status:** rebuilt.

## white_city — Flevance (47x47)
**Role:** the abandoned white town, with its hospital, church and white-lead mine.

**Old build:**

- The houses were white-noise cubes of mixed white blocks with slab lids.
- The hospital was a flat quartz box with a floating red cross.
- The church was a calcite box with a quartz-stair roof and a quartz-pyramid tower.

**Rebuilt** in white plaster on pale masonry, with slate roofs and snow:

- **Houses:** four slate-gabled houses with blue shutters, gable windows and snow along the ridges. Two are burnt, with scorched patches, a fallen roof end, a wall breach and charred beams.
- **Hospital:** two storeys with a plinth course, a floor band and a hip roof that has partly fallen in, with charred rafters and snow on the ward floor. It has a door hood and a red cross on the wall. Inside are the wards, the doctor's office (records, lectern, chest) and a stair to the upper ward.
- **Church:**
  - a buttressed nave with lancet windows and a steep slate gable;
  - a pointed-arch double door and a rose window;
  - pews facing the altar;
  - a bell tower that rises through the roof to a cornice, belfry and slate spire, reached by a ladder.
- **Square:** a pale masonry fountain and statue, and soul-lit lamps.
- **Barricade:** weathered planks with a barred gate.
- **Mine:** a headframe with a winding wheel and cable over a timbered adit. Timber frames in the gallery lead to the sealed records gallery.

**Kit:** white plaster; pale masonry, its stairs, slab and wall; slate roof tiles, stairs and slab; blue shutters; marble columns; weathered beams and planks; dark beam; plank table.

**Markers kept:** two bandits, scholar, ruin_guardian; office and mine LOOT_RUIN, two LOOT_HOUSE barrels, LOOT_TREASURE; `treasure`, `legend`.

**Status:** rebuilt. The world-stamp check passes at 99.6%.

## explorers_monument — Lvneel (27x27)
**Role:** the monument to the kingdom's great explorer, and the Explorers' Hall museum.

**Old build:** a polished-deepslate plinth under the statue, and a stone-brick box museum with log posts and a spruce hip roof.

**Rebuilt:**

- **Plaza:** a harbour-stone compass rose with pale and chiselled points. A moulded pale masonry plinth carries a plinth course, cornice and plaque.
- **Statue:** a refined bronze explorer pointing his spyglass out to sea.
- **Furnishings:** lamp posts, benches and flower beds.
- **Explorers' Hall:**
  - pale masonry on a fieldstone plinth with marble pilasters;
  - a slate gable with eave brackets;
  - tall windows, gable lights and a lantern cupola on the ridge;
  - a four-column pedimented portico.
- **Interior:** display cases on dark-beam pedestals, the logbook wall, the model ship on its stand, the chart table and the lectern.
- The vault under the plinth is kept.

**Kit:** pale masonry, its stairs and wall; fieldstone and its stairs; harbour stone; marble columns; slate roof; mullion and lattice windows; dark beam; plank benches; brass lanterns.

**Markers kept:** child, resident, scholar, guard; vault LOOT_TREASURE, hall LOOT_SHOP; `treasure`, `legend`.

**Status:** rebuilt.

## junkyard_fort — Spider Miles (37x37)
**Role:** a pirate fort cobbled from junk. It is the junkyard_family arc anchor and has a prisoner cell.

**Old build:** a 2-thick wall of per-block random scrap (visual noise), a bare overturned-hull ellipsoid and a shed.

**Rebuilt as "junk, but built":**

- The curtain wall is in bays, one salvaged material per bay (weathered planks, rusty or patinated copper, deepslate, dark planks). Weathered-beam posts separate the bays, with a mossy footing and iron spikes on the ragged top.
- Two timber watch-towers with tin roofs and ladders flank a gate under a dark-beam lintel.
- The hull hall has:
  - a keel that follows the hull and a gunwale beam;
  - portholes and a beam-framed bow door;
  - stern windows on the captain's boarded cabin.
- The crow's nest stands on a mast with a yard and a torn sail.
- The smithy is an open shed under a tin roof, with a brick forge and smoking chimney.
- The cage has dark-beam posts.

**Kit:** weathered beams and planks; dark beams; dock planks; lattice windows; deck cannon; cargo crate; brass lanterns.

**Markers kept:** five pirates, pirate_captain, lookout; `cell`, `treasure`, `arc_anchor`; LOOT_TAVERN x2, LOOT_WAREHOUSE, cabin LOOT_TREASURE, LOOT_SMITH, buried LOOT_TREASURE.

**Status:** rebuilt.

## royal_castle — Ilisia (51x51)
**Role:** the flower kingdom's royal castle. It is the royal_court arc anchor and holds the treasury.

**Old build:**

- Everything was quartz brick.
- The towers had light-blue terracotta cones.
- The keep was a flat crenellated box with a cylinder spire.

**Rebuilt** in the PALACE style:

- **Curtain wall:** pale masonry with a fieldstone footing, a diorite string course, a cornice and crenels.
- **Towers:** a white upper stage, a diorite band, mullion windows, and flared teal-tile cone roofs with gold finials.
- **Gatehouse:** a half-raised portcullis, windows and banners.
- **Courtyard:** flower beds, flowering hedges, planters, a pale masonry fountain with a marble column, lamp posts and benches.
- **Keep:**
  - white plaster on a pale masonry and fieldstone plinth, with floor bands and cornices;
  - marble pilasters, and mullion windows with blue shutters and planters;
  - a teal hip roof with eave brackets;
  - a white spire stage with a teal cone, gold finial and the pink banner;
  - a four-column porch carrying a balcony, with a pointed-arch transom over the great doors.
- **Interiors:** kept from the old build (throne hall, banquet hall, kitchen, grand stairs, royal chambers, library and chapel, guard hall, treasury). They gain kit furniture, and the roof hatch is dropped because the roof is now pitched.

**Kit:** pale masonry, its stairs, slab and wall; white plaster; marble columns; teal roof tiles and stairs (cones and the keep's hip); fieldstone; mullion windows; blue shutters; flower planters; shop counters; cargo sacks; plank tables and benches; brass lanterns.

**Markers kept:** the same 14 NPCs (guards, resident, child, two nobles, waiter, cook, priest); `treasure`, `arc_anchor`; LOOT_MARINE x2, LOOT_TAVERN, LOOT_WAREHOUSE x2, LOOT_HOUSE, LOOT_TREASURE x2.

**Status:** rebuilt (exterior). The interior layout is kept and refurnished.

---

## LandmarkCheck results (batch B)

The result lines are copied from `run/selftest-phaselandmarks.txt` at the time of the commit:

```
PASS landmark alubarna_palace — 55x55, 20877 blocks, 16 NPCs (16 reachable), 7 chests, 19 markers, interior 1582 cells, 4 rotations in footprint
PASS landmark cricket_house — 21x19, 1469 blocks, 3 NPCs (3 reachable), 4 chests, 5 markers, interior 62 cells, 4 rotations in footprint
PASS landmark explorers_monument — 27x27, 2285 blocks, 4 NPCs (4 reachable), 2 chests, 6 markers, interior 255 cells, 4 rotations in footprint
PASS landmark giant_duel_ground — 45x45, 4099 blocks, 3 NPCs (3 reachable), 7 chests, 6 markers, interior 442 cells, 4 rotations in footprint
PASS landmark gray_terminal — 37x33, 5918 blocks, 10 NPCs (10 reachable), 10 chests, 12 markers, interior 408 cells, 4 rotations in footprint
PASS landmark hilltop_mansion — 33x29, 3818 blocks, 6 NPCs (6 reachable), 7 chests, 7 markers, interior 534 cells, 4 rotations in footprint
PASS landmark junkyard_fort — 37x37, 4110 blocks, 7 NPCs (7 reachable), 6 chests, 10 markers, interior 293 cells, 4 rotations in footprint
PASS landmark nanohana_port — 41x35 (sea level), 13493 blocks, 14 NPCs (14 reachable), 12 chests, 19 markers, interior 1076 cells, 4 rotations in footprint
PASS landmark rainbase_casino — 37x37, 9381 blocks, 10 NPCs (10 reachable), 12 chests, 15 markers, interior 2788 cells, 4 rotations in footprint
PASS landmark royal_castle — 51x51, 16569 blocks, 14 NPCs (14 reachable), 8 chests, 16 markers, interior 2830 cells, 4 rotations in footprint
PASS landmark tangerine_grove — 25x21, 1409 blocks, 3 NPCs (3 reachable), 3 chests, 5 markers, interior 219 cells, 4 rotations in footprint
PASS landmark volcano_vent — 27x27, 3222 blocks, 1 NPCs (1 reachable), 2 chests, 3 markers, interior 141 cells, 4 rotations in footprint
PASS landmark welcome_arch — 27x25, 3526 blocks, 10 NPCs (10 reachable), 13 chests, 14 markers, interior 552 cells, 4 rotations in footprint
PASS landmark white_city — 47x47, 7462 blocks, 4 NPCs (4 reachable), 5 chests, 6 markers, interior 1011 cells, 4 rotations in footprint
PASS landmark yuba_oasis — 29x29, 3099 blocks, 3 NPCs (3 reachable), 5 chests, 5 markers, interior 275 cells, 4 rotations in footprint
PASS landmark alubarna_palace stamped into the world — NONE on alabasta at 19623,74,-627: 4169/4169 sampled blocks match (100.0%)
PASS landmark white_city stamped into the world — NONE on flevance at 27977,82,-24023: 4802/4802 sampled blocks match (100.0%)
```

The only other failure in the full run is outside this batch: `sword_dojo` makes 64 writes outside its footprint,
starting at local 0,5,-1.

## Notes for the parent

- In the brief, `white_city` and `royal_castle` are hosted on Flevance and Ilisia (`CanonIslands`), not on Goa. They were designed for their actual hosts: the abandoned white town and the flower kingdom's palace. The Goa wall appears as the kingdom wall in `gray_terminal`.
- The `Buildings.house` calls in `nanohana_port` are replaced by custom desert buildings, with the same interiors' marker roles and loot tables.
- I found no Arch bugs.
- `Arch.window` puts the sill and shutters one block outside the wall. On a wall that sits on the footprint edge they land outside the footprint, so I avoided that case. A `noSill` variant would help builders whose walls touch the footprint edge.

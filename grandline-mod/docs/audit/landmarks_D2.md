# Landmark audit — batch D2 (White Sea and deep sea)

Files: `src/main/java/com/grandline/world/landmark/DimensionLandmarks.java` (registration + the seven sky set pieces) and
the new `LandmarksD2.java` (the four Fish-Man Island / Sea Forest set pieces + the shared shapes of this batch: soft
cumulus puffs and cloud balls rounded with snow layers, a heightmap dome with stair shingles, a scalloped shell dome,
onion caps, a giant bell, coral trees, wrecks). No footprint (`Spec`) changed, so nothing on the host islands moves.

Renders (view 0 = camera from the south-east, view 2 = from the north-west, plus floor-plan cuts):
`docs/audit/renders/<id>.png`. The front of every piece is local north.

Marker kinds and NPC roles are unchanged for every landmark; NPC cells are cleared before the marker is written, so
every NPC stands on a floor with two blocks of air. Loot chests keep their loot tables (and count).

---

## heavens_gate — Heaven's Gate (sky: `heavens_gate` island)
- **Type / role:** ceremonial gate and toll booth at the top of the cloud stair; arrival point of the White Sea.
- **Footprint:** 23 x 17 (unchanged).
- **Old build:** two plain white-concrete slabs as pillars, a flat cloud beam with a one-row gold strip and a bell on
  top, a 7x5 concrete box with a slab lid as the toll booth (no roof, one pane), cloud "steps" floating in front of the
  gate unconnected to it. Nothing named the gate; no facade depth anywhere.
- **Changes:** a raised terrace (sky-plaster plinth course, white plaster, quartz paving) with steps up from the road
  and down the far side; two 4x4 pillars with marble corner columns, sky-blue recessed panels banded in gold, a moulded
  stair base and corbelled capital with a froglight band; a round arch sprung between them (stair soffit, gold
  keystone, a real bell under it, brass lanterns); the beam with a stepped cornice and the sky-blue **name plaque in a
  gold frame** on both faces; a golden sunburst rising out of cumulus clouds on top; clouds wreathing the capitals and
  banked round the terrace (rounded with snow layers so they read soft). The **toll booth** is a real building:
  white plaster with sky corners, a sky-scale hip roof, a teller window on the road with a counter, a blue awning and a
  pot on the counter, a 3-tall door, windows with sills; benches, planters and brass-lantern lamp posts for the
  queue on the other side of the road.
- **Kit blocks:** white/sky plaster, white plaster stairs, marble column, sky roof tiles (hip), shop counter, blue
  awning, plank bench, flower planter, brass lantern.
- **Gameplay kept:** `npc` keeper (inside the booth behind the counter), `npc` guard (in the gate passage); chest
  `LOOT_SHOP` in the booth.
- **Status:** rebuilt.
- **Limitations:** the name on the plaque is a coloured panel (signs cannot be written by world generation).

## cloud_chapel — Angel Island chapel (sky: `angel_island`)
- **Type / role:** the island's chapel with a dial merchant outside.
- **Footprint:** 19 x 19 (unchanged).
- **Old build:** a white-concrete cylinder with a quartz dome that was cut through by its own clearing pass (holes in
  the lower dome ring), eight single glass columns, a door cut straight into the wall, no step, the merchant standing
  inside the chapel.
- **Changes:** a rotunda (sky plinth course, white walls, sky frieze, a white cornice ledge) with proud sky-blue
  pilasters and tall glazed windows between them; a **smooth heightmap dome** in sky-scale tiles with stair shingles and
  white ribs over the pilasters, topped by an open marble-columned bell cupola with a gold cap; a columned **portico**
  with an entablature, a pediment (sky-scale gable) with a gold oculus, steps and lanterns; inside, pews facing a
  raised quartz dais, a gold altar with candles, a lectern, the yellow altarpiece window and a froglight chandelier.
  The **dial merchant** now has a stall on the forecourt (posts, blue awning roof, counter, shelves, wares); a garden
  bench and planters; low cloud banks in the corners.
- **Kit blocks:** white/sky plaster (+stairs/slabs), sky roof tiles (+stairs/slabs), marble column, shop counter,
  goods shelf, blue awning, plank bench, flower planter, brass lantern.
- **Gameplay kept:** `npc` priest (on the dais), merchant (at the stall), resident (garden); chest `LOOT_SHRINE`.
- **Status:** rebuilt.

## shandora_ruins — Shandora, the City of Gold (sky: `upper_yard`)
- **Type / role:** the overgrown ruined city; poneglyph, sealed vault of gold, trial guardians.
- **Footprint:** 47 x 47 (unchanged).
- **Old build:** a noisy broken perimeter wall round a flat plaza, four identical hollow 8x8 boxes, stone-brick-wall
  sticks as columns, a dais with the poneglyph and two gold blocks; no focal point and nothing golden; the vault ladder
  hung in mid-air.
- **Changes:** composition round an axis: the north **gate** (two battered 5x5 pylons with the golden sun of Shandora
  inlaid, stepped caps with gold capstones, a lintel broken in the middle with its fallen piece on the avenue) — the
  **avenue** of golden-stone (sandstone column) pillars, some intact with gold capitals, some broken, one fallen — the
  round **plaza** paved in rings with the poneglyph on its stepped dais (gold-capped corner posts) — the stepped
  **temple** (three tiers with gold inlay, grand stair between cheek walls, braziers) whose **gold-pillared summit shrine**
  (golden sun on the back wall, gilded altar, chest, a stepped roof with a gold crown) is the focal point. Four ruined
  houses (fieldstone and ashlar, round-headed doorways, slit windows; one still roofed with its stone dome and
  furnished, three collapsed toward the back corner with rubble), giant buttress-rooted jungle trees and smaller trees,
  moss, ferns and vines over the walls; breaches in the city wall with rubble heaps. The **vault** beneath the dais now
  has a proper shaft (cracked flagstone cover, ladder against the vault wall), a gold-veined floor, stacked gold and raw
  gold, a chiselled plinth for the two treasure chests and a lantern.
- **Kit blocks:** fieldstone (walls, house plinths, floors), sandstone column, plank table.
- **Gameplay kept:** `poneglyph` marker on the poneglyph, `treasure` marker at the vault chests; 3 `npc` ruin_guardian;
  4 chests `LOOT_RUIN` (three houses + the temple shrine; the fourth, collapsed house keeps only a pot, so the count is
  unchanged) and 2 chests `LOOT_TREASURE` (vault).
- **Status:** rebuilt.

## golden_belfry — Giant Jack and the Golden Bell (sky: `upper_yard`)
- **Type / role:** the colossal beanstalk with the ruined bell tower of upper Shandora and the Golden Bell at its crown.
- **Footprint:** 17 x 17 (unchanged). Height reduced from 152 to about 117 (crown at +97).
- **Old build:** a 140-tall jungle-log cylinder with moss bands; a one-plank-wide "spiral" that rose one block per step
  (only climbable by jumping, no edge); leaf rings; at the top a cage of gold blocks with a single vanilla bell as "the
  Golden Bell".
- **Changes:** a green beanstalk of three strands twisting round a dark core, with buttress roots and great leaves
  sprouting between the turns; a **walkable spiral way** of weathered planks rising in half-block steps (2 wide, a leaf
  hedge on the outside, a bracket under the inner edge) that comes up through the cloud at the crown; the **cloud**
  (cumulus balls round a paved plaza of upper Shandora with broken golden-stone columns); the **bell tower**: a stone
  room with an arched doorway, the treasure chest, candles and a ladder up; a gold band; an open belfry with round
  arches on all four sides and a balustrade; a corbelled cornice and a stepped crown with a gold cap. The **Golden Bell**
  is a 5-wide gold bell (crown, waist, flared lip, hollow) with a real bell hung inside as the clapper, so players who
  climb the ladder can stand under it and ring it.
- **Kit blocks:** weathered planks and slabs, sandstone column.
- **Gameplay kept:** `npc` master (bell-tower room), chest `LOOT_TREASURE` + `treasure` marker.
- **Status:** rebuilt.

## sacrificial_altar — the Upper Yard altar (sky: `upper_yard`)
- **Type / role:** the stepped stone altar of the sky priests' trials.
- **Footprint:** 17 x 17 (unchanged).
- **Old build:** three stacked slabs of stone (9x9, 7x7, 3x3) with a stone-brick-wall stick and lightning rod; the
  chest on the 3x3 top was only reachable by jumping up the tiers; no interior.
- **Changes:** a three-tier stepped platform (fieldstone base, weathered ashlar, polished summit) with clean lips and a
  **grand stair** between cheek walls to the summit; the offering table (chiselled stone with gold ends) carrying the
  chest; an obelisk with a gold cap and Enel's lightning rod; soul-fire braziers and brass lanterns; corner obelisks with
  soul lanterns; a fieldstone apron; vines; and a small **crypt of offerings** inside the base entered from the back
  (bones, a skull, candles, gold, a pot, a soul lantern).
- **Kit blocks:** fieldstone, fieldstone wall, brass lantern.
- **Gameplay kept:** `npc` ruin_guardian at the foot of the stair, chest `LOOT_SHRINE` + `secret` marker on the altar.
- **Status:** rebuilt.

## weather_lab — Weatheria's laboratory (sky: `weatheria`)
- **Type / role:** the weather scientists' lab on the floating island.
- **Footprint:** 21 x 21 (unchanged).
- **Old build:** a 15x15 white-concrete box with a glass dome, three panes on one wall, an iron door, furniture pushed
  into corners, and four "balloons" that were wool cubes on fence posts.
- **Changes:** a two-storey lab (sky plinth, white plaster, sky corner posts and floor band, windows on every side, a
  porch with a sky-scale canopy) under a **glass observatory dome** with white ribs and a railed roof deck with an
  anemometer and a lightning rod; ground floor laid out as the weather laboratory (brewing stand, cauldron of rain
  samples, cartography table, lectern, loom of charts, chest and barrel); a stair up to the observatory floor (books,
  a bed, a desk, the telescope under the dome). Two **science huts ride their own clouds** (a cumulus under each, cloud
  steps down), each held by a gored, teardrop **striped balloon** on ropes; a tethered weather balloon; a rain gauge.
- **Kit blocks:** white/sky plaster, sky roof tiles (hip roofs, canopy), plank table, goods shelf, brass lantern.
- **Gameplay kept:** 2 `npc` scholar, chest `LOOT_SHRINE`, barrel `LOOT_SHOP`.
- **Status:** rebuilt.

## cloud_end_gate — the Cloud End (sky: `cloud_end`)
- **Type / role:** the edge of the White Sea; a guide warns sailors of the long way down.
- **Footprint:** 13 x 11 (unchanged).
- **Old build:** two 2x3 cloud blocks with a cloud lintel and a froglight strip; a sign; the guide on bare ground.
- **Changes:** a weathered cloud arch (cloud pillars round sky-plaster cores, a cumulus lintel) carrying a sky plaque
  with a gold arrow pointing down, a warning bell and red paper lanterns; a quartz road; a roofed **lookout** (white posts,
  sky-scale hip roof, benches facing the edge, barrel, rope coil, lantern); a signpost and red-lantern lamp posts.
- **Kit blocks:** white/sky plaster, sky roof tiles, plank bench, rope coil, red paper lantern, brass lantern.
- **Gameplay kept:** `npc` canal_guide.
- **Status:** rebuilt (small piece; deliberately modest).

## ryugu_palace — Ryugu Palace (deep sea: `fishman_island`)
- **Type / role:** the royal palace crowning Fish-Man Island; throne room, royal guard, treasury.
- **Footprint:** 43 x 43 (unchanged).
- **Old build:** a crenellated prismarine curtain wall (a European castle), four cylinder towers with tube-coral domes,
  a prismarine box keep with a glass dome; a gold slab as throne. None of Ryugu's identity: no shell dome, no coral
  palette, no bubble.
- **Changes:** the palace now sits in **its own bubble** (a skin of bubble membrane — no collision — over the whole
  court, as in canon) on a prismarine podium with a grand stair. The **great hall** is a drum (salmon plaster, white
  marble pilasters, tall windows, white frieze and cornice) under a **ribbed scallop-shell dome** (white ribs over
  salmon, scalloped rim, smoothed with stairs) crowned with a gold collar and a **pearl**. A tall **entrance pavilion**
  (marble columns, round-headed portal with a gold keystone, glazed bays, a balustraded roof) carries a big **shell
  crest**. Two wings with sky-scale hip roofs hold the princess's chambers (her great bed, vanity, flowers) and the
  banquet hall (long table, kitchen corner). Four slender **coral towers** (white banded salmon, windows, balcony rings,
  sky-scale onion caps with gold finials) frame the podium. Inside the hall: carpet to the **throne on a golden dais**
  before a gold-and-white shell fan with a glowing pearl, dark prismarine columns with glowing capitals, a sea-lantern
  chandelier, the two treasury chests. A sea gate of coral pillars and a salmon arch stands at the bubble's edge;
  coral-tree gardens and lamps in the court.
- **Kit blocks:** salmon/white plaster (+stairs/slabs), marble column, sky roof tiles (hip roofs, onions), flower
  planter, goods shelf, brass lantern.
- **Gameplay kept:** `npc` noble (before the throne), 4 `npc` fishman_guard (2 in the hall, 2 at the sea gate), 2 chests
  `LOOT_TREASURE` + `treasure` marker.
- **Status:** rebuilt.
- **Limitations:** the palace bubble is decorative (inside Fish-Man Island's own air dome); coral blocks used as
  decoration may turn into dead coral if a neighbouring block is changed (they are never adjacent to walkways).

## mermaid_cove — the Mermaid Cove and Café (deep sea: `fishman_island`, at the harbour line)
- **Type / role:** lagoon where the merfolk gather, with the Mermaid Café.
- **Footprint:** 27 x 27, sea level (unchanged).
- **Old build:** a ring of sandstone/coral columns round a lagoon, three sandstone posts, and a 7x6 prismarine box with a
  slab lid as the café.
- **Changes:** a lagoon (real sea water at and below the harbour line, sandy floor with coral, seagrass and sea pickles)
  open to the sea on the north, inside a rounded rocky rim with sea stacks; three rocks for the merfolk to sit on. The
  **Mermaid Café** is a giant **pink-and-white scallop shell** (a quarter-sphere bandshell with a scalloped lip and a
  pearl lamp) opening onto a **deck on pilings** over the lagoon: bar counter, shelves of bottles, tavern barrel, tables
  with stools and candles, paper lanterns, a checkered floor; on the deck parasol tables and a jetty with steps down
  into the water, a bollard and rope.
- **Kit blocks:** salmon/white plaster, dock planks (+stairs), weathered beam pilings, shop counter, goods shelf, plank
  table, bollard, rope coil, white paper lantern, red awning (parasols).
- **Gameplay kept:** `npc` bartender (behind the bar), 2 `npc` resident (on the rocks), `npc` child (on the deck);
  barrel `LOOT_TAVERN`.
- **Status:** rebuilt.

## gyoncorde_plaza — Gyoncorde Plaza (deep sea: `fishman_island`)
- **Type / role:** the great square: speeches, market, notice boards.
- **Footprint:** 29 x 29 (unchanged).
- **Old build:** a flat ring-patterned pavement, a one-block-deep ring "fountain" with a wall stick, four lamp pillars,
  the quest board one oak plank with a sign.
- **Changes:** a mosaic of rings and rays; a **three-tier fountain** (wide basin with a rim course, two water-filled
  bowls on a coral column, sea lantern and a pearl crown); the **speakers' stage** on the south under a sky-and-white
  **shell canopy** with a lectern and lamp columns; an entrance arch of mint plaster with a gold keystone; a roofed
  **quest board** under it and a roofed **bounty board** on the west; a **market row** of two stalls with striped
  awnings, counters and shelves; lamps round the square, benches and coral trees.
- **Kit blocks:** mint/sky/salmon/white plaster (+stairs), sky roof tile slabs, marble column, shop counter, goods
  shelf, blue/green awnings, cargo crate and sacks, plank bench.
- **Gameplay kept:** `quest_board` and `bounty_board` markers (on their boards), `npc` merchant (in a stall), resident,
  fishman_guard, child.
- **Status:** rebuilt.
- **Limitations:** no loot chest (as before).

## sea_forest_graveyard — the Sea Forest (deep sea: `sea_forest`, in its own air dome)
- **Type / role:** ship graveyard under coral trees, Queen Otohime's memorial, the poneglyph (quest fmi_5).
- **Footprint:** 37 x 37 (unchanged).
- **Old build:** a 3-radius brain-coral cylinder with six straight rays of coral, two one-block-high plank outlines as
  "hulls", six grave walls, a poneglyph pillar; no shelter or memorial.
- **Changes:** a giant **coral tree** (pale gnarled trunk with buttress roots, upswept limbs, crowns of living coral and
  a few lamps) and a smaller one, plus coral trees; a **half-sunk galleon** (planked hull with a wale stripe, ribs bare
  amidships, sterncastle with rail, window and lantern, a standing mast with yard and torn sail, a broken mast fallen
  across the sand, bowsprit, sand drifted against it) and a smaller **brig** with its bow pushed up on the sand;
  **Queen Otohime's memorial** (quartz platform, marble columns, a salmon dome with white ribs, a memorial stone with a
  glowing pearl, candles and flowers, the shrine chest); rows of sailors' graves (crosses and stones with candles); the
  **keeper's hut** of ship's timbers (dark beams, plank walls, gable roof, door, windows, bed, table, barrel, fishing
  net); scattered cargo, a cannon, the poneglyph on a stone plinth.
- **Kit blocks:** weathered planks (+stairs/slabs), dark beam, marble column, salmon/white plaster (+stairs/slabs),
  plank table, fishing net, rope coil, deck cannon, cargo crate/sacks.
- **Gameplay kept:** `npc` keeper, `poneglyph` marker on the poneglyph, `treasure` marker, chests: `LOOT_SHRINE`
  (memorial) and 2 x `LOOT_WRECK` (galleon, brig).
- **Status:** rebuilt.

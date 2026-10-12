# Grand Line Odyssey — architectural art direction

A one-page style guide for every structure the mod generates. It's derived from what the world actually contains: 15 building
styles (`world/build/BuildStyle`), the canon islands' set pieces (`world/landmark/*`) and the settlement building types
(`world/settlement/Buildings`).

**Goal:** an explorable One Piece world that looks like it was built by a good Minecraft build team. That means:

- distinct islands, readable silhouettes, real roofs, furnished interiors and harbours that work;
- colourful, slightly exaggerated and deliberately composed;
- still coherent as Minecraft at 16×16 texture density.

## Rules for every building

1. **Massing first.** Each building gets one primary form, supporting forms such as wings, porches, towers or terraces, and a focal point.
   - Don't use oversized empty boxes.
   - Don't use a uniform box with a decorative strip pasted on it.
2. **Scale.**
   - Storey height is 4 blocks: 3 clear plus the floor above. Doorways are 3 tall, so giant races fit.
   - Rooms are sized for people, not cathedrals, unless the building is a monument.
3. **Façade depth.** Use these to give the façade depth:
   - corner posts and beams;
   - a stone plinth course;
   - a beam or trim band at each floor line;
   - windows with sills or planters and shutters;
   - door hoods, porches and eaves with brackets.

   Side and rear walls get windows too.
4. **Roofs are geometry.**
   - Each roof is a gable, low-pitch slab, hip, flared or flat-with-parapet roof (`Arch.gable/hip/flat/leanTo`), with an overhang and a ridge cap.
   - Gable triangles are filled.
   - Crenellations are only for fortifications.
5. **Foundations.**
   - Fill down to the terrain under the footprint (`Builder.foundation`).
   - Steps are also founded, and doors meet a step.
   - Docks sit at sea level + 1 on pilings.
6. **Interiors.**
   - Every accessible floor gets a layout, with stairs (not ladders) where there's room.
   - Furniture matches the building's job (see the per-type notes in `Buildings.java`).
   - Gameplay markers stay: NPC roles, `bounty_board`, `quest_board`, `cell`, `town_center`, `ship_spawn` and loot chests.
7. **Edit for clarity.** Use at most three wall materials per building, and no noisy mixing.
8. **Connected blocks.** World generation runs no neighbour updates, so set the connections explicitly on panes, bars, fences and walls (`Arch.along / Arch.connect`).

## Materials: the architectural kit (`registry/ArchBlocks`, 91 blocks)

| Family | Blocks | Typical use |
|---|---|---|
| Plaster | white, cream, ochre, salmon, sky, mint (+ stairs, slabs) | walls of towns; coloured stucco for Water 7, Whole Cake, Fish-Man Island |
| Timber framing | timber frame (plain, crossed, braced, studded) | upper storeys of East Blue, Loguetown and Drum houses |
| Beams | oak, dark timber, weathered (pillars) | corner posts, floor-line beams, hoists, Wano framing |
| Planks | weathered planks, dock planks (+ stairs, slabs) | docks, sheds, shipyards, ruins |
| Roof tiles | terracotta barrel, slate, teal glaze, kawara, sky scale, thatch (+ stairs, slabs) | regional roofs |
| Stone | dressed sandstone, pale masonry, fieldstone, harbour stone (+ stairs, slabs, walls) | Alabasta, Marine, plinths, quays |
| Columns | sandstone, marble | porticos, palaces, Marine entrances |
| Openings | shutters (green, blue, red, oak), lattice window, mullioned window | windows |
| Canvas | red, blue, green, yellow striped awnings | shopfronts, market stalls, desert terraces |
| Props | cargo crate, sacks, rope coil, bollard, deck cannon, shop counter, goods shelf, plank table, bench, fishing net, window planter | storytelling and furniture |
| Lights | red and white paper lanterns, brass lantern | Wano and jungle; towns and Marines |

## Style families (BuildStyle → look)

| Style | Where | Walls | Roof | Openings and accents |
|---|---|---|---|---|
| VILLAGE (coastal East Blue) | Dawn, Syrup, Cocoyasi and most small towns | white, cream or ochre plaster on fieldstone; braced timber upper storey | steep gable: terracotta, teal or slate, varying by house | green, blue or red shutters, window planters, brass lanterns, smoking chimneys |
| STONE_TOWN (Loguetown and old towns) | stone towns | fieldstone ground floor; timber-framed upper floor | slate gable with eave brackets | oak or red shutters, dark beams |
| DESERT (Alabasta) | desert islands | cream or ochre plaster with dressed-sandstone corners and bands | flat roof, low parapet and cornice, roof terraces, domes on civic buildings | lattice windows, striped awnings, sandstone columns |
| SNOW (Drum) | winter islands | white plaster over fieldstone; crossed timber upper floor | steep slate, heavy eaves | red shutters, chimneys always smoking |
| JUNGLE | tropical isles | bamboo planks on jungle-log frames, raised on stilts | thatch | lattice windows, paper lanterns |
| WANO | Wano | white plaster between dark timber | flared kawara hip roofs, layered where tall | lattice screens, red paper lanterns |
| VENETIAN (Water 7) | canal cities | salmon, ochre, sky or cream stucco on harbour stone | low-pitch terracotta (half-block slab steps) | green shutters, iron balconies, planters |
| CANDY (Whole Cake) | Totto Land | pastel stucco | sky-scale or teal roofs | pink glass, red shutters, planters |
| MARINE | Marine bases and posts | pale masonry, white plaster upstairs, blue bands | slate hip | blue shutters and awnings, marble columns, flag masts |
| GOTHIC (Thriller Bark) | dark isles | deepslate bricks, weathered planks | steep slate | purple glass, soul lanterns |
| FUTURE (Egghead) | future isles | white and sky plaster, light concrete frame | flat | cyan trim, sea lanterns, glass |
| GIANT (Elbaf) | giant isles | weathered planks on spruce logs | thatch with brackets | lattice windows |
| PALACE | royal islands | pale masonry, white plaster, marble columns | teal hip | blue shutters, gold trim |
| CLOUD (Skypiea) | sky dimension | white plaster, sky corners | sky-scale hip | blue glass, froglights, gold |
| CORAL (Fish-Man Island) | deep-sea domes | mint, sky or salmon plaster on prismarine | sky-scale hip | blue glass, sea lanterns |

## Canon landmarks

Landmarks must keep the identity of the canon location: its recognisable silhouette, roofline, colours and signature features. Translate those into
block geometry.

- **Marine HQ:** a tiered white fortress with blue bands and the "MARINE" kanji tower.
- **Alubarna:** a sandstone palace with onion domes and towers.
- **Water 7:** a fountain tower over a canal city.
- **Enies Lobby:** a white courthouse tower, with the Gates of Justice in the sea.
- **Baratie:** a fish-shaped restaurant ship.
- **Arlong Park:** a walled compound with a central tower.
- **Wano:** a flared-roof castle on a cliff.

Each one should read clearly from a distance and from street level.

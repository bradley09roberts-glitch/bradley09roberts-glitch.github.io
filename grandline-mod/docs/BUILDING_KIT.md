# Architectural building kit

There are 91 blocks in 31 families, all registered in `registry/ArchBlocks` (namespace `grandline`). They appear in the creative tab "Grand Line Architecture".

One spec, `tools/assetgen/arch_blocks.py`, generates everything for them:

- the Java registration, 16×16 textures, block and item models and block states;
- loot tables (slabs drop two when double), recipes (crafting plus stonecutting for stone and tile families), mineable and shape tags, and English names.

Textures are original pixel art painted by code (`tools/assetgen/arch_textures.py`).

"Used in" counts the structures whose AFTER dump contains the block. It covers settlement buildings in every style, landmarks, secrets and ship interiors; streets and piers are stamped during generation and are listed separately.

| family | block | kind | used in |
|---|---|---|---|
| white_plaster | `white_plaster` (White Plaster) | cube | 142 structures (e.g. inn__village, church__village, church__cloud, church__snow) |
| white_plaster | `white_plaster_stairs` (White Plaster Stairs) | stairs | kit only (available to players) |
| white_plaster | `white_plaster_slab` (White Plaster Slab) | slab | kit only (available to players) |
| cream_plaster | `cream_plaster` (Cream Plaster) | cube | 9 structures (e.g. church__desert, mansion__desert, inn__desert, mansion__venetian) |
| cream_plaster | `cream_plaster_stairs` (Cream Plaster Stairs) | stairs | kit only (available to players) |
| cream_plaster | `cream_plaster_slab` (Cream Plaster Slab) | slab | kit only (available to players) |
| ochre_plaster | `ochre_plaster` (Ochre Plaster) | cube | 9 structures (e.g. church__venetian, house_large__desert, warehouse__venetian, shop__desert) |
| ochre_plaster | `ochre_plaster_stairs` (Ochre Plaster Stairs) | stairs | kit only (available to players) |
| ochre_plaster | `ochre_plaster_slab` (Ochre Plaster Slab) | slab | kit only (available to players) |
| salmon_plaster | `salmon_plaster` (Salmon Plaster) | cube | 15 structures (e.g. tavern__candy, inn__venetian, mansion__candy, house__candy) |
| salmon_plaster | `salmon_plaster_stairs` (Salmon Plaster Stairs) | stairs | kit only (available to players) |
| salmon_plaster | `salmon_plaster_slab` (Salmon Plaster Slab) | slab | kit only (available to players) |
| sky_plaster | `sky_plaster` (Sky Blue Plaster) | cube | 28 structures (e.g. mansion__coral, warehouse__candy, tavern__venetian, house__venetian) |
| sky_plaster | `sky_plaster_stairs` (Sky Blue Plaster Stairs) | stairs | kit only (available to players) |
| sky_plaster | `sky_plaster_slab` (Sky Blue Plaster Slab) | slab | kit only (available to players) |
| mint_plaster | `mint_plaster` (Mint Plaster) | cube | 12 structures (e.g. inn__candy, church__coral, church__candy, house_large__candy) |
| mint_plaster | `mint_plaster_stairs` (Mint Plaster Stairs) | stairs | kit only (available to players) |
| mint_plaster | `mint_plaster_slab` (Mint Plaster Slab) | slab | kit only (available to players) |
| timber_frame | `timber_frame` (Timber Frame) | cube | 3 structures (e.g. mansion__snow, house_large__stone_town, house_small__stone_town) |
| timber_frame | `timber_frame_cross` (Crossed Timber Frame) | cube | 8 structures (e.g. inn__stone_town, tavern__snow, house__snow, warehouse__snow) |
| timber_frame | `timber_frame_diagonal` (Braced Timber Frame) | cube | 7 structures (e.g. tavern__stone_town, tavern__village, shop__village, warehouse__village) |
| timber_frame | `timber_frame_studs` (Studded Timber Frame) | cube | 5 structures (e.g. house_large__snow, shop__snow, inn__snow, house__stone_town) |
| oak_beam | `oak_beam` (Oak Beam) | pillar | 32 structures (e.g. mansion__village, tavern__village, house_large__village, inn__village) |
| dark_beam | `dark_beam` (Dark Timber Beam) | pillar | 75 structures (e.g. mansion__wano, mansion__stone_town, mansion__gothic, mansion__snow) |
| weathered_beam | `weathered_beam` (Weathered Beam) | pillar | 2 structures (e.g. lost_shrine, hidden_cache) |
| weathered_planks | `weathered_planks` (Weathered Planks) | cube | 54 structures (e.g. shipwright__snow, shipwright__gothic, shipwright__palace, shipwright__future) |
| weathered_planks | `weathered_plank_stairs` (Weathered Plank Stairs) | stairs | 7 structures (e.g. shipwright__palace, shipwright__future, shipwright__coral, shipwright__wano) |
| weathered_planks | `weathered_plank_slab` (Weathered Plank Slab) | slab | 7 structures (e.g. shipwright__palace, shipwright__future, shipwright__coral, shipwright__wano) |
| dock_planks | `dock_planks` (Dock Planks) | cube | 28 structures (e.g. mansion__jungle, tavern__jungle, inn__jungle, shipwright__palace) |
| dock_planks | `dock_plank_stairs` (Dock Plank Stairs) | stairs | kit only (available to players) |
| dock_planks | `dock_plank_slab` (Dock Plank Slab) | slab | kit only (available to players) |
| terracotta_roof_tiles | `terracotta_roof_tiles` (Terracotta Roof Tiles) | cube | 14 structures (e.g. inn__village, house_large__village, warehouse__village, inn__venetian) |
| terracotta_roof_tiles | `terracotta_roof_tile_stairs` (Terracotta Roof Tile Stairs) | stairs | 19 structures (e.g. mansion__venetian, dojo__venetian, inn__village, house_large__village) |
| terracotta_roof_tiles | `terracotta_roof_tile_slab` (Terracotta Roof Tile Slab) | slab | 24 structures (e.g. tavern__venetian, shipwright__venetian, inn__venetian, warehouse__venetian) |
| slate_roof_tiles | `slate_roof_tiles` (Slate Roof Tiles) | cube | 63 structures (e.g. shipwright__snow, shipwright__gothic, warehouse__gothic, warehouse__snow) |
| slate_roof_tiles | `slate_roof_tile_stairs` (Slate Roof Tile Stairs) | stairs | 87 structures (e.g. mansion__stone_town, mansion__village, mansion__snow, mansion__marine) |
| slate_roof_tiles | `slate_roof_tile_slab` (Slate Roof Tile Slab) | slab | 72 structures (e.g. blacksmith__stone_town, blacksmith__snow, blacksmith__gothic, tavern__snow) |
| teal_roof_tiles | `teal_roof_tiles` (Teal Glazed Roof Tiles) | cube | 27 structures (e.g. warehouse__candy, tavern__candy, church__candy, shipwright__candy) |
| teal_roof_tiles | `teal_roof_tile_stairs` (Teal Glazed Roof Tile Stairs) | stairs | 29 structures (e.g. mansion__candy, mansion__palace, dojo__village, dojo__candy) |
| teal_roof_tiles | `teal_roof_tile_slab` (Teal Glazed Roof Tile Slab) | slab | 29 structures (e.g. tavern__candy, blacksmith__palace, tavern__palace, warehouse__candy) |
| kawara_roof_tiles | `kawara_roof_tiles` (Kawara Roof Tiles) | cube | 14 structures (e.g. warehouse__wano, tavern__wano, mansion__wano, blacksmith__wano) |
| kawara_roof_tiles | `kawara_roof_tile_stairs` (Kawara Roof Tile Stairs) | stairs | 16 structures (e.g. mansion__wano, inn__wano, dojo__wano, tavern__wano) |
| kawara_roof_tiles | `kawara_roof_tile_slab` (Kawara Roof Tile Slab) | slab | 16 structures (e.g. blacksmith__wano, tavern__wano, house_large__wano, dojo__wano) |
| sky_roof_tiles | `sky_roof_tiles` (Sky Scale Roof Tiles) | cube | 34 structures (e.g. inn__candy, house_large__candy, church__coral, church__cloud) |
| sky_roof_tiles | `sky_roof_tile_stairs` (Sky Scale Roof Tile Stairs) | stairs | 40 structures (e.g. mansion__cloud, mansion__coral, dojo__coral, dojo__cloud) |
| sky_roof_tiles | `sky_roof_tile_slab` (Sky Scale Roof Tile Slab) | slab | 40 structures (e.g. blacksmith__candy, blacksmith__cloud, blacksmith__coral, tavern__coral) |
| thatch | `thatch` (Thatch) | cube | 31 structures (e.g. inn__jungle, inn__giant, house_large__jungle, house_large__giant) |
| thatch | `thatch_stairs` (Thatch Stairs) | stairs | 35 structures (e.g. mansion__giant, mansion__jungle, dojo__giant, shipwright__giant) |
| thatch | `thatch_slab` (Thatch Slab) | slab | 35 structures (e.g. blacksmith__giant, blacksmith__jungle, tavern__giant, tavern__jungle) |
| dressed_sandstone | `dressed_sandstone` (Dressed Sandstone) | cube | 18 structures (e.g. mansion__desert, church__desert, inn__desert, tavern__desert) |
| dressed_sandstone | `dressed_sandstone_stairs` (Dressed Sandstone Stairs) | stairs | 16 structures (e.g. mansion__desert, tavern__desert, inn__desert, dojo__desert) |
| dressed_sandstone | `dressed_sandstone_slab` (Dressed Sandstone Slab) | slab | 4 structures (e.g. bounty_board__desert, well__desert, ruined_house__desert, watchtower__desert) |
| dressed_sandstone | `dressed_sandstone_wall` (Dressed Sandstone Wall) | wall | kit only (available to players) |
| pale_masonry | `pale_masonry` (Pale Masonry) | cube | 46 structures (e.g. church__marine, church__palace, mansion__marine, dojo__marine) |
| pale_masonry | `pale_masonry_stairs` (Pale Masonry Stairs) | stairs | 26 structures (e.g. marine_post__cloud, marine_post__giant, marine_post__stone_town, marine_post__jungle) |
| pale_masonry | `pale_masonry_slab` (Pale Masonry Slab) | slab | 15 structures (e.g. marine_post__cloud, marine_post__giant, marine_post__stone_town, marine_post__jungle) |
| pale_masonry | `pale_masonry_wall` (Pale Masonry Wall) | wall | kit only (available to players) |
| fieldstone | `fieldstone` (Fieldstone) | cube | 66 structures (e.g. church__stone_town, mansion__giant, mansion__snow, mansion__village) |
| fieldstone | `fieldstone_stairs` (Fieldstone Stairs) | stairs | kit only (available to players) |
| fieldstone | `fieldstone_slab` (Fieldstone Slab) | slab | kit only (available to players) |
| fieldstone | `fieldstone_wall` (Fieldstone Wall) | wall | kit only (available to players) |
| harbour_stone | `harbour_stone` (Harbour Stone) | cube | 45 structures (e.g. mansion__marine, mansion__venetian, marine_post__cloud, marine_post__giant) |
| harbour_stone | `harbour_stone_stairs` (Harbour Stone Stairs) | stairs | kit only (available to players) |
| harbour_stone | `harbour_stone_slab` (Harbour Stone Slab) | slab | kit only (available to players) |
| harbour_stone | `harbour_stone_wall` (Harbour Stone Wall) | wall | kit only (available to players) |
| sandstone_column | `sandstone_column` (Sandstone Column) | pillar | 1 structures (e.g. mansion__desert) |
| marble_column | `marble_column` (Marble Column) | pillar | 42 structures (e.g. mansion__palace, tavern__palace, inn__palace, house_large__palace) |
| shutters | `green_shutter` (Green Shutter) | trapdoor | 9 structures (e.g. tavern__village, tavern__venetian, mansion__venetian, inn__venetian) |
| shutters | `blue_shutter` (Blue Shutter) | trapdoor | 42 structures (e.g. marine_post__cloud, tavern__palace, marine_post__giant, marine_post__stone_town) |
| shutters | `red_shutter` (Red Shutter) | trapdoor | 14 structures (e.g. mansion__stone_town, tavern__snow, mansion__candy, tavern__candy) |
| shutters | `oak_shutter` (Oak Shutter) | trapdoor | 14 structures (e.g. tavern__gothic, mansion__gothic, house__stone_town, house__gothic) |
| windows | `lattice_window` (Lattice Window) | pane | 45 structures (e.g. mansion__jungle, mansion__giant, tavern__desert, mansion__wano) |
| windows | `mullion_window` (Mullioned Window) | pane | 81 structures (e.g. marine_post__cloud, marine_post__giant, marine_post__stone_town, marine_post__jungle) |
| awnings | `red_awning` (Red Striped Awning) | decor | 27 structures (e.g. tavern__desert, tavern__future, market_stall__village, market_stall__snow) |
| awnings | `blue_awning` (Blue Striped Awning) | decor | 25 structures (e.g. market_stall__desert, market_stall__marine, shop__village, shop__marine) |
| awnings | `green_awning` (Green Striped Awning) | decor | 10 structures (e.g. market_stall__candy, shop__stone_town, house_small__desert, inn__desert) |
| awnings | `yellow_awning` (Yellow Striped Awning) | decor | 5 structures (e.g. blacksmith__desert, blacksmith__future, shop__desert, shop__candy) |
| cargo | `cargo_crate` (Cargo Crate) | cube | 51 structures (e.g. warehouse__village, warehouse__future, warehouse__jungle, warehouse__giant) |
| cargo | `cargo_sacks` (Cargo Sacks) | decor | 50 structures (e.g. warehouse__village, warehouse__future, warehouse__jungle, warehouse__giant) |
| cargo | `rope_coil` (Rope Coil) | decor | 35 structures (e.g. warehouse__village, warehouse__future, warehouse__jungle, warehouse__giant) |
| dock | `bollard` (Mooring Bollard) | decor | 15 structures (e.g. shipwright__palace, shipwright__future, shipwright__giant, shipwright__coral) |
| dock | `deck_cannon` (Deck Cannon) | decor | 1 structures (e.g. shipwreck) |
| dock | `fishing_net` (Fishing Net) | decor | kit only (available to players) |
| furniture | `shop_counter` (Shop Counter) | decor | 75 structures (e.g. tavern__palace, tavern__snow, tavern__stone_town, tavern__coral) |
| furniture | `goods_shelf` (Goods Shelf) | decor | 15 structures (e.g. shop__coral, shop__future, shop__palace, shop__candy) |
| furniture | `plank_table` (Plank Table) | decor | 125 structures (e.g. tavern__palace, tavern__snow, tavern__stone_town, tavern__coral) |
| furniture | `plank_bench` (Plank Bench) | decor | 61 structures (e.g. plaza__village, plaza__coral, plaza__marine, plaza__palace) |
| furniture | `flower_planter` (Window Planter) | decor | 98 structures (e.g. mansion__candy, mansion__palace, mansion__venetian, mansion__village) |
| lanterns | `red_paper_lantern` (Red Paper Lantern) | lantern | 17 structures (e.g. tavern__wano, plaza__wano, dojo__wano, house_large__wano) |
| lanterns | `white_paper_lantern` (White Paper Lantern) | lantern | 19 structures (e.g. tavern__jungle, dojo__jungle, plaza__jungle, mansion__jungle) |
| lanterns | `brass_lantern` (Brass Lantern) | lantern | 173 structures (e.g. tavern__palace, tavern__snow, tavern__stone_town, tavern__desert) |

Pier and street pieces stamped by the world generator (not in the dumps): `dock_planks`, `weathered_beam` (pilings), `bollard`, `brass_lantern`, `cargo_crate`, `cargo_sacks`, `rope_coil`, `dressed_sandstone` and `dressed_sandstone_wall` (desert paving and lamps), and the paper lanterns (Wano and jungle street lamps).

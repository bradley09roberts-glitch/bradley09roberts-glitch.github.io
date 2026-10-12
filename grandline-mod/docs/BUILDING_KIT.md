# Architectural building kit

There are 91 blocks in 31 families, all registered in `registry/ArchBlocks` (namespace `grandline`). They appear in the creative tab "Grand Line Architecture".

One spec, `tools/assetgen/arch_blocks.py`, generates everything for them:

- the Java registration, 16×16 textures, block and item models and block states;
- loot tables (slabs drop two when double), recipes (crafting plus stonecutting for stone and tile families), mineable and shape tags, and English names.

Textures are original pixel art painted by code (`tools/assetgen/arch_textures.py`).

"Used in" counts the structures whose AFTER dump contains the block. It covers settlement buildings in every style, landmarks, secrets and ship interiors; streets and piers are stamped during generation and are listed separately.

| family | block | kind | used in |
|---|---|---|---|
| white_plaster | `white_plaster` (White Plaster) | cube | 176 structures (e.g. cake_chateau, royal_castle, marine_headquarters, alubarna_palace) |
| white_plaster | `white_plaster_stairs` (White Plaster Stairs) | stairs | 34 structures (e.g. auction_house, heavens_gate, rommel_keep, sea_forest_graveyard) |
| white_plaster | `white_plaster_slab` (White Plaster Slab) | slab | 19 structures (e.g. cake_chateau, abandoned_lab, mansion__future, ryugu_palace) |
| cream_plaster | `cream_plaster` (Cream Plaster) | cube | 22 structures (e.g. alubarna_palace, arlong_park, kuja_palace, rainbase_casino) |
| cream_plaster | `cream_plaster_stairs` (Cream Plaster Stairs) | stairs | 8 structures (e.g. rainbase_casino, alubarna_palace, nanohana_port, mansion__desert) |
| cream_plaster | `cream_plaster_slab` (Cream Plaster Slab) | slab | 5 structures (e.g. mansion__desert, dojo__desert, inn__desert, warehouse__desert) |
| ochre_plaster | `ochre_plaster` (Ochre Plaster) | cube | 17 structures (e.g. germa_fleet, church__venetian, auction_house, yuba_oasis) |
| ochre_plaster | `ochre_plaster_stairs` (Ochre Plaster Stairs) | stairs | 5 structures (e.g. shop__desert, house_large__desert, yuba_oasis, house_small__desert) |
| ochre_plaster | `ochre_plaster_slab` (Ochre Plaster Slab) | slab | 4 structures (e.g. house_large__desert, shop__desert, house_small__desert, hut__desert) |
| salmon_plaster | `salmon_plaster` (Salmon Plaster) | cube | 28 structures (e.g. corrida_colosseum, ryugu_palace, kyuka_resort, galley_la_docks) |
| salmon_plaster | `salmon_plaster_stairs` (Salmon Plaster Stairs) | stairs | 8 structures (e.g. corrida_colosseum, ryugu_palace, sea_forest_graveyard, house_large__coral) |
| salmon_plaster | `salmon_plaster_slab` (Salmon Plaster Slab) | slab | 2 structures (e.g. corrida_colosseum, sea_forest_graveyard) |
| sky_plaster | `sky_plaster` (Sky Blue Plaster) | cube | 39 structures (e.g. abandoned_lab, heavens_gate, future_lab, cricket_house) |
| sky_plaster | `sky_plaster_stairs` (Sky Blue Plaster Stairs) | stairs | 1 structures (e.g. mansion__coral) |
| sky_plaster | `sky_plaster_slab` (Sky Blue Plaster Slab) | slab | kit only (available to players) |
| mint_plaster | `mint_plaster` (Mint Plaster) | cube | 15 structures (e.g. inn__candy, church__coral, church__candy, house_large__candy) |
| mint_plaster | `mint_plaster_stairs` (Mint Plaster Stairs) | stairs | 6 structures (e.g. tavern__coral, house__coral, gyoncorde_plaza, dojo__coral) |
| mint_plaster | `mint_plaster_slab` (Mint Plaster Slab) | slab | kit only (available to players) |
| timber_frame | `timber_frame` (Timber Frame) | cube | 3 structures (e.g. mansion__snow, house_large__stone_town, house_small__stone_town) |
| timber_frame | `timber_frame_cross` (Crossed Timber Frame) | cube | 9 structures (e.g. mercenary_guild_hall, inn__stone_town, tavern__snow, house__snow) |
| timber_frame | `timber_frame_diagonal` (Braced Timber Frame) | cube | 8 structures (e.g. welcome_arch, tavern__stone_town, tavern__village, shop__village) |
| timber_frame | `timber_frame_studs` (Studded Timber Frame) | cube | 5 structures (e.g. house_large__snow, shop__snow, inn__snow, house__stone_town) |
| oak_beam | `oak_beam` (Oak Beam) | pillar | 36 structures (e.g. mansion__village, tavern__village, house_large__village, inn__village) |
| dark_beam | `dark_beam` (Dark Timber Beam) | pillar | 103 structures (e.g. flower_capital_castle, haunted_mansion, welcome_arch, galley_la_docks) |
| weathered_beam | `weathered_beam` (Weathered Beam) | pillar | 16 structures (e.g. junkyard_fort, nanohana_port, white_city, mermaid_cove) |
| weathered_planks | `weathered_planks` (Weathered Planks) | cube | 69 structures (e.g. golden_belfry, giant_duel_ground, shipwright__snow, shipwright__gothic) |
| weathered_planks | `weathered_plank_stairs` (Weathered Plank Stairs) | stairs | 10 structures (e.g. shipwright__palace, shipwright__future, shipwright__coral, shipwright__wano) |
| weathered_planks | `weathered_plank_slab` (Weathered Plank Slab) | slab | 10 structures (e.g. golden_belfry, shipwright__palace, shipwright__future, shipwright__coral) |
| dock_planks | `dock_planks` (Dock Planks) | cube | 43 structures (e.g. junkyard_fort, mansion__jungle, tavern__jungle, inn__jungle) |
| dock_planks | `dock_plank_stairs` (Dock Plank Stairs) | stairs | 23 structures (e.g. shipwright__palace, mermaid_cove, shipwright__future, shipwright__giant) |
| dock_planks | `dock_plank_slab` (Dock Plank Slab) | slab | 16 structures (e.g. welcome_arch, shipwright__palace, shipwright__future, shipwright__giant) |
| terracotta_roof_tiles | `terracotta_roof_tiles` (Terracotta Roof Tiles) | cube | 22 structures (e.g. royal_plateau, prodence_castle, cricket_house, inn__village) |
| terracotta_roof_tiles | `terracotta_roof_tile_stairs` (Terracotta Roof Tile Stairs) | stairs | 30 structures (e.g. royal_plateau, auction_house, welcome_arch, baratie) |
| terracotta_roof_tiles | `terracotta_roof_tile_slab` (Terracotta Roof Tile Slab) | slab | 33 structures (e.g. galley_la_docks, kyuka_resort, tavern__venetian, shipwright__venetian) |
| slate_roof_tiles | `slate_roof_tiles` (Slate Roof Tiles) | cube | 73 structures (e.g. haunted_mansion, rommel_keep, ruined_castle, shipwright__snow) |
| slate_roof_tiles | `slate_roof_tile_stairs` (Slate Roof Tile Stairs) | stairs | 97 structures (e.g. haunted_mansion, white_city, hilltop_mansion, explorers_monument) |
| slate_roof_tiles | `slate_roof_tile_slab` (Slate Roof Tile Slab) | slab | 85 structures (e.g. pirate_hive, endless_bridge, mercenary_guild_hall, blacksmith__stone_town) |
| teal_roof_tiles | `teal_roof_tiles` (Teal Glazed Roof Tiles) | cube | 37 structures (e.g. royal_castle, baratie, arlong_park, cake_chateau) |
| teal_roof_tiles | `teal_roof_tile_stairs` (Teal Glazed Roof Tile Stairs) | stairs | 40 structures (e.g. kuja_palace, royal_castle, marine_headquarters, oykot_palace) |
| teal_roof_tiles | `teal_roof_tile_slab` (Teal Glazed Roof Tile Slab) | slab | 37 structures (e.g. arlong_park, mary_geoise, tavern__candy, blacksmith__palace) |
| kawara_roof_tiles | `kawara_roof_tiles` (Kawara Roof Tiles) | cube | 19 structures (e.g. flower_capital_castle, lighthouse__wano, skull_dome, waterfall_gate) |
| kawara_roof_tiles | `kawara_roof_tile_stairs` (Kawara Roof Tile Stairs) | stairs | 20 structures (e.g. flower_capital_castle, mansion__wano, inn__wano, dojo__wano) |
| kawara_roof_tiles | `kawara_roof_tile_slab` (Kawara Roof Tile Slab) | slab | 20 structures (e.g. flower_capital_castle, skull_dome, blacksmith__wano, tavern__wano) |
| sky_roof_tiles | `sky_roof_tiles` (Sky Scale Roof Tiles) | cube | 40 structures (e.g. ryugu_palace, cloud_chapel, drum_castle, cake_chateau) |
| sky_roof_tiles | `sky_roof_tile_stairs` (Sky Scale Roof Tile Stairs) | stairs | 47 structures (e.g. mansion__cloud, mansion__coral, dojo__coral, dojo__cloud) |
| sky_roof_tiles | `sky_roof_tile_slab` (Sky Scale Roof Tile Slab) | slab | 44 structures (e.g. blacksmith__candy, blacksmith__cloud, blacksmith__coral, tavern__coral) |
| thatch | `thatch` (Thatch) | cube | 37 structures (e.g. kyuka_resort, giant_legs, giant_duel_ground, inn__jungle) |
| thatch | `thatch_stairs` (Thatch Stairs) | stairs | 40 structures (e.g. giant_duel_ground, giant_legs, mansion__giant, mansion__jungle) |
| thatch | `thatch_slab` (Thatch Slab) | slab | 41 structures (e.g. blacksmith__giant, blacksmith__jungle, tavern__giant, tavern__jungle) |
| dressed_sandstone | `dressed_sandstone` (Dressed Sandstone) | cube | 29 structures (e.g. kuja_palace, alubarna_palace, corrida_colosseum, rainbase_casino) |
| dressed_sandstone | `dressed_sandstone_stairs` (Dressed Sandstone Stairs) | stairs | 25 structures (e.g. corrida_colosseum, alubarna_palace, rainbase_casino, nanohana_port) |
| dressed_sandstone | `dressed_sandstone_slab` (Dressed Sandstone Slab) | slab | 7 structures (e.g. alubarna_palace, kuja_palace, corrida_colosseum, bounty_board__desert) |
| dressed_sandstone | `dressed_sandstone_wall` (Dressed Sandstone Wall) | wall | 6 structures (e.g. rainbase_casino, alubarna_palace, kuja_palace, nanohana_port) |
| pale_masonry | `pale_masonry` (Pale Masonry) | cube | 65 structures (e.g. gates_of_justice, royal_castle, gray_terminal, germa_fleet) |
| pale_masonry | `pale_masonry_stairs` (Pale Masonry Stairs) | stairs | 43 structures (e.g. royal_castle, gates_of_justice, marine_headquarters, tower_of_justice) |
| pale_masonry | `pale_masonry_slab` (Pale Masonry Slab) | slab | 19 structures (e.g. white_city, hilltop_mansion, royal_castle, marine_base) |
| pale_masonry | `pale_masonry_wall` (Pale Masonry Wall) | wall | 11 structures (e.g. mary_geoise, tower_of_justice, marine_headquarters, royal_castle) |
| fieldstone | `fieldstone` (Fieldstone) | cube | 90 structures (e.g. flower_capital_castle, rommel_keep, prodence_castle, shandora_ruins) |
| fieldstone | `fieldstone_stairs` (Fieldstone Stairs) | stairs | 30 structures (e.g. flower_capital_castle, skull_dome, explorers_monument, giant_duel_ground) |
| fieldstone | `fieldstone_slab` (Fieldstone Slab) | slab | 3 structures (e.g. twin_capes_lighthouse, welcome_arch, treasure_cache) |
| fieldstone | `fieldstone_wall` (Fieldstone Wall) | wall | 12 structures (e.g. mansion__jungle, mansion__stone_town, mansion__giant, flower_capital_castle) |
| harbour_stone | `harbour_stone` (Harbour Stone) | cube | 60 structures (e.g. galley_la_docks, gates_of_justice, corrida_colosseum, arlong_park) |
| harbour_stone | `harbour_stone_stairs` (Harbour Stone Stairs) | stairs | 9 structures (e.g. auction_house, water7_fountain, galley_la_docks, inn__venetian) |
| harbour_stone | `harbour_stone_slab` (Harbour Stone Slab) | slab | 1 structures (e.g. water7_fountain) |
| harbour_stone | `harbour_stone_wall` (Harbour Stone Wall) | wall | 4 structures (e.g. galley_la_docks, water7_fountain, mansion__coral, mansion__venetian) |
| sandstone_column | `sandstone_column` (Sandstone Column) | pillar | 7 structures (e.g. alubarna_palace, rainbase_casino, laugh_tale_ruins, shandora_ruins) |
| marble_column | `marble_column` (Marble Column) | pillar | 66 structures (e.g. gates_of_justice, royal_castle, mary_geoise, tower_of_justice) |
| shutters | `green_shutter` (Green Shutter) | trapdoor | 15 structures (e.g. galley_la_docks, kyuka_resort, royal_plateau, tavern__village) |
| shutters | `blue_shutter` (Blue Shutter) | trapdoor | 49 structures (e.g. marine_headquarters, royal_castle, tower_of_justice, marine_base) |
| shutters | `red_shutter` (Red Shutter) | trapdoor | 21 structures (e.g. rommel_keep, prodence_castle, welcome_arch, drum_castle) |
| shutters | `oak_shutter` (Oak Shutter) | trapdoor | 16 structures (e.g. tavern__gothic, mansion__gothic, house__stone_town, house__gothic) |
| windows | `lattice_window` (Lattice Window) | pane | 62 structures (e.g. alubarna_palace, rainbase_casino, kuja_palace, flower_capital_castle) |
| windows | `mullion_window` (Mullioned Window) | pane | 106 structures (e.g. revolutionary_hq, marine_headquarters, tower_of_justice, royal_castle) |
| awnings | `red_awning` (Red Striped Awning) | decor | 38 structures (e.g. auction_house, nanohana_port, alubarna_palace, tavern__desert) |
| awnings | `blue_awning` (Blue Striped Awning) | decor | 34 structures (e.g. nanohana_port, cloud_chapel, sea_train_station, mangrove_grove) |
| awnings | `green_awning` (Green Striped Awning) | decor | 14 structures (e.g. gray_terminal, nanohana_port, market_stall__candy, gyoncorde_plaza) |
| awnings | `yellow_awning` (Yellow Striped Awning) | decor | 12 structures (e.g. blacksmith__desert, blacksmith__future, nanohana_port, alubarna_palace) |
| cargo | `cargo_crate` (Cargo Crate) | cube | 84 structures (e.g. nanohana_port, tangerine_grove, galley_la_docks, ship_flagship) |
| cargo | `cargo_sacks` (Cargo Sacks) | decor | 85 structures (e.g. nanohana_port, alubarna_palace, windmill, ship_flagship) |
| cargo | `rope_coil` (Rope Coil) | decor | 50 structures (e.g. nanohana_port, germa_fleet, galley_la_docks, baratie) |
| dock | `bollard` (Mooring Bollard) | decor | 24 structures (e.g. nanohana_port, galley_la_docks, pirate_hive, marine_headquarters) |
| dock | `deck_cannon` (Deck Cannon) | decor | 13 structures (e.g. germa_fleet, marine_headquarters, pirate_hive, marine_base) |
| dock | `fishing_net` (Fishing Net) | decor | 5 structures (e.g. giant_duel_ground, cricket_house, nanohana_port, arlong_park) |
| furniture | `shop_counter` (Shop Counter) | decor | 102 structures (e.g. nanohana_port, pirate_hive, rainbase_casino, marine_headquarters) |
| furniture | `goods_shelf` (Goods Shelf) | decor | 39 structures (e.g. nanohana_port, shop__coral, shop__future, shop__palace) |
| furniture | `plank_table` (Plank Table) | decor | 172 structures (e.g. marine_headquarters, welcome_arch, cake_chateau, rainbase_casino) |
| furniture | `plank_bench` (Plank Bench) | decor | 98 structures (e.g. welcome_arch, water7_fountain, alubarna_palace, mary_geoise) |
| furniture | `flower_planter` (Window Planter) | decor | 126 structures (e.g. royal_castle, galley_la_docks, kyuka_resort, royal_plateau) |
| lanterns | `red_paper_lantern` (Red Paper Lantern) | lantern | 26 structures (e.g. kuja_palace, skull_dome, flower_capital_castle, welcome_arch) |
| lanterns | `white_paper_lantern` (White Paper Lantern) | lantern | 24 structures (e.g. kyuka_resort, welcome_arch, arlong_park, tavern__jungle) |
| lanterns | `brass_lantern` (Brass Lantern) | lantern | 219 structures (e.g. endless_bridge, marine_headquarters, rainbase_casino, alubarna_palace) |

Pier and street pieces stamped by the world generator (not in the dumps): `dock_planks`, `weathered_beam` (pilings), `bollard`, `brass_lantern`, `cargo_crate`, `cargo_sacks`, `rope_coil`, `dressed_sandstone` and `dressed_sandstone_wall` (desert paving and lamps), and the paper lanterns (Wano and jungle street lamps).

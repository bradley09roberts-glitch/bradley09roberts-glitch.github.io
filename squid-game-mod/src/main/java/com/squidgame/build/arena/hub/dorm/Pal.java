package com.squidgame.build.arena.hub.dorm;

/**
 * Block palette of the dormitory: pale institutional concrete, dark steel for every frame, pastel stripes taken from
 * the mod's own pastel blocks, and a few warm accents (lamps, brass, the prize pig's pink glass).
 */
final class Pal {
    private Pal() {
    }

    static final String AIR = "minecraft:air";

    // walls and floors
    static final String WHITE = "minecraft:white_concrete";
    static final String CALCITE = "minecraft:calcite";
    static final String DIORITE = "minecraft:polished_diorite";
    static final String LGRAY = "minecraft:light_gray_concrete";
    static final String GRAY = "minecraft:gray_concrete";
    static final String STONE = "minecraft:stone";
    static final String ANDESITE = "minecraft:andesite";
    static final String PANDESITE = "minecraft:polished_andesite";
    static final String SMOOTH = "minecraft:smooth_stone";
    static final String BRICKS = "minecraft:stone_bricks";
    static final String CRACKED = "minecraft:cracked_stone_bricks";

    // steel and blacks
    static final String STEEL = "minecraft:polished_deepslate";
    static final String STEEL_TILES = "minecraft:deepslate_tiles";
    static final String STEEL_BRICKS = "minecraft:deepslate_bricks";
    static final String BLACK = "minecraft:black_concrete";
    static final String BSTONE = "minecraft:polished_blackstone";
    static final String BSTONE_BRICKS = "minecraft:polished_blackstone_bricks";
    static final String BARS = "minecraft:iron_bars";
    static final String CHAIN = "minecraft:chain[axis=y]";
    static final String STEEL_WALL = "minecraft:polished_deepslate_wall";
    static final String STEEL_STAIRS = "minecraft:deepslate_brick_stairs";
    static final String STEEL_SLAB_BRICK = "minecraft:deepslate_brick_slab";
    static final String STEEL_SLAB_B = "minecraft:polished_deepslate_slab[type=bottom]";
    static final String STEEL_SLAB_T = "minecraft:polished_deepslate_slab[type=top]";

    // pastel stripes (mod blocks)
    static final String PINK = "squidgame:pastel_pink";
    static final String MINT = "squidgame:pastel_mint";
    static final String CREAM = "squidgame:pastel_cream";
    static final String YELLOW = "squidgame:pastel_yellow";
    static final String SKY = "squidgame:pastel_sky";
    static final String TILE_PINK = "squidgame:tile_pink";
    static final String TILE_WHITE = "squidgame:tile_white";
    static final String TILE_BLACK = "squidgame:tile_black";

    // lights
    static final String PANEL_WHITE = "squidgame:panel_light_white";
    static final String PANEL_WARM = "squidgame:panel_light_warm";
    static final String PANEL_PINK = "squidgame:panel_light_pink";
    static final String SEA = "minecraft:sea_lantern";
    static final String FROG = "minecraft:pearlescent_froglight[axis=y]";
    static final String LANTERN_HANG = "minecraft:lantern[hanging=true]";

    // bedding
    static final String SHEET_GREEN = "minecraft:green_carpet";
    static final String SHEET_LGRAY = "minecraft:light_gray_carpet";
    static final String SHEET_GRAY = "minecraft:gray_carpet";
    static final String PILLOW = "minecraft:white_carpet";

    // prize pig
    static final String PIG_GLASS = "minecraft:pink_stained_glass";
    static final String PIG_SOLID = "minecraft:pink_concrete";
    static final String PIG_DARK = "minecraft:magenta_concrete";
    static final String GOLD = "minecraft:gold_block";

    static String slabB(String id) {
        return id + "[type=bottom]";
    }

    static String slabT(String id) {
        return id + "[type=top]";
    }

    /** Stairs state: {@code facing} is the direction in which the stair ascends. */
    static String stair(String id, String facing, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    static String wall(String id) {
        return id;
    }
}

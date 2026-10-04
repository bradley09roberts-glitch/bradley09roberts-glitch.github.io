package com.squidgame.build.arena.tug;

/** Block palette of the Tug of War hall: poured concrete, black steel, planks, hazard paint and hard white light. */
final class Pal {
    private Pal() {
    }

    // concrete family (poured walls, floors)
    static final String CONC_L = "minecraft:light_gray_concrete";
    static final String CONC_D = "minecraft:gray_concrete";
    static final String BLACK = "minecraft:black_concrete";
    static final String WHITE = "minecraft:white_concrete";
    static final String STONE = "minecraft:stone";
    static final String SMOOTH = "minecraft:smooth_stone";
    static final String ANDESITE = "minecraft:polished_andesite";
    static final String BRICKS = "minecraft:stone_bricks";
    static final String CRACKED = "minecraft:cracked_stone_bricks";

    // steel and dark stone
    static final String STEEL = "minecraft:polished_blackstone";
    static final String STEEL_C = "minecraft:chiseled_polished_blackstone";
    static final String IRON = "minecraft:iron_block";
    static final String BARS = "minecraft:iron_bars";
    static final String DS_TILES = "minecraft:deepslate_tiles";
    static final String DS_CRACKED = "minecraft:cracked_deepslate_tiles";
    static final String DS_BRICKS = "minecraft:deepslate_bricks";
    static final String DS_POLISHED = "minecraft:polished_deepslate";
    static final String DS_CHISELED = "minecraft:chiseled_deepslate";
    static final String BS_POLISHED = "minecraft:polished_blackstone";
    static final String BS_BRICKS = "minecraft:polished_blackstone_bricks";
    static final String GRATE = "minecraft:oxidized_copper_grate";

    // hazard / team paint
    static final String YELLOW = "minecraft:yellow_concrete";
    static final String RED = "minecraft:red_concrete";
    static final String BLUE = "minecraft:blue_concrete";
    static final String ORANGE = "minecraft:orange_concrete";

    // timber
    static final String PLANK_D = "minecraft:dark_oak_planks";
    static final String PLANK_S = "minecraft:spruce_planks";

    // light
    static final String SEA = "minecraft:sea_lantern";
    static final String PANEL = "squidgame:panel_light_white";
    static final String PANEL_WARM = "squidgame:panel_light_warm";
    static final String SHROOM = "minecraft:shroomlight";
    static final String GLOW = "minecraft:glowstone";

    static final String AIR = "minecraft:air";

    static String hazardRow() {
        return YELLOW;
    }

    static String hazardIron() {
        return YELLOW;
    }

    static String log(String wood, char axis) {
        return "minecraft:" + wood + "[axis=" + axis + "]";
    }

    static String chain() {
        return "minecraft:chain[axis=y]";
    }

    static String chain(char axis) {
        return "minecraft:chain[axis=" + axis + "]";
    }

    static String stairs(String base, String facing, boolean top) {
        return "minecraft:" + base + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    static String slab(String base, boolean top) {
        return "minecraft:" + base + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    static String trapdoor(String base, String facing, boolean top, boolean open) {
        return "minecraft:" + base + "_trapdoor[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",open=" + open + "]";
    }

    /** Diagonal hazard stripes in yellow / black along coordinate {@code a} (stripe width 2). */
    static String hazard(int a) {
        return Math.floorMod(a, 4) < 2 ? YELLOW : BLACK;
    }

    /** Cheap deterministic hash in [0,1) from integer coordinates (stable weathering independent of build order). */
    static double hash(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h >>> 11) & 0xFFFFFFFFFFFFFL) / (double) (1L << 52);
    }
}

package com.squidgame.build.arena.bridge;

/**
 * Block palette of the Glass Bridge hall: black steel, dark concrete and deepslate, with guard-pink strips,
 * cold white spotlights and a green exit. Everything is a block-state string for the build DSL.
 */
final class Pal {
    private Pal() {
    }

    // dark concrete / steel family
    static final String BLK = "minecraft:black_concrete";
    static final String GRY = "minecraft:gray_concrete";
    static final String LGRY = "minecraft:light_gray_concrete";
    static final String WHITE = "minecraft:white_concrete";
    static final String DS = "minecraft:deepslate";
    static final String DST = "minecraft:deepslate_tiles";
    static final String DSB = "minecraft:deepslate_bricks";
    static final String DSC = "minecraft:cracked_deepslate_tiles";
    static final String DSBC = "minecraft:cracked_deepslate_bricks";
    static final String DSP = "minecraft:polished_deepslate";
    static final String DSCH = "minecraft:chiseled_deepslate";
    static final String BS = "minecraft:blackstone";
    static final String PBS = "minecraft:polished_blackstone";
    static final String PBSB = "minecraft:polished_blackstone_bricks";
    static final String PBSC = "minecraft:chiseled_polished_blackstone";
    static final String BASALT = "minecraft:smooth_basalt";
    static final String COAL = "minecraft:coal_block";
    static final String IRON = "minecraft:iron_block";
    static final String BARS = "minecraft:iron_bars";
    static final String TRAP_IRON = "minecraft:iron_trapdoor";

    // guard colours
    static final String PINK_PANEL = "squidgame:pastel_pink";
    static final String PINK_CONC = "minecraft:pink_concrete";
    static final String MAGENTA = "minecraft:magenta_concrete";

    // tiles
    static final String TILE_W = "squidgame:tile_white";
    static final String TILE_B = "squidgame:tile_black";
    static final String TILE_P = "squidgame:tile_pink";

    // lights
    static final String PINK_LIGHT = "squidgame:panel_light_pink";
    static final String WHITE_LIGHT = "squidgame:panel_light_white";
    static final String SEA = "minecraft:sea_lantern";
    static final String GREEN_LIGHT = "minecraft:verdant_froglight";
    /** Soft pink-white glowing froglight used for floor lines (a full block, safe under standing markers). */
    static final String PEARL = "minecraft:pearlescent_froglight";
    static final String SOUL_LANTERN = "minecraft:soul_lantern";

    static final String GLASS = "squidgame:bridge_glass";
    static final String SYM_CIRCLE = "squidgame:symbol_circle";
    static final String SYM_TRIANGLE = "squidgame:symbol_triangle";
    static final String SYM_SQUARE = "squidgame:symbol_square";

    static final String LIGHT15 = "minecraft:light[level=15]";

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

    static String ladder(String facing) {
        return "minecraft:ladder[facing=" + facing + "]";
    }

    static String lantern(boolean hanging) {
        return "minecraft:lantern[hanging=" + hanging + "]";
    }

    static String trapdoor(String base, String facing, boolean top, boolean open) {
        return "minecraft:" + base + "_trapdoor[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",open=" + open + "]";
    }

    /** Cheap deterministic integer hash used for position based weathering (independent of build order). */
    static int hash(int x, int y, int z) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ z * 0x9e3779b1;
        h ^= h >>> 15;
        h *= 0x2c1b3c6d;
        h ^= h >>> 12;
        h *= 0x297a2d39;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    /** Uniform pseudo random in [0,100) for a position and salt. */
    static int pct(int x, int y, int z, int salt) {
        return hash(x + salt * 131, y, z - salt * 977) % 100;
    }
}

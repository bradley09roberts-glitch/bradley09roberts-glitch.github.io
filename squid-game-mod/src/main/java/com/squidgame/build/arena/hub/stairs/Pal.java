package com.squidgame.build.arena.hub.stairs;

/** The candy palette of the stairway hall and block-state string helpers. */
final class Pal {
    private Pal() {
    }

    static final int PINK = 0, MINT = 1, YELLOW = 2, SKY = 3, LILAC = 4, PEACH = 5, CREAM = 6;
    private static final String[] NAME = {"pink", "mint", "yellow", "sky", "lilac", "peach", "cream"};

    static final String AIR = "minecraft:air";
    static final String WHITE = "minecraft:white_concrete";
    static final String QUARTZ = "minecraft:quartz_block";
    static final String TILE_WHITE = "squidgame:tile_white";
    static final String LIGHT_WHITE = "squidgame:panel_light_white";
    static final String LIGHT_WARM = "squidgame:panel_light_warm";
    static final String LIGHT_PINK = "squidgame:panel_light_pink";
    static final String SEA = "minecraft:sea_lantern";
    static final String GLOW = "minecraft:glowstone";
    static final String SHROOM = "minecraft:shroomlight";
    static final String ROD = "minecraft:end_rod[facing=up]";
    static final String PANE = "minecraft:white_stained_glass_pane";

    static String name(int i) {
        return NAME[Math.floorMod(i, 7)];
    }

    static String block(int i) {
        return "squidgame:pastel_" + NAME[Math.floorMod(i, 7)];
    }

    static String stair(int i, String facing, boolean top) {
        return "squidgame:pastel_" + NAME[Math.floorMod(i, 7)] + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom")
                + ",shape=straight]";
    }

    static String stair(int i, String facing, boolean top, String shape) {
        return "squidgame:pastel_" + NAME[Math.floorMod(i, 7)] + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom")
                + ",shape=" + shape + "]";
    }

    static String slab(int i, boolean top) {
        return "squidgame:pastel_" + NAME[Math.floorMod(i, 7)] + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    /** Next colour of the hue wheel (pink, peach, yellow, mint, sky, lilac) for gradients; 6 (cream) maps to itself. */
    static int wheel(int k) {
        int[] w = {PINK, PEACH, YELLOW, MINT, SKY, LILAC};
        return w[Math.floorMod(k, 6)];
    }
}

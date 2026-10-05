package com.squidgame.build.arena.hub.control;

/** Palette and state helpers of the control room: black marble and dark tile, pink / teal / red accent light. */
final class Pal {
    private Pal() {
    }

    static final String AIR = "minecraft:air";

    static final String TILE_BLACK = "squidgame:tile_black";
    static final String TILE_PINK = "squidgame:tile_pink";
    static final String TILE_WHITE = "squidgame:tile_white";
    static final String LIGHT_PINK = "squidgame:panel_light_pink";
    static final String LIGHT_WHITE = "squidgame:panel_light_white";
    static final String LIGHT_WARM = "squidgame:panel_light_warm";
    static final String SYM_CIRCLE = "squidgame:symbol_circle";
    static final String SYM_TRIANGLE = "squidgame:symbol_triangle";
    static final String SYM_SQUARE = "squidgame:symbol_square";

    static final String BLACK = "minecraft:black_concrete";
    static final String GRAY = "minecraft:gray_concrete";
    static final String PINK = "minecraft:pink_concrete";
    static final String WHITE = "minecraft:white_concrete";
    static final String PBS = "minecraft:polished_blackstone";
    static final String PBS_BRICKS = "minecraft:polished_blackstone_bricks";
    static final String DEEPSLATE_TILES = "minecraft:deepslate_tiles";
    static final String POLISHED_DEEPSLATE = "minecraft:polished_deepslate";
    static final String SEA = "minecraft:sea_lantern";
    static final String GLASS_BLACK = "minecraft:black_stained_glass";
    static final String GLASS_CYAN = "minecraft:cyan_stained_glass";
    static final String GLASS_PINK = "minecraft:pink_stained_glass";
    static final String GLASS_RED = "minecraft:red_stained_glass";
    static final String GLASS_BLUE = "minecraft:light_blue_stained_glass";
    static final String RED = "minecraft:red_concrete";
    static final String TEAL = "minecraft:cyan_concrete";

    static String stairs(String id, String facing, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    static String slab(String id, boolean top) {
        return id + "[type=" + (top ? "top" : "bottom") + "]";
    }

    static String trapdoor(String id, String facing, boolean open, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",open=" + open + "]";
    }

    static String monitor(String facing) {
        return "squidgame:monitor[facing=" + facing + "]";
    }

    static String chain(String axis) {
        return "minecraft:chain[axis=" + axis + "]";
    }

    static String endRod(String facing) {
        return "minecraft:end_rod[facing=" + facing + "]";
    }

    static String lantern(boolean hanging) {
        return "minecraft:lantern[hanging=" + hanging + "]";
    }

    static String bed(String color, String facing, boolean head) {
        return "minecraft:" + color + "_bed[facing=" + facing + ",part=" + (head ? "head" : "foot") + ",occupied=false]";
    }

    static String symbol(int kind) {
        return switch (Math.floorMod(kind, 3)) {
            case 0 -> SYM_CIRCLE;
            case 1 -> SYM_TRIANGLE;
            default -> SYM_SQUARE;
        };
    }
}

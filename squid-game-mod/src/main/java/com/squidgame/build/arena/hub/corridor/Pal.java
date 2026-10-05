package com.squidgame.build.arena.hub.corridor;

/**
 * Block palette and tiny state-string helpers for the guard corridors: the pink / white / black "guard" look
 * (tiles for floors, concrete for walls), light panels for functional lighting, pastel blocks for accents.
 */
final class Pal {
    private Pal() {
    }

    static final String AIR = "minecraft:air";

    // custom blocks
    static final String TILE_PINK = "squidgame:tile_pink";
    static final String TILE_WHITE = "squidgame:tile_white";
    static final String TILE_BLACK = "squidgame:tile_black";
    static final String LIGHT_WHITE = "squidgame:panel_light_white";
    static final String LIGHT_PINK = "squidgame:panel_light_pink";
    static final String LIGHT_WARM = "squidgame:panel_light_warm";
    static final String SYM_CIRCLE = "squidgame:symbol_circle";
    static final String SYM_TRIANGLE = "squidgame:symbol_triangle";
    static final String SYM_SQUARE = "squidgame:symbol_square";

    // concrete family
    static final String WHITE = "minecraft:white_concrete";
    static final String PINK = "minecraft:pink_concrete";
    static final String BLACK = "minecraft:black_concrete";
    static final String GRAY = "minecraft:gray_concrete";
    static final String LGRAY = "minecraft:light_gray_concrete";
    static final String QUARTZ = "minecraft:smooth_quartz";
    static final String PINK_TERRA = "minecraft:pink_terracotta";
    static final String RIB = "minecraft:quartz_pillar[axis=y]";
    static final String BEAM = "minecraft:pink_concrete";
    static final String PBS = "minecraft:polished_blackstone";
    static final String PBS_BRICKS = "minecraft:polished_blackstone_bricks";

    static final String[] PASTEL = {"pink", "peach", "yellow", "mint", "sky", "lilac", "cream"};

    static String pastel(int i) {
        return "squidgame:pastel_" + PASTEL[Math.floorMod(i, PASTEL.length)];
    }

    static String pastelStairs(int i, String facing, boolean top) {
        return "squidgame:pastel_" + PASTEL[Math.floorMod(i, PASTEL.length)] + "_stairs[facing=" + facing
                + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    static String pastelSlab(int i, boolean top) {
        return "squidgame:pastel_" + PASTEL[Math.floorMod(i, PASTEL.length)] + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    static String symbol(int kind) {
        return switch (Math.floorMod(kind, 3)) {
            case 0 -> SYM_CIRCLE;
            case 1 -> SYM_TRIANGLE;
            default -> SYM_SQUARE;
        };
    }

    // ---- state helpers

    static String stairs(String id, String facing, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight]";
    }

    static String slab(String id, boolean top) {
        return id + "[type=" + (top ? "top" : "bottom") + "]";
    }

    static String trapdoor(String id, String facing, boolean open, boolean top) {
        return id + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",open=" + open + "]";
    }

    static String bed(String color, String facing, boolean head) {
        return "minecraft:" + color + "_bed[facing=" + facing + ",part=" + (head ? "head" : "foot") + ",occupied=false]";
    }

    static String lantern(boolean hanging) {
        return "minecraft:lantern[hanging=" + hanging + "]";
    }

    static String chain(String axis) {
        return "minecraft:chain[axis=" + axis + "]";
    }

    static String log(String id, String axis) {
        return id + "[axis=" + axis + "]";
    }

    static String endRod(String facing) {
        return "minecraft:end_rod[facing=" + facing + "]";
    }

    static String monitor(String facing) {
        return "squidgame:monitor[facing=" + facing + "]";
    }

    /** Direction names indexed 0=north(-z) 1=east(+x) 2=south(+z) 3=west(-x). */
    static final String[] DIR = {"north", "east", "south", "west"};
}

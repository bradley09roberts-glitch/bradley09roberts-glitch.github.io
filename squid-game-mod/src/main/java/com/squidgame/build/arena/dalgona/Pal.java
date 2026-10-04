package com.squidgame.build.arena.dalgona;

/** Block palette and small state-string helpers for the Dalgona hall. */
public final class Pal {
    private Pal() {
    }

    // --- timber -------------------------------------------------------------------------------
    public static final String OAK = "minecraft:oak_planks";
    public static final String SPRUCE = "minecraft:spruce_planks";
    public static final String DARK_OAK = "minecraft:dark_oak_planks";
    public static final String BIRCH = "minecraft:birch_planks";
    public static final String JUNGLE = "minecraft:jungle_planks";
    public static final String MANGROVE = "minecraft:mangrove_planks";

    // --- plaster, stone ---------------------------------------------------------------------
    public static final String CREAM = "minecraft:smooth_sandstone";
    public static final String CREAM_WORN = "minecraft:white_terracotta";
    public static final String CREAM_SPECK = "minecraft:cut_sandstone";
    public static final String OCHRE = "minecraft:yellow_terracotta";
    public static final String WHITE = "minecraft:white_concrete";
    public static final String QUARTZ = "minecraft:quartz_block";
    public static final String QUARTZ_SMOOTH = "minecraft:smooth_quartz";
    public static final String STONE_BRICKS = "minecraft:stone_bricks";
    public static final String DEEPSLATE_TILES = "minecraft:deepslate_tiles";
    public static final String BLACK = "minecraft:black_concrete";
    public static final String GREEN_BOARD = "minecraft:green_concrete";

    // --- light --------------------------------------------------------------------------------
    public static final String SHROOMLIGHT = "minecraft:shroomlight";
    public static final String LIGHT = "minecraft:light[level=15]";
    public static final String WARM_PANEL = "squidgame:panel_light_warm";
    public static final String GLASS_PANE = "minecraft:glass_pane";
    public static final String WHITE_PANE = "minecraft:white_stained_glass_pane";
    public static final String MILK_GLASS = "minecraft:white_stained_glass";

    public static final String STATION = "squidgame:dalgona_station";

    // --- helpers ------------------------------------------------------------------------------

    /** {@code stairs("dark_oak", "north", false)} = bottom dark oak stairs whose high side is north. */
    public static String stairs(String material, String facing, boolean upsideDown) {
        return "minecraft:" + material + "_stairs[facing=" + facing + ",half=" + (upsideDown ? "top" : "bottom")
                + ",shape=straight]";
    }

    public static String slab(String material, boolean top) {
        return "minecraft:" + material + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    /** Log / wood block with an axis ("x", "y" or "z"). */
    public static String log(String name, char axis) {
        return "minecraft:" + name + "[axis=" + axis + "]";
    }

    public static String trapdoor(String material, String facing, boolean open, boolean top) {
        return "minecraft:" + material + "_trapdoor[facing=" + facing + ",half=" + (top ? "top" : "bottom")
                + ",open=" + open + "]";
    }

    public static String lantern(boolean hanging) {
        return "minecraft:lantern[hanging=" + hanging + "]";
    }

    public static String chain() {
        return "minecraft:chain[axis=y]";
    }
}

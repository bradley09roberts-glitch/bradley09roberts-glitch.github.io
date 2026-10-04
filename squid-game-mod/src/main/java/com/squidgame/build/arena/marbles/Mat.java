package com.squidgame.build.arena.marbles;

/**
 * Block palette and block-state string helpers for the Marbles alley village. Every id used here exists in
 * vanilla 1.21.1; states only use properties the placer accepts.
 */
final class Mat {
    private Mat() {
    }

    // ---- ground
    static final String COBBLE = "minecraft:cobblestone";
    static final String MOSSY_COBBLE = "minecraft:mossy_cobblestone";
    static final String GRAVEL = "minecraft:gravel";
    static final String COARSE = "minecraft:coarse_dirt";
    static final String PATH = "minecraft:dirt_path";
    static final String MUD = "minecraft:packed_mud";
    static final String SB = "minecraft:stone_bricks";
    static final String SB_CRACK = "minecraft:cracked_stone_bricks";
    static final String SB_MOSS = "minecraft:mossy_stone_bricks";
    static final String ANDESITE = "minecraft:andesite";
    static final String P_ANDESITE = "minecraft:polished_andesite";
    static final String STONE = "minecraft:stone";
    static final String SMOOTH = "minecraft:smooth_stone";
    static final String TUFF = "minecraft:tuff";
    static final String PUDDLE = "minecraft:light_gray_concrete";
    static final String DEEPSLATE = "minecraft:deepslate";
    static final String DIRT = "minecraft:dirt";

    // ---- walls
    static final String PLASTER = "minecraft:white_terracotta";
    static final String CALCITE = "minecraft:calcite";
    static final String DIORITE = "minecraft:diorite";
    static final String CREAM = "minecraft:cut_sandstone";
    static final String CEMENT = "minecraft:light_gray_concrete";
    static final String BRICK = "minecraft:bricks";
    static final String LOG = "minecraft:dark_oak_log";
    static final String LOG_S = "minecraft:stripped_dark_oak_log";
    static final String PLANKS = "minecraft:dark_oak_planks";
    static final String SPRUCE = "minecraft:spruce_planks";
    static final String SPRUCE_LOG = "minecraft:stripped_spruce_log";

    // ---- roofs
    static final String TILE = "minecraft:deepslate_tiles";
    static final String TILE_ST = "minecraft:deepslate_tile_stairs";
    static final String TILE_SL = "minecraft:deepslate_tile_slab";
    static final String TILE_WALL = "minecraft:deepslate_tile_wall";
    static final String COBDS = "minecraft:cobbled_deepslate";
    static final String COBDS_ST = "minecraft:cobbled_deepslate_stairs";
    static final String COBDS_SL = "minecraft:cobbled_deepslate_slab";
    static final String BLACKSTONE = "minecraft:blackstone";
    static final String BLK_ST = "minecraft:blackstone_stairs";
    static final String BLK_SL = "minecraft:blackstone_slab";
    static final String SB_ST = "minecraft:stone_brick_stairs";
    static final String SB_SL = "minecraft:stone_brick_slab";
    static final String SB_WALL = "minecraft:stone_brick_wall";
    static final String MUDB = "minecraft:mud_bricks";
    static final String MUDB_ST = "minecraft:mud_brick_stairs";
    static final String MUDB_SL = "minecraft:mud_brick_slab";

    // ---- light
    static final String LANTERN = "minecraft:lantern";
    static final String SEA = "minecraft:sea_lantern";
    static final String GLOW = "minecraft:glowstone";
    static final String SHROOM = "minecraft:shroomlight";

    // ---- misc
    static final String AIR = "minecraft:air";
    static final String CHAIN_X = "minecraft:chain[axis=x]";
    static final String CHAIN_Y = "minecraft:chain[axis=y]";
    static final String CHAIN_Z = "minecraft:chain[axis=z]";
    static final String PANE_WARM = "minecraft:white_stained_glass_pane";
    static final String PANE_ORANGE = "minecraft:orange_stained_glass_pane";
    static final String FENCE = "minecraft:dark_oak_fence";
    static final String BARS = "minecraft:iron_bars";

    // ---- state builders
    static String stair(String base, String facing) {
        return base + "[facing=" + facing + ",half=bottom,shape=straight]";
    }

    static String stairTop(String base, String facing) {
        return base + "[facing=" + facing + ",half=top,shape=straight]";
    }

    static String slab(String base, String type) {
        return base + "[type=" + type + "]";
    }

    static String slabB(String base) {
        return base + "[type=bottom]";
    }

    static String slabT(String base) {
        return base + "[type=top]";
    }

    static String log(String base, String axis) {
        return base + "[axis=" + axis + "]";
    }

    static String lantern(boolean hanging) {
        return LANTERN + "[hanging=" + hanging + "]";
    }

    static String trapdoor(String base, String facing, String half, boolean open) {
        return base + "[facing=" + facing + ",half=" + half + ",open=" + open + "]";
    }

    static String door(String base, String facing, boolean upper, String hinge, boolean open) {
        return base + "[facing=" + facing + ",half=" + (upper ? "upper" : "lower") + ",hinge=" + hinge + ",open=" + open + "]";
    }

    static String leaves(String base) {
        return base + "[persistent=true]";
    }

    static String concrete(String color) {
        return "minecraft:" + color + "_concrete";
    }

    static String wool(String color) {
        return "minecraft:" + color + "_wool";
    }

    static String carpet(String color) {
        return "minecraft:" + color + "_carpet";
    }
}

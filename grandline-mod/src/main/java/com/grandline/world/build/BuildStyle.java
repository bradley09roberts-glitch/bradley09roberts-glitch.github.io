package com.grandline.world.build;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Architectural palette for settlement buildings. */
public enum BuildStyle {
    VILLAGE(Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.COBBLESTONE, Blocks.GLASS_PANE, Blocks.OAK_DOOR, Blocks.OAK_FENCE, Blocks.LANTERN, Blocks.WHITE_TERRACOTTA),
    STONE_TOWN(Blocks.STONE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.POLISHED_ANDESITE, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_FENCE, Blocks.LANTERN, Blocks.BRICKS),
    DESERT(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.SANDSTONE_SLAB, Blocks.CHISELED_SANDSTONE, Blocks.AIR, Blocks.ACACIA_DOOR, Blocks.ACACIA_FENCE, Blocks.LANTERN, Blocks.ORANGE_TERRACOTTA),
    SNOW(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.COBBLED_DEEPSLATE, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE, Blocks.SOUL_LANTERN, Blocks.WHITE_WOOL),
    JUNGLE(Blocks.BAMBOO_PLANKS, Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.BAMBOO_MOSAIC_STAIRS, Blocks.BAMBOO_MOSAIC_SLAB, Blocks.MUD_BRICKS, Blocks.AIR, Blocks.BAMBOO_DOOR, Blocks.BAMBOO_FENCE, Blocks.LANTERN, Blocks.MOSS_BLOCK),
    WANO(Blocks.BIRCH_PLANKS, Blocks.DARK_OAK_LOG, Blocks.CHERRY_PLANKS, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, Blocks.STONE_BRICKS, Blocks.WHITE_STAINED_GLASS_PANE, Blocks.CHERRY_DOOR, Blocks.DARK_OAK_FENCE, Blocks.LANTERN, Blocks.RED_TERRACOTTA),
    VENETIAN(Blocks.WHITE_TERRACOTTA, Blocks.SMOOTH_QUARTZ, Blocks.TERRACOTTA, Blocks.BRICK_STAIRS, Blocks.BRICK_SLAB, Blocks.STONE_BRICKS, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.IRON_BARS, Blocks.LANTERN, Blocks.LIGHT_BLUE_TERRACOTTA),
    CANDY(Blocks.PINK_TERRACOTTA, Blocks.WHITE_CONCRETE, Blocks.MAGENTA_TERRACOTTA, Blocks.PURPUR_STAIRS, Blocks.PURPUR_SLAB, Blocks.QUARTZ_BLOCK, Blocks.PINK_STAINED_GLASS_PANE, Blocks.CHERRY_DOOR, Blocks.CHERRY_FENCE, Blocks.PEARLESCENT_FROGLIGHT, Blocks.YELLOW_TERRACOTTA),
    MARINE(Blocks.WHITE_CONCRETE, Blocks.SMOOTH_STONE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICKS, Blocks.GLASS_PANE, Blocks.IRON_DOOR, Blocks.IRON_BARS, Blocks.LANTERN, Blocks.BLUE_CONCRETE),
    GOTHIC(Blocks.DEEPSLATE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.BLACKSTONE_STAIRS, Blocks.BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_FENCE, Blocks.SOUL_LANTERN, Blocks.CRYING_OBSIDIAN),
    FUTURE(Blocks.WHITE_CONCRETE, Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.LIGHT_GRAY_CONCRETE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.IRON_DOOR, Blocks.IRON_BARS, Blocks.SEA_LANTERN, Blocks.CYAN_CONCRETE),
    GIANT(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.MOSSY_COBBLESTONE, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE, Blocks.LANTERN, Blocks.BROWN_WOOL),
    PALACE(Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_STAIRS, Blocks.QUARTZ_SLAB, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.GLASS_PANE, Blocks.SPRUCE_DOOR, Blocks.IRON_BARS, Blocks.LANTERN, Blocks.GOLD_BLOCK),
    /**
     * Skypiea: white cloud-stone houses with sky-blue corners, aqua roofs, a blue plinth and gold trim, so they read
     * against the white Island Cloud they stand on.
     */
    CLOUD(Blocks.WHITE_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE, Blocks.SMOOTH_QUARTZ, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_SLAB, Blocks.LIGHT_BLUE_CONCRETE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.BIRCH_DOOR, Blocks.BIRCH_FENCE, Blocks.PEARLESCENT_FROGLIGHT, Blocks.GOLD_BLOCK),
    /** Fish-Man Island: coral and prismarine with sea-lantern light. */
    CORAL(Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SMOOTH_SANDSTONE, Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB, Blocks.PRISMARINE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, Blocks.WARPED_DOOR, Blocks.WARPED_FENCE, Blocks.SEA_LANTERN, Blocks.TUBE_CORAL_BLOCK);

    public final Block wall, frame, floor, roofStairs, roofSlab, foundation, window, door, fence, light, accent;

    BuildStyle(Block wall, Block frame, Block floor, Block roofStairs, Block roofSlab, Block foundation, Block window, Block door, Block fence, Block light, Block accent) {
        this.wall = wall;
        this.frame = frame;
        this.floor = floor;
        this.roofStairs = roofStairs;
        this.roofSlab = roofSlab;
        this.foundation = foundation;
        this.window = window;
        this.door = door;
        this.fence = fence;
        this.light = light;
        this.accent = accent;
    }
}

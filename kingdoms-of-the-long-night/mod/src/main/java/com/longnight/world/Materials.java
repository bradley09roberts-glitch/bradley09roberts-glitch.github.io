package com.longnight.world;

import com.longnight.sim.Commodity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.Map;

/** What a block costs to build, in ledger commodities. Terrain-like blocks are free. */
public final class Materials {
    private Materials() {}

    public static Map<Commodity, Double> cost(BlockState state) {
        Map<Commodity, Double> m = new EnumMap<>(Commodity.class);
        if (state.isAir()) return m;
        Block b = state.getBlock();
        String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
        double scale = b instanceof SlabBlock ? 0.5 : 1.0;
        if (state.is(BlockTags.LOGS) || id.endsWith("_wood") || id.endsWith("_hyphae")) {
            m.put(Commodity.LOGS, 1.0);
        } else if (state.is(BlockTags.PLANKS) || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS) || state.is(BlockTags.ALL_SIGNS) || id.contains("barrel")
                || id.equals("crafting_table") || id.equals("composter") || id.equals("lectern") || id.equals("loom")
                || id.equals("cartography_table") || id.equals("fletching_table") || id.equals("scaffolding") || id.equals("ladder")
                || id.equals("bookshelf") || id.equals("chiseled_bookshelf")) {
            m.put(Commodity.PLANKS, id.equals("bookshelf") ? 2.0 : scale);
        } else if (state.is(BlockTags.BEDS)) {
            m.put(Commodity.PLANKS, 2.0);
            m.put(Commodity.WOOL, 1.0);
        } else if (id.contains("glass")) {
            m.put(Commodity.GLASS, id.contains("pane") ? 0.4 : 1.0);
        } else if (id.equals("lantern") || id.equals("soul_lantern") || id.equals("iron_bars") || id.equals("chain")) {
            m.put(Commodity.IRON, 0.25);
        } else if (id.equals("anvil") || id.equals("blast_furnace") || id.equals("smithing_table") || id.equals("cauldron")
                || id.equals("bell")) {
            m.put(Commodity.IRON, 3.0);
            m.put(Commodity.STONE, 1.0);
        } else if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.BANNERS)) {
            m.put(Commodity.WOOL, state.is(BlockTags.WOOL_CARPETS) ? 0.5 : 1.0);
        } else if (id.contains("stone") || id.contains("cobble") || id.contains("brick") || id.contains("sandstone")
                || id.contains("deepslate") || id.contains("blackstone") || id.contains("andesite") || id.contains("diorite")
                || id.contains("granite") || id.contains("terracotta") || id.contains("calcite") || id.contains("tuff")
                || id.contains("quartz") || id.contains("prismarine") || id.equals("furnace") || id.equals("smoker")
                || id.equals("grindstone") || id.equals("stonecutter") || id.contains("packed_mud") || id.contains("bone_block")) {
            m.put(Commodity.STONE, scale);
        } else if (id.equals("hay_block")) {
            m.put(Commodity.GRAIN, 3.0);
        } else if (id.equals("gold_block")) {
            m.put(Commodity.IRON, 4.0);
        } else if (id.equals("amethyst_block") || id.equals("crying_obsidian") || id.equals("magma_block")) {
            m.put(Commodity.STONE, 2.0);
        }
        return m;
    }

    /** Blocks that may be cleared or built over without destroying anything a player made. */
    public static boolean isNaturalOrReplaceable(BlockState s) {
        if (s.isAir() || s.canBeReplaced()) return true;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.SAPLINGS) || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.CORAL_BLOCKS) || s.is(BlockTags.CAVE_VINES) || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES)
                || s.is(BlockTags.COPPER_ORES)) {
            return true;
        }
        if (s.is(BlockTags.LEAVES)) {
            return !s.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                    || !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT);
        }
        Block b = s.getBlock();
        return b == Blocks.GRAVEL || b == Blocks.CLAY || b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE || b == Blocks.SNOW_BLOCK
                || b == Blocks.POWDER_SNOW || b == Blocks.CACTUS || b == Blocks.SUGAR_CANE || b == Blocks.PUMPKIN || b == Blocks.MELON
                || b == Blocks.BAMBOO || b == Blocks.SWEET_BERRY_BUSH || b == Blocks.MOSS_BLOCK || b == Blocks.MOSS_CARPET
                || b == Blocks.BROWN_MUSHROOM_BLOCK || b == Blocks.RED_MUSHROOM_BLOCK || b == Blocks.MUSHROOM_STEM
                || b == Blocks.MUD || b == Blocks.MANGROVE_ROOTS || b == Blocks.MUDDY_MANGROVE_ROOTS || b == Blocks.DRIPSTONE_BLOCK
                || b == Blocks.POINTED_DRIPSTONE || b == Blocks.BEE_NEST || b == Blocks.COCOA || b == Blocks.VINE
                || b == Blocks.LILY_PAD || b == Blocks.KELP || b == Blocks.KELP_PLANT || b == Blocks.SEAGRASS || b == Blocks.TALL_SEAGRASS
                || b == Blocks.WATER || b == Blocks.LAVA || b == Blocks.OBSIDIAN || b == Blocks.AMETHYST_BLOCK
                || b == Blocks.BUDDING_AMETHYST || b == Blocks.SMOOTH_BASALT || b == Blocks.CALCITE || b == Blocks.PACKED_ICE
                || b == Blocks.BLUE_ICE;
    }

    /** Natural tree logs (attached to non-persistent leaves) may be felled; logs in buildings may not. */
    public static boolean isLog(BlockState s) {
        return s.is(BlockTags.LOGS);
    }
}

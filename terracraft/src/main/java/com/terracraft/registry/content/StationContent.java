package com.terracraft.registry.content;

import com.terracraft.block.CraftingStationBlock;
import com.terracraft.registry.ModBlocks;
import com.terracraft.registry.TabGroup;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.RegistryObject;

/** Crafting station blocks and other functional blocks. */
public final class StationContent {
    private static final VoxelShape TABLE = Shapes.or(Block.box(0, 12, 0, 16, 16, 16),
        Block.box(1, 0, 1, 4, 12, 4), Block.box(12, 0, 1, 15, 12, 4), Block.box(1, 0, 12, 4, 12, 15), Block.box(12, 0, 12, 15, 12, 15));
    private static final VoxelShape ANVIL = Shapes.or(Block.box(2, 0, 3, 14, 4, 13), Block.box(5, 4, 5, 11, 9, 11), Block.box(0, 9, 2, 16, 15, 14));

    public static final RegistryObject<CraftingStationBlock> WORK_BENCH = ModBlocks.register("work_bench", TabGroup.BLOCKS,
        p -> new CraftingStationBlock(p, TABLE),
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion());
    public static final RegistryObject<CraftingStationBlock> IRON_ANVIL = anvil("iron_anvil", MapColor.METAL);
    public static final RegistryObject<CraftingStationBlock> LEAD_ANVIL = anvil("lead_anvil", MapColor.TERRACOTTA_BLUE);

    private StationContent() {}

    public static void init() {}

    private static RegistryObject<CraftingStationBlock> anvil(String name, MapColor color) {
        return ModBlocks.register(name, TabGroup.BLOCKS, p -> new CraftingStationBlock(p, ANVIL),
            () -> BlockBehaviour.Properties.of().mapColor(color).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.ANVIL).noOcclusion());
    }
}

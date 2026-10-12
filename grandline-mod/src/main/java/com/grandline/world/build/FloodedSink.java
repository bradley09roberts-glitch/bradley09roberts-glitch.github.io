package com.grandline.world.build;

import com.grandline.world.DeepWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Deep-sea building: anything a builder clears to air outside the air bubbles becomes sea water instead. */
public final class FloodedSink implements BlockSink {
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private final BlockSink inner;

    public FloodedSink(BlockSink inner) {
        this.inner = inner;
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        if (state.isAir() && y <= DeepWorld.WATER_TOP && !DeepWorld.inAnyDome(x, y, z)) state = WATER;
        inner.set(x, y, z, state);
    }

    @Override
    public BlockState get(int x, int y, int z) {
        return inner.get(x, y, z);
    }

    @Override
    public boolean accepts(int minX, int minZ, int maxX, int maxZ) {
        return inner.accepts(minX, minZ, maxX, maxZ);
    }

    @Override
    public void marker(String kind, BlockPos pos, CompoundTag data) {
        inner.marker(kind, pos, data);
    }

    @Override
    public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {
        inner.lootChest(x, y, z, chest, lootTable);
    }
}

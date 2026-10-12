package com.grandline.world.build;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/** Writes into a single chunk during world generation, silently dropping writes outside it. */
public final class ChunkSink implements BlockSink {
    private final WorldGenLevel level;
    private final ChunkAccess chunk;
    private final int minX, minZ, maxX, maxZ;
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

    public ChunkSink(WorldGenLevel level, ChunkAccess chunk) {
        this.level = level;
        this.chunk = chunk;
        this.minX = chunk.getPos().getMinBlockX();
        this.minZ = chunk.getPos().getMinBlockZ();
        this.maxX = chunk.getPos().getMaxBlockX();
        this.maxZ = chunk.getPos().getMaxBlockZ();
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return;
        if (y <= chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) return;
        mpos.set(x, y, z);
        level.setBlock(mpos, state, 2);
    }

    @Override
    public BlockState get(int x, int y, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return null;
        return chunk.getBlockState(mpos.set(x, y, z));
    }

    @Override
    public boolean accepts(int ax, int az, int bx, int bz) {
        return bx >= minX && ax <= maxX && bz >= minZ && az <= maxZ;
    }

    @Override
    public void lootChest(int x, int y, int z, BlockState chest, String lootTable) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return;
        set(x, y, z, chest);
        BlockEntity be = level.getBlockEntity(mpos.set(x, y, z));
        if (be instanceof RandomizableContainerBlockEntity rc) {
            rc.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(lootTable)), level.getRandom().nextLong());
        }
    }

    @Override
    public void marker(String kind, BlockPos pos, CompoundTag data) {
        // markers are recomputed on demand by the population system; nothing to store at generation time
    }
}

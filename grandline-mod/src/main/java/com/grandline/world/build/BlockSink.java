package com.grandline.world.build;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Destination for generated blocks. Implementations clip to a chunk, record markers, or write to a live level. */
public interface BlockSink {
    void set(int x, int y, int z, BlockState state);

    /** Optional: reads the current block (used for "replace only air" style operations). May return null if unknown. */
    default BlockState get(int x, int y, int z) {
        return null;
    }

    /** Whether any block in this column range could be written (fast rejection of off-chunk work). */
    default boolean accepts(int minX, int minZ, int maxX, int maxZ) {
        return true;
    }

    /** Record a gameplay marker (NPC spawn, chest loot table, quest anchor...). */
    default void marker(String kind, BlockPos pos, CompoundTag data) {}

    /** Place a chest with a loot table id (deferred until block entity exists). */
    default void lootChest(int x, int y, int z, BlockState chest, String lootTable) {
        set(x, y, z, chest);
    }
}

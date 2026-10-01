package com.starforged.tempest.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** A block that takes part in a storm network (see {@link com.starforged.tempest.world.StormNetwork}). */
public interface StormNode {
    /**
     * A storm pulse reached this block travelling in direction {@code travel}.
     *
     * @return the directions the pulse leaves in; empty if this block absorbs it.
     */
    List<Direction> receive(ServerLevel level, BlockPos pos, BlockState state, Direction travel);
}

package com.starforged.sun.world;

import com.starforged.sun.SunBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Raises a Solar Gateway: a 5x5 sunbaked-brick dais with a 3x3 pool of liquid sunlight in the middle and
 * four lantern-topped pillars. Used by the Solar Key and for the automatic return gateway.
 */
public final class GatewayBuilder {
    private GatewayBuilder() {
    }

    /** {@code center} is the block where the middle of the pool goes (the dais is one block below). */
    public static void build(Level level, BlockPos center) {
        BlockState bricks = SunBlocks.SUNBAKED_BRICKS.get().defaultBlockState();
        BlockState chiseled = SunBlocks.CHISELED_SUNBAKED_BRICKS.get().defaultBlockState();
        BlockState gate = SunBlocks.SOLAR_GATEWAY.get().defaultBlockState();
        BlockState lantern = SunBlocks.SUN_LANTERN.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                // Foundation down to solid ground (or a few blocks into lava/air).
                for (int dy = -2; dy >= -6; dy--) {
                    BlockPos below = center.offset(dx, dy, dz);
                    BlockState existing = level.getBlockState(below);
                    if (!existing.isAir() && !existing.liquid() && !existing.canBeReplaced()) {
                        break;
                    }
                    level.setBlock(below, bricks, Block.UPDATE_ALL);
                }
                level.setBlock(center.offset(dx, -1, dz), corner || (dx == 0 || dz == 0) && (Math.abs(dx) == 2 || Math.abs(dz) == 2) ? chiseled : bricks,
                    Block.UPDATE_ALL);
                for (int dy = 0; dy <= 4; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState state;
                    if (corner) {
                        state = dy < 3 ? bricks : dy == 3 ? chiseled : lantern;
                    } else if (dy == 0 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        state = gate;
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(p, state, Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Finds an existing gateway pool near {@code pos}, or null. */
    public static BlockPos find(Level level, BlockPos pos, int radius, int height) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -height; dy <= height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(m).is(SunBlocks.SOLAR_GATEWAY.get())) {
                        // Walk to the middle of the 3x3 pool.
                        BlockPos found = m.immutable();
                        int cx = found.getX();
                        int cz = found.getZ();
                        while (level.getBlockState(new BlockPos(cx - 1, found.getY(), cz)).is(SunBlocks.SOLAR_GATEWAY.get())) {
                            cx--;
                        }
                        while (level.getBlockState(new BlockPos(cx, found.getY(), cz - 1)).is(SunBlocks.SOLAR_GATEWAY.get())) {
                            cz--;
                        }
                        return new BlockPos(cx + 1, found.getY(), cz + 1);
                    }
                }
            }
        }
        return null;
    }
}

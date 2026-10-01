package com.starforged.tempest.world;

import com.starforged.tempest.TempestBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Raises a Stormgate: a 5x5 tempest-brick dais with a 3x3 whirl of caged storm in the middle and
 * four lantern-topped pillars. Used by the Skybreaker Core and for the automatic return gate.
 */
public final class StormgateBuilder {
    private StormgateBuilder() {
    }

    /** {@code center} is the block where the middle of the pool goes (the dais is one block below). */
    public static void build(Level level, BlockPos center) {
        BlockState bricks = TempestBlocks.TEMPEST_BRICKS.get().defaultBlockState();
        BlockState chiseled = TempestBlocks.CHISELED_TEMPEST_BRICKS.get().defaultBlockState();
        BlockState gate = TempestBlocks.STORMGATE.get().defaultBlockState();
        BlockState lantern = TempestBlocks.STORM_LANTERN.get().defaultBlockState();
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

    /**
     * Raises a small floating island of skyrock under {@code center} so a gate over the void has ground around it
     * (Stormreach is mostly sky).
     */
    public static void buildIsland(Level level, BlockPos center) {
        BlockState rock = TempestBlocks.SKYROCK.get().defaultBlockState();
        BlockState stone = TempestBlocks.STORMSTONE.get().defaultBlockState();
        BlockState grass = TempestBlocks.STORMGRASS.get().defaultBlockState();
        int radius = 7;
        for (int dy = 0; dy < 9; dy++) {
            double r = radius - dy * 0.75;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > r * r) {
                        continue;
                    }
                    BlockPos p = center.offset(dx, -2 - dy, dz);
                    if (level.getBlockState(p).isAir()) {
                        level.setBlock(p, dy == 0 ? grass : dy < 3 ? rock : stone, Block.UPDATE_CLIENTS);
                    }
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
                    if (level.getBlockState(m).is(TempestBlocks.STORMGATE.get())) {
                        // Walk to the middle of the 3x3 pool.
                        BlockPos found = m.immutable();
                        int cx = found.getX();
                        int cz = found.getZ();
                        while (level.getBlockState(new BlockPos(cx - 1, found.getY(), cz)).is(TempestBlocks.STORMGATE.get())) {
                            cx--;
                        }
                        while (level.getBlockState(new BlockPos(cx, found.getY(), cz - 1)).is(TempestBlocks.STORMGATE.get())) {
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

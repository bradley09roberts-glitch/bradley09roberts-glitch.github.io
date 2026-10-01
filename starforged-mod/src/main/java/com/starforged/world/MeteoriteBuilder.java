package com.starforged.world;

import com.starforged.block.AstralCrystalClusterBlock;
import com.starforged.registry.ModBlocks;
import com.starforged.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Carves impact craters and assembles meteorites. Shared by live meteor impacts and world generation.
 */
public final class MeteoriteBuilder {
    private MeteoriteBuilder() {
    }

    /**
     * Blasts a bowl-shaped crater centred on {@code impact}.
     *
     * @return the position at the bottom-centre of the crater
     */
    public static BlockPos carveCrater(LevelAccessor level, BlockPos impact, float radius, RandomSource random, boolean scorch, int flags) {
        int r = (int) Math.ceil(radius) + 1;
        double sphereOffset = radius * 0.45;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double wobble = radius * (0.88 + random.nextDouble() * 0.24);
                for (int dy = -r; dy <= r; dy++) {
                    double sy = dy - sphereOffset;
                    double distSq = dx * dx + sy * sy * 1.2 + dz * dz;
                    pos.set(impact.getX() + dx, impact.getY() + dy, impact.getZ() + dz);
                    if (distSq <= wobble * wobble) {
                        if (canBlast(level, pos)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                        }
                    } else if (scorch && distSq <= (wobble + 1.6) * (wobble + 1.6) && dy <= 0) {
                        scorch(level, pos, random, flags);
                    }
                }
            }
        }
        // Find the crater floor below the impact point.
        BlockPos.MutableBlockPos floor = impact.mutable();
        int limit = r * 2 + 4;
        while (limit-- > 0 && floor.getY() > level.getMinY() + 2 && level.getBlockState(floor.below()).isAir()) {
            floor.move(Direction.DOWN);
        }
        return floor.immutable();
    }

    private static void scorch(LevelAccessor level, BlockPos pos, RandomSource random, int flags) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !canBlast(level, pos)) {
            return;
        }
        if (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            float roll = random.nextFloat();
            BlockState replacement = roll < 0.35F ? Blocks.COARSE_DIRT.defaultBlockState()
                : roll < 0.55F ? Blocks.GRAVEL.defaultBlockState()
                : roll < 0.62F ? Blocks.MAGMA_BLOCK.defaultBlockState()
                : roll < 0.75F ? ModBlocks.METEORITE_ROCK.get().defaultBlockState()
                : null;
            if (replacement != null) {
                level.setBlock(pos, replacement, flags);
            }
        }
        BlockPos above = pos.above();
        if (random.nextFloat() < 0.12F && level.getBlockState(above).isAir() && level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
            level.setBlock(above, BaseFireBlock.getState(level, above), flags);
        }
    }

    public static boolean canBlast(LevelAccessor level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (state.is(ModTags.METEOR_PROOF) || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        return level.getBlockEntity(pos) == null;
    }

    /**
     * Assembles a lumpy meteorite half-buried at {@code floor}: meteorite rock laced with Starmetal ore, magma
     * veins, and Astral Crystals growing from its surface.
     */
    public static void buildMeteorite(LevelAccessor level, BlockPos floor, float radius, RandomSource random, float oreChance, int flags) {
        int r = (int) Math.ceil(radius) + 1;
        BlockPos center = floor.above((int) Math.floor(radius * 0.35));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double wobble = radius * (0.8 + random.nextDouble() * 0.35);
                    if (dx * dx + dy * dy + dz * dz > wobble * wobble) {
                        continue;
                    }
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && !canBlast(level, pos) && !existing.canBeReplaced()) {
                        continue;
                    }
                    float roll = random.nextFloat();
                    BlockState state = roll < oreChance ? ModBlocks.STARMETAL_ORE.get().defaultBlockState()
                        : roll < oreChance + 0.07F ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : ModBlocks.METEORITE_ROCK.get().defaultBlockState();
                    level.setBlock(pos, state, flags);
                }
            }
        }
        // Grow crystals on exposed faces.
        BlockState cluster = ModBlocks.ASTRAL_CRYSTAL_CLUSTER.get().defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r + 1; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(ModBlocks.METEORITE_ROCK.get()) && !state.is(ModBlocks.STARMETAL_ORE.get())) {
                        continue;
                    }
                    for (Direction dir : Direction.values()) {
                        if (dir == Direction.DOWN || random.nextFloat() > (dir == Direction.UP ? 0.28F : 0.08F)) {
                            continue;
                        }
                        BlockPos out = pos.relative(dir);
                        if (level.getBlockState(out).isAir()) {
                            level.setBlock(out, cluster.setValue(AstralCrystalClusterBlock.FACING, dir), flags);
                        }
                    }
                }
            }
        }
    }
}

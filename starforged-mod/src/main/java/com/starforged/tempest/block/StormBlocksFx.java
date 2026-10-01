package com.starforged.tempest.block;

import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/** Client-side particle helpers for storm blocks. */
final class StormBlocksFx {
    private StormBlocksFx() {
    }

    /** {@code count} sparks crawling over the open faces of a block. */
    static void sparks(Level level, BlockPos pos, RandomSource random, int count) {
        for (int i = 0; i < count; i++) {
            Direction face = Direction.getRandom(random);
            if (level.getBlockState(pos.relative(face)).isSolidRender()) {
                continue;
            }
            double x = pos.getX() + 0.5 + face.getStepX() * 0.55 + (face.getStepX() == 0 ? random.nextDouble() - 0.5 : 0.0);
            double y = pos.getY() + 0.5 + face.getStepY() * 0.55 + (face.getStepY() == 0 ? random.nextDouble() - 0.5 : 0.0);
            double z = pos.getZ() + 0.5 + face.getStepZ() * 0.55 + (face.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0.0);
            level.addParticle(ModParticles.STATIC_SPARK.get(), x, y, z, 0.0, 0.0, 0.0);
        }
    }
}

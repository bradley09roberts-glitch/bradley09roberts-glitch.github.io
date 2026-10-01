package com.starforged.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deep blue glass flecked with captured starlight. Used in the domes of the old observatories.
 */
public class StarglassBlock extends TransparentBlock {
    public static final MapCodec<StarglassBlock> CODEC = simpleCodec(StarglassBlock::new);

    public StarglassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(60) == 0) {
            level.addParticle(ModParticles.STAR_SPARKLE.get(),
                pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
        }
    }
}

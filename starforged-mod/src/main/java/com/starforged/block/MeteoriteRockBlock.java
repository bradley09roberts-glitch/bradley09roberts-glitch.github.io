package com.starforged.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Charred rock from a fallen star. Its magma veins still smoulder and occasionally spit embers.
 */
public class MeteoriteRockBlock extends Block {
    public static final MapCodec<MeteoriteRockBlock> CODEC = simpleCodec(MeteoriteRockBlock::new);

    public MeteoriteRockBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(12) == 0 && level.getBlockState(pos.above()).isAir()) {
            double x = pos.getX() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ModParticles.METEOR_EMBER.get(), x, pos.getY() + 1.05, z, 0.0, 0.02, 0.0);
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.1, z, 0.0, 0.03, 0.0);
            }
        }
    }
}

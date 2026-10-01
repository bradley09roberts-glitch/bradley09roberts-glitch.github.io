package com.starforged.sun.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that sheds drifting solar sparks: Sun Lanterns and the glowing chiseled brick of the Sun Temple.
 */
public class SunDecorBlock extends Block {
    public static final MapCodec<SunDecorBlock> CODEC = simpleCodec(SunDecorBlock::new);

    public SunDecorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) == 0) {
            level.addParticle(ModParticles.SOLAR_SPARK.get(),
                pos.getX() - 0.1 + random.nextDouble() * 1.2, pos.getY() + 0.2 + random.nextDouble(), pos.getZ() - 0.1 + random.nextDouble() * 1.2,
                0.0, 0.02, 0.0);
        }
    }
}

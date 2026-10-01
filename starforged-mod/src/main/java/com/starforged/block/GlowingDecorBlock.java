package com.starforged.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A light source holding a tiny captured star. Sheds twinkling motes.
 */
public class GlowingDecorBlock extends Block {
    public static final MapCodec<GlowingDecorBlock> CODEC = simpleCodec(GlowingDecorBlock::new);

    public GlowingDecorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            level.addParticle(ModParticles.STAR_SPARKLE.get(),
                pos.getX() - 0.1 + random.nextDouble() * 1.2, pos.getY() - 0.1 + random.nextDouble() * 1.2, pos.getZ() - 0.1 + random.nextDouble() * 1.2,
                0.0, 0.01, 0.0);
        }
    }
}

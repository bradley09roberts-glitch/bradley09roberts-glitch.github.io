package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** A block that crackles with stray sparks: chiseled Tempest Bricks and Charged Aetherium. */
public class TempestDecorBlock extends Block {
    public static final MapCodec<TempestDecorBlock> CODEC = simpleCodec(TempestDecorBlock::new);

    public TempestDecorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(7) == 0) {
            level.addParticle(ModParticles.STATIC_SPARK.get(),
                pos.getX() - 0.1 + random.nextDouble() * 1.2, pos.getY() + 0.2 + random.nextDouble(), pos.getZ() - 0.1 + random.nextDouble() * 1.2,
                0.0, 0.01, 0.0);
        }
    }
}

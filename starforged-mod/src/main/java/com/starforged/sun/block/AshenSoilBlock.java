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
 * The ground of the Ember Plains: ash over warm earth, with the odd ember still smouldering in it.
 */
public class AshenSoilBlock extends Block {
    public static final MapCodec<AshenSoilBlock> CODEC = simpleCodec(AshenSoilBlock::new);

    public AshenSoilBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(40) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(ModParticles.ASH_FLAKE.get(), pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(),
                0.0, 0.03, 0.0);
        }
    }
}

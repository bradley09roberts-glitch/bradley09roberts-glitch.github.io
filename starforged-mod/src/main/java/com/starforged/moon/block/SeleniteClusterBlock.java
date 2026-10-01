package com.starforged.moon.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Glowing selenite crystals of the Selenite Hollows. */
public class SeleniteClusterBlock extends AmethystClusterBlock {
    public static final MapCodec<AmethystClusterBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(Codec.FLOAT.fieldOf("height").forGetter(b -> 7.0F), Codec.FLOAT.fieldOf("width").forGetter(b -> 9.0F), propertiesCodec())
            .apply(i, SeleniteClusterBlock::new)
    );

    public SeleniteClusterBlock(float height, float width, BlockBehaviour.Properties props) {
        super(height, width, props);
    }

    @Override
    public MapCodec<AmethystClusterBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            Direction facing = state.getValue(FACING);
            level.addParticle(ModParticles.LUNAR_GLIMMER.get(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepX() * 0.2,
                pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepY() * 0.2,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepZ() * 0.2, 0.0, 0.01, 0.0);
        }
    }
}

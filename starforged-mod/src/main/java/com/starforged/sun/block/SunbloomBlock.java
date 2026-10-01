package com.starforged.sun.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A golden flower that drinks sunlight and glows with it. Grows on Ashen Soil and Sunsand.
 */
public class SunbloomBlock extends FlowerBlock {
    public static final MapCodec<FlowerBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(propertiesCodec()).apply(i, SunbloomBlock::new)
    );

    public SunbloomBlock(BlockBehaviour.Properties properties) {
        super(MobEffects.FIRE_RESISTANCE, 8.0F, properties);
    }

    @Override
    public MapCodec<FlowerBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) == 0) {
            level.addParticle(ModParticles.SOLAR_SPARK.get(), pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.7,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.015, 0.0);
        }
    }
}

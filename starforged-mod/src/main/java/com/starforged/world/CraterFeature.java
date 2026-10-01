package com.starforged.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Ancient impact craters scattered across the overworld: a scorched bowl with a half-buried meteorite.
 * They give early access to Starmetal before the first Starfall.
 */
public class CraterFeature extends Feature<NoneFeatureConfiguration> {
    public CraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (origin.getY() <= level.getSeaLevel() || !level.getBlockState(origin.below()).isSolid()) {
            return false;
        }
        float radius = 3.0F + random.nextFloat() * 2.5F;
        BlockPos floor = MeteoriteBuilder.carveCrater(level, origin.below(), radius, random, true, Block.UPDATE_CLIENTS);
        MeteoriteBuilder.buildMeteorite(level, floor, 1.2F + random.nextFloat() * 0.8F, random, 0.26F, Block.UPDATE_CLIENTS);
        return true;
    }
}

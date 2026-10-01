package com.starforged.moon.world;

import com.mojang.serialization.Codec;
import com.starforged.moon.MoonBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Chunks of the moon torn loose and left hanging in the sky over the Shattered Rim: an inverted cone of moonstone
 * capped with regolith, sometimes crowned with selenite or a moonpetal.
 */
public class FloatingIslandFeature extends Feature<NoneFeatureConfiguration> {
    public FloatingIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin().above(18 + random.nextInt(34));
        if (origin.getY() > level.getMaxY() - 10) {
            return false;
        }
        float radius = 3.0F + random.nextFloat() * 4.5F;
        int depth = (int) (radius * (1.2F + random.nextFloat() * 0.8F));
        BlockState stone = MoonBlocks.MOONSTONE.get().defaultBlockState();
        BlockState top = MoonBlocks.REGOLITH.get().defaultBlockState();
        BlockState ore = MoonBlocks.MOONSILVER_ORE.get().defaultBlockState();
        int r = (int) Math.ceil(radius);
        for (int dy = 0; dy <= depth; dy++) {
            float layer = radius * (1.0F - dy / (float) (depth + 1)) + random.nextFloat() * 0.6F;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > layer * layer) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, -dy, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        continue;
                    }
                    BlockState state = dy == 0 ? top : random.nextInt(40) == 0 ? ore : stone;
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                }
            }
        }
        // A little crown of crystal or flowers on top.
        for (int i = 0; i < 3; i++) {
            BlockPos pos = origin.offset(random.nextInt(r * 2 + 1) - r, 1, random.nextInt(r * 2 + 1) - r);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).is(MoonBlocks.REGOLITH.get())) {
                level.setBlock(pos, random.nextBoolean() ? MoonBlocks.MOONPETAL.get().defaultBlockState()
                    : MoonBlocks.SELENITE_CLUSTER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }
}

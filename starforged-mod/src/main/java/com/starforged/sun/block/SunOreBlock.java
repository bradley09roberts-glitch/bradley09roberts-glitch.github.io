package com.starforged.sun.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scorchstone shot through with veins of Sunsteel. The veins glow like embers and spit sparks.
 */
public class SunOreBlock extends DropExperienceBlock {
    public static final MapCodec<SunOreBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(IntProviders.codec(0, 10).fieldOf("experience").forGetter(b -> b.xp), propertiesCodec()).apply(i, SunOreBlock::new)
    );
    private final IntProvider xp;

    public SunOreBlock(IntProvider xpRange, BlockBehaviour.Properties properties) {
        super(xpRange, properties);
        this.xp = xpRange;
    }

    @Override
    public MapCodec<SunOreBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) {
            return;
        }
        Direction face = Direction.getRandom(random);
        if (level.getBlockState(pos.relative(face)).isSolidRender()) {
            return;
        }
        double x = pos.getX() + 0.5 + face.getStepX() * 0.55 + (face.getStepX() == 0 ? random.nextDouble() - 0.5 : 0.0);
        double y = pos.getY() + 0.5 + face.getStepY() * 0.55 + (face.getStepY() == 0 ? random.nextDouble() - 0.5 : 0.0);
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.55 + (face.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0.0);
        level.addParticle(ModParticles.SOLAR_SPARK.get(), x, y, z, 0.0, 0.02, 0.0);
    }
}

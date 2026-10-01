package com.starforged.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Meteorite laced with veins of Starmetal. The veins glitter faintly.
 */
public class StarmetalOreBlock extends DropExperienceBlock {
    public static final MapCodec<StarmetalOreBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(net.minecraft.util.valueproviders.IntProviders.codec(0, 10).fieldOf("experience").forGetter(b -> b.xp), propertiesCodec()).apply(i, StarmetalOreBlock::new)
    );
    private final IntProvider xp;

    public StarmetalOreBlock(IntProvider xpRange, BlockBehaviour.Properties properties) {
        super(xpRange, properties);
        this.xp = xpRange;
    }

    @Override
    public MapCodec<StarmetalOreBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }
        Direction face = Direction.getRandom(random);
        BlockPos side = pos.relative(face);
        if (level.getBlockState(side).isSolidRender()) {
            return;
        }
        double x = pos.getX() + 0.5 + face.getStepX() * 0.55 + (face.getStepX() == 0 ? random.nextDouble() - 0.5 : 0.0);
        double y = pos.getY() + 0.5 + face.getStepY() * 0.55 + (face.getStepY() == 0 ? random.nextDouble() - 0.5 : 0.0);
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.55 + (face.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0.0);
        level.addParticle(ModParticles.STAR_SPARKLE.get(), x, y, z, 0.0, 0.0, 0.0);
    }
}

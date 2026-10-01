package com.starforged.moon.block;

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
 * Moonstone threaded with Moonsilver. Too hard for anything below Sunsteel: only a Sunsteel (or better) pickaxe
 * brings the ore out whole.
 */
public class MoonOreBlock extends DropExperienceBlock {
    public static final MapCodec<MoonOreBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(IntProviders.codec(0, 10).fieldOf("experience").forGetter(b -> b.xp), propertiesCodec()).apply(i, MoonOreBlock::new)
    );
    private final IntProvider xp;

    public MoonOreBlock(IntProvider xpRange, BlockBehaviour.Properties properties) {
        super(xpRange, properties);
        this.xp = xpRange;
    }

    @Override
    public MapCodec<MoonOreBlock> codec() {
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
        level.addParticle(ModParticles.LUNAR_GLIMMER.get(), x, y, z, 0.0, 0.01, 0.0);
    }
}

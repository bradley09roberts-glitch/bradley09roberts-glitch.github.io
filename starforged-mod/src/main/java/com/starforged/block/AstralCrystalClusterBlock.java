package com.starforged.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Crystals of solidified starlight that grow on meteorites. They hum when you stand near them.
 */
public class AstralCrystalClusterBlock extends AmethystClusterBlock {
    public static final MapCodec<AmethystClusterBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(Codec.FLOAT.fieldOf("height").forGetter(b -> 7.0F), Codec.FLOAT.fieldOf("width").forGetter(b -> 10.0F), propertiesCodec())
            .apply(i, AstralCrystalClusterBlock::new)
    );

    public AstralCrystalClusterBlock(float height, float width, BlockBehaviour.Properties props) {
        super(height, width, props);
    }

    @Override
    public MapCodec<AmethystClusterBlock> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            Direction facing = state.getValue(FACING);
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepX() * 0.2;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepY() * 0.2;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepZ() * 0.2;
            level.addParticle(ModParticles.ASTRAL_GLINT.get(), x, y, z, facing.getStepX() * 0.01, facing.getStepY() * 0.01 + 0.005, facing.getStepZ() * 0.01);
        }
        if (random.nextInt(200) == 0) {
            level.playLocalSound(pos, ModSounds.CRYSTAL_CHIME.get(), SoundSource.BLOCKS, 0.5F, 0.8F + random.nextFloat() * 0.4F, false);
        }
    }
}

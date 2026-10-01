package com.starforged.sun.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Crystallised sunfire that grows in the Ember Caldera. Hot to the touch - brushing against one sets you alight.
 */
public class EmberCrystalClusterBlock extends AmethystClusterBlock {
    public static final MapCodec<AmethystClusterBlock> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(Codec.FLOAT.fieldOf("height").forGetter(b -> 7.0F), Codec.FLOAT.fieldOf("width").forGetter(b -> 10.0F), propertiesCodec())
            .apply(i, EmberCrystalClusterBlock::new)
    );

    public EmberCrystalClusterBlock(float height, float width, BlockBehaviour.Properties props) {
        super(height, width, props);
    }

    @Override
    public MapCodec<AmethystClusterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
        if (!level.isClientSide() && entity instanceof LivingEntity living && !living.fireImmune() && level.getGameTime() % 20 == 0) {
            living.igniteForSeconds(3.0F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            Direction facing = state.getValue(FACING);
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepX() * 0.2;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepY() * 0.2;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6 + facing.getStepZ() * 0.2;
            level.addParticle(ModParticles.SOLAR_SPARK.get(), x, y, z, 0.0, 0.03, 0.0);
        }
    }
}

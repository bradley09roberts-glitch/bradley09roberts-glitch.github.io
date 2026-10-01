package com.starforged.moon.block;

import com.mojang.serialization.MapCodec;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plate of polarised moonsilver. Step on it and gravity flips: you are flung up onto the ceiling and pinned there
 * for a few seconds. Sneak to cross it safely.
 */
public class GravityPlateBlock extends Block {
    public static final MapCodec<GravityPlateBlock> CODEC = simpleCodec(GravityPlateBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public GravityPlateBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living && !living.isSteppingCarefully()
            && !living.hasEffect(MobEffects.LEVITATION)) {
            living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 4, false, false, true));
            living.setDeltaMovement(living.getDeltaMovement().add(0, 1.0, 0));
            living.hurtMarked = true;
            server.playSound(null, pos, MoonSounds.GRAVITY_PLATE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 20, 0.4, 0.1, 0.4, 0.2);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.LUNAR_GLIMMER.get(), pos.getX() + random.nextDouble(), pos.getY() + 0.2, pos.getZ() + random.nextDouble(),
                0.0, 0.12, 0.0);
        }
    }
}

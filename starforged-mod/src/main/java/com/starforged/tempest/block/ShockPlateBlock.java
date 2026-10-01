package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModTags;
import com.starforged.tempest.TempestSounds;
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

/** Shock Plate: a live plate. Anything that walks across it is shocked and briefly stunned. Sneak to cross safely. */
public class ShockPlateBlock extends Block {
    public static final MapCodec<ShockPlateBlock> CODEC = simpleCodec(ShockPlateBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public ShockPlateBlock(BlockBehaviour.Properties properties) {
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
            && !living.is(ModTags.STORMBORN) && living.invulnerableTime == 0) {
            if (living.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.STORM, null), 4.0F)) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3, false, false, true));
                server.playSound(null, pos, TempestSounds.SHOCK_PLATE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                server.sendParticles(ModParticles.STATIC_SPARK.get(), living.getX(), living.getY() + 0.8, living.getZ(), 20, 0.3, 0.6, 0.3, 0.1);
            }
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.STATIC_SPARK.get(), pos.getX() + random.nextDouble(), pos.getY() + 0.15, pos.getZ() + random.nextDouble(),
                0.0, 0.0, 0.0);
        }
    }
}

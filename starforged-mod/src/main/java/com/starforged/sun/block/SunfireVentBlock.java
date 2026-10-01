package com.starforged.sun.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A grate over a sunfire well. It erupts on a steady rhythm - hiss, then a three-block pillar of flame -
 * so you can time your dash across, but sneaking will not save you.
 */
public class SunfireVentBlock extends Block {
    public static final MapCodec<SunfireVentBlock> CODEC = simpleCodec(SunfireVentBlock::new);
    /** 0 = dormant, 1 = hissing (warning), 2..13 = erupting (one step every 2 ticks). */
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 13);
    public static final int DORMANT_TICKS = 50;
    public static final int WARNING_TICKS = 20;

    public SunfireVentBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PHASE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    /** Vents in a row erupt one after another, like a wave. */
    public static int initialDelay(BlockPos pos) {
        return 10 + Math.floorMod(pos.getX() + pos.getZ(), 4) * 12;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) && !level.isClientSide()) {
            level.scheduleTick(pos, this, initialDelay(pos));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int phase = state.getValue(PHASE);
        if (phase == 0) {
            level.setBlock(pos, state.setValue(PHASE, 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, WARNING_TICKS);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.6F);
        } else if (phase == 1) {
            level.setBlock(pos, state.setValue(PHASE, 2), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, 2);
            level.playSound(null, pos, SunSounds.VENT_ERUPT.get(), SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F);
            this.burst(level, pos, 0);
        } else {
            this.burst(level, pos, phase);
            if (phase < 13) {
                level.setBlock(pos, state.setValue(PHASE, phase + 1), Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, 2);
            } else {
                level.setBlock(pos, state.setValue(PHASE, 0), Block.UPDATE_CLIENTS);
                level.scheduleTick(pos, this, DORMANT_TICKS);
            }
        }
    }

    private void burst(ServerLevel level, BlockPos pos, int t) {
        Vec3 c = Vec3.atBottomCenterOf(pos.above());
        for (int i = 0; i < 6; i++) {
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y + i * 0.5, c.z, 3, 0.18, 0.15, 0.18, 0.02);
        }
        level.sendParticles(ModParticles.SOLAR_SPARK.get(), c.x, c.y + 1.5, c.z, 4, 0.25, 1.0, 0.25, 0.04);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()).expandTowards(0, 2, 0).inflate(0.1, 0, 0.1))) {
            if (victim.fireImmune()) {
                continue;
            }
            victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SUNFIRE, null), 4.0F);
            victim.igniteForSeconds(5.0F);
            victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.25, 0));
            victim.hurtMarked = true;
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int phase = state.getValue(PHASE);
        if (phase == 1) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 0.0, 0.05, 0.0);
            level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
        } else if (phase == 0 && random.nextInt(8) == 0) {
            level.addParticle(ModParticles.SOLAR_SPARK.get(), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.02, 0.0);
        }
    }
}

package com.starforged.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Engraved floor tiles left by the astronomers to guard their observatories.
 * <ul>
 *     <li>{@link Kind#GRAVITY}: hurls whatever steps on it high into the air.</li>
 *     <li>{@link Kind#STARFIRE}: erupts in a pillar of starfire.</li>
 * </ul>
 * Sneaking lets you tip-toe across safely.
 */
public class RuneBlock extends Block {
    public static final MapCodec<RuneBlock> CODEC = simpleCodec(p -> new RuneBlock(Kind.GRAVITY, p));
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public enum Kind { GRAVITY, STARFIRE }

    private final Kind kind;

    public RuneBlock(Kind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel serverLevel && !state.getValue(POWERED) && entity instanceof LivingEntity && !entity.isSteppingCarefully()) {
            this.trigger(serverLevel, pos, state);
        }
        super.stepOn(level, pos, state, entity);
    }

    private void trigger(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, this.kind == Kind.GRAVITY ? 30 : 40);
        Vec3 center = Vec3.atCenterOf(pos).add(0, 0.6, 0);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos.above()).inflate(0.6, 0.5, 0.6));

        if (this.kind == Kind.GRAVITY) {
            level.playSound(null, pos, ModSounds.RUNE_TRIGGER.get(), SoundSource.BLOCKS, 1.2F, 1.4F);
            for (LivingEntity victim : victims) {
                Vec3 motion = victim.getDeltaMovement();
                victim.setDeltaMovement(motion.x * 0.4, 1.55, motion.z * 0.4);
                victim.hurtMarked = true;
            }
            for (int i = 0; i < 40; i++) {
                double angle = i / 40.0 * Math.PI * 2;
                level.sendParticles(ModParticles.ASTRAL_GLINT.get(), center.x + Math.cos(angle) * 0.7, center.y - 0.4, center.z + Math.sin(angle) * 0.7,
                    1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.GUST, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.0, center.z, 30, 0.2, 1.5, 0.2, 0.08);
        } else {
            level.playSound(null, pos, ModSounds.RUNE_TRIGGER.get(), SoundSource.BLOCKS, 1.2F, 0.7F);
            for (LivingEntity victim : victims) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STARLIGHT, null), 5.0F);
                victim.igniteForSeconds(4.0F);
            }
            for (int i = 0; i < 6; i++) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + i * 0.5, center.z, 12, 0.25, 0.2, 0.25, 0.03);
            }
            level.sendParticles(ModParticles.STAR_SPARKLE.get(), center.x, center.y + 1.5, center.z, 25, 0.4, 1.2, 0.4, 0.05);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(state.getValue(POWERED) ? 1 : 16) == 0 && level.getBlockState(pos.above()).isAir()) {
            level.addParticle(this.kind == Kind.GRAVITY ? ModParticles.ASTRAL_GLINT.get() : ModParticles.METEOR_EMBER.get(),
                pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.02, 0.0);
        }
    }
}

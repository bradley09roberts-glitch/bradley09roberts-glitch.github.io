package com.starforged.moon.boss;

import com.starforged.moon.MoonBlocks;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import com.starforged.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Lunar Anchors hold the Pale Matriarch aloft during her Inversion. Each takes four hits; every one you break drags
 * her closer to the ground, and its wreckage leaves a stone canopy - remember where they stood when the moon falls.
 */
public class LunarAnchorEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_BOSS = SynchedEntityData.defineId(LunarAnchorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HITS = SynchedEntityData.defineId(LunarAnchorEntity.class, EntityDataSerializers.INT);
    public static final int HITS = 4;
    private int age;

    public LunarAnchorEntity(EntityType<? extends LunarAnchorEntity> type, Level level) {
        super(type, level);
    }

    public void bind(PaleMatriarchEntity boss) {
        this.entityData.set(DATA_BOSS, boss.getId());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BOSS, -1);
        builder.define(DATA_HITS, HITS);
    }

    public @Nullable PaleMatriarchEntity boss() {
        return this.level().getEntity(this.entityData.get(DATA_BOSS)) instanceof PaleMatriarchEntity boss ? boss : null;
    }

    public int hitsLeft() {
        return this.entityData.get(DATA_HITS);
    }

    public int age() {
        return this.age;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        PaleMatriarchEntity boss = this.boss();
        if (boss == null || !boss.isAlive()) {
            if (!this.level().isClientSide() && this.age > 5) {
                this.discard();
            }
            return;
        }
        if (this.level().isClientSide() && this.random.nextInt(2) == 0) {
            this.level().addParticle(ModParticles.LUNAR_GLIMMER.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                this.getY() + this.random.nextDouble() * 3.0, this.getZ() + (this.random.nextDouble() - 0.5) * 0.8, 0.0, 0.03, 0.0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!(source.getEntity() instanceof Player) || this.isRemoved()) {
            return false;
        }
        int hits = this.hitsLeft() - 1;
        this.entityData.set(DATA_HITS, hits);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.5F, 0.8F + hits * 0.1F);
        Fx.burst(level, ModParticles.LUNAR_GLIMMER.get(), this.position().add(0, 1.5, 0), 20, 0.4, 0.15);
        if (hits <= 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.ANCHOR_SHATTER.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
            Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), this.position().add(0, 1.5, 0), 0.5, 70, 0.5);
            Fx.burst(level, ParticleTypes.EXPLOSION, this.position().add(0, 1.5, 0), 2, 0.3, 0.0);
            Fx.shake(level, this.position(), 24.0, 0.6F, 10);
            this.buildCanopy(level);
            PaleMatriarchEntity boss = this.boss();
            if (boss != null) {
                boss.onAnchorShattered(level);
            }
            this.discard();
        }
        return true;
    }

    /** The anchor's wreckage: a slab canopy on four posts, big enough to shelter under. */
    private void buildCanopy(ServerLevel level) {
        BlockPos base = this.blockPosition();
        BlockState post = MoonBlocks.CHISELED_LUNAR_BRICKS.get().defaultBlockState();
        BlockState roof = MoonBlocks.LUNAR_BRICK_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                for (int y = 0; y < 3; y++) {
                    BlockPos p = base.offset(dx * 2, y, dz * 2);
                    if (level.getBlockState(p).canBeReplaced()) {
                        level.setBlock(p, post, Block.UPDATE_ALL);
                    }
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = base.offset(dx, 3, dz);
                if (level.getBlockState(p).canBeReplaced()) {
                    level.setBlock(p, roof, Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

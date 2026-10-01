package com.starforged.tempest.boss;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Storm Conductor raised around Veyr's arena while he hides behind his storm shield. Right-click one to raise its rod
 * for ten seconds: the next bolt Veyr calls is pulled into the rod and thrown back at him.
 */
public class StormConductorEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_RAISED = SynchedEntityData.defineId(StormConductorEntity.class, EntityDataSerializers.INT);
    public static final int RAISE_TICKS = 200;
    private int boss = -1;

    public StormConductorEntity(EntityType<? extends StormConductorEntity> type, Level level) {
        super(type, level);
    }

    public void bind(VeyrEntity veyr) {
        this.boss = veyr.getId();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RAISED, 0);
    }

    public int raised() {
        return this.entityData.get(DATA_RAISED);
    }

    public boolean isRaised() {
        return this.raised() > 0;
    }

    public void lower() {
        this.entityData.set(DATA_RAISED, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.isRaised() || this.random.nextInt(4) == 0) {
                this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getX() + (this.random.nextDouble() - 0.5) * 0.6,
                    this.getY() + 2.6 + this.random.nextDouble() * 0.5, this.getZ() + (this.random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
            return;
        }
        if (!(this.level().getEntity(this.boss) instanceof VeyrEntity veyr) || !veyr.isAlive()) {
            this.discard();
            return;
        }
        int raised = this.raised();
        if (raised > 0) {
            this.entityData.set(DATA_RAISED, raised - 1);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (this.level() instanceof ServerLevel server && !this.isRaised()) {
            this.entityData.set(DATA_RAISED, RAISE_TICKS);
            server.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.CONDUCTOR_TURN.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
            player.sendOverlayMessage(Component.translatable("entity.starforged.storm_conductor.raised").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(Entity other) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}

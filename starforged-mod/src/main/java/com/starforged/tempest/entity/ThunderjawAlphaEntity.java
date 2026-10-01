package com.starforged.tempest.entity;

import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestSounds;
import com.starforged.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Thunderjaw Alpha: the storm-forged colossus that charges out of a Supercell. Everything a Thunderjaw does, bigger -
 * plus a <b>Chain Stamp</b> (three stamps marching forward), a <b>Storm Cage</b> (a ring of lightning closes around
 * you - stay inside it) and a <b>Sky Charge</b> (it bounds into the air and crashes down where you stand).
 * It is always charged.
 */
public class ThunderjawAlphaEntity extends ThunderjawEntity {
    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
        Component.translatable("entity.starforged.thunderjaw_alpha").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
        BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int specialCooldown = 100;
    private int chain;
    private int cage;
    private @Nullable Vec3 cageCenter;
    private int skyCharge;
    private @Nullable Vec3 skyTarget;

    public ThunderjawAlphaEntity(EntityType<? extends ThunderjawAlphaEntity> type, Level level) {
        super(type, level);
        this.xpReward = 120;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 280.0)
            .add(Attributes.ARMOR, 16.0)
            .add(Attributes.ARMOR_TOUGHNESS, 4.0)
            .add(Attributes.MOVEMENT_SPEED, 0.26)
            .add(Attributes.ATTACK_DAMAGE, 14.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.STEP_HEIGHT, 2.0)
            .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    public void announce(ServerLevel level) {
        SunFx.titleNear(level, this.position(), 128.0, Component.translatable("entity.starforged.thunderjaw_alpha").withStyle(ChatFormatting.AQUA,
            ChatFormatting.BOLD), Component.translatable("entity.starforged.thunderjaw_alpha.title").withStyle(ChatFormatting.GRAY), 10, 50, 15);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.ALPHA_ROAR.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
        TempestFx.strike(level, this.position(), this, 0.0F, 0.0);
    }

    @Override
    public boolean isCharged() {
        return true;
    }

    @Override
    protected float stampRadius() {
        return 7.0F;
    }

    @Override
    protected boolean specialAttack(ServerLevel level, LivingEntity target, double dist) {
        if (this.chain > 0) {
            this.getNavigation().stop();
            if (++this.chain % 10 == 0) {
                Vec3 forward = target.position().subtract(this.position()).multiply(1, 0, 1).normalize().scale(2.5);
                this.setDeltaMovement(forward.x * 0.5, 0.35, forward.z * 0.5);
                this.groundStamp(level);
            }
            if (this.chain >= 32) {
                this.chain = 0;
            }
            return true;
        }
        if (this.skyCharge > 0) {
            this.skyCharge++;
            if (this.skyCharge == 2) {
                this.skyTarget = target.position();
                this.setDeltaMovement(0, 1.6, 0);
            } else if (this.skyCharge == 18 && this.skyTarget != null) {
                Vec3 down = this.skyTarget.subtract(this.position());
                this.setDeltaMovement(down.x * 0.15, -1.6, down.z * 0.15);
            } else if (this.skyCharge > 18 && (this.onGround() || this.skyCharge > 60)) {
                this.groundStamp(level);
                TempestFx.strike(level, this.position(), this, 10.0F, 4.0);
                this.skyCharge = 0;
            }
            return true;
        }
        if (--this.specialCooldown > 0 || dist > 24.0) {
            return false;
        }
        this.specialCooldown = 140 + this.random.nextInt(60);
        int pick = this.random.nextInt(3);
        if (pick == 0 && dist < 10.0) {
            this.chain = 1;
        } else if (pick == 1 && this.cage == 0) {
            this.cage = 160;
            this.cageCenter = target.position();
            SunFx.messageNear(level, this.position(), 48.0, Component.translatable("entity.starforged.thunderjaw_alpha.cage")
                .withStyle(ChatFormatting.AQUA));
        } else {
            this.skyCharge = 1;
            level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.ALPHA_ROAR.get(), SoundSource.HOSTILE, 3.0F, 1.3F);
        }
        return pick != 1;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.cage > 0 && this.cageCenter != null) {
            this.cage--;
            // Storm Cage: a ring of strikes walks around the trapped player; stepping out of the ring is what hurts.
            double angle = this.cage * 0.35;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                Vec3 at = this.cageCenter.add(Math.cos(a) * 9.0, 0, Math.sin(a) * 9.0);
                if (this.cage % 6 == 0) {
                    TempestFx.strike(level, at, this, 8.0F, 2.5);
                }
                Fx.column(level, ModParticles.STATIC_SPARK.get(), at, 3.0, 4, 0.2, 0.0);
            }
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(40.0))) {
                if (player.position().distanceTo(this.cageCenter) > 9.5 && player.position().distanceTo(this.cageCenter) < 14.0 && this.cage % 10 == 0) {
                    TempestFx.zap(level, this.cageCenter.add(0, 4, 0), player, this, 4.0F);
                }
            }
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }
}

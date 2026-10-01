package com.starforged.moon.entity;

import com.starforged.moon.MoonEntities;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Pale Matriarch's MOONFALL: a colossal shard of moon that descends on the arena over fifteen seconds. Kill her
 * before it lands, or hide under cover - the open sky is death.
 */
public class FallingMoonEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_TICKS = SynchedEntityData.defineId(FallingMoonEntity.class, EntityDataSerializers.INT);
    public static final int DURATION = 300;
    public static final float SIZE = 16.0F;
    private Vec3 ground = Vec3.ZERO;
    private int bossId = -1;

    public FallingMoonEntity(EntityType<? extends FallingMoonEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static FallingMoonEntity summon(ServerLevel level, Vec3 ground, Entity boss) {
        FallingMoonEntity moon = new FallingMoonEntity(MoonEntities.FALLING_MOON.get(), level);
        moon.ground = ground;
        moon.bossId = boss.getId();
        moon.setPos(ground.x, ground.y + 60, ground.z);
        level.addFreshEntity(moon);
        return moon;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TICKS, 0);
    }

    public int fallTicks() {
        return this.entityData.get(DATA_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        int t = this.fallTicks() + 1;
        this.entityData.set(DATA_TICKS, t);
        double progress = t / (double) DURATION;
        double height = 60.0 * (1.0 - progress * progress);
        this.setPos(this.ground.x, this.ground.y + height + SIZE * 0.5, this.ground.z);
        Entity boss = server.getEntity(this.bossId);
        if (boss == null || !boss.isAlive()) {
            Fx.sphere(server, ModParticles.LUNAR_GLIMMER.get(), this.position(), 3.0, 240, 1.2);
            server.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.ANCHOR_SHATTER.get(), SoundSource.HOSTILE, 6.0F, 0.5F);
            SunFx.messageNear(server, this.ground, 96.0, Component.translatable("entity.starforged.pale_matriarch.moonfall_shattered")
                .withStyle(ChatFormatting.AQUA));
            this.discard();
            return;
        }
        if (t % 20 == 0 && t < DURATION) {
            int seconds = (DURATION - t) / 20;
            SunFx.messageNear(server, this.ground, 96.0, Component.translatable("entity.starforged.pale_matriarch.moonfall_countdown", seconds)
                .withStyle(seconds <= 5 ? ChatFormatting.RED : ChatFormatting.AQUA));
            server.playSound(null, this.ground.x, this.ground.y + 20, this.ground.z, MoonSounds.MATRIARCH_MOONFALL.get(), SoundSource.HOSTILE, 4.0F,
                0.5F + (float) progress * 0.5F);
        }
        if (t % 3 == 0) {
            server.sendParticles(ModParticles.MOON_DUST.get(), true, true, this.getX(), this.getY() - SIZE * 0.4, this.getZ(), 10, SIZE * 0.3, 1.0,
                SIZE * 0.3, 0.05);
        }
        if (t >= DURATION) {
            this.impact(server, boss);
        }
    }

    private void impact(ServerLevel server, Entity boss) {
        DamageSource source = ModDamageTypes.source(server, ModDamageTypes.GRAVITY, boss);
        for (Player player : server.getEntitiesOfClass(Player.class, new AABB(BlockPos.containing(this.ground)).inflate(48.0, 24.0, 48.0),
            p -> !p.isSpectator() && !p.isCreative())) {
            boolean covered = !server.canSeeSky(player.blockPosition().above());
            player.hurtServer(server, source, covered ? 4.0F : 34.0F);
            player.setDeltaMovement(player.getDeltaMovement().add(0, covered ? 0.2 : 1.0, 0));
            player.hurtMarked = true;
        }
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, this.ground.x, this.ground.y + 1, this.ground.z, 6, 6, 1, 6, 0);
        Fx.sphere(server, ModParticles.LUNAR_GLIMMER.get(), this.ground.add(0, 2, 0), 2.0, 400, 2.2);
        Fx.ring(server, ModParticles.MOON_DUST.get(), this.ground.add(0, 0.5, 0), 2.0, 160, 1.4, 0.05);
        server.playSound(null, this.ground.x, this.ground.y, this.ground.z, MoonSounds.MATRIARCH_MOONFALL.get(), SoundSource.HOSTILE, 10.0F, 0.4F);
        Fx.shake(server, this.ground, 128.0, 3.0F, 50);
        SunFx.messageNear(server, this.ground, 96.0, Component.translatable("entity.starforged.pale_matriarch.moonfall_impact")
            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        this.discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320 * 320;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}

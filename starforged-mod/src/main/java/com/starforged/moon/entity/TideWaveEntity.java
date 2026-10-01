package com.starforged.moon.entity;

import com.starforged.moon.MoonEntities;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** The Tidecaller Glaive's Silver Tide: a seven-block-wide wall of silver water that rolls forward, sweeping enemies along. */
public class TideWaveEntity extends Projectile {
    public static final int LIFE = 32;
    private final Set<Integer> hit = new HashSet<>();
    private float damage = 10.0F;
    private int life;

    public TideWaveEntity(EntityType<? extends TideWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void send(ServerLevel level, LivingEntity owner, float damage) {
        TideWaveEntity wave = new TideWaveEntity(MoonEntities.TIDE_WAVE.get(), level);
        wave.setOwner(owner);
        wave.damage = damage;
        Vec3 dir = Vec3.directionFromRotation(0, owner.getYRot());
        wave.setPos(owner.getX() + dir.x * 1.5, owner.getY(), owner.getZ() + dir.z * 1.5);
        wave.setYRot(owner.getYRot());
        wave.setDeltaMovement(dir.scale(0.85));
        level.addFreshEntity(wave);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public int life() {
        return this.life;
    }

    @Override
    public void tick() {
        super.tick();
        this.life++;
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        Vec3 dir = motion.lengthSqr() > 0 ? motion.normalize() : Vec3.directionFromRotation(0, this.getYRot());
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (this.level() instanceof ServerLevel server) {
            Entity owner = this.getOwner();
            for (int i = -3; i <= 3; i++) {
                Vec3 p = this.position().add(side.scale(i));
                server.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.6, p.z, 6, 0.3, 0.5, 0.3, 0.1);
                server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), p.x, p.y + 1.2, p.z, 1, 0.2, 0.4, 0.2, 0.02);
                if (i % 2 == 0) {
                    server.sendParticles(ParticleTypes.BUBBLE_POP, p.x, p.y + 1.6, p.z, 2, 0.3, 0.3, 0.3, 0.02);
                }
            }
            for (LivingEntity victim : Combat.targetsAround(server, owner, this.position().add(0, 1, 0), 4.2)) {
                Vec3 rel = victim.position().subtract(this.position());
                if (Math.abs(rel.dot(dir)) > 1.6) {
                    continue;
                }
                victim.clearFire();
                victim.setDeltaMovement(dir.x * 0.9, 0.25, dir.z * 0.9);
                victim.hurtMarked = true;
                if (this.hit.add(victim.getId())) {
                    victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.MOONLIGHT, this, owner), this.damage);
                }
            }
            if (this.life >= LIFE) {
                this.discard();
            }
        }
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }
}

package com.starforged.moon.entity;

import com.starforged.moon.MoonEntities;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Crescent Glaive: it sweeps two full orbits around its wielder, cutting everything it passes, then
 * returns to their hand.
 */
public class CrescentGlaiveEntity extends Projectile {
    public static final int LIFE = 44;
    private final Set<Integer> hitThisLap = new HashSet<>();
    private float damage = 9.0F;
    private float startAngle;
    private int life;
    private int lap;

    public CrescentGlaiveEntity(EntityType<? extends CrescentGlaiveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spin(ServerLevel level, LivingEntity owner, float damage) {
        CrescentGlaiveEntity glaive = new CrescentGlaiveEntity(MoonEntities.CRESCENT_GLAIVE.get(), level);
        glaive.setOwner(owner);
        glaive.damage = damage;
        glaive.startAngle = (owner.getYRot() + 90.0F) * (float) (Math.PI / 180.0);
        glaive.setPos(owner.getX(), owner.getY() + 1.0, owner.getZ());
        level.addFreshEntity(glaive);
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
        Entity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            if (!this.level().isClientSide()) {
                this.discard();
            }
            return;
        }
        float progress = this.life / (float) LIFE;
        double radius = progress < 0.15 ? 1.0 + progress / 0.15 * 3.5 : progress > 0.85 ? 4.5 * (1.0 - progress) / 0.15 + 0.5 : 4.5;
        double angle = this.startAngle + this.life * (Math.PI * 4 / LIFE);
        Vec3 at = owner.position().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
        this.setPos(at.x, at.y, at.z);
        if (this.level() instanceof ServerLevel server) {
            int currentLap = this.life * 2 / LIFE;
            if (currentLap != this.lap) {
                this.lap = currentLap;
                this.hitThisLap.clear();
            }
            for (LivingEntity victim : Combat.targetsAround(server, owner, at, 1.6)) {
                if (this.hitThisLap.add(victim.getId())) {
                    victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.MOONLIGHT, this, owner), this.damage);
                    server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        10, 0.2, 0.3, 0.2, 0.08);
                    server.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.5F);
                }
            }
            if (this.life % 5 == 0) {
                server.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.3F, 2.0F);
            }
            if (this.life >= LIFE) {
                this.discard();
            }
        } else {
            this.level().addParticle(ModParticles.LUNAR_GLIMMER.get(), at.x, at.y, at.z, 0.0, 0.0, 0.0);
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

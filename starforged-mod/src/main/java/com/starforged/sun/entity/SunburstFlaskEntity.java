package com.starforged.sun.entity;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunEntities;
import com.starforged.sun.SunItems;
import com.starforged.sun.SunSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The thrown Sunburst Flask: shatters in a blinding solar flash that scorches and dazzles everything nearby. */
public class SunburstFlaskEntity extends ThrowableItemProjectile {
    public SunburstFlaskEntity(EntityType<? extends SunburstFlaskEntity> type, Level level) {
        super(type, level);
    }

    public SunburstFlaskEntity(Level level, LivingEntity owner, ItemStack stack) {
        super(SunEntities.SUNBURST_FLASK.get(), owner, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return SunItems.SUNBURST_FLASK.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.level().addParticle(ModParticles.SOLAR_SPARK.get(), this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 at = hitResult.getLocation().add(0, 0.4, 0);
        for (LivingEntity victim : Combat.targetsAround(server, this.getOwner(), at, 5.5)) {
            victim.hurtServer(server, ModDamageTypes.source(server, ModDamageTypes.SUNFIRE, this, this.getOwner()), 7.0F);
            victim.igniteForSeconds(6.0F);
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this.getOwner());
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this.getOwner());
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), this.getOwner());
            Combat.blast(victim, at, 0.8, 0.35);
        }
        server.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE9A0), at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.0);
        Fx.sphere(server, ModParticles.SOLAR_SPARK.get(), at, 0.5, 90, 0.5);
        Fx.ring(server, ParticleTypes.FLAME, at, 0.8, 40, 0.4, 0.05);
        Fx.ring(server, ModParticles.ECLIPSE_FLARE.get(), at, 0.5, 8, 0.2, 0.0);
        server.playSound(null, at.x, at.y, at.z, SunSounds.FLASK_BURST.get(), SoundSource.PLAYERS, 1.4F, 1.0F);
        Fx.shake(server, at, 12.0, 0.35F, 8);
        this.discard();
    }
}

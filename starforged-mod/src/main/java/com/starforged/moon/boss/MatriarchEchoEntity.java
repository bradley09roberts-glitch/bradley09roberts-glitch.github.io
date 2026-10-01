package com.starforged.moon.boss;

import com.starforged.moon.MoonSounds;
import com.starforged.moon.entity.MoonletEntity;
import com.starforged.registry.ModParticles;
import com.starforged.util.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A false Matriarch conjured in the New Moon phase. It looks exactly like her - except that it casts no shadow.
 * Strike one and it bursts into blinding moonlight, and the real Matriarch drinks the light back.
 */
public class MatriarchEchoEntity extends Monster {
    private int bossId = -1;
    private Vec3 post = Vec3.ZERO;
    private int fireCooldown = 60;

    public MatriarchEchoEntity(EntityType<? extends MatriarchEchoEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1024.0)
            .add(Attributes.FOLLOW_RANGE, 64.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public void bind(PaleMatriarchEntity boss, Vec3 post) {
        this.bossId = boss.getId();
        this.post = post;
        this.setHealth(boss.getHealth());
    }

    public void moveTo(Vec3 post) {
        this.post = post;
        this.setPos(post.x, post.y, post.z);
    }

    public @Nullable PaleMatriarchEntity boss() {
        return this.level().getEntity(this.bossId) instanceof PaleMatriarchEntity boss ? boss : null;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        PaleMatriarchEntity boss = this.boss();
        if (boss == null || !boss.isAlive() || boss.phase() != PaleMatriarchEntity.PHASE_NEW_MOON) {
            this.vanish(level);
            return;
        }
        this.setHealth(Math.max(1.0F, boss.getHealth()));
        double bob = Math.sin(this.tickCount * 0.08 + this.getId()) * 0.4;
        Vec3 want = this.post.add(0, bob, 0);
        this.setDeltaMovement(want.subtract(this.position()).scale(0.2));
        Player target = level.getNearestPlayer(this, 48.0);
        if (target != null) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();
            this.setYRot((float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();
            if (--this.fireCooldown <= 0 && !target.isCreative() && !target.isSpectator()) {
                this.fireCooldown = 50 + this.random.nextInt(40);
                Vec3 from = this.position().add(0, 3.0, 0);
                MoonletEntity.shoot(level, this, from, target.getEyePosition().subtract(from), 1.2F, 4.0F, MoonletEntity.Kind.CRESCENT, null);
            }
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) {
            return false;
        }
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
        PaleMatriarchEntity boss = this.boss();
        if (boss != null) {
            boss.heal(12.0F);
            boss.onEchoShattered(level, this);
        }
        Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), this.position().add(0, 2.5, 0), 0.6, 120, 0.7);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.ANCHOR_SHATTER.get(), SoundSource.HOSTILE, 2.0F, 1.4F);
        this.discard();
        return true;
    }

    private void vanish(ServerLevel level) {
        level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), this.getX(), this.getY() + 2.5, this.getZ(), 40, 0.8, 1.5, 0.8, 0.05);
        this.discard();
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MoonSounds.MATRIARCH_HURT.get();
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    public @Nullable LivingEntity echoTarget() {
        return this.getTarget();
    }
}

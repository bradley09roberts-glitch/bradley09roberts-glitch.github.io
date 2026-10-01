package com.starforged.entity.animal;

import com.starforged.entity.projectile.StarboltEntity;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.registry.ModTags;
import java.util.EnumSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Starlings: tiny living stars that hatch from Astral Eggs. A bonded Starling follows you everywhere, lights your
 * nights (Night Vision while it is near), and fires little starbolts at anything that threatens you.
 * Feed it Stardust to heal it; right-click with an empty hand to make it wait.
 */
public class StarlingEntity extends TamableAnimal {
    public StarlingEntity(EntityType<? extends StarlingEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.FLYING_SPEED, 0.6)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new BoltGoal());
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2, 6.0F, 2.0F));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ModTags.STARLING_FOOD);
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    protected boolean canFlyToOwner() {
        return true;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.isTame()) {
            if (this.isFood(stack)) {
                if (this.level() instanceof ServerLevel) {
                    this.usePlayerItem(player, hand, stack);
                    if (this.random.nextInt(3) == 0) {
                        this.tame(player);
                        this.setOrderedToSit(false);
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        this.level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return super.mobInteract(player, hand);
        }
        if (this.isOwnedBy(player)) {
            if (this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide()) {
                    this.usePlayerItem(player, hand, stack);
                    this.heal(8.0F);
                }
                this.spawnHeal();
                return InteractionResult.SUCCESS;
            }
            if (stack.isEmpty()) {
                if (!this.level().isClientSide()) {
                    boolean sit = !this.isOrderedToSit();
                    this.setOrderedToSit(sit);
                    this.getNavigation().stop();
                    this.setTarget(null);
                    player.sendOverlayMessage(Component.translatable(sit ? "entity.starforged.starling.wait" : "entity.starforged.starling.follow")
                        .withStyle(ChatFormatting.AQUA));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    private void spawnHeal() {
        for (int i = 0; i < 6; i++) {
            this.level().addParticle(ParticleTypes.HEART, this.getRandomX(0.6), this.getRandomY() + 0.3, this.getRandomZ(0.6), 0, 0.05, 0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        // Starlight companion: lights the night for its owner.
        if (this.isTame() && this.tickCount % 40 == 0 && level.isDarkOutside()) {
            LivingEntity owner = this.getOwner();
            if (owner != null && owner.distanceTo(this) < 16.0) {
                owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
            this.level().addParticle(ModParticles.STAR_SPARKLE.get(), this.getRandomX(0.5), this.getY() + 0.25, this.getRandomZ(0.5), 0.0, -0.01, 0.0);
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    public boolean isFlying() {
        return !this.onGround();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.STARLING_CHIRP.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    /** Keep a little distance from the target and pepper it with starbolts. */
    private class BoltGoal extends Goal {
        private int cooldown;

        BoltGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = StarlingEntity.this.getTarget();
            return target != null && target.isAlive() && !StarlingEntity.this.isOrderedToSit();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            StarlingEntity self = StarlingEntity.this;
            LivingEntity target = self.getTarget();
            if (target == null) {
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double angle = self.tickCount * 0.05 + self.getId();
            Vec3 orbit = target.position().add(Math.cos(angle) * 5.0, 2.5, Math.sin(angle) * 5.0);
            self.getMoveControl().setWantedPosition(orbit.x, orbit.y, orbit.z, 1.2);
            if (--this.cooldown <= 0 && self.hasLineOfSight(target) && self.level() instanceof ServerLevel server) {
                Vec3 from = self.position().add(0, 0.3, 0);
                StarboltEntity.shoot(server, self, from, target.getEyePosition().subtract(from), 1.2F, 4.0F, StarboltEntity.Variant.STAR, target);
                server.playSound(null, self.getX(), self.getY(), self.getZ(), ModSounds.STARLING_CHIRP.get(), SoundSource.NEUTRAL, 0.6F, 1.6F);
                this.cooldown = 25 + self.random.nextInt(10);
            }
        }
    }
}

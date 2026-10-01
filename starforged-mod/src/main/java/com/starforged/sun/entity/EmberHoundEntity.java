package com.starforged.sun.entity;

import com.starforged.registry.ModParticles;
import com.starforged.sun.SunItems;
import com.starforged.sun.SunSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Ember Hounds: wolves of smouldering coal that hunt the Sunlands in packs. Wild hounds attack on sight,
 * but one fed Solar Essence may come to trust you - and a bonded hound's bite sets your enemies ablaze.
 */
public class EmberHoundEntity extends TamableAnimal {
    public EmberHoundEntity(EntityType<? extends EmberHoundEntity> type, Level level) {
        super(type, level);
        this.xpReward = 7;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.MOVEMENT_SPEED, 0.34)
            .add(Attributes.ATTACK_DAMAGE, 5.0)
            .add(Attributes.ARMOR, 3.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.2, 10.0F, 2.0F));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
            (target, level) -> !this.isTame()));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(SunItems.SOLAR_ESSENCE.get());
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
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
                        this.setTarget(null);
                        this.getNavigation().stop();
                        this.setOrderedToSit(false);
                        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
                        this.setHealth(40.0F);
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
                    this.heal(12.0F);
                }
                return InteractionResult.SUCCESS;
            }
            if (stack.isEmpty()) {
                if (!this.level().isClientSide()) {
                    boolean sit = !this.isOrderedToSit();
                    this.setOrderedToSit(sit);
                    this.getNavigation().stop();
                    this.setTarget(null);
                    player.sendOverlayMessage(Component.translatable(sit ? "entity.starforged.ember_hound.wait" : "entity.starforged.ember_hound.follow")
                        .withStyle(ChatFormatting.GOLD));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            target.igniteForSeconds(4.0F);
            level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.2, 0.3, 0.2, 0.03);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
            this.level().addParticle(this.random.nextBoolean() ? ParticleTypes.SMOKE : ModParticles.SOLAR_SPARK.get(),
                this.getX() + (this.random.nextDouble() - 0.5) * 0.6, this.getY() + 0.9, this.getZ() + (this.random.nextDouble() - 0.5) * 0.6,
                0.0, 0.03, 0.0);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.isTame();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.getTarget() != null ? SunSounds.HOUND_GROWL.get() : SunSounds.HOUND_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SunSounds.HOUND_GROWL.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SunSounds.HOUND_DEATH.get();
    }
}

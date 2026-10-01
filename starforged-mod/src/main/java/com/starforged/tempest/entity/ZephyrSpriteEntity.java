package com.starforged.tempest.entity;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestItems;
import com.starforged.tempest.TempestSounds;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Zephyr Sprites: little wind spirits that nest in Gale Seeds. Tame one with a Gale Seed and it rides the air at your
 * shoulder.
 * <ul>
 *     <li><b>Windguard</b> - it blows projectiles aimed at you off course.</li>
 *     <li><b>Tailwind</b> - sneak + right-click it for a rush of wind at your back: Speed, Jump Boost and Slow Falling
 *     for fifteen seconds (every 45 seconds).</li>
 * </ul>
 * Right-click with an empty hand to make it wait.
 */
public class ZephyrSpriteEntity extends TamableAnimal {
    private int windguardCooldown;
    private int tailwindCooldown;

    public ZephyrSpriteEntity(EntityType<? extends ZephyrSpriteEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.FLYING_SPEED, 0.7)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
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
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.3, 5.0F, 2.0F));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(TempestItems.GALE_SEED.get());
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
        if (this.isOwnedBy(player) && stack.isEmpty()) {
            if (!this.level().isClientSide()) {
                if (player.isShiftKeyDown()) {
                    this.tailwind(player);
                } else {
                    boolean sit = !this.isOrderedToSit();
                    this.setOrderedToSit(sit);
                    this.getNavigation().stop();
                    player.sendOverlayMessage(Component.translatable(sit ? "entity.starforged.zephyr_sprite.wait" : "entity.starforged.zephyr_sprite.follow")
                        .withStyle(ChatFormatting.AQUA));
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (this.isOwnedBy(player) && this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
            if (!this.level().isClientSide()) {
                this.usePlayerItem(player, hand, stack);
                this.heal(8.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    private void tailwind(Player owner) {
        if (this.tailwindCooldown > 0) {
            owner.sendOverlayMessage(Component.translatable("entity.starforged.zephyr_sprite.tired", (this.tailwindCooldown + 19) / 20)
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        this.tailwindCooldown = 900;
        owner.addEffect(new MobEffectInstance(MobEffects.SPEED, 300, 1, false, false, true));
        owner.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 300, 1, false, false, true));
        owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, false, true));
        owner.sendOverlayMessage(Component.translatable("entity.starforged.zephyr_sprite.tailwind").withStyle(ChatFormatting.AQUA));
        if (this.level() instanceof ServerLevel server) {
            server.playSound(null, owner.getX(), owner.getY(), owner.getZ(), TempestSounds.SPRITE_GUARD.get(), SoundSource.NEUTRAL, 1.0F, 1.3F);
            server.sendParticles(ModParticles.STORM_WISP.get(), owner.getX(), owner.getY() + 0.5, owner.getZ(), 40, 0.6, 0.4, 0.6, 0.15);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.tailwindCooldown > 0) {
            this.tailwindCooldown--;
        }
        if (this.windguardCooldown > 0) {
            this.windguardCooldown--;
            return;
        }
        LivingEntity owner = this.getOwner();
        if (!this.isTame() || owner == null || owner.distanceTo(this) > 12.0) {
            return;
        }
        // Windguard: turn aside projectiles closing on the owner.
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(5.0))) {
            if (projectile.getOwner() == owner || projectile.getOwner() == this) {
                continue;
            }
            Vec3 v = projectile.getDeltaMovement();
            Vec3 toOwner = owner.position().add(0, 1, 0).subtract(projectile.position());
            if (v.lengthSqr() < 0.01 || v.dot(toOwner) <= 0) {
                continue;
            }
            Vec3 away = projectile.position().subtract(owner.position()).multiply(1, 0, 1).normalize();
            projectile.setDeltaMovement(away.scale(v.length()).add(0, 0.4, 0));
            projectile.hurtMarked = true;
            level.sendParticles(ModParticles.STORM_WISP.get(), projectile.getX(), projectile.getY(), projectile.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), TempestSounds.SPRITE_GUARD.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
            this.windguardCooldown = 30;
            break;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && this.random.nextInt(3) == 0) {
            this.level().addParticle(ModParticles.STORM_WISP.get(), this.getRandomX(0.5), this.getY() + 0.1, this.getRandomZ(0.5), 0.0, -0.03, 0.0);
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
        return TempestSounds.SPRITE_AMBIENT.get();
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

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.isTame();
    }
}

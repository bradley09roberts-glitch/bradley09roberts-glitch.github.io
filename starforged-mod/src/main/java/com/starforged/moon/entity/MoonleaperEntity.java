package com.starforged.moon.entity;

import com.starforged.moon.MoonItems;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Moonleaper: an enormous celestial hare. Tame one with Moonpetals and ride it. Hold jump to coil its legs and
 * release to leap - a full charge carries you twenty-odd blocks (much further in the Pale Reach), and the landing
 * sends out a gravity shockwave.
 */
public class MoonleaperEntity extends TamableAnimal {
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(MoonleaperEntity.class, EntityDataSerializers.INT);
    public static final int MAX_CHARGE = 30;
    private int charge;
    private boolean leaping;
    private int leapTicks;

    public MoonleaperEntity(EntityType<? extends MoonleaperEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 60.0)
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.ARMOR, 4.0)
            .add(Attributes.STEP_HEIGHT, 1.5)
            .add(Attributes.SAFE_FALL_DISTANCE, 40.0)
            .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CHARGE, 0);
    }

    public int leapCharge() {
        return this.entityData.get(DATA_CHARGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.5));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.1, 12.0F, 3.0F));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(MoonItems.MOONPETAL.get());
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
                if (!this.level().isClientSide()) {
                    this.usePlayerItem(player, hand, stack);
                    if (this.random.nextInt(4) == 0) {
                        this.tame(player);
                        this.level().broadcastEntityEvent(this, (byte) 7);
                    } else {
                        this.level().broadcastEntityEvent(this, (byte) 6);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        if (this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
            if (!this.level().isClientSide()) {
                this.usePlayerItem(player, hand, stack);
                this.heal(10.0F);
            }
            return InteractionResult.SUCCESS;
        }
        if (this.isOwnedBy(player) && !player.isSecondaryUseActive() && !this.isVehicle()) {
            if (!this.level().isClientSide()) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return this.isTame() && this.getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        if (this.charge > 0) {
            return Vec3.ZERO;
        }
        float strafe = controller.xxa * 0.5F;
        float forward = controller.zza;
        if (forward <= 0.0F) {
            forward *= 0.25F;
        }
        return new Vec3(strafe, 0.0, forward);
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        this.setRot(controller.getYRot(), controller.getXRot() * 0.5F);
        this.yRotO = this.yBodyRot = this.yHeadRot = this.getYRot();
        boolean jumping = controller instanceof net.minecraft.server.level.ServerPlayer sp ? sp.getLastClientInput().jump() : controller.isJumping();
        if (this.onGround()) {
            if (jumping) {
                this.charge = Math.min(MAX_CHARGE, this.charge + 1);
                if (!this.level().isClientSide()) {
                    this.entityData.set(DATA_CHARGE, this.charge);
                    if (this.charge % 3 == 0) {
                        int bars = this.charge * 10 / MAX_CHARGE;
                        controller.sendOverlayMessage(Component.literal("▮".repeat(bars) + "▯".repeat(10 - bars))
                            .withStyle(bars >= 10 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
                    }
                }
            } else if (this.charge > 0) {
                this.leap(controller);
            }
        }
    }

    private void leap(Player controller) {
        float power = Math.max(0.25F, this.charge / (float) MAX_CHARGE);
        this.charge = 0;
        Vec3 look = Vec3.directionFromRotation(0, controller.getYRot());
        this.setDeltaMovement(look.x * 1.6 * power, 0.55 + 0.75 * power, look.z * 1.6 * power);
        this.needsSync = true;
        this.leaping = true;
        this.leapTicks = 0;
        if (!this.level().isClientSide()) {
            this.entityData.set(DATA_CHARGE, 0);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.LEAPER_LEAP.get(), SoundSource.NEUTRAL, 1.2F,
                1.3F - power * 0.4F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.leaping) {
            this.leapTicks++;
            if (this.level().isClientSide()) {
                this.level().addParticle(ModParticles.MOON_DUST.get(), this.getX(), this.getY() + 0.3, this.getZ(), 0.0, 0.0, 0.0);
            }
            if (this.onGround() && this.leapTicks > 4) {
                this.leaping = false;
                if (this.level() instanceof ServerLevel server) {
                    Vec3 at = this.position();
                    for (LivingEntity victim : Combat.targetsAround(server, this.getControllingPassenger(), at, 4.5)) {
                        if (victim == this) {
                            continue;
                        }
                        victim.hurtServer(server, this.damageSources().mobAttack(this), 6.0F);
                        Combat.blast(victim, at, 1.0, 0.6);
                    }
                    Fx.ring(server, ModParticles.MOON_DUST.get(), at.add(0, 0.2, 0), 1.0, 40, 0.5, 0.02);
                    Fx.shake(server, at, 12.0, 0.4F, 8);
                    server.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_SMALL_FALL, SoundSource.NEUTRAL, 1.5F, 0.6F);
                }
            }
        }
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.25F;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.isTame() && !this.isVehicle();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MoonSounds.LEAPER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MoonSounds.LEAPER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MoonSounds.LEAPER_AMBIENT.get();
    }

    public boolean isLeaping() {
        return this.leaping || !this.onGround();
    }

    public float bodyLean() {
        return Mth.clamp((float) this.getDeltaMovement().y * 2.0F, -0.8F, 0.8F);
    }
}

package com.starforged.tempest.entity;

import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestItems;
import com.starforged.tempest.TempestSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Storm Rocs: vast thunderbirds of the Shardwind Cliffs - the fastest mounts in the game. Win one over with Charged
 * Aether Dust, then ride it: look to steer, hold forward to fly, jump to climb.
 * <ul>
 *     <li><b>Wingburst</b> - sprint to beat its wings: a burst of speed that blasts away anything beside you.</li>
 *     <li><b>Storm Dive</b> - dive steeply into the ground at speed and it lands in a crash of lightning.</li>
 * </ul>
 * Its rider is immune to the storm's lightning.
 */
public class StormRocEntity extends TamableAnimal {
    private int burstCooldown;
    private int diveCooldown;

    public StormRocEntity(EntityType<? extends StormRocEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 6, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 80.0)
            .add(Attributes.FLYING_SPEED, 0.75)
            .add(Attributes.MOVEMENT_SPEED, 0.32)
            .add(Attributes.ARMOR, 6.0)
            .add(Attributes.FOLLOW_RANGE, 48.0);
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
        this.goalSelector.addGoal(1, new SoarGoal());
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(TempestItems.CHARGED_AETHER_DUST.get());
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
                this.heal(12.0F);
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
        float strafe = controller.xxa * 0.5F;
        float forward = 0.0F;
        float up = 0.0F;
        if (controller.zza != 0.0F) {
            float pitch = controller.getXRot() * Mth.DEG_TO_RAD;
            forward = Mth.cos(pitch);
            up = -Mth.sin(pitch);
            if (controller.zza < 0.0F) {
                forward *= -0.4F;
                up *= -0.4F;
            }
        }
        if (controller.isJumping()) {
            up += 0.6F;
        }
        return new Vec3(strafe, up, forward);
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 rot = new Vec2(controller.getXRot() * 0.5F, controller.getYRot());
        float yRot = this.getYRot() + Mth.wrapDegrees(rot.y - this.getYRot()) * 0.2F;
        this.setRot(yRot, rot.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = yRot;
        if (this.level() instanceof ServerLevel server && controller instanceof ServerPlayer rider) {
            this.riddenAbilities(server, rider);
        }
    }

    private void riddenAbilities(ServerLevel level, ServerPlayer rider) {
        if (this.burstCooldown > 0) {
            this.burstCooldown--;
        }
        if (this.diveCooldown > 0) {
            this.diveCooldown--;
        }
        if (this.burstCooldown == 0 && rider.getLastClientInput().sprint()) {
            this.burstCooldown = 100;
            Vec3 look = rider.getLookAngle();
            this.setDeltaMovement(this.getDeltaMovement().add(look.scale(2.2)));
            this.hurtMarked = true;
            for (LivingEntity victim : Combat.targetsAround(level, rider, this.position(), 5.0)) {
                if (victim != this) {
                    Combat.blast(victim, this.position(), 1.4, 0.6);
                }
            }
            Fx.ring(level, ModParticles.STORM_WISP.get(), this.position().add(0, 1.0, 0), 1.5, 50, 0.8, 0.0);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.ROC_SCREECH.get(), SoundSource.NEUTRAL, 1.5F, 1.2F);
        }
        Vec3 v = this.getDeltaMovement();
        boolean diving = v.y < -0.8 && v.length() > 1.0 && rider.getXRot() > 40.0F;
        if (diving && this.diveCooldown == 0) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ());
            if (this.getY() - ground < 2.5 && ground > level.getMinY() + 4) {
                this.diveCooldown = 60;
                TempestFx.strike(level, this.position(), rider, 12.0F, 5.0);
                for (LivingEntity victim : Combat.targetsAround(level, rider, this.position(), 6.0)) {
                    if (victim != this) {
                        Combat.blast(victim, this.position(), 1.2, 0.8);
                    }
                }
                Fx.shake(level, this.position(), 20.0, 0.7F, 10);
                this.setDeltaMovement(v.x, 0.6, v.z);
            }
        }
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return (float) this.getAttributeValue(Attributes.FLYING_SPEED);
    }

    @Override
    public void travel(Vec3 input) {
        // Cruise speed settles at push / 0.09 blocks per tick: ~36 blocks a second when ridden.
        float speed = (float) this.getAttributeValue(Attributes.FLYING_SPEED) * (this.isVehicle() ? 0.24F : 0.5F);
        this.travelFlying(input, speed, speed, speed);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            if (this.getDeltaMovement().length() > 0.6 || this.random.nextInt(4) == 0) {
                this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getRandomX(2.0), this.getRandomY(), this.getRandomZ(2.0), 0, 0, 0);
            }
        }
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return !this.isTame() && !this.isVehicle();
    }

    public boolean isFlying() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return TempestSounds.ROC_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 260;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return TempestSounds.ROC_SCREECH.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TempestSounds.ROC_SCREECH.get();
    }

    /** Soars in wide arcs high over the cliffs; tame rocs stay near their owner. */
    private class SoarGoal extends Goal {
        private @Nullable Vec3 waypoint;

        SoarGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !StormRocEntity.this.isVehicle();
        }

        @Override
        public void tick() {
            StormRocEntity roc = StormRocEntity.this;
            if (this.waypoint == null || roc.position().distanceToSqr(this.waypoint) < 9.0 || roc.random.nextInt(200) == 0) {
                Vec3 anchor = roc.position();
                LivingEntity owner = roc.getOwner();
                if (roc.isTame() && owner != null) {
                    anchor = owner.position();
                }
                double angle = roc.random.nextDouble() * Math.PI * 2;
                double dist = roc.isTame() ? 4 + roc.random.nextDouble() * 6 : 12 + roc.random.nextDouble() * 24;
                int x = (int) (anchor.x + Math.cos(angle) * dist);
                int z = (int) (anchor.z + Math.sin(angle) * dist);
                int ground = roc.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (ground <= roc.level().getMinY() + 4) {
                    ground = (int) anchor.y - 8;
                }
                double height = roc.isTame() ? 3 + roc.random.nextDouble() * 4 : 10 + roc.random.nextDouble() * 16;
                this.waypoint = new Vec3(x + 0.5, Math.max(ground + height, roc.level().getMinY() + 5), z + 0.5);
            }
            roc.getMoveControl().setWantedPosition(this.waypoint.x, this.waypoint.y, this.waypoint.z, 1.0);
        }
    }
}

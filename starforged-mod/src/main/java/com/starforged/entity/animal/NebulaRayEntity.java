package com.starforged.entity.animal;

import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.registry.ModTags;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
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
 * Nebula Rays: enormous gentle manta rays that swim through the night sky during a Starfall.
 * Feed one Stardust until it trusts you, then right-click to ride it. Look where you want to go,
 * hold forward to fly, and jump to climb.
 */
public class NebulaRayEntity extends TamableAnimal {
    public NebulaRayEntity(EntityType<? extends NebulaRayEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 6, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
            .add(Attributes.MAX_HEALTH, 60.0)
            .add(Attributes.FLYING_SPEED, 0.4)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.ARMOR, 4.0)
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
        return stack.is(ModTags.STARLING_FOOD);
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
        float yRot = this.getYRot() + Mth.wrapDegrees(rot.y - this.getYRot()) * 0.15F;
        this.setRot(yRot, rot.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = yRot;
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return (float) this.getAttributeValue(Attributes.FLYING_SPEED);
    }

    @Override
    public void travel(Vec3 input) {
        float speed = (float) this.getAttributeValue(Attributes.FLYING_SPEED) * (this.isVehicle() ? 1.6F : 0.5F);
        this.travelFlying(input, speed, speed, speed);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) {
            double speed = this.getDeltaMovement().length();
            if (this.random.nextInt(speed > 0.3 ? 1 : 5) == 0) {
                float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
                double side = this.random.nextBoolean() ? 1.6 : -1.6;
                this.level().addParticle(ModParticles.STAR_SPARKLE.get(), this.getX() + Math.cos(yaw) * side, this.getY() + 0.3,
                    this.getZ() + Math.sin(yaw) * side, 0.0, -0.02, 0.0);
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
        return ModSounds.NEBULA_RAY_SONG.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
    }

    /** Glides in slow arcs high above the ground; tame rays stay near their owner. */
    private class SoarGoal extends Goal {
        private @Nullable Vec3 waypoint;

        SoarGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !NebulaRayEntity.this.isVehicle();
        }

        @Override
        public void tick() {
            NebulaRayEntity ray = NebulaRayEntity.this;
            if (this.waypoint == null || ray.position().distanceToSqr(this.waypoint) < 9.0 || ray.random.nextInt(200) == 0) {
                Vec3 anchor = ray.position();
                LivingEntity owner = ray.getOwner();
                if (ray.isTame() && owner != null) {
                    anchor = owner.position();
                }
                double angle = ray.random.nextDouble() * Math.PI * 2;
                double dist = ray.isTame() ? 4 + ray.random.nextDouble() * 6 : 10 + ray.random.nextDouble() * 20;
                int x = (int) (anchor.x + Math.cos(angle) * dist);
                int z = (int) (anchor.z + Math.sin(angle) * dist);
                int ground = ray.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                double height = ray.isTame() ? 3 + ray.random.nextDouble() * 4 : 10 + ray.random.nextDouble() * 14;
                this.waypoint = new Vec3(x + 0.5, Math.max(ground + height, ray.level().getMinY() + 5), z + 0.5);
            }
            ray.getMoveControl().setWantedPosition(this.waypoint.x, this.waypoint.y, this.waypoint.z, 1.0);
        }
    }

    public BlockPos homePosition() {
        return this.blockPosition();
    }
}

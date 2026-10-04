package com.terracraft.entity.mob;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Flying Dutchman: the Pirate Invasion's ghost ship. It sails slowly back and forth above its target, staying
 * high in the air, and its four cannons (two on each side) fire arcing cannonballs in turn.
 */
public class FlyingDutchman extends TerrariaMob {
    private static final double HEIGHT = 12.0;
    private int cannon;
    private int cannonTimer = 60;
    private int heading = 1;
    private int leavingTicks;

    public FlyingDutchman(EntityType<? extends FlyingDutchman> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);     // (a /summon reloads the flag from its NBT)
        Vec3 motion = getDeltaMovement().scale(0.9);
        if (!com.terracraft.world.event.EventManager.isActive(level.getServer(), com.terracraft.world.event.TerrariaEvents.PIRATE_INVASION)) {
            // the invasion is over: the ship sails off into the sky
            setDeltaMovement(motion.add(heading * 0.01, 0.03, 0));
            if (++leavingTicks > 200) {
                discard();
            }
            return;
        }
        if (getTarget() instanceof Player target && target.isAlive()) {
            // sail past the player, turn around a good way beyond them, and keep high above the ground
            double dx = target.getX() - getX();
            if (dx * heading < -14) {
                heading = -heading;
            }
            double wantY = target.getY() + HEIGHT;
            motion = motion.add(heading * 0.012, Mth.clamp((wantY - getY()) * 0.01, -0.02, 0.02), Mth.clamp((target.getZ() - getZ()) * 0.004, -0.01, 0.01));
            if (--cannonTimer <= 0) {
                fire(level, target);
                cannonTimer = 18 + random.nextInt(10);
            }
        } else {
            motion = motion.add(heading * 0.006, 0, 0);
        }
        if (horizontalCollision) {
            heading = -heading;
            motion = motion.add(0, 0.05, 0);
        }
        setDeltaMovement(motion);
        float yaw = heading > 0 ? -90.0F : 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), yaw, 4.0F));
        yBodyRot = getYRot();
        if (tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.5, getZ(), 1, 2.0, 0.4, 1.0, 0.01);
        }
    }

    /** One of the four cannons fires an arcing cannonball at the player. */
    private void fire(ServerLevel level, Player target) {
        cannon = (cannon + 1) % 4;
        double along = (cannon < 2 ? -1.2 : 1.2);
        double side = (cannon % 2 == 0 ? -1.0 : 1.0);
        float yawRad = getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
        Vec3 right = new Vec3(forward.z, 0, -forward.x);
        Vec3 from = position().add(forward.scale(along)).add(right.scale(side * 1.4)).add(0, 0.6, 0);
        Vec3 aim = target.position().add(0, 1.0, 0).subtract(from);
        Vec3 dir = new Vec3(aim.x, aim.y + aim.horizontalDistance() * 0.15, aim.z).normalize();
        TerrariaProjectile.shoot(level, this, ProjectileKinds.ENEMY_CANNONBALL, from, dir, 5.0F, 3.0F,
            70.0F * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 3.0F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 6, 0.2, 0.2, 0.2, 0.02);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.6F);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }
}

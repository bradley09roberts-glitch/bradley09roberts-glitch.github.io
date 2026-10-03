package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Skeletron, the Dungeon's guardian, summoned by cursing the Old Man at night.
 * <ul>
 *     <li>Head: hovers above the player, then spins and chases them for a few seconds (more defense while
 *     spinning). Once both hands are gone it spins more often and throws homing skulls.</li>
 *     <li>Hands: two separate enemies that float beside the head and take turns swiping at the player.</li>
 *     <li>At daybreak it becomes enraged: extremely fast, near-invulnerable and lethal (Terraria's "Dungeon
 *     Guardian" behaviour), so the fight must be won at night.</li>
 * </ul>
 */
public class Skeletron extends TerrariaBoss {
    public static final int PHASE_HOVER = 0;
    public static final int PHASE_SPIN = 1;
    public static final int PHASE_ENRAGED = 2;

    private boolean handsSpawned;
    private int livingHandsCached = 2;

    public Skeletron(EntityType<? extends Skeletron> type, Level level) {
        super(type, level, BossEvent.BossBarColor.WHITE);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.SKELETRON;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public int terrariaDefense() {
        return switch (phase()) {
            case PHASE_SPIN -> super.terrariaDefense() * 2;
            case PHASE_ENRAGED -> 9999;
            default -> super.terrariaDefense();
        };
    }

    @Override
    public float contactDamage() {
        return phase() == PHASE_ENRAGED ? 9999.0F : super.contactDamage() * (phase() == PHASE_SPIN ? 1.4F : 1.0F);
    }

    @Override
    public float spriteSpin(float partialTicks) {
        return phase() != PHASE_HOVER ? (tickCount + partialTicks) * 30.0F : 0.0F;
    }

    public int livingHands() {
        return level().getEntitiesOfClass(Hand.class, getBoundingBox().inflate(64), h -> h.isAlive() && getUUID().equals(h.headId)).size();
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!handsSpawned) {
            handsSpawned = true;
            for (int side = -1; side <= 1; side += 2) {
                Hand hand = MobContent.SKELETRON_HAND.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (hand != null) {
                    hand.attach(this, side);
                    hand.snapTo(getX() + side * 3, getY() - 1, getZ(), 0.0F, 0.0F);
                    hand.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                    level.addFreshEntity(hand);
                }
            }
        }
        Player target = findTarget(level);
        if (target == null || isLeaving(level)) {
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, 0.05, 0));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        setTarget(target);
        boolean enraged = level.isBrightOutside();
        if (enraged && phase() != PHASE_ENRAGED) {
            setPhase(PHASE_ENRAGED);
            playSound(SoundEvents.WITHER_SPAWN, 3.0F, 1.2F);
        }
        if (tickCount % 10 == 0) {
            livingHandsCached = livingHands();
        }
        Vec3 motion = getDeltaMovement();
        if (phase() == PHASE_ENRAGED) {
            // Terraria: daytime Skeletron homes in at enormous speed
            Vec3 toward = target.getEyePosition().subtract(position()).normalize();
            motion = motion.scale(0.9).add(toward.scale(0.25));
            if (motion.length() > 1.6) {
                motion = motion.normalize().scale(1.6);
            }
        } else if (phase() == PHASE_SPIN) {
            Vec3 toward = target.getEyePosition().subtract(0, 1.0, 0).subtract(position()).normalize();
            double speed = livingHandsCached == 0 ? 0.42 : 0.3;
            motion = motion.scale(0.92).add(toward.scale(speed * 0.12));
            if (motion.length() > speed) {
                motion = motion.normalize().scale(speed);
            }
            if (aiTimer > 160) {
                setPhase(PHASE_HOVER);
                setAiState(0);
            }
        } else {
            // hover about 6 blocks above the player
            Vec3 goal = target.position().add(0, 6.5, 0);
            Vec3 toward = goal.subtract(position());
            motion = motion.scale(0.9).add(toward.normalize().scale(Math.min(0.06, toward.length() * 0.01)));
            int spinEvery = livingHandsCached == 0 ? 200 : 420;
            if (aiTimer > spinEvery) {
                setPhase(PHASE_SPIN);
                setAiState(0);
                playSound(SoundEvents.SKELETON_HURT, 3.0F, 0.5F);
            }
            if (livingHandsCached == 0 && aiTimer % 40 == 20) {
                throwSkull(level, target);
            }
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 look = target.getEyePosition().subtract(position());
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void throwSkull(ServerLevel level, Player target) {
        Vec3 from = position().add(0, getBbHeight() * 0.4, 0);
        Vec3 aim = target.getEyePosition().subtract(from).normalize();
        float damage = 17.0F * TerrariaDifficulty.enemyDamageMultiplier(level);
        TerrariaProjectile.shoot(level, this, ProjectileKinds.SKELETRON_SKULL, from, aim, 6.0F, 0.0F, damage, DamageClass.GENERIC, 0, 3.0F);
        playSound(SoundEvents.SKELETON_SHOOT, 2.0F, 0.6F);
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            for (Hand hand : level.getEntitiesOfClass(Hand.class, getBoundingBox().inflate(64), h -> getUUID().equals(h.headId))) {
                hand.discard();
            }
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 40, 1.5, 1.5, 1.5, 0.05);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel level) {
            // despawned (everyone died or left): the hands go too and the Old Man comes back later
            for (Hand hand : level.getEntitiesOfClass(Hand.class, getBoundingBox().inflate(64), h -> getUUID().equals(h.headId))) {
                hand.discard();
            }
        }
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("HandsSpawned", handsSpawned);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        handsSpawned = input.getBooleanOr("HandsSpawned", false);
    }

    /** Skeletron Hand: floats beside the head and swipes at the player in turn with the other hand. */
    public static class Hand extends TerrariaMob {
        private UUID headId;
        private int side = 1;
        private int swipeTimer = 120;

        public Hand(EntityType<? extends Hand> type, Level level) {
            super(type, level);
            setNoGravity(true);
            noPhysics = true;
        }

        void attach(Skeletron head, int side) {
            this.headId = head.getUUID();
            this.side = side;
            this.swipeTimer = side < 0 ? 90 : 180;
        }

        public int side() {
            return side;
        }

        @Override
        protected void registerGoals() {
        }

        @Override
        public boolean countsTowardSpawnCap() {
            return false;
        }

        @Override
        public void travel(Vec3 input) {
        }

        @Override
        public boolean removeWhenFarAway(double distanceSq) {
            return false;
        }

        @Override
        public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
            return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || super.isInvulnerableTo(level, source);
        }

        @Override
        public int terrariaDefense() {
            return level().isBrightOutside() ? 9999 : super.terrariaDefense();
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            super.customServerAiStep(level);
            if (headId == null || !(level.getEntity(headId) instanceof Skeletron head) || !head.isAlive()) {
                discard();
                return;
            }
            Player target = head.getTarget() instanceof Player p ? p : null;
            Vec3 facing = target != null ? target.position().subtract(head.position()).multiply(1, 0, 1) : Vec3.directionFromRotation(0, head.getYRot());
            if (facing.lengthSqr() < 1.0E-4) {
                facing = new Vec3(0, 0, 1);
            }
            facing = facing.normalize();
            Vec3 sideways = new Vec3(-facing.z, 0, facing.x).scale(side * 3.2);
            Vec3 rest = head.position().add(sideways).add(0, -1.2 + Mth.sin((tickCount + side * 20) * 0.08F) * 0.5, 0);
            Vec3 goal = rest;
            swipeTimer--;
            if (target != null && head.phase() == PHASE_HOVER && swipeTimer < 30 && swipeTimer > 0) {
                // swipe: reach for where the player is
                goal = target.position().add(0, target.getBbHeight() * 0.4, 0);
            } else if (swipeTimer <= 0) {
                swipeTimer = 140 + random.nextInt(60);
            }
            double pull = swipeTimer < 30 && swipeTimer > 0 ? 0.22 : 0.12;
            Vec3 motion = goal.subtract(position()).scale(pull);
            if (motion.length() > 1.0) {
                motion = motion.normalize();
            }
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            float yaw = (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }

        @Override
        protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            if (headId != null) {
                output.store("Head", UUIDUtil.CODEC, headId);
            }
            output.putInt("Side", side);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            headId = input.read("Head", UUIDUtil.CODEC).orElse(null);
            side = input.getIntOr("Side", 1);
        }
    }
}

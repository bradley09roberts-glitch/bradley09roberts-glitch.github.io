package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.BossContent;
import com.terracraft.registry.content.CoreItems;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Skeletron Prime, summoned with a Mechanical Skull at night: a metal skull with four arms, each its own enemy.
 * <ul>
 *     <li>Prime Cannon lobs bombs, Prime Laser fires lasers, Prime Saw and Prime Vice lunge at the player.</li>
 *     <li>The head hovers above the player and every so often spins after them (more defense, more damage);
 *     with its arms gone it spins more often.</li>
 *     <li>At daybreak it becomes enraged like Skeletron, so the fight must be won at night.</li>
 * </ul>
 */
public class SkeletronPrime extends TerrariaBoss {
    public static final int PHASE_HOVER = 0;
    public static final int PHASE_SPIN = 1;
    public static final int PHASE_ENRAGED = 2;

    private boolean armsSpawned;
    private int livingArms = 4;

    public SkeletronPrime(EntityType<? extends SkeletronPrime> type, Level level) {
        super(type, level, BossEvent.BossBarColor.WHITE);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.SKELETRON_PRIME;
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
        return phase() == PHASE_ENRAGED ? 9999.0F : super.contactDamage() * (phase() == PHASE_SPIN ? 2.0F : 1.0F);
    }

    @Override
    public float spriteSpin(float partialTicks) {
        return phase() != PHASE_HOVER ? (tickCount + partialTicks) * 30.0F : 0.0F;
    }

    private List<Arm> arms(ServerLevel level) {
        return level.getEntitiesOfClass(Arm.class, getBoundingBox().inflate(64), a -> a.isAlive() && getUUID().equals(a.headId));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!armsSpawned) {
            armsSpawned = true;
            spawnArm(level, MobContent.PRIME_CANNON, Arm.Kind.CANNON);
            spawnArm(level, MobContent.PRIME_SAW, Arm.Kind.SAW);
            spawnArm(level, MobContent.PRIME_VICE, Arm.Kind.VICE);
            spawnArm(level, MobContent.PRIME_LASER, Arm.Kind.LASER);
        }
        Player target = findTarget(level);
        if (target == null || isLeaving(level)) {
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, 0.05, 0));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        setTarget(target);
        if (level.isBrightOutside() && phase() != PHASE_ENRAGED) {
            setPhase(PHASE_ENRAGED);
            playSound(SoundEvents.WITHER_SPAWN, 3.0F, 1.0F);
        }
        if (tickCount % 10 == 0) {
            livingArms = arms(level).size();
        }
        Vec3 motion = getDeltaMovement();
        if (phase() == PHASE_ENRAGED) {
            Vec3 toward = target.getEyePosition().subtract(position()).normalize();
            motion = motion.scale(0.9).add(toward.scale(0.25));
            if (motion.length() > 1.6) {
                motion = motion.normalize().scale(1.6);
            }
        } else if (phase() == PHASE_SPIN) {
            Vec3 toward = target.getEyePosition().subtract(0, 1.0, 0).subtract(position()).normalize();
            double speed = livingArms == 0 ? 0.5 : 0.36;
            motion = motion.scale(0.92).add(toward.scale(speed * 0.12));
            if (motion.length() > speed) {
                motion = motion.normalize().scale(speed);
            }
            if (aiTimer > 200) {
                setPhase(PHASE_HOVER);
                setAiState(0);
            }
        } else {
            Vec3 goal = target.position().add(0, 7.0, 0);
            Vec3 toward = goal.subtract(position());
            motion = motion.scale(0.9).add(toward.normalize().scale(Math.min(0.07, toward.length() * 0.012)));
            if (aiTimer > (livingArms == 0 ? 160 : 500)) {
                setPhase(PHASE_SPIN);
                setAiState(0);
                playSound(SoundEvents.IRON_GOLEM_HURT, 3.0F, 0.5F);
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

    private void spawnArm(ServerLevel level, Supplier<? extends EntityType<Arm>> type, Arm.Kind kind) {
        Arm arm = type.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (arm != null) {
            arm.attach(this, kind);
            arm.snapTo(getX() + kind.side * 3, getY() + kind.height, getZ(), 0.0F, 0.0F);
            arm.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            level.addFreshEntity(arm);
        }
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            arms(level).forEach(Arm::discard);
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 6, 1.5, 1.5, 1.5, 0.05);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel level) {
            arms(level).forEach(Arm::discard);
        }
    }

    @Override
    protected Item healingPotion() {
        return CoreItems.GREATER_HEALING_POTION.get();
    }

    @Override
    protected void dropFightLoot(ServerLevel level) {
        dropStack(level, BossContent.SOUL_OF_FRIGHT.get(), 25 + random.nextInt(16));
        dropStack(level, BossContent.HALLOWED_BAR.get(), 15 + random.nextInt(16));
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("ArmsSpawned", armsSpawned);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        armsSpawned = input.getBooleanOr("ArmsSpawned", false);
    }

    /** One of Prime's four arms; each kind attacks differently. */
    public static class Arm extends TerrariaMob {
        public enum Kind {
            CANNON(-1, 0.5), LASER(1, 0.5), SAW(1, -2.0), VICE(-1, -2.0);

            final int side;
            final double height;

            Kind(int side, double height) {
                this.side = side;
                this.height = height;
            }
        }

        private UUID headId;
        private Kind kind;
        private int attackTimer;

        public Arm(EntityType<? extends Arm> type, Level level, Kind kind) {
            super(type, level);
            this.kind = kind;
            this.attackTimer = 60 + kind.ordinal() * 25;
            setNoGravity(true);
            noPhysics = true;
        }

        void attach(SkeletronPrime head, Kind kind) {
            this.headId = head.getUUID();
            this.kind = kind;
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
            if (headId == null || !(level.getEntity(headId) instanceof SkeletronPrime head) || !head.isAlive()) {
                discard();
                return;
            }
            Player target = head.getTarget() instanceof Player p ? p : null;
            Vec3 facing = target != null ? target.position().subtract(head.position()).multiply(1, 0, 1) : Vec3.directionFromRotation(0, head.getYRot());
            if (facing.lengthSqr() < 1.0E-4) {
                facing = new Vec3(0, 0, 1);
            }
            facing = facing.normalize();
            Vec3 sideways = new Vec3(-facing.z, 0, facing.x).scale(kind.side * 3.5);
            Vec3 rest = head.position().add(sideways).add(0, kind.height + Mth.sin((tickCount + kind.ordinal() * 15) * 0.08F) * 0.4, 0);
            Vec3 goal = rest;
            double pull = 0.12;
            attackTimer--;
            boolean melee = kind == Kind.SAW || kind == Kind.VICE;
            if (target != null) {
                if (melee && attackTimer < 40 && attackTimer > 0) {
                    goal = target.position().add(0, target.getBbHeight() * 0.4, 0);   // lunge
                    pull = kind == Kind.SAW ? 0.3 : 0.2;
                } else if (!melee && attackTimer == 0) {
                    fire(level, target);
                }
            }
            if (attackTimer <= 0) {
                attackTimer = switch (kind) {
                    case SAW -> 90;
                    case VICE -> 150;
                    case CANNON -> 70;
                    case LASER -> 45;
                } + random.nextInt(30);
            }
            Vec3 motion = goal.subtract(position()).scale(pull);
            if (motion.length() > 1.1) {
                motion = motion.normalize().scale(1.1);
            }
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            Vec3 look = target != null ? target.getEyePosition().subtract(position()) : facing;
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
        }

        private void fire(ServerLevel level, Player target) {
            Vec3 from = position().add(0, getBbHeight() / 2, 0);
            float multiplier = TerrariaDifficulty.enemyDamageMultiplier(level);
            if (kind == Kind.CANNON) {
                // lob a bomb in an arc
                Vec3 to = target.position().subtract(from);
                Vec3 aim = new Vec3(to.x, to.y + to.horizontalDistance() * 0.5, to.z).normalize();
                TerrariaProjectile.shoot(level, this, ProjectileKinds.PRIME_BOMB, from, aim, 5.0F, 0.5F, 40.0F * multiplier, DamageClass.GENERIC, 0, 4.0F);
                playSound(SoundEvents.TNT_PRIMED, 1.5F, 1.0F);
            } else {
                Vec3 aim = target.getEyePosition().subtract(from).normalize();
                TerrariaProjectile.shoot(level, this, ProjectileKinds.MECH_LASER, from, aim, 10.0F, 0.3F, 25.0F * multiplier, DamageClass.GENERIC, 0, 1.0F);
                playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 2.0F);
            }
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
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            headId = input.read("Head", UUIDUtil.CODEC).orElse(null);
        }
    }
}

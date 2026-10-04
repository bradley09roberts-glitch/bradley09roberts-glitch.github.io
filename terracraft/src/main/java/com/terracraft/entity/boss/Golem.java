package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.GolemContent;
import com.terracraft.registry.content.MobContent;
import com.terracraft.world.temple.TempleWorld;
import net.minecraft.core.BlockPos;
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

/**
 * Golem, the Lihzahrd idol, awoken by a Power Cell placed in the Lihzahrd Altar.
 * <ul>
 *     <li>The stone body hops after the player; killing it ends the fight.</li>
 *     <li>Its head shoots fireballs, and eye lasers once it is hurt; beaten down, the head breaks free and flies
 *     after the player, untouchable, firing faster until the body falls.</li>
 *     <li>Two fists on chains punch out at the player in turn.</li>
 *     <li>Out of the temple it gets angry: faster jumps and harder hits.</li>
 * </ul>
 */
public class Golem extends TerrariaBoss {
    private boolean partsSpawned;
    private int jumpDelay = 40;
    private boolean enraged;

    public Golem(EntityType<? extends Golem> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.GOLEM;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public float contactDamage() {
        return super.contactDamage() * (enraged ? 1.5F : 1.0F);
    }

    boolean enraged() {
        return enraged;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!partsSpawned) {
            spawnParts(level);
        }
        Player target = findTarget(level);
        if (target == null) {
            return;
        }
        setTarget(target);
        enraged = !TempleWorld.layout(level).insideBricks(target.getBlockX(), target.getBlockY(), target.getBlockZ());
        Vec3 look = target.position().subtract(position());
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), yaw, 6.0F));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (!onGround()) {
            return;
        }
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x * 0.5, motion.y, motion.z * 0.5);
        if (--jumpDelay > 0) {
            return;
        }
        // a heavy hop towards the player, higher if they are above
        double dist = look.horizontalDistance();
        double forward = Math.min(0.75, dist * 0.06 + 0.15) * (enraged ? 1.4 : 1.0);
        double up = 0.75 + Mth.clamp(look.y * 0.06, 0.0, 0.45);
        setDeltaMovement(look.x / Math.max(dist, 0.01) * forward, up, look.z / Math.max(dist, 0.01) * forward);
        playSound(SoundEvents.IRON_GOLEM_STEP, 2.0F, 0.5F);
        jumpDelay = (enraged ? 20 : 35) + random.nextInt(25);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        if (fallDistance > 2 && level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 12, 1.5, 0.1, 1.5, 0.02);
            playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
        }
        return false;
    }

    private void spawnParts(ServerLevel level) {
        partsSpawned = true;
        Head head = MobContent.GOLEM_HEAD.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (head != null) {
            head.snapTo(getX(), getY() + getBbHeight(), getZ(), getYRot(), 0.0F);
            head.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            head.attach(this);
            head.setTether(null);
            level.addFreshEntity(head);
        }
        for (int side = -1; side <= 1; side += 2) {
            Fist fist = MobContent.GOLEM_FIST.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (fist != null) {
                fist.side = side;
                fist.punchTimer = 60 + (side > 0 ? 45 : 0);
                fist.snapTo(getX() + side * 2.5, getY() + 1.5, getZ(), getYRot(), 0.0F);
                fist.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                fist.attach(this);
                level.addFreshEntity(fist);
            }
        }
    }

    void shoot(ServerLevel level, ProjectileKind kind, Vec3 from, Vec3 dir, float speed, float terrariaDamage) {
        float damage = terrariaDamage * TerrariaDifficulty.enemyDamageMultiplier(level) * (enraged ? 1.5F : 1.0F);
        TerrariaProjectile.shoot(level, this, kind, from, dir, speed, 0.3F, damage, DamageClass.GENERIC, 0, 1.0F);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        super.checkFallDamage(ya, onGround, onState, pos);
    }

    @Override
    protected Item healingPotion() {
        return CoreItems.GREATER_HEALING_POTION.get();
    }

    @Override
    protected void dropFightLoot(ServerLevel level) {
        List<Item> loot = List.of(GolemContent.HEAT_RAY.get(), GolemContent.POSSESSED_HATCHET.get(), GolemContent.SUN_STONE.get(),
            GolemContent.EYE_OF_THE_GOLEM.get());
        dropStack(level, loot.get(random.nextInt(loot.size())), 1);
        if (random.nextInt(3) == 0) {
            dropStack(level, GolemContent.PICKSAW.get(), 1);
        }
        dropStack(level, GolemContent.BEETLE_HUSK.get(), 4 + random.nextInt(5));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Parts", partsSpawned);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        partsSpawned = input.getBooleanOr("Parts", false);
    }

    // ================================================================ parts

    /**
     * Golem's Head: sits on the body and shoots; once its life runs out it breaks free instead of dying (the
     * {@code free} texture, glowing eyes), flies over the player and cannot be hurt any more.
     */
    public static class Head extends BossPart<Golem> {
        private boolean free;
        private int fireTimer = 50;
        private int laserTimer = 80;

        public Head(EntityType<? extends Head> type, Level level) {
            super(type, level, Golem.class);
        }

        @Override
        public String spriteVariant() {
            return phase() ? "free" : "";
        }

        private boolean phase() {
            return free || getHealth() <= 1.0F;
        }

        @Override
        protected boolean tetheredToOwner() {
            return false;
        }

        @Override
        public boolean showsHealthBar() {
            return !phase();
        }

        @Override
        public void setHealth(float health) {
            // the head never dies: at zero life it breaks off the body
            super.setHealth(level().isClientSide() ? health : Math.max(1.0F, health));
        }

        /** /kill still removes it. */
        @Override
        public void kill(ServerLevel level) {
            discard();
        }

        @Override
        public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
            return free && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) || super.isInvulnerableTo(level, source);
        }

        @Override
        protected void tickPart(ServerLevel level, Golem golem) {
            if (!free && getHealth() <= 1.0F) {
                free = true;
                level.sendParticles(ParticleTypes.LAVA, getX(), getY() + 1.0, getZ(), 20, 0.8, 0.5, 0.8, 0.1);
                playSound(SoundEvents.IRON_GOLEM_DAMAGE, 3.0F, 0.5F);
            }
            Player target = golem.getTarget() instanceof Player p ? p : null;
            if (!free) {
                // ride on the body's shoulders
                setPos(golem.getX(), golem.getY() + golem.getBbHeight() - 0.3, golem.getZ());
                setDeltaMovement(Vec3.ZERO);
                setYRot(golem.getYRot());
                yBodyRot = golem.getYRot();
                yHeadRot = golem.getYRot();
            } else if (target != null) {
                Vec3 goal = target.position().add(Mth.sin(tickCount * 0.04F) * 7.0, 7.0, Mth.cos(tickCount * 0.04F) * 7.0);
                Vec3 motion = getDeltaMovement().scale(0.88).add(goal.subtract(position()).normalize().scale(0.06));
                if (motion.length() > 0.55) {
                    motion = motion.normalize().scale(0.55);
                }
                setDeltaMovement(motion);
                move(MoverType.SELF, motion);
                Vec3 look = target.position().subtract(position());
                float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                yBodyRot = yaw;
                yHeadRot = yaw;
            }
            if (target == null || !hasLineOfSight(target)) {
                return;
            }
            Vec3 from = position().add(0, getBbHeight() * 0.45, 0);
            if (--fireTimer <= 0) {
                golem.shoot(level, ProjectileKinds.GOLEM_FIREBALL, from, target.getEyePosition().subtract(from).normalize(), 7.0F, 60.0F);
                playSound(SoundEvents.BLAZE_SHOOT, 1.5F, 0.7F);
                fireTimer = (free ? 35 : 60) + random.nextInt(20);
            }
            boolean lasers = free || golem.getHealth() < golem.getMaxHealth() * 0.75F;
            if (lasers && --laserTimer <= 0) {
                float yawRad = getYRot() * Mth.DEG_TO_RAD;
                Vec3 sideways = new Vec3(Mth.cos(yawRad), 0, Mth.sin(yawRad)).scale(0.45);
                for (int eye = -1; eye <= 1; eye += 2) {
                    Vec3 eyePos = from.add(sideways.scale(eye)).add(0, 0.2, 0);
                    golem.shoot(level, ProjectileKinds.GOLEM_LASER, eyePos, target.getEyePosition().subtract(eyePos).normalize(), 12.0F, 50.0F);
                }
                playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.8F);
                laserTimer = (free ? 30 : 55) + random.nextInt(15);
            }
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            output.putBoolean("Free", free);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            free = input.getBooleanOr("Free", false);
        }
    }

    /** Golem's Fist: hangs beside the body on a chain and punches out at the player every few seconds. */
    public static class Fist extends BossPart<Golem> {
        int side = 1;
        int punchTimer = 60;
        private int punching;

        public Fist(EntityType<? extends Fist> type, Level level) {
            super(type, level, Golem.class);
        }

        @Override
        public String tetherStyle() {
            return "chain";
        }

        @Override
        protected void tickPart(ServerLevel level, Golem golem) {
            float yawRad = golem.getYRot() * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(Mth.cos(yawRad), 0, Mth.sin(yawRad));
            Vec3 forward = new Vec3(-Mth.sin(yawRad), 0, Mth.cos(yawRad));
            Vec3 rest = golem.position().add(right.scale(side * 2.6)).add(forward.scale(0.6)).add(0, 1.4, 0);
            Vec3 goal = rest;
            double speed = 0.35;
            Player target = golem.getTarget() instanceof Player p ? p : null;
            if (punching > 0) {
                punching--;
                if (target != null && punching > 12) {
                    goal = target.position().add(0, target.getBbHeight() * 0.4, 0);
                    speed = golem.enraged() ? 1.5 : 1.1;
                }
            } else if (target != null && --punchTimer <= 0 && target.distanceToSqr(golem) < 22 * 22) {
                punching = 30;
                punchTimer = (golem.enraged() ? 45 : 70) + random.nextInt(30);
                playSound(SoundEvents.PISTON_EXTEND, 1.5F, 0.6F);
            }
            Vec3 toGoal = goal.subtract(position());
            Vec3 motion = toGoal.length() < speed ? toGoal : toGoal.normalize().scale(speed);
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            Vec3 look = target != null && punching > 12 ? target.position().subtract(position()) : forward;
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            output.putInt("Side", side);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            side = input.getIntOr("Side", 1);
        }
    }
}

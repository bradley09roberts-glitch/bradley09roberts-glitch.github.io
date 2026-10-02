package com.terracraft.entity.boss;

import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.BossContent;
import com.terracraft.registry.content.EvilContent;
import com.terracraft.registry.content.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Brain of Cthulhu. First phase: shielded by a swarm of Creepers circling it (it cannot be hurt while any
 * remain), drifting toward the player and teleporting around them. Second phase, once all Creepers are dead:
 * it lunges at the player in quick dashes and teleports between them.
 */
public class BrainOfCthulhu extends TerrariaBoss {
    public static final int PHASE_SHIELDED = 0;
    public static final int PHASE_EXPOSED = 1;
    private static final int DRIFT = 0;
    private static final int DASH = 1;

    private boolean creepersSpawned;
    private int dashes;

    public BrainOfCthulhu(EntityType<? extends BrainOfCthulhu> type, Level level) {
        super(type, level, BossEvent.BossBarColor.RED);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.BRAIN_OF_CTHULHU;
    }

    @Override
    protected void registerGoals() {
    }

    public int livingCreepers() {
        return level().getEntitiesOfClass(BrainCreeper.class, getBoundingBox().inflate(48), c -> c.isAlive() && c.brainId() != null
            && c.brainId().equals(getUUID())).size();
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return phase() == PHASE_SHIELDED && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) || super.isInvulnerableTo(level, source);
    }

    @Override
    public String spriteVariant() {
        return phase() == PHASE_EXPOSED ? "exposed" : "";
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!creepersSpawned) {
            creepersSpawned = true;
            int count = TerrariaDifficulty.isExpert(level) ? 25 : 20;
            for (int i = 0; i < count; i++) {
                BrainCreeper creeper = MobContent.BRAIN_CREEPER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (creeper != null) {
                    creeper.attach(this, i * (360.0F / count));
                    creeper.snapTo(getX(), getY() + 1, getZ(), 0.0F, 0.0F);
                    creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                    level.addFreshEntity(creeper);
                }
            }
        }
        Player target = findTarget(level);
        if (target == null || isLeaving(level)) {
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, 0.03, 0));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
            return;
        }
        setTarget(target);
        if (phase() == PHASE_SHIELDED && tickCount % 20 == 0 && livingCreepers() == 0) {
            setPhase(PHASE_EXPOSED);
            setAiState(DASH);
            playSound(SoundEvents.SLIME_DEATH, 2.0F, 0.5F);
        }
        if (phase() == PHASE_SHIELDED) {
            Vec3 goal = target.getEyePosition().add(0, 2, 0);
            Vec3 toward = goal.subtract(position());
            Vec3 desired = toward.length() > 6 ? toward.normalize().scale(0.18) : Vec3.ZERO;
            setDeltaMovement(getDeltaMovement().lerp(desired, 0.1));
            if (aiTimer > 160) {
                teleportAround(level, target);
                setAiState(DRIFT);
            }
        } else if (aiState == DASH) {
            if (aiTimer == 1) {
                Vec3 dir = target.getEyePosition().subtract(position()).normalize();
                setDeltaMovement(dir.scale(TerrariaDifficulty.isExpert(level) ? 0.95 : 0.8));
                playSound(SoundEvents.PHANTOM_SWOOP, 1.5F, 0.6F);
            }
            setDeltaMovement(getDeltaMovement().scale(0.97));
            if (aiTimer > 25) {
                if (++dashes % 3 == 0) {
                    teleportAround(level, target);
                }
                setAiState(DASH);
            }
        }
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        Vec3 look = target.getEyePosition().subtract(position());
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
    }

    @Override
    public void travel(Vec3 input) {
    }

    private void teleportAround(ServerLevel level, Player target) {
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 1, getZ(), 15, 1, 1, 1, 0.1);
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 8 + random.nextDouble() * 6;
        teleportTo(target.getX() + Math.cos(angle) * distance, target.getY() + 1 + random.nextDouble() * 3, target.getZ() + Math.sin(angle) * distance);
        setDeltaMovement(Vec3.ZERO);
        playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5F, 0.5F);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 1, getZ(), 15, 1, 1, 1, 0.1);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.EVIL_BOSS);
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), new ItemStack(EvilContent.CRIMTANE_ORE.get(), 40 + random.nextInt(51))));
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), new ItemStack(BossContent.TISSUE_SAMPLE.get(), 10 + random.nextInt(11))));
            for (BrainCreeper creeper : level.getEntitiesOfClass(BrainCreeper.class, getBoundingBox().inflate(64))) {
                creeper.discard();
            }
        }
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    /** Creeper: a veiny eye that circles its Brain and periodically lunges at the player. */
    public static class BrainCreeper extends TerrariaMob {
        private java.util.UUID brainId;
        private float angle;
        private int lungeTimer = 80;

        public BrainCreeper(EntityType<? extends BrainCreeper> type, Level level) {
            super(type, level);
            setNoGravity(true);
            noPhysics = true;
        }

        void attach(BrainOfCthulhu brain, float startAngle) {
            this.brainId = brain.getUUID();
            this.angle = startAngle;
            this.lungeTimer = 60 + random.nextInt(120);
        }

        java.util.UUID brainId() {
            return brainId;
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
        protected void customServerAiStep(ServerLevel level) {
            super.customServerAiStep(level);
            if (brainId == null || !(level.getEntity(brainId) instanceof BrainOfCthulhu brain) || !brain.isAlive()) {
                discard();
                return;
            }
            Player target = brain.getTarget() instanceof Player p ? p : null;
            angle += 3.0F;
            Vec3 orbit = brain.position().add(Mth.cos(angle * Mth.DEG_TO_RAD) * 4.5, 1.0 + Mth.sin(angle * 0.05F) * 1.5, Mth.sin(angle * Mth.DEG_TO_RAD) * 4.5);
            Vec3 motion;
            if (--lungeTimer <= 0 && target != null) {
                if (lungeTimer > -20) {
                    motion = target.getEyePosition().subtract(position()).normalize().scale(0.6);
                } else {
                    lungeTimer = 100 + random.nextInt(120);
                    motion = Vec3.ZERO;
                }
            } else {
                motion = orbit.subtract(position()).scale(0.25);
            }
            setDeltaMovement(motion);
            move(net.minecraft.world.entity.MoverType.SELF, motion);
            if (target != null) {
                Vec3 look = target.getEyePosition().subtract(position());
                setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F);
                yBodyRot = getYRot();
                setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
            }
        }

        @Override
        public boolean removeWhenFarAway(double distanceSq) {
            return false;
        }

        @Override
        protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        }

        @Override
        protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
            super.addAdditionalSaveData(output);
            if (brainId != null) {
                output.store("Brain", net.minecraft.core.UUIDUtil.CODEC, brainId);
            }
        }

        @Override
        protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
            super.readAdditionalSaveData(input);
            brainId = input.read("Brain", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        }
    }

}

package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.MobContent;
import com.terracraft.world.jungle.JungleFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Queen Bee. Cycles through three attacks like in Terraria:
 * <ol>
 *     <li>three fast dashes straight through the player,</li>
 *     <li>hovering while bees hatch from her abdomen,</li>
 *     <li>hovering above the player while firing poisonous stingers.</li>
 * </ol>
 * Below half life she attacks faster. Outside the Jungle she is enraged (double damage), and like Terraria's
 * bosses she gives up if every player leaves or dies.
 */
public class QueenBee extends TerrariaBoss {
    public static final int PHASE_NORMAL = 0;
    public static final int PHASE_DASHING = 1;
    private static final int DASH_WINDUP = 0;
    private static final int DASH = 1;
    private static final int SUMMON = 2;
    private static final int STING = 3;

    private int dashes;
    private Vec3 dashDirection = Vec3.ZERO;
    private boolean enraged;

    public QueenBee(EntityType<? extends QueenBee> type, Level level) {
        super(type, level, BossEvent.BossBarColor.YELLOW);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.QUEEN_BEE;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public float contactDamage() {
        return super.contactDamage() * (enraged ? 2.0F : 1.0F);
    }

    private boolean angry() {
        return getHealth() < getMaxHealth() * 0.5F;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player target = findTarget(level);
        if (target == null || isLeaving(level)) {
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, 0.05, 0));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        setTarget(target);
        if (tickCount % 40 == 0) {
            enraged = !JungleFeature.isJungle(level, target.getBlockX(), target.getBlockZ());
        }
        Vec3 motion = getDeltaMovement();
        Vec3 look = target.getEyePosition().subtract(position());
        switch (aiState) {
            case DASH_WINDUP -> {
                // line up beside the player at their height
                Vec3 side = look.multiply(1, 0, 1).normalize();
                if (side.lengthSqr() < 1.0E-4) {
                    side = new Vec3(1, 0, 0);
                }
                Vec3 goal = target.position().add(0, 1.0, 0).subtract(side.scale(9));
                motion = motion.scale(0.8).add(goal.subtract(position()).scale(0.05));
                setPhase(PHASE_NORMAL);
                if (aiTimer > (angry() ? 15 : 25)) {
                    dashDirection = target.position().add(0, 1.0, 0).subtract(position()).normalize();
                    setPhase(PHASE_DASHING);
                    setAiState(DASH);
                    playSound(SoundEvents.BEE_LOOP_AGGRESSIVE, 3.0F, 0.6F);
                }
            }
            case DASH -> {
                double speed = (angry() ? 1.05 : 0.85) * (enraged ? 1.3 : 1.0);
                motion = dashDirection.scale(speed);
                look = dashDirection;
                if (aiTimer > 28) {
                    dashes++;
                    setAiState(dashes % 3 == 0 ? (random.nextBoolean() ? SUMMON : STING) : DASH_WINDUP);
                }
            }
            case SUMMON -> {
                setPhase(PHASE_NORMAL);
                Vec3 goal = target.position().add(0, 6, 0);
                motion = motion.scale(0.85).add(goal.subtract(position()).scale(0.02));
                if (aiTimer % (angry() ? 12 : 18) == 0) {
                    spawnBee(level);
                }
                if (aiTimer > 80) {
                    setAiState(STING);
                }
            }
            case STING -> {
                setPhase(PHASE_NORMAL);
                Vec3 goal = target.position().add(Mth.sin(aiTimer * 0.05F) * 6, 7, Mth.cos(aiTimer * 0.05F) * 6);
                motion = motion.scale(0.85).add(goal.subtract(position()).scale(0.02));
                if (aiTimer % (angry() ? 10 : 16) == 0) {
                    Vec3 from = position().add(0, getBbHeight() * 0.2, 0);
                    Vec3 aim = target.getEyePosition().subtract(from).normalize();
                    float damage = 11.0F * TerrariaDifficulty.enemyDamageMultiplier(level) * (enraged ? 2.0F : 1.0F);
                    TerrariaProjectile.shoot(level, this, ProjectileKinds.STINGER, from, aim, 9.0F, 2.0F, damage, DamageClass.GENERIC, 0, 1.0F);
                    playSound(SoundEvents.BEE_STING, 1.5F, 0.8F);
                }
                if (aiTimer > 100) {
                    setAiState(DASH_WINDUP);
                }
            }
            default -> setAiState(DASH_WINDUP);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private void spawnBee(ServerLevel level) {
        long bees = level.getEntitiesOfClass(com.terracraft.entity.mob.FlyerMob.class, getBoundingBox().inflate(40),
            e -> e.getType() == MobContent.BEE.get()).size();
        if (bees >= 12) {
            return;
        }
        var bee = MobContent.BEE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (bee != null) {
            bee.snapTo(getX(), getY() + 0.3, getZ(), random.nextFloat() * 360.0F, 0.0F);
            bee.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            bee.setTarget(getTarget());
            level.addFreshEntity(bee);
            playSound(SoundEvents.BEEHIVE_EXIT, 1.5F, 1.0F);
        }
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }
}

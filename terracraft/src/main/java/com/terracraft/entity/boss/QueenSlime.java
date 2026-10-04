package com.terracraft.entity.boss;

import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.combat.DamageClass;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.ArmorContent;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.MobContent;
import com.terracraft.combat.TerrariaDifficulty;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Queen Slime (Hardmode, summoned with a Gelatin Crystal in the Hallow).
 * <ul>
 *     <li>First half of her life: hops at the player; every fourth hop is a high leap that ends in a slam,
 *     scattering Regal Gel. Crystal, Bouncy and Heavenly Slimes keep joining the fight.</li>
 *     <li>Second half: she grows wings and flies above the player, firing gel volleys and diving down in
 *     slams that splash gel in a ring.</li>
 * </ul>
 */
public class QueenSlime extends TerrariaBoss {
    private static final int HOP = 0;
    private static final int LEAP = 1;
    private static final int FLY = 2;
    private static final int DIVE = 3;

    private int hops;
    private int jumpDelay = 30;
    private int minionTimer = 100;

    public QueenSlime(EntityType<? extends QueenSlime> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PINK);
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.QUEEN_SLIME;
    }

    @Override
    protected void registerGoals() {
    }

    /** The second phase shows her wings (alternate texture/model state). */
    @Override
    public String spriteVariant() {
        return phase() >= 1 ? "winged" : "";
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player target = findTarget(level);
        if (target != null) {
            setTarget(target);
        }
        if (phase() == 0 && getHealth() <= getMaxHealth() * 0.5F) {
            setPhase(1);
            setNoGravity(true);
            setAiState(FLY);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 60, 1.5, 1.2, 1.5, 0.15);
            playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.7F);
        }
        if (target == null) {
            return;
        }
        summonMinions(level, target);
        switch (aiState) {
            case LEAP -> leap(level);
            case FLY -> fly(level, target);
            case DIVE -> dive(level);
            default -> hop(target);
        }
    }

    // ---------------------------------------------------------------- phase 1

    private void hop(Player target) {
        if (!onGround()) {
            return;
        }
        Vec3 motion = getDeltaMovement();
        setDeltaMovement(motion.x * 0.3, motion.y, motion.z * 0.3);
        if (--jumpDelay > 0) {
            return;
        }
        hops++;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        if (hops % 4 == 0) {
            // a high leap that comes down on the player
            double forward = Math.min(0.9, horizontal * 0.055);
            setDeltaMovement(-Mth.sin(yaw * Mth.DEG_TO_RAD) * forward, 1.35, Mth.cos(yaw * Mth.DEG_TO_RAD) * forward);
            setAiState(LEAP);
            playSound(SoundEvents.SLIME_JUMP, 2.0F, 0.5F);
        } else {
            double forward = Math.min(0.55, horizontal * 0.06 + 0.12);
            setDeltaMovement(-Mth.sin(yaw * Mth.DEG_TO_RAD) * forward, 0.8, Mth.cos(yaw * Mth.DEG_TO_RAD) * forward);
            playSound(SoundEvents.SLIME_JUMP, 1.5F, 0.7F);
        }
        needsSync = true;
        jumpDelay = 20 + random.nextInt(10);
    }

    private void leap(ServerLevel level) {
        if (aiTimer > 8 && getDeltaMovement().y < 0) {
            // falling: speed up into a slam
            setDeltaMovement(getDeltaMovement().multiply(0.9, 1.0, 0.9).add(0, -0.12, 0));
        }
        if (aiTimer > 8 && onGround()) {
            slamSplash(level, 10);
            setAiState(HOP);
            jumpDelay = 25;
        }
    }

    // ---------------------------------------------------------------- phase 2

    private void fly(ServerLevel level, Player target) {
        Vec3 goal = target.position().add(Mth.sin(aiTimer * 0.03F) * 6.0, 7.0, Mth.cos(aiTimer * 0.03F) * 6.0);
        Vec3 toGoal = goal.subtract(position());
        Vec3 motion = getDeltaMovement().scale(0.88).add(toGoal.normalize().scale(Math.min(0.09, toGoal.length() * 0.02)));
        setDeltaMovement(motion);
        needsSync = true;
        lookAt(target);
        if (aiTimer % 28 == 0) {
            // a spread of three gel balls at the player
            Vec3 from = position().add(0, getBbHeight() * 0.4, 0);
            Vec3 aim = target.getEyePosition().subtract(from).normalize();
            for (int i = -1; i <= 1; i++) {
                shoot(level, ProjectileKinds.REGAL_GEL, from, aim.yRot(i * 0.25F), 9.0F, 50.0F);
            }
            playSound(SoundEvents.SLIME_SQUISH, 1.6F, 1.3F);
        }
        if (aiTimer >= 140 && Math.abs(target.getX() - getX()) < 4 && Math.abs(target.getZ() - getZ()) < 4) {
            setNoGravity(false);
            setDeltaMovement(0, -1.4, 0);
            setAiState(DIVE);
            playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.6F);
        } else if (aiTimer >= 260) {
            // could not get above the player: dive anyway
            setNoGravity(false);
            setDeltaMovement(target.position().subtract(position()).normalize().scale(1.2));
            setAiState(DIVE);
        }
    }

    private void dive(ServerLevel level) {
        if (onGround() || aiTimer > 60) {
            slamSplash(level, 14);
            setNoGravity(true);
            setDeltaMovement(0, 0.6, 0);
            setAiState(FLY);
        }
    }

    // ---------------------------------------------------------------- attacks

    /** Landing: Regal Gel bursts out in a ring and up into the air. */
    private void slamSplash(ServerLevel level, int count) {
        level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.3, getZ(), 40, 1.8, 0.3, 1.8, 0.2);
        playSound(SoundEvents.SLIME_BLOCK_FALL, 3.0F, 0.5F);
        Vec3 from = position().add(0, 0.8, 0);
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + random.nextDouble() * 0.2;
            Vec3 dir = new Vec3(Math.cos(angle), 0.9 + random.nextDouble() * 0.6, Math.sin(angle)).normalize();
            shoot(level, ProjectileKinds.REGAL_GEL, from, dir, 7.0F + random.nextFloat() * 3.0F, 50.0F);
        }
    }

    private void shoot(ServerLevel level, ProjectileKind kind, Vec3 from, Vec3 direction, float speed, float terrariaDamage) {
        float damage = terrariaDamage * TerrariaDifficulty.enemyDamageMultiplier(level);
        TerrariaProjectile.shoot(level, this, kind, from, direction, speed, 0.5F, damage, DamageClass.GENERIC, 0, 1.0F);
    }

    /** Crystal, Bouncy and Heavenly Slimes join the fight (at most six at a time). */
    private void summonMinions(ServerLevel level, Player target) {
        if (--minionTimer > 0) {
            return;
        }
        minionTimer = phase() >= 1 ? 160 : 110;
        List<TerrariaMob> minions = level.getEntitiesOfClass(TerrariaMob.class, getBoundingBox().inflate(40),
            m -> m.getType() == MobContent.CRYSTAL_SLIME.get() || m.getType() == MobContent.BOUNCY_SLIME.get()
                || m.getType() == MobContent.HEAVENLY_SLIME.get());
        if (minions.size() >= 6) {
            return;
        }
        EntityType<? extends TerrariaMob> type = switch (random.nextInt(3)) {
            case 0 -> MobContent.CRYSTAL_SLIME.get();
            case 1 -> MobContent.BOUNCY_SLIME.get();
            default -> MobContent.HEAVENLY_SLIME.get();
        };
        TerrariaMob minion = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (minion != null) {
            minion.snapTo(getX(), getY() + getBbHeight() * 0.5, getZ(), random.nextFloat() * 360.0F, 0.0F);
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            minion.setDeltaMovement((random.nextDouble() - 0.5) * 0.8, 0.5, (random.nextDouble() - 0.5) * 0.8);
            minion.setTarget(target);
            level.addFreshEntity(minion);
        }
    }

    private void lookAt(Player target) {
        float yaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, net.minecraft.world.level.block.state.BlockState onState, net.minecraft.core.BlockPos pos) {
    }

    // ---------------------------------------------------------------- loot

    @Override
    protected Item healingPotion() {
        return CoreItems.GREATER_HEALING_POTION.get();
    }

    @Override
    protected void dropFightLoot(ServerLevel level) {
        dropStack(level, CoreItems.GEL.get(), 30 + random.nextInt(31));
        List<Item> pieces = List.of(ArmorContent.CRYSTAL_ASSASSIN.helmet().get(), ArmorContent.CRYSTAL_ASSASSIN.chest().get(),
            ArmorContent.CRYSTAL_ASSASSIN.legs().get());
        dropStack(level, pieces.get(random.nextInt(pieces.size())), 1);
        if (random.nextInt(3) == 0) {
            dropStack(level, com.terracraft.registry.content.QueenSlimeContent.VOLATILE_GELATIN.get(), 1);
        }
    }
}

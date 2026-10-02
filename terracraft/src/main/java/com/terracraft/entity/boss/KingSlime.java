package com.terracraft.entity.boss;

import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * King Slime: hops at the player (every third hop is a high one), shrinks as it loses life, teleports to the
 * player when they get away (shrink, vanish, reappear, grow) and sheds Blue Slimes when hurt.
 */
public class KingSlime extends TerrariaBoss {
    private static final int HOP = 0;
    private static final int TELEPORT_OUT = 1;
    private static final int TELEPORT_IN = 2;
    private static final int TELEPORT_TICKS = 30;

    private int hops;
    private int jumpDelay = 30;
    private int farTicks;
    private float lifeLostSinceSpawn;

    public KingSlime(EntityType<? extends KingSlime> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.KING_SLIME;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player target = findTarget(level);
        if (target != null) {
            setTarget(target);
        }
        switch (aiState) {
            case TELEPORT_OUT -> {
                if (aiTimer % 3 == 0) {
                    level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.5, getZ(), 10, 1.0, 0.5, 1.0, 0.1);
                }
                if (aiTimer >= TELEPORT_TICKS) {
                    if (target != null) {
                        teleportNear(level, target);
                    }
                    setAiState(TELEPORT_IN);
                }
            }
            case TELEPORT_IN -> {
                if (aiTimer >= TELEPORT_TICKS) {
                    setAiState(HOP);
                }
            }
            default -> hop(level, target);
        }
        updateScale();
    }

    private void hop(ServerLevel level, Player target) {
        if (target == null) {
            return;
        }
        double horizontal = Math.sqrt(Mth.square(target.getX() - getX()) + Mth.square(target.getZ() - getZ()));
        farTicks = horizontal > 22 || Math.abs(target.getY() - getY()) > 12 ? farTicks + 1 : 0;
        if (onGround()) {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x * 0.3, motion.y, motion.z * 0.3);
            if (farTicks > 60 || hops >= 10 && random.nextInt(3) == 0) {
                hops = 0;
                farTicks = 0;
                playSound(SoundEvents.SLIME_SQUISH, 2.0F, 0.5F);
                setAiState(TELEPORT_OUT);
                return;
            }
            if (--jumpDelay <= 0) {
                hops++;
                boolean high = hops % 3 == 0;
                float yaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                yBodyRot = yaw;
                double forward = Math.min(high ? 0.35 : 0.5, horizontal * 0.06 + 0.1);
                double up = high ? 1.05 : 0.7;
                setDeltaMovement(-Mth.sin(yaw * Mth.DEG_TO_RAD) * forward, up, Mth.cos(yaw * Mth.DEG_TO_RAD) * forward);
                needsSync = true;
                jumpDelay = (getHealth() / getMaxHealth() < 0.5F ? 18 : 28) + random.nextInt(10);
                playSound(SoundEvents.SLIME_JUMP, 1.5F, 0.6F);
            }
        }
    }

    private void teleportNear(ServerLevel level, Player target) {
        int x = Mth.floor(target.getX() + (random.nextDouble() - 0.5) * 6);
        int z = Mth.floor(target.getZ() + (random.nextDouble() - 0.5) * 6);
        int y = target.getBlockY();
        if (level.getBlockState(new BlockPos(x, y - 1, z)).isAir()) {
            y = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), y + 8);
        }
        teleportTo(x + 0.5, y, z + 0.5);
        setDeltaMovement(Vec3.ZERO);
    }

    /** Shrinks with lost life (100% -> 60%) and during teleports. */
    private void updateScale() {
        float scale = 0.6F + 0.4F * Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F);
        if (aiState == TELEPORT_OUT) {
            scale *= Math.max(0.1F, 1.0F - aiTimer / (float) TELEPORT_TICKS);
        } else if (aiState == TELEPORT_IN) {
            scale *= Math.max(0.1F, aiTimer / (float) TELEPORT_TICKS);
        }
        AttributeInstance attribute = getAttribute(Attributes.SCALE);
        if (attribute != null && Math.abs(attribute.getBaseValue() - scale) > 0.02) {
            attribute.setBaseValue(scale);
        }
    }

    @Override
    protected boolean dealsContactDamage() {
        return aiState == HOP;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        float before = getHealth();
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive()) {
            // Terraria: King Slime sheds slimes as it is damaged (about one per 4% of its life).
            lifeLostSinceSpawn += (before - getHealth());
            float per = getMaxHealth() * 0.04F;
            while (lifeLostSinceSpawn >= per) {
                lifeLostSinceSpawn -= per;
                spawnMinion(level);
            }
        }
        return hurt;
    }

    private void spawnMinion(ServerLevel level) {
        TerrariaMob slime = (random.nextInt(4) == 0 ? MobContent.GREEN_SLIME : MobContent.BLUE_SLIME).get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (slime != null) {
            slime.snapTo(getX() + (random.nextDouble() - 0.5) * getBbWidth(), getY() + 0.5, getZ() + (random.nextDouble() - 0.5) * getBbWidth(), 0.0F, 0.0F);
            slime.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            slime.setDeltaMovement((random.nextDouble() - 0.5) * 0.6, 0.4, (random.nextDouble() - 0.5) * 0.6);
            level.addFreshEntity(slime);
        }
    }
}

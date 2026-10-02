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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Eye of Cthulhu. First form: hovers above the player summoning Servants of Cthulhu, then charges three
 * times. At half life it spins while transforming into its mouth form: no defense, more damage, faster
 * charges. Leaves at daybreak.
 */
public class EyeOfCthulhu extends TerrariaBoss {
    public static final int PHASE_EYE = 0;
    public static final int PHASE_TRANSFORM = 1;
    public static final int PHASE_MOUTH = 2;

    private static final int HOVER = 0;
    private static final int CHARGE = 1;
    private static final int TRANSFORM = 2;

    private int charges;
    private int servantsThisHover;

    public EyeOfCthulhu(EntityType<? extends EyeOfCthulhu> type, Level level) {
        super(type, level, BossEvent.BossBarColor.RED);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.EYE_OF_CTHULHU;
    }

    @Override
    protected boolean fleesAtDay() {
        return true;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public int terrariaDefense() {
        return phase() == PHASE_MOUTH ? 0 : super.terrariaDefense();
    }

    @Override
    public float contactDamage() {
        // Terraria: 15 in the first form, 23 in the second (before Expert scaling)
        return super.contactDamage() * (phase() == PHASE_MOUTH ? 23.0F / 15.0F : 1.0F);
    }

    @Override
    public String spriteVariant() {
        return phase() == PHASE_MOUTH ? "mouth" : "";
    }

    @Override
    public float spriteSpin(float partialTicks) {
        return phase() == PHASE_TRANSFORM ? (tickCount + partialTicks) * 40.0F : 0.0F;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        Player target = findTarget(level);
        if (isLeaving(level) || target == null) {
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0.0, 0.06, 0.0));
            return;
        }
        setTarget(target);
        boolean mouth = phase() == PHASE_MOUTH;
        if (!mouth && phase() != PHASE_TRANSFORM && getHealth() < getMaxHealth() * 0.5F) {
            setPhase(PHASE_TRANSFORM);
            setAiState(TRANSFORM);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 1.5F);
        }
        switch (aiState) {
            case TRANSFORM -> transform(level);
            case CHARGE -> charge(target, mouth);
            default -> hover(level, target, mouth);
        }
    }

    private void hover(ServerLevel level, Player target, boolean mouth) {
        Vec3 goal = target.position().add(Mth.sin(aiTimer * 0.03F) * 4.0, 7.0, Mth.cos(aiTimer * 0.03F) * 4.0);
        Vec3 toward = goal.subtract(position());
        double speed = mouth ? 0.6 : 0.45;
        Vec3 desired = toward.lengthSqr() > 1 ? toward.normalize().scale(Math.min(speed, toward.length() * 0.1)) : Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().lerp(desired, 0.12));
        lookAt(target.getEyePosition());
        if (!mouth && aiTimer % 35 == 20 && servantsThisHover < 3) {
            servantsThisHover++;
            summonServant(level, target);
        }
        if (aiTimer > (mouth ? 50 : 150)) {
            servantsThisHover = 0;
            charges = 0;
            startCharge(target, mouth);
        }
    }

    private void startCharge(Player target, boolean mouth) {
        setAiState(CHARGE);
        Vec3 direction = target.getEyePosition().subtract(position()).normalize();
        setDeltaMovement(direction.scale(mouth ? 1.25 : 0.95));
        lookAt(position().add(direction));
        playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, mouth ? 0.7F : 0.5F);
    }

    private void charge(Player target, boolean mouth) {
        setDeltaMovement(getDeltaMovement().scale(0.975));
        lookAt(position().add(getDeltaMovement()));
        if (aiTimer > (mouth ? 22 : 30)) {
            charges++;
            if (charges >= 3) {
                setAiState(HOVER);
            } else {
                startCharge(target, mouth);
            }
        }
    }

    private void transform(ServerLevel level) {
        setDeltaMovement(getDeltaMovement().scale(0.85));
        if (aiTimer % 10 == 0) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY() + 1, getZ(), 6, 0.8, 0.8, 0.8, 0.2);
        }
        if (aiTimer == 40) {
            for (int i = 0; i < 3; i++) {
                summonServant(level, null);
            }
        }
        if (aiTimer >= 80) {
            setPhase(PHASE_MOUTH);
            setAiState(HOVER);
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.8F);
        }
    }

    private void summonServant(ServerLevel level, Player target) {
        TerrariaMob servant = MobContent.SERVANT_OF_CTHULHU.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (servant == null) {
            return;
        }
        Vec3 look = getLookAngle();
        servant.snapTo(getX() + look.x, getY() + getBbHeight() / 2 + look.y, getZ() + look.z, getYRot(), 0.0F);
        servant.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        servant.setDeltaMovement(look.scale(0.4));
        if (target != null) {
            servant.setTarget(target);
        }
        level.addFreshEntity(servant);
    }

    /** Points yaw and pitch at a position (used by the sprite renderer's tilt). */
    private void lookAt(Vec3 point) {
        Vec3 d = point.subtract(getX(), getY() + getBbHeight() / 2, getZ());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setXRot(pitch);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            // Terraria: 30-87 Demonite or Crimtane Ore depending on the world's evil
            boolean crimson = com.terracraft.progression.WorldProgression.get(level.getServer()).variants().evil()
                == com.terracraft.progression.WorldVariants.WorldEvil.CRIMSON;
            int count = 30 + random.nextInt(58);
            var ore = (crimson ? com.terracraft.registry.content.EvilContent.CRIMTANE_ORE : com.terracraft.registry.content.EvilContent.DEMONITE_ORE).get().asItem();
            while (count > 0) {
                int stack = Math.min(count, 64);
                level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, getX(), getY() + 0.5, getZ(), new net.minecraft.world.item.ItemStack(ore, stack)));
                count -= stack;
            }
        }
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }
}

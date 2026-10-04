package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.LunarContent;
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
 * Moon Lord. The body drifts slowly after the player; its exposed heart cannot be hurt until both hand eyes and the
 * head eye are destroyed.
 * <ul>
 *     <li>Hands: phantasmal eyes that home in, and slow phantasmal spheres thrown in a fan.</li>
 *     <li>Head: phantasmal bolts, and every so often the Phantasmal Deathray, a beam that sweeps at the player after
 *     a charging glow.</li>
 *     <li>Heart (once exposed): bursts of phantasmal bolts.</li>
 * </ul>
 */
public class MoonLord extends TerrariaBoss {
    private boolean partsSpawned;
    private int coreTimer = 60;

    public MoonLord(EntityType<? extends MoonLord> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.MOON_LORD;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    /** "exposed" once all the eyes are gone: the heart shows. */
    @Override
    public String spriteVariant() {
        return phase() >= 1 ? "exposed" : "";
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return phase() == 0 && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) || super.isInvulnerableTo(level, source);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        if (!partsSpawned) {
            spawnParts(level);
        }
        if (phase() == 0 && tickCount % 10 == 0
            && level.getEntitiesOfClass(Eye.class, getBoundingBox().inflate(60), e -> getUUID().equals(e.ownerId)).isEmpty()) {
            setPhase(1);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 3, getZ(), 120, 2, 2, 2, 0.2);
            playSound(SoundEvents.WARDEN_ROAR, 4.0F, 0.6F);
        }
        Player target = findTarget(level);
        if (target == null) {
            return;
        }
        setTarget(target);
        // keeps his distance: 16 blocks out from the player on whichever side he is, looming a little above
        Vec3 away = new Vec3(getX() - target.getX(), 0, getZ() - target.getZ());
        away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
        Vec3 goal = target.position().add(away.scale(16)).add(0, 2, 0);
        Vec3 motion = getDeltaMovement().scale(0.9).add(goal.subtract(position()).normalize().scale(0.012));
        if (motion.length() > 0.2) {
            motion = motion.normalize().scale(0.2);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 look = target.position().subtract(position());
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        if (phase() >= 1 && --coreTimer <= 0) {
            Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + random.nextDouble() * 0.2;
                shoot(level, this, ProjectileKinds.PHANTASMAL_BOLT, from, target.getEyePosition().subtract(from).normalize()
                    .add(Math.cos(a) * 0.25, Math.sin(a) * 0.25, 0).normalize(), 8.0F, 80.0F);
            }
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.4F);
            coreTimer = 40 + random.nextInt(20);
        }
    }

    private void spawnParts(ServerLevel level) {
        partsSpawned = true;
        for (int i = 0; i < 3; i++) {
            Eye eye = (i < 2 ? MobContent.MOON_LORD_HAND : MobContent.MOON_LORD_HEAD).get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (eye != null) {
                eye.slot = i;
                eye.snapTo(getX(), getY() + 4, getZ(), 0, 0);
                eye.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                eye.attach(this);
                level.addFreshEntity(eye);
            }
        }
    }

    static void shoot(ServerLevel level, net.minecraft.world.entity.LivingEntity from, ProjectileKind kind, Vec3 pos, Vec3 dir, float speed,
                      float terrariaDamage) {
        TerrariaProjectile.shoot(level, from, kind, pos, dir, speed, 0.0F, terrariaDamage * TerrariaDifficulty.enemyDamageMultiplier(level),
            DamageClass.GENERIC, 0, 1.0F);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    protected Item healingPotion() {
        return CoreItems.GREATER_HEALING_POTION.get();
    }

    @Override
    protected void dropFightLoot(ServerLevel level) {
        dropStack(level, LunarContent.LUMINITE.get(), 70 + random.nextInt(21));
        List<Item> loot = List.of(LunarContent.MEOWMERE.get(), LunarContent.STAR_WRATH.get(), LunarContent.SDMG.get(), LunarContent.LAST_PRISM.get(),
            LunarContent.LUNAR_FLARE.get());
        dropStack(level, loot.get(random.nextInt(loot.size())), 1);
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

    /** Moon Lord's hand eyes (slots 0 and 1) and head eye (slot 2): they hang from the body and attack until destroyed. */
    public static class Eye extends BossPart<MoonLord> {
        int slot;
        private int attackTimer = 50;
        private int beam;
        private Vec3 beamDir = Vec3.ZERO;

        public Eye(EntityType<? extends Eye> type, Level level) {
            super(type, level, MoonLord.class);
        }

        @Override
        public String tetherStyle() {
            return "flesh";
        }

        @Override
        protected void tickPart(ServerLevel level, MoonLord lord) {
            float yawRad = lord.getYRot() * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(Mth.cos(yawRad), 0, Mth.sin(yawRad));
            Vec3 rest = slot == 2 ? lord.position().add(0, lord.getBbHeight() + 1.0, 0)
                : lord.position().add(right.scale(slot == 0 ? -9 : 9)).add(0, 2 + Mth.sin((tickCount + slot * 20) * 0.05F) * 1.5, 0);
            Vec3 motion = rest.subtract(position()).scale(0.3);
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            setYRot(lord.getYRot());
            yBodyRot = lord.getYRot();
            yHeadRot = lord.getYRot();
            Player target = lord.getTarget() instanceof Player p ? p : null;
            if (target == null) {
                return;
            }
            Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
            if (beam > 0) {
                // the Phantasmal Deathray: charge, then a sweeping beam
                beam--;
                if (beam > 40) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 6, 0.5, 0.5, 0.5, 0.05);
                    beamDir = target.getEyePosition().subtract(from).normalize();
                } else {
                    Vec3 toward = target.getEyePosition().subtract(from).normalize();
                    beamDir = beamDir.add(toward.subtract(beamDir).scale(0.06)).normalize();
                    shoot(level, this, ProjectileKinds.DEATHRAY, from, beamDir, 20.0F, 150.0F);
                }
                return;
            }
            if (--attackTimer > 0) {
                return;
            }
            Vec3 aim = target.getEyePosition().subtract(from).normalize();
            if (slot == 2) {
                if (random.nextInt(3) == 0) {
                    beam = 70;
                    playSound(SoundEvents.BEACON_POWER_SELECT, 4.0F, 0.5F);
                } else {
                    for (int i = 0; i < 4; i++) {
                        shoot(level, this, ProjectileKinds.PHANTASMAL_BOLT, from, aim.add((random.nextDouble() - 0.5) * 0.1, 0,
                            (random.nextDouble() - 0.5) * 0.1).normalize(), 9.0F, 70.0F);
                    }
                }
                attackTimer = 70 + random.nextInt(30);
            } else if (random.nextBoolean()) {
                for (int i = 0; i < 3; i++) {
                    shoot(level, this, ProjectileKinds.PHANTASMAL_EYE, from, aim.add(0, 0.5, 0).yRot((i - 1) * 0.5F).normalize(), 4.0F, 75.0F);
                }
                attackTimer = 60 + random.nextInt(30);
            } else {
                for (int i = 0; i < 5; i++) {
                    shoot(level, this, ProjectileKinds.PHANTASMAL_SPHERE, from, aim.yRot((i - 2) * 0.25F), 2.5F, 80.0F);
                }
                attackTimer = 80 + random.nextInt(30);
            }
            playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 1.6F);
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            output.putInt("Slot", slot);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            slot = input.getIntOr("Slot", 0);
        }
    }
}

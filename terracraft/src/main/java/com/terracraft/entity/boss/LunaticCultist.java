package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.LunarContent;
import com.terracraft.registry.content.MobContent;
import com.terracraft.world.event.CelestialEvents;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lunatic Cultist, who appears when the cultists praying at the Dungeon entrance are disturbed (after Golem).
 * <ul>
 *     <li>Fireballs, a crackling Lightning Orb, a slow freezing Ice Mist and swarms of homing Ancient Light.</li>
 *     <li>The Ritual: he vanishes and reappears among copies of himself in a circle over the player. Hit the real
 *     one to break it; copies left standing keep attacking as Cultist Clones.</li>
 *     <li>His defeat starts the Celestial Events: the four pillars descend.</li>
 * </ul>
 */
public class LunaticCultist extends TerrariaBoss {
    private static final int HOVER = 0;
    private static final int RITUAL = 1;
    private int attack;
    private final List<CultistClone> ritualClones = new ArrayList<>();

    public LunaticCultist(EntityType<? extends LunaticCultist> type, Level level) {
        super(type, level, BossEvent.BossBarColor.BLUE);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.LUNATIC_CULTIST;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        Player target = findTarget(level);
        if (target == null) {
            return;
        }
        setTarget(target);
        face(target.position().subtract(position()));
        if (aiState == RITUAL) {
            ritual(level, target);
            return;
        }
        Vec3 goal = target.position().add(Mth.sin(tickCount * 0.02F) * 9, 6, Mth.cos(tickCount * 0.02F) * 9);
        Vec3 motion = getDeltaMovement().scale(0.85).add(goal.subtract(position()).normalize().scale(0.05));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        int period = getHealth() < getMaxHealth() * 0.5F ? 45 : 60;
        if (aiTimer % period != period - 1) {
            return;
        }
        Vec3 from = position().add(0, 1.6, 0);
        Vec3 aim = target.getEyePosition().subtract(from).normalize();
        switch (attack++ % 5) {
            case 0 -> {
                for (int i = -1; i <= 1; i++) {
                    shoot(level, ProjectileKinds.CULTIST_FIREBALL, from, aim.yRot(i * 0.35F), 5.0F, 60.0F);
                }
                playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.8F);
            }
            case 1 -> {
                for (int i = 0; i < 4; i++) {
                    shoot(level, ProjectileKinds.CULTIST_LIGHTNING, from, aim.add((random.nextDouble() - 0.5) * 0.2, 0, (random.nextDouble() - 0.5) * 0.2)
                        .normalize(), 12.0F, 70.0F);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 30, 0.6, 0.6, 0.6, 0.2);
                playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.6F);
            }
            case 2 -> {
                shoot(level, ProjectileKinds.ICE_MIST, from, aim, 1.8F, 70.0F);
                playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
            }
            case 3 -> {
                for (int i = 0; i < 6; i++) {
                    Vec3 dir = aim.add((random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 0.8, (random.nextDouble() - 0.5) * 1.2).normalize();
                    shoot(level, ProjectileKinds.ANCIENT_LIGHT, from, dir, 4.0F, 60.0F);
                }
                playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 2.0F, 1.2F);
            }
            default -> startRitual(level, target);
        }
    }

    /** Vanish and reappear at one of four points around the player, the others taken by copies. */
    private void startRitual(ServerLevel level, Player target) {
        setAiState(RITUAL);
        ritualClones.clear();
        int real = random.nextInt(4);
        Vec3 center = target.position().add(0, 7, 0);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            Vec3 spot = center.add(Math.cos(a) * 9, 0, Math.sin(a) * 9);
            level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1, spot.z, 40, 0.4, 1.0, 0.4, 0.5);
            if (i == real) {
                teleportTo(spot.x, spot.y, spot.z);
                setDeltaMovement(Vec3.ZERO);
            } else {
                CultistClone clone = MobContent.CULTIST_CLONE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (clone != null) {
                    clone.snapTo(spot.x, spot.y, spot.z, 0, 0);
                    clone.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(spot)), EntitySpawnReason.MOB_SUMMONED, null);
                    clone.cultist = getUUID();
                    level.addFreshEntity(clone);
                    ritualClones.add(clone);
                }
            }
        }
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 3.0F, 0.7F);
    }

    private void ritual(ServerLevel level, Player target) {
        setDeltaMovement(Vec3.ZERO);
        if (tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1, getZ(), 6, 0.5, 1.0, 0.5, 0.5);
        }
        if (aiTimer >= 100) {
            // nobody found him: the copies wake up as Cultist Clones and he comes back fighting
            for (CultistClone clone : ritualClones) {
                if (clone.isAlive()) {
                    clone.awaken();
                }
            }
            ritualClones.clear();
            setAiState(HOVER);
        }
    }

    /** A hit during the Ritual breaks it: the copies vanish. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && aiState == RITUAL && source.getEntity() instanceof Player) {
            for (CultistClone clone : ritualClones) {
                if (clone.isAlive()) {
                    level.sendParticles(ParticleTypes.POOF, clone.getX(), clone.getY() + 1, clone.getZ(), 10, 0.3, 0.6, 0.3, 0.05);
                    clone.discard();
                }
            }
            ritualClones.clear();
            setAiState(HOVER);
        }
        return hurt;
    }

    private void shoot(ServerLevel level, ProjectileKind kind, Vec3 from, Vec3 dir, float speed, float terrariaDamage) {
        TerrariaProjectile.shoot(level, this, kind, from, dir, speed, 0.2F, terrariaDamage * TerrariaDifficulty.enemyDamageMultiplier(level),
            DamageClass.GENERIC, 0, 1.0F);
    }

    private void face(Vec3 look) {
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
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
        dropStack(level, LunarContent.ANCIENT_MANIPULATOR.get().asItem(), 1);
        CelestialEvents.startPillars(level);
    }

    /** One of the cultists praying at the Dungeon entrance; hurting any of them brings the Lunatic Cultist. */
    public static class Devotee extends TerrariaMob {
        public Devotee(EntityType<? extends Devotee> type, Level level) {
            super(type, level);
        }

        @Override
        protected void registerGoals() {
        }

        @Override
        public void travel(Vec3 input) {
            super.travel(Vec3.ZERO);
        }

        @Override
        public boolean removeWhenFarAway(double distanceSq) {
            return false;
        }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                CelestialEvents.disturbDevotees(level, player, this);
                return true;
            }
            return source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL) && super.hurtServer(level, source, amount);
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            super.customServerAiStep(level);
            if (tickCount % 10 == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 2.2, getZ(), 3, 0.3, 0.3, 0.3, 0.3);
            }
        }
    }

    /** A copy left over from the Ritual: it floats near the player throwing fireballs; vanishes with the Cultist. */
    public static class CultistClone extends TerrariaMob {
        java.util.UUID cultist;
        private boolean awake;

        public CultistClone(EntityType<? extends CultistClone> type, Level level) {
            super(type, level);
            setNoGravity(true);
            noPhysics = true;
        }

        void awaken() {
            awake = true;
        }

        @Override
        protected void registerGoals() {
        }

        @Override
        public void travel(Vec3 input) {
        }

        @Override
        public boolean countsTowardSpawnCap() {
            return false;
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            super.customServerAiStep(level);
            setNoGravity(true);
            if (cultist == null || !(level.getEntity(cultist) instanceof LunaticCultist boss) || !boss.isAlive()) {
                discard();
                return;
            }
            Player target = boss.getTarget() instanceof Player p ? p : null;
            if (!awake || target == null) {
                return;
            }
            Vec3 goal = target.position().add(Mth.cos(tickCount * 0.03F + getId()) * 8, 5, Mth.sin(tickCount * 0.03F + getId()) * 8);
            Vec3 motion = getDeltaMovement().scale(0.85).add(goal.subtract(position()).normalize().scale(0.04));
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            if (tickCount % 70 == getId() % 70) {
                Vec3 from = position().add(0, 1.6, 0);
                TerrariaProjectile.shoot(level, this, ProjectileKinds.CULTIST_FIREBALL, from, target.getEyePosition().subtract(from).normalize(),
                    5.0F, 0.3F, 50.0F * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 1.0F);
            }
        }
    }
}

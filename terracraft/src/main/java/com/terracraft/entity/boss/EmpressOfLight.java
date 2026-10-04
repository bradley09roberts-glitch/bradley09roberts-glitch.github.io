package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.EmpressContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Empress of Light, who comes when a Prismatic Lacewing is killed in the Hallow at night.
 * <ul>
 *     <li>Prismatic Bolts: a fan of homing rainbow bolts.</li>
 *     <li>Ethereal Lances: rows of lances appear around the player and shoot in after a moment.</li>
 *     <li>Sun Dance: beams of light sweep out from her in a turning star.</li>
 *     <li>Dash: she charges straight through.</li>
 *     <li>Second half: everything faster, plus the Everlasting Rainbow, a spiral of bolts.</li>
 *     <li>Fought in daylight she is furious: her attacks are lethal.</li>
 * </ul>
 */
public class EmpressOfLight extends TerrariaBoss {
    private static final int HOVER = 0;
    private static final int BOLTS = 1;
    private static final int LANCES = 2;
    private static final int SUN_DANCE = 3;
    private static final int DASH = 4;
    private static final int RAINBOW = 5;

    private int nextAttack;
    private Vec3 dashDir = Vec3.ZERO;

    public EmpressOfLight(EntityType<? extends EmpressOfLight> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PINK);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.EMPRESS_OF_LIGHT;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    private boolean furious() {
        return level().isBrightOutside();
    }

    @Override
    public float contactDamage() {
        return super.contactDamage() * (furious() ? 4.0F : 1.0F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        Player target = findTarget(level);
        if (target == null) {
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, 0.05, 0));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        setTarget(target);
        if (phase() == 0 && getHealth() <= getMaxHealth() * 0.5F) {
            setPhase(1);
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 100, 2, 2, 2, 0.2);
            playSound(SoundEvents.BEACON_POWER_SELECT, 3.0F, 1.6F);
        }
        if (tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 2, 1.5, 0.8, 0.5, 0.01);
        }
        float speed = phase() >= 1 ? 1.4F : 1.0F;
        switch (aiState) {
            case BOLTS -> bolts(level, target);
            case LANCES -> lances(level, target, speed);
            case SUN_DANCE -> sunDance(level, target);
            case DASH -> dash(level);
            case RAINBOW -> rainbow(level, target);
            default -> hover(target, speed);
        }
    }

    private void hover(Player target, float speed) {
        Vec3 goal = target.position().add(Mth.sin(tickCount * 0.02F) * 8, 6, Mth.cos(tickCount * 0.02F) * 8);
        drift(goal, 0.1 * speed);
        face(target.position().subtract(position()));
        if (aiTimer >= (phase() >= 1 ? 20 : 35)) {
            int[] order = phase() >= 1 ? new int[]{BOLTS, LANCES, RAINBOW, SUN_DANCE, DASH, LANCES} : new int[]{BOLTS, LANCES, SUN_DANCE, DASH};
            int next = order[nextAttack++ % order.length];
            if (next == DASH) {
                dashDir = target.getEyePosition().subtract(position().add(0, 1.5, 0)).normalize();
                playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 3.0F, 1.4F);
            }
            setAiState(next);
        }
    }

    private void bolts(ServerLevel level, Player target) {
        drift(target.position().add(0, 7, 0), 0.05);
        if (aiTimer % 10 == 1) {
            Vec3 from = position().add(0, 1.5, 0);
            Vec3 aim = target.getEyePosition().subtract(from).normalize();
            for (int i = -3; i <= 3; i++) {
                shoot(level, ProjectileKinds.PRISMATIC_BOLT, from, aim.yRot(i * 0.3F).add(0, 0.4, 0).normalize(), 5.0F, 70.0F);
            }
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.2F);
        }
        if (aiTimer >= 40) {
            setAiState(HOVER);
        }
    }

    /** A ring of lances appears around the player, then they all fly in. */
    private void lances(ServerLevel level, Player target, float speed) {
        drift(target.position().add(0, 8, 0), 0.05);
        if (aiTimer == 1 || phase() >= 1 && aiTimer == 30) {
            int count = phase() >= 1 ? 14 : 10;
            double offset = random.nextDouble() * Math.PI;
            for (int i = 0; i < count; i++) {
                double a = offset + i * Math.PI * 2 / count;
                Vec3 from = target.getEyePosition().add(Math.cos(a) * 14, (random.nextDouble() - 0.3) * 4, Math.sin(a) * 14);
                level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.01);
                shoot(level, ProjectileKinds.ETHEREAL_LANCE, from, target.getEyePosition().subtract(from).normalize(), 3.5F * speed, 80.0F);
            }
            playSound(SoundEvents.AMETHYST_CLUSTER_BREAK, 2.5F, 1.6F);
        }
        if (aiTimer >= (phase() >= 1 ? 60 : 45)) {
            setAiState(HOVER);
        }
    }

    /** Beams of light sweep out in a slowly turning star. */
    private void sunDance(ServerLevel level, Player target) {
        drift(target.position().add(0, 5, 0), 0.04);
        if (aiTimer % 6 == 0) {
            Vec3 from = position().add(0, 1.5, 0);
            int rays = 8;
            for (int i = 0; i < rays; i++) {
                double a = aiTimer * 0.05 + i * Math.PI * 2 / rays;
                shoot(level, ProjectileKinds.SUN_RAY, from, new Vec3(Math.cos(a), 0, Math.sin(a)), 4.0F, 70.0F);
            }
        }
        if (aiTimer >= 80) {
            setAiState(HOVER);
        }
    }

    private void dash(ServerLevel level) {
        Vec3 motion = dashDir.scale(phase() >= 1 ? 1.6 : 1.2);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        face(motion);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 4, 0.8, 0.8, 0.8, 0.02);
        if (aiTimer >= 28) {
            setAiState(HOVER);
        }
    }

    /** The Everlasting Rainbow: a spiral of slow bolts unwinding from her. */
    private void rainbow(ServerLevel level, Player target) {
        drift(target.position().add(0, 6, 0), 0.03);
        if (aiTimer % 2 == 0) {
            double a = aiTimer * 0.35;
            Vec3 from = position().add(0, 1.5, 0);
            shoot(level, ProjectileKinds.PRISMATIC_BOLT, from, new Vec3(Math.cos(a), -0.1, Math.sin(a)), 2.5F, 70.0F);
        }
        if (aiTimer >= 70) {
            setAiState(HOVER);
        }
    }

    private void drift(Vec3 goal, double accel) {
        Vec3 toGoal = goal.subtract(position());
        Vec3 motion = getDeltaMovement().scale(0.88).add(toGoal.normalize().scale(Math.min(accel, toGoal.length() * 0.02)));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
    }

    private void shoot(ServerLevel level, ProjectileKind kind, Vec3 from, Vec3 dir, float speed, float terrariaDamage) {
        float damage = terrariaDamage * TerrariaDifficulty.enemyDamageMultiplier(level) * (furious() ? 4.0F : 1.0F);
        TerrariaProjectile.shoot(level, this, kind, from, dir, speed, 0.0F, damage, DamageClass.GENERIC, 0, 1.0F);
    }

    private void face(Vec3 look) {
        if (look.lengthSqr() < 1.0E-4) {
            return;
        }
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
        List<Item> loot = List.of(EmpressContent.NIGHTGLOW.get(), EmpressContent.STARLIGHT.get());
        dropStack(level, loot.get(random.nextInt(loot.size())), 1);
        if (random.nextInt(4) == 0) {
            dropStack(level, EmpressContent.EMPRESS_WINGS.get(), 1);
        }
    }
}

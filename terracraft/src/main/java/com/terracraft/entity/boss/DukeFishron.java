package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.FishronContent;
import com.terracraft.registry.content.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Duke Fishron, fished up in the ocean with a Truffle Worm as bait.
 * <ul>
 *     <li>Hovers beside the player, then charges in a run of dashes; between runs it blows a ring of homing
 *     bubbles or spits a Sharknado, a waterspout that keeps throwing Sharkrons at the player.</li>
 *     <li>Below half life it turns furious (red eyes): longer, faster dash runs and more Sharknados.</li>
 *     <li>Away from the ocean it is enraged: much faster and twice the damage.</li>
 * </ul>
 */
public class DukeFishron extends TerrariaBoss {
    private static final int HOVER = 0;
    private static final int WINDUP = 1;
    private static final int DASH = 2;
    private static final int BUBBLES = 3;

    private int dashesLeft;
    private int attackCycle;
    private Vec3 dashDir = Vec3.ZERO;
    private Vec3 sharknado;
    private int sharknadoTicks;
    private boolean enraged;

    public DukeFishron(EntityType<? extends DukeFishron> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.DUKE_FISHRON;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public String spriteVariant() {
        return phase() >= 1 ? "rage" : "";
    }

    @Override
    public float contactDamage() {
        return super.contactDamage() * (phase() >= 1 ? 1.25F : 1.0F) * (enraged ? 2.0F : 1.0F);
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
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, 0.05, 0));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        setTarget(target);
        enraged = !atSea(level, target.blockPosition());
        if (phase() == 0 && getHealth() <= getMaxHealth() * 0.5F) {
            setPhase(1);
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 120, 1.5, 1.0, 1.5, 0.3);
            playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 3.0F, 0.7F);
            setAiState(HOVER);
        }
        tickSharknado(level, target);
        double speed = enraged ? 1.6 : 1.0;
        switch (aiState) {
            case WINDUP -> windup(target);
            case DASH -> dash(level, speed);
            case BUBBLES -> bubbles(level, target);
            default -> hover(level, target, speed);
        }
    }

    private static boolean atSea(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH);
    }

    /** Floats off to one side of the player; after a while starts the next attack. */
    private void hover(ServerLevel level, Player target, double speed) {
        double side = (attackCycle % 2 == 0) ? 9 : -9;
        Vec3 goal = target.position().add(side, 4, 0);
        Vec3 toGoal = goal.subtract(position());
        Vec3 motion = getDeltaMovement().scale(0.88).add(toGoal.normalize().scale(Math.min(0.12 * speed, toGoal.length() * 0.03)));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        face(target.position().subtract(position()));
        if (aiTimer < (phase() >= 1 ? 25 : 45)) {
            return;
        }
        attackCycle++;
        int pick = attackCycle % (phase() >= 1 ? 3 : 4);
        if (pick == 1) {
            setAiState(BUBBLES);
        } else if (pick == 2 && sharknadoTicks <= 0) {
            spitSharknado(level, target);
            setAiState(HOVER);
        } else {
            dashesLeft = phase() >= 1 ? 4 : 3;
            setAiState(WINDUP);
        }
    }

    private void windup(Player target) {
        setDeltaMovement(getDeltaMovement().scale(0.8));
        move(MoverType.SELF, getDeltaMovement());
        Vec3 aim = target.getEyePosition().subtract(position().add(0, getBbHeight() * 0.5, 0));
        face(aim);
        if (aiTimer >= (phase() >= 1 ? 8 : 14)) {
            dashDir = aim.normalize();
            playSound(SoundEvents.DOLPHIN_ATTACK, 3.0F, 0.5F);
            setAiState(DASH);
        }
    }

    private void dash(ServerLevel level, double speed) {
        double dashSpeed = (phase() >= 1 ? 1.9 : 1.5) * speed;
        setDeltaMovement(dashDir.scale(dashSpeed));
        move(MoverType.SELF, getDeltaMovement());
        if (tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 1, getZ(), 6, 0.8, 0.6, 0.8, 0.05);
        }
        if (aiTimer >= 22) {
            setAiState(--dashesLeft > 0 ? WINDUP : HOVER);
        }
    }

    /** Circles around, blowing homing bubbles that burst on whatever they touch. */
    private void bubbles(ServerLevel level, Player target) {
        Vec3 center = target.position().add(0, 3, 0);
        double angle = aiTimer * 0.12;
        Vec3 goal = center.add(Math.cos(angle) * 10, 2, Math.sin(angle) * 10);
        Vec3 motion = goal.subtract(position()).scale(0.2);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        face(motion);
        if (aiTimer % 5 == 0) {
            Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
            Vec3 dir = target.getEyePosition().subtract(from).normalize();
            TerrariaProjectile.shoot(level, this, ProjectileKinds.DETONATING_BUBBLE, from, dir, 2.5F, 4.0F,
                damage(level, 70.0F), DamageClass.GENERIC, 0, 1.0F);
            playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.5F, 1.2F);
        }
        if (aiTimer >= 60) {
            setAiState(HOVER);
        }
    }

    private void spitSharknado(ServerLevel level, Player target) {
        sharknado = target.position().add((random.nextDouble() - 0.5) * 16, 0, (random.nextDouble() - 0.5) * 16);
        sharknadoTicks = phase() >= 1 ? 220 : 160;
        playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 3.0F, 1.2F);
    }

    /** The waterspout: a whirling column of spray that throws a Sharkron at the player every so often. */
    private void tickSharknado(ServerLevel level, Player target) {
        if (sharknadoTicks <= 0 || sharknado == null) {
            return;
        }
        sharknadoTicks--;
        for (int i = 0; i < 4; i++) {
            double h = random.nextDouble() * 12;
            double a = (tickCount * 0.4 + h) % Mth.TWO_PI;
            double r = 0.8 + h * 0.25;
            level.sendParticles(ParticleTypes.SPLASH, sharknado.x + Math.cos(a) * r, sharknado.y + h, sharknado.z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.0);
            level.sendParticles(ParticleTypes.CLOUD, sharknado.x + Math.cos(a + Math.PI) * r, sharknado.y + h, sharknado.z + Math.sin(a + Math.PI) * r,
                1, 0.1, 0.1, 0.1, 0.0);
        }
        if (sharknadoTicks % 40 == 0) {
            TerrariaMob shark = MobContent.SHARKRON.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (shark != null) {
                shark.snapTo(sharknado.x, sharknado.y + 8, sharknado.z, random.nextFloat() * 360, 0);
                shark.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(sharknado)), EntitySpawnReason.MOB_SUMMONED, null);
                shark.setTarget(target);
                shark.setDeltaMovement(target.position().subtract(sharknado).normalize().scale(0.6).add(0, 0.3, 0));
                level.addFreshEntity(shark);
            }
        }
    }

    private float damage(ServerLevel level, float terraria) {
        return terraria * TerrariaDifficulty.enemyDamageMultiplier(level) * (enraged ? 2.0F : 1.0F);
    }

    private void face(Vec3 look) {
        if (look.lengthSqr() < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
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
        List<Item> loot = List.of(FishronContent.TSUNAMI.get(), FishronContent.RAZORBLADE_TYPHOON.get(), FishronContent.BUBBLE_GUN.get());
        dropStack(level, loot.get(random.nextInt(loot.size())), 1);
        if (random.nextInt(3) == 0) {
            dropStack(level, FishronContent.FISHRON_WINGS.get(), 1);
        }
    }
}

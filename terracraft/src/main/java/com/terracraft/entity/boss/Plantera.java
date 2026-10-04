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
import com.terracraft.registry.content.MobContent;
import com.terracraft.registry.content.PlanteraContent;
import com.terracraft.world.jungle.JungleFeature;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Plantera, awoken by breaking a Plantera's Bulb in the underground jungle once the three mechanical bosses are dead.
 * <ul>
 *     <li>Three hooks shoot out on vines and latch onto the cave walls around the player; the bulb can only move
 *     within reach of where its hooks hold, so it crawls through the caves by moving them one at a time.</li>
 *     <li>First half of its life: seeds, poison seeds and now and then a thorn ball that bounces around the cave.</li>
 *     <li>Second half: the petals open into a toothed mouth, tentacles sprout around it, it moves faster, and it
 *     fires drifting spores instead.</li>
 *     <li>Leaving the jungle enrages it: twice as fast and twice the damage.</li>
 * </ul>
 */
public class Plantera extends TerrariaBoss {
    private static final int HOOKS = 3;
    private static final int TENTACLES = 8;

    private List<Hook> hooks = List.of();
    private int shotTimer = 60;
    private boolean enraged;
    private boolean tentaclesGrown;

    public Plantera(EntityType<? extends Plantera> type, Level level) {
        super(type, level, BossEvent.BossBarColor.GREEN);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.PLANTERA;
    }

    @Override
    protected void registerGoals() {
    }

    /** The second phase opens the petals into a mouth. */
    @Override
    public String spriteVariant() {
        return phase() >= 1 ? "mouth" : "";
    }

    @Override
    public int terrariaDefense() {
        return phase() >= 1 ? 10 : super.terrariaDefense();
    }

    @Override
    public float contactDamage() {
        return super.contactDamage() * (phase() >= 1 ? 1.4F : 1.0F) * (enraged ? 2.0F : 1.0F);
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        setNoGravity(true);
        Player target = findTarget(level);
        if (target != null) {
            setTarget(target);
        }
        if (tickCount % 5 == 1) {
            hooks = level.getEntitiesOfClass(Hook.class, getBoundingBox().inflate(80), h -> getUUID().equals(h.ownerId));
        }
        if (hooks.size() < HOOKS && target != null) {
            growHook(level);
        }
        if (phase() == 0 && getHealth() <= getMaxHealth() * 0.5F) {
            setPhase(1);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 1.0, getZ(), 80, 1.5, 1.5, 1.5, 0.1);
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 1.3F);
        }
        if (phase() >= 1 && !tentaclesGrown) {
            growTentacles(level);
        }
        if (target == null) {
            setDeltaMovement(getDeltaMovement().scale(0.9));
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        enraged = !JungleFeature.isJungle(level, target.getBlockX(), target.getBlockZ());
        moveTowards(target);
        attack(level, target);
    }

    /** Moves at the player, but only as far from the middle of its hooks as the vines allow. */
    private void moveTowards(Player target) {
        Vec3 goal = target.getEyePosition().subtract(0, getBbHeight() * 0.5, 0);
        if (!hooks.isEmpty()) {
            Vec3 center = Vec3.ZERO;
            for (Hook hook : hooks) {
                center = center.add(hook.position());
            }
            center = center.scale(1.0 / hooks.size());
            double leash = phase() >= 1 ? 15.0 : 12.0;
            Vec3 offset = goal.subtract(center);
            if (offset.length() > leash) {
                goal = center.add(offset.normalize().scale(leash));
            }
        }
        double speed = (phase() >= 1 ? 0.42 : 0.28) * (enraged ? 2.0 : 1.0);
        Vec3 toGoal = goal.subtract(position());
        Vec3 motion = getDeltaMovement().scale(0.9).add(toGoal.normalize().scale(Math.min(speed * 0.15, toGoal.length() * 0.05)));
        if (motion.length() > speed) {
            motion = motion.normalize().scale(speed);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        Vec3 look = target.getEyePosition().subtract(position().add(0, getBbHeight() * 0.5, 0));
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    private void attack(ServerLevel level, Player target) {
        if (--shotTimer > 0 || !hasLineOfSight(target)) {
            return;
        }
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 aim = target.getEyePosition().subtract(from).normalize();
        float speedBoost = enraged ? 1.5F : 1.0F;
        if (phase() == 0) {
            int roll = random.nextInt(12);
            if (roll == 0) {
                shoot(level, ProjectileKinds.THORN_BALL, from, aim, 6.0F * speedBoost, 70.0F);
                playSound(SoundEvents.SWEET_BERRY_BUSH_PLACE, 2.0F, 0.6F);
            } else {
                shoot(level, roll < 4 ? ProjectileKinds.POISON_SEED : ProjectileKinds.PLANTERA_SEED, from, aim, 11.0F * speedBoost, 50.0F);
                playSound(SoundEvents.BIG_DRIPLEAF_TILT_DOWN, 1.5F, 1.4F);
            }
            shotTimer = 22 + random.nextInt(18);
        } else {
            for (int i = 0; i < 3; i++) {
                Vec3 dir = aim.add((random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8, (random.nextDouble() - 0.5) * 0.8).normalize();
                shoot(level, ProjectileKinds.PLANTERA_SPORE, from, dir, 4.0F * speedBoost, 62.0F);
            }
            playSound(SoundEvents.SPORE_BLOSSOM_HIT, 2.0F, 0.8F);
            shotTimer = 45 + random.nextInt(20);
        }
    }

    private void shoot(ServerLevel level, ProjectileKind kind, Vec3 from, Vec3 dir, float speed, float terrariaDamage) {
        float damage = terrariaDamage * TerrariaDifficulty.enemyDamageMultiplier(level) * (enraged ? 2.0F : 1.0F);
        TerrariaProjectile.shoot(level, this, kind, from, dir, speed, 0.5F, damage, DamageClass.GENERIC, 0, 1.0F);
    }

    private void growHook(ServerLevel level) {
        Hook hook = MobContent.PLANTERA_HOOK.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (hook != null) {
            hook.snapTo(getX(), getY() + getBbHeight() * 0.5, getZ(), 0.0F, 0.0F);
            hook.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            hook.attach(this);
            hook.relocateTimer = 0;
            level.addFreshEntity(hook);
            hooks = new java.util.ArrayList<>(hooks);
            hooks.add(hook);
        }
    }

    private void growTentacles(ServerLevel level) {
        tentaclesGrown = true;
        for (int i = 0; i < TENTACLES; i++) {
            Tentacle tentacle = MobContent.PLANTERA_TENTACLE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
            if (tentacle == null) {
                continue;
            }
            tentacle.angle = i * Mth.TWO_PI / TENTACLES;
            tentacle.snapTo(getX(), getY() + getBbHeight() * 0.5, getZ(), 0.0F, 0.0F);
            tentacle.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            tentacle.attach(this);
            level.addFreshEntity(tentacle);
        }
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
        dropStack(level, PlanteraContent.TEMPLE_KEY.get(), 1);
        List<Item> weapons = List.of(PlanteraContent.SEEDLER.get(), PlanteraContent.VENUS_MAGNUM.get(), PlanteraContent.LEAF_BLOWER.get());
        dropStack(level, weapons.get(random.nextInt(weapons.size())), 1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Tentacles", tentaclesGrown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        tentaclesGrown = input.getBooleanOr("Tentacles", false);
    }

    // ================================================================ parts

    /**
     * Plantera's Hook: flies out to a wall near the player and holds on there. Every few seconds one lets go and
     * finds a new spot. Hooks cannot be hurt.
     */
    public static class Hook extends BossPart<Plantera> {
        private @Nullable Vec3 anchor;
        int relocateTimer = 40;

        public Hook(EntityType<? extends Hook> type, Level level) {
            super(type, level, Plantera.class);
        }

        @Override
        public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
            return !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        }

        @Override
        public boolean showsHealthBar() {
            return false;
        }

        @Override
        protected void tickPart(ServerLevel level, Plantera plantera) {
            Player target = plantera.getTarget() instanceof Player p ? p : null;
            boolean fast = plantera.phase() >= 1;
            if (target != null && (--relocateTimer <= 0 || anchor == null || anchor.distanceTo(target.position()) > 26)) {
                anchor = findWall(level, target);
                relocateTimer = (fast ? 50 : 90) + random.nextInt(fast ? 60 : 110);
            }
            if (anchor == null) {
                return;
            }
            Vec3 toAnchor = anchor.subtract(position());
            double distance = toAnchor.length();
            double speed = (fast ? 0.9 : 0.6) * (plantera.enraged ? 1.5 : 1.0);
            Vec3 motion = distance < speed ? toAnchor : toAnchor.scale(speed / distance);
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            if (distance > 0.3) {
                float yaw = (float) (Mth.atan2(toAnchor.z, toAnchor.x) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                yBodyRot = yaw;
            }
        }

        /** A spot on a solid block some way from the player, or open air if the cave is too wide to find one. */
        private Vec3 findWall(ServerLevel level, Player target) {
            Vec3 from = target.getEyePosition();
            for (int attempt = 0; attempt < 8; attempt++) {
                double yaw = random.nextDouble() * Mth.TWO_PI;
                double pitch = (random.nextDouble() - 0.5) * Math.PI;
                Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
                BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(18)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, target));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceTo(from) > 5) {
                    return hit.getLocation().subtract(dir.scale(0.4)).subtract(0, getBbHeight() * 0.5, 0);
                }
            }
            double yaw = random.nextDouble() * Mth.TWO_PI;
            return from.add(Math.cos(yaw) * 12, random.nextDouble() * 6 - 2, Math.sin(yaw) * 12);
        }
    }

    /** Plantera's Tentacle (second phase): circles the bulb on a vine, snapping outward at the player. */
    public static class Tentacle extends BossPart<Plantera> {
        float angle;

        public Tentacle(EntityType<? extends Tentacle> type, Level level) {
            super(type, level, Plantera.class);
        }

        @Override
        protected void tickPart(ServerLevel level, Plantera plantera) {
            angle += 0.03F;
            float reach = 3.5F + Mth.sin((tickCount + angle * 20) * 0.12F) * 1.5F;
            Vec3 center = plantera.position().add(0, plantera.getBbHeight() * 0.5 - getBbHeight() * 0.5, 0);
            Vec3 goal = center.add(Mth.cos(angle) * reach, Mth.sin(angle * 2) * 1.5, Mth.sin(angle) * reach);
            if (plantera.getTarget() instanceof Player target && target.distanceToSqr(this) < 36 && (tickCount + (int) (angle * 10)) % 80 < 15) {
                goal = target.position().add(0, target.getBbHeight() * 0.4, 0);   // snap at the player
            }
            Vec3 motion = goal.subtract(position()).scale(0.25);
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            Vec3 out = position().subtract(center);
            float yaw = (float) (Mth.atan2(out.z, out.x) * Mth.RAD_TO_DEG) - 90.0F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }
}

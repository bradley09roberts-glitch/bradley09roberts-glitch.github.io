package com.terracraft.entity.summon;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerraDamageTypes;
import com.terracraft.combat.TerraHit;
import com.terracraft.combat.WhipTags;
import com.terracraft.entity.SpriteEntity;
import com.terracraft.entity.projectile.ProjectileKind;
import com.terracraft.entity.projectile.TargetRules;
import com.terracraft.entity.projectile.TerrariaProjectile;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A summoned minion or sentry. Minions follow their owner and attack nearby enemies (by ramming them or by
 * shooting), going first for the enemy their owner last struck with a whip. Sentries stay where they were placed
 * and shoot. Neither can be hurt, neither is saved with the world, and both vanish when their owner dies, leaves or
 * summons past their slot limit.
 */
public class MinionEntity extends Mob implements TargetRules.FriendlyToPlayers, SpriteEntity {
    /**
     * How a minion fights.
     *
     * @param sentry       stays where it was placed (sentry slot) instead of following (minion slot)
     * @param shot         projectile it fires, or null to ram enemies
     * @param shotInterval ticks between shots
     * @param shotSpeed    Terraria shoot speed of its projectile
     * @param speed        top speed in blocks per tick
     * @param range        how far from its owner (or itself, for sentries) it looks for enemies
     */
    public record Spec(boolean sentry, @Nullable Supplier<ProjectileKind> shot, int shotInterval, float shotSpeed, double speed, double range) {
        public static Spec rammer(double speed) {
            return new Spec(false, null, 0, 0.0F, speed, 30.0);
        }

        public static Spec shooter(Supplier<ProjectileKind> shot, int interval, float shotSpeed) {
            return new Spec(false, shot, interval, shotSpeed, 0.6, 30.0);
        }

        public static Spec sentry(Supplier<ProjectileKind> shot, int interval, float shotSpeed) {
            return new Spec(true, shot, interval, shotSpeed, 0.0, 32.0);
        }
    }

    /** Sentries last ten minutes. */
    private static final int SENTRY_LIFE = 10 * 60 * 20;
    private static final int CONTACT_COOLDOWN = 12;

    private final Spec spec;
    private @Nullable UUID ownerId;
    private float damage;
    private float knockback;
    private int shotTimer = 20;
    private int life = SENTRY_LIFE;
    private int order;
    private @Nullable LivingEntity target;
    private final Int2IntOpenHashMap contactCooldowns = new Int2IntOpenHashMap();

    public MinionEntity(EntityType<? extends MinionEntity> type, Level level, Spec spec) {
        super(type, level);
        this.spec = spec;
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    public Spec spec() {
        return spec;
    }

    public boolean isSentry() {
        return spec.sentry();
    }

    public boolean ownedBy(Player player) {
        return player.getUUID().equals(ownerId);
    }

    /** Sets who summoned it and how hard it hits (the staff's summon damage and knockback). */
    public void setup(Player owner, float damage, float knockback) {
        this.ownerId = owner.getUUID();
        this.damage = damage;
        this.knockback = knockback;
    }

    // ------------------------------------------------------------------ never hurt, never saved, never in the way

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return source.is(DamageTypes.GENERIC_KILL) && super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean showsHealthBar() {
        return false;
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void travel(Vec3 input) {
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != level || owner.isSpectator()) {
            discard();
            return;
        }
        contactCooldowns.int2IntEntrySet().removeIf(e -> e.getIntValue() <= tickCount);
        if (tickCount % 10 == 0 || target == null || !target.isAlive()) {
            target = findTarget(level, owner);
        }
        if (spec.sentry()) {
            if (--life <= 0) {
                discard();
                return;
            }
            setDeltaMovement(Vec3.ZERO);
            if (target != null) {
                face(target.position().subtract(position()));
                shoot(level, owner);
            }
            return;
        }
        if (distanceToSqr(owner) > 48 * 48) {
            teleportTo(owner.getX(), owner.getY() + 2, owner.getZ());
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
        if (tickCount % 20 == 0) {
            List<MinionEntity> mine = level.getEntitiesOfClass(MinionEntity.class, owner.getBoundingBox().inflate(64),
                m -> !m.isSentry() && m.ownedBy(owner));
            mine.sort(Comparator.comparingInt(MinionEntity::getId));
            order = Math.max(0, mine.indexOf(this));
        }
        Vec3 motion;
        if (target != null && spec.shot() == null) {
            // ram the enemy, overshooting a little like Terraria's minions
            Vec3 toward = target.getBoundingBox().getCenter().subtract(position().add(0, getBbHeight() * 0.5, 0));
            motion = getDeltaMovement().scale(0.92).add(toward.normalize().scale(spec.speed() * 0.22));
            if (motion.length() > spec.speed() * 1.6) {
                motion = motion.normalize().scale(spec.speed() * 1.6);
            }
        } else {
            Vec3 goal;
            if (target != null) {
                Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                goal = target.position().add(away.scale(5)).add(0, 3, 0);
            } else {
                goal = idleSpot(owner);
            }
            Vec3 toGoal = goal.subtract(position());
            motion = toGoal.scale(0.12);
            double max = Math.max(0.3, spec.speed() * 1.2);
            if (motion.length() > max) {
                motion = motion.normalize().scale(max);
            }
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        face(target != null ? target.position().subtract(position()) : motion);
        if (spec.shot() == null) {
            ram(level, owner);
        } else if (target != null) {
            shoot(level, owner);
        }
    }

    /** Where an idle minion floats: behind its owner's shoulder, one after another. */
    private Vec3 idleSpot(Player owner) {
        Vec3 back = owner.getLookAngle().multiply(1, 0, 1);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : back.normalize().scale(-1);
        Vec3 side = new Vec3(-back.z, 0, back.x);
        int row = order / 2;
        double sign = order % 2 == 0 ? 1 : -1;
        return owner.position().add(back.scale(1.2 + row * 0.9)).add(side.scale(sign * (0.8 + row * 0.3)))
            .add(0, 1.6 + Mth.sin((tickCount + getId() * 7) * 0.08F) * 0.25, 0);
    }

    private @Nullable LivingEntity findTarget(ServerLevel level, Player owner) {
        LivingEntity focus = WhipTags.focus(owner);
        if (focus != null && focus.distanceToSqr(owner) < spec.range() * spec.range() * 1.5) {
            return focus;
        }
        Vec3 center = spec.sentry() ? position() : owner.position();
        LivingEntity best = null;
        double bestDistance = spec.range() * spec.range();
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(spec.range() + 8),
            e -> e instanceof Enemy && e.isAlive() && !(e instanceof TargetRules.FriendlyToPlayers) && !e.isInvulnerable())) {
            double distance = candidate.distanceToSqr(center);
            if (distance < bestDistance && (!spec.sentry() || hasLineOfSight(candidate))) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void ram(ServerLevel level, ServerPlayer owner) {
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4),
            e -> e instanceof Enemy && e.isAlive() && !(e instanceof TargetRules.FriendlyToPlayers) && !contactCooldowns.containsKey(e.getId()))) {
            TerraHit hit = new TerraHit(DamageClass.SUMMON, 0, 0, knockback, false);
            victim.invulnerableTime = 0;
            float amount = damage + WhipTags.bonus(victim, ownerId);
            if (victim.hurtServer(level, TerraDamageTypes.source(level, TerraDamageTypes.PROJECTILE, this, owner, hit), amount) && knockback > 0) {
                Vec3 push = victim.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() > 1.0E-4) {
                    victim.push(push.normalize().scale(knockback * 0.05));
                }
            }
            contactCooldowns.put(victim.getId(), tickCount + CONTACT_COOLDOWN);
        }
    }

    private void shoot(ServerLevel level, ServerPlayer owner) {
        if (--shotTimer > 0 || target == null || spec.shot() == null) {
            return;
        }
        shotTimer = spec.shotInterval();
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 aim = target.getBoundingBox().getCenter().subtract(from).normalize();
        TerrariaProjectile.shoot(level, owner, spec.shot().get(), from, aim, spec.shotSpeed(), 0.0F, damage, DamageClass.SUMMON, 0, knockback);
    }

    private void face(Vec3 look) {
        if (look.horizontalDistanceSqr() < 1.0E-4) {
            return;
        }
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }
}

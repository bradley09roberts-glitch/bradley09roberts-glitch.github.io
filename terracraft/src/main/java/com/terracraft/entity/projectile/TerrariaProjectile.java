package com.terracraft.entity.projectile;

import com.terracraft.combat.DamageCalc;
import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerraDamageSource;
import com.terracraft.combat.TerraDamageTypes;
import com.terracraft.combat.TerraHit;
import com.terracraft.registry.ModEntities;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The single projectile entity behind every TerraCraft arrow, bullet, thrown weapon, spell and enemy shot.
 * Behaviour comes from its {@link ProjectileKind}; per-shot numbers (damage, class, crit, knockback) are set
 * by whoever fires it.
 */
public class TerrariaProjectile extends Projectile {
    private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(TerrariaProjectile.class, EntityDataSerializers.STRING);
    /** Ticks a boomerang flies outward before returning. */
    private static final int BOOMERANG_OUTWARD_TICKS = 18;

    private ProjectileKind cachedKind;
    private float damage;
    private DamageClass damageClass = DamageClass.GENERIC;
    private int critChance;
    private float knockback;
    private int armorPenetration;
    private boolean preScaled;
    private int pierceLeft;
    private int bouncesLeft;
    private int age;
    private boolean returning;
    private LivingEntity homingTarget;
    private final Int2IntOpenHashMap hitCooldowns = new Int2IntOpenHashMap();

    public TerrariaProjectile(EntityType<? extends TerrariaProjectile> type, Level level) {
        super(type, level);
    }

    /**
     * Creates and launches a projectile.
     *
     * @param speed Terraria shoot speed (converted with {@link ProjectileKinds#VELOCITY_SCALE})
     */
    public static TerrariaProjectile shoot(ServerLevel level, LivingEntity owner, ProjectileKind kind, Vec3 from, Vec3 direction,
                                           float speed, float inaccuracy, float damage, DamageClass damageClass,
                                           int critChance, float knockback) {
        TerrariaProjectile projectile = new TerrariaProjectile(ModEntities.PROJECTILE.get(), level);
        projectile.setKind(kind);
        projectile.setOwner(owner);
        projectile.setPos(from.x, from.y, from.z);
        projectile.damage = damage;
        projectile.damageClass = damageClass;
        projectile.critChance = critChance;
        projectile.knockback = knockback;
        projectile.preScaled = !(owner instanceof Player);
        projectile.pierceLeft = kind.pierce();
        projectile.bouncesLeft = kind.bounces();
        projectile.shoot(direction.x, direction.y, direction.z, speed * ProjectileKinds.VELOCITY_SCALE, inaccuracy);
        level.addFreshEntity(projectile);
        return projectile;
    }

    // ------------------------------------------------------------------ kind / data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, ProjectileKinds.WOODEN_ARROW.id().toString());
    }

    public void setKind(ProjectileKind kind) {
        entityData.set(KIND, kind.id().toString());
        cachedKind = kind;
    }

    public ProjectileKind kind() {
        String id = entityData.get(KIND);
        if (cachedKind == null || !cachedKind.id().toString().equals(id)) {
            cachedKind = ProjectileKinds.get(Identifier.parse(id));
        }
        return cachedKind;
    }

    public int age() {
        return age;
    }

    // ------------------------------------------------------------------ simulation

    @Override
    public void tick() {
        ProjectileKind kind = kind();
        age++;
        if (!level().isClientSide()) {
            if (age > kind.lifetime()) {
                discard();
                return;
            }
            hitCooldowns.int2IntEntrySet().removeIf(e -> e.getIntValue() <= age);
            if (kind.homing() > 0.0F) {
                steerTowardsTarget(kind);
            }
        }
        if (kind.behavior() == ProjectileKind.Behavior.BOOMERANG) {
            tickBoomerang();
        } else {
            Vec3 motion = getDeltaMovement();
            setDeltaMovement(motion.x * kind.drag(), (motion.y - kind.gravity()) * kind.drag(), motion.z * kind.drag());
        }

        HitResult hit;
        if (kind.tileCollide()) {
            hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        } else {
            Vec3 from = position();
            Vec3 to = from.add(getDeltaMovement());
            AABB area = getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, from, to, area, this::canHitEntity);
            hit = entityHit != null ? entityHit : BlockHitResult.miss(to, Direction.UP, blockPosition());
        }
        Vec3 next = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : position().add(getDeltaMovement());
        setPos(next);
        updateRotation();
        super.tick();
        if (hit.getType() != HitResult.Type.MISS && isAlive() && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit)) {
            hitTargetOrDeflectSelf(hit);
        }
        if (level().isClientSide()) {
            spawnTrail(kind);
        }
    }

    private void tickBoomerang() {
        Entity owner = getOwner();
        if (!returning && age > BOOMERANG_OUTWARD_TICKS) {
            returning = true;
        }
        if (returning) {
            if (owner == null) {
                if (!level().isClientSide()) {
                    discard();
                }
                return;
            }
            Vec3 toOwner = owner.position().add(0, owner.getBbHeight() * 0.5, 0).subtract(position());
            if (toOwner.lengthSqr() < 1.5 && !level().isClientSide()) {
                discard();
                return;
            }
            double speed = Math.max(0.6, getDeltaMovement().length());
            setDeltaMovement(getDeltaMovement().scale(0.6).add(toOwner.normalize().scale(speed * 0.4)).normalize().scale(speed));
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.97));
        }
    }

    private void steerTowardsTarget(ProjectileKind kind) {
        if (homingTarget == null || !homingTarget.isAlive() || age % 10 == 0) {
            homingTarget = findHomingTarget(kind.homingRange());
        }
        if (homingTarget != null) {
            Vec3 motion = getDeltaMovement();
            double speed = motion.length();
            Vec3 wanted = homingTarget.position().add(0, homingTarget.getBbHeight() * 0.5, 0).subtract(position()).normalize();
            Vec3 steered = motion.normalize().scale(1.0 - kind.homing()).add(wanted.scale(kind.homing())).normalize().scale(speed);
            setDeltaMovement(steered);
        }
    }

    private LivingEntity findHomingTarget(float range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range), this::canHitEntity)) {
            double distance = candidate.distanceToSqr(this);
            if (distance < bestDistance && candidate.hasLineOfSight(this)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void spawnTrail(ProjectileKind kind) {
        if (kind.trail() != null && random.nextInt(2) == 0) {
            ParticleOptions particle = kind.trail().get();
            level().addParticle(particle, getX(), getY() + getBbHeight() * 0.5, getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    // ------------------------------------------------------------------ collisions

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || !super.canHitEntity(entity)) {
            return false;
        }
        if (hitCooldowns.containsKey(entity.getId())) {
            return false;
        }
        Entity owner = getOwner();
        if (owner == null) {
            return true;
        }
        if (entity == owner) {
            return false;
        }
        if (owner instanceof Player) {
            // Player shots never hit players (no PvP by default) or the player's tamed animals.
            return !(entity instanceof Player) && !TargetRules.isFriendlyToPlayers(living);
        }
        // Enemy shots only hit players and friendly town NPCs.
        return entity instanceof Player || TargetRules.isFriendlyToPlayers(living);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel serverLevel) || !(result.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ProjectileKind kind = kind();
        Entity owner = getOwner();
        TerraHit hit = new TerraHit(damageClass, critChance, armorPenetration, knockback, preScaled);
        TerraDamageSource source = TerraDamageTypes.source(serverLevel, kind.damageType(), this, owner, hit);
        if (!(target instanceof Player)) {
            // Terraria uses per-projectile immunity instead of global invulnerability frames.
            target.invulnerableTime = 0;
        }
        boolean hurt = target.hurtServer(serverLevel, source, damage);
        hitCooldowns.put(target.getId(), age + kind.hitCooldown());
        if (hurt) {
            applyKnockback(target, source);
            if (kind.igniteTicks() > 0 && random.nextFloat() < kind.igniteChance()) {
                target.igniteForTicks(kind.igniteTicks());
            }
            if (kind.debuff() != null && kind.debuffTicks() > 0 && random.nextFloat() < kind.debuffChance()) {
                Holder<MobEffect> effect = kind.debuff().get();
                if (effect != null) {
                    target.addEffect(new MobEffectInstance(effect, kind.debuffTicks()), owner);
                }
            }
        }
        if (kind.explosionRadius() > 0.0F) {
            explode(serverLevel, kind);
            return;
        }
        if (kind.behavior() == ProjectileKind.Behavior.BOOMERANG) {
            returning = true;
            return;
        }
        if (pierceLeft == 0) {
            discard();
        } else if (pierceLeft > 0) {
            pierceLeft--;
        }
    }

    private void applyKnockback(LivingEntity target, TerraDamageSource source) {
        if (knockback <= 0.0F) {
            return;
        }
        Vec3 motion = getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal < 1.0E-4) {
            return;
        }
        target.knockback(knockback * DamageCalc.KNOCKBACK_SCALE, -motion.x / horizontal, -motion.z / horizontal, source, damage);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        ProjectileKind kind = kind();
        if (kind.behavior() == ProjectileKind.Behavior.BOOMERANG) {
            returning = true;
            return;
        }
        if (bouncesLeft > 0) {
            bouncesLeft--;
            Vec3 motion = getDeltaMovement();
            Direction face = result.getDirection();
            Vec3 bounced = switch (face.getAxis()) {
                case X -> new Vec3(-motion.x, motion.y, motion.z);
                case Y -> new Vec3(motion.x, -motion.y, motion.z);
                case Z -> new Vec3(motion.x, motion.y, -motion.z);
            };
            setDeltaMovement(bounced.scale(0.8));
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            if (kind.explosionRadius() > 0.0F) {
                explode(serverLevel, kind);
            } else {
                discard();
            }
        }
    }

    private void explode(ServerLevel level, ProjectileKind kind) {
        float radius = kind.explosionRadius();
        Entity owner = getOwner();
        TerraHit hit = new TerraHit(damageClass, critChance, armorPenetration, knockback, preScaled);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::canHitEntity)) {
            float falloff = 1.0F - (float) Math.min(1.0, victim.distanceTo(this) / (radius + 1.0F)) * 0.5F;
            victim.invulnerableTime = 0;
            victim.hurtServer(level, TerraDamageTypes.source(level, kind.damageType(), this, owner, hit), damage * falloff);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        discard();
    }

    @Override
    protected void updateRotation() {
        Vec3 motion = getDeltaMovement();
        double horizontal = motion.horizontalDistance();
        setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(motion.y, horizontal) * Mth.RAD_TO_DEG));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** Like vanilla throwables: hidden for the first ticks while still inside the shooter's view. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        if (tickCount < 2 && distance < 12.25) {
            return false;
        }
        return distance < 128.0 * 128.0;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Kind", entityData.get(KIND));
        output.putFloat("Damage", damage);
        output.putString("Class", damageClass.getSerializedName());
        output.putInt("Crit", critChance);
        output.putFloat("Knockback", knockback);
        output.putInt("ArmorPen", armorPenetration);
        output.putBoolean("PreScaled", preScaled);
        output.putInt("Pierce", pierceLeft);
        output.putInt("Bounces", bouncesLeft);
        output.putInt("Age", age);
        output.putBoolean("Returning", returning);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(KIND, input.getStringOr("Kind", ProjectileKinds.WOODEN_ARROW.id().toString()));
        damage = input.getFloatOr("Damage", 0.0F);
        String cls = input.getStringOr("Class", "generic");
        for (DamageClass value : DamageClass.values()) {
            if (value.getSerializedName().equals(cls)) {
                damageClass = value;
            }
        }
        critChance = input.getIntOr("Crit", 0);
        knockback = input.getFloatOr("Knockback", 0.0F);
        armorPenetration = input.getIntOr("ArmorPen", 0);
        preScaled = input.getBooleanOr("PreScaled", false);
        pierceLeft = input.getIntOr("Pierce", 0);
        bouncesLeft = input.getIntOr("Bounces", 0);
        age = input.getIntOr("Age", 0);
        returning = input.getBooleanOr("Returning", false);
    }
}

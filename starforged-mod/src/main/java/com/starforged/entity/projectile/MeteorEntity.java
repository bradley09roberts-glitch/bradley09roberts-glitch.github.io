package com.starforged.entity.projectile;

import com.starforged.StarforgedConfig;
import com.starforged.event.StarfallManager;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.registry.ModTags;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import com.starforged.world.MeteoriteBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A burning star falling from the sky.
 * <ul>
 *     <li>{@link Kind#NATURAL}: Starfall meteors. They blast a crater and leave a meteorite full of Starmetal and crystals.</li>
 *     <li>{@link Kind#HOLLOW}: a rare natural meteor carrying an Astral Egg.</li>
 *     <li>{@link Kind#SUMMONED}: called by the Starcaller Staff; hurts enemies but not terrain or its caller.</li>
 *     <li>{@link Kind#BOSS}: rained down by the Eclipse Sovereign.</li>
 * </ul>
 */
public class MeteorEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);

    public enum Kind {
        NATURAL, HOLLOW, SUMMONED, BOSS;

        static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NATURAL;
        }

        boolean craters() {
            return this == NATURAL || this == HOLLOW;
        }
    }

    private @Nullable UUID ownerId;
    private int age;

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static MeteorEntity launch(ServerLevel level, Vec3 start, Vec3 target, float size, Kind kind, @Nullable Entity owner, float speed) {
        MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR.get(), level);
        meteor.setPos(start.x, start.y, start.z);
        meteor.entityData.set(DATA_SIZE, size);
        meteor.entityData.set(DATA_KIND, kind.ordinal());
        meteor.ownerId = owner == null ? null : owner.getUUID();
        meteor.setDeltaMovement(target.subtract(start).normalize().scale(speed));
        level.addFreshEntity(meteor);
        level.playSound(null, meteor, ModSounds.METEOR_WHOOSH.get(), SoundSource.HOSTILE, 6.0F + size * 2.0F, 0.9F + level.getRandom().nextFloat() * 0.25F - size * 0.1F);
        return meteor;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 1.0F);
        builder.define(DATA_KIND, 0);
    }

    public float size() {
        return this.entityData.get(DATA_SIZE);
    }

    public Kind kind() {
        return Kind.byId(this.entityData.get(DATA_KIND));
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        Vec3 pos = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 next = pos.add(motion);

        if (this.level() instanceof ServerLevel server) {
            if (this.age > 600 || pos.y < server.getMinY()) {
                this.discard();
                return;
            }
            BlockHitResult blockHit = server.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            Vec3 end = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
            Entity owner = this.owner(server);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this, pos, end, this.getBoundingBox().expandTowards(motion).inflate(0.6 * this.size()),
                e -> e instanceof LivingEntity living && e.isPickable() && !e.isSpectator() && (owner == null || Combat.canHit(server, owner, living)),
                motion.lengthSqr() * 4.0);
            if (entityHit != null) {
                this.impact(server, entityHit.getLocation(), BlockPos.containing(entityHit.getLocation()));
                return;
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                this.impact(server, blockHit.getLocation(), blockHit.getBlockPos());
                return;
            }
        }
        this.setPos(next.x, next.y, next.z);

        if (this.level().isClientSide()) {
            this.spawnTrail(pos, motion);
        }
    }

    private void spawnTrail(Vec3 pos, Vec3 motion) {
        float size = this.size();
        Level level = this.level();
        int count = 2 + (int) (size * 3);
        for (int i = 0; i < count; i++) {
            double f = this.random.nextDouble();
            double x = pos.x - motion.x * f + (this.random.nextDouble() - 0.5) * size * 0.8;
            double y = pos.y + 0.5 * size - motion.y * f + (this.random.nextDouble() - 0.5) * size * 0.8;
            double z = pos.z - motion.z * f + (this.random.nextDouble() - 0.5) * size * 0.8;
            level.addAlwaysVisibleParticle(ModParticles.METEOR_EMBER.get(), x, y, z, -motion.x * 0.05, -motion.y * 0.05, -motion.z * 0.05);
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, x, y, z, (this.random.nextDouble() - 0.5) * 0.05, 0.02, (this.random.nextDouble() - 0.5) * 0.05);
        }
        level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, pos.x - motion.x * 0.8, pos.y + 0.5 * size - motion.y * 0.8, pos.z - motion.z * 0.8, 0.0, 0.02, 0.0);
        if (this.random.nextInt(2) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x - motion.x * 1.5, pos.y + 0.5 * size - motion.y * 1.5, pos.z - motion.z * 1.5, 0.0, 0.01, 0.0);
        }
        if (this.random.nextInt(3) == 0) {
            level.addAlwaysVisibleParticle(ModParticles.STAR_SPARKLE.get(), pos.x, pos.y + 0.5 * size, pos.z, (this.random.nextDouble() - 0.5) * 0.2, 0.05, (this.random.nextDouble() - 0.5) * 0.2);
        }
        if (this.kind() == Kind.HOLLOW) {
            level.addAlwaysVisibleParticle(ModParticles.ASTRAL_GLINT.get(), pos.x, pos.y + 0.5 * size, pos.z, 0.0, 0.0, 0.0);
        }
    }

    private @Nullable Entity owner(ServerLevel level) {
        return this.ownerId == null ? null : level.getEntity(this.ownerId);
    }

    private void impact(ServerLevel level, Vec3 at, BlockPos hitPos) {
        Kind kind = this.kind();
        float size = this.size();
        Entity owner = this.owner(level);
        DamageSource source = ModDamageTypes.source(level, ModDamageTypes.METEOR, this, owner);
        float power = 1.6F + size * 1.5F;

        // Blast (no block damage from the explosion itself; natural meteors carve their own crater below).
        ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                if (!(entity instanceof LivingEntity living)) {
                    return entity instanceof ItemEntity ? false : super.shouldDamageEntity(explosion, entity);
                }
                return owner == null || Combat.canHit(level, owner, living);
            }

            @Override
            public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
                return super.getEntityDamageAmount(explosion, entity, exposure) * (kind == Kind.BOSS ? 0.55F : 0.8F);
            }
        };
        level.explode(this, source, calculator, at.x, at.y, at.z, power, false, Level.ExplosionInteraction.NONE);

        BlockPos impact = hitPos;
        if (kind.craters() && StarforgedConfig.METEOR_CRATERS.get()) {
            float craterRadius = 2.6F + size * 1.8F;
            BlockPos floor = MeteoriteBuilder.carveCrater(level, impact, craterRadius, this.random, true, Block.UPDATE_ALL);
            MeteoriteBuilder.buildMeteorite(level, floor, 1.0F + size * 0.75F, this.random, kind == Kind.HOLLOW ? 0.18F : 0.3F, Block.UPDATE_ALL);
            impact = floor;
        }

        Vec3 center = Vec3.atCenterOf(impact);
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, at, 1 + (int) size, 0.5, 0.0);
        Fx.sphere(level, ModParticles.METEOR_EMBER.get(), at, 0.5, (int) (50 * size), 0.55 * size);
        Fx.sphere(level, ModParticles.STAR_SPARKLE.get(), at, 0.8, (int) (30 * size), 0.4 * size);
        Fx.ring(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 1.0, 24, 0.25 * size, 0.05);
        Fx.burst(level, ParticleTypes.LAVA, at, (int) (12 * size), 0.6, 0.0);
        level.playSound(null, at.x, at.y, at.z, ModSounds.METEOR_IMPACT.get(), SoundSource.HOSTILE, 5.0F + size * 3.0F,
            1.0F - size * 0.15F + this.random.nextFloat() * 0.1F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
        Fx.shake(level, at, 40.0 + size * 20.0, 0.8F + size * 0.6F, 16);

        if (kind.craters()) {
            StarfallManager.markImpact(level, center, kind == Kind.HOLLOW);
            this.spawnPassengers(level, center, kind);
        }
        this.discard();
    }

    /** Natural meteors sometimes carry stowaways. */
    private void spawnPassengers(ServerLevel level, Vec3 at, Kind kind) {
        if (kind == Kind.HOLLOW) {
            ItemEntity egg = new ItemEntity(level, at.x, at.y + 1.0, at.z, new ItemStack(ModItems.ASTRAL_EGG.get()));
            egg.setUnlimitedLifetime();
            egg.setGlowingTag(true);
            egg.setInvulnerable(true);
            egg.setDeltaMovement(0, 0.35, 0);
            level.addFreshEntity(egg);
            if (this.random.nextFloat() < 0.5F) {
                Mob starling = ModEntities.STARLING.get().create(level, EntitySpawnReason.EVENT);
                if (starling != null) {
                    starling.snapTo(at.x, at.y + 2.0, at.z, this.random.nextFloat() * 360F, 0);
                    level.addFreshEntity(starling);
                }
            }
            return;
        }
        float roll = this.random.nextFloat();
        if (roll < 0.45F) {
            int mites = 2 + this.random.nextInt(3);
            for (int i = 0; i < mites; i++) {
                spawnNear(level, ModEntities.STAR_MITE.get(), at, 2.5);
            }
        } else if (roll < 0.55F && level.isDarkOutside()) {
            spawnNear(level, ModEntities.VOID_STALKER.get(), at, 3.0);
        } else if (roll < 0.6F && this.size() > 1.5F) {
            spawnNear(level, ModEntities.ASTRAL_GOLEM.get(), at, 2.0);
        }
    }

    private void spawnNear(ServerLevel level, EntityType<? extends Mob> type, Vec3 at, double spread) {
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return;
        }
        double x = at.x + (this.random.nextDouble() - 0.5) * spread * 2;
        double z = at.z + (this.random.nextDouble() - 0.5) * spread * 2;
        mob.snapTo(x, at.y + 1.0, z, this.random.nextFloat() * 360F, 0);
        Optional.ofNullable(level.getNearestPlayer(mob, 48.0)).ifPresent(p -> {
            if (!p.isCreative() && !p.isSpectator()) {
                mob.setTarget(p);
            }
        });
        level.addFreshEntity(mob);
        level.sendParticles(ModParticles.STAR_SPARKLE.get(), mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 320 * 320;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(DATA_SIZE, input.getFloatOr("size", 1.0F));
        this.entityData.set(DATA_KIND, input.getIntOr("kind", 0));
        this.age = input.getIntOr("age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("size", this.size());
        output.putInt("kind", this.entityData.get(DATA_KIND));
        output.putInt("age", this.age);
    }

    public boolean isAlliedToSovereign() {
        return this.is(ModTags.SOVEREIGN_ALLIES);
    }
}

package com.terracraft.entity.boss;

import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Wall of Flesh, guardian of the Underworld and gatekeeper of Hardmode.
 * <p>
 * Terraria's wall spans the whole height of the (2D) Underworld and crawls sideways toward the player. Here it is
 * a vertical wall of flesh {@value #HALF_WIDTH} blocks to each side of its mouth, moving along one horizontal axis
 * (its facing) through terrain. The mouth tracks the player sideways and vertically; touching the wall hurts,
 * and falling behind it drags the player back ("The Tongued"). Two Eyes (sharing the wall's life) fire lasers,
 * faster as the wall weakens, and the mouth spits out The Hungry, mouths tethered to the wall. It speeds up
 * as its life drops. Killing it starts Hardmode.
 */
public class WallOfFlesh extends TerrariaBoss {
    public static final double HALF_WIDTH = 18.0;
    public static final double HALF_HEIGHT = 10.0;
    private static final EntityDataAccessor<Integer> DATA_FACING = SynchedEntityData.defineId(WallOfFlesh.class, EntityDataSerializers.INT);

    private boolean partsSpawned;
    private boolean facingChosen;
    private int hungryTimer = 100;

    public WallOfFlesh(EntityType<? extends WallOfFlesh> type, Level level) {
        super(type, level, BossEvent.BossBarColor.PURPLE);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FACING, Direction.EAST.get2DDataValue());
    }

    /** Direction the wall crawls in. */
    public Direction facing() {
        return Direction.from2DDataValue(entityData.get(DATA_FACING));
    }

    public void setFacing(Direction direction) {
        facingChosen = true;
        entityData.set(DATA_FACING, direction.get2DDataValue());
        setYRot(direction.toYRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    protected ProgressionFlag defeatFlag() {
        return ProgressionFlags.WALL_OF_FLESH;
    }

    @Override
    protected void registerGoals() {
    }

    private double speed() {
        float life = getHealth() / getMaxHealth();
        double base = 0.09 + (1.0 - life) * 0.12;
        return TerrariaDifficulty.isExpert(level()) ? base * 1.2 : base;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!partsSpawned) {
            partsSpawned = true;
            for (int i = 0; i < 2; i++) {
                Eye eye = MobContent.WALL_OF_FLESH_EYE.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (eye != null) {
                    eye.attach(this, i == 0 ? 1 : -1);
                    eye.snapTo(getX(), getY(), getZ(), getYRot(), 0.0F);
                    eye.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                    level.addFreshEntity(eye);
                }
            }
        }
        Player target = findTarget(level);
        if (!facingChosen && target != null) {
            // crawl toward the player along the dominant horizontal axis
            Vec3 toward = target.position().subtract(position());
            setFacing(Math.abs(toward.x) > Math.abs(toward.z) ? (toward.x > 0 ? Direction.EAST : Direction.WEST)
                : (toward.z > 0 ? Direction.SOUTH : Direction.NORTH));
        }
        Direction facing = facing();
        Vec3 forward = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 motion = forward.scale(speed());
        if (target != null && !isLeaving(level)) {
            setTarget(target);
            // the mouth slides along the wall and up/down to stay level with the player
            Vec3 offset = target.position().subtract(position());
            double lateral = offset.dot(side);
            double vertical = target.getY() - getY();
            motion = motion.add(side.scale(Mth.clamp(lateral * 0.05, -0.15, 0.15))).add(0, Mth.clamp(vertical * 0.05, -0.15, 0.15), 0);
            wallContact(level, target, forward, side);
            if (--hungryTimer <= 0) {
                hungryTimer = getHealth() < getMaxHealth() * 0.5F ? 140 : 220;
                spawnHungry(level, forward);
            }
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        setYRot(facing.toYRot());
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Touching the wall hurts; a player left behind it is dragged back through it. */
    private void wallContact(ServerLevel level, Player player, Vec3 forward, Vec3 side) {
        Vec3 offset = player.position().subtract(position());
        double along = offset.dot(forward);
        double lateral = Math.abs(offset.dot(side));
        double vertical = Math.abs(player.getY() + player.getBbHeight() / 2 - getY() - getBbHeight() / 2);
        if (lateral > HALF_WIDTH || vertical > HALF_HEIGHT + 2) {
            return;
        }
        if (along < 1.2 && along > -1.5) {
            hurtTarget(level, player, contactDamage());
        } else if (along <= -1.5) {
            // "The Tongued": behind the wall
            player.setDeltaMovement(player.getDeltaMovement().add(forward.scale(0.4)));
            player.hurtMarked = true;
            hurtTarget(level, player, contactDamage());
        }
    }

    private void spawnHungry(ServerLevel level, Vec3 forward) {
        long alive = level.getEntitiesOfClass(Hungry.class, getBoundingBox().inflate(48), h -> getUUID().equals(h.wallId)).size();
        if (alive >= 6) {
            return;
        }
        Hungry hungry = MobContent.THE_HUNGRY.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (hungry != null) {
            hungry.wallId = getUUID();
            hungry.snapTo(getX() + forward.x * 2, getY() + getBbHeight() / 2, getZ() + forward.z * 2, getYRot(), 0.0F);
            hungry.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            hungry.setTarget(getTarget());
            level.addFreshEntity(hungry);
            playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 1.4F);
        }
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void die(DamageSource source) {
        // the wall usually dies over the lava sea: hand the loot to the nearest player instead of burning it
        if (level() instanceof ServerLevel level) {
            Player nearest = level.getNearestPlayer(this, 64.0);
            if (nearest != null) {
                setPos(nearest.getX(), nearest.getY() + 0.5, nearest.getZ());
            }
        }
        super.die(source);
        if (level() instanceof ServerLevel level && !isNoAi()) {
            ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.HARDMODE);
            removeParts(level);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (reason == RemovalReason.DISCARDED && level() instanceof ServerLevel level) {
            removeParts(level);
        }
    }

    private void removeParts(ServerLevel level) {
        for (TerrariaMob part : level.getEntitiesOfClass(TerrariaMob.class, getBoundingBox().inflate(64),
            e -> e instanceof Eye eye && getUUID().equals(eye.wallId) || e instanceof Hungry hungry && getUUID().equals(hungry.wallId))) {
            part.discard();
        }
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Facing", facing().get2DDataValue());
        output.putBoolean("PartsSpawned", partsSpawned);
        output.putBoolean("FacingChosen", facingChosen);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setFacing(Direction.from2DDataValue(input.getIntOr("Facing", Direction.EAST.get2DDataValue())));
        partsSpawned = input.getBooleanOr("PartsSpawned", false);
        facingChosen = input.getBooleanOr("FacingChosen", false);
    }

    // ------------------------------------------------------------------ parts

    /** One of the wall's two eyes: rides on the wall, fires lasers and passes any damage on to the wall. */
    public static class Eye extends TerrariaMob {
        private UUID wallId;
        private int side = 1;
        private int laserTimer = 60;

        public Eye(EntityType<? extends Eye> type, Level level) {
            super(type, level);
            setNoGravity(true);
            noPhysics = true;
        }

        void attach(WallOfFlesh wall, int side) {
            this.wallId = wall.getUUID();
            this.side = side;
            this.laserTimer = side > 0 ? 40 : 80;
        }

        private WallOfFlesh wall(ServerLevel level) {
            return wallId != null && level.getEntity(wallId) instanceof WallOfFlesh wall && wall.isAlive() ? wall : null;
        }

        @Override
        protected void registerGoals() {
        }

        @Override
        public boolean countsTowardSpawnCap() {
            return false;
        }

        @Override
        public boolean showsHealthBar() {
            return false;
        }

        @Override
        public void travel(Vec3 input) {
        }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            WallOfFlesh wall = wall(level);
            if (wall == null || source.is(DamageTypes.IN_WALL)) {
                return false;
            }
            // shared life: the wall takes the hit (its own defense and life scale apply)
            return wall.hurtServer(level, source, amount);
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            super.customServerAiStep(level);
            WallOfFlesh wall = wall(level);
            if (wall == null) {
                discard();
                return;
            }
            Direction facing = wall.facing();
            Vec3 forward = new Vec3(facing.getStepX(), 0, facing.getStepZ());
            Vec3 sideways = new Vec3(-forward.z, 0, forward.x);
            Vec3 spot = wall.position().add(sideways.scale(side * 6.0)).add(0, side > 0 ? 6.0 : -3.0, 0).add(forward.scale(0.5));
            setPos(spot.x, spot.y, spot.z);
            LivingEntity target = wall.getTarget();
            if (target != null) {
                Vec3 look = target.getEyePosition().subtract(getEyePosition());
                setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F);
                setXRot((float) -(Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG));
                yBodyRot = getYRot();
                yHeadRot = getYRot();
                if (--laserTimer <= 0) {
                    float life = wall.getHealth() / wall.getMaxHealth();
                    laserTimer = (int) (30 + 50 * life);
                    Vec3 from = getEyePosition();
                    float damage = 11.0F * TerrariaDifficulty.enemyDamageMultiplier(level);
                    TerrariaProjectile.shoot(level, this, ProjectileKinds.WOF_LASER, from, look.normalize(), 12.0F, 0.5F, damage, DamageClass.GENERIC, 0, 1.0F);
                    playSound(SoundEvents.GUARDIAN_ATTACK, 1.5F, 1.6F);
                }
            }
        }

        @Override
        public boolean removeWhenFarAway(double distanceSq) {
            return false;
        }

        @Override
        protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            if (wallId != null) {
                output.store("Wall", UUIDUtil.CODEC, wallId);
            }
            output.putInt("Side", side);
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            wallId = input.read("Wall", UUIDUtil.CODEC).orElse(null);
            side = input.getIntOr("Side", 1);
        }
    }

    /** The Hungry: a fanged mouth on a fleshy tether that lunges at players but cannot stray far from the wall. */
    public static class Hungry extends TerrariaMob {
        private UUID wallId;

        public Hungry(EntityType<? extends Hungry> type, Level level) {
            super(type, level);
            setNoGravity(true);
            noPhysics = true;
        }

        @Override
        protected void registerGoals() {
        }

        @Override
        public boolean countsTowardSpawnCap() {
            return false;
        }

        @Override
        public void travel(Vec3 input) {
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            super.customServerAiStep(level);
            if (wallId == null || !(level.getEntity(wallId) instanceof WallOfFlesh wall) || !wall.isAlive()) {
                discard();
                return;
            }
            LivingEntity target = wall.getTarget();
            Vec3 motion = getDeltaMovement().scale(0.9);
            if (target != null) {
                motion = motion.add(target.getEyePosition().subtract(position()).normalize().scale(0.05));
                Vec3 look = target.getEyePosition().subtract(position());
                setYRot((float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F);
                yBodyRot = getYRot();
            }
            // tether: never more than 10 blocks from the wall
            Vec3 toWall = wall.position().add(0, wall.getBbHeight() / 2, 0).subtract(position());
            if (toWall.length() > 10.0) {
                motion = motion.add(toWall.normalize().scale(0.12));
            }
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
        }

        @Override
        protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
            super.addAdditionalSaveData(output);
            if (wallId != null) {
                output.store("Wall", UUIDUtil.CODEC, wallId);
            }
        }

        @Override
        protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            wallId = input.read("Wall", UUIDUtil.CODEC).orElse(null);
        }
    }
}

package com.starforged.sun.boss;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunEntities;
import com.starforged.sun.SunFx;
import com.starforged.sun.SunItems;
import com.starforged.sun.SunSounds;
import com.starforged.sun.entity.SolarFlareEntity;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * THE SUN WARDEN - Last Light of the Sky.
 * <p>
 * A colossal knight of gold and sunstone with a blazing solar disc for a halo, sworn to guard the Sun. Attacks:
 * <ul>
 *     <li><b>Sunfall</b> - raises its blade and calls burning flares down from the sky.</li>
 *     <li><b>Solar Lance</b> - its halo focuses into a sweeping beam of sunlight.</li>
 *     <li><b>Flame Pillars</b> - the ground glows, then erupts in columns of fire (move!).</li>
 *     <li><b>Molten Slam</b> - leaps and crashes down, sending out shockwave rings (jump them!).</li>
 *     <li><b>Blade Sweep</b> - a huge burning sword arc at close range.</li>
 *     <li><b>Call the Pack</b> - summons Cinder Imps and Ember Hounds.</li>
 *     <li><b>Corona</b> (enraged) - erupts in a ring of phoenix flares.</li>
 * </ul>
 * At half health it kneels and begins a <b>Supernova</b>: four Solar Pylons rise and shield it. Shatter them all
 * before the countdown ends to stun it - fail, and the Supernova scorches the whole arena.
 */
public class SunWardenEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(SunWardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(SunWardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_BEAM_END = SynchedEntityData.defineId(SunWardenEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_RISE = SynchedEntityData.defineId(SunWardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NOVA = SynchedEntityData.defineId(SunWardenEntity.class, EntityDataSerializers.INT);

    public static final int PHASE_RISING = 0;
    public static final int PHASE_FIGHT = 1;
    public static final int PHASE_SUPERNOVA = 2;
    public static final int PHASE_STUNNED = 3;
    public static final int PHASE_ENRAGED = 4;

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_SUNFALL = 1;
    public static final int ATTACK_BEAM = 2;
    public static final int ATTACK_PILLARS = 3;
    public static final int ATTACK_SLAM = 4;
    public static final int ATTACK_SUMMON = 5;
    public static final int ATTACK_SWEEP = 6;
    public static final int ATTACK_CORONA = 7;

    public static final int RISE_TICKS = 100;
    public static final int BEAM_CHARGE = 30;
    public static final int BEAM_END = 95;
    public static final int NOVA_TICKS = 600;
    public static final int SWEEP_HIT = 16;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
        Component.translatable("entity.starforged.sun_warden").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
        BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20);

    private BlockPos home = BlockPos.ZERO;
    private int attackTicks;
    private int attackCooldown = 40;
    private int stunTicks;
    private int lastAttack = ATTACK_NONE;
    private int idleNoPlayers;
    private Vec3 beamPoint = Vec3.ZERO;
    private Vec3 slamTarget = Vec3.ZERO;
    private boolean novaStarted;
    private final List<Integer> pylons = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<Pillar> pillars = new ArrayList<>();
    private @Nullable DamageSource deathSource;

    public int clientAttackTicks;
    public int clientPhaseTicks;

    private static final class Shockwave {
        final Vec3 center;
        double radius = 1.0;
        final double maxRadius;
        final float damage;
        final Set<Integer> hit = new HashSet<>();

        Shockwave(Vec3 center, double maxRadius, float damage) {
            this.center = center;
            this.maxRadius = maxRadius;
            this.damage = damage;
        }
    }

    private static final class Pillar {
        final Vec3 at;
        int delay;
        int burn = 10;

        Pillar(Vec3 at, int delay) {
            this.at = at;
            this.delay = delay;
        }
    }

    public SunWardenEntity(EntityType<? extends SunWardenEntity> type, Level level) {
        super(type, level);
        this.xpReward = 800;
        this.setPersistenceRequired();
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 900.0)
            .add(Attributes.ARMOR, 16.0)
            .add(Attributes.ARMOR_TOUGHNESS, 8.0)
            .add(Attributes.ATTACK_DAMAGE, 18.0)
            .add(Attributes.FOLLOW_RANGE, 80.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.27)
            .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, PHASE_RISING);
        builder.define(DATA_ATTACK, ATTACK_NONE);
        builder.define(DATA_BEAM_END, new Vector3f());
        builder.define(DATA_RISE, RISE_TICKS);
        builder.define(DATA_NOVA, NOVA_TICKS);
    }

    @Override
    protected void registerGoals() {
        // Driven by the state machine in customServerAiStep.
    }

    // --- Accessors -------------------------------------------------------------------------------------------------

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public int attack() {
        return this.entityData.get(DATA_ATTACK);
    }

    public int riseTicks() {
        return this.entityData.get(DATA_RISE);
    }

    public int novaTicks() {
        return this.entityData.get(DATA_NOVA);
    }

    public Vec3 beamEnd() {
        Vector3fc v = this.entityData.get(DATA_BEAM_END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** The centre of the solar halo behind the Warden's head, where beams fire from. */
    public Vec3 haloPosition() {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return this.position().add(Mth.sin(yaw) * 0.9, 5.7, -Mth.cos(yaw) * 0.9);
    }

    public void setHome(BlockPos home) {
        this.home = home;
    }

    private void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
    }

    private void setAttack(int attack) {
        this.entityData.set(DATA_ATTACK, attack);
        this.attackTicks = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_ATTACK.equals(accessor)) {
            this.clientAttackTicks = 0;
        } else if (DATA_PHASE.equals(accessor)) {
            this.clientPhaseTicks = 0;
        }
    }

    // --- Tick ------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        this.noPhysics = this.phase() == PHASE_RISING;
        super.tick();
        if (this.level().isClientSide()) {
            this.clientAttackTicks++;
            this.clientPhaseTicks++;
            this.clientParticles();
        }
    }

    private void clientParticles() {
        Level level = this.level();
        Vec3 halo = this.haloPosition();
        if (this.random.nextInt(2) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2;
            level.addParticle(ModParticles.SOLAR_SPARK.get(), halo.x + Math.cos(a) * 1.6, halo.y + Math.sin(a) * 1.6, halo.z, 0.0, 0.02, 0.0);
        }
        if (this.random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FLAME, this.getX() + (this.random.nextDouble() - 0.5) * 2.0, this.getY() + this.random.nextDouble() * 4.0,
                this.getZ() + (this.random.nextDouble() - 0.5) * 2.0, 0.0, 0.02, 0.0);
        }
        if (this.phase() == PHASE_SUPERNOVA) {
            for (int i = 0; i < 2; i++) {
                Vec3 off = new Vec3(this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5).normalize().scale(5.0);
                level.addParticle(ModParticles.SOLAR_SPARK.get(), halo.x + off.x, halo.y + off.y, halo.z + off.z, -off.x * 0.08, -off.y * 0.08, -off.z * 0.08);
            }
        }
        if (this.attack() == ATTACK_BEAM && this.clientAttackTicks < BEAM_CHARGE) {
            for (int i = 0; i < 3; i++) {
                Vec3 off = new Vec3(this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5).normalize().scale(3.0);
                level.addParticle(ParticleTypes.FLAME, halo.x + off.x, halo.y + off.y, halo.z + off.z, -off.x * 0.1, -off.y * 0.1, -off.z * 0.1);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.home.equals(BlockPos.ZERO)) {
            this.home = this.blockPosition();
        }
        Player nearest = level.getNearestPlayer(this, 96.0);
        if (nearest == null) {
            if (++this.idleNoPlayers > 1200) {
                this.retreat(level);
                return;
            }
        } else {
            this.idleNoPlayers = 0;
        }
        this.tickShockwaves(level);
        this.tickPillars(level);
        switch (this.phase()) {
            case PHASE_RISING -> this.tickRising(level);
            case PHASE_SUPERNOVA -> this.tickSupernova(level);
            case PHASE_STUNNED -> this.tickStunned(level);
            default -> this.tickCombat(level);
        }
    }

    private void tickRising(ServerLevel level) {
        int remaining = this.riseTicks();
        double targetY = this.home.getY() + 1.0;
        double y = targetY - 6.5 * (remaining / (double) RISE_TICKS);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.home.getX() + 0.5, y, this.home.getZ() + 0.5);
        BlockState ground = level.getBlockState(this.home);
        if (remaining % 2 == 0 && !ground.isAir()) {
            Fx.ring(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), Vec3.atBottomCenterOf(this.home.above()), 2.2, 16, 0.25, 0.35);
        }
        if (remaining % 10 == 0) {
            Fx.shake(level, this.position(), 48.0, 0.6F, 12);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        Player nearest = level.getNearestPlayer(this, 64.0);
        if (nearest != null) {
            this.lookAtTarget(nearest, 30.0F);
        }
        if (remaining <= 0) {
            this.setPhase(PHASE_FIGHT);
            this.roar(level);
            Vec3 center = this.position();
            for (LivingEntity target : Combat.targetsAround(level, this, center, 12.0)) {
                Combat.blast(target, center, 1.4, 0.6);
            }
            Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), this.haloPosition(), 1.0, 90, 0.9);
            Fx.ring(level, ParticleTypes.FLAME, center.add(0, 0.3, 0), 1.0, 60, 0.6, 0.0);
            Fx.shake(level, center, 64.0, 2.0F, 30);
            SunFx.titleNear(level, center, 128.0, Component.translatable("entity.starforged.sun_warden").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("entity.starforged.sun_warden.title").withStyle(ChatFormatting.YELLOW), 10, 60, 20);
            this.attackCooldown = 30;
        } else {
            this.entityData.set(DATA_RISE, remaining - 1);
        }
    }

    private void roar(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
    }

    private void tickStunned(ServerLevel level) {
        this.getNavigation().stop();
        if (this.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 5.5, this.getZ(), 4, 0.6, 0.2, 0.6, 0.02);
        }
        if (--this.stunTicks <= 0) {
            this.enrage(level);
        }
    }

    private void enrage(ServerLevel level) {
        this.setPhase(PHASE_ENRAGED);
        this.bossEvent.setColor(BossEvent.BossBarColor.RED);
        this.roar(level);
        this.broadcast(level, Component.translatable("entity.starforged.sun_warden.enraged").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), this.haloPosition(), 1.0, 120, 1.1);
        Fx.shake(level, this.position(), 64.0, 1.6F, 25);
        this.attackCooldown = 20;
        this.setAttack(ATTACK_NONE);
    }

    private void tickCombat(ServerLevel level) {
        LivingEntity target = this.findTarget(level);
        this.setTarget(target);
        boolean enraged = this.phase() == PHASE_ENRAGED;
        if (enraged && this.tickCount % 10 == 0) {
            for (LivingEntity victim : Combat.targetsAround(level, this, this.position().add(0, 1, 0), 3.5)) {
                victim.igniteForSeconds(3.0F);
            }
        }
        if (this.attack() == ATTACK_NONE) {
            this.stalk(target);
            if (target != null && --this.attackCooldown <= 0) {
                this.chooseAttack(level, target);
            }
            return;
        }
        this.getNavigation().stop();
        this.attackTicks++;
        switch (this.attack()) {
            case ATTACK_SUNFALL -> this.tickSunfall(level, enraged);
            case ATTACK_BEAM -> this.tickBeam(level, target, enraged);
            case ATTACK_PILLARS -> this.tickPillarAttack(level, target, enraged);
            case ATTACK_SLAM -> this.tickSlam(level, target, enraged);
            case ATTACK_SUMMON -> this.tickSummon(level);
            case ATTACK_SWEEP -> this.tickSweep(level, target);
            case ATTACK_CORONA -> this.tickCorona(level);
            default -> this.finishAttack(20);
        }
    }

    private @Nullable LivingEntity findTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) < 80.0) {
            if (this.random.nextInt(240) != 0) {
                return current;
            }
        }
        List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(64.0),
            p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        return players.isEmpty() ? null : players.get(this.random.nextInt(players.size()));
    }

    /** Lumbers toward its target, never straying far from the altar. */
    private void stalk(@Nullable LivingEntity target) {
        if (target == null) {
            if (this.distanceToSqr(Vec3.atCenterOf(this.home)) > 16.0) {
                this.getNavigation().moveTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, 0.8);
            }
            return;
        }
        this.lookAtTarget(target, 6.0F);
        double dist = this.distanceTo(target);
        boolean leashed = target.position().distanceTo(Vec3.atCenterOf(this.home)) > 40.0;
        if (dist > 5.0 && !leashed) {
            this.getNavigation().moveTo(target, 1.0);
        } else {
            this.getNavigation().stop();
        }
    }

    private void lookAtTarget(Entity target, float maxTurn) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, maxTurn));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    private void chooseAttack(ServerLevel level, LivingEntity target) {
        boolean enraged = this.phase() == PHASE_ENRAGED;
        double dist = this.distanceTo(target);
        long minions = level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(48.0),
            m -> m.getType() == SunEntities.CINDER_IMP.get() || m.getType() == SunEntities.EMBER_HOUND.get()).size();
        List<Integer> options = new ArrayList<>();
        options.add(ATTACK_SUNFALL);
        options.add(ATTACK_BEAM);
        options.add(ATTACK_BEAM);
        options.add(ATTACK_PILLARS);
        options.add(ATTACK_PILLARS);
        options.add(ATTACK_SLAM);
        if (dist < 6.0) {
            options.add(ATTACK_SWEEP);
            options.add(ATTACK_SWEEP);
            options.add(ATTACK_SWEEP);
        }
        if (dist > 14.0) {
            options.add(ATTACK_SLAM);
            options.add(ATTACK_SUNFALL);
        }
        if (minions < 5) {
            options.add(ATTACK_SUMMON);
        }
        if (enraged) {
            options.add(ATTACK_CORONA);
            options.add(ATTACK_CORONA);
            options.add(ATTACK_SLAM);
        }
        options.removeIf(a -> a == this.lastAttack && this.random.nextInt(3) != 0);
        int chosen = options.get(this.random.nextInt(options.size()));
        this.lastAttack = chosen;
        this.setAttack(chosen);
        switch (chosen) {
            case ATTACK_BEAM -> {
                this.beamPoint = target.position().add(this.random.nextDouble() * 8 - 4, 0.5, this.random.nextDouble() * 8 - 4);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_BEAM.get(), SoundSource.HOSTILE, 4.0F, 0.8F);
            }
            case ATTACK_SLAM -> this.slamTarget = target.position();
            case ATTACK_SUNFALL -> level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_FLARE.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
            default -> {
            }
        }
    }

    private void finishAttack(int cooldown) {
        this.setAttack(ATTACK_NONE);
        this.entityData.set(DATA_BEAM_END, new Vector3f());
        this.attackCooldown = this.phase() == PHASE_ENRAGED ? cooldown / 2 + 5 : cooldown;
    }

    // --- Attacks ---------------------------------------------------------------------------------------------------

    private void tickSunfall(ServerLevel level, boolean enraged) {
        int interval = enraged ? 3 : 5;
        if (this.attackTicks >= 14 && this.attackTicks <= 60 && this.attackTicks % interval == 0) {
            List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(48.0), p -> !p.isCreative() && !p.isSpectator());
            if (!players.isEmpty()) {
                Player victim = players.get(this.random.nextInt(players.size()));
                Vec3 spot = victim.position().add(this.random.nextGaussian() * 3.5, 0, this.random.nextGaussian() * 3.5);
                Vec3 start = spot.add(this.random.nextGaussian() * 4.0, 30.0 + this.random.nextDouble() * 8.0, this.random.nextGaussian() * 4.0);
                SolarFlareEntity.shoot(level, this, start, spot.subtract(start), 0.6F, 10.0F, SolarFlareEntity.Kind.SUNFALL, null);
            }
            Fx.burst(level, ModParticles.SOLAR_SPARK.get(), this.position().add(0, 8.0, 0), 6, 0.6, 0.15);
        }
        if (this.attackTicks >= 70) {
            this.finishAttack(45);
        }
    }

    private void tickBeam(ServerLevel level, @Nullable LivingEntity target, boolean enraged) {
        Vec3 halo = this.haloPosition();
        if (target != null) {
            this.lookAtTarget(target, 3.0F);
            Vec3 aim = target.position().add(0, 0.8, 0);
            double speed = enraged ? 0.5 : 0.3;
            Vec3 delta = aim.subtract(this.beamPoint);
            if (delta.length() > speed) {
                delta = delta.normalize().scale(speed);
            }
            this.beamPoint = this.beamPoint.add(delta);
        }
        if (this.attackTicks < BEAM_CHARGE) {
            return;
        }
        if (this.attackTicks == BEAM_CHARGE || this.attackTicks % 20 == 0) {
            level.playSound(null, halo.x, halo.y, halo.z, SunSounds.WARDEN_BEAM.get(), SoundSource.HOSTILE, 5.0F, 1.2F);
        }
        Vec3 dir = this.beamPoint.subtract(halo).normalize();
        BlockHitResult hit = level.clip(new ClipContext(halo, halo.add(dir.scale(56.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getLocation();
        this.entityData.set(DATA_BEAM_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        double width = enraged ? 1.6 : 1.2;
        if (this.attackTicks % 4 == 0) {
            AABB box = new AABB(halo, end).inflate(width);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> Combat.canHit(level, this, e))) {
                Vec3 p = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
                double t = Mth.clamp(p.subtract(halo).dot(dir), 0.0, end.distanceTo(halo));
                if (halo.add(dir.scale(t)).distanceTo(p) < width + victim.getBbWidth() * 0.5) {
                    victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SOLAR_BEAM, this), enraged ? 7.0F : 5.0F);
                    victim.igniteForSeconds(4.0F);
                }
            }
        }
        level.sendParticles(ParticleTypes.FLAME, true, true, end.x, end.y, end.z, 4, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ModParticles.SOLAR_SPARK.get(), true, true, end.x, end.y, end.z, 3, 0.3, 0.3, 0.3, 0.1);
        if (this.attackTicks >= BEAM_END) {
            this.finishAttack(55);
        }
    }

    private void tickPillarAttack(ServerLevel level, @Nullable LivingEntity target, boolean enraged) {
        if (this.attackTicks == 10 || this.attackTicks == 30 || (enraged && this.attackTicks == 50)) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_PILLAR.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
            List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(40.0), p -> !p.isCreative() && !p.isSpectator());
            for (Player victim : players) {
                this.pillars.add(new Pillar(this.ground(level, victim.position()), 22));
                for (int i = 0; i < (enraged ? 5 : 3); i++) {
                    Vec3 spot = victim.position().add(this.random.nextGaussian() * 4.0, 0, this.random.nextGaussian() * 4.0);
                    this.pillars.add(new Pillar(this.ground(level, spot), 22 + this.random.nextInt(10)));
                }
            }
        }
        if (this.attackTicks >= (enraged ? 75 : 55)) {
            this.finishAttack(40);
        }
    }

    private Vec3 ground(ServerLevel level, Vec3 near) {
        BlockPos pos = BlockPos.containing(near);
        for (int dy = 2; dy >= -6; dy--) {
            BlockPos below = pos.offset(0, dy - 1, 0);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(pos.offset(0, dy, 0)).isAir()) {
                return Vec3.atBottomCenterOf(pos.offset(0, dy, 0));
            }
        }
        return near;
    }

    private void tickPillars(ServerLevel level) {
        Iterator<Pillar> it = this.pillars.iterator();
        while (it.hasNext()) {
            Pillar pillar = it.next();
            if (pillar.delay > 0) {
                pillar.delay--;
                if (pillar.delay % 3 == 0) {
                    Fx.ring(level, ParticleTypes.FLAME, pillar.at.add(0, 0.1, 0), 1.1, 10, 0.0, 0.01);
                    level.sendParticles(ParticleTypes.SMOKE, pillar.at.x, pillar.at.y + 0.1, pillar.at.z, 2, 0.4, 0.0, 0.4, 0.01);
                }
                if (pillar.delay == 0) {
                    level.playSound(null, pillar.at.x, pillar.at.y, pillar.at.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.6F);
                }
                continue;
            }
            for (int i = 0; i < 8; i++) {
                level.sendParticles(ParticleTypes.FLAME, true, true, pillar.at.x, pillar.at.y + i * 0.7, pillar.at.z, 3, 0.25, 0.2, 0.25, 0.02);
            }
            level.sendParticles(ModParticles.SOLAR_SPARK.get(), pillar.at.x, pillar.at.y + 3, pillar.at.z, 3, 0.3, 1.5, 0.3, 0.05);
            if (pillar.burn % 5 == 0) {
                AABB column = new AABB(pillar.at.x - 1.1, pillar.at.y, pillar.at.z - 1.1, pillar.at.x + 1.1, pillar.at.y + 5.5, pillar.at.z + 1.1);
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, column, e -> Combat.canHit(level, this, e))) {
                    victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SUNFIRE, this), 8.0F);
                    victim.igniteForSeconds(5.0F);
                    victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.6, 0));
                    victim.hurtMarked = true;
                }
            }
            if (--pillar.burn <= 0) {
                it.remove();
            }
        }
    }

    private void tickSlam(ServerLevel level, @Nullable LivingEntity target, boolean enraged) {
        if (this.attackTicks == 8) {
            if (target != null) {
                this.slamTarget = target.position();
            }
            Vec3 jump = this.slamTarget.subtract(this.position()).multiply(1, 0, 1);
            double dist = Math.min(jump.length(), 18.0);
            Vec3 velocity = jump.lengthSqr() > 0.01 ? jump.normalize().scale(dist / 20.0) : Vec3.ZERO;
            this.setDeltaMovement(velocity.x, 1.25, velocity.z);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 3.0F, 1.4F);
            Fx.ring(level, ParticleTypes.CLOUD, this.position().add(0, 0.2, 0), 1.5, 20, 0.3, 0.05);
        }
        if (this.attackTicks > 12 && this.onGround() && this.attackTicks < 60 && this.slamTarget != null) {
            Vec3 ground = this.position();
            for (LivingEntity victim : Combat.targetsAround(level, this, ground.add(0, 1, 0), 4.5)) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, this), 16.0F);
                Combat.blast(victim, ground, 1.2, 0.9);
            }
            this.shockwaves.add(new Shockwave(ground, enraged ? 20.0 : 15.0, enraged ? 10.0F : 8.0F));
            if (enraged) {
                this.shockwaves.add(new Shockwave(ground, 20.0, 10.0F));
                this.shockwaves.get(this.shockwaves.size() - 1).radius = -4.0;
            }
            BlockState below = level.getBlockState(this.blockPosition().below());
            if (!below.isAir()) {
                Fx.ring(level, new BlockParticleOption(ParticleTypes.BLOCK, below), ground.add(0, 0.2, 0), 2.5, 50, 0.4, 0.5);
            }
            Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, ground, 1, 0.0, 0.0);
            level.playSound(null, ground.x, ground.y, ground.z, SunSounds.WARDEN_SLAM.get(), SoundSource.HOSTILE, 5.0F, 0.9F);
            Fx.shake(level, ground, 48.0, 1.8F, 18);
            this.attackTicks = 60;
        }
        if (this.attackTicks >= 75) {
            this.finishAttack(45);
        }
    }

    /** Expanding rings of fire along the ground - jump over them! */
    private void tickShockwaves(ServerLevel level) {
        Iterator<Shockwave> it = this.shockwaves.iterator();
        while (it.hasNext()) {
            Shockwave wave = it.next();
            wave.radius += 0.6;
            if (wave.radius <= 0) {
                continue;
            }
            int points = (int) (wave.radius * 6);
            for (int i = 0; i < points; i++) {
                double a = i / (double) points * Math.PI * 2;
                level.sendParticles(ParticleTypes.FLAME, true, true, wave.center.x + Math.cos(a) * wave.radius, wave.center.y + 0.25,
                    wave.center.z + Math.sin(a) * wave.radius, 1, 0.0, 0.1, 0.0, 0.0);
            }
            for (LivingEntity victim : Combat.targetsAround(level, this, wave.center, wave.radius + 1.0)) {
                double d = victim.position().multiply(1, 0, 1).distanceTo(wave.center.multiply(1, 0, 1));
                boolean grounded = victim.getY() - wave.center.y < 0.9;
                if (Math.abs(d - wave.radius) < 0.9 && grounded && wave.hit.add(victim.getId())) {
                    victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, this), wave.damage);
                    victim.igniteForSeconds(3.0F);
                    Combat.blast(victim, wave.center, 0.8, 0.5);
                }
            }
            if (wave.radius >= wave.maxRadius) {
                it.remove();
            }
        }
    }

    private void tickSweep(ServerLevel level, @Nullable LivingEntity target) {
        if (target != null && this.attackTicks < SWEEP_HIT - 4) {
            this.lookAtTarget(target, 20.0F);
        }
        if (this.attackTicks == SWEEP_HIT) {
            Vec3 facing = Vec3.directionFromRotation(0, this.yBodyRot);
            for (LivingEntity victim : Combat.targetsAround(level, this, this.position().add(facing.scale(2.5)).add(0, 1, 0), 5.0)) {
                Vec3 to = victim.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                if (facing.dot(to) > -0.1) {
                    victim.hurtServer(level, this.damageSources().mobAttack(this), 18.0F);
                    victim.igniteForSeconds(6.0F);
                    Combat.knock(victim, 1.6, to);
                }
            }
            for (int i = -9; i <= 9; i++) {
                double a = Math.toRadians(this.yBodyRot + 90 + i * 10);
                double x = this.getX() - Math.cos(a) * 4.5;
                double z = this.getZ() - Math.sin(a) * 4.5;
                level.sendParticles(ParticleTypes.FLAME, x, this.getY() + 1.5, z, 4, 0.2, 0.3, 0.2, 0.03);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, this.getY() + 1.5, z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0F, 0.5F);
            Fx.shake(level, this.position(), 24.0, 0.6F, 8);
        }
        if (this.attackTicks >= SWEEP_HIT + 14) {
            this.finishAttack(25);
        }
    }

    private void tickSummon(ServerLevel level) {
        if (this.attackTicks == 5) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_ROAR.get(), SoundSource.HOSTILE, 4.0F, 1.2F);
        }
        if (this.attackTicks == 20) {
            boolean hounds = this.random.nextBoolean();
            int count = hounds ? 2 : 3;
            for (int i = 0; i < count; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                Vec3 at = this.position().add(Math.cos(angle) * (4 + this.random.nextDouble() * 6), 0, Math.sin(angle) * (4 + this.random.nextDouble() * 6));
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
                Mob minion = (hounds ? SunEntities.EMBER_HOUND.get() : SunEntities.CINDER_IMP.get()).create(level, EntitySpawnReason.MOB_SUMMONED);
                if (minion != null) {
                    minion.snapTo(ground.getX() + 0.5, ground.getY() + (hounds ? 0 : 2), ground.getZ() + 0.5, this.random.nextFloat() * 360F, 0);
                    minion.setTarget(this.getTarget());
                    level.addFreshEntity(minion);
                    Fx.column(level, ParticleTypes.FLAME, minion.position(), 3.0, 40, 0.5, 0.05);
                    Fx.burst(level, ModParticles.SOLAR_SPARK.get(), minion.position().add(0, 1, 0), 20, 0.5, 0.1);
                }
            }
        }
        if (this.attackTicks >= 34) {
            this.finishAttack(40);
        }
    }

    private void tickCorona(ServerLevel level) {
        Vec3 halo = this.haloPosition();
        if (this.attackTicks < 25) {
            if (this.attackTicks % 3 == 0) {
                Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), halo, 3.5 - this.attackTicks * 0.12, 14, -0.15);
            }
            return;
        }
        if (this.attackTicks == 25) {
            List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(40.0), p -> !p.isCreative() && !p.isSpectator());
            for (int i = 0; i < 16; i++) {
                double angle = i / 16.0 * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(angle), 0.15, Math.sin(angle));
                LivingEntity homing = players.isEmpty() ? null : players.get(i % players.size());
                SolarFlareEntity.shoot(level, this, halo.add(dir.scale(1.5)), dir, 0.7F, 7.0F, SolarFlareEntity.Kind.PHOENIX, homing);
            }
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFD27A), true, true, halo.x, halo.y, halo.z, 1, 0, 0, 0, 0);
            level.playSound(null, halo.x, halo.y, halo.z, SunSounds.WARDEN_FLARE.get(), SoundSource.HOSTILE, 5.0F, 0.7F);
        }
        if (this.attackTicks >= 40) {
            this.finishAttack(45);
        }
    }

    // --- Phase 2: Supernova ----------------------------------------------------------------------------------------

    private void beginSupernova(ServerLevel level) {
        this.novaStarted = true;
        this.setPhase(PHASE_SUPERNOVA);
        this.finishAttack(30);
        this.getNavigation().stop();
        this.entityData.set(DATA_NOVA, NOVA_TICKS);
        this.pylons.clear();
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2 + Math.PI / 4;
            Vec3 spot = Vec3.atBottomCenterOf(this.home).add(Math.cos(angle) * 11.0, 0, Math.sin(angle) * 11.0);
            Vec3 ground = this.ground(level, spot.add(0, 3, 0));
            SolarPylonEntity pylon = new SolarPylonEntity(SunEntities.SOLAR_PYLON.get(), level);
            pylon.bind(this);
            pylon.setPos(ground.x, ground.y, ground.z);
            level.addFreshEntity(pylon);
            this.pylons.add(pylon.getId());
            Fx.column(level, ParticleTypes.FLAME, ground, 6.0, 60, 0.4, 0.1);
        }
        this.roar(level);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_SUPERNOVA.get(), SoundSource.HOSTILE, 5.0F, 0.7F);
        SunFx.titleNear(level, this.position(), 128.0, Component.translatable("entity.starforged.sun_warden.supernova").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
            Component.translatable("entity.starforged.sun_warden.supernova_sub").withStyle(ChatFormatting.GOLD), 5, 60, 10);
        Fx.shake(level, this.position(), 48.0, 1.2F, 20);
    }

    private void tickSupernova(ServerLevel level) {
        // Kneel at the altar, gathering light.
        Vec3 home = Vec3.atBottomCenterOf(this.home);
        if (this.position().distanceToSqr(home) > 4.0) {
            this.getNavigation().moveTo(home.x, home.y, home.z, 1.2);
        } else {
            this.getNavigation().stop();
        }
        int nova = this.novaTicks() - 1;
        this.entityData.set(DATA_NOVA, nova);
        if (nova % 100 == 0 && nova > 0) {
            this.broadcast(level, Component.translatable("entity.starforged.sun_warden.countdown", nova / 20).withStyle(ChatFormatting.GOLD));
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0F, 0.6F + (NOVA_TICKS - nova) / 1000.0F);
        }
        // Pressure: occasional Sunfall while the pylons stand.
        if (nova % 80 == 40) {
            List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(40.0), p -> !p.isCreative() && !p.isSpectator());
            for (Player victim : players) {
                Vec3 spot = victim.position().add(this.random.nextGaussian() * 3, 0, this.random.nextGaussian() * 3);
                SolarFlareEntity.shoot(level, this, spot.add(0, 28, 0), new Vec3(0, -1, 0), 0.5F, 8.0F, SolarFlareEntity.Kind.SUNFALL, null);
            }
        }
        this.pylons.removeIf(id -> !(level.getEntity(id) instanceof SolarPylonEntity p) || !p.isAlive());
        if (this.pylons.isEmpty()) {
            this.setPhase(PHASE_STUNNED);
            this.stunTicks = 160;
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_HURT.get(), SoundSource.HOSTILE, 5.0F, 0.6F);
            this.broadcast(level, Component.translatable("entity.starforged.sun_warden.stunned").withStyle(ChatFormatting.GOLD));
            Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), this.haloPosition(), 1.0, 100, 0.8);
            return;
        }
        if (nova <= 0) {
            this.detonate(level);
        }
    }

    private void detonate(ServerLevel level) {
        Vec3 halo = this.haloPosition();
        for (LivingEntity victim : Combat.targetsAround(level, this, halo, 32.0)) {
            victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SUNFIRE, this), 22.0F);
            victim.igniteForSeconds(10.0F);
            Combat.blast(victim, halo, 2.4, 1.0);
        }
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF0B0), true, true, halo.x, halo.y, halo.z, 3, 1, 1, 1, 0);
        Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), halo, 1.0, 300, 1.6);
        Fx.sphere(level, ParticleTypes.FLAME, halo, 1.0, 200, 1.2);
        level.playSound(null, halo.x, halo.y, halo.z, SunSounds.WARDEN_SUPERNOVA.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        Fx.shake(level, halo, 96.0, 2.5F, 40);
        this.broadcast(level, Component.translatable("entity.starforged.sun_warden.detonated").withStyle(ChatFormatting.RED));
        this.discardPylons(level);
        this.enrage(level);
    }

    public void onPylonShattered(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SunSounds.WARDEN_HURT.get(), SoundSource.HOSTILE, 4.0F, 1.2F);
    }

    // --- Damage & death --------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        int phase = this.phase();
        if (phase == PHASE_RISING || source.getEntity() == this || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)
            || source.is(DamageTypeTags.IS_DROWNING)
            || (source.getEntity() instanceof Mob mob && (mob.getType() == SunEntities.CINDER_IMP.get() || mob.getType() == SunEntities.EMBER_HOUND.get()))) {
            return false;
        }
        if (phase == PHASE_SUPERNOVA) {
            if (source.getEntity() instanceof Player player) {
                player.sendOverlayMessage(Component.translatable("entity.starforged.sun_warden.immune").withStyle(ChatFormatting.GOLD));
                level.sendParticles(ModParticles.SOLAR_SPARK.get(), this.getX(), this.getY() + 3, this.getZ(), 20, 1.0, 1.5, 1.0, 0.1);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.0F, 1.6F);
            }
            return false;
        }
        if (phase == PHASE_STUNNED) {
            damage *= 1.5F;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && !this.novaStarted && this.getHealth() <= this.getMaxHealth() * 0.5F && this.isAlive()) {
            this.beginSupernova(level);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        this.deathSource = source;
        super.die(source);
        if (this.level() instanceof ServerLevel level) {
            this.setAttack(ATTACK_NONE);
            this.roar(level);
            this.broadcast(level, Component.translatable("entity.starforged.sun_warden.dying").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // Deferred until the end of the death sequence (see tickDeath).
    }

    @Override
    public boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 halo = this.haloPosition();
        if (this.deathTime % 4 == 0) {
            Vec3 at = halo.add(this.random.nextGaussian() * 2, this.random.nextGaussian() * 2 - 2, this.random.nextGaussian() * 2);
            level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.8F + this.random.nextFloat() * 0.4F);
        }
        if (this.deathTime % 2 == 0) {
            Vec3 dir = new Vec3(this.random.nextGaussian(), Math.abs(this.random.nextGaussian()), this.random.nextGaussian()).normalize();
            Fx.line(level, ModParticles.SOLAR_SPARK.get(), halo, halo.add(dir.scale(12.0)), 0.7);
        }
        level.sendParticles(ParticleTypes.FLAME, true, true, halo.x, halo.y, halo.z, 6, 1.0, 1.0, 1.0, 0.1);
        if (this.deathTime == 60) {
            level.playSound(null, halo.x, halo.y, halo.z, SunSounds.WARDEN_DEATH.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        }
        if (this.deathTime >= 100 && !this.isRemoved()) {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4C0), true, true, halo.x, halo.y, halo.z, 1, 0, 0, 0, 0);
            Fx.sphere(level, ModParticles.SOLAR_SPARK.get(), halo, 1.0, 260, 1.4);
            Fx.sphere(level, ParticleTypes.FLAME, halo, 1.0, 120, 1.0);
            Fx.shake(level, halo, 96.0, 2.5F, 40);
            DamageSource source = this.deathSource != null ? this.deathSource : this.damageSources().generic();
            super.dropAllDeathLoot(level, source);
            this.broadcast(level, Component.translatable("entity.starforged.sun_warden.defeated").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            this.discardPylons(level);
            this.shockwaves.clear();
            this.pillars.clear();
            level.broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    private void retreat(ServerLevel level) {
        this.broadcast(level, Component.translatable("entity.starforged.sun_warden.retreat").withStyle(ChatFormatting.GOLD));
        Fx.burst(level, ParticleTypes.FLAME, this.position().add(0, 3, 0), 200, 2.0, 0.3);
        ItemEntity sigil = new ItemEntity(level, this.home.getX() + 0.5, this.home.getY() + 1.5, this.home.getZ() + 0.5,
            new ItemStack(SunItems.SUNFIRE_SIGIL.get()));
        sigil.setUnlimitedLifetime();
        sigil.setGlowingTag(true);
        level.addFreshEntity(sigil);
        this.discardPylons(level);
        this.discard();
    }

    private void discardPylons(ServerLevel level) {
        for (int id : this.pylons) {
            Entity pylon = level.getEntity(id);
            if (pylon != null) {
                pylon.discard();
            }
        }
        this.pylons.clear();
    }

    private void broadcast(ServerLevel level, Component message) {
        SunFx.messageNear(level, this.position(), 128.0, message);
    }

    // --- Misc ------------------------------------------------------------------------------------------------------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return false;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void checkDespawn() {
        this.noActionTime = 0;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SunSounds.WARDEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SunSounds.WARDEN_ROAR.get();
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("phase", this.phase() == PHASE_SUPERNOVA || this.phase() == PHASE_STUNNED ? PHASE_ENRAGED : this.phase());
        output.putInt("rise", this.riseTicks());
        output.putBoolean("nova_started", this.novaStarted);
        output.putLong("home", this.home.asLong());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPhase(input.getIntOr("phase", PHASE_RISING));
        this.entityData.set(DATA_RISE, input.getIntOr("rise", RISE_TICKS));
        this.novaStarted = input.getBooleanOr("nova_started", false);
        this.home = BlockPos.of(input.getLongOr("home", BlockPos.ZERO.asLong()));
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}

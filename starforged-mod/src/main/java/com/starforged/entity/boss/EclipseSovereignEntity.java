package com.starforged.entity.boss;

import com.starforged.entity.projectile.MeteorEntity;
import com.starforged.entity.projectile.SingularityEntity;
import com.starforged.entity.projectile.StarboltEntity;
import com.starforged.event.StarfallManager;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
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
 * THE ECLIPSE SOVEREIGN - Devourer of Stars.
 * <p>
 * A colossal crowned tyrant with a black sun for a heart and two detached gauntlets. Its attacks:
 * <ul>
 *     <li><b>Starfall Barrage</b> - drags meteors out of the sky onto the players.</li>
 *     <li><b>Eclipse Beam</b> - charges, then sweeps a devastating beam of black light.</li>
 *     <li><b>Gauntlet Slam</b> - flies over its prey and pounds the ground into shockwaves.</li>
 *     <li><b>Singularity</b> - opens a black hole that pulls everything in.</li>
 *     <li><b>Call of the Void</b> - summons Void Stalkers and Star Mites.</li>
 *     <li><b>Blink</b> - tears through space to reposition.</li>
 *     <li><b>Supernova</b> (enraged) - erupts in a ring of starbolts.</li>
 * </ul>
 * At half health it raises four Eclipse Crystals and becomes invulnerable until they are shattered; it then
 * collapses, stunned, before rising again ENRAGED.
 */
public class EclipseSovereignEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(EclipseSovereignEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(EclipseSovereignEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_BEAM_END = SynchedEntityData.defineId(EclipseSovereignEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_ENTRANCE = SynchedEntityData.defineId(EclipseSovereignEntity.class, EntityDataSerializers.INT);

    public static final int PHASE_ENTRANCE = 0;
    public static final int PHASE_FIGHT = 1;
    public static final int PHASE_SHIELDED = 2;
    public static final int PHASE_STUNNED = 3;
    public static final int PHASE_ENRAGED = 4;

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_METEORS = 1;
    public static final int ATTACK_BEAM = 2;
    public static final int ATTACK_SLAM = 3;
    public static final int ATTACK_SINGULARITY = 4;
    public static final int ATTACK_SUMMON = 5;
    public static final int ATTACK_BLINK = 6;
    public static final int ATTACK_NOVA = 7;

    public static final int BEAM_CHARGE = 35;
    public static final int BEAM_END = 100;
    public static final int ENTRANCE_TICKS = 90;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
        Component.translatable("entity.starforged.eclipse_sovereign").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);

    private BlockPos home = BlockPos.ZERO;
    private int attackTicks;
    private int attackCooldown = 40;
    private int stunTicks;
    private int lastAttack = ATTACK_NONE;
    private int idleNoPlayers;
    private Vec3 beamPoint = Vec3.ZERO;
    private Vec3 slamTarget = Vec3.ZERO;
    private final List<Integer> crystals = new ArrayList<>();
    private boolean crystalsSpawned;
    private @Nullable DamageSource deathSource;

    /** Client-side: ticks since the current attack started. */
    public int clientAttackTicks;
    public int clientPhaseTicks;

    public EclipseSovereignEntity(EntityType<? extends EclipseSovereignEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = false;
        this.xpReward = 600;
        this.setPersistenceRequired();
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setCreateWorldFog(true);
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 700.0)
            .add(Attributes.ARMOR, 12.0)
            .add(Attributes.ARMOR_TOUGHNESS, 6.0)
            .add(Attributes.ATTACK_DAMAGE, 16.0)
            .add(Attributes.FOLLOW_RANGE, 80.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3)
            .add(Attributes.FLYING_SPEED, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, PHASE_ENTRANCE);
        builder.define(DATA_ATTACK, ATTACK_NONE);
        builder.define(DATA_BEAM_END, new Vector3f());
        builder.define(DATA_ENTRANCE, ENTRANCE_TICKS);
    }

    @Override
    protected void registerGoals() {
        // Movement and attacks are driven directly by the state machine in customServerAiStep.
    }

    // --- Accessors -------------------------------------------------------------------------------------------------

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public int attack() {
        return this.entityData.get(DATA_ATTACK);
    }

    public int entranceTicks() {
        return this.entityData.get(DATA_ENTRANCE);
    }

    public Vec3 beamEnd() {
        Vector3fc v = this.entityData.get(DATA_BEAM_END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public Vec3 corePosition() {
        return this.position().add(0, 3.4, 0);
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
        super.tick();
        if (this.level().isClientSide()) {
            this.clientAttackTicks++;
            this.clientPhaseTicks++;
            this.clientParticles();
        }
    }

    private void clientParticles() {
        Level level = this.level();
        Vec3 core = this.corePosition();
        if (this.random.nextInt(2) == 0) {
            level.addParticle(ModParticles.VOID_MOTE.get(), this.getX() + (this.random.nextDouble() - 0.5) * 3.0, this.getY() + this.random.nextDouble() * 1.5,
                this.getZ() + (this.random.nextDouble() - 0.5) * 3.0, 0.0, -0.03, 0.0);
        }
        if (this.random.nextInt(3) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2;
            level.addParticle(ModParticles.ECLIPSE_FLARE.get(), core.x + Math.cos(a) * 0.9, core.y + Math.sin(a) * 0.9, core.z, 0.0, 0.0, 0.0);
        }
        if (this.attack() == ATTACK_BEAM && this.clientAttackTicks < BEAM_CHARGE) {
            for (int i = 0; i < 3; i++) {
                Vec3 off = new Vec3(this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5).normalize().scale(4.0);
                level.addParticle(ParticleTypes.PORTAL, core.x, core.y, core.z, off.x, off.y, off.z);
            }
        }
        if (this.phase() == PHASE_SHIELDED && this.random.nextInt(2) == 0) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double b = this.random.nextDouble() * Math.PI;
            level.addParticle(ModParticles.ASTRAL_GLINT.get(), core.x + Math.cos(a) * Math.sin(b) * 3.5, core.y + Math.cos(b) * 3.5,
                core.z + Math.sin(a) * Math.sin(b) * 3.5, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 20 == 0) {
            StarfallManager.setEclipse(level.getServer(), 1.0F);
        }
        if (this.home.equals(BlockPos.ZERO)) {
            this.home = this.blockPosition().below(8);
        }

        // Abandoned fights end with the Sovereign withdrawing into the sky. Spectating creative players keep it here (for filming).
        Player nearest = level.getNearestPlayer(this, 96.0);
        if (nearest == null) {
            if (++this.idleNoPlayers > 1200) {
                this.retreat(level);
                return;
            }
        } else {
            this.idleNoPlayers = 0;
        }

        switch (this.phase()) {
            case PHASE_ENTRANCE -> this.tickEntrance(level);
            case PHASE_SHIELDED -> {
                this.tickCrystals(level);
                this.tickCombat(level);
            }
            case PHASE_STUNNED -> this.tickStunned(level);
            default -> this.tickCombat(level);
        }
    }

    private void tickEntrance(ServerLevel level) {
        int remaining = this.entranceTicks();
        double floatY = this.home.getY() + 8.0;
        double y = Mth.lerp(0.06, this.getY(), floatY);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.home.getX() + 0.5, y, this.home.getZ() + 0.5);
        if (remaining % 4 == 0) {
            Fx.ring(level, ModParticles.ECLIPSE_FLARE.get(), this.position().add(0, 0.5, 0), 3.0 + (ENTRANCE_TICKS - remaining) * 0.05, 18, 0.15, 0.0);
        }
        Player nearest = level.getNearestPlayer(this, 64.0);
        if (nearest != null) {
            this.getLookControl().setLookAt(nearest, 10.0F, 10.0F);
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
        }
        if (remaining <= 0) {
            this.setPhase(PHASE_FIGHT);
            this.roar(level);
            Vec3 center = this.position();
            for (LivingEntity target : Combat.targetsAround(level, this, center, 14.0)) {
                Combat.blast(target, center, 1.6, 0.7);
            }
            Fx.sphere(level, ModParticles.ECLIPSE_FLARE.get(), this.corePosition(), 1.0, 80, 0.9);
            Fx.ring(level, ParticleTypes.SONIC_BOOM, center, 4.0, 10, 0.0, 0.0);
            Fx.shake(level, center, 64.0, 2.0F, 30);
            this.attackCooldown = 40;
        } else {
            this.entityData.set(DATA_ENTRANCE, remaining - 1);
        }
    }

    private void roar(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
    }

    private void tickStunned(ServerLevel level) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.0, 0.5).add(0, -0.03, 0));
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, this.getBlockX(), this.getBlockZ());
        if (this.getY() > ground + 1.5) {
            this.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.15, 0));
        }
        if (this.tickCount % 5 == 0) {
            level.sendParticles(ModParticles.STAR_SPARKLE.get(), this.getX(), this.getY() + 5.5, this.getZ(), 4, 0.6, 0.2, 0.6, 0.02);
        }
        if (--this.stunTicks <= 0) {
            this.setPhase(PHASE_ENRAGED);
            this.bossEvent.setColor(BossEvent.BossBarColor.RED);
            this.roar(level);
            this.broadcast(level, Component.translatable("entity.starforged.eclipse_sovereign.enraged").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            Fx.sphere(level, ModParticles.ECLIPSE_FLARE.get(), this.corePosition(), 1.0, 100, 1.1);
            Fx.shake(level, this.position(), 64.0, 1.6F, 25);
            this.attackCooldown = 20;
        }
    }

    private void tickCombat(ServerLevel level) {
        LivingEntity target = this.findTarget(level);
        this.setTarget(target);
        boolean enraged = this.phase() == PHASE_ENRAGED;

        if (this.attack() == ATTACK_NONE) {
            this.hover(level, target);
            if (target != null && --this.attackCooldown <= 0) {
                this.chooseAttack(level, target);
            }
            return;
        }
        this.attackTicks++;
        switch (this.attack()) {
            case ATTACK_METEORS -> this.tickMeteors(level, enraged);
            case ATTACK_BEAM -> this.tickBeam(level, target, enraged);
            case ATTACK_SLAM -> this.tickSlam(level, target);
            case ATTACK_SINGULARITY -> this.tickSingularity(level, target);
            case ATTACK_SUMMON -> this.tickSummon(level);
            case ATTACK_BLINK -> this.tickBlink(level, target);
            case ATTACK_NOVA -> this.tickNova(level);
            default -> this.finishAttack(20);
        }
    }

    private @Nullable LivingEntity findTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) < 80.0) {
            if (this.random.nextInt(200) != 0) {
                return current;
            }
        }
        List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(64.0),
            p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        return players.isEmpty() ? null : players.get(this.random.nextInt(players.size()));
    }

    /** Hovers above the arena at a menacing distance from its target, slowly circling. */
    private void hover(ServerLevel level, @Nullable LivingEntity target) {
        Vec3 desired;
        if (target != null) {
            Vec3 away = this.position().subtract(target.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-3) {
                away = new Vec3(1, 0, 0);
            }
            double orbit = this.tickCount * 0.012;
            Vec3 dir = away.normalize().yRot((float) orbit * 0.15F);
            desired = target.position().add(dir.scale(11.0)).add(0, 6.0, 0);
        } else {
            desired = Vec3.atCenterOf(this.home).add(0, 8.0, 0);
        }
        // Stay near the altar.
        Vec3 home = Vec3.atCenterOf(this.home);
        Vec3 fromHome = desired.subtract(home);
        if (fromHome.horizontalDistance() > 36.0) {
            Vec3 flat = new Vec3(fromHome.x, 0, fromHome.z).normalize().scale(36.0);
            desired = new Vec3(home.x + flat.x, desired.y, home.z + flat.z);
        }
        Vec3 delta = desired.subtract(this.position());
        Vec3 motion = this.getDeltaMovement().scale(0.85).add(delta.normalize().scale(Math.min(0.06, delta.length() * 0.02)));
        this.setDeltaMovement(motion);
        this.move(net.minecraft.world.entity.MoverType.SELF, motion);
        if (target != null) {
            this.lookAtTarget(target);
        }
    }

    private void lookAtTarget(Entity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 8.0F));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    private void chooseAttack(ServerLevel level, LivingEntity target) {
        boolean enraged = this.phase() == PHASE_ENRAGED;
        boolean shielded = this.phase() == PHASE_SHIELDED;
        double dist = this.distanceTo(target);
        long minions = level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(48.0),
            m -> m.getType() == ModEntities.VOID_STALKER.get() || m.getType() == ModEntities.STAR_MITE.get()).size();
        List<Integer> options = new ArrayList<>();
        options.add(ATTACK_METEORS);
        options.add(ATTACK_METEORS);
        if (!shielded) {
            options.add(ATTACK_BEAM);
            options.add(ATTACK_BEAM);
            options.add(ATTACK_SLAM);
            options.add(ATTACK_SINGULARITY);
        }
        if (minions < 6) {
            options.add(ATTACK_SUMMON);
        }
        if (dist < 5.0 || dist > 30.0) {
            options.add(ATTACK_BLINK);
            options.add(ATTACK_BLINK);
        }
        if (enraged) {
            options.add(ATTACK_NOVA);
            options.add(ATTACK_NOVA);
            options.add(ATTACK_SLAM);
        }
        options.removeIf(a -> a == this.lastAttack && this.random.nextInt(3) != 0);
        int chosen = options.get(this.random.nextInt(options.size()));
        this.lastAttack = chosen;
        this.setAttack(chosen);
        switch (chosen) {
            case ATTACK_BEAM -> {
                this.beamPoint = target.position().add(0, 1.0, 0).add(this.random.nextDouble() * 6 - 3, 0, this.random.nextDouble() * 6 - 3);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOSS_BEAM_CHARGE.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
            }
            case ATTACK_SLAM -> this.slamTarget = target.position();
            case ATTACK_METEORS -> level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 4.0F, 1.3F);
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

    private void tickMeteors(ServerLevel level, boolean enraged) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        int interval = enraged ? 3 : 5;
        if (this.attackTicks >= 12 && this.attackTicks <= 56 && this.attackTicks % interval == 0) {
            List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(48.0), p -> !p.isCreative() && !p.isSpectator());
            if (!players.isEmpty()) {
                Player victim = players.get(this.random.nextInt(players.size()));
                Vec3 spot = victim.position().add(this.random.nextGaussian() * 4.0, 0, this.random.nextGaussian() * 4.0);
                Vec3 start = spot.add(this.random.nextGaussian() * 12.0, 42.0 + this.random.nextDouble() * 10.0, this.random.nextGaussian() * 12.0);
                MeteorEntity.launch(level, start, spot, 0.8F + this.random.nextFloat() * 0.5F, MeteorEntity.Kind.BOSS, this, 1.6F);
            }
            Fx.burst(level, ModParticles.ECLIPSE_FLARE.get(), this.position().add(0, 6.5, 0), 4, 1.2, 0.05);
        }
        if (this.attackTicks >= 64) {
            this.finishAttack(50);
        }
    }

    private void tickBeam(ServerLevel level, @Nullable LivingEntity target, boolean enraged) {
        this.setDeltaMovement(Vec3.ZERO);
        Vec3 core = this.corePosition();
        if (target != null) {
            this.lookAtTarget(target);
            Vec3 aim = target.position().add(0, 1.0, 0);
            double speed = enraged ? 0.55 : 0.32;
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
            level.playSound(null, core.x, core.y, core.z, ModSounds.BOSS_BEAM_FIRE.get(), SoundSource.HOSTILE, 5.0F, 1.0F);
        }
        Vec3 dir = this.beamPoint.subtract(core).normalize();
        Vec3 far = core.add(dir.scale(56.0));
        BlockHitResult hit = level.clip(new ClipContext(core, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getLocation();
        this.entityData.set(DATA_BEAM_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));

        double width = enraged ? 1.8 : 1.3;
        if (this.attackTicks % 4 == 0) {
            AABB box = new AABB(core, end).inflate(width);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> Combat.canHit(level, this, e))) {
                Vec3 p = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
                double t = Mth.clamp(p.subtract(core).dot(dir), 0.0, end.distanceTo(core));
                if (core.add(dir.scale(t)).distanceTo(p) < width + victim.getBbWidth() * 0.5) {
                    victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.ECLIPSE_BEAM, this), enraged ? 7.0F : 5.0F);
                    victim.igniteForSeconds(3.0F);
                }
            }
        }
        level.sendParticles(ModParticles.ECLIPSE_FLARE.get(), true, true, end.x, end.y, end.z, 2, 0.3, 0.3, 0.3, 0.05);
        level.sendParticles(ParticleTypes.LAVA, true, true, end.x, end.y, end.z, 1, 0.2, 0.2, 0.2, 0.0);
        if (this.attackTicks % 3 == 0) {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!state.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), true, true, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.2);
            }
        }
        if (this.attackTicks >= BEAM_END) {
            this.finishAttack(60);
        }
    }

    private void tickSlam(ServerLevel level, @Nullable LivingEntity target) {
        if (this.attackTicks < 22) {
            if (target != null) {
                this.slamTarget = target.position();
            }
            Vec3 above = this.slamTarget.add(0, 6.5, 0);
            Vec3 delta = above.subtract(this.position());
            Vec3 motion = delta.scale(0.12);
            if (motion.length() > 1.4) {
                motion = motion.normalize().scale(1.4);
            }
            this.setDeltaMovement(motion);
            this.move(net.minecraft.world.entity.MoverType.SELF, motion);
            if (target != null) {
                this.lookAtTarget(target);
            }
            return;
        }
        if (this.attackTicks == 26) {
            BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(this.slamTarget));
            Vec3 ground = Vec3.atBottomCenterOf(groundPos);
            for (LivingEntity victim : Combat.targetsAround(level, this, ground.add(0, 1, 0), 7.5)) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, this), 14.0F);
                Combat.blast(victim, ground, 1.3, 1.1);
            }
            BlockState below = level.getBlockState(groundPos.below());
            if (!below.isAir()) {
                BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, below);
                for (int r = 1; r <= 4; r++) {
                    Fx.ring(level, debris, ground.add(0, 0.2, 0), r * 2.0, 18 + r * 10, 0.2, 0.4);
                }
            }
            Fx.ring(level, ModParticles.ECLIPSE_FLARE.get(), ground.add(0, 0.4, 0), 1.0, 40, 0.9, 0.0);
            Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, ground, 1, 0.0, 0.0);
            level.playSound(null, ground.x, ground.y, ground.z, ModSounds.BOSS_SLAM.get(), SoundSource.HOSTILE, 5.0F, 0.9F);
            Fx.shake(level, ground, 48.0, 1.8F, 18);
        }
        if (this.attackTicks >= 44) {
            this.finishAttack(50);
        }
    }

    private void tickSingularity(ServerLevel level, @Nullable LivingEntity target) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        if (this.attackTicks == 16 && target != null) {
            Vec3 between = target.position().add(this.position().subtract(target.position()).normalize().scale(3.0)).add(0, 1.5, 0);
            SingularityEntity.spawn(level, between, this, this.phase() == PHASE_ENRAGED ? 110 : 90, 1.4F);
            level.playSound(null, between.x, between.y, between.z, ModSounds.BOSS_SUMMON.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        if (this.attackTicks >= 34) {
            this.finishAttack(60);
        }
    }

    private void tickSummon(ServerLevel level) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
        if (this.attackTicks == 5) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOSS_SUMMON.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
        }
        if (this.attackTicks == 22) {
            boolean stalkers = this.random.nextBoolean();
            int count = stalkers ? 2 : 4;
            for (int i = 0; i < count; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2;
                Vec3 at = Vec3.atCenterOf(this.home).add(Math.cos(angle) * (6 + this.random.nextDouble() * 8), 0, Math.sin(angle) * (6 + this.random.nextDouble() * 8));
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
                Mob minion = (stalkers ? ModEntities.VOID_STALKER.get() : ModEntities.STAR_MITE.get()).create(level, EntitySpawnReason.MOB_SUMMONED);
                if (minion != null) {
                    minion.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, this.random.nextFloat() * 360F, 0);
                    minion.setTarget(this.getTarget());
                    level.addFreshEntity(minion);
                    Fx.column(level, ParticleTypes.REVERSE_PORTAL, minion.position(), 3.0, 40, 0.6, 0.1);
                    Fx.burst(level, ModParticles.VOID_MOTE.get(), minion.position().add(0, 1, 0), 20, 0.5, 0.05);
                }
            }
        }
        if (this.attackTicks >= 36) {
            this.finishAttack(40);
        }
    }

    private void tickBlink(ServerLevel level, @Nullable LivingEntity target) {
        if (this.attackTicks == 8) {
            Vec3 from = this.position();
            Vec3 center = target != null ? target.position() : Vec3.atCenterOf(this.home);
            double angle = this.random.nextDouble() * Math.PI * 2;
            Vec3 to = center.add(Math.cos(angle) * 12.0, 7.0, Math.sin(angle) * 12.0);
            Fx.burst(level, ParticleTypes.REVERSE_PORTAL, from.add(0, 3, 0), 120, 1.5, 0.5);
            Fx.burst(level, ModParticles.VOID_MOTE.get(), from.add(0, 3, 0), 60, 1.5, 0.1);
            this.teleportTo(to.x, to.y, to.z);
            Fx.burst(level, ParticleTypes.PORTAL, to.add(0, 3, 0), 120, 1.5, 1.0);
            level.playSound(null, from.x, from.y, from.z, ModSounds.STALKER_TELEPORT.get(), SoundSource.HOSTILE, 4.0F, 0.5F);
            level.playSound(null, to.x, to.y, to.z, ModSounds.STALKER_TELEPORT.get(), SoundSource.HOSTILE, 4.0F, 0.5F);
        }
        if (this.attackTicks >= 18) {
            this.finishAttack(15);
        }
    }

    private void tickNova(ServerLevel level) {
        this.setDeltaMovement(Vec3.ZERO);
        Vec3 core = this.corePosition();
        if (this.attackTicks < 30) {
            if (this.attackTicks % 3 == 0) {
                Fx.sphere(level, ModParticles.ECLIPSE_FLARE.get(), core, 4.0 - this.attackTicks * 0.12, 12, -0.15);
            }
            return;
        }
        if (this.attackTicks == 30) {
            for (int i = 0; i < 20; i++) {
                double angle = i / 20.0 * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(angle), -0.25, Math.sin(angle));
                StarboltEntity.shoot(level, this, core.add(dir.scale(2.0)), dir, 0.9F, 8.0F, StarboltEntity.Variant.VOID, null);
            }
            for (LivingEntity victim : Combat.targetsAround(level, this, core, 9.0)) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STARLIGHT, this), 10.0F);
                Combat.blast(victim, core, 2.0, 0.8);
            }
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFC9A0FF), true, true, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            Fx.sphere(level, ModParticles.STAR_SPARKLE.get(), core, 1.0, 120, 1.0);
            level.playSound(null, core.x, core.y, core.z, ModSounds.SINGULARITY_COLLAPSE.get(), SoundSource.HOSTILE, 5.0F, 0.7F);
            Fx.shake(level, core, 48.0, 1.5F, 16);
        }
        if (this.attackTicks >= 44) {
            this.finishAttack(50);
        }
    }

    // --- Phase 2: Eclipse Crystals ---------------------------------------------------------------------------------

    private void raiseCrystals(ServerLevel level) {
        this.crystalsSpawned = true;
        this.setPhase(PHASE_SHIELDED);
        this.finishAttack(30);
        this.crystals.clear();
        for (int i = 0; i < 4; i++) {
            EclipseCrystalEntity crystal = new EclipseCrystalEntity(ModEntities.ECLIPSE_CRYSTAL.get(), level);
            crystal.bind(this, i);
            level.addFreshEntity(crystal);
            this.crystals.add(crystal.getId());
        }
        this.roar(level);
        this.broadcast(level, Component.translatable("entity.starforged.eclipse_sovereign.shielded").withStyle(ChatFormatting.LIGHT_PURPLE));
        Fx.sphere(level, ModParticles.ASTRAL_GLINT.get(), this.corePosition(), 2.0, 80, 0.5);
        Fx.shake(level, this.position(), 48.0, 1.0F, 20);
    }

    private void tickCrystals(ServerLevel level) {
        this.crystals.removeIf(id -> !(level.getEntity(id) instanceof EclipseCrystalEntity c) || !c.isAlive());
        if (this.crystals.isEmpty()) {
            this.setPhase(PHASE_STUNNED);
            this.setAttack(ATTACK_NONE);
            this.stunTicks = 140;
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOSS_HURT.get(), SoundSource.HOSTILE, 5.0F, 0.6F);
            this.broadcast(level, Component.translatable("entity.starforged.eclipse_sovereign.stunned").withStyle(ChatFormatting.GOLD));
            Fx.sphere(level, ModParticles.STAR_SPARKLE.get(), this.corePosition(), 1.0, 100, 0.8);
        }
    }

    public void onCrystalShattered() {
        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOSS_HURT.get(), SoundSource.HOSTILE, 4.0F, 1.2F);
        }
    }

    // --- Damage & death --------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        int phase = this.phase();
        if (phase == PHASE_ENTRANCE || source.getEntity() == this || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
            || (source.getEntity() != null && source.getEntity().getType() != null && source.getEntity() instanceof Mob mob
                && (mob.getType() == ModEntities.VOID_STALKER.get() || mob.getType() == ModEntities.STAR_MITE.get()))) {
            return false;
        }
        if (phase == PHASE_SHIELDED) {
            if (source.getEntity() instanceof Player player) {
                player.sendOverlayMessage(Component.translatable("entity.starforged.eclipse_sovereign.immune").withStyle(ChatFormatting.LIGHT_PURPLE));
                level.sendParticles(ModParticles.ASTRAL_GLINT.get(), this.getX(), this.getY() + 3, this.getZ(), 20, 1.0, 1.5, 1.0, 0.1);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 2.0F, 0.6F);
            }
            return false;
        }
        if (phase == PHASE_STUNNED) {
            damage *= 1.5F;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile && phase != PHASE_STUNNED) {
            damage *= 0.75F;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && !this.crystalsSpawned && this.getHealth() <= this.getMaxHealth() * 0.5F && this.isAlive()) {
            this.raiseCrystals(level);
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
            this.broadcast(level, Component.translatable("entity.starforged.eclipse_sovereign.dying").withStyle(ChatFormatting.GOLD));
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
        this.setDeltaMovement(0, 0.04, 0);
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        Vec3 core = this.corePosition();
        if (this.deathTime % 4 == 0) {
            Vec3 at = core.add(this.random.nextGaussian() * 2, this.random.nextGaussian() * 2, this.random.nextGaussian() * 2);
            level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0F, 0.8F + this.random.nextFloat() * 0.4F);
        }
        if (this.deathTime % 2 == 0) {
            Vec3 dir = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian()).normalize();
            Fx.line(level, ParticleTypes.END_ROD, core, core.add(dir.scale(10.0)), 0.7);
        }
        level.sendParticles(ModParticles.STAR_SPARKLE.get(), true, true, core.x, core.y, core.z, 6, 1.0, 1.0, 1.0, 0.2);
        if (this.deathTime == 60) {
            level.playSound(null, core.x, core.y, core.z, ModSounds.BOSS_DEATH.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        }
        if (this.deathTime >= 100 && !this.isRemoved()) {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFF4D6), true, true, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            Fx.sphere(level, ModParticles.STAR_SPARKLE.get(), core, 1.0, 250, 1.4);
            Fx.sphere(level, ModParticles.ECLIPSE_FLARE.get(), core, 1.0, 80, 0.8);
            Fx.shake(level, core, 96.0, 2.5F, 40);
            DamageSource source = this.deathSource != null ? this.deathSource : this.damageSources().generic();
            super.dropAllDeathLoot(level, source);
            StarfallManager.setEclipse(level.getServer(), 0.0F);
            StarfallManager.recordSovereignDefeat(level.getServer());
            this.broadcast(level, Component.translatable("entity.starforged.eclipse_sovereign.defeated").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            this.discardCrystals(level);
            level.broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    private void retreat(ServerLevel level) {
        this.broadcast(level, Component.translatable("entity.starforged.eclipse_sovereign.retreat").withStyle(ChatFormatting.DARK_PURPLE));
        Fx.burst(level, ParticleTypes.REVERSE_PORTAL, this.position().add(0, 3, 0), 200, 2.0, 1.0);
        ItemSigilDrop.drop(level, Vec3.atCenterOf(this.home).add(0, 1.5, 0));
        StarfallManager.setEclipse(level.getServer(), 0.0F);
        this.discardCrystals(level);
        this.discard();
    }

    private void discardCrystals(ServerLevel level) {
        for (int id : this.crystals) {
            Entity crystal = level.getEntity(id);
            if (crystal != null) {
                crystal.discard();
            }
        }
        this.crystals.clear();
    }

    private void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceTo(this) < 128.0) {
                player.sendSystemMessage(message);
            }
        }
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
    public void travel(Vec3 input) {
        // All movement is applied directly by the attack state machine.
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
        return ModSounds.BOSS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BOSS_ROAR.get();
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("phase", this.phase() == PHASE_SHIELDED || this.phase() == PHASE_STUNNED ? PHASE_ENRAGED : this.phase());
        output.putInt("entrance", this.entranceTicks());
        output.putBoolean("crystals_spawned", this.crystalsSpawned);
        output.putLong("home", this.home.asLong());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPhase(input.getIntOr("phase", PHASE_ENTRANCE));
        this.entityData.set(DATA_ENTRANCE, input.getIntOr("entrance", ENTRANCE_TICKS));
        this.crystalsSpawned = input.getBooleanOr("crystals_spawned", false);
        this.home = BlockPos.of(input.getLongOr("home", BlockPos.ZERO.asLong()));
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }

    /** Returns the Eclipse Sigil if a fight is abandoned so the player can try again. */
    private static final class ItemSigilDrop {
        static void drop(ServerLevel level, Vec3 at) {
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, at.x, at.y, at.z,
                new net.minecraft.world.item.ItemStack(com.starforged.registry.ModItems.ECLIPSE_SIGIL.get()));
            item.setUnlimitedLifetime();
            item.setGlowingTag(true);
            level.addFreshEntity(item);
        }
    }
}

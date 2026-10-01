package com.starforged.moon.boss;

import com.starforged.moon.MoonEntities;
import com.starforged.moon.MoonItems;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.entity.FallingMoonEntity;
import com.starforged.moon.entity.MoonletEntity;
import com.starforged.moon.world.MoonGravity;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * THE PALE MATRIARCH - Keeper of the Tides.
 * <ul>
 *     <li><b>Waxing Moon</b> (100-66%): Tidal Waves (jump the rings), the Gravity Lance, moonlet volleys, crescent blades,
 *     a crushing dive, and blinks through moonlight.</li>
 *     <li><b>Inversion</b> (66%): gravity all but vanishes. She rises out of reach, held up by three Lunar Anchors; every
 *     anchor you break drags her lower, and with the last she crashes down, stunned and vulnerable.</li>
 *     <li><b>Waning Moon</b>: the same attacks, faster.</li>
 *     <li><b>New Moon</b> (33%): darkness falls and she splits into four. Only the real one casts a shadow.</li>
 *     <li><b>Moonfall</b> (15%): a colossal moon begins to fall. Kill her in fifteen seconds or hide under the anchor wreckage.</li>
 * </ul>
 */
public class PaleMatriarchEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(PaleMatriarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(PaleMatriarchEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_BEAM_END = SynchedEntityData.defineId(PaleMatriarchEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_EMERGE = SynchedEntityData.defineId(PaleMatriarchEntity.class, EntityDataSerializers.INT);

    public static final int PHASE_EMERGE = 0;
    public static final int PHASE_WAXING = 1;
    public static final int PHASE_INVERSION = 2;
    public static final int PHASE_STUNNED = 3;
    public static final int PHASE_WANING = 4;
    public static final int PHASE_NEW_MOON = 5;

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_WAVE = 1;
    public static final int ATTACK_LANCE = 2;
    public static final int ATTACK_VOLLEY = 3;
    public static final int ATTACK_CRESCENT = 4;
    public static final int ATTACK_TELEPORT = 5;
    public static final int ATTACK_DIVE = 6;

    public static final int EMERGE_TICKS = 50;
    public static final int LANCE_CHARGE = 30;
    public static final int LANCE_END = 80;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
        Component.translatable("entity.starforged.pale_matriarch").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
        BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_20);

    private BlockPos home = BlockPos.ZERO;
    private double floorY;
    private int attackTicks;
    private int attackCooldown = 40;
    private int stunTicks;
    private int lastAttack = ATTACK_NONE;
    private int idleNoPlayers;
    private double inversionHeight;
    private boolean inversionDone;
    private boolean newMoonDone;
    private boolean moonfallDone;
    private int shuffleTimer;
    private Vec3 beamPoint = Vec3.ZERO;
    private Vec3 diveTarget = Vec3.ZERO;
    private final List<Integer> anchors = new ArrayList<>();
    private final List<Integer> echoes = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private @Nullable DamageSource deathSource;

    public int clientAttackTicks;
    public int clientPhaseTicks;

    private static final class Wave {
        final Vec3 center;
        double radius = 1.0;
        final float damage;
        final Set<Integer> hit = new HashSet<>();

        Wave(Vec3 center, float damage) {
            this.center = center;
            this.damage = damage;
        }
    }

    public PaleMatriarchEntity(EntityType<? extends PaleMatriarchEntity> type, Level level) {
        super(type, level);
        this.xpReward = 1000;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1024.0)
            .add(Attributes.ARMOR, 14.0)
            .add(Attributes.ARMOR_TOUGHNESS, 8.0)
            .add(Attributes.ATTACK_DAMAGE, 14.0)
            .add(Attributes.FOLLOW_RANGE, 80.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, PHASE_EMERGE);
        builder.define(DATA_ATTACK, ATTACK_NONE);
        builder.define(DATA_BEAM_END, new Vector3f());
        builder.define(DATA_EMERGE, 0);
    }

    @Override
    protected void registerGoals() {
    }

    // --- Accessors -------------------------------------------------------------------------------------------------

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public int attack() {
        return this.entityData.get(DATA_ATTACK);
    }

    public int emergeTicks() {
        return this.entityData.get(DATA_EMERGE);
    }

    public Vec3 beamEnd() {
        Vector3fc v = this.entityData.get(DATA_BEAM_END);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** The glowing moon-core in her chest, where the Gravity Lance fires from. */
    public Vec3 corePosition() {
        return this.position().add(0, 3.0, 0);
    }

    public void setHome(BlockPos altar) {
        this.home = altar;
        this.floorY = altar.getY() - 1;
    }

    private Vec3 arenaCenter() {
        return new Vec3(this.home.getX() + 0.5, this.floorY, this.home.getZ() + 0.5);
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
            if (this.random.nextInt(2) == 0) {
                this.level().addParticle(ModParticles.MOON_DUST.get(), this.getX() + (this.random.nextDouble() - 0.5) * 2.4,
                    this.getY() + this.random.nextDouble() * 1.2, this.getZ() + (this.random.nextDouble() - 0.5) * 2.4, 0.0, -0.02, 0.0);
            }
            if (this.attack() == ATTACK_LANCE && this.clientAttackTicks < LANCE_CHARGE) {
                Vec3 core = this.corePosition();
                Vec3 off = new Vec3(this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5, this.random.nextDouble() - 0.5).normalize().scale(3.0);
                this.level().addParticle(ModParticles.LUNAR_GLIMMER.get(), core.x + off.x, core.y + off.y, core.z + off.z, -off.x * 0.1, -off.y * 0.1,
                    -off.z * 0.1);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.home.equals(BlockPos.ZERO)) {
            this.setHome(this.blockPosition().below(6));
        }
        if (level.getNearestPlayer(this, 96.0) == null) {
            if (++this.idleNoPlayers > 1200) {
                this.retreat(level);
                return;
            }
        } else {
            this.idleNoPlayers = 0;
        }
        this.tickWaves(level);
        switch (this.phase()) {
            case PHASE_EMERGE -> this.tickEmerge(level);
            case PHASE_INVERSION -> this.tickInversion(level);
            case PHASE_STUNNED -> this.tickStunned(level);
            default -> this.tickCombat(level);
        }
    }

    private void tickEmerge(ServerLevel level) {
        int t = this.emergeTicks() + 1;
        this.entityData.set(DATA_EMERGE, t);
        this.setDeltaMovement(Vec3.ZERO);
        Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), this.corePosition(), 3.5 - t * 0.05, 8, -0.05);
        Player nearest = level.getNearestPlayer(this, 64.0);
        if (nearest != null) {
            this.lookAtTarget(nearest, 20.0F);
        }
        if (t >= EMERGE_TICKS) {
            // BOOM - gravity returns.
            this.setPhase(PHASE_WAXING);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_ROAR.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
            for (Player player : this.playersInArena(level)) {
                MoonGravity.release(player);
                player.removeEffect(MobEffects.LEVITATION);
                player.setDeltaMovement(player.getDeltaMovement().x, -1.2, player.getDeltaMovement().z);
                player.hurtMarked = true;
            }
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFDDEEFF), true, true, this.getX(), this.getY() + 3, this.getZ(), 1, 0, 0, 0, 0);
            Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), this.corePosition(), 1.0, 200, 1.2);
            Fx.ring(level, ModParticles.MOON_DUST.get(), this.arenaCenter().add(0, 0.3, 0), 1.0, 100, 1.0, 0.0);
            Fx.shake(level, this.position(), 64.0, 2.2F, 30);
            SunFx.titleNear(level, this.position(), 128.0, Component.translatable("entity.starforged.pale_matriarch").withStyle(ChatFormatting.AQUA,
                ChatFormatting.BOLD), Component.translatable("entity.starforged.pale_matriarch.title").withStyle(ChatFormatting.WHITE), 10, 60, 20);
            this.attackCooldown = 30;
        }
    }

    private List<Player> playersInArena(ServerLevel level) {
        return level.getEntitiesOfClass(Player.class, new AABB(this.home).inflate(48.0, 32.0, 48.0), p -> !p.isSpectator());
    }

    private void roar(ServerLevel level) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_ROAR.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
    }

    private void tickCombat(ServerLevel level) {
        LivingEntity target = this.findTarget(level);
        this.setTarget(target);
        boolean waning = this.phase() >= PHASE_WANING;
        if (this.phase() == PHASE_NEW_MOON) {
            this.tickNewMoon(level);
        }
        if (this.attack() == ATTACK_NONE) {
            this.hover(target, 1.0);
            if (target != null && --this.attackCooldown <= 0) {
                this.chooseAttack(level, target);
            }
            return;
        }
        this.attackTicks++;
        switch (this.attack()) {
            case ATTACK_WAVE -> this.tickWave(level, waning);
            case ATTACK_LANCE -> this.tickLance(level, target, waning);
            case ATTACK_VOLLEY -> this.tickVolley(level, target, waning);
            case ATTACK_CRESCENT -> this.tickCrescent(level, target);
            case ATTACK_TELEPORT -> this.tickTeleport(level, target);
            case ATTACK_DIVE -> this.tickDive(level, target);
            default -> this.finishAttack(20);
        }
    }

    /** Glides in slow circles around the altar, a few blocks above the arena floor. */
    private void hover(@Nullable LivingEntity target, double speed) {
        Vec3 center = this.arenaCenter();
        double angle = this.tickCount * 0.012 + this.getId();
        Vec3 want = center.add(Math.cos(angle) * 8.0, 5.0 + Math.sin(this.tickCount * 0.05) * 0.8, Math.sin(angle) * 8.0);
        Vec3 delta = want.subtract(this.position());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.6).add(delta.normalize().scale(Math.min(0.12, delta.length() * 0.05) * speed)));
        if (target != null) {
            this.lookAtTarget(target, 8.0F);
        }
    }

    private @Nullable LivingEntity findTarget(ServerLevel level) {
        LivingEntity current = this.getTarget();
        if (current instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceTo(this) < 80.0
            && this.random.nextInt(240) != 0) {
            return current;
        }
        List<Player> players = level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(64.0),
            p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        return players.isEmpty() ? null : players.get(this.random.nextInt(players.size()));
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
        double dist = this.distanceTo(target);
        List<Integer> options = new ArrayList<>(List.of(ATTACK_WAVE, ATTACK_WAVE, ATTACK_LANCE, ATTACK_LANCE, ATTACK_VOLLEY, ATTACK_VOLLEY,
            ATTACK_CRESCENT, ATTACK_CRESCENT, ATTACK_DIVE));
        if (dist > 14.0) {
            options.add(ATTACK_TELEPORT);
            options.add(ATTACK_DIVE);
        }
        if (dist < 6.0) {
            options.add(ATTACK_TELEPORT);
        }
        options.removeIf(a -> a == this.lastAttack && this.random.nextInt(3) != 0);
        int chosen = options.get(this.random.nextInt(options.size()));
        this.lastAttack = chosen;
        this.setAttack(chosen);
        switch (chosen) {
            case ATTACK_LANCE -> {
                this.beamPoint = target.position().add(this.random.nextDouble() * 8 - 4, 0.5, this.random.nextDouble() * 8 - 4);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_LANCE.get(), SoundSource.HOSTILE, 4.0F, 0.8F);
            }
            case ATTACK_DIVE -> this.diveTarget = target.position();
            case ATTACK_WAVE -> level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_WAVE.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
            default -> {
            }
        }
    }

    private void finishAttack(int cooldown) {
        this.setAttack(ATTACK_NONE);
        this.entityData.set(DATA_BEAM_END, new Vector3f());
        this.attackCooldown = this.phase() >= PHASE_WANING ? cooldown * 2 / 3 : cooldown;
    }

    // --- Attacks ---------------------------------------------------------------------------------------------------

    /** Raises a tidal ring that rolls out across the floor - jump it! */
    private void tickWave(ServerLevel level, boolean waning) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        if (this.attackTicks == 12 || (waning && this.attackTicks == 30)) {
            Vec3 ground = new Vec3(this.getX(), this.floorY + 1.0, this.getZ());
            this.waves.add(new Wave(ground, 9.0F));
            level.playSound(null, ground.x, ground.y, ground.z, MoonSounds.MATRIARCH_WAVE.get(), SoundSource.HOSTILE, 3.0F, 1.2F);
            Fx.shake(level, ground, 24.0, 0.5F, 8);
        }
        if (this.attackTicks >= (waning ? 44 : 30)) {
            this.finishAttack(35);
        }
    }

    private void tickWaves(ServerLevel level) {
        Iterator<Wave> it = this.waves.iterator();
        while (it.hasNext()) {
            Wave wave = it.next();
            wave.radius += 0.55;
            int points = (int) (wave.radius * 5);
            for (int i = 0; i < points; i++) {
                double a = i / (double) points * Math.PI * 2;
                double x = wave.center.x + Math.cos(a) * wave.radius;
                double z = wave.center.z + Math.sin(a) * wave.radius;
                level.sendParticles(ParticleTypes.SPLASH, true, true, x, wave.center.y + 0.1, z, 2, 0.05, 0.2, 0.05, 0.0);
                if (i % 3 == 0) {
                    level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), true, true, x, wave.center.y + 0.5, z, 1, 0.0, 0.1, 0.0, 0.0);
                }
            }
            for (LivingEntity victim : Combat.targetsAround(level, this, wave.center, wave.radius + 1.0)) {
                double d = victim.position().multiply(1, 0, 1).distanceTo(wave.center.multiply(1, 0, 1));
                boolean grounded = victim.getY() - wave.center.y < 0.8;
                if (Math.abs(d - wave.radius) < 0.9 && grounded && wave.hit.add(victim.getId())) {
                    victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.MOONLIGHT, this), wave.damage);
                    Combat.blast(victim, wave.center, 1.0, 0.6);
                }
            }
            if (wave.radius >= 22.0) {
                it.remove();
            }
        }
    }

    /** A beam of crushing gravity that tracks its target and drags it in. */
    private void tickLance(ServerLevel level, @Nullable LivingEntity target, boolean waning) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.4));
        Vec3 core = this.corePosition();
        if (target != null) {
            this.lookAtTarget(target, 4.0F);
            Vec3 aim = target.position().add(0, 0.8, 0);
            double speed = waning ? 0.42 : 0.28;
            Vec3 delta = aim.subtract(this.beamPoint);
            if (delta.length() > speed) {
                delta = delta.normalize().scale(speed);
            }
            this.beamPoint = this.beamPoint.add(delta);
        }
        if (this.attackTicks < LANCE_CHARGE) {
            return;
        }
        Vec3 dir = this.beamPoint.subtract(core).normalize();
        Vec3 end = core.add(dir.scale(40.0));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(core, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        end = hit.getLocation();
        this.entityData.set(DATA_BEAM_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        if (this.attackTicks % 8 == 0) {
            level.playSound(null, end.x, end.y, end.z, MoonSounds.MATRIARCH_LANCE.get(), SoundSource.HOSTILE, 2.0F, 1.4F);
        }
        double length = core.distanceTo(end);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(core, end).inflate(1.5),
            e -> e != this && Combat.canHit(level, this, e))) {
            Vec3 rel = victim.position().add(0, victim.getBbHeight() * 0.5, 0).subtract(core);
            double along = rel.dot(dir);
            if (along < 0 || along > length || rel.subtract(dir.scale(along)).length() > 1.4) {
                continue;
            }
            Vec3 pull = core.subtract(victim.position()).normalize().scale(0.25);
            victim.setDeltaMovement(victim.getDeltaMovement().scale(0.7).add(pull));
            victim.hurtMarked = true;
            if (this.attackTicks % 8 == 0) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.GRAVITY, this), 5.0F);
            }
        }
        if (this.attackTicks % 2 == 0) {
            level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), true, true, end.x, end.y, end.z, 4, 0.3, 0.3, 0.3, 0.06);
        }
        if (this.attackTicks >= LANCE_END) {
            this.finishAttack(40);
        }
    }

    private void tickVolley(ServerLevel level, @Nullable LivingEntity target, boolean waning) {
        this.hover(target, 0.4);
        int interval = waning ? 3 : 5;
        if (target != null && this.attackTicks >= 10 && this.attackTicks <= 38 && this.attackTicks % interval == 0) {
            Vec3 core = this.corePosition();
            double a = this.attackTicks * 0.9;
            Vec3 from = core.add(Math.cos(a) * 2.0, 0.5, Math.sin(a) * 2.0);
            Vec3 aim = target.getEyePosition().subtract(from).add(this.random.nextGaussian() * 2, 2.0, this.random.nextGaussian() * 2);
            MoonletEntity.shoot(level, this, from, aim, 0.7F, 7.0F, MoonletEntity.Kind.VOLLEY, target);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, 1.4F);
        }
        if (this.attackTicks >= 45) {
            this.finishAttack(30);
        }
    }

    private void tickCrescent(ServerLevel level, @Nullable LivingEntity target) {
        this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        if (target != null && this.attackTicks < 12) {
            this.lookAtTarget(target, 15.0F);
        }
        if (target != null && (this.attackTicks == 12 || this.attackTicks == 24)) {
            Vec3 from = this.corePosition();
            for (int i = -2; i <= 2; i++) {
                Vec3 aim = target.getEyePosition().subtract(from).normalize().yRot(i * 0.18F);
                MoonletEntity.shoot(level, this, from, aim, 1.5F, 8.0F, MoonletEntity.Kind.CRESCENT, null);
            }
            level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0F, 0.6F);
        }
        if (this.attackTicks >= 34) {
            this.finishAttack(25);
        }
    }

    private void tickTeleport(ServerLevel level, @Nullable LivingEntity target) {
        if (this.attackTicks == 1) {
            Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), this.corePosition(), 0.6, 80, 0.5);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_TELEPORT.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
        }
        if (this.attackTicks == 10 && target != null) {
            Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(6.0)).add(0, 3.0, 0);
            behind = new Vec3(behind.x, Math.max(behind.y, this.floorY + 2.0), behind.z);
            this.teleportTo(behind.x, behind.y, behind.z);
            this.lookAtTarget(target, 180.0F);
            Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), this.corePosition(), 0.6, 80, 0.5);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_TELEPORT.get(), SoundSource.HOSTILE, 3.0F, 1.3F);
            this.setAttack(ATTACK_CRESCENT);
            this.attackTicks = 6;
            return;
        }
        if (this.attackTicks > 20) {
            this.finishAttack(20);
        }
    }

    /** Rises, then plunges onto her target's position and bursts outward. */
    private void tickDive(ServerLevel level, @Nullable LivingEntity target) {
        if (this.attackTicks <= 12) {
            this.setDeltaMovement(0, 0.25, 0);
            if (target != null) {
                this.diveTarget = target.position();
                this.lookAtTarget(target, 20.0F);
            }
            return;
        }
        if (this.attackTicks <= 24) {
            Vec3 to = this.diveTarget.subtract(this.position());
            this.setDeltaMovement(to.scale(0.22));
            if (to.length() < 1.5 || this.attackTicks == 24) {
                Vec3 at = this.position();
                for (LivingEntity victim : Combat.targetsAround(level, this, at.add(0, 1, 0), 5.0)) {
                    victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.GRAVITY, this), 13.0F);
                    Combat.blast(victim, at, 1.4, 0.8);
                }
                Fx.ring(level, ModParticles.MOON_DUST.get(), new Vec3(at.x, this.floorY + 1.2, at.z), 1.0, 60, 0.8, 0.02);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 2, 0.5, 0.2, 0.5, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.7F);
                Fx.shake(level, at, 32.0, 1.2F, 14);
                this.attackTicks = 25;
            }
            return;
        }
        this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
        if (this.attackTicks >= 40) {
            this.finishAttack(30);
        }
    }

    // --- Inversion ---------------------------------------------------------------------------------------------------

    private void beginInversion(ServerLevel level) {
        this.inversionDone = true;
        this.setPhase(PHASE_INVERSION);
        this.setAttack(ATTACK_NONE);
        this.inversionHeight = 16.0;
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_INVERSION.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
        SunFx.titleNear(level, this.position(), 128.0, Component.translatable("entity.starforged.pale_matriarch.inversion")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            Component.translatable("entity.starforged.pale_matriarch.inversion_sub").withStyle(ChatFormatting.WHITE), 10, 50, 15);
        Vec3 center = this.arenaCenter();
        for (int i = 0; i < 3; i++) {
            double a = Math.PI / 2 + i * Math.PI * 2 / 3;
            LunarAnchorEntity anchor = MoonEntities.LUNAR_ANCHOR.get().create(level, EntitySpawnReason.EVENT);
            if (anchor == null) {
                continue;
            }
            anchor.bind(this);
            anchor.snapTo(center.x + Math.cos(a) * 10.0, this.floorY + 1.0, center.z + Math.sin(a) * 10.0, 0.0F, 0.0F);
            level.addFreshEntity(anchor);
            this.anchors.add(anchor.getId());
            Fx.column(level, ModParticles.LUNAR_GLIMMER.get(), anchor.position(), 6.0, 40, 0.4, 0.1);
        }
    }

    private void tickInversion(ServerLevel level) {
        Vec3 center = this.arenaCenter();
        Vec3 want = center.add(0, this.inversionHeight, 0);
        this.setDeltaMovement(want.subtract(this.position()).scale(0.08));
        if (this.tickCount % 20 == 0) {
            for (Player player : this.playersInArena(level)) {
                MoonGravity.invert(player, 45);
            }
        }
        if (this.tickCount % 70 == 0) {
            LivingEntity target = this.findTarget(level);
            if (target != null) {
                Vec3 from = this.corePosition();
                for (int i = 0; i < 3; i++) {
                    MoonletEntity.shoot(level, this, from, target.getEyePosition().subtract(from).add(this.random.nextGaussian() * 3, 0,
                        this.random.nextGaussian() * 3), 0.6F, 6.0F, MoonletEntity.Kind.VOLLEY, target);
                }
            }
        }
        this.anchors.removeIf(id -> !(level.getEntity(id) instanceof LunarAnchorEntity a) || !a.isAlive());
        if (this.anchors.isEmpty()) {
            this.crash(level);
        }
    }

    public void onAnchorShattered(ServerLevel level) {
        this.inversionHeight = Math.max(4.0, this.inversionHeight - 4.0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_HURT.get(), SoundSource.HOSTILE, 4.0F, 1.2F);
    }

    private void crash(ServerLevel level) {
        Vec3 center = this.arenaCenter();
        this.teleportTo(this.getX(), this.floorY + 1.0, this.getZ());
        this.setPhase(PHASE_STUNNED);
        this.stunTicks = 160;
        for (Player player : this.playersInArena(level)) {
            MoonGravity.release(player);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_HURT.get(), SoundSource.HOSTILE, 6.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1, this.getZ(), 1, 0, 0, 0, 0);
        Fx.ring(level, ModParticles.MOON_DUST.get(), new Vec3(this.getX(), this.floorY + 1.2, this.getZ()), 1.0, 80, 1.0, 0.02);
        Fx.shake(level, center, 64.0, 2.0F, 25);
        SunFx.messageNear(level, center, 128.0, Component.translatable("entity.starforged.pale_matriarch.stunned").withStyle(ChatFormatting.AQUA));
    }

    private void tickStunned(ServerLevel level) {
        this.setDeltaMovement(0, Math.min(0, this.getY() - (this.floorY + 1.0)) * -0.1, 0);
        if (this.tickCount % 5 == 0) {
            level.sendParticles(ModParticles.MOON_DUST.get(), this.getX(), this.getY() + 4.5, this.getZ(), 6, 0.6, 0.2, 0.6, 0.02);
        }
        if (--this.stunTicks <= 0) {
            this.setPhase(PHASE_WANING);
            this.bossEvent.setColor(BossEvent.BossBarColor.WHITE);
            this.roar(level);
            SunFx.messageNear(level, this.position(), 128.0, Component.translatable("entity.starforged.pale_matriarch.waning")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            this.attackCooldown = 20;
        }
    }

    // --- New Moon & Moonfall --------------------------------------------------------------------------------------

    private void beginNewMoon(ServerLevel level) {
        this.newMoonDone = true;
        this.setPhase(PHASE_NEW_MOON);
        this.setAttack(ATTACK_NONE);
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        this.bossEvent.setDarkenScreen(true);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_INVERSION.get(), SoundSource.HOSTILE, 6.0F, 0.6F);
        SunFx.titleNear(level, this.position(), 128.0, Component.translatable("entity.starforged.pale_matriarch.new_moon")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
            Component.translatable("entity.starforged.pale_matriarch.new_moon_sub").withStyle(ChatFormatting.GRAY), 10, 60, 15);
        Vec3 center = this.arenaCenter();
        for (int i = 0; i < 3; i++) {
            MatriarchEchoEntity echo = MoonEntities.MATRIARCH_ECHO.get().create(level, EntitySpawnReason.EVENT);
            if (echo == null) {
                continue;
            }
            double a = i * Math.PI * 2 / 3;
            Vec3 post = center.add(Math.cos(a) * 9, 4.0, Math.sin(a) * 9);
            echo.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
            echo.bind(this, post);
            level.addFreshEntity(echo);
            this.echoes.add(echo.getId());
        }
        this.shuffleTimer = 0;
    }

    private void tickNewMoon(ServerLevel level) {
        if (this.tickCount % 40 == 0) {
            for (Player player : this.playersInArena(level)) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false), this);
            }
        }
        this.echoes.removeIf(id -> !(level.getEntity(id) instanceof MatriarchEchoEntity e) || !e.isAlive());
        if (++this.shuffleTimer >= 260 && this.attack() == ATTACK_NONE) {
            this.shuffleTimer = 0;
            this.shuffle(level);
        }
    }

    /** She and her echoes trade places in a flash of moonlight. */
    private void shuffle(ServerLevel level) {
        if (this.echoes.size() < 3) {
            for (int i = this.echoes.size(); i < 3; i++) {
                MatriarchEchoEntity echo = MoonEntities.MATRIARCH_ECHO.get().create(level, EntitySpawnReason.EVENT);
                if (echo != null) {
                    echo.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                    echo.bind(this, this.position());
                    level.addFreshEntity(echo);
                    this.echoes.add(echo.getId());
                }
            }
        }
        Vec3 center = this.arenaCenter();
        double offset = this.random.nextDouble() * Math.PI * 2;
        int slot = this.random.nextInt(this.echoes.size() + 1);
        int i = 0;
        for (int s = 0; s <= this.echoes.size(); s++) {
            double a = offset + s * Math.PI * 2 / (this.echoes.size() + 1);
            Vec3 post = center.add(Math.cos(a) * 9, 4.0, Math.sin(a) * 9);
            level.sendParticles(ModParticles.LUNAR_GLIMMER.get(), post.x, post.y + 2, post.z, 40, 0.6, 1.2, 0.6, 0.05);
            if (s == slot) {
                this.teleportTo(post.x, post.y, post.z);
            } else if (i < this.echoes.size() && level.getEntity(this.echoes.get(i++)) instanceof MatriarchEchoEntity echo) {
                echo.moveTo(post);
            }
        }
        level.playSound(null, center.x, center.y + 4, center.z, MoonSounds.MATRIARCH_TELEPORT.get(), SoundSource.HOSTILE, 4.0F, 0.8F);
    }

    public void onEchoShattered(ServerLevel level, MatriarchEchoEntity echo) {
        this.echoes.remove((Integer) echo.getId());
    }

    private void beginMoonfall(ServerLevel level) {
        this.moonfallDone = true;
        FallingMoonEntity.summon(level, this.arenaCenter(), this);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), MoonSounds.MATRIARCH_MOONFALL.get(), SoundSource.HOSTILE, 8.0F, 0.6F);
        SunFx.titleNear(level, this.position(), 128.0, Component.translatable("entity.starforged.pale_matriarch.moonfall")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
            Component.translatable("entity.starforged.pale_matriarch.moonfall_sub").withStyle(ChatFormatting.WHITE), 10, 60, 15);
    }

    // --- Damage & death --------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        int phase = this.phase();
        Entity attacker = source.getEntity();
        if (phase == PHASE_EMERGE || attacker == this || attacker instanceof MatriarchEchoEntity || source.is(DamageTypeTags.IS_FALL)
            || source.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        }
        if (phase == PHASE_INVERSION) {
            if (attacker instanceof Player player) {
                player.sendOverlayMessage(Component.translatable("entity.starforged.pale_matriarch.immune").withStyle(ChatFormatting.AQUA));
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1.0F, 0.6F);
            }
            return false;
        }
        if (phase == PHASE_STUNNED) {
            damage *= 1.5F;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive()) {
            float frac = this.getHealth() / this.getMaxHealth();
            if (!this.inversionDone && frac <= 0.66F) {
                this.beginInversion(level);
            } else if (!this.newMoonDone && this.inversionDone && phase != PHASE_STUNNED && frac <= 0.33F) {
                this.beginNewMoon(level);
            } else if (!this.moonfallDone && this.newMoonDone && frac <= 0.15F) {
                this.beginMoonfall(level);
            }
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
            this.bossEvent.setDarkenScreen(false);
            SunFx.messageNear(level, this.position(), 128.0, Component.translatable("entity.starforged.pale_matriarch.dying").withStyle(ChatFormatting.AQUA));
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
        this.setDeltaMovement(0, 0.02, 0);
        Vec3 core = this.corePosition();
        if (this.deathTime % 3 == 0) {
            Vec3 dir = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian()).normalize();
            Fx.line(level, ModParticles.LUNAR_GLIMMER.get(), core, core.add(dir.scale(10.0)), 0.6);
        }
        if (this.deathTime % 5 == 0) {
            level.playSound(null, core.x, core.y, core.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F + this.random.nextFloat() * 0.5F);
        }
        if (this.deathTime == 50) {
            level.playSound(null, core.x, core.y, core.z, MoonSounds.MATRIARCH_DEATH.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        }
        if (this.deathTime >= 100 && !this.isRemoved()) {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE0F0FF), true, true, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), core, 1.0, 300, 1.4);
            Fx.sphere(level, ModParticles.MOON_DUST.get(), core, 1.0, 150, 0.9);
            Fx.shake(level, core, 96.0, 2.5F, 40);
            DamageSource source = this.deathSource != null ? this.deathSource : this.damageSources().generic();
            super.dropAllDeathLoot(level, source);
            SunFx.messageNear(level, core, 128.0, Component.translatable("entity.starforged.pale_matriarch.defeated")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            this.cleanup(level);
            level.broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    private void retreat(ServerLevel level) {
        SunFx.messageNear(level, this.position(), 128.0, Component.translatable("entity.starforged.pale_matriarch.retreat").withStyle(ChatFormatting.AQUA));
        Fx.burst(level, ModParticles.LUNAR_GLIMMER.get(), this.position().add(0, 3, 0), 200, 2.0, 0.3);
        ItemEntity sigil = new ItemEntity(level, this.home.getX() + 0.5, this.home.getY() + 1.5, this.home.getZ() + 0.5,
            new ItemStack(MoonItems.TIDAL_SIGIL.get()));
        sigil.setUnlimitedLifetime();
        sigil.setGlowingTag(true);
        level.addFreshEntity(sigil);
        this.cleanup(level);
        this.discard();
    }

    private void cleanup(ServerLevel level) {
        for (int id : this.anchors) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        for (int id : this.echoes) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        this.anchors.clear();
        this.echoes.clear();
        this.waves.clear();
        for (Player player : this.playersInArena(level)) {
            MoonGravity.release(player);
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
        return MoonSounds.MATRIARCH_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MoonSounds.MATRIARCH_ROAR.get();
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        int phase = this.phase();
        output.putInt("phase", phase == PHASE_INVERSION || phase == PHASE_STUNNED ? PHASE_WANING : phase == PHASE_EMERGE ? PHASE_WAXING : phase);
        output.putBoolean("inversion_done", this.inversionDone);
        output.putBoolean("new_moon_done", this.newMoonDone);
        output.putBoolean("moonfall_done", this.moonfallDone);
        output.putLong("home", this.home.asLong());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPhase(input.getIntOr("phase", PHASE_WAXING));
        this.inversionDone = input.getBooleanOr("inversion_done", false);
        this.newMoonDone = input.getBooleanOr("new_moon_done", false);
        this.moonfallDone = input.getBooleanOr("moonfall_done", false);
        BlockPos stored = BlockPos.of(input.getLongOr("home", BlockPos.ZERO.asLong()));
        if (!stored.equals(BlockPos.ZERO)) {
            this.setHome(stored);
        }
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}

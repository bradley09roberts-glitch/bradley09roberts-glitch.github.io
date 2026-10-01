package com.starforged.tempest.boss;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.tempest.TempestBlocks;
import com.starforged.tempest.TempestEntities;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestItems;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.entity.ArcBeamEntity;
import com.starforged.tempest.entity.CycloneEntity;
import com.starforged.tempest.event.TempestAbilities;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Veyr, the Tempest Regent. The final boss of Tempestforged, fought on the summit of a Tempest Citadel.
 * <ol>
 *     <li><b>Assembly</b> - his empty armour is struck together by lightning, piece by piece.</li>
 *     <li><b>Tempest</b> (100-60%) - Thunderstep, spear combos, storm volleys, the Arc Lance and the Tornado Wall.</li>
 *     <li><b>Conduction</b> (60%) - he rises behind a storm shield and calls bolts down on you. Raise the Storm Conductors
 *     around the arena: each raised rod steals a bolt and hurls it back into him. Four hits break the shield and he
 *     crashes down, stunned and taking extra damage.</li>
 *     <li><b>Shattered Sky</b> - the summit breaks apart into floating fragments and he hunts you across them.</li>
 *     <li><b>The Last Thunder</b> (15%) - lightning falls everywhere at once except inside the drifting Eyes of the
 *     Storm. Keep moving with them.</li>
 * </ol>
 * He has 1024 health but shrugs off a fifth of every blow - about 1300 in all.
 */
public class VeyrEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(VeyrEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(VeyrEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ASSEMBLE = SynchedEntityData.defineId(VeyrEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SHIELD = SynchedEntityData.defineId(VeyrEntity.class, EntityDataSerializers.INT);

    public static final int PHASE_ASSEMBLE = 0;
    public static final int PHASE_TEMPEST = 1;
    public static final int PHASE_CONDUCTION = 2;
    public static final int PHASE_STUNNED = 3;
    public static final int PHASE_SHATTERED = 4;
    public static final int PHASE_LAST_THUNDER = 5;

    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_THUNDERSTEP = 1;
    public static final int ATTACK_COMBO = 2;
    public static final int ATTACK_VOLLEY = 3;
    public static final int ATTACK_LANCE = 4;
    public static final int ATTACK_TORNADO = 5;

    public static final int ASSEMBLE_TICKS = 100;
    public static final int LANCE_CHARGE = 30;
    public static final int SHIELD_HITS = 4;
    private static final double ARENA = 15.0;
    private static final float DAMAGE_TAKEN = 1024.0F / 1300.0F;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random),
        Component.translatable("entity.starforged.veyr").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
        BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_20);

    private BlockPos home = BlockPos.ZERO;
    private int attackTicks;
    private int attackCooldown = 40;
    private int lastAttack = ATTACK_NONE;
    private int phaseTicks;
    private int idleNoPlayers;
    private boolean conductionDone;
    private boolean lastThunderDone;
    private Vec3 hover = Vec3.ZERO;
    private Vec3 lanceAim = Vec3.ZERO;
    private final List<Integer> props = new ArrayList<>();
    private final List<BlockPos> brokenPos = new ArrayList<>();
    private final List<BlockState> brokenState = new ArrayList<>();
    private @Nullable DamageSource deathSource;

    public int clientAttackTicks;
    public int clientPhaseTicks;

    public VeyrEntity(EntityType<? extends VeyrEntity> type, Level level) {
        super(type, level);
        this.xpReward = 1500;
        this.setPersistenceRequired();
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1024.0)
            .add(Attributes.ARMOR, 16.0)
            .add(Attributes.ARMOR_TOUGHNESS, 8.0)
            .add(Attributes.ATTACK_DAMAGE, 14.0)
            .add(Attributes.FOLLOW_RANGE, 80.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.STEP_HEIGHT, 1.5)
            .add(Attributes.MOVEMENT_SPEED, 0.32);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, PHASE_ASSEMBLE);
        builder.define(DATA_ATTACK, ATTACK_NONE);
        builder.define(DATA_ASSEMBLE, 0);
        builder.define(DATA_SHIELD, 0);
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

    public int assembleTicks() {
        return this.entityData.get(DATA_ASSEMBLE);
    }

    public int shield() {
        return this.entityData.get(DATA_SHIELD);
    }

    public void setHome(BlockPos altar) {
        this.home = altar.immutable();
    }

    private Vec3 arenaCenter() {
        return new Vec3(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5);
    }

    private void setPhase(int phase) {
        this.entityData.set(DATA_PHASE, phase);
        this.phaseTicks = 0;
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

    private List<Player> playersInArena(ServerLevel level) {
        return level.getEntitiesOfClass(Player.class, new AABB(this.home).inflate(ARENA + 12, 30, ARENA + 12),
            p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    private @Nullable Player target(ServerLevel level) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : this.playersInArena(level)) {
            double d = p.distanceToSqr(this);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // --- Tick ------------------------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.clientAttackTicks++;
            this.clientPhaseTicks++;
            int sparks = this.phase() == PHASE_ASSEMBLE ? 3 : 1;
            for (int i = 0; i < sparks; i++) {
                this.level().addParticle(ModParticles.STATIC_SPARK.get(), this.getRandomX(1.0), this.getY() + this.random.nextDouble() * 3.8,
                    this.getRandomZ(1.0), 0, 0, 0);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.home.equals(BlockPos.ZERO)) {
            this.setHome(this.blockPosition());
        }
        if (this.target(level) == null && level.getNearestPlayer(this, 96.0) == null) {
            if (++this.idleNoPlayers > 1200) {
                this.retreat(level);
                return;
            }
        } else {
            this.idleNoPlayers = 0;
        }
        this.phaseTicks++;
        switch (this.phase()) {
            case PHASE_ASSEMBLE -> this.tickAssemble(level);
            case PHASE_CONDUCTION -> this.tickConduction(level);
            case PHASE_STUNNED -> this.tickStunned(level);
            case PHASE_LAST_THUNDER -> this.tickLastThunder(level);
            default -> this.tickCombat(level);
        }
    }

    private void tickAssemble(ServerLevel level) {
        int t = this.assembleTicks() + 1;
        this.entityData.set(DATA_ASSEMBLE, t);
        this.setDeltaMovement(Vec3.ZERO);
        if (t % 20 == 1 && t < ASSEMBLE_TICKS) {
            // Each strike welds on another piece of armour.
            StormNetwork.visualBolt(level, this.blockPosition());
            Fx.sphere(level, ModParticles.STATIC_SPARK.get(), this.position().add(0, 2.0, 0), 1.5, 40, 0.3);
        }
        if (t >= ASSEMBLE_TICKS) {
            this.setPhase(PHASE_TEMPEST);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.VEYR_ROAR.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
            SunFx.titleNear(level, this.position(), 128.0, Component.translatable("entity.starforged.veyr").withStyle(ChatFormatting.AQUA,
                ChatFormatting.BOLD), Component.translatable("entity.starforged.veyr.title").withStyle(ChatFormatting.GRAY), 10, 60, 20);
            for (Player player : this.playersInArena(level)) {
                Combat.blast(player, this.position(), 1.2, 0.5);
            }
            Fx.shake(level, this.position(), 48.0, 1.2F, 20);
        }
    }

    private void tickCombat(ServerLevel level) {
        Player target = this.target(level);
        boolean flying = this.phase() == PHASE_SHATTERED;
        this.setNoGravity(flying);
        if (target == null) {
            this.setAttack(ATTACK_NONE);
            if (flying) {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            }
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.lookAt(target, 30.0F, 30.0F);
        int attack = this.attack();
        if (attack != ATTACK_NONE) {
            this.attackTicks++;
            this.tickAttack(level, target, attack);
            return;
        }
        if (flying) {
            if (this.phaseTicks % 90 == 1 || this.hover.equals(Vec3.ZERO)) {
                this.hover = this.fragmentNear(target.position()).add(0, 3.0, 0);
            }
            Vec3 to = this.hover.subtract(this.position());
            this.setDeltaMovement(to.scale(0.08));
        } else if (this.distanceTo(target) > 3.5) {
            this.getNavigation().moveTo(target, 1.1);
        } else {
            this.getNavigation().stop();
        }
        if (--this.attackCooldown > 0) {
            return;
        }
        double dist = this.distanceTo(target);
        int next;
        int roll = this.random.nextInt(10);
        if (dist < 4.0 && !flying) {
            next = roll < 6 ? ATTACK_COMBO : ATTACK_TORNADO;
        } else if (roll < 3) {
            next = ATTACK_THUNDERSTEP;
        } else if (roll < 5) {
            next = ATTACK_VOLLEY;
        } else if (roll < 8) {
            next = ATTACK_LANCE;
        } else {
            next = ATTACK_TORNADO;
        }
        if (next == this.lastAttack) {
            next = next == ATTACK_LANCE ? ATTACK_VOLLEY : ATTACK_LANCE;
        }
        this.lastAttack = next;
        this.getNavigation().stop();
        this.setAttack(next);
    }

    private void tickAttack(ServerLevel level, Player target, int attack) {
        int t = this.attackTicks;
        switch (attack) {
            case ATTACK_THUNDERSTEP -> {
                if (t == 1) {
                    this.thunderstep(level, target);
                } else if (t == 8 && this.distanceTo(target) < 4.5) {
                    this.swing(InteractionHand.MAIN_HAND);
                    target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, this), 12.0F);
                    Combat.blast(target, this.position(), 1.2, 0.5);
                } else if (t > 16) {
                    this.endAttack(30);
                }
            }
            case ATTACK_COMBO -> {
                if (t == 4 || t == 12 || t == 22) {
                    Vec3 dir = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
                    this.setDeltaMovement(dir.scale(0.7).add(0, this.getDeltaMovement().y, 0));
                    this.swing(InteractionHand.MAIN_HAND);
                    if (this.distanceTo(target) < 4.0) {
                        target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, this), t == 22 ? 14.0F : 10.0F);
                        if (t == 22) {
                            Combat.blast(target, this.position(), 1.4, 0.6);
                            TempestFx.strike(level, target.position(), this, 6.0F, 2.0);
                        }
                    }
                } else if (t > 30) {
                    this.endAttack(25);
                }
            }
            case ATTACK_VOLLEY -> {
                if (t == 1) {
                    level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.VEYR_ROAR.get(), SoundSource.HOSTILE, 3.0F, 1.4F);
                    Vec3 c = target.position();
                    this.telegraphStrike(level, c, 22);
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3 + this.random.nextDouble() * 0.4;
                        double r = 3.0 + this.random.nextDouble() * 3.0;
                        this.telegraphStrike(level, c.add(Math.cos(a) * r, 0, Math.sin(a) * r), 26 + i * 2);
                    }
                } else if (t > 40) {
                    this.endAttack(35);
                }
            }
            case ATTACK_LANCE -> {
                if (t == 1) {
                    level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.CANNON_CHARGE.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
                }
                if (t < LANCE_CHARGE) {
                    this.lanceAim = target.getEyePosition();
                    Fx.burst(level, ModParticles.STATIC_SPARK.get(), this.spearTip(), 3, 0.3, 0.05);
                } else if (t == LANCE_CHARGE) {
                    this.fireLance(level);
                } else if (t > LANCE_CHARGE + 15) {
                    this.endAttack(35);
                }
            }
            case ATTACK_TORNADO -> {
                if (t == 1) {
                    this.tornadoWall(level, 4, 200);
                } else if (t > 20) {
                    this.endAttack(60);
                }
            }
            default -> this.endAttack(20);
        }
    }

    private void endAttack(int cooldown) {
        this.setAttack(ATTACK_NONE);
        this.attackCooldown = this.phase() == PHASE_SHATTERED ? cooldown - 10 : cooldown;
    }

    public Vec3 spearTip() {
        Vec3 look = Vec3.directionFromRotation(0, this.yBodyRot);
        return this.position().add(look.scale(0.9)).add(0, 3.6, 0);
    }

    private void thunderstep(ServerLevel level, LivingEntity target) {
        Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(2.5));
        if (this.phase() == PHASE_SHATTERED) {
            behind = behind.add(0, 1.5, 0);
        }
        StormNetwork.visualBolt(level, this.blockPosition());
        this.teleportTo(behind.x, behind.y, behind.z);
        TempestFx.strike(level, behind, this, 8.0F, 2.5);
        level.playSound(null, behind.x, behind.y, behind.z, TempestSounds.VEYR_THUNDERSTEP.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
    }

    /** A strike that lands after {@code delay} ticks; a column of sparks marks the spot meanwhile. */
    private void telegraphStrike(ServerLevel level, Vec3 at, int delay) {
        Fx.column(level, ModParticles.STATIC_SPARK.get(), at, 4.0, 20, 0.15, 0.0);
        TempestAbilities.scheduleStrike(level, delay, at, this, 12.0F, 2.5);
    }

    private void fireLance(ServerLevel level) {
        Vec3 from = this.spearTip();
        Vec3 dir = this.lanceAim.subtract(from).normalize();
        Vec3 end = level.clip(new ClipContext(from, from.add(dir.scale(48.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
        ArcBeamEntity.fire(level, from, end, 0.8F);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.2),
            e -> e != this && Combat.canHit(level, this, e))) {
            Vec3 p = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
            Vec3 ab = end.subtract(from);
            double s = Math.max(0, Math.min(1, p.subtract(from).dot(ab) / ab.lengthSqr()));
            if (p.distanceTo(from.add(ab.scale(s))) < 1.4) {
                victim.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, this), 16.0F);
            }
        }
        TempestFx.thunder(level, end, 3.0F, 0.6F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.CANNON_FIRE.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    private void tornadoWall(ServerLevel level, int count, int life) {
        Vec3 c = this.arenaCenter();
        double base = this.random.nextDouble() * Math.PI * 2;
        double speed = (this.random.nextBoolean() ? 1 : -1) * 0.025;
        for (int i = 0; i < count; i++) {
            CycloneEntity.wall(level, this, c, 11.0, base + i * Math.PI * 2 / count, speed, life);
        }
        level.playSound(null, c.x, c.y, c.z, TempestSounds.VEYR_TORNADO.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
        SunFx.messageNear(level, c, 64.0, Component.translatable("entity.starforged.veyr.tornado").withStyle(ChatFormatting.AQUA));
    }

    // --- Conduction ------------------------------------------------------------------------------------------------

    private void beginConduction(ServerLevel level) {
        this.conductionDone = true;
        this.setAttack(ATTACK_NONE);
        this.setPhase(PHASE_CONDUCTION);
        this.entityData.set(DATA_SHIELD, SHIELD_HITS);
        this.setNoGravity(true);
        Vec3 c = this.arenaCenter();
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            StormConductorEntity conductor = TempestEntities.STORM_CONDUCTOR.get().create(level, EntitySpawnReason.EVENT);
            if (conductor != null) {
                conductor.bind(this);
                conductor.snapTo(c.x + Math.cos(a) * 11.0, c.y, c.z + Math.sin(a) * 11.0, 0.0F, 0.0F);
                level.addFreshEntity(conductor);
                this.props.add(conductor.getId());
                StormNetwork.visualBolt(level, conductor.blockPosition());
            }
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.VEYR_ROAR.get(), SoundSource.HOSTILE, 6.0F, 0.8F);
        SunFx.titleNear(level, c, 128.0, Component.translatable("entity.starforged.veyr.conduction").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            Component.translatable("entity.starforged.veyr.conduction_sub").withStyle(ChatFormatting.WHITE), 10, 60, 15);
    }

    private void tickConduction(ServerLevel level) {
        Vec3 want = this.arenaCenter().add(0, 9.0, 0);
        this.setDeltaMovement(want.subtract(this.position()).scale(0.08));
        if (this.phaseTicks % 70 != 0) {
            return;
        }
        // Thunderstorm: bolts for every player - and every raised conductor steals one and throws it back.
        for (Player player : this.playersInArena(level)) {
            this.telegraphStrike(level, player.position(), 20);
        }
        for (int id : this.props) {
            if (level.getEntity(id) instanceof StormConductorEntity conductor && conductor.isRaised()) {
                conductor.lower();
                Vec3 rod = conductor.position().add(0, 3.0, 0);
                StormNetwork.visualBolt(level, conductor.blockPosition());
                StormNetwork.arcTo(level, rod, this.position().add(0, 2.0, 0));
                level.playSound(null, rod.x, rod.y, rod.z, TempestSounds.CONDUCTOR_REDIRECT.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
                int shield = this.shield() - 1;
                this.entityData.set(DATA_SHIELD, shield);
                level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.VEYR_HURT.get(), SoundSource.HOSTILE, 4.0F, 1.0F);
                if (shield <= 0) {
                    this.crash(level);
                    return;
                }
            }
        }
    }

    private void crash(ServerLevel level) {
        this.setPhase(PHASE_STUNNED);
        this.setNoGravity(false);
        this.discardProps(level);
        this.teleportTo(this.getX(), this.arenaCenter().y + 0.5, this.getZ());
        Fx.shake(level, this.position(), 48.0, 1.4F, 20);
        Fx.sphere(level, ModParticles.STATIC_SPARK.get(), this.position().add(0, 2, 0), 1.0, 120, 0.8);
        SunFx.messageNear(level, this.position(), 96.0, Component.translatable("entity.starforged.veyr.stunned").withStyle(ChatFormatting.AQUA,
            ChatFormatting.BOLD));
    }

    private void tickStunned(ServerLevel level) {
        this.setDeltaMovement(this.getDeltaMovement().multiply(0, 1, 0));
        if (this.phaseTicks % 5 == 0) {
            Fx.burst(level, ModParticles.STATIC_SPARK.get(), this.position().add(0, 1.5, 0), 6, 0.6, 0.05);
        }
        if (this.phaseTicks > 160) {
            this.shatter(level);
            this.setPhase(PHASE_SHATTERED);
            this.attackCooldown = 30;
        }
    }

    // --- Shattered Sky ---------------------------------------------------------------------------------------------

    /** Fragment centres (relative to the arena centre): the middle and a ring of six. */
    private List<Vec3> fragments() {
        List<Vec3> list = new ArrayList<>();
        Vec3 c = this.arenaCenter();
        list.add(c);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            list.add(c.add(Math.cos(a) * 9.0, 0, Math.sin(a) * 9.0));
        }
        return list;
    }

    private Vec3 fragmentNear(Vec3 pos) {
        Vec3 best = this.arenaCenter();
        double bestDist = Double.MAX_VALUE;
        for (Vec3 f : this.fragments()) {
            double d = f.distanceToSqr(pos.x, f.y, pos.z);
            if (d < bestDist) {
                bestDist = d;
                best = f;
            }
        }
        return best;
    }

    /** Breaks the summit floor into floating fragments (restored when the fight ends). */
    private void shatter(ServerLevel level) {
        if (!this.brokenPos.isEmpty()) {
            return;
        }
        Vec3 c = this.arenaCenter();
        List<Vec3> frags = this.fragments();
        int r = (int) ARENA - 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                Vec3 col = c.add(dx, 0, dz);
                boolean keep = false;
                for (int i = 0; i < frags.size(); i++) {
                    double fr = i == 0 ? 3.5 : 2.6;
                    if (frags.get(i).distanceToSqr(col.x, frags.get(i).y, col.z) < fr * fr) {
                        keep = true;
                        break;
                    }
                }
                if (keep) {
                    continue;
                }
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos p = BlockPos.containing(col.x, c.y - dy, col.z);
                    BlockState state = level.getBlockState(p);
                    if (state.isAir() || state.getDestroySpeed(level, p) < 0 || state.is(TempestBlocks.TEMPEST_ALTAR.get())) {
                        continue;
                    }
                    this.brokenPos.add(p);
                    this.brokenState.add(state);
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (this.random.nextInt(6) == 0) {
                        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), p.getX() + 0.5,
                            p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }
        level.playSound(null, c.x, c.y, c.z, TempestSounds.VEYR_SHATTER.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
        Fx.shake(level, c, 64.0, 2.0F, 30);
        SunFx.titleNear(level, c, 128.0, Component.translatable("entity.starforged.veyr.shattered").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
            Component.translatable("entity.starforged.veyr.shattered_sub").withStyle(ChatFormatting.WHITE), 10, 50, 15);
    }

    private void restoreFloor(ServerLevel level) {
        for (int i = 0; i < this.brokenPos.size(); i++) {
            BlockPos p = this.brokenPos.get(i);
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, this.brokenState.get(i), Block.UPDATE_ALL);
            }
        }
        this.brokenPos.clear();
        this.brokenState.clear();
    }

    // --- The Last Thunder ------------------------------------------------------------------------------------------

    private void beginLastThunder(ServerLevel level) {
        this.lastThunderDone = true;
        this.setAttack(ATTACK_NONE);
        this.setPhase(PHASE_LAST_THUNDER);
        this.setNoGravity(true);
        Vec3 c = this.arenaCenter();
        for (int i = 0; i < 3; i++) {
            StormEyeEntity eye = TempestEntities.STORM_EYE.get().create(level, EntitySpawnReason.EVENT);
            if (eye != null) {
                eye.drift(c, 8.5, i * Math.PI * 2 / 3, (i % 2 == 0 ? 1 : -1) * (0.012 + i * 0.004), 420);
                level.addFreshEntity(eye);
                this.props.add(eye.getId());
            }
        }
        level.playSound(null, c.x, c.y, c.z, TempestSounds.VEYR_LAST_THUNDER.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        SunFx.titleNear(level, c, 128.0, Component.translatable("entity.starforged.veyr.last_thunder").withStyle(ChatFormatting.RED,
            ChatFormatting.BOLD), Component.translatable("entity.starforged.veyr.last_thunder_sub").withStyle(ChatFormatting.WHITE), 10, 60, 15);
    }

    private void tickLastThunder(ServerLevel level) {
        Vec3 want = this.arenaCenter().add(0, 6.0, 0);
        this.setDeltaMovement(want.subtract(this.position()).scale(0.08));
        if (this.phaseTicks % 10 == 0) {
            Fx.burst(level, ModParticles.STATIC_SPARK.get(), this.spearTip(), 10, 0.4, 0.1);
        }
        if (this.phaseTicks % 50 == 0 && this.phaseTicks <= 400) {
            List<StormEyeEntity> eyes = new ArrayList<>();
            for (int id : this.props) {
                if (level.getEntity(id) instanceof StormEyeEntity eye) {
                    eyes.add(eye);
                }
            }
            for (Player player : this.playersInArena(level)) {
                boolean safe = eyes.stream().anyMatch(e -> e.contains(player.position()));
                if (!safe) {
                    TempestFx.strike(level, player.position(), this, 12.0F, 1.5);
                }
            }
            for (int i = 0; i < 8; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = this.random.nextDouble() * ARENA;
                Vec3 at = this.arenaCenter().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                if (eyes.stream().noneMatch(e -> e.contains(at))) {
                    StormNetwork.visualBolt(level, BlockPos.containing(at));
                }
            }
            TempestFx.thunder(level, this.arenaCenter(), 4.0F, 0.8F);
        }
        if (this.phaseTicks > 420) {
            this.discardProps(level);
            this.setPhase(PHASE_SHATTERED);
            this.attackCooldown = 20;
        }
    }

    private void discardProps(ServerLevel level) {
        for (int id : this.props) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        this.props.clear();
    }

    // --- Damage & death --------------------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        int phase = this.phase();
        Entity attacker = source.getEntity();
        if (phase == PHASE_ASSEMBLE || attacker == this || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_LIGHTNING)
            || source.is(ModDamageTypes.STORM) && !(attacker instanceof Player)) {
            return false;
        }
        if (phase == PHASE_CONDUCTION) {
            if (attacker instanceof Player player) {
                player.sendOverlayMessage(Component.translatable("entity.starforged.veyr.immune").withStyle(ChatFormatting.AQUA));
            }
            return false;
        }
        damage *= DAMAGE_TAKEN;
        if (phase == PHASE_STUNNED) {
            damage *= 1.5F;
        }
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && this.isAlive()) {
            float frac = this.getHealth() / this.getMaxHealth();
            if (!this.conductionDone && frac <= 0.6F) {
                this.beginConduction(level);
            } else if (!this.lastThunderDone && this.conductionDone && phase == PHASE_SHATTERED && frac <= 0.15F) {
                this.beginLastThunder(level);
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
            this.discardProps(level);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), TempestSounds.VEYR_ROAR.get(), SoundSource.HOSTILE, 6.0F, 0.7F);
            SunFx.messageNear(level, this.position(), 128.0, Component.translatable("entity.starforged.veyr.dying").withStyle(ChatFormatting.AQUA));
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
        this.setDeltaMovement(0, 0.03, 0);
        Vec3 core = this.position().add(0, 2.5, 0);
        if (this.deathTime % 8 == 0) {
            double a = this.random.nextDouble() * Math.PI * 2;
            StormNetwork.visualBolt(level, BlockPos.containing(core.add(Math.cos(a) * 4, -2.5, Math.sin(a) * 4)));
        }
        if (this.deathTime % 3 == 0) {
            Vec3 dir = new Vec3(this.random.nextGaussian(), this.random.nextGaussian(), this.random.nextGaussian()).normalize();
            StormNetwork.arcTo(level, core, core.add(dir.scale(8.0)));
        }
        if (this.deathTime == 60) {
            level.playSound(null, core.x, core.y, core.z, TempestSounds.VEYR_DEATH.get(), SoundSource.HOSTILE, 8.0F, 1.0F);
        }
        if (this.deathTime >= 110 && !this.isRemoved()) {
            level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFB8DCFF), true, true, core.x, core.y, core.z, 1, 0, 0, 0, 0);
            Fx.sphere(level, ModParticles.STATIC_SPARK.get(), core, 1.0, 300, 1.4);
            Fx.shake(level, core, 96.0, 2.5F, 40);
            StormNetwork.visualBolt(level, this.blockPosition());
            DamageSource source = this.deathSource != null ? this.deathSource : this.damageSources().generic();
            super.dropAllDeathLoot(level, source);
            SunFx.messageNear(level, core, 128.0, Component.translatable("entity.starforged.veyr.defeated").withStyle(ChatFormatting.AQUA,
                ChatFormatting.BOLD));
            this.restoreFloor(level);
            level.broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    private void retreat(ServerLevel level) {
        SunFx.messageNear(level, this.position(), 128.0, Component.translatable("entity.starforged.veyr.retreat").withStyle(ChatFormatting.AQUA));
        Fx.burst(level, ModParticles.STATIC_SPARK.get(), this.position().add(0, 2, 0), 200, 2.0, 0.3);
        ItemEntity sigil = new ItemEntity(level, this.home.getX() + 0.5, this.home.getY() + 1.5, this.home.getZ() + 0.5,
            new ItemStack(TempestItems.TEMPEST_SIGIL.get()));
        sigil.setUnlimitedLifetime();
        sigil.setGlowingTag(true);
        level.addFreshEntity(sigil);
        this.discardProps(level);
        this.restoreFloor(level);
        this.discard();
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
        return TempestSounds.VEYR_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return TempestSounds.VEYR_ROAR.get();
    }

    @Override
    protected float getSoundVolume() {
        return 4.0F;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        int phase = this.phase();
        output.putInt("phase", phase == PHASE_ASSEMBLE ? PHASE_TEMPEST
            : phase == PHASE_CONDUCTION || phase == PHASE_STUNNED || phase == PHASE_LAST_THUNDER ? PHASE_SHATTERED : phase);
        output.putBoolean("conduction_done", this.conductionDone);
        output.putBoolean("last_thunder_done", this.lastThunderDone);
        output.putLong("home", this.home.asLong());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setPhase(input.getIntOr("phase", PHASE_TEMPEST));
        this.conductionDone = input.getBooleanOr("conduction_done", false) || this.phase() == PHASE_SHATTERED;
        this.lastThunderDone = input.getBooleanOr("last_thunder_done", false);
        BlockPos stored = BlockPos.of(input.getLongOr("home", BlockPos.ZERO.asLong()));
        if (!stored.equals(BlockPos.ZERO)) {
            this.setHome(stored);
        }
        if (this.hasCustomName()) {
            this.bossEvent.setName(this.getDisplayName());
        }
    }
}

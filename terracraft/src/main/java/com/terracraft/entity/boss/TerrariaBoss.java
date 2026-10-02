package com.terracraft.entity.boss;

import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.TerrariaMob;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.CoreItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class of Terraria bosses: boss bar, phase/state machine data synced to clients (for sprites),
 * Expert/Master boss life scaling (x1.4 / x1.68 plus 35% per extra player in Expert), despawn rules
 * (everyone dead or far away, daylight for night bosses), "has awoken"/"has been defeated" messages,
 * progression flag on death and Terraria's healing potion drop.
 */
public abstract class TerrariaBoss extends TerrariaMob {
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(TerrariaBoss.class, EntityDataSerializers.INT);
    private static final double BOSS_RANGE = 160.0;

    private final ServerBossEvent bossBar;
    /** Ticks in the current AI state; subclasses reset it when switching states. */
    protected int aiTimer;
    protected int aiState;
    private int despawnTimer;

    protected TerrariaBoss(EntityType<? extends TerrariaBoss> type, Level level, BossEvent.BossBarColor color) {
        super(type, level);
        this.bossBar = new ServerBossEvent(java.util.UUID.randomUUID(), getDisplayName(), color, BossEvent.BossBarOverlay.PROGRESS);
        setPersistenceRequired();
    }

    /** Progression flag set when this boss dies. */
    protected abstract ProgressionFlag defeatFlag();

    /** Whether the boss leaves at daytime (Eye of Cthulhu, Skeletron...). */
    protected boolean fleesAtDay() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 0);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    protected void setPhase(int phase) {
        entityData.set(DATA_PHASE, phase);
    }

    protected void setAiState(int state) {
        aiState = state;
        aiTimer = 0;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        ServerLevel server = level.getLevel();
        float multiplier = switch (TerrariaDifficulty.of(server)) {
            case CLASSIC -> 1.0F;
            case EXPERT -> 1.4F;
            case MASTER -> 1.68F;
        };
        if (TerrariaDifficulty.isExpert(server)) {
            long players = server.players().stream().filter(p -> !p.isSpectator() && p.distanceToSqr(this) < BOSS_RANGE * BOSS_RANGE).count();
            multiplier *= 1.0F + 0.35F * Math.max(0, players - 1);
        }
        applyLife(definition().life() * multiplier);
        return data;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel level) {
            bossBar.setProgress(getHealth() / getMaxHealth());
            if (tickCount % 10 == 0) {
                updateBossBarPlayers(level);
                checkDespawn(level);
            }
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        aiTimer++;
    }

    private void updateBossBarPlayers(ServerLevel level) {
        List<ServerPlayer> current = new ArrayList<>(bossBar.getPlayers());
        for (ServerPlayer player : current) {
            if (player.level() != level || player.distanceToSqr(this) > BOSS_RANGE * BOSS_RANGE) {
                bossBar.removePlayer(player);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= BOSS_RANGE * BOSS_RANGE) {
                bossBar.addPlayer(player);
            }
        }
    }

    /** Living, non-creative players the boss can fight. */
    protected boolean isValidTarget(@Nullable LivingEntity target) {
        return target instanceof Player player && player.isAlive() && !player.isCreative() && !player.isSpectator()
            && player.distanceToSqr(this) < BOSS_RANGE * BOSS_RANGE;
    }

    /** Picks the closest valid player (bosses retarget instead of using vanilla target goals). */
    protected @Nullable Player findTarget(ServerLevel level) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : level.players()) {
            double d = player.distanceToSqr(this);
            if (isValidTarget(player) && d < bestDist) {
                best = player;
                bestDist = d;
            }
        }
        return best;
    }

    /** True while the boss is leaving (no targets or daylight); subclasses fly/hop away instead of attacking. */
    protected boolean isLeaving(ServerLevel level) {
        return despawnTimer > 0;
    }

    private void checkDespawn(ServerLevel level) {
        boolean noTargets = findTarget(level) == null;
        boolean day = fleesAtDay() && level.isBrightOutside();
        if (noTargets || day) {
            despawnTimer += 10;
            if (despawnTimer > (day ? 120 : 200)) {
                discard();
            }
        } else {
            despawnTimer = 0;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossBar.removeAllPlayers();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.terracraft.boss.defeated", getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
            ProgressionManager.markDefeated(level.getServer(), defeatFlag());
            // Terraria: bosses drop healing potions (Lesser Healing early on).
            int potions = 5 + random.nextInt(11);
            level.addFreshEntity(new ItemEntity(level, getX(), getY() + 0.5, getZ(), new ItemStack(CoreItems.LESSER_HEALING_POTION.get(), potions)));
        }
    }

    /** Bosses ignore suffocation, drowning and cramming (they pass through terrain in Terraria). */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
            || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity vehicle) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BossPhase", phase());
        output.putInt("BossState", aiState);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setPhase(input.getIntOr("BossPhase", 0));
        aiState = input.getIntOr("BossState", 0);
        if (hasCustomName()) {
            bossBar.setName(getDisplayName());
        }
    }
}

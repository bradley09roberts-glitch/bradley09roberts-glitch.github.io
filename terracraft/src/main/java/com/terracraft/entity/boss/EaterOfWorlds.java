package com.terracraft.entity.boss;

import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.mob.WormMob;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.BossContent;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.EvilContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Eater of Worlds: a giant burrowing worm made of many segments, each with its own life. Destroying a
 * middle segment splits the worm into two worms (the segment behind becomes a new head). One boss bar
 * tracks the life of every remaining segment; the boss is defeated when the last segment dies. Every segment
 * drops Demonite Ore and sometimes Shadow Scales, like in Terraria.
 */
public class EaterOfWorlds extends WormMob {
    public static final Spec SPEC = new Spec(30, 1.35, 0.6, 0.085, false, false);
    private static final int BODY_LIFE = 150;
    private static final int HEAD_LIFE = 65;
    private static final int TAIL_LIFE = 220;

    /** Shared state of every segment of one Eater of Worlds (boss bar, totals). */
    static final class Group {
        final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.translatable("entity.terracraft.eater_of_worlds"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);
        float maxLife;
        long lastTick = -1;
        int alive;
        float life;
    }

    private static final Map<UUID, Group> GROUPS = new HashMap<>();
    private @Nullable UUID groupId;

    public EaterOfWorlds(EntityType<? extends EaterOfWorlds> type, Level level) {
        super(type, level, SPEC);
        setPersistenceRequired();
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                                                  @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        if (groupId == null) {
            groupId = getUUID();
        }
        scaleLife(level.getLevel(), part());
        return data;
    }

    private void scaleLife(ServerLevel level, int part) {
        int base = part == HEAD ? HEAD_LIFE : part == TAIL ? TAIL_LIFE : BODY_LIFE;
        float multiplier = switch (TerrariaDifficulty.of(level)) {
            case CLASSIC -> 1.0F;
            case EXPERT -> 1.4F;
            case MASTER -> 1.68F;
        };
        applyLife(base * multiplier);
    }

    @Override
    protected void onSegmentSpawned(WormMob segment, int index) {
        EaterOfWorlds eater = (EaterOfWorlds) segment;
        eater.groupId = groupId;
        eater.scaleLife((ServerLevel) level(), segment.part());
    }

    @Override
    public int terrariaDefense() {
        return switch (part()) {
            case HEAD -> 2;
            case TAIL -> 8;
            default -> 4;
        };
    }

    @Override
    public float contactDamage() {
        float base = part() == HEAD ? 22 : part() == TAIL ? 11 : 13;
        return base * TerrariaDifficulty.enemyDamageMultiplier(level());
    }

    @Override
    public boolean countsTowardSpawnCap() {
        return false;
    }

    @Override
    public long coinValue() {
        return 300;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSq) {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (groupId == null) {
            groupId = getUUID();
        }
        Group group = GROUPS.computeIfAbsent(groupId, id -> new Group());
        long now = level.getGameTime();
        if (group.lastTick != now) {
            // first segment this tick: publish last tick's totals, then start counting again
            if (group.lastTick >= 0) {
                group.maxLife = Math.max(group.maxLife, group.life);
                group.bar.setProgress(group.maxLife > 0 ? Math.min(1.0F, group.life / group.maxLife) : 0.0F);
            }
            group.lastTick = now;
            group.life = 0;
            group.alive = 0;
            if (now % 10 == 0) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(this) < 160 * 160) {
                        group.bar.addPlayer(player);
                    } else {
                        group.bar.removePlayer(player);
                    }
                }
            }
        }
        group.life += getHealth();
        group.alive++;
        if (isHead() && getTarget() == null && level.getNearestPlayer(this, 160) == null && tickCount > 400) {
            discardGroup();
        }
    }

    private void discardGroup() {
        Group group = GROUPS.remove(groupId);
        if (group != null) {
            group.bar.removeAllPlayers();
        }
        for (var entity : ((ServerLevel) level()).getAllEntities()) {
            if (entity instanceof EaterOfWorlds eater && groupId.equals(eater.groupId)) {
                eater.discard();
            }
        }
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)
            || super.isInvulnerableTo(level, source);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        // per-segment loot
        level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), new ItemStack(EvilContent.DEMONITE_ORE.get(), 2 + random.nextInt(4))));
        if (random.nextFloat() < 0.5F) {
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), new ItemStack(BossContent.SHADOW_SCALE.get(), 1 + random.nextInt(2))));
        }
        boolean last = true;
        for (var entity : level.getAllEntities()) {
            if (entity != this && entity instanceof EaterOfWorlds eater && eater.isAlive() && groupId != null && groupId.equals(eater.groupId)) {
                last = false;
                break;
            }
        }
        if (last) {
            Group group = GROUPS.remove(groupId);
            if (group != null) {
                group.bar.removeAllPlayers();
            }
            level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.terracraft.boss.defeated",
                Component.translatable("entity.terracraft.eater_of_worlds")).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
            ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.EATER_OF_WORLDS);
            ProgressionManager.markDefeated(level.getServer(), ProgressionFlags.EVIL_BOSS);
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), new ItemStack(CoreItems.LESSER_HEALING_POTION.get(), 5 + random.nextInt(11))));
            level.addFreshEntity(new ItemEntity(level, getX(), getY(), getZ(), new ItemStack(EvilContent.DEMONITE_ORE.get(), 30 + random.nextInt(30))));
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (groupId != null) {
            output.store("EaterGroup", UUIDUtil.CODEC, groupId);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        groupId = input.read("EaterGroup", UUIDUtil.CODEC).orElse(null);
    }
}

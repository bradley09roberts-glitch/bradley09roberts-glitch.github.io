package com.terracraft.player;

import com.terracraft.config.TerraConfig;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.SyncPlayerStatsPacket;
import com.terracraft.player.stats.Stat;
import com.terracraft.player.stats.StatCalculator;
import com.terracraft.progression.ProgressionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import java.lang.invoke.MethodHandles;

/**
 * Player lifecycle for Terraria stats: first join, login, respawn, death cloning, per-tick regeneration
 * and synchronisation to the owning client.
 */
public final class PlayerEvents {
    /** Stats are always recomputed at least this often (buff expiry, food changes, armor swaps...). */
    private static final int STAT_REFRESH_INTERVAL = 10;
    /** Minimum ticks between two sync packets caused only by mana regeneration. */
    private static final int SYNC_INTERVAL = 2;

    private PlayerEvents() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), PlayerEvents.class);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TerraPlayerData data = TerraPlayerData.get(player);
        refresh(player, data);
        if (!data.isInitialised()) {
            data.setInitialised(true);
            HealthManager.healToFull(player);
            data.setMana(data.stats().maxMana);
            StarterKit.give(player);
        }
        ProgressionManager.syncTo(player);
        TerraNetwork.sendToPlayer(player, com.terracraft.network.packet.PlayerWingsPacket.of(player));
        com.terracraft.data.DataEvents.syncTo(player);
        sync(player, data);
    }

    /** Tell a player which wings the players they start seeing wear. */
    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer tracker && event.getTarget() instanceof ServerPlayer target) {
            TerraNetwork.sendToPlayer(tracker, com.terracraft.network.packet.PlayerWingsPacket.of(target));
        }
    }

    @SubscribeEvent
    static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            TerraPlayerData oldData = TerraPlayerData.getOrNull(original);
            TerraPlayerData newData = TerraPlayerData.getOrNull(event.getEntity());
            if (oldData != null && newData != null) {
                newData.copyFrom(oldData, event.isWasDeath());
            }
        } finally {
            original.invalidateCaps();
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TerraPlayerData data = TerraPlayerData.get(player);
            refresh(player, data);
            HealthManager.healToFull(player);
            data.setMana(data.stats().maxMana);
            sync(player, data);
        }
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TerraPlayerData data = TerraPlayerData.get(player);
            refresh(player, data);
            sync(player, data);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(TickEvent.PlayerTickEvent.Post event) {
        if (!(event.player() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        TerraPlayerData data = TerraPlayerData.get(player);
        if (data.consumeStatsDirty() || player.tickCount % STAT_REFRESH_INTERVAL == 0) {
            refresh(player, data);
        }
        if (player.onGround() || player.isInWater()) {
            data.doubleJumpsUsed = 0;
        }
        tickLavaImmunity(player, data);
        FoodManager.tick(player);
        NoBootsSlot.tick(player);
        ManaManager.tick(player, data);
        if (TerraConfig.COMMON.terrariaLifeRegen.get()) {
            LifeRegenManager.tick(player, data);
        }
        if (player.tickCount % SYNC_INTERVAL == 0 && data.consumeSyncDirty()) {
            sync(player, data);
        }
    }

    private static void tickLavaImmunity(ServerPlayer player, TerraPlayerData data) {
        int max = Math.round(data.stats().get(Stat.LAVA_IMMUNITY_SECONDS) * 20.0F);
        if (player.isInLava()) {
            if (data.lavaImmunityTicks > 0) {
                data.lavaImmunityTicks--;
                player.clearFire();
            }
        } else if (data.lavaImmunityTicks < max) {
            data.lavaImmunityTicks = Math.min(max, data.lavaImmunityTicks + 2);
        } else {
            data.lavaImmunityTicks = max;
        }
    }

    /** Recomputes stats and applies attribute-backed values (max life, movement...). */
    public static void refresh(ServerPlayer player, TerraPlayerData data) {
        int previousMaxLife = data.stats().maxLife;
        int previousMaxMana = data.stats().maxMana;
        int previousDefense = data.stats().defense();
        int previousBits = data.stats().abilityBits();
        int previousJumps = data.stats().extraJumps();
        var previousWings = data.stats().wings;
        StatCalculator.recompute(player, data);
        HealthManager.applyMaxLife(player, data.stats().maxLife);
        AttributeEffects.apply(player, data.stats());
        if (previousMaxLife != data.stats().maxLife || previousMaxMana != data.stats().maxMana
            || previousDefense != data.stats().defense() || previousBits != data.stats().abilityBits()
            || previousJumps != data.stats().extraJumps()) {
            data.markSyncDirty();
        }
        if (!java.util.Objects.equals(previousWings, data.stats().wings)) {
            TerraNetwork.sendToTrackingAndSelf(player, com.terracraft.network.packet.PlayerWingsPacket.of(player));
        }
    }

    public static void sync(ServerPlayer player, TerraPlayerData data) {
        TerraNetwork.sendToPlayer(player, SyncPlayerStatsPacket.of(data));
    }

    /** Recompute + sync immediately (after consuming a crystal, equipping, commands...). */
    public static void refreshAndSync(ServerPlayer player) {
        TerraPlayerData data = TerraPlayerData.get(player);
        refresh(player, data);
        sync(player, data);
    }
}

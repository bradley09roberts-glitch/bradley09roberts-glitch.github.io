package com.terracraft.data;

import com.terracraft.crafting.TerraRecipeManager;
import com.terracraft.mining.MiningPower;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.SyncMiningPowerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;


/** Registers TerraCraft's datapack loaders and pushes loaded data to clients. */
public final class DataEvents {
    private DataEvents() {}

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(DataEvents.class);
        com.terracraft.TerraCraft.modBus().addListener(BuiltInPacks::onAddPackFinders);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(com.terracraft.TerraCraft.id("mining_power"), new JsonDataLoader("terracraft/mining_power", MiningPower::load));
        event.addListener(com.terracraft.TerraCraft.id("recipes"), new JsonDataLoader("terracraft/recipe", TerraRecipeManager::load));
        event.addListener(com.terracraft.TerraCraft.id("spawns"), new JsonDataLoader("terracraft/spawns", com.terracraft.world.spawn.TerrariaSpawner::load));
        event.addListener(com.terracraft.TerraCraft.id("shops"), new JsonDataLoader("terracraft/shops", com.terracraft.npc.NpcShops::load));
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        MiningPower.invalidate();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(DataEvents::syncTo);
    }

    public static void syncTo(ServerPlayer player) {
        TerraNetwork.sendToPlayer(player, new SyncMiningPowerPacket(MiningPower.snapshot()));
        TerraNetwork.sendToPlayer(player, TerraRecipeManager.syncPacket());
    }
}

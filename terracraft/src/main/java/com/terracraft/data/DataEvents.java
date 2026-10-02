package com.terracraft.data;

import com.terracraft.crafting.TerraRecipeManager;
import com.terracraft.mining.MiningPower;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.SyncMiningPowerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import java.lang.invoke.MethodHandles;

/** Registers TerraCraft's datapack loaders and pushes loaded data to clients. */
public final class DataEvents {
    private DataEvents() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), DataEvents.class);
        net.minecraftforge.event.AddPackFindersEvent.BUS.addListener(BuiltInPacks::onAddPackFinders);
    }

    @SubscribeEvent
    static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new JsonDataLoader("terracraft/mining_power", MiningPower::load));
        event.addListener(new JsonDataLoader("terracraft/recipe", TerraRecipeManager::load));
        event.addListener(new JsonDataLoader("terracraft/spawns", com.terracraft.world.spawn.TerrariaSpawner::load));
        event.addListener(new JsonDataLoader("terracraft/shops", com.terracraft.npc.NpcShops::load));
    }

    @SubscribeEvent
    static void onTagsUpdated(TagsUpdatedEvent event) {
        MiningPower.invalidate();
    }

    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getPlayers().forEach(DataEvents::syncTo);
    }

    public static void syncTo(ServerPlayer player) {
        TerraNetwork.sendToPlayer(player, new SyncMiningPowerPacket(MiningPower.snapshot()));
        TerraNetwork.sendToPlayer(player, TerraRecipeManager.syncPacket());
    }
}

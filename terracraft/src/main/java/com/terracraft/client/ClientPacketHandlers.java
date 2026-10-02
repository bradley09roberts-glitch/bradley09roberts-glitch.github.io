package com.terracraft.client;

import com.terracraft.client.gui.DevMenuScreen;
import com.terracraft.network.packet.SyncPlayerStatsPacket;
import com.terracraft.network.packet.SyncProgressionPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Clientbound packet handling. Packet classes are common code, so every method checks the dist before
 * touching client-only classes (the JVM resolves those classes lazily, only when the guarded code runs).
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {}

    public static void handleProgression(SyncProgressionPacket packet) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientState.update(packet);
        }
    }

    public static void handlePlayerStats(SyncPlayerStatsPacket packet) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientState.update(packet);
        }
    }

    public static void openDevMenu() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.openDevMenu();
        }
    }

    public static void openCrafting() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.openCrafting();
        }
    }

    public static void setEvent(String event) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientState.setActiveEvent(event);
        }
    }

    public static void openNpcChat(com.terracraft.network.packet.OpenNpcChatPacket packet) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.openNpcChat(packet);
        }
    }

    /** Holder for code that names client-only classes. */
    private static final class Client {
        static void openNpcChat(com.terracraft.network.packet.OpenNpcChatPacket packet) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof com.terracraft.client.gui.NpcChatScreen open && open.entityId() == packet.entityId()) {
                open.update(packet);
            } else {
                mc.gui.setScreen(new com.terracraft.client.gui.NpcChatScreen(packet));
            }
        }

        static void openDevMenu() {
            Minecraft.getInstance().gui.setScreen(new DevMenuScreen());
        }

        static void openCrafting() {
            Minecraft.getInstance().gui.setScreen(new com.terracraft.client.gui.TerrariaCraftingScreen());
        }
    }
}

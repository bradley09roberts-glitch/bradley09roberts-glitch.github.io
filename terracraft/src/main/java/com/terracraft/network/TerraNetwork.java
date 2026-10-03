package com.terracraft.network;

import com.terracraft.network.packet.CraftRecipePacket;
import com.terracraft.network.packet.DevActionPacket;
import com.terracraft.network.packet.DoubleJumpPacket;
import com.terracraft.network.packet.NpcActionPacket;
import com.terracraft.network.packet.OpenAccessoriesPacket;
import com.terracraft.network.packet.OpenCraftingPacket;
import com.terracraft.network.packet.OpenDevMenuPacket;
import com.terracraft.network.packet.OpenNpcChatPacket;
import com.terracraft.network.packet.PlayerWingsPacket;
import com.terracraft.network.packet.SyncEventPacket;
import com.terracraft.network.packet.SyncMiningPowerPacket;
import com.terracraft.network.packet.SyncPlayerStatsPacket;
import com.terracraft.network.packet.SyncProgressionPacket;
import com.terracraft.network.packet.SyncRecipesPacket;
import com.terracraft.network.packet.UseWeaponPacket;
import com.terracraft.network.packet.WingFlightPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * TerraCraft's play-phase payloads.
 * <p>
 * Packet classes are records with a {@code TYPE}, a {@code STREAM_CODEC} and a static {@code handle} method.
 * Clientbound handlers delegate to {@code com.terracraft.client.ClientPacketHandlers}; serverbound handlers always
 * validate the sender because clients are untrusted. Bump {@link #PROTOCOL} whenever a packet layout changes.
 */
public final class TerraNetwork {
    public static final String PROTOCOL = "5";

    private TerraNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(PROTOCOL);
        // clientbound
        r.playToClient(SyncProgressionPacket.TYPE, SyncProgressionPacket.STREAM_CODEC, (p, c) -> SyncProgressionPacket.handle(p, new PacketContext(c)));
        r.playToClient(SyncPlayerStatsPacket.TYPE, SyncPlayerStatsPacket.STREAM_CODEC, (p, c) -> SyncPlayerStatsPacket.handle(p, new PacketContext(c)));
        r.playToClient(OpenDevMenuPacket.TYPE, OpenDevMenuPacket.STREAM_CODEC, (p, c) -> OpenDevMenuPacket.handle(p, new PacketContext(c)));
        r.playToClient(OpenCraftingPacket.TYPE, OpenCraftingPacket.STREAM_CODEC, (p, c) -> OpenCraftingPacket.handle(p, new PacketContext(c)));
        r.playToClient(SyncRecipesPacket.TYPE, SyncRecipesPacket.STREAM_CODEC, (p, c) -> SyncRecipesPacket.handle(p, new PacketContext(c)));
        r.playToClient(SyncMiningPowerPacket.TYPE, SyncMiningPowerPacket.STREAM_CODEC, (p, c) -> SyncMiningPowerPacket.handle(p, new PacketContext(c)));
        r.playToClient(SyncEventPacket.TYPE, SyncEventPacket.STREAM_CODEC, (p, c) -> SyncEventPacket.handle(p, new PacketContext(c)));
        r.playToClient(OpenNpcChatPacket.TYPE, OpenNpcChatPacket.STREAM_CODEC, (p, c) -> OpenNpcChatPacket.handle(p, new PacketContext(c)));
        r.playToClient(PlayerWingsPacket.TYPE, PlayerWingsPacket.STREAM_CODEC, (p, c) -> PlayerWingsPacket.handle(p, new PacketContext(c)));
        // serverbound
        r.playToServer(DevActionPacket.TYPE, DevActionPacket.STREAM_CODEC, (p, c) -> DevActionPacket.handle(p, new PacketContext(c)));
        r.playToServer(OpenAccessoriesPacket.TYPE, OpenAccessoriesPacket.STREAM_CODEC, (p, c) -> OpenAccessoriesPacket.handle(p, new PacketContext(c)));
        r.playToServer(CraftRecipePacket.TYPE, CraftRecipePacket.STREAM_CODEC, (p, c) -> CraftRecipePacket.handle(p, new PacketContext(c)));
        r.playToServer(DoubleJumpPacket.TYPE, DoubleJumpPacket.STREAM_CODEC, (p, c) -> DoubleJumpPacket.handle(p, new PacketContext(c)));
        r.playToServer(WingFlightPacket.TYPE, WingFlightPacket.STREAM_CODEC, (p, c) -> WingFlightPacket.handle(p, new PacketContext(c)));
        r.playToServer(UseWeaponPacket.TYPE, UseWeaponPacket.STREAM_CODEC, (p, c) -> UseWeaponPacket.handle(p, new PacketContext(c)));
        r.playToServer(NpcActionPacket.TYPE, NpcActionPacket.STREAM_CODEC, (p, c) -> NpcActionPacket.handle(p, new PacketContext(c)));
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToAll(CustomPacketPayload packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendToTrackingAndSelf(Entity entity, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packet);
    }

    /** Client side only. */
    public static void sendToServer(CustomPacketPayload packet) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(packet);
    }
}

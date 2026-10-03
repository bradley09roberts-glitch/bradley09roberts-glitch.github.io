package com.terracraft.network.packet;

import com.terracraft.player.TerraPlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import com.terracraft.network.PacketContext;

/**
 * Client -> server, a few times a second while flying or gliding with wings. Movement is client-authoritative,
 * so the client moves the player; the server checks that wings are worn and cancels the fall damage built up.
 */
public record WingFlightPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<WingFlightPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("wing_flight_packet"));

    @Override
    public Type<WingFlightPacket> type() {
        return TYPE;
    }

    public static final WingFlightPacket INSTANCE = new WingFlightPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, WingFlightPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(WingFlightPacket packet, PacketContext ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null && TerraPlayerData.get(player).stats().wings != null) {
            player.resetFallDistance();
        }
    }
}

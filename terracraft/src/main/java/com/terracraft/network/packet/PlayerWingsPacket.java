package com.terracraft.network.packet;

import com.terracraft.client.ClientPacketHandlers;
import com.terracraft.item.accessory.WingsItem;
import com.terracraft.player.TerraPlayerData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import com.terracraft.network.PacketContext;

/**
 * Server -> clients: which wings a player wears (style "" = none), sent to the player and everyone tracking
 * them so the wings render on every client and the owner knows its flight time.
 */
public record PlayerWingsPacket(int entityId, String style, int flightTicks, float ascent) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<PlayerWingsPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("player_wings_packet"));

    @Override
    public Type<PlayerWingsPacket> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerWingsPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeVarInt(p.entityId);
            buf.writeUtf(p.style, 64);
            buf.writeVarInt(p.flightTicks);
            buf.writeFloat(p.ascent);
        },
        buf -> new PlayerWingsPacket(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readFloat()));

    public static PlayerWingsPacket of(Player player) {
        WingsItem.Flight wings = TerraPlayerData.get(player).stats().wings;
        return wings == null ? new PlayerWingsPacket(player.getId(), "", 0, 0.0F)
            : new PlayerWingsPacket(player.getId(), wings.style(), wings.flightTicks(), wings.ascent());
    }

    public static void handle(PlayerWingsPacket packet, PacketContext ctx) {
        ClientPacketHandlers.setWings(packet);
    }
}

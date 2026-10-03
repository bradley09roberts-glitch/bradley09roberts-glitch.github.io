package com.terracraft.network.packet;

import com.terracraft.command.DevActions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import com.terracraft.network.PacketContext;

/**
 * Client -> server: a developer menu action ("toggle_flag", "heal", "max_stats"...).
 * The server re-checks permissions before doing anything.
 */
public record DevActionPacket(String action, String argument) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<DevActionPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("dev_action_packet"));

    @Override
    public Type<DevActionPacket> type() {
        return TYPE;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DevActionPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.action, 64);
            buf.writeUtf(p.argument, 256);
        },
        buf -> new DevActionPacket(buf.readUtf(64), buf.readUtf(256))
    );

    public static void handle(DevActionPacket packet, PacketContext ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            DevActions.perform(sender, packet.action, packet.argument);
        }
    }
}

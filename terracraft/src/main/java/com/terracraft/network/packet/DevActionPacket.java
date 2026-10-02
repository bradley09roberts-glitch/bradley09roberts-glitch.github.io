package com.terracraft.network.packet;

import com.terracraft.command.DevActions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Client -> server: a developer menu action ("toggle_flag", "heal", "max_stats"...).
 * The server re-checks permissions before doing anything.
 */
public record DevActionPacket(String action, String argument) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DevActionPacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeUtf(p.action, 64);
            buf.writeUtf(p.argument, 256);
        },
        buf -> new DevActionPacket(buf.readUtf(64), buf.readUtf(256))
    );

    public static void handle(DevActionPacket packet, CustomPayloadEvent.Context ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            DevActions.perform(sender, packet.action, packet.argument);
        }
    }
}

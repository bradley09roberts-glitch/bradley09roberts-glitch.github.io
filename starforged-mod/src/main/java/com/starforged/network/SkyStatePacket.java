package com.starforged.network;

import com.starforged.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server -> client: current sky mood. {@code starfall} tints the night violet and fills it with shooting stars,
 * {@code eclipse} (0..1) darkens the world during the Eclipse Sovereign's fight.
 */
public record SkyStatePacket(boolean starfall, float eclipse) {
    public static final StreamCodec<RegistryFriendlyByteBuf, SkyStatePacket> STREAM_CODEC = StreamCodec.<ByteBuf, SkyStatePacket, Boolean, Float>composite(
        ByteBufCodecs.BOOL, SkyStatePacket::starfall,
        ByteBufCodecs.FLOAT, SkyStatePacket::eclipse,
        SkyStatePacket::new
    ).cast();

    public static void handle(SkyStatePacket packet, CustomPayloadEvent.Context context) {
        ClientHooks.setSky(packet.starfall, packet.eclipse);
        context.setPacketHandled(true);
    }
}

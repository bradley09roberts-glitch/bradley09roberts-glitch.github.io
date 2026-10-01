package com.starforged.network;

import com.starforged.client.ClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> client: shake the camera. */
public record ScreenShakePacket(float intensity, int duration) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenShakePacket> STREAM_CODEC = StreamCodec.<ByteBuf, ScreenShakePacket, Float, Integer>composite(
        ByteBufCodecs.FLOAT, ScreenShakePacket::intensity,
        ByteBufCodecs.VAR_INT, ScreenShakePacket::duration,
        ScreenShakePacket::new
    ).cast();

    public static void handle(ScreenShakePacket packet, CustomPayloadEvent.Context context) {
        ClientHooks.shake(packet.intensity, packet.duration);
        context.setPacketHandled(true);
    }
}

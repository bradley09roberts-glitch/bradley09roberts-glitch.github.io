package com.starforged.network;

import com.starforged.event.ArmorAbilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> server: the local player performed a Comet Boots mid-air jump. */
public record DoubleJumpPacket() {
    public static final DoubleJumpPacket INSTANCE = new DoubleJumpPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, DoubleJumpPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(DoubleJumpPacket packet, CustomPayloadEvent.Context context) {
        ServerPlayer player = context.getSender();
        if (player != null) {
            ArmorAbilities.onDoubleJump(player);
        }
        context.setPacketHandled(true);
    }
}

package com.terracraft.network.packet;

import com.terracraft.player.TerraPlayerData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import com.terracraft.network.PacketContext;

/**
 * Client -> server: the player performed an extra (double) jump. Movement is client-authoritative in
 * Minecraft, so the client applies the velocity; the server validates the ability, counts jumps,
 * resets fall distance and shows the cloud puff to everyone.
 */
public record DoubleJumpPacket() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<DoubleJumpPacket> TYPE = new Type<>(com.terracraft.TerraCraft.id("double_jump_packet"));

    @Override
    public Type<DoubleJumpPacket> type() {
        return TYPE;
    }

    public static final DoubleJumpPacket INSTANCE = new DoubleJumpPacket();
    public static final StreamCodec<RegistryFriendlyByteBuf, DoubleJumpPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    public static void handle(DoubleJumpPacket packet, PacketContext ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || player.onGround()) {
            return;
        }
        TerraPlayerData data = TerraPlayerData.get(player);
        if (data.doubleJumpsUsed >= data.stats().extraJumps()) {
            return;
        }
        data.doubleJumpsUsed++;
        player.resetFallDistance();
        player.level().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
    }
}

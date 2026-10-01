package com.starforged.network;

import com.starforged.Starforged;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

public final class ModNetwork {
    private static final int PROTOCOL = 1;

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Starforged.id("main"))
        .networkProtocolVersion(PROTOCOL)
        .simpleChannel()
            .play()
                .clientbound()
                    .addMain(ScreenShakePacket.class, ScreenShakePacket.STREAM_CODEC, ScreenShakePacket::handle)
                    .addMain(SkyStatePacket.class, SkyStatePacket.STREAM_CODEC, SkyStatePacket::handle)
                .serverbound()
                    .addMain(DoubleJumpPacket.class, DoubleJumpPacket.STREAM_CODEC, DoubleJumpPacket::handle)
        .build();

    public static void init() {
        Starforged.LOGGER.debug("Starforged network channel v{} ready", CHANNEL.getProtocolVersion());
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToServer(Object packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }

    private ModNetwork() {
    }
}

package com.terracraft.network;

import com.terracraft.TerraCraft;
import com.terracraft.network.packet.DevActionPacket;
import com.terracraft.network.packet.OpenDevMenuPacket;
import com.terracraft.network.packet.SyncPlayerStatsPacket;
import com.terracraft.network.packet.SyncProgressionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/**
 * TerraCraft's single play-phase channel.
 * <p>
 * Packet classes are plain records with a {@code STREAM_CODEC} and a static {@code handle} method.
 * Clientbound handlers delegate to {@code com.terracraft.client.ClientPacketHandlers}; serverbound
 * handlers always validate the sender because clients are untrusted.
 * Bump {@link #PROTOCOL} whenever a packet layout changes.
 */
public final class TerraNetwork {
    public static final int PROTOCOL = 1;

    public static final SimpleChannel CHANNEL = ChannelBuilder.named(TerraCraft.id("main"))
        .networkProtocolVersion(PROTOCOL)
        .simpleChannel()
        .play()
            .clientbound()
                .addMain(SyncProgressionPacket.class, SyncProgressionPacket.STREAM_CODEC, SyncProgressionPacket::handle)
                .addMain(SyncPlayerStatsPacket.class, SyncPlayerStatsPacket.STREAM_CODEC, SyncPlayerStatsPacket::handle)
                .addMain(OpenDevMenuPacket.class, OpenDevMenuPacket.STREAM_CODEC, OpenDevMenuPacket::handle)
            .serverbound()
                .addMain(DevActionPacket.class, DevActionPacket.STREAM_CODEC, DevActionPacket::handle)
        .build();

    private TerraNetwork() {}

    /** Forces class loading so the channel registers during mod construction. */
    public static void init() {
        TerraCraft.LOGGER.debug("Registered network channel {} v{}", CHANNEL.getName(), PROTOCOL);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    public static void sendToAll(Object packet) {
        CHANNEL.send(packet, PacketDistributor.ALL.noArg());
    }

    public static void sendToTrackingAndSelf(Entity entity, Object packet) {
        CHANNEL.send(packet, PacketDistributor.TRACKING_ENTITY_AND_SELF.with(entity));
    }

    public static void sendToServer(Object packet) {
        CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
    }
}

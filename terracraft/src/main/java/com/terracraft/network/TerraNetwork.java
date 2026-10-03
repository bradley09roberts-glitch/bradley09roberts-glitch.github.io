package com.terracraft.network;

import com.terracraft.TerraCraft;
import com.terracraft.network.packet.CraftRecipePacket;
import com.terracraft.network.packet.DevActionPacket;
import com.terracraft.network.packet.DoubleJumpPacket;
import com.terracraft.network.packet.OpenAccessoriesPacket;
import com.terracraft.network.packet.OpenCraftingPacket;
import com.terracraft.network.packet.SyncMiningPowerPacket;
import com.terracraft.network.packet.SyncRecipesPacket;
import com.terracraft.network.packet.UseWeaponPacket;
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
    public static final int PROTOCOL = 4;

    public static final SimpleChannel CHANNEL = ChannelBuilder.named(TerraCraft.id("main"))
        .networkProtocolVersion(PROTOCOL)
        .simpleChannel()
        .play()
            .clientbound()
                .addMain(SyncProgressionPacket.class, SyncProgressionPacket.STREAM_CODEC, SyncProgressionPacket::handle)
                .addMain(SyncPlayerStatsPacket.class, SyncPlayerStatsPacket.STREAM_CODEC, SyncPlayerStatsPacket::handle)
                .addMain(OpenDevMenuPacket.class, OpenDevMenuPacket.STREAM_CODEC, OpenDevMenuPacket::handle)
                .addMain(OpenCraftingPacket.class, OpenCraftingPacket.STREAM_CODEC, OpenCraftingPacket::handle)
                .addMain(SyncRecipesPacket.class, SyncRecipesPacket.STREAM_CODEC, SyncRecipesPacket::handle)
                .addMain(SyncMiningPowerPacket.class, SyncMiningPowerPacket.STREAM_CODEC, SyncMiningPowerPacket::handle)
                .addMain(com.terracraft.network.packet.SyncEventPacket.class, com.terracraft.network.packet.SyncEventPacket.STREAM_CODEC,
                    com.terracraft.network.packet.SyncEventPacket::handle)
                .addMain(com.terracraft.network.packet.OpenNpcChatPacket.class, com.terracraft.network.packet.OpenNpcChatPacket.STREAM_CODEC,
                    com.terracraft.network.packet.OpenNpcChatPacket::handle)
                .addMain(com.terracraft.network.packet.PlayerWingsPacket.class, com.terracraft.network.packet.PlayerWingsPacket.STREAM_CODEC,
                    com.terracraft.network.packet.PlayerWingsPacket::handle)
            .serverbound()
                .addMain(DevActionPacket.class, DevActionPacket.STREAM_CODEC, DevActionPacket::handle)
                .addMain(OpenAccessoriesPacket.class, OpenAccessoriesPacket.STREAM_CODEC, OpenAccessoriesPacket::handle)
                .addMain(CraftRecipePacket.class, CraftRecipePacket.STREAM_CODEC, CraftRecipePacket::handle)
                .addMain(DoubleJumpPacket.class, DoubleJumpPacket.STREAM_CODEC, DoubleJumpPacket::handle)
                .addMain(com.terracraft.network.packet.WingFlightPacket.class, com.terracraft.network.packet.WingFlightPacket.STREAM_CODEC,
                    com.terracraft.network.packet.WingFlightPacket::handle)
                .addMain(UseWeaponPacket.class, UseWeaponPacket.STREAM_CODEC, UseWeaponPacket::handle)
                .addMain(com.terracraft.network.packet.NpcActionPacket.class, com.terracraft.network.packet.NpcActionPacket.STREAM_CODEC,
                    com.terracraft.network.packet.NpcActionPacket::handle)
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

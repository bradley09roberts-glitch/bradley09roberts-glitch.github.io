package com.starforged.sun;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Small presentation helpers shared by the Sunforged content. */
public final class SunFx {
    private SunFx() {
    }

    public static void title(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void titleNear(ServerLevel level, Vec3 at, double range, Component title, Component subtitle, int in, int stay, int out) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(at) < range) {
                title(player, title, subtitle, in, stay, out);
            }
        }
    }

    public static void messageNear(ServerLevel level, Vec3 at, double range, Component message) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(at) < range) {
                player.sendSystemMessage(message);
            }
        }
    }
}

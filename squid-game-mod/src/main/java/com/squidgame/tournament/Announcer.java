package com.squidgame.tournament;

import com.squidgame.world.ArenaWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Broadcast helpers: everything is addressed to the "audience" = players currently in the tournament dimension. */
public final class Announcer {
    private Announcer() {
    }

    public static List<ServerPlayer> audience(MinecraftServer server) {
        ServerLevel level = ArenaWorld.level(server);
        List<ServerPlayer> out = new ArrayList<>();
        if (level != null) {
            out.addAll(level.players());
        }
        return out;
    }

    public static void chat(MinecraftServer server, Component msg) {
        for (ServerPlayer p : audience(server)) {
            p.sendSystemMessage(msg);
        }
    }

    public static void chat(ServerPlayer player, Component msg) {
        player.sendSystemMessage(msg);
    }

    public static void title(MinecraftServer server, Component title, Component subtitle, int in, int stay, int out) {
        for (ServerPlayer p : audience(server)) {
            title(p, title, subtitle, in, stay, out);
        }
    }

    public static void title(ServerPlayer p, Component title, Component subtitle, int in, int stay, int out) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void actionBar(MinecraftServer server, Component msg) {
        for (ServerPlayer p : audience(server)) {
            p.displayClientMessage(msg, true);
        }
    }

    /** Non-positional sound for everyone in the audience (PA system, UI cues). */
    public static void sound(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer p : audience(server)) {
            p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        }
    }

    public static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    /** World sound heard by everyone near the position. */
    public static void soundAt(ServerLevel level, Vec3 pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, source, volume, pitch);
    }
}

package com.terracraft.network;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/** What packet handlers need from NeoForge's payload context. Handlers run on the main thread. */
public record PacketContext(IPayloadContext context) {
    /** The sending player for serverbound packets, otherwise null. */
    @Nullable
    public ServerPlayer getSender() {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    public Connection getConnection() {
        return context.connection();
    }
}

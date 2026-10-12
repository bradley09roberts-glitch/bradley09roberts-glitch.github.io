package com.grandline.creature;

import net.minecraft.server.MinecraftServer;

/** Server-tick entry point for creature spawning (Sea Kings at sea, wildlife on islands). */
public final class CreatureSpawns {
    public static void tick(MinecraftServer server) {
        SeaKingSpawner.tick(server);
        WildlifeSpawner.tick(server);
    }

    private CreatureSpawns() {}
}

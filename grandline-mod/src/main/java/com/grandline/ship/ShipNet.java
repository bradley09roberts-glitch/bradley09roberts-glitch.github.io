package com.grandline.ship;

import com.grandline.net.GLNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Ship packet handlers (filled in by the ships milestone). */
public final class ShipNet {
    public static void handle(ServerPlayer sp, CompoundTag t) {}

    public static void control(ServerPlayer sp, GLNetwork.ShipControl p) {}

    private ShipNet() {}
}

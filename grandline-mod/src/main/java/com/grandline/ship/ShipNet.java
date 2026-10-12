package com.grandline.ship;

import com.grandline.net.GLNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Ship packet handlers: helm input, cannon fire, shipyard actions. */
public final class ShipNet {
    public static final byte ANCHOR = 1, FIRE = 2, BURST = 4, SAIL_UP = 8, SAIL_DOWN = 16;

    public static void handle(ServerPlayer sp, CompoundTag t) {
        Shipyard.action(sp, t);
    }

    public static void control(ServerPlayer sp, GLNetwork.ShipControl p) {
        if (!(sp.getVehicle() instanceof ShipEntity ship) || ship.getId() != p.shipId()) return;
        int seat = ship.seatIndex(sp);
        if (seat == ship.helmSeat) ship.helmInput(sp, p.throttle(), p.rudder(), p.flags());
        else if ((p.flags() & FIRE) != 0) ship.fireCannon(sp);
    }

    private ShipNet() {}
}

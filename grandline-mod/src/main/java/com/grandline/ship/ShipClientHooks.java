package com.grandline.ship;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Client-only helpers referenced from ShipEntity behind isClientSide checks. */
public final class ShipClientHooks {
    public static boolean isLocalPlayer(Player p) {
        return p == Minecraft.getInstance().player;
    }

    /** Ship the local player was standing still on, and where on it (ship-local coordinates). */
    private static int anchorShip = -1;
    private static Vec3 anchorLocal;

    /**
     * Keeps the local player at their spot on deck while they stand still: whatever makes a standing player creep across
     * a moving deck on a slow client (lagging interpolation, collision jitter against the hull parts), it is undone here,
     * in ship-relative coordinates, after the ship's own carry. Any movement input, a jump or leaving the deck re-anchors.
     */
    static void anchor(ShipEntity ship) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isPassenger() || !ship.carries(p)) {
            if (anchorShip == ship.getId()) anchorShip = -1;
            return;
        }
        boolean idle = p.onGround() && p.input.forwardImpulse == 0 && p.input.leftImpulse == 0 && !p.input.jumping && !p.input.shiftKeyDown;
        if (idle && anchorShip == ship.getId() && anchorLocal != null) {
            Vec3 w = ship.toWorld(anchorLocal.x, anchorLocal.y, anchorLocal.z);
            double ex = w.x - p.getX(), ez = w.z - p.getZ();
            if (ex * ex + ez * ez > 0.0004 && ex * ex + ez * ez < 9) p.setPos(w.x, p.getY(), w.z);
            return;
        }
        anchorShip = ship.getId();
        anchorLocal = ship.toLocal(p.position());
    }

    private ShipClientHooks() {}
}

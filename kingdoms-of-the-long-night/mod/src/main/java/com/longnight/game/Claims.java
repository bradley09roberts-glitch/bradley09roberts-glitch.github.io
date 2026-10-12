package com.longnight.game;

import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Boundary stones protect a player's land from the settlements' builders and woodcutters. */
public final class Claims {
    private Claims() {}

    public static final int RADIUS = 32;

    public static void claim(ServerPlayer p, ServerLevel level, BlockPos pos) {
        WorldSim sim = WorldSim.get(level.getServer());
        for (WorldSim.Claim c : sim.claims) {
            if (c.contains(pos, RADIUS) && !c.owner().equals(p.getUUID())) {
                Ui.msg(p, "This stone stands too close to land claimed by " + c.ownerName() + "; it marks nothing.");
                return;
            }
        }
        Settlement s = sim.settlementAt(pos, 0);
        if (s != null && !s.playerFounded && !s.citizenPlayers.contains(p.getUUID())) {
            Ui.msg(p, "You cannot claim land inside " + s.name + " unless you are a citizen.");
            return;
        }
        sim.claims.add(new WorldSim.Claim(p.getUUID(), p.getGameProfile().getName(), pos.immutable(), RADIUS));
        sim.setDirty();
        Ui.msg(p, "Boundary set. No settlement will build, fell trees or clear land within " + RADIUS + " blocks of this stone.");
    }

    public static void unclaim(ServerLevel level, BlockPos pos) {
        WorldSim sim = WorldSim.get(level.getServer());
        if (sim.claims.removeIf(c -> c.center().equals(pos))) sim.setDirty();
    }

    public static void describe(ServerPlayer p, ServerLevel level, BlockPos pos) {
        WorldSim sim = WorldSim.get(level.getServer());
        for (WorldSim.Claim c : sim.claims) {
            if (c.center().equals(pos)) {
                Ui.msg(p, "Land of " + c.ownerName() + ", " + c.radius() + " blocks around this stone.");
                return;
            }
        }
        Ui.msg(p, "An old marker stone. It claims nothing.");
    }
}

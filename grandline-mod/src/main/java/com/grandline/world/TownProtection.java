package com.grandline.world;

import com.grandline.core.GLConfig;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.Settlement;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Agreed rule: settlements are protected from block breaking (configurable). */
public final class TownProtection {
    public static boolean canModify(ServerLevel level, BlockPos pos) {
        if (!GLConfig.PROTECT_TOWNS.get()) return true;
        if (level.dimension() != Level.OVERWORLD) return true;
        WorldPlan plan = WorldPlan.of(level.getServer());
        if (plan == null) return true;
        return !protectedAt(plan, pos.getX(), pos.getZ());
    }

    public static boolean protectedAt(WorldPlan plan, int x, int z) {
        for (Island isl : plan.candidates(x, z)) {
            if (isl.settlementType == SettlementType.NONE && isl.landmarks.isEmpty()) continue;
            Settlement s = isl.settlement();
            if (!s.bounds(x, z)) continue;
            for (Plot p : s.plots) if (p.contains(x, z, 2)) return true;
            if (s.hasCenter && Math.abs(x - s.centerX) < 6 && Math.abs(z - s.centerZ) < 6) return true;
        }
        return false;
    }

    private TownProtection() {}
}

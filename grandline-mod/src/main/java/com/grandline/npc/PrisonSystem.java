package com.grandline.npc;

import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import com.grandline.world.settlement.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/** Finds the nearest Marine holding cell for arrests (Marine post / base marker positions). */
public final class PrisonSystem {
    @Nullable
    public static BlockPos findCell(ServerLevel level, BlockPos near) {
        WorldPlan plan = WorldPlan.of(level.getServer());
        if (plan == null) return null;
        Island best = null;
        double bd = Double.MAX_VALUE;
        for (Island i : plan.islands) {
            if (!(i.faction.equals("marine") || i.settlementType == com.grandline.world.settlement.SettlementType.TOWN || i.settlementType == com.grandline.world.settlement.SettlementType.CITY)) continue;
            double d = i.distance(near.getX(), near.getZ());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        if (best == null) return null;
        for (Plot p : best.settlement().plots) {
            if (p.type() == BuildingType.MARINE_POST || "marine_base".equals(p.landmark()) || "marine_headquarters".equals(p.landmark()) || "impel_down".equals(p.landmark())) {
                for (MarkerCollector.Marker m : MarkerCollector.markers(best, p)) {
                    if (m.kind().equals("cell")) return m.pos();
                }
            }
        }
        int x = best.x, z = best.z;
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private PrisonSystem() {}
}

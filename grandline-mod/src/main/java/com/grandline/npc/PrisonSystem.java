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

    /** A player sitting out their sentence in a cell (guards leave them alone until they break out). */
    public static boolean isJailed(net.minecraft.server.level.ServerPlayer sp) {
        return com.grandline.player.Characters.get(sp).flags.contains("imprisoned");
    }

    /** Every second: a prisoner who gets more than 10 blocks from their cell has escaped and is fair game again. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
            var d = com.grandline.player.Characters.get(sp);
            if (!d.flags.contains("imprisoned")) continue;
            BlockPos cell = null;
            for (String f : d.flags)
                if (f.startsWith("cell:")) {
                    String[] p = f.substring(5).split(",");
                    cell = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                }
            if (cell == null || sp.blockPosition().distSqr(cell) > 10 * 10) {
                d.flags.remove("imprisoned");
                d.flags.removeIf(f -> f.startsWith("cell:"));
                d.flags.add("escaped_prison");
                if (cell != null) {
                    d.bounty += Math.max(5_000_000L, d.bounty / 10);
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("grandline.prison.escaped").withStyle(net.minecraft.ChatFormatting.GOLD));
                }
                com.grandline.player.CharacterManager.markDirty(sp);
            }
        }
    }
}

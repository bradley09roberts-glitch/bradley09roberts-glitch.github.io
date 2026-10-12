package com.grandline.world;

import com.grandline.npc.HumanoidNpc;
import com.grandline.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Wilderness encounters on islands (bandit gangs, pirate landing parties, Marine patrols) that replace vanilla
 * night monsters. Sea encounters (Sea Kings, enemy ships) are added by the ships milestone.
 */
public final class Encounters {
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level.getGameTime() % 200 != 0) return;
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return;
        for (ServerPlayer sp : level.players()) {
            if (sp.isSpectator() || sp.isCreative() || level.random.nextInt(4) != 0) continue;
            Island isl = plan.islandAt(sp.getBlockX(), sp.getBlockZ());
            if (isl == null || TownProtection.protectedAt(plan, sp.getBlockX(), sp.getBlockZ())) continue;
            if (isl.settlement().bounds(sp.getBlockX(), sp.getBlockZ()) && isl.settlementType != com.grandline.world.settlement.SettlementType.RUINS) continue;
            int nearby = level.getEntitiesOfClass(HumanoidNpc.class, sp.getBoundingBox().inflate(48)).size();
            if (nearby > 6) continue;
            boolean night = !level.isDay();
            String role = isl.zone.isBlue() ? "bandit" : (night || level.random.nextBoolean()) ? "pirate" : "bandit";
            if (isl.faction.equals("marine")) role = "marine";
            int count = 1 + level.random.nextInt(isl.tier >= 5 ? 4 : 3);
            double ang = level.random.nextDouble() * Math.PI * 2;
            int bx = sp.getBlockX() + (int) (Math.cos(ang) * 28), bz = sp.getBlockZ() + (int) (Math.sin(ang) * 28);
            for (int i = 0; i < count; i++) {
                int x = bx + level.random.nextInt(5) - 2, z = bz + level.random.nextInt(5) - 2;
                if (!com.grandline.core.Chunks.ready(level, x, z)) continue; // loaded now (not still generating)
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y <= WorldPlan.SEA_LEVEL) continue;
                HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(level);
                if (npc == null) continue;
                npc.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360, 0);
                String r = (i == 0 && isl.tier >= 3 && role.equals("pirate")) ? "pirate_captain" : role;
                npc.configure(r, isl.tier, isl.id, new BlockPos(x, y, z), "", level.random);
                level.addFreshEntity(npc);
            }
        }
    }

    private Encounters() {}
}

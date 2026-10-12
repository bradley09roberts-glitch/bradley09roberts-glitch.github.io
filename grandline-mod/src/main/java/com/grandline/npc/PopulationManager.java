package com.grandline.npc;

import com.grandline.GrandLine;
import com.grandline.core.GLConfig;
import com.grandline.registry.ModEntities;
import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Efficient population: settlement NPCs are spawned from building markers only when a player comes near, once
 * per marker. Dead NPCs are replaced after an in-game day. Nothing is simulated as an entity while far away.
 */
public final class PopulationManager extends SavedData {
    /** marker key → game time when it may (re)spawn; Long.MAX = alive. */
    private final Map<String, Long> markers = new HashMap<>();
    private static final long RESPAWN = 24000L;

    public static PopulationManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PopulationManager::new, PopulationManager::load, null), GrandLine.MODID + "_population");
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level.getGameTime() % 40 != 0) return;
        WorldPlan plan = WorldPlan.of(server);
        if (plan == null) return;
        PopulationManager pm = get(server);
        int radius = GLConfig.NPC_SIM_RADIUS.get();
        for (ServerPlayer sp : level.players()) {
            for (Island isl : plan.candidates(sp.getBlockX(), sp.getBlockZ())) {
                if (isl.distance(sp.getBlockX(), sp.getBlockZ()) > isl.radius + radius) continue;
                if (isl.settlementType == com.grandline.world.settlement.SettlementType.NONE && isl.landmarks.isEmpty()) continue;
                pm.populate(level, isl, sp, radius);
            }
        }
    }

    private void populate(ServerLevel level, Island isl, ServerPlayer near, int radius) {
        long now = level.getGameTime();
        int budget = 4;
        int perSettlement = GLConfig.MAX_NPCS_PER_SETTLEMENT.get();
        int spawnedHere = 0;
        for (Plot p : isl.settlement().plots) {
            for (MarkerCollector.Marker m : MarkerCollector.markers(isl, p)) {
                if (!m.kind().equals("npc")) continue;
                if (m.pos().distSqr(near.blockPosition()) > (double) radius * radius) continue;
                String key = isl.id + "@" + m.pos().asLong();
                Long state = markers.get(key);
                if (state != null && (state == Long.MAX_VALUE || state > now)) {
                    spawnedHere++;
                    continue;
                }
                if (spawnedHere >= perSettlement && p.landmark().isEmpty()) continue;
                if (!level.isLoaded(m.pos())) continue;
                String role = m.data().getString("role");
                HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(level);
                if (npc == null) continue;
                npc.moveTo(m.pos().getX() + 0.5, m.pos().getY(), m.pos().getZ() + 0.5, level.random.nextFloat() * 360, 0);
                npc.configure(role, isl.tier, isl.id, m.pos(), key, level.random);
                level.addFreshEntity(npc);
                markers.put(key, Long.MAX_VALUE);
                setDirty();
                spawnedHere++;
                if (--budget <= 0) return;
            }
        }
    }

    public static void onNpcGone(ServerLevel level, GLNpc npc) {
        if (npc.markerKey.isEmpty()) return;
        PopulationManager pm = get(level.getServer());
        pm.markers.put(npc.markerKey, npc.notableId().isEmpty() ? level.getGameTime() + RESPAWN : Long.MAX_VALUE - 1);
        pm.setDirty();
    }

    public int trackedMarkers() {
        return markers.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag m = new CompoundTag();
        markers.forEach(m::putLong);
        tag.put("markers", m);
        return tag;
    }

    public static PopulationManager load(CompoundTag tag, HolderLookup.Provider provider) {
        PopulationManager pm = new PopulationManager();
        CompoundTag m = tag.getCompound("markers");
        for (String k : m.getAllKeys()) pm.markers.put(k, m.getLong(k));
        return pm;
    }
}

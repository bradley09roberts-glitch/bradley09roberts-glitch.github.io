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
import net.minecraft.util.RandomSource;
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
        if (server.overworld().getGameTime() % 40 != 0) return;
        PopulationManager pm = get(server);
        int radius = GLConfig.NPC_SIM_RADIUS.get();
        // the ocean world, the White Sea and the deep sea each have their own plan
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            WorldPlan plan = WorldPlan.forLevel(level);
            if (plan == null) continue;
            for (ServerPlayer sp : level.players()) {
                for (Island isl : plan.around(sp.getBlockX(), sp.getBlockZ())) {
                    if (isl.distance(sp.getBlockX(), sp.getBlockZ()) > isl.radius + radius) continue;
                    if (isl.settlementType == com.grandline.world.settlement.SettlementType.NONE && isl.landmarks.isEmpty()) continue;
                    pm.populate(level, isl, sp, radius);
                }
            }
        }
    }

    /** Locals of the sky and the deep sea are of the peoples who live there. */
    private static void localRace(HumanoidNpc npc, Island isl, RandomSource r) {
        if (!npc.raceId().equals("human")) return;
        if (isl.zone == com.grandline.world.SeaZone.SKY_SEA) npc.setRace(com.grandline.player.Race.SKY_PEOPLE);
        else if (isl.zone == com.grandline.world.SeaZone.DEEP_SEA) npc.setRace(r.nextInt(4) == 0 ? com.grandline.player.Race.MERFOLK : com.grandline.player.Race.FISHMAN);
    }

    private void populate(ServerLevel level, Island isl, ServerPlayer near, int radius) {
        long now = level.getGameTime();
        dockShipwright(level, isl, near, radius, now);
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
                localRace(npc, isl, level.random);
                level.addFreshEntity(npc);
                markers.put(key, Long.MAX_VALUE);
                setDirty();
                spawnedHere++;
                if (--budget <= 0) return;
            }
        }
    }

    /** The shipwright of a town with no yard: on the ground at the pier's landing (works in worlds made before too). */
    private void dockShipwright(ServerLevel level, Island isl, ServerPlayer near, int radius, long now) {
        var s = isl.settlement();
        if (!s.hasDockShipwright) return;
        int x = s.dockShipwrightX, z = s.dockShipwrightZ;
        if (near.distanceToSqr(x, near.getY(), z) > (double) radius * radius || !level.isLoaded(new net.minecraft.core.BlockPos(x, 64, z))) return;
        String key = isl.id + "@dock_shipwright";
        Long state = markers.get(key);
        if (state != null && (state == Long.MAX_VALUE || state > now)) return;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        HumanoidNpc npc = ModEntities.HUMANOID_NPC.get().create(level);
        if (npc == null) return;
        npc.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360, 0);
        npc.configure("shipwright", isl.tier, isl.id, pos, key, level.random);
        localRace(npc, isl, level.random);
        level.addFreshEntity(npc);
        markers.put(key, Long.MAX_VALUE);
        setDirty();
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

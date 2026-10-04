package com.squidgame.world;

import com.squidgame.build.ArenaId;
import com.squidgame.build.BlockBuffer;
import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent record of which arenas have been built (and at which builder version), their bounding
 * boxes and every marker / region the builders produced. Game logic reads markers from here, never
 * from hard-coded coordinates, so an arena can be redesigned without touching the rules.
 */
public final class ArenaData extends SavedData {
    public static final String KEY = "squidgame_arenas";
    private static final Factory<ArenaData> FACTORY = new Factory<>(ArenaData::new, ArenaData::load, DataFixTypes.LEVEL);

    /** Stored data for one arena. */
    public static final class Record {
        public int version;
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public final Map<String, List<Marker>> markers = new LinkedHashMap<>();
        public final Map<String, List<Region>> regions = new LinkedHashMap<>();
    }

    private final Map<ArenaId, Record> arenas = new EnumMap<>(ArenaId.class);

    public static ArenaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    public boolean isBuilt(ArenaId id) {
        return arenas.containsKey(id);
    }

    public boolean isUpToDate(ArenaId id, int version) {
        Record r = arenas.get(id);
        return r != null && r.version >= version;
    }

    public Record record(ArenaId id) {
        return arenas.get(id);
    }

    public void store(ArenaId id, int version, BlockBuffer buffer) {
        Record r = new Record();
        r.version = version;
        r.minX = buffer.minX();
        r.minY = buffer.minY();
        r.minZ = buffer.minZ();
        r.maxX = buffer.maxX();
        r.maxY = buffer.maxY();
        r.maxZ = buffer.maxZ();
        buffer.markers().forEach((k, v) -> r.markers.put(k, new ArrayList<>(v)));
        buffer.regions().forEach((k, v) -> r.regions.put(k, new ArrayList<>(v)));
        arenas.put(id, r);
        setDirty();
    }

    public void forget(ArenaId id) {
        if (arenas.remove(id) != null) {
            setDirty();
        }
    }

    // -------------------------------------------------------------- queries

    public List<Marker> markers(ArenaId id, String name) {
        Record r = arenas.get(id);
        return r == null ? List.of() : r.markers.getOrDefault(name, List.of());
    }

    public Marker marker(ArenaId id, String name) {
        List<Marker> l = markers(id, name);
        return l.isEmpty() ? null : l.get(0);
    }

    public List<Region> regions(ArenaId id, String name) {
        Record r = arenas.get(id);
        return r == null ? List.of() : r.regions.getOrDefault(name, List.of());
    }

    public Region region(ArenaId id, String name) {
        List<Region> l = regions(id, name);
        return l.isEmpty() ? null : l.get(0);
    }

    // -------------------------------------------------------------- persistence

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<ArenaId, Record> e : arenas.entrySet()) {
            Record r = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putString("arena", e.getKey().name());
            t.putInt("version", r.version);
            t.putIntArray("bounds", new int[]{r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ});
            ListTag ms = new ListTag();
            r.markers.values().forEach(l -> l.forEach(m -> {
                CompoundTag mt = new CompoundTag();
                mt.putString("n", m.name());
                mt.putDouble("x", m.x());
                mt.putDouble("y", m.y());
                mt.putDouble("z", m.z());
                mt.putFloat("yaw", m.yaw());
                if (m.data() != null && !m.data().isEmpty()) {
                    mt.putString("d", m.data());
                }
                ms.add(mt);
            }));
            t.put("markers", ms);
            ListTag rs = new ListTag();
            r.regions.values().forEach(l -> l.forEach(g -> {
                CompoundTag rt = new CompoundTag();
                rt.putString("n", g.name());
                rt.putIntArray("b", new int[]{g.minX(), g.minY(), g.minZ(), g.maxX(), g.maxY(), g.maxZ()});
                rs.add(rt);
            }));
            t.put("regions", rs);
            list.add(t);
        }
        tag.put("arenas", list);
        return tag;
    }

    private static ArenaData load(CompoundTag tag, HolderLookup.Provider provider) {
        ArenaData d = new ArenaData();
        ListTag list = tag.getList("arenas", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ArenaId id = ArenaId.byId(t.getString("arena"));
            if (id == null) {
                continue;
            }
            Record r = new Record();
            r.version = t.getInt("version");
            int[] b = t.getIntArray("bounds");
            if (b.length == 6) {
                r.minX = b[0];
                r.minY = b[1];
                r.minZ = b[2];
                r.maxX = b[3];
                r.maxY = b[4];
                r.maxZ = b[5];
            }
            ListTag ms = t.getList("markers", Tag.TAG_COMPOUND);
            for (int j = 0; j < ms.size(); j++) {
                CompoundTag mt = ms.getCompound(j);
                Marker m = new Marker(mt.getString("n"), mt.getDouble("x"), mt.getDouble("y"), mt.getDouble("z"),
                        mt.getFloat("yaw"), mt.getString("d"));
                r.markers.computeIfAbsent(m.name(), k -> new ArrayList<>()).add(m);
            }
            ListTag rs = t.getList("regions", Tag.TAG_COMPOUND);
            for (int j = 0; j < rs.size(); j++) {
                CompoundTag rt = rs.getCompound(j);
                int[] g = rt.getIntArray("b");
                if (g.length == 6) {
                    Region rg = new Region(rt.getString("n"), g[0], g[1], g[2], g[3], g[4], g[5]);
                    r.regions.computeIfAbsent(rg.name(), k -> new ArrayList<>()).add(rg);
                }
            }
            d.arenas.put(id, r);
        }
        return d;
    }
}

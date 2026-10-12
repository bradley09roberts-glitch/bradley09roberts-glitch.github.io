package com.grandline.npc.notable;

import com.grandline.GrandLine;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * World-wide fate of every notable character: not yet met, alive (with its entity UUID), sailing in a player's crew,
 * defeated (permanently — permadeath applies to the cast too) or arrested. Saved as {@code grandline_notables}.
 */
public final class NotableState extends SavedData {
    public enum Status {
        UNSPAWNED, ALIVE, RECRUITED, DEFEATED, ARRESTED;

        public boolean gone() {
            return this == DEFEATED || this == ARRESTED;
        }
    }

    public static final class Entry {
        public Status status = Status.UNSPAWNED;
        @Nullable public UUID entity;
        /** Who defeated/arrested/recruited them (free text for messages and the chronicle). */
        public String fate = "";
        public long since;
        /** Transient: consecutive checks in which the entity could not be found near its home. */
        int missing;
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private static NotableState cached;
    private static MinecraftServer cachedFor;

    public static NotableState get(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cached = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NotableState::new, NotableState::load, null), GrandLine.MODID + "_notables");
        cachedFor = server;
        return cached;
    }

    public Entry entry(String id) {
        return entries.computeIfAbsent(id, k -> new Entry());
    }

    @Nullable
    public Entry peek(String id) {
        return entries.get(id);
    }

    public Status status(String id) {
        Entry e = entries.get(id);
        return e == null ? Status.UNSPAWNED : e.status;
    }

    public boolean isGone(String id) {
        return status(id).gone();
    }

    public void set(String id, Status status, @Nullable UUID entity, String fate, long now) {
        Entry e = entry(id);
        e.status = status;
        e.entity = entity;
        if (fate != null && !fate.isEmpty()) e.fate = fate;
        e.since = now;
        e.missing = 0;
        setDirty();
    }

    public int count(Status s) {
        int c = 0;
        for (Entry e : entries.values()) if (e.status == s) c++;
        return c;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag all = new CompoundTag();
        entries.forEach((id, e) -> {
            CompoundTag t = new CompoundTag();
            t.putString("status", e.status.name().toLowerCase(Locale.ROOT));
            if (e.entity != null) t.putUUID("entity", e.entity);
            t.putString("fate", e.fate);
            t.putLong("since", e.since);
            all.put(id, t);
        });
        tag.put("notables", all);
        return tag;
    }

    public static NotableState load(CompoundTag tag, HolderLookup.Provider provider) {
        NotableState s = new NotableState();
        CompoundTag all = tag.getCompound("notables");
        for (String id : all.getAllKeys()) {
            CompoundTag t = all.getCompound(id);
            Entry e = new Entry();
            try {
                e.status = Status.valueOf(t.getString("status").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                e.status = Status.UNSPAWNED;
            }
            if (t.hasUUID("entity")) e.entity = t.getUUID("entity");
            e.fate = t.getString("fate");
            e.since = t.getLong("since");
            s.entries.put(id, e);
        }
        return s;
    }
}

package com.squidgame.tournament;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Snapshots of players currently "inside" the tournament (visitors, contestants, spectators), keyed by UUID. */
public final class PlayerStore {
    private final Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();

    public boolean has(UUID id) {
        return snapshots.containsKey(id);
    }

    public int size() {
        return snapshots.size();
    }

    public java.util.Set<UUID> ids() {
        return new java.util.LinkedHashSet<>(snapshots.keySet());
    }

    /** Captures the player's current state unless one is already stored (never overwrite the original). */
    public boolean capture(ServerPlayer p) {
        if (snapshots.containsKey(p.getUUID())) {
            return false;
        }
        snapshots.put(p.getUUID(), PlayerSnapshot.capture(p));
        return true;
    }

    /** Restores and forgets the snapshot. Returns false if none existed. */
    public boolean restore(ServerPlayer p) {
        PlayerSnapshot s = snapshots.remove(p.getUUID());
        if (s == null) {
            return false;
        }
        s.restore(p);
        return true;
    }

    public void forget(UUID id) {
        snapshots.remove(id);
    }

    public ListTag save() {
        ListTag list = new ListTag();
        snapshots.forEach((id, s) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.put("data", s.save());
            list.add(t);
        });
        return list;
    }

    public void load(ListTag list) {
        snapshots.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            snapshots.put(t.getUUID("id"), PlayerSnapshot.load(t.getCompound("data")));
        }
    }
}

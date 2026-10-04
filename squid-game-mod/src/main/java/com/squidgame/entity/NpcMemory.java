package com.squidgame.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Short and long term memory of one NPC. Entries expire after a number of ticks (or never). Behaviours store
 * what they have *observed* here (e.g. which bridge panels they saw break, how loud the doll chant was); the
 * tournament never hands NPCs hidden state, so their knowledge is limited to what a human could remember.
 */
public final class NpcMemory {
    private record Entry(Object value, long expiresAt) {
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private long now;

    /** Advance the memory clock (called by the owning entity once per AI step). */
    public void tick(long gameTime) {
        this.now = gameTime;
        if ((gameTime & 63) == 0) {
            entries.values().removeIf(e -> e.expiresAt != Long.MAX_VALUE && e.expiresAt < now);
        }
    }

    public void remember(String key, Object value) {
        entries.put(key, new Entry(value, Long.MAX_VALUE));
    }

    public void remember(String key, Object value, int ttlTicks) {
        entries.put(key, new Entry(value, now + ttlTicks));
    }

    @SuppressWarnings("unchecked")
    public <T> T recall(String key, T fallback) {
        Entry e = entries.get(key);
        if (e == null || (e.expiresAt != Long.MAX_VALUE && e.expiresAt < now)) {
            return fallback;
        }
        return (T) e.value;
    }

    public boolean knows(String key) {
        Entry e = entries.get(key);
        return e != null && (e.expiresAt == Long.MAX_VALUE || e.expiresAt >= now);
    }

    public void forget(String key) {
        entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    /** Numeric helper: stored double or fallback. */
    public double number(String key, double fallback) {
        Object v = recall(key, null);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    public int size() {
        return entries.size();
    }
}

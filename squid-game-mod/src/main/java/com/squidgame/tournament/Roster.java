package com.squidgame.tournament;

import com.squidgame.core.ContestantStatus;
import com.squidgame.core.util.Rng;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The contestants of one tournament, indexed by number and (for humans) by player UUID. */
public final class Roster {
    public static final int MAX_NUMBER = 456;

    private final Map<Integer, Contestant> byNumber = new LinkedHashMap<>();
    private final Map<UUID, Contestant> byPlayer = new LinkedHashMap<>();
    private final Set<Integer> used = new HashSet<>();

    public void add(Contestant c) {
        byNumber.put(c.number, c);
        used.add(c.number);
        if (c.playerId != null) {
            byPlayer.put(c.playerId, c);
        }
    }

    public void remove(Contestant c) {
        byNumber.remove(c.number);
        used.remove(c.number);
        if (c.playerId != null) {
            byPlayer.remove(c.playerId);
        }
    }

    public Contestant get(int number) {
        return byNumber.get(number);
    }

    public Contestant ofPlayer(UUID id) {
        return byPlayer.get(id);
    }

    public int allocateNumber(Rng rng, int preferred) {
        if (preferred >= 1 && preferred <= MAX_NUMBER && !used.contains(preferred)) {
            return preferred;
        }
        for (int i = 0; i < 4000; i++) {
            int n = 1 + rng.nextInt(MAX_NUMBER);
            if (!used.contains(n)) {
                return n;
            }
        }
        for (int n = 1; n <= MAX_NUMBER; n++) {
            if (!used.contains(n)) {
                return n;
            }
        }
        throw new IllegalStateException("no free contestant number");
    }

    public List<Contestant> all() {
        List<Contestant> l = new ArrayList<>(byNumber.values());
        l.sort(Comparator.comparingInt(c -> c.number));
        return l;
    }

    public List<Contestant> alive() {
        List<Contestant> l = new ArrayList<>();
        for (Contestant c : byNumber.values()) {
            if (c.status() == ContestantStatus.ALIVE) {
                l.add(c);
            }
        }
        l.sort(Comparator.comparingInt(c -> c.number));
        return l;
    }

    public int aliveCount() {
        int n = 0;
        for (Contestant c : byNumber.values()) {
            if (c.isAlive()) {
                n++;
            }
        }
        return n;
    }

    public int size() {
        return byNumber.size();
    }

    public int humanCount() {
        int n = 0;
        for (Contestant c : byNumber.values()) {
            if (c.isHuman()) {
                n++;
            }
        }
        return n;
    }

    public int aliveHumanCount() {
        int n = 0;
        for (Contestant c : byNumber.values()) {
            if (c.isHuman() && c.isAlive()) {
                n++;
            }
        }
        return n;
    }

    public List<Contestant> eliminatedInOrder() {
        List<Contestant> l = new ArrayList<>();
        for (Contestant c : byNumber.values()) {
            if (c.isEliminated()) {
                l.add(c);
            }
        }
        l.sort(Comparator.comparingInt(Contestant::eliminationOrder));
        return l;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Contestant c : byNumber.values()) {
            list.add(c.save());
        }
        return list;
    }

    public static Roster load(ListTag list) {
        Roster r = new Roster();
        for (int i = 0; i < list.size(); i++) {
            r.add(Contestant.load(list.getCompound(i)));
        }
        return r;
    }

    public static Roster load(CompoundTag tag, String key) {
        return load(tag.getList(key, Tag.TAG_COMPOUND));
    }
}

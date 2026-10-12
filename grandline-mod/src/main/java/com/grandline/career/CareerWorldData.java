package com.grandline.career;

import com.grandline.GrandLine;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * World-wide career state, saved as {@code grandline_careers}: the limited seats (three Admirals, one Fleet Admiral,
 * seven Warlords, one Chief of Staff) and the islands pirates hold as territory. Entries are keyed by the holder's
 * character id, so a permadeath (a new character id) frees them ({@link Careers} releases them on the death itself).
 */
public final class CareerWorldData extends SavedData {
    public static final String ADMIRAL = "admiral", FLEET_ADMIRAL = "fleet_admiral", WARLORD = "warlord", CHIEF_OF_STAFF = "chief_of_staff";
    public static final Map<String, Integer> SEATS = Map.of(ADMIRAL, 3, FLEET_ADMIRAL, 1, WARLORD, 7, CHIEF_OF_STAFF, 1);

    public record Holder(UUID character, UUID player, String name, long since) {}

    private final Map<String, List<Holder>> seats = new TreeMap<>();
    private final Map<String, Holder> territories = new TreeMap<>();

    private static CareerWorldData cached;
    private static MinecraftServer cachedFor;

    public static CareerWorldData get(MinecraftServer server) {
        if (cached != null && cachedFor == server) return cached;
        cached = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CareerWorldData::new, CareerWorldData::load, null), GrandLine.MODID + "_careers");
        cachedFor = server;
        return cached;
    }

    // ================================================================== seats

    public List<Holder> holders(String seat) {
        return Collections.unmodifiableList(seats.getOrDefault(seat, List.of()));
    }

    public boolean holds(String seat, UUID character) {
        for (Holder h : seats.getOrDefault(seat, List.of())) if (h.character.equals(character)) return true;
        return false;
    }

    /** A seat is open to this character: they hold it already, or fewer than its limit are taken. */
    public boolean open(String seat, UUID character) {
        return holds(seat, character) || seats.getOrDefault(seat, List.of()).size() < SEATS.getOrDefault(seat, Integer.MAX_VALUE);
    }

    public boolean take(String seat, UUID character, UUID player, String name, long now) {
        if (holds(seat, character)) return true;
        if (!open(seat, character)) return false;
        seats.computeIfAbsent(seat, k -> new ArrayList<>()).add(new Holder(character, player, name, now));
        setDirty();
        return true;
    }

    public void leave(String seat, UUID character) {
        List<Holder> l = seats.get(seat);
        if (l != null && l.removeIf(h -> h.character.equals(character))) setDirty();
    }

    // ================================================================== territories

    @Nullable
    public Holder territory(String islandId) {
        return territories.get(islandId);
    }

    public List<String> territoriesOf(UUID character) {
        List<String> out = new ArrayList<>();
        territories.forEach((k, v) -> {
            if (v.character.equals(character)) out.add(k);
        });
        return out;
    }

    public void claim(String islandId, UUID character, UUID player, String name, long now) {
        territories.put(islandId, new Holder(character, player, name, now));
        setDirty();
    }

    public void abandon(String islandId) {
        if (territories.remove(islandId) != null) setDirty();
    }

    /** A character is gone (permadeath) or left the career: every seat and territory it held is free again. */
    public int release(UUID character) {
        int n = 0;
        for (List<Holder> l : seats.values()) {
            int before = l.size();
            l.removeIf(h -> h.character.equals(character));
            n += before - l.size();
        }
        int before = territories.size();
        territories.values().removeIf(h -> h.character.equals(character));
        n += before - territories.size();
        if (n > 0) setDirty();
        return n;
    }

    // ================================================================== NBT

    private static CompoundTag holder(Holder h) {
        CompoundTag t = new CompoundTag();
        t.putUUID("character", h.character);
        t.putUUID("player", h.player);
        t.putString("name", h.name);
        t.putLong("since", h.since);
        return t;
    }

    private static Holder holder(CompoundTag t) {
        return new Holder(t.getUUID("character"), t.getUUID("player"), t.getString("name"), t.getLong("since"));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag s = new CompoundTag();
        seats.forEach((k, l) -> {
            ListTag lt = new ListTag();
            for (Holder h : l) lt.add(holder(h));
            s.put(k, lt);
        });
        tag.put("seats", s);
        CompoundTag t = new CompoundTag();
        territories.forEach((k, h) -> t.put(k, holder(h)));
        tag.put("territories", t);
        return tag;
    }

    public static CareerWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        CareerWorldData d = new CareerWorldData();
        CompoundTag s = tag.getCompound("seats");
        for (String k : s.getAllKeys()) {
            ListTag lt = s.getList(k, Tag.TAG_COMPOUND);
            List<Holder> l = new ArrayList<>();
            for (int i = 0; i < lt.size(); i++) l.add(holder(lt.getCompound(i)));
            d.seats.put(k, l);
        }
        CompoundTag t = tag.getCompound("territories");
        for (String k : t.getAllKeys()) d.territories.put(k, holder(t.getCompound(k)));
        return d;
    }
}

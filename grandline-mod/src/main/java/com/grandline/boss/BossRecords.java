package com.grandline.boss;

import com.grandline.GrandLine;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * What the world remembers about boss fights: which relics have dropped (each exists once per world) and which
 * players have already been rewarded for which boss (a yielding rival can be fought again, but pays out once).
 */
public final class BossRecords extends SavedData {
    private final Set<String> relics = new LinkedHashSet<>();
    private final Map<UUID, Set<String>> claims = new HashMap<>();

    public static BossRecords get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BossRecords::new, BossRecords::load, null), GrandLine.MODID + "_bosses");
    }

    /** @return true the first time: the relic drops now. */
    public boolean dropRelic(String boss) {
        boolean first = relics.add(boss);
        if (first) setDirty();
        return first;
    }

    public boolean relicDropped(String boss) {
        return relics.contains(boss);
    }

    /** @return true the first time this player is rewarded for this boss. */
    public boolean claim(UUID player, String boss) {
        boolean first = claims.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(boss);
        if (first) setDirty();
        return first;
    }

    public boolean claimed(UUID player, String boss) {
        return claims.getOrDefault(player, Set.of()).contains(boss);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag r = new ListTag();
        for (String s : relics) r.add(StringTag.valueOf(s));
        tag.put("relics", r);
        CompoundTag c = new CompoundTag();
        claims.forEach((u, set) -> {
            ListTag l = new ListTag();
            for (String s : set) l.add(StringTag.valueOf(s));
            c.put(u.toString(), l);
        });
        tag.put("claims", c);
        return tag;
    }

    public static BossRecords load(CompoundTag tag, HolderLookup.Provider provider) {
        BossRecords d = new BossRecords();
        ListTag r = tag.getList("relics", Tag.TAG_STRING);
        for (int i = 0; i < r.size(); i++) d.relics.add(r.getString(i));
        CompoundTag c = tag.getCompound("claims");
        for (String k : c.getAllKeys()) {
            try {
                UUID u = UUID.fromString(k);
                ListTag l = c.getList(k, Tag.TAG_STRING);
                Set<String> set = new LinkedHashSet<>();
                for (int i = 0; i < l.size(); i++) set.add(l.getString(i));
                d.claims.put(u, set);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return d;
    }
}

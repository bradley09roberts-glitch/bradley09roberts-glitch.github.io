package com.grandline.block;

import com.grandline.GrandLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** Regrowth timers for fruit blooms. */
public final class BloomData extends SavedData {
    private final Map<Long, Long> readyAt = new HashMap<>();

    public static BloomData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(BloomData::new, BloomData::load, null), GrandLine.MODID + "_blooms");
    }

    public boolean ready(BlockPos pos, long now) {
        return readyAt.getOrDefault(pos.asLong(), 0L) <= now;
    }

    public void harvested(BlockPos pos, long nextReady) {
        readyAt.put(pos.asLong(), nextReady);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag m = new CompoundTag();
        readyAt.forEach((k, v) -> m.putLong(Long.toString(k), v));
        tag.put("blooms", m);
        return tag;
    }

    public static BloomData load(CompoundTag tag, HolderLookup.Provider provider) {
        BloomData d = new BloomData();
        CompoundTag m = tag.getCompound("blooms");
        for (String k : m.getAllKeys()) d.readyAt.put(Long.parseLong(k), m.getLong(k));
        return d;
    }
}

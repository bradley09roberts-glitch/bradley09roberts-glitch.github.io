package com.longnight.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Small helpers for null-safe NBT persistence. */
public final class Nbt {
    private Nbt() {}

    public static void putUUID(CompoundTag t, String key, UUID id) {
        if (id != null) t.putUUID(key, id);
    }

    public static UUID getUUID(CompoundTag t, String key) {
        return t.hasUUID(key) ? t.getUUID(key) : null;
    }

    public static ListTag uuidList(Collection<UUID> ids) {
        ListTag l = new ListTag();
        for (UUID id : ids) l.add(NbtUtils.createUUID(id));
        return l;
    }

    public static List<UUID> readUuidList(CompoundTag t, String key) {
        List<UUID> out = new ArrayList<>();
        for (Tag x : t.getList(key, Tag.TAG_INT_ARRAY)) out.add(NbtUtils.loadUUID(x));
        return out;
    }

    public static ListTag stringList(Collection<String> s) {
        ListTag l = new ListTag();
        for (String x : s) l.add(StringTag.valueOf(x));
        return l;
    }

    public static List<String> readStringList(CompoundTag t, String key) {
        List<String> out = new ArrayList<>();
        for (Tag x : t.getList(key, Tag.TAG_STRING)) out.add(x.getAsString());
        return out;
    }

    public static void putPos(CompoundTag t, String key, BlockPos pos) {
        if (pos != null) t.putLong(key, pos.asLong());
    }

    public static BlockPos getPos(CompoundTag t, String key) {
        return t.contains(key) ? BlockPos.of(t.getLong(key)) : null;
    }
}

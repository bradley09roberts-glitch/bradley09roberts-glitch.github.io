package com.grandline.fruit;

import com.grandline.GrandLine;
import com.grandline.core.GLConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * World-wide Devil Fruit ledger enforcing "one of each fruit per world". A fruit is either in the POOL (can appear
 * on a fruit tree, in treasure, as a boss drop or at the black market), IN_WORLD (an item exists) or OWNED.
 * When an owner dies or an item is destroyed, the fruit returns to the pool and regrows somewhere else.
 */
public final class FruitWorldData extends SavedData {
    public enum State { POOL, IN_WORLD, OWNED }

    private final Map<String, State> states = new HashMap<>();
    private final Map<String, UUID> owners = new HashMap<>();
    private final Map<String, String> ownerNames = new HashMap<>();

    public static FruitWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FruitWorldData::new, FruitWorldData::load, null), GrandLine.MODID + "_fruits");
    }

    public State state(String id) {
        return states.getOrDefault(id, State.POOL);
    }

    @Nullable
    public UUID owner(String id) {
        return owners.get(id);
    }

    public String ownerName(String id) {
        return ownerNames.getOrDefault(id, "?");
    }

    /** Take a random pooled fruit (weighted toward the given tier band) and mark it as existing in the world. */
    @Nullable
    public String takeFromPool(RandomSource r, int minTier, int maxTier) {
        boolean dup = GLConfig.ALLOW_DUPLICATE_FRUITS.get();
        List<DevilFruit> candidates = new ArrayList<>();
        for (DevilFruit f : DevilFruits.available()) {
            if (f.tier < minTier || f.tier > maxTier) continue;
            if (!dup && state(f.id) != State.POOL) continue;
            candidates.add(f);
        }
        if (candidates.isEmpty()) return null;
        DevilFruit f = candidates.get(r.nextInt(candidates.size()));
        if (!dup) {
            states.put(f.id, State.IN_WORLD);
            setDirty();
        }
        return f.id;
    }

    /** Mark a specific fruit as existing (creative/commands). Returns false if it is already out in the world. */
    public boolean claimSpecific(String id) {
        if (GLConfig.ALLOW_DUPLICATE_FRUITS.get()) return true;
        if (state(id) != State.POOL) return false;
        states.put(id, State.IN_WORLD);
        setDirty();
        return true;
    }

    public void eaten(String id, UUID owner, String name) {
        states.put(id, State.OWNED);
        owners.put(id, owner);
        ownerNames.put(id, name);
        setDirty();
    }

    /** The owner died or the item was destroyed: the fruit returns to the pool and will regrow elsewhere. */
    public void release(MinecraftServer server, String id, @Nullable UUID owner) {
        if (owner != null && !owner.equals(owners.get(id)) && state(id) == State.OWNED) return;
        states.put(id, State.POOL);
        owners.remove(id);
        ownerNames.remove(id);
        setDirty();
        GrandLine.LOGGER.info("Devil Fruit {} returned to the world pool", id);
    }

    public void itemDestroyed(String id) {
        if (state(id) == State.IN_WORLD) {
            states.put(id, State.POOL);
            setDirty();
        }
    }

    public Map<String, State> snapshot() {
        Map<String, State> m = new TreeMap<>();
        for (DevilFruit f : DevilFruits.all()) m.put(f.id, state(f.id));
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag s = new CompoundTag();
        states.forEach((k, v) -> s.putString(k, v.name()));
        tag.put("states", s);
        CompoundTag o = new CompoundTag();
        owners.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("uuid", v);
            e.putString("name", ownerNames.getOrDefault(k, "?"));
            o.put(k, e);
        });
        tag.put("owners", o);
        return tag;
    }

    public static FruitWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        FruitWorldData d = new FruitWorldData();
        CompoundTag s = tag.getCompound("states");
        for (String k : s.getAllKeys()) {
            try {
                d.states.put(k, State.valueOf(s.getString(k)));
            } catch (IllegalArgumentException ignored) {}
        }
        CompoundTag o = tag.getCompound("owners");
        for (String k : o.getAllKeys()) {
            CompoundTag e = o.getCompound(k);
            if (e.hasUUID("uuid")) d.owners.put(k, e.getUUID("uuid"));
            d.ownerNames.put(k, e.getString("name"));
        }
        return d;
    }
}

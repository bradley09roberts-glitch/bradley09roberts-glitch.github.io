package com.longnight.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * A planned, growing, finished or damaged structure. {@code progress} counts blueprint steps
 * that have been PAID for (materials consumed exactly once per step). {@code placed} counts
 * steps that physically exist in the world. When a settlement is simulated in the distance,
 * progress advances abstractly and the world catches up (free of charge) when it is next loaded.
 */
public class Building {
    public enum State { PLANNED, CONSTRUCTING, COMPLETE, DAMAGED, RUINED }

    public UUID id;
    public BuildingType type;
    public BlockPos origin;      // minimum corner at floor level
    public int rotation;         // 0..3 quarter turns
    public int level = 1;
    public State state = State.PLANNED;
    public int progress;         // paid blueprint steps
    public int placed;           // physically placed blueprint steps
    public int total;            // blueprint length
    public String name = "";
    public long startedDay;
    public long completedDay = -1;
    public long seed;
    public final EnumMap<Commodity, Double> cost = new EnumMap<>(Commodity.class);
    public final List<UUID> workers = new ArrayList<>();
    public final List<UUID> residents = new ArrayList<>();
    /** set when a builder or the abstract planner finds the site unusable */
    public String problem = "";
    /** faith whose shrine block/decor the building uses */
    public String faith = "";
    /** ruins: the decay pass (and its loot chest) has been applied */
    public boolean decayed;

    public Building(UUID id, BuildingType type) {
        this.id = id;
        this.type = type;
    }

    public boolean usable() {
        return state == State.COMPLETE || state == State.DAMAGED;
    }

    public boolean underConstruction() {
        return state == State.PLANNED || state == State.CONSTRUCTING;
    }

    public int width() {
        return (rotation & 1) == 0 ? type.width : type.depth;
    }

    public int depth() {
        return (rotation & 1) == 0 ? type.depth : type.width;
    }

    public BlockPos centre() {
        return origin.offset(width() / 2, 0, depth() / 2);
    }

    public boolean contains(BlockPos p, int margin) {
        return p.getX() >= origin.getX() - margin && p.getX() < origin.getX() + width() + margin
                && p.getZ() >= origin.getZ() - margin && p.getZ() < origin.getZ() + depth() + margin
                && p.getY() >= origin.getY() - 8 && p.getY() <= origin.getY() + 24;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("type", type.id());
        Nbt.putPos(t, "origin", origin);
        t.putInt("rot", rotation);
        t.putInt("level", level);
        t.putString("state", state.name());
        t.putInt("progress", progress);
        t.putInt("placed", placed);
        t.putInt("total", total);
        t.putString("name", name);
        t.putLong("started", startedDay);
        t.putLong("completed", completedDay);
        t.putLong("seed", seed);
        CompoundTag c = new CompoundTag();
        cost.forEach((k, v) -> c.putDouble(k.id(), v));
        t.put("cost", c);
        t.put("workers", Nbt.uuidList(workers));
        t.put("residents", Nbt.uuidList(residents));
        t.putString("problem", problem);
        t.putString("faith", faith);
        t.putBoolean("decayed", decayed);
        return t;
    }

    public static Building load(CompoundTag t) {
        BuildingType type = BuildingType.byId(t.getString("type"));
        if (type == null) type = BuildingType.HOUSE;
        Building b = new Building(t.getUUID("id"), type);
        b.origin = Nbt.getPos(t, "origin");
        b.rotation = t.getInt("rot");
        b.level = t.getInt("level");
        b.state = State.valueOf(t.getString("state"));
        b.progress = t.getInt("progress");
        b.placed = t.getInt("placed");
        b.total = t.getInt("total");
        b.name = t.getString("name");
        b.startedDay = t.getLong("started");
        b.completedDay = t.getLong("completed");
        b.seed = t.getLong("seed");
        CompoundTag c = t.getCompound("cost");
        for (String k : c.getAllKeys()) {
            Commodity cm = Commodity.byId(k);
            if (cm != null) b.cost.put(cm, c.getDouble(k));
        }
        b.workers.addAll(Nbt.readUuidList(t, "workers"));
        b.residents.addAll(Nbt.readUuidList(t, "residents"));
        b.problem = t.getString("problem");
        b.faith = t.getString("faith");
        b.decayed = t.getBoolean("decayed");
        return b;
    }
}

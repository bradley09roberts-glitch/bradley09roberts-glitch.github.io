package com.longnight.sim;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

/**
 * A settlement ledger. Quantities are doubles so that abstract production can accrue
 * fractional amounts, but whole units are what players can withdraw.
 */
public class Stockpile {
    private final EnumMap<Commodity, Double> amounts = new EnumMap<>(Commodity.class);
    /** lifetime counters, useful for tests and the chronicle */
    private final EnumMap<Commodity, Double> produced = new EnumMap<>(Commodity.class);
    private final EnumMap<Commodity, Double> consumed = new EnumMap<>(Commodity.class);

    public double get(Commodity c) {
        return amounts.getOrDefault(c, 0.0);
    }

    public int whole(Commodity c) {
        return (int) Math.floor(get(c) + 1e-9);
    }

    public void add(Commodity c, double q) {
        if (q <= 0) return;
        amounts.merge(c, q, Double::sum);
        produced.merge(c, q, Double::sum);
    }

    /** Removes exactly q if available. Returns false (and changes nothing) otherwise. */
    public boolean take(Commodity c, double q) {
        if (q <= 0) return true;
        double have = get(c);
        if (have + 1e-9 < q) return false;
        amounts.put(c, Math.max(0, have - q));
        consumed.merge(c, q, Double::sum);
        return true;
    }

    public boolean has(Map<Commodity, Double> needs, double scale) {
        for (Map.Entry<Commodity, Double> e : needs.entrySet()) {
            if (get(e.getKey()) + 1e-9 < e.getValue() * scale) return false;
        }
        return true;
    }

    /** Atomically takes all inputs or none. */
    public boolean takeAll(Map<Commodity, Double> needs, double scale) {
        if (!has(needs, scale)) return false;
        for (Map.Entry<Commodity, Double> e : needs.entrySet()) take(e.getKey(), e.getValue() * scale);
        return true;
    }

    public double total() {
        double s = 0;
        for (double v : amounts.values()) s += v;
        return s;
    }

    public double foodUnits() {
        double s = 0;
        for (Commodity c : Commodity.values()) if (c.edible()) s += get(c) * c.nourishment;
        return s;
    }

    public double producedTotal(Commodity c) {
        return produced.getOrDefault(c, 0.0);
    }

    public double consumedTotal(Commodity c) {
        return consumed.getOrDefault(c, 0.0);
    }

    public Map<Commodity, Double> view() {
        return amounts;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        CompoundTag a = new CompoundTag();
        amounts.forEach((k, v) -> a.putDouble(k.id(), v));
        CompoundTag p = new CompoundTag();
        produced.forEach((k, v) -> p.putDouble(k.id(), v));
        CompoundTag c = new CompoundTag();
        consumed.forEach((k, v) -> c.putDouble(k.id(), v));
        t.put("a", a);
        t.put("p", p);
        t.put("c", c);
        return t;
    }

    public static Stockpile load(CompoundTag t) {
        Stockpile s = new Stockpile();
        readInto(t.getCompound("a"), s.amounts);
        readInto(t.getCompound("p"), s.produced);
        readInto(t.getCompound("c"), s.consumed);
        return s;
    }

    private static void readInto(CompoundTag t, EnumMap<Commodity, Double> map) {
        for (String k : t.getAllKeys()) {
            Commodity c = Commodity.byId(k);
            if (c != null) map.put(c, t.getDouble(k));
        }
    }
}

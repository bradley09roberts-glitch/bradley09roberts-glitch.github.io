package com.longnight.command;

import com.longnight.sim.Building;
import com.longnight.sim.Commodity;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consistency rules for the save data and the live world. Used by /longnight admin validate,
 * the GameTests and the scripted server tests.
 */
public final class Invariants {
    private Invariants() {}

    public static List<String> check(WorldSim sim, ServerLevel level) {
        List<String> err = new ArrayList<>(checkData(sim));
        if (level != null) {
            Map<UUID, Integer> bodies = LongNightCommand.countBodies(level);
            for (Map.Entry<UUID, Integer> e : bodies.entrySet()) {
                Person p = sim.person(e.getKey());
                if (e.getValue() > 1) err.add("person " + e.getKey() + " has " + e.getValue() + " bodies");
                if (p == null) err.add("body for unknown person " + e.getKey());
                else if (!p.alive) err.add("dead person has a body: " + p.fullName());
            }
        }
        return err;
    }

    public static List<String> checkData(WorldSim sim) {
        List<String> err = new ArrayList<>();
        Map<UUID, UUID> residence = new HashMap<>();
        for (Settlement s : sim.settlements.values()) {
            for (UUID id : s.residents) {
                Person p = sim.person(id);
                if (p == null) err.add(s.name + ": resident " + id + " does not exist");
                else if (!p.alive) err.add(s.name + ": dead resident " + p.fullName());
                else if (!s.id.equals(p.settlement)) err.add(s.name + ": resident " + p.fullName() + " thinks they live elsewhere");
                UUID prev = residence.put(id, s.id);
                if (prev != null && !prev.equals(s.id)) err.add("person " + id + " lives in two settlements");
            }
            for (Building b : s.buildings) {
                if (b.progress < 0 || b.placed < 0) err.add(s.name + ": negative progress on " + b.type);
                if (b.placed > b.progress) err.add(s.name + ": " + b.type + " has more blocks placed (" + b.placed + ") than paid for (" + b.progress + ")");
                if (b.progress > b.total) err.add(s.name + ": " + b.type + " progress beyond plan");
            }
            for (Commodity c : Commodity.values()) if (s.stock.get(c) < -1e-6) err.add(s.name + ": negative " + c);
            if (s.treasury < 0) err.add(s.name + ": negative treasury");
        }
        for (Person p : sim.people.values()) {
            if (p.alive && p.settlement != null && !residence.containsKey(p.id)) {
                Settlement s = sim.settlement(p.settlement);
                if (s != null) err.add(p.fullName() + " is not on the rolls of " + s.name);
            }
            if (p.alive && p.spouse != null) {
                Person sp = sim.person(p.spouse);
                if (sp != null && sp.alive && !p.id.equals(sp.spouse)) err.add("marriage of " + p.fullName() + " is one-sided");
            }
            if (!p.alive && p.deathDay == Long.MIN_VALUE) err.add(p.fullName() + " is dead without a date of death");
        }
        return err;
    }
}

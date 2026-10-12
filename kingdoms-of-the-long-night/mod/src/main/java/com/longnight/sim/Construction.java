package com.longnight.sim;

import com.longnight.world.Blueprint;
import com.longnight.world.BlueprintGen;

import java.util.Map;

/**
 * Pays for and records construction progress. A blueprint step is charged exactly once, when
 * {@code progress} advances past it; placing the block in the world later is free. This is the
 * invariant that prevents materials from being charged twice or construction being free.
 */
public final class Construction {
    private Construction() {}

    public static Blueprint blueprint(Settlement s, Building b) {
        String faith = b.faith.isEmpty() ? "hearth" : b.faith;
        return BlueprintGen.get(b.type, s.culture, b.level, b.seed, faith);
    }

    public static void initPlan(Settlement s, Building b) {
        Blueprint bp = blueprint(s, b);
        b.total = bp.steps.size();
        b.cost.clear();
        b.cost.putAll(bp.totalCost());
    }

    /**
     * Pays for up to {@code steps} further steps. Returns the number of steps actually paid for.
     * Stops (and records why) when materials run out.
     */
    public static int advance(WorldSim sim, Settlement s, Building b, int steps, long day) {
        if (!b.underConstruction()) return 0;
        Blueprint bp = blueprint(s, b);
        b.total = bp.steps.size();
        int done = 0;
        while (done < steps && b.progress < b.total) {
            Map<Commodity, Double> cost = bp.stepCost(b.progress);
            if (!s.stock.has(cost, 1)) {
                hew(s, cost);
                if (!s.stock.has(cost, 1)) {
                    b.problem = "Waiting for " + missing(s, cost) + ".";
                    break;
                }
            }
            s.stock.takeAll(cost, 1);
            b.progress++;
            done++;
            b.state = Building.State.CONSTRUCTING;
            b.problem = "";
        }
        if (b.progress >= b.total && b.underConstruction()) complete(sim, s, b, day);
        if (done > 0) sim.setDirty();
        return done;
    }

    /** Builders square timber themselves when no carpenter has made planks (wasteful but possible). */
    static void hew(Settlement s, Map<Commodity, Double> cost) {
        Double planks = cost.get(Commodity.PLANKS);
        if (planks != null && s.stock.get(Commodity.PLANKS) < planks && s.stock.take(Commodity.LOGS, 1)) {
            s.stock.add(Commodity.PLANKS, 2);
        }
    }

    static String missing(Settlement s, Map<Commodity, Double> cost) {
        for (Map.Entry<Commodity, Double> e : cost.entrySet()) {
            if (s.stock.get(e.getKey()) < e.getValue()) return e.getKey().displayName.toLowerCase();
        }
        return "materials";
    }

    public static void complete(WorldSim sim, Settlement s, Building b, long day) {
        b.state = Building.State.COMPLETE;
        b.completedDay = day;
        b.problem = "";
        s.morale = Math.min(1.0, s.morale + 0.03);
        String label = b.name.isEmpty() ? b.type.displayName.toLowerCase() : b.name;
        s.chronicle(day, "Completed the " + label + ".");
        int importance = switch (b.type) {
            case TEMPLE, TOWN_HALL, MARKET, WATCHTOWER, BARRACKS, LIBRARY, SCHOOL -> 2;
            default -> 1;
        };
        sim.addNews(day, "building", "The folk of " + s.name + " finished building a " + b.type.displayName.toLowerCase() + ".", s,
                importance, "item", b.type.displayName.toLowerCase());
        for (Person p : sim.residents(s)) {
            if (p.profession == Profession.BUILDER) {
                p.remember(day, "built", null, "helped build the " + b.type.displayName.toLowerCase() + " in " + s.name, 6);
                p.prestige++;
            }
        }
    }

    /** Remaining material bill for a building, for display. */
    public static String remainingBill(Settlement s, Building b) {
        Blueprint bp = blueprint(s, b);
        java.util.EnumMap<Commodity, Double> rest = new java.util.EnumMap<>(Commodity.class);
        for (int i = b.progress; i < bp.steps.size(); i++) bp.stepCost(i).forEach((k, v) -> rest.merge(k, v, Double::sum));
        StringBuilder sb = new StringBuilder();
        rest.forEach((k, v) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Math.round(v)).append(' ').append(k.displayName.toLowerCase());
        });
        return sb.length() == 0 ? "nothing" : sb.toString();
    }
}

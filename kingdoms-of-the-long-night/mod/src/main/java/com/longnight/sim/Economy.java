package com.longnight.sim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Production chains, storage, prices, taxes and caravans. A settlement only has what its people
 * made, traded for, or were given; nothing is created from nothing.
 */
public final class Economy {
    private Economy() {}

    public static final int CYCLES_PER_DAY = 8;

    public static void daily(WorldSim sim, long day) {
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT) continue;
            for (Person p : sim.residents(s)) {
                if (!sim.isAdult(p, day)) continue;
                equipTools(s, p, sim);
                // whatever part of the working day a body did not work (unwatched, walking, stuck) is worked here,
                // so watching a settlement neither adds to nor takes from what its people make
                int worked = Math.min(CYCLES_PER_DAY, sim.workedCyclesToday.getOrDefault(p.id, 0));
                if (p.profession.producesGoods() && worked < CYCLES_PER_DAY) {
                    work(sim, s, p, CYCLES_PER_DAY - worked, day);
                }
                // without a lumber camp or quarry, idle hands gather deadwood and fieldstone (how camps bootstrap);
                // bodies never gather physically, so this is always the abstract amount
                if (p.profession == Profession.LABOURER) gather(sim, s, 1.0);
                // builders whose work waits for materials go and fetch what they can by hand
                else if (p.profession == Profession.BUILDER && waitingForMaterials(s)) gather(sim, s, 1.0);
                // professions that serve rather than produce still earn a little and practise
                if (!p.profession.producesGoods() && p.profession != Profession.LABOURER) {
                    p.addSkill(p.profession, 0.25f * (0.5f + p.diligence));
                }
            }
            spoil(s);
            income(sim, s);
        }
        Caravans.daily(sim, day);
    }

    static boolean waitingForMaterials(Settlement s) {
        for (Building b : s.buildings) if (b.underConstruction() && b.problem.startsWith("Waiting for")) return true;
        return false;
    }

    /** Someone actually works this kind of place (a quarry with no quarrier yields nothing). */
    static boolean staffed(WorldSim sim, Settlement s, BuildingType t, Profession worker) {
        if (!s.hasUsable(t)) return false;
        for (Person p : sim.residents(s)) if (p.profession == worker && p.workplace != null) return true;
        return false;
    }

    static void gather(WorldSim sim, Settlement s, double share) {
        // without a carpentry, logs are split and hewn into rough planks by hand: slow and wasteful (a carpenter gets 4 a log)
        if (!s.hasUsable(BuildingType.CARPENTRY) && s.stock.get(Commodity.PLANKS) < 30 && s.stock.take(Commodity.LOGS, 2.0 * share)) {
            s.stock.add(Commodity.PLANKS, 4.0 * share);
        }
        boolean lumber = staffed(sim, s, BuildingType.LUMBER_CAMP, Profession.LUMBERJACK);
        if (!lumber && s.stock.get(Commodity.LOGS) < 40) s.stock.add(Commodity.LOGS, 2.5 * share);
        if (!lumber && s.stock.get(Commodity.FUEL) < 15) s.stock.add(Commodity.FUEL, 1.0 * share);
        if (!staffed(sim, s, BuildingType.QUARRY, Profession.QUARRIER) && s.stock.get(Commodity.STONE) < 60) s.stock.add(Commodity.STONE, 3.0 * share);
    }

    static void equipTools(Settlement s, Person p, WorldSim sim) {
        if (p.hasTools && sim.rng.nextFloat() < 0.04f) p.hasTools = false; // tools wear out
        if (!p.hasTools && p.profession.producesGoods() && s.stock.take(Commodity.TOOLS, 1)) p.hasTools = true;
        if (p.profession == Profession.GUARD || p.profession == Profession.SOLDIER) {
            if (p.weaponTier == 0 && s.stock.take(Commodity.WEAPONS, 1)) p.weaponTier = 1;
            if (p.armorTier == 0 && s.stock.take(Commodity.ARMOR, 1)) p.armorTier = 1;
        }
    }

    /**
     * Performs {@code cycles} work cycles (8 = a full day). Used both by the daily abstract
     * update and by embodied workers finishing a visible task, so output is identical either way.
     * Returns the commodity produced (or null) and sets the person's blocked reason when stuck.
     */
    public static Commodity work(WorldSim sim, Settlement s, Person p, double cycles, long day) {
        List<Profession.Recipe> recipes = p.profession.recipes();
        if (recipes.isEmpty() || cycles <= 0) return null;
        Building w = s.building(p.workplace);
        if (p.workplace != null && (w == null || !w.usable())) {
            p.blocked = "My workplace is damaged or gone.";
            return null;
        }
        double share = cycles / CYCLES_PER_DAY;
        Map<Commodity, Double> targets = targets(sim, s);
        // full stores stop surplus piling up, never the food people eat or the goods the builders are short of
        if (s.stock.total() >= s.storageCapacity() && !feedsPeople(recipes) && !shortOfAny(s, targets, recipes)) {
            p.blocked = "The stores are full; we need another storehouse.";
            return null;
        }
        String missing = null;
        for (Profession.Recipe r : recipes) {
            boolean wanted = false;
            for (Commodity out : r.outputs().keySet()) {
                if (s.stock.get(out) < targets.getOrDefault(out, 20.0) * 3) wanted = true;
            }
            if (!wanted) continue;
            if (!s.stock.has(r.inputs(), share)) {
                for (Map.Entry<Commodity, Double> in : r.inputs().entrySet()) {
                    if (s.stock.get(in.getKey()) < in.getValue() * share) {
                        missing = in.getKey().displayName.toLowerCase();
                        break;
                    }
                }
                continue;
            }
            s.stock.takeAll(r.inputs(), share);
            double f = productivity(sim, s, p, day);
            Commodity main = null;
            for (Map.Entry<Commodity, Double> out : r.outputs().entrySet()) {
                s.stock.add(out.getKey(), out.getValue() * share * f);
                if (main == null) main = out.getKey();
            }
            p.addSkill(p.profession, (float) (0.3 * share * (0.5 + p.diligence)));
            p.blocked = "";
            return main;
        }
        p.blocked = missing != null ? "I have no " + missing + " to work with." : "We have plenty already; I am helping where I can.";
        return null;
    }

    static boolean shortOfAny(Settlement s, Map<Commodity, Double> targets, List<Profession.Recipe> recipes) {
        for (Profession.Recipe r : recipes) {
            for (Commodity out : r.outputs().keySet()) if (s.stock.get(out) < targets.getOrDefault(out, 0.0)) return true;
        }
        return false;
    }

    static boolean feedsPeople(List<Profession.Recipe> recipes) {
        for (Profession.Recipe r : recipes) for (Commodity out : r.outputs().keySet()) if (out.nourishment > 0) return true;
        return false;
    }

    public static double productivity(WorldSim sim, Settlement s, Person p, long day) {
        double skill = 0.55 + p.skill(p.profession) / 100.0 * 0.9;
        double tools = p.hasTools ? 1.0 : 0.7;
        double mood = 0.85 + 0.15 * (p.mood() + 1) / 2 + (p.diligence - 0.5) * 0.2;
        double health = 0.5 + 0.5 * p.health;
        double season = 1.0;
        if (p.profession == Profession.FARMER || p.profession == Profession.SHEPHERD) {
            season = Calendar.harvestFactor(day, sim.rules.daysPerYear) * LongNightArc.blightFactor(sim);
        }
        if (p.profession == Profession.HUNTER && p.workplace == null) season = 0.6; // foraging without a lodge
        return skill * tools * mood * health * season;
    }

    /** How much of each good a settlement wants to keep in store. Drives work choices and prices. */
    public static Map<Commodity, Double> targets(WorldSim sim, Settlement s) {
        Map<Commodity, Double> t = new EnumMap<>(Commodity.class);
        int pop = Math.max(1, s.residents.size());
        double foodDays = 10;
        t.put(Commodity.GRAIN, pop * foodDays * 0.4 + 10);
        t.put(Commodity.BREAD, pop * foodDays * 0.4);
        t.put(Commodity.MEAT, pop * foodDays * 0.2);
        t.put(Commodity.FISH, pop * foodDays * 0.2);
        t.put(Commodity.VEGETABLES, pop * foodDays * 0.2);
        t.put(Commodity.FLOUR, s.hasUsable(BuildingType.BAKERY) ? 30.0 : 0.0);
        t.put(Commodity.ALE, pop * 2.0);
        t.put(Commodity.LOGS, 40.0);
        t.put(Commodity.PLANKS, 80.0);
        t.put(Commodity.STONE, 80.0);
        t.put(Commodity.GLASS, 12.0);
        t.put(Commodity.ORE, s.hasUsable(BuildingType.SMITHY) ? 15.0 : 0.0);
        t.put(Commodity.IRON, 12.0);
        t.put(Commodity.FUEL, 20.0 + pop);
        int workers = 0, fighters = 0;
        for (Person p : sim.residents(s)) {
            if (p.profession.producesGoods()) workers++;
            if (p.profession == Profession.GUARD || p.profession == Profession.SOLDIER) fighters++;
        }
        t.put(Commodity.TOOLS, workers * 0.5 + 2);
        t.put(Commodity.WEAPONS, fighters + 2.0);
        t.put(Commodity.ARMOR, fighters + 1.0);
        t.put(Commodity.WOOL, 10.0);
        t.put(Commodity.CLOTH, pop * 0.3 + 4);
        t.put(Commodity.LEATHER, 8.0);
        // construction needs
        for (Building b : s.buildings) {
            if (!b.underConstruction() || b.total <= 0) continue;
            double remaining = 1.0 - (double) b.progress / b.total;
            for (Map.Entry<Commodity, Double> c : b.cost.entrySet()) t.merge(c.getKey(), c.getValue() * remaining, Double::sum);
        }
        return t;
    }

    /** Local price in copper pennies; scarce goods cost more. */
    public static int price(WorldSim sim, Settlement s, Commodity c) {
        double target = targets(sim, s).getOrDefault(c, 10.0);
        double ratio = Math.sqrt((target + 4) / (s.stock.get(c) + 4));
        ratio = Math.max(0.35, Math.min(3.0, ratio));
        return Math.max(1, (int) Math.round(c.basePrice * ratio));
    }

    static void spoil(Settlement s) {
        double rate = s.hasUsable(BuildingType.GRANARY) ? 0.004 : 0.02;
        for (Commodity c : new Commodity[]{Commodity.MEAT, Commodity.FISH, Commodity.BREAD}) {
            double q = s.stock.get(c);
            if (q > 0) s.stock.take(c, q * rate);
        }
    }

    static void income(WorldSim sim, Settlement s) {
        // market dues, rents and fees, scaled by prosperity
        int pop = s.residents.size();
        int dues = (int) Math.round(pop * (0.6 + s.morale) * (s.hasUsable(BuildingType.MARKET) ? 1.6 : 1.0));
        s.treasury += dues;
        Faction f = sim.faction(s.faction);
        if (f != null && !f.destroyed) {
            int tax = (int) Math.round(dues * f.taxRate * 2);
            tax = Math.min(tax, s.treasury);
            s.treasury -= tax;
            f.treasury += tax;
        }
        // soldiers are paid
        int upkeep = 0;
        for (Person p : sim.residents(s)) if (p.profession == Profession.SOLDIER) upkeep++;
        s.treasury = Math.max(0, s.treasury - upkeep);
        // wages: a little coin reaches working people
        if (s.treasury > pop * 4) {
            for (Person p : sim.residents(s)) {
                if (p.profession != Profession.LABOURER && s.treasury > 0) {
                    p.coins++;
                    s.treasury--;
                }
            }
        }
    }

    public static List<Commodity> surplus(WorldSim sim, Settlement s) {
        Map<Commodity, Double> t = targets(sim, s);
        List<Commodity> out = new ArrayList<>();
        for (Commodity c : Commodity.values()) if (s.stock.get(c) > t.getOrDefault(c, 10.0) * 2 + 10) out.add(c);
        return out;
    }

    public static List<Commodity> shortages(WorldSim sim, Settlement s) {
        Map<Commodity, Double> t = targets(sim, s);
        List<Commodity> out = new ArrayList<>();
        for (Commodity c : Commodity.values()) {
            double target = t.getOrDefault(c, 0.0);
            if (target >= 4 && s.stock.get(c) < target * 0.4) out.add(c);
        }
        return out;
    }
}

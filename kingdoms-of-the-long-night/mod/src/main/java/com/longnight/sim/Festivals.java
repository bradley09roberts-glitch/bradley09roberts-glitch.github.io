package com.longnight.sim;

import com.longnight.content.Content;
import com.longnight.content.Faith;

import java.util.UUID;

/**
 * Holy days and gatherings. Each faith keeps its festival in its month; every settlement keeps the
 * harvest home in Reaping. A feast uses food and ale from the stores (a poor town keeps a thin
 * feast), lifts spirits, and draws people to the shrine or temple during the day. The dead are
 * mourned the day after they die: family and friends gather at the burial ground.
 */
public final class Festivals {
    private Festivals() {}

    public static void daily(WorldSim sim, long day) {
        int month = Calendar.month(day, sim.rules.daysPerYear);
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT || s.residents.size() < 3) continue;
            String faithId = Planner.dominantFaith(sim, s);
            Faith f = Content.faith(faithId);
            String name = null;
            if (!f.festival.isEmpty() && f.festivalMonth == month && !f.forbidden) name = f.festival; // the Choir keeps its rites in secret
            else if (month == 4) name = "the Harvest Home";
            if (name == null) {
                s.festival = "";
                continue;
            }
            if (Calendar.month(day - 1, sim.rules.daysPerYear) == month && name.equals(s.festival)) continue; // already begun
            s.festival = name;
            s.festivalDay = day;
            int pop = s.residents.size();
            boolean ale = s.stock.take(Commodity.ALE, Math.max(1, pop * 0.4));
            boolean feast = s.stock.foodUnits() > pop * 4;
            if (feast) {
                for (Commodity c : new Commodity[]{Commodity.BREAD, Commodity.MEAT, Commodity.GRAIN}) {
                    double q = Math.min(s.stock.get(c), pop * 0.3);
                    if (q > 0) s.stock.take(c, q);
                }
            }
            float lift = (feast ? 0.15f : 0.05f) + (ale ? 0.1f : 0f);
            for (Person p : sim.residents(s)) {
                p.social = Math.min(1f, p.social + lift);
                if (p.faith.equals(faithId) || name.equals("the Harvest Home")) {
                    p.remember(day, "festival", null, "kept " + name + " in " + s.name + (feast ? " with a fine feast" : ""), feast ? 10 : 4);
                }
            }
            s.morale = Math.min(1, s.morale + (feast ? 0.05 : 0.01));
            sim.addNews(day, "festival", "The folk of " + s.name + " kept " + name + (feast ? " with a feast." : ", though the tables were bare."),
                    s, 1, "item", name);
        }
    }

    /** Called when someone dies: their funeral is tomorrow. */
    public static void funeral(WorldSim sim, Settlement s, Person dead, long day) {
        if (s == null || s.kind != Settlement.Kind.SETTLEMENT) return;
        s.funeralFor = dead.id;
        s.funeralDay = day + 1;
    }

    public static boolean mourns(WorldSim sim, Person p, Settlement s, long day) {
        if (s.funeralFor == null || s.funeralDay != day) return false;
        UUID d = s.funeralFor;
        if (d.equals(p.spouse) || d.equals(p.mother) || d.equals(p.father) || p.children.contains(d)) return true;
        return p.opinionOf(d) > 35 || p.profession == Profession.PRIEST || p.profession == Profession.GRAVEDIGGER;
    }
}

package com.longnight.sim;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Trade between settlements. A caravan carries surplus goods to a settlement that lacks them,
 * is paid at the destination's prices, and carries news both ways. Caravans can be robbed on
 * roads that pass near outlaw camps. They are abstract (not yet embodied as walking traders).
 */
public final class Caravans {
    private Caravans() {}

    static final double BLOCKS_PER_DAY = 700;

    public static void daily(WorldSim sim, long day) {
        arrive(sim, day);
        dispatch(sim, day);
    }

    static void dispatch(WorldSim sim, long day) {
        for (Settlement from : sim.settlements.values()) {
            if (from.kind != Settlement.Kind.SETTLEMENT || from.residents.size() < 5) continue;
            boolean market = from.hasUsable(BuildingType.MARKET);
            if (!market && Math.floorMod(day + from.id.hashCode(), 3) != 0) continue;
            if (busy(sim, from)) continue;
            List<Commodity> surplus = Economy.surplus(sim, from);
            if (surplus.isEmpty()) continue;
            Settlement best = null;
            Commodity bestGood = null;
            double bestScore = 0;
            for (Settlement to : sim.settlements.values()) {
                if (to == from || to.kind != Settlement.Kind.SETTLEMENT || to.center == null || from.center == null) continue;
                if (!canTrade(sim, from, to)) continue;
                double dist = Math.sqrt(from.center.distSqr(to.center));
                if (dist > 2000) continue;
                List<Commodity> need = Economy.shortages(sim, to);
                for (Commodity c : surplus) {
                    if (!need.contains(c)) continue;
                    double score = Economy.price(sim, to, c) / (double) Economy.price(sim, from, c) / (1 + dist / 800);
                    if (score > bestScore) {
                        bestScore = score;
                        best = to;
                        bestGood = c;
                    }
                }
            }
            if (best == null) continue;
            WorldSim.Caravan cv = new WorldSim.Caravan();
            cv.from = from.id;
            cv.to = best.id;
            cv.departDay = day;
            double dist = Math.sqrt(from.center.distSqr(best.center));
            cv.arriveDay = day + Math.max(1, (long) Math.ceil(dist / BLOCKS_PER_DAY));
            double qty = Math.min(40, from.stock.get(bestGood) - Economy.targets(sim, from).getOrDefault(bestGood, 10.0));
            if (qty < 5) continue;
            from.stock.take(bestGood, qty);
            cv.goods.put(bestGood, qty);
            for (Map.Entry<Long, Long> n : from.knownNews.entrySet()) cv.newsCarried.add(n.getKey());
            for (Person p : sim.residents(from)) {
                if (p.profession == Profession.MERCHANT || (cv.leader == null && sim.isAdult(p, day) && p.profession == Profession.LABOURER)) {
                    cv.leader = p.id;
                    p.activity = "leading a caravan to " + best.name;
                    break;
                }
            }
            sim.caravans.add(cv);
            from.chronicle(day, "A caravan left for " + best.name + " with " + Math.round(qty) + " " + bestGood.displayName.toLowerCase() + ".");
        }
    }

    static boolean busy(WorldSim sim, Settlement s) {
        int n = 0;
        for (WorldSim.Caravan c : sim.caravans) if (s.id.equals(c.from) && !c.returning) n++;
        return n >= (s.hasUsable(BuildingType.MARKET) ? 2 : 1);
    }

    public static boolean canTrade(WorldSim sim, Settlement a, Settlement b) {
        if (a.faction == null || b.faction == null || a.faction.equals(b.faction)) return true;
        Faction fa = sim.faction(a.faction);
        if (fa == null) return true;
        Faction.Stance st = fa.stance(b.faction);
        return st != Faction.Stance.WAR && fa.relation(b.faction) > -40;
    }

    static void arrive(WorldSim sim, long day) {
        Iterator<WorldSim.Caravan> it = sim.caravans.iterator();
        List<WorldSim.Caravan> spawned = new ArrayList<>();
        while (it.hasNext()) {
            WorldSim.Caravan cv = it.next();
            Settlement from = sim.settlement(cv.from);
            Settlement to = sim.settlement(cv.to);
            if (to == null || from == null) {
                it.remove();
                continue;
            }
            // outlaws prey on the roads
            if (!cv.returning && Threats.ambush(sim, cv, from, to, day)) {
                it.remove();
                continue;
            }
            if (day < cv.arriveDay) continue;
            it.remove();
            // goods and news arrive
            for (long n : cv.newsCarried) to.knownNews.putIfAbsent(n, day);
            if (cv.returning) {
                from.treasury += 0; // payment already settled on arrival leg
                to.treasury += cv.coins;
                Person leader = sim.person(cv.leader);
                if (leader != null && leader.alive) leader.activity = "";
                continue;
            }
            int pay = 0;
            for (Map.Entry<Commodity, Double> g : cv.goods.entrySet()) {
                to.stock.add(g.getKey(), g.getValue());
                pay += (int) Math.round(g.getValue() * Economy.price(sim, to, g.getKey()) * 0.8);
            }
            pay = Math.min(pay, to.treasury);
            to.treasury -= pay;
            to.chronicle(day, "A caravan from " + from.name + " arrived.");
            if (sim.rng.nextFloat() < 0.5f) {
                sim.addNews(day, "trade", "A caravan from " + from.name + " reached " + to.name + ".", to, 1,
                        "other_settlement", from.name);
            }
            // the return journey carries payment and news home
            WorldSim.Caravan back = new WorldSim.Caravan();
            back.from = to.id;
            back.to = from.id;
            back.departDay = day;
            back.arriveDay = day + Math.max(1, cv.arriveDay - cv.departDay);
            back.coins = pay;
            back.returning = true;
            back.leader = cv.leader;
            for (Map.Entry<Long, Long> n : to.knownNews.entrySet()) back.newsCarried.add(n.getKey());
            spawned.add(back);
            // trade warms relations between realms
            Faction fa = sim.faction(from.faction);
            if (fa != null && to.faction != null && !to.faction.equals(from.faction)) {
                fa.changeRelation(to.faction, 1);
                Faction fb = sim.faction(to.faction);
                if (fb != null) fb.changeRelation(from.faction, 1);
            }
        }
        sim.caravans.addAll(spawned);
    }
}

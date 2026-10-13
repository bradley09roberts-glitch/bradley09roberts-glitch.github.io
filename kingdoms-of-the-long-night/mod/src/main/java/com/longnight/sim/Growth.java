package com.longnight.sim;

import com.longnight.content.Content;
import com.longnight.content.Culture;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Migration and the founding of new places: newcomers drift to prosperous settlements,
 * the unhappy leave, war and plunder make refugees, and crowded towns send out bands of
 * settlers who pitch camp elsewhere and may one day found realms of their own.
 */
public final class Growth {
    private Growth() {}

    public static void daily(WorldSim sim, long day) {
        Random r = sim.rng;
        for (Settlement s : new ArrayList<>(sim.settlements.values())) {
            if (s.kind != Settlement.Kind.SETTLEMENT) continue;
            List<Person> people = sim.residents(s);
            if (people.isEmpty()) {
                abandon(sim, s, day);
                continue;
            }
            double foodDays = s.stock.foodUnits() / Math.max(1, people.size());
            int room = s.housingCapacity() - people.size();
            double mood = 0;
            for (Person p : people) mood += p.mood();
            mood /= people.size();
            s.morale = Math.max(0, Math.min(1, s.morale * 0.9 + (mood + 1) / 2 * 0.1));
            // newcomers
            if (foodDays > 6 && room >= 2 && mood > -0.1 && r.nextFloat() < 0.18f) immigrate(sim, s, day, r);
            // the unhappy leave
            for (Person p : people) {
                if (!sim.isAdult(p, day) || p.profession == Profession.RULER) continue;
                if (p.mood() < -0.5f && r.nextFloat() < 0.04f) emigrate(sim, s, p, day, r);
            }
            // crowded, well-fed settlements send out settlers
            if (people.size() >= 18 && (room < 0 || people.size() > 40) && foodDays > 4 && r.nextFloat() < 0.05f) foundCamp(sim, s, day, r);
        }
    }

    static void immigrate(WorldSim sim, Settlement s, long day, Random r) {
        // refugees first: someone from a recently plundered or war-torn place
        for (Settlement o : sim.settlements.values()) {
            if (o == s || o.kind != Settlement.Kind.SETTLEMENT || o.morale > 0.3) continue;
            for (Person p : sim.residents(o)) {
                if (sim.isAdult(p, day) && p.spouse == null && p.profession != Profession.RULER && p.mood() < 0) {
                    sim.join(p, s);
                    p.remember(day, "refugee", null, "fled " + o.name + " for " + s.name, -10);
                    sim.addNews(day, "refugees", p.fullName() + ", a refugee from " + o.name + ", found shelter in " + s.name + ".", s, 2,
                            "other_settlement", o.name, "person", p.fullName());
                    return;
                }
            }
        }
        // otherwise a wanderer: mostly the settlement's own people, sometimes strangers
        String culture = s.culture;
        if (r.nextFloat() < 0.18f) culture = Content.CULTURE_IDS[r.nextInt(Content.CULTURE_IDS.length)];
        if (culture.equals("skrell") && r.nextFloat() < 0.5f) culture = s.culture;
        long birth = day - (long) ((sim.rules.adultAge + r.nextInt(20)) * sim.rules.daysPerYear);
        Person p = sim.createPerson(culture, null, birth, r);
        sim.join(p, s);
        giveTrade(p, r);
        String what = culture.equals(s.culture) ? "A newcomer" : "A " + Content.culture(culture).name + " traveller";
        sim.addNews(day, "newcomer", what + ", " + p.fullName() + ", has settled in " + s.name + ".", s, 1, "person", p.fullName());
        p.remember(day, "arrival", null, "made a new home in " + s.name, 10);
    }

    public static void giveTrade(Person p, Random r) {
        Profession[] trades = {Profession.FARMER, Profession.LUMBERJACK, Profession.BUILDER, Profession.QUARRIER, Profession.CARPENTER,
                Profession.BLACKSMITH, Profession.HUNTER, Profession.MINER, Profession.BAKER, Profession.GUARD};
        Profession t = trades[r.nextInt(trades.length)];
        p.skills.put(t, 20f + r.nextInt(45));
        Profession t2 = trades[r.nextInt(trades.length)];
        p.skills.merge(t2, 5f + r.nextInt(20), Math::max);
    }

    static void emigrate(WorldSim sim, Settlement s, Person p, long day, Random r) {
        Culture c = Content.culture(p.culture);
        Settlement dest = null;
        double best = Double.MAX_VALUE;
        for (Settlement o : sim.settlements.values()) {
            if (o == s || o.kind != Settlement.Kind.SETTLEMENT || o.center == null) continue;
            if (o.housingCapacity() <= o.residents.size()) continue;
            double d = Math.sqrt(o.center.distSqr(s.center)) * (o.culture.equals(c.id) ? 1 : 2);
            if (d < best) {
                best = d;
                dest = o;
            }
        }
        if (dest == null) return;
        // the family goes together
        List<Person> family = new ArrayList<>();
        family.add(p);
        Person spouse = sim.person(p.spouse);
        if (spouse != null && spouse.alive && s.id.equals(spouse.settlement)) family.add(spouse);
        for (UUID cid : p.children) {
            Person ch = sim.person(cid);
            if (ch != null && ch.alive && !sim.isAdult(ch, day) && s.id.equals(ch.settlement)) family.add(ch);
        }
        for (Person f : family) {
            sim.join(f, dest);
            f.remember(day, "emigrated", null, "left " + s.name + " for " + dest.name, -5);
        }
        sim.addNews(day, "emigration", p.fullName() + (family.size() > 1 ? " and family" : "") + " left " + s.name + " for " + dest.name + ".",
                s, 1, "person", p.fullName(), "other_settlement", dest.name);
    }

    static void foundCamp(WorldSim sim, Settlement parent, long day, Random r) {
        List<Person> people = sim.residents(parent);
        Person leader = people.stream().filter(p -> sim.isAdult(p, day) && p.profession != Profession.RULER)
                .max(Comparator.comparingDouble(p -> p.ambition + p.bravery * 0.5)).orElse(null);
        if (leader == null || leader.ambition < 0.55f) return;
        BlockPos site = null;
        double best = Double.MAX_VALUE;
        for (BlockPos sp : sim.spareSites) {
            double d = Math.sqrt(sp.distSqr(new BlockPos(parent.center.getX(), sp.getY(), parent.center.getZ())));
            if (d < 220 || d > 900) continue;
            boolean taken = false;
            for (Settlement o : sim.settlements.values()) {
                if (o.center != null && Math.sqrt(o.center.distSqr(new BlockPos(sp.getX(), o.center.getY(), sp.getZ()))) < o.radius + 90) taken = true;
            }
            if (taken || sim.claimed(sp, 80)) continue;
            if (d < best) {
                best = d;
                site = sp;
            }
        }
        if (site == null) return;
        sim.spareSites.remove(site);
        Settlement camp = WorldBuilder.newSettlement(sim, parent.culture, site, day, WorldBuilder.uniqueCamp(sim, Content.names(parent.culture), r));
        camp.tier = Settlement.Tier.CAMP;
        camp.radius = 30;
        // settlers: the leader's household plus a few young adults
        List<Person> settlers = new ArrayList<>();
        settlers.add(leader);
        Person sp = sim.person(leader.spouse);
        if (sp != null && sp.alive) settlers.add(sp);
        for (UUID cid : leader.children) {
            Person ch = sim.person(cid);
            if (ch != null && ch.alive && parent.id.equals(ch.settlement)) settlers.add(ch);
        }
        for (Person p : people) {
            if (settlers.size() >= 7) break;
            if (!settlers.contains(p) && sim.isAdult(p, day) && p.spouse == null && p.ambition > 0.45f && p.profession != Profession.RULER) {
                settlers.add(p);
            }
        }
        for (Person p : settlers) {
            sim.join(p, camp);
            p.remember(day, "pioneer", leader.id, "set out with " + leader.given + " to found " + camp.name, 15);
        }
        // they take supplies
        for (Commodity c : new Commodity[]{Commodity.GRAIN, Commodity.BREAD, Commodity.PLANKS, Commodity.LOGS, Commodity.STONE, Commodity.TOOLS}) {
            double q = Math.min(parent.stock.get(c) * 0.15, 40);
            if (q > 0 && parent.stock.take(c, q)) camp.stock.add(c, q);
        }
        Faction pf = sim.faction(parent.faction);
        boolean independent = pf == null || (leader.ambition > 0.8f && leader.opinionOf(pf.ruler) < 0);
        if (independent && pf != null) {
            Politics.secede(sim, pf, camp, leader, day, "struck out on their own");
        } else {
            camp.faction = parent.faction;
        }
        Building tents = new Building(UUID.randomUUID(), BuildingType.CAMPSITE);
        tents.seed = r.nextLong();
        tents.origin = camp.center.offset(-4, 0, 5);
        tents.faith = leader.faith;
        Construction.initPlan(camp, tents);
        tents.progress = tents.total;
        tents.state = Building.State.COMPLETE;
        tents.completedDay = day;
        camp.buildings.add(tents);
        String text = leader.fullName() + " led settlers out of " + parent.name + " to found " + camp.name + ".";
        sim.addNews(day, "founding", text, parent, 3, "person", leader.fullName(), "other_settlement", camp.name);
        camp.knownNews.put(sim.nextNewsId - 1, day);
        parent.chronicle(day, text);
        camp.chronicle(day, "Founded by " + leader.fullName() + " and " + (settlers.size() - 1) + " companions from " + parent.name + ".");
    }

    static void abandon(WorldSim sim, Settlement s, long day) {
        s.kind = Settlement.Kind.RUIN;
        for (Building b : s.buildings) {
            // empty houses fall into ruin; what was never raised in the world appears as a ruin when found
            b.state = Building.State.RUINED;
            if (b.placed == 0) b.progress = (int) (b.total * 0.6);
            else b.progress = b.placed;
        }
        s.chronicle(day, s.name + " was abandoned.");
        sim.addNews(day, "abandoned", s.name + " stands empty; the last of its people are gone.", s, 3);
    }
}

package com.longnight.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Who does what. People keep their trade unless their workplace is gone or the settlement is in
 * real need (hunger first). Vacancies are filled by the best-suited person, preferring those who
 * already have skill, then apprentices of that trade, then anyone idle.
 */
public final class Jobs {
    private Jobs() {}

    public static List<Profession> professionsFor(BuildingType t) {
        return switch (t) {
            case FARM -> List.of(Profession.FARMER, Profession.FARMER);
            case LUMBER_CAMP -> List.of(Profession.LUMBERJACK, Profession.LUMBERJACK);
            case QUARRY -> List.of(Profession.QUARRIER, Profession.QUARRIER);
            case MINE -> List.of(Profession.MINER, Profession.MINER);
            case FISHERY -> List.of(Profession.FISHER, Profession.FISHER);
            case HUNTING_LODGE -> List.of(Profession.HUNTER);
            case PASTURE -> List.of(Profession.SHEPHERD);
            case MILL -> List.of(Profession.MILLER);
            case BAKERY -> List.of(Profession.BAKER);
            case CARPENTRY -> List.of(Profession.CARPENTER, Profession.CARPENTER);
            case SMITHY -> List.of(Profession.BLACKSMITH, Profession.BLACKSMITH);
            case WEAVERY -> List.of(Profession.WEAVER, Profession.WEAVER);
            case TAVERN -> List.of(Profession.INNKEEPER, Profession.BREWER);
            case MARKET -> List.of(Profession.MERCHANT, Profession.MERCHANT);
            case SHRINE -> List.of(Profession.PRIEST);
            case TEMPLE -> List.of(Profession.PRIEST, Profession.PRIEST);
            case WATCHTOWER -> List.of(Profession.GUARD);
            case BARRACKS -> List.of(Profession.SOLDIER, Profession.SOLDIER, Profession.SOLDIER, Profession.SOLDIER);
            case TOWN_HALL -> List.of(Profession.STEWARD);
            case SCHOOL -> List.of(Profession.TEACHER);
            case LIBRARY -> List.of(Profession.SCHOLAR);
            case HEALER_HOUSE -> List.of(Profession.HEALER);
            case GRAVEYARD -> List.of(Profession.GRAVEDIGGER);
            case BUILDERS_YARD -> List.of(Profession.BUILDER, Profession.BUILDER, Profession.BUILDER);
            default -> List.of();
        };
    }

    record Slot(Building building, Profession profession, int priority) {}

    public static void assign(WorldSim sim, Settlement s, long day) {
        List<Person> people = sim.residents(s);
        List<Person> adults = new ArrayList<>();
        for (Person p : people) {
            if (sim.isAdult(p, day) && p.profession != Profession.RULER && p.health > 0.25f) adults.add(p);
            else if (!sim.isAdult(p, day)) {
                p.profession = Profession.LABOURER;
                p.workplace = null;
            }
        }
        if (adults.isEmpty()) return;

        double foodDays = s.stock.foodUnits() / Math.max(1, people.size());
        boolean hungry = foodDays < 4;

        // 1. build the list of slots
        List<Slot> slots = new ArrayList<>();
        for (Building b : s.buildings) {
            if (!b.usable()) continue;
            for (Profession p : professionsFor(b.type)) slots.add(new Slot(b, p, priority(p, hungry, s)));
        }
        // even a camp needs someone to build and someone to keep watch
        boolean projects = s.activeProjects() > 0;
        if (projects && !s.hasUsable(BuildingType.BUILDERS_YARD)) {
            int n = adults.size() >= 10 ? 3 : adults.size() >= 4 ? 2 : 1;
            for (int i = 0; i < n; i++) slots.add(new Slot(null, Profession.BUILDER, priority(Profession.BUILDER, hungry, s)));
        }
        if (!s.hasUsable(BuildingType.WATCHTOWER) && adults.size() >= 6) {
            int n = Math.max(1, adults.size() / 10);
            for (int i = 0; i < n; i++) slots.add(new Slot(null, Profession.GUARD, priority(Profession.GUARD, hungry, s)));
        }
        if (!s.hasUsable(BuildingType.FARM) && !s.hasUsable(BuildingType.FISHERY) && !s.hasUsable(BuildingType.HUNTING_LODGE)) {
            // foragers: without fields, people hunt and gather
            for (int i = 0; i < Math.max(1, adults.size() / 3); i++) slots.add(new Slot(null, Profession.HUNTER, 9));
        }
        slots.sort(Comparator.comparingInt(Slot::priority).reversed());

        // 2. validate current assignments; free invalid ones
        Map<Profession, Integer> wanted = new EnumMap<>(Profession.class);
        for (Slot sl : slots) wanted.merge(sl.profession, 1, Integer::sum);
        Map<Profession, Integer> have = new EnumMap<>(Profession.class);
        for (Person p : adults) {
            Building w = s.building(p.workplace);
            boolean valid = p.profession != Profession.LABOURER && wanted.getOrDefault(p.profession, 0) > have.getOrDefault(p.profession, 0)
                    && (w == null ? p.workplace == null : w.usable() && professionsFor(w.type).contains(p.profession));
            if (valid) have.merge(p.profession, 1, Integer::sum);
            else {
                if (p.profession != Profession.LABOURER) {
                    p.remember(day, "job_lost", null, "lost my place as " + p.profession.displayName.toLowerCase(), -6);
                }
                setJob(s, p, Profession.LABOURER, null);
            }
        }
        // 3. fill vacancies in priority order
        for (Slot sl : slots) {
            int need = wanted.getOrDefault(sl.profession, 0) - have.getOrDefault(sl.profession, 0);
            if (need <= 0) continue;
            if (sl.building != null && countIn(sl.building, sl.profession, adults) >= countSlots(sl.building, sl.profession)) continue;
            Person best = null;
            double bestScore = -1;
            for (Person p : adults) {
                boolean idle = p.profession == Profession.LABOURER;
                // in a famine, low-priority workers may be pulled into the fields
                boolean poachable = hungry && sl.priority >= 9 && priority(p.profession, hungry, s) < 5;
                // an urgent trade may take one of two people doing a less urgent one (never someone's only worker)
                if (!poachable && sl.priority >= 8 && p.profession != sl.profession && priority(p.profession, hungry, s) < sl.priority
                        && have.getOrDefault(p.profession, 0) >= 2) poachable = true;
                if (!idle && !poachable) continue;
                double score = p.skill(sl.profession) + (idle ? 30 : 0) + p.diligence * 10;
                if (p.mentor != null) {
                    Person m = sim.person(p.mentor);
                    if (m != null && m.profession == sl.profession) score += 15;
                }
                if (score > bestScore) {
                    best = p;
                    bestScore = score;
                }
            }
            if (best == null) continue;
            if (best.profession != Profession.LABOURER) have.merge(best.profession, -1, Integer::sum);
            setJob(s, best, sl.profession, sl.building);
            have.merge(sl.profession, 1, Integer::sum);
            best.remember(day, "new_job", null, "took up work as " + sl.profession.displayName.toLowerCase(), 4);
        }
        // 4. everyone without a trade labours (hauls, fetches, helps the builders)
        for (Person p : adults) {
            if (p.profession == Profession.LABOURER) p.activity = "labouring";
        }
    }

    static boolean waitsFor(Settlement s, String... materials) {
        if (s == null) return false;
        for (Building b : s.buildings) {
            if (!b.underConstruction()) continue;
            for (String m : materials) if (b.problem.equals("Waiting for " + m + ".")) return true;
        }
        return false;
    }

    static int countIn(Building b, Profession p, List<Person> people) {
        int n = 0;
        for (Person x : people) if (b.id.equals(x.workplace) && x.profession == p) n++;
        return n;
    }

    static int countSlots(Building b, Profession p) {
        int n = 0;
        for (Profession x : professionsFor(b.type)) if (x == p) n++;
        return n;
    }

    static void setJob(Settlement s, Person p, Profession prof, Building b) {
        Building old = s.building(p.workplace);
        if (old != null) old.workers.remove(p.id);
        p.profession = prof;
        p.workplace = b == null ? null : b.id;
        if (b != null && !b.workers.contains(p.id)) b.workers.add(p.id);
        p.blocked = "";
    }

    static int priority(Profession p, boolean hungry, Settlement s) {
        return switch (p) {
            case FARMER, FISHER, HUNTER -> hungry ? 10 : 7;
            case SHEPHERD -> 5;
            case BUILDER -> 8;
            // a trade whose goods the builders are waiting for is wanted urgently
            case LUMBERJACK -> waitsFor(s, "logs", "fuel") ? 8 : 6;
            case QUARRIER -> waitsFor(s, "stone", "glass") ? 8 : 6;
            case MILLER, BAKER -> hungry ? 9 : 6;
            case GUARD -> s.raidWarning > 0 ? 9 : 5;
            case SOLDIER -> s.raidWarning > 0 ? 8 : 4;
            case HEALER, PRIEST -> 5;
            case BLACKSMITH, CARPENTER, MINER -> 5;
            case STEWARD, MERCHANT, INNKEEPER, BREWER -> 4;
            case WEAVER, TEACHER, SCHOLAR, GRAVEDIGGER -> 3;
            default -> 1;
        };
    }
}

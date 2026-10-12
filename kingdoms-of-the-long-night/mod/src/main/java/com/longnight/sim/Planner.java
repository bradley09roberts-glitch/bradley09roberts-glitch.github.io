package com.longnight.sim;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a settlement decides to build, and where. Choices follow real needs: shelter, food,
 * storage, materials, faith, safety, then the institutions of a growing town. The ruler's
 * agenda shifts priorities (a fearful lord builds towers before taverns).
 */
public final class Planner {
    private Planner() {}

    public static final int CELL = 13;
    /** blueprint steps one average builder completes in a full day of abstract work */
    public static final int STEPS_PER_BUILDER_DAY = 260;

    public static void daily(WorldSim sim, long day) {
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT || s.residents.isEmpty()) continue;
            List<Person> builders = new ArrayList<>();
            int adults = 0;
            for (Person p : sim.residents(s)) {
                if (!sim.isAdult(p, day)) continue;
                adults++;
                if (p.profession == Profession.BUILDER) builders.add(p);
            }
            double share = 1.0 - sim.embodiedFraction(s);
            List<Building> projects = new ArrayList<>();
            for (Building b : s.buildings) if (b.underConstruction()) projects.add(b);
            // labourers help: each idle adult adds a little building capacity
            int labourers = 0;
            for (Person p : sim.residents(s)) if (sim.isAdult(p, day) && p.profession == Profession.LABOURER) labourers++;
            int i = 0;
            for (Building b : projects) {
                double crew = 0;
                if (i < builders.size()) {
                    Person bd = builders.get(i);
                    crew = 0.55 + bd.skill(Profession.BUILDER) / 100.0 * 0.9;
                    bd.activity = "building the " + b.type.displayName.toLowerCase();
                } else if (i == 0 && builders.isEmpty() && adults > 0) {
                    crew = 0.5; // everyone pitches in when there is no builder yet
                }
                if (i == 0) crew += Math.min(3, labourers) * 0.15;
                i++;
                if (crew <= 0) continue;
                int steps = (int) Math.round(STEPS_PER_BUILDER_DAY * crew * share);
                if (steps > 0) Construction.advance(sim, s, b, steps, day);
                if (!b.problem.isEmpty()) {
                    for (Person bd : builders) if (bd.activity.contains(b.type.displayName.toLowerCase())) bd.blocked = b.problem;
                }
            }
            // a project whose plot cannot be resolved is abandoned and its materials returned to the stores
            for (Building b : new ArrayList<>(s.buildings)) {
                if (b.underConstruction() && b.placed == 0 && b.problem.contains("No other plot")) cancel(sim, s, b, day);
            }
            int maxProjects = Math.max(1, builders.size());
            if (s.activeProjects() < maxProjects) {
                BuildingType next = next(sim, s, day);
                if (next != null) plan(sim, s, next, day);
            }
            updateTier(sim, s, day);
        }
    }

    public static BuildingType next(WorldSim sim, Settlement s, long day) {
        List<Person> people = sim.residents(s);
        int pop = people.size();
        int adults = 0, children = 0;
        for (Person p : people) {
            if (sim.isAdult(p, day)) adults++;
            else children++;
        }
        Map<BuildingType, Integer> n = new HashMap<>();
        for (Building b : s.buildings) if (b.state != Building.State.RUINED) n.merge(b.type, 1, Integer::sum);
        Faction f = sim.faction(s.faction);
        Faction.Agenda agenda = f == null ? Faction.Agenda.CONSOLIDATE : f.agenda;
        double foodDays = s.stock.foodUnits() / Math.max(1, pop);
        int farms = n.getOrDefault(BuildingType.FARM, 0) + n.getOrDefault(BuildingType.FISHERY, 0);
        boolean threatened = s.raidWarning > 0 || s.raidsSurvived > 0 || agenda == Faction.Agenda.FORTIFY;

        if (!has(n, BuildingType.WELL)) return BuildingType.WELL;
        if (s.housingCapacity() < pop + 1) return pop > 20 && sim.rng.nextBoolean() ? BuildingType.LARGE_HOUSE : BuildingType.HOUSE;
        if (farms == 0 || (foodDays < 6 && farms < pop / 6 + 1)) {
            return s.culture.equals("tidefolk") && !has(n, BuildingType.FISHERY) ? BuildingType.FISHERY : BuildingType.FARM;
        }
        if (pop >= 4 && !has(n, BuildingType.LUMBER_CAMP)) return BuildingType.LUMBER_CAMP;
        if (pop >= 5 && !has(n, BuildingType.STOREHOUSE)) return BuildingType.STOREHOUSE;
        if (pop >= 6 && !has(n, BuildingType.QUARRY)) return BuildingType.QUARRY;
        if (threatened && pop >= 6 && !has(n, BuildingType.WATCHTOWER)) return BuildingType.WATCHTOWER;
        if (pop >= 6 && !has(n, BuildingType.SHRINE) && !has(n, BuildingType.TEMPLE)) return BuildingType.SHRINE;
        if (pop >= 8 && !has(n, BuildingType.BUILDERS_YARD)) return BuildingType.BUILDERS_YARD;
        if (pop >= 8 && !has(n, BuildingType.CARPENTRY)) return BuildingType.CARPENTRY;
        if (pop >= 8 && !has(n, BuildingType.GRAVEYARD)) return BuildingType.GRAVEYARD;
        if (s.stock.total() > s.storageCapacity() * 0.85 && n.getOrDefault(BuildingType.STOREHOUSE, 0) < 4) return BuildingType.STOREHOUSE;
        if (pop >= 10 && !has(n, BuildingType.MILL)) return BuildingType.MILL;
        if (has(n, BuildingType.MILL) && !has(n, BuildingType.BAKERY)) return BuildingType.BAKERY;
        if (pop >= 10 && !has(n, BuildingType.PASTURE)) return BuildingType.PASTURE;
        if (agenda == Faction.Agenda.TRADE && pop >= 10 && !has(n, BuildingType.MARKET)) return BuildingType.MARKET;
        if (pop >= 12 && !has(n, BuildingType.TAVERN)) return BuildingType.TAVERN;
        if (pop >= 12 && !has(n, BuildingType.HUNTING_LODGE)) return BuildingType.HUNTING_LODGE;
        if (pop >= 12 && !has(n, BuildingType.MINE)) return BuildingType.MINE;
        if (pop >= 12 && !has(n, BuildingType.SMITHY)) return BuildingType.SMITHY;
        if (threatened && pop >= 14 && !has(n, BuildingType.BARRACKS)) return BuildingType.BARRACKS;
        if (pop >= 16 && !has(n, BuildingType.MARKET)) return BuildingType.MARKET;
        if (pop >= 16 && !has(n, BuildingType.GRANARY)) return BuildingType.GRANARY;
        if (pop >= 18 && !has(n, BuildingType.HEALER_HOUSE)) return BuildingType.HEALER_HOUSE;
        if (has(n, BuildingType.PASTURE) && pop >= 18 && !has(n, BuildingType.WEAVERY)) return BuildingType.WEAVERY;
        if (agenda == Faction.Agenda.PIETY && pop >= 18 && !has(n, BuildingType.TEMPLE)) return BuildingType.TEMPLE;
        if (pop >= 22 && !has(n, BuildingType.TOWN_HALL)) return BuildingType.TOWN_HALL;
        if (children >= 4 && pop >= 22 && !has(n, BuildingType.SCHOOL)) return BuildingType.SCHOOL;
        if (pop >= 28 && !has(n, BuildingType.TEMPLE)) return BuildingType.TEMPLE;
        if (pop >= 28 && !has(n, BuildingType.BARRACKS)) return BuildingType.BARRACKS;
        if (pop >= 34 && !has(n, BuildingType.LIBRARY)) return BuildingType.LIBRARY;
        if (foodDays < 8 && farms < pop / 5 + 1) return BuildingType.FARM;
        if (s.housingCapacity() < pop + 4) return BuildingType.HOUSE;
        return null;
    }

    static boolean has(Map<BuildingType, Integer> n, BuildingType t) {
        return n.getOrDefault(t, 0) > 0;
    }

    /** Creates a planned building on a free plot. Returns null if no plot is available. */
    public static Building plan(WorldSim sim, Settlement s, BuildingType type, long day) {
        int[] cell = findCell(sim, s, type);
        if (cell == null) {
            if (s.radius < 120) s.radius += CELL; // grow outward
            return null;
        }
        Building b = new Building(UUID.randomUUID(), type);
        b.seed = sim.rng.nextLong();
        b.rotation = rotationFor(cell[0], cell[1]);
        b.origin = originFor(s, cell[0], cell[1], b);
        b.startedDay = day;
        b.faith = dominantFaith(sim, s);
        b.name = BuildingNames.name(sim, s, b);
        Construction.initPlan(s, b);
        s.buildings.add(b);
        sim.setDirty();
        return b;
    }

    public static String dominantFaith(WorldSim sim, Settlement s) {
        Map<String, Integer> count = new HashMap<>();
        for (Person p : sim.residents(s)) count.merge(p.faith, 1, Integer::sum);
        return count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("hearth");
    }

    public static long cellKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public static int[] cellOf(Settlement s, BlockPos p) {
        int cx = Math.floorDiv(p.getX() - s.center.getX() + CELL / 2, CELL);
        int cz = Math.floorDiv(p.getZ() - s.center.getZ() + CELL / 2, CELL);
        return new int[]{cx, cz};
    }

    static boolean outerType(BuildingType t) {
        return switch (t) {
            case FARM, PASTURE, LUMBER_CAMP, QUARRY, MINE, GRAVEYARD, FISHERY, HUNTING_LODGE -> true;
            default -> false;
        };
    }

    public static int[] findCell(WorldSim sim, Settlement s, BuildingType type) {
        java.util.Set<Long> used = new java.util.HashSet<>();
        for (Building b : s.buildings) {
            int[] c = cellOf(s, b.centre());
            used.add(cellKey(c[0], c[1]));
        }
        int maxRing = Math.max(2, s.radius / CELL) + 1;
        List<int[]> candidates = new ArrayList<>();
        for (int ring = 0; ring <= maxRing; ring++) {
            for (int cx = -ring; cx <= ring; cx++) {
                for (int cz = -ring; cz <= ring; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != ring) continue;
                    candidates.add(new int[]{cx, cz, ring});
                }
            }
        }
        boolean outer = outerType(type);
        candidates.sort(Comparator.comparingInt(c -> {
            int ring = c[2];
            int pref = outer ? Math.abs(ring - 3) * 3 + (ring < 2 ? 50 : 0) : ring * 3;
            if (type == BuildingType.WELL) pref = ring * 10;
            return pref * 1000 + Math.floorMod((c[0] * 73 + c[1] * 31) ^ (int) s.id.getLeastSignificantBits(), 997);
        }));
        for (int[] c : candidates) {
            if (type != BuildingType.WELL && c[0] == 0 && c[1] == 0) continue;
            if (type == BuildingType.WELL && c[2] > 1) continue; // the well stays at the heart of things
            long key = cellKey(c[0], c[1]);
            if (used.contains(key) || s.badCells.contains(key)) continue;
            BlockPos mid = s.center.offset(c[0] * CELL, 0, c[1] * CELL);
            if (sim.claimed(mid, CELL)) continue;
            Building probe = new Building(null, type);
            probe.rotation = rotationFor(c[0], c[1]);
            BlockPos plotOrigin = originFor(s, c[0], c[1], probe);
            if (!sim.hooks.plotLooksBuildable(plotOrigin, probe.width(), probe.depth())) {
                s.badCells.add(key);
                continue;
            }
            boolean clash = false;
            for (Settlement o : sim.settlements.values()) {
                if (o == s || o.center == null) continue;
                double dx = o.center.getX() - mid.getX(), dz = o.center.getZ() - mid.getZ();
                if (dx * dx + dz * dz < (double) (o.radius + CELL) * (o.radius + CELL)) clash = true;
            }
            if (clash) continue;
            return new int[]{c[0], c[1]};
        }
        return null;
    }

    static int rotationFor(int cx, int cz) {
        if (cx == 0 && cz == 0) return 0;
        if (Math.abs(cz) >= Math.abs(cx)) return cz > 0 ? 0 : 2;
        return cx > 0 ? 3 : 1;
    }

    static BlockPos originFor(Settlement s, int cx, int cz, Building b) {
        int baseX = s.center.getX() + cx * CELL - CELL / 2;
        int baseZ = s.center.getZ() + cz * CELL - CELL / 2;
        int w = b.width(), d = b.depth();
        int ox = baseX + 1 + Math.max(0, (CELL - 1 - w) / 2);
        int oz = baseZ + 1 + Math.max(0, (CELL - 1 - d) / 2);
        return new BlockPos(ox, s.center.getY(), oz);
    }

    /** Abandon an unstarted project, refunding exactly what was paid for it. */
    public static void cancel(WorldSim sim, Settlement s, Building b, long day) {
        if (b.placed > 0) return;
        com.longnight.world.Blueprint bp = Construction.blueprint(s, b);
        for (int i = 0; i < b.progress && i < bp.steps.size(); i++) bp.stepCost(i).forEach(s.stock::add);
        s.buildings.remove(b);
        s.chronicle(day, "Plans for a " + b.type.displayName.toLowerCase() + " were abandoned: " + b.problem);
        sim.setDirty();
    }

    /** Move a not-yet-started building to another plot (site turned out unusable). */
    public static boolean relocate(WorldSim sim, Settlement s, Building b, String reason) {
        int[] old = cellOf(s, b.centre());
        s.badCells.add(cellKey(old[0], old[1]));
        int[] cell = findCell(sim, s, b.type);
        for (int grow = 0; cell == null && grow < 3 && s.radius < 150; grow++) {
            s.radius += CELL; // spread outward along the land
            cell = findCell(sim, s, b.type);
        }
        if (cell == null) {
            b.problem = reason + " No other plot is free.";
            if (b.state == Building.State.COMPLETE && b.placed == 0) {
                // a building from before the player's time that the land cannot hold: it never stood here
                s.buildings.remove(b);
                for (UUID id : b.residents) {
                    Person p = sim.person(id);
                    if (p != null) p.home = null;
                }
                for (UUID id : b.workers) {
                    Person p = sim.person(id);
                    if (p != null) p.workplace = null;
                }
            }
            return false;
        }
        b.rotation = rotationFor(cell[0], cell[1]);
        b.origin = originFor(s, cell[0], cell[1], b);
        b.placed = 0;
        b.problem = "";
        sim.setDirty();
        return true;
    }

    static void updateTier(WorldSim sim, Settlement s, long day) {
        int pop = s.residents.size();
        Settlement.Tier t = Settlement.Tier.CAMP;
        for (Settlement.Tier x : Settlement.Tier.values()) if (pop >= x.minPopulation) t = x;
        // institutions are required as well as people
        if (t.ordinal() >= Settlement.Tier.TOWN.ordinal() && !s.hasUsable(BuildingType.MARKET) && !s.hasUsable(BuildingType.TOWN_HALL)) {
            t = Settlement.Tier.VILLAGE;
        }
        if (t == Settlement.Tier.CITY && !(s.hasUsable(BuildingType.TOWN_HALL) && s.hasUsable(BuildingType.TEMPLE))) t = Settlement.Tier.TOWN;
        int houses = s.byType(BuildingType.HOUSE).size() + s.byType(BuildingType.LARGE_HOUSE).size();
        if (t.ordinal() >= Settlement.Tier.HAMLET.ordinal() && houses < 2) t = Settlement.Tier.CAMP;
        if (t.ordinal() > s.tier.ordinal()) {
            s.tier = t;
            s.chronicle(day, s.name + " has grown into a " + t.displayName.toLowerCase() + ".");
            sim.addNews(day, "growth", s.name + " has grown into a " + t.displayName.toLowerCase() + ".", s, 2);
        } else if (t.ordinal() < s.tier.ordinal() - 1) {
            s.tier = Settlement.Tier.values()[s.tier.ordinal() - 1];
            s.chronicle(day, s.name + " has dwindled to a " + s.tier.displayName.toLowerCase() + ".");
        }
    }
}

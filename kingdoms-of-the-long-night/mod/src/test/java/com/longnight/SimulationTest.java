package com.longnight;

import com.longnight.command.Invariants;
import com.longnight.content.Content;
import com.longnight.sim.Building;
import com.longnight.sim.BuildingType;
import com.longnight.sim.Commodity;
import com.longnight.sim.Construction;
import com.longnight.sim.Person;
import com.longnight.sim.Planner;
import com.longnight.sim.Settlement;
import com.longnight.sim.Stockpile;
import com.longnight.sim.WorldBuilder;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests of the simulation (no world, no players). */
public class SimulationTest {
    @BeforeAll
    static void content() {
        Content.loadBundled();
    }

    static WorldSim world(long seed) {
        WorldSim sim = new WorldSim();
        Random r = new Random(seed);
        List<WorldBuilder.Site> sites = new ArrayList<>();
        String[] cultures = Content.CULTURE_IDS;
        for (int i = 0; i < 40; i++) {
            BlockPos p = new BlockPos(r.nextInt(2400) - 1200, 70, r.nextInt(2400) - 1200);
            sites.add(new WorldBuilder.Site(p, cultures[r.nextInt(cultures.length)], r.nextBoolean(), r.nextBoolean(), false, r.nextDouble() * 3));
        }
        WorldBuilder.generate(sim, sites, 7, 2, 2, seed);
        sim.lastDay = 0;
        return sim;
    }

    @Test
    void contentLoads() {
        assertEquals(8, Content.CULTURES.size(), "cultures");
        assertEquals(8, Content.FAITHS.size(), "faiths");
        assertEquals(8, Content.NAMES.size(), "name sets");
        assertFalse(Content.RUMOURS.isEmpty(), "rumours");
        assertFalse(Content.EPITAPHS.isEmpty(), "epitaphs");
    }

    @Test
    void generationIsConsistent() {
        WorldSim sim = world(1);
        assertTrue(sim.generated);
        long settlements = sim.settlements.values().stream().filter(s -> s.kind == Settlement.Kind.SETTLEMENT).count();
        assertTrue(settlements >= 7, "settlements: " + settlements);
        assertTrue(sim.people.size() > 80, "people: " + sim.people.size());
        assertTrue(sim.factions.values().stream().anyMatch(f -> f.ruler != null), "some realm has a ruler");
        assertEquals(List.of(), Invariants.checkData(sim));
    }

    @Test
    void stockpileIsAtomic() {
        Stockpile s = new Stockpile();
        s.add(Commodity.STONE, 5);
        Map<Commodity, Double> need = new EnumMap<>(Commodity.class);
        need.put(Commodity.STONE, 3.0);
        need.put(Commodity.PLANKS, 1.0);
        assertFalse(s.takeAll(need, 1), "missing planks: nothing taken");
        assertEquals(5.0, s.get(Commodity.STONE), 1e-9);
        assertFalse(s.take(Commodity.STONE, 6));
        assertEquals(5.0, s.get(Commodity.STONE), 1e-9);
    }

    /** Materials are charged exactly once per blueprint step, never twice, never skipped. */
    @Test
    void constructionChargesExactlyOnce() {
        WorldSim sim = world(2);
        Settlement s = sim.settlements.values().stream().filter(x -> x.kind == Settlement.Kind.SETTLEMENT).findFirst().orElseThrow();
        s.stock = new Stockpile();
        Building b = Planner.plan(sim, s, BuildingType.HOUSE, 0);
        assertNotNull(b);
        Map<Commodity, Double> cost = Construction.blueprint(s, b).totalCost();
        cost.forEach((c, q) -> s.stock.add(c, q));
        double before = s.stock.total();
        int paid = 0;
        for (int i = 0; i < 1000 && b.underConstruction(); i++) paid += Construction.advance(sim, s, b, 7, 0);
        assertEquals(b.total, paid, "every step paid once");
        assertEquals(Building.State.COMPLETE, b.state);
        assertEquals(0.0, s.stock.total(), 1e-6, "exactly the bill was consumed (before=" + before + ")");
        assertEquals(0, Construction.advance(sim, s, b, 50, 1), "a finished building charges nothing more");
        assertEquals(0.0, s.stock.total(), 1e-6);
    }

    @Test
    void constructionStopsAndExplainsWhenShort() {
        WorldSim sim = world(3);
        Settlement s = sim.settlements.values().stream().filter(x -> x.kind == Settlement.Kind.SETTLEMENT).findFirst().orElseThrow();
        s.stock = new Stockpile();
        Building b = Planner.plan(sim, s, BuildingType.SMITHY, 0);
        int paid = Construction.advance(sim, s, b, 10_000, 0);
        assertTrue(paid < b.total);
        assertTrue(b.problem.startsWith("Waiting for"), b.problem);
        assertTrue(b.underConstruction());
    }

    /** The world runs for 60 days with births, deaths, marriages, trade, raids and politics, staying consistent. */
    @Test
    void sixtyDaysStayConsistent() {
        WorldSim sim = world(4);
        int startPeople = (int) sim.people.values().stream().filter(p -> p.alive).count();
        for (long d = 1; d <= 60; d++) {
            sim.daily(d);
            sim.lastDay = d;
            List<String> err = Invariants.checkData(sim);
            assertEquals(List.of(), err, "day " + d);
        }
        long alive = sim.people.values().stream().filter(p -> p.alive).count();
        long dead = sim.people.values().stream().filter(p -> !p.alive).count();
        long born = sim.people.values().stream().filter(p -> p.birthDay > 0).count();
        System.out.println("60 days: alive=" + alive + " dead=" + dead + " born=" + born + " news=" + sim.news.size()
                + " wars=" + sim.wars.size() + " caravans=" + sim.caravans.size());
        assertTrue(alive > startPeople / 2, "population did not collapse: " + alive + " of " + startPeople);
        assertTrue(born > 0, "children were born");
        assertTrue(sim.news.size() > 10, "things happened");
        for (Person p : sim.people.values()) {
            if (!p.alive) {
                assertTrue(p.deathDay >= 0 || p.deathDay == Long.MIN_VALUE);
                for (Settlement s : sim.settlements.values()) assertFalse(s.residents.contains(p.id), "dead stay dead");
            }
        }
        // some buildings were completed by the settlements themselves
        long built = sim.settlements.values().stream().flatMap(s -> s.buildings.stream()).filter(b -> b.completedDay > 0).count();
        assertTrue(built > 0, "construction happened");
    }

    @Test
    void killIsFinalAndIdempotent() {
        WorldSim sim = world(5);
        Settlement s = sim.settlements.values().stream().filter(x -> x.kind == Settlement.Kind.SETTLEMENT).findFirst().orElseThrow();
        Person p = sim.residents(s).get(0);
        int coinsBefore = sim.people.values().stream().mapToInt(x -> x.coins).sum() + s.treasury;
        sim.kill(p, 3, "test");
        sim.kill(p, 4, "again");
        assertFalse(p.alive);
        assertEquals(3, p.deathDay);
        assertEquals("test", p.deathCause);
        assertFalse(s.residents.contains(p.id));
        int coinsAfter = sim.people.values().stream().mapToInt(x -> x.coins).sum() + s.treasury;
        assertEquals(coinsBefore, coinsAfter, "inheritance conserves coin");
        for (long d = 1; d <= 10; d++) sim.daily(d);
        assertFalse(p.alive, "still dead");
    }

    @Test
    void bodiesWorkReplacesTheDailyWorkInsteadOfAddingToIt() {
        WorldSim watched = world(11), unwatched = world(11);
        // in one world every producer's body already worked the whole day in front of a player
        for (Person p : watched.people.values()) if (p.alive && p.profession.producesGoods()) watched.workedCyclesToday.put(p.id, 8);
        Map<java.util.UUID, Map<Commodity, Double>> w0 = stocks(watched), u0 = stocks(unwatched);
        com.longnight.sim.Economy.daily(watched, 1);
        com.longnight.sim.Economy.daily(unwatched, 1);
        for (Settlement s : watched.settlements.values()) {
            for (Commodity c : Commodity.values()) {
                if (c == Commodity.LOGS || c == Commodity.STONE || c == Commodity.FUEL) continue; // hand-gathering is never embodied
                assertTrue(s.stock.get(c) <= w0.get(s.id).get(c) + 1e-9, s.name + " produced " + c + " twice");
            }
        }
        boolean produced = false;
        for (Settlement s : unwatched.settlements.values()) {
            for (Commodity c : Commodity.values()) {
                if (c != Commodity.LOGS && c != Commodity.STONE && c != Commodity.FUEL && s.stock.get(c) > u0.get(s.id).get(c) + 1e-9) produced = true;
            }
        }
        assertTrue(produced, "the unwatched world worked its day abstractly");
    }

    static Map<java.util.UUID, Map<Commodity, Double>> stocks(WorldSim sim) {
        Map<java.util.UUID, Map<Commodity, Double>> out = new java.util.HashMap<>();
        for (Settlement s : sim.settlements.values()) {
            Map<Commodity, Double> m = new EnumMap<>(Commodity.class);
            for (Commodity c : Commodity.values()) m.put(c, s.stock.get(c));
            out.put(s.id, m);
        }
        return out;
    }

    @Test
    void saveDataRoundTrips() {
        WorldSim sim = world(6);
        for (long d = 1; d <= 15; d++) sim.daily(d);
        sim.lastDay = 15;
        // a murderer's warrant survives a restart
        java.util.UUID town = sim.settlements.keySet().iterator().next(), player = java.util.UUID.randomUUID();
        sim.wanted.computeIfAbsent(town, k -> new java.util.HashMap<>()).put(player, 72000L);
        WorldSim copy = sim.roundTrip();
        assertEquals(72000L, copy.wanted.get(town).get(player));
        assertEquals(sim.people.size(), copy.people.size());
        assertEquals(sim.settlements.size(), copy.settlements.size());
        assertEquals(sim.factions.size(), copy.factions.size());
        assertEquals(sim.news.size(), copy.news.size());
        for (Settlement s : sim.settlements.values()) {
            Settlement c = copy.settlements.get(s.id);
            assertEquals(s.name, c.name);
            assertEquals(s.residents, c.residents);
            assertEquals(s.buildings.size(), c.buildings.size());
            for (Commodity cm : Commodity.values()) assertEquals(s.stock.get(cm), c.stock.get(cm), 1e-9, s.name + " " + cm);
        }
        for (Person p : sim.people.values()) {
            Person q = copy.people.get(p.id);
            assertEquals(p.fullName(), q.fullName());
            assertEquals(p.alive, q.alive);
            assertEquals(p.spouse, q.spouse);
            assertEquals(p.children, q.children);
            assertEquals(p.memories.size(), q.memories.size());
            assertEquals(p.relations.size(), q.relations.size());
        }
        assertEquals(List.of(), Invariants.checkData(copy));
        // and it keeps running identically-consistent after reload
        for (long d = 16; d <= 25; d++) copy.daily(d);
        assertEquals(List.of(), Invariants.checkData(copy));
    }
}

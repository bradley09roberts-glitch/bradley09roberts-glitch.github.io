package com.longnight;

import com.longnight.command.Invariants;
import com.longnight.content.Content;
import com.longnight.sim.Building;
import com.longnight.sim.Person;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 200 simulated days (25 years at the default pacing): the world must stay healthy and consistent. */
public class LongRunTest {
    @BeforeAll
    static void content() {
        Content.loadBundled();
    }

    @Test
    void twoHundredDays() throws Exception {
        WorldSim sim = SimulationTest.world(77);
        long people0 = sim.people.values().stream().filter(p -> p.alive).count();
        StringBuilder log = new StringBuilder();
        for (long d = 1; d <= 200; d++) {
            sim.daily(d);
            sim.lastDay = d;
            if (d % 25 == 0) {
                List<String> err = Invariants.checkData(sim);
                assertEquals(List.of(), err, "day " + d);
                long alive = sim.people.values().stream().filter(p -> p.alive).count();
                long built = sim.settlements.values().stream().flatMap(s -> s.buildings.stream()).filter(b -> b.completedDay > 0).count();
                long settlements = sim.settlements.values().stream().filter(s -> s.kind == Settlement.Kind.SETTLEMENT).count();
                double food = sim.settlements.values().stream().filter(s -> s.kind == Settlement.Kind.SETTLEMENT)
                        .mapToDouble(s -> s.stock.foodUnits() / Math.max(1, s.residents.size())).average().orElse(0);
                log.append(String.format("day %3d: alive=%d settlements=%d built=%d foodDays=%.1f wars=%d caravans=%d stage=%d news=%d%n",
                        d, alive, settlements, built, food, sim.wars.size(), sim.caravans.size(), sim.nightStage, sim.news.size()));
            }
        }
        System.out.print(log);
        long alive = sim.people.values().stream().filter(p -> p.alive).count();
        long dead = sim.people.values().stream().filter(p -> !p.alive).count();
        // no collapse, no runaway explosion
        assertTrue(alive > people0 * 0.5, "population collapsed: " + alive + " of " + people0);
        assertTrue(alive < people0 * 6, "population exploded: " + alive + " from " + people0);
        assertTrue(dead > 0, "people die");
        // generations turn over: some born in the simulation are now adults
        long grownUp = sim.people.values().stream().filter(p -> p.alive && p.birthDay > 0 && sim.isAdult(p, 200)).count();
        assertTrue(grownUp > 0, "children grew up");
        // settlements keep building
        long builtLate = sim.settlements.values().stream().flatMap(s -> s.buildings.stream()).filter(b -> b.completedDay > 100).count();
        assertTrue(builtLate > 0, "construction continues in the second century of days");
        // save size stays reasonable
        CompoundTag t = sim.save(new CompoundTag(), null);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(t, bytes);
        System.out.println("save size after 200 days: " + bytes.size() / 1024 + " KiB compressed, people records " + sim.people.size());
        assertTrue(bytes.size() < 8 * 1024 * 1024, "save under 8 MiB");
        // and it still reloads cleanly
        WorldSim copy = sim.roundTrip();
        assertEquals(List.of(), Invariants.checkData(copy));
        int married = 0;
        for (Person p : sim.people.values()) if (p.alive && p.spouse != null) married++;
        System.out.println("alive=" + alive + " dead=" + dead + " grownUp=" + grownUp + " married=" + married);
        for (var f : sim.factions.values()) {
            Person r = sim.person(f.ruler);
            StringBuilder rel = new StringBuilder();
            f.relations.forEach((k, v) -> rel.append(' ').append(v));
            System.out.println("  realm " + f.name + " gov=" + f.government + " agenda=" + f.agenda + " strength=" + com.longnight.sim.Politics.strength(sim, f)
                    + " ruler=" + (r == null ? "-" : r.given + " amb=" + String.format("%.2f", r.ambition) + " brave=" + String.format("%.2f", r.bravery))
                    + " relations:" + rel + " destroyed=" + f.destroyed);
        }
        for (Settlement s : sim.settlements.values()) {
            if (s.kind != Settlement.Kind.SETTLEMENT) continue;
            int stalled = 0;
            StringBuilder why = new StringBuilder();
            for (Building b : s.buildings) {
                if (b.underConstruction() && !b.problem.isEmpty()) {
                    stalled++;
                    why.append(" [").append(b.type.id()).append(": ").append(b.problem).append("]");
                }
            }
            System.out.println("  " + s.name + " " + s.tier + " pop=" + s.residents.size() + " buildings=" + s.buildings.size()
                    + " stalled=" + stalled + " treasury=" + s.treasury + why);
        }
    }
}

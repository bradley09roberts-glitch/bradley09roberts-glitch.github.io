package com.squidgame.core.redlight;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartOrderTest {
    /** The slots of the real arena (see build/arena/redlight/Markers): 7 rows x 71 columns, numbered centre-out. */
    private static double[][] arenaSlots() {
        List<double[]> pts = new ArrayList<>();
        for (int j = 0; j < 7; j++) {
            double z = 10.5 - 1.4 * j;
            for (int k = -35; k <= 35; k++) {
                pts.add(new double[]{0.5 + 1.4 * k, z, Math.max(Math.abs(k) / 4.0, j), j, Math.abs(k), k});
            }
        }
        pts.sort((a, b) -> {
            for (int i = 2; i < 6; i++) {
                int cmp = Double.compare(a[i], b[i]);
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });
        double[][] out = new double[2][pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            out[0][i] = pts.get(i)[0];
            out[1][i] = pts.get(i)[1];
        }
        return out;
    }

    private static List<String> crowd(int npcs, String... humans) {
        List<String> all = new ArrayList<>(List.of(humans));
        for (int i = 0; i < npcs; i++) {
            all.add("npc" + i);
        }
        return all;
    }

    @Test
    void aHumanWhoWasFirstOnTheRosterEndsUpInsideTheCrowdNotAtTheFront() {
        double[][] s = arenaSlots();
        List<String> order = StartOrder.humansIntoCrowd(crowd(99, "human"), c -> c.startsWith("human"), s[0], s[1]);
        assertEquals(100, order.size());
        int slot = order.indexOf("human");
        assertTrue(slot > 0, "not slot 0, the front centre");
        // the crowd must be on every side of the human: runners ahead (larger z), behind (smaller z), left and right
        boolean ahead = false, behind = false, left = false, right = false;
        int nearAhead = 0;
        for (int i = 0; i < 100; i++) {
            if (i == slot) {
                continue;
            }
            double dx = s[0][i] - s[0][slot], dz = s[1][i] - s[1][slot];
            ahead |= dz > 0.5;
            behind |= dz < -0.5;
            left |= dx < -0.5;
            right |= dx > 0.5;
            if (dz > 0.5 && Math.abs(dx) < 10) {
                nearAhead++;
            }
        }
        assertTrue(ahead && behind && left && right);
        assertTrue(nearAhead >= 10, "plenty of runners visible straight ahead, was " + nearAhead);
    }

    @Test
    void machineContestantsKeepTheirRelativeOrderAndEveryoneGetsASlot() {
        double[][] s = arenaSlots();
        List<String> order = StartOrder.humansIntoCrowd(crowd(60, "humanA", "humanB"), c -> c.startsWith("human"), s[0], s[1]);
        assertEquals(62, order.size());
        assertEquals(62, order.stream().distinct().count());
        List<String> npcs = new ArrayList<>(order);
        npcs.removeIf(c -> c.startsWith("human"));
        for (int i = 0; i < 60; i++) {
            assertEquals("npc" + i, npcs.get(i));
        }
        // two humans stand close together
        int a = order.indexOf("humanA"), b = order.indexOf("humanB");
        double d = Math.hypot(s[0][a] - s[0][b], s[1][a] - s[1][b]);
        assertTrue(d < 4.5, "distance " + d);
    }

    @Test
    void noHumansKeepsTheRosterOrder() {
        double[][] s = arenaSlots();
        List<String> all = crowd(30);
        assertEquals(all, StartOrder.humansIntoCrowd(all, c -> false, s[0], s[1]));
    }

    @Test
    void tinyFieldsAndMissingSlotsAreHandled() {
        double[][] s = arenaSlots();
        assertEquals(List.of("human"), StartOrder.humansIntoCrowd(List.of("human"), c -> true, s[0], s[1]));
        List<String> two = StartOrder.humansIntoCrowd(List.of("human", "npc0"), c -> c.startsWith("human"), s[0], s[1]);
        assertEquals(2, two.size());
        assertTrue(two.contains("human") && two.contains("npc0"));
        assertEquals(List.of(), StartOrder.humansIntoCrowd(List.<String>of(), c -> true, s[0], s[1]));
        // fewer slots than contestants: nobody is lost
        List<String> many = StartOrder.humansIntoCrowd(crowd(10, "human"), c -> c.startsWith("human"), new double[]{0, 1, 2}, new double[]{0, 0, 0});
        assertEquals(11, many.stream().filter(c -> c != null).count());
    }
}

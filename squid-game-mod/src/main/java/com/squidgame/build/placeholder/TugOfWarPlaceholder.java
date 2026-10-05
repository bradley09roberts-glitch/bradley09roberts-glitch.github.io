package com.squidgame.build.placeholder;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Throw-away test fixture for the Tug of War: a small, crude hall with the real geometry the game depends on, so the game works
 * unchanged on the real arena ({@code TugOfWarBuilder}, which always wins because this fixture has version 0).
 *
 * <pre>
 *  x:  waiting room | west plateau -75..-61 | deck A -60..-8 | gap -7..6 | deck B 7..59 | east plateau 60..74
 *  y:  pit floor -30, decks / plateaus / gallery floor 40 (standing level 41), ceiling 75
 *  z:  hall -35..35, decks -2..2 (curbs and rails at +-3), spare gallery 29..35 along the south wall
 * </pre>
 * The decks are three layers thick under the standing level (surface, plate, ribs) like the real ones, because the game removes
 * exactly those layers when a team falls. Markers follow docs/ARENA_MARKERS.md: 48 slots per team, rope anchors at y = 42.2,
 * lobbies on the plateaus, a gallery for the spare contestants.
 */
public final class TugOfWarPlaceholder implements ArenaBuilder {
    private static final int PIT = -30;
    private static final int DECK = 40;
    private static final int CEIL = 75;
    private static final int CAT = 58;
    private static final int HZ = 35;
    private static final int PIER0 = -75, PIER1 = -61;
    private static final String CONCRETE = "minecraft:gray_concrete";
    private static final String BLACK = "minecraft:black_concrete";
    private static final String PLANKS = "minecraft:spruce_planks";
    private static final String RED = "minecraft:red_concrete";
    private static final String BLUE = "minecraft:blue_concrete";
    private static final int SLOTS = 48;

    @Override
    public ArenaId id() {
        return ArenaId.TUG_OF_WAR;
    }

    /** Placeholders are always older than any real builder (version >= 1), so a real builder replaces them. */
    @Override
    public int version() {
        return 0;
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> m = new ArrayList<>(CommonMarkers.REQUIRED);
        m.addAll(List.of("tug.slot_a", "tug.slot_b", "tug.rope_a", "tug.rope_b", "tug.rope_center", "tug.waiting_a", "tug.waiting_b",
                "tug.spare", "tug.pit_floor"));
        return m;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> r = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        r.addAll(List.of("tug.edge_a", "tug.edge_b", "tug.pit"));
        return r;
    }

    @Override
    public void build(BuildContext c) {
        hall(c);
        plateaus(c);
        decks(c);
        gallery(c);
        lights(c);
        waitingRoom(c);
        markers(c);
    }

    // ------------------------------------------------------------------ structure

    /** The shell: a floor at the bottom of the pit, walls all round and a ceiling; the pit is the whole hall below the decks. */
    private static void hall(BuildContext c) {
        c.fill(-78, PIT - 2, -HZ - 3, 77, PIT - 1, HZ + 3, "minecraft:stone_bricks");
        c.fill(-76, PIT, -HZ - 1, 75, PIT, HZ + 1, "minecraft:deepslate_tiles");
        c.shell(-76, PIT, -HZ - 1, 75, CEIL + 1, HZ + 1, CONCRETE);
        c.clear(-75, PIT + 1, -HZ, 74, CEIL, HZ);
    }

    /** The two piers with their plateaus (the team lobbies): solid from the pit floor up to the standing level. */
    private static void plateaus(BuildContext c) {
        for (boolean west : new boolean[]{true, false}) {
            int x0 = west ? PIER0 : -PIER1 - 1;
            int x1 = west ? PIER1 : -PIER0 - 1;
            c.fill(x0, PIT + 1, -HZ, x1, DECK - 1, HZ, CONCRETE);
            c.fill(x0, DECK, -HZ, x1, DECK, HZ, "minecraft:smooth_stone");
            String team = west ? RED : BLUE;
            int zoneA = west ? -73 : 72;
            int zoneB = west ? -65 : 64;
            c.fill(Math.min(zoneA, zoneB), DECK, -14, Math.max(zoneA, zoneB), DECK, -14, team);
            c.fill(Math.min(zoneA, zoneB), DECK, 14, Math.max(zoneA, zoneB), DECK, 14, team);
            // rails along the pit edge, open in front of the deck
            int edge = west ? PIER1 : -PIER1 - 1;
            for (int z = 5; z <= HZ; z++) {
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    c.set(edge, DECK + 1, z * sgn, "minecraft:iron_bars");
                    c.set(edge, DECK + 2, z * sgn, "minecraft:iron_bars");
                }
            }
        }
    }

    /** Two cantilevered walkways: surface, plate and ribs under the standing level, curbs and rails at the sides. */
    private static void decks(BuildContext c) {
        for (boolean west : new boolean[]{true, false}) {
            int tip = west ? -8 : 7;
            int dir = west ? -1 : 1;
            int rear = west ? -60 : 59;
            String team = west ? RED : BLUE;
            for (int d = 0; d < 53; d++) {
                int x = tip + dir * d;
                for (int z = -2; z <= 2; z++) {
                    String top = d <= 1 ? (((d + z) & 1) == 0 ? "minecraft:yellow_concrete" : BLACK) : Math.abs(z) == 1 ? team : PLANKS;
                    c.set(x, DECK, z, top);
                    c.set(x, DECK - 1, z, BLACK);
                }
                c.set(x, DECK - 1, -3, BLACK);
                c.set(x, DECK - 1, 3, BLACK);
                if (d % 3 == 0) {
                    c.fill(x, DECK - 2, -2, x, DECK - 2, 2, BLACK);
                }
                // curbs and rails (open at the tip, where the game puts its own fence)
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    c.fill(x, DECK - 4, 3 * sgn, x, DECK, 3 * sgn, BLACK);
                    if (d >= 2) {
                        c.fill(x, DECK + 1, 3 * sgn, x, DECK + 2, 3 * sgn, "minecraft:iron_bars");
                    }
                }
                if (d >= 3 && d % 4 == 3) {
                    c.set(x, DECK, -2, "squidgame:panel_light_warm");
                    c.set(x, DECK, 2, "squidgame:panel_light_warm");
                }
            }
            // two supporting pillars per deck down to the pit floor
            for (int pillar : new int[]{rear - dir * -8, tip + dir * 26}) {
                c.fill(pillar, PIT + 1, -4, pillar + 1, DECK - 5, -3, "minecraft:iron_block");
                c.fill(pillar, PIT + 1, 3, pillar + 1, DECK - 5, 4, "minecraft:iron_block");
            }
            // the deck meets its plateau
            c.fill(rear + dir * -1, DECK - 1, -2, rear + dir * -1, DECK - 1, 2, BLACK);
        }
    }

    /** The gallery for the contestants that sit out a heat: a solid ledge along the south wall with a rail towards the pit. */
    private static void gallery(BuildContext c) {
        // a small catwalk across the gap, where the eliminated watch from
        c.fill(-6, CAT, 1, 6, CAT, 6, "minecraft:smooth_stone");
        c.fill(-6, CAT + 1, 6, 6, CAT + 1, 6, "minecraft:iron_bars");
        c.fill(-6, PIT + 1, 5, -6, CAT - 1, 6, "minecraft:iron_block");
        c.fill(6, PIT + 1, 5, 6, CAT - 1, 6, "minecraft:iron_block");
        c.fill(-60, PIT + 1, 29, 59, DECK - 1, HZ, CONCRETE);
        c.fill(-60, DECK, 29, 59, DECK, HZ, "minecraft:smooth_stone");
        for (int x = -60; x <= 59; x++) {
            c.set(x, DECK + 1, 29, "minecraft:iron_bars");
            c.set(x, DECK + 2, 29, "minecraft:iron_bars");
        }
    }

    private static void lights(BuildContext c) {
        for (int x = -72; x <= 72; x += 8) {
            for (int z = -28; z <= 28; z += 8) {
                c.set(x, CEIL, z, "minecraft:sea_lantern");
            }
        }
        for (int x = -70; x <= 70; x += 10) {
            for (int z : new int[]{-8, 8, -26, 26}) {
                c.set(x, DECK + 6, z, "minecraft:sea_lantern");
            }
        }
        for (int x = -52; x <= 52; x += 8) {
            for (int z = -12; z <= 12; z += 8) {
                c.set(x, PIT, z, "minecraft:sea_lantern");
            }
        }
        for (int x : new int[]{-74, -69, -64, 63, 68, 73}) {
            for (int z = -30; z <= 30; z += 10) {
                c.set(x, DECK, z, "squidgame:panel_light_warm");
            }
        }
    }

    /** The shared waiting room behind the west plateau: its gate wall faces east (three quarter turns), like in the real arena. */
    private static void waitingRoom(BuildContext c) {
        c.fill(-100, PIT, -22, -77, DECK - 1, 22, CONCRETE);
        c.at(-76, DECK, 0, 3, () -> WaitingRoomPrefab.build(c, WaitingRoomPrefab.Spec.of("TUG OF WAR")));
        for (int x : new int[]{-96, -91, -86, -81, -77}) {
            for (int z : new int[]{-18, -13, -8, -3, 2, 7, 12, 17, 20}) {
                c.set(x, DECK + 2, z, "minecraft:light[level=15]");
            }
        }
    }

    // ------------------------------------------------------------------ markers

    private static void markers(BuildContext c) {
        for (int k = 0; k < SLOTS; k++) {
            c.marker("tug.slot_a", -8.5 - k, DECK + 1, 0.5, -90f, "slot=" + k);
            c.marker("tug.slot_b", 8.5 + k, DECK + 1, 0.5, 90f, "slot=" + k);
        }
        c.marker("tug.rope_a", -7.0, 42.2, 0.5, -90f);
        c.marker("tug.rope_b", 7.0, 42.2, 0.5, 90f);
        c.marker("tug.rope_center", 0.0, 42.2, 0.5, 0f);
        c.marker("tug.pit_floor", 0.5, PIT + 1, 0.5, 0f);
        lobbies(c);
        List<int[]> cells = new ArrayList<>();
        for (int x = -58; x <= 57; x += 2) {
            for (int z : new int[]{32, 34}) {
                cells.add(new int[]{x, z});
            }
        }
        cells.sort(Comparator.comparingDouble(a -> Math.abs(a[0] + 0.5) + (a[1] == 34 ? 0.5 : 0)));
        for (int i = 0; i < cells.size(); i++) {
            c.marker("tug.spare", cells.get(i)[0] + 0.5, DECK + 1, cells.get(i)[1] + 0.5, 180f, "slot=" + i);
        }
        c.region("tug.edge_a", -9, DECK, -3, -8, DECK + 5, 3);
        c.region("tug.edge_b", 7, DECK, -3, 8, DECK + 5, 3);
        c.region("tug.pit", -60, PIT, -20, 59, DECK - 1, 20);
        c.region("arena.bounds", PIER0, PIT, -HZ, -PIER0 - 1, CEIL - 1, HZ);
        c.marker(CommonMarkers.SPECTATOR, 0.5, CAT + 1, 2.5, -90f);
        c.marker(CommonMarkers.EXIT, 66.5, DECK + 1, 0.5, -90f);
        for (int sgn : new int[]{-1, 1}) {
            c.marker(CommonMarkers.GUARD_POST, -64.5, DECK + 1, 7.5 * sgn, -90f, "rank=triangle");
            c.marker(CommonMarkers.GUARD_POST, 63.5, DECK + 1, 7.5 * sgn, 90f, "rank=triangle");
        }
        for (int x : new int[]{-40, -20, 20, 40}) {
            c.marker(CommonMarkers.GUARD_POST, x + 0.5, DECK + 1, 31.5, 180f, "rank=triangle");
        }
        c.marker(CommonMarkers.GUARD_POST, -64.5, DECK + 1, 0.5, -90f, "rank=square");
    }

    /** Waiting grids on the plateaus: 2-block pitch, nearest the deck gate first. */
    private static void lobbies(BuildContext c) {
        List<int[]> cells = new ArrayList<>();
        for (int x = -72; x <= -66; x += 2) {
            for (int z = -13; z <= 13; z += 2) {
                cells.add(new int[]{x, z});
            }
        }
        cells.sort(Comparator.comparingDouble(a -> Math.hypot(a[0] + 61, a[1])));
        for (int i = 0; i < cells.size(); i++) {
            int[] p = cells.get(i);
            c.marker("tug.waiting_a", p[0] + 0.5, DECK + 1, p[1] + 0.5, -90f, "slot=" + i);
            c.marker("tug.waiting_b", -p[0] - 1 + 0.5, DECK + 1, p[1] + 0.5, 90f, "slot=" + i);
        }
    }
}

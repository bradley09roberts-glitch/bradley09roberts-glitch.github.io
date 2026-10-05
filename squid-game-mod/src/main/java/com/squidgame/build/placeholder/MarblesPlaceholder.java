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
 * Throw-away fixture for the marbles game: a crude flat village yard with the full marker contract of
 * docs/ARENA_MARKERS.md - the guards' waiting room (prefab) at the north gate, a pairing square with 165 standing
 * spots, and 64 pair spots (8 x 8 open courts with shared side walls) that use the <b>exact court geometry</b> of the
 * real arena: partner pads 3 blocks apart, a throw line one block in front of them and a 5 x 5 painted bullseye seven
 * blocks beyond the line, all on a floor whose standing height is y = 1.0 (local). The real {@code MarblesBuilder}
 * replaces this fixture automatically (this one has version 0).
 *
 * <p>Court frame (local x lateral, z along the lane): pads at x = -1.0 (A, facing east) and x = 2.0 (B, facing west),
 * z = 1.5; throw line row z = 2; bullseye centre (0.5, 9.5). Spot k = 0 is the one nearest to the square.
 */
public final class MarblesPlaceholder implements ArenaBuilder {
    private static final int COLUMNS = 8, ROWS = 8;
    private static final int PITCH_X = 8, PITCH_Z = 18;
    private static final int FIRST_X = -28, FIRST_Z = 22;

    @Override
    public ArenaId id() {
        return ArenaId.MARBLES;
    }

    @Override
    public int version() {
        return 0;
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED);
        l.addAll(List.of("marbles.square_spawn", "marbles.pair_a", "marbles.pair_b", "marbles.pair_target",
                "marbles.pair_line", "marbles.table", CommonMarkers.GUARD_POST));
        return l;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        l.addAll(List.of("marbles.square", "marbles.plot"));
        return l;
    }

    @Override
    public void build(BuildContext c) {
        ground(c);
        walls(c);
        c.at(0, 0, -10, 0, () -> WaitingRoomPrefab.build(c,
                new WaitingRoomPrefab.Spec(37, 21, 9, "MARBLES", "WAIT FOR THE GAME TO START", 144)));
        square(c);
        courts(c);
        guards(c);
        c.marker("arena.spectator", 0.5, 34.0, 70.5, 0f);
        c.marker("arena.exit", 0.5, 1.0, 6.5, 180f);
        c.region("arena.bounds", -41, -2, -12, 41, 60, 164);
    }

    private void ground(BuildContext c) {
        c.fill(-42, -3, -10, 42, -1, 166, "minecraft:stone_bricks");
        c.noise(-41, 0, -9, 41, 0, 165, new String[]{"minecraft:coarse_dirt", "minecraft:gravel", "minecraft:packed_mud"},
                new double[]{55, 25, 20});
    }

    private void walls(BuildContext c) {
        c.fill(-42, 1, -10, -42, 8, 166, "minecraft:stone_bricks");
        c.fill(42, 1, -10, 42, 8, 166, "minecraft:stone_bricks");
        c.fill(-42, 1, 166, 42, 8, 166, "minecraft:stone_bricks");
        c.fill(-42, 1, -10, -20, 8, -10, "minecraft:stone_bricks");
        c.fill(20, 1, -10, 42, 8, -10, "minecraft:stone_bricks");
    }

    private void square(BuildContext c) {
        c.fill(-15, 0, -9, 15, 0, 15, "minecraft:stone_bricks");
        c.checker(-14, 0, -8, 14, 14, "minecraft:stone_bricks", "minecraft:smooth_stone");
        c.region("marbles.square", -15, 0, -9, 15, 8, 15);
        int slot = 0;
        for (int z = -7; z <= 13; z += 2) {
            for (int x = -14; x <= 14; x += 2) {
                c.marker("marbles.square_spawn", x + 0.5, 1.0, z + 0.5, 0f, "slot=" + slot++);
            }
        }
        for (int x : new int[]{-12, 12}) {
            c.set(x, 1, 13, "minecraft:lantern");
        }
    }

    /** One entry per pair spot: origin of the court frame (gate-line centre). */
    private record Spot(int ox, int oz) {
        double walkingDistance() {
            return Math.abs(ox) + Math.abs(oz + 5 - 3) * 1.15;
        }
    }

    private void courts(BuildContext c) {
        List<Spot> spots = new ArrayList<>();
        for (int col = 0; col < COLUMNS; col++) {
            for (int row = 0; row < ROWS; row++) {
                spots.add(new Spot(FIRST_X + PITCH_X * col, FIRST_Z + PITCH_Z * row));
            }
        }
        spots.sort(Comparator.comparingDouble(Spot::walkingDistance).thenComparingInt(Spot::ox).thenComparingInt(Spot::oz));
        for (int k = 0; k < spots.size(); k++) {
            Spot s = spots.get(k);
            final int index = k;
            c.at(s.ox(), 0, s.oz(), 0, () -> court(c, index));
        }
    }

    /** A crude court in the real court frame (see the class comment). */
    private void court(BuildContext c, int k) {
        c.noise(-3, 0, 0, 3, 0, 11, new String[]{"minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone"},
                new double[]{70, 15, 15});
        for (int side = -1; side <= 1; side += 2) {
            c.fill(4 * side, 1, -1, 4 * side, 3, 12, "minecraft:stone_bricks");
        }
        c.fill(-4, 1, 12, 4, 3, 12, "minecraft:stone_bricks");
        // throw line, partner pads (A light blue, B orange), the table between them
        c.fill(-3, 0, 2, 3, 0, 2, "minecraft:white_concrete");
        c.fill(-2, 0, 1, -1, 0, 1, "minecraft:light_blue_concrete");
        c.fill(1, 0, 1, 2, 0, 1, "minecraft:orange_concrete");
        c.set(0, 0, 1, "minecraft:stone_bricks");
        c.set(0, 1, 1, "minecraft:spruce_slab[type=bottom]");
        // the target: gold bullseye, then red, white, blue, white, red by squared block distance 0,1,2,4,5,8
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                String colour = switch (dx * dx + dz * dz) {
                    case 0 -> "yellow";
                    case 1 -> "red";
                    case 2 -> "white";
                    case 4 -> "blue";
                    case 5 -> "white";
                    default -> "red";
                };
                c.set(dx, 0, 9 + dz, "minecraft:" + colour + "_concrete");
            }
        }
        c.set(-4, 4, 11, "minecraft:lantern[hanging=false]");
        c.set(4, 4, 11, "minecraft:lantern[hanging=false]");
        c.text(0.5, 2.4, 11.4, String.valueOf(k + 1), "#FFE8A0", 2.0f, 180f, false);
        String data = "k=" + k;
        c.marker("marbles.pair_a", -1.0, 1.0, 1.5, -90f, data);
        c.marker("marbles.pair_b", 2.0, 1.0, 1.5, 90f, data);
        c.marker("marbles.pair_line", 0.5, 1.0, 2.5, 0f, data);
        c.marker("marbles.pair_target", 0.5, 1.0, 9.5, 0f, data);
        c.marker("marbles.table", 0.5, 1.5, 1.5, 0f, data);
        c.region("marbles.plot", -3, 0, 0, 3, 8, 11);
    }

    private void guards(BuildContext c) {
        String[] ranks = {"triangle", "triangle", "triangle", "circle", "triangle", "triangle", "square", "triangle"};
        int i = 0;
        for (int z = 0; z <= 150; z += 30) {
            c.marker("guard.post", -39.5, 1.0, z + 0.5, -90f, "rank=" + ranks[i++ % ranks.length]);
            c.marker("guard.post", 39.5, 1.0, z + 0.5, 90f, "rank=" + ranks[i++ % ranks.length]);
        }
        c.marker("guard.post", 0.5, 1.0, 16.5, 0f, "rank=triangle");
        c.marker("guard.post", -17.5, 1.0, 1.5, -90f, "rank=circle");
        // one patrol along the alley in front of the first court row
        int pi = 0;
        for (int x = -36; x <= 36; x += 12) {
            c.marker("guard.patrol", x + 0.5, 1.0, 19.5, 0f, "route=a,i=" + pi++);
        }
    }
}

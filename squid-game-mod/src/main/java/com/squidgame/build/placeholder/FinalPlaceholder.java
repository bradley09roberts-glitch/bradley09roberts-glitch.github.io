package com.squidgame.build.placeholder;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.core.finale.SquidShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Test fixture of the Final Squid Game arena, used while no real builder exists (the real
 * {@code com.squidgame.build.arena.FinalBuilder} replaces it as soon as it is on the class path, because this fixture is
 * version 0). A flat sand yard with the squid court painted on it exactly as the real arena has it (the outline and
 * line cells come from {@link SquidShape}), a few lanterns, an open gallery strip beside the court and all markers and
 * regions of the contract in docs/ARENA_MARKERS.md, including the {@code final.boundary} polygon.
 *
 * <p>Local frame: floor block y=0, +Z south, court between z=-25 (top of the head) and z=25 (bottom of the square),
 * the waiting area south of it (z 31..43).
 */
public final class FinalPlaceholder implements ArenaBuilder {
    private static final String SAND = "minecraft:smooth_sandstone";
    private static final String LINE = "minecraft:white_concrete";
    private static final String GOLD_RING = "minecraft:yellow_concrete";
    private static final String GOLD_PAD = "minecraft:gold_block";

    private static final int X0 = -34, X1 = 34, Z0 = -40, Z1 = 46;
    private static final int COURT_TOP = SquidShape.ZC - SquidShape.R;

    @Override
    public ArenaId id() {
        return ArenaId.FINAL;
    }

    @Override
    public int version() {
        return 0;
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED);
        out.addAll(List.of(CommonMarkers.GUARD_POST, "final.boundary", "final.circle", "final.triangle", "final.neck",
                "final.attacker_spawn", "final.defender_spawn", "final.audience"));
        return out;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        out.addAll(List.of("final.court", "final.attack_zone", "final.defence_zone"));
        return out;
    }

    @Override
    public void build(BuildContext c) {
        // ground, rim wall and a clear space above
        c.fill(X0 - 1, -2, Z0 - 1, X1 + 1, -1, Z1 + 1, "minecraft:stone_bricks");
        c.fill(X0, 0, Z0, X1, 0, Z1, SAND);
        c.clear(X0, 1, Z0, X1, 14, Z1);
        c.walls(X0 - 1, 0, Z0 - 1, X1 + 1, 2, Z1 + 1, "minecraft:white_concrete");
        // the wall between the court and the waiting area, with the opening of the gate (5 wide, 4 high)
        c.fill(X0, 1, 29, X1, 5, 29, "minecraft:white_concrete");
        c.clear(-2, 1, 29, 2, 4, 29);

        // the squid: white lines, a golden ring and pad in the head
        for (int[] cell : SquidShape.lineCells()) {
            c.set(cell[0], 0, cell[1], LINE);
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 3.25 && d > 2.25) {
                    c.set(dx, 0, SquidShape.ZC + dz, GOLD_RING);
                }
            }
        }
        c.fill(-1, 0, SquidShape.ZC - 1, 1, 0, SquidShape.ZC + 1, GOLD_PAD);

        // light: lantern posts around the court and invisible light blocks over it
        for (int x = -11; x <= 11; x += 22) {
            for (int z = -24; z <= 24; z += 16) {
                c.fill(x, 1, z, x, 4, z, "minecraft:stone_brick_wall");
                c.set(x, 5, z, "minecraft:sea_lantern");
            }
        }
        for (int x = -12; x <= 12; x += 6) {
            for (int z = -24; z <= 30; z += 6) {
                c.set(x, 6, z, "minecraft:light[level=15]");
            }
        }

        // a gallery strip east of the court: raised two blocks, a rail in front
        c.fill(14, 0, -20, 20, 1, 22, "minecraft:stone_bricks");
        c.fill(14, 2, -20, 14, 2, 22, "minecraft:stone_brick_wall");
        int slot = 0;
        for (int z = -19; z <= 21 && slot < 60; z += 3) {
            for (int x = 16; x <= 19 && slot < 60; x++) {
                c.marker("final.audience", x + 0.5, 2.0, z + 0.5, 90f, "slot=" + slot++);
            }
        }

        // court markers
        c.marker("final.circle", 0.5, 1.0, SquidShape.ZC + 0.5, 0f, "r=" + SquidShape.R);
        c.marker("final.triangle", 0.5, 1.0, SquidShape.ZC + 16.5, 0f, "base=" + (2 * SquidShape.HW) + ",height=" + (SquidShape.ZB - SquidShape.ZC - SquidShape.R + 1));
        c.marker("final.neck", 0.5, 1.0, (SquidShape.ZB + SquidShape.ZS) / 2 + 0.5, 0f, "w=3");
        c.marker("final.attacker_spawn", 0.5, 1.0, SquidShape.ZQ - 2.5, 180f);
        c.marker("final.defender_spawn", 0.5, 1.0, SquidShape.ZC + 14.5, 0f);
        List<double[]> outline = SquidShape.outline();
        for (int i = 0; i < outline.size(); i++) {
            c.marker("final.boundary", outline.get(i)[0], 1.0, outline.get(i)[1], 0f, "i=" + i + ",stand=0");
        }
        c.region("final.court", -SquidShape.HW, 0, COURT_TOP, SquidShape.HW, 12, SquidShape.ZQ);
        c.region("final.attack_zone", -SquidShape.HW, 0, SquidShape.ZS, SquidShape.HW, 12, SquidShape.ZQ);
        c.region("final.defence_zone", -SquidShape.HW, 0, COURT_TOP, SquidShape.HW, 12, SquidShape.ZB);
        c.marker("final.podium", 0.5, 1.0, COURT_TOP - 4.5, 0f);

        // common markers: the waiting area south of the court, viewing point, exit, guards
        slot = 0;
        for (int z = 32; z <= 43; z++) {
            for (int x = -30; x <= 30; x++) {
                c.marker("waiting.spawn", x + 0.5, 1.0, z + 0.5, 180f, "slot=" + slot++);
            }
        }
        c.marker("waiting.player_entry", 0.5, 1.0, 44.5, 180f);
        c.marker("gate.door", 0.5, 1.0, 29.5, 180f, "w=5,h=4");
        c.marker("arena.spectator", 0.5, 8.0, 36.5, 180f);
        c.marker("arena.exit", 0.5, 1.0, 30.5, 180f);
        for (int x = -24; x <= 24; x += 12) {
            c.marker("guard.post", x + 0.5, 1.0, -34.5, 0f, "rank=triangle");
        }
        c.marker("guard.post", -20.5, 1.0, 10.5, -90f, "rank=circle");
        c.marker("guard.post", 12.5, 1.0, 28.5, 180f, "rank=square");
        c.region(CommonMarkers.REGION_BOUNDS, X0, 0, Z0, X1, 40, Z1);
        c.region(CommonMarkers.REGION_WAITING, X0, 0, 30, X1, 14, Z1);
        c.text(0.5, 5.0, COURT_TOP - 6.5, "THE FINAL SQUID GAME (fixture)", "white", 3f, 0f, true);
    }
}

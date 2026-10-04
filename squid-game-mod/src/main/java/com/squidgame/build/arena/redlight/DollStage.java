package com.squidgame.build.arena.redlight;

import com.squidgame.build.BuildContext;

import static com.squidgame.build.arena.redlight.Layout.*;

/**
 * The doll's round wooden stage: a one-block-high disc of radial planks (radius 4) with an orange trim ring, a ring of
 * low slab steps, a paved apron with glowing floor lights. The 9-block doll stands on the centre (marker
 * {@code redlight.doll}, feet at y = 2.0); the air above (14 blocks) and around (5 blocks) is kept empty.
 */
public final class DollStage {
    private DollStage() {
    }

    private static final String ORANGE = "minecraft:orange_concrete";

    public static void build(BuildContext c) {
        int cx = DOLL_X, cz = DOLL_Z;
        // paved apron at floor level (r up to 8.4) with sandstone and quartz accents, lit by floor lights
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 8.4 || d <= PEDESTAL_R + 0.25) {
                    continue;
                }
                double r = Noise.rand(201, cx + dx, 0, cz + dz);
                String b;
                if (d > 7.2) {
                    b = r < 0.5 ? "minecraft:smooth_stone" : "minecraft:polished_andesite";
                } else if (d > 6.2) {
                    b = "minecraft:orange_terracotta";
                } else {
                    b = r < 0.55 ? "minecraft:cut_sandstone" : (r < 0.85 ? "minecraft:quartz_block" : "minecraft:smooth_stone");
                }
                c.set(cx + dx, 0, cz + dz, b);
            }
        }
        // floor lights: a ring of 12 flush sea lanterns
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + Math.PI / 12;
            c.set(cx + (int) Math.round(Math.cos(a) * 5.9), 0, cz + (int) Math.round(Math.sin(a) * 5.9), "minecraft:sea_lantern");
        }
        // slab steps around the stage (half-height), then the stage itself
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > PEDESTAL_R + 0.25 && d <= PEDESTAL_R + 1.35) {
                    c.set(cx + dx, 1, cz + dz, "minecraft:spruce_slab[type=bottom]");
                }
            }
        }
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > PEDESTAL_R + 0.25) {
                    continue;
                }
                String b;
                if (d > PEDESTAL_R - 0.85) {
                    b = ORANGE;
                } else {
                    // radial boards: alternate wood tones per sector
                    double ang = Math.atan2(dz, dx);
                    int sector = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * 14);
                    b = (sector & 1) == 0 ? "minecraft:spruce_planks" : "minecraft:dark_oak_planks";
                    if (d < 1.2) {
                        b = "minecraft:stripped_dark_oak_log[axis=y]";
                    }
                }
                c.set(cx + dx, 1, cz + dz, b);
            }
        }
        // free air above and around the doll
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.hypot(dx, dz) <= 5.7) {
                    c.clear(cx + dx, 2, cz + dz, cx + dx, 18, cz + dz);
                }
            }
        }
    }

    /** Clears the doll's air column again (call after anything tall has been built nearby). */
    public static void keepClear(BuildContext c) {
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.hypot(dx, dz) <= 5.7) {
                    c.clear(DOLL_X + dx, 2, DOLL_Z + dz, DOLL_X + dx, 18, DOLL_Z + dz);
                }
            }
        }
    }
}

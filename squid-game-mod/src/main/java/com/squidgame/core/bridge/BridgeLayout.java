package com.squidgame.core.bridge;

import java.util.List;

/**
 * Geometry of the bridge in world coordinates, built from the arena markers: the 2x2 footprint of every panel, the
 * platforms at both ends, which panel a body touches, and where a body should take off and land to hop from one
 * panel to the next. The bridge runs along +Z (row r + 1 lies at larger z than row r); lane 0 is the one with the
 * smaller x. Pure Java, no Minecraft types.
 */
public final class BridgeLayout {
    /** Edge length of a panel in blocks. */
    public static final double PANEL_SIZE = 2.0;
    /** Half the width of a contestant's hitbox. */
    public static final double HALF_WIDTH = 0.3;
    /** How far inside a panel a body stands before it hops (keeps the whole hitbox supported). */
    public static final double TAKEOFF_MARGIN = 0.45;
    /** How far inside the destination a hop is aimed (keeps the landing safely on the panel). */
    public static final double LANDING_INSET = 0.75;

    public record Vec2(double x, double z) {
        public double distanceTo(Vec2 o) {
            return Math.hypot(o.x - x, o.z - z);
        }
    }

    /** Axis-aligned rectangle on the XZ plane (max exclusive). */
    public record Rect(double minX, double maxX, double minZ, double maxZ) {
        public boolean contains(double x, double z) {
            return x >= minX && x < maxX && z >= minZ && z < maxZ;
        }

        public double centerX() {
            return (minX + maxX) / 2;
        }

        public double centerZ() {
            return (minZ + maxZ) / 2;
        }

        public Rect inset(double m) {
            double cx = centerX(), cz = centerZ();
            return new Rect(Math.min(cx, minX + m), Math.max(cx, maxX - m), Math.min(cz, minZ + m), Math.max(cz, maxZ - m));
        }

        public Vec2 clamp(double x, double z) {
            return new Vec2(Math.max(minX, Math.min(maxX, x)), Math.max(minZ, Math.min(maxZ, z)));
        }

        /** Overlap area with the square of half width {@code h} around (x, z). */
        double overlap(double x, double z, double h) {
            double w = Math.min(maxX, x + h) - Math.max(minX, x - h);
            double d = Math.min(maxZ, z + h) - Math.max(minZ, z - h);
            return w > 0 && d > 0 ? w * d : 0;
        }
    }

    public record Panel(int row, int lane, Rect rect, double top) {
    }

    /**
     * A {@code bridge.panel} marker: row, lane, a point inside the panel (the arena contract puts it at the centre of the
     * panel's first block: x = -2.5 / 1.5, z = 10.5 + 3r; a point on the block corner at the panel's centre works too)
     * and the height of its walking surface.
     */
    public record PanelSpec(int row, int lane, double centerX, double centerZ, double top) {
    }

    private final int rows;
    private final Panel[][] panels;
    private final Rect start;
    private final Rect finish;

    private BridgeLayout(int rows, Panel[][] panels, Rect start, Rect finish) {
        this.rows = rows;
        this.panels = panels;
        this.start = start;
        this.finish = finish;
    }

    /**
     * @param specs  one entry per panel (rows 0..n-1, lanes 0..1, all present)
     * @param start  the start platform (where the queue waits)
     * @param finish the end platform
     * @throws IllegalArgumentException when panels are missing or duplicated
     */
    public static BridgeLayout of(List<PanelSpec> specs, Rect start, Rect finish) {
        int rows = 0;
        for (PanelSpec s : specs) {
            rows = Math.max(rows, s.row() + 1);
        }
        if (rows == 0 || specs.size() != rows * BridgeRoute.LANES) {
            throw new IllegalArgumentException("expected " + rows * BridgeRoute.LANES + " panels, got " + specs.size());
        }
        Panel[][] panels = new Panel[rows][BridgeRoute.LANES];
        for (PanelSpec s : specs) {
            if (s.row() < 0 || s.lane() < 0 || s.lane() >= BridgeRoute.LANES || panels[s.row()][s.lane()] != null) {
                throw new IllegalArgumentException("bad panel " + s);
            }
            panels[s.row()][s.lane()] = new Panel(s.row(), s.lane(), footprint(s.centerX(), s.centerZ()), s.top());
        }
        return new BridgeLayout(rows, panels, start, finish);
    }

    /**
     * The 2x2 block footprint of the panel a marker belongs to. The arena contract puts the marker at the centre of the
     * panel's first block (x = -2.5 for blocks -3 and -2; z = 10.5 for blocks 10 and 11), so the footprint starts at the
     * block containing the marker. Rounding (half up) gives exactly that for those positions and also for a marker on the
     * block corner at the panel's centre (x = -2.0, z = 11.0).
     */
    public static Rect footprint(double centerX, double centerZ) {
        double x0 = Math.round(centerX) - 1;
        double z0 = Math.round(centerZ) - 1;
        return new Rect(x0, x0 + PANEL_SIZE, z0, z0 + PANEL_SIZE);
    }

    public int rows() {
        return rows;
    }

    public Panel panel(int row, int lane) {
        return panels[row][lane];
    }

    public Rect start() {
        return start;
    }

    public Rect finish() {
        return finish;
    }

    /** Height of the walking surface of the deck. */
    public double deckTop() {
        return panels[0][0].top();
    }

    /** The panel whose footprint contains the point, or null. */
    public Panel panelAt(double x, double z) {
        for (int r = 0; r < rows; r++) {
            for (int l = 0; l < BridgeRoute.LANES; l++) {
                if (panels[r][l].rect().contains(x, z)) {
                    return panels[r][l];
                }
            }
        }
        return null;
    }

    /**
     * The panel a body centred at (x, z) stands on: the one overlapping its hitbox footprint the most (a body can
     * stand with its centre slightly beyond an edge), or null when it overlaps none.
     */
    public Panel supporting(double x, double z) {
        int row = approxRow(z);
        Panel best = null;
        double bestOverlap = 0;
        for (int r = Math.max(0, row - 1); r <= Math.min(rows - 1, row + 1); r++) {
            for (int l = 0; l < BridgeRoute.LANES; l++) {
                double o = panels[r][l].rect().overlap(x, z, HALF_WIDTH);
                if (o > bestOverlap) {
                    bestOverlap = o;
                    best = panels[r][l];
                }
            }
        }
        return best;
    }

    private int approxRow(double z) {
        double first = panels[0][0].rect().minZ();
        double pitch = rows > 1 ? panels[1][0].rect().minZ() - first : 3.0;
        return (int) Math.floor((z - first) / pitch);
    }

    public double laneCenterX(int lane) {
        double sum = 0;
        for (int r = 0; r < rows; r++) {
            sum += panels[r][lane].rect().centerX();
        }
        return sum / rows;
    }

    /** True when the lane is on the left-hand side of a contestant walking along the bridge (towards +Z). */
    public boolean isLeftLane(int lane) {
        return laneCenterX(lane) > laneCenterX(1 - lane);
    }

    // ------------------------------------------------------------------ hop geometry

    /** Where to stand on {@code from} before hopping towards {@code to}: the point of the panel nearest to the target. */
    public Vec2 takeoff(Panel from, Panel to) {
        return from.rect().inset(TAKEOFF_MARGIN).clamp(to.rect().centerX(), to.rect().centerZ());
    }

    /** Where to land on {@code to} when taking off at {@code takeoff}: the nearest point of the panel, well inside. */
    public Vec2 landing(Vec2 takeoff, Panel to) {
        return to.rect().inset(LANDING_INSET).clamp(takeoff.x(), takeoff.z());
    }

    /** Take-off point on the start platform for the hop onto {@code first}: in line with the panel, at the platform's front edge. */
    public Vec2 gateTakeoff(Panel first) {
        return new Vec2(first.rect().centerX(), start.maxZ() - TAKEOFF_MARGIN - 0.05);
    }

    /** Where to land on the end platform: straight ahead of the take-off point, a little way past its near edge. */
    public Vec2 finishLanding(Vec2 takeoff) {
        return finish.inset(LANDING_INSET + 0.25).clamp(takeoff.x(), takeoff.z());
    }
}

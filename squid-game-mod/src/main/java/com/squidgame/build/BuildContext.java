package com.squidgame.build;

import com.squidgame.core.util.Rng;

import java.util.function.Predicate;

/**
 * The building DSL handed to every {@link ArenaBuilder}. All coordinates passed to its methods
 * are <b>local</b>: they go through the current transform stack (translation + rotation in
 * quarter turns around Y) and then the structure origin, so a prefab such as a bunk bed can be
 * written once and placed anywhere, facing any way, with {@link #at(int, int, int, int, Runnable)}.
 *
 * <p>Block arguments are block-state strings such as {@code "minecraft:stone_bricks"} or
 * {@code "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]"}; stairs/axes/facing are
 * rotated along with the transform. Everything is written into a {@link BlockBuffer}; nothing here
 * touches Minecraft, so builders run in unit tests and in the preview tool.
 *
 * <p>Convention reminders: +X east, +Z south, +Y up; yaw 0 faces south (+Z), 90 west, 180 north,
 * -90 east.
 */
public final class BuildContext {
    private final BlockBuffer buffer;
    private final int originX, originY, originZ;
    private final Rng rng;

    // transform stack: rotation steps + translation, composed
    private int rot;                 // quarter turns, clockwise from above
    private int tx, ty, tz;          // translation applied after rotation (in structure-local space)
    private final java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();

    public BuildContext(BlockBuffer buffer, int originX, int originY, int originZ, long seed) {
        this.buffer = buffer;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.rng = new Rng(seed);
    }

    public BlockBuffer buffer() {
        return buffer;
    }

    public Rng rng() {
        return rng;
    }

    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }

    // ------------------------------------------------------------ transform

    /** Runs {@code body} with the local frame translated by (dx,dy,dz) and rotated {@code rotSteps} quarter turns. */
    public void at(int dx, int dy, int dz, int rotSteps, Runnable body) {
        stack.push(new int[]{rot, tx, ty, tz});
        // new local origin expressed in parent-local space
        int[] p = rotateVec(dx, dz, rot);
        tx += p[0];
        ty += dy;
        tz += p[1];
        rot = ((rot + rotSteps) % 4 + 4) % 4;
        try {
            body.run();
        } finally {
            int[] s = stack.pop();
            rot = s[0];
            tx = s[1];
            ty = s[2];
            tz = s[3];
        }
    }

    public void at(int dx, int dy, int dz, Runnable body) {
        at(dx, dy, dz, 0, body);
    }

    private static int[] rotateVec(int x, int z, int steps) {
        steps = ((steps % 4) + 4) % 4;
        return switch (steps) {
            case 0 -> new int[]{x, z};
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            default -> new int[]{z, -x};
        };
    }

    /** Local -> world X. */
    private int wx(int x, int z) {
        return originX + tx + rotateVec(x, z, rot)[0];
    }

    private int wz(int x, int z) {
        return originZ + tz + rotateVec(x, z, rot)[1];
    }

    private int wy(int y) {
        return originY + ty + y;
    }

    /** World X of a local point (public so builders can compute absolute coordinates for markers). */
    public int worldX(int x, int z) {
        return wx(x, z);
    }

    public int worldY(int y) {
        return wy(y);
    }

    public int worldZ(int x, int z) {
        return wz(x, z);
    }

    /** Yaw (degrees) in world space for a local yaw. */
    public float worldYaw(float localYaw) {
        return localYaw + rot * 90f;
    }

    private String st(String state) {
        return rot == 0 ? state : StateString.rotate(state, rot);
    }

    // ------------------------------------------------------------ primitives

    public void set(int x, int y, int z, String state) {
        buffer.set(wx(x, z), wy(y), wz(x, z), st(state));
    }

    /** Sets only where nothing solid has been written yet. */
    public void setIfFree(int x, int y, int z, String state) {
        int X = wx(x, z), Y = wy(y), Z = wz(x, z);
        if (!buffer.isSolidSet(X, Y, Z)) {
            buffer.set(X, Y, Z, st(state));
        }
    }

    public String get(int x, int y, int z) {
        return buffer.get(wx(x, z), wy(y), wz(x, z));
    }

    public boolean isSolid(int x, int y, int z) {
        return buffer.isSolidSet(wx(x, z), wy(y), wz(x, z));
    }

    public void air(int x, int y, int z) {
        set(x, y, z, BlockBuffer.AIR);
    }

    /** Inclusive cuboid fill. */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        int ax = wx(x1, z1), az = wz(x1, z1), bx = wx(x2, z2), bz = wz(x2, z2);
        int minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
        int minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
        int minY = wy(Math.min(y1, y2)), maxY = wy(Math.max(y1, y2));
        int id = buffer.paletteId(st(state));
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    buffer.setRaw(x, y, z, id);
                }
            }
        }
    }

    /** Fills only positions where {@code replaceable} accepts the existing state (null = untouched). */
    public void fillWhere(int x1, int y1, int z1, int x2, int y2, int z2, Predicate<String> replaceable, String state) {
        int ax = wx(x1, z1), az = wz(x1, z1), bx = wx(x2, z2), bz = wz(x2, z2);
        int minX = Math.min(ax, bx), maxX = Math.max(ax, bx);
        int minZ = Math.min(az, bz), maxZ = Math.max(az, bz);
        int minY = wy(Math.min(y1, y2)), maxY = wy(Math.max(y1, y2));
        int id = buffer.paletteId(st(state));
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (replaceable.test(buffer.get(x, y, z))) {
                        buffer.setRaw(x, y, z, id);
                    }
                }
            }
        }
    }

    /** Fills a cuboid with air (carves). */
    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, BlockBuffer.AIR);
    }

    /** Hollow shell: all six faces of the box, interior untouched. */
    public void shell(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        int lx = Math.min(x1, x2), hx = Math.max(x1, x2);
        int ly = Math.min(y1, y2), hy = Math.max(y1, y2);
        int lz = Math.min(z1, z2), hz = Math.max(z1, z2);
        fill(lx, ly, lz, hx, ly, hz, state);
        fill(lx, hy, lz, hx, hy, hz, state);
        fill(lx, ly, lz, lx, hy, hz, state);
        fill(hx, ly, lz, hx, hy, hz, state);
        fill(lx, ly, lz, hx, hy, lz, state);
        fill(lx, ly, hz, hx, hy, hz, state);
    }

    /** The four vertical walls of a box (no floor / ceiling). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        int lx = Math.min(x1, x2), hx = Math.max(x1, x2);
        int lz = Math.min(z1, z2), hz = Math.max(z1, z2);
        fill(lx, y1, lz, lx, y2, hz, state);
        fill(hx, y1, lz, hx, y2, hz, state);
        fill(lx, y1, lz, hx, y2, lz, state);
        fill(lx, y1, hz, hx, y2, hz, state);
    }

    /**
     * A complete room: solid shell of {@code wall}, floor and ceiling overrides, interior carved to air.
     * Floor occupies the y1 layer, ceiling the y2 layer.
     */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, String floor, String wall, String ceiling) {
        int lx = Math.min(x1, x2), hx = Math.max(x1, x2);
        int ly = Math.min(y1, y2), hy = Math.max(y1, y2);
        int lz = Math.min(z1, z2), hz = Math.max(z1, z2);
        fill(lx, ly + 1, lz, hx, hy - 1, hz, BlockBuffer.AIR);
        walls(lx, ly + 1, lz, hx, hy - 1, hz, wall);
        fill(lx, ly, lz, hx, ly, hz, floor);
        fill(lx, hy, lz, hx, hy, hz, ceiling);
    }

    /** 3D line (integer Bresenham). */
    public void line(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), dz = Math.abs(z2 - z1);
        int sx = Integer.compare(x2, x1), sy = Integer.compare(y2, y1), sz = Integer.compare(z2, z1);
        int dm = Math.max(dx, Math.max(dy, dz));
        int x = x1, y = y1, z = z1;
        int ex = dm / 2, ey = dm / 2, ez = dm / 2;
        for (int i = 0; i <= dm; i++) {
            set(x, y, z, state);
            ex -= dx;
            if (ex < 0) {
                ex += dm;
                x += sx;
            }
            ey -= dy;
            if (ey < 0) {
                ey += dm;
                y += sy;
            }
            ez -= dz;
            if (ez < 0) {
                ez += dm;
                z += sz;
            }
        }
    }

    /** Filled horizontal disc at height y. */
    public void disc(int cx, int y, int cz, double r, String state) {
        int ir = (int) Math.ceil(r);
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) <= r + 0.25) {
                    set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    /** One-block-thick ring at height y. */
    public void ring(int cx, int y, int cz, double r, String state) {
        int ir = (int) Math.ceil(r) + 1;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= r + 0.25 && d > r - 0.75) {
                    set(cx + dx, y, cz + dz, state);
                }
            }
        }
    }

    public void cylinder(int cx, int y1, int y2, int cz, double r, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            disc(cx, y, cz, r, state);
        }
    }

    public void hollowCylinder(int cx, int y1, int y2, int cz, double r, String state) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            ring(cx, y, cz, r, state);
        }
    }

    public void sphere(int cx, int cy, int cz, double r, String state, boolean hollow) {
        int ir = (int) Math.ceil(r) + 1;
        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d <= r + 0.25 && (!hollow || d > r - 0.9)) {
                        set(cx + dx, cy + dy, cz + dz, state);
                    }
                }
            }
        }
    }

    /** Ellipsoid with radii rx, ry, rz. */
    public void ellipsoid(int cx, int cy, int cz, double rx, double ry, double rz, String state, boolean hollow) {
        int ix = (int) Math.ceil(rx) + 1, iy = (int) Math.ceil(ry) + 1, iz = (int) Math.ceil(rz) + 1;
        double minR = Math.min(rx, Math.min(ry, rz));
        for (int dx = -ix; dx <= ix; dx++) {
            for (int dy = -iy; dy <= iy; dy++) {
                for (int dz = -iz; dz <= iz; dz++) {
                    double d = Math.sqrt((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz));
                    if (d <= 1.0 + 0.2 / minR && (!hollow || d > 1.0 - 1.3 / minR)) {
                        set(cx + dx, cy + dy, cz + dz, state);
                    }
                }
            }
        }
    }

    /** Function fill: {@code f} returns a state, or null to skip. */
    public void pattern(int x1, int y1, int z1, int x2, int y2, int z2, TriFunction f) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    String s = f.apply(x, y, z);
                    if (s != null) {
                        set(x, y, z, s);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    public interface TriFunction {
        String apply(int x, int y, int z);
    }

    /** Weighted random fill for weathering / texture variation. */
    public void noise(int x1, int y1, int z1, int x2, int y2, int z2, String[] states, double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        final double sum = total;
        pattern(x1, y1, z1, x2, y2, z2, (x, y, z) -> {
            double r = rng.nextDouble() * sum;
            for (int i = 0; i < states.length; i++) {
                r -= weights[i];
                if (r <= 0) {
                    return states[i];
                }
            }
            return states[states.length - 1];
        });
    }

    /** Scatters {@code state} on free positions with the given probability. */
    public void scatter(int x1, int y1, int z1, int x2, int y2, int z2, String state, double density) {
        pattern(x1, y1, z1, x2, y2, z2, (x, y, z) -> rng.chance(density) ? state : null);
    }

    /** Checkerboard on a flat layer. */
    public void checker(int x1, int y, int z1, int x2, int z2, String a, String b) {
        pattern(x1, y, z1, x2, y, z2, (x, yy, z) -> ((x + z) & 1) == 0 ? a : b);
    }

    // ------------------------------------------------------------ markers / entities

    /** Registers a marker at a local position (block centre) with a local yaw. */
    public Marker marker(String name, double x, double y, double z, float localYaw) {
        return marker(name, x, y, z, localYaw, "");
    }

    public Marker marker(String name, double x, double y, double z, float localYaw, String data) {
        double[] w = worldPos(x, y, z);
        Marker m = new Marker(name, w[0], w[1], w[2], worldYaw(localYaw), data);
        buffer.addMarker(m);
        return m;
    }

    /** Marker at the standing position on top of local block (bx, by, bz): centred, y = block top. */
    public Marker markerOn(String name, int bx, int by, int bz, float localYaw) {
        return marker(name, bx + 0.5, by + 1.0, bz + 0.5, localYaw, "");
    }

    public Region region(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        int ax = wx(x1, z1), az = wz(x1, z1), bx = wx(x2, z2), bz = wz(x2, z2);
        Region r = new Region(name, Math.min(ax, bx), wy(Math.min(y1, y2)), Math.min(az, bz),
                Math.max(ax, bx), wy(Math.max(y1, y2)), Math.max(az, bz));
        buffer.addRegion(r);
        return r;
    }

    /** World position of a local (fractional) point; block centre of local block (x,z) is x+0.5, z+0.5. */
    public double[] worldPos(double x, double y, double z) {
        // rotate about the block-grid origin: block (x,z) covers [x, x+1); rotate its centre
        double cx = x, cz = z;
        double rx, rz;
        switch (((rot % 4) + 4) % 4) {
            case 0 -> { rx = cx; rz = cz; }
            case 1 -> { rx = -cz; rz = cx; }
            case 2 -> { rx = -cx; rz = -cz; }
            default -> { rx = cz; rz = -cx; }
        }
        // rotation of the continuous point maps the block corner grid with an offset of 1 on the negative axes
        // for quarter turns; correct so a block centre stays a block centre.
        double offX = 0, offZ = 0;
        switch (((rot % 4) + 4) % 4) {
            case 1 -> offX = 1;
            case 2 -> { offX = 1; offZ = 1; }
            case 3 -> offZ = 1;
            default -> { }
        }
        return new double[]{originX + tx + rx + offX, originY + ty + y, originZ + tz + rz + offZ};
    }

    /**
     * Spawns a text display (signage). {@code text} is plain text (avoid apostrophes); {@code color} a
     * hex "#RRGGBB" or a Minecraft colour name; {@code scale} multiplies the default size; {@code localYaw}
     * is the direction the text faces (0 = faces south/+Z).
     */
    public void text(double x, double y, double z, String text, String color, float scale, float localYaw, boolean background) {
        double[] w = worldPos(x, y, z);
        // text component as JSON, then embedded in an SNBT single-quoted string (backslashes and quotes escaped)
        String json = "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                + "\",\"color\":\"" + color + "\"}";
        String quoted = "'" + json.replace("\\", "\\\\").replace("'", "\\'") + "'";
        String snbt = "{text:" + quoted + ",billboard:\"fixed\",alignment:\"center\",line_width:400,shadow:true,"
                + "see_through:false,background:" + (background ? "1073741824" : "0")
                + ",brightness:{block:15,sky:15},"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],"
                + "scale:[" + scale + "f," + scale + "f," + scale + "f]}}";
        buffer.addEntity(new EntitySpec("minecraft:text_display", w[0], w[1], w[2], worldYaw(localYaw), 0f, snbt));
    }

    /** Spawns an arbitrary entity at a local position. */
    public void entity(String type, double x, double y, double z, float localYaw, String snbt) {
        double[] w = worldPos(x, y, z);
        buffer.addEntity(new EntitySpec(type, w[0], w[1], w[2], worldYaw(localYaw), 0f, snbt == null ? "{}" : snbt));
    }
}

package com.squidgame.game.finale;

import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.core.finale.CourtGeometry;
import com.squidgame.core.finale.CourtGeometry.Pt;
import com.squidgame.game.GameContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The final arena as the game sees it: the court geometry built from the markers of the contract
 * (docs/ARENA_MARKERS.md: {@code final.boundary}, {@code final.circle}, {@code final.neck}, the two spawns) and the
 * places for the audience. Nothing is hard-coded: the same game runs on the fixture and on the real arena.
 */
final class FinaleArena {
    final CourtGeometry court;
    final Vec3 attackerSpawn, defenderSpawn;
    final float attackerYaw, defenderYaw;
    final Vec3 center;
    final double floorY;
    final List<Marker> audience;
    @Nullable
    final Marker podium;
    /** Places beside the court for humans who wait for a duel (see {@link #beltSpot}), nearest to the middle first. */
    private final List<double[]> belt = new ArrayList<>();

    FinaleArena(CourtGeometry court, Marker attacker, Marker defender, Vec3 center, List<Marker> audience, @Nullable Marker podium) {
        this.court = court;
        this.attackerSpawn = new Vec3(attacker.x(), attacker.y(), attacker.z());
        this.defenderSpawn = new Vec3(defender.x(), defender.y(), defender.z());
        this.attackerYaw = attacker.yaw();
        this.defenderYaw = defender.yaw();
        this.center = center;
        this.floorY = attacker.y();
        this.audience = audience;
        this.podium = podium;
        computeBelt();
    }

    /**
     * Humans must stay inside {@code arena.bounds} while a game runs (the tournament puts them back and finally eliminates
     * them), and the gallery of the real arena lies outside of it. So a human who waits for their duel watches from the
     * clear belt around the court instead: two columns on either side, at least 2.4 blocks outside the painted line (nothing
     * a fighter can be thrown into: the line decides the duel first), looking at the middle of the court. NPCs sit on the
     * gallery as the contract wants.
     */
    private void computeBelt() {
        Pt axis = court.axis();
        double lx = -axis.z(), lz = axis.x();
        double half = court.axisLength() / 2 + 3;
        for (int step = 0; step * 3.0 <= half + 3; step++) {
            for (int sign = step == 0 ? 1 : -1; sign <= 1; sign += 2) {
                double along = step * 3.0 * sign;
                for (int col = 0; col < 2; col++) {
                    for (int side = 1; side >= -1; side -= 2) {
                        double lat = side * (10.5 + col * 1.5);
                        double x = center.x + axis.x() * along + lx * lat;
                        double z = center.z + axis.z() * along + lz * lat;
                        if (court.edgeDistance(x, z) < -2.4) {
                            double yaw = com.squidgame.core.finale.FinaleRules.yawOf(center.x - x, center.z - z);
                            belt.add(new double[]{x, z, yaw});
                        }
                    }
                }
            }
        }
    }

    /** The i-th place for a waiting human (wraps when there are more waiting humans than places). */
    Vec3 beltSpot(int i) {
        if (belt.isEmpty()) {
            return center;
        }
        double[] b = belt.get(i % belt.size());
        return new Vec3(b[0], floorY, b[1]);
    }

    float beltYaw(int i) {
        return belt.isEmpty() ? 0f : (float) belt.get(i % belt.size())[2];
    }

    /** Reads the arena through the context. Returns null when the markers needed for a duel are missing. */
    @Nullable
    static FinaleArena read(GameContext ctx) {
        Marker attacker = ctx.marker("final.attacker_spawn");
        Marker defender = ctx.marker("final.defender_spawn");
        Marker circle = ctx.marker("final.circle");
        if (attacker == null || defender == null || circle == null) {
            return null;
        }
        CourtGeometry court = courtFrom(ctx.markers("final.boundary"), circle, ctx.marker("final.neck"), attacker, defender,
                ctx.region("final.court"));
        if (court == null) {
            return null;
        }
        List<Marker> audience = new ArrayList<>(ctx.markers("final.audience"));
        audience.sort(Comparator.comparingInt(m -> m.getInt("slot", 0)));
        Vec3 center = new Vec3((attacker.x() + circle.x()) / 2, attacker.y(), (attacker.z() + circle.z()) / 2);
        return new FinaleArena(court, attacker, defender, center, audience, ctx.marker("final.podium"));
    }

    /**
     * The court from the markers of the contract: the polygon of the {@code final.boundary} vertices in the order of their
     * {@code i}, the head circle with its radius, the neck, the spawns. A broken arena without a usable polygon falls back
     * to the box of {@code final.court} so that the game still runs; null when even that is missing.
     */
    @Nullable
    static CourtGeometry courtFrom(List<Marker> boundaryMarkers, Marker circle, @Nullable Marker neck, Marker attacker,
                                   Marker defender, @Nullable Region box) {
        List<Marker> boundary = new ArrayList<>(boundaryMarkers);
        boundary.sort(Comparator.comparingInt(m -> m.getInt("i", 0)));
        List<Pt> poly = new ArrayList<>();
        for (Marker m : boundary) {
            poly.add(new Pt(m.x(), m.z()));
        }
        if (poly.size() < 3) {
            if (box == null) {
                return null;
            }
            poly.clear();
            poly.add(new Pt(box.minX(), box.minZ()));
            poly.add(new Pt(box.maxX() + 1, box.minZ()));
            poly.add(new Pt(box.maxX() + 1, box.maxZ() + 1));
            poly.add(new Pt(box.minX(), box.maxZ() + 1));
        }
        double radius = parse(circle.get("r", "5"), 5);
        Pt neckCenter = neck != null ? new Pt(neck.x(), neck.z())
                : new Pt((attacker.x() + circle.x()) / 2, (attacker.z() + circle.z()) / 2);
        double neckWidth = neck != null ? parse(neck.get("w", "3"), 3) : 3;
        return new CourtGeometry(poly, new Pt(circle.x(), circle.z()), radius, neckCenter, neckWidth,
                new Pt(attacker.x(), attacker.z()), new Pt(defender.x(), defender.z()));
    }

    private static double parse(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The i-th audience spot (wraps when there are more waiting contestants than spots). */
    Vec3 audienceSpot(int i) {
        if (audience.isEmpty()) {
            Vec3 c = center;
            return new Vec3(c.x + court.neckWidth() + 6 + (i % 8) * 1.2, floorY, c.z - 4 + (i / 8) * 1.2);
        }
        Marker m = audience.get(i % audience.size());
        double jitter = i >= audience.size() ? 0.4 * (i / audience.size()) : 0.0;
        return new Vec3(m.x() + jitter, m.y(), m.z());
    }

    float audienceYaw(int i) {
        return audience.isEmpty() ? 90f : audience.get(i % audience.size()).yaw();
    }
}

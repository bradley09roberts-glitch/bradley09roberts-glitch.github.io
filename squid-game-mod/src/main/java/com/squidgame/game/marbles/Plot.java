package com.squidgame.game.marbles;

import com.squidgame.build.Marker;
import com.squidgame.build.Region;
import com.squidgame.core.marbles.Side;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One pair spot of the arena (docs/ARENA_MARKERS.md): the two standing pads, the throw line, the bullseye, the table
 * where the exchange is shown and the box that contains the court. All positions come from the arena markers.
 */
record Plot(int k, Marker padA, Marker padB, Marker line, Marker target, @Nullable Marker table, @Nullable Region region) {
    Marker pad(Side s) {
        return s == Side.A ? padA : padB;
    }

    /** Standing position of a partner. */
    Vec3 padPos(Side s) {
        Marker m = pad(s);
        return new Vec3(m.x(), m.y(), m.z());
    }

    Vec3 bullseye() {
        return new Vec3(target.x(), target.y(), target.z());
    }

    /** The floor plane the marbles land on (the standing height of the target marker). */
    double floorY() {
        return target.y();
    }

    /** Horizontal unit vector from the throw line to the bullseye. */
    Vec3 laneDir() {
        double dx = target.x() - line.x(), dz = target.z() - line.z();
        double len = Math.hypot(dx, dz);
        return len < 1.0E-6 ? new Vec3(0, 0, 1) : new Vec3(dx / len, 0, dz / len);
    }

    /** Distance of a position past the throw line along the lane (negative = still behind it). */
    double alongLine(Vec3 p) {
        Vec3 d = laneDir();
        return (p.x - line.x()) * d.x + (p.z - line.z()) * d.z;
    }

    /** Yaw (degrees) that faces the bullseye from a position. */
    static float yawToward(Vec3 from, Vec3 to) {
        return (float) (Math.atan2(to.z - from.z, to.x - from.x) * 180.0 / Math.PI) - 90.0f;
    }

    /** Where the table text hangs: above the table between the partners (or between the pads without a table). */
    Vec3 tableTextPos() {
        if (table != null) {
            return new Vec3(table.x(), table.y() + 1.95, table.z());
        }
        return new Vec3((padA.x() + padB.x()) / 2, padA.y() + 2.4, (padA.z() + padB.z()) / 2);
    }

    boolean near(Vec3 p, double margin) {
        if (region == null) {
            return p.distanceToSqr(padPos(Side.A)) < 20 * 20;
        }
        return p.x >= region.minX() - margin && p.x < region.maxX() + 1 + margin
                && p.z >= region.minZ() - margin && p.z < region.maxZ() + 1 + margin;
    }
}

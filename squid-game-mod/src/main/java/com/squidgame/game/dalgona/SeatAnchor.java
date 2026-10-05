package com.squidgame.game.dalgona;

import com.squidgame.build.Marker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Where one contestant sits, derived from the arena's {@code dalgona.seat} / {@code dalgona.station} markers and the
 * blocks around them. Two marker conventions are understood without any extra data:
 * <ul>
 *   <li>the marker is a free floor cell with a low bench directly behind it (the real hall: contestants sit on the
 *       bench slab, 0.3 behind the marker);</li>
 *   <li>the marker is the standing position on the seat itself (the contract in ARENA_MARKERS.md).</li>
 * </ul>
 *
 * @param npcPos     body origin of a seated NPC (the sit animation rests its thighs 0.23 above that point)
 * @param humanSeat  where the invisible seat a human rides on is placed (rider's seat point = the seat surface)
 * @param floorPos   a floor position in front of the seat (where an NPC that was shot falls down)
 * @param tableTop   centre of the table surface in front of the seat
 * @param stationBlock the {@code dalgona_station} block in front of the seat, or null
 */
record SeatAnchor(Vec3 npcPos, Vec3 humanSeat, Vec3 floorPos, float yaw, Vec3 tableTop, @Nullable BlockPos stationBlock) {
    /** How far below the surface a seated NPC's body origin is. */
    static final double NPC_SIT_DROP = 0.23;
    /** How far in front of the middle of the bench the hips sit (leaning towards the table). */
    private static final double LEAN = 0.15;

    static SeatAnchor resolve(ServerLevel level, Marker seat, @Nullable Marker station, double xJitter) {
        double yawRad = Math.toRadians(seat.yaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double top = seat.y();
        double sx = seat.x();
        double sz = seat.z();
        for (double back : new double[]{0.5, 0.9, 1.3}) {
            BlockPos bp = BlockPos.containing(seat.x() - fx * back, seat.y() + 0.01, seat.z() - fz * back);
            BlockState st = level.getBlockState(bp);
            VoxelShape shape = st.getCollisionShape(level, bp);
            if (shape.isEmpty()) {
                continue;
            }
            double surface = bp.getY() + shape.max(Direction.Axis.Y);
            double rise = surface - seat.y();
            if (rise >= 0.2 && rise <= 0.8 && free(level, bp.above()) && free(level, bp.above(2))) {
                top = surface;
                sx = bp.getX() + 0.5 + fx * LEAN;
                sz = bp.getZ() + 0.5 + fz * LEAN;
                break;
            }
        }
        Vec3 table;
        BlockPos block = null;
        if (station != null) {
            table = new Vec3(station.x(), station.y() - 0.5, station.z());
            block = BlockPos.containing(station.x(), station.y() - 0.5, station.z());
        } else {
            table = new Vec3(seat.x() + fx * 1.2, seat.y() + 0.5, seat.z() + fz * 1.2);
        }
        return new SeatAnchor(new Vec3(sx + xJitter, top - NPC_SIT_DROP, sz), new Vec3(sx + xJitter, top, sz),
                new Vec3(seat.x() + xJitter, seat.y(), seat.z()), seat.yaw(), table, block);
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}

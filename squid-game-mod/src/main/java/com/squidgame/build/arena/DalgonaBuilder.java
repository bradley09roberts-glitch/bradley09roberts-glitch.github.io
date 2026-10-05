package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.dalgona.Exterior;
import com.squidgame.build.arena.dalgona.Floor;
import com.squidgame.build.arena.dalgona.Front;
import com.squidgame.build.arena.dalgona.Geo;
import com.squidgame.build.arena.dalgona.Guards;
import com.squidgame.build.arena.dalgona.LightPass;
import com.squidgame.build.arena.dalgona.Props;
import com.squidgame.build.arena.dalgona.Rear;
import com.squidgame.build.arena.dalgona.Roof;
import com.squidgame.build.arena.dalgona.Seating;
import com.squidgame.build.arena.dalgona.Shell;
import com.squidgame.build.arena.dalgona.Stage;
import com.squidgame.build.arena.dalgona.Walls;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dalgona (honeycomb candy) hall: a huge, warm, old-school classroom hall (71 x 90, up to 22 high) with a
 * shallow gabled roof on curved timber ribs and honey-glass pendant lamps, cream plaster walls over a dark
 * wainscot with tall arched windows and four candy-shape murals (circle, triangle, star, umbrella), 8 rows of
 * 4 low benches / desks with one {@code squidgame:dalgona_station} per seat (160 seats), a raised teacher's
 * podium with a huge chalkboard and clock at the north end, and a gallery balcony with a round VIP loge along the
 * south (rear) wall. The shared waiting room prefab sits behind the rear wall; its gate opens into the hall.
 *
 * <p>Everything faces north (yaw 180) towards the board. Markers, besides the common ones:
 * <ul>
 *   <li>{@code dalgona.seat} (160, {@code slot=N}, row-major from the front row, west to east): a free floor cell
 *       (flat floor, 2 free blocks above) between the 0.5 high bench slab directly behind it and the station in front;
 *       the marker sits 0.7 into its cell, 0.3 in front of the bench, yaw 180. Aisles: 9 (centre), 5, 4 (sides), and
 *       4 between the rows; everything is reachable from the gate;</li>
 *   <li>{@code dalgona.station} (160, {@code slot=N}): the {@code squidgame:dalgona_station[facing=north]} block
 *       placed at floor level directly in front of seat N (marker y = block y + 1);</li>
 *   <li>{@code dalgona.front} (on the stage in front of the teacher's desk, yaw 0), {@code dalgona.board}
 *       (centre of the chalkboard, on its face, yaw 0; my own heading text sits in its top strip);</li>
 *   <li>regions {@code arena.bounds} (hall interior incl. the gate doorway row), {@code waiting.bounds} (prefab),
 *       {@code dalgona.seating}; 19 hall {@code guard.post}s (10 triangle on the gallery and side walls, 6 circle at
 *       gate / side doors / stair feet, 3 square on the stage; the prefab adds 5, total 24) and two optional
 *       {@code guard.patrol} routes ({@code floor}, {@code gallery});</li>
 *   <li>{@code arena.spectator} on the gallery loge, {@code arena.exit} in the front aisle below the podium.</li>
 * </ul>
 *
 * <p>Lighting: visible fixtures (lanterns, pendants, glowing window panels) plus a few hundred invisible
 * {@code minecraft:light} blocks placed by {@link LightPass} so every walkable cell is at least level 12.
 */
public final class DalgonaBuilder implements ArenaBuilder {
    @Override
    public ArenaId id() {
        return ArenaId.DALGONA;
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED);
        l.add("dalgona.seat");
        l.add("dalgona.station");
        l.add("dalgona.front");
        l.add("dalgona.board");
        l.add(CommonMarkers.GUARD_POST);
        return l;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        l.add("dalgona.seating");
        return l;
    }

    @Override
    public int version() {
        return 2;       // 1 was the placeholder structure
    }

    @Override
    public void build(BuildContext c) {
        Shell.build(c);
        Floor.build(c);
        Walls.build(c);
        Roof.build(c);
        Front.build(c);
        Stage.build(c);
        Seating.build(c);
        Rear.build(c);
        Rear.markers(c);
        Props.build(c);
        Guards.build(c);
        Exterior.build(c);
        c.marker("dalgona.front", 0.5, 2.0, -84.5, 0f);
        c.marker("dalgona.board", 0.5, Front.BOARD_CENTER_Y, -90.95, 0f);
        c.marker(CommonMarkers.EXIT, 0.5, 1.0, -81.5, 180f);
        c.at(0, 0, 0, 2, () -> WaitingRoomPrefab.build(c, WaitingRoomPrefab.Spec.of("DALGONA")));
        // the prefab's two wall-worker guard posts land on its own bench slabs when it is turned half a turn
        // (their z has no +0.5); free the two cells so those posts stand on open floor
        c.air(-19, 1, 12);
        c.air(19, 1, 12);
        LightPass.run(c);
        // the whole hall interior including the gate doorway row (z = -1) so arriving contestants are never outside
        c.region(CommonMarkers.REGION_BOUNDS, -Geo.HALF_W, 0, Geo.Z_FRONT, Geo.HALF_W, 23, Geo.REAR_WALL_Z);
    }
}

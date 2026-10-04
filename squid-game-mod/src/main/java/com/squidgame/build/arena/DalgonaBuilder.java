package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.dalgona.Floor;
import com.squidgame.build.arena.dalgona.Front;
import com.squidgame.build.arena.dalgona.Geo;
import com.squidgame.build.arena.dalgona.Roof;
import com.squidgame.build.arena.dalgona.Seating;
import com.squidgame.build.arena.dalgona.Shell;
import com.squidgame.build.arena.dalgona.Stage;
import com.squidgame.build.arena.dalgona.Walls;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dalgona (honeycomb candy) hall: a huge, warm, old-school classroom hall (71 x 90 x 22) with a
 * gabled timber roof, plastered and panelled walls carrying four murals, rows of long wooden benches
 * and low desks with one dalgona station per seat, a raised teacher's podium with a huge chalkboard
 * at the north end, a gallery balcony along the south (rear) wall and the shared waiting room behind it.
 *
 * <p>Everything faces north (yaw 180). Markers, besides the common ones:
 * <ul>
 *   <li>{@code dalgona.seat} (160, {@code slot=N}): floor-level standing cell between bench and desk,
 *       the contestant sits on the 0.5 high bench directly behind it, yaw 180;</li>
 *   <li>{@code dalgona.station} (160, {@code slot=N}): the {@code squidgame:dalgona_station} block placed in
 *       front of seat N (marker y = block y + 1);</li>
 *   <li>{@code dalgona.front}, {@code dalgona.board}; regions {@code dalgona.seating}, {@code arena.bounds},
 *       {@code waiting.bounds}; 19 hall {@code guard.post}s (the prefab adds 5 more).</li>
 * </ul>
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
        return 1;
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
        c.marker("dalgona.front", 0.5, 2.0, -84.5, 0f);
        c.marker("dalgona.board", 0.5, Front.BOARD_CENTER_Y, -90.95, 0f);
        c.marker(CommonMarkers.EXIT, 0.5, 1.0, -81.5, 180f);
        c.at(0, 0, 0, 2, () -> WaitingRoomPrefab.build(c, WaitingRoomPrefab.Spec.of("DALGONA")));
        c.region(CommonMarkers.REGION_BOUNDS, -Geo.HALF_W, 0, Geo.Z_FRONT, Geo.HALF_W, 23, Geo.Z_REAR);
    }
}

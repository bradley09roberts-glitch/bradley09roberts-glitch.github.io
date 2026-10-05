package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.stairs.Builder;

import java.util.List;

/**
 * The Stairway Maze Hall: a colossal pastel hall (interior x[-45,45] z[-170,-82] y[0,72], floor y=-1) full of crossing
 * staircases, bridges, arcaded pavilions and landings, with one wide walkable main route (pink treads, 5-wide lanes with
 * lanterns, arches and railings) from the entrance in the south wall (z=-81, opening x[-3,3] y[0,5]) over about 150
 * cells up to the gate terrace at y=60 along the north wall.
 *
 * <p>Markers: {@code stairs.path} (21 waypoints, {@code i=0..20}, every 7 cells of the route, from just inside the
 * entrance to the middle of the terrace) and {@code guard.post} (13: six armed triangles along the terrace edge, two
 * squares, two circles, one triangle on each of three upper landings). Regions: {@code gate.red_light},
 * {@code gate.dalgona}, {@code gate.tug_of_war}, {@code gate.marbles}, {@code gate.glass_bridge}, {@code gate.final}:
 * 5 x 5 x 1 boxes in the north-wall plane (z=-171) at x = -37, -22, -7, 7, 22, 37, y[60,64], each the first layer of a
 * 3-deep alcove closed by a black double door.
 *
 * <p>The tangle is generated deterministically from a private fork of the context's random generator (so it does not
 * depend on what the other hub parts drew). Verification tools: {@code stairs/StairCheck} (half-block walking model,
 * light solver, enclosure test).
 */
public final class StairwayPart implements HubPart {
    @Override
    public void build(BuildContext c) {
        Builder.build(c);
    }

    @Override
    public int version() {
        return 2;
    }

    @Override
    public List<String> markers() {
        return List.of("stairs.path", "guard.post");
    }

    @Override
    public List<String> regions() {
        return List.of("gate.red_light", "gate.dalgona", "gate.tug_of_war", "gate.marbles", "gate.glass_bridge",
                "gate.final");
    }
}

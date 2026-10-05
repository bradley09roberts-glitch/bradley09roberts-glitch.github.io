package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.stairs.Builder;

import java.util.List;

/**
 * The Stairway Maze Hall: a colossal pastel hall (interior x[-45,45] z[-170,-82] y[0,72]) full of crossing staircases,
 * bridges, arches and landings, with one wide walkable main route from the entrance (south wall, z=-81) up to the
 * top terrace at y=60 along the north wall, where six gates lead on to the six games.
 */
public final class StairwayPart implements HubPart {
    @Override
    public void build(BuildContext c) {
        Builder.build(c);
    }

    @Override
    public int version() {
        return 1;
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

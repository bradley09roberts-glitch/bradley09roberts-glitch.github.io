package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.corridor.Compound;

import java.util.List;

/**
 * The guard corridors of the hub: a pink / white / black guard compound between the dormitory (south) and the stairway
 * hall (north). See {@link Compound} for the full description and marker list.
 *
 * <p>Interfaces (hub frame, standing level y = 0, floor blocks at y = -1): the dorm exit door opens into the doorway cell
 * z = -35 (x[-3,3], y[0,5]) of the checkpoint hall; the compound ends with the doorway cell z = -80 (x[-3,3], y[0,5])
 * directly in front of the stairway hall's entrance plane z = -81.
 */
public final class CorridorPart implements HubPart {
    @Override
    public void build(BuildContext c) {
        Compound.build(c);
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<String> markers() {
        return List.of("guard.post", "guard.patrol");
    }

    @Override
    public List<String> regions() {
        return List.of("corridor.bounds");
    }
}

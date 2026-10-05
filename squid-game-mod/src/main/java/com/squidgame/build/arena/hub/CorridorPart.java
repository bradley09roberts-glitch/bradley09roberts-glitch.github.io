package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.corridor.Compound;

import java.util.List;

/**
 * The guard corridors of the hub: a pink / white / black guard compound between the dormitory (south) and the stairway
 * hall (north) - a ring of 7-wide corridors (C1) round a block of guard rooms, with the checkpoint hall behind the
 * dorm door, a junction hall, four corner halls, a pastel gradient corridor, a rainbow vestibule in front of the
 * stairway doorway and seven side rooms (canteen, infirmary, barracks, monitoring room, armory, manager's office and
 * the coffin store). See {@link Compound}.
 *
 * <p>Interfaces (hub frame, standing level y = 0, floor blocks at y = -1): the dorm exit door (x[-3,3] y[0,5],
 * carved through z = -34..-33 by the dormitory) opens into the doorway cell z = -35 of the checkpoint hall; the
 * compound ends with the doorway cell z = -80 (x[-3,3], y[0,5]) directly in front of the stairway hall's entrance
 * plane z = -81. Everything lies inside x[-30,30] z[-80,-35].
 *
 * <p>Markers: guard.post (23, rank=triangle|circle|square), guard.patrol (route=a|b|c with i = order; a = dorm door ->
 * east arm -> stairway doorway, b = west arm, c = staff wing / barracks / monitoring loop). Region: corridor.bounds.
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

package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.dorm.Dormitory;

import java.util.List;

/**
 * The Dormitory of the hub: the huge bare hall of the tournament with its towering steel bunk-bed stacks, the giant
 * transparent piggy bank hanging over the centre, the registration plinth, the ceremonial podium, the exit door in the
 * north wall and the black-glass wall + catwalk stair towards the control room in the east wall.
 *
 * <p>Hub frame: standing level y = 0 (floor blocks at y = -1), interior x[-40,40] z[-32,32] y[0,36], walls x = +-41..42
 * and z = +-33..34, roof from y = 37. See {@link Dormitory} for the full description.
 *
 * <p>Markers: dorm.player_spawn, dorm.registration_terminal, dorm.npc_spawn (slot=N), dorm.exit_door, hub.podium,
 * prize.pig, prize.counter, guard.post. Regions: prize.fill (stacked empty boxes inside the pig, bottom to top).
 */
public final class DormitoryPart implements HubPart {
    @Override
    public void build(BuildContext c) {
        Dormitory.build(c);
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<String> markers() {
        return List.of("dorm.player_spawn", "dorm.registration_terminal", "dorm.npc_spawn", "dorm.exit_door",
                "hub.podium", "prize.pig", "prize.counter", "guard.post");
    }

    @Override
    public List<String> regions() {
        return List.of("prize.fill");
    }
}

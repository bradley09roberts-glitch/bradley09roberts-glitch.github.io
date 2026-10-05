package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.control.Control;

import java.util.List;

/**
 * The Front Man's / VIP control room east of the dormitory: a dark high-tech hall with a floor-to-ceiling wall of
 * animated monitors inside a glowing pink halo, curved console desks, a raised three-tier commander's dais with a
 * throne looking west through the black windows over the dormitory, a holographic map table, server racks, a
 * mezzanine VIP office and an entrance lobby behind the doorway to the dormitory's catwalk. See {@link Control}.
 *
 * <p>Interface (hub frame, standing level y = 12): interior x[44,84] z[-18,18] y[12,32]; the west wall x = 43 is
 * shared with the dormitory, with window openings z[-16,-3] and z[3,16] (y 14..30) glazed here with black glass and
 * the doorway z[-2,2] y[12,15] left open (glowing threshold, lobby behind it).
 *
 * <p>Markers: hub.spectator (gallery in front of the north window, yaw 90 = looking west), control.monitor (one per
 * monitor cluster, not a standing position), control.commander (the Front Man's place in front of the throne),
 * guard.post (7: lobby, dais, console floor, service aisle, mezzanine).
 */
public final class ControlRoomPart implements HubPart {
    @Override
    public void build(BuildContext c) {
        Control.build(c);
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<String> markers() {
        return List.of("hub.spectator", "control.monitor", "control.commander", "guard.post");
    }
}

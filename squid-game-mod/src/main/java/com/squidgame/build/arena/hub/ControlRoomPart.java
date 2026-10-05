package com.squidgame.build.arena.hub;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.hub.control.Control;

import java.util.List;

/**
 * The Front Man's / VIP control room east of the dormitory: a dark high-tech hall with a wall of animated monitors,
 * curved console desks, a raised commander's dais with a throne looking through the black windows over the
 * dormitory, a holographic map table, server racks and a mezzanine office. See {@link Control}.
 *
 * <p>Interface (hub frame, standing level y = 12): interior x[44,84] z[-18,18] y[12,32]; west wall x = 43 shared with
 * the dormitory with window openings z[-16,-3] and z[3,16] (y 14..30) glazed with black glass here, and the
 * doorway z[-2,2] y[12,15] left open.
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
        return List.of("hub.spectator", "guard.post");
    }
}

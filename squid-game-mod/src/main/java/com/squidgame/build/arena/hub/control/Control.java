package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/**
 * Orchestrates the Front Man's control room east of the dormitory (interior x[44,84] z[-18,18] y[12,32]).
 *
 * <p>Markers: {@code hub.spectator} (viewing gallery in front of the north window, yaw 90 = looking west over the
 * dormitory), {@code control.monitor} (one per monitor cluster, optional), {@code control.commander} (the Front
 * Man's place in front of the throne), {@code guard.post} (lobby, dais, consoles, mezzanine).
 */
public final class Control {
    private Control() {
    }

    public static void build(BuildContext c) {
        Shell.build(c);
        Walls.build(c);
        Floor.build(c);
        DisplayWall.build(c);
        Dais.build(c);
        Consoles.build(c);
        HoloTable.build(c);
        Lobby.build(c);
        Racks.build(c);
        Mezzanine.build(c);
        Galleries.build(c);
        Marks.build(c);
    }
}

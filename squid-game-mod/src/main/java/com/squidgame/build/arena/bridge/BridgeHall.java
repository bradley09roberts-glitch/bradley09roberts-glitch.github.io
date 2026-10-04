package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;

/**
 * Orchestrates the Glass Bridge hall: shell and pit, waiting room (prefab) behind the start platform, platforms, deck,
 * and everything that dresses the hall. Order matters: later steps may overwrite earlier blocks.
 */
public final class BridgeHall {
    private BridgeHall() {
    }

    public static void build(BuildContext c) {
        Hall.build(c);
        // the shared waiting room, its gate wall is the hall's back wall (z = -8); it carves into the back wall mass
        c.at(0, Geo.DECK, Geo.ROOM_Z, 0, () -> WaitingRoomPrefab.build(c,
                new WaitingRoomPrefab.Spec(41, 22, 9, "GLASS BRIDGE", "ONLY ONE PANEL IN TWO WILL HOLD YOU", 144)));
        Platforms.build(c);
        Deck.build(c);
        Lounge.build(c);
        markers(c);
    }

    private static void markers(BuildContext c) {
        Spots.panels(c);
        Spots.queue(c);
        Spots.finish(c);
        // gate across the front of the start platform: door plane z = 8, centre-bottom of the opening
        c.marker("bridge.gate", 0.5, Geo.STAND, Geo.GATE_Z + 0.5, 0f, "w=7,h=5");
        c.marker("bridge.pit_floor", 0.5, Geo.PIT + 1, 36.5, 0f);
        // regions
        c.region("bridge.start", Geo.PX0, Geo.DECK, Geo.START_Z0, Geo.PX1, Geo.RING - 1, Geo.START_Z1);
        c.region("bridge.finish", Geo.PX0, Geo.DECK, Geo.END_Z0, Geo.PX1, Geo.RING - 1, Geo.END_Z1);
        c.region("bridge.pit", Geo.X0, Geo.PIT + 1, Geo.Z0, Geo.X1, Geo.DECK - 1, Geo.Z1);
        c.region("bridge.deck", -4, Geo.DECK, 9, 3, Geo.DECK + 3, 65);
        c.region("arena.bounds", Geo.WX0, Geo.FOUND, -35, Geo.WX1, Geo.ROOF_TOP, Geo.LZ1 + 2);
        c.marker("arena.spectator", 0.5, 71, -4.5, 0f);
        c.marker("arena.exit", 0.5, Geo.STAND, 95.5, 180f);
        c.marker(CommonMarkers_GUARD(), 0.5, Geo.STAND, 4.5, 0f, "rank=triangle");
    }

    private static String CommonMarkers_GUARD() {
        return com.squidgame.build.CommonMarkers.GUARD_POST;
    }
}

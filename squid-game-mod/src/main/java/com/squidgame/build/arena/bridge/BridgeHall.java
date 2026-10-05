package com.squidgame.build.arena.bridge;

import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
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
        // the shared waiting room: its gate wall is the hall's back wall (z = -8); it carves into the back wall mass
        c.at(0, Geo.DECK, Geo.ROOM_Z, 0, () -> WaitingRoomPrefab.build(c,
                new WaitingRoomPrefab.Spec(41, 22, 9, "GLASS BRIDGE", "ONLY ONE PANEL IN TWO WILL HOLD YOU", 144)));
        skinRoomWall(c);
        Platforms.build(c);
        Floor.build(c);
        Deck.build(c);
        Pit.build(c);
        Gantries.build(c);
        Roof.build(c);
        Lounge.build(c);
        markers(c);
    }

    /** Re-skins the white concrete of the room's gate wall (the hall's back wall) into dark plates. */
    private static void skinRoomWall(BuildContext c) {
        c.pattern(-22, Geo.DECK + 1, Geo.ROOM_Z, 22, Geo.DECK + 11, Geo.ROOM_Z,
                (x, y, z) -> "minecraft:white_concrete".equals(c.get(x, y, z)) ? Hall.plate(x, y) : null);
    }

    private static void markers(BuildContext c) {
        Spots.panels(c);
        Spots.queue(c);
        Spots.finish(c);
        Guards.build(c);
        // gate across the front of the start platform: door plane z = 8, centre-bottom of the opening
        c.marker("bridge.gate", 0.5, Geo.STAND, Geo.GATE_Z + 0.5, 0f, "w=7,h=5");
        c.marker("bridge.pit_floor", 0.5, Geo.PIT + 1, 36.5, 0f);
        // decorative: the centre spotlight rigs over the deck
        for (int zc : Roof.TRUSS_Z) {
            if (zc != Geo.ZC) {
                c.marker("bridge.hanging_cage", 0.0, 62.5, zc + 0.5, 0f);
            }
        }
        // gallery on the back wall (spectators) and the lounge (survivors)
        c.marker("arena.spectator", 0.5, WallArt.BALCONY_Y + 1, -4.5, 0f);
        c.marker("arena.exit", 0.5, Geo.STAND, 95.5, 180f);
        // regions
        c.region("bridge.start", Geo.PX0, Geo.DECK, Geo.START_Z0, Geo.PX1, Geo.RING - 1, Geo.START_Z1);
        c.region("bridge.finish", Geo.PX0, Geo.DECK, Geo.END_Z0, Geo.PX1, Geo.RING - 1, Geo.END_Z1);
        c.region("bridge.pit", Geo.X0, Geo.PIT + 1, Geo.Z0, Geo.X1, Geo.DECK - 1, Geo.Z1);
        c.region("bridge.deck", -4, Geo.DECK, 9, 3, Geo.DECK + 3, 65);
        c.region(CommonMarkers.REGION_BOUNDS, Geo.WX0, Geo.FOUND, -35, Geo.WX1, Geo.ROOF_TOP, Geo.LZ1 + 2);
    }
}

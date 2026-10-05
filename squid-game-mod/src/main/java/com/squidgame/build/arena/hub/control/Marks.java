package com.squidgame.build.arena.hub.control;

import com.squidgame.build.BuildContext;

/**
 * Markers of the control room (hub frame, standing level of the main floor y = 12):
 * <ul>
 *   <li>{@code hub.spectator}: on the viewing gallery in front of the north window (standing level 14), yaw 90 = looking
 *       west through the glass over the dormitory;</li>
 *   <li>{@code control.commander}: the Front Man's place on the top tier of the dais in front of the throne (yaw 90);</li>
 *   <li>{@code control.monitor}: one per monitor cluster (not standing positions), emitted by {@link DisplayWall};</li>
 *   <li>{@code guard.post}: lobby (2 armed), dais (manager), console floor (worker), service aisle (worker), stair head
 *       (armed) and the mezzanine office (manager).</li>
 * </ul>
 */
final class Marks {
    private Marks() {
    }

    private static void post(BuildContext c, double x, double y, double z, float yaw, String rank) {
        c.marker("guard.post", x, y, z, yaw, "rank=" + rank);
    }

    static void build(BuildContext c) {
        // spectator viewpoint: standing on the gallery in front of the north window, looking west over the dormitory
        c.marker("hub.spectator", 44.5, 14.0, -8.5, 90f);
        // lobby: armed guards flanking the scanner arch
        post(c, 45.5, 12.0, -2.5, 0f, "triangle");
        post(c, 45.5, 12.0, 3.5, 180f, "triangle");
        // dais: the manager beside the throne, facing the room
        post(c, 82.5, 15.0, -2.5, 90f, "square");
        // console floor and service aisle: workers
        post(c, 66.5, 12.0, 0.5, 90f, "circle");
        post(c, 64.5, 12.0, 15.5, 0f, "circle");
        // stair head of the mezzanine and the VIP office
        post(c, 74.5, 20.0, -16.5, 0f, "triangle");
        post(c, 65.5, 20.0, -16.5, 180f, "square");
    }
}

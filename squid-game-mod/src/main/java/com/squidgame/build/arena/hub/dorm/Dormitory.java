package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * Orchestrates the dormitory passes (shell, floor, bunk towers, pig, podium, registration, exit door, east gallery,
 * props, lighting, guards). Every pass is deterministic and independent of the others' internal order except where
 * noted: the shell comes first (it carves the hall), lights and guards come last.
 */
public final class Dormitory {
    private Dormitory() {
    }

    public static void build(BuildContext c) {
        Shell.build(c);
        Floor.build(c);
        Bunks.build(c);
        Pig.build(c);
        Podium.build(c);
        Registration.build(c);
        ExitDoor.build(c);
        EastWall.build(c);
        Gallery.build(c);
        c.at(0, 0, 0, 2, () -> Gallery.build(c));
        Murals.build(c);
        Props.build(c);
        Lights.build(c);
        Guards.build(c);
        Bunks.emitSpawns(c);
    }
}

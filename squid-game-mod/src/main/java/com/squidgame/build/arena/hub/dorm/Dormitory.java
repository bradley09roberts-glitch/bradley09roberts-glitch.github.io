package com.squidgame.build.arena.hub.dorm;

import com.squidgame.build.BuildContext;

/**
 * The Dormitory of the hub, pass by pass (hub frame: standing level y = 0, floor blocks at y = -1):
 *
 * <ol>
 *   <li>{@link Shell}: foundation, banded walls with pilasters, ceiling with light panels, roof trusses (carves the hall)</li>
 *   <li>{@link Floor}: worn concrete, polished avenue, plaza rings, bunk-zone outlines, arrival pad, hazard threshold</li>
 *   <li>{@link Bunks} / {@link BunkTower}: twelve six-tier steel bunk towers (4 columns x 3 rows) with beds, ladders,
 *       lanterns, letter plates and bunk numbers</li>
 *   <li>{@link Pig}: the giant transparent piggy bank hanging over the plaza, its prize.fill boxes, the prize board</li>
 *   <li>{@link Podium}, {@link Registration}, {@link ExitDoor}: the three focal points along the central avenue</li>
 *   <li>{@link EastWall} + {@link Gallery}: black windows and door towards the control room, the catwalk with its stair
 *       (built twice: the west gallery is the east one turned half round, without a door)</li>
 *   <li>{@link Murals}, {@link Props}: neon symbols, cameras, speakers, vents, conduits, pendants, ducts</li>
 *   <li>{@link Lights}: floor panel-light lattice and the remaining fixtures; the hall is 37 high so every walkable
 *       surface is lit from low fixtures (verified with a block-light simulation: every reachable standing cell >= 10)</li>
 *   <li>{@link Guards}, then {@link Bunks#emitSpawns}: markers, validated against what was actually built</li>
 * </ol>
 *
 * <p>Every pass is deterministic (stateless hashed noise, no shared RNG) and independent of the others' internal order
 * except where noted above.
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

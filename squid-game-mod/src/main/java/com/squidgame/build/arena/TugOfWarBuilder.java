package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.tug.HallBuilder;

import java.util.List;

/**
 * Tug of War arena: a terrifying industrial hall (150 x 71 interior, 105 blocks from pit floor to ceiling) with two
 * cantilevered steel decks facing each other across a 14 block gap over a 70 block deep pit.
 *
 * <p>Layout (local blocks, x east, hall mirror symmetric about the plane x = 0, i.e. block x maps to -x-1):
 * <pre>
 *  x -100..-76 waiting room (shared prefab, floor y 40, gate wall x=-76, door 7 x 5 onto the west plateau)
 *  x  -75..-61 west plateau = team A lobby (y 40, painted red zone, tug.waiting_a), steel portal onto deck A
 *  x  -60..-8  deck A (z -2..2, planks, red stripes, curbs + iron-bar rails at z=+-3), lattice legs to the pit floor
 *  x   -7..6   the gap; rope anchor portals (stripped dark oak posts, iron brackets, chain + lantern) at x=-7 / 7
 *  x    7..59  deck B (blue stripes), x 60..74 east plateau = team B lobby, x 75..98 survivors' exit lounge
 *  y  -30 pit floor (grating, glowing lanterns, mattresses, debris), 0 hall floor ring, 40 decks / plateaus / lower
 *     galleries (south one = tug.spare), 58 upper catwalks + cross gantry + control booth (arena.spectator), 75 roof
 *  z  pit -20..20, hall interior -35..35; perimeter walls 3 thick
 * </pre>
 *
 * <p>Markers beyond the contract: none (guard.post: 25 here + 5 from the waiting room prefab, tug.rope_center is
 * at x = 0.0, the exact middle of the gap). {@code tug.pit} spans y -30..39, i.e. everything below deck level.
 * The shared waiting room prefab hangs 8 blocks below its ceiling lights, so this builder adds a grid of invisible
 * {@code minecraft:light} blocks inside it to reach block light >= 10 on the spawn grid. The structure is deterministic
 * (all "random" scatter uses fixed local seeds) and about 1.5 million cells are written (0.69 million solid).
 */
public final class TugOfWarBuilder implements ArenaBuilder {
    @Override
    public ArenaId id() {
        return ArenaId.TUG_OF_WAR;
    }

    @Override
    public void build(BuildContext c) {
        HallBuilder.build(c);
    }

    @Override
    public List<String> requiredMarkers() {
        return HallBuilder.REQUIRED_MARKERS;
    }

    @Override
    public List<String> requiredRegions() {
        return HallBuilder.REQUIRED_REGIONS;
    }

    @Override
    public int version() {
        return 1;
    }
}

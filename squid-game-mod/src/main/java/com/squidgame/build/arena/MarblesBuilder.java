package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.marbles.MarblesVillage;

import java.util.ArrayList;
import java.util.List;

/**
 * Marbles: a night-time old Korean neighbourhood (cobbled alleys, tiled roofs, lanterns, laundry lines, doors with
 * numbers) built inside a giant set - a tall painted perimeter wall with a city / hill panorama and a star-sky roof with a
 * moon at y 48-50. Interior 131 x 111 blocks (x -65..65, z -55..55, floor y 0).
 *
 * <ul>
 *   <li><b>Pairing square</b> x[-15,15] z[-9,15] ({@code marbles.square}): glowing circle / triangle / square inlay, a roofed
 *       well, a big flowering tree, benches, a raised stage for the squares; the waiting-room prefab is dressed as a village
 *       hall on its north edge and its gate opens onto the square. 190+ {@code marbles.square_spawn} spots.</li>
 *   <li><b>64 pair spots</b> (courts, {@code marbles.plot} regions in k order, k = 0 nearest to the square): a walled yard
 *       7 x 12 blocks entered from an alley through a gate that carries the plaque k+1. Partners A / B stand on coloured pads
 *       3 blocks apart (x = -1.0 / +2.0 around the lane axis) one block behind the white throw line, the 5 x 5 gold / red /
 *       white / blue / white / red bullseye is 7 blocks beyond the line, a low table stands between the partners.</li>
 *   <li>Special plots: exit courtyard ({@code arena.exit}, behind the hall), lookout tower ({@code arena.spectator} on its
 *       deck), small shrine.</li>
 *   <li>30 {@code guard.post}s (triangles on the hall terrace, tower deck and flat-roof houses, circles at alley ends, squares
 *       on the stage; five of them come from the prefab), {@code guard.patrol} ring around the square.</li>
 * </ul>
 *
 * <p>Extra markers (non-standing ones carry {@code stand=0}): {@code marbles.pair_line}, {@code marbles.pair_target},
 * {@code marbles.table} per spot, {@code marbles.stage}, {@code marbles.well}, {@code marbles.tree}, {@code marbles.tower},
 * {@code marbles.shrine}, {@code marbles.exit_gather}. Lighting is guaranteed by a flood-fill pass (see {@code Lighting}).
 * The structure is assembled in {@link MarblesVillage}.
 */
public final class MarblesBuilder implements ArenaBuilder {
    @Override
    public ArenaId id() {
        return ArenaId.MARBLES;
    }

    @Override
    public void build(BuildContext c) {
        MarblesVillage.build(c);
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED);
        l.addAll(List.of("marbles.square_spawn", "marbles.pair_a", "marbles.pair_b", "marbles.pair_target",
                "marbles.pair_line", "marbles.table", CommonMarkers.GUARD_POST));
        return l;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        l.addAll(List.of("marbles.square", "marbles.plot"));
        return l;
    }

    @Override
    public int version() {
        return 1;
    }
}

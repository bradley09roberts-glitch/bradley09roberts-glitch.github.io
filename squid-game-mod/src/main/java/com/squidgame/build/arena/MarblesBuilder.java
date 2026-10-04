package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.marbles.MarblesVillage;

import java.util.ArrayList;
import java.util.List;

/**
 * Marbles: a night-time old Korean neighbourhood (alleys, tiled roofs, lanterns, laundry lines) built inside a giant
 * set - a tall painted perimeter wall and a star-sky roof. Pairing square in the middle, 64 pair spots (courts) around
 * the alleys. The marker contract is in docs/ARENA_MARKERS.md; the extra markers this builder emits are documented in
 * {@link MarblesVillage}.
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

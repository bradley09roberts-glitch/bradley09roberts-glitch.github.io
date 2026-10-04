package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.arena.tug.HallBuilder;

import java.util.List;

/**
 * Tug of War arena: a terrifying industrial hall (about 150 x 71 x 105 blocks) with two cantilevered steel decks
 * facing each other over a 70 block deep pit. See {@link HallBuilder} for the layout.
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

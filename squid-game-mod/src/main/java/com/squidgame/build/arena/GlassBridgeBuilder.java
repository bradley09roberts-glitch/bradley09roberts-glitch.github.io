package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.bridge.BridgeHall;

import java.util.ArrayList;
import java.util.List;

/**
 * Glass Bridge arena: a vast, dark industrial hall (about 96 x 143 blocks, roof at y = 90) with a start platform, a
 * two-lane deck of 18 rows of 2x2 glass panels at y = 40 over a 70 block deep pit, an end platform with a green exit
 * door and a finish lounge behind it. See {@link BridgeHall} for the layout; markers are documented in
 * docs/ARENA_MARKERS.md.
 *
 * <p>Extra (optional) markers emitted: {@code bridge.hanging_cage}, {@code guard.patrol} (route {@code ring}).
 */
public final class GlassBridgeBuilder implements ArenaBuilder {
    @Override
    public ArenaId id() {
        return ArenaId.GLASS_BRIDGE;
    }

    @Override
    public void build(BuildContext c) {
        BridgeHall.build(c);
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED);
        out.add(CommonMarkers.GUARD_POST);
        out.addAll(List.of("bridge.panel", "bridge.queue", "bridge.gate", "bridge.finish_spawn", "bridge.pit_floor"));
        return out;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        out.addAll(List.of("bridge.start", "bridge.finish", "bridge.pit", "bridge.deck"));
        return out;
    }

    @Override
    public int version() {
        return 1;
    }
}

package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.finale.FinalPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * The Final Squid Game arena: the childhood schoolyard at dusk where the squid game is fought. See
 * {@link FinalPlan} for the construction steps and docs/ARENA_MARKERS.md for the marker contract.
 */
public final class FinalBuilder implements ArenaBuilder {
    @Override
    public ArenaId id() {
        return ArenaId.FINAL;
    }

    @Override
    public void build(BuildContext ctx) {
        FinalPlan.build(ctx);
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED);
        out.addAll(List.of(CommonMarkers.GUARD_POST, "final.boundary", "final.circle", "final.triangle", "final.neck",
                "final.attacker_spawn", "final.defender_spawn", "final.audience"));
        return out;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> out = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        out.addAll(List.of("final.court", "final.attack_zone", "final.defence_zone"));
        return out;
    }

    @Override
    public int version() {
        return 1;
    }
}

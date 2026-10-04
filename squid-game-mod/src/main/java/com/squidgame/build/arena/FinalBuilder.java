package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.finale.FinalPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * The Final Squid Game arena: the childhood schoolyard at dusk where the squid game is fought (pure Java, no Minecraft
 * classes; helpers in {@code com.squidgame.build.arena.finale}).
 *
 * <p>Layout (local frame, floor block y=0, +Z south): a 101 x 121 yard of pale sand (x -50..50, z -60..60) inside a low
 * whitewashed rim wall, enclosed by a 45 high painted sunset wall. The squid court (1 block white concrete lines,
 * 15 x 50) lies in the middle: head circle r=5 at z=-20 (-Z end, golden target ring and 3x3 gold pad), the triangle body
 * (14 between line centres, 20 tall) entered through the 3 wide open junction of the head, the 3 wide neck and the
 * 14 x 14 square (+Z end). The school building with its portico, clock and assembly podium closes the north end; the
 * playground (swings, slides, see-saws, jungle gyms, monkey bars, merry-go-round, tyres, sandbox, the big old tree) fills
 * the strips beside the court; four floodlight towers stand in the corners; the grandstand with a broadcast booth lies
 * behind the east rim wall (aisle with steps over the wall into the yard); the shared waiting room (prefab, raised two
 * blocks) is behind the south wall, its gate leading over a half block ramp to the sand. The court and a 6 block belt
 * around it are dead flat and empty; the route from the ramp to the square (|x| &lt;= 12) is clear.
 *
 * <p>Markers beyond the common/required ones (documented here as the contract asks):
 * <ul>
 *   <li>{@code final.podium} - winner's spot on the assembly podium in front of the school (x=0.5, y=3.0, z=-43.5);</li>
 *   <li>{@code guard.patrol} - route {@code a} (12 points) looping around the court inside the clear belt;</li>
 *   <li>{@code final.boundary} - 34 clockwise vertices of the OUTER outline of the painted squid (22 on the head circle,
 *       r=5.5 about (0.5,-19.5)), {@code data="i=N,stand=0"}; the head is joined to the triangle by a 3 wide gap, so the
 *       outline is one simple polygon;</li>
 *   <li>{@code arena.spectator} - deck of the judges' tower beside the court (a flying viewpoint), {@code arena.exit} -
 *       front of the grandstand terrace.</li>
 * </ul>
 * Lighting: the yard, court, gallery and ramp are open to the sky (no roof anywhere over the court), so they are lit by
 * the dimension's fixed noon sky light; all covered or shaded spots (school portico, judges' deck, under the big tree)
 * carry lanterns, and the towers carry sea lantern / shroomlight banks.
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

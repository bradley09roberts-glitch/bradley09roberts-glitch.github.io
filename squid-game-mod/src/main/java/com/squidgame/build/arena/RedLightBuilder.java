package com.squidgame.build.arena;

import com.squidgame.build.ArenaBuilder;
import com.squidgame.build.ArenaId;
import com.squidgame.build.BuildContext;
import com.squidgame.build.CommonMarkers;
import com.squidgame.build.arena.prefab.WaitingRoomPrefab;
import com.squidgame.build.arena.redlight.DollStage;
import com.squidgame.build.arena.redlight.GatePortal;
import com.squidgame.build.arena.redlight.Ground;
import com.squidgame.build.arena.redlight.Guards;
import com.squidgame.build.arena.redlight.Lighting;
import com.squidgame.build.arena.redlight.Markers;
import com.squidgame.build.arena.redlight.OldTree;
import com.squidgame.build.arena.redlight.Props;
import com.squidgame.build.arena.redlight.Walls;

import java.util.ArrayList;
import java.util.List;

import static com.squidgame.build.arena.redlight.Layout.NEAR_FACE_Z;

/**
 * Red Light, Green Light: a gigantic sun-baked school playground (112 x 170 blocks) inside four 40-block painted-sky
 * backdrop walls with hanging stage lights, a start line near z=12, a finish line at z=140 and a safe zone beyond it
 * with the doll's wooden stage (z=147) in front of a huge old tree (z=158). The shared waiting room sits behind the
 * near wall; its doorway (plane z=-8) opens into a covered gate hall under a symbol lintel and a control gallery.
 *
 * <p>Markers (see docs/ARENA_MARKERS.md): redlight.start_spawn (497 slots, centre-out order), redlight.doll,
 * redlight.tree, arena.spectator (control gallery above the gate), arena.exit (western lounge of the safe zone),
 * guard.post x24 (14 side-row triangles, 4 triangles behind the start, 2 square managers on podiums, 4 circle
 * workers) plus the waiting room prefab's own markers. Regions: arena.bounds, redlight.start_zone, start_line,
 * finish_line, safe_zone and the prefab's waiting.bounds.
 *
 * <p>Geometry notes for the game code: the whole field is one flat floor at y=0 (stand height 1.0) with no obstacle
 * inside |x| &lt;= 53; NPCs run in straight lanes, so everything tall stands in the side margins (|x| &gt;= 54), behind the
 * lines, or in the safe zone. Doll clearance: 14 blocks above and 5.7 around her feet.
 */
public final class RedLightBuilder implements ArenaBuilder {
    @Override
    public ArenaId id() {
        return ArenaId.RED_LIGHT;
    }

    @Override
    public void build(BuildContext c) {
        Walls.build(c);
        Ground.build(c);
        c.at(0, 0, NEAR_FACE_Z, 0, () -> WaitingRoomPrefab.build(c, WaitingRoomPrefab.Spec.of("RED LIGHT, GREEN LIGHT")));
        lightWaitingRoom(c);
        GatePortal.build(c);
        DollStage.build(c);
        OldTree.build(c);
        DollStage.keepClear(c);
        Guards.build(c);
        Props.build(c);
        OldTree.floorLights(c);
        Markers.startSpawns(c);
        Markers.keyMarkers(c);
        Guards.markers(c);
        Markers.regions(c);
    }

    /**
     * The prefab hangs its panels 8 blocks above the floor, which only gives light level 7 or less at standing height,
     * so the room gets glowing floor tiles on an L1 covering lattice (every cell within 3 blocks of a tile = level 11+).
     */
    private static void lightWaitingRoom(BuildContext c) {
        int hw = 20;
        int z0 = NEAR_FACE_Z - 22, z1 = NEAR_FACE_Z - 1;
        Lighting.floorGrid(c, -hw, z0, hw, z1, 3, "squidgame:panel_light_white", (x, z) -> true, (x, z) -> {
            String f = c.get(x, 0, z);
            return f != null && f.startsWith("squidgame:tile") && !c.isSolid(x, 1, z);
        }, "squidgame:panel_light_white");
    }

    @Override
    public List<String> requiredMarkers() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED);
        l.add(CommonMarkers.GUARD_POST);
        l.add("redlight.start_spawn");
        l.add("redlight.doll");
        l.add("redlight.tree");
        return l;
    }

    @Override
    public List<String> requiredRegions() {
        List<String> l = new ArrayList<>(CommonMarkers.REQUIRED_REGIONS);
        l.add("redlight.start_zone");
        l.add("redlight.start_line");
        l.add("redlight.finish_line");
        l.add("redlight.safe_zone");
        return l;
    }

    /** Bumped above the placeholder's version (1) so worlds that built the placeholder rebuild the real arena. */
    @Override
    public int version() {
        return 2;
    }
}

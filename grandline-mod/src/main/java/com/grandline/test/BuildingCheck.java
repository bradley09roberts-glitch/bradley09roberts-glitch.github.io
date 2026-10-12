package com.grandline.test;

import com.grandline.npc.Archetypes;
import com.grandline.world.build.BuildStyle;
import com.grandline.world.build.Builder;
import com.grandline.world.settlement.BuildingType;
import com.grandline.world.settlement.Buildings;
import com.grandline.world.settlement.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;

import java.util.*;

/**
 * Deterministic verification of every settlement building type in every architectural style and all four rotations
 * (the settlement counterpart of {@link LandmarkCheck}): builds into memory over flat ground and checks that it does
 * not throw, stays within three blocks of its plot (porch steps, eaves and the stilts' front steps may stand outside;
 * the town layout keeps three blocks between plots), rebuilds identically, keeps its NPC markers on a floor with
 * headroom, uses known NPC roles, and that every NPC can be walked to from outside. One report line per type.
 */
public final class BuildingCheck {
    private static final int ORIGIN_Y = 80, MARGIN = 4;

    private BuildingCheck() {}

    public static int run(LandmarkCheck.Reporter r) {
        for (BuildingType t : BuildingType.values()) {
            if (t == BuildingType.LANDMARK || t == BuildingType.BURIED_TREASURE || t == BuildingType.HIDDEN_CACHE || t == BuildingType.SHIPWRECK) continue;
            List<String> problems = new ArrayList<>();
            int npcs = 0, variants = 0;
            for (BuildStyle st : BuildStyle.values()) {
                for (Rotation rot : Rotation.values()) {
                    try {
                        npcs += checkOne(t, st, rot, problems);
                        variants++;
                    } catch (Throwable ex) {
                        problems.add(st + "/" + rot + ": exception " + ex);
                    }
                }
            }
            r.check("building " + t.name().toLowerCase(Locale.ROOT), problems.isEmpty(),
                    problems.isEmpty() ? variants + " style/rotation variants, " + npcs + " NPC markers all on a floor and reachable"
                            : problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
        }
        return 0;
    }

    private static LandmarkCheck.MemSink build(BuildingType t, BuildStyle st, Rotation rot, long seed) {
        boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
        int sx = swap ? t.d : t.w, sz = swap ? t.w : t.d;
        LandmarkCheck.MemSink sink = new LandmarkCheck.MemSink(-MARGIN, -MARGIN, sx - 1 + MARGIN, sz - 1 + MARGIN, ORIGIN_Y, false);
        Plot plot = new Plot(t, 0, ORIGIN_Y, 0, sx, sz, rot, seed, "", true);
        Buildings.build(plot, st, new Builder(sink, 0, ORIGIN_Y, 0, t.w, t.d, rot, seed));
        return sink;
    }

    /** Returns the number of NPC markers checked. */
    private static int checkOne(BuildingType t, BuildStyle st, Rotation rot, List<String> problems) {
        long seed = 0x5EEDL + t.ordinal() * 131L + st.ordinal() * 7L + rot.ordinal();
        String tag = st.name().toLowerCase(Locale.ROOT) + "/" + rot.name().toLowerCase(Locale.ROOT);
        LandmarkCheck.MemSink m = build(t, st, rot, seed);
        if (m.outside > 0) problems.add(tag + ": " + m.outside + " writes more than " + MARGIN + " blocks outside the plot");
        LandmarkCheck.MemSink again = build(t, st, rot, seed);
        if (!again.blocks.equals(m.blocks)) problems.add(tag + ": rebuild differs");
        boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
        int sx = swap ? t.d : t.w, sz = swap ? t.w : t.d;
        LandmarkCheck.Reach reach = new LandmarkCheck.Reach(m, sx + 2 * MARGIN, sz + 2 * MARGIN);
        int n = 0;
        for (Object[] mk : m.markers) {
            if (!"npc".equals(mk[0])) continue;
            n++;
            String role = ((CompoundTag) mk[2]).getString("role");
            BlockPos p = (BlockPos) mk[1];
            String at = role + "@" + p.getX() + "," + (p.getY() - ORIGIN_Y) + "," + p.getZ();
            if (!Archetypes.has(role)) problems.add(tag + ": unknown role " + role);
            else if (!reach.standable(p.getX(), p.getY(), p.getZ())) problems.add(tag + ": " + at + " has no floor/headroom");
            else if (!reach.reached(p.getX(), p.getY(), p.getZ())) problems.add(tag + ": " + at + " not reachable from outside");
        }
        return n;
    }
}

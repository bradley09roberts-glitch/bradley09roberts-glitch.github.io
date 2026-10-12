package com.grandline.core;

import com.grandline.ship.ShipEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-level index of multipart pieces, used by {@code LevelPartEntitiesMixin}. NeoForge's {@code Level#getEntities}
 * tests every part entity in the level against every query box (each mob move, push, target search and projectile
 * step), and every ship carries dozens of hull parts, so that loop grew with ships × entities. Once per tick (or
 * whenever the part set changes) the parts are grouped by ship; a query then only gets the parts of ships whose
 * extent can reach its box. Parts of any other multipart entity are always included, so query results are the same
 * as before (only their order may differ).
 */
public final class PartEntityIndex {
    /** Below this many parts the plain list is cheap enough. */
    private static final int MIN_PARTS = 48;
    /** Slack for parts lagging their ship by a tick's movement (they are re-placed at the end of the ship's tick). */
    private static final double SLACK = 4;

    private record Group(ShipEntity ship, AABB partBounds, List<PartEntity<?>> parts) {}

    private Collection<PartEntity<?>> source;
    private int size = -1;
    private long time = Long.MIN_VALUE;
    // replaced (never mutated) on rebuild, so a list handed out stays valid
    private List<Group> groups = List.of();
    private List<PartEntity<?>> others = List.of();

    public synchronized Collection<PartEntity<?>> near(Level level, Collection<PartEntity<?>> all, AABB box) {
        int n = all.size();
        if (n < MIN_PARTS) return all;
        long t = level.getGameTime();
        if (t != time || n != size || all != source) rebuild(all, n, t);
        List<PartEntity<?>> out = null;
        for (Group g : groups) {
            if (!mayReach(g, box)) continue;
            if (out == null) out = new ArrayList<>(others);
            out.addAll(g.parts);
        }
        return out != null ? out : others;
    }

    private static boolean mayReach(Group g, AABB box) {
        if (g.partBounds.intersects(box)) return true;
        ShipEntity s = g.ship;
        double r = s.cls.length * 0.5 + s.cls.beam * 0.5 + SLACK;
        return box.maxX >= s.getX() - r && box.minX <= s.getX() + r && box.maxZ >= s.getZ() - r && box.minZ <= s.getZ() + r
                && box.maxY >= s.getY() - 8 && box.minY <= s.getY() + 24;
    }

    private void rebuild(Collection<PartEntity<?>> all, int n, long t) {
        Map<Entity, List<PartEntity<?>>> byShip = new IdentityHashMap<>();
        List<PartEntity<?>> rest = new ArrayList<>();
        for (PartEntity<?> p : all) {
            if (p.getParent() instanceof ShipEntity ship) byShip.computeIfAbsent(ship, k -> new ArrayList<>()).add(p);
            else rest.add(p);
        }
        List<Group> gs = new ArrayList<>(byShip.size());
        for (var en : byShip.entrySet()) {
            AABB bounds = null;
            for (PartEntity<?> p : en.getValue()) bounds = bounds == null ? p.getBoundingBox() : bounds.minmax(p.getBoundingBox());
            gs.add(new Group((ShipEntity) en.getKey(), bounds.inflate(SLACK), en.getValue()));
        }
        groups = gs;
        others = rest;
        source = all;
        size = n;
        time = t;
    }
}

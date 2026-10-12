package com.grandline.world.settlement;

import com.grandline.world.Island;
import com.grandline.world.WorldPlan;
import com.grandline.world.landmark.Landmarks;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.List;

/** Deterministic town layout for an island: plots, roads and a dock. */
public final class Settlement {
    public final Island island;
    public final List<Plot> plots = new ArrayList<>();
    /** Road segments {x1, z1, x2, z2}. */
    public final List<int[]> roads = new ArrayList<>();
    public int centerX, centerZ;
    public boolean hasCenter;
    /** Dock: start (on the coast) and direction (unit cardinal-ish vector *1000). */
    public int dockX, dockZ, dockDirX, dockDirZ;
    public boolean hasDock;
    public int minX, minZ, maxX, maxZ;

    private Settlement(Island island) {
        this.island = island;
    }

    public boolean bounds(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Dock tip (open water end), where arriving ships and ferries tie up. */
    public int dockTipX() {
        return dockX + dockDirX * DOCK_LENGTH / 1000;
    }

    public int dockTipZ() {
        return dockZ + dockDirZ * DOCK_LENGTH / 1000;
    }

    public static final int DOCK_LENGTH = 28;

    public static Settlement layout(Island island) {
        Settlement s = new Settlement(island);
        RandomSource r = new LegacyRandomSource(island.seed ^ 0x5E771EL);
        // ---------------- landmarks ----------------
        for (Island.LandmarkPlacement lp : island.landmarks) {
            Landmarks.Spec spec = Landmarks.spec(lp.id());
            int cx = island.x + lp.dx(), cz = island.z + lp.dz();
            Rotation rot = Rotation.values()[Math.floorMod(lp.rotation(), 4)];
            boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
            int sx = swap ? spec.depth() : spec.width(), sz = swap ? spec.width() : spec.depth();
            int y = spec.atSeaLevel() ? WorldPlan.SEA_LEVEL : (int) Math.max(WorldPlan.SEA_LEVEL + 2, Math.round(avgNatural(island, cx - sx / 2, cz - sz / 2, sx, sz)));
            if (spec.fixedY() != 0) y = spec.fixedY();
            s.plots.add(new Plot(BuildingType.LANDMARK, cx - sx / 2, y, cz - sz / 2, sx, sz, rot, island.seed ^ lp.id().hashCode(), lp.id(), spec.flatten()));
        }
        if (island.settlementType != SettlementType.NONE) {
            layoutTown(s, island, r);
        }
        // bounds
        s.minX = Integer.MAX_VALUE;
        s.minZ = Integer.MAX_VALUE;
        s.maxX = Integer.MIN_VALUE;
        s.maxZ = Integer.MIN_VALUE;
        for (Plot p : s.plots) {
            s.minX = Math.min(s.minX, p.x() - 8);
            s.minZ = Math.min(s.minZ, p.z() - 8);
            s.maxX = Math.max(s.maxX, p.x() + p.sizeX() + 8);
            s.maxZ = Math.max(s.maxZ, p.z() + p.sizeZ() + 8);
        }
        if (s.hasDock) {
            s.minX = Math.min(s.minX, Math.min(s.dockX, s.dockTipX()) - 6);
            s.maxX = Math.max(s.maxX, Math.max(s.dockX, s.dockTipX()) + 6);
            s.minZ = Math.min(s.minZ, Math.min(s.dockZ, s.dockTipZ()) - 6);
            s.maxZ = Math.max(s.maxZ, Math.max(s.dockZ, s.dockTipZ()) + 6);
        }
        if (s.plots.isEmpty() && !s.hasDock) {
            s.minX = s.minZ = 1;
            s.maxX = s.maxZ = 0;
        }
        return s;
    }

    private static void layoutTown(Settlement s, Island island, RandomSource r) {
        // pick a centre on solid land
        int cx = island.x, cz = island.z;
        for (int attempt = 0; attempt < 24; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = island.radius * (attempt == 0 ? 0.0 : 0.05 + r.nextDouble() * 0.35);
            int tx = island.x + (int) (Math.cos(ang) * dist), tz = island.z + (int) (Math.sin(ang) * dist);
            if (!buildable(island, tx - 5, tz - 5, 10, 10, 6) || overlaps(s, tx - 5, tz - 5, 10, 10, 6)) continue;
            cx = tx;
            cz = tz;
            break;
        }
        s.centerX = cx;
        s.centerZ = cz;
        s.hasCenter = true;

        List<BuildingType> wanted = new ArrayList<>();
        SettlementType st = island.settlementType;
        switch (st) {
            case HAMLET -> {
                wanted.add(BuildingType.WELL);
                for (int i = 0; i < 4; i++) wanted.add(r.nextBoolean() ? BuildingType.HUT : BuildingType.HOUSE_SMALL);
            }
            case VILLAGE -> {
                wanted.add(BuildingType.PLAZA);
                wanted.add(BuildingType.TAVERN);
                wanted.add(BuildingType.SHOP);
                wanted.add(BuildingType.BOUNTY_BOARD);
                wanted.add(BuildingType.FARM);
            }
            case TOWN, PORT -> {
                wanted.add(BuildingType.PLAZA);
                wanted.add(BuildingType.TAVERN);
                wanted.add(BuildingType.SHOP);
                wanted.add(BuildingType.BLACKSMITH);
                wanted.add(BuildingType.INN);
                wanted.add(BuildingType.BOUNTY_BOARD);
                wanted.add(BuildingType.CHURCH);
                if (island.faction.equals("marine") || r.nextInt(3) == 0) wanted.add(BuildingType.MARINE_POST);
                if (st == SettlementType.PORT) {
                    wanted.add(BuildingType.WAREHOUSE);
                    wanted.add(BuildingType.WAREHOUSE);
                }
            }
            case CITY -> {
                wanted.add(BuildingType.PLAZA);
                wanted.add(BuildingType.TAVERN);
                wanted.add(BuildingType.TAVERN);
                wanted.add(BuildingType.SHOP);
                wanted.add(BuildingType.SHOP);
                wanted.add(BuildingType.BLACKSMITH);
                wanted.add(BuildingType.INN);
                wanted.add(BuildingType.BOUNTY_BOARD);
                wanted.add(BuildingType.CHURCH);
                wanted.add(BuildingType.MANSION);
                wanted.add(BuildingType.WAREHOUSE);
                wanted.add(BuildingType.DOJO);
                wanted.add(BuildingType.MARINE_POST);
                for (int i = 0; i < 3; i++) wanted.add(BuildingType.MARKET_STALL);
            }
            case MARINE_BASE -> {
                wanted.add(BuildingType.MARINE_POST);
                wanted.add(BuildingType.MARINE_POST);
                wanted.add(BuildingType.WATCHTOWER);
                wanted.add(BuildingType.WATCHTOWER);
                wanted.add(BuildingType.WAREHOUSE);
                wanted.add(BuildingType.BOUNTY_BOARD);
            }
            case FORTRESS -> {
                wanted.add(BuildingType.WATCHTOWER);
                wanted.add(BuildingType.WATCHTOWER);
                wanted.add(BuildingType.WAREHOUSE);
                wanted.add(BuildingType.TAVERN);
                wanted.add(BuildingType.HOUSE_LARGE);
            }
            case RUINS -> {
                for (int i = 0; i < 3; i++) wanted.add(BuildingType.RUINED_HOUSE);
            }
            default -> {}
        }
        int target = Mth.nextInt(r, st.minBuildings, st.maxBuildings);
        target = Math.max(wanted.size(), (int) Math.min(target, 3 + island.radius / 22.0));
        BuildingType filler = switch (st) {
            case RUINS -> BuildingType.RUINED_HOUSE;
            case HAMLET -> BuildingType.HUT;
            case MARINE_BASE -> BuildingType.HOUSE_LARGE;
            default -> BuildingType.HOUSE;
        };
        while (wanted.size() < target) {
            int roll = r.nextInt(10);
            wanted.add(roll < 2 ? BuildingType.HOUSE_SMALL : roll < 3 ? BuildingType.HOUSE_LARGE : filler);
        }

        int spiral = 0;
        int placed = 0;
        for (BuildingType type : wanted) {
            boolean ok = false;
            for (int attempt = 0; attempt < 60 && !ok; attempt++, spiral++) {
                double rad = (type == BuildingType.PLAZA || type == BuildingType.WELL) && placed == 0 ? 0 : 13 + 8.5 * Math.sqrt(spiral);
                double ang = spiral * 2.39996 + r.nextDouble() * 0.3;
                int px = cx + (int) Math.round(Math.cos(ang) * rad);
                int pz = cz + (int) Math.round(Math.sin(ang) * rad);
                // door faces the centre
                double dx = cx - px, dz = cz - pz;
                Rotation rot;
                if (Math.abs(dx) > Math.abs(dz)) rot = dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
                else rot = dz > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE;
                if (rad == 0) rot = Rotation.NONE;
                boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
                int sx = swap ? type.d : type.w, sz = swap ? type.w : type.d;
                int x0 = px - sx / 2, z0 = pz - sz / 2;
                if (!buildable(island, x0, z0, sx, sz, type == BuildingType.WATCHTOWER ? 9 : 6)) continue;
                if (overlaps(s, x0, z0, sx, sz, 3)) continue;
                int y = (int) Math.round(avgNatural(island, x0, z0, sx, sz));
                y = Math.max(WorldPlan.SEA_LEVEL + 2, y + 1);
                s.plots.add(new Plot(type, x0, y, z0, sx, sz, rot, island.seed * 31 + spiral, "", true));
                // road from door to centre
                int doorX = px + (rot == Rotation.CLOCKWISE_90 ? sx / 2 + 1 : rot == Rotation.COUNTERCLOCKWISE_90 ? -sx / 2 - 1 : 0);
                int doorZ = pz + (rot == Rotation.NONE ? -sz / 2 - 1 : rot == Rotation.CLOCKWISE_180 ? sz / 2 + 1 : 0);
                if (rad > 0) s.roads.add(new int[]{doorX, doorZ, cx, cz});
                ok = true;
                placed++;
            }
        }

        // ---------------- dock + shipwright on the nearest coast ----------------
        if (st != SettlementType.RUINS) {
            double bestDist = Double.MAX_VALUE;
            int bx = 0, bz = 0, bdx = 0, bdz = 0;
            for (int i = 0; i < 16; i++) {
                double ang = i * Math.PI / 8;
                double ux = Math.cos(ang), uz = Math.sin(ang);
                for (int d = 8; d < island.radius * 1.6; d += 4) {
                    int tx = cx + (int) (ux * d), tz = cz + (int) (uz * d);
                    double h = island.naturalHeight(tx, tz);
                    if (Double.isNaN(h) || h < WorldPlan.SEA_LEVEL) {
                        // make sure open water continues for the dock length
                        boolean open = true;
                        for (int k = 4; k <= DOCK_LENGTH + 10; k += 4) {
                            double h2 = island.naturalHeight(tx + (int) (ux * k), tz + (int) (uz * k));
                            if (!Double.isNaN(h2) && h2 >= WorldPlan.SEA_LEVEL - 1) {
                                open = false;
                                break;
                            }
                        }
                        if (open && d < bestDist) {
                            bestDist = d;
                            bx = tx - (int) (ux * 3);
                            bz = tz - (int) (uz * 3);
                            bdx = (int) Math.round(ux * 1000);
                            bdz = (int) Math.round(uz * 1000);
                        }
                        break;
                    }
                }
            }
            if (bestDist < Double.MAX_VALUE) {
                s.hasDock = true;
                s.dockX = bx;
                s.dockZ = bz;
                s.dockDirX = bdx;
                s.dockDirZ = bdz;
                s.roads.add(new int[]{bx, bz, cx, cz});
                if (st != SettlementType.HAMLET) {
                    // shipwright yard beside the dock root, inland
                    BuildingType t = BuildingType.SHIPWRIGHT;
                    int inX = bx - bdx * 14 / 1000, inZ = bz - bdz * 14 / 1000;
                    int sideX = -bdz * 14 / 1000, sideZ = bdx * 14 / 1000;
                    for (int side = -1; side <= 1; side += 2) {
                        int px = inX + sideX * side, pz = inZ + sideZ * side;
                        Rotation rot = Math.abs(bdx) > Math.abs(bdz) ? (bdx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90)
                                : (bdz > 0 ? Rotation.CLOCKWISE_180 : Rotation.NONE);
                        boolean swap = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
                        int sx = swap ? t.d : t.w, sz = swap ? t.w : t.d;
                        int x0 = px - sx / 2, z0 = pz - sz / 2;
                        if (!buildable(island, x0, z0, sx, sz, 8) || overlaps(s, x0, z0, sx, sz, 2)) continue;
                        int y = Math.max(WorldPlan.SEA_LEVEL + 2, (int) Math.round(avgNatural(island, x0, z0, sx, sz)) + 1);
                        s.plots.add(new Plot(t, x0, y, z0, sx, sz, rot, island.seed * 17 + side, "", true));
                        break;
                    }
                }
            }
        }
    }

    private static double avgNatural(Island island, int x0, int z0, int sx, int sz) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i <= 2; i++)
            for (int j = 0; j <= 2; j++) {
                double h = island.naturalHeight(x0 + sx * i / 2, z0 + sz * j / 2);
                if (Double.isNaN(h)) h = WorldPlan.SEA_LEVEL - 10;
                sum += h;
                n++;
            }
        return sum / n;
    }

    private static boolean buildable(Island island, int x0, int z0, int sx, int sz, int maxSlope) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int i = 0; i <= 2; i++)
            for (int j = 0; j <= 2; j++) {
                int x = x0 + sx * i / 2, z = z0 + sz * j / 2;
                double h = island.naturalHeight(x, z);
                if (Double.isNaN(h) || h < WorldPlan.SEA_LEVEL + 1) return false;
                if (island.t(x, z) > 0.86) return false;
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        return max - min <= maxSlope;
    }

    private static boolean overlaps(Settlement s, int x0, int z0, int sx, int sz, int gap) {
        for (Plot p : s.plots) {
            if (x0 < p.x() + p.sizeX() + gap && x0 + sx + gap > p.x() && z0 < p.z() + p.sizeZ() + gap && z0 + sz + gap > p.z()) return true;
        }
        return false;
    }
}

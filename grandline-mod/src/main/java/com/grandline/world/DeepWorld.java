package com.grandline.world;

import com.grandline.world.build.BuildStyle;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;

/**
 * The deep sea, 10,000 metres down (dimension {@code grandline:deep_sea}). Water fills everything up to
 * {@link #WATER_TOP}; coated ships travel at y 63 inside their bubble. Fish-Man Island and the Sea Forest sit in
 * great air bubbles (domes) whose skin is {@code grandline:bubble_membrane}: no collision, but it holds the sea back.
 * Ships dive in from Sabaody (the Paradise end) and surface at the New World Gate.
 */
public final class DeepWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, com.grandline.GrandLine.id("deep_sea"));
    public static final int WATER_TOP = 250;
    public static final int ENTRY_X = -1500, ENTRY_Z = 0;
    public static final int EXIT_X = 1500;
    public static final int BOUNDS = 1900;

    /** An air bubble: an ellipsoid above the harbour line (y 63), radius {@code rxz} across and {@code ry} up. */
    public record Dome(int cx, int cz, int rxz, int ry) {
        public double value(double x, double y, double z) {
            double dx = (x - cx) / rxz, dz = (z - cz) / rxz, dy = (y - 63) / ry;
            return dx * dx + dy * dy + dz * dz;
        }

        /** Air inside the bubble (only above the harbour line; below it the sea flows in and out freely). */
        public boolean inside(int x, int y, int z) {
            return y > 63 && value(x + 0.5, y + 0.5, z + 0.5) < 1;
        }
    }

    public static final List<Dome> DOMES = List.of(new Dome(0, 0, 250, 170), new Dome(-620, 260, 125, 72));

    public static boolean inAnyDome(int x, int y, int z) {
        for (Dome d : DOMES) if (d.inside(x, y, z)) return true;
        return false;
    }

    /** Water-tight skin: outside every dome but touching one. */
    public static boolean isMembrane(int x, int y, int z) {
        if (y <= 63 || inAnyDome(x, y, z)) return false;
        return inAnyDome(x + 1, y, z) || inAnyDome(x - 1, y, z) || inAnyDome(x, y + 1, z) || inAnyDome(x, y, z + 1) || inAnyDome(x, y, z - 1);
    }

    /** Dome whose footprint covers (x, z), or null. */
    public static Dome domeAt(int x, int z) {
        for (Dome d : DOMES) if (Math.hypot(x - d.cx(), z - d.cz()) < d.rxz() + 2) return d;
        return null;
    }

    public static double floor(SimplexNoise n, int x, int z) {
        double f = -14 + n.getValue(x * 0.004, z * 0.004) * 10 + n.getValue(x * 0.02, z * 0.02) * 3;
        // the abyssal trench north of the route
        double trench = Math.exp(-Math.pow((z + 700) / 160.0, 2));
        return f - trench * 35;
    }

    public static List<IslandSpec> islands(long seed) {
        List<IslandSpec> l = new ArrayList<>();
        l.add(spec("fishman_island", 0, 0, 200).shape(IslandShape.HILLS, 58).theme(IslandTheme.CORAL).style(BuildStyle.CORAL)
                .settle(SettlementType.CITY).tier(7).landmark("ryugu_palace", 0, -30).landmark("mermaid_cove", 150, 70)
                .landmark("gyoncorde_plaza", -70, 95).named("Fish-Man Island"));
        l.add(spec("sea_forest", -620, 260, 95).shape(IslandShape.FLAT, 4).theme(IslandTheme.CORAL).style(BuildStyle.CORAL)
                .settle(SettlementType.NONE).tier(7).landmark("sea_forest_graveyard", 0, 0).named("Sea Forest"));
        l.add(spec("sunken_ruins", 760, -320, 140).shape(IslandShape.HILLS, 20).theme(IslandTheme.CORAL)
                .settle(SettlementType.NONE).tier(8).landmark("ancient_ruin", 0, 0).landmark("treasure_cache", 60, 40).named("Sunken Ruins"));
        l.add(spec("coral_reef", -900, -260, 160).shape(IslandShape.ATOLL, 10).theme(IslandTheme.CORAL)
                .settle(SettlementType.NONE).tier(7).landmark("treasure_cache", 0, 120).named("Coral Hill Reef"));
        return l;
    }

    private static IslandSpec spec(String id, int x, int z, int r) {
        IslandSpec s = IslandSpec.canon(id, x, z, r);
        s.zone = SeaZone.DEEP_SEA;
        return s;
    }

    private DeepWorld() {}
}

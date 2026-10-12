package com.grandline.world;

import com.grandline.world.build.BuildStyle;
import com.grandline.world.settlement.SettlementType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The White Sea, 10,000 metres up (dimension {@code grandline:sky}). Sea Cloud is tinted water (y 48–63) over an
 * Island Cloud floor (y 46–47); islands are Island Cloud or, for Upper Yard, earth that the Knock Up Stream once
 * carried up from Jaya. Ships arrive at Heaven's Gate from the Knock Up Stream and leave by the Cloud End.
 */
public final class SkyWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, com.grandline.GrandLine.id("sky"));
    public static final int FLOOR_Y = 47;
    /** Where ships come out of the Knock Up Stream (just east of Heaven's Gate), heading east. */
    public static final int ARRIVAL_X = -880, ARRIVAL_Z = 0;
    /** Ships sailing past this x (or beyond the sky's bounds) go over the Cloud End and fall back to the Blue Sea. */
    public static final int CLOUD_END_X = 1500;
    public static final int BOUNDS = 1900;

    public static List<IslandSpec> islands(long seed) {
        List<IslandSpec> l = new ArrayList<>();
        l.add(spec("heavens_gate", -1000, 0, 70).shape(IslandShape.FLAT, 3).theme(IslandTheme.CLOUD).settle(SettlementType.NONE).tier(5)
                .landmark("heavens_gate", 0, 0).named("Heaven's Gate"));
        l.add(spec("angel_island", -450, 140, 210).shape(IslandShape.HILLS, 12).theme(IslandTheme.CLOUD).style(BuildStyle.CLOUD)
                .settle(SettlementType.TOWN).tier(5).landmark("cloud_chapel", 0, -60).named("Angel Island"));
        l.add(spec("upper_yard", 420, -80, 430).shape(IslandShape.GIANT_TREE, 70).theme(IslandTheme.TROPICAL).settle(SettlementType.NONE).tier(6)
                .landmark("shandora_ruins", -60, 40).landmark("golden_belfry", 120, -90).landmark("sacrificial_altar", -250, 160).named("Upper Yard"));
        l.add(spec("weatheria", -200, -720, 85).shape(IslandShape.HILLS, 10).theme(IslandTheme.CLOUD).style(BuildStyle.CLOUD)
                .settle(SettlementType.NONE).tier(5).landmark("weather_lab", 0, 0).named("Weatheria"));
        l.add(spec("cloud_end", CLOUD_END_X - 60, 0, 45).shape(IslandShape.FLAT, 2).theme(IslandTheme.CLOUD).settle(SettlementType.NONE).tier(5)
                .landmark("cloud_end_gate", 0, 0).named("Cloud End"));
        // drifting cloud islets: hamlets of Sky People, shrines and caches
        RandomSource r = new LegacyRandomSource(seed ^ 0x5C1EL);
        List<int[]> placed = new ArrayList<>();
        for (IslandSpec s : l) placed.add(new int[]{s.x, s.z, s.radius});
        int n = 0;
        for (int attempt = 0; attempt < 400 && n < 14; attempt++) {
            int x = Mth.nextInt(r, -1500, 1300), z = Mth.nextInt(r, -1500, 1500);
            int rad = Mth.nextInt(r, 28, 90);
            boolean ok = Math.abs(z) > 120 || x < -1100; // keep the main channel east from Heaven's Gate open
            for (int[] p : placed) if (Math.hypot(p[0] - x, p[1] - z) < p[2] + rad + 120) ok = false;
            if (!ok) continue;
            placed.add(new int[]{x, z, rad});
            IslandSpec s = spec("sky_islet_" + n, x, z, rad).shape(r.nextBoolean() ? IslandShape.HILLS : IslandShape.FLAT, Mth.nextInt(r, 4, 14))
                    .theme(IslandTheme.CLOUD).style(BuildStyle.CLOUD).settle(rad > 60 && r.nextBoolean() ? SettlementType.HAMLET : SettlementType.NONE).tier(5);
            s.name = com.grandline.world.islands.IslandNames.generate(r);
            l.add(s);
            n++;
        }
        return l;
    }

    private static IslandSpec spec(String id, int x, int z, int r) {
        IslandSpec s = IslandSpec.canon(id, x, z, r);
        s.zone = SeaZone.SKY_SEA;
        return s;
    }

    private SkyWorld() {}
}

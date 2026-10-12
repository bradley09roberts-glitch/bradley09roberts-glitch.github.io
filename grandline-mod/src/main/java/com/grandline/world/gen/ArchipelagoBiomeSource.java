package com.grandline.world.gen;

import com.grandline.GrandLine;
import com.grandline.world.Island;
import com.grandline.world.IslandTheme;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldPlan;
import com.grandline.world.settlement.Settlement;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.*;
import java.util.stream.Stream;

/** Biomes follow the world plan: sea zones get custom ocean biomes, islands get their theme's land biomes. */
public final class ArchipelagoBiomeSource extends BiomeSource {
    public static final MapCodec<ArchipelagoBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, ArchipelagoBiomeSource::new));

    public static final ResourceKey<Biome> EAST_BLUE = key("east_blue"), WEST_BLUE = key("west_blue"), NORTH_BLUE = key("north_blue"),
            SOUTH_BLUE = key("south_blue"), PARADISE = key("paradise_sea"), CALM_BELT = key("calm_belt"), NEW_WORLD = key("new_world_sea"),
            WORLD_EDGE = key("world_edge"), RED_LINE = key("red_line"), REVERSE_MOUNTAIN = key("reverse_mountain");

    /** Plan used by plan-unaware callers (locate, spawn search). Set by the chunk generator / server start. */
    static volatile WorldPlan activePlan;

    private final HolderGetter<Biome> biomes;
    private final Map<ResourceKey<Biome>, Holder<Biome>> cache = new HashMap<>();

    private static ResourceKey<Biome> key(String n) {
        return ResourceKey.create(Registries.BIOME, GrandLine.id(n));
    }

    public ArchipelagoBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    private Holder<Biome> holder(ResourceKey<Biome> k) {
        synchronized (cache) {
            return cache.computeIfAbsent(k, kk -> biomes.getOrThrow(kk));
        }
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Set<ResourceKey<Biome>> keys = new LinkedHashSet<>(List.of(EAST_BLUE, WEST_BLUE, NORTH_BLUE, SOUTH_BLUE, PARADISE, CALM_BELT, NEW_WORLD, WORLD_EDGE, RED_LINE, REVERSE_MOUNTAIN, Biomes.BEACH));
        for (IslandTheme t : IslandTheme.values()) {
            keys.add(t.wildBiome);
            keys.add(t.settledBiome);
        }
        return keys.stream().map(this::holder);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        WorldPlan plan = activePlan;
        if (plan == null) return holder(PARADISE);
        return biomeAt(plan, qx << 2, qz << 2, new WorldPlan.Sample());
    }

    public Holder<Biome> biomeAt(WorldPlan plan, int x, int z, WorldPlan.Sample s) {
        plan.sample(x, z, s);
        return holder(biomeKey(s, x, z));
    }

    static ResourceKey<Biome> biomeKey(WorldPlan.Sample s, int x, int z) {
        if (s.canal) return REVERSE_MOUNTAIN;
        if (s.redLine || (s.island == null && s.height > WorldPlan.SEA_LEVEL + 20)) return RED_LINE;
        Island isl = s.island;
        if (isl != null && s.height >= WorldPlan.SEA_LEVEL) {
            IslandTheme theme = isl.themeAt(x, z);
            if (s.height <= WorldPlan.SEA_LEVEL + 2 && isl.t(x, z) > 0.88) return theme == IslandTheme.SNOW || theme == IslandTheme.ICE ? Biomes.SNOWY_BEACH : Biomes.BEACH;
            Settlement st = isl.settlement();
            if (st.bounds(x, z)) return theme.settledBiome;
            return theme.wildBiome;
        }
        return seaBiome(s.zone);
    }

    static ResourceKey<Biome> seaBiome(SeaZone z) {
        return switch (z) {
            case EAST_BLUE -> EAST_BLUE;
            case WEST_BLUE -> WEST_BLUE;
            case NORTH_BLUE -> NORTH_BLUE;
            case SOUTH_BLUE -> SOUTH_BLUE;
            case CALM_BELT -> CALM_BELT;
            case PARADISE -> PARADISE;
            case NEW_WORLD -> NEW_WORLD;
            case RED_LINE -> RED_LINE;
            case WORLD_EDGE -> WORLD_EDGE;
        };
    }
}

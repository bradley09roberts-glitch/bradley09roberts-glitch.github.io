package com.terracraft.world.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Terraria's biomes only: wraps a normal biome source (the vanilla overworld's multi-noise one) and replaces every
 * biome Terraria does not have with its closest Terraria counterpart, e.g. plains, cherry groves and swamps become
 * Forest, taigas and ice spikes become the Snow biome, badlands become Desert and bamboo jungles become Jungle.
 * The terrain shape is untouched (it comes from the noise settings, not the biomes). The mapping lives in the world
 * preset JSON ({@code data/minecraft/worldgen/world_preset/normal.json}, written by tools/generate_data.py).
 */
public class TerrariaBiomeSource extends BiomeSource {
    public static final MapCodec<TerrariaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        BiomeSource.CODEC.fieldOf("base").forGetter(s -> s.base),
        Codec.unboundedMap(ResourceKey.codec(Registries.BIOME), Biome.CODEC).fieldOf("replace").forGetter(s -> s.replace)
    ).apply(i, TerrariaBiomeSource::new));

    private final BiomeSource base;
    private final Map<ResourceKey<Biome>, Holder<Biome>> replace;

    public TerrariaBiomeSource(BiomeSource base, Map<ResourceKey<Biome>, Holder<Biome>> replace) {
        this.base = base;
        this.replace = replace;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return base.possibleBiomes().stream().map(this::map).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return map(base.getNoiseBiome(quartX, quartY, quartZ, sampler));
    }

    private Holder<Biome> map(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> replace.getOrDefault(key, biome)).orElse(biome);
    }

    @Override
    public void addDebugInfo(List<String> result, net.minecraft.core.BlockPos feetPos, Climate.Sampler sampler) {
        base.addDebugInfo(result, feetPos, sampler);
    }
}

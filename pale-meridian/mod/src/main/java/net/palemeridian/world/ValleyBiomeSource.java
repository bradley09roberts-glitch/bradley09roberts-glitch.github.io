package net.palemeridian.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * {@code palemeridian:valley} biome source: designed district biomes inside the valley (above y=0),
 * the wrapped vanilla source everywhere else (outside the rim, and deep caves below the valley).
 */
public final class ValleyBiomeSource extends BiomeSource {
	public static final MapCodec<ValleyBiomeSource> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(
				BiomeSource.CODEC.fieldOf("vanilla").forGetter(s -> s.vanilla),
				Codec.unboundedMap(Codec.STRING, Biome.CODEC).fieldOf("districts").forGetter(s -> s.districts)
			)
			.apply(i, ValleyBiomeSource::new)
	);

	private final BiomeSource vanilla;
	private final Map<String, Holder<Biome>> districts;

	public ValleyBiomeSource(BiomeSource vanilla, Map<String, Holder<Biome>> districts) {
		this.vanilla = vanilla;
		this.districts = districts;
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return Stream.concat(this.vanilla.possibleBiomes().stream(), this.districts.values().stream());
	}

	@Override
	public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
		int x = QuartPos.toBlock(quartX), y = QuartPos.toBlock(quartY), z = QuartPos.toBlock(quartZ);
		ValleyTerrain terrain = ValleyTerrain.get();
		if (y < 0 || terrain.distanceFromCenter(x, z) >= terrain.layout().biomeRadius) {
			return this.vanilla.getNoiseBiome(quartX, quartY, quartZ, sampler);
		}
		Holder<Biome> biome = this.districts.get(terrain.district(x, y, z));
		return biome != null ? biome : this.vanilla.getNoiseBiome(quartX, quartY, quartZ, sampler);
	}
}

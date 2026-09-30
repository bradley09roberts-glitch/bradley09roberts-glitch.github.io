package net.palemeridian.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** Places a structure start only in an explicit list of chunks (the valley's authored sites). */
public final class FixedStructurePlacement extends StructurePlacement {
	public static final MapCodec<FixedStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(
		i -> placementCodec(i)
			.and(ChunkPos.CODEC.listOf().fieldOf("chunks").forGetter(FixedStructurePlacement::chunks))
			.apply(i, FixedStructurePlacement::new)
	);

	private final List<ChunkPos> chunks;
	private final LongSet packed = new LongOpenHashSet();

	public FixedStructurePlacement(
		Vec3i locateOffset,
		FrequencyReductionMethod frequencyReductionMethod,
		float frequency,
		int salt,
		Optional<ExclusionZone> exclusionZone,
		List<ChunkPos> chunks
	) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
		this.chunks = List.copyOf(chunks);
		for (ChunkPos c : this.chunks) {
			this.packed.add(ChunkPos.pack(c.x(), c.z()));
		}
	}

	public List<ChunkPos> chunks() {
		return this.chunks;
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		return this.packed.contains(ChunkPos.pack(sourceX, sourceZ));
	}

	@Override
	public StructurePlacementType<?> type() {
		return PMWorldgen.FIXED_PLACEMENT;
	}
}

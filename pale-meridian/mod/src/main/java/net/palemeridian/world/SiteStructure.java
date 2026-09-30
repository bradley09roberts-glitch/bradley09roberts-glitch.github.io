package net.palemeridian.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * An authored site: one or more structure templates placed at absolute, fixed positions with fixed
 * rotations. Combined with {@link FixedStructurePlacement} this gives deterministic story locations
 * that generate with the world (restart-safe, never re-placed over player builds).
 */
public final class SiteStructure extends Structure {
	public record PieceDef(Identifier template, BlockPos pos, Rotation rotation) {
		public static final Codec<PieceDef> CODEC = RecordCodecBuilder.create(
			i -> i.group(
					Identifier.CODEC.fieldOf("template").forGetter(PieceDef::template),
					BlockPos.CODEC.fieldOf("pos").forGetter(PieceDef::pos),
					Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(PieceDef::rotation)
				)
				.apply(i, PieceDef::new)
		);
	}

	public static final MapCodec<SiteStructure> CODEC = RecordCodecBuilder.mapCodec(
		i -> i.group(settingsCodec(i), PieceDef.CODEC.listOf().fieldOf("pieces").forGetter(s -> s.pieces)).apply(i, SiteStructure::new)
	);

	private final List<PieceDef> pieces;

	public SiteStructure(StructureSettings settings, List<PieceDef> pieces) {
		super(settings);
		this.pieces = List.copyOf(pieces);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		if (this.pieces.isEmpty()) {
			return Optional.empty();
		}
		BlockPos anchor = this.pieces.getFirst().pos();
		return Optional.of(new GenerationStub(anchor, builder -> {
			for (PieceDef def : this.pieces) {
				builder.addPiece(new SitePiece(context.structureTemplateManager(), def.template(), def.pos(), def.rotation()));
			}
		}));
	}

	@Override
	public StructureType<?> type() {
		return PMWorldgen.SITE_STRUCTURE;
	}
}

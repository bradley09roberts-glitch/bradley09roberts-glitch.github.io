package net.palemeridian.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** A single authored template placed at a fixed position and rotation. */
public final class SitePiece extends TemplateStructurePiece {
	public SitePiece(StructureTemplateManager manager, Identifier template, BlockPos pos, Rotation rotation) {
		super(PMWorldgen.SITE_PIECE, 0, manager, template, template.toString(), settings(rotation), pos);
	}

	public SitePiece(StructureTemplateManager manager, CompoundTag tag) {
		super(PMWorldgen.SITE_PIECE, tag, manager, location -> settings(tag.read("Rot", Rotation.LEGACY_CODEC).orElse(Rotation.NONE)));
	}

	private static StructurePlaceSettings settings(Rotation rotation) {
		return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		super.addAdditionalSaveData(context, tag);
		tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
	}

	@Override
	protected void handleDataMarker(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB) {
	}
}

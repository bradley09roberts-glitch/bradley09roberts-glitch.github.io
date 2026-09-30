package net.palemeridian.world;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** Registers the valley's custom worldgen types. Must run during mod initialisation. */
public final class PMWorldgen {
	public static StructurePieceType SITE_PIECE;
	public static StructureType<SiteStructure> SITE_STRUCTURE;
	public static StructurePlacementType<FixedStructurePlacement> FIXED_PLACEMENT;
	public static Feature<NoneFeatureConfiguration> VALE_ROADS;

	private PMWorldgen() {
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("palemeridian", path);
	}

	public static void register() {
		SITE_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("site"), (StructurePieceType.StructureTemplateType) SitePiece::new);
		SITE_STRUCTURE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id("site"), () -> SiteStructure.CODEC);
		FIXED_PLACEMENT = Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, id("fixed"), () -> FixedStructurePlacement.CODEC);
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("valley"), ValleyDensityFunction.CODEC.codec());
		Registry.register(BuiltInRegistries.BIOME_SOURCE, id("valley"), ValleyBiomeSource.CODEC);
		VALE_ROADS = Registry.register(BuiltInRegistries.FEATURE, id("vale_roads"), new ValeRoadsFeature(NoneFeatureConfiguration.CODEC));
	}
}

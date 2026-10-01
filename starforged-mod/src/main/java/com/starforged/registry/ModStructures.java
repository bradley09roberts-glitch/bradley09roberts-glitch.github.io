package com.starforged.registry;

import com.starforged.Starforged;
import com.starforged.world.ObservatoryPiece;
import com.starforged.world.ObservatoryStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Starforged.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Starforged.MODID);

    public static final RegistryObject<StructureType<ObservatoryStructure>> OBSERVATORY = STRUCTURE_TYPES.register("observatory",
        () -> () -> ObservatoryStructure.CODEC);

    public static final RegistryObject<StructurePieceType> OBSERVATORY_PIECE = PIECE_TYPES.register("observatory_piece",
        () -> (StructurePieceType.ContextlessType) ObservatoryPiece::new);

    public static final RegistryObject<StructureType<com.starforged.sun.world.SunTempleStructure>> SUN_TEMPLE = STRUCTURE_TYPES.register("sun_temple",
        () -> () -> com.starforged.sun.world.SunTempleStructure.CODEC);

    public static final RegistryObject<StructurePieceType> SUN_TEMPLE_PIECE = PIECE_TYPES.register("sun_temple_piece",
        () -> (StructurePieceType.ContextlessType) com.starforged.sun.world.SunTemplePiece::new);

    public static final RegistryObject<StructureType<com.starforged.moon.world.TidalOrreryStructure>> TIDAL_ORRERY = STRUCTURE_TYPES.register(
        "tidal_orrery", () -> () -> com.starforged.moon.world.TidalOrreryStructure.CODEC);

    public static final RegistryObject<StructurePieceType> TIDAL_ORRERY_PIECE = PIECE_TYPES.register("tidal_orrery_piece",
        () -> (StructurePieceType.ContextlessType) com.starforged.moon.world.TidalOrreryPiece::new);

    public static final RegistryObject<StructureType<com.starforged.tempest.world.TempestCitadelStructure>> TEMPEST_CITADEL = STRUCTURE_TYPES.register(
        "tempest_citadel", () -> () -> com.starforged.tempest.world.TempestCitadelStructure.CODEC);

    public static final RegistryObject<StructurePieceType> TEMPEST_CITADEL_PIECE = PIECE_TYPES.register("tempest_citadel_piece",
        () -> (StructurePieceType.ContextlessType) com.starforged.tempest.world.TempestCitadelPiece::new);

    private ModStructures() {
    }
}

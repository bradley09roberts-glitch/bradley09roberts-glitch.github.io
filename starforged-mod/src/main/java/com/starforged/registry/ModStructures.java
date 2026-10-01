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

    private ModStructures() {
    }
}

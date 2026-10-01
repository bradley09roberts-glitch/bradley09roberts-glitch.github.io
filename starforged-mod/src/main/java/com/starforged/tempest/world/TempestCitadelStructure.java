package com.starforged.tempest.world;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** The Tempest Citadel: a fortress on its own floating island, high in the Stormreach sky. */
public class TempestCitadelStructure extends Structure {
    public static final MapCodec<TempestCitadelStructure> CODEC = simpleCodec(TempestCitadelStructure::new);
    public static final int FLOOR_Y = 150;

    public TempestCitadelStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        BlockPos start = new BlockPos(cx, FLOOR_Y, cz);
        return Optional.of(new Structure.GenerationStub(start, builder -> builder.addPiece(
            new TempestCitadelPiece(cx - TempestCitadelPiece.CENTER, FLOOR_Y, cz - TempestCitadelPiece.CENTER))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.TEMPEST_CITADEL.get();
    }
}

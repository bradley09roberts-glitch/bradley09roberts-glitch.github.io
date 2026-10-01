package com.starforged.moon.world;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModStructures;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** The Tidal Orrery: a round observatory half-sunk in the shallows of the Pale Reach. */
public class TidalOrreryStructure extends Structure {
    public static final MapCodec<TidalOrreryStructure> CODEC = simpleCodec(TidalOrreryStructure::new);

    public TidalOrreryStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        int[][] offsets = {{0, 0}, {-17, -17}, {17, -17}, {-17, 17}, {17, 17}};
        int[] heights = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            heights[i] = generator.getFirstOccupiedHeight(cx + offsets[i][0], cz + offsets[i][1], Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        }
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        if (sorted[sorted.length - 1] - sorted[0] > 14) {
            return Optional.empty();
        }
        int base = Math.max(sorted[sorted.length / 2], generator.getSeaLevel() + 1) - 1;
        BlockPos start = new BlockPos(cx, base, cz);
        return Optional.of(new Structure.GenerationStub(start, builder -> builder.addPiece(
            new TidalOrreryPiece(cx - TidalOrreryPiece.CENTER, base, cz - TidalOrreryPiece.CENTER))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.TIDAL_ORRERY.get();
    }
}

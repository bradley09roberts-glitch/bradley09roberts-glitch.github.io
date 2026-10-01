package com.starforged.world;

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

/**
 * The Fallen Observatory: a ruined astronomers' tower crowned with a starglass dome, a great telescope
 * and the Celestial Altar. Placed on fairly flat, dry land.
 */
public class ObservatoryStructure extends Structure {
    public static final MapCodec<ObservatoryStructure> CODEC = simpleCodec(ObservatoryStructure::new);

    public ObservatoryStructure(Structure.StructureSettings settings) {
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
            int x = cx + offsets[i][0];
            int z = cz + offsets[i][1];
            int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            int floor = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
            if (surface != floor) {
                return Optional.empty(); // water
            }
            heights[i] = surface;
        }
        int[] sorted = heights.clone();
        Arrays.sort(sorted);
        if (sorted[sorted.length - 1] - sorted[0] > 12) {
            return Optional.empty();
        }
        int base = sorted[sorted.length / 2] - 1;
        if (base < generator.getSeaLevel()) {
            return Optional.empty();
        }
        BlockPos start = new BlockPos(cx, base, cz);
        return Optional.of(new Structure.GenerationStub(start, builder -> builder.addPiece(new ObservatoryPiece(cx - ObservatoryPiece.CENTER, base, cz - ObservatoryPiece.CENTER))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.OBSERVATORY.get();
    }
}

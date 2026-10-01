package com.starforged.sun.world;

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
 * The Sun Temple: a stepped ziggurat holding the Hall of Braziers and, on its summit, the Sun Altar.
 * Placed on land that is dry (no lava seas) and not too steep.
 */
public class SunTempleStructure extends Structure {
    public static final MapCodec<SunTempleStructure> CODEC = simpleCodec(SunTempleStructure::new);

    public SunTempleStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX();
        int cz = chunk.getMiddleBlockZ();
        ChunkGenerator generator = context.chunkGenerator();
        int[][] offsets = {{0, 0}, {-22, -22}, {22, -22}, {-22, 22}, {22, 22}};
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
        if (sorted[sorted.length - 1] - sorted[0] > 16) {
            return Optional.empty();
        }
        int base = sorted[sorted.length / 2] - 1;
        if (base < generator.getSeaLevel()) {
            return Optional.empty();
        }
        BlockPos start = new BlockPos(cx, base, cz);
        return Optional.of(new Structure.GenerationStub(start, builder -> builder.addPiece(new SunTemplePiece(cx - SunTemplePiece.CENTER, base, cz - SunTemplePiece.CENTER))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SUN_TEMPLE.get();
    }
}

package com.grandline.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.tags.FluidTags;

/**
 * Paints the loaded terrain around a point into an image the way a vanilla map does: each column's top block colour,
 * lit by its height against the column to the north, water shaded by depth. Unloaded columns are left transparent.
 * One pixel per block. Used by the minimap and by the sea chart's close-up view.
 */
public final class TerrainSampler {
    private TerrainSampler() {}

    /** Fills {@code img} (size × size) with the terrain centred on (cx, cz). */
    public static void sample(ClientLevel level, int cx, int cz, int size, NativeImage img) {
        int x0 = cx - size / 2, z0 = cz - size / 2;
        int[] prev = new int[size];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < size; i++) prev[i] = column(level, x0 + i, z0 - 1);
        for (int j = 0; j < size; j++) {
            int z = z0 + j;
            for (int i = 0; i < size; i++) {
                int x = x0 + i;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    img.setPixelRGBA(i, j, 0);
                    prev[i] = Integer.MIN_VALUE;
                    continue;
                }
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                p.set(x, h, z);
                var state = level.getBlockState(p);
                MapColor mc = state.getMapColor(level, p);
                MapColor.Brightness b;
                FluidState fluid = state.getFluidState();
                if (fluid.is(FluidTags.WATER)) {
                    int depth = 0;
                    while (depth < 12 && level.getBlockState(p.move(0, -1, 0)).getFluidState().is(FluidTags.WATER)) depth++;
                    mc = MapColor.WATER;
                    b = depth < 2 ? MapColor.Brightness.HIGH : depth < 5 ? MapColor.Brightness.NORMAL : depth < 9 ? MapColor.Brightness.LOW : MapColor.Brightness.LOWEST;
                } else {
                    int north = prev[i];
                    b = north == Integer.MIN_VALUE || h == north ? MapColor.Brightness.NORMAL : h > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                    if (mc == MapColor.NONE) mc = MapColor.STONE;
                }
                img.setPixelRGBA(i, j, mc.calculateRGBColor(b));
                prev[i] = h;
            }
        }
    }

    private static int column(ClientLevel level, int x, int z) {
        return level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1 : Integer.MIN_VALUE;
    }
}

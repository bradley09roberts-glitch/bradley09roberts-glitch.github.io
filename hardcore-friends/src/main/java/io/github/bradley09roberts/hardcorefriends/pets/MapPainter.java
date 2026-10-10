package io.github.bradley09roberts.hardcorefriends.pets;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Fills in a map as a friend walks, the way a map in a player's hand fills in. The game's own map update
 * ({@code MapItem.update}) only works for a player holding the map, so this is the same drawing (the same colours,
 * shading and water depth) with the friend as the one holding it, and two differences that keep it safe: it only
 * looks at chunks that are already loaded (a friend never makes the game load or generate land just to draw it), and
 * it draws a smaller circle round the friend ({@code radius} blocks), one sixteenth of the columns per call, so each
 * call costs a few hundred block lookups. Maps of dimensions with a ceiling (the Nether) are not drawn.
 */
final class MapPainter {
	private MapPainter() {
	}

	/**
	 * Draws one slice of the land round {@code holder} onto the map. {@code step} picks the slice (call with a counter
	 * that goes up by one each time). Returns true if any pixel changed.
	 */
	static boolean paint(ServerLevel level, MapItemSavedData data, Entity holder, int step, int radiusBlocks) {
		if (level.dimension() != data.dimension || level.dimensionType().hasCeiling() || data.locked) {
			return false;
		}
		int scale = 1 << data.scale;
		int centreX = data.centerX;
		int centreZ = data.centerZ;
		int holderX = Mth.floor(holder.getX() - centreX) / scale + 64;
		int holderY = Mth.floor(holder.getZ() - centreZ) / scale + 64;
		int radius = Math.max(4, radiusBlocks / scale);
		if (holderX + radius < 0 || holderX - radius >= 128 || holderY + radius < 0 || holderY - radius >= 128) {
			return false; // the friend is too far off this map to draw any of it
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
		boolean changed = false;
		boolean consecutive = false;
		for (int imgX = holderX - radius + 1; imgX < holderX + radius; imgX++) {
			if ((imgX & 15) != (step & 15) && !consecutive) {
				continue;
			}
			consecutive = false;
			double previousHeight = 0.0;
			for (int imgY = holderY - radius - 1; imgY < holderY + radius; imgY++) {
				if (imgX < 0 || imgY < -1 || imgX >= 128 || imgY >= 128) {
					continue;
				}
				int distSqr = Mth.square(imgX - holderX) + Mth.square(imgY - holderY);
				boolean ditherBlack = distSqr > (radius - 2) * (radius - 2);
				int minX = (centreX / scale + imgX - 64) * scale;
				int minZ = (centreZ / scale + imgY - 64) * scale;
				// Only land already loaded: never load or generate a chunk to draw it.
				LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(minX), SectionPos.blockToSectionCoord(minZ));
				if (chunk == null || chunk.isEmpty()) {
					continue;
				}
				Multiset<MapColor> colours = LinkedHashMultiset.create();
				int waterDepth = 0;
				double averageHeight = 0.0;
				for (int dx = 0; dx < scale; dx++) {
					for (int dz = 0; dz < scale; dz++) {
						pos.set(minX + dx, 0, minZ + dz);
						int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) + 1;
						BlockState state;
						if (y <= level.getMinY()) {
							state = Blocks.BEDROCK.defaultBlockState();
						} else {
							do {
								pos.setY(--y);
								state = chunk.getBlockState(pos);
							} while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinY());
							if (y > level.getMinY() && !state.getFluidState().isEmpty()) {
								int solidY = y - 1;
								below.set(pos);
								BlockState under;
								do {
									below.setY(solidY--);
									under = chunk.getBlockState(below);
									waterDepth++;
								} while (solidY > level.getMinY() && !under.getFluidState().isEmpty());
								state = fluidFace(level, state, pos);
							}
						}
						data.checkBanners(level, pos.getX(), pos.getZ());
						averageHeight += (double) y / (scale * scale);
						colours.add(state.getMapColor(level, pos));
					}
				}
				waterDepth /= scale * scale;
				MapColor colour = Iterables.getFirst(Multisets.copyHighestCountFirst(colours), MapColor.NONE);
				MapColor.Brightness brightness;
				if (colour == MapColor.WATER) {
					double diff = waterDepth * 0.1 + (imgX + imgY & 1) * 0.2;
					brightness = diff < 0.5 ? MapColor.Brightness.HIGH : diff > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				} else {
					double diff = (averageHeight - previousHeight) * 4.0 / (scale + 4) + ((imgX + imgY & 1) - 0.5) * 0.4;
					brightness = diff > 0.6 ? MapColor.Brightness.HIGH : diff < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				}
				previousHeight = averageHeight;
				if (imgY >= 0 && distSqr < radius * radius && (!ditherBlack || (imgX + imgY & 1) != 0)) {
					boolean now = data.updateColor(imgX, imgY, colour.getPackedId(brightness));
					consecutive |= now;
					changed |= now;
				}
			}
		}
		return changed;
	}

	/** How the surface of water or lava shows on a map: the fluid's own colour unless a solid top covers it. */
	private static BlockState fluidFace(ServerLevel level, BlockState state, BlockPos pos) {
		FluidState fluid = state.getFluidState();
		return !fluid.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluid.createLegacyBlock() : state;
	}

	/** Share of the map's 128 x 128 pixels drawn so far, 0 to 1. */
	static float coverage(MapItemSavedData data) {
		int drawn = 0;
		for (byte b : data.colors) {
			if (b != 0) {
				drawn++;
			}
		}
		return drawn / (float) data.colors.length;
	}
}

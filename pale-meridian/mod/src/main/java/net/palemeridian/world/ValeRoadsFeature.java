package net.palemeridian.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Draws the valley's old roads and their lamp posts, one chunk at a time. Lamp posts carry a waxed
 * copper bulb that generates unlit; the restoration system lights them when a district is restored.
 */
public final class ValeRoadsFeature extends Feature<NoneFeatureConfiguration> {
	public ValeRoadsFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	private static int hash(int x, int z) {
		int h = x * 0x27d4eb2d ^ z * 0x165667b1;
		h ^= h >>> 15;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		return h;
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		BlockPos origin = context.origin();
		int minX = origin.getX() & ~15, minZ = origin.getZ() & ~15;
		ValleyTerrain terrain = ValleyTerrain.get();
		if (terrain.valleyWeight(minX + 8, minZ + 8) <= 0.0) {
			return false;
		}
		boolean placed = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (ValleyLayout.Road road : terrain.layout().roads) {
			double half = road.width() * 0.5;
			double[][] p = road.points();
			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					int x = minX + dx, z = minZ + dz;
					double d = distanceToRoad(p, x + 0.5, z + 0.5);
					if (d > half + 1.0) {
						continue;
					}
					int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
					pos.set(x, top, z);
					BlockState ground = level.getBlockState(pos);
					if (!ground.getFluidState().isEmpty() || !(ground.is(BlockTags.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.SAND)
						|| ground.is(Blocks.GRAVEL) || ground.is(Blocks.MUD) || ground.is(Blocks.PALE_MOSS_BLOCK) || ground.is(Blocks.MOSS_BLOCK))) {
						continue;
					}
					int hsh = hash(x, z);
					BlockState surface;
					if (d <= half) {
						surface = (hsh & 15) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
					} else {
						int r = hsh & 7;
						if (r > 2) {
							continue;
						}
						surface = r == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
					}
					level.setBlock(pos, surface, 2);
					for (int up = 1; up <= 2; up++) {
						pos.set(x, top + up, z);
						BlockState above = level.getBlockState(pos);
						if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
						}
					}
					placed = true;
				}
			}
		}
		for (LampPosts.Post post : LampPosts.all()) {
			if (post.x() < minX || post.x() >= minX + 16 || post.z() < minZ || post.z() >= minZ + 16) {
				continue;
			}
			int base = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, post.x(), post.z());
			pos.set(post.x(), base - 1, post.z());
			BlockState ground = level.getBlockState(pos);
			if (!ground.getFluidState().isEmpty() || ground.isAir() || ground.canBeReplaced()) {
				continue;
			}
			placeColumn(level, pos.set(post.x(), base, post.z()), Blocks.STONE_BRICK_WALL.defaultBlockState());
			placeColumn(level, pos.set(post.x(), base + 1, post.z()), Blocks.PALE_OAK_FENCE.defaultBlockState());
			placeColumn(level, pos.set(post.x(), base + 2, post.z()), Blocks.PALE_OAK_FENCE.defaultBlockState());
			placeColumn(level, pos.set(post.x(), base + 3, post.z()),
				Blocks.COPPER_BULB.waxed().exposed().defaultBlockState().setValue(CopperBulbBlock.LIT, false));
			placeColumn(level, pos.set(post.x(), base + 4, post.z()), Blocks.STONE_BRICK_SLAB.defaultBlockState());
			placed = true;
		}
		return placed;
	}

	private static void placeColumn(WorldGenLevel level, BlockPos pos, BlockState state) {
		if (level.getFluidState(pos).is(Fluids.WATER)) {
			return;
		}
		level.setBlock(pos, state, 2);
	}

	static double distanceToRoad(double[][] p, double x, double z) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 1 < p.length; i++) {
			double ax = p[i][0], az = p[i][1], bx = p[i + 1][0], bz = p[i + 1][1];
			double vx = bx - ax, vz = bz - az;
			double len2 = vx * vx + vz * vz;
			double t = len2 <= 0.0 ? 0.0 : ((x - ax) * vx + (z - az) * vz) / len2;
			t = Math.max(0.0, Math.min(1.0, t));
			double d = Math.hypot(x - (ax + vx * t), z - (az + vz * t));
			if (d < best) {
				best = d;
			}
		}
		return best;
	}
}

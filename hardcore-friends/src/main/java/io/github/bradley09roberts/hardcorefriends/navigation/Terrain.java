package io.github.bradley09roberts.hardcorefriends.navigation;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.phys.Vec3;

/**
 * What the ground is like at a spot, shared by the pathfinding, the senses and the ways out of trouble: whether a spot
 * is underground (sky-less and well under the top of its column, in a world that has a sky), whether a friend could
 * stand there, which way a current runs, and a safe spot to put a friend down. Everything here only reads chunks that
 * are already loaded: an unloaded spot is never loaded (or generated) to answer a question.
 */
public final class Terrain {
	/** Sky light at or below this counts as sky-less: a cave. Above it is open enough (a cave mouth, a ravine, an overhang). */
	static final int SKYLESS = 6;
	/** A spot at least this many blocks under the top of its column can be underground; nearer the top it is the surface. */
	static final int COVER = 3;

	private Terrain() {
	}

	/**
	 * True in a world with a sky and no ceiling (the Overworld). In the Nether there is no sky light at all and the
	 * roof tops every column, so "underground" means nothing there, and nothing about caves applies.
	 */
	public static boolean caveAware(Level level) {
		return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
	}

	/**
	 * True when an open spot (somewhere a friend could be, such as their feet) is underground: in a world with a sky, a
	 * few blocks or more under the top of its column, and with little or no sky light reaching it. A room with windows,
	 * the shade of a tree or an overhang open to the side is not underground; a cave, a mine tunnel or a sealed
	 * cellar is. False for a spot that is not loaded.
	 */
	public static boolean underground(Level level, BlockPos pos) {
		if (!caveAware(level) || !level.isLoaded(pos)) {
			return false;
		}
		if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - COVER) {
			return false;
		}
		return level.getBrightness(LightLayer.SKY, pos) <= SKYLESS;
	}

	/**
	 * Like {@link #underground} for a place a job is heading to, which may be a solid block (a log, an ore, a chest):
	 * solid blocks hold no light, so the open spots beside and above it decide. An ore in a cave wall is underground;
	 * the foot of a tree is not.
	 */
	public static boolean undergroundTarget(Level level, BlockPos pos) {
		if (!caveAware(level) || !level.isLoaded(pos)) {
			return false;
		}
		if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - COVER) {
			return false;
		}
		int sky = level.getBrightness(LightLayer.SKY, pos);
		for (Direction d : Direction.values()) {
			if (d != Direction.DOWN && sky <= SKYLESS) {
				BlockPos side = pos.relative(d);
				if (level.isLoaded(side)) {
					sky = Math.max(sky, level.getBrightness(LightLayer.SKY, side));
				}
			}
		}
		return sky <= SKYLESS;
	}

	/** True for blocks that hurt to stand in or on: fire, lava, magma, a lit campfire, cactus, berry bushes, powder snow. */
	public static boolean hazard(BlockState state) {
		return NodeEvaluator.isBurningBlock(state) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
			|| state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE) || state.getFluidState().is(FluidTags.LAVA);
	}

	/** True when nothing at this block gets in a body's way (air, grass, flowers, an open door's gap...). */
	public static boolean passable(LevelReader level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/**
	 * True if a friend could stand with their feet here: loaded, firm ground below, room for the body, no fluid and
	 * nothing that hurts.
	 */
	public static boolean standable(LevelReader level, BlockPos feet) {
		if (!level.hasChunkAt(feet)) {
			return false;
		}
		BlockPos belowPos = feet.below();
		BlockState below = level.getBlockState(belowPos);
		BlockState at = level.getBlockState(feet);
		BlockState head = level.getBlockState(feet.above());
		return below.isFaceSturdy(level, belowPos, Direction.UP) && !hazard(below) && !below.is(BlockTags.LEAVES)
			&& at.getCollisionShape(level, feet).isEmpty() && head.getCollisionShape(level, feet.above()).isEmpty()
			&& at.getFluidState().isEmpty() && head.getFluidState().isEmpty() && !hazard(at) && !hazard(head);
	}

	/**
	 * Like {@link #standable}, but any floor with something to stand on will do (a slab, a dirt path, farmland, the top
	 * of a stair), as when a friend walks: for spots to step or climb onto, rather than spots to be put down on.
	 */
	public static boolean canStand(LevelReader level, BlockPos feet) {
		if (!level.hasChunkAt(feet)) {
			return false;
		}
		BlockPos belowPos = feet.below();
		BlockState below = level.getBlockState(belowPos);
		BlockState at = level.getBlockState(feet);
		BlockState head = level.getBlockState(feet.above());
		return !below.getCollisionShape(level, belowPos).isEmpty() && !hazard(below) && !below.is(BlockTags.LEAVES)
			&& below.getFluidState().isEmpty() && at.getCollisionShape(level, feet).isEmpty()
			&& head.getCollisionShape(level, feet.above()).isEmpty() && at.getFluidState().isEmpty()
			&& head.getFluidState().isEmpty() && !hazard(at) && !hazard(head);
	}

	/** True when lava is within a block of this spot (any of the 26 blocks round it, or the spot itself). */
	public static boolean nearLava(LevelReader level, BlockPos pos) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					m.setWithOffset(pos, dx, dy, dz);
					if (level.hasChunkAt(m) && level.getFluidState(m).is(FluidTags.LAVA)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** True for water that is falling (a waterfall or a spring running down a cliff): it carries anyone in it down. */
	public static boolean fallingWater(FluidState fluid) {
		return fluid.is(FluidTags.WATER) && !fluid.isSource() && fluid.getValueOrElse(FlowingFluid.FALLING, false);
	}

	/** True for water that is running (not still): a stream, a river's current, the spread from a spring. */
	public static boolean runningWater(FluidState fluid) {
		return fluid.is(FluidTags.WATER) && !fluid.isSource();
	}

	/** Which way the water at this spot pushes, flat (zero for still water or no water). */
	public static Vec3 current(Level level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return Vec3.ZERO;
		}
		FluidState fluid = level.getFluidState(pos);
		if (!fluid.is(FluidTags.WATER) || fluid.isSource() && !fluid.getValueOrElse(FlowingFluid.FALLING, false)) {
			return Vec3.ZERO;
		}
		Vec3 flow = fluid.getFlow(level, pos);
		return new Vec3(flow.x, 0, flow.z);
	}

	/**
	 * The height of the first air block above the water a friend at {@code feet} is in (at most four blocks up), or the
	 * feet's own height when they are not in water.
	 */
	public static int waterSurface(Level level, BlockPos feet) {
		BlockPos.MutableBlockPos m = feet.mutable();
		for (int i = 0; i < 4 && level.isLoaded(m) && level.getFluidState(m).is(FluidTags.WATER); i++) {
			m.move(Direction.UP);
		}
		return m.getY();
	}

	/**
	 * A safe standing spot near {@code base}, nearest first, within {@code radius} blocks across and three up or down:
	 * firm, dry, nothing that hurts, no lava within a block, and in land that is loaded and ticking (so a friend put down
	 * there carries on living). Null when there is none.
	 */
	public static @Nullable BlockPos safeSpotNear(ServerLevel level, BlockPos base, int radius) {
		return safeSpotNear(level, base, radius, p -> true);
	}

	/** Like {@link #safeSpotNear(ServerLevel, BlockPos, int)}, keeping only spots {@code also} accepts. */
	public static @Nullable BlockPos safeSpotNear(ServerLevel level, BlockPos base, int radius, Predicate<BlockPos> also) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int[] heights = {0, 1, -1, 2, -2, 3, -3};
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue; // only the ring at this distance; the inner rings were tried already
					}
					for (int dy : heights) {
						m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
						if (level.isPositionEntityTicking(m) && standable(level, m) && !nearLava(level, m)) {
							BlockPos spot = m.immutable();
							if (also.test(spot)) {
								return spot;
							}
						}
					}
				}
			}
		}
		return null;
	}
}

package io.github.bradley09roberts.hardcorefriends.ai.role.mine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/** Small, bounded block checks shared by Flint's routines: standing spots, exposed ores, pickaxes and torches. */
public final class MiningHelper {
	/** Below this light level at his feet Flint lights the spot with a torch, if he carries one. */
	public static final int MIN_LIGHT = 7;
	/** How close (eye to block centre) a standing spot must be to work on a block, with a little margin. */
	public static final double WORK_REACH = 4.0;

	private MiningHelper() {
	}

	/** Nothing to bump into and no fluid: air, torches, short grass and similar. */
	public static boolean isPassable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
	}

	/** A dry block with a full top face to stand on. */
	public static boolean isSolidFloor(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
	}

	/** Solid floor below and two passable blocks for the body. */
	public static boolean isStandable(ServerLevel level, BlockPos feet) {
		return isSolidFloor(level, feet.below()) && isPassable(level, feet) && isPassable(level, feet.above());
	}

	/** True if at least one face of the block touches air (so it can be seen and reached). */
	public static boolean touchesAir(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (level.getBlockState(pos.relative(d)).isAir()) {
				return true;
			}
		}
		return false;
	}

	public static boolean isWantedOre(BlockState state) {
		return state.is(ModTags.WANTED_ORES);
	}

	/** True when a friend standing at {@code feet} could reach the centre of {@code target}. */
	public static boolean reachableFrom(BlockPos feet, BlockPos target, double reach) {
		double dx = feet.getX() + 0.5 - (target.getX() + 0.5);
		double dy = feet.getY() + 1.62 - (target.getY() + 0.5);
		double dz = feet.getZ() + 0.5 - (target.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz <= reach * reach;
	}

	/**
	 * The standing spot closest to {@code near} from which {@code target} can be worked: solid floor, two free
	 * blocks, within reach. Never the spot on top of the target, so Flint does not dig out his own floor and drop
	 * into a shaft or a cave. Checks at most 7×5×7 positions.
	 */
	public static @Nullable BlockPos standSpotFor(ServerLevel level, BlockPos target, BlockPos near) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				for (int dy = -3; dy <= 1; dy++) {
					feet.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (feet.equals(target) || feet.above().equals(target) || isOnTop(feet, target) || !level.isLoaded(feet)) {
						continue;
					}
					if (!reachableFrom(feet, target, WORK_REACH) || !isStandable(level, feet)) {
						continue;
					}
					double d = feet.distSqr(near);
					if (d < bestDist) {
						bestDist = d;
						best = feet.immutable();
					}
				}
			}
		}
		return best;
	}

	/** True when a friend standing at {@code feet} stands directly on {@code target}: breaking it drops them. */
	public static boolean isOnTop(BlockPos feet, BlockPos target) {
		return feet.getX() == target.getX() && feet.getZ() == target.getZ() && feet.getY() == target.getY() + 1;
	}

	public static boolean isPickaxe(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES);
	}

	public static boolean hasAnyPickaxe(CompanionEntity c) {
		return c.actions().has(MiningHelper::isPickaxe);
	}

	/** True if a carried pickaxe gets drops from this block. */
	public static boolean hasPickaxeFor(CompanionEntity c, BlockState state) {
		return c.actions().has(s -> isPickaxe(s) && s.isCorrectToolForDrops(state));
	}

	/** True if this block can be dug without wasting it: no tool needed, or a correct one is carried. */
	public static boolean canHarvest(CompanionEntity c, BlockState state) {
		return !state.requiresCorrectToolForDrops() || c.actions().has(s -> s.isCorrectToolForDrops(state));
	}

	/** Raw iron, copper or gold: what Flint takes to the furnace. */
	public static boolean isRawOre(ItemStack stack) {
		return stack.is(Items.RAW_IRON) || stack.is(Items.RAW_COPPER) || stack.is(Items.RAW_GOLD);
	}

	/**
	 * Lights the spot at Flint's feet with a floor torch from his backpack when the light there is below
	 * {@value #MIN_LIGHT}. Returns false only when the guard's edit pacing says to try again next tick.
	 */
	public static boolean lightIfDark(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos feet = c.blockPosition();
		BlockState torch = Blocks.TORCH.defaultBlockState();
		if (level.getMaxLocalRawBrightness(feet) >= MIN_LIGHT || !c.backpack().has(s -> s.is(Items.TORCH))
			|| !level.getBlockState(feet).isAir() || !torch.canSurvive(level, feet)) {
			return true;
		}
		WorldEditGuard.Verdict verdict = WorldEditGuard.canPlace(c, feet, torch, WorldEditGuard.Reason.MINE);
		if (!verdict.allowed() && "pacing".equals(verdict.why())) {
			return false;
		}
		placeTorch(c, feet);
		return true;
	}

	/** Places one carried torch on the floor at {@code pos} (Reason.MINE). */
	public static boolean placeTorch(CompanionEntity c, BlockPos pos) {
		ServerLevel level = (ServerLevel) c.level();
		BlockState torch = Blocks.TORCH.defaultBlockState();
		if (!level.getBlockState(pos).isAir() || !torch.canSurvive(level, pos)) {
			return false;
		}
		return c.actions().place(pos, torch, s -> s.is(Items.TORCH), WorldEditGuard.Reason.MINE);
	}
}

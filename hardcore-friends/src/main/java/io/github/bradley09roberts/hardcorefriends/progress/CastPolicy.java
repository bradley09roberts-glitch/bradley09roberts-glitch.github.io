package io.github.bradley09roberts.hardcorefriends.progress;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The rules for {@link WorldEditGuard.Reason#CAST}: pouring water on lava to make obsidian, and mining that obsidian
 * back out. Narrow on purpose:
 * <ul>
 * <li><b>Casting</b> turns one still lava source into obsidian: only a source block of plain lava (never a cauldron),
 * in the camp's dimension, inside the gathering ring, with nothing player-built within {@value #BUILD_GAP} blocks, and
 * never lava that is pouring down into something (a source with air under it or a falling stream beside it). The lava
 * must also look natural ({@link #looksNatural}): deep down or under a roof, never lava open to the sky above
 * y = {@value #OPEN_LAVA_MAX_Y} (it may be a player's moat or pool), and touching nothing but air, fluid and natural
 * ground (never a tank or a channel a player made).</li>
 * <li><b>Mining</b> takes out only obsidian the friends cast themselves (remembered in {@link ProgressData}), inside the
 * gathering ring, away from player builds; the guard's own checks still refuse a block that touches water or lava.</li>
 * <li>Nothing is ever placed with this reason.</li>
 * </ul>
 */
public final class CastPolicy implements WorldEditGuard.Policy {
	/** No casting within this many blocks of anything that looks player-built. */
	public static final int BUILD_GAP = 4;
	/** Lava open to the sky at or above this height is never cast: at the surface it may be a player's moat or pool. */
	public static final int OPEN_LAVA_MAX_Y = 40;

	@Override
	public WorldEditGuard.Verdict canBreak(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState state) {
		if (!FriendsConfig.get().allowMining) {
			return WorldEditGuard.Verdict.deny("mining disabled in config");
		}
		if (!state.is(Blocks.OBSIDIAN) || !ProgressData.get(level.getServer()).isCast(level, pos)) {
			return WorldEditGuard.Verdict.deny("only obsidian we made ourselves");
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || !WorldEditGuard.inResourceZone(c, pos)) {
			return WorldEditGuard.Verdict.deny("outside the gathering area");
		}
		if (WorldEditGuard.looksPlayerBuilt(level, pos, 2, data)) {
			return WorldEditGuard.Verdict.deny("too close to a build");
		}
		return WorldEditGuard.Verdict.OK;
	}

	@Override
	public boolean mayBreakProtected(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState state) {
		return state.is(Blocks.OBSIDIAN) && ProgressData.get(level.getServer()).isCast(level, pos);
	}

	@Override
	public WorldEditGuard.Verdict canPlace(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
		return WorldEditGuard.Verdict.deny("casting places nothing");
	}

	@Override
	public WorldEditGuard.Verdict canTransform(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
		if (!newState.is(Blocks.OBSIDIAN)) {
			return WorldEditGuard.Verdict.deny("casting only makes obsidian");
		}
		return castRule(c, level, pos);
	}

	/**
	 * The rule for casting one lava source into obsidian, on its own (without the guard's common checks such as its
	 * edit pacing), so a job can ask it while planning.
	 */
	public static WorldEditGuard.Verdict castRule(CompanionEntity c, ServerLevel level, BlockPos pos) {
		if (!FriendsConfig.get().allowMining) {
			return WorldEditGuard.Verdict.deny("mining disabled in config");
		}
		BlockState current = level.getBlockState(pos);
		if (!isStillLavaSource(level, pos, current)) {
			return WorldEditGuard.Verdict.deny("not a still lava source");
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || !WorldEditGuard.inResourceZone(c, pos)) {
			return WorldEditGuard.Verdict.deny("outside the gathering area");
		}
		if (!looksNatural(level, pos, data)) {
			return WorldEditGuard.Verdict.deny("lava a player may have put there");
		}
		if (WorldEditGuard.looksPlayerBuilt(level, pos, BUILD_GAP, data)) {
			return WorldEditGuard.Verdict.deny("too close to a build");
		}
		return WorldEditGuard.Verdict.OK;
	}

	/**
	 * True when this lava looks natural as far as the world can show. Players put lava in moats, pools and tanks, so
	 * lava open to the sky at or above y = {@value #OPEN_LAVA_MAX_Y} never counts (lava lakes in caves and deep down
	 * do), and nothing but air, water, lava and natural ground may touch it: no glass, no placed blocks (bar the
	 * friends' own), no cobblestone a player laid, no obsidian above that height unless the friends cast it. Unloaded
	 * neighbours count as not natural.
	 */
	public static boolean looksNatural(ServerLevel level, BlockPos pos, CampData data) {
		if (pos.getY() >= OPEN_LAVA_MAX_Y
			&& pos.getY() + 1 >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ())) {
			return false; // nothing over it: up here that may be a player's moat or pool
		}
		ProgressData progress = ProgressData.get(level.getServer());
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (!level.isLoaded(n)) {
				return false;
			}
			BlockState s = level.getBlockState(n);
			boolean natural = s.isAir() || s.is(Blocks.LAVA) || s.is(Blocks.WATER) || s.is(ModTags.MINEABLE_NATURAL)
				|| s.is(ModTags.EARTH_GATHERABLE) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT)
				|| s.is(Blocks.MAGMA_BLOCK)
				|| (s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(ModTags.BUILD_MARKERS)) // grass, snow
				// obsidian: the friends' own, or deep down where lava meeting water makes it
				|| (s.is(Blocks.OBSIDIAN) && (n.getY() < OPEN_LAVA_MAX_Y || progress.isCast(level, n)))
				|| data.isPlacedByFriends(level, n);
			if (!natural) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A plain lava source block that is not pouring anywhere: solid ground or more lava under it, and no falling lava
	 * beside it. Unloaded neighbours count as unsafe.
	 */
	public static boolean isStillLavaSource(ServerLevel level, BlockPos pos, BlockState state) {
		if (!state.is(Blocks.LAVA) || !state.getFluidState().isSource()) {
			return false;
		}
		BlockPos below = pos.below();
		if (!level.isLoaded(below)) {
			return false;
		}
		BlockState under = level.getBlockState(below);
		if (under.isAir() || under.getFluidState().is(FluidTags.LAVA) && !under.getFluidState().isSource()) {
			return false; // it feeds lava falling away below
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = pos.relative(d);
			if (!level.isLoaded(n)) {
				return false;
			}
			FluidState fluid = level.getFluidState(n);
			if (fluid.is(FluidTags.LAVA) && !fluid.isSource() && fluid.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING)) {
				return false; // a lavafall runs beside it
			}
		}
		return true;
	}
}

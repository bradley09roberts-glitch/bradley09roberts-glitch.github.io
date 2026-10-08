package io.github.bradley09roberts.hardcorefriends.progress;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The rules for {@link WorldEditGuard.Reason#CAST}: pouring water on lava to make obsidian, and mining that obsidian
 * back out. Narrow on purpose:
 * <ul>
 * <li><b>Casting</b> turns one still lava source into obsidian: only a source block of plain lava (never a cauldron),
 * in the camp's dimension, inside the gathering ring, with nothing player-built within {@value #BUILD_GAP} blocks, and
 * never lava that is pouring down into something (a source with air under it or a falling stream beside it).</li>
 * <li><b>Mining</b> takes out only obsidian the friends cast themselves (remembered in {@link ProgressData}), inside the
 * gathering ring, away from player builds; the guard's own checks still refuse a block that touches water or lava.</li>
 * <li>Nothing is ever placed with this reason.</li>
 * </ul>
 */
public final class CastPolicy implements WorldEditGuard.Policy {
	/** No casting within this many blocks of anything that looks player-built. */
	public static final int BUILD_GAP = 4;

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
		if (!FriendsConfig.get().allowMining) {
			return WorldEditGuard.Verdict.deny("mining disabled in config");
		}
		if (!newState.is(Blocks.OBSIDIAN)) {
			return WorldEditGuard.Verdict.deny("casting only makes obsidian");
		}
		BlockState current = level.getBlockState(pos);
		if (!isStillLavaSource(level, pos, current)) {
			return WorldEditGuard.Verdict.deny("not a still lava source");
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || !WorldEditGuard.inResourceZone(c, pos)) {
			return WorldEditGuard.Verdict.deny("outside the gathering area");
		}
		if (WorldEditGuard.looksPlayerBuilt(level, pos, BUILD_GAP, data)) {
			return WorldEditGuard.Verdict.deny("too close to a build");
		}
		return WorldEditGuard.Verdict.OK;
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

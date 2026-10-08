package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Verdict;

/**
 * The edit rules of the two reasons this package owns, registered in {@link WorldEditGuard#POLICIES}. The guard's
 * own checks (editing allowed, the chunk loaded, pacing, no player right there; for breaking also no block entity,
 * nothing protected and no water or lava let in) have already passed when these are asked.
 */
final class SurvivalPolicies {
	/** Nothing is placed or dug this close to anything player-built to stay alive. */
	static final int BUILD_GAP = 6;

	private SurvivalPolicies() {
	}

	/** Registers both rules. */
	static void register() {
		WorldEditGuard.POLICIES.put(WorldEditGuard.Reason.GRADE, new Grade());
		WorldEditGuard.POLICIES.put(WorldEditGuard.Reason.SURVIVAL, new Survival());
	}

	/**
	 * Levelling a building site ({@code GRADE}): only inside a levelling plan's box, digging only natural earth, sand,
	 * gravel, stone and plants (or the friends' own fill) away from anything player-built, and filling only the
	 * plan's own spaces with dirt, coarse dirt, cobblestone or stone. Off when terraforming is switched off.
	 */
	static final class Grade implements WorldEditGuard.Policy {
		@Override
		public Verdict canBreak(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState state) {
			if (!FriendsConfig.get().allowTerraforming) {
				return Verdict.deny("levelling ground is switched off");
			}
			CampData data = Camp.data(level.getServer());
			if (!Camp.isCampLevel(level, data)) {
				return Verdict.deny("not at the camp");
			}
			Optional<SiteGrading.Job> plan = SiteGrading.planAt(data, pos);
			if (plan.isEmpty()) {
				return Verdict.deny("not part of a site being levelled");
			}
			boolean ownFill = data.isPlacedByFriends(level, pos) && SiteGrading.isFillBlock(state);
			if (!SiteGrading.isGradeable(state) && !ownFill) {
				return Verdict.deny("only natural earth, sand, gravel, stone and plants may be dug away");
			}
			if (!ownFill && WorldEditGuard.looksPlayerBuilt(level, pos, 2, data)) {
				return Verdict.deny("too close to a build");
			}
			return Verdict.OK;
		}

		@Override
		public Verdict canPlace(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
			if (!FriendsConfig.get().allowTerraforming) {
				return Verdict.deny("levelling ground is switched off");
			}
			CampData data = Camp.data(level.getServer());
			if (!Camp.isCampLevel(level, data)) {
				return Verdict.deny("not at the camp");
			}
			Optional<SiteGrading.Job> plan = SiteGrading.planAt(data, pos);
			if (plan.isEmpty() || !plan.get().fill().contains(pos.asLong())) {
				return Verdict.deny("not a dip of a site being levelled");
			}
			return SiteGrading.isFillBlock(newState) ? Verdict.OK : Verdict.deny("dips are filled with dirt or cobblestone");
		}
	}

	/**
	 * Staying alive away from camp ({@code SURVIVAL}): placing only dirt, cobblestone, stone, a torch or water (to
	 * break a fall) right around the friend, never within {@value #BUILD_GAP} blocks of anything player-built and never
	 * water where it would boil away; breaking only the pieces the friend placed themselves, or the few natural blocks
	 * they planned to dig to get under cover; taking back only their own water.
	 */
	static final class Survival implements WorldEditGuard.Policy {
		@Override
		public Verdict canBreak(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState state) {
			CampData data = Camp.data(level.getServer());
			if (Shelters.isPiece(c, pos) && near(c, pos, 5)) {
				return data.isPlacedByFriends(level, pos) ? Verdict.OK : Verdict.deny("not our own block any more");
			}
			if (Shelters.mayDig(c, pos) && near(c, pos, 3)) {
				if (!SiteGrading.isGradeable(state)) {
					return Verdict.deny("only natural earth and stone may be dug for cover");
				}
				if (WorldEditGuard.looksPlayerBuilt(level, pos, BUILD_GAP, data)) {
					return Verdict.deny("too close to a build");
				}
				return Verdict.OK;
			}
			return Verdict.deny("not a block we placed or planned to dig");
		}

		@Override
		public Verdict canPlace(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
			if (newState.is(Blocks.WATER)) {
				// A falling friend pours water onto the ground just below them, up to two fast-falling ticks away.
				BlockPos at = c.blockPosition();
				int below = at.getY() - pos.getY();
				if (Math.abs(at.getX() - pos.getX()) > 2 || Math.abs(at.getZ() - pos.getZ()) > 2 || below < 0 || below > 10) {
					return Verdict.deny("too far from the friend");
				}
				if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos)) {
					return Verdict.deny("water boils away here");
				}
			} else if (!near(c, pos, 4)) {
				return Verdict.deny("too far from the friend");
			} else if (!Shelters.isShelterState(newState) && !newState.is(Blocks.TORCH) && !newState.is(Blocks.WALL_TORCH)) {
				return Verdict.deny("only dirt, cobblestone, stone and torches keep a friend safe");
			}
			if (WorldEditGuard.looksPlayerBuilt(level, pos, BUILD_GAP, Camp.data(level.getServer()))) {
				return Verdict.deny("too close to a build");
			}
			return Verdict.OK;
		}

		@Override
		public Verdict canTransform(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
			BlockState current = level.getBlockState(pos);
			boolean ownWater = current.getFluidState().is(FluidTags.WATER) && current.getFluidState().isSource()
				&& Shelters.isPiece(c, pos);
			return ownWater && newState.isAir() && near(c, pos, 6) ? Verdict.OK : Verdict.deny("only our own water is taken back");
		}
	}

	/** True when a position is within {@code reach} blocks of the friend in every direction. */
	static boolean near(CompanionEntity c, BlockPos pos, int reach) {
		BlockPos at = c.blockPosition();
		return Math.abs(at.getX() - pos.getX()) <= reach && Math.abs(at.getY() - pos.getY()) <= reach
			&& Math.abs(at.getZ() - pos.getZ()) <= reach;
	}
}

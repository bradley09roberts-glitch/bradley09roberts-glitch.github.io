package io.github.bradley09roberts.hardcorefriends.expedition;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Verdict;

/**
 * The {@code EXPEDITION} edit rules, registered in {@link WorldEditGuard#POLICIES}. The guard's own checks (editing
 * allowed, the chunk loaded, pacing, no player right there; for breaking also no block entity, nothing protected and
 * no water or lava let in) have already passed. Everything else is refused:
 * <ul>
 * <li><b>The Nether</b>, following a player who is near: nether wart and soul sand (for the camp's brewing), never
 * beside anything player-built (a fortress's own bricks do not count).</li>
 * <li><b>The stronghold</b>, following a player who is near: an eye into an empty end portal frame (nothing else
 * about the frame changes), and portal blocks into the opening of a ring whose twelve frames all hold an eye.</li>
 * <li><b>Scout's marker</b> over the stronghold: cobblestone (or dirt) and a torch, only in the column she was given,
 * only into air, and never within {@value #MARKER_GAP} blocks of anything player-built.</li>
 * <li><b>The End</b>: cobblestone, dirt or netherrack only in the column of the pillar a friend was given beside a
 * caged end crystal, and back out again (only the friends' own blocks); and iron bars within {@value #BARS_REACH}
 * blocks of an end crystal (its cage).</li>
 * </ul>
 */
final class ExpeditionPolicy implements WorldEditGuard.Policy {
	/** Nothing player-built may stand this close to Scout's marker. */
	static final int MARKER_GAP = 6;
	/** Iron bars this close to an end crystal are its cage. */
	static final double BARS_REACH = 3;
	/** The player a friend gathers or fills frames beside must be this close. */
	private static final double WITH_PLAYER = 16;

	@Override
	public Verdict canBreak(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState state) {
		CampData data = Camp.data(level.getServer());
		if (level.dimension() == Level.NETHER) {
			if (!state.is(Blocks.NETHER_WART) && !state.is(Blocks.SOUL_SAND)) {
				return Verdict.deny("only nether wart and soul sand are gathered in the Nether");
			}
			if (!withPlayer(c)) {
				return Verdict.deny("only gathered beside the player they follow");
			}
			if (!inFortress(level, pos) && WorldEditGuard.looksPlayerBuilt(level, pos, 2, data)) {
				return Verdict.deny("too close to a build");
			}
			return Verdict.OK;
		}
		if (level.dimension() == Level.END) {
			if (state.is(Blocks.IRON_BARS)) {
				return nearCrystal(level, pos) ? Verdict.OK : Verdict.deny("only the bars of an end crystal's cage");
			}
			if (DragonFight.isPillarBlock(state) && data.isPlacedByFriends(level, pos)) {
				return Verdict.OK; // taking their own pillar back down
			}
			return Verdict.deny("only cage bars and their own pillar blocks are broken in the End");
		}
		return Verdict.deny("expeditions break nothing here");
	}

	@Override
	public Verdict canPlace(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
		if (!level.getBlockState(pos).isAir()) {
			return Verdict.deny("only into air");
		}
		if (level.dimension() == Level.END) {
			if (!DragonFight.isPillarBlock(newState)) {
				return Verdict.deny("only cobblestone, dirt or netherrack for a pillar");
			}
			return DragonFight.inClimbColumn(c, pos) ? Verdict.OK : Verdict.deny("not the pillar beside a caged crystal");
		}
		if (newState.is(Blocks.END_PORTAL)) {
			return EndPortal.openingAt(level, pos) != null ? Verdict.OK : Verdict.deny("not inside a full End portal frame");
		}
		if (newState.is(Blocks.COBBLESTONE) || newState.is(Blocks.DIRT) || newState.is(Blocks.TORCH)) {
			if (!StrongholdTask.inMarkerColumn(c, pos)) {
				return Verdict.deny("not the stronghold marker's spot");
			}
			if (WorldEditGuard.looksPlayerBuilt(level, pos, MARKER_GAP, Camp.data(level.getServer()))) {
				return Verdict.deny("too close to a build");
			}
			return Verdict.OK;
		}
		return Verdict.deny("expeditions place nothing like that here");
	}

	@Override
	public Verdict canTransform(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
		BlockState current = level.getBlockState(pos);
		if (!EndPortal.emptyFrame(current) || !newState.is(Blocks.END_PORTAL_FRAME)) {
			return Verdict.deny("only an eye into an empty end portal frame");
		}
		if (!newState.getValue(EndPortalFrameBlock.HAS_EYE)
			|| newState.getValue(EndPortalFrameBlock.FACING) != current.getValue(EndPortalFrameBlock.FACING)) {
			return Verdict.deny("only the eye changes");
		}
		return withPlayer(c) ? Verdict.OK : Verdict.deny("only beside the player they follow");
	}

	/** Following a player who is close by in the same dimension. */
	private static boolean withPlayer(CompanionEntity c) {
		ServerPlayer leader = c.leader();
		return c.mode() == CompanionMode.FOLLOW && leader != null && leader.isAlive() && leader.level() == c.level()
			&& leader.distanceToSqr(c) <= WITH_PLAYER * WITH_PLAYER;
	}

	/** True inside a Nether fortress's bounds (its bricks, fences and stairs are not anybody's build). */
	static boolean inFortress(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(BuiltinStructures.FORTRESS)).isValid();
	}

	/** True when an end crystal is within {@value #BARS_REACH} blocks. */
	static boolean nearCrystal(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(EndCrystal.class, new AABB(pos).inflate(BARS_REACH), EndCrystal::isAlive).isEmpty();
	}
}

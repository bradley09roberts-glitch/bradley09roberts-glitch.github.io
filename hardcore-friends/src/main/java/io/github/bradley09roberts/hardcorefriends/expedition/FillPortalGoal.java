package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.Milestone;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Filling the End portal. A friend following a player through a stronghold, carrying eyes of ender, sets an eye in
 * each empty end portal frame within reach of the room they are in (one at a time, each eye really used up), and when
 * a ring's twelve frames all hold an eye opens it the way setting the last eye does for a player: the opening fills
 * with portal blocks and the whole world hears it. Only frames and the opening change (the {@code EXPEDITION} rules),
 * and only with the player close by.
 */
public class FillPortalGoal extends Goal {
	private static final double WITH_PLAYER = 16;
	private static final int LOOK_EVERY = 40;
	private static final int GIVE_UP = 20 * 20;

	private final CompanionEntity c;
	private @Nullable BlockPos frame;
	private @Nullable BlockPos opening;
	private int ticks;
	private int lookAgain;

	public FillPortalGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (--lookAgain > 0 || c.level().dimension() == Level.END || !(c.level() instanceof ServerLevel level)) {
			return false;
		}
		lookAgain = LOOK_EVERY;
		if (c.mode() != CompanionMode.FOLLOW || c.getTarget() != null || c.isRetreating() || !withPlayer()) {
			return false;
		}
		if (!EndPortal.inStronghold(level, c.blockPosition())) {
			return false;
		}
		opening = EndPortal.unopened(level, c.blockPosition());
		if (opening != null) {
			return true;
		}
		if (!c.backpack().has(s -> s.is(Items.ENDER_EYE))) {
			return false;
		}
		frame = EndPortal.nearestEmptyFrame(level, c.blockPosition());
		return frame != null;
	}

	@Override
	public boolean canContinueToUse() {
		return (frame != null || opening != null) && ticks < GIVE_UP && c.getTarget() == null && !c.isRetreating() && withPlayer();
	}

	@Override
	public void start() {
		ticks = 0;
	}

	@Override
	public void stop() {
		c.actions().stopWalking();
		frame = null;
		opening = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private boolean withPlayer() {
		ServerPlayer leader = c.leader();
		return leader != null && leader.isAlive() && leader.level() == c.level() && leader.distanceToSqr(c) <= WITH_PLAYER * WITH_PLAYER;
	}

	@Override
	public void tick() {
		ticks++;
		ServerLevel level = (ServerLevel) c.level();
		if (opening != null) {
			open(level);
			return;
		}
		BlockPos at = frame;
		if (at == null) {
			return;
		}
		if (!EndPortal.emptyFrame(level.getBlockState(at))) {
			next(level); // filled meanwhile (by the player, or another friend)
			return;
		}
		if (!c.actions().canReach(at)) {
			c.actions().walkTo(at, 2.0);
			if (c.actions().isStuck()) {
				frame = null;
			}
			return;
		}
		c.actions().stopWalking();
		c.getLookControl().setLookAt(Vec3.atCenterOf(at));
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return;
		}
		ItemStack eye = c.backpack().take(s -> s.is(Items.ENDER_EYE), 1);
		if (eye.isEmpty()) {
			frame = null;
			return;
		}
		BlockState old = level.getBlockState(at);
		BlockState filled = old.setValue(EndPortalFrameBlock.HAS_EYE, true);
		if (!WorldEditGuard.canTransform(c, at, filled, WorldEditGuard.Reason.EXPEDITION).allowed()) {
			c.backpack().insert(eye);
			frame = null;
			return;
		}
		Block.pushEntitiesUp(old, filled, level, at);
		if (!WorldEditGuard.transformBlock(c, at, filled, WorldEditGuard.Reason.EXPEDITION)) {
			ItemStack left = c.backpack().insert(eye);
			if (!left.isEmpty()) {
				c.spawnAtLocation(level, left);
			}
			frame = null;
			return;
		}
		level.updateNeighbourForOutputSignal(at, Blocks.END_PORTAL_FRAME);
		level.levelEvent(1503, at, 0);
		c.swingArm();
		next(level);
	}

	/** On to the next empty frame within reach, or to opening the portal if the ring is full. */
	private void next(ServerLevel level) {
		opening = EndPortal.unopened(level, c.blockPosition());
		frame = opening == null && c.backpack().has(s -> s.is(Items.ENDER_EYE)) ? EndPortal.nearestEmptyFrame(level, c.blockPosition()) : null;
		ticks = 0;
	}

	/** Fills the opening of a full ring with portal blocks, one at a time, as setting the last eye does. */
	private void open(ServerLevel level) {
		BlockPos at = opening;
		if (at == null || level.getGameTime() - c.lastEditTick() < 4) {
			return;
		}
		BlockPos origin = EndPortal.openingAt(level, at);
		if (origin == null) {
			opening = null;
			return;
		}
		if (level.getBlockState(at).isAir()) {
			c.getLookControl().setLookAt(Vec3.atCenterOf(at));
			WorldEditGuard.placeBlock(c, at, Blocks.END_PORTAL.defaultBlockState(), WorldEditGuard.Reason.EXPEDITION);
		}
		BlockPos rest = EndPortal.unopened(level, c.blockPosition());
		if (rest != null && !rest.equals(at)) {
			opening = rest;
			return;
		}
		if (rest != null) {
			return; // try this one again in a moment (the guard paces edits)
		}
		opening = null;
		opened(level, origin);
	}

	/** The portal is open: the world hears it, the friend says so, and Sage's plan moves on. */
	private void opened(ServerLevel level, BlockPos origin) {
		level.globalLevelEvent(1038, origin.offset(1, 0, 1), 0);
		Speech.say(c, Line.PORTAL_FILLED);
		MinecraftServer server = level.getServer();
		ExpeditionData.get(server).addPlace(ExpeditionData.END_PORTAL, Travel.dimId(level), origin.offset(1, 0, 1), level.getGameTime());
		if (ProgressPlan.enabled()) {
			ProgressPlan.complete(server, Milestone.STRONGHOLD);
			ProgressPlan.complete(server, Milestone.END_PORTAL);
		}
	}
}

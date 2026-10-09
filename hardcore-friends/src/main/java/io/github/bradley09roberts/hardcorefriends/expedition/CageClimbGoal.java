package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Opening a caged end crystal's cage. The friend {@link DragonFight} picked walks to the foot of the column beside the
 * tower, pillars straight up it the way players do (jump, and place a block under their feet) to the cage's lowest
 * ring, breaks the iron bars within reach (only bars within three blocks of the crystal: its cage), and comes back
 * down taking every block of the pillar with them; then the archers can shoot the crystal. Badly knocked about, or
 * low on health, they come straight down. A friend found standing on their own pillar with no climb to finish (after
 * the world was closed half way) simply comes down. A reflex above falling back: stepping off a pillar would be a long
 * fall, so nothing interrupts it once begun.
 */
public class CageClimbGoal extends Goal {
	private enum Stage {
		WALK,
		UP,
		BARS,
		DOWN,
		DONE
	}

	private static final int TICKS_PER_JUMP = 12;
	private static final int MAX_TRIES = 4;
	private static final int BARS_TIME = 20 * 60;
	private static final int WALK_TIME = 20 * 45;

	private final CompanionEntity c;
	private Stage stage = Stage.DONE;
	private @Nullable BlockPos jumpFrom;
	private int jumpTicks;
	private int tries;
	private int stageTicks;
	private @Nullable BlockPos bar;

	public CageClimbGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (c.level().dimension() != Level.END || (c.tickCount + c.getId()) % 10 != 0 || !c.isTeamMember()) {
			return false;
		}
		DragonFight.Climb climb = DragonFight.climb();
		if (climb != null && climb.friend().equals(c.getUUID())) {
			stage = c.getHealth() >= c.getMaxHealth() * 0.6F ? Stage.WALK : Stage.DONE;
			if (stage == Stage.DONE) {
				DragonFight.endClimb(c);
			}
			return stage != Stage.DONE;
		}
		if (c.onGround() && onOwnPillar()) {
			stage = Stage.DOWN; // left up a pillar with nothing to finish: come down
			return true;
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return stage != Stage.DONE && c.isAlive();
	}

	@Override
	public void start() {
		stageTicks = 0;
		jumpFrom = null;
		tries = 0;
		bar = null;
		c.setTarget(null);
		c.getNavigation().stop();
	}

	@Override
	public void stop() {
		c.actions().cancelMining();
		c.actions().stopWalking();
		DragonFight.endClimb(c);
		stage = Stage.DONE;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ServerLevel level = (ServerLevel) c.level();
		stageTicks++;
		DragonFight.Climb climb = DragonFight.climb();
		boolean mine = climb != null && climb.friend().equals(c.getUUID());
		if ((stage == Stage.UP || stage == Stage.BARS) && (!mine || hurt())) {
			next(Stage.DOWN); // called off, or knocked about: straight back down
		}
		switch (stage) {
			case WALK -> walk(climb);
			case UP -> up(level, climb);
			case BARS -> bars(level, climb);
			case DOWN -> down();
			case DONE -> {
			}
		}
	}

	private boolean hurt() {
		return c.getHealth() < c.getMaxHealth() * 0.6F || c.ticksSinceDamaged() < 10;
	}

	private void next(Stage s) {
		stage = s;
		stageTicks = 0;
		jumpFrom = null;
		tries = 0;
		bar = null;
		c.actions().cancelMining();
	}

	private void walk(DragonFight.@Nullable Climb climb) {
		if (climb == null || hurt()) {
			next(Stage.DONE);
			return;
		}
		BlockPos foot = new BlockPos(climb.x(), climb.baseY(), climb.z());
		if (c.blockPosition().equals(foot) && c.onGround()) {
			c.actions().stopWalking();
			next(Stage.UP);
			return;
		}
		c.actions().walkTo(foot, 0.4);
		if (c.actions().isStuck() || stageTicks > WALK_TIME) {
			c.actions().stopWalking();
			next(Stage.DONE); // cannot get to the foot of the column: somebody else may try later
		}
	}

	/** One block at a time up the column: jump, and place a block where their feet were. */
	private void up(ServerLevel level, DragonFight.@Nullable Climb climb) {
		if (climb == null) {
			next(Stage.DOWN);
			return;
		}
		if (c.getBlockY() >= climb.topY() && c.onGround()) {
			next(Stage.BARS);
			return;
		}
		if (jumpFrom == null) {
			if (!c.onGround()) {
				return; // wait to land first
			}
			jumpFrom = c.blockPosition();
			jumpTicks = 0;
			BlockPos head = jumpFrom.above(2);
			if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) {
				next(Stage.DOWN); // something overhead
				return;
			}
		}
		BlockPos feet = jumpFrom;
		ItemStack block = c.backpack().find(DragonFight::isPillarItem);
		if (block.isEmpty()) {
			next(Stage.DOWN);
			return;
		}
		c.getNavigation().stop();
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		if (jumpTicks == 0) {
			c.setPos(feet.getX() + 0.5, c.getY(), feet.getZ() + 0.5);
			c.getJumpControl().jump();
		}
		jumpTicks++;
		if (c.getY() >= feet.getY() + 1.0 && level.getGameTime() - c.lastEditTick() >= 4) {
			BlockState state = Block.byItem(block.getItem()).defaultBlockState();
			ItemStack template = block.copyWithCount(1);
			if (c.actions().place(feet, state, s -> ItemStack.isSameItemSameComponents(s, template), WorldEditGuard.Reason.EXPEDITION)) {
				jumpFrom = null;
				tries = 0;
				return;
			}
		}
		if (jumpTicks > TICKS_PER_JUMP) {
			jumpTicks = 0;
			if (++tries >= MAX_TRIES) {
				next(Stage.DOWN); // the jumps keep falling short
			}
		}
	}

	/** Breaks the cage's bars within reach, nearest first, then heads down. */
	private void bars(ServerLevel level, DragonFight.@Nullable Climb climb) {
		c.getNavigation().stop();
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		EndCrystal crystal = climb == null ? null : DragonFight.crystal(climb.crystal());
		if (crystal == null || stageTicks > BARS_TIME) {
			next(Stage.DOWN);
			return;
		}
		if (bar == null || !level.getBlockState(bar).is(Blocks.IRON_BARS)) {
			bar = nearestBar(level, crystal);
			if (bar == null) {
				next(Stage.DOWN); // every bar within reach is gone: the archers can see in now
				return;
			}
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(bar));
		Actions.Result r = c.actions().mine(bar, WorldEditGuard.Reason.EXPEDITION);
		if (r != Actions.Result.RUNNING) {
			bar = null;
		}
	}

	private @Nullable BlockPos nearestBar(ServerLevel level, EndCrystal crystal) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos here = c.blockPosition();
		AABB cage = crystal.getBoundingBox().inflate(ExpeditionPolicy.BARS_REACH);
		for (BlockPos p : BlockPos.betweenClosed(here.offset(-4, -1, -4), here.offset(4, 5, 4))) {
			if (!level.getBlockState(p).is(Blocks.IRON_BARS) || !c.actions().canReach(p) || !cage.intersects(new AABB(p))) {
				continue;
			}
			double d = p.distSqr(here);
			if (d < bestDist) {
				bestDist = d;
				best = p.immutable();
			}
		}
		return best;
	}

	/** Takes the pillar back block by block from the top, standing on it, until the ground is reached. */
	private void down() {
		c.getNavigation().stop();
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		if (!c.onGround()) {
			return; // still dropping onto the next block
		}
		BlockPos below = c.blockPosition().below();
		if (!onOwnPillar()) {
			next(Stage.DONE);
			return;
		}
		switch (c.actions().mine(below, WorldEditGuard.Reason.EXPEDITION)) {
			case FAILED -> next(Stage.DONE); // cannot take it back: left standing, and the friend climbs down as best they can
			case DONE, RUNNING -> {
			}
		}
	}

	/** True when standing on a block of a pillar the friends built in the End. */
	private boolean onOwnPillar() {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos below = c.blockPosition().below();
		return DragonFight.isPillarBlock(level.getBlockState(below)) && Camp.data(level.getServer()).isPlacedByFriends(level, below);
	}
}

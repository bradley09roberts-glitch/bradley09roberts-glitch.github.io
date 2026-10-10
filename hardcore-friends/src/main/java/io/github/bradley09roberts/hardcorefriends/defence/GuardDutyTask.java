package io.github.bradley09roberts.hardcorefriends.defence;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Standing guard at night for a friend on the rota ({@link GuardRota}): to their post (the watchtower lookout, the
 * gate, a stretch of wall), where they stand looking out, and, at the gate and the walls, now and then walk a short
 * patrol (up the street, along the wall) and back. A dark post gets a torch, if the guard carries one, put down beside
 * it through the edit guard (the landscaping rules: a torch on the camp's own ground, never on a player's build). The
 * fighting is the reflexes' and {@link DefenceTargetGoal}'s; up at the lookout, a guard with a bow shoots from there.
 *
 * <p>Scores {@value #SCORE}, the night watch's own score: above bedtime (which the rota keeps from guards on duty
 * anyway), below a hungry friend's meal, in rounds of {@value #ROUND} ticks so a pressing need gets a look in.
 */
final class GuardDutyTask implements CompanionTask {
	static final String ID = "defence.guard";
	private static final double SCORE = 75;
	private static final int ROUND = 20 * 60;
	/** How long a guard stands at their post before a patrol. */
	private static final int STAND = 20 * 40;
	private static final int PAUSE = 20 * 3;
	private static final double REACH = 1.2;
	/** A post darker than this (block light) gets a torch. */
	private static final int DARK = 7;

	private enum Stage {
		GOING,
		STANDING,
		PATROL
	}

	private Posts.@Nullable Post post;
	private Stage stage = Stage.GOING;
	private int ticks;
	private int standing;
	private int leg;
	private int pause;
	private boolean lit;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Posts.Post p = post;
		String where = p == null ? "the village" : p.what();
		return switch (stage) {
			case GOING -> "going on guard at " + where;
			case STANDING -> "standing guard at " + where;
			case PATROL -> "walking a patrol from " + where;
		};
	}

	@Override
	public double score(CompanionEntity c) {
		return GuardRota.postOf(c) != null ? SCORE : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		post = GuardRota.postOf(c);
		stage = Stage.GOING;
		ticks = 0;
		standing = 0;
		leg = 0;
		pause = 0;
		lit = false;
		if (post == null) {
			return false;
		}
		Alarm.say(c, Line.TO_THE_WALLS);
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Posts.Post now = GuardRota.postOf(c);
		if (now == null || !(c.level() instanceof ServerLevel level)) {
			return TaskStatus.SUCCESS; // the shift is over (or passed to someone else)
		}
		if (!now.equals(post)) {
			post = now;
			stage = Stage.GOING;
		}
		if (++ticks > ROUND) {
			return TaskStatus.SUCCESS;
		}
		Posts.Post p = post;
		switch (stage) {
			case GOING -> {
				if (c.actions().walkTo(p.stand(), REACH)) {
					stage = Stage.STANDING;
					standing = 0;
				} else if (c.actions().isStuck()) {
					GuardRota.failed(c);
					return TaskStatus.FAILURE;
				}
			}
			case STANDING -> {
				c.actions().stopWalking();
				lookOut(c);
				if (!lit) {
					lit = true;
					lightPost(c, level, p.stand());
				}
				if (++standing >= STAND && !p.patrol().isEmpty() && !Alarm.isActive()) {
					stage = Stage.PATROL;
					leg = 0;
					pause = 0;
				}
			}
			case PATROL -> {
				List<BlockPos> route = p.patrol();
				if (leg >= route.size() || Alarm.isActive()) {
					stage = Stage.GOING; // back to the post
					break;
				}
				if (pause > 0) {
					pause--;
					lookOut(c);
					break;
				}
				if (c.actions().walkTo(route.get(leg), 2.0) || c.actions().isStuck()) {
					c.actions().stopWalking();
					leg++;
					pause = PAUSE;
				}
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Looks at the nearest hostile about, else slowly round the post. */
	private void lookOut(CompanionEntity c) {
		LivingEntity threat = Threats.nearest(c, 24);
		if (threat != null) {
			c.getLookControl().setLookAt(threat);
			return;
		}
		double angle = (c.tickCount / 60) * (Math.PI / 3);
		c.getLookControl().setLookAt(c.getX() + Math.cos(angle) * 8, c.getEyeY(), c.getZ() + Math.sin(angle) * 8);
	}

	/**
	 * A torch beside a dark post, from the guard's own backpack, on the ground next to where they stand (never the post
	 * itself, so nobody stands in it), through the edit guard.
	 */
	private static void lightPost(CompanionEntity c, ServerLevel level, BlockPos stand) {
		if (level.getBrightness(LightLayer.BLOCK, stand) > DARK || !c.backpack().has(s -> s.is(Items.TORCH))) {
			return;
		}
		BlockState torch = Blocks.TORCH.defaultBlockState();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos spot = stand.relative(d);
			if (level.isLoaded(spot) && level.getBlockState(spot).isAir() && torch.canSurvive(level, spot)
				&& c.actions().place(spot, torch, s -> s.is(Items.TORCH), WorldEditGuard.Reason.LANDSCAPE)) {
				return;
			}
		}
	}

	@Override
	public void stop(CompanionEntity c) {
		post = null;
		stage = Stage.GOING;
		ticks = 0;
		standing = 0;
	}

	@Override
	public int failureCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return ROUND + 20 * 30;
	}
}

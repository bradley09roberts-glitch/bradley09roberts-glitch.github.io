package io.github.bradley09roberts.hardcorefriends.ai.role.sage;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.role.SageAdvisor;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Every few minutes Sage walks to the supply chest, takes stock (recomputing the camp's needs) and announces the
 * team's focus, naming the friend best placed to cover it.
 */
public final class ReviewStoresTask implements CompanionTask {
	/** {@link CampData#memory} key; holds {@code "at"} (game time of the last review). */
	public static final String MEMORY = "sage.review";
	private static final double CHEST_REACH = 2.0;
	private static final int STUDY_TICKS = 40;

	private int studied;

	@Override
	public String id() {
		return "sage.review_stores";
	}

	@Override
	public String describe() {
		return "reviewing our stores";
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isNight(level) && !WorldEditGuard.inCamp(c, c.blockPosition())) {
			return 0;
		}
		return SupplyChest.of(level).isPresent() ? 30 : 0;
	}

	@Override
	public boolean start(CompanionEntity c) {
		studied = 0;
		return Camp.data(c.level().getServer()).chestPos().isPresent();
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		Optional<BlockPos> chest = data.chestPos();
		if (chest.isEmpty() || SupplyChest.of(level).isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(chest.get(), CHEST_REACH)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		BlockPos p = chest.get();
		c.getLookControl().setLookAt(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
		if (++studied < STUDY_TICKS) {
			return TaskStatus.RUNNING;
		}
		CampNeeds.recompute(level.getServer());
		CampNeeds.Need focus = CampNeeds.focus();
		if (Speech.say(c, Line.ADVICE, TeamPlan.announce(focus, level.getGameTime()))) {
			SageAdvisor.markFocusAnnounced(focus);
		}
		data.memory(MEMORY).putLong("at", level.getGameTime());
		data.setDirty();
		data.addStat("sage_reviews", 1);
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		studied = 0;
	}

	@Override
	public int successCooldown() {
		return 20 * 60 * 4;
	}

	@Override
	public int failureCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}

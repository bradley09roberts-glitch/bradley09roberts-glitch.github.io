package io.github.bradley09roberts.hardcorefriends.ai.role.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.BuildJob;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Shared shape of a job that builds one blueprint batch with {@link BuildJob}: choose a plan, run one batch, and
 * pick a cooldown that fits why it stopped (short of materials, no room, no crafting table...). A plan that is
 * short of materials or has no room is set aside for a while, so the friend can get on with the next one.
 */
public abstract class BlueprintTask implements CompanionTask {
	protected @Nullable BuildJob job;
	private @Nullable Blueprint target;
	private boolean doneWithoutBuilding;
	private int cooldown = 600;
	private final Map<String, Long> setAsideUntil = new HashMap<>();

	/** The plan to work on now, or null. May complete trivial improvements itself and return null. */
	protected abstract @Nullable Blueprint choose(CompanionEntity c, CampData data);

	protected abstract WorldEditGuard.Reason reason();

	protected abstract double baseScore();

	protected boolean repair() {
		return false;
	}

	/** Called in {@link #start} before a job is made: return true if the improvement was finished without work. */
	protected boolean finishWithoutBuilding(CompanionEntity c, CampData data, Blueprint plan) {
		return false;
	}

	/** True while a plan is set aside after it could not be built. */
	protected boolean isSetAside(CompanionEntity c, String structureId) {
		Long until = setAsideUntil.get(structureId);
		return until != null && c.level().getGameTime() < until;
	}

	/** Improvements of this role for the current or an earlier stage that are not finished, in plan order. */
	protected static List<Structures.Entry> pending(CampData data, Role role) {
		List<Structures.Entry> list = new ArrayList<>();
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() <= data.stage() && e.owner() == role && !data.isCompleted(e.id())) {
				list.add(e);
			}
		}
		return list;
	}

	@Override
	public String describe() {
		Blueprint plan = target;
		if (plan == null) {
			return repair() ? "repairing the camp" : "building";
		}
		String name = Structures.get(plan.id()).displayName();
		return (repair() ? "repairing the " : "building the ") + name;
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		Blueprint plan = choose(c, data);
		if (plan == null) {
			return 0;
		}
		return baseScore();
	}

	@Override
	public boolean start(CompanionEntity c) {
		CampData data = Camp.data(c.level().getServer());
		target = choose(c, data);
		cooldown = 600;
		doneWithoutBuilding = false;
		if (target == null) {
			return false;
		}
		if (finishWithoutBuilding(c, data, target)) {
			doneWithoutBuilding = true;
			return true;
		}
		job = new BuildJob(c, target, reason(), repair());
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (doneWithoutBuilding) {
			return TaskStatus.SUCCESS;
		}
		BuildJob j = job;
		if (j == null) {
			return TaskStatus.FAILURE;
		}
		TaskStatus status = j.tick();
		if (status == TaskStatus.FAILURE) {
			int wait = switch (j.failure()) {
				case SHORT -> 1200;
				case NO_SITE -> 2400;
				case NO_TABLE -> 600;
				case UNREACHABLE -> 300;
				case CLEARING -> 400;
				case NONE -> 200;
			};
			boolean planSpecific = j.failure() == BuildJob.Failure.SHORT || j.failure() == BuildJob.Failure.NO_SITE
				|| j.failure() == BuildJob.Failure.UNREACHABLE || j.failure() == BuildJob.Failure.CLEARING;
			if (planSpecific) {
				setAsideUntil.put(j.blueprint().id(), c.level().getGameTime() + wait);
				cooldown = 60; // try the next plan soon
			} else {
				cooldown = wait;
			}
		}
		return status;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (job != null) {
			job.stop();
		}
		job = null;
	}

	@Override
	public int failureCooldown() {
		return cooldown;
	}

	@Override
	public int maxTicks() {
		return 20 * 180;
	}
}

package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * Independence and survival skills: the camp keeps running while a player of the camp is online, friends go on
 * trips beyond the gathering ring (far exploring, foraging, trading), make room to build (search further out, level
 * uneven ground), build a night shelter when caught far from camp, get out of reach when cornered, break their falls,
 * and grow more skilled at the work they do.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS}, sub-commands through
 * {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and block-edit rules through
 * {@code WorldEditGuard.POLICIES}.
 */
public final class Survival {
	/** Levelling a site: Terra's own speed and eagerness. */
	private static final double GRADE_SCORE = 62;
	/** Levelling a site: the builder lends a hand while the building waits, as with clearing trees. */
	private static final double GRADE_BUILDER_SCORE = 45;

	private Survival() {
	}

	public static void init() {
		ChunkLoader.init();
		SurvivalPolicies.register();
		SurvivalLines.register();

		// Jobs: levelling (Terra's speciality, the builder helps, one friend at a time), the night shelter (anyone),
		// trading trips (anyone, Sage and Rowan keenest) and far exploring (Scout only).
		SpecialityTask.EXCLUSIVE.add(GradeSiteTask.ID);
		TaskRegistry.SPECIALIST_ONLY.add(FarTripTask.ID);
		TaskScheduler.NIGHT_JOBS.add(ShelterTask.ID);
		TaskScheduler.FIT_WHEN_WEAK.add(ShelterTask.ID);
		TaskRegistry.PACKS.add(Survival::tasks);
		TaskScheduler.JOB_DONE.add(Skills::onJobDone);

		CompanionEvents.TICK.add(WaterClutch::tick);
		CompanionEvents.TICK.add(Skills::tick);
		CompanionEvents.HIT.add(Skills::onHit);
		CompanionEvents.HURT.add(PillarGoal::noteHit);
		// Out of reach when cornered is a reflex: above falling back (priority 0), so it is never interrupted once begun.
		CompanionEvents.GOALS.add((companion, goals, targets) -> goals.addGoal(-1, new PillarGoal(companion)));
		CompanionEvents.DEATH.add((companion, level, source) -> ChunkLoader.stopRoaming(companion));
		CompanionEvents.DISMISSED.add((companion, level) -> ChunkLoader.stopRoaming(companion));

		FriendsCommand.EXTENSIONS.add(SurvivalCommands::register);

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Trips.clearClaims();
			SiteGrading.clearCache();
			PillarGoal.clear();
			WaterClutch.clear();
		});
	}

	/** This package's jobs for one friend (fresh instances: tasks keep per-friend state). */
	private static List<CompanionTask> tasks(FriendId id) {
		List<CompanionTask> list = new ArrayList<>();
		list.add(new SpecialityTask(new GradeSiteTask(GRADE_SCORE), Role.LANDSCAPER));
		list.add(new SpecialityTask(new GradeSiteTask(GRADE_BUILDER_SCORE), Role.BUILDER));
		list.add(new ShelterTask());
		list.add(new TradeTripTask());
		if (id.role() == Role.EXPLORER) {
			list.add(new SpecialityTask(new FarTripTask(), Role.EXPLORER));
		}
		return list;
	}
}

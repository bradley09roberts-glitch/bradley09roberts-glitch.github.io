package io.github.bradley09roberts.hardcorefriends.people;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.ShareTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.needs.SocializeTask;
import io.github.bradley09roberts.hardcorefriends.civic.Families;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;

/**
 * Living together: friendships and romance between friends, couples, weddings, children who grow up, family names,
 * and the skins people wear (more skins can be added as data). Provides {@code civic.Families}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}. Nothing here changes a block, so it registers no
 * block-edit rules.
 *
 * <p>The parts: {@link PeopleData} (what the world remembers), {@link Relationships} and {@link Compatibility}
 * (friendship and romance), {@link DateTask} (dates and proposals), {@link Weddings} and {@link WeddingTask},
 * {@link Births}, {@link Children} with {@link PlayTask}, {@link LearnTask}, {@link ChildHomeTask},
 * {@link StayCloseTask}, {@link ChildRefugeGoal} and, for the grown-ups, {@link FeedChildTask} and
 * {@link ProtectChildGoal}; {@link Names}, {@link Skins}, {@link FamiliesProvider}, {@link PeopleEvents} (the hooks),
 * {@link PeopleCommands} and {@link PeopleLines}.
 */
public final class People {
	/** Every job of this package has an id starting with this. */
	public static final String JOB_PREFIX = "people.";

	private People() {
	}

	/**
	 * Lets another package's job be done by children too (by default a child only looks after their needs, plays,
	 * learns, goes home and goes to weddings). For jobs that change no block and take a child nowhere dangerous, such as
	 * going to their own bed in the family home.
	 */
	public static void allowChildJob(String jobId) {
		Children.JOBS.add(jobId);
	}

	/** The jobs a child may do besides their needs (read only). */
	public static Set<String> childJobs() {
		return Set.copyOf(Children.JOBS);
	}

	public static void init() {
		PeopleLines.register();
		Families.provide(new FamiliesProvider());

		// Children's games, lessons and early nights; dates, weddings and feeding a hungry child for the grown-ups. Each
		// job scores nothing for the people it is not for.
		TaskRegistry.PACKS.add(id -> List.of(new PlayTask(), new LearnTask(), new ChildHomeTask(), new StayCloseTask(),
			new FeedChildTask(), new DateTask(), new WeddingTask()));
		TaskScheduler.JOB_FILTERS.add(Children::mayDo);
		TaskScheduler.JOB_FILTERS.add(DateTask::mayDo);

		CompanionEvents.TICK.add(PeopleEvents::tick);
		CompanionEvents.DEATH.add(PeopleEvents::died);
		CompanionEvents.DISMISSED.add(PeopleEvents::dismissed);
		CompanionEvents.HIT.add(Relationships::hit);
		CompanionEvents.INTERACT.add(Children::interact);
		CompanionEvents.GOALS.add((companion, goals, targets) -> {
			goals.addGoal(2, new ChildRefugeGoal(companion));
			targets.addGoal(1, new ProtectChildGoal(companion));
		});
		SocializeTask.CHATTED.add(Relationships::chatted);
		ShareTask.SHARED.add(Relationships::shared);

		FriendsCommand.EXTENSIONS.add(PeopleCommands::register);
		ServerTickEvents.END_SERVER_TICK.register(PeopleEvents::serverTick);
		ServerLifecycleEvents.SERVER_STARTING.register(server -> PeopleEvents.clear());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PeopleEvents.clear());
	}
}

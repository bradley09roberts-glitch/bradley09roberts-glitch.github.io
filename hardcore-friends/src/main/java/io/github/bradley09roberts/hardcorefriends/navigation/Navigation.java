package io.github.bradley09roberts.hardcorefriends.navigation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;

/**
 * Finding the way: better pathfinding (no wandering into caves to reach things on the surface, no getting stuck
 * behind flowing water, keeping away from drops and lava), getting unstuck (and out of caves, and up for air), sharper
 * senses for danger, and sprinting over long distances.
 *
 * <ul>
 * <li>{@link FriendNavigation} and {@link FriendNodeEvaluator}: every friend's paths (made in
 * {@code CompanionEntity.createNavigation}), so every walk in {@code Actions}, the trip walker, following and fleeing
 * use them.</li>
 * <li>{@link Senses}: what a friend can hear and feel, for any package to ask.</li>
 * <li>{@link Wayfinder} (watching every friend for getting stuck) and {@link WayOutGoal} (the plans to get out:
 * {@link ShorePlan}, {@link CaveExitPlan}, {@link StepPlan}, {@link StairPlan}), and the last-resort rescue.</li>
 * <li>{@link Sprint}: running on long walks.</li>
 * <li>{@code /friends senses [name]}: what a friend senses and whether they are stuck.</li>
 * </ul>
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, sub-commands through {@code FriendsCommand.EXTENSIONS} and wording through
 * {@code Lines.define}. Blocks a friend places or digs to get out use the survival package's {@code SURVIVAL} edit
 * rules (its pieces and planned digs), so this package registers no edit rules of its own.
 */
public final class Navigation {
	private Navigation() {
	}

	public static void init() {
		NavigationLines.register();
		CompanionEvents.TICK.add(Wayfinder::tick);
		CompanionEvents.TICK.add(Sprint::tick);
		CompanionEvents.GOALS.add((companion, goals, targets) -> goals.addGoal(WayOutGoal.PRIORITY, new WayOutGoal(companion)));
		FriendsCommand.EXTENSIONS.add(NavigationCommands::register);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			Wayfinder.clear();
			Sprint.clear();
			Senses.clear();
		});
	}
}

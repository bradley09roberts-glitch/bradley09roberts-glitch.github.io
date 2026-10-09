package io.github.bradley09roberts.hardcorefriends.navigation;

/**
 * Finding the way: better pathfinding (no wandering into caves to reach things on the surface, no getting stuck
 * behind flowing water, keeping away from drops and lava), getting unstuck (and out of caves, and up for air), sharper
 * senses for danger, and sprinting over long distances.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class Navigation {
	private Navigation() {
	}

	public static void init() {
	}
}

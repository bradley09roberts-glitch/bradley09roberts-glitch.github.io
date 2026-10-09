package io.github.bradley09roberts.hardcorefriends.architecture;

/**
 * Better builds: building plans kept as data files (houses, shops, workplaces, civic buildings, decorations) in
 * many styles, the materials they are built from (stairs, stone bricks, glass, beds, barrels...) and how the friends
 * make them, and building tall things safely (scaffolding they take down again). Provides {@code civic.BlueprintLibrary}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: hooks into friends go through
 * {@code CompanionEvents}, jobs through {@code TaskRegistry.PACKS} (and {@code TaskScheduler.JOB_FILTERS} to keep a
 * friend off jobs), sub-commands through {@code FriendsCommand.EXTENSIONS}, wording through {@code Lines.define} and
 * block-edit rules through {@code WorldEditGuard.POLICIES}.
 */
public final class Architecture {
	private Architecture() {
	}

	public static void init() {
	}
}

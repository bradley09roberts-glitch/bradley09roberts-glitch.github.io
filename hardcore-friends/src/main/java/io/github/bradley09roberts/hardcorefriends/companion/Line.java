package io.github.bradley09roberts.hardcorefriends.companion;

/**
 * Situations a friend can speak about. Each friend has their own wording in {@link Lines}. Arguments are
 * substituted with {@link String#format}; the documented placeholders say what each line receives.
 */
public enum Line {
	// Lifecycle and orders
	/** Just recruited. %1$s = player name. */
	RECRUITED(Priority.IMPORTANT, 0),
	/** Ordered to follow. %1$s = player name. */
	FOLLOW(Priority.IMPORTANT, 0),
	/** Ordered to hold position. */
	STAY(Priority.IMPORTANT, 0),
	/** Ordered back to autonomous work. */
	WORK(Priority.IMPORTANT, 0),
	/** Dismissed from the team. */
	DISMISSED(Priority.IMPORTANT, 0),
	/** Player right-clicked them: a status greeting. %1$s = current activity, %2$s = player name. */
	GREETING(Priority.IMPORTANT, 0),
	/** Player handed them food. */
	THANKS_FOOD(Priority.IMPORTANT, 0),
	/** Player handed them a non-food item. %1$s = item name. */
	THANKS_GIFT(Priority.IMPORTANT, 0),

	// Danger
	/** Health low; falling back. */
	RETREAT(Priority.DANGER, 200),
	/** Recovered after retreating. */
	RECOVERED(Priority.CASUAL, 600),
	/** Saw a creeper close by. */
	CREEPER(Priority.DANGER, 200),
	/** Engaging a hostile mob. %1$s = mob name. */
	FIGHT(Priority.CASUAL, 400),
	/** Warning a player about something. %1$s = player name, %2$s = hazard description. */
	WARNING(Priority.DANGER, 100),
	/** Another friend just died. %1$s = their name. */
	FRIEND_DIED(Priority.IMPORTANT, 0),
	/** A player is badly hurt nearby. %1$s = player name. */
	PLAYER_HURT(Priority.DANGER, 600),

	// Day cycle
	/** Heading home because night is falling. */
	NIGHT_RETURN(Priority.CASUAL, 6000),
	/** Morning greeting. */
	MORNING(Priority.CASUAL, 12000),

	// Work
	/** Starting their main job. %1$s = activity. */
	WORK_START(Priority.CASUAL, 1200),
	/** Needs a tool. %1$s = tool kind, e.g. "pickaxe". */
	NEED_TOOL(Priority.CASUAL, 2400),
	/** Their tool just broke. %1$s = item name. */
	TOOL_BROKE(Priority.IMPORTANT, 0),
	/** Missing materials. %1$s = short list, e.g. "12 planks, 3 glass panes". */
	NEED_MATERIALS(Priority.CASUAL, 2400),
	/** Dropped items into the supply chest. */
	DEPOSIT(Priority.CASUAL, 2400),
	/** Gave something to another friend or player. %1$s = recipient, %2$s = item description. */
	SHARE(Priority.CASUAL, 600),
	/** Finished a structure. %1$s = structure name. */
	BUILD_DONE(Priority.IMPORTANT, 0),
	/** Finished a contraption. %1$s = contraption name. */
	CONTRAPTION_DONE(Priority.IMPORTANT, 0),
	/** Found something interesting. %1$s = description with coordinates. */
	DISCOVERY(Priority.CASUAL, 600),
	/** Strategy or survival advice. %1$s = the advice text. */
	ADVICE(Priority.CASUAL, 1200),
	/** The team bond reached a new level. %1$s = level name. */
	UNITY_UP(Priority.IMPORTANT, 0),
	/** The camp reached a new stage. %1$s = stage name. */
	CAMP_UP(Priority.IMPORTANT, 0),
	/** Idle small talk with no arguments. */
	IDLE(Priority.CASUAL, 3600);

	/** How a line is rate-limited and who hears it. */
	public enum Priority {
		/** Obeys the friend's chattiness gap and the global chatter setting. Skipped entirely when quiet. */
		CASUAL,
		/** Always shown to nearby players, ignoring the chattiness gap. */
		IMPORTANT,
		/** Always shown, even when chatter is quiet; only the per-line cooldown applies. */
		DANGER
	}

	private final Priority priority;
	private final int cooldownTicks;

	Line(Priority priority, int cooldownTicks) {
		this.priority = priority;
		this.cooldownTicks = cooldownTicks;
	}

	public Priority priority() {
		return priority;
	}

	/** Minimum ticks between two uses of this line by the same friend. */
	public int cooldownTicks() {
		return cooldownTicks;
	}
}

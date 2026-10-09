package io.github.bradley09roberts.hardcorefriends.navigation;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * Every friend's wording for this package's lines: being stuck and getting free, lost in a cave and finding the way
 * out, digging out, gasping for air, swimming out of the water, being brought back, and setting off at a run. The same
 * rules as the main table apply (see {@code Lines}): British English, at most 90 characters, no placeholders (none of
 * these lines takes any), and at least two variants of their own for each of the nine friends.
 */
final class NavigationLines {
	private NavigationLines() {
	}

	static void register() {
		generic();
		fern();
		oak();
		flint();
		scout();
		spark();
		aegis();
		sage();
		terra();
		rowan();
	}

	private static void generic() {
		Lines.defineGeneric(Line.STUCK, "Hmm, I'm stuck. Let me try another way.", "Can't get through here. Trying round.");
		Lines.defineGeneric(Line.UNSTUCK, "There we go, free again.", "Out at last. On I go.");
		Lines.defineGeneric(Line.LOST_IN_CAVE, "I've lost my way down here. Looking for daylight.",
			"It's dark and I'm lost. Heading for the light.");
		Lines.defineGeneric(Line.FOUND_WAY_OUT, "Daylight! Found the way out.", "Out of the cave at last.");
		Lines.defineGeneric(Line.DIGGING_OUT, "No way out on foot. I'll dig my way up.", "Shut in here. Digging some steps up.");
		Lines.defineGeneric(Line.GASPING, "Air! I need air!", "Can't breathe! Up, up!");
		Lines.defineGeneric(Line.SWIMMING_OUT, "Too much water. Swimming for the bank.", "The current's strong. Heading for the shore.");
		Lines.defineGeneric(Line.RESCUED, "That was a long way round. Glad to be back.", "Phew. Made it back at last.");
		Lines.defineGeneric(Line.SPRINTING, "Long way to go. Picking up the pace!", "Coming through!");
	}

	private static void fern() {
		FriendId f = FriendId.FERN;
		Lines.define(f, Line.STUCK,
			"Oh dear, I seem to be stuck. Let me try another way.",
			"Goodness, I can't get past. I'll go round, dears.");
		Lines.define(f, Line.UNSTUCK,
			"There we are, free again. On we go!",
			"Phew, unstuck. Back to it, dears.");
		Lines.define(f, Line.LOST_IN_CAVE,
			"Oh my, it's so dark down here. I must find the daylight.",
			"I've lost my way, dears. Looking for the sky.");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Oh, the sky! Thank goodness for that.",
			"Out at last. Fresh air, lovely.");
		Lines.define(f, Line.DIGGING_OUT,
			"There's no way out, so I'll dig myself some steps.",
			"Shut in! A few steps up should do it.");
		Lines.define(f, Line.GASPING,
			"Oh! I need air!",
			"Help, I can't breathe! Up I go!");
		Lines.define(f, Line.SWIMMING_OUT,
			"Oh, this water! I'll paddle to the bank.",
			"Swimming for the shore, nice and steady.");
		Lines.define(f, Line.RESCUED,
			"Oh, what a fright. It's good to be back, dears.",
			"Back again! Nothing a cup of tea won't fix.");
		Lines.define(f, Line.SPRINTING,
			"Long way to go! A little jog will do me good.",
			"Hurrying along now, dears!");
	}

	private static void oak() {
		FriendId f = FriendId.OAK;
		Lines.define(f, Line.STUCK,
			"Blocked. Finding another way.",
			"Stuck. Going round.");
		Lines.define(f, Line.UNSTUCK,
			"Free. Moving on.",
			"Clear again.");
		Lines.define(f, Line.LOST_IN_CAVE,
			"Lost underground. Heading for daylight.",
			"Wrong turn. Finding the way up.");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Out. Back on top.",
			"Daylight. Good.");
		Lines.define(f, Line.DIGGING_OUT,
			"No exit. Cutting stairs.",
			"Shut in. Digging steps up.");
		Lines.define(f, Line.GASPING,
			"Air. Need air.",
			"Out of breath. Going up.");
		Lines.define(f, Line.SWIMMING_OUT,
			"Getting out of the water.",
			"Current's strong. Making for the bank.");
		Lines.define(f, Line.RESCUED,
			"Back. Long way round.",
			"Made it back. Won't go that way again.");
		Lines.define(f, Line.SPRINTING,
			"Long walk. Running it.",
			"Picking up the pace.");
	}

	private static void flint() {
		FriendId f = FriendId.FLINT;
		Lines.define(f, Line.STUCK,
			"Stuck. Don't panic. Try another way.",
			"Can't get through. Backing up and thinking.");
		Lines.define(f, Line.UNSTUCK,
			"Free. That's how you do it: slowly.",
			"Out. Nobody saw that, right?");
		Lines.define(f, Line.LOST_IN_CAVE,
			"Even I lost my bearings down here. Following the air out.",
			"Lost. Not good. Heading for daylight, carefully.");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Out. And I'll take more torches next time.",
			"Daylight. Never doubted it. Much.");
		Lines.define(f, Line.DIGGING_OUT,
			"No way out? Then I'll make one. Stairs up.",
			"Shut in. Good thing digging is my job.");
		Lines.define(f, Line.GASPING,
			"Air! Up, now!",
			"Running out of air!");
		Lines.define(f, Line.SWIMMING_OUT,
			"I'm a miner, not a fish. To the shore.",
			"Water. Hate it. Swimming out.");
		Lines.define(f, Line.RESCUED,
			"Back. Let's never speak of it.",
			"Made it. My heart's still racing.");
		Lines.define(f, Line.SPRINTING,
			"Running. Carefully.",
			"Long way. Best get a move on.");
	}

	private static void scout() {
		FriendId f = FriendId.SCOUT;
		Lines.define(f, Line.STUCK,
			"Huh, a dead end! I'll find another way!",
			"Stuck! No matter, there's always another path!");
		Lines.define(f, Line.UNSTUCK,
			"Free! Onwards!",
			"Unstuck! The adventure continues!");
		Lines.define(f, Line.LOST_IN_CAVE,
			"Lost in a cave! Find the light, find the way out!",
			"This cave goes on for ever! Looking for daylight!");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Daylight! I knew there was a way out!",
			"Out! What a tunnel that was!");
		Lines.define(f, Line.DIGGING_OUT,
			"No way out? I'll dig my own! Stairs time!",
			"Trapped! Tunnelling up!");
		Lines.define(f, Line.GASPING,
			"Need air! Swimming up!",
			"Can't breathe! Surface!");
		Lines.define(f, Line.SWIMMING_OUT,
			"Swimming for it! There's the shore!",
			"Whoa, strong current! Heading for the bank!");
		Lines.define(f, Line.RESCUED,
			"Made it back! What an adventure!",
			"Back! That place goes on my map as 'avoid'!");
		Lines.define(f, Line.SPRINTING,
			"Race you there!",
			"Running! Nothing beats a good sprint!");
	}

	private static void spark() {
		FriendId f = FriendId.SPARK;
		Lines.define(f, Line.STUCK,
			"Error: stuck! Recalculating!",
			"I'm wedged! Trying a different route!");
		Lines.define(f, Line.UNSTUCK,
			"Unstuck! The new route works!",
			"Free again! Problem solved!");
		Lines.define(f, Line.LOST_IN_CAVE,
			"Lost underground! Navigating by daylight!",
			"Too dark! Searching for the exit!");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Sky detected! Exit found!",
			"Out! The plan worked!");
		Lines.define(f, Line.DIGGING_OUT,
			"No exit found! Building one: stairs, engage!",
			"Trapped! Digging an escape staircase!");
		Lines.define(f, Line.GASPING,
			"Air running low! Surfacing!",
			"Air! Air! Up!");
		Lines.define(f, Line.SWIMMING_OUT,
			"Water! Swimming across the current, not with it!",
			"Aquatic retreat! To the shore!");
		Lines.define(f, Line.RESCUED,
			"Back! I'll invent a better compass!",
			"Made it back! That was not in the plan!");
		Lines.define(f, Line.SPRINTING,
			"Maximum speed!",
			"Zoom! Coming through!");
	}

	private static void aegis() {
		FriendId f = FriendId.AEGIS;
		Lines.define(f, Line.STUCK,
			"Path blocked. Adjusting.",
			"Stuck. Finding another way.");
		Lines.define(f, Line.UNSTUCK,
			"Clear. Resuming.",
			"Free. Moving on.");
		Lines.define(f, Line.LOST_IN_CAVE,
			"Lost underground. Moving towards daylight.",
			"Cave. No sky. Finding the way out.");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Out of the cave. Resuming.",
			"Open sky. Good.");
		Lines.define(f, Line.DIGGING_OUT,
			"No way out. Digging up.",
			"Trapped. Cutting stairs.");
		Lines.define(f, Line.GASPING,
			"Low on air. Surfacing.",
			"Need air.");
		Lines.define(f, Line.SWIMMING_OUT,
			"Leaving the water.",
			"Strong current. Making for the bank.");
		Lines.define(f, Line.RESCUED,
			"Back. Ready for duty.",
			"Returned. No harm done.");
		Lines.define(f, Line.SPRINTING,
			"Moving quickly.",
			"Double time.");
	}

	private static void sage() {
		FriendId f = FriendId.SAGE;
		Lines.define(f, Line.STUCK,
			"This way is closed. Another will serve.",
			"I appear to be stuck. Patience, then another path.");
		Lines.define(f, Line.UNSTUCK,
			"Free again. Patience is rewarded.",
			"There. A little thought solves most things.");
		Lines.define(f, Line.LOST_IN_CAVE,
			"I have strayed underground. The light will show the way out.",
			"Lost below ground. Calm thinking leads to daylight.");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"The sky again. As I expected.",
			"Out at last. A lesson in caution.");
		Lines.define(f, Line.DIGGING_OUT,
			"If there is no way out, one makes a way. Stairs up.",
			"Shut in. A staircase is the answer.");
		Lines.define(f, Line.GASPING,
			"Air, quickly!",
			"My breath is running out!");
		Lines.define(f, Line.SWIMMING_OUT,
			"Across the current, never with it. To the shore.",
			"The water is no place to linger. Swimming out.");
		Lines.define(f, Line.RESCUED,
			"Back, and a little wiser.",
			"Returned. I shall plan my routes better.");
		Lines.define(f, Line.SPRINTING,
			"A brisk pace saves daylight.",
			"Haste, but not carelessness.");
	}

	private static void terra() {
		FriendId f = FriendId.TERRA;
		Lines.define(f, Line.STUCK,
			"Oops, I'm stuck. I'll find a gentler way round.",
			"Can't get past here. Let me look for another way.");
		Lines.define(f, Line.UNSTUCK,
			"Free again! Off I go.",
			"There we are. Lovely, I'm out.");
		Lines.define(f, Line.LOST_IN_CAVE,
			"I don't like caves. Looking for the sky.",
			"Lost in the dark. I'll find my way back to the grass.");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Grass and sky! Much better.",
			"Out in the open again. What a relief.");
		Lines.define(f, Line.DIGGING_OUT,
			"No way out. I'll dig some neat steps up.",
			"Shut in! Carefully digging my way up.");
		Lines.define(f, Line.GASPING,
			"Oh no, I need air!",
			"Up, up, I can't breathe!");
		Lines.define(f, Line.SWIMMING_OUT,
			"Swimming for the bank. Gently does it.",
			"The current's pulling. Off to the shore.");
		Lines.define(f, Line.RESCUED,
			"Back! I've never been so glad to see you all.",
			"Back safe. What a relief.");
		Lines.define(f, Line.SPRINTING,
			"Off at a trot! Lovely day for a run.",
			"Hurrying along!");
	}

	private static void rowan() {
		FriendId f = FriendId.ROWAN;
		Lines.define(f, Line.STUCK,
			"Stuck. I'll work my way round.",
			"No way through here. Trying another.");
		Lines.define(f, Line.UNSTUCK,
			"Out. Back to it.",
			"Free again. Right, where was I?");
		Lines.define(f, Line.LOST_IN_CAVE,
			"Lost down here. I'll follow the air out.",
			"Wrong way. Heading back up to the light.");
		Lines.define(f, Line.FOUND_WAY_OUT,
			"Out. Fresh air at last.",
			"There's the sky. Back to work.");
		Lines.define(f, Line.DIGGING_OUT,
			"Shut in. I'll dig my way out.",
			"No way out but up. Digging.");
		Lines.define(f, Line.GASPING,
			"Need air!",
			"Out of breath! Going up!");
		Lines.define(f, Line.SWIMMING_OUT,
			"Too much water. Making for the bank.",
			"Current's strong. Swimming across it.");
		Lines.define(f, Line.RESCUED,
			"Back. That was a long way round.",
			"Made it back. Glad that's over.");
		Lines.define(f, Line.SPRINTING,
			"Long way. I'll run it.",
			"Picking up the pace.");
	}
}

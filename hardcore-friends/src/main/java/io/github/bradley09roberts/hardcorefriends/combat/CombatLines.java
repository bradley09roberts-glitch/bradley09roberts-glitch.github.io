package io.github.bradley09roberts.hardcorefriends.combat;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say about bows, shields, gear and emergency healing, in each friend's own voice (the same rules as
 * the main table in {@link Lines}: British English, at most 90 characters before the names are filled in, only the
 * placeholders each line documents). Item and mob names arrive as the game shows them, capitalised ("Iron
 * Chestplate", "Skeleton"), so they sit after a colon or as a name rather than after "a".
 */
public final class CombatLines {
	private CombatLines() {
	}

	/** Adds every combat line's wording (called once from {@link Combat#init()}). */
	public static void register() {
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
		Lines.defineGeneric(Line.DRAW_BOW, "Got you in my sights, %1$s.", "Arrow ready. Hold still, %1$s.",
			"Taking aim at the %1$s.");
		Lines.defineGeneric(Line.RAISE_SHIELD, "Shield up!", "Behind my shield!", "Shield ready!");
		Lines.defineGeneric(Line.EMERGENCY_HEAL, "Badly hurt. Using my %1$s!", "Quick, the %1$s!",
			"Patching up with my %1$s.");
		Lines.defineGeneric(Line.NEW_GEAR, "New gear: %1$s. Much better.", "Wearing the %1$s now.",
			"This %1$s will do nicely.");
		Lines.defineGeneric(Line.MADE_GEAR, "Made something for the chest: %1$s.", "Fresh from the table: %1$s.",
			"One more for the chest: %1$s.");
		Lines.defineGeneric(Line.TAKE_OVER, "Fall back, %1$s! I've got the %2$s.", "I'll take the %2$s, %1$s. Get clear!",
			"Leave the %2$s to me, %1$s.");
	}

	private static void fern() {
		FriendId f = FriendId.FERN;
		Lines.define(f, Line.DRAW_BOW,
			"Stay away from the crops, %1$s. I mean it.",
			"I don't like this, %1$s, but I'll shoot if I must.");
		Lines.define(f, Line.RAISE_SHIELD,
			"Oh! Shield up, everyone keep back!",
			"Behind my shield, quickly now!");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Ouch. I'd better have my %1$s now.",
			"I'm in a bad way. Thank goodness for this %1$s.");
		Lines.define(f, Line.NEW_GEAR,
			"Oh, this %1$s fits nicely. I feel safer already.",
			"For me? The %1$s? How thoughtful. I'll take good care of it.");
		Lines.define(f, Line.MADE_GEAR,
			"I made something to keep someone safe: %1$s.",
			"There, all done and put in the chest: %1$s.");
		Lines.define(f, Line.TAKE_OVER,
			"Go and rest, %1$s. I'll see to the %2$s.",
			"%1$s, step back, love. I've got this %2$s.");
	}

	private static void oak() {
		FriendId f = FriendId.OAK;
		Lines.define(f, Line.DRAW_BOW,
			"Measure twice, shoot once. Steady, %1$s.",
			"I've got a clear line on the %1$s.");
		Lines.define(f, Line.RAISE_SHIELD,
			"Shield up. Solid as a good wall.",
			"Bracing behind the shield.");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Need a repair. Using my %1$s.",
			"Running low. Time for the %1$s.");
		Lines.define(f, Line.NEW_GEAR,
			"Good workmanship, this %1$s. It'll hold.",
			"Fitted the %1$s. Sturdy job.");
		Lines.define(f, Line.MADE_GEAR,
			"Built to last and in the chest: %1$s.",
			"Another job done properly: %1$s.");
		Lines.define(f, Line.TAKE_OVER,
			"Off you go, %1$s. I'll handle the %2$s.",
			"%1$s, step back. This %2$s is on my list now.");
	}

	private static void flint() {
		FriendId f = FriendId.FLINT;
		Lines.define(f, Line.DRAW_BOW,
			"Arrows. Because walking up to a %1$s is for heroes.",
			"From over here, %1$s. Where it's safe.");
		Lines.define(f, Line.RAISE_SHIELD,
			"Shield up. I like having a wall in front of me.",
			"Shield. The sensible person's hat.");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"This is why I carry a %1$s.",
			"Right. %1$s. Before I become a cautionary tale.");
		Lines.define(f, Line.NEW_GEAR,
			"The %1$s. Less of me to hurt now. Good.",
			"Wearing the %1$s. I feel slightly less doomed.");
		Lines.define(f, Line.MADE_GEAR,
			"Made one: %1$s. Someone's less likely to die. Nice.",
			"Into the chest it goes: %1$s. You're welcome.");
		Lines.define(f, Line.TAKE_OVER,
			"Go, %1$s. I'll keep the %2$s busy. Reluctantly.",
			"Fall back, %1$s. The %2$s and I need a word.");
	}

	private static void scout() {
		FriendId f = FriendId.SCOUT;
		Lines.define(f, Line.DRAW_BOW,
			"Got eyes on the %1$s! Loosing!",
			"Watch this, %1$s! Long shot!",
			"%1$s at range! I've got it!");
		Lines.define(f, Line.RAISE_SHIELD,
			"Shield up! Incoming!",
			"Whoa! Ducking behind the shield!");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Ow! Quick, my %1$s!",
			"Not ending the adventure here! Using my %1$s!");
		Lines.define(f, Line.NEW_GEAR,
			"Ooh, the %1$s! Ready for anything now!",
			"New %1$s! I'm going to go so much further!");
		Lines.define(f, Line.MADE_GEAR,
			"Made it myself and it's in the chest: %1$s!",
			"Look what I made: %1$s! Into the chest!");
		Lines.define(f, Line.TAKE_OVER,
			"I've got the %2$s, %1$s! Run!",
			"Swap! You rest, %1$s, the %2$s is mine!");
	}

	private static void spark() {
		FriendId f = FriendId.SPARK;
		Lines.define(f, Line.DRAW_BOW,
			"Trajectory calculated! Hello, %1$s!",
			"Allowing for the drop, wind, and you, %1$s. Loose!");
		Lines.define(f, Line.RAISE_SHIELD,
			"Deploying the shield!",
			"Shield up! Brilliant invention, the shield!");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Emergency repairs! Using my %1$s!",
			"Running on fumes! %1$s, now!");
		Lines.define(f, Line.NEW_GEAR,
			"Upgrade installed: %1$s!",
			"Ooh, the %1$s! Excellent engineering!");
		Lines.define(f, Line.MADE_GEAR,
			"Prototype finished and in the chest: %1$s!",
			"Fresh off the workbench: %1$s!");
		Lines.define(f, Line.TAKE_OVER,
			"Switching over! Rest up, %1$s, I've got the %2$s!",
			"%1$s, out! The %2$s is my problem now!");
	}

	private static void aegis() {
		FriendId f = FriendId.AEGIS;
		Lines.define(f, Line.DRAW_BOW,
			"Out of reach, %1$s? Not of my arrows.",
			"%1$s. Hold still.");
		Lines.define(f, Line.RAISE_SHIELD,
			"Shields up. Stay behind me.",
			"Shield raised. Get behind me.");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Wounded. Taking my %1$s.",
			"%1$s. Then back to the fight.");
		Lines.define(f, Line.NEW_GEAR,
			"The %1$s. Good. I'll guard better for it.",
			"Equipped: %1$s.");
		Lines.define(f, Line.MADE_GEAR,
			"Forged and stored: %1$s. Someone will be safer.",
			"In the chest: %1$s. Wear it well.");
		Lines.define(f, Line.TAKE_OVER,
			"Fall back, %1$s. The %2$s is mine.",
			"%1$s, behind me. I have the %2$s.");
	}

	private static void sage() {
		FriendId f = FriendId.SAGE;
		Lines.define(f, Line.DRAW_BOW,
			"Distance is the wiser weapon, %1$s.",
			"A measured shot at the %1$s.");
		Lines.define(f, Line.RAISE_SHIELD,
			"Shield up. Prevention before cure.",
			"Raising my shield. Patience wins this.");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Lesson noted. Using my %1$s.",
			"The prudent choice: my %1$s.");
		Lines.define(f, Line.NEW_GEAR,
			"The %1$s. Preparation is half the battle.",
			"A sensible upgrade: %1$s.");
		Lines.define(f, Line.MADE_GEAR,
			"Planned, made and stored: %1$s.",
			"Another part of the plan in the chest: %1$s.");
		Lines.define(f, Line.TAKE_OVER,
			"Withdraw, %1$s. I'll keep the %2$s occupied.",
			"%1$s, fall back and recover. The %2$s is mine.");
	}

	private static void terra() {
		FriendId f = FriendId.TERRA;
		Lines.define(f, Line.DRAW_BOW,
			"Away from the flowerbeds, %1$s!",
			"Hold still, %1$s. I never miss a weed.");
		Lines.define(f, Line.RAISE_SHIELD,
			"Shield up! Mind the paths!",
			"Hiding behind my shield!");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Ouch! Time for my %1$s.",
			"Patching myself up with my %1$s.");
		Lines.define(f, Line.NEW_GEAR,
			"Ooh, the %1$s! Smart and tidy.",
			"The %1$s suits me, don't you think?");
		Lines.define(f, Line.MADE_GEAR,
			"Made with care and in the chest: %1$s.",
			"Neatly made and stored: %1$s.");
		Lines.define(f, Line.TAKE_OVER,
			"Go on, %1$s, rest! I'll tidy up this %2$s.",
			"%1$s, back off! This %2$s is mine to sweep up.");
	}

	private static void rowan() {
		FriendId f = FriendId.ROWAN;
		Lines.define(f, Line.DRAW_BOW,
			"Easy does it, %1$s. Arrow's ready.",
			"I've got a shot at the %1$s.");
		Lines.define(f, Line.RAISE_SHIELD,
			"Shield up! Stay close!",
			"Getting behind my shield!");
		Lines.define(f, Line.EMERGENCY_HEAL,
			"Good thing I kept my %1$s handy.",
			"Bit battered. Using my %1$s.");
		Lines.define(f, Line.NEW_GEAR,
			"The %1$s! I'll make good use of it.",
			"Wearing the %1$s. Thanks, whoever stocked it!");
		Lines.define(f, Line.MADE_GEAR,
			"Made a spare for whoever needs it: %1$s.",
			"Put this in the chest for the team: %1$s.");
		Lines.define(f, Line.TAKE_OVER,
			"Go on, %1$s, I'll deal with the %2$s.",
			"Swap, %1$s! I've got the %2$s.");
	}
}

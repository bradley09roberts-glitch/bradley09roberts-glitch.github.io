package io.github.bradley09roberts.hardcorefriends.defence;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say when the village defends itself: the alarm bell, taking cover, going to the walls, the
 * all-clear, a raid beaten off and a fire put out, in each friend's own voice (the rules of the main table in
 * {@link Lines}: British English, at most 90 characters before the danger is filled in, only the placeholders each line
 * documents). The danger arrives in lower case ("monsters", "a creeper", "raiders", "raiders on the way", "trouble"),
 * so it always follows "look out for" or "watch out for". Children say these in their trade's voice, so none of them
 * talks down to "the little ones".
 */
final class DefenceLines {
	private DefenceLines() {
	}

	/** Adds every defence line's wording (called once from {@link Defence#init()}). */
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
		Lines.defineGeneric(Line.ALARM_BELL, "Ring the bell! Watch out for %1$s! Everyone indoors!",
			"Danger! Look out for %1$s! Inside, everyone, quickly!", "The bell! Look out for %1$s! Fighters, to your posts!");
		Lines.defineGeneric(Line.TAKE_COVER, "Taking cover indoors.", "Inside, quickly!", "I'll wait indoors until it's safe.");
		Lines.defineGeneric(Line.TO_THE_WALLS, "To the walls!", "Taking my post.", "I'll stand guard.");
		Lines.defineGeneric(Line.ALL_CLEAR, "All clear! You can come out now.", "It's safe again. All clear!", "All clear, everyone.");
		Lines.defineGeneric(Line.RAID_WON, "We beat off the raid! The village is safe!", "The raiders are gone. We did it!",
			"Raid over, and we won!");
		Lines.defineGeneric(Line.FIRE_OUT, "That fire's out.", "Fire's out. No harm done.", "There, the fire's out.");
	}

	private static void fern() {
		FriendId f = FriendId.FERN;
		Lines.define(f, Line.ALARM_BELL,
			"Ring the bell! Oh, look out for %1$s! Everyone inside, quickly now!",
			"Indoors, please, everyone! Watch out for %1$s!");
		Lines.define(f, Line.TAKE_COVER,
			"Indoors I go. Stay close, everyone, and keep away from the windows.",
			"I'll be inside, out of harm's way. Do be careful out there.");
		Lines.define(f, Line.TO_THE_WALLS,
			"I'll stand my post. Nobody hurts our village.",
			"To the walls, then. Stay safe, everyone.");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear, dears. Out you come, it's safe now.",
			"It's over. Is everyone all right? All clear!");
		Lines.define(f, Line.RAID_WON,
			"The raiders are gone! Oh, I'm so proud of everyone.",
			"We did it! Is everyone safe? The raid is over.");
		Lines.define(f, Line.FIRE_OUT,
			"There, the fire's out. Nobody hurt, I hope?",
			"Fire's out. That gave me a fright.");
	}

	private static void oak() {
		FriendId f = FriendId.OAK;
		Lines.define(f, Line.ALARM_BELL,
			"Bell's ringing! Watch out for %1$s. Doors shut, tools up!",
			"Sound the bell! Look out for %1$s. Everyone inside, fighters with me!");
		Lines.define(f, Line.TAKE_COVER,
			"Heading in. Good walls make a safe house.",
			"Inside I go. Sturdy doors, these.");
		Lines.define(f, Line.TO_THE_WALLS,
			"To my post. Let them try these walls.",
			"On guard. I built this place, and I'll hold it.");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear. Back to work, everyone.",
			"All clear. I'll check the walls for damage.");
		Lines.define(f, Line.RAID_WON,
			"Raid beaten. The walls held, and so did we.",
			"That's the last of them. Good, solid work, all.");
		Lines.define(f, Line.FIRE_OUT,
			"Fire's out. I'll see to the repairs.",
			"That's the fire out. The house will mend.");
	}

	private static void flint() {
		FriendId f = FriendId.FLINT;
		Lines.define(f, Line.ALARM_BELL,
			"Ring the bell. Watch out for %1$s. I knew it was too quiet.",
			"Bell! Look out for %1$s. Indoors, all of you. Now.");
		Lines.define(f, Line.TAKE_COVER,
			"Indoors. Away from the windows. Sensible.",
			"I'm going inside. Tell me when nothing's trying to eat us.");
		Lines.define(f, Line.TO_THE_WALLS,
			"To my post. I'll be the one expecting the worst.",
			"On guard. Something always comes out of the dark.");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear. For now, anyway.",
			"All clear. You can stop holding your breath.");
		Lines.define(f, Line.RAID_WON,
			"The raid's over and we're still here. I'll take it.",
			"We beat them. I'm almost cheerful about it.");
		Lines.define(f, Line.FIRE_OUT,
			"Fire's out. As if monsters weren't enough.",
			"Put it out. Nothing burns on my watch.");
	}

	private static void scout() {
		FriendId f = FriendId.SCOUT;
		Lines.define(f, Line.ALARM_BELL,
			"Ring the bell! Look out for %1$s! Everyone get inside!",
			"Bell's ringing! Watch out for %1$s! Fighters, with me!");
		Lines.define(f, Line.TAKE_COVER,
			"Inside! I'll want to hear everything afterwards!",
			"Going in! I'll watch from the doorway. No, further in.");
		Lines.define(f, Line.TO_THE_WALLS,
			"To the walls! Best view in the village!",
			"On guard! Nothing's sneaking past me!");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear! Did everyone see that?",
			"All clear! Out you come, it's quiet again!");
		Lines.define(f, Line.RAID_WON,
			"We won! The raiders are running for the hills!",
			"Raid beaten! What a story to tell!");
		Lines.define(f, Line.FIRE_OUT,
			"Fire's out! That was close!",
			"Stamped that fire out! All safe!");
	}

	private static void spark() {
		FriendId f = FriendId.SPARK;
		Lines.define(f, Line.ALARM_BELL,
			"Ding ding ding! Look out for %1$s! Everybody inside, now!",
			"That's the alarm! Watch out for %1$s! Doors shut, quick!");
		Lines.define(f, Line.TAKE_COVER,
			"Inside! Away from the windows! I read that somewhere!",
			"Indoors! If only I'd built that iron door already!");
		Lines.define(f, Line.TO_THE_WALLS,
			"To the walls! Let's see them get past this!",
			"Guard duty! I'll keep my eyes wide open!");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear! Phew! That was exciting!",
			"All clear! Everyone out, everyone safe!");
		Lines.define(f, Line.RAID_WON,
			"We won! We actually won! The raid is over!",
			"Raiders gone! Best teamwork ever!");
		Lines.define(f, Line.FIRE_OUT,
			"Fire's out! Note to self: no sparks near the hay.",
			"Out it goes! Fires and I don't mix.");
	}

	private static void aegis() {
		FriendId f = FriendId.AEGIS;
		Lines.define(f, Line.ALARM_BELL,
			"The bell. Look out for %1$s. Inside, all of you. Fighters, with me.",
			"Ring the bell. Watch out for %1$s. I'll hold them off.");
		Lines.define(f, Line.TAKE_COVER,
			"Too hurt to hold the line. Going inside.",
			"Taking cover. I'll be back out soon.");
		Lines.define(f, Line.TO_THE_WALLS,
			"To the walls.",
			"Taking my post. Nothing gets through.");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear.",
			"Safe now. All clear.");
		Lines.define(f, Line.RAID_WON,
			"The raid is broken. Well fought.",
			"They're beaten. The village stands.");
		Lines.define(f, Line.FIRE_OUT,
			"The fire is out.",
			"Fire's out. Stay back from the embers.");
	}

	private static void sage() {
		FriendId f = FriendId.SAGE;
		Lines.define(f, Line.ALARM_BELL,
			"Ring the bell! Watch out for %1$s. Indoors, everyone; fighters to the posts.",
			"The bell! Look out for %1$s. Calmly now, everyone to their places.");
		Lines.define(f, Line.TAKE_COVER,
			"Indoors, and away from the windows. Panic helps nobody.",
			"Taking shelter. Let the fighters do their work.");
		Lines.define(f, Line.TO_THE_WALLS,
			"To my post. A good watch is half the battle.",
			"Taking my place at the wall. Stay calm, everyone.");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear. Well done, everyone. We held together.",
			"The danger has passed. All clear.");
		Lines.define(f, Line.RAID_WON,
			"The raid is over. We stood together and we won.",
			"Victory. Well planned and well fought, everyone.");
		Lines.define(f, Line.FIRE_OUT,
			"The fire is out. Caught early, luckily.",
			"Fire's out. We were lucky this time.");
	}

	private static void terra() {
		FriendId f = FriendId.TERRA;
		Lines.define(f, Line.ALARM_BELL,
			"Ring the bell! Look out for %1$s! Everyone indoors, and mind the flowers!",
			"The bell! Watch out for %1$s! Inside, everyone, quickly!");
		Lines.define(f, Line.TAKE_COVER,
			"Inside I go. I hope they keep off the garden.",
			"Indoors! Wipe your feet, everyone.");
		Lines.define(f, Line.TO_THE_WALLS,
			"To the walls! Our lovely village stays safe.",
			"On guard. Nobody tramples these streets tonight.");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear! Now, let's tidy up.",
			"All clear, everyone. The village is safe.");
		Lines.define(f, Line.RAID_WON,
			"The raiders are gone! Now to mend the gardens.",
			"We won! And the village is still beautiful.");
		Lines.define(f, Line.FIRE_OUT,
			"Fire's out. Oh, my poor flowerbeds.",
			"That's out. I'll plant something new there.");
	}

	private static void rowan() {
		FriendId f = FriendId.ROWAN;
		Lines.define(f, Line.ALARM_BELL,
			"Ring the bell! Look out for %1$s! Inside, you lot, quick as you can!",
			"Bell's going! Watch out for %1$s! Everyone get indoors!");
		Lines.define(f, Line.TAKE_COVER,
			"In we go! I've got snacks if anyone gets peckish.",
			"Taking cover. Plenty of room in here, come on in!");
		Lines.define(f, Line.TO_THE_WALLS,
			"To my post! Shout if you need me.",
			"On guard, then. I'll keep an eye out for you all.");
		Lines.define(f, Line.ALL_CLEAR,
			"All clear! Who's hungry after all that?",
			"All clear! Come on out, you lot.");
		Lines.define(f, Line.RAID_WON,
			"We did it! A feast for everyone, I say!",
			"Raid's over! Well done, you lot!");
		Lines.define(f, Line.FIRE_OUT,
			"Fire's out! Nothing lost but a bit of wood.",
			"Put that one out. Everyone all right?");
	}
}

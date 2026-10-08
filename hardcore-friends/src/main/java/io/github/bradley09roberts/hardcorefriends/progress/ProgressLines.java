package io.github.bradley09roberts.hardcorefriends.progress;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * The friends' words for beating the game: Sage's next goal and each step reached, diamonds found, lava walled off,
 * obsidian made, gear enchanted and mended, potions brewed. Generic wording plus each friend's own, in their voice.
 * Goals and step names arrive as phrases ("a diamond pickaxe in the camp", "Iron age"), so they come last, after a
 * colon; item names arrive capitalised as the game shows them ("Iron Sword"), potions in lower case ("fire resistance").
 */
public final class ProgressLines {
	private ProgressLines() {
	}

	public static void register() {
		Lines.defineGeneric(Line.GOAL_NEW, "Our next goal: %1$s.", "Here's what we work towards now: %1$s.");
		Lines.defineGeneric(Line.GOAL_REACHED, "Another step of the plan done: %1$s!", "We did it: %1$s!");
		Lines.defineGeneric(Line.FOUND_DIAMONDS, "Diamonds! Right here in the wall!", "I've found diamonds!");
		Lines.defineGeneric(Line.LAVA_SEALED, "Lava behind this wall. I'll leave it closed and go round.",
			"There's lava here. Going round it, carefully.");
		Lines.defineGeneric(Line.OBSIDIAN_MADE, "Water on lava, and there's obsidian.", "Made some obsidian.");
		Lines.defineGeneric(Line.ENCHANTED, "Enchanted the %1$s.", "The %1$s is enchanted now.");
		Lines.defineGeneric(Line.POTIONS_BREWED, "Potions of %1$s, ready for the chest.", "Brewed some %1$s.");
		Lines.defineGeneric(Line.REPAIRED, "Mended the %1$s at the anvil.", "The %1$s is as good as new.");

		FriendId f = FriendId.FERN;
		Lines.define(f, Line.GOAL_NEW,
			"Sage has a new goal for us, everyone: %1$s.",
			"Next, together, step by step: %1$s.");
		Lines.define(f, Line.GOAL_REACHED,
			"Look how far we've come! Done: %1$s.",
			"Well done, everyone. That's another step: %1$s!");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Oh, diamonds! They sparkle like dew in the morning.",
			"Diamonds! I'll bring them home safe.");
		Lines.define(f, Line.LAVA_SEALED,
			"There's lava just here. I'll leave the wall be and go round.",
			"Lava! Nobody come this way, please. I'm going round it.");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"A splash of water and the lava's gone hard. Obsidian!",
			"Obsidian, made gently. No burns today.");
		Lines.define(f, Line.ENCHANTED,
			"The %1$s has a little magic in it now. Use it well.",
			"Enchanted the %1$s. That should keep someone safer.");
		Lines.define(f, Line.POTIONS_BREWED,
			"Some %1$s, brewed with care. They're in the chest.",
			"Potions of %1$s, like a herb tea, only stronger.");
		Lines.define(f, Line.REPAIRED,
			"I've mended the %1$s. A bit of care goes a long way.",
			"The %1$s is mended. Good as new for whoever needs it.");

		f = FriendId.OAK;
		Lines.define(f, Line.GOAL_NEW,
			"Next on the plans: %1$s.",
			"New target, and we build towards it properly: %1$s.");
		Lines.define(f, Line.GOAL_REACHED,
			"Solid work. Step complete: %1$s.",
			"Ticked off the plan: %1$s. On to the next.");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds. That's the next stage of the plan sorted.",
			"Found diamonds. Hard stuff, good tools coming.");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava behind the stone. The wall stays; I'll work round it.",
			"Lava here. Not touching that wall.");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Cast some obsidian. Strongest building block there is.",
			"Obsidian made. That's portal stock.");
		Lines.define(f, Line.ENCHANTED,
			"Enchanted the %1$s. Better tools, better work.",
			"The %1$s is enchanted. Built to last now.");
		Lines.define(f, Line.POTIONS_BREWED,
			"Brewed some %1$s. Stored and labelled.",
			"Potions of %1$s, done. In the chest.");
		Lines.define(f, Line.REPAIRED,
			"Mended the %1$s. Waste not.",
			"The %1$s is repaired. Good for plenty more work.");

		f = FriendId.FLINT;
		Lines.define(f, Line.GOAL_NEW,
			"Right. The new goal, apparently: %1$s.",
			"Next item on the list of things that might go wrong: %1$s.");
		Lines.define(f, Line.GOAL_REACHED,
			"Well, nobody fell in lava. Step done: %1$s.",
			"Done, and in one piece: %1$s.");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds. Don't get excited. Fine, get a bit excited.",
			"Diamonds in the wall. Worth every careful step.");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava behind this one. The wall stays exactly where it is.",
			"Lava. I'm going round it. Very far round it.");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Water on lava. Obsidian. No eyebrows lost.",
			"Made obsidian. Lava's much nicer when it's solid.");
		Lines.define(f, Line.ENCHANTED,
			"Enchanted the %1$s. Still not fireproof, mind.",
			"The %1$s glows now. Handy in a dark tunnel.");
		Lines.define(f, Line.POTIONS_BREWED,
			"Potions of %1$s. I'll take two, just in case.",
			"Brewed some %1$s. Much safer than the alternative.");
		Lines.define(f, Line.REPAIRED,
			"Mended the %1$s. Cheaper than a new one.",
			"The %1$s is fixed. One less thing to worry about.");

		f = FriendId.SCOUT;
		Lines.define(f, Line.GOAL_NEW,
			"New adventure! Next we go for: %1$s!",
			"Ooh, a new goal on the map: %1$s!");
		Lines.define(f, Line.GOAL_REACHED,
			"We did it! Another landmark: %1$s!",
			"Mark it on the map! Done: %1$s!");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds! Shiny, shiny diamonds!",
			"Look, diamonds! Best find all week!");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava ahead! Marking it down and going round!",
			"Whoa, lava! This way's closed. Taking the long route!");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Sizzle! Lava into obsidian, just like that!",
			"Made obsidian! That's the stuff portals are made of!");
		Lines.define(f, Line.ENCHANTED,
			"The %1$s is glowing! It looks amazing!",
			"Enchanted the %1$s! Ready for an adventure!");
		Lines.define(f, Line.POTIONS_BREWED,
			"Potions of %1$s! Perfect for a long trip!",
			"Brewed some %1$s! Packing a few for the road!");
		Lines.define(f, Line.REPAIRED,
			"The %1$s is fixed! Back out exploring with it!",
			"Mended the %1$s! Good for another hundred miles!");

		f = FriendId.SPARK;
		Lines.define(f, Line.GOAL_NEW,
			"New goal! Brilliant! Next up: %1$s!",
			"I've got ideas already! The next goal: %1$s!");
		Lines.define(f, Line.GOAL_REACHED,
			"Yes! Step complete: %1$s! What's next?",
			"Another step done: %1$s! The plan works!");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds! Think of the tools! Think of the gadgets!",
			"Diamonds! Oh, this is brilliant!");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava behind the wall! Not today, lava. Going round!",
			"Lava! Fascinating, but I'm leaving that wall alone!");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Water plus lava equals obsidian! Science!",
			"Obsidian! Hardest block going, made in a second!");
		Lines.define(f, Line.ENCHANTED,
			"Enchanted the %1$s! Look at it shimmer!",
			"The %1$s is enchanted! Magic is just clever maths!");
		Lines.define(f, Line.POTIONS_BREWED,
			"Potions of %1$s! The brewing stand's a marvel!",
			"Brewed some %1$s! Bubbling, fizzing, done!");
		Lines.define(f, Line.REPAIRED,
			"Mended the %1$s! Good engineering, that anvil.",
			"The %1$s is fixed up! Nothing wasted!");

		f = FriendId.AEGIS;
		Lines.define(f, Line.GOAL_NEW,
			"Our next goal: %1$s. I'll keep everyone safe on the way.",
			"New goal: %1$s. We go together.");
		Lines.define(f, Line.GOAL_REACHED,
			"Step done: %1$s. Well done, all of you.",
			"We did it: %1$s. Stay sharp for the next one.");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds. Good. Better armour for the team.",
			"Found diamonds. That will keep someone alive.");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava here. The wall stays. Nobody goes this way.",
			"Lava behind the stone. Going round it.");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Obsidian made. Steady hands.",
			"Lava turned to stone. Safely done.");
		Lines.define(f, Line.ENCHANTED,
			"The %1$s is enchanted. It will serve well.",
			"Enchanted the %1$s. Stronger protection for us.");
		Lines.define(f, Line.POTIONS_BREWED,
			"Potions of %1$s. Keep one with you.",
			"Brewed some %1$s. Good for a hard fight.");
		Lines.define(f, Line.REPAIRED,
			"Mended the %1$s. Ready for the next fight.",
			"The %1$s is sound again.");

		f = FriendId.SAGE;
		Lines.define(f, Line.GOAL_NEW,
			"I've thought it through. Our next goal: %1$s.",
			"The plan's next step, as I see it: %1$s.");
		Lines.define(f, Line.GOAL_REACHED,
			"Exactly as planned. Step reached: %1$s.",
			"A good day for the plan. Done: %1$s.");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds. That moves the whole plan forward.",
			"Interesting: diamonds, just where I'd expect them.");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava behind the wall. Wiser to go round than through.",
			"I've noted lava here. We leave this wall standing.");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Obsidian, as planned. Two more steps closer.",
			"Lava and water, patiently combined: obsidian.");
		Lines.define(f, Line.ENCHANTED,
			"I've enchanted the %1$s. Knowledge, put to use.",
			"The %1$s carries an enchantment now. Use it wisely.");
		Lines.define(f, Line.POTIONS_BREWED,
			"Potions of %1$s. Preparation wins the day.",
			"Brewed some %1$s. We'll be glad of them later.");
		Lines.define(f, Line.REPAIRED,
			"Mended the %1$s. Mending is cheaper than replacing.",
			"The %1$s is repaired. A sensible use of iron.");

		f = FriendId.TERRA;
		Lines.define(f, Line.GOAL_NEW,
			"A new goal! I can picture it already: %1$s.",
			"Next we make this happen, beautifully: %1$s.");
		Lines.define(f, Line.GOAL_REACHED,
			"How lovely! That's done: %1$s.",
			"Another step, neat and tidy: %1$s!");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds! Such a pretty blue in the stone.",
			"Oh, diamonds! They'd look gorgeous in a wall. Tools first.");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava back there. I'll leave the wall neat and go round.",
			"There's lava here. Not a nice colour for a tunnel. Going round.");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Obsidian! So dark and glossy.",
			"Made some obsidian. What a gorgeous black.");
		Lines.define(f, Line.ENCHANTED,
			"The %1$s has such a pretty shimmer now.",
			"Enchanted the %1$s. It looks lovely.");
		Lines.define(f, Line.POTIONS_BREWED,
			"Potions of %1$s, all lined up in the chest.",
			"Brewed some %1$s. Such a lovely colour.");
		Lines.define(f, Line.REPAIRED,
			"Mended the %1$s. Tidy and good as new.",
			"The %1$s is fixed. Nice and neat again.");

		f = FriendId.ROWAN;
		Lines.define(f, Line.GOAL_NEW,
			"Next goal, and I'll gather whatever it takes: %1$s.",
			"New goal for all of us: %1$s.");
		Lines.define(f, Line.GOAL_REACHED,
			"We got there together! Done: %1$s.",
			"That's a step done: %1$s. Everyone helped!");
		Lines.define(f, Line.FOUND_DIAMONDS,
			"Diamonds! Enough to share, I hope.",
			"Found diamonds! Into the chest for everyone.");
		Lines.define(f, Line.LAVA_SEALED,
			"Lava behind here. Leaving it shut and going round.",
			"Lava! I'll find another way through.");
		Lines.define(f, Line.OBSIDIAN_MADE,
			"Made obsidian. Nothing wasted, not even lava.",
			"A bucket of water, and the lava's useful now.");
		Lines.define(f, Line.ENCHANTED,
			"Enchanted the %1$s. It's in the chest for whoever needs it.",
			"The %1$s is enchanted. A gift for the team.");
		Lines.define(f, Line.POTIONS_BREWED,
			"Brewed some %1$s. Plenty to go round.",
			"Potions of %1$s in the chest. Help yourselves!");
		Lines.define(f, Line.REPAIRED,
			"Mended the %1$s. Why waste a good one?",
			"The %1$s is fixed. Back in the chest for anyone.");
	}
}

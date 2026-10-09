package io.github.bradley09roberts.hardcorefriends.architecture;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * The friends' words for building properly: putting up scaffolding, finishing a village building, firing building
 * materials in the furnace, digging sand or clay, and shearing sheep for wool. Generic wording plus each friend's own,
 * in their voice. Building names arrive in lower case ("cabin", "oak cottage"), materials too ("glass", "smooth
 * stone"), and what is dug is "sand" or "clay".
 */
public final class ArchitectureLines {
	private ArchitectureLines() {
	}

	public static void register() {
		Lines.defineGeneric(Line.SCAFFOLDING, "Putting up a bit of scaffolding to reach the top of the %1$s.",
			"Too high to reach from here. Up a pillar I go.");
		Lines.defineGeneric(Line.BUILDING_FINISHED, "The %1$s is finished!", "There. The %1$s is built and ready.");
		Lines.defineGeneric(Line.FIRING_KILN, "Firing some %1$s in the furnace for the builders.",
			"The builders need %1$s. Into the furnace it goes.");
		Lines.defineGeneric(Line.DIGGING_SAND, "Off to dig some %1$s for the builders.",
			"The builders need %1$s. I know where to find it.");
		Lines.defineGeneric(Line.SHEARING, "Just a trim! The wool is for our beds.", "Shearing a sheep. It will grow back.");

		FriendId f = FriendId.FERN;
		Lines.define(f, Line.SCAFFOLDING,
			"Up I go, gently. The %1$s needs a hand up top.",
			"A little scaffolding so I can reach the top of the %1$s.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is finished. I hope it feels like home.",
			"All done! The %1$s is ready for whoever needs it.");
		Lines.define(f, Line.FIRING_KILN,
			"A warm furnace and some %1$s for the builders.",
			"Making %1$s for the walls and windows. Nice and slow.");
		Lines.define(f, Line.DIGGING_SAND,
			"I'll fetch a little %1$s from the dry ground.",
			"Off to dig some %1$s for the builders. Back soon.");
		Lines.define(f, Line.SHEARING,
			"Hold still, little one. Just a gentle trim.",
			"A bit of wool for our beds. Thank you, sheep.");

		f = FriendId.OAK;
		Lines.define(f, Line.SCAFFOLDING,
			"Scaffold up. Can't build the top of the %1$s from the ground.",
			"Pillar first, then the top of the %1$s. Proper job.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is up. Square, solid and roofed.",
			"Finished the %1$s. That one will stand for years.");
		Lines.define(f, Line.FIRING_KILN,
			"Firing %1$s. Good materials make good buildings.",
			"Furnace loaded for %1$s. We'll need it soon.");
		Lines.define(f, Line.DIGGING_SAND,
			"Fetching %1$s. Glass and bricks start in the ground.",
			"Need %1$s for the build. Digging some now.");
		Lines.define(f, Line.SHEARING,
			"Wool for the beds. A clean shear.",
			"Shearing. Beds need wool.");

		f = FriendId.FLINT;
		Lines.define(f, Line.SCAFFOLDING,
			"Climbing a dirt pillar. Not my favourite way up the %1$s.",
			"Scaffolding up, then straight back down. Carefully.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is done. Not a block out of place.",
			"Finished the %1$s. Roof and all. I checked twice.");
		Lines.define(f, Line.FIRING_KILN,
			"Furnace on for %1$s. Mind the heat.",
			"Smelting %1$s. Watching the fuel, as ever.");
		Lines.define(f, Line.DIGGING_SAND,
			"Digging %1$s. Away from the water, of course.",
			"Off for %1$s. Only the top layer, no holes.");
		Lines.define(f, Line.SHEARING,
			"Shearing a sheep. It's not even grumpy about it.",
			"A quick trim. The sheep looks cooler already.");

		f = FriendId.SCOUT;
		Lines.define(f, Line.SCAFFOLDING,
			"Up the scaffold! Best view of the %1$s from up here.",
			"A pillar to climb? Don't mind if I do.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is finished! Come and look!",
			"Done! The %1$s looks even better from the outside.");
		Lines.define(f, Line.FIRING_KILN,
			"Into the furnace! Some %1$s coming up.",
			"Making %1$s. I'll find more if we need it.");
		Lines.define(f, Line.DIGGING_SAND,
			"I spotted %1$s out there. Back soon!",
			"Off to dig %1$s! Race you.");
		Lines.define(f, Line.SHEARING,
			"Got you, woolly! Just a trim.",
			"Wool hunt! No sheep harmed.");

		f = FriendId.SPARK;
		Lines.define(f, Line.SCAFFOLDING,
			"Vertical access! Scaffold going up beside the %1$s.",
			"Stacking blocks to reach the top. Simple and brilliant.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is complete! Every block just as planned.",
			"Finished the %1$s! I love it when a plan comes together.");
		Lines.define(f, Line.FIRING_KILN,
			"Furnace loaded. Science in progress: %1$s!",
			"A little heat turns rough stuff into %1$s. Lovely!");
		Lines.define(f, Line.DIGGING_SAND,
			"Fetching %1$s: the raw stuff of glass and bricks!",
			"Off to dig %1$s. Raw materials, here I come!");
		Lines.define(f, Line.SHEARING,
			"Shears ready! Wool, here we come.",
			"Snip, snip! Wool that grows back. Fascinating.");

		f = FriendId.AEGIS;
		Lines.define(f, Line.SCAFFOLDING,
			"Going up to finish the %1$s. Watch the ground for me.",
			"Scaffold up. I'll be quick and come straight down.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is finished. Safe walls and a good roof.",
			"The %1$s stands. One more place we can keep safe.");
		Lines.define(f, Line.FIRING_KILN,
			"Loading the furnace to make %1$s.",
			"Making %1$s for the builders. The fuel is in.");
		Lines.define(f, Line.DIGGING_SAND,
			"Going for %1$s. I'll keep an eye out on the way.",
			"Fetching %1$s for the builders. Back before dark.");
		Lines.define(f, Line.SHEARING,
			"Easy now. Just taking the wool.",
			"Shearing a sheep. Gently does it.");

		f = FriendId.SAGE;
		Lines.define(f, Line.SCAFFOLDING,
			"From up here the %1$s will be easier to finish.",
			"A pillar to reach the top, and then it comes down again.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is finished. The village grows.",
			"Another building done: the %1$s. Well planned, well built.");
		Lines.define(f, Line.FIRING_KILN,
			"The builders will need %1$s. Best make it now.",
			"Firing %1$s ahead of time. Planning pays.");
		Lines.define(f, Line.DIGGING_SAND,
			"We are short of %1$s. I'll gather some.",
			"Digging %1$s, a little from each place.");
		Lines.define(f, Line.SHEARING,
			"Wool grows back. A fair trade with the sheep.",
			"Shearing, for warm beds in the winter.");

		f = FriendId.TERRA;
		Lines.define(f, Line.SCAFFOLDING,
			"A tidy little scaffold for the %1$s. It comes down after.",
			"Up to the roofline. Not a block will be left behind.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is done, and it looks lovely.",
			"Finished the %1$s. It fits the place nicely.");
		Lines.define(f, Line.FIRING_KILN,
			"Making %1$s. It will look lovely in the walls.",
			"Into the furnace for some %1$s. Nice and neat.");
		Lines.define(f, Line.DIGGING_SAND,
			"Taking a little %1$s from the top. No ugly pits.",
			"Off for %1$s. I'll leave the ground tidy.");
		Lines.define(f, Line.SHEARING,
			"A neat trim! Lovely soft wool.",
			"Shearing. I'll leave the sheep looking tidy.");

		f = FriendId.ROWAN;
		Lines.define(f, Line.SCAFFOLDING,
			"Borrowing some dirt for a scaffold. It all goes back.",
			"Up a pillar to reach the %1$s. Back down soon.");
		Lines.define(f, Line.BUILDING_FINISHED,
			"The %1$s is finished! Plenty of room for everyone.",
			"All built: the %1$s. Every block well found.");
		Lines.define(f, Line.FIRING_KILN,
			"Making %1$s from what we gathered. Nothing wasted.",
			"Furnace on for %1$s, with our own fuel.");
		Lines.define(f, Line.DIGGING_SAND,
			"I'll bring back %1$s for everyone.",
			"Fetching %1$s. There's plenty out there.");
		Lines.define(f, Line.SHEARING,
			"Wool for everyone's beds! Thank you, sheep.",
			"A little wool to share. The sheep won't miss it.");
	}
}

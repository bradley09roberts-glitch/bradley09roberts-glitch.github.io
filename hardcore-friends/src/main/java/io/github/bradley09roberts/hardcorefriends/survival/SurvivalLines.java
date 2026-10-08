package io.github.bradley09roberts.hardcorefriends.survival;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * Every friend's wording for this package's lines: trips, finds, trades, night shelters, getting out of reach,
 * breaking a fall, levelling up and making room for a building. The same rules as the main table apply (see
 * {@code Lines}): British English, at most 90 characters before the arguments go in, only the placeholders each line
 * documents, and at least two variants of their own for each of the nine friends. Trip summaries arrive as
 * phrases that follow "I" ("found a village and the dark forest", "brought back 6 bread").
 */
final class SurvivalLines {
	private SurvivalLines() {
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
		Lines.defineGeneric(Line.TRIP_START, "Off on a trip to %1$s. Back before dark.", "Setting off for %1$s. See you later.");
		Lines.defineGeneric(Line.TRIP_FIND, "Found something: %1$s.", "Worth knowing: %1$s.");
		Lines.defineGeneric(Line.TRIP_BACK, "Back at camp. I %1$s.", "Home again. On the trip I %1$s.");
		Lines.defineGeneric(Line.TRIP_TURN_BACK, "Time to turn back. Heading home.", "That's far enough for today. Home I go.");
		Lines.defineGeneric(Line.TRADED, "Traded for %1$s.", "A fair trade: %1$s.");
		Lines.defineGeneric(Line.SHELTER, "Too far from camp to make it back. Digging in for the night.",
			"Night's caught me out. I'll shelter here till morning.");
		Lines.defineGeneric(Line.SHELTER_MORNING, "Morning. Packing up my shelter and heading home.",
			"Daylight. Taking my shelter down and off home.");
		Lines.defineGeneric(Line.CORNERED, "Surrounded! Up out of reach!", "Too many of them! Climbing up!");
		Lines.defineGeneric(Line.CLUTCH, "Water bucket, just in time!", "Phew. Landed in my own puddle.");
		Lines.defineGeneric(Line.LEVEL_UP, "I'm getting better at %1$s: level %2$s!", "Level %2$s at %1$s now. Practice pays.");
		Lines.defineGeneric(Line.LOOKING_FURTHER, "No room for the %1$s in camp. We'll spread out a little.",
			"The camp's too cramped for the %1$s. Looking a bit further out.");
	}

	private static void fern() {
		FriendId f = FriendId.FERN;
		Lines.define(f, Line.TRIP_START,
			"I'm off to %1$s. I'll be back before supper.",
			"Just popping over to %1$s. Don't worry about me.");
		Lines.define(f, Line.TRIP_FIND,
			"Oh, look what I've found: %1$s.",
			"Something worth knowing, everyone: %1$s.");
		Lines.define(f, Line.TRIP_BACK,
			"Home safe and sound. I %1$s.",
			"Back again, dears. On my way I %1$s.");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"That's enough walking for one day. Home I go.",
			"I'd better turn back now. Better safe than sorry.");
		Lines.define(f, Line.TRADED,
			"A lovely trade: %1$s for the larder.",
			"Got %1$s. That will help us all.");
		Lines.define(f, Line.SHELTER,
			"I'm too far from home tonight. I'll tuck in here till morning.",
			"It's dark and camp is far. A snug little shelter will do.");
		Lines.define(f, Line.SHELTER_MORNING,
			"Good morning! Tidying my shelter away, then home for breakfast.",
			"Morning already. I'll take this down and head home.");
		Lines.define(f, Line.CORNERED,
			"Oh dear, there are too many! Up I go!",
			"I can't get away! Climbing up out of reach!");
		Lines.define(f, Line.CLUTCH,
			"Oh my, that was a drop! Good thing I had water.",
			"Splash! A bucket of water saves the day.");
		Lines.define(f, Line.LEVEL_UP,
			"I'm getting rather good at %1$s. Level %2$s!",
			"Level %2$s at %1$s. Slow and steady does it.");
		Lines.define(f, Line.LOOKING_FURTHER,
			"There's no room for the %1$s. Let's make the camp a little bigger.",
			"We need more space for the %1$s. I'll look a bit further out.");
	}

	private static void oak() {
		FriendId f = FriendId.OAK;
		Lines.define(f, Line.TRIP_START,
			"Heading to %1$s. Back by sundown.",
			"Trip to %1$s. Planned it out. Off I go.");
		Lines.define(f, Line.TRIP_FIND,
			"Worth noting for the plans: %1$s.",
			"Found: %1$s. Useful.");
		Lines.define(f, Line.TRIP_BACK,
			"Back at camp. I %1$s.",
			"Trip done. I %1$s.");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"Not worth the risk. Turning back.",
			"Calling it there. Back to camp.");
		Lines.define(f, Line.TRADED,
			"Fair trade: %1$s.",
			"Got %1$s. That's supplies sorted.");
		Lines.define(f, Line.SHELTER,
			"Too far to get back before dark. Building a shelter.",
			"Night's here. Walls up, then rest.");
		Lines.define(f, Line.SHELTER_MORNING,
			"Morning. Taking the shelter down. Leave no mess.",
			"Light's up. Packing the blocks and heading back.");
		Lines.define(f, Line.CORNERED,
			"Too many to fight. Building up.",
			"Cornered. Going up a few blocks.");
		Lines.define(f, Line.CLUTCH,
			"Water at the bottom. Textbook.",
			"Long drop. The bucket handled it.");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s. Practice pays off.",
			"Getting quicker at %1$s. Level %2$s.");
		Lines.define(f, Line.LOOKING_FURTHER,
			"No room for the %1$s in camp. We'll widen the boundary.",
			"Camp's too tight for the %1$s. Looking further out.");
	}

	private static void flint() {
		FriendId f = FriendId.FLINT;
		Lines.define(f, Line.TRIP_START,
			"Going to %1$s. Above ground, for once.",
			"Off to %1$s. If I'm not back, it was a creeper.");
		Lines.define(f, Line.TRIP_FIND,
			"Mild excitement: %1$s.",
			"Noted: %1$s. You're welcome.");
		Lines.define(f, Line.TRIP_BACK,
			"Back. Still in one piece. I %1$s.",
			"Made it home. I %1$s.");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"Nope. Turning back. Caution is a skill.",
			"That's enough adventure. Heading home.");
		Lines.define(f, Line.TRADED,
			"Got %1$s. The villager didn't even haggle.",
			"Traded for %1$s. Nobody exploded.");
		Lines.define(f, Line.SHELTER,
			"Too far from camp in the dark. I know what to do: dig in.",
			"Night out here? No thanks. Walling myself in.");
		Lines.define(f, Line.SHELTER_MORNING,
			"Morning. Unbricking myself. Home.",
			"Sun's up. Taking my hidey-hole apart.");
		Lines.define(f, Line.CORNERED,
			"Too many. Going up. Zombies can't climb.",
			"Cornered. Pillar time.");
		Lines.define(f, Line.CLUTCH,
			"Water bucket. Never leave the mine without one.",
			"Long fall, wet landing. I'll take it.");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s. Try not to look so surprised.",
			"Level %2$s at %1$s now. Quietly pleased.");
		Lines.define(f, Line.LOOKING_FURTHER,
			"No room for the %1$s here. Moving the edge of camp out a bit.",
			"Camp's full. The %1$s goes a little further out.");
	}

	private static void scout() {
		FriendId f = FriendId.SCOUT;
		Lines.define(f, Line.TRIP_START,
			"Adventure! I'm off to explore %1$s!",
			"Heading out to %1$s. Back before dusk with news!");
		Lines.define(f, Line.TRIP_FIND,
			"New on the map: %1$s!",
			"Ooh, exciting find: %1$s!");
		Lines.define(f, Line.TRIP_BACK,
			"I'm back! On the trip I %1$s!",
			"Home again! Best trip yet. I %1$s!");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"I'd better turn back. The map will wait!",
			"Heading home early. More exploring tomorrow!");
		Lines.define(f, Line.TRADED,
			"Ooh, %1$s! Great trade!",
			"Traded for %1$s. Villagers are fun!");
		Lines.define(f, Line.SHELTER,
			"Too far to get home tonight! Camping out, explorer style!",
			"Night's caught me out here. Time for a cosy hideout!");
		Lines.define(f, Line.SHELTER_MORNING,
			"Morning! Packing up camp and racing home!",
			"Sunrise! Taking down my hideout. What a night!");
		Lines.define(f, Line.CORNERED,
			"They've got me boxed in! Going up!",
			"Too many! Up and out of reach!");
		Lines.define(f, Line.CLUTCH,
			"Woohoo! Water bucket landing!",
			"That was a big drop! Splash!");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s! I'm getting good at this!",
			"Yes! Level %2$s at %1$s now!");
		Lines.define(f, Line.LOOKING_FURTHER,
			"No room for the %1$s in camp? I know a spot a bit further out!",
			"The %1$s needs space. Let's stretch the camp a little!");
	}

	private static void spark() {
		FriendId f = FriendId.SPARK;
		Lines.define(f, Line.TRIP_START,
			"Field trip to %1$s! Back soon!",
			"Off to %1$s! I'll take notes!");
		Lines.define(f, Line.TRIP_FIND,
			"Ooh, look: %1$s!",
			"Brilliant find: %1$s!");
		Lines.define(f, Line.TRIP_BACK,
			"Back! Data collected! I %1$s!",
			"Home again! The trip was a success: I %1$s!");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"Aborting the trip! Back to base!",
			"Turning round! Home, quick quick!");
		Lines.define(f, Line.TRADED,
			"Traded for %1$s! What a bargain!",
			"Got %1$s! Brilliant!");
		Lines.define(f, Line.SHELTER,
			"Too far from camp! Emergency shelter, engage!",
			"It's dark! Building a tiny bunker! So cosy!");
		Lines.define(f, Line.SHELTER_MORNING,
			"Morning! Dismantling the bunker! Home!",
			"Sunrise! Packing up my little fortress!");
		Lines.define(f, Line.CORNERED,
			"Too many! Vertical escape!",
			"Surrounded! Going up, up, up!");
		Lines.define(f, Line.CLUTCH,
			"Water bucket landing! Physics!",
			"Splash! I worked that out perfectly! Mostly!");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s! Upgrade complete!",
			"I've levelled up at %1$s: level %2$s! Brilliant!");
		Lines.define(f, Line.LOOKING_FURTHER,
			"No room for the %1$s! Expanding the camp! Exciting!",
			"The %1$s won't fit! Let's push the camp out a bit!");
	}

	private static void aegis() {
		FriendId f = FriendId.AEGIS;
		Lines.define(f, Line.TRIP_START,
			"Heading to %1$s. I'll be careful.",
			"Setting out for %1$s. Back before dark.");
		Lines.define(f, Line.TRIP_FIND,
			"Report: %1$s.",
			"Noted: %1$s.");
		Lines.define(f, Line.TRIP_BACK,
			"Back at camp. I %1$s.",
			"Returned safely. I %1$s.");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"Turning back. No sense in taking risks.",
			"Returning to camp.");
		Lines.define(f, Line.TRADED,
			"Traded for %1$s.",
			"Fair exchange: %1$s.");
		Lines.define(f, Line.SHELTER,
			"Too far from camp. Fortifying for the night.",
			"Night has caught me out. I'll hold here.");
		Lines.define(f, Line.SHELTER_MORNING,
			"Dawn. Breaking camp and returning.",
			"Morning. Taking down the shelter.");
		Lines.define(f, Line.CORNERED,
			"Too many. Taking the high ground.",
			"Surrounded. Going up.");
		Lines.define(f, Line.CLUTCH,
			"Water broke the fall.",
			"Landed safely. Water bucket.");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s. I'll keep training.",
			"My %1$s has improved. Level %2$s.");
		Lines.define(f, Line.LOOKING_FURTHER,
			"No room for the %1$s inside our bounds. We'll extend the perimeter.",
			"The %1$s needs space. Widening the camp.");
	}

	private static void sage() {
		FriendId f = FriendId.SAGE;
		Lines.define(f, Line.TRIP_START,
			"A trip to %1$s seems wise. Back before dusk.",
			"I'll go to %1$s. Planned, timed and sensible.");
		Lines.define(f, Line.TRIP_FIND,
			"An observation: %1$s.",
			"Worth remembering: %1$s.");
		Lines.define(f, Line.TRIP_BACK,
			"Back at camp. As planned, I %1$s.",
			"Home again. On the trip I %1$s.");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"The sensible thing now is to turn back.",
			"Pressing on would be unwise. Returning.");
		Lines.define(f, Line.TRADED,
			"A good exchange: %1$s.",
			"Traded for %1$s. Value for value.");
		Lines.define(f, Line.SHELTER,
			"Too far to return safely. A shelter is the wise choice.",
			"Night has come early for me. I'll wall myself in.");
		Lines.define(f, Line.SHELTER_MORNING,
			"Morning. I'll leave no trace and return.",
			"Daylight. Dismantling the shelter, then home.");
		Lines.define(f, Line.CORNERED,
			"Outnumbered. The answer is height.",
			"Too many to face. Climbing out of reach.");
		Lines.define(f, Line.CLUTCH,
			"A long fall, and water at the end. Planning pays.",
			"Water, then ground. As intended.");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s. Experience is the best teacher.",
			"I've grown wiser at %1$s: level %2$s.");
		Lines.define(f, Line.LOOKING_FURTHER,
			"There is no room for the %1$s. The camp must grow a little.",
			"The %1$s won't fit. A slightly larger camp is the logical step.");
	}

	private static void terra() {
		FriendId f = FriendId.TERRA;
		Lines.define(f, Line.TRIP_START,
			"Off to %1$s. I'll admire the view on the way.",
			"Trip to %1$s! Back before the sun sets.");
		Lines.define(f, Line.TRIP_FIND,
			"Oh, how lovely: %1$s.",
			"Look what I found: %1$s.");
		Lines.define(f, Line.TRIP_BACK,
			"Home again! I %1$s.",
			"Back at camp. Such a pretty trip. I %1$s.");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"Time to head home. It'll be getting dark soon.",
			"I'll turn back here. Home, then.");
		Lines.define(f, Line.TRADED,
			"Traded for %1$s. Lovely.",
			"Got %1$s. Very nice indeed.");
		Lines.define(f, Line.SHELTER,
			"Too far to get home. A neat little shelter for tonight.",
			"Dark already! I'll tuck myself in until morning.");
		Lines.define(f, Line.SHELTER_MORNING,
			"Morning! Tidying away my shelter, not a block out of place.",
			"Sunrise. Putting the ground back just so, then home.");
		Lines.define(f, Line.CORNERED,
			"Too many of them! Up I go!",
			"I'm trapped! Climbing out of reach!");
		Lines.define(f, Line.CLUTCH,
			"Splash! A soft landing.",
			"Water bucket! That was close.");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s! My work is looking lovely.",
			"I'm getting better at %1$s: level %2$s!");
		Lines.define(f, Line.LOOKING_FURTHER,
			"No room for the %1$s. I'll find a spot a little further out.",
			"The %1$s needs space. Let's let the camp grow a little.");
	}

	private static void rowan() {
		FriendId f = FriendId.ROWAN;
		Lines.define(f, Line.TRIP_START,
			"Off to %1$s. I'll bring back plenty.",
			"Heading for %1$s. Back before dark.");
		Lines.define(f, Line.TRIP_FIND,
			"Good find: %1$s.",
			"Worth sharing: %1$s.");
		Lines.define(f, Line.TRIP_BACK,
			"Back with the goods! I %1$s.",
			"Home again. I %1$s. Help yourselves.");
		Lines.define(f, Line.TRIP_TURN_BACK,
			"That'll do for today. Heading home.",
			"I'll turn back. No sense pushing my luck.");
		Lines.define(f, Line.TRADED,
			"Traded for %1$s. That's one for the chest.",
			"Got %1$s. Fair swap.");
		Lines.define(f, Line.SHELTER,
			"Too far from camp tonight. I'll make do out here.",
			"Night's caught me. I'll dig in and wait for dawn.");
		Lines.define(f, Line.SHELTER_MORNING,
			"Morning! Picking up my blocks and heading home.",
			"Up and about. Shelter's coming down, then home.");
		Lines.define(f, Line.CORNERED,
			"Too many! Getting up out of reach!",
			"They've got me cornered. Up I go!");
		Lines.define(f, Line.CLUTCH,
			"Long way down! Good thing I carry water.",
			"Splash! Always pack a bucket.");
		Lines.define(f, Line.LEVEL_UP,
			"Level %2$s at %1$s. Happy to keep learning.",
			"Getting better at %1$s: level %2$s now.");
		Lines.define(f, Line.LOOKING_FURTHER,
			"No room for the %1$s. We'll spread the camp out a bit.",
			"The %1$s won't fit. Plenty of space a little further out.");
	}
}

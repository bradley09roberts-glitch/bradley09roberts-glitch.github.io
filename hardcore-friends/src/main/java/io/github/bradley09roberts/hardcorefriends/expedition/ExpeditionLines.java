package io.github.bradley09roberts.hardcorefriends.expedition;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * The friends' words on expeditions: coming through a portal, heading home through one alone, the camp's portal lit,
 * bartering with a piglin, a blaze brought down, a fortress spotted, an eye of ender thrown and the stronghold found,
 * the End portal opened, climbing to a caged crystal, a crystal gone, the dragon landing, and victory. Generic wording
 * plus each friend's own, in their voice. The compass direction arrives as a word ("north-east"); the stronghold's
 * place as "x 1204, z -388", so it comes last, after a colon.
 */
public final class ExpeditionLines {
	private ExpeditionLines() {
	}

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
		Lines.defineGeneric(Line.PORTAL_THROUGH, "Through the portal, right behind you.", "We made it across. Stay close.");
		Lines.defineGeneric(Line.PORTAL_HOME, "Nobody's coming back, so I'm heading home through the portal.",
			"I've waited long enough. Home through the portal.");
		Lines.defineGeneric(Line.PORTAL_LIT, "The Nether portal at camp is lit!", "The portal's lit. Take care over there.");
		Lines.defineGeneric(Line.BARTER, "Here's some gold. What will you give me for it?", "A gold ingot for you. Fair trade?");
		Lines.defineGeneric(Line.BLAZE_ROD, "Blaze down! That should be a rod for the plan.", "Got the blaze. Look for its rod.");
		Lines.defineGeneric(Line.FORTRESS_SEEN, "A Nether fortress! Blazes live in there.", "There's a fortress ahead. Careful now.");
		Lines.defineGeneric(Line.EYE_THROWN, "The eye flew %1$s.", "It went %1$s. On to the next throw.");
		Lines.defineGeneric(Line.STRONGHOLD_FOUND, "I found the stronghold: %1$s.", "Home again. The stronghold is here: %1$s.");
		Lines.defineGeneric(Line.PORTAL_FILLED, "The last eye is in. The End portal is open!", "All twelve eyes set. The portal's open.");
		Lines.defineGeneric(Line.CAGE_CLIMB, "I'll pillar up and open that crystal's cage.", "That crystal's caged. I'm climbing up.");
		Lines.defineGeneric(Line.CRYSTAL_DOWN, "Crystal destroyed!", "One crystal fewer!");
		Lines.defineGeneric(Line.DRAGON_PERCHED, "The dragon's landed on the portal! Strike now!", "It's sitting on the portal! Now!");
		Lines.defineGeneric(Line.VICTORY, "The dragon is defeated! We did it!", "We beat the dragon, together!");
	}

	private static void fern() {
		FriendId f = FriendId.FERN;
		Lines.define(f, Line.PORTAL_THROUGH,
			"Right behind you, dear. Mind your step on this side.",
			"Through we come, together. Stay close, everyone.");
		Lines.define(f, Line.PORTAL_HOME,
			"Nobody's come back for me, so I'll head home through the portal.",
			"I've waited long enough. Home through the portal, carefully.");
		Lines.define(f, Line.PORTAL_LIT,
			"The portal's glowing purple now. Do take care on the other side.",
			"There, it's lit. Promise me you'll all come back safe.");
		Lines.define(f, Line.BARTER,
			"Here you are, a bit of gold for you. Something nice in return, please?",
			"A shiny ingot for you, little piglin. Let's be friends.");
		Lines.define(f, Line.BLAZE_ROD,
			"That blaze is out. I hope it left a rod for the plan.",
			"Got it! A blaze rod for the camp, with any luck.");
		Lines.define(f, Line.FORTRESS_SEEN,
			"Look, a fortress. Blazes live in there, so let's be careful.",
			"A great dark fortress ahead. Stay together, everyone.");
		Lines.define(f, Line.EYE_THROWN,
			"The eye flew off to the %1$s. Off we go after it.",
			"There it goes, to the %1$s. How clever these eyes are.");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"I followed the eyes and found the stronghold: %1$s.",
			"Home safe. The stronghold lies here: %1$s.");
		Lines.define(f, Line.PORTAL_FILLED,
			"The last eye is in, and the portal's open. Hold hands, everyone.",
			"There, all twelve. Look at the stars in it.");
		Lines.define(f, Line.CAGE_CLIMB,
			"I'll climb up and open that cage. Do keep an eye on me.",
			"Up I go to that cage. Steady hands, steady feet.");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"That crystal's gone. One less to heal the dragon.",
			"Got the crystal! Is everyone all right?");
		Lines.define(f, Line.DRAGON_PERCHED,
			"The dragon's landed! Quickly now, while it rests!",
			"It's on the portal! Swords out, and mind the breath!");
		Lines.define(f, Line.VICTORY,
			"We did it! The dragon is gone. I'm so proud of us all.",
			"It's over, and we're all still here. What a day.");
	}

	private static void oak() {
		FriendId f = FriendId.OAK;
		Lines.define(f, Line.PORTAL_THROUGH,
			"Through. Check your footing before you move.",
			"Made it across. Let's get our bearings.");
		Lines.define(f, Line.PORTAL_HOME,
			"No one to follow here. Back through the portal to camp.",
			"Waited long enough. Heading home through the portal.");
		Lines.define(f, Line.PORTAL_LIT,
			"Frame's solid and the portal's lit. Good work, team.",
			"Lit. Ten obsidian, one flint and steel, one portal.");
		Lines.define(f, Line.BARTER,
			"One gold ingot. Let's see what it's worth to you.",
			"Fair trade: gold for whatever you've got.");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze down. That should be a rod for the plan.",
			"One less blaze. Check the ground for a rod.");
		Lines.define(f, Line.FORTRESS_SEEN,
			"Fortress up ahead. Solid build, whoever made it.",
			"That's a Nether fortress. Blazes inside, most likely.");
		Lines.define(f, Line.EYE_THROWN,
			"Eye went %1$s. Marking the line.",
			"Bearing noted: %1$s. On to the next throw.");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"Lines crossed and checked. The stronghold: %1$s.",
			"Back at camp. Stronghold located: %1$s.");
		Lines.define(f, Line.PORTAL_FILLED,
			"All twelve frames filled. Portal's open.",
			"Last eye set. That's a working portal.");
		Lines.define(f, Line.CAGE_CLIMB,
			"I'll pillar up and take that cage apart.",
			"Cage needs opening. I'll build my way up.");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Crystal down. Next one.",
			"That crystal's finished. Keep going.");
		Lines.define(f, Line.DRAGON_PERCHED,
			"It's landed on the portal! Hit it now!",
			"Dragon's down on the portal. Get in close!");
		Lines.define(f, Line.VICTORY,
			"Dragon's done. Best job we've ever finished.",
			"That's the plan complete. Well built, everyone.");
	}

	private static void flint() {
		FriendId f = FriendId.FLINT;
		Lines.define(f, Line.PORTAL_THROUGH,
			"We're through. Nobody touch the lava.",
			"On the other side. Still in one piece, so far.");
		Lines.define(f, Line.PORTAL_HOME,
			"No one's here and I'm not staying. Home through the portal.",
			"Waited. Nobody came. Portal home it is.");
		Lines.define(f, Line.PORTAL_LIT,
			"It's lit. Purple and humming. Lovely.",
			"Portal's open. I'll stand well back, thanks.");
		Lines.define(f, Line.BARTER,
			"Here's gold. Please don't make it weird.",
			"One ingot. Throw back something useful, ideally.");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze is out. Fire's someone else's problem now.",
			"Got the blaze. I'd like my eyebrows back.");
		Lines.define(f, Line.FORTRESS_SEEN,
			"Fortress. Full of things that want us gone.",
			"A fortress. Wonderful. More blazes.");
		Lines.define(f, Line.EYE_THROWN,
			"Eye went %1$s. Didn't even blink.",
			"The eye says %1$s. I'll take its word for it.");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"Found the stronghold. Somewhere under this lot: %1$s.",
			"I'm back. The stronghold is under here: %1$s.");
		Lines.define(f, Line.PORTAL_FILLED,
			"Twelve eyes. Portal's open. No going back now.",
			"That's the portal open. Nobody fall in by accident.");
		Lines.define(f, Line.CAGE_CLIMB,
			"I'll climb to the cage. Don't watch, it makes me nervous.",
			"Up the pillar I go. Carefully. Very carefully.");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Crystal's gone. Big bang, nobody hurt.",
			"One less crystal. My ears are ringing.");
		Lines.define(f, Line.DRAGON_PERCHED,
			"It's landed! Hit it and step back from the breath!",
			"Dragon's on the portal. Now, while it's sitting!");
		Lines.define(f, Line.VICTORY,
			"The dragon's done for and we're not. I'll take that.",
			"We won. I'd like a very long sit down now.");
	}

	private static void scout() {
		FriendId f = FriendId.SCOUT;
		Lines.define(f, Line.PORTAL_THROUGH,
			"Whoa, we're through! Look at this place!",
			"New world, same team! Lead on!");
		Lines.define(f, Line.PORTAL_HOME,
			"Nobody's coming back, so I'll find my own way home. Portal ho!",
			"Time to head home. The portal's this way!");
		Lines.define(f, Line.PORTAL_LIT,
			"It's lit! Where shall we explore first?",
			"The portal's open! The Nether awaits!");
		Lines.define(f, Line.BARTER,
			"Gold for you! Show me what treasures you've got!",
			"Here, catch! What will you trade me?");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze down! That's a rod for the plan, I bet!",
			"Got the blaze! Let's look for its rod!");
		Lines.define(f, Line.FORTRESS_SEEN,
			"A fortress! I've always wanted to see one!",
			"Look, a Nether fortress! Let's remember where it is!");
		Lines.define(f, Line.EYE_THROWN,
			"It flew %1$s! Let's follow it!",
			"The eye says %1$s! Off we go!");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"I'm back! I found the stronghold, right here: %1$s!",
			"Best trip ever! The stronghold is waiting: %1$s!");
		Lines.define(f, Line.PORTAL_FILLED,
			"All twelve eyes in! The portal's open, let's go!",
			"Look at the stars in it! The End is next!");
		Lines.define(f, Line.CAGE_CLIMB,
			"I'll climb up to that cage! Cover me!",
			"A cage up top? I'm on my way up!");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Bullseye! That crystal's gone!",
			"Crystal down! What a shot!");
		Lines.define(f, Line.DRAGON_PERCHED,
			"The dragon's landed! Everyone, now!",
			"It's sitting on the portal! Charge!");
		Lines.define(f, Line.VICTORY,
			"We beat the dragon! Best adventure ever!",
			"The dragon's gone! What's next to explore?");
	}

	private static void spark() {
		FriendId f = FriendId.SPARK;
		Lines.define(f, Line.PORTAL_THROUGH,
			"We're through! Everyone's in one piece, as predicted!",
			"Across already! Fascinating, absolutely fascinating!");
		Lines.define(f, Line.PORTAL_HOME,
			"No one here! I'll take the portal home. Simple!",
			"Waited ages. Portal home, then!");
		Lines.define(f, Line.PORTAL_LIT,
			"It's lit! Look at those sparkles!",
			"Portal's active! Obsidian and fire, a perfect pairing!");
		Lines.define(f, Line.BARTER,
			"Gold in, mystery out! Let's run the experiment!",
			"Trading with a piglin! Let's see the results!");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze down! Rods mean powder mean brewing!",
			"Got it! A rod for the brewing stand, I hope!");
		Lines.define(f, Line.FORTRESS_SEEN,
			"A fortress! Such architecture! Such danger!",
			"Nether fortress spotted! Blazes, here we come!");
		Lines.define(f, Line.EYE_THROWN,
			"The eye went %1$s! The maths begins!",
			"Bearing recorded: %1$s! Brilliant!");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"Two lines, one crossing, one stronghold: %1$s!",
			"Triangulated! The stronghold is here: %1$s!");
		Lines.define(f, Line.PORTAL_FILLED,
			"Twelve eyes, one portal, no problems! It's open!",
			"The portal's open! Look at it shimmer!");
		Lines.define(f, Line.CAGE_CLIMB,
			"I'll pillar up and take that cage apart!",
			"Cage removal! I'm climbing up!");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Crystal destroyed! What an explosion!",
			"Boom! That crystal's gone!");
		Lines.define(f, Line.DRAGON_PERCHED,
			"The dragon's landed! Strike now!",
			"It's on the portal! Everyone, quick!");
		Lines.define(f, Line.VICTORY,
			"We did it! The dragon is defeated! Brilliant teamwork!",
			"Victory! I'll remember this day forever!");
	}

	private static void aegis() {
		FriendId f = FriendId.AEGIS;
		Lines.define(f, Line.PORTAL_THROUGH,
			"Through. I'm with you. Stay close.",
			"We crossed safely. I'll watch your back.");
		Lines.define(f, Line.PORTAL_HOME,
			"There's nobody left to guard here. I'll return home through the portal.",
			"No one to protect here now. Heading home.");
		Lines.define(f, Line.PORTAL_LIT,
			"The portal is lit. I'll go first when it's time.",
			"It's open. Nobody goes through alone.");
		Lines.define(f, Line.BARTER,
			"Gold, as offered. Keep the peace.",
			"A trade, nothing more. Easy now.");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze down. Stay out of its fire next time.",
			"That blaze won't trouble us again.");
		Lines.define(f, Line.FORTRESS_SEEN,
			"A fortress. Stay behind me.",
			"Fortress ahead. Keep your guard up.");
		Lines.define(f, Line.EYE_THROWN,
			"The eye flew %1$s. I'll follow.",
			"It went %1$s, then. Stay alert on the way.");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"I've returned. The stronghold lies here: %1$s.",
			"Found it and came back safe. Stronghold: %1$s.");
		Lines.define(f, Line.PORTAL_FILLED,
			"The portal is open. We go together.",
			"Twelve eyes set. Ready yourselves.");
		Lines.define(f, Line.CAGE_CLIMB,
			"I'll climb and break that cage. Keep the dragon busy.",
			"Climbing to the cage. Cover me.");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Crystal destroyed. Well done.",
			"One crystal fewer. Keep going.");
		Lines.define(f, Line.DRAGON_PERCHED,
			"It's landed. Strike now, together!",
			"The dragon sits. Swords up!");
		Lines.define(f, Line.VICTORY,
			"The dragon is defeated. Everyone is safe.",
			"It's over. I'm proud to have stood with you.");
	}

	private static void sage() {
		FriendId f = FriendId.SAGE;
		Lines.define(f, Line.PORTAL_THROUGH,
			"Through. Let's note where this portal stands.",
			"We've crossed. Remember this place for the way back.");
		Lines.define(f, Line.PORTAL_HOME,
			"Nobody's coming back for us. The sensible thing is home.",
			"I've waited long enough. Back through the portal.");
		Lines.define(f, Line.PORTAL_LIT,
			"The portal is lit. The plan moves on to the Nether.",
			"Lit, as planned. Now for blaze rods and pearls.");
		Lines.define(f, Line.BARTER,
			"A gold ingot for you. Let's see what you offer.",
			"Bartering is wiser than fighting. Here, take this.");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze down. That's one more rod towards the eyes.",
			"One blaze fewer. Count the rods, everyone.");
		Lines.define(f, Line.FORTRESS_SEEN,
			"A fortress. Blazes spawn inside. We should be careful.",
			"Note this fortress. We may need it again.");
		Lines.define(f, Line.EYE_THROWN,
			"The eye flew %1$s. One more throw will fix the spot.",
			"Bearing taken: %1$s. Now we cross it with another.");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"The lines crossed where I expected. Stronghold: %1$s.",
			"My reckoning is done. The stronghold lies here: %1$s.");
		Lines.define(f, Line.PORTAL_FILLED,
			"The portal is open. The last step of the plan lies beyond.",
			"All twelve eyes set. The End awaits us.");
		Lines.define(f, Line.CAGE_CLIMB,
			"That crystal is caged. I'll climb up and open it.",
			"I'll pillar up to the cage. Archers, be ready.");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Crystal destroyed. The dragon can't heal from that one.",
			"One crystal fewer. We're winning.");
		Lines.define(f, Line.DRAGON_PERCHED,
			"It has landed on the portal. Strike now!",
			"The dragon rests. This is our moment!");
		Lines.define(f, Line.VICTORY,
			"The dragon is defeated. The plan is complete.",
			"We beat the game, together. Remarkable.");
	}

	private static void terra() {
		FriendId f = FriendId.TERRA;
		Lines.define(f, Line.PORTAL_THROUGH,
			"Through we go! Oh, the colours here.",
			"Made it! What a strange landscape.");
		Lines.define(f, Line.PORTAL_HOME,
			"No one to follow here. Home through the portal, then.",
			"Waited long enough. Back to the camp's gardens.");
		Lines.define(f, Line.PORTAL_LIT,
			"The portal's lit. It suits the camp, I think.",
			"A purple glow at camp! Rather pretty, really.");
		Lines.define(f, Line.BARTER,
			"A gold ingot for you. Something nice in return?",
			"Here's gold. Let's swap, neatly now.");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze down. Messy fire, but a useful rod.",
			"Got the blaze! A rod for the plan, I hope.");
		Lines.define(f, Line.FORTRESS_SEEN,
			"A fortress! Grand, if a bit gloomy.",
			"Look at that fortress. Dark bricks everywhere.");
		Lines.define(f, Line.EYE_THROWN,
			"The eye went %1$s. What a lovely trail.",
			"It flew %1$s. Pretty sparkles, too.");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"I'm home. The stronghold is right under here: %1$s.",
			"Found it and marked it neatly. Stronghold: %1$s.");
		Lines.define(f, Line.PORTAL_FILLED,
			"All twelve eyes in place. The portal's open.",
			"There, a full set. Look at it glimmer.");
		Lines.define(f, Line.CAGE_CLIMB,
			"I'll build up to that cage and clear it away.",
			"A cage in the way? I'll climb up and tidy it.");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Crystal gone. Bit of a mess, but worth it.",
			"That crystal's cleared away!");
		Lines.define(f, Line.DRAGON_PERCHED,
			"It's landed! Now, everyone!",
			"The dragon's on the portal! Quickly!");
		Lines.define(f, Line.VICTORY,
			"The dragon's gone! The sky looks cleaner already.",
			"We did it! What a beautiful sight.");
	}

	private static void rowan() {
		FriendId f = FriendId.ROWAN;
		Lines.define(f, Line.PORTAL_THROUGH,
			"Through! Grab anything useful you see.",
			"We're across. Plenty to gather here, I bet.");
		Lines.define(f, Line.PORTAL_HOME,
			"No one's back for me, so home I go through the portal.",
			"Waited long enough. Home, with whatever I've found.");
		Lines.define(f, Line.PORTAL_LIT,
			"Portal's lit! Bring back plenty for everyone.",
			"It's open! Think of all we'll find over there.");
		Lines.define(f, Line.BARTER,
			"Here, have some gold. Share something back?",
			"A trade! Gold for you, goodies for us.");
		Lines.define(f, Line.BLAZE_ROD,
			"Blaze down! Let's grab that rod.",
			"Got it! Rods for the camp, with luck.");
		Lines.define(f, Line.FORTRESS_SEEN,
			"A fortress! Nether wart grows in there, you know.",
			"Fortress spotted. Good pickings inside, if we're careful.");
		Lines.define(f, Line.EYE_THROWN,
			"The eye flew %1$s. I'll fetch it back if it lands.",
			"It went %1$s! I'll pick the eye up again. Waste not.");
		Lines.define(f, Line.STRONGHOLD_FOUND,
			"Home with good news. The stronghold is here: %1$s.",
			"Found it! Here's where to dig: %1$s.");
		Lines.define(f, Line.PORTAL_FILLED,
			"All the eyes are in. The portal's open!",
			"Every frame filled. Off to the End we go.");
		Lines.define(f, Line.CAGE_CLIMB,
			"I've got blocks to spare. I'll climb up to that cage.",
			"I'll pillar up and open the cage.");
		Lines.define(f, Line.CRYSTAL_DOWN,
			"Crystal's gone! Good shot!",
			"That crystal won't heal the dragon now!");
		Lines.define(f, Line.DRAGON_PERCHED,
			"The dragon's landed! Get in there!",
			"It's on the portal! Now's our chance!");
		Lines.define(f, Line.VICTORY,
			"The dragon's beaten! Feast at camp tonight!",
			"We won! Everyone deserves a treat for this.");
	}
}

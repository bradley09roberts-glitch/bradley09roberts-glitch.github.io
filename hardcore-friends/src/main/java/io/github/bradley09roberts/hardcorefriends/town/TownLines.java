package io.github.bradley09roberts.hardcorefriends.town;

import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;

/**
 * What the friends say about the players they share the camp with: warm and cool hellos by bond, mourning a fallen
 * player, reading out a note, thanking a delivery, a mailbox delivery made, politely refusing an order or refusing to
 * follow, keeping a fallen player's things safe, and the siege night's warning and its dawn. Each line is written once
 * plainly and twice in each of the nine voices (newcomers speak in their trade's voice). The usual rules for wording
 * apply (see {@link Lines}).
 */
final class TownLines {
	private TownLines() {
	}

	static void register() {
		generic();
		voice(FriendId.FERN,
			new String[] {"Oh, %1$s! Come here, dear. Have you eaten?", "%1$s! You always brighten up the farm."},
			new String[] {"Hello, %1$s. Mind the crops, please.", "Oh. %1$s. I'm rather busy with the field."},
			new String[] {"Oh, %1$s... I'll plant something lovely for you.", "We've lost %1$s. Please, everyone, look after each other."},
			new String[] {"Oh, a note from %1$s! Let me read it:", "%1$s left us a note, dear. It says:"},
			new String[] {"Thank you, %1$s! That'll keep us all fed and happy.", "Oh, %1$s, you're a dear. Into the chest it goes."},
			new String[] {"I've left a little parcel in %1$s's mailbox. Eat well, dear.", "%1$s's mailbox has something nice in it now."},
			new String[] {"Sorry, dear. I only take orders from our own camp folk.", "I'd love to help, %1$s, but ask the camp's leader first."},
			new String[] {"I'd rather stay with the crops, %1$s. Thank you all the same.", "No, %1$s. I don't feel safe following you."},
			new String[] {"Oh, poor %1$s. I'll keep your things safe in the chest.", "Let me gather %1$s's things before they're lost."},
			new String[] {"Something's stirring tonight, dears. Stay close to the fire.", "I've a bad feeling about tonight. Everyone keep near camp."},
			new String[] {"Morning, everyone! We all made it. I'll make breakfast.", "We're all still here! Oh, thank goodness."});
		voice(FriendId.OAK,
			new String[] {"%1$s! Good to see you. The work goes better with you about.", "Ah, %1$s. Always glad of a steady pair of hands."},
			new String[] {"%1$s. I'm busy. Mind the scaffolding.", "Hm. %1$s. What is it?"},
			new String[] {"%1$s is gone. I'll build something to remember them by.", "We've lost %1$s. Hold together, everyone."},
			new String[] {"Note from %1$s on the chest. Here's what it says:", "%1$s left us a note. Reads:"},
			new String[] {"Thanks, %1$s. That goes straight into the build.", "Just what we needed, %1$s. Good work."},
			new String[] {"Parcel delivered to %1$s's mailbox. Neat and tidy.", "Left a delivery in %1$s's mailbox. Job done."},
			new String[] {"Sorry, %1$s. I take orders from the camp's own people.", "Not my call, %1$s. Ask whoever runs the camp."},
			new String[] {"No, %1$s. I'd rather keep to my work here.", "I'll not follow you, %1$s. Not yet."},
			new String[] {"%1$s's things are going in the chest. Safe and sound.", "I'll gather %1$s's belongings. Nothing gets lost."},
			new String[] {"Something's stirring tonight. Stay close and keep the doors shut.", "I don't like it out there tonight. Everyone near the walls."},
			new String[] {"Walls held, and so did we. Good night's work, everyone.", "Morning. All accounted for. That's how it's done."});
		voice(FriendId.FLINT,
			new String[] {"%1$s. Still alive. Glad to see it.", "Oh, it's %1$s. My favourite person. Don't tell the others."},
			new String[] {"%1$s. Hm. Keep your distance, please.", "Oh. %1$s. Wonderful."},
			new String[] {"%1$s. I don't say this often: I'll miss you.", "We lost %1$s. Keep your torches lit, everyone."},
			new String[] {"There's a note from %1$s. I'll read it. Slowly:", "%1$s left a note. Let's see:"},
			new String[] {"Thanks, %1$s. Useful. Very useful, actually.", "You brought supplies, %1$s? I'm almost cheerful."},
			new String[] {"Delivered to %1$s's mailbox. Didn't even fall in a hole.", "%1$s's mailbox is a bit fuller now. You're welcome."},
			new String[] {"No offence, %1$s, but I only take orders from our lot.", "Ask the camp's leader first, %1$s. Rules are rules."},
			new String[] {"Follow you, %1$s? I'd sooner dig straight down.", "No thanks, %1$s. I'm staying put."},
			new String[] {"I'll put %1$s's things in the chest. Before something burns.", "Collecting %1$s's belongings. Carefully."},
			new String[] {"Something's stirring tonight. I'd stay close. I'm staying close.", "Bad feeling about tonight. More torches, everyone."},
			new String[] {"We all survived. I'm as surprised as anyone.", "Morning. Nobody fell. I'll take that."});
		voice(FriendId.SCOUT,
			new String[] {"%1$s! There you are! I've got so much to tell you!", "Hey, %1$s! Best person in the whole camp!"},
			new String[] {"Oh. Hi, %1$s.", "%1$s. I'm off exploring soon, so..."},
			new String[] {"No... %1$s. I'll miss our adventures.", "%1$s is gone. I'll name the next hill after them."},
			new String[] {"Ooh, a note from %1$s! Listen to this:", "%1$s left a note! It says:"},
			new String[] {"Thanks, %1$s! That's brilliant!", "Wow, %1$s! The camp will love this!"},
			new String[] {"Delivery done! There's a parcel in %1$s's mailbox!", "I ran all the way! %1$s's mailbox has a surprise in it!"},
			new String[] {"Sorry, %1$s! I only take orders from our camp!", "Ask the camp's leader first, %1$s! Then we'll talk!"},
			new String[] {"Nope, %1$s. I'll explore on my own, thanks.", "Not with you, %1$s. Sorry."},
			new String[] {"I'll grab %1$s's things before they vanish!", "Don't worry about %1$s's things! I'm saving them!"},
			new String[] {"Something's stirring tonight! Stay close, everyone!", "I saw tracks out there. Lots. Stay near camp tonight!"},
			new String[] {"We made it! Every single one of us!", "Morning! We're all alive! What a night!"});
		voice(FriendId.SPARK,
			new String[] {"%1$s! Hi! Hi! Come and see what I'm making!", "Oh, %1$s! You're just the person I wanted!"},
			new String[] {"Oh. %1$s. I'm, um, busy with wires.", "Hello, %1$s. Please don't touch anything."},
			new String[] {"%1$s... I'll build you a lamp that never goes out.", "Oh, %1$s. The camp won't be the same without you."},
			new String[] {"A note from %1$s! Ooh, let me read it!", "%1$s left a note! Here goes:"},
			new String[] {"Thanks, %1$s! So many things I could make!", "Brilliant, %1$s! The chest is happier already!"},
			new String[] {"Parcel delivered to %1$s's mailbox! Zoom!", "%1$s's mailbox has a surprise in it now! Well, a parcel!"},
			new String[] {"Sorry, %1$s! Camp folk only! It's a rule!", "Ask the camp's leader first, %1$s! Then yes!"},
			new String[] {"Erm, no, %1$s. I'll stay with my gadgets.", "No thank you, %1$s! Busy! Very busy!"},
			new String[] {"I'll keep %1$s's things safe! Into the chest!", "Saving %1$s's things! Nothing gets lost on my watch!"},
			new String[] {"Something's stirring tonight! Stay close! Very close!", "My instruments say trouble tonight! Well, my nose does!"},
			new String[] {"We did it! Everyone's alive! Brilliant!", "Morning! All present! Best night ever! Well, nearly!"});
		voice(FriendId.AEGIS,
			new String[] {"%1$s. Good. You're here. Stay close.", "Welcome back, %1$s. I'll keep you safe."},
			new String[] {"%1$s.", "%1$s. Keep your weapon sheathed."},
			new String[] {"%1$s has fallen. I should have been there.", "Rest, %1$s. Your fight is over."},
			new String[] {"A note from %1$s. Listen:", "%1$s left word for the camp:"},
			new String[] {"Thank you, %1$s. The camp is stronger for it.", "Well done, %1$s. That helps us hold."},
			new String[] {"Delivered to %1$s's mailbox. All safe.", "Parcel left for %1$s. Nothing touched on the way."},
			new String[] {"No, %1$s. I answer to the camp.", "Ask the camp's leader, %1$s. Not me."},
			new String[] {"I won't follow you, %1$s.", "No. I guard the camp, %1$s, not you."},
			new String[] {"I'll guard %1$s's things until they're in the chest.", "%1$s's belongings. I'll keep them safe."},
			new String[] {"Something's stirring tonight. Stay close. Weapons ready.", "They're coming tonight. Everyone near the fire."},
			new String[] {"We held. Every one of us. Well fought.", "Dawn. All alive. Rest now, you've earned it."});
		voice(FriendId.SAGE,
			new String[] {"Ah, %1$s. I always enjoy our talks.", "%1$s, my friend. Sit a while."},
			new String[] {"Hm. %1$s. I'm thinking.", "%1$s. Trust is earned slowly, you know."},
			new String[] {"%1$s... We'll remember you, and learn from this.", "We've lost %1$s. Let's honour them by taking care."},
			new String[] {"A note from %1$s. Let me read it aloud:", "%1$s has left us some words:"},
			new String[] {"Thank you, %1$s. Exactly what the plan needed.", "Well judged, %1$s. The camp is grateful."},
			new String[] {"I've left a parcel in %1$s's mailbox. A small kindness.", "%1$s's mailbox has a delivery waiting."},
			new String[] {"Forgive me, %1$s. I take direction from the camp's own.", "Speak to the camp's leader first, %1$s."},
			new String[] {"I think not, %1$s. Trust must be mended first.", "No, %1$s. I'll stay where I'm needed."},
			new String[] {"I'll see %1$s's things safely to the chest.", "Let's not lose %1$s's belongings too. I'll gather them."},
			new String[] {"Something's stirring tonight. Stay close, and stay together.", "The signs aren't good for tonight. Keep near the camp."},
			new String[] {"We all saw the dawn. Preparation pays.", "Everyone survived. Remember how we did it."});
		voice(FriendId.TERRA,
			new String[] {"%1$s! Come and see the flowers!", "Oh, %1$s, you've made my day lovelier."},
			new String[] {"Hello, %1$s. Please stay on the path.", "Oh. %1$s. Mind my flowerbeds."},
			new String[] {"Oh, %1$s... I'll plant a garden for you.", "We've lost %1$s. The camp feels so empty."},
			new String[] {"Oh, a note from %1$s! It says:", "%1$s left us a note. How sweet:"},
			new String[] {"Thank you, %1$s! Everything's so tidy now.", "How lovely, %1$s! Into the chest, neat and orderly."},
			new String[] {"I left a parcel in %1$s's mailbox. Wrapped with care!", "%1$s's mailbox has a delivery. All tidy!"},
			new String[] {"Sorry, %1$s, I only take orders from our camp.", "Please ask the camp's leader first, %1$s."},
			new String[] {"I'd rather not follow you, %1$s.", "No, %1$s. My garden needs me more."},
			new String[] {"I'll tidy %1$s's things into the chest. Nothing lost.", "Gathering %1$s's belongings, gently."},
			new String[] {"Something's stirring tonight. Stay close, everyone.", "The night feels wrong. Please stay near the lights."},
			new String[] {"We're all safe! Oh, I could plant a whole garden!", "Morning! Everyone made it. Isn't that wonderful?"});
		voice(FriendId.ROWAN,
			new String[] {"%1$s! Good to see you, friend! Need anything?", "There's %1$s! Come on, I've saved you something."},
			new String[] {"Oh. %1$s. Hello.", "%1$s. Don't expect me to share today."},
			new String[] {"%1$s... I'll keep a seat by the fire for you.", "We've lost %1$s. Share what you have, everyone."},
			new String[] {"A note from %1$s! Here's what it says:", "%1$s left us a note, friends:"},
			new String[] {"Cheers, %1$s! Sharing's what this camp's about.", "Thanks, %1$s! That'll go a long way."},
			new String[] {"Dropped a parcel in %1$s's mailbox. Share and share alike!", "%1$s's mailbox has a gift from the camp now."},
			new String[] {"Sorry, friend. I take orders from our own camp.", "Ask the camp's leader first, %1$s. No hard feelings."},
			new String[] {"I'll not follow you, %1$s. Not today.", "No, %1$s. I'd rather stay with the others."},
			new String[] {"I'll keep %1$s's things safe. Nobody's taking them.", "Gathering up %1$s's belongings for the chest."},
			new String[] {"Something's stirring tonight. Stay close, friends.", "I wouldn't wander tonight. Stay near the fire."},
			new String[] {"We all made it! Breakfast is on me!", "Morning, friends! Everyone's here. Let's share a meal."});
	}

	private static void generic() {
		Lines.defineGeneric(Line.GREET_WARM, "%1$s! Always good to see you.", "There you are, %1$s. I was hoping you'd come by.",
			"Hello, %1$s, my friend!");
		Lines.defineGeneric(Line.GREET_COOL, "Oh. Hello, %1$s.", "%1$s. What do you want?", "Hello, %1$s. I'm busy.");
		Lines.defineGeneric(Line.MOURN_PLAYER, "We've lost %1$s. I can't believe it.", "Rest well, %1$s. We'll remember you.",
			"Goodbye, %1$s. The camp won't forget you.");
		Lines.defineGeneric(Line.NOTE_FOUND, "There's a note here from %1$s. Listen:", "A note from %1$s! It says:",
			"%1$s left the camp a note:");
		Lines.defineGeneric(Line.THANKS_DELIVERY, "Thank you, %1$s! That's just what the camp needed.",
			"Brilliant, %1$s. Into the chest it goes.", "Thanks, %1$s. That helps everyone.");
		Lines.defineGeneric(Line.MAIL_DELIVERED, "I've left a parcel in %1$s's mailbox.",
			"Delivery done! Something's waiting in %1$s's mailbox.", "%1$s's mailbox has a little something in it now.");
		Lines.defineGeneric(Line.REFUSE_ORDER, "Sorry, %1$s. I only take orders from the camp's own people.",
			"I'd rather not, %1$s. Ask the camp's leader first.", "Not from you, %1$s, I'm afraid. Ask the camp to trust you.");
		Lines.defineGeneric(Line.REFUSE_FOLLOW, "I'd rather not follow you, %1$s. Not after everything.", "No, %1$s. I'm staying here.",
			"Sorry, %1$s. I don't feel safe with you.");
		Lines.defineGeneric(Line.KEEPING_ITEMS, "I'll keep %1$s's things safe in the chest.",
			"Gathering %1$s's belongings before they're lost.", "%1$s's things won't be lost. I'll put them away.");
		Lines.defineGeneric(Line.SIEGE_DUSK, "Something's stirring tonight. Stay close.",
			"I don't like the feel of tonight. Stay near the camp.", "Trouble's coming tonight. Keep together.");
		Lines.defineGeneric(Line.SIEGE_HELD, "We held! Everyone made it through the night.",
			"Dawn, and we're all still here. Well done, everyone.", "We stood together and we're all alive. That's the camp!");
	}

	/** One voice's wording for every town line, in the order of the parameters. */
	private static void voice(FriendId friend, String[] warm, String[] cool, String[] mourn, String[] note, String[] thanks,
			String[] mail, String[] refuseOrder, String[] refuseFollow, String[] keeping, String[] siegeDusk, String[] siegeHeld) {
		Lines.define(friend, Line.GREET_WARM, warm);
		Lines.define(friend, Line.GREET_COOL, cool);
		Lines.define(friend, Line.MOURN_PLAYER, mourn);
		Lines.define(friend, Line.NOTE_FOUND, note);
		Lines.define(friend, Line.THANKS_DELIVERY, thanks);
		Lines.define(friend, Line.MAIL_DELIVERED, mail);
		Lines.define(friend, Line.REFUSE_ORDER, refuseOrder);
		Lines.define(friend, Line.REFUSE_FOLLOW, refuseFollow);
		Lines.define(friend, Line.KEEPING_ITEMS, keeping);
		Lines.define(friend, Line.SIEGE_DUSK, siegeDusk);
		Lines.define(friend, Line.SIEGE_HELD, siegeHeld);
	}
}

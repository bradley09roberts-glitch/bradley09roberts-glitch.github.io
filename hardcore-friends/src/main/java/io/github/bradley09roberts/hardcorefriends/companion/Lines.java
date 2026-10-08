package io.github.bradley09roberts.hardcorefriends.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every friend's wording for every {@link Line}. Each friend has their own variants for every line (three or more
 * for work and lifecycle lines, two to four for the everyday needs), written in their own voice; {@link Speech} picks
 * one at random and fills the placeholders documented on the line.
 *
 * <p>Rules for the table: British English, under 90 characters before substitution, family friendly, never
 * about mods or code, and suited to a Hardcore world without being grim. A line only uses the placeholders its
 * {@link Line} constant documents, and lines without arguments contain no {@code %} at all. Arguments that may
 * arrive as whole sentences (advice, Scout's report, discoveries) always come last, after a colon, so their own
 * capitals and full stops read naturally. Food names arrive capitalised ("Sweet Berries"), so food lines avoid
 * "a"/"that ... was" around them; job and pastime arguments are "-ing" phrases ("felling a tree"). A generic
 * fallback keeps {@link #get} from ever returning nothing. {@link #problems()} checks the rules that can be checked.
 */
public final class Lines {
	/** The longest a template may be before its arguments are filled in. */
	public static final int MAX_LENGTH = 90;
	private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+)\\$s");
	private static final EnumMap<FriendId, EnumMap<Line, String[]>> TABLE = new EnumMap<>(FriendId.class);
	private static final EnumMap<Line, String[]> FALLBACK = new EnumMap<>(Line.class);
	private static final String[] LAST_RESORT = {"Hmm."};

	static {
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
		livestock();
	}

	private Lines() {
	}

	/**
	 * The variants {@code friend} can say for {@code line}: their own if written, otherwise the generic ones.
	 * Never null or empty. The returned array is a copy and may be modified by the caller.
	 */
	public static String[] get(FriendId friend, Line line) {
		if (line != null) {
			EnumMap<Line, String[]> own = friend == null ? null : TABLE.get(friend);
			String[] variants = own == null ? null : own.get(line);
			if (variants != null && variants.length > 0) {
				return variants.clone();
			}
			String[] generic = FALLBACK.get(line);
			if (generic != null && generic.length > 0) {
				return generic.clone();
			}
		}
		return LAST_RESORT.clone();
	}

	/** True if this friend has wording of their own for the line, rather than falling back to the generic one. */
	public static boolean hasOwn(FriendId friend, Line line) {
		EnumMap<Line, String[]> own = TABLE.get(friend);
		String[] variants = own == null ? null : own.get(line);
		return variants != null && variants.length > 0;
	}

	/**
	 * The table's self-check: a description of every breach of the rules above, or an empty list when all is well.
	 * Every friend has at least two variants of their own for every line; every template, theirs or generic, is at
	 * most {@value #MAX_LENGTH} characters, uses only the placeholders its line receives ({@link Line#args()}), has no
	 * {@code %} at all when it receives none, and formats cleanly.
	 */
	public static List<String> problems() {
		List<String> found = new ArrayList<>();
		for (Line line : Line.values()) {
			String[] generic = FALLBACK.get(line);
			if (generic == null || generic.length == 0) {
				found.add("no generic wording for " + line);
			} else {
				check("generic", line, generic, found);
			}
			for (FriendId friend : FriendId.values()) {
				String[] own = hasOwn(friend, line) ? TABLE.get(friend).get(line) : new String[0];
				if (own.length < 2) {
					found.add(friend.displayName() + " has " + own.length + " line(s) of their own for " + line + ", not 2 or more");
				}
				check(friend.displayName(), line, own, found);
			}
		}
		return found;
	}

	private static void check(String who, Line line, String[] variants, List<String> found) {
		Object[] sample = new Object[line.args()];
		Arrays.fill(sample, "Sample");
		for (String template : variants) {
			String where = who + " " + line + " \"" + template + "\"";
			if (template.isBlank()) {
				found.add(where + ": blank");
			}
			if (template.length() > MAX_LENGTH) {
				found.add(where + ": longer than " + MAX_LENGTH + " characters");
			}
			if (PLACEHOLDER.matcher(template).replaceAll("").contains("%")) {
				found.add(where + ": a % that is not a %N$s placeholder");
			}
			Matcher m = PLACEHOLDER.matcher(template);
			while (m.find()) {
				int n = Integer.parseInt(m.group(1));
				if (n < 1 || n > line.args()) {
					found.add(where + ": uses %" + n + "$s but the line receives " + line.args() + " argument(s)");
				}
			}
			try {
				String.format(template, sample);
			} catch (IllegalFormatException e) {
				found.add(where + ": does not format (" + e.getMessage() + ")");
			}
		}
	}

	private static void put(FriendId friend, Line line, String... variants) {
		TABLE.computeIfAbsent(friend, f -> new EnumMap<>(Line.class)).put(line, variants);
	}

	private static void generic(Line line, String... variants) {
		FALLBACK.put(line, variants);
	}

	/** Plain, voice-neutral wording used when a friend has no line of their own. */
	private static void generic() {
		generic(Line.RECRUITED, "Glad to join you, %1$s.", "Hello, %1$s. Let's look after each other.",
			"Thanks, %1$s. I'm ready to help.");
		generic(Line.FOLLOW, "Following you, %1$s.", "Right behind you, %1$s.", "Lead the way, %1$s.");
		generic(Line.STAY, "Staying here.", "I'll wait right here.", "Holding this spot.");
		generic(Line.WORK, "Back to work.", "Back to my jobs.", "Right, back to it.");
		generic(Line.DISMISSED, "Goodbye, then. Stay safe.", "Take care of yourself.", "Farewell, friends.");
		generic(Line.GREETING, "Hello, %2$s. I'm %1$s.", "Hi, %2$s. Just %1$s.", "Good to see you, %2$s. I'm %1$s.");
		generic(Line.THANKS_FOOD, "Thank you.", "Thanks, that helps.", "Much appreciated.");
		generic(Line.THANKS_GIFT, "Thank you for the %1$s.", "The %1$s? Thank you.", "Every %1$s helps. Thanks.");
		generic(Line.RETREAT, "I'm hurt. Falling back.", "Pulling back to recover.", "I need a moment. Backing off.");
		generic(Line.RECOVERED, "Feeling better now.", "All healed. Back to it.", "That's better.");
		generic(Line.CREEPER, "Creeper! Stay back!", "Creeper nearby! Move away!", "Watch out, a creeper!");
		generic(Line.FIGHT, "Back off, %1$s!", "Not today, %1$s!", "Clear off, %1$s!");
		generic(Line.WARNING, "%1$s, careful: %2$s!", "Watch out, %1$s: %2$s!", "%1$s, heads up: %2$s!");
		generic(Line.FRIEND_DIED, "Goodbye, %1$s. We'll miss you.", "Rest well, %1$s.", "We've lost %1$s.");
		generic(Line.PLAYER_HURT, "%1$s, you're badly hurt! Heal up.", "%1$s, step back and eat something!",
			"%1$s, you need to rest!");
		generic(Line.NIGHT_RETURN, "It's getting dark. Heading back to camp.", "Dusk. Time to go home.",
			"Night's coming. Back to camp.");
		generic(Line.MORNING, "Good morning.", "Morning, everyone.", "A new day.");
		generic(Line.NIGHT_WATCH, "I'll keep watch. Sleep well.", "I've got the watch. Get some rest.",
			"My watch. Off to bed, everyone.");
		generic(Line.ALARM, "%1$s in the camp! Everyone up!", "Wake up! %1$s in the camp!",
			"Alarm! %1$s inside the camp!");
		generic(Line.WORK_START, "I'll be %1$s.", "Time for %1$s.", "Starting on it: %1$s.");
		generic(Line.NEED_TOOL, "I need a tool: %1$s.", "Any %1$s going spare?", "Tool needed: %1$s.");
		generic(Line.TOOL_BROKE, "My %1$s broke.", "There goes my %1$s.", "My %1$s is worn out.");
		generic(Line.NEED_MATERIALS, "I need %1$s.", "I'm short of %1$s.", "Can anyone bring %1$s?");
		generic(Line.DEPOSIT, "Supplies are in the chest.", "Chest stocked.", "All stored away.");
		generic(Line.SHARE, "Here, %1$s: %2$s.", "For you, %1$s: %2$s.", "%1$s, I brought you %2$s.");
		generic(Line.BUILD_DONE, "Finished the %1$s.", "That's the %1$s done.", "Done: the %1$s.");
		generic(Line.CONTRAPTION_DONE, "Finished the %1$s.", "That's the %1$s working.", "Done: the %1$s.");
		generic(Line.DISCOVERY, "Something to note: %1$s", "Worth knowing: %1$s", "Take note: %1$s");
		generic(Line.ADVICE, "A tip: %1$s", "Remember: %1$s", "Worth knowing: %1$s");
		generic(Line.UNITY_UP, "We're %1$s now.", "%1$s! That feels good.", "We've become %1$s.");
		generic(Line.CAMP_UP, "Our camp is a %1$s now.", "We're a %1$s now!", "A %1$s! Well done, everyone.");
		generic(Line.IDLE, "All quiet.", "Nice day for it.", "Keep your torches handy.");
		generic(Line.HUNGRY, "I'm getting hungry.", "Time for a bite to eat.", "My stomach's rumbling.");
		generic(Line.ATE, "Mm, %1$s. That hit the spot.", "Mm, %1$s.", "Much better.");
		generic(Line.NO_FOOD, "There's nothing to eat. Could someone bring food to the chest?", "We're out of food.");
		generic(Line.STARVING, "I'm starving!", "I need food, now.");
		generic(Line.SLEEPY, "I'm worn out. Time for bed.", "Off to sleep.", "Goodnight, everyone.");
		generic(Line.RESTED, "Slept like a log.", "I feel rested.", "Ready for a new day.");
		generic(Line.CHAT, "Hey %1$s, how's it going?", "%1$s! Got a minute?", "How are you, %1$s?");
		generic(Line.CHAT_REPLY, "Not bad, thanks for asking.", "Good to see you too.", "Can't complain.");
		generic(Line.LEISURE, "Time for %1$s.", "Taking a break: %1$s.", "Just %1$s for a bit.");
		generic(Line.COSY, "Nothing like a warm fire.", "Ah, that's cosy.", "Warming up a bit.");
		generic(Line.MOOD_LOW, "I'm not feeling my best. It's my %1$s need.", "Could be better, honestly.");
		generic(Line.MOOD_GREAT, "What a lovely day!", "I feel great.", "Life's good at this camp.");
		generic(Line.HELPING_OUT, "I'll help with %1$s.", "I'll pitch in: %1$s.", "Lending a hand: %1$s.");
	}

	/** Fern, the farmer: patient, caring and warm; crops, and making sure everyone has eaten. */
	private static void fern() {
		FriendId f = FriendId.FERN;
		put(f, Line.RECRUITED,
			"Hello, %1$s. I'll keep the crops growing and everyone fed.",
			"Thank you for the food, %1$s. Now let me grow us plenty more.",
			"%1$s, it's lovely to meet you. Let's make sure nobody goes hungry.",
			"I'm Fern. Don't you worry, %1$s, you'll never go hungry with me around.");
		put(f, Line.FOLLOW,
			"Right behind you, %1$s. Shout if you get peckish.",
			"Coming, %1$s. I've packed a little something to eat.",
			"Lead the way, %1$s. I'll keep an eye on your hunger.");
		put(f, Line.STAY,
			"I'll wait right here. Take your time.",
			"Staying put. Come back safely, won't you?",
			"I'll keep this spot warm for you.");
		put(f, Line.WORK,
			"Back to the fields, then. Those crops won't tend themselves.",
			"Off to the farm. There's always something to see to.",
			"Right, back to work. I'll make sure there's food tonight.");
		put(f, Line.DISMISSED,
			"Oh. Well, look after yourself. And please remember to eat.",
			"I understand. Look after the farm for me, won't you?",
			"Goodbye, then. Keep some bread with you, always.");
		put(f, Line.GREETING,
			"Hello, %2$s! I'm %1$s. Have you eaten today?",
			"Hi, %2$s. Just %1$s. Are you keeping well?",
			"Oh, %2$s! I'm %1$s at the moment. Don't forget to eat.",
			"Good to see you, %2$s. I'm %1$s, nice and steady.");
		put(f, Line.THANKS_FOOD,
			"Oh, thank you! I'll save the crumbs for the chickens.",
			"That's very kind. Food tastes better when it's shared.",
			"Thank you, dear. I feel better already.");
		put(f, Line.THANKS_GIFT,
			"Thank you for the %1$s. I'll put it to good use.",
			"How thoughtful! I'll take good care of this %1$s.",
			"The %1$s? For me? You're very sweet.");
		put(f, Line.RETREAT,
			"I'm hurt. I need to step back and rest a moment.",
			"Ouch! Falling back. Please be careful out there.",
			"I can't take much more. Pulling back to safety.");
		put(f, Line.RECOVERED,
			"That's better. A little rest does wonders.",
			"Feeling much better now. Back to the crops.",
			"All mended. Thank you for being patient with me.");
		put(f, Line.CREEPER,
			"Creeper! Everyone, away from the crops!",
			"Oh no, a creeper! Keep your distance!",
			"Creeper close by! Back away, slowly now.");
		put(f, Line.FIGHT,
			"Leave my crops alone, %1$s!",
			"Go away, %1$s! Shoo!",
			"I'd rather not, %1$s, but I'll stand my ground.");
		put(f, Line.WARNING,
			"%1$s, please be careful: %2$s!",
			"Oh, %1$s, mind yourself: %2$s!",
			"%1$s, dear, watch out: %2$s!");
		put(f, Line.FRIEND_DIED,
			"Oh, %1$s... I'll plant something lovely for you.",
			"We'll miss you, %1$s. Rest now.",
			"%1$s is gone. Let's look after each other, please.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! Eat something and rest, please.",
			"Oh, %1$s, you're hurt! Come back to camp and have a bite.",
			"%1$s, please step back and eat. You're very hurt.");
		put(f, Line.NIGHT_RETURN,
			"It's getting dark. Time to head home for supper.",
			"The sun's going down. Let's all get home safely.",
			"Evening's coming. I'll head back and check the food stores.");
		put(f, Line.MORNING,
			"Good morning! The crops look lovely in the sun.",
			"Morning, everyone. Make sure you have some breakfast.",
			"A new day. Let's see what's ripened overnight.");
		put(f, Line.NIGHT_WATCH,
			"I'll keep watch tonight. Sleep tight, dears.",
			"My turn to watch. Rest now, all of you. I'll wake you if need be.",
			"I'll sit up by the fire and keep an eye out. Goodnight.");
		put(f, Line.ALARM,
			"Wake up, everyone! %1$s in the camp!",
			"Oh, there's a %1$s coming in! Up, quickly, dears!",
			"Everyone up! %1$s by the camp, please hurry!");
		put(f, Line.WORK_START,
			"Right, I'll be %1$s if anyone needs me.",
			"Time for %1$s. Slow and steady does it.",
			"Off I go, %1$s. Back before supper.");
		put(f, Line.NEED_TOOL,
			"Has anyone got a spare %1$s? I can't tend the farm without one.",
			"Tool needed: %1$s. Could someone help me out?",
			"No %1$s, no harvest, I'm afraid.");
		put(f, Line.TOOL_BROKE,
			"Oh dear, my %1$s has broken.",
			"There goes my %1$s. It served me well.",
			"My %1$s just snapped. I'll need another.");
		put(f, Line.NEED_MATERIALS,
			"I'm short of %1$s. Could anyone help?",
			"If anyone finds %1$s, I'd be ever so grateful.",
			"I need %1$s before I can carry on.");
		put(f, Line.DEPOSIT,
			"I've put the harvest in the chest. Help yourselves.",
			"The chest's stocked. Nobody needs to go hungry.",
			"Supplies are in the chest. Take what you need.");
		put(f, Line.SHARE,
			"Here, %1$s: %2$s. I've plenty to spare.",
			"%1$s, I've brought you %2$s. Take care of yourself.",
			"For you, %1$s: %2$s. Sharing is caring.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. That was lovely work.",
			"That's the %1$s done. Now, who's hungry?",
			"All done with the %1$s. Everyone should be proud.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. I don't understand it, but it's clever!",
			"The %1$s: done! Clever stuff, I must say.",
			"That's the %1$s finished. How marvellous.");
		put(f, Line.DISCOVERY,
			"Oh, look at this: %1$s",
			"Something worth noting: %1$s",
			"Worth knowing, everyone: %1$s");
		put(f, Line.ADVICE,
			"A gentle reminder: %1$s",
			"Please do listen: %1$s",
			"Take it from me: %1$s");
		put(f, Line.UNITY_UP,
			"We're %1$s now. That warms my heart.",
			"%1$s! I always knew we'd grow close.",
			"%1$s at last. Like a good harvest, it took time.");
		put(f, Line.CAMP_UP,
			"Our home is a %1$s now. Room for a bigger farm!",
			"A %1$s! I'll plant more so there's enough for everyone.",
			"We've grown into a %1$s, just like a seedling.");
		put(f, Line.IDLE,
			"Wheat likes patience. So do people, I find.",
			"Has everyone eaten? Just checking.",
			"I do love the smell of fresh bread.",
			"A full larder makes a happy camp.");
		// Everyday needs
		put(f, Line.HUNGRY,
			"My tummy's rumbling. Time for a little something.",
			"I'd better eat. I can't look after everyone on an empty stomach.",
			"Feeling peckish. I'll fetch a bite to eat.");
		put(f, Line.ATE,
			"Mm, %1$s. Grown with care, eaten with thanks.",
			"%1$s. Just what I needed, dear.",
			"Lovely, %1$s. Now I can keep going.");
		put(f, Line.NO_FOOD,
			"Not a crumb left in the chest. Could someone bring food, please?",
			"The larder's empty and I'm so hungry. Some food in the chest would help, dear.",
			"Oh dear, no food anywhere. Please, put something to eat in the chest.");
		put(f, Line.STARVING,
			"I'm starving. Please, anything to eat!",
			"So hungry it hurts. I need food soon.");
		put(f, Line.SLEEPY,
			"I'm worn out. Off to bed. Goodnight, everyone.",
			"Time for bed. Sleep well, all of you.",
			"My eyes are closing. Goodnight, dears.");
		put(f, Line.RESTED,
			"Slept beautifully. Good morning, everyone!",
			"A good night's sleep. Now, who wants breakfast?",
			"I feel rested and ready for the fields.");
		put(f, Line.CHAT,
			"%1$s! Have you eaten today?",
			"Hello, %1$s. How are you keeping?",
			"%1$s, come and sit a moment. How's your day been?");
		put(f, Line.CHAT_REPLY,
			"Oh, I'm well, %1$s. Thank you for asking.",
			"Lovely to chat, %1$s. The crops are coming along nicely.",
			"I'm fine, dear. Are you eating properly, %1$s?");
		put(f, Line.LEISURE,
			"Time for %1$s. The crops can spare me a while.",
			"Just %1$s for a bit. Bliss.",
			"A little break: %1$s. Good for the soul.");
		put(f, Line.COSY,
			"Ah, warm at last. Lovely.",
			"Nothing like a warm fire after a day in the fields.",
			"Snug as a seed in the soil.");
		put(f, Line.MOOD_LOW,
			"I'm feeling a bit low, I'm afraid. It's my %1$s need, mostly.",
			"Not my best day. I must see to my %1$s need.");
		put(f, Line.MOOD_GREAT,
			"What a lovely day. Everyone's fed and happy!",
			"I feel wonderful. Full bellies and good friends.",
			"My heart's as full as the harvest basket.");
		put(f, Line.HELPING_OUT,
			"I'll lend a hand with %1$s.",
			"Not my usual work, but happy to help: %1$s.",
			"Many hands make light work. I'll be %1$s.");
	}

	/** Oak, the builder: practical, methodical and measured; plans and materials. */
	private static void oak() {
		FriendId f = FriendId.OAK;
		put(f, Line.RECRUITED,
			"Oak, builder. Good to meet you, %1$s. Let's plan a proper camp.",
			"Right, %1$s. Show me the site and I'll start measuring.",
			"Thanks, %1$s. Bring me wood and stone and I'll give you walls.");
		put(f, Line.FOLLOW,
			"Following, %1$s. I'll note good building spots on the way.",
			"Lead on, %1$s. Steady pace, mind.",
			"With you, %1$s. I've spare planks if we need a bridge.");
		put(f, Line.STAY,
			"Holding here. I'll be ready when you are.",
			"Staying put. I'll go over my plans.",
			"Right. I'll wait on this spot.");
		put(f, Line.WORK,
			"Back to the build. One block at a time.",
			"Right, back to work. Plans don't build themselves.",
			"Understood. Back to building.");
		put(f, Line.DISMISSED,
			"Fair enough. The foundations are sound; look after them.",
			"Understood. Keep the walls in good repair.",
			"Right. Measure twice, place once. Goodbye.");
		put(f, Line.GREETING,
			"%2$s. I'm %1$s. All going to plan.",
			"Hello, %2$s. Currently %1$s. Steady progress.",
			"Good to see you, %2$s. I'm %1$s.",
			"Hi, %2$s. I'm %1$s, then on to the next job.");
		put(f, Line.THANKS_FOOD,
			"Thanks. Can't build on an empty stomach.",
			"Much appreciated. That'll keep me going.",
			"Cheers. Good fuel for a long shift.");
		put(f, Line.THANKS_GIFT,
			"Thanks for the %1$s. I'll find a use for it.",
			"The %1$s? Useful. I'll add it to the inventory.",
			"Every %1$s counts. Thank you.");
		put(f, Line.RETREAT,
			"Taking too much damage. Stepping back.",
			"I'm hurt. Pulling back to regroup.",
			"Need to fall back. Can't build if I can't stand.");
		put(f, Line.RECOVERED,
			"Patched up. Back to it.",
			"Better now. Where was I?",
			"Feeling sound again. Resuming work.");
		put(f, Line.CREEPER,
			"Creeper! Keep it away from the walls!",
			"Creeper nearby. Back off before it ruins the build.",
			"Creeper! I've no wish to rebuild anything today.");
		put(f, Line.FIGHT,
			"Right, %1$s. You're not getting past me.",
			"Off my building site, %1$s.",
			"Dealing with this %1$s, then back to work.");
		put(f, Line.WARNING,
			"%1$s, careful: %2$s.",
			"Watch yourself, %1$s: %2$s.",
			"%1$s, heads up: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll build something to remember you by.",
			"We've lost %1$s. Hold together, everyone.",
			"Rest easy, %1$s. We'll finish what you started.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt. Step back and heal.",
			"%1$s! Get behind a wall and patch yourself up.",
			"Careful, %1$s. You're in bad shape. Rest a moment.");
		put(f, Line.NIGHT_RETURN,
			"Light's fading. Downing tools and heading in.",
			"Getting dark. No building in the dark. Back to camp.",
			"Sun's setting. I'll pick this up in the morning.");
		put(f, Line.MORNING,
			"Morning. I've got a plan for today.",
			"Good morning. Let's check the materials and get started.",
			"New day. First job: count the planks.");
		put(f, Line.NIGHT_WATCH,
			"I'll take the watch. Get your rest.",
			"My watch. Nothing gets past the fire.",
			"Watch is mine tonight. Sleep, we've work in the morning.");
		put(f, Line.ALARM,
			"%1$s in the camp! Up, everyone, tools in hand!",
			"Wake up! %1$s at the camp. Let's deal with it together.",
			"Alarm! %1$s inside the camp. On your feet!");
		put(f, Line.WORK_START,
			"Starting on it: %1$s.",
			"Plan's set. I'll be %1$s.",
			"On to the next job: %1$s.");
		put(f, Line.NEED_TOOL,
			"I need a tool: %1$s. Anyone?",
			"Requisition: one %1$s. Can't work with bare hands.",
			"No %1$s, no progress. Simple as that.");
		put(f, Line.TOOL_BROKE,
			"That's my %1$s worn through. Need a new one.",
			"My %1$s broke. Adding a replacement to the list.",
			"My %1$s has given out. To be expected.");
		put(f, Line.NEED_MATERIALS,
			"Materials needed: %1$s.",
			"Can't continue without %1$s.",
			"To finish this I need %1$s. Anyone able to help?");
		put(f, Line.DEPOSIT,
			"Stock's in the chest. Keep it tidy.",
			"Supplies deposited. Everything in its place.",
			"That's the chest stocked. Inventory's in order.");
		put(f, Line.SHARE,
			"%1$s, here: %2$s. Should help with the job.",
			"Passing %2$s to you, %1$s. Should come in handy.",
			"For you, %1$s: %2$s.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Solid work.",
			"That's the %1$s complete. Built to last.",
			"Done: the %1$s. On to the next plan.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Neat bit of engineering.",
			"That's the %1$s working. Tidy job.",
			"Done: the %1$s. Good, reliable work.");
		put(f, Line.DISCOVERY,
			"Worth noting: %1$s",
			"One for the plans: %1$s",
			"Useful to know: %1$s");
		put(f, Line.ADVICE,
			"Practical tip: %1$s",
			"From experience: %1$s",
			"Plan ahead: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s. Good foundations make strong walls.",
			"We're %1$s now. Built properly, block by block.",
			"%1$s. That's what steady work gets you.");
		put(f, Line.CAMP_UP,
			"We're a %1$s now. Time for the next set of plans.",
			"The camp's grown into a %1$s. Well built, everyone.",
			"A %1$s. I've already drawn up what comes next.");
		put(f, Line.IDLE,
			"Measure twice, place once.",
			"Good wood, good walls. Simple.",
			"I'm thinking a porch would suit the cabin.",
			"Everything's square. I checked twice.");
		// Everyday needs
		put(f, Line.HUNGRY,
			"Stomach's empty. Breaking for food.",
			"Can't build on an empty stomach. Getting something to eat.",
			"Time to refuel. Back shortly.");
		put(f, Line.ATE,
			"%1$s. Good, solid food.",
			"Right. %1$s, done. Fuelled for the next shift.",
			"That's lunch: %1$s. Back to it.");
		put(f, Line.NO_FOOD,
			"No food in my pack or the chest. Someone needs to stock the larder.",
			"Food stores are empty. Put some in the chest and we'll all work better.");
		put(f, Line.STARVING,
			"I'm starving. Can't work like this.",
			"No food for too long. I'm getting weak.");
		put(f, Line.SLEEPY,
			"Tools down. Time to sleep.",
			"Long day. I'm turning in.",
			"Bed. We'll pick it up in the morning.");
		put(f, Line.RESTED,
			"Slept well. Ready to build.",
			"Good night's rest. The plans are fresh in my head.",
			"Rested. Let's get to work.");
		put(f, Line.CHAT,
			"%1$s. How's your work coming along?",
			"Got a minute, %1$s? Tell me how things stand.",
			"%1$s, a quick word. All going to plan?");
		put(f, Line.CHAT_REPLY,
			"Steady progress, %1$s. Thanks for asking.",
			"All square here, %1$s. You?",
			"Can't complain, %1$s. Walls are up, roof's on.");
		put(f, Line.LEISURE,
			"Break time: %1$s. Keeps the hands steady.",
			"Time for %1$s. Even builders need a rest.");
		put(f, Line.COSY,
			"Warm and dry. That's what a good roof is for.",
			"Good fire. Well laid, too.",
			"Out of the weather. Proper comfort.");
		put(f, Line.MOOD_LOW,
			"Not at my best. Need to fix my %1$s need.",
			"Morale's low. Worst need: %1$s. I'll sort it.");
		put(f, Line.MOOD_GREAT,
			"Everything's going to plan. Feeling good.",
			"Solid day. Solid camp. Can't ask for more.",
			"I'm in fine form. Let's build something.");
		put(f, Line.HELPING_OUT,
			"Not my trade, but I'll do it properly: %1$s.",
			"Lending a hand. I'll be %1$s.",
			"It needs doing, so I'll take on %1$s.");
	}

	/** Flint, the miner: cautious, with dry, deadpan humour; wary of lava, creepers and dark tunnels. */
	private static void flint() {
		FriendId f = FriendId.FLINT;
		put(f, Line.RECRUITED,
			"Flint. I dig. You're %1$s? Don't dig straight down and we'll get along.",
			"Fine, %1$s, I'm in. I'll bring the torches. Lots of torches.",
			"%1$s, is it? Right. I mine, I don't do lava. Deal?");
		put(f, Line.FOLLOW,
			"Following you, %1$s. Try not to walk into lava.",
			"Lead on, %1$s. I'll watch the dark bits.",
			"Right behind you, %1$s. Not in front. Behind.");
		put(f, Line.STAY,
			"Staying here. Nice and still. No lava.",
			"Standing still. My favourite job.",
			"Right. I'll stand here and be suspicious of shadows.");
		put(f, Line.WORK,
			"Back underground. My natural habitat.",
			"Back to the mine. Torches first.",
			"Right. Back to hitting rocks.");
		put(f, Line.DISMISSED,
			"Fair enough. Keep the torches lit.",
			"Off I go, then. Don't dig straight down without me.",
			"Understood. Mind the lava. I mean it.");
		put(f, Line.GREETING,
			"%2$s. I'm %1$s. Nothing's exploded yet.",
			"Oh, it's you, %2$s. I'm %1$s. Riveting stuff.",
			"%2$s. Still alive, still %1$s.",
			"Hi, %2$s. I'm %1$s. Quietly. Carefully.");
		put(f, Line.THANKS_FOOD,
			"Food. My second favourite thing, after not falling in lava.",
			"Thanks. That beats eating cave dust.",
			"Cheers. Digging is hungry work.");
		put(f, Line.THANKS_GIFT,
			"The %1$s? Thanks. I'll keep it well away from lava.",
			"Thanks for the %1$s. Can't remember my last present.",
			"A gift? The %1$s? I'm touched. Mildly.");
		put(f, Line.RETREAT,
			"I'm hurt. Retreating. Strategically. Quickly.",
			"Nope. Too much damage. Backing off.",
			"Health's low. Not today, thank you. Backing off.");
		put(f, Line.RECOVERED,
			"Better. Still cautious, but better.",
			"Patched up. Let's not do that again.",
			"Right as rain. Well, right as gravel.");
		put(f, Line.CREEPER,
			"Creeper. The hissing kind. Move!",
			"Creeper! I'd rather not be a crater today.",
			"That's a creeper. I'm leaving. You should too.");
		put(f, Line.FIGHT,
			"Go back to your cave, %1$s.",
			"Right then, %1$s. Let's make this quick.",
			"This %1$s again? Of course. Today was too quiet.");
		put(f, Line.WARNING,
			"%1$s, quick word: %2$s.",
			"Not to alarm you, %1$s, but %2$s.",
			"%1$s. Bad news: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s. I don't say this often: I'll miss you.",
			"Lost %1$s. Keep your torches lit, everyone.",
			"%1$s... Gone too soon. Rest easy.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you look terrible. Not a joke. Heal up.",
			"%1$s, you're badly hurt. Back off and eat something.",
			"%1$s! Health. Low. Retreat. Now.");
		put(f, Line.NIGHT_RETURN,
			"Getting dark. Dark is where creepers live. Heading in.",
			"Sun's going. So am I. Back to camp.",
			"Night's coming. I've seen enough dark tunnels for one day.");
		put(f, Line.MORNING,
			"Morning. Survived another night. Impressive, really.",
			"Good morning. Nothing exploded. Promising start.",
			"Morning. Daylight. My favourite kind of light.");
		put(f, Line.NIGHT_WATCH,
			"I'll keep watch. Somebody has to stay paranoid.",
			"My watch. I'll be the one staring at shadows.",
			"I've got the watch. Sleep. I'll worry for everyone.");
		put(f, Line.ALARM,
			"%1$s in the camp. Told you the dark was trouble. Up!",
			"Everyone up. There's a %1$s, and it's not here to chat.",
			"Wake up! %1$s in camp. Not a drill.");
		put(f, Line.WORK_START,
			"Right. I'll be %1$s. Carefully.",
			"Time for %1$s. Torches at the ready.",
			"Starting on it: %1$s. Lava permitting.",
			"Back to %1$s. Wish me a boring day.");
		put(f, Line.NEED_TOOL,
			"Tool needed: %1$s. Fists are a poor substitute.",
			"Any %1$s going spare? I've tried glaring at the rock.",
			"No %1$s, no mining. The rock is very firm about this.");
		put(f, Line.TOOL_BROKE,
			"My %1$s just broke. Rest in pieces.",
			"There goes the %1$s. It had a good run.",
			"My %1$s broke. I'm not upset. It's just dust in my eye.");
		put(f, Line.NEED_MATERIALS,
			"Need %1$s. Can't make it out of thin air.",
			"Short of %1$s. Anyone?",
			"Can't carry on without %1$s. Sad, but true.");
		put(f, Line.DEPOSIT,
			"Dropped the rocks in the chest. You're welcome.",
			"The chest's full of stone. Try to contain your excitement.",
			"Deposited. Mostly cobblestone. Occasionally interesting.");
		put(f, Line.SHARE,
			"%1$s, take %2$s. Mind the lava on the way.",
			"Here, %1$s: %2$s. Don't make a fuss.",
			"Got %2$s for you, %1$s. No need to thank me.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Nobody fell in anything.",
			"That's the %1$s done. Torches and all.",
			"The %1$s: finished. Mildly proud of that.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. It clicks. I trust it. Mostly.",
			"That's the %1$s working. Didn't even explode.",
			"The %1$s: done. Let's see how long it lasts.");
		put(f, Line.DISCOVERY,
			"Mild excitement: %1$s",
			"Note this down: %1$s",
			"Spotted something: %1$s");
		put(f, Line.ADVICE,
			"Word of warning: %1$s",
			"Learned this the hard way: %1$s",
			"Free advice, worth more than gold: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s now. I suppose I like you lot.",
			"We're %1$s. Don't tell anyone I'm pleased.",
			"%1$s. Fine. I'm a bit chuffed.");
		put(f, Line.CAMP_UP,
			"A %1$s, eh? Fancy. Still needs more torches.",
			"We're a %1$s now. Built on good stone, I hope.",
			"Look at that, a %1$s. Nobody even fell in a hole.");
		put(f, Line.IDLE,
			"Never dig straight down. I say it a lot because it's true.",
			"Gravel. Why is it always gravel?",
			"I like caves. I just don't trust them.",
			"Lava's pretty. From a very long way away.");
		// Everyday needs
		put(f, Line.HUNGRY,
			"I'm hungry. Digging is hungry work. Who knew.",
			"My stomach's louder than the gravel. Food time.",
			"Food. Now. Or soon. Soon is fine.");
		put(f, Line.ATE,
			"%1$s. Not cave dust. A treat.",
			"Ate the %1$s. Feeling slightly less doomed.",
			"Mm, %1$s. Beats gravel, I'll give it that.");
		put(f, Line.NO_FOOD,
			"No food in my pack or the chest. That's worrying, and I'm the worrier.",
			"The chest has no food. Someone fix that before I start eating cobblestone.");
		put(f, Line.STARVING,
			"I'm starving. Not a joke. Food, please.",
			"Starving. Even I can't find the funny side.");
		put(f, Line.SLEEPY,
			"I'm exhausted. Bed. Somewhere with no creepers.",
			"Too tired to be careful. Bed, then.",
			"Sleeping now. Wake me if anything hisses.");
		put(f, Line.RESTED,
			"Slept well. Nothing exploded. Lovely.",
			"Rested. Still suspicious of everything, but rested.",
			"Good sleep. No dreams about lava. Mostly.");
		put(f, Line.CHAT,
			"%1$s. Seen any lava today? No? Good.",
			"Got a minute, %1$s? I've got cave stories.",
			"%1$s. Tell me something that isn't gravel.");
		put(f, Line.CHAT_REPLY,
			"Still alive, %1$s. That's the main thing.",
			"Fine, %1$s. Nothing's exploded. High praise.",
			"Not bad, %1$s. Found a nice rock earlier.");
		put(f, Line.LEISURE,
			"Time for %1$s. Relaxing. Lava-free.",
			"Break time: %1$s. Don't tell anyone I'm enjoying it.",
			"Just %1$s for a bit. Very safe. Very calm.");
		put(f, Line.COSY,
			"Warm, dry and creeper-free. Perfect.",
			"Ah, a fire. The good kind of hot.",
			"Cosy. I could almost relax.");
		put(f, Line.MOOD_LOW,
			"Feeling grim. Worst need: %1$s. Fix pending.",
			"Not great. My %1$s need is dragging me down.");
		put(f, Line.MOOD_GREAT,
			"I feel good. Suspiciously good.",
			"Great day. I'm waiting for the catch.",
			"I'm almost cheerful. Don't tell anyone.");
		put(f, Line.HELPING_OUT,
			"Fine, I'll do it: %1$s. Carefully.",
			"Not my job, but I'll be %1$s. Lava permitting.",
			"Pitching in: %1$s. Try to look surprised.");
	}

	/** Scout, the explorer: curious, adventurous and upbeat; reports clearly. */
	private static void scout() {
		FriendId f = FriendId.SCOUT;
		put(f, Line.RECRUITED,
			"Scout here! Ready to explore, %1$s. Where are we heading first?",
			"Hi, %1$s! I'll map everything around camp. Can't wait!",
			"Count me in, %1$s! I'll keep my eyes peeled for trouble and treasure.");
		put(f, Line.FOLLOW,
			"Lead the way, %1$s! I'll keep watch on the flanks.",
			"Adventure time! Right behind you, %1$s.",
			"Following you, %1$s. I'll call out anything I spot.");
		put(f, Line.STAY,
			"Staying here. I'll keep a lookout.",
			"Holding this spot. Great view from here!",
			"Okay, I'll wait. I'll keep watching the horizon.");
		put(f, Line.WORK,
			"Back to scouting! So much left to see.",
			"Off exploring again. I'll report back!",
			"Right! Time to map the area.");
		put(f, Line.DISMISSED,
			"Oh. Well, it was a great adventure. Stay safe out there!",
			"Okay. Remember: torches and an escape route, always.",
			"Goodbye! I'll keep exploring on my own.");
		put(f, Line.GREETING,
			"Hey, %2$s! I'm %1$s. Loads to report soon!",
			"Hi, %2$s! Currently %1$s. Never a dull moment!",
			"%2$s! I'm %1$s. Want to come along?",
			"Hello, %2$s! I'm %1$s, eyes open as always.");
		put(f, Line.THANKS_FOOD,
			"Thanks! Exploring makes me so hungry.",
			"Brilliant, snacks for the trail!",
			"Cheers! That'll keep me going till the next hill.");
		put(f, Line.THANKS_GIFT,
			"Ooh, the %1$s! Thank you!",
			"Thanks for the %1$s! I'll take it on my next trip.",
			"For me? The %1$s? Brilliant!");
		put(f, Line.RETREAT,
			"Too hot out here! Falling back!",
			"I'm hurt! Pulling back to recover.",
			"Retreating! I'll scout from a safer distance.");
		put(f, Line.RECOVERED,
			"Good as new! Where was I?",
			"Back on my feet. Let's see what's over the next hill!",
			"All healed up. Ready to roam again!");
		put(f, Line.CREEPER,
			"Creeper! Creeper! Everybody move!",
			"Creeper spotted, close by! Back off!",
			"Heads up, creeper right here!");
		put(f, Line.FIGHT,
			"Out of my way, %1$s!",
			"Come on then, %1$s!",
			"Engaging the %1$s! Stay clear!");
		put(f, Line.WARNING,
			"%1$s, heads up: %2$s!",
			"Warning, %1$s: %2$s!",
			"Careful, %1$s: %2$s!",
			"%1$s, look sharp: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll name the next hill after you.",
			"No... %1$s. I'll miss our adventures.",
			"We lost %1$s. I'll remember every trail we walked.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! Get to safety!",
			"%1$s! Your health's low! Back off and heal!",
			"%1$s, you're in bad shape! Pull back, I'll cover you!");
		put(f, Line.NIGHT_RETURN,
			"Sun's going down! Heading back to camp.",
			"Dusk already? Back home before the mobs come out.",
			"Getting dark. I'll finish the map tomorrow!");
		put(f, Line.MORNING,
			"Morning! The world's waiting to be explored!",
			"Good morning! I wonder what's over the next hill.",
			"Rise and shine! Let's see what's out there!");
		put(f, Line.NIGHT_WATCH,
			"I'll take the watch! I can see the whole camp from here.",
			"Watch is mine! Sleep tight, I'll keep my eyes peeled.",
			"On watch! Nothing's sneaking past me tonight.");
		put(f, Line.ALARM,
			"%1$s in the camp! Everybody up, now!",
			"Contact! %1$s coming in! Wake up!",
			"Alarm! %1$s spotted inside the camp!");
		put(f, Line.WORK_START,
			"Off I go, %1$s!",
			"Time for %1$s! Back soon with news.",
			"I'll be %1$s. Shout if you need me!",
			"Adventure calls: %1$s!");
		put(f, Line.NEED_TOOL,
			"Could someone spare one %1$s? Then I'm off again!",
			"Tool needed: %1$s. Can someone help?",
			"I need a tool: %1$s. The trail's waiting!");
		put(f, Line.TOOL_BROKE,
			"Oh no, my %1$s broke!",
			"My %1$s just snapped! Mid-adventure, too.",
			"There goes my %1$s. Time for a new one!");
		put(f, Line.NEED_MATERIALS,
			"I'm short of %1$s. Can anyone help?",
			"Need %1$s! I'll keep an eye out too.",
			"If anyone spots %1$s, let me know!");
		put(f, Line.DEPOSIT,
			"Dropped my finds in the chest!",
			"The chest's stocked with goodies from the trail.",
			"All unloaded! Ready for the next trip.");
		put(f, Line.SHARE,
			"Here, %1$s, %2$s for you!",
			"%1$s! Brought you %2$s from the trail.",
			"Catch, %1$s: %2$s!");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s! Looks great!",
			"Done with the %1$s! Next stop: anywhere!",
			"The %1$s: finished! Another landmark for the map.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s! That's so cool!",
			"The %1$s: done! I have to see it working!",
			"Wow, look at the %1$s go! Brilliant!");
		put(f, Line.DISCOVERY,
			"New on the map: %1$s",
			"Ooh, exciting: %1$s",
			"Report from the field: %1$s",
			"Marking this down: %1$s");
		put(f, Line.ADVICE,
			"Tip from the trail: %1$s",
			"Explorer's tip: %1$s",
			"Something I learned out there: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s! Best team I've ever explored with!",
			"We're %1$s now! What an adventure!",
			"%1$s! I knew we'd get here!");
		put(f, Line.CAMP_UP,
			"We're a %1$s now! Can't wait to map the surroundings.",
			"A %1$s! It looks amazing from the hilltop.",
			"Our camp's a %1$s now! Best base for miles!");
		put(f, Line.IDLE,
			"I wonder what's past that ridge.",
			"Some caves seem to go on forever. I'd love to find the end of one.",
			"I counted three new hills today. Lovely ones.",
			"One day I'll find the edge of the world. Or try!");
		// Everyday needs
		put(f, Line.HUNGRY,
			"Exploring makes me so hungry! Snack time.",
			"My stomach says it's lunchtime!",
			"Quick bite, then back on the trail!");
		put(f, Line.ATE,
			"%1$s! Perfect trail food!",
			"Mm, %1$s! Ready for the next hill!",
			"Fuelled up on %1$s! Let's go!");
		put(f, Line.NO_FOOD,
			"No food anywhere! Could someone stock the chest? I'll keep an eye out too!",
			"My pack's empty and so is the chest. We need food!");
		put(f, Line.STARVING,
			"I'm starving! I need food, really soon!",
			"So hungry! I can't keep going like this!");
		put(f, Line.SLEEPY,
			"Big day! Off to bed.",
			"I'm wiped out. Bedtime, adventures tomorrow!",
			"Goodnight, everyone! Dream of new horizons!");
		put(f, Line.RESTED,
			"Slept great! Where are we exploring today?",
			"Fully rested! I could walk to the edge of the world!",
			"Morning! I dreamt of a huge cave. Let's find it!");
		put(f, Line.CHAT,
			"%1$s! Guess what I saw today!",
			"Hey, %1$s! Want to hear about the ridge out east?",
			"%1$s! What's new? Tell me everything!");
		put(f, Line.CHAT_REPLY,
			"Hey, %1$s! Great to see you!",
			"Oh, %1$s, I've got so much to tell you!",
			"Doing great, %1$s! Just back from the trail!");
		put(f, Line.LEISURE,
			"Break time: %1$s! So much out there.",
			"Time for %1$s! I wonder what's beyond it all.",
			"Just %1$s for a bit. Planning the next trip!");
		put(f, Line.COSY,
			"Ooh, warm! Just what I needed after the trail.",
			"Cosy! Like a camp in a story.",
			"Warming up! Then back out there!");
		put(f, Line.MOOD_LOW,
			"Feeling a bit flat. My %1$s need, probably.",
			"Not my brightest day. Worst need: %1$s.");
		put(f, Line.MOOD_GREAT,
			"I feel amazing! Best camp ever!",
			"What a day! I could explore forever!",
			"Everything's brilliant! Even the clouds!");
		put(f, Line.HELPING_OUT,
			"New skill time! I'll try %1$s!",
			"Ooh, I'll give it a go: %1$s!",
			"Lending a hand: %1$s! How hard can it be?");
	}

	/** Spark, the redstone inventor: clever, excitable and fast-talking; gadgets and exclamation marks. */
	private static void spark() {
		FriendId f = FriendId.SPARK;
		put(f, Line.RECRUITED,
			"Spark here! %1$s, I've got SO many ideas! Pistons! Lamps! Doors!",
			"Oh brilliant, %1$s! Let's build something clever together!",
			"Hi, %1$s! Got redstone? No? We'll find some! This'll be great!");
		put(f, Line.FOLLOW,
			"Following, %1$s! Ooh, is that a lever? No? Okay!",
			"Right behind you, %1$s! Thinking up gadgets as we go!",
			"Lead on, %1$s! I'll bring the clever ideas!");
		put(f, Line.STAY,
			"Staying! I'll sketch some circuits while I wait!",
			"Holding still! Hard, but I'll manage!",
			"Okay, staying put! Plenty to think about!");
		put(f, Line.WORK,
			"Back to tinkering! Yes!",
			"Back to the workshop! So many projects!",
			"Work time! I've got a plan! Several plans!");
		put(f, Line.DISMISSED,
			"Oh! Right. Keep the redstone dry. Bye!",
			"Okay! Look after my gadgets, please!",
			"Leaving already? Fine! Mind the pressure plates on the way out!");
		put(f, Line.GREETING,
			"Hi, %2$s! I'm %1$s! Ask me about pistons later!",
			"%2$s! Hello! I'm %1$s, and it's going brilliantly!",
			"Oh, hi, %2$s! Busy %1$s! Lots to do!",
			"Hey, %2$s! I'm %1$s! Got any redstone?");
		put(f, Line.THANKS_FOOD,
			"Food! Brilliant! Brains need fuel!",
			"Thanks! I forgot to eat again, didn't I?",
			"Ooh, thank you! Back to full power!");
		put(f, Line.THANKS_GIFT,
			"The %1$s?! I can definitely make something with that!",
			"Ooh, the %1$s! Thank you! Ideas incoming!",
			"Thanks for the %1$s! Is it for a gadget? It's for a gadget!");
		put(f, Line.RETREAT,
			"Ow! Ow! Backing off!",
			"Too much damage! Tactical retreat!",
			"I'm hurt! Falling back to recharge!");
		put(f, Line.RECOVERED,
			"Fully recharged! Let's go!",
			"All better! Where was I? Oh yes, everything!",
			"Healed up! And I had three new ideas while resting!");
		put(f, Line.CREEPER,
			"Creeper! Not near my circuits, please!",
			"Creeper! That's a walking explosion! Move!",
			"Creeper! Run! Theory later!");
		put(f, Line.FIGHT,
			"Take that, %1$s!",
			"Not today, %1$s! I've got projects to finish!",
			"Back off, %1$s! I'm very busy!");
		put(f, Line.WARNING,
			"%1$s! Careful: %2$s!",
			"%1$s, quick, look out: %2$s!",
			"Alert, %1$s: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll build you a lamp that never goes out.",
			"Oh, %1$s. Camp won't be the same without you.",
			"%1$s is gone? I... We keep going. For you, %1$s.");
		put(f, Line.PLAYER_HURT,
			"%1$s! You're badly hurt! Heal! Now! Please!",
			"%1$s, your health! Back off and eat something!",
			"%1$s! Danger! You need healing right away!");
		put(f, Line.NIGHT_RETURN,
			"Getting dark! Time to light the lamps at camp!",
			"Sunset! Back to camp, I've got tinkering to do!",
			"Night's coming! Home we go, quick quick!");
		put(f, Line.MORNING,
			"Morning! I dreamt about a piston lift!",
			"Good morning! New day, new inventions!",
			"Morning! Who's ready for some engineering?");
		put(f, Line.NIGHT_WATCH,
			"I'm on watch! Best time for thinking, anyway!",
			"Night watch! I'll keep both eyes open, promise!",
			"My watch! If only I'd built that alarm bell already!");
		put(f, Line.ALARM,
			"%1$s in the camp! Alarm! Alarm! Everyone up!",
			"Wake up! %1$s! In the camp! Now!",
			"Intruder alert! %1$s in camp! Up, up, up!");
		put(f, Line.WORK_START,
			"Ooh, %1$s! Let's go!",
			"Time for %1$s! Brilliant!",
			"I'll be %1$s! Watch and learn!");
		put(f, Line.NEED_TOOL,
			"I need one %1$s! Just one! Quick, quick!",
			"Tool emergency! One %1$s, please!",
			"I need a tool: %1$s! Can't invent without one!");
		put(f, Line.TOOL_BROKE,
			"My %1$s just broke! Noooo!",
			"Snap! There goes my %1$s!",
			"My %1$s broke! I'll make a better one!");
		put(f, Line.NEED_MATERIALS,
			"I need %1$s! Then it all comes together!",
			"Short of %1$s! Anyone? Anyone?",
			"If someone brings %1$s, I'll build something amazing!");
		put(f, Line.DEPOSIT,
			"Chest stocked! Organised by colour! Not really!",
			"Dropped everything in the chest! Hands free for tinkering!",
			"All deposited! Now, where's my redstone?");
		put(f, Line.SHARE,
			"%1$s! Here: %2$s! You're welcome!",
			"Catch, %1$s! %2$s, just for you!",
			"Got %2$s for you, %1$s! Sharing is efficient!");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s! Not bad, if I say so myself!",
			"Done! The %1$s, built and ready!",
			"That's the %1$s finished! Next project!");
		put(f, Line.CONTRAPTION_DONE,
			"It works! The %1$s! It actually works!",
			"Finished the %1$s! Click, whirr, perfect!",
			"Behold, the %1$s! Tested and working!",
			"Done! The %1$s! Go on, try it!");
		put(f, Line.DISCOVERY,
			"Ooh, look: %1$s",
			"Interesting find: %1$s",
			"Oh, this is brilliant: %1$s");
		put(f, Line.ADVICE,
			"Quick tip: %1$s",
			"Clever idea: %1$s",
			"Listen, this is important: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s! Our team runs like a perfect machine!",
			"We're %1$s now! That's brilliant!",
			"%1$s! Everything's clicking into place!");
		put(f, Line.CAMP_UP,
			"We're a %1$s! More room for gadgets!",
			"A %1$s! I've got plans for every building!",
			"Our home's a %1$s now! Time to automate everything!");
		put(f, Line.IDLE,
			"What if doors opened themselves? Oh wait, I can do that!",
			"Redstone is just very tiny lightning. Probably.",
			"A lamp that turns on at night! Should I? I should!",
			"Pistons, pistons, pistons. Sorry, thinking out loud!");
		// Everyday needs
		put(f, Line.HUNGRY,
			"I forgot to eat again! Food time!",
			"Running low on power! Need a snack!",
			"Brain needs fuel! Getting food!");
		put(f, Line.ATE,
			"%1$s! Fully recharged!",
			"Mm, %1$s! Power levels rising!",
			"%1$s, eaten! Back to the circuits!");
		put(f, Line.NO_FOOD,
			"No food in the chest! Or my pack! Someone, anyone, food please!",
			"Food stores empty! That's a design flaw! Can someone fix it?");
		put(f, Line.STARVING,
			"Starving! Totally out of power! Food, please!",
			"So hungry I can't think! That's bad! Food!");
		put(f, Line.SLEEPY,
			"So tired! Brain shutting down! Bed!",
			"Bedtime! I'll dream up new gadgets!",
			"Powering down for the night! Goodnight!");
		put(f, Line.RESTED,
			"Fully recharged! And I had an idea in my sleep!",
			"Morning! Rested and buzzing!",
			"Slept brilliantly! Let's invent something!");
		put(f, Line.CHAT,
			"%1$s! Want to hear about pistons? Of course you do!",
			"Ooh, %1$s! I had the best idea! Listen!",
			"%1$s! Quick question! What would you automate?");
		put(f, Line.CHAT_REPLY,
			"%1$s! Hi! I was just thinking about redstone! And you!",
			"Great, %1$s! I've got three new ideas!",
			"Brilliant, %1$s! Thanks for asking!");
		put(f, Line.LEISURE,
			"Break time! Well, %1$s! Same thing!",
			"Time for %1$s! My favourite!",
			"Ooh, %1$s! Relaxing AND useful!");
		put(f, Line.COSY,
			"Ooh, toasty! Warm brain, clever brain!",
			"Lovely and warm! Fires are just very old gadgets!",
			"Warming up! Perfect thinking spot!");
		put(f, Line.MOOD_LOW,
			"I'm running low! Worst need: %1$s!",
			"Feeling flat! My %1$s need is all wrong!");
		put(f, Line.MOOD_GREAT,
			"I feel brilliant! Everything's clicking!",
			"Best day ever! Ideas everywhere!",
			"Fully charged and happy! Let's build something!");
		put(f, Line.HELPING_OUT,
			"Ooh, I'll help with %1$s! How hard can it be?",
			"I'll try %1$s! New skills, new ideas!",
			"Pitching in: %1$s! I might even improve it!");
	}

	/** Aegis, the warrior: calm and protective, a soldier of few words and steady reassurance. */
	private static void aegis() {
		FriendId f = FriendId.AEGIS;
		put(f, Line.RECRUITED,
			"Aegis. I'll keep you safe, %1$s.",
			"%1$s. You have my shield.",
			"I stand with you, %1$s. Nothing gets past me.");
		put(f, Line.FOLLOW,
			"With you, %1$s.",
			"I'll guard your back, %1$s.",
			"Lead, %1$s. I'm behind you.");
		put(f, Line.STAY,
			"Holding here.",
			"I'll guard this spot.",
			"Standing firm.");
		put(f, Line.WORK,
			"Back on guard.",
			"Returning to my post.",
			"Understood. I'll watch the camp.");
		put(f, Line.DISMISSED,
			"As you wish. Stay safe.",
			"Understood. Keep your shield up.",
			"Farewell. Guard each other.");
		put(f, Line.GREETING,
			"%2$s. I'm %1$s. All is well.",
			"%2$s. I'm %1$s. Stay close.",
			"Here, %2$s. I'm %1$s. You're safe.");
		put(f, Line.THANKS_FOOD,
			"Thank you.",
			"Much appreciated. I'm stronger for it.",
			"Thanks. I'll stay sharp.");
		put(f, Line.THANKS_GIFT,
			"Thank you for the %1$s.",
			"The %1$s. I'll put it to use.",
			"Kind of you. I'll keep the %1$s safe.");
		put(f, Line.RETREAT,
			"Wounded. Falling back.",
			"I need a moment. Pulling back.",
			"Stepping back to recover. Stay close.");
		put(f, Line.RECOVERED,
			"Recovered. Back on guard.",
			"I'm well again.",
			"Ready. Nothing will get through.");
		put(f, Line.CREEPER,
			"Creeper. Stand back.",
			"Creeper close. Move away, now.",
			"Creeper. Keep your distance.");
		put(f, Line.FIGHT,
			"Stand back. I'll handle the %1$s.",
			"%1$s. You'll go no further.",
			"Behind me. I have the %1$s.");
		put(f, Line.WARNING,
			"%1$s. Careful: %2$s.",
			"%1$s, stay alert: %2$s.",
			"Steady, %1$s: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s has fallen. I'll guard the others.",
			"Rest, %1$s. Your watch is over.",
			"Farewell, %1$s. You'll be remembered.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're hurt. Get behind me.",
			"%1$s. Fall back. I'll cover you.",
			"Heal up, %1$s. I'm right here.");
		put(f, Line.NIGHT_RETURN,
			"Dusk. Back to camp. I'll take the watch.",
			"Night falls. Returning to guard the camp.",
			"Darkness coming. Everyone in. I'll keep watch.");
		put(f, Line.MORNING,
			"Morning. Another night behind us.",
			"Dawn. All safe.",
			"Morning. I kept watch. All's well.");
		put(f, Line.NIGHT_WATCH,
			"I have the watch. Sleep.",
			"This watch is mine. Rest easy.",
			"I'll keep watch. Nothing comes through.");
		put(f, Line.ALARM,
			"%1$s in the camp. Up. Arms ready.",
			"To arms. %1$s inside the camp.",
			"Wake. %1$s. Stand with me.");
		put(f, Line.WORK_START,
			"On duty: %1$s.",
			"I'll be %1$s.",
			"Starting: %1$s.");
		put(f, Line.NEED_TOOL,
			"I need a tool: %1$s.",
			"One %1$s. When you can.",
			"Tool needed: %1$s.");
		put(f, Line.TOOL_BROKE,
			"My %1$s broke.",
			"My %1$s is gone. I'll manage.",
			"Lost my %1$s. I need another.");
		put(f, Line.NEED_MATERIALS,
			"I need %1$s.",
			"Short of %1$s.",
			"Bring %1$s when you can.");
		put(f, Line.DEPOSIT,
			"Supplies stored.",
			"Chest stocked.",
			"Stored in the chest. All accounted for.");
		put(f, Line.SHARE,
			"%1$s. Take %2$s.",
			"Yours, %1$s: %2$s.",
			"Here, %1$s: %2$s. Stay strong.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s.",
			"Done: the %1$s.",
			"The %1$s: complete. Good.");
		put(f, Line.CONTRAPTION_DONE,
			"The %1$s: complete.",
			"Finished the %1$s. Well done.",
			"Done: the %1$s. Good work.");
		put(f, Line.DISCOVERY,
			"Noted: %1$s",
			"Something you should know: %1$s",
			"Report: %1$s");
		put(f, Line.ADVICE,
			"Listen: %1$s",
			"Stay safe: %1$s",
			"A soldier's advice: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s. I'd stand for any of you.",
			"We're %1$s. Good.",
			"%1$s. Stronger together.");
		put(f, Line.CAMP_UP,
			"A %1$s. Worth protecting.",
			"We're a %1$s now. I'll guard it well.",
			"Our home is a %1$s. It will be safe.");
		put(f, Line.IDLE,
			"All quiet.",
			"I'm watching. Rest easy.",
			"Keep your shield ready.",
			"Nothing gets past me.");
		// Everyday needs
		put(f, Line.HUNGRY,
			"Hungry. I'll eat, then back to guard.",
			"Time to eat. Stay alert while I do.",
			"I need food. Back shortly.");
		put(f, Line.ATE,
			"%1$s. Good.",
			"Ate the %1$s. Strong again.",
			"%1$s. Thank you, whoever stocked it.");
		put(f, Line.NO_FOOD,
			"No food in the chest. The team needs supplies.",
			"Our stores are empty. Bring food to the chest, please.");
		put(f, Line.STARVING,
			"Starving. I'm weakening.",
			"Too long without food. I must eat.");
		put(f, Line.SLEEPY,
			"I'll rest now. Stay safe.",
			"Tired. Someone keep watch.",
			"Sleeping. Wake me if there's danger.");
		put(f, Line.RESTED,
			"Rested. Ready to guard.",
			"Slept well. Is everyone safe?",
			"I'm rested. Back on watch.");
		put(f, Line.CHAT,
			"%1$s. All well?",
			"%1$s. How are you holding up?",
			"A word, %1$s. Are you keeping safe?");
		put(f, Line.CHAT_REPLY,
			"All well, %1$s. Thank you.",
			"I'm fine, %1$s. Stay close.",
			"Good to see you, %1$s.");
		put(f, Line.LEISURE,
			"Off duty: %1$s.",
			"Practice keeps me sharp: %1$s.",
			"A quiet moment. Time for %1$s.");
		put(f, Line.COSY,
			"Warm. Good.",
			"A fire. I'll rest a moment.",
			"Shelter and warmth. Enough.");
		put(f, Line.MOOD_LOW,
			"Not at my best. My %1$s need.",
			"Low spirits. Worst need: %1$s.");
		put(f, Line.MOOD_GREAT,
			"All is well. I'm content.",
			"Good company. Good camp. I'm glad.",
			"I feel strong today.");
		put(f, Line.HELPING_OUT,
			"I'll help: %1$s.",
			"Not my duty, but I'll do it: %1$s.",
			"I'll take a turn at %1$s.");
	}

	/** Sage, the strategist: thoughtful and observant; frames things as plans and lessons. */
	private static void sage() {
		FriendId f = FriendId.SAGE;
		put(f, Line.RECRUITED,
			"Sage. A pleasure, %1$s. Shall we plan ahead together?",
			"Welcome, %1$s. Every good camp starts with a good plan.",
			"Thank you, %1$s. I'll watch, think and advise.");
		put(f, Line.FOLLOW,
			"Following, %1$s. I'll observe as we go.",
			"Lead on, %1$s. I'll keep an eye on the bigger picture.",
			"With you, %1$s. Let's be careful and deliberate.");
		put(f, Line.STAY,
			"I'll stay and observe from here.",
			"Holding this position. A good spot to think.",
			"I'll wait. Patience is a strategy too.");
		put(f, Line.WORK,
			"Back to planning, then.",
			"I'll return to my observations.",
			"Understood. The camp needs a plan.");
		put(f, Line.DISMISSED,
			"Very well. Remember: plan first, act second.",
			"I understand. Take what you've learned with you.",
			"Farewell. Light your way and keep food close.");
		put(f, Line.GREETING,
			"Ah, %2$s. I'm %1$s. Something on your mind?",
			"Hello, %2$s. I'm %1$s, and thinking ahead.",
			"%2$s, good timing. I'm %1$s. Need any advice?",
			"Greetings, %2$s. I'm %1$s. All according to plan.");
		put(f, Line.THANKS_FOOD,
			"Thank you. A full stomach is the first rule of survival.",
			"Much appreciated. One should never plan on an empty stomach.",
			"Thank you. Thoughtful of you.");
		put(f, Line.THANKS_GIFT,
			"Thank you for the %1$s. I'll consider how best to use it.",
			"The %1$s? A thoughtful gift.",
			"Every %1$s has its purpose. Thank you.");
		put(f, Line.RETREAT,
			"I'm hurt. The wise move is to retreat.",
			"Falling back. A strategic withdrawal.",
			"Too hurt to continue. Retreating to recover.");
		put(f, Line.RECOVERED,
			"Recovered. Lesson learned: keep more distance.",
			"Better. Let's not repeat that mistake.",
			"I'm well again. Back to the plan.");
		put(f, Line.CREEPER,
			"Creeper. Increase your distance, quickly.",
			"A creeper. The best plan is to be elsewhere.",
			"Creeper nearby. Move away now, think later.");
		put(f, Line.FIGHT,
			"If I must, %1$s.",
			"%1$s. You leave me no choice.",
			"Very well, %1$s. Let's be quick about it.");
		put(f, Line.WARNING,
			"%1$s, take note: %2$s.",
			"%1$s, I've observed something: %2$s.",
			"A word, %1$s: %2$s.");
		put(f, Line.FRIEND_DIED,
			"%1$s... We'll remember you, and learn from this.",
			"We've lost %1$s. Let's honour them by taking care.",
			"Farewell, %1$s. Your work will not be forgotten.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt. Retreat, eat and recover.",
			"%1$s, your health is low. The wise choice is to back away.",
			"%1$s, step back. Heal first, fight later.");
		put(f, Line.NIGHT_RETURN,
			"Dusk. A sensible time to return to camp.",
			"Night is coming. The plan is simple: be home before it.",
			"The light is fading. Let's regroup at camp.");
		put(f, Line.MORNING,
			"Good morning. Let's review today's priorities.",
			"Morning. A new day, a fresh plan.",
			"Dawn. I've been thinking about what we need.");
		put(f, Line.NIGHT_WATCH,
			"I'll keep the watch. A rested camp is a safe camp.",
			"My watch. Sleep; I'll keep my eyes on the dark.",
			"The watch is mine tonight. Someone should always be awake.");
		put(f, Line.ALARM,
			"%1$s in the camp! Everyone up, stand together!",
			"Alarm: %1$s inside the camp. Up, and stay together!",
			"Wake up! %1$s in camp. Together we're stronger.");
		put(f, Line.WORK_START,
			"Next on the plan: %1$s.",
			"I'll be %1$s. It's the sensible choice.",
			"Time for %1$s, I think.");
		put(f, Line.NEED_TOOL,
			"I'll need a tool for this: %1$s.",
			"Any %1$s going spare? The plan requires one.",
			"No %1$s, no progress. Can someone help?");
		put(f, Line.TOOL_BROKE,
			"My %1$s has broken. A reminder to carry spares.",
			"The %1$s is gone. Lesson noted.",
			"There goes my %1$s. We should keep a spare in the chest.");
		put(f, Line.NEED_MATERIALS,
			"We're short of %1$s. Worth making a priority.",
			"To proceed, I need %1$s.",
			"Note for the team: we need %1$s.");
		put(f, Line.DEPOSIT,
			"Supplies stored. The chest is in good order.",
			"I've stocked the chest. Our reserves are growing.",
			"Deposited. A well-stocked chest is a quiet comfort.");
		put(f, Line.SHARE,
			"%1$s, take %2$s. I thought ahead.",
			"For you, %1$s: %2$s. Spend wisely.",
			"I planned for this, %1$s: %2$s, for you.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. One step closer to our goal.",
			"The %1$s: complete. Well planned, well built.",
			"That's the %1$s done. Next, we consider our priorities.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Ingenious, really.",
			"The %1$s: complete. A clever solution.",
			"That's the %1$s done. Good thinking, all round.");
		put(f, Line.DISCOVERY,
			"An observation: %1$s",
			"Worth remembering: %1$s",
			"I've noticed something: %1$s");
		put(f, Line.ADVICE,
			"A thought: %1$s",
			"Consider this: %1$s",
			"If I may: %1$s",
			"From what I've observed: %1$s",
			"Let's think ahead: %1$s");
		put(f, Line.UNITY_UP,
			"We're %1$s now. Trust is the strongest strategy.",
			"%1$s. Together we're far wiser.",
			"%1$s. Exactly as I hoped.");
		put(f, Line.CAMP_UP,
			"A %1$s. The plan is working.",
			"We're a %1$s now. Let's decide what comes next.",
			"Our camp is a %1$s. Each stage teaches us something.");
		put(f, Line.IDLE,
			"The best fight is the one you avoid.",
			"Always know your way home.",
			"Prepare in daylight for what the night may bring.",
			"Hmm. I'm weighing up our food reserves.");
		// Everyday needs
		put(f, Line.HUNGRY,
			"Hunger dulls the mind. I'll eat.",
			"Time for a meal. A clear head needs a full stomach.",
			"I should eat before I grow careless.");
		put(f, Line.ATE,
			"%1$s. Food is the first rule of survival.",
			"Ate the %1$s. My mind clears already.",
			"A sensible meal: %1$s.");
		put(f, Line.NO_FOOD,
			"Our food stores are empty. That must be our first priority.",
			"No food in the chest or my pack. Please stock the larder before anything else.");
		put(f, Line.STARVING,
			"I'm starving. This has become urgent.",
			"Without food I'll weaken quickly. Help, please.");
		put(f, Line.SLEEPY,
			"Tiredness breeds mistakes. Time to sleep.",
			"I'll rest now. Tomorrow, a fresh plan.",
			"Goodnight. Sleep is a strategy too.");
		put(f, Line.RESTED,
			"Rested. My thoughts are clear.",
			"A good night's sleep. I've already planned the day.",
			"Well rested. Shall we begin?");
		put(f, Line.CHAT,
			"%1$s, a moment? I'd value your view.",
			"How are things, %1$s? Tell me what you've noticed.",
			"%1$s. What did you learn today?");
		put(f, Line.CHAT_REPLY,
			"A fair question, %1$s. I'm well, thank you.",
			"Good to talk, %1$s. I've been observing the camp.",
			"I'm well, %1$s. And you?");
		put(f, Line.LEISURE,
			"Time for %1$s. Good for reflection.",
			"A little %1$s. The mind needs rest too.",
			"Some %1$s, I think. Patterns everywhere.");
		put(f, Line.COSY,
			"Warmth and shelter. Simple, and essential.",
			"A fire well kept is a camp well kept.",
			"Comfortable at last. Now I can think.");
		put(f, Line.MOOD_LOW,
			"My spirits are low. The cause: my %1$s need.",
			"I'm not at my best. Worst need: %1$s. Noted.");
		put(f, Line.MOOD_GREAT,
			"Everything is in balance. A rare and good feeling.",
			"I feel well. The plan is working.",
			"Content. This is what we worked for.");
		put(f, Line.HELPING_OUT,
			"This needs doing, so I'll be %1$s.",
			"Not my speciality, but a sensible use of my time: %1$s.",
			"Everyone helps where they can. I'll be %1$s.");
	}

	/** Terra, the landscaper: creative and tidy; paths, greenery and beauty. */
	private static void terra() {
		FriendId f = FriendId.TERRA;
		put(f, Line.RECRUITED,
			"Hello, %1$s! I'm Terra. Let's make this place beautiful.",
			"%1$s, lovely to meet you! I can see paths and flowers everywhere.",
			"Thank you, %1$s. I'll keep the camp tidy and green.");
		put(f, Line.FOLLOW,
			"Following, %1$s. Oh, look at those flowers!",
			"Coming, %1$s. I'll admire the scenery on the way.",
			"Lead on, %1$s. Such pretty country around here.");
		put(f, Line.STAY,
			"I'll stay here and enjoy the view.",
			"Staying put. This spot needs a flower or two.",
			"Holding here. I'll imagine a garden while I wait.");
		put(f, Line.WORK,
			"Back to the gardens!",
			"Time to tidy up. Back to work.",
			"Back to making the camp lovely.");
		put(f, Line.DISMISSED,
			"Oh. Please keep the paths clear for me.",
			"Goodbye. Water the flowers now and then.",
			"Very well. Keep it tidy, won't you?");
		put(f, Line.GREETING,
			"Hello, %2$s! I'm %1$s. Isn't it lovely here?",
			"Hi, %2$s. I'm %1$s. Camp's looking tidier, don't you think?",
			"%2$s! I'm %1$s. Mind where you step!");
		put(f, Line.THANKS_FOOD,
			"Thank you! Beautifully timed.",
			"How kind! That hits the spot.",
			"Thank you. Gardening is hungry work.");
		put(f, Line.THANKS_GIFT,
			"The %1$s! I know just where it'll look nicest.",
			"Thank you for the %1$s. How thoughtful!",
			"Ooh, the %1$s. That'll fit right in.");
		put(f, Line.RETREAT,
			"Ouch! I need to step back.",
			"I'm hurt. Retreating somewhere safer.",
			"Too rough out here. Falling back.");
		put(f, Line.RECOVERED,
			"That's better. Fresh as a daisy.",
			"All healed. Back to the gardens.",
			"Feeling lovely again. Thank you for your patience.");
		put(f, Line.CREEPER,
			"Creeper! Don't let it blow a hole in my garden!",
			"Creeper close by! Everyone back!",
			"A creeper! Away from the paths, quickly!");
		put(f, Line.FIGHT,
			"Off my flowerbeds, %1$s!",
			"Shoo, %1$s! You're making a mess!",
			"Not in my garden, %1$s!");
		put(f, Line.WARNING,
			"%1$s, do be careful: %2$s!",
			"Oh, %1$s, watch out: %2$s!",
			"%1$s, mind yourself: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll plant a garden in your memory.",
			"Goodbye, %1$s. Every flower here is for you.",
			"We've lost %1$s. I'll keep their favourite spot beautiful.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! Please rest and heal.",
			"Oh, %1$s, you're hurt! Come back to camp.",
			"%1$s, step away and eat something, please!");
		put(f, Line.NIGHT_RETURN,
			"The sun's setting. So pretty. Back to camp, though.",
			"Getting dark. I'll head home and admire the torchlight.",
			"Evening already. Home we go.");
		put(f, Line.MORNING,
			"Good morning! The dew on the grass is lovely.",
			"Morning! Let's make camp prettier today.",
			"Good morning! Everything looks so fresh.");
		put(f, Line.NIGHT_WATCH,
			"I'll keep watch. The camp looks lovely by firelight.",
			"My watch tonight. Sleep well, I'll mind the garden.",
			"I'm on watch. Nothing's trampling my paths tonight.");
		put(f, Line.ALARM,
			"%1$s in the camp! Everyone up, quickly!",
			"Wake up! A %1$s is trampling through the camp!",
			"Alarm! %1$s by the paths! Up, everyone!");
		put(f, Line.WORK_START,
			"I'll be %1$s. It'll look lovely when I'm done.",
			"Time for %1$s. Neat and tidy.",
			"Off I go, %1$s. A little beauty every day.");
		put(f, Line.NEED_TOOL,
			"Any %1$s going spare? I've so much to tidy.",
			"Tool needed: %1$s. The paths are waiting.",
			"I need a tool: %1$s. Can anyone help?");
		put(f, Line.TOOL_BROKE,
			"Oh, my poor %1$s has broken!",
			"There goes my %1$s. Such a shame.",
			"My %1$s is worn out. I'll need another.");
		put(f, Line.NEED_MATERIALS,
			"I need %1$s to finish this properly.",
			"Short of %1$s. Could anyone help?",
			"If anyone has %1$s, the camp will thank you.");
		put(f, Line.DEPOSIT,
			"All tidied into the chest.",
			"The chest's stocked and neatly sorted.",
			"Everything put away. A tidy camp is a happy camp.");
		put(f, Line.SHARE,
			"Here, %1$s: %2$s. Enjoy!",
			"%1$s, I brought you %2$s.",
			"For you, %1$s: %2$s. Shared with a smile!");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Isn't that pretty?",
			"That's the %1$s done. Neat as a pin.",
			"The %1$s: finished. I could look at it all day.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Clever, and quite pretty too!",
			"The %1$s: done! It even looks tidy.",
			"That's the %1$s finished. Lovely work.");
		put(f, Line.DISCOVERY,
			"Oh, how lovely: %1$s",
			"Look at this: %1$s",
			"Worth a look: %1$s");
		put(f, Line.ADVICE,
			"A friendly tip: %1$s",
			"Gentle reminder: %1$s",
			"Do remember: %1$s");
		put(f, Line.UNITY_UP,
			"We're %1$s! Like a garden, we grew.",
			"%1$s! How lovely.",
			"%1$s now. My heart's blooming.");
		put(f, Line.CAMP_UP,
			"A %1$s! It needs more flowers, of course.",
			"We're a %1$s now! Time to plan some new paths.",
			"Our home's a %1$s. And so pretty!");
		put(f, Line.IDLE,
			"A path here, a flower there. Perfect.",
			"Tidy camp, tidy mind.",
			"I think a little greenery would brighten that wall.",
			"Every camp deserves a garden.");
		// Everyday needs
		put(f, Line.HUNGRY,
			"I'm hungry. A quick bite, then back to the gardens.",
			"Time for a snack. Gardening is hungry work.",
			"Ooh, I'm peckish. Food first.");
		put(f, Line.ATE,
			"%1$s. Delicious, and no crumbs on the path.",
			"Mm, %1$s. Lovely.",
			"Ate the %1$s. Neatly, of course.");
		put(f, Line.NO_FOOD,
			"There's no food in the chest. Could someone fill it, please?",
			"No food in my pack or the chest. Oh dear.");
		put(f, Line.STARVING,
			"I'm starving! I really need to eat.",
			"So hungry! Please, any food at all.");
		put(f, Line.SLEEPY,
			"I'm tired. Off to bed. Goodnight!",
			"Bedtime. I'll dream of gardens.",
			"Goodnight, everyone. Sleep tight.");
		put(f, Line.RESTED,
			"Slept beautifully. Let's make the camp lovely.",
			"Good morning! Rested and full of ideas.",
			"Fresh as a daisy after that sleep.");
		put(f, Line.CHAT,
			"%1$s! Have you seen the new flowerbed?",
			"Hello, %1$s! What do you think of the paths?",
			"%1$s, come and chat. Isn't it pretty today?");
		put(f, Line.CHAT_REPLY,
			"Oh, hello, %1$s! I'm well, thank you.",
			"Lovely to see you, %1$s.",
			"I'm fine, %1$s. Mind the flowers!");
		put(f, Line.LEISURE,
			"Time for %1$s. Pure joy.",
			"Just %1$s for a while.",
			"Break time: %1$s. Beauty is good for the soul.");
		put(f, Line.COSY,
			"Ah, cosy. Everything's just right.",
			"Lovely and warm in here.",
			"A warm fire and a tidy camp. Perfect.");
		put(f, Line.MOOD_LOW,
			"I'm feeling a bit low. My %1$s need is in a muddle.",
			"Not my best day. Worst need: %1$s.");
		put(f, Line.MOOD_GREAT,
			"I feel wonderful! The camp looks beautiful.",
			"What a lovely day. Everything's in its place.",
			"So happy! Even the paths look cheerful.");
		put(f, Line.HELPING_OUT,
			"I'll help with %1$s. Neatly, of course.",
			"A change of scene! I'll be %1$s.",
			"Not my usual job, but I'll make it tidy: %1$s.");
	}

	/** Rowan, the forager: resourceful and generous; shares everything and gathers wood, stone and dirt for Oak. */
	private static void rowan() {
		FriendId f = FriendId.ROWAN;
		put(f, Line.RECRUITED,
			"Rowan's the name. I'll gather whatever we need, %1$s.",
			"Thanks, %1$s! Wood, stone, berries: you name it, I'll find it.",
			"Glad to help, %1$s. What's mine is the camp's.");
		put(f, Line.FOLLOW,
			"Coming, %1$s. I'll pick up anything useful on the way.",
			"Right behind you, %1$s. Need anything? Just ask.",
			"Following, %1$s. My pockets are open for sharing.");
		put(f, Line.STAY,
			"Staying here. Shout if you need anything.",
			"Holding here. I've plenty to share if anyone's short.",
			"Staying. I'll sort my pockets while I wait.");
		put(f, Line.WORK,
			"Back to gathering. Oak will want more wood.",
			"Off to forage. I'll bring back plenty.",
			"Back to work. There's always something to collect.");
		put(f, Line.DISMISSED,
			"Fair enough. Take what's left in my pockets.",
			"Alright. Share what you have, won't you?",
			"Goodbye. Plant a sapling for every tree you fell.");
		put(f, Line.GREETING,
			"Hi, %2$s! I'm %1$s. Need anything? I've got spares.",
			"Hello, %2$s. I'm %1$s. Plenty to go round.",
			"%2$s! I'm %1$s. Shout if you're short of anything.");
		put(f, Line.THANKS_FOOD,
			"Thank you! I'll share my next batch of berries with you.",
			"Cheers! I'll pass the favour on.",
			"Thanks. Kindness always comes back around.");
		put(f, Line.THANKS_GIFT,
			"Thanks for the %1$s! I'll make sure it goes to good use.",
			"The %1$s? I know just who needs this.",
			"Every %1$s helps. Thank you!");
		put(f, Line.RETREAT,
			"I'm hurt. Backing off for a bit.",
			"Too much! Falling back.",
			"Ouch. Retreating to patch myself up.");
		put(f, Line.RECOVERED,
			"Right as rain. Back to gathering.",
			"Much better. Now, where were those trees?",
			"Healed up. Let's get back to it.");
		put(f, Line.CREEPER,
			"Creeper! Drop what you're doing and run!",
			"Creeper nearby! Clear off, quick!",
			"Creeper! I'm not losing my woodpile to that!");
		put(f, Line.FIGHT,
			"Clear off, %1$s!",
			"I'll deal with this %1$s.",
			"Not today, %1$s!");
		put(f, Line.WARNING,
			"%1$s, watch out: %2$s!",
			"%1$s, careful now: %2$s!",
			"Heads up, %1$s: %2$s!");
		put(f, Line.FRIEND_DIED,
			"%1$s... I'll plant a sapling for you.",
			"We've lost %1$s. Let's look after each other.",
			"Goodbye, %1$s. You always gave so much.");
		put(f, Line.PLAYER_HURT,
			"%1$s, you're badly hurt! I've got food if you need it.",
			"%1$s! Back off and eat something, quick!",
			"%1$s, you need healing. Take a break, please.");
		put(f, Line.NIGHT_RETURN,
			"Light's going. Heading back with what I've got.",
			"Getting dark. Back to camp to share out the haul.",
			"Dusk. Time to head home.");
		put(f, Line.MORNING,
			"Morning! The trees are waiting.",
			"Good morning. Who needs what today?",
			"Morning! I'll fetch whatever the camp's short of.");
		put(f, Line.NIGHT_WATCH,
			"I'll keep watch. You lot get some sleep.",
			"Watch is mine. Rest up, I'll wake you if anything comes.",
			"I'll sit up by the fire. Sleep tight, all.");
		put(f, Line.ALARM,
			"%1$s in the camp! Up, everyone, grab your tools!",
			"Wake up! %1$s coming in. Let's see it off together!",
			"Alarm! %1$s at the camp! On your feet!");
		put(f, Line.WORK_START,
			"I'll be %1$s. Back soon with plenty.",
			"Off I go, %1$s.",
			"Time for %1$s. Oak will be pleased.");
		put(f, Line.NEED_TOOL,
			"Any %1$s going spare? Can't gather without one.",
			"Tool needed: %1$s. Anyone?",
			"I need a tool: %1$s. Then I'll bring back loads.");
		put(f, Line.TOOL_BROKE,
			"My %1$s broke. Well, it did a lot of work.",
			"There goes my %1$s. I'll find another.",
			"Snap! My %1$s is done for.");
		put(f, Line.NEED_MATERIALS,
			"I'm short of %1$s. I'll keep looking.",
			"Need %1$s. Can anyone help?",
			"Can't carry on without %1$s.");
		put(f, Line.DEPOSIT,
			"The chest's stocked. Help yourselves, everyone.",
			"Dropped the haul in the chest. Plenty to share.",
			"Wood and stone in the chest. Take what you need.");
		put(f, Line.SHARE,
			"Here you go, %1$s: %2$s.",
			"%1$s, I brought you %2$s. Plenty more where that came from.",
			"For you, %1$s: %2$s. Share and share alike.",
			"%1$s! Got %2$s for you.");
		put(f, Line.BUILD_DONE,
			"Finished the %1$s. Good teamwork.",
			"That's the %1$s done. Nice work.",
			"The %1$s: finished. Glad I could chip in.");
		put(f, Line.CONTRAPTION_DONE,
			"Finished the %1$s. Clever stuff!",
			"The %1$s: done. Glad I could help.",
			"That's the %1$s working. Brilliant.");
		put(f, Line.DISCOVERY,
			"Good find: %1$s",
			"Worth sharing: %1$s",
			"Have a look: %1$s");
		put(f, Line.ADVICE,
			"Forager's tip: %1$s",
			"Here's a tip: %1$s",
			"Pass it on: %1$s");
		put(f, Line.UNITY_UP,
			"%1$s! What's mine is yours, always.",
			"We're %1$s now. Feels good to share it all.",
			"%1$s! Best team I've ever gathered for.");
		put(f, Line.CAMP_UP,
			"A %1$s! I'll need more wood. Lots more.",
			"We're a %1$s now. All that gathering paid off.",
			"Our camp's a %1$s! I'll keep the supplies coming.");
		put(f, Line.IDLE,
			"Plant a sapling for every tree you fell.",
			"Berries, sticks, stones. It all adds up.",
			"Anyone short of anything? I've got spares.",
			"A full chest is good. A shared one's better.");
		// Everyday needs
		put(f, Line.HUNGRY,
			"I'm hungry. Better eat before I give all my food away.",
			"Time for a bite. Back to gathering after.",
			"Stomach's rumbling. Food time.");
		put(f, Line.ATE,
			"%1$s. Good, but better shared.",
			"Mm, %1$s. Thanks to whoever gathered it.",
			"Ate the %1$s. Ready to gather more.");
		put(f, Line.NO_FOOD,
			"No food left in the chest, and I've given mine away. Could someone bring some?",
			"Food stores are empty. Let's fill that chest, everyone.");
		put(f, Line.STARVING,
			"I'm starving. I need food, quickly.",
			"Too hungry to keep going. Food, please.");
		put(f, Line.SLEEPY,
			"I'm worn out. Goodnight, all.",
			"Time to sleep. Busy day tomorrow.",
			"Bed for me. Wake me if anyone needs anything.");
		put(f, Line.RESTED,
			"Slept well. Who needs what today?",
			"Rested and ready to gather.",
			"Good sleep. Now, breakfast for everyone?");
		put(f, Line.CHAT,
			"%1$s! Need anything? I've got spares.",
			"Hey, %1$s. How are you doing?",
			"%1$s, how's it going? Short of anything?");
		put(f, Line.CHAT_REPLY,
			"Doing well, %1$s. Need anything?",
			"Good, thanks, %1$s. Pockets full, heart full.",
			"Can't complain, %1$s. Fancy a berry?");
		put(f, Line.LEISURE,
			"Time for %1$s. Free, and plenty to go round.",
			"A little %1$s. The best things in life are free.",
			"Break time: %1$s.");
		put(f, Line.COSY,
			"Warm and snug. Room for one more by the fire.",
			"Lovely and warm. Anyone want to join me?",
			"Cosy. Pull up a log, everyone.");
		put(f, Line.MOOD_LOW,
			"Feeling low. Worst need: %1$s.",
			"Not great. My %1$s need could use some help.");
		put(f, Line.MOOD_GREAT,
			"Feeling great. Good friends, full chest.",
			"What a day! Plenty for everyone.",
			"I'm so happy. This camp's the best thing I ever found.");
		put(f, Line.HELPING_OUT,
			"Happy to help: %1$s.",
			"I'll pitch in with %1$s.",
			"Lending a hand: %1$s. That's what friends do.");
	}

	/**
	 * Keeping livestock: bringing an animal to the pen (%1$s = "cow"), breeding (%1$s = "pigs"), butchering
	 * (%1$s = "sheep"), cooking (%1$s = "beef") and hunting (%1$s = "rabbit"). Every friend can do this work, so every
	 * friend has their own wording, kept together here.
	 */
	private static void livestock() {
		generic(Line.LEADING_ANIMAL, "Come along, %1$s. This way.", "Bringing a %1$s home to the pen.");
		generic(Line.BRED_ANIMALS, "The %1$s are fed. Young ones soon.", "A little food, and the %1$s are in love.");
		generic(Line.BUTCHERING, "One %1$s for the stores.", "We need the meat. Sorry, %1$s.");
		generic(Line.COOKING, "Cooking %1$s on the fire.", "Some %1$s for supper.");
		generic(Line.HUNTING, "Off to hunt a %1$s.", "Hunting a %1$s for the camp.");

		FriendId f = FriendId.FERN;
		put(f, Line.LEADING_ANIMAL,
			"Come along, little %1$s. There's a nice safe pen waiting.",
			"This way, %1$s. I've got something tasty for you.",
			"Easy now, %1$s. Home we go.");
		put(f, Line.BRED_ANIMALS,
			"A little treat for the %1$s. We'll have young ones soon.",
			"The %1$s look very happy together.",
			"There we go. The %1$s will make a little family.");
		put(f, Line.BUTCHERING,
			"Sorry, little %1$s. The camp needs feeding.",
			"It's never easy, but we need the meat. Thank you, %1$s.",
			"One %1$s for the larder. I'll make it quick.");
		put(f, Line.COOKING,
			"A bit of %1$s on the fire. Supper's coming!",
			"Cooking some %1$s. It'll be lovely and filling.",
			"Nothing like hot %1$s to keep everyone going.");
		put(f, Line.HUNTING,
			"I'll fetch us a %1$s. We need more food.",
			"Off to find a %1$s. Quick and careful.",
			"The stores are low. I'll hunt a %1$s.");

		f = FriendId.OAK;
		put(f, Line.LEADING_ANIMAL,
			"Bringing a %1$s to the pen. Steady does it.",
			"One %1$s, heading for the pen.",
			"Come on, %1$s. The pen's this way.");
		put(f, Line.BRED_ANIMALS,
			"Fed the %1$s. That's next season's stock.",
			"The %1$s are paired up. Good planning.",
			"Two %1$s fed. The herd will grow.");
		put(f, Line.BUTCHERING,
			"Culling a %1$s. The pen's over capacity.",
			"One %1$s for the stores. It's practical.",
			"We keep the breeders. This %1$s feeds us.");
		put(f, Line.COOKING,
			"Putting some %1$s on the fire.",
			"Cooking %1$s. Good fuel for good work.",
			"Some %1$s on the fire, then back to it.");
		put(f, Line.HUNTING,
			"Hunting a %1$s. The stores need topping up.",
			"A %1$s should keep us going. Back soon.",
			"Off after a %1$s. Short trip.");

		f = FriendId.FLINT;
		put(f, Line.LEADING_ANIMAL,
			"Come on, %1$s. Don't make this awkward.",
			"Escorting a %1$s. It's not a creeper, at least.",
			"Walk, %1$s. Slowly. Like me in a cave.");
		put(f, Line.BRED_ANIMALS,
			"Fed the %1$s. Romance, apparently.",
			"The %1$s are in love. Good for them.",
			"Two %1$s, one snack each. Nature does the rest.");
		put(f, Line.BUTCHERING,
			"One %1$s for the larder. Nothing personal.",
			"Sorry, %1$s. Hunger's the more dangerous mob.",
			"Butchering a %1$s. Grim, but so is starving.");
		put(f, Line.COOKING,
			"Cooking %1$s. Safer than mining.",
			"Some %1$s on the fire. Mind the sparks.",
			"Grilling %1$s. Fire I can trust, for once.");
		put(f, Line.HUNTING,
			"Hunting a %1$s. In daylight, like a sensible person.",
			"Off after a %1$s. Back before dark.",
			"A %1$s, then straight home. No caves.");

		f = FriendId.SCOUT;
		put(f, Line.LEADING_ANIMAL,
			"Found a %1$s! Bringing it home!",
			"Follow me, %1$s! Big adventure to the pen!",
			"This %1$s is coming with me!");
		put(f, Line.BRED_ANIMALS,
			"The %1$s are in love! Babies soon!",
			"Fed the %1$s! The herd's growing!",
			"Look at the %1$s! Hearts everywhere!");
		put(f, Line.BUTCHERING,
			"Butchering a %1$s for the stores. Quick and clean.",
			"Sorry, %1$s! We need the food.",
			"One %1$s for supper. That's camp life!");
		put(f, Line.COOKING,
			"Cooking %1$s over the fire! Smells amazing!",
			"Some %1$s on the fire. Explorer's supper!",
			"Roasting %1$s! Best meal out here!");
		put(f, Line.HUNTING,
			"Spotted a %1$s out there! I'll bring it back!",
			"Hunting a %1$s! Back before sundown!",
			"Tracking a %1$s for the camp!");

		f = FriendId.SPARK;
		put(f, Line.LEADING_ANIMAL,
			"Follow the snack, %1$s! Science!",
			"Animal transport, manual edition! Come on, %1$s!",
			"Pen delivery: one %1$s! No redstone needed!");
		put(f, Line.BRED_ANIMALS,
			"Fed the %1$s! Biological automation!",
			"The %1$s are in love! Breeding farm online!",
			"Two %1$s fed! Output: one baby, eventually!");
		put(f, Line.BUTCHERING,
			"Butchering a %1$s! Not my favourite process.",
			"One %1$s for the food supply. Sorry, little one!",
			"Harvesting a %1$s. A very manual harvest!");
		put(f, Line.COOKING,
			"Cooking %1$s! Heat plus meat equals dinner!",
			"Some %1$s on the fire! No hopper needed!",
			"Campfire online! Cooking %1$s!");
		put(f, Line.HUNTING,
			"Hunting a %1$s! Field research!",
			"Off after a %1$s! Sword calibrated!",
			"A %1$s for the larder! Back soon!");

		f = FriendId.AEGIS;
		put(f, Line.LEADING_ANIMAL,
			"Easy, %1$s. You're safe with me.",
			"I'll see this %1$s to the pen.",
			"Walk on, %1$s. I'll watch your back.");
		put(f, Line.BRED_ANIMALS,
			"The %1$s are fed. The pen grows.",
			"Fed the %1$s. More to protect.",
			"The %1$s will breed. Good.");
		put(f, Line.BUTCHERING,
			"One %1$s for the stores. Swift and clean.",
			"A quick end, %1$s. The camp must eat.",
			"Butchering a %1$s. No suffering.");
		put(f, Line.COOKING,
			"Cooking %1$s. Soldiers eat well.",
			"Some %1$s on the fire. Strength for tomorrow.",
			"The fire's hot. Cooking %1$s.");
		put(f, Line.HUNTING,
			"Hunting a %1$s. Back before dusk.",
			"I'll bring back a %1$s.",
			"A %1$s for the camp. Stay alert while I'm out.");

		f = FriendId.SAGE;
		put(f, Line.LEADING_ANIMAL,
			"A %1$s in the pen is worth two in the wild.",
			"Bringing a %1$s home. Patience is the best lead.",
			"This %1$s will be the start of a herd.");
		put(f, Line.BRED_ANIMALS,
			"The %1$s are fed. A small investment, a lasting return.",
			"Fed the %1$s. Breeding beats hunting in the long run.",
			"The %1$s will multiply. Planning pays.");
		put(f, Line.BUTCHERING,
			"We keep the breeders and eat the surplus. One %1$s.",
			"A %1$s for the larder. The herd stays balanced.",
			"Culling one %1$s. A steady pen is a full larder.");
		put(f, Line.COOKING,
			"Cooked %1$s fills far more than raw. Onto the fire.",
			"Cooking %1$s. Fire triples its worth.",
			"Some %1$s on the fire. A wise use of a raw resource.");
		put(f, Line.HUNTING,
			"The stores are thin. I'll hunt a %1$s by daylight.",
			"Hunting a %1$s. Never the last of a kind.",
			"A %1$s, taken with care. Back before dusk.");

		f = FriendId.TERRA;
		put(f, Line.LEADING_ANIMAL,
			"Come and see your lovely new pen, %1$s.",
			"This way, %1$s. Mind the flowers.",
			"A %1$s will look perfect in the pen.");
		put(f, Line.BRED_ANIMALS,
			"The %1$s are so sweet together.",
			"Fed the %1$s. The pen will be lively soon.",
			"The %1$s are in love. How lovely!");
		put(f, Line.BUTCHERING,
			"One %1$s for the stores. I'll tidy up after.",
			"Sorry, %1$s. I'll keep it quick and neat.",
			"Butchering a %1$s. Not the pretty part of farming.");
		put(f, Line.COOKING,
			"Cooking %1$s. The fire looks so cosy.",
			"Some %1$s on the campfire. Smells wonderful.",
			"Cooking %1$s, laid out neatly on the fire.");
		put(f, Line.HUNTING,
			"Hunting a %1$s. I'll leave the meadow as I found it.",
			"Off after a %1$s. Back before the light goes.",
			"The stores need a %1$s. I'll be careful.");

		f = FriendId.ROWAN;
		put(f, Line.LEADING_ANIMAL,
			"Found a %1$s for the pen. Plenty to share later!",
			"Come on, %1$s. Good food where we're going.",
			"Bringing a %1$s home. Every bit helps.");
		put(f, Line.BRED_ANIMALS,
			"Fed the %1$s. More to go round soon.",
			"The %1$s are happy. Plenty for everyone later.",
			"A bit of food for the %1$s. The herd will grow.");
		put(f, Line.BUTCHERING,
			"One %1$s, and everybody eats tonight.",
			"Butchering a %1$s. Nothing goes to waste.",
			"Sorry, %1$s. You'll feed the whole camp.");
		put(f, Line.COOKING,
			"Cooking %1$s. Plenty for everyone!",
			"Some %1$s on the fire. Come and share!",
			"Cooking %1$s for the chest. Help yourselves.");
		put(f, Line.HUNTING,
			"Hunting a %1$s. I'll share it round.",
			"Off after a %1$s for the stores.",
			"A %1$s should feed a few of us. Back soon.");
	}
}

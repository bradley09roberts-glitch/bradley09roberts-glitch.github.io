package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Lines;
import io.github.bradley09roberts.hardcorefriends.companion.MoodPassives;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Mood;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Mood and its displays: every friend's own voice for the everyday needs, the needs command (no cheats), mood showing
 * in what friends say, starving that hurts but never kills on its own, a great team mood growing Unity within its
 * daily cap, and hand-feeding a hungry friend.
 */
public class MoodGameTest {
	/** The everyday situations every friend words in their own voice. */
	private static final List<Line> NEEDS_LINES = List.of(Line.HUNGRY, Line.ATE, Line.NO_FOOD, Line.STARVING,
		Line.SLEEPY, Line.RESTED, Line.CHAT, Line.CHAT_REPLY, Line.LEISURE, Line.COSY, Line.MOOD_LOW, Line.MOOD_GREAT,
		Line.HELPING_OUT);

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_101", maxTicks = 20)
	public void everyFriendWordsTheirNeedsInTheirOwnVoice(GameTestHelper helper) {
		List<String> problems = Lines.problems();
		helper.assertTrue(problems.isEmpty(), problems.size() + " dialogue problem(s), first: "
			+ String.join(" | ", problems.subList(0, Math.min(5, problems.size()))));
		for (Line line : NEEDS_LINES) {
			List<String> generic = List.of(Lines.get(null, line));
			Map<String, FriendId> usedBy = new HashMap<>();
			for (FriendId id : FriendId.values()) {
				helper.assertTrue(Lines.hasOwn(id, line), id.displayName() + " has no wording of their own for " + line);
				String[] own = Lines.get(id, line);
				helper.assertTrue(own.length >= 2 && own.length <= 4,
					id.displayName() + " has " + own.length + " variants for " + line + ", not 2 to 4");
				for (String variant : own) {
					helper.assertFalse(generic.contains(variant),
						id.displayName() + " borrows the generic wording for " + line + ": " + variant);
					FriendId other = usedBy.putIfAbsent(variant, id);
					helper.assertTrue(other == null, id.displayName() + " and " + other + " both say \"" + variant + "\" for " + line);
				}
			}
		}
		// The worst need arrives as a single word; every friend's wording takes each of the five.
		for (FriendId id : FriendId.values()) {
			for (String template : Lines.get(id, Line.MOOD_LOW)) {
				helper.assertTrue(template.contains("%1$s need") || template.contains("Worst need: %1$s"),
					id.displayName() + "'s low-mood line does not read with a need word such as \"social\": " + template);
			}
		}
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_102", maxTicks = 40)
	public void needsCommandWorksWithoutCheats(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre().east(3));
		fern.needs().set(Need.HUNGER, 20); // her lowest need, and there is no food anywhere
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		List<String> out = new ArrayList<>();
		CommandSourceStack source = player.createCommandSourceStack().withSource(collector(out))
			.withPermission(LevelBasedPermissionSet.ALL);
		helper.assertFalse(source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "the test player must not be an operator");
		Commands commands = helper.getLevel().getServer().getCommands();

		commands.performPrefixedCommand(source, "friends needs");
		String all = String.join("\n", out);
		for (Need need : Need.values()) {
			long lines = out.stream().filter(s -> s.contains(" " + need.title() + " ")).count();
			helper.assertTrue(lines == 2, "both friends report " + need.title() + " (" + lines + " lines):\n" + all);
		}
		helper.assertTrue(all.contains(Needs.bar(20) + " Hunger 20"), "Fern's hunger shows as a bar with its number:\n" + all);
		helper.assertTrue(all.contains("[Fern] mood " + fern.needs().mood().word() + " - " + fern.activity()),
			"Fern's heading gives her mood and what she is doing:\n" + all);
		helper.assertTrue(all.contains("lowest: no food in the backpack or the supply chest"),
			"Fern's lowest need says there is no food to be had:\n" + all);

		out.clear();
		commands.performPrefixedCommand(source, "friends needs oak");
		all = String.join("\n", out);
		helper.assertTrue(all.contains("[Oak]") && !all.contains("[Fern]") && out.size() == 7,
			"one friend's report is a title, a heading and five needs:\n" + all);

		out.clear();
		commands.performPrefixedCommand(source, "friends needs scout");
		helper.assertTrue(String.join("\n", out).contains("not on your team"), "asking after a friend not recruited explains why: " + out);

		out.clear();
		commands.performPrefixedCommand(source, "friends list");
		helper.assertTrue(out.stream().anyMatch(s -> s.startsWith("[Fern]") && s.contains("mood " + fern.needs().mood().word())),
			"the list shows Fern's mood: " + out);

		out.clear();
		commands.performPrefixedCommand(source, "friends help");
		helper.assertTrue(String.join("\n", out).contains("/friends needs"), "help mentions the needs command: " + out);
		helper.assertTrue(fern.statusLine().getString().contains(" - mood " + fern.needs().mood().word()),
			"right-click status shows the mood: " + fern.statusLine().getString());
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_103", maxTicks = 1000)
	public void starvingHurtsDownToOneHeartButNeverKills(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		flint.needs().set(Need.HUNGER, 0);
		flint.setHealth(7.0F);
		// One check per tick, which also ends the test: succeed() discards the test's entities at once, so a separate
		// end-of-test runnable on the same tick could run first and leave this check looking at a discarded Flint.
		helper.onEachTick(() -> {
			helper.assertTrue(flint.isAlive() && data.ledger(FriendId.FLINT).state == CampData.LifeState.ALIVE,
				"Flint died of hunger alone while " + flint.activity());
			helper.assertTrue(flint.getHealth() >= 2.0F, "starving took Flint below one heart: " + flint.getHealth());
			if (helper.getTick() >= 800) {
				helper.assertTrue(flint.needs().get(Need.HUNGER) <= 0, "still starving with nothing to eat, hunger "
					+ flint.needs().get(Need.HUNGER) + ", " + flint.activity());
				helper.assertValueEqual(flint.getHealth(), 2.0F, "starving hurts down to exactly one heart");
				helper.assertTrue(flint.speechMemory().containsKey(Line.STARVING), "Flint said he is starving, while " + flint.activity());
				helper.succeed();
			}
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_104", maxTicks = 20)
	public void greatTeamMoodGrowsUnityWithinADailyCap(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		MinecraftServer server = helper.getLevel().getServer();
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, TestSupport.centre().east(2));
		data.setUnity(50);

		setAll(fern, 10);
		setAll(sage, 10);
		helper.assertValueEqual(Unity.teamSpirit(server), 0, "a miserable team adds no Unity");
		helper.assertValueEqual(data.unity(), 50, "and a miserable team costs none either");
		setAll(fern, 70);
		setAll(sage, 70);
		helper.assertValueEqual(Unity.teamSpirit(server), 0, "a merely good team mood adds nothing");
		setAll(fern, 100);
		setAll(sage, 40);
		helper.assertValueEqual(Unity.teamSpirit(server), 0, "one delighted friend does not make a great team mood");

		setAll(sage, 100);
		helper.assertValueEqual(MoodPassives.teamMood(List.of(fern, sage)), Mood.GREAT, "everyone content is a great team mood");
		int gained = 0;
		for (int hour = 0; hour < 24; hour++) {
			gained += Unity.teamSpirit(server);
		}
		helper.assertValueEqual(gained, Unity.SPIRIT_DAILY_CAP, "a whole great day adds the daily cap and no more");
		helper.assertValueEqual(data.unity(), 50 + Unity.SPIRIT_DAILY_CAP, "the bond grew by the daily cap");
		TestSupport.setTime(helper, 1000 + 24000);
		helper.assertValueEqual(Unity.teamSpirit(server), 1, "the next day brings a fresh allowance");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_105", maxTicks = 800)
	public void moodShowsInWhatFriendsSay(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, TestSupport.centre());
		terra.needs().set(Need.HUNGER, 60);
		terra.needs().set(Need.ENERGY, 40);
		terra.needs().set(Need.SOCIAL, 30);
		terra.needs().set(Need.FUN, 5);
		terra.needs().set(Need.COMFORT, 30);
		helper.assertValueEqual(terra.needs().mood(), Mood.LOW, "Terra's mood");
		helper.assertValueEqual(MoodPassives.voice(terra), Line.MOOD_LOW, "a low mood is voiced");
		helper.assertTrue(terra.speechMemory().containsKey(Line.MOOD_LOW), "Terra said she feels low");
		helper.assertValueEqual(MoodPassives.word(terra.needs().lowest()), "fun", "her worst need, as the line names it");
		helper.assertTrue(MoodPassives.moodText(terra).equals("low (worst: fun 5)"), "status text: " + MoodPassives.moodText(terra));

		CompanionEntity spark = TestSupport.spawnFriend(helper, FriendId.SPARK, TestSupport.centre().east(3));
		setAll(spark, 100);
		helper.assertValueEqual(MoodPassives.voice(spark), Line.MOOD_GREAT, "a great mood is voiced");

		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, TestSupport.centre().west(3));
		setAll(sage, 10);
		sage.setAsleep(true);
		helper.assertTrue(MoodPassives.voice(sage) == null && !sage.speechMemory().containsKey(Line.MOOD_LOW),
			"a sleeping friend says nothing about their mood");
		sage.setAsleep(false);

		// The passive speaks up by itself: Rowan, miserable and alone, holds position so no job steps in first.
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(3, TestSupport.STAND_Y, 3));
		setAll(rowan, 15);
		rowan.setMode(CompanionMode.STAY, null);
		helper.succeedWhen(() -> helper.assertTrue(rowan.speechMemory().containsKey(Line.MOOD_LOW),
			"Rowan has not voiced her low mood; she is " + rowan.activity() + ", mood " + MoodPassives.moodText(rowan)));
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_106", maxTicks = 20)
	public void hungryFriendsEatFoodHandedToThem(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre());
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		oak.needs().set(Need.HUNGER, 30);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 2));
		oak.interact(player, InteractionHand.MAIN_HAND, oak.position());
		double fed = 30 + CompanionEntity.hungerValue(new ItemStack(Items.BREAD));
		helper.assertTrue(Math.abs(oak.needs().get(Need.HUNGER) - fed) < 0.01, "a hungry Oak eats the bread at once, hunger "
			+ oak.needs().get(Need.HUNGER));
		helper.assertTrue(player.getMainHandItem().getCount() == 1 && oak.backpack().count(Items.BREAD) == 0,
			"one loaf eaten, none stored");
		oak.needs().set(Need.HUNGER, 95);
		oak.interact(player, InteractionHand.MAIN_HAND, oak.position());
		helper.assertTrue(oak.backpack().count(Items.BREAD) == 1, "a well-fed Oak keeps the bread for later");
		helper.succeed();
	}

	private static void setAll(CompanionEntity c, double value) {
		for (Need need : Need.values()) {
			c.needs().set(need, value);
		}
	}

	/** A command source that keeps every message it is sent, as plain text. */
	private static CommandSource collector(List<String> out) {
		return new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				out.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
	}
}

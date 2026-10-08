package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.farm.Crops;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Friends looking after their own needs: eating real food, sleeping at night, chatting, pastimes and keeping warm; and
 * the needs economy: how much food a friend eats, urgent needs coming before any work, resting at camp while too weak
 * to work, a skipped night counting as a whole night's sleep, and the camp counting its food as food.
 */
public class NeedsGameTest {
	private static final BlockPos CHEST = new BlockPos(19, 2, 16);

	// ------------------------------------------------------------------- food

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_81", maxTicks = 600)
	public void hungryFriendEatsFromBackpack(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.BREAD, 3), new ItemStack(Items.COOKED_BEEF, 1));
		fern.needs().set(Need.HUNGER, 20);
		helper.succeedWhen(() -> {
			double hunger = fern.needs().get(Need.HUNGER);
			helper.assertTrue(data.stat("meals_eaten") == 1, "Fern ate one meal (" + fern.activity() + ", hunger " + hunger + ")");
			// Missing 80 hunger: the steak (48) fits without waste, so it goes before the bread.
			helper.assertTrue(fern.backpack().count(Items.COOKED_BEEF) == 0, "the steak was eaten, as the best fit for her hunger");
			helper.assertTrue(fern.backpack().count(Items.BREAD) == 3, "the bread was kept");
			helper.assertTrue(hunger >= 20 + CompanionEntity.hungerValue(new ItemStack(Items.COOKED_BEEF)) - 1,
				"hunger rose by the steak's worth, now " + hunger);
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_82", maxTicks = 900)
	public void hungryFriendEatsFromSupplyChest(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.BREAD, 5));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(8, 2, 16));
		oak.needs().set(Need.HUNGER, 20); // hungry enough that the meal comes before topping up the food reserve
		helper.succeedWhen(() -> {
			double hunger = oak.needs().get(Need.HUNGER);
			helper.assertTrue(data.stat("meals_eaten") == 1, "Oak ate a meal (" + oak.activity() + ", hunger " + hunger + ")");
			// Checked the moment the meal is eaten, before any restocking: exactly one loaf left the chest and was eaten.
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.BREAD)) == 4, "one loaf came out of the chest, "
				+ SupplyChest.count(chest, s -> s.is(Items.BREAD)) + " left");
			helper.assertTrue(oak.backpack().count(Items.BREAD) == 0, "the loaf was eaten, not stored");
			helper.assertTrue(hunger >= 20 + CompanionEntity.hungerValue(new ItemStack(Items.BREAD)) - 1, "hunger rose, now " + hunger);
			double toChest = Math.sqrt(oak.blockPosition().distSqr(helper.absolutePos(CHEST)));
			helper.assertTrue(toChest <= 4, "Oak ate at the chest (" + toChest + " blocks away)");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_83", maxTicks = 4000)
	public void starvingFriendWithoutFoodStaysAlive(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		flint.needs().set(Need.HUNGER, 0);
		flint.setHealth(6);
		Container[] chest = {null};
		helper.startSequence()
			.thenIdle(600)
			.thenExecute(() -> {
				float health = flint.getHealth();
				boolean askedForFood = flint.speechMemory().containsKey(Line.NO_FOOD);
				boolean claimedToEat = flint.speechMemory().containsKey(Line.ATE);
				// Food turns up in the chest: even at one heart, Flint goes and gets it.
				chest[0] = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.BREAD, 3));
				helper.assertTrue(flint.isAlive(), "Flint is alive");
				helper.assertTrue(health == 2.0F, "starving stops at one heart, health " + health + " (" + flint.activity() + ")");
				helper.assertTrue(askedForFood, "Flint said there is no food (" + flint.activity() + ")");
				helper.assertFalse(claimedToEat, "Flint did not claim to have eaten");
				helper.assertTrue(data.stat("meals_eaten") == 0, "nothing was eaten");
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(chest[0] != null, "the chest was placed");
				int left = SupplyChest.count(chest[0], s -> s.is(Items.BREAD));
				helper.assertTrue(left < 3, "Flint fetched food from the chest (" + flint.activity() + ", health "
					+ flint.getHealth() + ", hunger " + flint.needs().get(Need.HUNGER) + ")");
				helper.assertTrue(flint.needs().get(Need.HUNGER) >= 20, "Flint ate and is no longer starving, hunger "
					+ flint.needs().get(Need.HUNGER));
				helper.assertTrue(flint.getHealth() > 2.0F, "Flint is healing, health " + flint.getHealth());
			})
			.thenSucceed();
	}

	// ------------------------------------------------------------------ sleep

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_84", maxTicks = 1200)
	public void sleepsAtNightAndWakesInTheMorning(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, new BlockPos(6, 2, 10));
		sage.needs().set(Need.ENERGY, 30);
		BlockPos home = helper.absolutePos(TestSupport.centre());
		double[] energyAtBedtime = {0};
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(sage.isAsleep(), "Sage went to bed (" + sage.activity() + " at " + helper.relativePos(sage.blockPosition()) + ")");
				helper.assertTrue(sage.getPose() == Pose.SLEEPING, "Sage lies down, pose " + sage.getPose());
				double d = Math.sqrt(Camp.horizontalDistSqr(sage.blockPosition(), home));
				helper.assertTrue(d <= 5, "Sage sleeps at her place by the camp centre (" + d + " blocks away)");
				energyAtBedtime[0] = sage.needs().get(Need.ENERGY);
			})
			.thenIdle(200)
			.thenExecute(() -> {
				helper.assertTrue(sage.isAsleep(), "Sage is still asleep (" + sage.activity() + ")");
				double gain = sage.needs().get(Need.ENERGY) - energyAtBedtime[0];
				helper.assertTrue(gain > 1.0, "sleep restores energy, gained " + gain);
				TestSupport.setTime(helper, 23999 + 500); // morning
			})
			.thenWaitUntil(() -> {
				helper.assertFalse(Camp.isNight(helper.getLevel()), "it is morning");
				helper.assertFalse(sage.isAsleep(), "Sage woke up in the morning (" + sage.activity() + ")");
				helper.assertTrue(sage.getPose() == Pose.STANDING, "Sage is back on her feet, pose " + sage.getPose());
			})
			.thenSucceed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_85", maxTicks = 1200)
	public void sleepingFriendWakesWhenHurt(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 18000);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre().offset(2, 0, 0));
		fern.needs().set(Need.ENERGY, 20);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(fern.isAsleep() && fern.getPose() == Pose.SLEEPING,
				"Fern is asleep (" + fern.activity() + ")"))
			.thenIdle(20)
			.thenExecute(() -> {
				ServerLevel level = helper.getLevel();
				fern.hurtServer(level, level.damageSources().generic(), 2.0F);
			})
			.thenWaitUntil(() -> {
				helper.assertFalse(fern.isAsleep(), "Fern woke when hurt (" + fern.activity() + ")");
				helper.assertTrue(fern.getPose() == Pose.STANDING, "Fern is on her feet, pose " + fern.getPose());
			})
			.thenSucceed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_90", maxTicks = 2400)
	public void aegisKeepsFirstWatchThenSleeps(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		CompanionEntity aegis = TestSupport.spawnFriend(helper, FriendId.AEGIS, TestSupport.centre());
		aegis.needs().set(Need.ENERGY, 50);
		helper.startSequence()
			.thenIdle(300)
			.thenExecute(() -> {
				helper.assertFalse(aegis.isAsleep(), "Aegis keeps the first watch (" + aegis.activity() + ")");
				TestSupport.setTime(helper, 19000); // the small hours
			})
			.thenWaitUntil(() -> helper.assertTrue(aegis.isAsleep(), "Aegis sleeps after midnight with no monster about ("
				+ aegis.activity() + ")"))
			.thenSucceed();
	}

	// ----------------------------------------------------------------- social

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_86", maxTicks = 1200)
	public void lonelyFriendsChat(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(10, 2, 16));
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, new BlockPos(22, 2, 16));
		fern.needs().set(Need.SOCIAL, 20);
		sage.needs().set(Need.SOCIAL, 20);
		int unityBefore = data.unity();
		List<String> seen = new ArrayList<>();
		helper.onEachTick(() -> {
			for (CompanionEntity c : List.of(fern, sage)) {
				String activity = c.friendId().displayName() + ": " + c.activity();
				if (activity.contains("chat") && !seen.contains(activity)) {
					seen.add(activity);
				}
			}
		});
		helper.succeedWhen(() -> {
			double a = fern.needs().get(Need.SOCIAL);
			double b = sage.needs().get(Need.SOCIAL);
			helper.assertTrue(data.unity() > unityBefore, "a chat between friends adds Unity (" + data.unity() + "; " + seen
				+ "; fern " + fern.activity() + ", sage " + sage.activity() + ")");
			helper.assertTrue(Math.max(a, b) >= 50, "the friend who came over feels much better: " + a + " and " + b);
			helper.assertTrue(Math.min(a, b) >= 38, "the friend they talked to feels better too: " + a + " and " + b);
			helper.assertTrue(fern.distanceTo(sage) <= 5, "they met to talk (" + fern.distanceTo(sage) + " blocks apart)");
		});
	}

	// ------------------------------------------------------------------- fun

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_87", maxTicks = 1200)
	public void boredFriendEnjoysAPastimeWithoutChangingTheWorld(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		BlockPos flower = new BlockPos(22, 2, 21);
		helper.setBlock(flower, Blocks.POPPY);
		List<BlockState> before = snapshot(helper);
		List<String> edits = new ArrayList<>();
		WorldEditGuard.listener = e -> edits.add(e.verb() + " " + e.state() + " at " + helper.relativePos(e.pos()));
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(12, 2, 12));
		fern.needs().set(Need.FUN, 10);
		boolean[] byTheFlower = {false};
		helper.onEachTick(() -> {
			CompanionTask job = fern.scheduler().current();
			if (job != null && job.id().equals("needs.leisure") && fern.activity().equals("smelling the flowers")
				&& fern.distanceToSqr(Vec3.atCenterOf(helper.absolutePos(flower))) <= 2.5 * 2.5) {
				byTheFlower[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			double fun = fern.needs().get(Need.FUN);
			helper.assertTrue(byTheFlower[0], "Fern smelled the flowers by the poppy (" + fern.activity() + " at "
				+ helper.relativePos(fern.blockPosition()) + ")");
			helper.assertTrue(fun >= 55, "a pastime is fun: fun " + fun);
			helper.assertTrue(edits.isEmpty(), "no blocks were changed for fun: " + edits);
			helper.assertTrue(snapshot(helper).equals(before), "the plot is exactly as it was");
			helper.assertTrue(helper.getBlockState(flower).is(Blocks.POPPY), "the poppy was only looked at");
			WorldEditGuard.listener = null;
		});
	}

	// ---------------------------------------------------------------- comfort

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_88", maxTicks = 1500)
	public void chillyFriendWarmsUpByTheCampfire(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 18000); // night in the open: comfort on its own would only reach 40
		BlockPos fire = new BlockPos(20, 2, 20);
		helper.setBlock(fire, Blocks.CAMPFIRE);
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(6, 2, 10));
		terra.needs().set(Need.COMFORT, 10);
		boolean[] warmingUp = {false};
		helper.onEachTick(() -> {
			CompanionTask job = terra.scheduler().current();
			if (job != null && job.id().equals("needs.cosy") && terra.getPose() == Pose.CROUCHING) {
				warmingUp[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			double comfort = terra.needs().get(Need.COMFORT);
			double d = Math.sqrt(terra.blockPosition().distSqr(helper.absolutePos(fire)));
			helper.assertTrue(warmingUp[0], "Terra huddled by the fire (" + terra.activity() + " at "
				+ helper.relativePos(terra.blockPosition()) + ")");
			helper.assertTrue(d <= 4, "Terra is beside the fire (" + d + " blocks away)");
			helper.assertTrue(comfort >= 45, "the fire warmed Terra beyond what the open night allows: comfort " + comfort);
			helper.assertTrue(terra.getHealth() == terra.getMaxHealth(), "nobody stood in the fire");
		});
	}

	// ------------------------------------------------------------ persistence

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_89", maxTicks = 100)
	public void needsSurviveSaveAndLoad(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		ServerLevel level = helper.getLevel();
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, TestSupport.centre());
		double[] values = {12, 34, 56, 78, 23};
		for (Need n : Need.values()) {
			rowan.needs().set(n, values[n.ordinal()]);
		}
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		rowan.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		CompanionEntity copy = ModEntities.COMPANION.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(copy != null, "a fresh companion could be created");
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		Needs loaded = copy.needs();
		for (Need n : Need.values()) {
			helper.assertTrue(Math.abs(loaded.get(n) - values[n.ordinal()]) < 0.01, n.title() + " survived saving: "
				+ loaded.get(n) + " (expected " + values[n.ordinal()] + ")");
		}
		helper.assertTrue(copy.friendId() == FriendId.ROWAN, "the copy is Rowan");
		helper.assertTrue(loaded.mood() == rowan.needs().mood(), "the mood is the same after loading");
		helper.succeed();
	}

	// --------------------------------------------------------- needs economy

	/** The documented daily food use (half a loaf a friend, four or five loaves for nine) is what the code does. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_118", maxTicks = 20)
	public void dailyFoodUseMatchesTheDesign(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		double loaf = CompanionEntity.hungerValue(new ItemStack(Items.BREAD));
		helper.assertTrue(loaf == 30, "a loaf of bread fills 30 hunger, not " + loaf);
		double day = Needs.typicalDailyHunger();
		double perFriend = day / loaf;
		helper.assertTrue(perFriend >= 0.45 && perFriend <= 0.6, "a friend eats about half a loaf a day: " + perFriend);
		helper.assertTrue(9 * perFriend >= 4 && 9 * perFriend <= 5, "nine friends eat four or five loaves a day: " + 9 * perFriend);
		double idle = Needs.hungerDrain(false) * Needs.DAY_SECONDS / loaf;
		double busy = Needs.hungerDrain(true) * Needs.DAY_SECONDS / loaf;
		helper.assertTrue(idle >= 0.4 && busy <= 0.6 && busy > idle, "a whole day at work costs a little more than one at rest: "
			+ busy + " against " + idle + " loaves");
		// A day of the friend's own needs ticks (at work through the daylight, asleep at night) uses exactly that much.
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre());
		oak.needs().set(Need.HUNGER, 100);
		for (int second = 0; second < Needs.DAY_SECONDS; second++) {
			boolean daylight = second < Needs.DAYLIGHT_SECONDS;
			oak.needs().tickSecond(oak, daylight, !daylight);
		}
		double used = 100 - oak.needs().get(Need.HUNGER);
		helper.assertTrue(Math.abs(used - day) < 0.01, "a day of needs ticks used " + used + " hunger, the design says " + day);
		helper.succeed();
	}

	/**
	 * Starving with food in the chest, a friend breaks off even the most pressing work at once to eat; with nothing to
	 * eat anywhere, being hungry does not interrupt the work at all.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_114", maxTicks = 1200)
	public void starvingFriendBreaksOffPressingWorkToEat(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		wheatField(helper, 20, 27, 11, 21, 7);
		Container chest = TestSupport.placeChest(helper, CHEST);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		fern.needs().set(Need.HUNGER, 20); // hungry, and nothing to eat anywhere yet
		CampNeeds.recompute(helper.getLevel().getServer());
		helper.assertTrue(CampNeeds.weight(CampNeeds.Need.FOOD) >= 2.0, "with no food in camp, harvesting weighs double");
		boolean[] mealTooSoon = {false};
		long[] foodAt = {-1};
		long[] harvestedBefore = {0};
		helper.onEachTick(() -> mealTooSoon[0] |= foodAt[0] < 0 && doing(fern, "needs.eat"));
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(doing(fern, "fern.harvest"), "Fern harvests the ripe field (" + fern.activity() + ")"))
			.thenExecute(() -> harvestedBefore[0] = data.stat("crops_harvested"))
			.thenIdle(30)
			.thenExecute(() -> {
				helper.assertFalse(mealTooSoon[0], "with nothing to eat, the meal never broke off the harvest");
				helper.assertTrue(doing(fern, "fern.harvest"), "Fern is still harvesting (" + fern.activity() + ")");
				// Food arrives, and Fern is starving: the meal comes first, at once.
				SupplyChest.insert(chest, new ItemStack(Items.BREAD, 3));
				fern.needs().set(Need.HUNGER, 5);
				foodAt[0] = helper.getTick();
			})
			.thenWaitUntil(() -> helper.assertTrue(doing(fern, "needs.eat"), "Fern went to eat (" + fern.activity() + ")"))
			.thenExecute(() -> {
				long took = helper.getTick() - foodAt[0];
				long harvested = data.stat("crops_harvested") - harvestedBefore[0];
				helper.assertTrue(took <= 45, "the meal took over within two seconds, not " + took + " ticks");
				helper.assertTrue(harvested < 12, "she broke off the harvest part way (" + harvested + " of 12 crops in the run)");
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(data.stat("meals_eaten") == 1, "Fern ate (" + fern.activity() + ")");
				helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.BREAD)) == 2, "the loaf came from the chest");
				helper.assertTrue(fern.needs().get(Need.HUNGER) >= 34, "her hunger rose, now " + fern.needs().get(Need.HUNGER));
			})
			.thenSucceed();
	}

	/**
	 * Starving on one heart with no food anywhere: no pointless falling back again and again, and no going back to work
	 * that could lead into danger (here exposed iron, a job Flint takes at once when well). He rests by the fire and asks
	 * for food, and eats as soon as some turns up.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_115", maxTicks = 2400)
	public void starvingFriendOnOneHeartRestsAtCampUntilThereIsFood(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		for (int x = 20; x <= 22; x++) {
			for (int z = 14; z <= 18; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		BlockPos ore = new BlockPos(20, 3, 16);
		helper.setBlock(ore, Blocks.IRON_ORE);
		BlockPos fire = new BlockPos(10, 2, 16);
		helper.setBlock(fire, Blocks.CAMPFIRE);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, new BlockPos(14, 2, 16));
		TestSupport.give(flint, new ItemStack(Items.STONE_PICKAXE));
		CompanionTask mining = flint.scheduler().tasks().stream().filter(t -> t.id().equals("flint.mine_ore")).findFirst().orElseThrow();
		helper.assertTrue(mining.score(flint) > 0, "the exposed iron is a job on offer for Flint");
		flint.needs().set(Need.HUNGER, 0);
		flint.setHealth(2.0F);
		List<String> wrong = new ArrayList<>();
		boolean[] foodArrived = {false};
		helper.onEachTick(() -> {
			if (foodArrived[0] || !wrong.isEmpty()) {
				return;
			}
			if (flint.isRetreating()) {
				wrong.add("fell back with no danger about, at tick " + helper.getTick());
			}
			CompanionTask job = flint.scheduler().current();
			if (job != null && !TaskScheduler.fitWhenWeak(job.id())) {
				wrong.add("took on " + job.id() + " at tick " + helper.getTick());
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(flint.activity().equals("resting by the fire"), "Flint rests by the fire (" + flint.activity()
					+ " at " + helper.relativePos(flint.blockPosition()) + ")");
				double d = Math.sqrt(flint.blockPosition().distSqr(helper.absolutePos(fire)));
				helper.assertTrue(d <= 3.5, "Flint is beside the fire (" + d + " blocks away)");
			})
			.thenIdle(400)
			.thenExecute(() -> {
				helper.assertTrue(wrong.isEmpty(), "too weak to work, Flint " + wrong);
				helper.assertBlockPresent(Blocks.IRON_ORE, ore);
				helper.assertTrue(flint.activity().startsWith("resting"), "Flint is still resting (" + flint.activity() + ")");
				helper.assertTrue(flint.speechMemory().containsKey(Line.NO_FOOD), "Flint asked for food");
				helper.assertTrue(flint.getHealth() == 2.0F, "starving, he could not heal: health " + flint.getHealth());
				foodArrived[0] = true;
				TestSupport.placeChest(helper, new BlockPos(12, 2, 20), new ItemStack(Items.BREAD, 2));
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(data.stat("meals_eaten") >= 1, "food turned up and Flint ate (" + flint.activity() + ")");
				helper.assertTrue(flint.needs().canHeal(), "Flint can heal again, hunger " + flint.needs().get(Need.HUNGER));
			})
			.thenSucceed();
	}

	/** Exhausted at night, a friend goes to bed even with the most pressing work waiting. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_120", maxTicks = 1200)
	public void exhaustedFriendGoesToBedBeforePressingWork(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		wheatField(helper, 22, 25, 12, 20, 7);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		fern.needs().set(Need.ENERGY, 20);
		CampNeeds.recompute(helper.getLevel().getServer()); // no food in camp: harvesting weighs double (110)
		helper.succeedWhen(() -> {
			helper.assertTrue(fern.isAsleep(), "exhausted at night, Fern went to bed (" + fern.activity() + ")");
			helper.assertTrue(data.stat("crops_harvested") == 0, "she went to bed before working the field");
		});
	}

	/** Work never wakes a sleeper: crops ripening in the night wait for the morning. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_121", maxTicks = 1200)
	public void workNeverWakesASleeper(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		List<BlockPos> crops = wheatField(helper, 22, 25, 12, 20, 0);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		fern.needs().set(Need.ENERGY, 50); // tired, not exhausted: bedtime scores 55
		CampNeeds.recompute(helper.getLevel().getServer());
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(fern.isAsleep(), "Fern went to bed (" + fern.activity() + ")"))
			.thenExecute(() -> {
				// The field ripens: harvesting (110 with no food in camp) far outscores her sleep.
				for (BlockPos p : crops) {
					helper.setBlock(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
				}
			})
			.thenIdle(200)
			.thenExecute(() -> {
				helper.assertTrue(fern.isAsleep(), "the ripe field did not wake Fern (" + fern.activity() + ")");
				helper.assertTrue(data.stat("crops_harvested") == 0, "nothing was harvested in the night");
			})
			.thenSucceed();
	}

	/**
	 * When the players sleep through the night the clock jumps to morning: that counts as a whole night's sleep, for a
	 * friend already asleep and for one still on the way to bed.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_116", maxTicks = 1200)
	public void skippedNightCountsAsAWholeNightsSleep(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, TestSupport.centre().east(2));
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(1, 2, 1));
		sage.needs().set(Need.ENERGY, 20);
		rowan.needs().set(Need.ENERGY, 20);
		double[] before = new double[2];
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(sage.isAsleep(), "Sage went to bed (" + sage.activity() + ")"))
			.thenExecute(() -> {
				helper.assertFalse(rowan.isAsleep(), "Rowan is still on her way to bed (" + rowan.activity() + ")");
				before[0] = sage.needs().get(Need.ENERGY);
				before[1] = rowan.needs().get(Need.ENERGY);
				TestSupport.setTime(helper, 24000 + 500); // everyone slept: it is morning
			})
			.thenWaitUntil(() -> {
				helper.assertFalse(Camp.isNight(helper.getLevel()), "it is morning");
				double sageGain = sage.needs().get(Need.ENERGY) - before[0];
				double rowanGain = rowan.needs().get(Need.ENERGY) - before[1];
				// About 10400 ticks of night in the open: 520 s × 0.13 = +67.
				helper.assertTrue(sageGain >= 60, "the night slept through restored Sage's energy, +" + sageGain);
				helper.assertTrue(rowanGain >= 60, "and Rowan's, though she was still walking to bed, +" + rowanGain);
			})
			.thenSucceed();
	}

	/**
	 * A friend carrying food feeds a hurt friend who has none before going off to gather, even to fell a tree the camp
	 * needs (felling scores 75 with no wood stock known; feeding used to score only 70).
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_122", maxTicks = 600)
	public void hurtFriendIsFedBeforeRoutineGathering(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, false);
		data.setCamp(helper.absolutePos(new BlockPos(-14, TestSupport.STAND_Y, 16)), Camp.dimensionId(helper.getLevel()));
		BlockPos tree = new BlockPos(24, 2, 16); // out in the gathering ring
		TestSupport.growOak(helper, tree);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(16, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.BREAD, 6));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(20, 2, 20));
		oak.setMode(CompanionMode.STAY, null);
		oak.setHealth(10.0F);
		oak.needs().set(Need.HUNGER, 8); // too hungry to heal on his own: he needs that food
		String[] firstJob = {null};
		helper.onEachTick(() -> {
			CompanionTask job = rowan.scheduler().current();
			if (firstJob[0] == null && job != null) {
				firstJob[0] = job.id();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue("common.share".equals(firstJob[0]), "Rowan fed her hurt friend before her own work, not " + firstJob[0]);
			helper.assertTrue(data.stat("shares") >= 1 && rowan.backpack().count(Items.BREAD) < 6, "Rowan handed Oak bread ("
				+ rowan.activity() + ")");
		});
	}

	/** Like natural healing, the Close Friends camp healing never heals a starving friend. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_117", maxTicks = 600)
	public void starvingFriendsDoNotHealEvenAtCamp(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setUnity(600); // Close Friends: an extra 1 health every 4 s at camp
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre());
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, TestSupport.centre().east(4));
		for (CompanionEntity c : List.of(oak, terra)) {
			c.setMode(CompanionMode.STAY, null);
			c.setHealth(12.0F);
		}
		oak.needs().set(Need.HUNGER, 5);
		terra.needs().set(Need.HUNGER, 60);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(oak.getHealth() == 12.0F, "starving Oak did not heal, not even at camp: health " + oak.getHealth());
			helper.assertTrue(terra.getHealth() >= 16.0F, "well-fed Terra healed at camp: health " + terra.getHealth());
			helper.succeed();
		});
	}

	/**
	 * The camp's food meter counts food as food: wheat by the bread it bakes into, seeds and raw potatoes not at all, and
	 * Fern's carrots for planting as seed. Fern keeps only a replanting run of carrots and sows them first.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_119", maxTicks = 20)
	public void campCountsFoodAsFood(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.WHEAT_SEEDS, 64), new ItemStack(Items.POTATO, 32));
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.CARROT, KeepList.SEED_CROPS_KEPT));
		CampNeeds.recompute(helper.getLevel().getServer());
		helper.assertTrue(CampNeeds.stock(CampNeeds.Need.FOOD) == 0, "seeds, raw potatoes and seed carrots are no food: "
			+ CampNeeds.stock(CampNeeds.Need.FOOD));
		helper.assertTrue(CampNeeds.need(CampNeeds.Need.FOOD) == 1.0, "so the camp is short of food");
		helper.assertTrue(CampNeeds.stock(CampNeeds.Need.SEEDS) > 0, "they count as seeds");

		SupplyChest.insert(chest, new ItemStack(Items.WHEAT, 9)); // three loaves once baked
		SupplyChest.insert(chest, new ItemStack(Items.BREAD, 2));
		TestSupport.give(fern, new ItemStack(Items.CARROT, 5)); // beyond her planting stock: 5 × 18 = 3 loaves' worth
		CampNeeds.recompute(helper.getLevel().getServer());
		helper.assertTrue(CampNeeds.stock(CampNeeds.Need.FOOD) == 8, "3 + 2 + 3 loaves' worth of food, counted "
			+ CampNeeds.stock(CampNeeds.Need.FOOD));
		helper.assertTrue(CampNeeds.need(CampNeeds.Need.FOOD) == 0.0, "plenty for one friend's next four days");

		TestSupport.give(fern, new ItemStack(Items.CARROT, 40));
		int surplus = KeepList.surplusOf(fern, s -> s.is(Items.CARROT));
		helper.assertTrue(surplus == 61 - KeepList.SEED_CROPS_KEPT - KeepList.FOOD_KEPT, "Fern keeps a replanting run of carrots "
			+ "and her food; the rest goes to the chest to be eaten (surplus " + surplus + ")");
		Backpack seeds = new Backpack();
		seeds.insert(new ItemStack(Items.WHEAT_SEEDS, 30));
		seeds.insert(new ItemStack(Items.CARROT, 2));
		helper.assertTrue(Crops.bestSeed(seeds) == Items.CARROT, "carrots are sown first: the most food per farmland");
		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	private static boolean doing(CompanionEntity c, String taskId) {
		CompanionTask job = c.scheduler().current();
		return job != null && job.id().equals(taskId);
	}

	/** Farmland sown with wheat of one age over a rectangle of the plot, watered from its middle; returns the crops. */
	private static List<BlockPos> wheatField(GameTestHelper helper, int x0, int x1, int z0, int z1, int age) {
		BlockPos water = new BlockPos((x0 + x1) / 2, 1, (z0 + z1) / 2);
		List<BlockPos> crops = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				BlockPos soil = new BlockPos(x, 1, z);
				if (soil.equals(water)) {
					helper.setBlock(soil, Blocks.WATER);
					continue;
				}
				helper.setBlock(soil, Blocks.FARMLAND);
				helper.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age));
				crops.add(soil.above());
			}
		}
		return crops;
	}

	/** Every block of the plot from the dirt up to head height, to prove nothing changed. */
	private static List<BlockState> snapshot(GameTestHelper helper) {
		List<BlockState> states = new ArrayList<>();
		for (int x = 0; x < 32; x++) {
			for (int y = 0; y < 6; y++) {
				for (int z = 0; z < 32; z++) {
					states.add(helper.getBlockState(new BlockPos(x, y, z)));
				}
			}
		}
		return states;
	}
}

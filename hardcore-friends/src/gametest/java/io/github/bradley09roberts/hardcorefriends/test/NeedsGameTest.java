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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/** Friends looking after their own needs: eating real food, sleeping at night, chatting, pastimes and keeping warm. */
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

	// ---------------------------------------------------------------- helpers

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

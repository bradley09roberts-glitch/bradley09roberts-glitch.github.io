package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Night safety, from a recorded two-day Hardcore run in which friends kept getting up to work instead of sleeping,
 * Aegis went to bed at midnight and then fought two zombies alone (the second killed him), and Sage, with nothing to
 * fight with, could only run. Covers bedtime, the watch rota, the alarm, the rally inside the camp and a weapon for
 * everyone.
 */
public class NightGameTest {
	/** A lit campfire just off the camp centre: where the watch is kept and chilly friends warm up. */
	private static final BlockPos FIRE = new BlockPos(16, 2, 19);

	/** At night a friend with building waiting goes to bed and sleeps; the build waits for the morning. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_161", maxTicks = 1600)
	public void friendWithBuildingToDoSleepsAtNight(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		TestSupport.placeChest(helper, new BlockPos(18, 2, 16), new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.COAL, 2));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre());
		oak.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		oak.needs().set(Need.ENERGY, 60); // tired, not exhausted: bedtime used to score 49, below the build's 60
		CompanionEntity aegis = TestSupport.spawnFriend(helper, FriendId.AEGIS, TestSupport.centre().west(3));
		aegis.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD)); // he keeps the watch
		double build = task(oak, "oak.build").score(oak);
		helper.assertTrue(build >= 60, "the campfire and crafting table are waiting to be built (build scores " + build + ")");
		List<String> edits = new ArrayList<>();
		WorldEditGuard.listener = e -> {
			if (e.companion() == oak) {
				edits.add(e.verb() + " " + e.state() + " at " + helper.relativePos(e.pos()));
			}
		};
		Set<String> jobs = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			CompanionTask job = oak.scheduler().current();
			if (job != null) {
				jobs.add(job.id());
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(NightWatch.watcher(helper.getLevel()) == aegis, "Aegis keeps the watch");
				helper.assertTrue(oak.isAsleep(), "Oak went to bed (" + oak.activity() + ", jobs " + jobs + ")");
			})
			.thenIdle(400)
			.thenExecute(() -> {
				WorldEditGuard.listener = null;
				helper.assertTrue(oak.isAsleep(), "Oak stayed asleep with building waiting (" + oak.activity() + ")");
				helper.assertFalse(jobs.contains("oak.build"), "Oak never started building in the night: " + jobs);
				helper.assertTrue(edits.isEmpty(), "no block was changed in the night: " + edits);
				helper.assertFalse(data.isCompleted(Structures.CAMPFIRE), "the campfire waits for the morning");
			})
			.thenSucceed();
	}

	/** After midnight Aegis sleeps while the second watch, a healthy armed friend, keeps watch by the fire. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_162", maxTicks = 2400)
	public void secondWatchKeepsWatchWhileAegisSleeps(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 17400);
		helper.setBlock(FIRE, Blocks.CAMPFIRE);
		ServerLevel level = helper.getLevel();
		CompanionEntity aegis = TestSupport.spawnFriend(helper, FriendId.AEGIS, TestSupport.centre().west(2));
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, TestSupport.centre().east(2));
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre().north(2));
		aegis.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		sage.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		fern.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
		for (CompanionEntity c : List.of(aegis, sage, fern)) {
			c.needs().set(Need.ENERGY, 70);
		}
		Vec3 fire = Vec3.atCenterOf(helper.absolutePos(FIRE));
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(NightWatch.watch(level) == NightWatch.Watch.FIRST, "before midnight: the first watch");
				helper.assertTrue(NightWatch.watcher(level) == aegis, "Aegis keeps the first watch, not " + who(level));
				helper.assertFalse(aegis.isAsleep(), "Aegis is awake on watch (" + aegis.activity() + ")");
				helper.assertTrue(sage.isAsleep() && fern.isAsleep(), "the others sleep: Sage " + sage.activity() + ", Fern "
					+ fern.activity());
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(NightWatch.watch(level) == NightWatch.Watch.SECOND, "after midnight: the second watch");
				helper.assertTrue(NightWatch.watcher(level) == sage, "Sage (sword) keeps the second watch, not " + who(level));
				helper.assertTrue(aegis.isAsleep(), "Aegis went to bed after his watch (" + aegis.activity() + ")");
				helper.assertTrue(doing(sage, "common.watch") && !sage.isAsleep(), "Sage got up to keep watch ("
					+ sage.activity() + ")");
				double d = sage.position().distanceTo(fire);
				helper.assertTrue(d <= 5, "Sage keeps watch by the campfire (" + String.format(Locale.ROOT, "%.1f", d) + " blocks)");
				helper.assertTrue(fern.isAsleep(), "Fern sleeps on (" + fern.activity() + ")");
			})
			.thenExecute(() -> helper.assertTrue(NightWatch.keptWatchRecently(aegis) && NightWatch.keptWatchRecently(sage)
				&& !NightWatch.keptWatchRecently(fern), "the watchers are remembered (they nap sooner tomorrow)"))
			.thenSucceed();
	}

	/** With Aegis dead the rota still works: the armed friend takes the first watch, the next the second. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_163", maxTicks = 2000)
	public void someoneKeepsWatchWithAegisDead(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		helper.setBlock(FIRE, Blocks.CAMPFIRE);
		ServerLevel level = helper.getLevel();
		CompanionEntity aegis = TestSupport.spawnFriend(helper, FriendId.AEGIS, new BlockPos(4, 2, 4));
		aegis.kill(level);
		helper.assertTrue(data.ledger(FriendId.AEGIS).state == CampData.LifeState.DEAD, "Aegis has fallen");
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre().west(2));
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre().east(2));
		oak.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		fern.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
		oak.needs().set(Need.ENERGY, 70);
		fern.needs().set(Need.ENERGY, 70);
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(NightWatch.watcher(level) == oak, "Oak (axe) keeps the first watch, not " + who(level));
				helper.assertTrue(doing(oak, "common.watch") && !oak.isAsleep(), "Oak is keeping watch (" + oak.activity() + ")");
				helper.assertTrue(fern.isAsleep(), "Fern sleeps (" + fern.activity() + ")");
			})
			.thenExecute(() -> TestSupport.setTime(helper, 18500))
			.thenWaitUntil(() -> {
				helper.assertTrue(NightWatch.watcher(level) == fern, "Fern takes the second watch, not " + who(level));
				helper.assertTrue(doing(fern, "common.watch") && !fern.isAsleep(), "Fern got up for her watch (" + fern.activity() + ")");
				helper.assertTrue(oak.isAsleep(), "Oak sleeps after the first watch (" + oak.activity() + ")");
			})
			.thenSucceed();
	}

	/** A zombie going for a friend in camp is joined by an armed friend 12 blocks away, round a wall he cannot see past. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_164", maxTicks = 900)
	public void armedFriendRalliesFromTwelveBlocks(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		// A wall between Rowan and the fight, so she must go round it without ever seeing the zombie first.
		for (int z = 13; z <= 21; z++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(10, y, z), Blocks.COBBLESTONE);
			}
		}
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(16, 2, 16));
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(4, 2, 17));
		fern.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
		rowan.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(16, 2, 19));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the daylight
		zombie.setTarget(fern);
		helper.assertFalse(rowan.hasLineOfSight(zombie), "the wall hides the zombie from Rowan");
		// Just spawned, Rowan has not landed, so no path can be worked out yet: that must not count the zombie as out of
		// reach (it once kept her out of the fight until Fern had fled beyond the rally's range).
		helper.assertFalse(rowan.onGround(), "Rowan has not landed yet");
		double start = rowan.distanceTo(zombie);
		helper.assertTrue(start > 8 && start < 16, "Rowan starts out of the old 8-block reach (" + start + " blocks)");
		double[] joinedAt = {-1};
		helper.onEachTick(() -> {
			if (joinedAt[0] < 0 && rowan.getTarget() == zombie) {
				joinedAt[0] = rowan.distanceTo(zombie);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(joinedAt[0] > 8, "Rowan joined the fight from beyond 8 blocks (" + joinedAt[0] + "; Rowan "
				+ rowan.activity() + " at " + helper.relativePos(rowan.blockPosition()) + ", Fern " + fern.activity() + " at "
				+ helper.relativePos(fern.blockPosition()) + " health " + fern.getHealth() + ", zombie at "
				+ helper.relativePos(zombie.blockPosition()) + " health " + zombie.getHealth() + ")");
			helper.assertFalse(zombie.isAlive(), "the zombie was beaten: Fern " + fern.activity() + ", Rowan " + rowan.activity());
			helper.assertTrue(fern.isAlive() && rowan.isAlive(), "both friends came through");
		});
	}

	/** The watch raises the alarm about a hostile in the camp, and a sleeper well away from it wakes. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_165", maxTicks = 1600)
	public void sleeperWakesWhenTheWatchRaisesTheAlarm(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 14000);
		helper.setBlock(FIRE, Blocks.CAMPFIRE);
		ServerLevel level = helper.getLevel();
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre().west(2));
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre().east(2));
		oak.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		fern.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
		oak.needs().set(Need.ENERGY, 70);
		fern.needs().set(Need.ENERGY, 70);
		Creeper[] creeper = {null};
		long[] alarmFrom = {Long.MAX_VALUE};
		double[] wokeAt = {-1};
		helper.onEachTick(() -> {
			if (creeper[0] != null && wokeAt[0] < 0 && !fern.isAsleep()) {
				wokeAt[0] = fern.distanceTo(creeper[0]);
			}
		});
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(NightWatch.watcher(level) == oak && doing(oak, "common.watch"), "Oak is on watch ("
					+ oak.activity() + ")");
				helper.assertTrue(fern.isAsleep(), "Fern is asleep (" + fern.activity() + ")");
			})
			.thenExecute(() -> {
				// A creeper (frozen, so it neither moves nor hisses) comes into the camp at its edge: too far from Fern to
				// wake her by itself, and the watch does not take on a creeper, so only the alarm can wake her.
				alarmFrom[0] = level.getGameTime();
				creeper[0] = helper.spawn(EntityTypes.CREEPER, new BlockPos(28, 2, 6));
				creeper[0].setNoAi(true);
				helper.assertTrue(fern.distanceTo(creeper[0]) > 12, "the creeper is well away from Fern");
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(NightWatch.alarmRaisedSince(level, alarmFrom[0] - 1), "Oak raised the alarm");
				helper.assertTrue(NightWatch.alarmed(level).contains(creeper[0]), "about the creeper");
				helper.assertTrue(wokeAt[0] > 8, "Fern woke for the alarm, the creeper " + wokeAt[0] + " blocks away");
			})
			.thenExecute(() -> creeper[0].discard())
			.thenSucceed();
	}

	/** Every friend brings something to fight with; Sage's is a sword, and she stands with Fern against a zombie. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_166", maxTicks = 900)
	public void sageStartsWithASwordAndStandsWithFern(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		ServerLevel level = helper.getLevel();
		for (FriendId id : FriendId.values()) {
			Item tool = id.starterTool();
			ItemStack stack = tool == null ? ItemStack.EMPTY : new ItemStack(tool);
			helper.assertTrue(stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES)
				|| stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES), id.displayName() + " brings something to fight with, not " + stack);
		}
		helper.assertTrue(KeepList.roleTool(Role.STRATEGIST) == ItemTags.SWORDS, "a sword is the strategist's tool");
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		BlockPos stand = helper.absolutePos(TestSupport.centre());
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 0.0F);
		player.getInventory().add(new ItemStack(Items.BREAD, 2));
		level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(),
			"friends recruit sage");
		CompanionEntity sage = Companions.find(FriendId.SAGE).orElseThrow(() -> new IllegalStateException("Sage was not recruited"));
		helper.assertTrue(sage.getMainHandItem().is(Items.WOODEN_SWORD), "Sage arrives with a wooden sword, not " + sage.getMainHandItem());
		helper.assertTrue(KeepList.isRoleTool(Role.STRATEGIST, sage.getMainHandItem()), "her sword counts as her tool, so she keeps it");
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre().east(2));
		fern.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(16, 2, 22));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setTarget(sage);
		helper.assertTrue(sage.canStandAndFight(zombie), "with a sword, Sage can stand and fight");
		boolean[] ran = {false};
		boolean[] sageHit = {false};
		helper.onEachTick(() -> {
			if (sage.activity().startsWith("getting away")) {
				ran[0] = true;
			}
			if (zombie.getLastHurtByMob() == sage) {
				sageHit[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(zombie.isAlive(), "the zombie was beaten: Sage " + sage.activity() + ", Fern " + fern.activity());
			helper.assertTrue(sageHit[0], "Sage struck it with her sword");
			helper.assertFalse(ran[0], "Sage stood her ground instead of running");
			helper.assertTrue(sage.isAlive() && fern.isAlive(), "both came through");
		});
	}

	/**
	 * A whole night, dusk to dawn, with three friends and two zombies coming into the camp, one in each watch. The
	 * watch spots each one and raises the alarm, the others get up and stand together, and everyone sees the morning.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_167", maxTicks = 12500)
	public void threeFriendsSeeTheNightThroughTwoZombies(GameTestHelper helper) {
		wholeNight(helper, List.of(FriendId.AEGIS, FriendId.SAGE, FriendId.FERN),
			List.of(new Arrival(14500, new BlockPos(1, 2, 1)), new Arrival(19500, new BlockPos(30, 2, 30))),
			List.of("FIRST Aegis", "SECOND Sage"));
	}

	/**
	 * The same without Aegis, and harder: one zombie in the first watch and two together in the second. Sage (the best
	 * weapon) keeps the first watch, Oak the second, and the three stand together.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_168", maxTicks = 12500)
	public void threeFriendsWithoutAegisHoldTheCamp(GameTestHelper helper) {
		wholeNight(helper, List.of(FriendId.OAK, FriendId.SAGE, FriendId.FERN),
			List.of(new Arrival(14500, new BlockPos(1, 2, 1)), new Arrival(19500, new BlockPos(30, 2, 30)),
				new Arrival(19500, new BlockPos(1, 2, 30))),
			List.of("FIRST Sage", "SECOND Oak"));
	}

	/** A zombie walking into the camp at a time of day, from a spot at the camp's edge. */
	private record Arrival(long timeOfDay, BlockPos at) {
	}

	/**
	 * Runs a whole night from dusk to dawn: the friends (holding their starter tools, food in the chest, a campfire by
	 * the camp centre) must all see the morning, each zombie must be beaten by them in the night (not burnt by the
	 * sunrise), the watches must go to the expected friends, everyone must sleep some of the night, and the watch must
	 * raise the alarm for each arrival.
	 */
	private static void wholeNight(GameTestHelper helper, List<FriendId> ids, List<Arrival> arrivals, List<String> expectedWatches) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 12000);
		helper.setBlock(FIRE, Blocks.CAMPFIRE);
		ServerLevel level = helper.getLevel();
		TestSupport.placeChest(helper, new BlockPos(20, 2, 16), new ItemStack(Items.BREAD, 16));
		List<CompanionEntity> friends = new ArrayList<>();
		BlockPos[] spots = {TestSupport.centre().west(2), TestSupport.centre().east(2), TestSupport.centre().north(2)};
		for (int i = 0; i < ids.size(); i++) {
			CompanionEntity c = TestSupport.spawnFriend(helper, ids.get(i), spots[i % spots.length]);
			c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ids.get(i).starterTool()));
			c.needs().set(Need.ENERGY, 75);
			friends.add(c);
		}
		Set<String> watchers = new LinkedHashSet<>();
		Set<String> slept = new LinkedHashSet<>();
		List<Zombie> zombies = new ArrayList<>();
		List<Long> spawnedAt = new ArrayList<>();
		String[] fights = new String[arrivals.size()];
		float[] lowest = new float[friends.size()];
		for (int i = 0; i < friends.size(); i++) {
			lowest[i] = friends.get(i).getMaxHealth();
		}
		long[] alarms = {0};
		long[] lastAlarm = {Long.MIN_VALUE};
		helper.onEachTick(() -> {
			for (int i = 0; i < friends.size(); i++) {
				lowest[i] = Math.min(lowest[i], friends.get(i).getHealth());
				if (friends.get(i).isAsleep()) {
					slept.add(friends.get(i).friendId().displayName());
				}
			}
			for (int i = 0; i < zombies.size(); i++) {
				Zombie z = zombies.get(i);
				if (fights[i] == null && !z.isAlive()) {
					LivingEntity by = z.getLastHurtByMob();
					fights[i] = "zombie " + (i + 1) + " beaten in " + (level.getGameTime() - spawnedAt.get(i)) + " ticks, last hit by "
						+ (by == null ? "nobody" : by.getName().getString());
				}
			}
			CompanionEntity w = NightWatch.watcher(level);
			if (w != null) {
				watchers.add(NightWatch.watch(level) + " " + w.friendId().displayName());
			}
			if (NightWatch.alarmRaisedSince(level, lastAlarm[0])) {
				alarms[0]++;
				lastAlarm[0] = level.getGameTime();
			}
			long t = Camp.timeOfDay(level);
			while (zombies.size() < arrivals.size() && t >= arrivals.get(zombies.size()).timeOfDay()) {
				zombies.add(helper.spawn(EntityTypes.ZOMBIE, arrivals.get(zombies.size()).at()));
				spawnedAt.add(level.getGameTime());
			}
		});
		long batches = arrivals.stream().mapToLong(Arrival::timeOfDay).distinct().count();
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(Camp.timeOfDay(level) >= 23300, "dawn is coming (" + Camp.timeOfDay(level) + ")"))
			.thenExecute(() -> {
				StringBuilder health = new StringBuilder();
				for (int i = 0; i < friends.size(); i++) {
					health.append(i == 0 ? "" : ", ").append(friends.get(i).friendId().displayName()).append(' ').append(lowest[i]);
				}
				String summary = Arrays.toString(fights) + "; lowest health " + health + "; watches " + watchers + "; " + alarms[0]
					+ " alarms";
				System.out.println("[NightGameTest] whole night: " + summary);
				helper.assertTrue(zombies.size() == arrivals.size(), "every zombie came into the camp");
				for (CompanionEntity c : friends) {
					helper.assertTrue(c.isAlive(), c.friendId().displayName() + " lived to see the morning: " + summary);
				}
				// Beaten in the fight, well before the sunrise could burn them.
				helper.assertTrue(Arrays.stream(fights).allMatch(f -> f != null && !f.contains("by nobody")),
					"each zombie was beaten by the friends in the night: " + summary);
				helper.assertTrue(watchers.containsAll(expectedWatches), "the watches went to " + expectedWatches + ": " + watchers);
				for (FriendId id : ids) {
					helper.assertTrue(slept.contains(id.displayName()), id.displayName() + " slept part of the night: " + slept);
				}
				helper.assertTrue(alarms[0] >= batches, "the watch raised the alarm for each arrival (" + alarms[0] + " alarms)");
			})
			.thenSucceed();
	}

	// ---------------------------------------------------------------- helpers

	private static CompanionTask task(CompanionEntity c, String id) {
		for (CompanionTask t : c.scheduler().tasks()) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		throw new IllegalStateException("no task " + id);
	}

	private static boolean doing(CompanionEntity c, String taskId) {
		CompanionTask job = c.scheduler().current();
		return job != null && job.id().equals(taskId);
	}

	private static String who(ServerLevel level) {
		CompanionEntity w = NightWatch.watcher(level);
		return w == null ? "nobody" : w.friendId().displayName() + " (" + w.activity() + ")";
	}
}

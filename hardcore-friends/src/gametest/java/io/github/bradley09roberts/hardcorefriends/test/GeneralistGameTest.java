package io.github.bradley09roberts.hardcorefriends.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import io.github.bradley09roberts.hardcorefriends.ai.role.forage.DeliverToBuilderTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.sage.ReviewStoresTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ExploreTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ScoutLog;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Everyone can do every job, specialists first: a friend stands in for an absent specialist, sticks to their own
 * speciality while there is some, shares a building job with nobody, and still does the duties only they can do.
 */
public class GeneralistGameTest {
	private static final BlockPos CHEST = new BlockPos(18, 2, 16);
	private static final BlockPos TABLE = new BlockPos(18, 2, 15);

	// ---------------------------------------------------------------- helpers

	private static BlockState ripeWheat() {
		return Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
	}

	/** A watered row of ripe wheat along z at the given x; returns the crop positions. */
	private static List<BlockPos> wheatRow(GameTestHelper helper, int x, int fromZ, int toZ) {
		List<BlockPos> crops = new ArrayList<>();
		for (int z = fromZ; z <= toZ; z++) {
			BlockPos soil = new BlockPos(x, 1, z);
			helper.setBlock(soil, Blocks.FARMLAND);
			helper.setBlock(soil.above(), ripeWheat());
			crops.add(soil.above());
		}
		// Watered like a real field: dry, bare farmland can turn back to dirt between harvesting and replanting.
		helper.setBlock(new BlockPos(x - 2, 1, (fromZ + toZ) / 2), Blocks.WATER);
		return crops;
	}

	private static boolean isRipe(GameTestHelper helper, BlockPos crop) {
		BlockState s = helper.getBlockState(crop);
		return s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == 7;
	}

	/** Puts the named jobs of this friend aside for a while. */
	private static void setAside(GameTestHelper helper, CompanionEntity c, int ticks, String... ids) {
		long now = helper.getLevel().getGameTime();
		for (String id : ids) {
			c.scheduler().cooldown(id, now, ticks);
		}
	}

	private static String job(CompanionEntity c) {
		CompanionTask current = c.scheduler().current();
		return current == null ? "nothing" : current.id();
	}

	// ------------------------------------------------------------- standing in

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_91", maxTicks = 2400)
	public void aegisHarvestsAndReplantsWithNoFarmer(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		List<BlockPos> crops = wheatRow(helper, 21, 14, 18);
		CompanionEntity aegis = TestSupport.spawnFriend(helper, FriendId.AEGIS, TestSupport.centre());
		aegis.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		helper.succeedWhen(() -> {
			for (BlockPos p : crops) {
				BlockState s = helper.getBlockState(p);
				helper.assertTrue(s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) < 7, "wheat at " + p
					+ " harvested and replanted by Aegis (now " + s + "; Aegis is " + aegis.activity() + ")");
			}
			helper.assertTrue(aegis.backpack().count(Items.WHEAT) >= 5, "Aegis carries the harvested wheat, has "
				+ aegis.backpack().count(Items.WHEAT) + " (" + aegis.activity() + ")");
			helper.assertTrue(data.stat("crops_harvested") >= 5, "harvests counted");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_92", maxTicks = 2400)
	public void rowanBuildsTheCampfireWithOakAway(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, TestSupport.centre());
		rowan.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		// Rowan has caught up with her own gathering (the plot has no trees or berries, and the quarry can wait).
		setAside(helper, rowan, 2400, "rowan.chop", "rowan.quarry", "rowan.forage");
		Set<String> jobs = new LinkedHashSet<>();
		helper.onEachTick(() -> jobs.add(job(rowan)));
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.CAMPFIRE), "Rowan built the campfire with Oak away (she is "
				+ rowan.activity() + "; jobs so far " + jobs + ")");
			BlockPos fire = data.site(Structures.CAMPFIRE).orElseThrow().origin;
			helper.assertTrue(helper.getLevel().getBlockState(fire).is(Blocks.CAMPFIRE), "a campfire stands at " + fire);
			helper.assertTrue(jobs.contains("oak.build"), "Rowan took on the building job herself, jobs " + jobs);
		});
	}

	// ------------------------------------------------------------- specialists first

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_93", maxTicks = 2400)
	public void fernFarmsAndFlintMinesEachTheirOwn(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		List<BlockPos> crops = wheatRow(helper, 10, 13, 18);
		// A small stone outcrop with three iron ores facing the plot centre.
		for (int x = 20; x <= 22; x++) {
			for (int z = 14; z <= 18; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		List<BlockPos> ores = List.of(new BlockPos(20, 3, 15), new BlockPos(20, 3, 16), new BlockPos(20, 3, 17));
		for (BlockPos ore : ores) {
			helper.setBlock(ore, Blocks.IRON_ORE);
		}
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(13, 2, 16));
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, new BlockPos(17, 2, 16));
		// Both can do both jobs: each carries a stone pickaxe, and harvesting needs no tool.
		TestSupport.give(fern, new ItemStack(Items.STONE_PICKAXE));
		TestSupport.give(flint, new ItemStack(Items.STONE_PICKAXE));
		List<String> strayed = new ArrayList<>();
		int[] harvestedBy = new int[2];
		int[] minedBy = new int[2];
		WorldEditGuard.listener = e -> {
			if (!e.verb().equals("broke")) {
				return;
			}
			boolean crop = e.state().is(Blocks.WHEAT);
			boolean ore = e.state().is(Blocks.IRON_ORE);
			boolean oresLeft = ores.stream().anyMatch(p -> helper.getBlockState(p).is(Blocks.IRON_ORE));
			boolean ripeLeft = crops.stream().anyMatch(p -> isRipe(helper, p));
			if (e.companion() == fern) {
				harvestedBy[0] += crop ? 1 : 0;
				minedBy[0] += ore ? 1 : 0;
				if (ore && ripeLeft) {
					strayed.add("Fern mined ore at " + helper.relativePos(e.pos()) + " with ripe wheat still standing");
				}
			} else if (e.companion() == flint) {
				harvestedBy[1] += crop ? 1 : 0;
				minedBy[1] += ore ? 1 : 0;
				if (crop && oresLeft) {
					strayed.add("Flint harvested at " + helper.relativePos(e.pos()) + " with ore still to mine");
				}
			}
		};
		helper.runAfterDelay(2390, () -> WorldEditGuard.listener = null);
		helper.succeedWhen(() -> {
			helper.assertTrue(strayed.isEmpty(), "each friend kept to their own speciality while it had work: " + strayed);
			for (BlockPos ore : ores) {
				helper.assertBlockPresent(Blocks.AIR, ore);
			}
			for (BlockPos p : crops) {
				BlockState s = helper.getBlockState(p);
				helper.assertTrue(s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) < 7, "wheat at " + p + " harvested and replanted (Fern is "
					+ fern.activity() + ", Flint is " + flint.activity() + ")");
			}
			// Once his ore is gone Flint may lend a hand in the field (he has nothing else to do), so Fern need not do it all.
			helper.assertTrue(harvestedBy[0] >= 3, "Fern did most of the harvesting (Fern " + harvestedBy[0] + ", Flint "
				+ harvestedBy[1] + ")");
			helper.assertTrue(minedBy[1] >= 2, "Flint did the mining (Flint " + minedBy[1] + ", Fern " + minedBy[0] + ")");
			WorldEditGuard.listener = null;
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_94", maxTicks = 4000)
	public void twoBuildersNeverShareABuildingJob(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(2);
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() < 2) {
				data.markCompleted(e.id());
			}
		}
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64),
			new ItemStack(Items.GLASS, 6), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		// Oak is away. Fern and Terra both stand in for him; neither has much of their own to do.
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(14, 2, 16));
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(16, 2, 18));
		for (CompanionEntity c : List.of(fern, terra)) {
			setAside(helper, c, 4000, "rowan.chop", "rowan.quarry", "rowan.forage");
		}
		Set<String> builders = new HashSet<>();
		List<String> clashes = new ArrayList<>();
		Set<String> othersWhileBuilding = new HashSet<>();
		helper.onEachTick(() -> {
			boolean fernBuilds = job(fern).equals("oak.build");
			boolean terraBuilds = job(terra).equals("oak.build");
			if (fernBuilds && terraBuilds) {
				clashes.add("tick " + helper.getTick());
			}
			if (fernBuilds) {
				builders.add("Fern");
				othersWhileBuilding.add(job(terra));
			}
			if (terraBuilds) {
				builders.add("Terra");
				othersWhileBuilding.add(job(fern));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(clashes.isEmpty(), "never two builders on the cabin at once, but both were at " + clashes);
			int progress = data.site(Structures.CABIN).map(s -> s.progress).orElse(0);
			helper.assertTrue(progress >= 60, "the cabin is going up (progress " + progress + "; Fern is " + fern.activity()
				+ ", Terra is " + terra.activity() + ")");
			othersWhileBuilding.remove("oak.build");
			helper.assertFalse(othersWhileBuilding.isEmpty(), "the other friend did something else while one built");
			helper.assertTrue(!builders.isEmpty(), "someone stood in as the builder");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_97", maxTicks = 2400)
	public void standInHandsTheBuildBackWhenOakReturns(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(2);
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() < 2) {
				data.markCompleted(e.id());
			}
		}
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64),
			new ItemStack(Items.GLASS, 6), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(14, 2, 16));
		rowan.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		setAside(helper, rowan, 2400, "rowan.chop", "rowan.quarry", "rowan.forage");
		// Oak is told to stay put, so Rowan stands in for him on the cabin.
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(16, 2, 20));
		oak.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		oak.setMode(CompanionMode.STAY, null);
		long[] oakBackAt = {-1};
		List<String> clashes = new ArrayList<>();
		helper.onEachTick(() -> {
			boolean rowanBuilds = job(rowan).equals("oak.build");
			if (oakBackAt[0] < 0 && rowanBuilds) {
				oak.setMode(CompanionMode.WORK, null); // back to work while Rowan is on his cabin
				oakBackAt[0] = helper.getTick();
			}
			if (rowanBuilds && job(oak).equals("oak.build")) {
				clashes.add("tick " + helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(oakBackAt[0] >= 0, "Rowan stood in on the cabin while Oak stayed put (Rowan is " + rowan.activity() + ")");
			helper.assertTrue(clashes.isEmpty(), "never both on the cabin at once, but both were at " + clashes);
			helper.assertTrue(job(oak).equals("oak.build"), "Oak took his cabin back (Oak is " + oak.activity() + ", Rowan is "
				+ rowan.activity() + ")");
			helper.assertTrue(helper.getTick() - oakBackAt[0] < 400, "Rowan handed it over promptly, after "
				+ (helper.getTick() - oakBackAt[0]) + " ticks");
		});
	}

	// ------------------------------------------------------------- duties only they do

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_95", maxTicks = 1600)
	@SuppressWarnings("removal") // the mock player helper is the supported way to get a server player in game tests
	public void sageAndScoutKeepTheirDutiesAmidOtherWork(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		// Plenty of other people's work: ripe wheat with no farmer, and a chest of logs and coal with no builder.
		List<BlockPos> crops = wheatRow(helper, 6, 13, 19);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.COAL, 4),
			new ItemStack(Items.BREAD, 16));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, new BlockPos(8, 2, 16));
		CompanionEntity scout = TestSupport.spawnFriend(helper, FriendId.SCOUT, new BlockPos(10, 2, 20));
		ScoutLog log = ScoutLog.of(data);
		log.noteFind(ScoutLog.ORE, "iron ore at 1 2 3", 4);
		log.advance(ExploreTask.rings(), 8, true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos stand = helper.absolutePos(new BlockPos(20, 2, 20));
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 0.0F);
		helper.runBeforeTestEnd(() -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.assertTrue(crops.stream().allMatch(p -> isRipe(helper, p)), "the wheat starts ripe");
		helper.succeedWhen(() -> {
			helper.assertTrue(data.memory(ReviewStoresTask.MEMORY).contains("at"), "Sage reviewed the stores (Sage is "
				+ sage.activity() + ")");
			helper.assertTrue(log.unreportedTotal() == 0, "Scout reported the finds (Scout is " + scout.activity() + ")");
			// runBeforeTestEnd only runs at the time limit. A player left behind stays in the level, and Aegis in a
			// test run next to it would guard them instead of working.
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}

	// ------------------------------------------------------------- cost

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_96", maxTicks = 1300)
	public void nineFriendsChooseJobsCheaply(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(1);
		wheatRow(helper, 6, 12, 20);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.COBBLESTONE, 32),
			new ItemStack(Items.COAL, 8), new ItemStack(Items.WHEAT_SEEDS, 16), new ItemStack(Items.BREAD, 16));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		TestSupport.growOak(helper, new BlockPos(3, 2, 3));
		TestSupport.growOak(helper, new BlockPos(28, 2, 28));
		helper.setBlock(new BlockPos(24, 2, 8), Blocks.IRON_ORE);
		int i = 0;
		for (FriendId id : FriendId.values()) {
			CompanionEntity c = TestSupport.spawnFriend(helper, id, new BlockPos(10 + 2 * (i % 3), 2, 10 + 2 * (i / 3)));
			if (id.starterTool() != null) {
				c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(id.starterTool()));
			}
			i++;
		}
		// Let everyone settle into work, then time a minute of choosing jobs.
		helper.runAfterDelay(100, TaskScheduler::resetTiming);
		helper.runAfterDelay(1300 - 20, () -> {
			long evaluations = TaskScheduler.evaluations();
			double totalMs = TaskScheduler.evaluateNanos() / 1e6;
			double perChoice = evaluations == 0 ? 0 : totalMs / evaluations;
			System.out.println(String.format("[HardcoreFriendsTest] nine friends: %d job choices in %d ticks took %.1f ms"
				+ " (%.3f ms each, %.2f ms per tick)", evaluations, 1300 - 120, totalMs, perChoice, totalMs / (1300 - 120)));
			helper.assertTrue(evaluations >= 9 * 50, "every friend kept choosing jobs, " + evaluations + " choices");
			// One server tick is 50 ms and each friend chooses about once a second, so even on a busy machine a choice
			// should stay well under 5 ms (about 1 ms on an idle 4-core machine; unbounded scans would show up here).
			helper.assertTrue(perChoice < 5.0, String.format("choosing a job takes %.3f ms on average", perChoice));
			helper.succeed();
		});
	}

	// ------------------------------------------------------------- letting go of shared jobs

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_125", maxTicks = 2400)
	public void reloadedStandInLetsOakTakeHisCabinBack(GameTestHelper helper) {
		cabinCamp(helper);
		CompanionEntity rowan = freeRowan(helper, new BlockPos(14, 2, 16));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(16, 2, 20));
		oak.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		oak.setMode(CompanionMode.STAY, null);
		CompanionEntity[] rowanNow = {rowan};
		long[] oakBackAt = {-1};
		helper.onEachTick(() -> {
			if (oakBackAt[0] < 0 && job(rowan).equals("oak.build")) {
				// The world is closed and opened again while Rowan is on Oak's cabin, so she comes back with a clean
				// slate and, as in a real camp where she has her own gathering, does not take the cabin up again.
				// Then Oak, back from a trip, returns to work with nothing to do but his cabin.
				rowanNow[0] = reload(helper, rowan);
				setAside(helper, rowanNow[0], 2400, "rowan.chop", "rowan.quarry", "rowan.forage", "oak.build");
				oak.setMode(CompanionMode.WORK, null);
				setAside(helper, oak, 2400, "oak.process_wood", "oak.repair", "clear_site");
				oakBackAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(oakBackAt[0] >= 0, "Rowan stood in on the cabin while Oak was away (she is " + rowan.activity() + ")");
			helper.assertTrue(rowanNow[0] != rowan && rowanNow[0].getUUID().equals(rowan.getUUID()), "Rowan was loaded again");
			helper.assertTrue(job(oak).equals("oak.build"), "Oak took his cabin back (Oak is " + oak.activity() + ", Rowan is "
				+ rowanNow[0].activity() + ")");
			helper.assertTrue(DeliverToBuilderTask.builder().orElse(null) == oak, "building materials go to Oak, who is building");
			helper.assertTrue(helper.getTick() - oakBackAt[0] < 400, "Oak was not kept off his cabin, he started after "
				+ (helper.getTick() - oakBackAt[0]) + " ticks");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_126", maxTicks = 2400)
	public void cabinStaysWithStandInWhenOakFallsAskingForIt(GameTestHelper helper) {
		cabinCamp(helper);
		CompanionEntity rowan = freeRowan(helper, new BlockPos(14, 2, 16));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(16, 2, 20));
		oak.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		oak.setMode(CompanionMode.STAY, null);
		long[] fellAt = {-1};
		List<String> problems = new ArrayList<>();
		helper.onEachTick(() -> {
			if (oak.mode() == CompanionMode.STAY && job(rowan).equals("oak.build")) {
				oak.setMode(CompanionMode.WORK, null); // back to work while Rowan is on his cabin: he asks for it back
			} else if (fellAt[0] < 0 && oak.mode() == CompanionMode.WORK && handovers().containsKey("oak.build")) {
				oak.kill(helper.getLevel());
				fellAt[0] = helper.getTick();
				if (handovers().containsKey("oak.build")) {
					problems.add("Oak's request for his cabin outlived him");
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fellAt[0] >= 0, "Oak came back for his cabin (Oak is " + oak.activity() + ", Rowan is "
				+ rowan.activity() + ")");
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.assertTrue(job(rowan).equals("oak.build"), "Rowan carries on with the cabin after Oak's death (she is "
				+ rowan.activity() + ")");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_127", maxTicks = 1600)
	public void handoverLeftFromAnotherWorldLapses(GameTestHelper helper) {
		cabinCamp(helper);
		// The world played before this one ran for longer, and its builder had just asked for his cabin back when it
		// closed: the request names a friend who is not here and a time this world has not reached.
		leaveHandover("oak.build", UUID.randomUUID(), helper.getLevel().getGameTime() + 1_000_000);
		helper.runBeforeTestEnd(() -> handovers().remove("oak.build"));
		CompanionEntity rowan = freeRowan(helper, new BlockPos(14, 2, 16));
		helper.succeedWhen(() -> {
			helper.assertFalse(handovers().containsKey("oak.build"), "the request from the other world was forgotten");
			helper.assertTrue(job(rowan).equals("oak.build"), "Rowan stands in on the cabin (she is " + rowan.activity() + ")");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_128", maxTicks = 1600)
	public void friendBackInRangeKeepsTheirJob(GameTestHelper helper) {
		cabinCamp(helper);
		CompanionEntity rowan = freeRowan(helper, new BlockPos(14, 2, 16));
		ServerLevel level = helper.getLevel();
		long[] backAt = {-1};
		helper.onEachTick(() -> {
			if (backAt[0] < 0 && job(rowan).equals("oak.build")) {
				// Her chunk drops out of range for a moment and comes back, as at the edge of a player's view: she is
				// unloaded, which lets go of the cabin, and loaded again still on it.
				ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(rowan, level);
				helper.assertTrue(SpecialityTask.runner("oak.build").isEmpty(), "unloading let go of the cabin");
				ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(rowan, level);
				backAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(backAt[0] >= 0 && helper.getTick() > backAt[0] + 2, "Rowan was on the cabin and came back in range"
				+ " (she is " + rowan.activity() + ")");
			helper.assertTrue(job(rowan).equals("oak.build"), "Rowan is still building (she is " + rowan.activity() + ")");
			helper.assertTrue(SpecialityTask.runner("oak.build").orElse(null) == rowan, "Rowan holds the cabin again, so"
				+ " nobody else starts on it");
		});
	}

	// ------------------------------------------------------------- tools for covered work

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_129", maxTicks = 1400)
	public void fullBackpackKeepsOwnToolOverCoverTool(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.WOODEN_HOE));
		// Oak works the camp alone, so he covers farming and wants a hoe besides his axe. His backpack is full of
		// building stock he keeps, so there is no room for it without putting his axe away.
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre());
		oak.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		TestSupport.give(oak, new ItemStack(Items.OAK_PLANKS, 16), new ItemStack(Items.OAK_LOG, 8),
			new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.GLASS_PANE, 8), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.OAK_SLAB, 8), new ItemStack(Items.LADDER, 4), new ItemStack(Items.OAK_FENCE, 4),
			new ItemStack(Items.BREAD, 4));
		helper.assertTrue(oak.backpack().freeSlots() == 0, "Oak's backpack starts full");
		// Fetching from the chest is all he does here.
		long now = helper.getLevel().getGameTime();
		for (CompanionTask task : oak.scheduler().tasks()) {
			if (!task.id().equals("common.restock")) {
				oak.scheduler().cooldown(task.id(), now, 1400);
			}
		}
		List<String> slips = new ArrayList<>();
		helper.onEachTick(() -> {
			if (slips.size() < 4 && !oak.getMainHandItem().is(Items.WOODEN_AXE)) {
				slips.add("tick " + helper.getTick() + " holding " + oak.getMainHandItem());
			}
			if (slips.size() < 4 && SupplyChest.count(chest, s -> s.is(ItemTags.AXES)) > 0) {
				slips.add("tick " + helper.getTick() + " his axe was in the chest");
			}
		});
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(slips.isEmpty(), "with a full backpack Oak kept his axe: " + slips);
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.WOODEN_HOE)) == 1, "the hoe stayed in the chest");
			oak.backpack().remove(s -> s.is(Items.LADDER), 4); // a slot comes free
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 300, "waiting for a slot to come free");
			helper.assertTrue(slips.isEmpty(), "Oak kept his axe in hand throughout: " + slips);
			helper.assertTrue(oak.backpack().count(Items.WOODEN_HOE) == 1, "with a slot free Oak carries the hoe (he is "
				+ oak.activity() + ")");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.WOODEN_HOE)) == 0, "the hoe left the chest");
		});
	}

	// ------------------------------------------------------------- helpers for claims

	/** A hamlet with the cabin to build next: logs, glass and coal in the chest, and a crafting table beside it. */
	private static CampData cabinCamp(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(2);
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() < 2) {
				data.markCompleted(e.id());
			}
		}
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64),
			new ItemStack(Items.GLASS, 6), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		return data;
	}

	/** Rowan with her axe and her own gathering caught up, so she is free to stand in on building. */
	private static CompanionEntity freeRowan(GameTestHelper helper, BlockPos at) {
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, at);
		rowan.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		setAside(helper, rowan, 2400, "rowan.chop", "rowan.quarry", "rowan.forage");
		return rowan;
	}

	/** Saves a friend and loads them back as a new entity, as closing and opening the world again does. */
	private static CompanionEntity reload(GameTestHelper helper, CompanionEntity c) {
		ServerLevel level = helper.getLevel();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		c.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		c.discard();
		CompanionEntity copy = ModEntities.COMPANION.create(level, EntitySpawnReason.LOAD);
		if (copy == null) {
			throw new IllegalStateException("could not create companion");
		}
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		level.addFreshEntity(copy);
		return copy;
	}

	/**
	 * The open requests to hand a shared job back, by job id. They are private to {@link SpecialityTask}, and only a
	 * world switch leaves a foreign one behind, which a game test cannot do, so these tests reach in.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> handovers() {
		try {
			Field field = SpecialityTask.class.getDeclaredField("HANDOVER");
			field.setAccessible(true);
			return (Map<String, Object>) field.get(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot read the handover requests", e);
		}
	}

	/** Leaves a request to hand this job to a friend, stamped with the given game time. */
	private static void leaveHandover(String jobId, UUID to, long at) {
		try {
			for (Class<?> type : SpecialityTask.class.getDeclaredClasses()) {
				if (type.getSimpleName().equals("Handover")) {
					Constructor<?> make = type.getDeclaredConstructor(UUID.class, long.class);
					make.setAccessible(true);
					handovers().put(jobId, make.newInstance(to, at));
					return;
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot leave a handover request", e);
		}
		throw new IllegalStateException("no handover record in SpecialityTask");
	}
}

package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.sage.ReviewStoresTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ExploreTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ScoutLog;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
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
}

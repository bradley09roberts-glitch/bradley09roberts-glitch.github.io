package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.forage.QuarryTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/** Fern's farming and Rowan's foraging: harvest and replant, tilling, felling, quarrying and berry picking. */
public class FarmingGameTest {
	private static BlockState ripeWheat() {
		return Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
	}

	/** The first carried item matching a tool tag, in hand or in the backpack. */
	private static ItemStack carried(CompanionEntity c, TagKey<Item> tag) {
		if (c.getMainHandItem().is(tag)) {
			return c.getMainHandItem();
		}
		return c.backpack().find(s -> s.is(tag));
	}

	// ------------------------------------------------------------------ Fern

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_15", maxTicks = 1200)
	public void fernHarvestsRipeWheatAndReplants(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		List<BlockPos> crops = new ArrayList<>();
		for (int z = 14; z <= 18; z++) {
			BlockPos soil = new BlockPos(21, 1, z);
			helper.setBlock(soil, Blocks.FARMLAND);
			helper.setBlock(soil.above(), ripeWheat());
			crops.add(soil.above());
		}
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.WHEAT_SEEDS, 2));
		helper.succeedWhen(() -> {
			for (BlockPos p : crops) {
				BlockState s = helper.getBlockState(p);
				helper.assertTrue(s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) < 7, "wheat at " + p + " harvested and replanted (now " + s
					+ " on " + helper.getBlockState(p.below()) + "; Fern at " + helper.relativePos(fern.blockPosition()) + " doing "
					+ fern.activity() + " with " + fern.backpack().count(Items.WHEAT_SEEDS) + " seeds)");
				helper.assertTrue(helper.getBlockState(p.below()).is(Blocks.FARMLAND), "farmland kept at " + p.below());
			}
			helper.assertTrue(crops.stream().anyMatch(p -> helper.getBlockState(p).getValue(CropBlock.AGE) == 0), "freshly sown age-0 wheat present");
			helper.assertTrue(fern.backpack().count(Items.WHEAT) >= 5, "Fern carries the harvested wheat");
			helper.assertTrue(data.stat("crops_harvested") >= 5, "harvests counted");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_16", maxTicks = 1600)
	public void fernTillsGrassBesideWater(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		BlockPos water = new BlockPos(22, 1, 16);
		helper.setBlock(water, Blocks.WATER);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 8));
		helper.succeedWhen(() -> {
			int farmland = 0;
			int sown = 0;
			for (BlockPos p : BlockPos.betweenClosed(water.offset(-4, 0, -4), water.offset(4, 0, 4))) {
				if (helper.getBlockState(p).is(Blocks.FARMLAND)) {
					farmland++;
					if (helper.getBlockState(p.above()).is(Blocks.WHEAT)) {
						sown++;
					}
				}
			}
			helper.assertTrue(farmland >= 4, "at least four farmland tilled beside the water, found " + farmland);
			helper.assertTrue(sown >= 4, "the new farmland is sown, found " + sown);
			ItemStack hoe = carried(fern, ItemTags.HOES);
			helper.assertTrue(!hoe.isEmpty() && hoe.getDamageValue() >= 4, "the hoe wore down with use");
			helper.assertTrue(helper.getBlockState(water).is(Blocks.WATER), "the water is untouched");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_17", maxTicks = 500)
	public void fernLeavesUnripeWheat(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		List<BlockPos> crops = new ArrayList<>();
		for (int z = 14; z <= 18; z += 2) {
			BlockPos soil = new BlockPos(21, 1, z);
			helper.setBlock(soil, Blocks.FARMLAND);
			helper.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
			crops.add(soil.above());
		}
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.WHEAT_SEEDS, 6), new ItemStack(Items.WOODEN_HOE));
		helper.runAfterDelay(400, () -> {
			for (BlockPos p : crops) {
				BlockState s = helper.getBlockState(p);
				helper.assertTrue(s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) < 7, "unripe wheat left standing at " + p);
			}
			helper.assertTrue(fern.backpack().count(Items.WHEAT) == 0, "no wheat harvested");
			helper.assertTrue(data.stat("crops_harvested") == 0, "no harvest counted");
			helper.succeed();
		});
	}

	// ----------------------------------------------------------------- Rowan

	/** Puts the camp 30 blocks west of the plot centre, so the plot's east side lies in the gathering ring. */
	private static CampData campToTheWest(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, false);
		data.setCamp(helper.absolutePos(new BlockPos(-14, TestSupport.STAND_Y, 16)), Camp.dimensionId(helper.getLevel()));
		return data;
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_18", maxTicks = 1600)
	public void rowanFellsNaturalOakAndReplants(GameTestHelper helper) {
		CampData data = campToTheWest(helper);
		BlockPos base = new BlockPos(24, 2, 16);
		TestSupport.growOak(helper, base);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(16, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.OAK_SAPLING));
		helper.succeedWhen(() -> {
			for (int y = 1; y < 4; y++) {
				helper.assertFalse(helper.getBlockState(base.above(y)).is(BlockTags.LOGS), "log " + y + " of the oak felled");
			}
			helper.assertTrue(rowan.backpack().count(Items.OAK_LOG) >= 4, "Rowan carries the four logs");
			helper.assertTrue(helper.getBlockState(base).is(Blocks.OAK_SAPLING), "an oak sapling replanted on the stump's soil");
			helper.assertTrue(data.stat("trees_felled") >= 1, "felling counted");
			ItemStack axe = carried(rowan, ItemTags.AXES);
			helper.assertTrue(!axe.isEmpty() && axe.getDamageValue() > 0, "the axe wore down");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_19", maxTicks = 700)
	public void rowanSparesLogWallsAndCampTrees(GameTestHelper helper) {
		CampData data = campToTheWest(helper);
		// A natural oak inside the camp (Terra's greenery).
		BlockPos campTree = new BlockPos(5, 2, 16);
		TestSupport.growOak(helper, campTree);
		// A log wall and a log pillar dressed with player-placed leaves, both out in the gathering ring.
		List<BlockPos> built = new ArrayList<>();
		for (int x = 21; x <= 25; x++) {
			for (int y = 2; y <= 3; y++) {
				built.add(new BlockPos(x, y, 25));
			}
		}
		for (int y = 2; y <= 5; y++) {
			built.add(new BlockPos(26, y, 8));
		}
		for (BlockPos p : built) {
			helper.setBlock(p, Blocks.SPRUCE_LOG);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(new BlockPos(26 + dx, 5, 8 + dz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
				}
			}
		}
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(18, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.OAK_SAPLING));
		helper.runAfterDelay(600, () -> {
			for (int y = 0; y < 4; y++) {
				helper.assertTrue(helper.getBlockState(campTree.above(y)).is(Blocks.OAK_LOG), "camp oak log " + y + " untouched");
			}
			for (BlockPos p : built) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.SPRUCE_LOG), "built log at " + p + " untouched");
			}
			helper.assertTrue(rowan.backpack().count(ItemTags.LOGS) == 0, "Rowan gathered no logs");
			helper.assertTrue(data.stat("trees_felled") == 0, "no felling counted");
			helper.succeed();
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_20", maxTicks = 2400)
	public void rowanQuarryStaysInFiveByFiveTwoDeep(GameTestHelper helper) {
		CampData data = campToTheWest(helper);
		// A stone floor under the floating test plot, so the lower layer has solid ground beneath it.
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 32; z++) {
				helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
			}
		}
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(24, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.STONE_SHOVEL));
		BlockPos camp = data.campPos().orElseThrow();
		helper.succeedWhen(() -> {
			helper.assertTrue(data.stat("blocks_quarried") >= 30, "Rowan quarried 30 blocks, so far " + data.stat("blocks_quarried"));
			BlockPos corner = QuarryTask.corner(data);
			helper.assertTrue(corner != null, "an active quarry is remembered");
			int inner = Camp.radius(data) + 4;
			for (int dx = 0; dx < 5; dx++) {
				for (int dz = 0; dz < 5; dz++) {
					BlockPos col = corner.offset(dx, 0, dz);
					helper.assertTrue(Camp.horizontalDistSqr(camp, col) >= (double) inner * inner, "quarry column " + col + " lies well outside the camp");
				}
			}
			int removed = 0;
			for (int x = 0; x < 32; x++) {
				for (int z = 0; z < 32; z++) {
					for (int y = -1; y <= 1; y++) {
						BlockPos rel = new BlockPos(x, y, z);
						if (!helper.getBlockState(rel).isAir()) {
							continue;
						}
						BlockPos abs = helper.absolutePos(rel);
						boolean inside = abs.getX() >= corner.getX() && abs.getX() < corner.getX() + 5
							&& abs.getZ() >= corner.getZ() && abs.getZ() < corner.getZ() + 5
							&& abs.getY() <= corner.getY() && abs.getY() >= corner.getY() - (QuarryTask.LAYERS - 1);
						helper.assertTrue(inside, "dug block " + rel + " lies inside the 5x5 quarry, at most 2 deep");
						removed++;
					}
				}
			}
			helper.assertTrue(removed >= 30, "the pit holds the quarried blocks, found " + removed);
			helper.assertTrue(rowan.backpack().count(Items.DIRT) >= 30, "Rowan carries the dirt");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_21", maxTicks = 1200)
	public void rowanPicksRipeBerries(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		BlockPos ripe = new BlockPos(20, 2, 16);
		BlockPos ripening = new BlockPos(12, 2, 20);
		BlockPos young = new BlockPos(16, 2, 10);
		helper.setBlock(ripe, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
		helper.setBlock(ripening, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 2));
		helper.setBlock(young, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1));
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, TestSupport.centre());
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(ripe).getValue(SweetBerryBushBlock.AGE) == 1, "the full bush was picked");
			helper.assertTrue(helper.getBlockState(ripening).getValue(SweetBerryBushBlock.AGE) == 1, "the half-grown bush was picked");
			helper.assertTrue(helper.getBlockState(young).is(Blocks.SWEET_BERRY_BUSH), "the young bush still stands");
			int berries = rowan.backpack().count(Items.SWEET_BERRIES);
			helper.assertTrue(berries >= 3 && berries <= 5, "Rowan carries 3-5 berries, has " + berries);
			helper.assertTrue(data.stat("berries_picked") == berries, "berries counted");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_22", maxTicks = 1200)
	public void rowanDeliversWoodToOak(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(8, 2, 16));
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(24, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.OAK_LOG, 20), new ItemStack(Items.DIRT, 5));
		CampNeeds.reportBuildShortage(helper.getLevel(), Map.of(CampNeeds.Need.WOOD, 20), "20 planks");
		helper.succeedWhen(() -> {
			helper.assertTrue(rowan.backpack().count(ItemTags.LOGS) == 0, "Rowan handed over every log");
			helper.assertTrue(rowan.backpack().count(Items.DIRT) == 5, "Rowan kept the dirt nobody asked for");
			helper.assertTrue(oak.backpack().count(ItemTags.LOGS) + oak.backpack().count(ItemTags.PLANKS) > 0, "Oak received the wood");
			helper.assertTrue(data.stat("materials_delivered") >= 20, "the delivery was counted");
			helper.assertTrue(data.unity() >= 2, "a hand-off strengthens the bond");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_23", maxTicks = 3600)
	public void fernLaysOutFarmPlotWithBucket(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(1);
		Container chest = TestSupport.placeChest(helper, new BlockPos(16, 2, 12), new ItemStack(Items.WATER_BUCKET));
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.WHEAT_SEEDS, 24));
		helper.succeedWhen(() -> {
			helper.assertTrue(data.site(Structures.FARM_PLOT).isPresent(), "the farm plot site is reserved");
			BlockPos centre = data.site(Structures.FARM_PLOT).get().origin;
			helper.assertTrue(helper.getLevel().getBlockState(centre).is(Blocks.WATER), "water poured at the plot centre");
			helper.assertTrue(fern.backpack().count(Items.BUCKET) == 1, "the bucket came back empty");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.WATER_BUCKET)) == 0, "the water bucket left the chest");
			int farmland = 0;
			for (BlockPos p : BlockPos.betweenClosed(centre.offset(-4, 0, -4), centre.offset(4, 0, 4))) {
				if (helper.getLevel().getBlockState(p).is(Blocks.FARMLAND)) {
					farmland++;
				}
			}
			helper.assertTrue(farmland >= 16, "16 farmland tilled around the water, found " + farmland);
			helper.assertTrue(data.isCompleted(Structures.FARM_PLOT), "the farm plot counts as built");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_24", maxTicks = 1600)
	public void fernBakesBreadAndTendsSeedlings(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		helper.setBlock(new BlockPos(20, 2, 20), Blocks.CRAFTING_TABLE);
		List<BlockPos> crops = new ArrayList<>();
		for (int z = 10; z <= 12; z++) {
			BlockPos soil = new BlockPos(11, 1, z);
			helper.setBlock(soil, Blocks.FARMLAND);
			helper.setBlock(soil.above(), Blocks.WHEAT);
			crops.add(soil.above());
		}
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.WHEAT, 7), new ItemStack(Items.BONE_MEAL, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(data.stat("bread_baked") >= 2 && fern.backpack().count(Items.BREAD) >= 2, "two loaves baked from seven wheat");
			helper.assertTrue(data.stat("bone_meal_used") >= 1, "bone meal spent on the seedlings");
			helper.assertTrue(fern.backpack().count(Items.BONE_MEAL) == 3 - data.stat("bone_meal_used"), "one bone meal per use");
			boolean grown = data.stat("crops_harvested") > 0;
			for (BlockPos p : crops) {
				BlockState s = helper.getBlockState(p);
				grown |= s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) > 0;
			}
			helper.assertTrue(grown, "a seedling grew");
		});
	}
}

package io.github.bradley09roberts.hardcorefriends.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.SageAdvisor;
import io.github.bradley09roberts.hardcorefriends.ai.role.sage.ReviewStoresTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/** Behaviour tests for Aegis (warrior), Sage (strategist) and Terra (landscaper). */
public class GuardianGameTest {
	// ------------------------------------------------------------------ Aegis

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_45", maxTicks = 1200)
	public void aegisEquipsBetterGearFromChest(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		Container chest = TestSupport.placeChest(helper, new BlockPos(20, 2, 16),
			new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.LEATHER_BOOTS));
		CompanionEntity aegis = TestSupport.spawnFriend(helper, FriendId.AEGIS, new BlockPos(10, 2, 16));
		aegis.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		aegis.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
		aegis.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		helper.succeedWhen(() -> {
			helper.assertTrue(aegis.getMainHandItem().is(Items.IRON_SWORD), "Aegis holds the iron sword, had " + aegis.getMainHandItem());
			helper.assertTrue(aegis.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "Aegis wears the iron chestplate");
			helper.assertTrue(aegis.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "worse leather boots are left in the chest");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.WOODEN_SWORD)) == 1, "the old sword went back into the chest");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.LEATHER_CHESTPLATE)) == 1, "the old chestplate went back into the chest");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.IRON_SWORD) || s.is(Items.IRON_CHESTPLATE)) == 0, "no duplicated gear");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.LEATHER_BOOTS)) == 1, "leather boots stay in the chest");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_46", maxTicks = 1200)
	public void aegisDefendsFriendFromZombie(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 18000); // night, so the zombie does not burn in the sun
		TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(20, 2, 16));
		CompanionEntity aegis = TestSupport.spawnFriend(helper, FriendId.AEGIS, new BlockPos(10, 2, 16));
		aegis.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(27, 2, 16));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.succeedWhen(() -> helper.assertTrue(zombie.getLastHurtByMob() == aegis
			&& (zombie.isDeadOrDying() || zombie.getHealth() < zombie.getMaxHealth()), "Aegis hurts the zombie closing in on Fern"));
	}

	// ------------------------------------------------------------------- Sage

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_47", maxTicks = 100)
	@SuppressWarnings("removal")
	public void sageAdvisesHungryPlayerToEat(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getFoodData().setFoodLevel(4);
		String advice = SageAdvisor.advice(player);
		SageAdvisor.Importance importance = SageAdvisor.consult(player).importance();
		player.getFoodData().setFoodLevel(20);
		player.setHealth(6);
		String hurtAdvice = SageAdvisor.advice(player);
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.assertTrue(advice.contains("hungry") && advice.contains("Eat"), "food advice for a hungry player, got: " + advice);
		helper.assertTrue(importance == SageAdvisor.Importance.SURVIVAL, "hunger is urgent advice");
		helper.assertTrue(hurtAdvice.contains("badly hurt"), "health advice comes first, got: " + hurtAdvice);
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_48", maxTicks = 2000)
	public void sageReviewsTheStores(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		TestSupport.placeChest(helper, new BlockPos(22, 2, 22), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.BREAD, 40));
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, new BlockPos(8, 2, 8));
		helper.succeedWhen(() -> {
			helper.assertTrue(data.memory(ReviewStoresTask.MEMORY).contains("at"), "Sage reviewed the stores");
			helper.assertTrue(sage.distanceToSqr(helper.absoluteVec(new net.minecraft.world.phys.Vec3(22.5, 2, 22.5))) < 5 * 5,
				"Sage went to the supply chest");
			helper.assertTrue(CampNeeds.stock(CampNeeds.Need.WOOD) >= 256, "the review counted the logs in the chest");
			helper.assertTrue(CampNeeds.focus() != CampNeeds.Need.WOOD, "wood is not the focus with a full stack of logs");
		});
	}

	// ------------------------------------------------------------------ Terra

	/** Counts blocks in the plot (relative y 1 to 4) that match a test. */
	private static int countInPlot(GameTestHelper helper, java.util.function.Predicate<BlockState> test) {
		int n = 0;
		for (int x = 0; x < 32; x++) {
			for (int z = 0; z < 32; z++) {
				for (int y = 1; y <= 4; y++) {
					if (test.test(helper.getBlockState(new BlockPos(x, y, z)))) {
						n++;
					}
				}
			}
		}
		return n;
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_49", maxTicks = 1200)
	public void terraLaysDirtPathToSite(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(1);
		data.putSite(Structures.CABIN, new CampData.Site(helper.absolutePos(new BlockPos(16, 2, 27)), 0, 0));
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(12, 2, 16));
		ItemStack shovel = new ItemStack(Items.WOODEN_SHOVEL);
		terra.setItemSlot(EquipmentSlot.MAINHAND, shovel);
		helper.succeedWhen(() -> {
			int paths = 0;
			for (int z = 16; z <= 26; z++) {
				if (helper.getBlockState(new BlockPos(16, 1, z)).is(Blocks.DIRT_PATH)) {
					paths++;
				}
			}
			helper.assertTrue(paths >= 6, "Terra laid at least 6 path blocks towards the cabin site, laid " + paths
				+ " (" + terra.activity() + ")");
			ItemStack held = terra.getMainHandItem().is(ItemTags.SHOVELS) ? terra.getMainHandItem()
				: terra.backpack().find(s -> s.is(ItemTags.SHOVELS));
			helper.assertTrue(held.getDamageValue() >= paths, "the shovel wore down by one per path block, damage "
				+ held.getDamageValue());
			helper.assertTrue(helper.getBlockState(new BlockPos(20, 1, 20)).is(Blocks.GRASS_BLOCK), "grass off the path is untouched");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_50", maxTicks = 1200)
	public void terraLightsDarkCampAtNight(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 18000);
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(14, 2, 14));
		terra.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		TestSupport.give(terra, new ItemStack(Items.TORCH, 8));
		helper.succeedWhen(() -> {
			int torches = countInPlot(helper, s -> s.is(Blocks.TORCH));
			helper.assertTrue(torches >= 2, "Terra placed torches in the dark camp, placed " + torches + " ("
				+ terra.activity() + " at " + helper.relativePos(terra.blockPosition()) + ")");
			helper.assertTrue(terra.backpack().count(Items.TORCH) <= 8 - torches, "each torch came out of the backpack");
			helper.assertTrue(helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(new BlockPos(16, 2, 16))) >= 8
				|| torches >= 3, "the camp centre is lit");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_51", maxTicks = 1200)
	public void terraPlantsSaplingOnTheRing(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, false);
		// The camp centre lies 20 blocks west of the plot centre, so the planting ring (18 to 22 blocks out) crosses the plot.
		BlockPos campCentre = helper.absolutePos(new BlockPos(-4, 2, 16));
		data.setCamp(campCentre, Camp.dimensionId(helper.getLevel()));
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(16, 2, 16));
		terra.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		TestSupport.give(terra, new ItemStack(Items.OAK_SAPLING, 4));
		helper.succeedWhen(() -> {
			int onRing = 0;
			for (int x = 0; x < 32; x++) {
				for (int z = 0; z < 32; z++) {
					BlockPos rel = new BlockPos(x, 2, z);
					BlockState s = helper.getBlockState(rel);
					if (s.is(BlockTags.SAPLINGS) || s.is(BlockTags.LOGS)) {
						double d = Math.sqrt(Camp.horizontalDistSqr(helper.absolutePos(rel), campCentre));
						helper.assertTrue(d >= 17.5 && d <= 22.5, "sapling at " + rel + " is on the ring (distance " + d + ")");
						onRing++;
					}
				}
			}
			helper.assertTrue(onRing >= 1, "Terra planted a sapling (" + terra.activity() + ")");
			helper.assertTrue(terra.backpack().count(Items.OAK_SAPLING) == 4 - onRing, "each sapling came out of the backpack");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_52", maxTicks = 1200)
	public void terraFillsHoleWithDirt(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		BlockPos hole = new BlockPos(19, 1, 18);
		helper.setBlock(hole, Blocks.AIR);
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(12, 2, 12));
		terra.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		TestSupport.give(terra, new ItemStack(Items.DIRT, 4));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(hole).is(Blocks.DIRT), "the hole was filled with dirt (" + terra.activity() + ")");
			helper.assertTrue(terra.backpack().count(Items.DIRT) == 3, "one dirt came out of the backpack");
		});
	}

	/** Lays out a farm on the plot's ground: farmland round one water block, tilled by the friends or by a player. */
	private static void farm(GameTestHelper helper, CampData data, int x0, int x1, int z0, int z1, int waterX, int waterZ,
		boolean friends) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				BlockPos rel = new BlockPos(x, 1, z);
				boolean water = x == waterX && z == waterZ;
				helper.setBlock(rel, water ? Blocks.WATER : Blocks.FARMLAND);
				if (friends && !water) {
					data.recordPlaced(helper.absolutePos(rel));
				}
			}
		}
	}

	/** Counts fences and gates on the ring one block outside a farm's bounding box, at fence height. */
	private static int[] ring(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		int fences = 0;
		int gates = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x != x0 && x != x1 && z != z0 && z != z1) {
					continue;
				}
				BlockState s = helper.getBlockState(new BlockPos(x, 2, z));
				if (s.is(BlockTags.FENCE_GATES)) {
					gates++;
				} else if (s.is(BlockTags.FENCES)) {
					fences++;
				}
			}
		}
		return new int[] {fences, gates};
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_53", maxTicks = 2400)
	public void terraFencesTheFarm(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(3);
		farm(helper, data, 14, 18, 6, 10, 16, 8, true);
		data.putSite(Structures.FARM_PLOT, new CampData.Site(helper.absolutePos(new BlockPos(16, 2, 8)), 0, 0));
		data.markCompleted(Structures.FARM_PLOT);
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(16, 2, 14));
		terra.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		TestSupport.give(terra, new ItemStack(Items.OAK_FENCE, 30), new ItemStack(Items.OAK_FENCE_GATE, 1));
		helper.succeedWhen(() -> {
			int fences = 0;
			int gates = 0;
			for (int x = 13; x <= 19; x++) {
				for (int z = 5; z <= 11; z++) {
					if (x != 13 && x != 19 && z != 5 && z != 11) {
						continue;
					}
					BlockState s = helper.getBlockState(new BlockPos(x, 2, z));
					if (s.is(BlockTags.FENCE_GATES)) {
						gates++;
					} else if (s.is(BlockTags.FENCES)) {
						fences++;
					}
				}
			}
			helper.assertTrue(fences == 23 && gates == 1, "the farm is ringed by 23 fences and one gate, found " + fences
				+ " fences and " + gates + " gates (" + terra.activity() + ")");
			BlockState gate = helper.getBlockState(new BlockPos(16, 2, 11));
			helper.assertTrue(gate.is(BlockTags.FENCE_GATES), "the gate faces the camp centre");
			helper.assertTrue(gate.getValue(FenceGateBlock.OPEN), "the gate is left open so friends can walk through");
			helper.assertTrue(terra.backpack().count(Items.OAK_FENCE) == 7, "fences came out of the backpack");
			helper.assertTrue(data.isCompleted(Structures.FARM_FENCE), "the farm fence is finished");
			helper.assertTrue(helper.getBlockState(new BlockPos(16, 1, 8)).is(Blocks.WATER), "the farm itself is untouched");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_54", maxTicks = 2400)
	public void terraCraftsFencesFromChestWood(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(3);
		farm(helper, data, 8, 10, 6, 8, 9, 7, true);
		data.putSite(Structures.FARM_PLOT, new CampData.Site(helper.absolutePos(new BlockPos(9, 2, 7)), 0, 0));
		data.markCompleted(Structures.FARM_PLOT);
		Container chest = TestSupport.placeChest(helper, new BlockPos(22, 2, 20),
			new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.STICK, 32));
		helper.setBlock(new BlockPos(22, 2, 23), Blocks.CRAFTING_TABLE);
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(16, 2, 14));
		terra.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		helper.succeedWhen(() -> {
			int fences = 0;
			int gates = 0;
			for (int x = 7; x <= 11; x++) {
				for (int z = 5; z <= 9; z++) {
					if (x != 7 && x != 11 && z != 5 && z != 9) {
						continue;
					}
					BlockState s = helper.getBlockState(new BlockPos(x, 2, z));
					if (s.is(BlockTags.FENCE_GATES)) {
						gates++;
					} else if (s.is(BlockTags.FENCES)) {
						fences++;
					}
				}
			}
			helper.assertTrue(fences == 15 && gates == 1, "the small farm is ringed by 15 fences and one gate, found " + fences
				+ " fences and " + gates + " gates (" + terra.activity() + ")");
			int planksUsed = 64 - SupplyChest.count(chest, s -> s.is(Items.OAK_PLANKS));
			helper.assertTrue(planksUsed >= 22, "fences and the gate were crafted from the chest's planks, used " + planksUsed);
			helper.assertTrue(data.isCompleted(Structures.FARM_FENCE), "the farm fence is finished");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_77", maxTicks = 1200)
	public void terraLeavesPlayersFieldsUnfenced(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(3);
		// The player's own field (not tilled by the friends) is what got the farm plot counted.
		farm(helper, data, 14, 18, 6, 10, 16, 8, false);
		data.markCompleted(Structures.FARM_PLOT);
		data.markCompleted(Structures.ANIMAL_PEN); // the pen would rightly take these fences (see LivestockGameTest)
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(16, 2, 14));
		terra.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		TestSupport.give(terra, new ItemStack(Items.OAK_FENCE, 30), new ItemStack(Items.OAK_FENCE_GATE, 1));
		helper.startSequence()
			.thenIdle(400)
			.thenExecute(() -> {
				int[] found = ring(helper, 13, 19, 5, 11);
				helper.assertTrue(found[0] == 0 && found[1] == 0, "the player's field is not fenced, found " + found[0] + " fences and "
					+ found[1] + " gates (" + terra.activity() + ")");
				helper.assertTrue(terra.backpack().count(Items.OAK_FENCE) == 30, "no fence left the backpack");
				helper.assertTrue(data.isCompleted(Structures.FARM_FENCE), "with no farm of the friends' own, the fence does not hold the camp back");
			})
			.thenSucceed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_78", maxTicks = 2400)
	public void terraKeepsFencePostsOffPlayersPath(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(3);
		farm(helper, data, 14, 18, 6, 10, 16, 8, true);
		data.putSite(Structures.FARM_PLOT, new CampData.Site(helper.absolutePos(new BlockPos(16, 2, 8)), 0, 0));
		data.markCompleted(Structures.FARM_PLOT);
		// A player's path leading east out of the farm, across the fence line.
		for (int x = 19; x <= 22; x++) {
			helper.setBlock(new BlockPos(x, 1, 8), Blocks.DIRT_PATH);
		}
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, new BlockPos(16, 2, 14));
		terra.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SHOVEL));
		TestSupport.give(terra, new ItemStack(Items.OAK_FENCE, 30), new ItemStack(Items.OAK_FENCE_GATE, 1));
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.FARM_FENCE), "the farm fence is finished (" + terra.activity() + ")");
			for (int z = 6; z <= 10; z++) {
				BlockPos cell = new BlockPos(19, 2, z);
				helper.assertTrue(helper.getBlockState(cell).isAir(), "no post on or within two blocks of the player's path at " + cell);
			}
			for (int x = 19; x <= 22; x++) {
				helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, 8)).is(Blocks.DIRT_PATH), "the player's path is untouched");
			}
			int[] found = ring(helper, 13, 19, 5, 11);
			helper.assertTrue(found[0] == 18 && found[1] == 1, "the rest of the ring has 18 fences and one gate, found " + found[0]
				+ " fences and " + found[1] + " gates");
		});
	}
}

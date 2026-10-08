package io.github.bradley09roberts.hardcorefriends.test;

import java.util.Map;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;

/** Common upkeep every friend shares: depositing, restocking, crafting tools, sharing, night return and tidying. */
public class CommonGameTest {
	private static final BlockPos CHEST = new BlockPos(19, 2, 16);

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_06", maxTicks = 1200)
	public void depositsSurplusButKeepsTools(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		Container chest = TestSupport.placeChest(helper, CHEST);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		// Nearly full backpack: 8 of 9 slots, so the trip is urgent upkeep.
		TestSupport.give(flint, new ItemStack(Items.COBBLESTONE, 40), new ItemStack(Items.STONE_PICKAXE),
			new ItemStack(Items.DIRT, 16), new ItemStack(Items.GRAVEL, 16), new ItemStack(Items.ANDESITE, 16),
			new ItemStack(Items.DIORITE, 16), new ItemStack(Items.GRANITE, 16), new ItemStack(Items.FLINT, 5));
		helper.succeedWhen(() -> {
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.COBBLESTONE)) == 40, "40 cobblestone in the chest");
			helper.assertTrue(flint.backpack().count(Items.COBBLESTONE) == 0, "no cobblestone left in the backpack");
			helper.assertTrue(flint.actions().hasTool(ItemTags.PICKAXES), "Flint kept the pickaxe");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.STONE_PICKAXE)) == 0, "the pickaxe was not deposited");
			helper.assertTrue(Camp.data(helper.getLevel().getServer()).stat("deposits") >= 1, "deposit recorded");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_07", maxTicks = 1200)
	public void restocksBestToolFromChest(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.WOODEN_PICKAXE),
			new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.BREAD, 8));
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		flint.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
		helper.succeedWhen(() -> {
			helper.assertTrue(flint.getMainHandItem().is(Items.STONE_PICKAXE), "Flint holds the stone pickaxe");
			helper.assertTrue(flint.backpack().count(Items.WOODEN_SWORD) == 1, "the sword went into the backpack");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.WOODEN_PICKAXE)) == 1, "the weaker pickaxe stays in the chest");
			helper.assertTrue(KeepList.foodCount(flint.backpack()) == KeepList.FOOD_KEPT, "food topped up to 4");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.BREAD)) == 4, "4 bread left in the chest");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_08", maxTicks = 1200)
	public void craftsStonePickaxeAtTable(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		helper.setBlock(new BlockPos(16, 2, 21), Blocks.CRAFTING_TABLE);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.STICK, 2));
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		helper.succeedWhen(() -> {
			helper.assertTrue(flint.getMainHandItem().is(Items.STONE_PICKAXE), "Flint crafted and holds a stone pickaxe");
			helper.assertTrue(SupplyChest.count(chest, s -> s.is(Items.COBBLESTONE) || s.is(Items.STICK)) == 0,
				"the cobblestone and sticks were used");
			helper.assertTrue(flint.backpack().count(s -> s.is(Items.COBBLESTONE) || s.is(Items.STICK)) == 0,
				"no ingredients left over");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_09", maxTicks = 1200)
	public void sharesLogsWithBuilderDuringShortage(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(10, 2, 16));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(22, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.STONE_AXE));
		CampNeeds.reportBuildShortage(helper.getLevel(), Map.of(CampNeeds.Need.WOOD, 32), "32 logs");
		helper.succeedWhen(() -> {
			helper.assertTrue(rowan.backpack().count(Items.OAK_LOG) < 32, "Rowan handed over logs: rowan " + rowan.activity() + " at " + helper.relativePos(rowan.blockPosition()) + " oak " + oak.activity() + " at " + helper.relativePos(oak.blockPosition()) + " shortage " + CampNeeds.buildShortage());
			int oakWood = oak.backpack().count(Items.OAK_LOG) * 4 + oak.backpack().count(Items.OAK_PLANKS);
			helper.assertTrue(oakWood > 0, "Oak received the wood");
			helper.assertTrue(rowan.actions().hasTool(ItemTags.AXES), "Rowan kept the axe");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_10", maxTicks = 1200)
	public void returnsHomeAtNight(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.setTime(helper, 13000);
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, new BlockPos(1, 2, 1));
		BlockPos home = helper.absolutePos(TestSupport.centre());
		double start = Math.sqrt(Camp.horizontalDistSqr(sage.blockPosition(), home));
		helper.assertTrue(start > 20, "starts outside the comfortable part of camp (" + start + ")");
		helper.succeedWhen(() -> {
			helper.assertTrue(Camp.isNight(helper.getLevel()) || Camp.isDusk(helper.getLevel()), "it is evening");
			double d = Math.sqrt(Camp.horizontalDistSqr(sage.blockPosition(), home));
			helper.assertTrue(d < 12, "Sage came home (distance " + d + ")");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_11", maxTicks = 1200)
	public void collectsAgedItems(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		buildPen(helper);
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, TestSupport.centre());
		ItemEntity cobble = dropItem(helper, new ItemStack(Items.COBBLESTONE, 8), new Vec3(18.5, 2.0, 18.5));
		helper.runAtTickTime(150, () -> helper.assertTrue(cobble.isAlive() && sage.backpack().count(Items.COBBLESTONE) == 0,
			"fresh items are left alone"));
		helper.succeedWhen(() -> {
			helper.assertTrue(!cobble.isAlive(), "the item was picked up");
			helper.assertTrue(sage.backpack().count(Items.COBBLESTONE) == 8, "all 8 cobblestone are in Sage's backpack");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_12", maxTicks = 1200)
	public void neverCollectsBackpacksOrPlayerDrops(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		buildPen(helper);
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, TestSupport.centre());
		ItemEntity backpack = dropItem(helper, new ItemStack(ModItems.BACKPACK), new Vec3(14.5, 2.0, 14.5));
		ItemEntity thrown = dropItem(helper, new ItemStack(Items.DIRT, 4), new Vec3(14.5, 2.0, 18.5));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		thrown.setThrower(player);
		ItemEntity cobble = dropItem(helper, new ItemStack(Items.COBBLESTONE, 3), new Vec3(18.5, 2.0, 18.5));
		helper.succeedWhen(() -> {
			helper.assertTrue(backpack.isAlive(), "a backpack on the ground is never taken");
			helper.assertTrue(thrown.isAlive(), "items a player threw are left for the player");
			helper.assertTrue(!cobble.isAlive(), "ordinary items are still tidied");
			helper.assertTrue(sage.backpack().count(ModItems.BACKPACK) == 0, "no backpack in Sage's backpack");
			helper.assertTrue(helper.getTick() >= 700, "waiting long enough to be sure");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_13", maxTicks = 1200)
	public void sharesFoodWithHurtFriend(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(10, 2, 16));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(22, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.BREAD, 6));
		oak.setHealth(oak.getMaxHealth() * 0.5F);
		helper.succeedWhen(() -> {
			helper.assertTrue(rowan.backpack().count(Items.BREAD) < 6, "Rowan gave bread away: rowan " + rowan.activity() + " at " + helper.relativePos(rowan.blockPosition()) + " oak " + oak.activity() + " at " + helper.relativePos(oak.blockPosition()) + " hp " + oak.getHealth());
			helper.assertTrue(rowan.backpack().count(Items.BREAD) >= 1, "Rowan kept some bread");
			helper.assertTrue(oak.backpack().count(Items.BREAD) > 0 || oak.getHealth() > oak.getMaxHealth() * 0.5F,
				"Oak received food");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_14", maxTicks = 100)
	public void keepListsSplitSurplus(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(8, 2, 8));
		TestSupport.give(oak, new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 16),
			new ItemStack(Items.BREAD, 10), new ItemStack(Items.STONE_AXE), new ItemStack(Items.WOODEN_PICKAXE));
		helper.assertTrue(surplus(oak, Items.OAK_PLANKS) == 16, "Oak keeps 64 planks");
		helper.assertTrue(surplus(oak, Items.BREAD) == 6, "Oak keeps 4 food");
		helper.assertTrue(surplus(oak, Items.STONE_AXE) == 0, "Oak keeps the role tool");
		helper.assertTrue(surplus(oak, Items.WOODEN_PICKAXE) == 1, "a pickaxe is surplus for a builder");
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(24, 2, 24));
		TestSupport.give(fern, new ItemStack(Items.CARROT, 64), new ItemStack(Items.CARROT, 6), new ItemStack(Items.WHEAT_SEEDS, 20));
		helper.assertTrue(surplus(fern, Items.CARROT) == 2, "Fern keeps 64 carrots to plant plus 4 to eat");
		helper.assertTrue(surplus(fern, Items.WHEAT_SEEDS) == 0, "Fern keeps her seeds");
		helper.assertTrue(KeepList.isUseful(Role.MINER, new ItemStack(Items.COAL)), "coal is useful to a miner");
		helper.assertFalse(KeepList.isUseful(Role.STRATEGIST, new ItemStack(Items.COAL)), "coal is not useful to a strategist");
		helper.assertTrue(KeepList.roleTool(Role.STRATEGIST) == null, "Sage needs no tool");
		helper.succeed();
	}

	// ----------------------------------------------------------------- helpers

	private static int surplus(CompanionEntity c, Item item) {
		Backpack bp = c.backpack();
		int[] surplus = KeepList.surplusBySlot(bp, c.friendId().role());
		int total = 0;
		for (int slot = 0; slot < surplus.length; slot++) {
			if (bp.get(slot).is(item)) {
				total += surplus[slot];
			}
		}
		return total;
	}

	private static ItemEntity dropItem(GameTestHelper helper, ItemStack stack, Vec3 relative) {
		ServerLevel level = helper.getLevel();
		Vec3 abs = helper.absoluteVec(relative);
		ItemEntity item = new ItemEntity(level, abs.x, abs.y, abs.z, stack);
		item.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(item);
		return item;
	}

	/** A 7×7 cobblestone pen around the camp centre so the friend stays near the dropped items. */
	private static void buildPen(GameTestHelper helper) {
		for (int i = 12; i <= 20; i++) {
			for (int y = 2; y <= 3; y++) {
				helper.setBlock(new BlockPos(i, y, 12), Blocks.COBBLESTONE);
				helper.setBlock(new BlockPos(i, y, 20), Blocks.COBBLESTONE);
				helper.setBlock(new BlockPos(12, y, i), Blocks.COBBLESTONE);
				helper.setBlock(new BlockPos(20, y, i), Blocks.COBBLESTONE);
			}
		}
	}
}

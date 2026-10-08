package io.github.bradley09roberts.hardcorefriends.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;
import io.github.bradley09roberts.hardcorefriends.world.TreeFinder;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/** Core safety rules: bounded, build-preserving world edits; natural tree detection; backpack drop on death. */
public class CoreGameTest {
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_01", maxTicks = 100)
	public void guardPreservesPlayerBuilds(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre());
		helper.setBlock(new BlockPos(20, 2, 16), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(21, 2, 16), Blocks.CHEST);
		helper.setBlock(new BlockPos(18, 2, 18), Blocks.SHORT_GRASS);
		BlockPos planks = helper.absolutePos(new BlockPos(20, 2, 16));
		BlockPos chest = helper.absolutePos(new BlockPos(21, 2, 16));
		BlockPos grass = helper.absolutePos(new BlockPos(18, 2, 18));
		for (Reason r : Reason.values()) {
			helper.assertFalse(WorldEditGuard.canBreak(oak, planks, r).allowed(), "planks must never be broken (" + r + ")");
			helper.assertFalse(WorldEditGuard.canBreak(oak, chest, r).allowed(), "chests must never be broken (" + r + ")");
		}
		helper.assertTrue(WorldEditGuard.canBreak(oak, grass, Reason.BUILD).allowed(), "short grass in camp may be cleared for building");
		helper.assertFalse(WorldEditGuard.canPlace(oak, planks, Blocks.COBBLESTONE.defaultBlockState(), Reason.BUILD).allowed(),
			"cannot place into an occupied block");
		BlockPos ground = helper.absolutePos(new BlockPos(17, 1, 17));
		helper.assertFalse(WorldEditGuard.canBreak(oak, ground, Reason.BUILD).allowed(), "builders do not dig up the ground");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_02", maxTicks = 100)
	public void guardBoundsByZone(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, TestSupport.centre());
		BlockPos camp = data.campPos().orElseThrow();
		int radius = Camp.radius(data);
		BlockPos farAway = camp.offset(radius + io.github.bradley09roberts.hardcorefriends.config.FriendsConfig.get().resourceRadius + 20, 0, 0);
		helper.assertFalse(WorldEditGuard.inResourceZone(rowan, farAway), "beyond the resource ring is off limits");
		helper.assertTrue(WorldEditGuard.inCamp(rowan, camp.offset(3, 0, 3)), "near the centre is in camp");
		BlockPos inCampDirt = helper.absolutePos(new BlockPos(16, 1, 18));
		helper.assertFalse(WorldEditGuard.canBreak(rowan, inCampDirt, Reason.GATHER_EARTH).allowed(), "no quarrying inside the camp");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_03", maxTicks = 100)
	public void naturalTreesOnly(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.growOak(helper, new BlockPos(6, 2, 6));
		// A log "pillar" with persistent (player-placed) leaves is not a natural tree.
		for (int y = 2; y < 6; y++) {
			helper.setBlock(new BlockPos(24, y, 24), Blocks.OAK_LOG);
		}
		helper.setBlock(new BlockPos(25, 5, 24), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		// A bare log wall is not a tree either.
		for (int x = 20; x < 24; x++) {
			helper.setBlock(new BlockPos(x, 2, 8), Blocks.SPRUCE_LOG);
		}
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(TreeFinder.isNaturalTreeLog(helper.getLevel(), helper.absolutePos(new BlockPos(6, 2, 6))), "grown oak is natural");
			helper.assertFalse(TreeFinder.isNaturalTreeLog(helper.getLevel(), helper.absolutePos(new BlockPos(24, 2, 24))), "persistent leaves = built");
			helper.assertFalse(TreeFinder.isNaturalTreeLog(helper.getLevel(), helper.absolutePos(new BlockPos(21, 2, 8))), "log wall = built");
			helper.succeed();
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_04", maxTicks = 100)
	public void deathDropsBackpackWithEverything(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		flint.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
		TestSupport.give(flint, new ItemStack(Items.RAW_IRON, 5), new ItemStack(Items.COAL, 9));
		flint.kill(helper.getLevel());
		helper.runAfterDelay(2, () -> {
			var bags = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), e -> e.getItem().is(ModItems.BACKPACK));
			helper.assertTrue(bags.size() == 1, "exactly one backpack dropped, found " + bags.size());
			ItemContainerContents contents = bags.getFirst().getItem().get(DataComponents.CONTAINER);
			long total = contents == null ? 0 : contents.nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(total == 15, "backpack holds pickaxe + 5 raw iron + 9 coal (15 items), had " + total);
			var stray = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds(), e -> !e.getItem().is(ModItems.BACKPACK));
			helper.assertTrue(stray.isEmpty(), "nothing else dropped loose");
			CampData data = Camp.data(helper.getLevel().getServer());
			helper.assertTrue(data.ledger(FriendId.FLINT).state == CampData.LifeState.DEAD, "ledger records the death");
			helper.succeed();
		});
	}
}

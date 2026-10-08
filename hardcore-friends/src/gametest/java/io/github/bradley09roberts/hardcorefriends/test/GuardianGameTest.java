package io.github.bradley09roberts.hardcorefriends.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.SageAdvisor;
import io.github.bradley09roberts.hardcorefriends.ai.role.sage.ReviewStoresTask;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
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
}

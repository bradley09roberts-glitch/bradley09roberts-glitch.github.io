package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.AvoidDangerGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.FollowLeaderGoal;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.ForageContext;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.item.BackpackItem;
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

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_05", maxTicks = 900)
	public void friendsFightTogetherAgainstAZombie(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(14, 2, 16));
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(16, 2, 14));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(18, 2, 16));
		fern.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_HOE));
		rowan.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
		oak.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		var zombie = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(16, 2, 20));
		zombie.setTarget(fern);
		helper.succeedWhen(() -> {
			helper.assertFalse(zombie.isAlive(), "the zombie is defeated (health " + zombie.getHealth() + ")");
			helper.assertTrue(fern.isAlive() && rowan.isAlive() && oak.isAlive(), "all three friends survive");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_57", maxTicks = 600)
	public void friendsHealOutOfCombat(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity sage = TestSupport.spawnFriend(helper, FriendId.SAGE, TestSupport.centre());
		sage.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 8.0F);
		float hurt = sage.getHealth();
		helper.succeedWhen(() -> helper.assertTrue(sage.getHealth() >= hurt + 3, "Sage healed from " + hurt + " to " + sage.getHealth()));
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_58", maxTicks = 200)
	public void backpackSurvivesLavaAndSpillsWhenDestroyed(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		ServerLevel level = helper.getLevel();
		for (int x = 8; x <= 10; x++) {
			for (int z = 8; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
			}
		}
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, new BlockPos(9, 2, 9));
		flint.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		TestSupport.give(flint, new ItemStack(Items.RAW_IRON, 5));
		flint.kill(level);
		// A bag destroyed some other way (a cactus here) spills what it held instead of deleting it.
		BlockPos spillAt = helper.absolutePos(new BlockPos(22, 2, 22));
		ItemStack spare = BackpackItem.pack(FriendId.OAK, List.of(new ItemStack(Items.COAL, 3), new ItemStack(Items.IRON_INGOT, 2))).getFirst();
		ItemEntity doomed = new ItemEntity(level, spillAt.getX() + 0.5, spillAt.getY(), spillAt.getZ() + 0.5, spare);
		doomed.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(doomed);
		doomed.hurtServer(level, level.damageSources().cactus(), 10.0F);
		helper.assertTrue(doomed.isRemoved(), "the cactus destroyed the spare bag");
		var spilled = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(spillAt).inflate(2),
			e -> !e.getItem().is(ModItems.BACKPACK));
		int coal = spilled.stream().filter(e -> e.getItem().is(Items.COAL)).mapToInt(e -> e.getItem().getCount()).sum();
		int iron = spilled.stream().filter(e -> e.getItem().is(Items.IRON_INGOT)).mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(coal == 3 && iron == 2, "the destroyed bag spilled 3 coal and 2 iron, found " + coal + " and " + iron);
		helper.runAfterDelay(80, () -> {
			var bags = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(ModItems.BACKPACK));
			helper.assertTrue(bags.size() == 1, "Flint's backpack survived four seconds in lava, found " + bags.size());
			ItemContainerContents contents = bags.getFirst().getItem().get(DataComponents.CONTAINER);
			long total = contents == null ? 0 : contents.nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(total == 6, "the bag still holds the pickaxe and 5 raw iron, had " + total);
			helper.succeed();
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_59", maxTicks = 100)
	public void stoneOnlyDropsForTheRightTool(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(14, 2, 16));
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, new BlockPos(18, 2, 16));
		rowan.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		flint.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
		helper.setBlock(new BlockPos(14, 2, 13), Blocks.STONE);
		helper.setBlock(new BlockPos(18, 2, 13), Blocks.STONE);
		helper.assertTrue(WorldEditGuard.breakBlock(rowan, helper.absolutePos(new BlockPos(14, 2, 13)), Reason.MINE), "Rowan may break the stone");
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(14, 2, 13));
		helper.assertTrue(rowan.backpack().count(Items.COBBLESTONE) == 0 && rowan.backpack().count(Items.STONE) == 0,
			"stone broken with an axe drops nothing");
		helper.assertTrue(WorldEditGuard.breakBlock(flint, helper.absolutePos(new BlockPos(18, 2, 13)), Reason.MINE), "Flint may break the stone");
		helper.assertTrue(flint.backpack().count(Items.COBBLESTONE) == 1, "stone broken with a pickaxe drops cobblestone");
		// The friends' own blocks always come back, whatever tool takes them down.
		BlockPos seal = helper.absolutePos(new BlockPos(14, 2, 20));
		flint.setLastEditTick(-100);
		helper.assertTrue(WorldEditGuard.placeBlock(flint, seal, Blocks.COBBLESTONE.defaultBlockState(), Reason.MINE), "Flint places a seal");
		rowan.setLastEditTick(-100);
		helper.assertTrue(WorldEditGuard.breakBlock(rowan, seal, Reason.BUILD), "Rowan may take down the friends' own block");
		helper.assertTrue(rowan.backpack().count(Items.COBBLESTONE) == 1, "the friends' own cobblestone comes back");
		// A pickaxe kept beyond the current backpack capacity is still used.
		rowan.backpack().container().setItem(20, new ItemStack(Items.STONE_PICKAXE));
		rowan.actions().equipBestFor(Blocks.STONE.defaultBlockState());
		helper.assertTrue(rowan.getMainHandItem().is(Items.STONE_PICKAXE), "the pickaxe in slot 21 is equipped, holding " + rowan.getMainHandItem());
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_60", maxTicks = 100)
	public void miningNeverDropsSandOrGravelIntoWater(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		// A river bed: stone holding up sand and gravel with water on top.
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.STONE);
		helper.setBlock(new BlockPos(8, 3, 8), Blocks.SAND);
		helper.setBlock(new BlockPos(8, 4, 8), Blocks.GRAVEL);
		helper.setBlock(new BlockPos(8, 5, 8), Blocks.WATER);
		// The same column on dry land.
		helper.setBlock(new BlockPos(24, 2, 24), Blocks.STONE);
		helper.setBlock(new BlockPos(24, 3, 24), Blocks.SAND);
		helper.setBlock(new BlockPos(24, 4, 24), Blocks.GRAVEL);
		WorldEditGuard.Verdict wet = WorldEditGuard.canBreak(flint, helper.absolutePos(new BlockPos(8, 2, 8)), Reason.MINE);
		helper.assertFalse(wet.allowed(), "stone under sand holding back water stays put");
		helper.assertTrue(wet.why().equals("next to water or lava"), "refused because of the water, not: " + wet.why());
		helper.assertTrue(WorldEditGuard.canBreak(flint, helper.absolutePos(new BlockPos(24, 2, 24)), Reason.MINE).allowed(),
			"the same column on dry land may be mined");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_61", maxTicks = 100)
	public void placedBlocksStayTheFriendsOnlyWhileTheirBlockIsThere(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		ServerLevel level = helper.getLevel();
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, TestSupport.centre());
		BlockPos torch = helper.absolutePos(new BlockPos(20, 2, 20));
		helper.assertTrue(WorldEditGuard.placeBlock(oak, torch, Blocks.TORCH.defaultBlockState(), Reason.BUILD), "Oak places a torch");
		helper.assertTrue(data.isPlacedByFriends(level, torch), "the torch is recorded as the friends' own");
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		if (nether != null) {
			helper.assertFalse(data.isPlacedByFriends(nether, torch), "the same coordinates in the Nether are not the friends'");
		}
		// The player swaps the torch for their own chest: it is the player's now.
		helper.setBlock(new BlockPos(20, 2, 20), Blocks.CHEST);
		helper.assertFalse(data.isPlacedByFriends(level, torch), "the player's chest is not recorded as the friends'");
		oak.setLastEditTick(-100);
		helper.assertFalse(WorldEditGuard.canBreak(oak, torch, Reason.BUILD).allowed(), "Oak leaves the player's chest alone");
		helper.assertFalse(WorldEditGuard.canPlace(oak, torch.above(), Blocks.TORCH.defaultBlockState(), Reason.BUILD).allowed(),
			"nothing is placed on top of the player's chest");
		// Cobblestone counts as player-built unless the friends placed it.
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.COBBLESTONE);
		helper.assertTrue(WorldEditGuard.looksPlayerBuilt(level, helper.absolutePos(new BlockPos(8, 2, 9)), 1, data), "cobblestone marks a build");
		// No fence post on the player's path, but on the friends' own path is fine.
		helper.setBlock(new BlockPos(10, 1, 20), Blocks.DIRT_PATH);
		helper.assertFalse(WorldEditGuard.canPlace(oak, helper.absolutePos(new BlockPos(10, 2, 20)), Blocks.OAK_FENCE.defaultBlockState(),
			Reason.LANDSCAPE).allowed(), "no fence post on the player's path");
		BlockPos ownPath = helper.absolutePos(new BlockPos(12, 1, 20));
		helper.assertTrue(WorldEditGuard.transformBlock(oak, ownPath, Blocks.DIRT_PATH.defaultBlockState(), Reason.LANDSCAPE), "Oak makes a path");
		oak.setLastEditTick(-100);
		helper.assertTrue(WorldEditGuard.canPlace(oak, ownPath.above(), Blocks.OAK_FENCE.defaultBlockState(), Reason.LANDSCAPE).allowed(),
			"a fence post may stand on the friends' own path");
		// The linked supply chest is shared: Spark's drop-off hopper sits on it.
		TestSupport.placeChest(helper, new BlockPos(24, 2, 12));
		helper.assertTrue(WorldEditGuard.canPlace(oak, helper.absolutePos(new BlockPos(24, 3, 12)), Blocks.HOPPER.defaultBlockState(),
			Reason.INVENT).allowed(), "a hopper may sit on the linked supply chest");
		// The record survives a save, and older saves' bare positions are still honoured.
		data.ledger(FriendId.FLINT).starterGiven = true;
		data.ledger(FriendId.FLINT).dismissedAtGameTime = 1234L;
		Tag saved = CampData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		CampData loaded = CampData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.isPlacedByFriends(level, ownPath), "the friends' path is still theirs after a save");
		helper.assertFalse(loaded.isPlacedByFriends(level, torch), "the player's chest is still the player's after a save");
		helper.assertTrue(loaded.ledger(FriendId.FLINT).starterGiven && loaded.ledger(FriendId.FLINT).dismissedAtGameTime == 1234L,
			"the ledger keeps the starter tool and dismissal time");
		CompoundTag old = new CompoundTag();
		old.putString("campDim", Camp.dimensionId(level));
		old.putLongArray("placed", new long[] {ownPath.asLong()});
		CampData legacy = CampData.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(legacy.isPlacedByFriends(level, ownPath), "an older save's record still counts");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_62", maxTicks = 160)
	public void nonFighterLeavesASkeletonsLineOfFire(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(21, 2, 16));
		fern.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_HOE));
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(30, 2, 16));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET)); // no burning in the daylight
		skeleton.setTarget(fern);
		fern.setTarget(skeleton);
		AvoidDangerGoal probe = new AvoidDangerGoal(fern, true);
		boolean flees = false;
		for (int i = 0; i < 5 && !flees; i++) {
			flees = probe.canUse();
		}
		helper.assertTrue(flees, "a skeleton aiming at Fern from 9 blocks is a danger to get away from");
		helper.succeedWhen(() -> {
			helper.assertTrue(fern.distanceTo(skeleton) > 15, "Fern moved out of the skeleton's range, distance "
				+ String.format(java.util.Locale.ROOT, "%.1f", fern.distanceTo(skeleton)) + " (" + fern.activity() + ")");
			helper.assertTrue(fern.getTarget() == null, "Fern dropped the target she cannot fight");
		});
	}

	/** Retreating home must not lead back into bow range: camp here is 14 blocks from the skeleton. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_56", maxTicks = 400)
	public void hurtFriendRetreatsOutOfBowRange(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(21, 2, 16));
		fern.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE)); // so the test is about moving, not dying
		fern.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		fern.setHealth(8.0F);
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(30, 2, 16));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		// It stands its ground near camp (rooted, so the test area's edge cannot corner Fern) and picks its own target,
		// so it loses interest beyond its 16-block follow range as it would in a world.
		skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		boolean[] escaped = {false};
		List<String> backInRange = new ArrayList<>();
		helper.onEachTick(() -> {
			if (!fern.isRetreating()) {
				return;
			}
			float d = fern.distanceTo(skeleton);
			escaped[0] |= d > 16;
			if (escaped[0] && d < 15 && backInRange.isEmpty()) {
				backInRange.add(String.format(java.util.Locale.ROOT, "%.1f at tick %d", d, helper.getTick()));
			}
		});
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(fern.isAlive(), "Fern survived");
			helper.assertTrue(escaped[0], "the hurt friend got out of bow range");
			helper.assertTrue(backInRange.isEmpty(), "while still recovering she stayed out of range, came back to " + backInRange);
			helper.succeed();
		});
	}

	/**
	 * Dodging a threat must not keep a badly hurt friend from falling back (goals never interrupt an equal one): in a
	 * river a friend may never get far enough from a drowned for the dodge to end, so the retreat must take over at once.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_111", maxTicks = 200)
	public void dodgingFriendFallsBackOnceBadlyHurt(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(24, 2, 16));
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(29, 2, 16));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		skeleton.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		skeleton.setTarget(fern);
		long[] hurtAt = {-1};
		helper.onEachTick(() -> {
			if (hurtAt[0] < 0 && fern.activity().startsWith("getting away from")) {
				fern.setHealth(6.0F); // badly hurt while still dodging, the skeleton only a few blocks off
				hurtAt[0] = helper.getTick();
			}
			if (hurtAt[0] >= 0 && !fern.isRetreating() && helper.getTick() - hurtAt[0] > 10) {
				helper.fail("badly hurt, Fern should fall back at once, but is " + fern.activity()
					+ String.format(java.util.Locale.ROOT, " %.1f blocks from the skeleton", fern.distanceTo(skeleton)));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hurtAt[0] >= 0, "Fern first got out of the skeleton's way: " + fern.activity());
			helper.assertTrue(fern.isRetreating(), "badly hurt, Fern falls back to recover: " + fern.activity());
		});
	}

	/**
	 * A drowned without a trident fights hand to hand: friends stand together against it as against a zombie
	 * instead of running from it as from an archer (from the recorded run, where running cost five lives).
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_113", maxTicks = 800)
	public void friendsStandTogetherAgainstAPlainDrowned(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, new BlockPos(15, 2, 16));
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(17, 2, 16));
		fern.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_HOE));
		rowan.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
		Drowned drowned = helper.spawn(EntityTypes.DROWNED, new BlockPos(16, 2, 21));
		drowned.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		drowned.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the daylight
		Drowned spearman = helper.spawn(EntityTypes.DROWNED, new BlockPos(2, 2, 2));
		spearman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		helper.assertFalse(Threats.isRanged(drowned), "a drowned with bare hands is not an archer");
		helper.assertTrue(Threats.isRanged(spearman), "a drowned with a trident is");
		spearman.discard();
		drowned.setTarget(fern);
		helper.succeedWhen(() -> {
			helper.assertFalse(drowned.isAlive(), "the drowned was beaten: Fern " + fern.activity() + ", Rowan " + rowan.activity());
			helper.assertTrue(fern.isAlive() && rowan.isAlive(), "both friends came through");
		});
	}

	/** Where a friend died is remembered for three days, and roaming jobs keep away from it. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_112", maxTicks = 40)
	public void deathSpotsAreAvoidedForThreeDays(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, TestSupport.centre());
		ServerLevel level = helper.getLevel();
		BlockPos spot = helper.absolutePos(TestSupport.centre()).offset(40, 0, 0);
		long now = level.getGameTime();
		helper.assertTrue(ForageContext.mayFell(rowan, spot), "a tree out there may be felled before");
		data.markDanger(spot, now);
		helper.assertTrue(data.nearDanger(spot.offset(20, 0, 0), now), "remembered within 24 blocks");
		helper.assertFalse(data.nearDanger(spot.offset(30, 0, 0), now), "not further away");
		helper.assertFalse(ForageContext.mayFell(rowan, spot.offset(5, 0, 5)), "no felling where a friend just died");
		helper.assertFalse(data.nearDanger(spot, now + 3 * 24000 + 1), "forgotten after three days");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_63", maxTicks = 100)
	public void stewsAndHoneyLeaveTheirContainers(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		ServerLevel level = helper.getLevel();
		CompanionEntity fern = TestSupport.spawnFriend(helper, FriendId.FERN, TestSupport.centre());
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		fern.hurtServer(level, level.damageSources().generic(), 10.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MUSHROOM_STEW));
		fern.interact(player, InteractionHand.MAIN_HAND, fern.position());
		helper.assertTrue(player.getMainHandItem().is(Items.BOWL), "the player gets the bowl back, holding " + player.getMainHandItem());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE, 2));
		fern.interact(player, InteractionHand.MAIN_HAND, fern.position());
		helper.assertTrue(player.getMainHandItem().is(Items.HONEY_BOTTLE) && player.getMainHandItem().getCount() == 1, "one honey bottle used");
		helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "the empty bottle goes into the inventory");
		TestSupport.give(fern, new ItemStack(Items.RABBIT_STEW));
		fern.setHealth(fern.getMaxHealth() - 8.0F); // the gifts healed her; a second hit this tick would be ignored
		helper.assertTrue(fern.eatFromBackpack(), "Fern eats the stew from her backpack");
		helper.assertTrue(fern.backpack().count(Items.BOWL) == 1 && fern.backpack().count(Items.RABBIT_STEW) == 0, "the bowl stays in her backpack");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_64", maxTicks = 400)
	public void dismissedFriendsWaitADayAndBringNoNewTool(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		BlockPos stand = helper.absolutePos(TestSupport.centre());
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 0.0F);
		player.getInventory().add(new ItemStack(Items.CARROT, 8));
		var source = player.createCommandSourceStack().withSuppressedOutput();
		var commands = level.getServer().getCommands();
		commands.performPrefixedCommand(source, "friends recruit flint");
		CompanionEntity flint = Companions.find(FriendId.FLINT).orElseThrow(() -> new IllegalStateException("Flint was not recruited"));
		helper.assertTrue(flint.getMainHandItem().is(Items.WOODEN_PICKAXE), "a first recruit brings the starter pickaxe");
		flint.hurtServer(level, level.damageSources().generic(), 1.0F);
		commands.performPrefixedCommand(source, "friends dismiss flint");
		helper.assertTrue(flint.isAlive() && data.ledger(FriendId.FLINT).state == CampData.LifeState.ALIVE,
			"a friend who was just hurt is in a fight and does not leave");
		helper.runAfterDelay(io.github.bradley09roberts.hardcorefriends.command.FriendsCommand.NO_DISMISS_AFTER_HURT + 10, () -> {
			commands.performPrefixedCommand(source, "friends dismiss flint");
			CampData.Ledger ledger = data.ledger(FriendId.FLINT);
			helper.assertTrue(ledger.state == CampData.LifeState.DISMISSED, "Flint left once the fight was over");
			commands.performPrefixedCommand(source, "friends recruit flint");
			helper.assertTrue(Companions.find(FriendId.FLINT).isEmpty(), "a friend dismissed moments ago does not rejoin at once");
			helper.assertTrue(player.getInventory().countItem(Items.CARROT) == 6, "a refused recruit costs nothing");
			TestSupport.setTime(helper, level.getOverworldClockTime() + 24001);
			commands.performPrefixedCommand(source, "friends recruit flint");
			CompanionEntity back = Companions.find(FriendId.FLINT).orElseThrow(() -> new IllegalStateException("Flint did not rejoin"));
			helper.assertTrue(back.getMainHandItem().isEmpty(), "a returning friend brings no second starter tool, holding " + back.getMainHandItem());
			helper.assertTrue(player.getInventory().countItem(Items.CARROT) == 4, "rejoining costs the usual food");
			helper.succeed();
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_65", maxTicks = 100)
	public void followersStayInTheirOwnDimensionAndLoadedGround(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		ServerLevel level = helper.getLevel();
		CompanionEntity scout = TestSupport.spawnFriend(helper, FriendId.SCOUT, TestSupport.centre());
		ServerPlayer here = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.assertTrue(FollowLeaderGoal.canFollow(scout, here), "a leader in the same dimension can be followed");
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		if (nether != null) {
			ServerPlayer away = new ServerPlayer(level.getServer(), nether, new GameProfile(UUID.randomUUID(), "nether-leader"),
				ClientInformation.createDefault());
			away.snapTo(scout.getX() + 200, scout.getY(), scout.getZ(), 0.0F, 0.0F);
			helper.assertFalse(FollowLeaderGoal.canFollow(scout, away), "a leader in the Nether is not chased across dimensions");
		}
		BlockPos far = helper.absolutePos(TestSupport.centre()).offset(200_000, 0, 200_000);
		helper.assertFalse(level.isLoaded(far), "the far spot starts unloaded");
		helper.assertTrue(FollowLeaderGoal.catchUpSpot(level, far, RandomSource.create(7)) == null, "no catch-up spot in unloaded ground");
		helper.assertFalse(level.isLoaded(far), "looking did not load the chunk");
		helper.assertTrue(FollowLeaderGoal.catchUpSpot(level, helper.absolutePos(TestSupport.centre()), RandomSource.create(7)) != null,
			"there is a catch-up spot on the plot");
		// Scout's speed bonus is in the attribute only, not doubled by the walking speed.
		helper.assertTrue(scout.actions().speed() == 1.0, "task walking uses the plain speed modifier");
		helper.assertTrue(Math.abs(scout.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 0.33) < 1e-6, "Scout's base speed carries the +10%");
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_66", maxTicks = 100)
	public void logWallTouchingATreeIsNotATree(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		TestSupport.growOak(helper, new BlockPos(8, 2, 8));
		// A player's two-column log wall right beside the oak's canopy (the first live test found Rowan felling one).
		for (int y = 2; y < 6; y++) {
			helper.setBlock(new BlockPos(11, y, 8), Blocks.OAK_LOG);
			helper.setBlock(new BlockPos(11, y, 9), Blocks.OAK_LOG);
		}
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(TreeFinder.isNaturalTreeLog(helper.getLevel(), helper.absolutePos(new BlockPos(8, 2, 8))), "the oak is natural");
			helper.assertFalse(TreeFinder.isNaturalTreeLog(helper.getLevel(), helper.absolutePos(new BlockPos(11, 3, 8))), "the log wall is not a tree");
			helper.succeed();
		});
	}
}

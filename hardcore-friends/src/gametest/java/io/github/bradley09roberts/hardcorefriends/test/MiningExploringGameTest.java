package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import io.github.bradley09roberts.hardcorefriends.ai.role.ScoutSenses;
import io.github.bradley09roberts.hardcorefriends.ai.role.forage.QuarryTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.CampFurnace;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.DigMineTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MinePlan;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MineSite;
import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MiningHelper;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ExploreTask;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.ScoutLog;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/** Flint's mining and smelting, and Scout's exploring and warnings. */
public class MiningExploringGameTest {
	/** A small natural stone outcrop with one iron ore facing an open (air) side towards the plot centre. */
	private static BlockPos buildOreOutcrop(GameTestHelper helper) {
		for (int x = 20; x <= 22; x++) {
			for (int z = 14; z <= 18; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		BlockPos ore = new BlockPos(20, 3, 16);
		helper.setBlock(ore, Blocks.IRON_ORE);
		return ore;
	}

	private static ItemStack findTool(CompanionEntity c, Item item) {
		if (c.getItemBySlot(EquipmentSlot.MAINHAND).is(item)) {
			return c.getItemBySlot(EquipmentSlot.MAINHAND);
		}
		return c.backpack().find(s -> s.is(item));
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_25", maxTicks = 800)
	public void flintMinesExposedIronWithStonePickaxe(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		BlockPos ore = buildOreOutcrop(helper);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		TestSupport.give(flint, new ItemStack(Items.STONE_PICKAXE));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.AIR, ore);
			helper.assertTrue(flint.backpack().count(Items.RAW_IRON) >= 1, "raw iron is in Flint's backpack");
			ItemStack pick = findTool(flint, Items.STONE_PICKAXE);
			helper.assertTrue(!pick.isEmpty() && pick.getDamageValue() > 0, "the stone pickaxe wore down");
			helper.assertTrue(Camp.data(helper.getLevel().getServer()).stat("ores_mined") >= 1, "ores_mined stat counted");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_26", maxTicks = 400)
	public void flintSkipsIronWithWoodenPickaxe(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		BlockPos ore = buildOreOutcrop(helper);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		TestSupport.give(flint, new ItemStack(Items.WOODEN_PICKAXE));
		helper.runAfterDelay(300, () -> {
			helper.assertBlockPresent(Blocks.IRON_ORE, ore);
			helper.assertTrue(flint.backpack().count(Items.RAW_IRON) == 0, "no raw iron without a stone pickaxe");
			helper.assertTrue(Camp.data(helper.getLevel().getServer()).stat("ores_mined") == 0, "nothing counted as mined");
			helper.succeed();
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_27", maxTicks = 1600)
	public void staircaseMineDigsOnlyNaturalStoneInsideItsBox(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, false);
		// Camp centre 40 blocks west of the plot centre: the plot lies in the gathering ring. The stone top stays
		// five blocks below the test's barrier roof, which counts as a build marker.
		BlockPos campCentre = helper.absolutePos(new BlockPos(-24, TestSupport.STAND_Y, 16));
		data.setCamp(campCentre, Camp.dimensionId(helper.getLevel()));
		for (int x = 1; x <= 30; x++) {
			for (int z = 1; z <= 30; z++) {
				for (int y = 0; y <= 6; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, new BlockPos(20, 7, 16));
		TestSupport.give(flint, new ItemStack(Items.STONE_PICKAXE));
		List<WorldEditGuard.EditEvent> edits = new ArrayList<>();
		WorldEditGuard.listener = e -> {
			if (e.companion() == flint) {
				edits.add(e);
			}
		};
		helper.runAfterDelay(1590, () -> WorldEditGuard.listener = null);
		helper.succeedWhen(() -> {
			MinePlan plan = MinePlan.of(data);
			helper.assertTrue(plan.exists(), "a mine was planned");
			long broken = edits.stream().filter(e -> e.verb().equals("broke")).count();
			helper.assertTrue(broken >= DigMineTask.BLOCKS_PER_RUN, "Flint dug a full run (" + broken + " blocks so far)");
			BoundingBox box = plan.box();
			helper.assertTrue(box.getXSpan() <= MinePlan.BOX_SIZE && box.getZSpan() <= MinePlan.BOX_SIZE, "box is at most 24x24");
			BlockPos entrance = plan.entrance();
			int ring = Camp.radius(data) + MineSite.RING_GAP;
			helper.assertTrue(Camp.horizontalDistSqr(entrance, campCentre) >= ring * ring, "entrance lies outside the camp edge");
			for (WorldEditGuard.EditEvent e : edits) {
				helper.assertTrue(box.isInside(e.pos()), "edit at " + e.pos() + " is inside the mine box");
				if (e.verb().equals("broke")) {
					helper.assertTrue(e.state().is(ModTags.MINEABLE_NATURAL), "only natural stone was dug, not " + e.state());
					helper.assertTrue(e.reason() == WorldEditGuard.Reason.MINE, "dug for mining");
				}
			}
			helper.assertTrue(plan.stepsDug() >= 3, "the staircase went down (" + plan.stepsDug() + " steps)");
			helper.assertTrue(flint.backpack().count(Items.COBBLESTONE) >= 8, "cobblestone collected for the camp");
			WorldEditGuard.listener = null;
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_28", maxTicks = 600)
	public void flintLoadsFurnaceWithRawIronAndCoal(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		BlockPos furnacePos = new BlockPos(19, 2, 19);
		helper.setBlock(furnacePos, Blocks.FURNACE);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		TestSupport.give(flint, new ItemStack(Items.RAW_IRON, 5), new ItemStack(Items.COAL, 2));
		BlockPos abs = helper.absolutePos(furnacePos);
		helper.succeedWhen(() -> {
			AbstractFurnaceBlockEntity f = CampFurnace.furnaceAt(helper.getLevel(), abs);
			helper.assertTrue(f != null, "furnace exists");
			helper.assertTrue(flint.backpack().count(Items.RAW_IRON) == 0, "all raw iron went in");
			helper.assertTrue(f.getItem(CampFurnace.SLOT_INPUT).is(Items.RAW_IRON), "raw iron is in the input slot");
			helper.assertTrue(flint.backpack().count(Items.COAL) == 1, "one coal is enough for five ores");
			boolean lit = helper.getLevel().getBlockState(abs).getValue(AbstractFurnaceBlock.LIT);
			helper.assertTrue(lit || f.getItem(CampFurnace.SLOT_FUEL).is(Items.COAL), "coal went into the fuel slot");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_29", maxTicks = 400)
	public void scoutRecordsExposedDiamondOre(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		for (int x = 5; x <= 7; x++) {
			for (int z = 5; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
			}
		}
		BlockPos diamond = new BlockPos(6, 3, 6);
		helper.setBlock(diamond, Blocks.DIAMOND_ORE);
		BlockPos abs = helper.absolutePos(diamond);
		TestSupport.spawnFriend(helper, FriendId.SCOUT, new BlockPos(12, 2, 12));
		helper.succeedWhen(() -> {
			boolean recorded = data.pois().stream().anyMatch(p -> p.type.equals(ScoutLog.ORE) && p.pos.equals(abs));
			helper.assertTrue(recorded, "the exposed diamond ore is recorded as an ore point of interest");
			helper.assertTrue(ScoutLog.of(data).unreported(ScoutLog.ORE) >= 1, "the find is kept for the next report");
			helper.assertBlockPresent(Blocks.DIAMOND_ORE, diamond);
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_30", maxTicks = 400)
	@SuppressWarnings("removal") // the mock player helper is the supported way to get a server player in game tests
	public void scoutWarnsPlayerAboutCreeper(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		CompanionEntity scout = TestSupport.spawnFriend(helper, FriendId.SCOUT, new BlockPos(4, 2, 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos stand = helper.absolutePos(new BlockPos(14, 2, 16));
		// Facing south (+Z) towards the creeper would be "in front"; face north so it is behind.
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180.0F, 0.0F);
		Creeper creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(16, 2, 25));
		creeper.setNoAi(true);
		helper.succeedWhen(() -> {
			helper.assertTrue(player.level() == helper.getLevel(), "the mock player is in the test level");
			long warned = ScoutSenses.lastWarned(scout, player.getUUID(), ScoutSenses.Kind.CREEPER);
			helper.assertTrue(warned >= 0, "Scout warned the player about the creeper");
			helper.getLevel().getServer().getPlayerList().remove(player);
			creeper.discard();
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_31", maxTicks = 600)
	public void flintCollectsIngotsFromFurnace(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		BlockPos furnacePos = new BlockPos(12, 2, 19);
		helper.setBlock(furnacePos, Blocks.FURNACE);
		BlockPos abs = helper.absolutePos(furnacePos);
		AbstractFurnaceBlockEntity furnace = CampFurnace.furnaceAt(helper.getLevel(), abs);
		helper.assertTrue(furnace != null, "furnace placed");
		furnace.setItem(CampFurnace.SLOT_RESULT, new ItemStack(Items.IRON_INGOT, 4));
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, TestSupport.centre());
		helper.succeedWhen(() -> {
			helper.assertTrue(flint.backpack().count(Items.IRON_INGOT) == 4, "Flint took the four ingots");
			helper.assertTrue(furnace.getItem(CampFurnace.SLOT_RESULT).isEmpty(), "the output slot is empty");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_32", maxTicks = 600)
	@SuppressWarnings("removal")
	public void scoutReportsFindsToPlayerInCamp(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity scout = TestSupport.spawnFriend(helper, FriendId.SCOUT, new BlockPos(6, 2, 6));
		TestSupport.give(scout, new ItemStack(Items.BREAD, 4));
		ScoutLog log = ScoutLog.of(data);
		log.noteFind(ScoutLog.ORE, "iron ore at 1 2 3", 4);
		log.noteFind(ScoutLog.TREE, "", 0);
		log.advance(ExploreTask.rings(), 8, true);
		scout.scheduler().cooldown("scout.explore", helper.getLevel().getGameTime(), 2000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos stand = helper.absolutePos(new BlockPos(20, 2, 20));
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0.0F, 0.0F);
		helper.succeedWhen(() -> {
			helper.assertTrue(log.unreportedTotal() == 0, "Scout reported the finds");
			helper.assertTrue(scout.distanceTo(player) < 6, "Scout walked over to the player");
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_67", maxTicks = 1200)
	public void flintNeverDigsOutHisOwnFloor(GameTestHelper helper) {
		TestSupport.resetCamp(helper, true);
		// A stone platform (well clear of the test's barrier floor, which counts as built) with a short vertical iron
		// vein right under the spot where Flint stands.
		for (int x = 13; x <= 19; x++) {
			for (int z = 13; z <= 19; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		BlockPos upper = new BlockPos(16, 4, 16);
		BlockPos lower = new BlockPos(16, 3, 16);
		helper.setBlock(upper, Blocks.IRON_ORE);
		helper.setBlock(lower, Blocks.IRON_ORE);
		BlockPos stand = upper.above();
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, stand);
		TestSupport.give(flint, new ItemStack(Items.STONE_PICKAXE));
		List<String> underFeet = new ArrayList<>();
		WorldEditGuard.listener = e -> {
			if (e.companion() == flint && e.verb().equals("broke") && MiningHelper.isOnTop(flint.blockPosition(), e.pos())) {
				underFeet.add(helper.relativePos(e.pos()).toShortString());
			}
		};
		helper.runAfterDelay(1190, () -> WorldEditGuard.listener = null);
		int surfaceY = helper.absolutePos(stand).getY();
		helper.succeedWhen(() -> {
			helper.assertTrue(underFeet.isEmpty(), "Flint never broke the block he stood on, but did at " + underFeet);
			helper.assertBlockPresent(Blocks.AIR, upper);
			helper.assertBlockPresent(Blocks.AIR, lower);
			helper.assertTrue(flint.backpack().count(Items.RAW_IRON) >= 2, "both ores were mined");
			helper.assertTrue(flint.getY() >= surfaceY - 0.01, "Flint is still up on the platform, not down the shaft (y "
				+ flint.getY() + ")");
			WorldEditGuard.listener = null;
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_68", maxTicks = 600)
	public void campMineAndQuarryAreLeftAloneInAnotherDimension(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, false);
		// The camp, its mine and its quarry are in the Nether; Flint and Rowan were recruited here, in the overworld.
		String nether = "minecraft:the_nether";
		data.setCamp(helper.absolutePos(TestSupport.centre()), nether);
		BlockPos entrance = helper.absolutePos(TestSupport.centre()).offset(200, 0, 0);
		MinePlan.of(data).begin(entrance, Direction.EAST, MinePlan.BOTTOM_Y, nether);
		BlockPos corner = helper.absolutePos(new BlockPos(20, TestSupport.GROUND_Y, 20));
		CompoundTag quarry = data.memory(QuarryTask.MEMORY);
		quarry.putBoolean("active", true);
		quarry.putLong("corner", corner.asLong());
		quarry.putInt("cell", 0);
		quarry.putString("dim", nether);
		CompanionEntity flint = TestSupport.spawnFriend(helper, FriendId.FLINT, new BlockPos(10, 2, 16));
		TestSupport.give(flint, new ItemStack(Items.STONE_PICKAXE));
		CompanionEntity rowan = TestSupport.spawnFriend(helper, FriendId.ROWAN, new BlockPos(22, 2, 16));
		TestSupport.give(rowan, new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.STONE_SHOVEL));
		helper.runAfterDelay(500, () -> {
			MinePlan plan = MinePlan.of(data);
			helper.assertTrue(plan.exists() && plan.entrance().equals(entrance), "the Nether mine was neither abandoned nor replaced");
			helper.assertTrue(plan.oldEntrances().length == 0, "no mine was given up");
			helper.assertTrue(corner.equals(QuarryTask.corner(data)), "the Nether quarry is still the active one");
			helper.assertTrue(data.memory(QuarryTask.MEMORY).getIntOr("cell", -1) == 0, "the Nether quarry was not advanced");
			helper.assertTrue(data.stat("blocks_mined") == 0 && data.stat("blocks_quarried") == 0, "nothing was dug here for it");
			helper.succeed();
		});
	}
}

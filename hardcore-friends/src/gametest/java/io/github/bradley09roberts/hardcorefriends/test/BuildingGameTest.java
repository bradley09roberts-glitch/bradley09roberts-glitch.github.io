package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SiteClearing;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/** Oak's building and Spark's contraptions: site finding, real materials, full structures and working redstone. */
public class BuildingGameTest {
	private static final BlockPos CHEST = new BlockPos(18, 2, 16);
	private static final BlockPos TABLE = new BlockPos(18, 2, 15);

	// ---------------------------------------------------------------- helpers

	/** Sets the camp's stage and marks every improvement of earlier stages (and the listed ones) as done. */
	private static CampData atStage(GameTestHelper helper, int stage, String... alsoDone) {
		CampData data = TestSupport.resetCamp(helper, true);
		data.setStage(stage);
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() < stage) {
				data.markCompleted(e.id());
			}
		}
		for (String id : alsoDone) {
			data.markCompleted(id);
		}
		return data;
	}

	/** Spawns a friend at the camp centre holding their starter tool, as {@code /friends recruit} does. */
	private static CompanionEntity recruit(GameTestHelper helper, FriendId id) {
		CompanionEntity c = TestSupport.spawnFriend(helper, id, TestSupport.centre());
		c.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(id.starterTool()));
		return c;
	}

	/** A plain item that builds this material, for setting up structures directly in tests. */
	private static ItemStack sample(MaterialSpec m) {
		return new ItemStack(switch (m) {
			case PLANKS, FOUNDATION -> Items.OAK_PLANKS;
			case LOG -> Items.OAK_LOG;
			case SLAB -> Items.OAK_SLAB;
			case DOOR, DOOR_TOP -> Items.OAK_DOOR;
			case FENCE -> Items.OAK_FENCE;
			case FENCE_GATE -> Items.OAK_FENCE_GATE;
			case PRESSURE_PLATE -> Items.OAK_PRESSURE_PLATE;
			case TORCH, WALL_TORCH -> Items.TORCH;
			case GLASS_PANE -> Items.GLASS_PANE;
			case LADDER -> Items.LADDER;
			case CHEST -> Items.CHEST;
			case CRAFTING_TABLE -> Items.CRAFTING_TABLE;
			case FURNACE -> Items.FURNACE;
			case CAMPFIRE -> Items.CAMPFIRE;
			case HOPPER -> Items.HOPPER;
			case COBBLESTONE -> Items.COBBLESTONE;
			case LANTERN -> Items.LANTERN;
			case REDSTONE_LAMP -> Items.REDSTONE_LAMP;
			case DAYLIGHT_DETECTOR -> Items.DAYLIGHT_DETECTOR;
			case BOOKSHELF -> Items.BOOKSHELF;
			case ENCHANTING_TABLE -> Items.ENCHANTING_TABLE;
			case ANVIL -> Items.ANVIL;
			case BREWING_STAND -> Items.BREWING_STAND;
			case OBSIDIAN -> Items.OBSIDIAN;
		});
	}

	/** Builds a plan instantly (test set-up only) and records it as a finished site. */
	private static void buildInstantly(GameTestHelper helper, CampData data, Blueprint bp, List<Part> parts) {
		ServerLevel level = helper.getLevel();
		List<Placement> all = Blueprints.placements(bp, parts);
		for (Placement p : all) {
			if (p.isFoundation()) {
				continue;
			}
			BlockState state = p.entry().material() == MaterialSpec.DOOR_TOP
				? level.getBlockState(p.pos().below()).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
				: p.entry().stateFor(sample(p.entry().material()), Blueprint.rotation(p.rotation()));
			level.setBlock(p.pos(), state, Block.UPDATE_ALL);
		}
		SiteFinder.reserve(data, bp, parts);
		data.site(bp.id()).orElseThrow().progress = all.size();
		data.markCompleted(bp.id());
	}

	/**
	 * Builds a plan instantly except the entries of one material (test set-up only), and reserves the site with its
	 * progress at the first entry left out, as if the builder had got that far.
	 */
	private static void buildAllBut(GameTestHelper helper, CampData data, Blueprint bp, List<Part> parts, MaterialSpec left) {
		ServerLevel level = helper.getLevel();
		List<Placement> all = Blueprints.placements(bp, parts);
		int firstLeft = all.size();
		for (Placement p : all) {
			if (p.entry().material() == left) {
				firstLeft = Math.min(firstLeft, p.index());
				continue;
			}
			if (p.isFoundation()) {
				continue;
			}
			BlockState state = p.entry().material() == MaterialSpec.DOOR_TOP
				? level.getBlockState(p.pos().below()).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
				: p.entry().stateFor(sample(p.entry().material()), Blueprint.rotation(p.rotation()));
			level.setBlock(p.pos(), state, Block.UPDATE_ALL);
		}
		SiteFinder.reserve(data, bp, parts);
		data.site(bp.id()).orElseThrow().progress = firstLeft;
	}

	/** World positions of a reserved plan's entries made of one material. */
	private static List<BlockPos> positions(CampData data, Blueprint bp, MaterialSpec material) {
		return Blueprints.placements(bp, SiteFinder.parts(data, bp)).stream()
			.filter(p -> p.entry().material() == material).map(Placement::pos).toList();
	}

	private static long count(GameTestHelper helper, List<BlockPos> list, Predicate<BlockState> filter) {
		return list.stream().filter(p -> filter.test(helper.getLevel().getBlockState(p))).count();
	}

	private static String status(CompanionEntity c, CampData data, String id) {
		String site = data.site(id).map(s -> "site " + s.origin + " rot " + s.rotation + " progress " + s.progress).orElse("no site");
		StringBuilder pack = new StringBuilder();
		for (ItemStack s : c.backpack().stacks()) {
			pack.append(s.getCount()).append(' ').append(s.getItem()).append(", ");
		}
		return c.friendId().displayName() + " " + c.activity() + " at " + c.blockPosition() + "; " + site + "; carrying [" + pack
			+ "]; shortage '" + CampNeeds.shortageText(c.level().getGameTime()) + "'; done " + data.completed();
	}

	private static int chestCount(Container chest, Predicate<ItemStack> filter) {
		return SupplyChest.count(chest, filter);
	}

	// ------------------------------------------------------------------- Oak

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_33", maxTicks = 2400)
	public void oakBuildsCampfireFromLogsAndCoal(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.COAL, 2));
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.CAMPFIRE), "campfire finished: " + status(oak, data, Structures.CAMPFIRE));
			BlockPos fire = data.site(Structures.CAMPFIRE).orElseThrow().origin;
			helper.assertTrue(helper.getLevel().getBlockState(fire).is(Blocks.CAMPFIRE), "a campfire stands at " + fire);
			helper.assertTrue(data.isCompleted(Structures.SUPPLY_CHEST), "the linked chest counts as the supply chest");
			int logs = chestCount(chest, s -> s.is(ItemTags.LOGS)) + oak.backpack().count(ItemTags.LOGS);
			helper.assertTrue(logs <= 12, "at least four logs were used (crafting table and campfire), " + logs + " left");
			helper.assertTrue(chestCount(chest, s -> s.is(Items.COAL)) + oak.backpack().count(Items.COAL) <= 1, "one coal was used");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_34", maxTicks = 1600)
	public void oakBuildsSupplyChestWhenNoneLinked(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		TestSupport.give(oak, new ItemStack(Items.OAK_PLANKS, 16));
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.SUPPLY_CHEST), "supply chest finished: " + status(oak, data, Structures.SUPPLY_CHEST));
			Optional<BlockPos> linked = data.chestPos();
			helper.assertTrue(linked.isPresent(), "the new chest is linked");
			helper.assertTrue(helper.getLevel().getBlockState(linked.get()).is(Blocks.CHEST), "a chest stands at the linked position");
			helper.assertTrue(SupplyChest.of(helper.getLevel()).isPresent(), "the supply chest is usable");
			helper.assertTrue(data.isPlacedByFriends(linked.get()), "the chest is recorded as the friends' own");
			helper.assertTrue(oak.backpack().count(ItemTags.PLANKS) <= 4, "12 planks became a crafting table and a chest");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_35", maxTicks = 2400)
	public void cabinIsSitedAwayFromPlayerBuilds(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.GLASS, 6), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		// A player's plank inside the cabin's preferred footprint.
		BlockPos playerBlock = new BlockPos(3, 2, 15);
		helper.setBlock(playerBlock, Blocks.OAK_PLANKS);
		BlockPos playerAbs = helper.absolutePos(playerBlock);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(playerBlock).is(Blocks.OAK_PLANKS), "the player's plank is never broken");
			Optional<CampData.Site> site = data.site(Structures.CABIN);
			helper.assertTrue(site.isPresent(), "the cabin has a site: " + status(oak, data, Structures.CABIN));
			long built = count(helper, positions(data, Blueprints.CABIN, MaterialSpec.PLANKS), s -> s.is(BlockTags.PLANKS));
			helper.assertTrue(built >= 20, "the cabin is under way (" + built + " planks): " + status(oak, data, Structures.CABIN));
			int[] box = Blueprints.CABIN.footprint(site.get().origin, site.get().rotation);
			boolean near = playerAbs.getX() >= box[0] - 2 && playerAbs.getX() <= box[2] + 2 && playerAbs.getZ() >= box[1] - 2
				&& playerAbs.getZ() <= box[3] + 2;
			helper.assertFalse(near, "the cabin keeps two blocks away from the player's plank");
			helper.assertTrue(helper.getBlockState(playerBlock).is(Blocks.OAK_PLANKS), "the player's plank is still there");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_36", maxTicks = 9000)
	public void oakBuildsWholeCabin(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64),
			new ItemStack(Items.GLASS, 6), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.CABIN), "cabin finished: " + status(oak, data, Structures.CABIN));
			Blueprint cabin = Blueprints.CABIN;
			CampData.Site site = data.site(Structures.CABIN).orElseThrow();
			BlockPos door = Blueprints.at(site, Blueprints.CABIN_DOOR);
			BlockState lower = helper.getLevel().getBlockState(door);
			BlockState upper = helper.getLevel().getBlockState(door.above());
			helper.assertTrue(lower.is(BlockTags.WOODEN_DOORS) && lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, "door lower half at " + door);
			helper.assertTrue(upper.is(BlockTags.WOODEN_DOORS) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "door upper half");
			List<BlockPos> planks = positions(data, cabin, MaterialSpec.PLANKS);
			List<BlockPos> logs = positions(data, cabin, MaterialSpec.LOG);
			List<BlockPos> slabs = positions(data, cabin, MaterialSpec.SLAB);
			helper.assertTrue(count(helper, planks, s -> s.is(BlockTags.PLANKS)) == planks.size(), "all " + planks.size() + " planks placed");
			helper.assertTrue(count(helper, logs, s -> s.is(BlockTags.LOGS)) == 16, "16 corner logs placed");
			helper.assertTrue(slabs.size() == 49 && count(helper, slabs, s -> s.is(BlockTags.WOODEN_SLABS)) == 49, "a 7x7 slab roof");
			helper.assertTrue(count(helper, positions(data, cabin, MaterialSpec.GLASS_PANE), s -> s.is(Blocks.GLASS_PANE)) == 2, "two windows");
			helper.assertTrue(count(helper, positions(data, cabin, MaterialSpec.WALL_TORCH), s -> s.is(Blocks.WALL_TORCH)) == 1, "a torch inside");
			int logsLeft = chestCount(chest, s -> s.is(ItemTags.LOGS)) + oak.backpack().count(ItemTags.LOGS);
			helper.assertTrue(logsLeft <= 128 - 45, "real wood was used: " + logsLeft + " logs left");
			helper.assertTrue(chestCount(chest, s -> s.is(Items.GLASS)) + oak.backpack().count(Items.GLASS) == 0, "glass became panes");
		});
	}

	/**
	 * A camp in a forest: trees every five blocks, so no tree-free spot fits the cabin. Oak picks the site with the
	 * fewest trees, exactly those are felled (by Rowan and Oak, no replanting there), every other camp tree stands,
	 * and the cabin gets built.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_80", maxTicks = 14000)
	public void forestCampClearsTreesForTheCabin(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		FriendsConfig.get().campRadius = 8; // stage 2 makes it 16: the whole camp fits on the test plot
		List<BlockPos> trunks = new ArrayList<>();
		for (int x = 1; x < 32; x += 5) {
			for (int z = 1; z < 32; z += 5) {
				if (Math.abs(x - 17) + Math.abs(z - 16) > 3) { // the camp centre, chest and table stay clear
					BlockPos base = new BlockPos(x, 2, z);
					TestSupport.growOak(helper, base);
					trunks.add(base);
				}
			}
		}
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64),
			new ItemStack(Items.GLASS, 6), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		CompanionEntity rowan = recruit(helper, FriendId.ROWAN);
		Set<BlockPos> toFell = new HashSet<>();
		helper.onEachTick(() -> SiteClearing.job(data, Structures.CABIN).ifPresent(j -> toFell.addAll(j.logs())));
		BlockPos centre = helper.absolutePos(TestSupport.centre());
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.CABIN), "cabin finished: " + status(oak, data, Structures.CABIN)
				+ "; " + rowan.activity());
			helper.assertTrue(!toFell.isEmpty(), "the cabin site needed trees felled");
			for (BlockPos log : toFell) {
				helper.assertFalse(helper.getLevel().getBlockState(log).is(BlockTags.LOGS), "site tree log felled at " + log);
			}
			int standing = 0;
			for (BlockPos rel : trunks) {
				BlockPos abs = helper.absolutePos(rel);
				boolean onSite = toFell.contains(abs);
				boolean inCamp = Camp.horizontalDistSqr(abs, centre) <= 16 * 16;
				if (inCamp && !onSite) {
					helper.assertTrue(helper.getLevel().getBlockState(abs).is(BlockTags.LOGS), "camp tree at " + rel + " still stands");
					standing++;
				}
			}
			helper.assertTrue(standing >= 10, "most camp trees stand: " + standing);
			FriendsConfig.get().campRadius = 24;
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_40", maxTicks = 1600)
	public void oakRepairsMissingCabinBlocks(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.SPRUCE_PLANKS, 16), new ItemStack(Items.TORCH, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		Part cabinPart = new Part(helper.absolutePos(new BlockPos(9, 2, 12)), Blueprint.rotationFacing(Direction.EAST));
		buildInstantly(helper, data, Blueprints.CABIN, List.of(cabinPart));
		CampData.Site site = data.site(Structures.CABIN).orElseThrow();
		BlockPos wall = Blueprint.worldPos(site.origin, site.rotation, 2, 2, 7);
		BlockPos torch = positions(data, Blueprints.CABIN, MaterialSpec.WALL_TORCH).getFirst();
		helper.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		helper.getLevel().setBlock(torch, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getBlockState(wall).is(BlockTags.PLANKS), "the missing wall plank is back: " + status(oak, data, Structures.CABIN));
			helper.assertTrue(helper.getLevel().getBlockState(torch).is(Blocks.WALL_TORCH), "the wall torch is back");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_41", maxTicks = 800)
	public void oakReportsWhatTheBuildIsShortOf(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.COAL, 4));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			helper.assertTrue(CampNeeds.buildShortage().containsKey(CampNeeds.Need.WOOD), "the cabin reports a wood shortage: " + status(oak, data, Structures.CABIN));
			helper.assertTrue(CampNeeds.shortageText(helper.getLevel().getGameTime()).contains("planks"), "the shortage names planks");
			helper.assertTrue(CampNeeds.need(CampNeeds.Need.BUILD) >= 1.0, "building materials are now the camp's need");
			helper.assertFalse(data.isCompleted(Structures.CABIN), "nothing is built from thin air");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_42", maxTicks = 1200)
	public void oakSawsPlanksForTheTeam(GameTestHelper helper) {
		CampData data = atStage(helper, 4);
		for (Structures.Entry e : Structures.ALL) {
			data.markCompleted(e.id());
		}
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.BIRCH_LOG, 16), new ItemStack(Items.COAL, 1));
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			int planks = chestCount(chest, s -> s.is(ItemTags.PLANKS));
			helper.assertTrue(planks >= 24, "planks sawn and stored, " + planks + ": " + status(oak, data, Structures.CABIN));
			helper.assertTrue(chestCount(chest, s -> s.is(Items.BIRCH_PLANKS)) == planks, "the planks are birch, like the logs");
			helper.assertTrue(chestCount(chest, s -> s.is(Items.STICK)) >= 4, "sticks made");
			helper.assertTrue(chestCount(chest, s -> s.is(Items.TORCH)) >= 4, "torches made from the coal");
			helper.assertTrue(chestCount(chest, s -> s.is(ItemTags.LOGS)) == 8, "eight logs used, eight kept");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_44", maxTicks = 6000)
	public void oakBuildsWatchtowerWithLookout(GameTestHelper helper) {
		CampData data = atStage(helper, 3, Structures.STOREHOUSE);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.COAL, 2));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.WATCHTOWER), "watchtower finished: " + status(oak, data, Structures.WATCHTOWER));
			Blueprint tower = Blueprints.WATCHTOWER;
			helper.assertTrue(count(helper, positions(data, tower, MaterialSpec.COBBLESTONE), s -> s.is(Blocks.COBBLESTONE)) == 34, "34 cobblestone walls and corners");
			helper.assertTrue(count(helper, positions(data, tower, MaterialSpec.LADDER), s -> s.is(Blocks.LADDER)) == 4, "a ladder up the inside");
			helper.assertTrue(count(helper, positions(data, tower, MaterialSpec.SLAB), s -> s.is(BlockTags.WOODEN_SLABS)) == 4, "slab lookout");
			helper.assertTrue(count(helper, positions(data, tower, MaterialSpec.TORCH), s -> s.is(Blocks.TORCH)) == 4, "four torches on top");
		});
	}

	// ----------------------------------------------------------------- Spark

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_43", maxTicks = 1200)
	public void sparkMakesTorches(GameTestHelper helper) {
		CampData data = atStage(helper, 4);
		for (Structures.Entry e : Structures.ALL) {
			data.markCompleted(e.id());
		}
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.COAL, 4), new ItemStack(Items.OAK_PLANKS, 2));
		CompanionEntity spark = recruit(helper, FriendId.SPARK);
		helper.succeedWhen(() -> {
			helper.assertTrue(chestCount(chest, s -> s.is(Items.TORCH)) == 16, "four coal and two planks make 16 torches: " + status(spark, data, ""));
			helper.assertTrue(chestCount(chest, s -> s.is(Items.COAL)) == 0 && chestCount(chest, s -> s.is(ItemTags.PLANKS)) == 0, "all used");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_37", maxTicks = 2400)
	public void sparkBuildsAutomaticCabinDoor(GameTestHelper helper) {
		CampData data = atStage(helper, 2, Structures.CABIN);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_PLANKS, 8));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		Part cabinPart = new Part(helper.absolutePos(new BlockPos(9, 2, 12)), Blueprint.rotationFacing(Direction.EAST));
		buildInstantly(helper, data, Blueprints.CABIN, List.of(cabinPart));
		CompanionEntity spark = recruit(helper, FriendId.SPARK);
		CampData.Site site = data.site(Structures.CABIN).orElseThrow();
		BlockPos door = Blueprints.at(site, Blueprints.CABIN_DOOR);
		BlockPos outside = Blueprint.worldPos(site.origin, site.rotation, 3, 1, 0);
		BlockPos inside = Blueprint.worldPos(site.origin, site.rotation, 3, 1, 2);
		helper.startSequence()
			.thenWaitUntil(() -> {
				helper.assertTrue(data.isCompleted(Structures.AUTO_DOOR), "automatic door finished: " + status(spark, data, Structures.AUTO_DOOR));
				helper.assertTrue(helper.getLevel().getBlockState(outside).is(BlockTags.WOODEN_PRESSURE_PLATES), "plate on the doorstep at " + outside);
				helper.assertTrue(helper.getLevel().getBlockState(inside).is(BlockTags.WOODEN_PRESSURE_PLATES), "plate inside at " + inside);
			})
			.thenExecute(() -> {
				// A player's dropped stick, lying still on the doorstep plate (friends leave player drops alone).
				ItemEntity pebble = new ItemEntity(helper.getLevel(), outside.getX() + 0.5, outside.getY() + 0.05, outside.getZ() + 0.5,
					new ItemStack(Items.STICK), 0, 0, 0);
				pebble.setPickUpDelay(32767);
				pebble.setThrower(helper.makeMockPlayer(GameType.SURVIVAL));
				helper.getLevel().addFreshEntity(pebble);
			})
			.thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN),
				"something on the doorstep plate opens the door (door " + helper.getLevel().getBlockState(door) + ", plate "
					+ helper.getLevel().getBlockState(outside) + ", items " + helper.getLevel().getEntitiesOfClass(ItemEntity.class,
					new net.minecraft.world.phys.AABB(outside).inflate(1)) + ", " + status(spark, data, Structures.AUTO_DOOR) + ")"))
			.thenSucceed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_38", maxTicks = 2400)
	public void sparkBuildsDropOffHopper(GameTestHelper helper) {
		CampData data = atStage(helper, 3, Structures.AUTO_DOOR);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.IRON_INGOT, 5), new ItemStack(Items.OAK_PLANKS, 8));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity spark = recruit(helper, FriendId.SPARK);
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.HOPPER_DROPOFF), "drop-off hopper finished: " + status(spark, data, Structures.HOPPER_DROPOFF));
			BlockState above = helper.getBlockState(CHEST.above());
			helper.assertTrue(above.is(Blocks.HOPPER) && above.getValue(HopperBlock.FACING) == Direction.DOWN, "a hopper points down into the chest");
			helper.assertTrue(chestCount(chest, s -> s.is(Items.IRON_INGOT)) + spark.backpack().count(Items.IRON_INGOT) == 0, "five iron went into it");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_39", maxTicks = 4800)
	public void autoSmelterSmeltsRawIron(GameTestHelper helper) {
		CampData data = atStage(helper, 4);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.IRON_INGOT, 15), new ItemStack(Items.OAK_LOG, 16),
			new ItemStack(Items.COBBLESTONE, 16));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity spark = recruit(helper, FriendId.SPARK);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(data.isCompleted(Structures.AUTO_SMELTER), "auto-smelter finished: "
				+ status(spark, data, Structures.AUTO_SMELTER)))
			.thenExecute(() -> {
				CampData.Site site = data.site(Structures.AUTO_SMELTER).orElseThrow();
				ServerLevel level = helper.getLevel();
				SupplyChest.at(level, Blueprints.at(site, Blueprints.SMELTER_INPUT)).orElseThrow().setItem(0, new ItemStack(Items.RAW_IRON, 2));
				SupplyChest.at(level, Blueprints.at(site, Blueprints.SMELTER_FUEL)).orElseThrow().setItem(0, new ItemStack(Items.COAL, 1));
			})
			.thenWaitUntil(() -> {
				CampData.Site site = data.site(Structures.AUTO_SMELTER).orElseThrow();
				Container output = SupplyChest.at(helper.getLevel(), Blueprints.at(site, Blueprints.SMELTER_OUTPUT)).orElseThrow();
				helper.assertTrue(chestCount(output, s -> s.is(Items.IRON_INGOT)) >= 1, "an iron ingot arrives in the output chest");
			})
			.thenSucceed();
	}

	// ------------------------------------------------------- fixes and edges

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_75", maxTicks = 200)
	public void campfireIsNotSitedOnPlayersCobblestone(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		// A player's cobblestone floor where the campfire would go (three blocks south of the centre).
		for (int x = 13; x <= 19; x++) {
			for (int z = 18; z <= 22; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.COBBLESTONE);
			}
		}
		SiteFinder.Search search = SiteFinder.search(helper.getLevel(), data, Blueprints.CAMPFIRE);
		List<Part> found = null;
		for (int i = 0; i < 200 && found == null && !search.failed(); i++) {
			found = search.step(16);
		}
		helper.assertTrue(found != null, "a campfire site is found");
		BlockPos origin = found.getFirst().origin();
		BlockState ground = helper.getLevel().getBlockState(origin.below());
		helper.assertTrue(SiteFinder.isNaturalGround(ground), "the campfire stands on natural ground, not on the player's floor: "
			+ ground + " under " + origin);
		helper.succeed();
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_76", maxTicks = 1600)
	public void oakClosesCabinWindowsWithPlanksWithoutGlass(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.OAK_LOG, 16));
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		Part cabinPart = new Part(helper.absolutePos(new BlockPos(9, 2, 12)), Blueprint.rotationFacing(Direction.EAST));
		// Everything is up except the two windows, and nobody has any glass.
		buildAllBut(helper, data, Blueprints.CABIN, List.of(cabinPart), MaterialSpec.GLASS_PANE);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.CABIN), "the cabin is finished without glass: " + status(oak, data, Structures.CABIN));
			List<BlockPos> windows = positions(data, Blueprints.CABIN, MaterialSpec.GLASS_PANE);
			helper.assertTrue(windows.size() == 2 && count(helper, windows, s -> s.is(BlockTags.PLANKS)) == 2,
				"both windows are closed with planks");
			helper.assertTrue(chestCount(chest, s -> s.is(ItemTags.LOGS)) + oak.backpack().count(ItemTags.LOGS) <= 15, "a log became the planks");
		});
	}

	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_79", maxTicks = 1600)
	public void oakReplacesBrokenSupplyChest(GameTestHelper helper) {
		CampData data = atStage(helper, 4);
		for (Structures.Entry e : Structures.ALL) {
			data.markCompleted(e.id());
		}
		TestSupport.placeChest(helper, CHEST);
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		CompanionEntity oak = recruit(helper, FriendId.OAK);
		TestSupport.give(oak, new ItemStack(Items.OAK_PLANKS, 16));
		long improvements = data.stat("improvements_built");
		helper.setBlock(CHEST, Blocks.AIR); // a creeper took the supply chest
		helper.succeedWhen(() -> {
			Optional<BlockPos> linked = data.chestPos();
			helper.assertTrue(linked.isPresent() && data.isPlacedByFriends(linked.get()),
				"a new chest built by Oak is linked, link " + linked + ": " + status(oak, data, Structures.SUPPLY_CHEST));
			helper.assertTrue(helper.getLevel().getBlockState(linked.get()).is(Blocks.CHEST), "a chest stands at the linked position");
			helper.assertTrue(SupplyChest.of(helper.getLevel()).isPresent(), "the supply chest is usable again");
			helper.assertTrue(data.stat("improvements_built") == improvements, "a replacement is not a new improvement");
		});
	}
}

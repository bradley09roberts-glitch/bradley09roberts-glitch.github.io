package io.github.bradley09roberts.hardcorefriends.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Livestock;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;

/**
 * Livestock: the animal pen built from real materials, wild animals brought home on a lead or following their food,
 * breeding, butchering the surplus, cooking meat on the campfire, hunting in the gathering ring, and the rules that
 * keep the player's own animals (and anyone shut in the pen) safe.
 */
public class LivestockGameTest {
	/** The pen's paddock centre in the plot (pen built facing west, towards the camp centre). */
	private static final BlockPos PEN_CENTRE = new BlockPos(26, 2, 16);
	private static final BlockPos CHEST = new BlockPos(12, 2, 22);

	// ---------------------------------------------------------------- helpers

	/** A camp at the plot centre that has reached the given stage, everything of earlier stages done. */
	private static CampData atStage(GameTestHelper helper, int stage) {
		CampData data = TestSupport.resetCamp(helper, true);
		clearAnimals(helper);
		data.setStage(stage);
		for (Structures.Entry e : Structures.ALL) {
			if (e.stage() < stage) {
				data.markCompleted(e.id());
			}
		}
		return data;
	}

	/** The camp 30 blocks west of the plot centre, so the plot's east part lies in the gathering ring. */
	private static CampData campToTheWest(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, false);
		clearAnimals(helper);
		data.setCamp(helper.absolutePos(new BlockPos(-14, TestSupport.STAND_Y, 16)), Camp.dimensionId(helper.getLevel()));
		return data;
	}

	/** Removes animals left over from earlier tests anywhere near this plot, so every test starts with its own. */
	private static void clearAnimals(GameTestHelper helper) {
		AABB around = new AABB(helper.absolutePos(TestSupport.centre())).inflate(120, 40, 120);
		helper.getLevel().getEntitiesOfClass(Animal.class, around, a -> Livestock.kind(a) != null).forEach(Entity::discard);
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> true).forEach(Entity::discard);
	}

	/**
	 * Passes a test whose checks are made at a set time. Passing removes the test's entities (the stone walls keep its
	 * animals on the plot), and any that strayed are swept up when the next livestock test starts.
	 */
	private static void finish(GameTestHelper helper) {
		helper.succeed();
	}

	/**
	 * True while the animal is alive and has never been hurt. One the test framework removed at the end of the test
	 * (rather than killed) counts as unharmed: per-tick checks may run once more in the tick the test passes.
	 */
	private static boolean unharmed(Animal a) {
		if (a.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
			return true;
		}
		return a.isAlive() && a.getHealth() == a.getMaxHealth();
	}

	/**
	 * Swaps the test framework's barrier case round the plot for a natural stone wall along its edge, so animals stay
	 * on the plot. Barriers count as a player's build (only a player in creative can place them), which would make
	 * every animal on the plot look like the player's own.
	 */
	private static void fenceInPlot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = -1; x <= 32; x++) {
			for (int z = -1; z <= 32; z++) {
				for (int y = -1; y <= 13; y++) {
					BlockPos abs = helper.absolutePos(new BlockPos(x, y, z));
					if (level.getBlockState(abs).is(Blocks.BARRIER)) {
						level.setBlock(abs, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
		for (int i = 0; i < 32; i++) {
			for (int y = 2; y <= 3; y++) {
				helper.setBlock(new BlockPos(i, y, 0), Blocks.STONE);
				helper.setBlock(new BlockPos(i, y, 31), Blocks.STONE);
				helper.setBlock(new BlockPos(0, y, i), Blocks.STONE);
				helper.setBlock(new BlockPos(31, y, i), Blocks.STONE);
			}
		}
	}

	/**
	 * Builds the animal pen instantly (test set-up only) as the friends would have built it, recorded as their own
	 * blocks, with its gate shut, and marks it finished.
	 */
	private static Pen buildPen(GameTestHelper helper, CampData data) {
		ServerLevel level = helper.getLevel();
		Blueprint bp = Blueprints.ANIMAL_PEN;
		int rotation = Blueprint.rotationFacing(Direction.WEST);
		BlockPos origin = bp.originFor(helper.absolutePos(PEN_CENTRE), rotation);
		List<Part> parts = List.of(new Part(origin, rotation));
		List<Placement> all = Blueprints.placements(bp, parts);
		List<BlockPos> placed = new ArrayList<>();
		for (Placement p : all) {
			if (p.isFoundation()) {
				continue;
			}
			boolean gate = p.entry().material() == MaterialSpec.FENCE_GATE;
			BlockState state = p.entry().stateFor(new ItemStack(gate ? Items.OAK_FENCE_GATE : Items.OAK_FENCE), Blueprint.rotation(rotation));
			if (gate) {
				state = state.setValue(FenceGateBlock.OPEN, false);
			}
			level.setBlock(p.pos(), state, Block.UPDATE_ALL);
			placed.add(p.pos());
		}
		for (BlockPos pos : placed) {
			BlockState connected = Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos);
			level.setBlock(pos, connected, Block.UPDATE_ALL);
			data.recordPlaced(level, pos, connected);
		}
		SiteFinder.reserve(data, bp, parts);
		data.site(Structures.ANIMAL_PEN).orElseThrow().progress = all.size();
		data.markCompleted(Structures.ANIMAL_PEN);
		return Pen.of(level).orElseThrow();
	}

	private static <T extends Animal> T animal(GameTestHelper helper, EntityType<T> type, BlockPos rel) {
		return helper.spawn(type, rel);
	}

	private static <T extends Animal> T baby(GameTestHelper helper, EntityType<T> type, BlockPos rel) {
		T a = helper.spawn(type, rel);
		a.setBaby(true);
		return a;
	}

	private static CompanionEntity fern(GameTestHelper helper, BlockPos rel) {
		return TestSupport.spawnFriend(helper, FriendId.FERN, rel);
	}

	private static int chestCount(Container chest, Predicate<ItemStack> filter) {
		return SupplyChest.count(chest, filter);
	}

	private static int count(GameTestHelper helper, Pen pen, EntityType<?> type, boolean babies) {
		int n = 0;
		for (Animal a : pen.animals(helper.getLevel())) {
			if (a.getType() == type && a.isBaby() == babies) {
				n++;
			}
		}
		return n;
	}

	/** Who is doing what where, for failure messages. (Positions are as the test helper reports them.) */
	private static String where(GameTestHelper helper, CompanionEntity c) {
		return c.friendId().displayName() + " " + c.activity() + " at " + helper.relativePos(c.blockPosition()) + ", health "
			+ c.getHealth();
	}

	private static String where(GameTestHelper helper, Entity e) {
		return e.getType().toShortString() + " at " + helper.relativePos(e.blockPosition());
	}

	// ------------------------------------------------------------------- pen

	/** Terra builds the pen from logs in the chest: fences and a gate crafted at the table, on a level site in camp. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_135", maxTicks = 6000)
	public void terraBuildsThePenFromChestMaterials(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		data.markCompleted(Structures.PATHS); // keep Terra on the pen
		Container chest = TestSupport.placeChest(helper, new BlockPos(18, 2, 16), new ItemStack(Items.OAK_LOG, 32));
		helper.setBlock(new BlockPos(18, 2, 15), Blocks.CRAFTING_TABLE);
		CompanionEntity terra = TestSupport.spawnFriend(helper, FriendId.TERRA, TestSupport.centre());
		helper.succeedWhen(() -> {
			helper.assertTrue(data.isCompleted(Structures.ANIMAL_PEN), "the pen is finished: " + where(helper, terra)
				+ " site " + data.site(Structures.ANIMAL_PEN).map(s -> helper.relativePos(s.origin) + " progress " + s.progress).orElse("none"));
			Pen pen = Pen.of(helper.getLevel()).orElseThrow();
			ServerLevel level = helper.getLevel();
			int fences = 0;
			int gates = 0;
			for (Placement p : Blueprints.placements(Blueprints.ANIMAL_PEN, SiteFinder.parts(data, Blueprints.ANIMAL_PEN))) {
				BlockState s = level.getBlockState(p.pos());
				if (p.entry().material() == MaterialSpec.FENCE && s.is(BlockTags.WOODEN_FENCES)) {
					fences++;
				}
				if (p.entry().material() == MaterialSpec.FENCE_GATE && s.is(BlockTags.FENCE_GATES)) {
					gates++;
				}
			}
			helper.assertTrue(fences == 31, "31 fences stand: " + fences);
			helper.assertTrue(gates == 1 && pen.hasGate(level), "and one gate");
			BlockPos centre = helper.absolutePos(TestSupport.centre());
			helper.assertTrue(Camp.horizontalDistSqr(pen.outside(), centre) < Camp.horizontalDistSqr(pen.back(), centre),
				"the gate faces the camp centre");
			helper.assertTrue(data.isPlacedByFriends(level, pen.gate()), "the gate is the friends' own block");
			int logsLeft = chestCount(chest, s -> s.is(ItemTags.LOGS)) + terra.backpack().count(ItemTags.LOGS);
			helper.assertTrue(logsLeft <= 32 - 12, "real wood was used: " + logsLeft + " logs left");
		});
	}

	// ---------------------------------------------------------- bringing home

	/** Without a lead, Fern holds wheat from the chest and a wild cow follows her into the pen; the gate is shut after. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_136", maxTicks = 3000)
	public void fernLuresAWildCowIntoThePenWithWheat(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		fenceInPlot(helper);
		Pen pen = buildPen(helper, data);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.WHEAT, 16));
		Animal cow = animal(helper, EntityTypes.COW, new BlockPos(6, 2, 10));
		CompanionEntity fern = fern(helper, TestSupport.centre());
		helper.succeedWhen(() -> {
			helper.assertTrue(pen.holds(cow), "the cow is in the pen: " + where(helper, cow) + "; " + where(helper, fern));
			helper.assertFalse(pen.gateOpen(helper.getLevel()), "the gate is shut behind it: " + where(helper, fern));
			helper.assertFalse(pen.holds(fern), "Fern is out of the pen");
			helper.assertFalse(cow.isLeashed(), "no lead was used");
			int wheat = chestCount(chest, s -> s.is(Items.WHEAT)) + fern.backpack().count(Items.WHEAT)
				+ (fern.getMainHandItem().is(Items.WHEAT) ? fern.getMainHandItem().getCount() : 0);
			helper.assertTrue(wheat == 16, "the wheat was only held, not used: " + wheat);
			helper.assertTrue(data.stat("animals_penned") >= 1, "counted");
		});
	}

	/** With a lead in the chest, Fern ties it on a wild sheep, leads it into the pen, and the lead comes back. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_137", maxTicks = 3000)
	public void fernLeadsASheepHomeAndGetsTheLeadBack(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		fenceInPlot(helper);
		Pen pen = buildPen(helper, data);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.LEAD, 1));
		Animal sheep = animal(helper, EntityTypes.SHEEP, new BlockPos(6, 2, 8));
		CompanionEntity fern = fern(helper, TestSupport.centre());
		boolean[] wasLeashed = {false};
		helper.onEachTick(() -> wasLeashed[0] |= sheep.isLeashed() && sheep.getLeashHolder() == fern);
		helper.succeedWhen(() -> {
			helper.assertTrue(wasLeashed[0], "Fern put the lead on the sheep: " + where(helper, fern));
			helper.assertTrue(pen.holds(sheep), "the sheep is in the pen: " + where(helper, sheep) + "; " + where(helper, fern));
			helper.assertFalse(sheep.isLeashed(), "and untied");
			helper.assertFalse(pen.holds(fern), "Fern is out of the pen");
			helper.assertFalse(pen.gateOpen(helper.getLevel()), "the gate is shut");
			int leads = chestCount(chest, s -> s.is(Items.LEAD)) + fern.backpack().count(Items.LEAD);
			helper.assertTrue(leads == 1, "the lead came back: " + leads);
		});
	}

	/** The player's animals are never led away: a named cow and a cow in the player's own fenced corner stay put. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_145", maxTicks = 1200)
	public void fernNeverTakesThePlayersAnimals(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		fenceInPlot(helper);
		Pen pen = buildPen(helper, data);
		TestSupport.placeChest(helper, CHEST, new ItemStack(Items.WHEAT, 16), new ItemStack(Items.LEAD, 2));
		Animal named = animal(helper, EntityTypes.COW, new BlockPos(6, 2, 8));
		named.setCustomName(Component.literal("Daisy"));
		Animal fenced = playersFencedAnimal(helper, EntityTypes.PIG, new BlockPos(6, 2, 24));
		CompanionEntity fern = fern(helper, TestSupport.centre());
		helper.onEachTick(() -> {
			helper.assertFalse(named.isLeashed() || fenced.isLeashed(), "nobody's animal is put on a lead");
			helper.assertFalse(fern.getMainHandItem().is(Items.WHEAT), "Fern never lures them: " + where(helper, fern));
		});
		helper.runAtTickTime(1000, () -> {
			helper.assertFalse(pen.holds(named) || pen.holds(fenced), "neither is in the pen");
			helper.assertTrue(data.stat("animals_penned") == 0, "nothing was penned");
			finish(helper);
		});
	}

	/** A one-block enclosure of the player's own fences round an animal (not the friends' blocks). */
	private static <T extends Animal> T playersFencedAnimal(GameTestHelper helper, EntityType<T> type, BlockPos rel) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(rel.offset(dx, 0, dz), Blocks.OAK_FENCE);
				}
			}
		}
		ServerLevel level = helper.getLevel();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos abs = helper.absolutePos(rel.offset(dx, 0, dz));
				if (dx != 0 || dz != 0) {
					level.setBlock(abs, Block.updateFromNeighbourShapes(level.getBlockState(abs), level, abs), Block.UPDATE_ALL);
				}
			}
		}
		return animal(helper, type, rel);
	}

	// -------------------------------------------------------------- breeding

	/** Two cows in the pen, wheat in the chest: Fern feeds them (the wheat is used up) and a calf is born. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_138", maxTicks = 2000)
	public void fernBreedsTwoPenCowsIntoACalf(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		fenceInPlot(helper);
		Pen pen = buildPen(helper, data);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.WHEAT, 6));
		animal(helper, EntityTypes.COW, new BlockPos(25, 2, 14));
		animal(helper, EntityTypes.COW, new BlockPos(27, 2, 18));
		CompanionEntity fern = fern(helper, TestSupport.centre());
		helper.succeedWhen(() -> {
			helper.assertTrue(count(helper, pen, EntityTypes.COW, true) >= 1, "a calf was born in the pen: " + where(helper, fern)
				+ ", cows " + count(helper, pen, EntityTypes.COW, false));
			int wheat = chestCount(chest, s -> s.is(Items.WHEAT)) + fern.backpack().count(Items.WHEAT);
			helper.assertTrue(wheat == 4, "two wheat were fed to them: " + wheat + " left");
			helper.assertFalse(pen.holds(fern), "Fern left the pen: " + where(helper, fern));
			helper.assertFalse(pen.gateOpen(helper.getLevel()), "and shut the gate");
		});
	}

	// ------------------------------------------------------------ butchering

	/**
	 * Six grown pigs and two piglets, four pigs kept for breeding: Fern butchers two grown pigs with a sword, never a
	 * piglet and never below the breeding pigs, and gathers the pork.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_139", maxTicks = 2400)
	public void fernButchersOnlyTheSurplusPigs(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		fenceInPlot(helper);
		Pen pen = buildPen(helper, data);
		TestSupport.placeChest(helper, CHEST);
		for (int i = 0; i < 6; i++) {
			animal(helper, EntityTypes.PIG, new BlockPos(24 + (i % 3) * 2, 2, 14 + (i / 3) * 4));
		}
		List<Animal> piglets = List.of(baby(helper, EntityTypes.PIG, new BlockPos(26, 2, 15)), baby(helper, EntityTypes.PIG, new BlockPos(26, 2, 17)));
		CompanionEntity fern = fern(helper, TestSupport.centre());
		TestSupport.give(fern, new ItemStack(Items.IRON_SWORD));
		int[] fewestAdults = {6};
		helper.onEachTick(() -> {
			fewestAdults[0] = Math.min(fewestAdults[0], count(helper, pen, EntityTypes.PIG, false));
			for (Animal piglet : piglets) {
				helper.assertTrue(unharmed(piglet), "a piglet is never harmed");
			}
		});
		helper.succeedWhen(() -> {
			int adults = count(helper, pen, EntityTypes.PIG, false);
			helper.assertTrue(adults == Livestock.KEEP_ADULTS, "the surplus was butchered: " + adults + " grown pigs; " + where(helper, fern));
			helper.assertTrue(fewestAdults[0] >= Livestock.KEEP_ADULTS && fewestAdults[0] >= Livestock.PAIR, "never below the breeding pigs");
			helper.assertTrue(data.stat("animals_butchered") == 2, "two butchered: " + data.stat("animals_butchered"));
			int pork = fern.backpack().count(Items.PORKCHOP) + chestCount(SupplyChest.of(helper.getLevel()).orElseThrow(), s -> s.is(Items.PORKCHOP));
			helper.assertTrue(pork >= 2, "the pork was gathered: " + pork);
			helper.assertFalse(pen.holds(fern), "Fern left the pen");
			helper.assertFalse(pen.gateOpen(helper.getLevel()), "and shut the gate");
		});
	}

	// --------------------------------------------------------------- cooking

	/** Raw beef from the chest goes on the lit campfire; Fern collects the cooked beef and puts it in the chest. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_140", maxTicks = 2400)
	public void fernCooksBeefOnTheCampfire(GameTestHelper helper) {
		CampData data = TestSupport.resetCamp(helper, true);
		clearAnimals(helper);
		helper.setBlock(new BlockPos(16, 2, 20), Blocks.CAMPFIRE);
		Container chest = TestSupport.placeChest(helper, CHEST, new ItemStack(Items.BEEF, 3));
		CompanionEntity fern = fern(helper, TestSupport.centre());
		helper.succeedWhen(() -> {
			int cooked = chestCount(chest, s -> s.is(Items.COOKED_BEEF));
			helper.assertTrue(cooked == 3, "three cooked beef are in the chest: " + cooked + "; " + where(helper, fern));
			int raw = chestCount(chest, s -> s.is(Items.BEEF)) + fern.backpack().count(Items.BEEF);
			helper.assertTrue(raw == 0, "no raw beef is left: " + raw);
			helper.assertTrue(data.stat("meals_cooked") >= 3, "counted");
		});
	}

	// --------------------------------------------------------------- hunting

	/** By day, armed and healthy, with the camp short of food, Fern hunts a wild cow in the gathering ring. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_141", maxTicks = 2400)
	public void fernHuntsAWildCowInTheRing(GameTestHelper helper) {
		CampData data = campToTheWest(helper);
		fenceInPlot(helper);
		List<Animal> cows = new ArrayList<>();
		for (BlockPos rel : List.of(new BlockPos(24, 2, 12), new BlockPos(26, 2, 20), new BlockPos(22, 2, 24), new BlockPos(28, 2, 16))) {
			cows.add(animal(helper, EntityTypes.COW, rel));
		}
		CompanionEntity fern = fern(helper, new BlockPos(16, 2, 16));
		TestSupport.give(fern, new ItemStack(Items.IRON_SWORD));
		int[] fewest = {4};
		helper.onEachTick(() -> fewest[0] = Math.min(fewest[0], (int) cows.stream().filter(Entity::isAlive).count()));
		helper.succeedWhen(() -> {
			long alive = cows.stream().filter(Entity::isAlive).count();
			helper.assertTrue(alive < 4, "a cow was hunted: " + where(helper, fern));
			helper.assertTrue(data.stat("animals_hunted") >= 1, "the hunt is counted");
			int meat = fern.backpack().count(Items.BEEF) + fern.backpack().count(Items.LEATHER);
			helper.assertTrue(meat >= 1, "Fern gathered what it dropped");
			helper.assertTrue(fewest[0] >= 2, "never the last two cows");
		});
	}

	/**
	 * Never hunted: a named cow, a cow in the player's fenced corner, a calf, a cow inside the camp, and the pen's own
	 * animals, even with Fern armed, healthy and the camp out of food.
	 */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_142", maxTicks = 1000)
	public void fernNeverHuntsProtectedAnimals(GameTestHelper helper) {
		CampData data = campToTheWest(helper);
		fenceInPlot(helper);
		List<Animal> safe = new ArrayList<>();
		for (BlockPos rel : List.of(new BlockPos(24, 2, 8), new BlockPos(26, 2, 10), new BlockPos(24, 2, 12))) {
			Animal named = animal(helper, EntityTypes.COW, rel);
			named.setCustomName(Component.literal("Bessie"));
			safe.add(named);
		}
		safe.add(playersFencedAnimal(helper, EntityTypes.COW, new BlockPos(27, 2, 24)));
		safe.add(baby(helper, EntityTypes.COW, new BlockPos(20, 2, 20)));
		// A cow inside the camp (20 blocks from its centre), walled in with natural stone so it stays there.
		BlockPos inCamp = new BlockPos(6, 2, 16);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					helper.setBlock(inCamp.offset(dx, 0, dz), Blocks.STONE);
					helper.setBlock(inCamp.offset(dx, 1, dz), Blocks.STONE);
				}
			}
		}
		safe.add(animal(helper, EntityTypes.COW, inCamp));
		CompanionEntity fern = fern(helper, new BlockPos(16, 2, 16));
		TestSupport.give(fern, new ItemStack(Items.IRON_SWORD));
		helper.onEachTick(() -> {
			for (Animal a : safe) {
				helper.assertTrue(unharmed(a), "never hunted: " + where(helper, a) + "; " + where(helper, fern));
			}
		});
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(data.stat("animals_hunted") == 0, "nothing was hunted");
			finish(helper);
		});
	}

	/** The last two cows anywhere near are left alone. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_143", maxTicks = 1000)
	public void fernNeverHuntsTheLastPair(GameTestHelper helper) {
		CampData data = campToTheWest(helper);
		fenceInPlot(helper);
		List<Animal> pair = List.of(animal(helper, EntityTypes.COW, new BlockPos(24, 2, 14)), animal(helper, EntityTypes.COW, new BlockPos(26, 2, 18)));
		CompanionEntity fern = fern(helper, new BlockPos(16, 2, 16));
		TestSupport.give(fern, new ItemStack(Items.IRON_SWORD));
		helper.onEachTick(() -> {
			for (Animal a : pair) {
				helper.assertTrue(unharmed(a), "the last pair is spared: " + where(helper, fern));
			}
		});
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(data.stat("animals_hunted") == 0, "nothing was hunted");
			finish(helper);
		});
	}

	/** Nobody hunts at night, however many cows there are. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_144", maxTicks = 1000)
	public void fernNeverHuntsAtNight(GameTestHelper helper) {
		CampData data = campToTheWest(helper);
		fenceInPlot(helper);
		TestSupport.setTime(helper, 18000);
		List<Animal> cows = new ArrayList<>();
		for (BlockPos rel : List.of(new BlockPos(24, 2, 12), new BlockPos(26, 2, 20), new BlockPos(22, 2, 24), new BlockPos(28, 2, 16))) {
			cows.add(animal(helper, EntityTypes.COW, rel));
		}
		CompanionEntity fern = fern(helper, new BlockPos(20, 2, 16));
		TestSupport.give(fern, new ItemStack(Items.IRON_SWORD));
		helper.onEachTick(() -> {
			for (Animal a : cows) {
				helper.assertTrue(unharmed(a), "no hunting at night: " + where(helper, fern));
			}
		});
		helper.runAtTickTime(900, () -> {
			helper.assertTrue(data.stat("animals_hunted") == 0, "nothing was hunted");
			finish(helper);
		});
	}

	// ------------------------------------------------------------------ safety

	/** A friend who finds themself shut in the pen walks out through the gate and shuts it behind them. */
	@GameTest(structure = TestSupport.PLOT, environment = "hardcorefriends-test:solo_146", maxTicks = 1200)
	public void nobodyStaysShutInThePen(GameTestHelper helper) {
		CampData data = atStage(helper, 2);
		fenceInPlot(helper);
		Pen pen = buildPen(helper, data);
		animal(helper, EntityTypes.CHICKEN, new BlockPos(28, 2, 14));
		CompanionEntity oak = TestSupport.spawnFriend(helper, FriendId.OAK, new BlockPos(27, 2, 17));
		helper.succeedWhen(() -> {
			helper.assertFalse(pen.holds(oak), "Oak got out of the pen: " + where(helper, oak));
			helper.assertFalse(pen.gateOpen(helper.getLevel()), "and shut the gate on the chicken");
			helper.assertTrue(count(helper, pen, EntityTypes.CHICKEN, false) == 1, "the chicken is still inside");
		});
	}
}

package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskRegistry;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.command.FriendsCommand;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEvents;
import io.github.bradley09roberts.hardcorefriends.companion.Role;

/**
 * Better builds: building plans kept as data files (houses, shops, workplaces, civic buildings, decorations) in
 * many styles, the materials they are built from (stairs, stone bricks, glass, beds, barrels...) and how the friends
 * make them, and building tall things safely (scaffolding they take down again). Provides {@code civic.BlueprintLibrary}.
 *
 * <p>Registered from {@code HardcoreFriends.onInitialize} through {@link #init()}: the plan library's data reload
 * listener, the vanilla recipes for the new materials, the jobs (taking down scaffolding, firing materials at the
 * furnace, digging sand and clay, shearing sheep), the reflex that brings a friend down from scaffolding, the camp
 * needs raised by what buildings are short of, {@code /friends builds}, and the friends' words.
 */
public final class Architecture {
	private Architecture() {
	}

	public static void init() {
		PlanLibrary.register();
		ArchitectureLines.register();
		recipes();
		jobs();
		CompanionEvents.GOALS.add((companion, goals, targets) -> goals.addGoal(0, new ScaffoldDescentGoal(companion)));
		CampNeeds.EXTRA.add(MaterialDemand::extraNeeds);
		FriendsCommand.EXTENSIONS.add(BuildsCommand::register);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MaterialDemand.clearAll());
	}

	/**
	 * The jobs, for everyone, each wrapped for its speciality so the specialist does it first: taking down scaffolding
	 * and firing materials for the builder, digging sand and clay for the forager, shearing for the farmer. Each works
	 * on one shared thing (a pillar, the furnace, the sand, the sheep), so one friend at a time.
	 */
	private static void jobs() {
		SpecialityTask.EXCLUSIVE.addAll(Set.of(ScaffoldCleanupTask.ID, KilnTask.ID, DigSandTask.ID, ShearSheepTask.ID));
		TaskRegistry.PACKS.add(id -> List.of(
			new SpecialityTask(new ScaffoldCleanupTask(), Role.BUILDER),
			new SpecialityTask(new KilnTask(), Role.BUILDER),
			new SpecialityTask(new DigSandTask(), Role.FORAGER),
			new SpecialityTask(new ShearSheepTask(), Role.FARMER)));
	}

	/**
	 * Vanilla recipes (vanilla quantities) for the materials plans use that the core crafting does not know yet. Wood
	 * shapes, stripped logs, carpets and beds are made by {@code camp.build.WoodWork}, which keeps the wood or colour.
	 */
	private static void recipes() {
		add(Items.WOOL.pick(DyeColor.WHITE), 1, false, Crafting.of(Items.STRING, 4));
		add(Items.COBBLESTONE_STAIRS, 4, true, Crafting.of(Items.COBBLESTONE, 6));
		add(Items.COBBLESTONE_WALL, 6, true, Crafting.of(Items.COBBLESTONE, 6));
		add(Items.STONE_STAIRS, 4, true, Crafting.of(Items.STONE, 6));
		add(Items.STONE_SLAB, 6, true, Crafting.of(Items.STONE, 3));
		add(Items.SMOOTH_STONE_SLAB, 6, true, Crafting.of(Items.SMOOTH_STONE, 3));
		add(Items.STONE_BRICKS, 4, false, Crafting.of(Items.STONE, 4));
		add(Items.STONE_BRICK_STAIRS, 4, true, Crafting.of(Items.STONE_BRICKS, 6));
		add(Items.STONE_BRICK_SLAB, 6, true, Crafting.of(Items.STONE_BRICKS, 3));
		add(Items.STONE_BRICK_WALL, 6, true, Crafting.of(Items.STONE_BRICKS, 6));
		add(Items.BRICKS, 1, false, Crafting.of(Items.BRICK, 4));
		add(Items.BRICK_STAIRS, 4, true, Crafting.of(Items.BRICKS, 6));
		add(Items.BRICK_SLAB, 6, true, Crafting.of(Items.BRICKS, 3));
		add(Items.SANDSTONE, 1, false, Crafting.of(Items.SAND, 4));
		add(Items.CUT_SANDSTONE, 4, false, Crafting.of(Items.SANDSTONE, 4));
		add(Items.SANDSTONE_STAIRS, 4, true, Crafting.of(Items.SANDSTONE, 6));
		add(Items.SANDSTONE_SLAB, 6, true, Crafting.of(Items.SANDSTONE, 3));
		add(Items.SANDSTONE_WALL, 6, true, Crafting.of(Items.SANDSTONE, 6));
		add(Items.HAY_BLOCK, 1, true, Crafting.of(Items.WHEAT, 9));
		add(Items.BARREL, 1, true, Crafting.of(ItemTags.PLANKS, 6), Crafting.of(ItemTags.WOODEN_SLABS, 2));
		add(Items.FLOWER_POT, 1, true, Crafting.of(Items.BRICK, 3));
		add(Items.IRON_BARS, 16, true, Crafting.of(Items.IRON_INGOT, 6));
		add(Items.IRON_CHAIN, 1, true, Crafting.of(Items.IRON_INGOT, 1), Crafting.of(Items.IRON_NUGGET, 2));
		add(Items.SMOKER, 1, true, Crafting.of(Items.FURNACE, 1), Crafting.of(ItemTags.LOGS, 4));
		add(Items.BLAST_FURNACE, 1, true, Crafting.of(Items.IRON_INGOT, 5), Crafting.of(Items.FURNACE, 1),
			Crafting.of(Items.SMOOTH_STONE, 3));
		add(Items.SMITHING_TABLE, 1, true, Crafting.of(Items.IRON_INGOT, 2), Crafting.of(ItemTags.PLANKS, 4));
		add(Items.FLETCHING_TABLE, 1, true, Crafting.of(Items.FLINT, 2), Crafting.of(ItemTags.PLANKS, 4));
		add(Items.CARTOGRAPHY_TABLE, 1, true, Crafting.of(Items.PAPER, 2), Crafting.of(ItemTags.PLANKS, 4));
		add(Items.LOOM, 1, false, Crafting.of(Items.STRING, 2), Crafting.of(ItemTags.PLANKS, 2));
		add(Items.STONECUTTER, 1, true, Crafting.of(Items.IRON_INGOT, 1), Crafting.of(Items.STONE, 3));
		add(Items.GRINDSTONE, 1, true, Crafting.of(Items.STICK, 2), Crafting.of(Items.STONE_SLAB, 1), Crafting.of(ItemTags.PLANKS, 2));
		add(Items.COMPOSTER, 1, true, Crafting.of(ItemTags.WOODEN_SLABS, 7));
		add(Items.LECTERN, 1, true, Crafting.of(ItemTags.WOODEN_SLABS, 4), Crafting.of(Items.BOOKSHELF, 1));
		add(Items.CAULDRON, 1, true, Crafting.of(Items.IRON_INGOT, 7));
		add(Items.SHEARS, 1, false, Crafting.of(Items.IRON_INGOT, 2));
	}

	/** Adds a recipe unless another package already taught the friends one for this item. */
	private static void add(Item output, int count, boolean needsTable, Crafting.Ingredient... inputs) {
		if (!Crafting.hasAddedRecipe(output)) {
			Crafting.addRecipe(output, count, needsTable, inputs);
		}
	}
}

package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Hard-coded crafting with real ingredients, taken from and returned to a friend's backpack. Recipes match vanilla
 * quantities. Wooden items keep the wood type of the planks used. Anything that needs a 3×3 grid requires a crafting
 * table within 6 blocks, just like for a player.
 */
public final class Crafting {
	/** Wooden shapes whose item id is {@code <wood>_<shape>}. */
	public enum WoodShape {
		PLANKS("planks", 4),
		SLAB("slab", 6),
		DOOR("door", 3),
		FENCE("fence", 3),
		FENCE_GATE("fence_gate", 1),
		PRESSURE_PLATE("pressure_plate", 1);

		private final String suffix;
		private final int yield;

		WoodShape(String suffix, int yield) {
			this.suffix = suffix;
			this.yield = yield;
		}
	}

	public enum Tier {
		WOOD,
		STONE,
		IRON
	}

	private static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
	private static final Predicate<ItemStack> STICK = s -> s.is(Items.STICK);
	private static final Predicate<ItemStack> COAL = s -> s.is(ItemTags.COALS);
	private static final Predicate<ItemStack> STONE_MATERIAL = s -> s.is(ItemTags.STONE_TOOL_MATERIALS);
	private static final Predicate<ItemStack> IRON = s -> s.is(Items.IRON_INGOT);

	private Crafting() {
	}

	// --------------------------------------------------------------- stations

	public static boolean nearCraftingTable(CompanionEntity c) {
		return findNearby(c, 6).isPresent();
	}

	public static Optional<BlockPos> findNearby(CompanionEntity c, int radius) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos centre = c.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
			if (level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
				return Optional.of(p.immutable());
			}
		}
		return Optional.empty();
	}

	// ------------------------------------------------------------------ wood

	/** The planks a log (or stem, or bamboo block) turns into, or empty if it is not a log. */
	public static Optional<Item> planksFor(ItemStack log) {
		if (log.is(Items.BAMBOO_BLOCK) || log.is(Items.STRIPPED_BAMBOO_BLOCK)) {
			return Optional.of(Items.BAMBOO_PLANKS);
		}
		if (!log.is(ItemTags.LOGS)) {
			return Optional.empty();
		}
		String path = BuiltInRegistries.ITEM.getKey(log.getItem()).getPath();
		String wood = path.replace("stripped_", "").replace("_log", "").replace("_wood", "")
			.replace("_stem", "").replace("_hyphae", "");
		return item(wood + "_planks").or(() -> Optional.of(Items.OAK_PLANKS));
	}

	/** Wood type name of a planks item, e.g. {@code "spruce"}. */
	public static String woodOf(ItemStack planks) {
		return BuiltInRegistries.ITEM.getKey(planks.getItem()).getPath().replace("_planks", "");
	}

	private static Optional<Item> item(String path) {
		Identifier id = Identifier.withDefaultNamespace(path.toLowerCase(Locale.ROOT));
		return BuiltInRegistries.ITEM.getOptional(id).filter(i -> i != Items.AIR);
	}

	/** Turns logs from the backpack into planks until at least {@code planks} planks are carried. */
	public static boolean ensurePlanks(Backpack bp, int planks) {
		while (bp.count(PLANKS) < planks) {
			ItemStack log = bp.find(s -> planksFor(s).isPresent());
			if (log.isEmpty()) {
				return false;
			}
			Item out = planksFor(log).get();
			ItemStack logOne = log.copyWithCount(1);
			if (!bp.canFit(new ItemStack(out, 4))) {
				return false;
			}
			bp.remove(s -> ItemStack.isSameItemSameComponents(s, logOne), 1);
			bp.insert(new ItemStack(out, 4));
		}
		return true;
	}

	/**
	 * Crafts a wooden shape from planks of one wood type (falls back to any planks). Doors, fences, gates, slabs and
	 * pressure plates need a crafting table; planks do not. Returns the number of items made.
	 */
	public static int craftWood(CompanionEntity c, WoodShape shape, int wanted) {
		Backpack bp = c.backpack();
		if (shape == WoodShape.PLANKS) {
			int before = bp.count(PLANKS);
			ensurePlanks(bp, before + wanted);
			return bp.count(PLANKS) - before;
		}
		if (!nearCraftingTable(c)) {
			return 0;
		}
		int made = 0;
		while (made < wanted) {
			int planksNeeded = switch (shape) {
				case SLAB -> 3;
				case DOOR -> 6;
				case FENCE -> 4;
				case FENCE_GATE -> 2;
				case PRESSURE_PLATE -> 2;
				default -> 0;
			};
			int sticksNeeded = shape == WoodShape.FENCE ? 2 : shape == WoodShape.FENCE_GATE ? 4 : 0;
			ensurePlanks(bp, planksNeeded);
			if (sticksNeeded > 0 && !ensureSticks(bp, sticksNeeded)) {
				break;
			}
			ensurePlanks(bp, planksNeeded);
			ItemStack sample = bp.find(PLANKS);
			if (sample.isEmpty() || bp.count(PLANKS) < planksNeeded) {
				break;
			}
			String wood = woodOf(sample);
			Item out = item(wood + "_" + shape.suffix).or(() -> item("oak_" + shape.suffix)).orElse(Items.AIR);
			if (out == Items.AIR || !bp.canFit(new ItemStack(out, shape.yield))) {
				break;
			}
			// Prefer planks of the sampled wood so a door is all one kind.
			Item planksItem = sample.getItem();
			int fromSame = bp.remove(s -> s.is(planksItem), planksNeeded);
			if (fromSame < planksNeeded) {
				bp.remove(PLANKS, planksNeeded - fromSame);
			}
			bp.remove(STICK, sticksNeeded);
			bp.insert(new ItemStack(out, shape.yield));
			made += shape.yield;
		}
		return made;
	}

	// ---------------------------------------------------------------- basics

	public static boolean ensureSticks(Backpack bp, int sticks) {
		while (bp.count(STICK) < sticks) {
			if (!ensurePlanks(bp, 2) || !bp.canFit(new ItemStack(Items.STICK, 4))) {
				return false;
			}
			bp.remove(PLANKS, 2);
			bp.insert(new ItemStack(Items.STICK, 4));
		}
		return true;
	}

	/** Makes torches (1 coal or charcoal + 1 stick = 4) until {@code torches} are carried. */
	public static boolean ensureTorches(Backpack bp, int torches) {
		while (bp.count(Items.TORCH) < torches) {
			if (!bp.has(COAL) || !ensureSticks(bp, 1) || !bp.canFit(new ItemStack(Items.TORCH, 4))) {
				return false;
			}
			bp.remove(COAL, 1);
			bp.remove(STICK, 1);
			bp.insert(new ItemStack(Items.TORCH, 4));
		}
		return true;
	}

	/**
	 * Tries to have {@code count} of a common camp item in the backpack, crafting it from carried ingredients.
	 * Supported: sticks, torches, crafting table, chest, furnace, campfire, ladder, glass pane, iron nugget, lantern,
	 * hopper, redstone torch, daylight detector, redstone lamp, bread, cobblestone slab. Items needing a crafting
	 * table are only made when one is within 6 blocks (the crafting table itself is a 2×2 recipe).
	 */
	public static boolean ensure(CompanionEntity c, Item target, int count) {
		Backpack bp = c.backpack();
		int guard = 0;
		while (bp.count(target) < count && guard++ < 64) {
			if (!craftOnce(c, target)) {
				return false;
			}
		}
		return bp.count(target) >= count;
	}

	private static boolean craftOnce(CompanionEntity c, Item target) {
		Backpack bp = c.backpack();
		boolean table = nearCraftingTable(c);
		if (target == Items.STICK) {
			return ensureSticks(bp, bp.count(Items.STICK) + 1);
		}
		if (target == Items.TORCH) {
			return ensureTorches(bp, bp.count(Items.TORCH) + 1);
		}
		if (target == Items.CRAFTING_TABLE) {
			return ensurePlanks(bp, 4) && swap(bp, List.of(in(PLANKS, 4)), new ItemStack(Items.CRAFTING_TABLE));
		}
		if (!table) {
			return false;
		}
		if (target == Items.CHEST) {
			return ensurePlanks(bp, 8) && swap(bp, List.of(in(PLANKS, 8)), new ItemStack(Items.CHEST));
		}
		if (target == Items.FURNACE) {
			return swap(bp, List.of(in(STONE_MATERIAL, 8)), new ItemStack(Items.FURNACE));
		}
		if (target == Items.CAMPFIRE) {
			return ensureSticks(bp, 3) && swap(bp, List.of(in(STICK, 3), in(COAL, 1), in(s -> s.is(ItemTags.LOGS), 3)), new ItemStack(Items.CAMPFIRE));
		}
		if (target == Items.LADDER) {
			return ensureSticks(bp, 7) && swap(bp, List.of(in(STICK, 7)), new ItemStack(Items.LADDER, 3));
		}
		if (target == Items.GLASS_PANE) {
			return swap(bp, List.of(in(s -> s.is(Items.GLASS), 6)), new ItemStack(Items.GLASS_PANE, 16));
		}
		if (target == Items.IRON_NUGGET) {
			return swap(bp, List.of(in(IRON, 1)), new ItemStack(Items.IRON_NUGGET, 9));
		}
		if (target == Items.LANTERN) {
			if (bp.count(Items.IRON_NUGGET) < 8 && !swap(bp, List.of(in(IRON, 1)), new ItemStack(Items.IRON_NUGGET, 9))) {
				return false;
			}
			return ensureTorches(bp, 1) && swap(bp, List.of(in(s -> s.is(Items.IRON_NUGGET), 8), in(s -> s.is(Items.TORCH), 1)), new ItemStack(Items.LANTERN));
		}
		if (target == Items.HOPPER) {
			if (!bp.has(s -> s.is(Items.CHEST)) && !craftOnce(c, Items.CHEST)) {
				return false;
			}
			return swap(bp, List.of(in(IRON, 5), in(s -> s.is(Items.CHEST), 1)), new ItemStack(Items.HOPPER));
		}
		if (target == Items.REDSTONE_TORCH) {
			return ensureSticks(bp, 1) && swap(bp, List.of(in(s -> s.is(Items.REDSTONE), 1), in(STICK, 1)), new ItemStack(Items.REDSTONE_TORCH));
		}
		if (target == Items.DAYLIGHT_DETECTOR) {
			if (bp.count(ItemTags.WOODEN_SLABS) < 3) {
				craftWood(c, WoodShape.SLAB, 3);
			}
			return swap(bp, List.of(in(s -> s.is(Items.GLASS), 3), in(s -> s.is(Items.QUARTZ), 3), in(s -> s.is(ItemTags.WOODEN_SLABS), 3)),
				new ItemStack(Items.DAYLIGHT_DETECTOR));
		}
		if (target == Items.REDSTONE_LAMP) {
			return swap(bp, List.of(in(s -> s.is(Items.REDSTONE), 4), in(s -> s.is(Items.GLOWSTONE), 1)), new ItemStack(Items.REDSTONE_LAMP));
		}
		if (target == Items.BREAD) {
			return swap(bp, List.of(in(s -> s.is(Items.WHEAT), 3)), new ItemStack(Items.BREAD));
		}
		if (target == Items.COBBLESTONE_SLAB) {
			return swap(bp, List.of(in(s -> s.is(Items.COBBLESTONE), 3)), new ItemStack(Items.COBBLESTONE_SLAB, 6));
		}
		return craftExtra(c, target, table, 0);
	}

	// ------------------------------------------------------- added recipes

	/** One ingredient of an added recipe: how many of the matching items it uses up. */
	public record Ingredient(Predicate<ItemStack> match, int count, @Nullable Item item) {
	}

	/**
	 * A recipe a feature package adds with {@link #addRecipe}: {@code count} of {@code output} from the ingredients,
	 * at a crafting table if {@code needsTable}. {@link #ensure} uses these after the built-in ones, and makes a missing
	 * single-item ingredient that has a recipe of its own first (sticks, planks, paper for books...).
	 */
	public record Recipe(Item output, int count, boolean needsTable, List<Ingredient> inputs) {
	}

	private static final List<Recipe> ADDED = new CopyOnWriteArrayList<>();
	/** How deep {@link #ensure} goes making ingredients of ingredients. */
	private static final int MAX_RECIPE_DEPTH = 4;

	/** Adds a recipe the friends can craft (called from a feature package's {@code init()}). */
	public static void addRecipe(Item output, int count, boolean needsTable, Ingredient... inputs) {
		ADDED.add(new Recipe(output, count, needsTable, List.of(inputs)));
	}

	/** {@code count} of one item. */
	public static Ingredient of(Item item, int count) {
		return new Ingredient(s -> s.is(item), count, item);
	}

	/** {@code count} of any item in the tag (planks, logs, wool...). */
	public static Ingredient of(TagKey<Item> tag, int count) {
		return new Ingredient(s -> s.is(tag), count, null);
	}

	/** True if the friends know how to make this item (built in or added). */
	public static boolean hasAddedRecipe(Item item) {
		for (Recipe r : ADDED) {
			if (r.output() == item) {
				return true;
			}
		}
		return false;
	}

	private static boolean craftExtra(CompanionEntity c, Item target, boolean table, int depth) {
		Backpack bp = c.backpack();
		for (Recipe recipe : ADDED) {
			if (recipe.output() != target || recipe.needsTable() && !table) {
				continue;
			}
			boolean ready = true;
			for (Ingredient input : recipe.inputs()) {
				int have = bp.count(input.match());
				if (have >= input.count()) {
					continue;
				}
				Item sub = input.item();
				if (sub == null || depth >= MAX_RECIPE_DEPTH) {
					ready = false;
					break;
				}
				int guard = 0;
				while (bp.count(input.match()) < input.count() && guard++ < 64) {
					boolean made = sub == Items.STICK ? ensureSticks(bp, bp.count(Items.STICK) + 1)
						: sub == Items.TORCH ? ensureTorches(bp, bp.count(Items.TORCH) + 1)
						: craftExtra(c, sub, table, depth + 1);
					if (!made) {
						break;
					}
				}
				if (bp.count(input.match()) < input.count()) {
					ready = false;
					break;
				}
			}
			if (!ready) {
				continue;
			}
			List<Input> inputs = new ArrayList<>();
			for (Ingredient input : recipe.inputs()) {
				inputs.add(in(input.match(), input.count()));
			}
			if (swap(bp, inputs, new ItemStack(recipe.output(), recipe.count()))) {
				return true;
			}
		}
		return false;
	}

	// ----------------------------------------------------------------- tools

	/** The best tool tier whose ingredients are carried, or empty. Iron beats stone beats wood. */
	public static Optional<Tier> affordableTier(Backpack bp, TagKey<Item> toolTag) {
		int head = headCount(toolTag);
		int sticks = toolTag == ItemTags.SWORDS ? 1 : 2;
		boolean sticksOk = bp.count(STICK) >= sticks || bp.count(PLANKS) >= 2 || bp.has(s -> s.is(ItemTags.LOGS));
		if (!sticksOk) {
			return Optional.empty();
		}
		if (bp.count(IRON) >= head) {
			return Optional.of(Tier.IRON);
		}
		if (bp.count(STONE_MATERIAL) >= head) {
			return Optional.of(Tier.STONE);
		}
		int planksAvailable = bp.count(PLANKS) + 4 * bp.count(s -> s.is(ItemTags.LOGS));
		if (planksAvailable >= head + 2) {
			return Optional.of(Tier.WOOD);
		}
		return Optional.empty();
	}

	/**
	 * Crafts the best affordable tool of a kind (pickaxe, axe, shovel, hoe or sword) at a crafting table. Returns
	 * true if one was made and put in the backpack.
	 */
	public static boolean craftTool(CompanionEntity c, TagKey<Item> toolTag) {
		if (!nearCraftingTable(c)) {
			return false;
		}
		Backpack bp = c.backpack();
		Optional<Tier> tier = affordableTier(bp, toolTag);
		if (tier.isEmpty()) {
			return false;
		}
		int head = headCount(toolTag);
		int sticks = toolTag == ItemTags.SWORDS ? 1 : 2;
		Predicate<ItemStack> headMaterial = switch (tier.get()) {
			case IRON -> IRON;
			case STONE -> STONE_MATERIAL;
			case WOOD -> PLANKS;
		};
		if (tier.get() == Tier.WOOD) {
			ensurePlanks(bp, head + 2);
		}
		if (!ensureSticks(bp, sticks)) {
			return false;
		}
		Item tool = toolItem(toolTag, tier.get());
		return swap(bp, List.of(in(headMaterial, head), in(STICK, sticks)), new ItemStack(tool));
	}

	private static int headCount(TagKey<Item> toolTag) {
		if (toolTag == ItemTags.PICKAXES || toolTag == ItemTags.AXES) {
			return 3;
		}
		if (toolTag == ItemTags.HOES || toolTag == ItemTags.SWORDS) {
			return 2;
		}
		return 1; // shovel
	}

	public static Item toolItem(TagKey<Item> toolTag, Tier tier) {
		String material = switch (tier) {
			case WOOD -> "wooden";
			case STONE -> "stone";
			case IRON -> "iron";
		};
		String kind = toolTag == ItemTags.PICKAXES ? "pickaxe" : toolTag == ItemTags.AXES ? "axe"
			: toolTag == ItemTags.HOES ? "hoe" : toolTag == ItemTags.SWORDS ? "sword" : "shovel";
		return item(material + "_" + kind).orElse(Items.WOODEN_PICKAXE);
	}

	// --------------------------------------------------------------- helpers

	private record Input(Predicate<ItemStack> match, int count) {
	}

	private static Input in(Predicate<ItemStack> match, int count) {
		return new Input(match, count);
	}

	/** Consumes all inputs and adds the output, only if every input is present and the output fits. */
	private static boolean swap(Backpack bp, List<Input> inputs, ItemStack output) {
		for (Input input : inputs) {
			if (bp.count(input.match()) < input.count()) {
				return false;
			}
		}
		if (!bp.canFit(output)) {
			// Consuming the inputs might free a slot; check by simulation-light rule: require one free slot.
			if (bp.freeSlots() == 0) {
				return false;
			}
		}
		for (Input input : inputs) {
			bp.remove(input.match(), input.count());
		}
		ItemStack left = bp.insert(output.copy());
		return left.isEmpty() || left.getCount() < output.getCount();
	}
}

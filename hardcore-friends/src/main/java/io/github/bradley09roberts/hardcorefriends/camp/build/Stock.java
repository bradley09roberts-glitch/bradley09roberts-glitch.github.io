package io.github.bradley09roberts.hardcorefriends.camp.build;

import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds.Need;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Every item the builders handle: raw materials gathered by the team and the things crafted from them for
 * blueprints. Each crafted kind knows its vanilla recipe (inputs per craft, output per craft, whether it needs a
 * crafting table) and which {@link Crafting} call makes it from real ingredients.
 */
public enum Stock {
	// Raw materials
	LOG("log", "logs", s -> s.is(ItemTags.LOGS), Need.WOOD),
	COAL("coal", "coal", s -> s.is(ItemTags.COALS), Need.TORCHES),
	COBBLESTONE("cobblestone", "cobblestone", s -> s.is(Items.COBBLESTONE), Need.STONE),
	STONE_MATERIAL("cobblestone", "cobblestone", s -> s.is(ItemTags.STONE_TOOL_MATERIALS), Need.STONE),
	FILL("dirt or cobblestone", "dirt or cobblestone", s -> s.is(Items.DIRT) || s.is(Items.COBBLESTONE), Need.DIRT),
	GLASS("glass", "glass", s -> s.is(Items.GLASS), Need.BUILD),
	IRON("iron ingot", "iron ingots", s -> s.is(Items.IRON_INGOT), Need.ORE),
	REDSTONE("redstone", "redstone", s -> s.is(Items.REDSTONE), Need.BUILD),
	GLOWSTONE("glowstone", "glowstone", s -> s.is(Items.GLOWSTONE), Need.BUILD),
	QUARTZ("quartz", "quartz", s -> s.is(Items.QUARTZ), Need.BUILD),

	// Crafted
	PLANKS("plank", "planks", s -> s.is(ItemTags.PLANKS), Need.WOOD),
	STICK("stick", "sticks", s -> s.is(Items.STICK), Need.WOOD),
	SLAB("wooden slab", "wooden slabs", s -> s.is(ItemTags.WOODEN_SLABS), Need.WOOD),
	DOOR("wooden door", "wooden doors", s -> s.is(ItemTags.WOODEN_DOORS), Need.WOOD),
	FENCE("fence", "fences", s -> s.is(ItemTags.WOODEN_FENCES), Need.WOOD),
	PRESSURE_PLATE("pressure plate", "pressure plates", s -> s.is(ItemTags.WOODEN_PRESSURE_PLATES), Need.WOOD),
	TORCH("torch", "torches", s -> s.is(Items.TORCH), Need.TORCHES),
	GLASS_PANE("glass pane", "glass panes", s -> s.is(Items.GLASS_PANE), Need.BUILD),
	LADDER("ladder", "ladders", s -> s.is(Items.LADDER), Need.WOOD),
	CHEST("chest", "chests", s -> s.is(Items.CHEST), Need.WOOD),
	CRAFTING_TABLE("crafting table", "crafting tables", s -> s.is(Items.CRAFTING_TABLE), Need.WOOD),
	FURNACE("furnace", "furnaces", s -> s.is(Items.FURNACE), Need.STONE),
	CAMPFIRE("campfire", "campfires", s -> s.is(Items.CAMPFIRE), Need.WOOD),
	IRON_NUGGET("iron nugget", "iron nuggets", s -> s.is(Items.IRON_NUGGET), Need.ORE),
	HOPPER("hopper", "hoppers", s -> s.is(Items.HOPPER), Need.ORE),
	LANTERN("lantern", "lanterns", s -> s.is(Items.LANTERN), Need.ORE),
	REDSTONE_LAMP("redstone lamp", "redstone lamps", s -> s.is(Items.REDSTONE_LAMP), Need.BUILD),
	DAYLIGHT_DETECTOR("daylight detector", "daylight detectors", s -> s.is(Items.DAYLIGHT_DETECTOR), Need.BUILD);

	/** One vanilla recipe: inputs per craft, items made per craft, and whether a crafting table is needed. */
	public record Recipe(int yield, boolean needsTable, Map<Stock, Integer> inputs) {
	}

	private final String singular;
	private final String plural;
	private final Predicate<ItemStack> item;
	private final Need need;

	Stock(String singular, String plural, Predicate<ItemStack> item, Need need) {
		this.singular = singular;
		this.plural = plural;
		this.item = item;
		this.need = need;
	}

	public Predicate<ItemStack> item() {
		return item;
	}

	public boolean matches(ItemStack stack) {
		return !stack.isEmpty() && item.test(stack);
	}

	/** "1 torch", "12 planks". */
	public String describe(int count) {
		return count + " " + (count == 1 ? singular : plural);
	}

	/** Which camp need a shortage of this item feeds. */
	public Need need() {
		return need;
	}

	/** The vanilla recipe, or null for raw materials that must be gathered. */
	public @Nullable Recipe recipe() {
		return switch (this) {
			case PLANKS -> new Recipe(4, false, Map.of(LOG, 1));
			case STICK -> new Recipe(4, false, Map.of(PLANKS, 2));
			case SLAB -> new Recipe(6, true, Map.of(PLANKS, 3));
			case DOOR -> new Recipe(3, true, Map.of(PLANKS, 6));
			case FENCE -> new Recipe(3, true, Map.of(PLANKS, 4, STICK, 2));
			case PRESSURE_PLATE -> new Recipe(1, true, Map.of(PLANKS, 2));
			case TORCH -> new Recipe(4, false, Map.of(COAL, 1, STICK, 1));
			case GLASS_PANE -> new Recipe(16, true, Map.of(GLASS, 6));
			case LADDER -> new Recipe(3, true, Map.of(STICK, 7));
			case CHEST -> new Recipe(1, true, Map.of(PLANKS, 8));
			case CRAFTING_TABLE -> new Recipe(1, false, Map.of(PLANKS, 4));
			case FURNACE -> new Recipe(1, true, Map.of(STONE_MATERIAL, 8));
			case CAMPFIRE -> new Recipe(1, true, Map.of(STICK, 3, COAL, 1, LOG, 3));
			case IRON_NUGGET -> new Recipe(9, true, Map.of(IRON, 1));
			case HOPPER -> new Recipe(1, true, Map.of(IRON, 5, CHEST, 1));
			case LANTERN -> new Recipe(1, true, Map.of(IRON_NUGGET, 8, TORCH, 1));
			case REDSTONE_LAMP -> new Recipe(1, true, Map.of(REDSTONE, 4, GLOWSTONE, 1));
			case DAYLIGHT_DETECTOR -> new Recipe(1, true, Map.of(GLASS, 3, QUARTZ, 3, SLAB, 3));
			default -> null;
		};
	}

	/**
	 * Crafts from what the friend carries until at least {@code target} are carried. Uses the core {@link Crafting}
	 * recipes, which consume real ingredients. Returns true when the target is reached.
	 */
	boolean craft(CompanionEntity c, int target) {
		var bp = c.backpack();
		int have = bp.count(item);
		if (have >= target) {
			return true;
		}
		switch (this) {
			case PLANKS -> Crafting.ensurePlanks(bp, target);
			case STICK -> Crafting.ensureSticks(bp, target);
			case TORCH -> Crafting.ensureTorches(bp, target);
			case SLAB -> Crafting.craftWood(c, Crafting.WoodShape.SLAB, target - have);
			case DOOR -> Crafting.craftWood(c, Crafting.WoodShape.DOOR, target - have);
			case FENCE -> Crafting.craftWood(c, Crafting.WoodShape.FENCE, target - have);
			case PRESSURE_PLATE -> Crafting.craftWood(c, Crafting.WoodShape.PRESSURE_PLATE, target - have);
			default -> {
				Item out = vanillaItem();
				if (out != null) {
					Crafting.ensure(c, out, target);
				}
			}
		}
		return bp.count(item) >= target;
	}

	private @Nullable Item vanillaItem() {
		return switch (this) {
			case GLASS_PANE -> Items.GLASS_PANE;
			case LADDER -> Items.LADDER;
			case CHEST -> Items.CHEST;
			case CRAFTING_TABLE -> Items.CRAFTING_TABLE;
			case FURNACE -> Items.FURNACE;
			case CAMPFIRE -> Items.CAMPFIRE;
			case IRON_NUGGET -> Items.IRON_NUGGET;
			case HOPPER -> Items.HOPPER;
			case LANTERN -> Items.LANTERN;
			case REDSTONE_LAMP -> Items.REDSTONE_LAMP;
			case DAYLIGHT_DETECTOR -> Items.DAYLIGHT_DETECTOR;
			default -> null;
		};
	}
}

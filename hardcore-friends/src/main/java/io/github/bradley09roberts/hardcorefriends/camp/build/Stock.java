package io.github.bradley09roberts.hardcorefriends.camp.build;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TallFlowerBlock;

import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds.Need;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;

/**
 * Every item the builders handle: raw materials gathered by the team, things smelted at the camp furnace, and the
 * things crafted from them for blueprints. Each crafted kind knows its vanilla recipe (inputs per craft, output per
 * craft, whether it needs a crafting table), sometimes a second one to fall back on (a straw bed when there is no
 * wool), and which {@link Crafting} or {@link WoodWork} call makes it from real ingredients. Smelted kinds say what
 * they are smelted from ({@link #smeltedFrom()}); the kiln job makes them.
 */
public enum Stock {
	// Raw materials
	LOG("log", "logs", s -> s.is(ItemTags.LOGS), Need.WOOD),
	COAL("coal", "coal", s -> s.is(ItemTags.COALS), Need.TORCHES),
	COBBLESTONE("cobblestone", "cobblestone", s -> s.is(Items.COBBLESTONE), Need.STONE),
	STONE_MATERIAL("cobblestone", "cobblestone", s -> s.is(ItemTags.STONE_TOOL_MATERIALS), Need.STONE),
	FILL("dirt or cobblestone", "dirt or cobblestone", s -> s.is(Items.DIRT) || s.is(Items.COBBLESTONE), Need.DIRT),
	/** Smelted from sand at the camp furnace. */
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
	FENCE_GATE("fence gate", "fence gates", s -> s.is(ItemTags.FENCE_GATES), Need.WOOD),
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
	DAYLIGHT_DETECTOR("daylight detector", "daylight detectors", s -> s.is(Items.DAYLIGHT_DETECTOR), Need.BUILD),

	// Sage's plan (package progress): raw materials the plan's work gathers, and what is crafted from them
	SUGAR_CANE("sugar cane", "sugar cane", s -> s.is(Items.SUGAR_CANE), Need.BUILD),
	LEATHER("leather", "leather", s -> s.is(Items.LEATHER), Need.BUILD),
	DIAMOND("diamond", "diamonds", s -> s.is(Items.DIAMOND), Need.ORE),
	OBSIDIAN("obsidian", "obsidian", s -> s.is(Items.OBSIDIAN), Need.ORE),
	BLAZE_ROD("blaze rod", "blaze rods", s -> s.is(Items.BLAZE_ROD), Need.BUILD),
	PAPER("paper", "paper", s -> s.is(Items.PAPER), Need.BUILD),
	BOOK("book", "books", s -> s.is(Items.BOOK), Need.BUILD),
	BOOKSHELF("bookshelf", "bookshelves", s -> s.is(Items.BOOKSHELF), Need.BUILD),
	ENCHANTING_TABLE("enchanting table", "enchanting tables", s -> s.is(Items.ENCHANTING_TABLE), Need.BUILD),
	IRON_BLOCK("block of iron", "blocks of iron", s -> s.is(Items.IRON_BLOCK), Need.ORE),
	ANVIL("anvil", "anvils", s -> s.is(Items.ANVIL), Need.ORE),
	BREWING_STAND("brewing stand", "brewing stands", s -> s.is(Items.BREWING_STAND), Need.BUILD),

	// ---- Better builds (package architecture) ----
	// Raw materials
	/** Sand or red sand: both smelt into glass. */
	SAND("sand", "sand", s -> s.is(ItemTags.SMELTS_TO_GLASS), Need.DIRT),
	CLAY_BALL("clay ball", "clay balls", s -> s.is(Items.CLAY_BALL), Need.DIRT),
	STRING("string", "string", s -> s.is(Items.STRING), Need.BUILD),
	WHEAT("wheat", "wheat", s -> s.is(Items.WHEAT), Need.FOOD),
	FLINT("flint", "flint", s -> s.is(Items.FLINT), Need.STONE),
	FLOWER("flower", "flowers", Stock::isSmallFlower, Need.BUILD),
	TALL_FLOWER("tall flower", "tall flowers", Stock::isTallFlower, Need.BUILD),
	DIRT("dirt", "dirt", s -> s.is(Items.DIRT), Need.DIRT),
	/** Friends cannot make a bell: only one already in the supply chest is used. */
	BELL("bell", "bells", s -> s.is(Items.BELL), Need.BUILD),
	/** Wool of any colour: from sheep, or crafted from string. */
	WOOL("wool", "wool", s -> s.is(ItemTags.WOOL), Need.BUILD),
	// Smelted at the camp furnace
	STONE("stone", "stone", s -> s.is(Items.STONE), Need.STONE),
	SMOOTH_STONE("smooth stone", "smooth stone", s -> s.is(Items.SMOOTH_STONE), Need.STONE),
	BRICK("brick", "bricks", s -> s.is(Items.BRICK), Need.BUILD),
	SMOOTH_SANDSTONE("smooth sandstone", "smooth sandstone", s -> s.is(Items.SMOOTH_SANDSTONE), Need.STONE),
	// Crafted
	WOOD_STAIRS("wooden stairs", "wooden stairs", s -> s.is(ItemTags.WOODEN_STAIRS), Need.WOOD),
	TRAPDOOR("wooden trapdoor", "wooden trapdoors", s -> s.is(ItemTags.WOODEN_TRAPDOORS), Need.WOOD),
	STRIPPED_LOG("stripped log", "stripped logs", Stock::isStrippedLog, Need.WOOD),
	COBBLESTONE_STAIRS("cobblestone stairs", "cobblestone stairs", s -> s.is(Items.COBBLESTONE_STAIRS), Need.STONE),
	COBBLESTONE_SLAB("cobblestone slab", "cobblestone slabs", s -> s.is(Items.COBBLESTONE_SLAB), Need.STONE),
	COBBLESTONE_WALL("cobblestone wall", "cobblestone walls", s -> s.is(Items.COBBLESTONE_WALL), Need.STONE),
	STONE_STAIRS("stone stairs", "stone stairs", s -> s.is(Items.STONE_STAIRS), Need.STONE),
	STONE_SLAB("stone slab", "stone slabs", s -> s.is(Items.STONE_SLAB), Need.STONE),
	SMOOTH_STONE_SLAB("smooth stone slab", "smooth stone slabs", s -> s.is(Items.SMOOTH_STONE_SLAB), Need.STONE),
	STONE_BRICKS("stone bricks", "stone bricks", s -> s.is(Items.STONE_BRICKS), Need.STONE),
	STONE_BRICK_STAIRS("stone brick stairs", "stone brick stairs", s -> s.is(Items.STONE_BRICK_STAIRS), Need.STONE),
	STONE_BRICK_SLAB("stone brick slab", "stone brick slabs", s -> s.is(Items.STONE_BRICK_SLAB), Need.STONE),
	STONE_BRICK_WALL("stone brick wall", "stone brick walls", s -> s.is(Items.STONE_BRICK_WALL), Need.STONE),
	BRICKS("block of bricks", "blocks of bricks", s -> s.is(Items.BRICKS), Need.BUILD),
	BRICK_STAIRS("brick stairs", "brick stairs", s -> s.is(Items.BRICK_STAIRS), Need.BUILD),
	BRICK_SLAB("brick slab", "brick slabs", s -> s.is(Items.BRICK_SLAB), Need.BUILD),
	SANDSTONE("sandstone", "sandstone", s -> s.is(Items.SANDSTONE), Need.STONE),
	CUT_SANDSTONE("cut sandstone", "cut sandstone", s -> s.is(Items.CUT_SANDSTONE), Need.STONE),
	SANDSTONE_STAIRS("sandstone stairs", "sandstone stairs", s -> s.is(Items.SANDSTONE_STAIRS), Need.STONE),
	SANDSTONE_SLAB("sandstone slab", "sandstone slabs", s -> s.is(Items.SANDSTONE_SLAB), Need.STONE),
	SANDSTONE_WALL("sandstone wall", "sandstone walls", s -> s.is(Items.SANDSTONE_WALL), Need.STONE),
	CARPET("carpet", "carpets", s -> s.is(ItemTags.WOOL_CARPETS), Need.BUILD),
	/** A wool bed of any colour or a straw bed. */
	BED("bed", "beds", s -> s.is(ItemTags.BEDS) || s.is(Items.STRAW_BED), Need.BUILD),
	HAY_BALE("hay bale", "hay bales", s -> s.is(Items.HAY_BLOCK), Need.FOOD),
	BARREL("barrel", "barrels", s -> s.is(Items.BARREL), Need.WOOD),
	FLOWER_POT("flower pot", "flower pots", s -> s.is(Items.FLOWER_POT), Need.BUILD),
	IRON_BARS("iron bars", "iron bars", s -> s.is(Items.IRON_BARS), Need.ORE),
	CHAIN("iron chain", "iron chains", s -> s.is(Items.IRON_CHAIN), Need.ORE),
	SMOKER("smoker", "smokers", s -> s.is(Items.SMOKER), Need.STONE),
	BLAST_FURNACE("blast furnace", "blast furnaces", s -> s.is(Items.BLAST_FURNACE), Need.ORE),
	SMITHING_TABLE("smithing table", "smithing tables", s -> s.is(Items.SMITHING_TABLE), Need.ORE),
	FLETCHING_TABLE("fletching table", "fletching tables", s -> s.is(Items.FLETCHING_TABLE), Need.WOOD),
	CARTOGRAPHY_TABLE("cartography table", "cartography tables", s -> s.is(Items.CARTOGRAPHY_TABLE), Need.WOOD),
	LOOM("loom", "looms", s -> s.is(Items.LOOM), Need.WOOD),
	STONECUTTER("stonecutter", "stonecutters", s -> s.is(Items.STONECUTTER), Need.STONE),
	GRINDSTONE("grindstone", "grindstones", s -> s.is(Items.GRINDSTONE), Need.STONE),
	COMPOSTER("composter", "composters", s -> s.is(Items.COMPOSTER), Need.WOOD),
	LECTERN("lectern", "lecterns", s -> s.is(Items.LECTERN), Need.BUILD),
	CAULDRON("cauldron", "cauldrons", s -> s.is(Items.CAULDRON), Need.ORE);

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

	/** The vanilla recipe (the first, if there are several), or null for raw and smelted materials. */
	public @Nullable Recipe recipe() {
		List<Recipe> all = recipes();
		return all.isEmpty() ? null : all.getFirst();
	}

	/**
	 * Every way the friends make this, best first: a bed from wool and planks, else a straw bed from hay. Empty for raw
	 * materials that must be gathered and for smelted ones (see {@link #smeltedFrom()}).
	 */
	public List<Recipe> recipes() {
		return switch (this) {
			case PLANKS -> one(4, false, Map.of(LOG, 1));
			case STICK -> one(4, false, Map.of(PLANKS, 2));
			case SLAB -> one(6, true, Map.of(PLANKS, 3));
			case DOOR -> one(3, true, Map.of(PLANKS, 6));
			case FENCE -> one(3, true, Map.of(PLANKS, 4, STICK, 2));
			case FENCE_GATE -> one(1, true, Map.of(PLANKS, 2, STICK, 4));
			case PRESSURE_PLATE -> one(1, true, Map.of(PLANKS, 2));
			case TORCH -> one(4, false, Map.of(COAL, 1, STICK, 1));
			case GLASS_PANE -> one(16, true, Map.of(GLASS, 6));
			case LADDER -> one(3, true, Map.of(STICK, 7));
			case CHEST -> one(1, true, Map.of(PLANKS, 8));
			case CRAFTING_TABLE -> one(1, false, Map.of(PLANKS, 4));
			case FURNACE -> one(1, true, Map.of(STONE_MATERIAL, 8));
			case CAMPFIRE -> one(1, true, Map.of(STICK, 3, COAL, 1, LOG, 3));
			case IRON_NUGGET -> one(9, true, Map.of(IRON, 1));
			case HOPPER -> one(1, true, Map.of(IRON, 5, CHEST, 1));
			case LANTERN -> one(1, true, Map.of(IRON_NUGGET, 8, TORCH, 1));
			case REDSTONE_LAMP -> one(1, true, Map.of(REDSTONE, 4, GLOWSTONE, 1));
			case DAYLIGHT_DETECTOR -> one(1, true, Map.of(GLASS, 3, QUARTZ, 3, SLAB, 3));
			case PAPER -> one(3, true, Map.of(SUGAR_CANE, 3));
			case BOOK -> one(1, false, Map.of(PAPER, 3, LEATHER, 1));
			case BOOKSHELF -> one(1, true, Map.of(PLANKS, 6, BOOK, 3));
			case ENCHANTING_TABLE -> one(1, true, Map.of(BOOK, 1, DIAMOND, 2, OBSIDIAN, 4));
			case IRON_BLOCK -> one(1, true, Map.of(IRON, 9));
			case ANVIL -> one(1, true, Map.of(IRON_BLOCK, 3, IRON, 4));
			case BREWING_STAND -> one(1, true, Map.of(BLAZE_ROD, 1, STONE_MATERIAL, 3));
			// Better builds
			case WOOL -> one(1, false, Map.of(STRING, 4));
			case WOOD_STAIRS -> one(4, true, Map.of(PLANKS, 6));
			case TRAPDOOR -> one(2, true, Map.of(PLANKS, 6));
			// Stripping takes a log and a swing of an axe (see WoodWork.strip).
			case STRIPPED_LOG -> one(1, false, Map.of(LOG, 1));
			case COBBLESTONE_STAIRS -> one(4, true, Map.of(COBBLESTONE, 6));
			case COBBLESTONE_SLAB -> one(6, true, Map.of(COBBLESTONE, 3));
			case COBBLESTONE_WALL -> one(6, true, Map.of(COBBLESTONE, 6));
			case STONE_STAIRS -> one(4, true, Map.of(STONE, 6));
			case STONE_SLAB -> one(6, true, Map.of(STONE, 3));
			case SMOOTH_STONE_SLAB -> one(6, true, Map.of(SMOOTH_STONE, 3));
			case STONE_BRICKS -> one(4, false, Map.of(STONE, 4));
			case STONE_BRICK_STAIRS -> one(4, true, Map.of(STONE_BRICKS, 6));
			case STONE_BRICK_SLAB -> one(6, true, Map.of(STONE_BRICKS, 3));
			case STONE_BRICK_WALL -> one(6, true, Map.of(STONE_BRICKS, 6));
			case BRICKS -> one(1, false, Map.of(BRICK, 4));
			case BRICK_STAIRS -> one(4, true, Map.of(BRICKS, 6));
			case BRICK_SLAB -> one(6, true, Map.of(BRICKS, 3));
			case SANDSTONE -> one(1, false, Map.of(SAND, 4));
			case CUT_SANDSTONE -> one(4, false, Map.of(SANDSTONE, 4));
			case SANDSTONE_STAIRS -> one(4, true, Map.of(SANDSTONE, 6));
			case SANDSTONE_SLAB -> one(6, true, Map.of(SANDSTONE, 3));
			case SANDSTONE_WALL -> one(6, true, Map.of(SANDSTONE, 6));
			case CARPET -> one(3, false, Map.of(WOOL, 2));
			case BED -> List.of(new Recipe(1, true, Map.of(WOOL, 3, PLANKS, 3)), new Recipe(4, true, Map.of(HAY_BALE, 3)));
			case HAY_BALE -> one(1, true, Map.of(WHEAT, 9));
			case BARREL -> one(1, true, Map.of(PLANKS, 6, SLAB, 2));
			case FLOWER_POT -> one(1, true, Map.of(BRICK, 3));
			case IRON_BARS -> one(16, true, Map.of(IRON, 6));
			case CHAIN -> one(1, true, Map.of(IRON, 1, IRON_NUGGET, 2));
			case SMOKER -> one(1, true, Map.of(FURNACE, 1, LOG, 4));
			case BLAST_FURNACE -> one(1, true, Map.of(IRON, 5, FURNACE, 1, SMOOTH_STONE, 3));
			case SMITHING_TABLE -> one(1, true, Map.of(IRON, 2, PLANKS, 4));
			case FLETCHING_TABLE -> one(1, true, Map.of(FLINT, 2, PLANKS, 4));
			case CARTOGRAPHY_TABLE -> one(1, true, Map.of(PAPER, 2, PLANKS, 4));
			case LOOM -> one(1, false, Map.of(STRING, 2, PLANKS, 2));
			case STONECUTTER -> one(1, true, Map.of(IRON, 1, STONE, 3));
			case GRINDSTONE -> one(1, true, Map.of(STICK, 2, STONE_SLAB, 1, PLANKS, 2));
			case COMPOSTER -> one(1, true, Map.of(SLAB, 7));
			case LECTERN -> one(1, true, Map.of(SLAB, 4, BOOKSHELF, 1));
			case CAULDRON -> one(1, true, Map.of(IRON, 7));
			default -> List.of();
		};
	}

	private static List<Recipe> one(int yield, boolean needsTable, Map<Stock, Integer> inputs) {
		return List.of(new Recipe(yield, needsTable, inputs));
	}

	/**
	 * What this is smelted from at the camp furnace (glass from sand, stone from cobblestone, smooth stone from stone,
	 * bricks from clay, smooth sandstone from sandstone), or null.
	 */
	public @Nullable Stock smeltedFrom() {
		return switch (this) {
			case GLASS -> SAND;
			case STONE -> COBBLESTONE;
			case SMOOTH_STONE -> STONE;
			case BRICK -> CLAY_BALL;
			case SMOOTH_SANDSTONE -> SANDSTONE;
			default -> null;
		};
	}

	/** The item one smelt of this gives, for loading the furnace, or null if it is not smelted. */
	public @Nullable Item smeltedItem() {
		return switch (this) {
			case GLASS -> Items.GLASS;
			case STONE -> Items.STONE;
			case SMOOTH_STONE -> Items.SMOOTH_STONE;
			case BRICK -> Items.BRICK;
			case SMOOTH_SANDSTONE -> Items.SMOOTH_SANDSTONE;
			default -> null;
		};
	}

	/**
	 * Crafts from what the friend carries until at least {@code target} are carried, preferring the given wood or colour
	 * where there is a choice. Uses the core {@link Crafting} recipes or {@link WoodWork}, which consume real
	 * ingredients. Returns true when the target is reached.
	 */
	boolean craft(CompanionEntity c, int target, @Nullable String preferred) {
		var bp = c.backpack();
		int have = bp.count(item);
		if (have >= target) {
			return true;
		}
		switch (this) {
			case PLANKS -> WoodWork.planks(c, target, preferred);
			case STICK -> Crafting.ensureSticks(bp, target);
			case TORCH -> Crafting.ensureTorches(bp, target);
			case SLAB, DOOR, FENCE, FENCE_GATE, PRESSURE_PLATE, WOOD_STAIRS, TRAPDOOR -> WoodWork.craft(c, this, target - have, preferred);
			case STRIPPED_LOG -> WoodWork.strip(c, target - have, preferred);
			case CARPET -> WoodWork.carpet(c, target - have, preferred);
			case BED -> WoodWork.bed(c, target - have, preferred);
			case WOOL -> {
				Item white = Items.WOOL.pick(DyeColor.WHITE);
				Crafting.ensure(c, white, bp.count(white) + target - have);
			}
			default -> {
				Item out = vanillaItem();
				if (out != null) {
					Crafting.ensure(c, out, bp.count(out) + target - have);
				}
			}
		}
		return bp.count(item) >= target;
	}

	/** Crafts with no preference (see {@link #craft(CompanionEntity, int, String)}). */
	boolean craft(CompanionEntity c, int target) {
		return craft(c, target, null);
	}

	/** The single vanilla item a fixed-item kind crafts into, or null for kinds of many items (any wood, any colour). */
	public @Nullable Item vanillaItem() {
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
			case PAPER -> Items.PAPER;
			case BOOK -> Items.BOOK;
			case BOOKSHELF -> Items.BOOKSHELF;
			case ENCHANTING_TABLE -> Items.ENCHANTING_TABLE;
			case IRON_BLOCK -> Items.IRON_BLOCK;
			case ANVIL -> Items.ANVIL;
			case BREWING_STAND -> Items.BREWING_STAND;
			case COBBLESTONE_STAIRS -> Items.COBBLESTONE_STAIRS;
			case COBBLESTONE_SLAB -> Items.COBBLESTONE_SLAB;
			case COBBLESTONE_WALL -> Items.COBBLESTONE_WALL;
			case STONE_STAIRS -> Items.STONE_STAIRS;
			case STONE_SLAB -> Items.STONE_SLAB;
			case SMOOTH_STONE_SLAB -> Items.SMOOTH_STONE_SLAB;
			case STONE_BRICKS -> Items.STONE_BRICKS;
			case STONE_BRICK_STAIRS -> Items.STONE_BRICK_STAIRS;
			case STONE_BRICK_SLAB -> Items.STONE_BRICK_SLAB;
			case STONE_BRICK_WALL -> Items.STONE_BRICK_WALL;
			case BRICKS -> Items.BRICKS;
			case BRICK_STAIRS -> Items.BRICK_STAIRS;
			case BRICK_SLAB -> Items.BRICK_SLAB;
			case SANDSTONE -> Items.SANDSTONE;
			case CUT_SANDSTONE -> Items.CUT_SANDSTONE;
			case SANDSTONE_STAIRS -> Items.SANDSTONE_STAIRS;
			case SANDSTONE_SLAB -> Items.SANDSTONE_SLAB;
			case SANDSTONE_WALL -> Items.SANDSTONE_WALL;
			case HAY_BALE -> Items.HAY_BLOCK;
			case BARREL -> Items.BARREL;
			case FLOWER_POT -> Items.FLOWER_POT;
			case IRON_BARS -> Items.IRON_BARS;
			case CHAIN -> Items.IRON_CHAIN;
			case SMOKER -> Items.SMOKER;
			case BLAST_FURNACE -> Items.BLAST_FURNACE;
			case SMITHING_TABLE -> Items.SMITHING_TABLE;
			case FLETCHING_TABLE -> Items.FLETCHING_TABLE;
			case CARTOGRAPHY_TABLE -> Items.CARTOGRAPHY_TABLE;
			case LOOM -> Items.LOOM;
			case STONECUTTER -> Items.STONECUTTER;
			case GRINDSTONE -> Items.GRINDSTONE;
			case COMPOSTER -> Items.COMPOSTER;
			case LECTERN -> Items.LECTERN;
			case CAULDRON -> Items.CAULDRON;
			default -> null;
		};
	}

	private static boolean isSmallFlower(ItemStack s) {
		return Block.byItem(s.getItem()).defaultBlockState().is(BlockTags.SMALL_FLOWERS);
	}

	private static boolean isTallFlower(ItemStack s) {
		return Block.byItem(s.getItem()) instanceof TallFlowerBlock;
	}

	private static boolean isStrippedLog(ItemStack s) {
		if (!s.is(ItemTags.LOGS)) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		return path.startsWith("stripped_") && !path.endsWith("_wood") && !path.endsWith("_hyphae");
	}
}

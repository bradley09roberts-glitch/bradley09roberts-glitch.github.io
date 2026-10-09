package io.github.bradley09roberts.hardcorefriends.camp.build;

import java.util.Locale;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * What one blueprint entry is made of: which carried items may be used (any planks, any log, any wooden door...),
 * how the chosen item turns into a block, and which blocks already in the world count as "this part is built".
 * Using item tags means whatever wood the team gathered is the wood the camp is built from; a plan may still say
 * which wood or colour it would like best ({@link #variant()}), and the builder uses that when it is to hand.
 *
 * <p>A few entries are placed for free, as the second half of something placed just before: the top of a door, the
 * head of a bed, the top of a tall flower. A few take two items: a potted flower takes a flower pot and a flower.
 */
public enum MaterialSpec {
	PLANKS(Stock.PLANKS, s -> s.is(BlockTags.PLANKS), Variant.WOOD, Blocks.OAK_PLANKS),
	LOG(Stock.LOG, s -> s.is(BlockTags.LOGS), Variant.WOOD, Blocks.OAK_LOG),
	SLAB(Stock.SLAB, s -> s.is(BlockTags.WOODEN_SLABS), Variant.WOOD, Blocks.OAK_SLAB),
	DOOR(Stock.DOOR, s -> s.is(BlockTags.WOODEN_DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, Variant.WOOD, Blocks.OAK_DOOR),
	/** The top half of the door below: placed with the same door item, so it needs no second item. */
	DOOR_TOP(null, s -> s.is(BlockTags.WOODEN_DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, Variant.WOOD, Blocks.OAK_DOOR),
	FENCE(Stock.FENCE, s -> s.is(BlockTags.WOODEN_FENCES), Variant.WOOD, Blocks.OAK_FENCE),
	/** Any wooden fence gate, open or shut: the animal pen's way in. */
	FENCE_GATE(Stock.FENCE_GATE, s -> s.is(BlockTags.FENCE_GATES), Variant.WOOD, Blocks.OAK_FENCE_GATE),
	PRESSURE_PLATE(Stock.PRESSURE_PLATE, s -> s.is(BlockTags.WOODEN_PRESSURE_PLATES), Variant.WOOD, Blocks.OAK_PRESSURE_PLATE),
	TORCH(Stock.TORCH, s -> s.is(Blocks.TORCH), Variant.NONE, Blocks.TORCH),
	WALL_TORCH(Stock.TORCH, s -> s.is(Blocks.WALL_TORCH), Variant.NONE, Blocks.WALL_TORCH),
	GLASS_PANE(Stock.GLASS_PANE, s -> s.is(Blocks.GLASS_PANE), Variant.NONE, Blocks.GLASS_PANE),
	LADDER(Stock.LADDER, s -> s.is(Blocks.LADDER), Variant.NONE, Blocks.LADDER),
	CHEST(Stock.CHEST, s -> s.is(Blocks.CHEST), Variant.NONE, Blocks.CHEST),
	CRAFTING_TABLE(Stock.CRAFTING_TABLE, s -> s.is(Blocks.CRAFTING_TABLE), Variant.NONE, Blocks.CRAFTING_TABLE),
	FURNACE(Stock.FURNACE, s -> s.is(Blocks.FURNACE), Variant.NONE, Blocks.FURNACE),
	CAMPFIRE(Stock.CAMPFIRE, s -> s.is(Blocks.CAMPFIRE), Variant.NONE, Blocks.CAMPFIRE),
	HOPPER(Stock.HOPPER, s -> s.is(Blocks.HOPPER), Variant.NONE, Blocks.HOPPER),
	COBBLESTONE(Stock.COBBLESTONE, s -> s.is(Blocks.COBBLESTONE), Variant.NONE, Blocks.COBBLESTONE),
	/** Fills a one-block dip under a footprint: dirt first, cobblestone otherwise. Solid ground counts as done. */
	FOUNDATION(Stock.FILL, s -> !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced(), Variant.NONE, Blocks.DIRT),
	LANTERN(Stock.LANTERN, s -> s.is(Blocks.LANTERN), Variant.NONE, Blocks.LANTERN),
	REDSTONE_LAMP(Stock.REDSTONE_LAMP, s -> s.is(Blocks.REDSTONE_LAMP), Variant.NONE, Blocks.REDSTONE_LAMP),
	DAYLIGHT_DETECTOR(Stock.DAYLIGHT_DETECTOR, s -> s.is(Blocks.DAYLIGHT_DETECTOR), Variant.NONE, Blocks.DAYLIGHT_DETECTOR),
	// Sage's plan (package progress): the library, the anvil and the brewing stand.
	BOOKSHELF(Stock.BOOKSHELF, s -> s.is(Blocks.BOOKSHELF), Variant.NONE, Blocks.BOOKSHELF),
	ENCHANTING_TABLE(Stock.ENCHANTING_TABLE, s -> s.is(Blocks.ENCHANTING_TABLE), Variant.NONE, Blocks.ENCHANTING_TABLE),
	/** Any anvil, chipped or damaged too: a worn anvil still stands. */
	ANVIL(Stock.ANVIL, s -> s.is(BlockTags.ANVIL), Variant.NONE, Blocks.ANVIL),
	BREWING_STAND(Stock.BREWING_STAND, s -> s.is(Blocks.BREWING_STAND), Variant.NONE, Blocks.BREWING_STAND),
	// Expeditions (package expedition): the Nether portal's frame.
	OBSIDIAN(Stock.OBSIDIAN, s -> s.is(Blocks.OBSIDIAN), Variant.NONE, Blocks.OBSIDIAN),

	// ---- Better builds (package architecture): what good-looking plans are made of ----
	/** Wooden stairs of any wood (roofs, steps, benches). */
	STAIRS(Stock.WOOD_STAIRS, s -> s.is(BlockTags.WOODEN_STAIRS), Variant.WOOD, Blocks.OAK_STAIRS),
	TRAPDOOR(Stock.TRAPDOOR, s -> s.is(BlockTags.WOODEN_TRAPDOORS), Variant.WOOD, Blocks.OAK_TRAPDOOR),
	/** A log with its bark taken off with an axe. */
	STRIPPED_LOG(Stock.STRIPPED_LOG, MaterialSpec::isStrippedLog, Variant.WOOD, Blocks.STRIPPED_OAK_LOG),
	COBBLESTONE_STAIRS(Stock.COBBLESTONE_STAIRS, s -> s.is(Blocks.COBBLESTONE_STAIRS), Variant.NONE, Blocks.COBBLESTONE_STAIRS),
	COBBLESTONE_SLAB(Stock.COBBLESTONE_SLAB, s -> s.is(Blocks.COBBLESTONE_SLAB), Variant.NONE, Blocks.COBBLESTONE_SLAB),
	COBBLESTONE_WALL(Stock.COBBLESTONE_WALL, s -> s.is(Blocks.COBBLESTONE_WALL), Variant.NONE, Blocks.COBBLESTONE_WALL),
	STONE(Stock.STONE, s -> s.is(Blocks.STONE), Variant.NONE, Blocks.STONE),
	STONE_STAIRS(Stock.STONE_STAIRS, s -> s.is(Blocks.STONE_STAIRS), Variant.NONE, Blocks.STONE_STAIRS),
	STONE_SLAB(Stock.STONE_SLAB, s -> s.is(Blocks.STONE_SLAB), Variant.NONE, Blocks.STONE_SLAB),
	SMOOTH_STONE(Stock.SMOOTH_STONE, s -> s.is(Blocks.SMOOTH_STONE), Variant.NONE, Blocks.SMOOTH_STONE),
	SMOOTH_STONE_SLAB(Stock.SMOOTH_STONE_SLAB, s -> s.is(Blocks.SMOOTH_STONE_SLAB), Variant.NONE, Blocks.SMOOTH_STONE_SLAB),
	STONE_BRICKS(Stock.STONE_BRICKS, s -> s.is(Blocks.STONE_BRICKS), Variant.NONE, Blocks.STONE_BRICKS),
	STONE_BRICK_STAIRS(Stock.STONE_BRICK_STAIRS, s -> s.is(Blocks.STONE_BRICK_STAIRS), Variant.NONE, Blocks.STONE_BRICK_STAIRS),
	STONE_BRICK_SLAB(Stock.STONE_BRICK_SLAB, s -> s.is(Blocks.STONE_BRICK_SLAB), Variant.NONE, Blocks.STONE_BRICK_SLAB),
	STONE_BRICK_WALL(Stock.STONE_BRICK_WALL, s -> s.is(Blocks.STONE_BRICK_WALL), Variant.NONE, Blocks.STONE_BRICK_WALL),
	BRICKS(Stock.BRICKS, s -> s.is(Blocks.BRICKS), Variant.NONE, Blocks.BRICKS),
	BRICK_STAIRS(Stock.BRICK_STAIRS, s -> s.is(Blocks.BRICK_STAIRS), Variant.NONE, Blocks.BRICK_STAIRS),
	BRICK_SLAB(Stock.BRICK_SLAB, s -> s.is(Blocks.BRICK_SLAB), Variant.NONE, Blocks.BRICK_SLAB),
	SANDSTONE(Stock.SANDSTONE, s -> s.is(Blocks.SANDSTONE), Variant.NONE, Blocks.SANDSTONE),
	CUT_SANDSTONE(Stock.CUT_SANDSTONE, s -> s.is(Blocks.CUT_SANDSTONE), Variant.NONE, Blocks.CUT_SANDSTONE),
	SMOOTH_SANDSTONE(Stock.SMOOTH_SANDSTONE, s -> s.is(Blocks.SMOOTH_SANDSTONE), Variant.NONE, Blocks.SMOOTH_SANDSTONE),
	SANDSTONE_STAIRS(Stock.SANDSTONE_STAIRS, s -> s.is(Blocks.SANDSTONE_STAIRS), Variant.NONE, Blocks.SANDSTONE_STAIRS),
	SANDSTONE_SLAB(Stock.SANDSTONE_SLAB, s -> s.is(Blocks.SANDSTONE_SLAB), Variant.NONE, Blocks.SANDSTONE_SLAB),
	SANDSTONE_WALL(Stock.SANDSTONE_WALL, s -> s.is(Blocks.SANDSTONE_WALL), Variant.NONE, Blocks.SANDSTONE_WALL),
	GLASS(Stock.GLASS, s -> s.is(Blocks.GLASS), Variant.NONE, Blocks.GLASS),
	/** Wool of any colour (a plan may prefer one). */
	WOOL(Stock.WOOL, s -> s.is(BlockTags.WOOL), Variant.COLOUR, Blocks.WOOL.pick(DyeColor.WHITE)),
	CARPET(Stock.CARPET, s -> s.is(BlockTags.WOOL_CARPETS), Variant.COLOUR, Blocks.CARPET.pick(DyeColor.WHITE)),
	/** The foot of a bed: a wool bed of any colour, or a straw bed when there is no wool. */
	BED(Stock.BED, s -> s.getBlock() instanceof AbstractBedBlock && s.getValue(AbstractBedBlock.PART) == BedPart.FOOT,
		Variant.COLOUR, Blocks.BED.pick(DyeColor.RED)),
	/** The head of the bed beside it: placed with the same bed, so it needs no second item. */
	BED_HEAD(null, s -> s.getBlock() instanceof AbstractBedBlock && s.getValue(AbstractBedBlock.PART) == BedPart.HEAD,
		Variant.COLOUR, Blocks.BED.pick(DyeColor.RED)),
	/** A small flower of any kind (a garden bed, a window box). */
	FLOWER(Stock.FLOWER, s -> s.is(BlockTags.SMALL_FLOWERS), Variant.NONE, Blocks.POPPY),
	/** The lower half of a tall flower (sunflower, lilac, rose bush, peony). */
	TALL_FLOWER(Stock.TALL_FLOWER, s -> s.getBlock() instanceof TallFlowerBlock && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER,
		Variant.NONE, Blocks.ROSE_BUSH),
	/** The top half of the tall flower below, which comes with it. */
	TALL_FLOWER_TOP(null, s -> s.getBlock() instanceof TallFlowerBlock && s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER,
		Variant.NONE, Blocks.ROSE_BUSH),
	FLOWER_POT(Stock.FLOWER_POT, s -> s.is(Blocks.FLOWER_POT), Variant.NONE, Blocks.FLOWER_POT),
	/** A flower pot with a small flower in it: takes a pot and a flower. */
	POTTED_FLOWER(Stock.FLOWER_POT, s -> s.getBlock() instanceof FlowerPotBlock && !s.is(Blocks.FLOWER_POT), Variant.NONE, Blocks.POTTED_POPPY),
	BARREL(Stock.BARREL, s -> s.is(Blocks.BARREL), Variant.NONE, Blocks.BARREL),
	IRON_BARS(Stock.IRON_BARS, s -> s.is(Blocks.IRON_BARS), Variant.NONE, Blocks.IRON_BARS),
	CHAIN(Stock.CHAIN, s -> s.is(Blocks.IRON_CHAIN), Variant.NONE, Blocks.IRON_CHAIN),
	HAY_BALE(Stock.HAY_BALE, s -> s.is(Blocks.HAY_BLOCK), Variant.NONE, Blocks.HAY_BLOCK),
	DIRT(Stock.DIRT, s -> s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT), Variant.NONE, Blocks.DIRT),
	SMOKER(Stock.SMOKER, s -> s.is(Blocks.SMOKER), Variant.NONE, Blocks.SMOKER),
	BLAST_FURNACE(Stock.BLAST_FURNACE, s -> s.is(Blocks.BLAST_FURNACE), Variant.NONE, Blocks.BLAST_FURNACE),
	SMITHING_TABLE(Stock.SMITHING_TABLE, s -> s.is(Blocks.SMITHING_TABLE), Variant.NONE, Blocks.SMITHING_TABLE),
	FLETCHING_TABLE(Stock.FLETCHING_TABLE, s -> s.is(Blocks.FLETCHING_TABLE), Variant.NONE, Blocks.FLETCHING_TABLE),
	CARTOGRAPHY_TABLE(Stock.CARTOGRAPHY_TABLE, s -> s.is(Blocks.CARTOGRAPHY_TABLE), Variant.NONE, Blocks.CARTOGRAPHY_TABLE),
	LOOM(Stock.LOOM, s -> s.is(Blocks.LOOM), Variant.NONE, Blocks.LOOM),
	STONECUTTER(Stock.STONECUTTER, s -> s.is(Blocks.STONECUTTER), Variant.NONE, Blocks.STONECUTTER),
	GRINDSTONE(Stock.GRINDSTONE, s -> s.is(Blocks.GRINDSTONE), Variant.NONE, Blocks.GRINDSTONE),
	COMPOSTER(Stock.COMPOSTER, s -> s.is(Blocks.COMPOSTER), Variant.NONE, Blocks.COMPOSTER),
	LECTERN(Stock.LECTERN, s -> s.is(Blocks.LECTERN), Variant.NONE, Blocks.LECTERN),
	/** An empty cauldron; one that has filled with rain counts too. */
	CAULDRON(Stock.CAULDRON, s -> s.is(BlockTags.CAULDRONS), Variant.NONE, Blocks.CAULDRON),
	/** A bell: friends cannot make one, so only a bell already in the supply chest is used. */
	BELL(Stock.BELL, s -> s.is(Blocks.BELL), Variant.NONE, Blocks.BELL),
	/** "Must be air": plants or snow there are cleared; nothing is placed. */
	AIR(null, BlockState::isAir, Variant.NONE, Blocks.AIR);

	/** What kind of preference a plan may give for this material. */
	public enum Variant {
		/** One kind only. */
		NONE,
		/** Any wood, preferring one ("spruce"). */
		WOOD,
		/** Any colour, preferring one ("red"). */
		COLOUR
	}

	private final @Nullable Stock stock;
	private final Predicate<BlockState> built;
	private final Variant variant;
	private final Block sample;

	MaterialSpec(@Nullable Stock stock, Predicate<BlockState> built, Variant variant, Block sample) {
		this.stock = stock;
		this.built = built;
		this.variant = variant;
		this.sample = sample;
	}

	/** The item kind consumed when placing, or null when the entry is placed for free (the top half of a door). */
	public @Nullable Stock stock() {
		return stock;
	}

	/** A second item used up with the first (the flower in a flower pot), or null. */
	public @Nullable Stock extraStock() {
		return this == POTTED_FLOWER ? Stock.FLOWER : null;
	}

	/** True if this carried item may be used for the entry. */
	public boolean accepts(ItemStack stack) {
		return stock != null && stock.matches(stack);
	}

	/** True if the block in the world already fulfils this entry. */
	public boolean isBuilt(BlockState state) {
		return built.test(state);
	}

	/** Which kind of preference ({@code wood} or {@code colour}) a plan may give for this material. */
	public Variant variant() {
		return variant;
	}

	/** A typical block of this material, used to check a plan's block properties ("facing", "half"...). */
	public Block sample() {
		return sample;
	}

	/** True for the second half of something, placed for free after its first half. */
	public boolean isSecondHalf() {
		return this == DOOR_TOP || this == BED_HEAD || this == TALL_FLOWER_TOP;
	}

	/**
	 * True if this item is of the preferred wood or colour (always true without a preference, or for a material with
	 * no variants). Wood and colour are read from the item's name: {@code spruce_stairs}, {@code stripped_spruce_log},
	 * {@code red_bed}.
	 */
	public boolean prefers(ItemStack stack, @Nullable String preferred) {
		if (preferred == null || variant == Variant.NONE) {
			return true;
		}
		return matchesVariant(stack, preferred);
	}

	/** True if the item's name starts with the wood or colour ("spruce_", "stripped_spruce_", "red_"). */
	public static boolean matchesVariant(ItemStack stack, String preferred) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.startsWith(preferred + "_") || path.startsWith("stripped_" + preferred + "_");
	}

	/** The plain block the chosen item places, before the blueprint's facing and shape tweaks. */
	public BlockState baseState(ItemStack chosen) {
		return switch (this) {
			case WALL_TORCH -> Blocks.WALL_TORCH.defaultBlockState();
			case TORCH -> Blocks.TORCH.defaultBlockState();
			case AIR -> Blocks.AIR.defaultBlockState();
			default -> {
				Block block = Block.byItem(chosen.getItem());
				yield block == Blocks.AIR ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
			}
		};
	}

	/** The lower-case name used in plan files ("stone_brick_stairs"). */
	public String fileName() {
		return name().toLowerCase(Locale.ROOT);
	}

	private static boolean isStrippedLog(BlockState s) {
		return s.is(BlockTags.LOGS) && BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("stripped_");
	}
}

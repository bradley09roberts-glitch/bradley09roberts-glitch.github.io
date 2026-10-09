package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.WoodWork;

/**
 * Turns the material names in plan files into {@link MaterialSpec}s. A name is either one of the mod's own material
 * names ({@code planks}, {@code stairs}, {@code stone_brick_wall}, {@code bed}...) or a vanilla block id
 * ({@code minecraft:spruce_stairs}, {@code red_bed}), which is matched to the material that block counts as, with its
 * wood or colour kept as the entry's preference. Also knows which materials are attachments and which are decoration
 * by default.
 */
public final class PlanMaterials {
	/** A resolved material name: the material and the wood or colour it named, if any. */
	public record Resolved(MaterialSpec spec, @Nullable String variant) {
	}

	/** Materials placed after the structure unless a plan says otherwise: things that hang on, stand in or sit on it. */
	private static final Set<MaterialSpec> ATTACHMENTS = EnumSet.of(MaterialSpec.DOOR, MaterialSpec.DOOR_TOP, MaterialSpec.BED,
		MaterialSpec.BED_HEAD, MaterialSpec.TORCH, MaterialSpec.WALL_TORCH, MaterialSpec.LANTERN, MaterialSpec.LADDER,
		MaterialSpec.GLASS_PANE, MaterialSpec.CARPET, MaterialSpec.FLOWER_POT, MaterialSpec.POTTED_FLOWER, MaterialSpec.FLOWER,
		MaterialSpec.TALL_FLOWER, MaterialSpec.TALL_FLOWER_TOP, MaterialSpec.PRESSURE_PLATE, MaterialSpec.TRAPDOOR,
		MaterialSpec.CHEST, MaterialSpec.BARREL, MaterialSpec.CRAFTING_TABLE, MaterialSpec.FURNACE, MaterialSpec.SMOKER,
		MaterialSpec.BLAST_FURNACE, MaterialSpec.SMITHING_TABLE, MaterialSpec.FLETCHING_TABLE, MaterialSpec.CARTOGRAPHY_TABLE,
		MaterialSpec.LOOM, MaterialSpec.STONECUTTER, MaterialSpec.GRINDSTONE, MaterialSpec.COMPOSTER, MaterialSpec.LECTERN,
		MaterialSpec.CAULDRON, MaterialSpec.BELL, MaterialSpec.CHAIN, MaterialSpec.IRON_BARS, MaterialSpec.CAMPFIRE,
		MaterialSpec.HOPPER, MaterialSpec.ENCHANTING_TABLE, MaterialSpec.ANVIL, MaterialSpec.BREWING_STAND);

	/** Decoration that never holds a building up unless a plan says otherwise. */
	private static final Set<MaterialSpec> DECORATION = EnumSet.of(MaterialSpec.FLOWER, MaterialSpec.TALL_FLOWER,
		MaterialSpec.TALL_FLOWER_TOP, MaterialSpec.FLOWER_POT, MaterialSpec.POTTED_FLOWER, MaterialSpec.CARPET, MaterialSpec.BELL);

	/** Friendly names besides each material's own lower-case name. */
	private static final Map<String, MaterialSpec> ALIASES = new LinkedHashMap<>();

	static {
		ALIASES.put("wooden_stairs", MaterialSpec.STAIRS);
		ALIASES.put("wood_stairs", MaterialSpec.STAIRS);
		ALIASES.put("wooden_slab", MaterialSpec.SLAB);
		ALIASES.put("wooden_door", MaterialSpec.DOOR);
		ALIASES.put("wooden_trapdoor", MaterialSpec.TRAPDOOR);
		ALIASES.put("wooden_fence", MaterialSpec.FENCE);
		ALIASES.put("wooden_pressure_plate", MaterialSpec.PRESSURE_PLATE);
		ALIASES.put("hay_block", MaterialSpec.HAY_BALE);
		ALIASES.put("iron_chain", MaterialSpec.CHAIN);
		ALIASES.put("flower_in_pot", MaterialSpec.POTTED_FLOWER);
		ALIASES.put("small_flower", MaterialSpec.FLOWER);
	}

	private PlanMaterials() {
	}

	/** The material a name stands for, or empty if it names nothing the friends can build with. */
	public static Optional<Resolved> resolve(String raw) {
		String name = raw.trim().toLowerCase(Locale.ROOT);
		if (name.startsWith("minecraft:")) {
			name = name.substring("minecraft:".length());
		} else if (name.contains(":")) {
			return Optional.empty(); // other mods' blocks are not something the friends know how to make
		}
		MaterialSpec alias = ALIASES.get(name);
		if (alias != null) {
			return Optional.of(new Resolved(alias, null));
		}
		for (MaterialSpec spec : MaterialSpec.values()) {
			if (spec != MaterialSpec.FOUNDATION && spec.fileName().equals(name)) {
				return Optional.of(new Resolved(spec, null));
			}
		}
		return fromBlockId(name);
	}

	/** A vanilla block id matched to the material its default state counts as, keeping its wood or colour. */
	private static Optional<Resolved> fromBlockId(String path) {
		Identifier id = Identifier.tryParse("minecraft:" + path);
		if (id == null) {
			return Optional.empty();
		}
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isEmpty() || block.get() == Blocks.AIR && !path.equals("air")) {
			return Optional.empty();
		}
		BlockState state = block.get().defaultBlockState();
		for (MaterialSpec spec : matchOrder()) {
			if (spec.isBuilt(state)) {
				String variant = switch (spec.variant()) {
					case WOOD -> WoodWork.woodOf(new ItemStack(block.get().asItem()));
					case COLOUR -> WoodWork.colourOf(new ItemStack(block.get().asItem()));
					case NONE -> null;
				};
				return Optional.of(new Resolved(spec, variant));
			}
		}
		return Optional.empty();
	}

	/** Materials in the order a block is matched against them: the narrower ones before the wider tags. */
	private static List<MaterialSpec> matchOrder() {
		List<MaterialSpec> order = new ArrayList<>();
		order.add(MaterialSpec.STRIPPED_LOG); // the logs tag holds stripped logs too
		for (MaterialSpec spec : MaterialSpec.values()) {
			if (spec != MaterialSpec.STRIPPED_LOG && spec != MaterialSpec.FOUNDATION && spec != MaterialSpec.AIR) {
				order.add(spec);
			}
		}
		order.add(MaterialSpec.AIR);
		return order;
	}

	/** True if this material is placed after the structure unless a plan says otherwise. */
	public static boolean isAttachmentByDefault(MaterialSpec spec) {
		return ATTACHMENTS.contains(spec);
	}

	/** True if this material is decoration that never holds a building up, unless a plan says otherwise. */
	public static boolean isDecorationByDefault(MaterialSpec spec) {
		return DECORATION.contains(spec);
	}

	/** Every name a plan file may use (the mod's own names and aliases), for the format documentation and errors. */
	public static List<String> knownNames() {
		List<String> names = new ArrayList<>();
		for (MaterialSpec spec : MaterialSpec.values()) {
			if (spec != MaterialSpec.FOUNDATION) {
				names.add(spec.fileName());
			}
		}
		names.addAll(ALIASES.keySet());
		return names;
	}
}

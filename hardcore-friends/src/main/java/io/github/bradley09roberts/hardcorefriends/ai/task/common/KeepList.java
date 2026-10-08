package io.github.bradley09roberts.hardcorefriends.ai.task.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;

/**
 * What each role keeps in their backpack when tidying up at the supply chest. Anything beyond these amounts is
 * surplus: it is deposited in the chest or handed to a friend who needs it. Everyone also keeps their role tool and
 * up to {@value #FOOD_KEPT} pieces of food; whatever is held in the hand is never part of the backpack and so is
 * always kept.
 *
 * <p>All helpers are static and side-effect free so other routines can reuse them.
 */
public final class KeepList {
	/** Food every friend keeps for healing. */
	public static final int FOOD_KEPT = 4;
	/** Copies of a tool kept in the backpack (besides whatever is held), so a spare is at hand. */
	public static final int TOOLS_KEPT = 2;
	/** Miners wear through pickaxes fastest, so they carry an extra one. */
	public static final int MINER_PICKAXES_KEPT = 3;

	/** Items matching {@code filter} are kept up to {@code amount} in total. */
	public record Rule(String label, Predicate<ItemStack> filter, int amount) {
		public boolean matches(ItemStack stack) {
			return filter.test(stack);
		}
	}

	private static final Map<Role, List<Rule>> ROLE_RULES = new EnumMap<>(Role.class);
	private static final Map<Role, List<Rule>> ALL_RULES = new EnumMap<>(Role.class);

	private KeepList() {
	}

	// ------------------------------------------------------------- role tools

	/** The tool a role needs for its main job, or null (the strategist works without one). */
	public static @Nullable TagKey<Item> roleTool(Role role) {
		return switch (role) {
			case FARMER -> ItemTags.HOES;
			case MINER, INVENTOR -> ItemTags.PICKAXES;
			case FORAGER, BUILDER -> ItemTags.AXES;
			case LANDSCAPER -> ItemTags.SHOVELS;
			case WARRIOR, EXPLORER -> ItemTags.SWORDS;
			case STRATEGIST -> null;
		};
	}

	public static boolean isRoleTool(Role role, ItemStack stack) {
		TagKey<Item> tool = roleTool(role);
		return tool != null && !stack.isEmpty() && stack.is(tool);
	}

	/** Plain English name of a tool kind, such as {@code "pickaxe"}. */
	public static String toolName(TagKey<Item> toolTag) {
		if (toolTag == ItemTags.PICKAXES) {
			return "pickaxe";
		}
		if (toolTag == ItemTags.AXES) {
			return "axe";
		}
		if (toolTag == ItemTags.SHOVELS) {
			return "shovel";
		}
		if (toolTag == ItemTags.HOES) {
			return "hoe";
		}
		if (toolTag == ItemTags.SWORDS) {
			return "sword";
		}
		return "tool";
	}

	/** True when the friend holds or carries their role tool (or needs none). */
	public static boolean hasRoleTool(CompanionEntity c) {
		TagKey<Item> tool = roleTool(c.friendId().role());
		return tool == null || c.actions().hasTool(tool);
	}

	// ------------------------------------------------------------------- food

	public static boolean isFood(ItemStack stack) {
		return !stack.isEmpty() && stack.is(ModTags.COMPANION_FOOD);
	}

	/** How filling a food is (nutrition plus saturation), so cooked food ranks above raw. 0 for non-food. */
	public static float foodValue(ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food == null ? 0.0F : food.nutrition() + food.saturation();
	}

	public static int foodCount(Backpack backpack) {
		return backpack.count(KeepList::isFood);
	}

	// ------------------------------------------------------------------ rules

	/** The role's own keep rules, without the role tool and food rules everyone shares (see {@link #rules}). */
	public static List<Rule> roleRules(Role role) {
		return ROLE_RULES.computeIfAbsent(role, KeepList::buildRoleRules);
	}

	/** Every keep rule for a role: its role tool, its own list, then food. */
	public static List<Rule> rules(Role role) {
		return ALL_RULES.computeIfAbsent(role, r -> {
			List<Rule> list = new ArrayList<>();
			TagKey<Item> tool = roleTool(r);
			if (tool != null) {
				list.add(new Rule(toolName(tool), s -> s.is(tool), r == Role.MINER ? MINER_PICKAXES_KEPT : TOOLS_KEPT));
			}
			list.addAll(roleRules(r));
			list.add(new Rule("food", KeepList::isFood, FOOD_KEPT));
			return List.copyOf(list);
		});
	}

	/** True if the role keeps this kind of item for its work (role tool or role list; food does not count). */
	public static boolean isUseful(Role role, ItemStack stack) {
		if (isRoleTool(role, stack)) {
			return true;
		}
		for (Rule rule : roleRules(role)) {
			if (rule.matches(stack)) {
				return true;
			}
		}
		return false;
	}

	/** How many of this kind of item the role keeps (summing every rule that matches), 0 if none. */
	public static int keepAmount(Role role, ItemStack stack) {
		long total = 0;
		for (Rule rule : rules(role)) {
			if (rule.matches(stack)) {
				total += rule.amount();
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	/**
	 * Surplus per backpack slot: how many items of each slot are not needed under the role's keep rules. Better
	 * tools (most durability left) and better food are kept first.
	 */
	public static int[] surplusBySlot(Backpack backpack, Role role) {
		List<Rule> rules = rules(role);
		int[] budget = new int[rules.size()];
		for (int i = 0; i < budget.length; i++) {
			budget[i] = rules.get(i).amount();
		}
		List<Integer> order = new ArrayList<>();
		for (int slot = 0; slot < Backpack.MAX_SLOTS; slot++) {
			if (!backpack.get(slot).isEmpty()) {
				order.add(slot);
			}
		}
		order.sort(Comparator.comparingDouble((Integer slot) -> quality(backpack.get(slot))).reversed());
		int[] surplus = new int[Backpack.MAX_SLOTS];
		for (int slot : order) {
			ItemStack stack = backpack.get(slot);
			int left = stack.getCount();
			for (int r = 0; r < budget.length && left > 0; r++) {
				if (budget[r] > 0 && rules.get(r).matches(stack)) {
					int keep = Math.min(budget[r], left);
					budget[r] -= keep;
					left -= keep;
				}
			}
			surplus[slot] = left;
		}
		return surplus;
	}

	/** Total number of surplus items in a friend's backpack. */
	public static int surplusTotal(CompanionEntity c) {
		int total = 0;
		for (int n : surplusBySlot(c.backpack(), c.friendId().role())) {
			total += n;
		}
		return total;
	}

	/** Surplus items matching a filter in a friend's backpack. */
	public static int surplusOf(CompanionEntity c, Predicate<ItemStack> filter) {
		int[] surplus = surplusBySlot(c.backpack(), c.friendId().role());
		int total = 0;
		for (int slot = 0; slot < surplus.length; slot++) {
			ItemStack stack = c.backpack().get(slot);
			if (surplus[slot] > 0 && filter.test(stack)) {
				total += surplus[slot];
			}
		}
		return total;
	}

	private static double quality(ItemStack stack) {
		if (stack.isDamageableItem()) {
			return stack.getMaxDamage() - stack.getDamageValue();
		}
		return foodValue(stack);
	}

	// ------------------------------------------------------------- role lists

	private static List<Rule> buildRoleRules(Role role) {
		List<Rule> list = new ArrayList<>();
		switch (role) {
			case FARMER -> {
				list.add(item("wheat seeds", Items.WHEAT_SEEDS, 64));
				list.add(item("beetroot seeds", Items.BEETROOT_SEEDS, 64));
				list.add(item("melon seeds", Items.MELON_SEEDS, 64));
				list.add(item("pumpkin seeds", Items.PUMPKIN_SEEDS, 64));
				list.add(item("carrots", Items.CARROT, 64));
				list.add(item("potatoes", Items.POTATO, 64));
				list.add(item("bone meal", Items.BONE_MEAL, 64));
				list.add(item("water bucket", Items.WATER_BUCKET, 1));
			}
			case BUILDER -> {
				list.add(tag("planks", ItemTags.PLANKS, 64));
				list.add(tag("logs", ItemTags.LOGS, 32));
				list.add(item("cobblestone", Items.COBBLESTONE, 64));
				list.add(item("glass panes", Items.GLASS_PANE, 16));
				list.add(tag("doors", BlockItemTags.DOORS.item(), 2));
				list.add(tag("slabs", BlockItemTags.SLABS.item(), 32));
				list.add(item("torches", Items.TORCH, 16));
				list.add(item("sticks", Items.STICK, 16));
				list.add(tag("fences", BlockItemTags.FENCES.item(), 16));
				list.add(item("ladders", Items.LADDER, 8));
			}
			case MINER -> {
				// Pickaxes are the role tool.
				list.add(item("torches", Items.TORCH, 32));
				list.add(tag("coal", ItemTags.COALS, 8));
				list.add(item("sticks", Items.STICK, 8));
			}
			case EXPLORER -> {
				// The sword is the role tool; nothing else.
			}
			case INVENTOR -> {
				list.add(item("redstone", Items.REDSTONE, 16));
				list.add(item("iron ingots", Items.IRON_INGOT, 16));
				list.add(item("torches", Items.TORCH, 16));
				list.add(item("hoppers", Items.HOPPER, 3));
				list.add(new Rule("pressure plates", KeepList::isPressurePlate, 4));
				list.add(item("glass", Items.GLASS, 6));
				list.add(item("quartz", Items.QUARTZ, 6));
				list.add(item("glowstone", Items.GLOWSTONE, 2));
				list.add(item("chests", Items.CHEST, 3));
				list.add(item("furnace", Items.FURNACE, 1));
			}
			case WARRIOR -> {
				// Swords are the role tool.
				list.add(tag("axes", ItemTags.AXES, 1));
				list.add(tag("spears", ItemTags.SPEARS, 1));
				list.add(tag("helmets", ItemTags.HEAD_ARMOR, TOOLS_KEPT));
				list.add(tag("chestplates", ItemTags.CHEST_ARMOR, TOOLS_KEPT));
				list.add(tag("leggings", ItemTags.LEG_ARMOR, TOOLS_KEPT));
				list.add(tag("boots", ItemTags.FOOT_ARMOR, TOOLS_KEPT));
				list.add(item("shield", Items.SHIELD, 1));
			}
			case STRATEGIST -> {
				// Sage carries nothing special.
			}
			case LANDSCAPER -> {
				list.add(tag("saplings", ItemTags.SAPLINGS, 16));
				list.add(tag("flowers", BlockItemTags.SMALL_FLOWERS.item(), 16));
				list.add(item("torches", Items.TORCH, 32));
				list.add(item("dirt", Items.DIRT, 32));
				list.add(tag("fences", BlockItemTags.FENCES.item(), 32));
				list.add(tag("fence gates", ItemTags.FENCE_GATES, 2));
			}
			case FORAGER -> {
				list.add(tag("shovels", ItemTags.SHOVELS, 1));
				list.add(tag("pickaxes", ItemTags.PICKAXES, 1));
				list.add(tag("saplings", ItemTags.SAPLINGS, 8));
			}
		}
		return List.copyOf(list);
	}

	private static boolean isPressurePlate(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_pressure_plate");
	}

	private static Rule item(String label, Item item, int amount) {
		return new Rule(label, s -> s.is(item), amount);
	}

	private static Rule tag(String label, TagKey<Item> tag, int amount) {
		return new Rule(label, s -> s.is(tag), amount);
	}
}

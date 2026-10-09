package io.github.bradley09roberts.hardcorefriends.camp.build;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Crafting where the kind of wood or the colour matters, with real ingredients from the backpack: wooden shapes
 * (stairs, slabs, doors, trapdoors, fences...) all of one wood, preferring the wood a plan asks for; stripping logs
 * with an axe; carpets and beds of one colour of wool, preferring the colour a plan asks for, and straw beds from hay
 * when there is no wool. Vanilla quantities throughout; anything bigger than a 2×2 grid needs a crafting table within
 * six blocks, as for a player.
 */
public final class WoodWork {
	private static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
	private static final Predicate<ItemStack> STICK = s -> s.is(Items.STICK);

	/** A wooden shape: item name suffix, planks and sticks per craft, items made per craft. */
	private record Shape(String suffix, int planks, int sticks, int yield) {
	}

	private WoodWork() {
	}

	private static @Nullable Shape shape(Stock stock) {
		return switch (stock) {
			case SLAB -> new Shape("slab", 3, 0, 6);
			case DOOR -> new Shape("door", 6, 0, 3);
			case FENCE -> new Shape("fence", 4, 2, 3);
			case FENCE_GATE -> new Shape("fence_gate", 2, 4, 1);
			case PRESSURE_PLATE -> new Shape("pressure_plate", 2, 0, 1);
			case WOOD_STAIRS -> new Shape("stairs", 6, 0, 4);
			case TRAPDOOR -> new Shape("trapdoor", 6, 0, 2);
			default -> null;
		};
	}

	// ------------------------------------------------------------------ names

	/** The wood of a wooden item ("spruce" for spruce stairs or a stripped spruce log), or null. */
	public static @Nullable String woodOf(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.startsWith("stripped_")) {
			path = path.substring("stripped_".length());
		}
		for (String suffix : new String[] {"_planks", "_log", "_wood", "_stem", "_hyphae", "_stairs", "_slab", "_trapdoor",
			"_door", "_fence_gate", "_fence", "_pressure_plate", "_block"}) {
			if (path.endsWith(suffix)) {
				return path.substring(0, path.length() - suffix.length());
			}
		}
		return null;
	}

	/** The colour of a wool, carpet or bed item ("red"), or null. */
	public static @Nullable String colourOf(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String suffix : new String[] {"_wool", "_carpet", "_bed"}) {
			if (path.endsWith(suffix)) {
				String colour = path.substring(0, path.length() - suffix.length());
				return DyeColor.byName(colour, null) != null ? colour : null;
			}
		}
		return null;
	}

	/** True if this names a wood the game has planks for ("spruce", "dark_oak", "bamboo"). */
	public static boolean isWood(String wood) {
		return item(wood + "_planks").isPresent();
	}

	static Optional<Item> item(String path) {
		Identifier id = Identifier.tryParse("minecraft:" + path.toLowerCase(Locale.ROOT));
		return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id).filter(i -> i != Items.AIR);
	}

	// ----------------------------------------------------------------- planks

	/**
	 * Turns logs into planks until at least {@code target} planks are carried, logs of the preferred wood first. A
	 * preferred wood that is carried as logs is sawn even when other planks would already do, so the plan gets its
	 * wood.
	 */
	static boolean planks(CompanionEntity c, int target, @Nullable String wood) {
		Backpack bp = c.backpack();
		if (wood != null) {
			Predicate<ItemStack> preferredPlanks = s -> s.is(ItemTags.PLANKS) && MaterialSpec.matchesVariant(s, wood);
			sawLogs(bp, s -> MaterialSpec.matchesVariant(s, wood), () -> bp.count(preferredPlanks) >= target);
		}
		return Crafting.ensurePlanks(bp, target);
	}

	/** Saws logs matching {@code which} one at a time until {@code done} or none are left. */
	private static void sawLogs(Backpack bp, Predicate<ItemStack> which, java.util.function.BooleanSupplier done) {
		int guard = 0;
		while (!done.getAsBoolean() && guard++ < 64) {
			ItemStack log = bp.find(s -> which.test(s) && Crafting.planksFor(s).isPresent());
			if (log.isEmpty()) {
				return;
			}
			Item out = Crafting.planksFor(log).orElseThrow();
			if (!bp.canFit(new ItemStack(out, 4))) {
				return;
			}
			ItemStack one = log.copyWithCount(1);
			bp.remove(s -> ItemStack.isSameItemSameComponents(s, one), 1);
			bp.insert(new ItemStack(out, 4));
		}
	}

	// ------------------------------------------------------------------ shapes

	/**
	 * Crafts {@code wanted} of a wooden shape at a crafting table, every craft from planks of one wood: the preferred
	 * wood if there are enough of its planks (sawn from its logs if need be), otherwise the wood carried most. Returns
	 * the number of items made.
	 */
	static int craft(CompanionEntity c, Stock stock, int wanted, @Nullable String wood) {
		Shape shape = shape(stock);
		if (shape == null || wanted <= 0 || !Crafting.nearCraftingTable(c)) {
			return 0;
		}
		Backpack bp = c.backpack();
		int made = 0;
		int guard = 0;
		while (made < wanted && guard++ < 64) {
			if (shape.sticks() > 0 && !Crafting.ensureSticks(bp, shape.sticks())) {
				break;
			}
			String use = chooseWood(bp, shape.planks(), wood);
			if (use == null) {
				Crafting.ensurePlanks(bp, shape.planks());
				use = chooseWood(bp, shape.planks(), null);
			}
			if (use == null) {
				break;
			}
			String chosen = use;
			Item out = item(chosen + "_" + shape.suffix()).or(() -> item("oak_" + shape.suffix())).orElse(Items.AIR);
			if (out == Items.AIR || !bp.canFit(new ItemStack(out, shape.yield())) && bp.freeSlots() == 0) {
				break;
			}
			int fromSame = bp.remove(s -> s.is(ItemTags.PLANKS) && MaterialSpec.matchesVariant(s, chosen), shape.planks());
			if (fromSame < shape.planks()) {
				bp.remove(PLANKS, shape.planks() - fromSame);
			}
			bp.remove(STICK, shape.sticks());
			ItemStack left = bp.insert(new ItemStack(out, shape.yield()));
			if (!left.isEmpty()) {
				c.spawnAtLocation((net.minecraft.server.level.ServerLevel) c.level(), left);
			}
			made += shape.yield();
		}
		return made;
	}

	/**
	 * The wood to craft one item from: the preferred wood when its planks (or logs sawn now) cover a craft, otherwise
	 * the wood with the most planks carried, as long as there are enough for a craft. Null when no wood will do.
	 */
	private static @Nullable String chooseWood(Backpack bp, int planksNeeded, @Nullable String preferred) {
		if (preferred != null) {
			Predicate<ItemStack> mine = s -> s.is(ItemTags.PLANKS) && MaterialSpec.matchesVariant(s, preferred);
			sawLogs(bp, s -> MaterialSpec.matchesVariant(s, preferred), () -> bp.count(mine) >= planksNeeded);
			if (bp.count(mine) >= planksNeeded) {
				return preferred;
			}
		}
		Map<String, Integer> byWood = new HashMap<>();
		for (ItemStack s : bp.stacks()) {
			if (s.is(ItemTags.PLANKS)) {
				String w = woodOf(s);
				if (w != null) {
					byWood.merge(w, s.getCount(), Integer::sum);
				}
			}
		}
		String best = null;
		int most = 0;
		for (Map.Entry<String, Integer> e : byWood.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		if (best == null) {
			return null;
		}
		// Mixed woods may still add up to a craft: the item takes the wood carried most.
		return bp.count(PLANKS) >= planksNeeded ? best : null;
	}

	// ---------------------------------------------------------------- stripping

	/**
	 * Strips {@code wanted} logs with an axe (from the backpack or hand), as a player would: each log becomes its
	 * stripped log and the axe wears by one. Logs of the preferred wood first. Returns how many were stripped.
	 */
	static int strip(CompanionEntity c, int wanted, @Nullable String wood) {
		Backpack bp = c.backpack();
		int done = 0;
		while (done < wanted) {
			Predicate<ItemStack> bark = s -> s.is(ItemTags.LOGS) && strippedOf(s).isPresent();
			ItemStack log = wood == null ? ItemStack.EMPTY : bp.find(s -> bark.test(s) && MaterialSpec.matchesVariant(s, wood));
			if (log.isEmpty()) {
				log = bp.find(bark);
			}
			if (log.isEmpty() || !c.actions().equip(s -> s.is(ItemTags.AXES))) {
				break;
			}
			Item out = strippedOf(log).orElseThrow();
			if (!bp.canFit(new ItemStack(out)) && bp.freeSlots() == 0) {
				break;
			}
			ItemStack one = log.copyWithCount(1);
			bp.remove(s -> ItemStack.isSameItemSameComponents(s, one), 1);
			bp.insert(new ItemStack(out));
			if (!Unity.carefulHands(c)) {
				c.damageMainHandTool(1);
			}
			done++;
		}
		return done;
	}

	/** The stripped form of a log or stem ("stripped_spruce_log"), if it has one. */
	private static Optional<Item> strippedOf(ItemStack log) {
		String path = BuiltInRegistries.ITEM.getKey(log.getItem()).getPath();
		if (path.startsWith("stripped_") || !(path.endsWith("_log") || path.endsWith("_stem") || path.equals("bamboo_block"))) {
			return Optional.empty();
		}
		return item("stripped_" + path);
	}

	// ------------------------------------------------------------------- colour

	/** The colour with at least {@code count} wool carried: the preferred one if it has enough, else the most carried. */
	private static @Nullable String woolColour(Backpack bp, int count, @Nullable String preferred) {
		if (preferred != null && bp.count(s -> s.is(ItemTags.WOOL) && preferred.equals(colourOf(s))) >= count) {
			return preferred;
		}
		String best = null;
		int most = count - 1;
		for (DyeColor colour : DyeColor.values()) {
			String name = colour.getName();
			int n = bp.count(s -> s.is(ItemTags.WOOL) && name.equals(colourOf(s)));
			if (n > most) {
				most = n;
				best = name;
			}
		}
		return best;
	}

	/** Two wool of one colour make three carpets of it (no table needed). Returns how many were made. */
	static int carpet(CompanionEntity c, int wanted, @Nullable String colour) {
		Backpack bp = c.backpack();
		int made = 0;
		while (made < wanted) {
			String use = woolColour(bp, 2, colour);
			Optional<Item> out = use == null ? Optional.empty() : item(use + "_carpet");
			if (out.isEmpty() || !bp.canFit(new ItemStack(out.get(), 3)) && bp.freeSlots() == 0) {
				break;
			}
			String chosen = use;
			bp.remove(s -> s.is(ItemTags.WOOL) && chosen.equals(colourOf(s)), 2);
			bp.insert(new ItemStack(out.get(), 3));
			made += 3;
		}
		return made;
	}

	/**
	 * Beds at a crafting table: three wool of one colour and three planks make a bed of that colour (the preferred colour
	 * if there is enough of it); without wool, three hay bales make four straw beds. Returns how many were made.
	 */
	static int bed(CompanionEntity c, int wanted, @Nullable String colour) {
		if (!Crafting.nearCraftingTable(c)) {
			return 0;
		}
		Backpack bp = c.backpack();
		int made = 0;
		int guard = 0;
		while (made < wanted && guard++ < 32) {
			String use = woolColour(bp, 3, colour);
			if (use != null && Crafting.ensurePlanks(bp, 3)) {
				Optional<Item> out = item(use + "_bed");
				if (out.isEmpty() || !bp.canFit(new ItemStack(out.get())) && bp.freeSlots() == 0) {
					break;
				}
				String chosen = use;
				bp.remove(s -> s.is(ItemTags.WOOL) && chosen.equals(colourOf(s)), 3);
				bp.remove(PLANKS, 3);
				bp.insert(new ItemStack(out.get()));
				made++;
				continue;
			}
			if (bp.count(Items.HAY_BLOCK) >= 3 && (bp.canFit(new ItemStack(Items.STRAW_BED, 4)) || bp.freeSlots() > 0)) {
				bp.remove(s -> s.is(Items.HAY_BLOCK), 3);
				ItemStack left = bp.insert(new ItemStack(Items.STRAW_BED, 4));
				if (!left.isEmpty()) {
					c.spawnAtLocation((net.minecraft.server.level.ServerLevel) c.level(), left);
				}
				made += 4;
				continue;
			}
			break;
		}
		return made;
	}
}

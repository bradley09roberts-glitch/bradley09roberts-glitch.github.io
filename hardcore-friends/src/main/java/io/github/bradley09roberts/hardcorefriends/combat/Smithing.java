package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * What the camp's smith makes next, from spare materials in the supply chest, for friends who lack gear the chest
 * cannot already give them ({@link GearPlan}). In this order, each time for the friends in order of need (Aegis,
 * whoever fights at night, everyone):
 * <ol>
 * <li>a shield for every friend (6 planks and 1 iron ingot: cheap and well worth it);</li>
 * <li>a sword for any friend with no sword or axe at all (diamond, iron or stone);</li>
 * <li>armour for the fighters (chestplate, leggings, helmet, boots): diamond, iron or leather, whichever is the best
 * the chest can spare and is better than what they have;</li>
 * <li>a bow for Scout, Sage and Aegis (3 sticks and 3 string), then for the others while string is plentiful;</li>
 * <li>arrows (flint, a stick and a feather make 4) while the chest holds fewer than {@value #ARROWS_STOCKED};</li>
 * <li>armour for everyone else.</li>
 * </ol>
 * Only spare materials in the chest are used: never diamonds below a reserve of {@value #DIAMOND_RESERVE} (a pickaxe and
 * an enchanting table's worth), never iron below {@value #IRON_RESERVE} ({@value #SHIELD_IRON_RESERVE} for a shield) or
 * cobblestone below {@value #STONE_RESERVE},
 * and no iron, wood or stone at all while the building in hand is short of that kind
 * ({@link CampNeeds#buildShortage()}). Nothing the smith carries for their own work is used: everything is fetched
 * from the chest, and whatever is left over goes back. The gear goes into the chest, and the friends' gear job hands it
 * out.
 */
public final class Smithing {
	/** Diamonds always left in the chest: 3 for a pickaxe and 2 for an enchanting table, with room to spare. */
	public static final int DIAMOND_RESERVE = 7;
	/** Iron ingots always left in the chest (a hopper's worth for Spark). */
	public static final int IRON_RESERVE = 5;
	/** Iron ingots left in the chest when making a shield (one ingot each, and worth it early on). */
	public static final int SHIELD_IRON_RESERVE = 1;
	/** Cobblestone always left in the chest for building. */
	public static final int STONE_RESERVE = 16;
	/** Arrows are made while the chest holds fewer than this. */
	public static final int ARROWS_STOCKED = 32;
	/** Bows for friends who do not prefer them are only made with at least this much string spare. */
	private static final int STRING_PLENTY = 6;
	private static final int REFRESH = 200;

	/** The armour slots in the order they are filled: the chestplate protects most. */
	private static final EquipmentSlot[] SLOT_ORDER = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET};

	static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
	static final Predicate<ItemStack> LOGS = s -> s.is(ItemTags.LOGS);
	static final Predicate<ItemStack> STICKS = s -> s.is(Items.STICK);
	private static final Predicate<ItemStack> IRON = s -> s.is(Items.IRON_INGOT);
	private static final Predicate<ItemStack> DIAMOND = s -> s.is(Items.DIAMOND);
	private static final Predicate<ItemStack> LEATHER = s -> s.is(Items.LEATHER);
	private static final Predicate<ItemStack> STONE = s -> s.is(ItemTags.STONE_TOOL_MATERIALS);
	private static final Predicate<ItemStack> STRING = s -> s.is(Items.STRING);
	private static final Predicate<ItemStack> FLINT = s -> s.is(Items.FLINT);
	private static final Predicate<ItemStack> FEATHER = s -> s.is(Items.FEATHER);

	/** Something to fetch from the chest: {@code count} of the matching items. */
	public record Material(Predicate<ItemStack> match, int count) {
	}

	/**
	 * One piece of work: make {@code count} of {@code item} for {@code forWhom} (null: the camp's stock) from the
	 * materials, plus {@code planks} planks and {@code sticks} sticks (made from planks when the chest has none).
	 */
	public record Order(Item item, int count, @Nullable CompanionEntity forWhom, double score, List<Material> materials,
		int planks, int sticks) {
	}

	/** A material an armour piece can be made of, best first, and how much of it the chest can spare. */
	private record Option(int tier, Predicate<ItemStack> match, int spare) {
	}

	private static @Nullable ServerLevel cachedLevel;
	private static long cachedAt = Long.MIN_VALUE;
	private static @Nullable Order cachedOrder;

	private Smithing() {
	}

	/** The recipes the smith uses, at vanilla counts, all at a crafting table (called once from {@link Combat#init()}). */
	static void addRecipes() {
		Crafting.addRecipe(Items.SHIELD, 1, true, Crafting.of(ItemTags.PLANKS, 6), Crafting.of(Items.IRON_INGOT, 1));
		Item[] material = {Items.LEATHER, Items.IRON_INGOT, Items.DIAMOND};
		for (int tier = 0; tier < material.length; tier++) {
			for (EquipmentSlot slot : SLOT_ORDER) {
				Crafting.addRecipe(piece(slot, tier), 1, true, Crafting.of(material[tier], cost(slot)));
			}
		}
		Crafting.addRecipe(Items.STONE_SWORD, 1, true, Crafting.of(ItemTags.STONE_TOOL_MATERIALS, 2), Crafting.of(Items.STICK, 1));
		Crafting.addRecipe(Items.IRON_SWORD, 1, true, Crafting.of(Items.IRON_INGOT, 2), Crafting.of(Items.STICK, 1));
		Crafting.addRecipe(Items.DIAMOND_SWORD, 1, true, Crafting.of(Items.DIAMOND, 2), Crafting.of(Items.STICK, 1));
		Crafting.addRecipe(Items.IRON_AXE, 1, true, Crafting.of(Items.IRON_INGOT, 3), Crafting.of(Items.STICK, 2));
		Crafting.addRecipe(Items.DIAMOND_AXE, 1, true, Crafting.of(Items.DIAMOND, 3), Crafting.of(Items.STICK, 2));
		Crafting.addRecipe(Items.BOW, 1, true, Crafting.of(Items.STICK, 3), Crafting.of(Items.STRING, 3));
		Crafting.addRecipe(Items.ARROW, 4, true, Crafting.of(Items.FLINT, 1), Crafting.of(Items.STICK, 1), Crafting.of(Items.FEATHER, 1));
	}

	/** The next order (for scoring), worked out at most every {@value #REFRESH} ticks. */
	public static @Nullable Order cachedOrder(ServerLevel level) {
		long now = level.getGameTime();
		if (cachedLevel != level || now - cachedAt >= REFRESH || now < cachedAt) {
			cachedLevel = level;
			cachedAt = now;
			Optional<Container> chest = SupplyChest.of(level);
			cachedOrder = chest.isPresent() ? plan(level, chest.get()) : null;
		}
		Order order = cachedOrder;
		CompanionEntity forWhom = order == null ? null : order.forWhom();
		return order != null && (forWhom == null || forWhom.isAlive()) ? order : null;
	}

	/** Forgets the cached order: a server stopping, or an order just finished. */
	public static void clear() {
		cachedLevel = null;
		cachedAt = Long.MIN_VALUE;
		cachedOrder = null;
	}

	/** The next order, worked out afresh from the chest (see the class description); null when nothing is needed. */
	public static @Nullable Order plan(ServerLevel level, Container chest) {
		List<CompanionEntity> team = GearPlan.team(level);
		if (team.isEmpty()) {
			return null;
		}
		Budget b = new Budget(chest);
		Map<CompanionEntity, List<GearPlan.Pick>> fromChest = GearPlan.assign(level, chest);
		// 1. Shields for everyone.
		if (b.shieldIron >= 1 && b.wood(6, 0)) {
			for (CompanionEntity c : team) {
				if (c.getOffhandItem().isEmpty() && !Gear.hasShield(c) && !gets(fromChest, c, GearPlan.Kind.SHIELD, null)) {
					return new Order(Items.SHIELD, 1, c, 50, List.of(new Material(IRON, 1)), 6, 0);
				}
			}
		}
		// 2. A sword for anyone with nothing to fight with.
		if (b.wood(0, 1)) {
			for (CompanionEntity c : team) {
				if (Gear.bestWeaponRank(c) == 0 && !gets(fromChest, c, GearPlan.Kind.WEAPON, null)) {
					Order sword = b.diamonds >= 2 ? sword(Items.DIAMOND_SWORD, DIAMOND, c)
						: b.iron >= 2 ? sword(Items.IRON_SWORD, IRON, c)
						: b.stone >= 2 ? sword(Items.STONE_SWORD, STONE, c) : null;
					if (sword != null) {
						return sword;
					}
				}
			}
		}
		// 3. Armour for the fighters.
		for (CompanionEntity c : team) {
			if (GearPlan.priority(c) <= 1) {
				Order o = armour(c, b, fromChest, 44);
				if (o != null) {
					return o;
				}
			}
		}
		if (FriendsConfig.get().friendsUseBows) {
			// 4. Bows: Scout, Sage and Aegis first, the others while string is plentiful.
			if (b.string >= 3 && b.wood(0, 3)) {
				for (int pass = 0; pass < 2; pass++) {
					for (CompanionEntity c : team) {
						boolean keen = Archery.prefersBowByNature(c) || c.isFighter();
						if (pass == 0 && !keen || pass == 1 && (keen || b.string < STRING_PLENTY)) {
							continue;
						}
						if (!Gear.hasBow(c) && !gets(fromChest, c, GearPlan.Kind.BOW, null)) {
							return new Order(Items.BOW, 1, c, 42, List.of(new Material(STRING, 3)), 0, 3);
						}
					}
				}
			}
			// 5. Arrows for the bow carriers.
			int batches = Math.min(4, Math.min(b.flint, b.feathers));
			if (b.arrows < ARROWS_STOCKED && batches > 0 && b.wood(0, batches) && anyArcher(team, chest)) {
				return new Order(Items.ARROW, 4 * batches, null, 40,
					List.of(new Material(FLINT, batches), new Material(FEATHER, batches)), 0, batches);
			}
		}
		// 6. Armour for everyone else.
		for (CompanionEntity c : team) {
			if (GearPlan.priority(c) > 1) {
				Order o = armour(c, b, fromChest, 40);
				if (o != null) {
					return o;
				}
			}
		}
		return null;
	}

	private static Order sword(Item sword, Predicate<ItemStack> head, CompanionEntity c) {
		return new Order(sword, 1, c, 46, List.of(new Material(head, 2)), 0, 1);
	}

	/** The best armour piece the chest can spare for this friend that beats what they have; null if none. */
	private static @Nullable Order armour(CompanionEntity c, Budget b, Map<CompanionEntity, List<GearPlan.Pick>> fromChest,
		double score) {
		Option[] options = {new Option(2, DIAMOND, b.diamonds), new Option(1, IRON, b.iron), new Option(0, LEATHER, b.leather)};
		for (EquipmentSlot slot : SLOT_ORDER) {
			if (gets(fromChest, c, GearPlan.Kind.ARMOUR, slot)) {
				continue;
			}
			ItemStack current = Gear.bestOwned(c, slot);
			int cost = cost(slot);
			for (Option o : options) {
				Item item = piece(slot, o.tier());
				if (o.spare() >= cost && Gear.betterArmour(new ItemStack(item), current, slot)) {
					return new Order(item, 1, c, score, List.of(new Material(o.match(), cost)), 0, 0);
				}
			}
		}
		return null;
	}

	/** How many of the material one piece for this slot takes, as in vanilla. */
	private static int cost(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 5;
			case CHEST -> 8;
			case LEGS -> 7;
			default -> 4;
		};
	}

	/** The armour piece for a slot in a material: 0 leather, 1 iron, 2 diamond. */
	private static Item piece(EquipmentSlot slot, int tier) {
		return switch (slot) {
			case HEAD -> tier == 2 ? Items.DIAMOND_HELMET : tier == 1 ? Items.IRON_HELMET : Items.LEATHER_HELMET;
			case CHEST -> tier == 2 ? Items.DIAMOND_CHESTPLATE : tier == 1 ? Items.IRON_CHESTPLATE : Items.LEATHER_CHESTPLATE;
			case LEGS -> tier == 2 ? Items.DIAMOND_LEGGINGS : tier == 1 ? Items.IRON_LEGGINGS : Items.LEATHER_LEGGINGS;
			default -> tier == 2 ? Items.DIAMOND_BOOTS : tier == 1 ? Items.IRON_BOOTS : Items.LEATHER_BOOTS;
		};
	}

	/** True when the chest already gives this friend that kind of gear (for that slot, if given). */
	private static boolean gets(Map<CompanionEntity, List<GearPlan.Pick>> fromChest, CompanionEntity c, GearPlan.Kind kind,
		@Nullable EquipmentSlot slot) {
		for (GearPlan.Pick p : fromChest.getOrDefault(c, List.of())) {
			if (p.kind() == kind && (slot == null || p.slot() == slot)) {
				return true;
			}
		}
		return false;
	}

	private static boolean anyArcher(List<CompanionEntity> team, Container chest) {
		for (CompanionEntity c : team) {
			if (Gear.hasBow(c)) {
				return true;
			}
		}
		return SupplyChest.count(chest, s -> s.is(Items.BOW)) > 0;
	}

	/** Wooden planks it takes to make {@code sticks} sticks (two planks make four). */
	static int planksForSticks(int sticks) {
		return 2 * ((Math.max(0, sticks) + 3) / 4);
	}

	/** What the chest can spare, after the reserves and whatever the building in hand is short of. */
	private static final class Budget {
		final int iron;
		/** Iron a shield may use (a smaller reserve). */
		final int shieldIron;
		final int diamonds;
		final int leather;
		final int stone;
		/** Planks, counting four for each log. */
		final int planks;
		final int sticks;
		final int string;
		final int flint;
		final int feathers;
		final int arrows;

		Budget(Container chest) {
			Map<CampNeeds.Need, Integer> shortage = CampNeeds.buildShortage();
			boolean wood = !shortage.containsKey(CampNeeds.Need.WOOD);
			int ironInChest = shortage.containsKey(CampNeeds.Need.ORE) ? 0 : SupplyChest.count(chest, IRON);
			iron = Math.max(0, ironInChest - IRON_RESERVE);
			shieldIron = Math.max(0, ironInChest - SHIELD_IRON_RESERVE);
			diamonds = Math.max(0, SupplyChest.count(chest, DIAMOND) - DIAMOND_RESERVE);
			leather = SupplyChest.count(chest, LEATHER);
			stone = shortage.containsKey(CampNeeds.Need.STONE) ? 0 : Math.max(0, SupplyChest.count(chest, STONE) - STONE_RESERVE);
			planks = wood ? SupplyChest.count(chest, PLANKS) + 4 * SupplyChest.count(chest, LOGS) : 0;
			sticks = wood ? SupplyChest.count(chest, STICKS) : 0;
			string = SupplyChest.count(chest, STRING);
			flint = SupplyChest.count(chest, FLINT);
			feathers = SupplyChest.count(chest, FEATHER);
			arrows = SupplyChest.count(chest, s -> s.is(ItemTags.ARROWS));
		}

		/** The chest can spare {@code planksNeeded} planks and {@code sticksNeeded} sticks (made from planks if need be). */
		boolean wood(int planksNeeded, int sticksNeeded) {
			int missingSticks = Math.max(0, sticksNeeded - sticks);
			return planks >= planksNeeded + planksForSticks(missingSticks);
		}
	}
}

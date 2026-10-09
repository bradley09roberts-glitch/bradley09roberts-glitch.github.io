package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.role.guard.Gear;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Who gets what from the supply chest. The friends at work at the camp take turns in order of need: Aegis first (and
 * warrior newcomers), then whoever fights at night (anyone on watch or who kept one lately, the sword carriers Scout
 * and Sage, and anyone with a sword or axe), then everyone else, each group in roster order. Each in turn takes the
 * best piece the chest still has for every one of their needs, if it beats what they wear or carry:
 * <ul>
 * <li>armour for each slot (head, chest, legs, feet);</li>
 * <li>a shield for an empty off hand;</li>
 * <li>a sword or axe better than the best they carry (any sword before any axe, as in a fight);</li>
 * <li>a golden apple or a potion of healing or regeneration for emergencies, up to {@value #HEALING_FIGHTER} for Aegis
 * and {@value #HEALING_OTHERS} for the others (an enchanted golden apple is too precious to take);</li>
 * </ul>
 * then bows go round, Scout, Sage and Aegis first, and arrows to every bow carrier who is short ({@value #ARROWS_LOW}),
 * up to {@value #ARROWS_KEPT}. What is carried rather than worn (a weapon not of their own kind, healing, a bow,
 * arrows) is only handed to a friend with room for it in their backpack. So the chest's best gear always goes where it
 * matters most, and two friends never set out for the same piece. Worked out for the team at most every
 * {@value #REFRESH} ticks for scoring, and afresh at the chest.
 */
public final class GearPlan {
	/** What one pick is for. */
	public enum Kind {
		ARMOUR,
		SHIELD,
		WEAPON,
		BOW,
		ARROWS,
		HEALING
	}

	/** Take {@code count} from {@code chestSlot}, for {@code kind} (worn in {@code slot} for armour). */
	public record Pick(Kind kind, @Nullable EquipmentSlot slot, int chestSlot, int count) {
	}

	/** A bow carrier with fewer arrows than this tops up from the chest. */
	public static final int ARROWS_LOW = 16;
	/** Arrows a bow carrier tops up to. */
	public static final int ARROWS_KEPT = 32;
	/** Emergency healing items Aegis carries. */
	public static final int HEALING_FIGHTER = 2;
	/** Emergency healing items everyone else carries. */
	public static final int HEALING_OTHERS = 1;
	private static final int REFRESH = 100;

	private static @Nullable ServerLevel cachedLevel;
	private static long cachedAt = Long.MIN_VALUE;
	private static Map<UUID, List<Pick>> cached = Map.of();

	private GearPlan() {
	}

	/** 0 for Aegis (and warrior newcomers), 1 for whoever fights at night, 2 for everyone else. */
	public static int priority(CompanionEntity c) {
		if (c.isFighter()) {
			return 0;
		}
		if (KeepList.roleTool(c.friendId().role()) == ItemTags.SWORDS || c.isArmed() || NightWatch.isOnWatch(c)
			|| NightWatch.keptWatchRecently(c)) {
			return 1;
		}
		return 2;
	}

	/** The friends who take from the chest, in the order they choose: at work, at the camp or in its gathering ring. */
	public static List<CompanionEntity> team(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		Optional<BlockPos> centre = data.campPos();
		List<CompanionEntity> team = new ArrayList<>();
		if (!Camp.isCampLevel(level, data) || centre.isEmpty()) {
			return team;
		}
		double reach = Camp.radius(data) + FriendsConfig.get().resourceRadius;
		for (CompanionEntity c : Companions.in(level)) {
			if (c.mode() == CompanionMode.WORK && Camp.horizontalDistSqr(c.blockPosition(), centre.get()) <= reach * reach) {
				team.add(c);
			}
		}
		team.sort(Comparator.comparingInt(GearPlan::priority).thenComparingInt(CompanionEntity::rosterIndex));
		return team;
	}

	/** What this friend would take from the chest now (from the team plan, refreshed every {@value #REFRESH} ticks). */
	public static List<Pick> picksFor(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return List.of();
		}
		long now = level.getGameTime();
		if (cachedLevel != level || now - cachedAt >= REFRESH || now < cachedAt) {
			cachedLevel = level;
			cachedAt = now;
			Map<UUID, List<Pick>> plan = new HashMap<>();
			Optional<Container> chest = SupplyChest.of(level);
			if (chest.isPresent()) {
				assign(level, chest.get()).forEach((friend, picks) -> plan.put(friend.getUUID(), picks));
			}
			cached = plan;
		}
		return cached.getOrDefault(c.getUUID(), List.of());
	}

	/** Forgets the cached plan: a server stopping. */
	public static void clear() {
		cachedLevel = null;
		cachedAt = Long.MIN_VALUE;
		cached = Map.of();
	}

	/**
	 * Who takes what from this chest, worked out afresh (see the class description). Only what will fit in each
	 * friend's backpack is handed out (a bow, arrows, healing, a weapon carried for fights), so a friend with a full
	 * backpack is not sent to the chest again and again for something they cannot carry.
	 */
	public static Map<CompanionEntity, List<Pick>> assign(ServerLevel level, Container chest) {
		return assign(level, chest, true);
	}

	/**
	 * Who takes what from this chest. With {@code roomMatters} off, a pick is made whether or not it fits in the
	 * backpack yet: the smith asks this way, so it does not make another bow for a friend whose bow is waiting in the
	 * chest until they have made room.
	 */
	public static Map<CompanionEntity, List<Pick>> assign(ServerLevel level, Container chest, boolean roomMatters) {
		int size = chest.getContainerSize();
		int[] left = new int[size];
		for (int i = 0; i < size; i++) {
			left[i] = chest.getItem(i).getCount();
		}
		List<CompanionEntity> team = team(level);
		Map<CompanionEntity, List<Pick>> plan = new LinkedHashMap<>();
		Map<CompanionEntity, int[]> rooms = new HashMap<>();
		for (CompanionEntity c : team) {
			List<Pick> picks = new ArrayList<>();
			int[] room = {roomMatters ? c.backpack().freeSlots() : Integer.MAX_VALUE};
			rooms.put(c, room);
			for (EquipmentSlot slot : Gear.ARMOUR_SLOTS) {
				pickArmour(c, chest, left, slot, picks);
			}
			if (c.getOffhandItem().isEmpty() && !Gear.hasShield(c)) {
				int best = bestSlot(chest, left, s -> Gear.blocks(s) ? 1 + durabilityLeft(s) : -1);
				claim(picks, left, Kind.SHIELD, EquipmentSlot.OFFHAND, best, 1);
			}
			int carried = Gear.bestWeaponRank(c);
			TagKey<Item> ownTool = KeepList.roleTool(c.friendId().role());
			// Their own kind of tool goes into the hand; any other weapon is carried, so it needs a free slot.
			int weapon = bestSlot(chest, left, s -> Gear.weaponRank(s) > carried
				&& (ownTool != null && s.is(ownTool) || room[0] > 0) ? Gear.weaponRank(s) : -1);
			if (claim(picks, left, Kind.WEAPON, EquipmentSlot.MAINHAND, weapon, 1)
				&& (ownTool == null || !chest.getItem(weapon).is(ownTool)) && room[0] != Integer.MAX_VALUE) {
				room[0]--;
			}
			int wantHealing = (c.isFighter() ? HEALING_FIGHTER : HEALING_OTHERS) - Gear.healingItems(c);
			takeStack(c, chest, left, room, picks, Kind.HEALING, wantHealing,
				s -> Gear.isHealing(s) && !s.is(Items.ENCHANTED_GOLDEN_APPLE));
			plan.put(c, picks);
		}
		if (FriendsConfig.get().friendsUseBows) {
			List<CompanionEntity> archers = new ArrayList<>(team);
			archers.sort(Comparator.comparing((CompanionEntity c) -> !(Archery.prefersBowByNature(c) || c.isFighter())));
			for (CompanionEntity c : archers) {
				List<Pick> picks = plan.get(c);
				int[] room = rooms.get(c);
				boolean bow = Gear.hasBow(c);
				if (!bow) {
					int best = bestSlot(chest, left, s -> s.is(Items.BOW) ? 1 + durabilityLeft(s) : -1);
					bow = best >= 0 && fits(c, chest.getItem(best), 1, room) && claim(picks, left, Kind.BOW, null, best, 1);
				}
				int have = Gear.arrows(c);
				if (bow && have < ARROWS_LOW) {
					takeStack(c, chest, left, room, picks, Kind.ARROWS, ARROWS_KEPT - have, s -> s.is(Items.ARROW));
					takeStack(c, chest, left, room, picks, Kind.ARROWS, ARROWS_KEPT - have - taken(picks, Kind.ARROWS),
						s -> s.is(ItemTags.ARROWS) && !s.is(Items.ARROW));
				}
			}
		}
		plan.values().removeIf(List::isEmpty);
		return plan;
	}

	/**
	 * True when {@code count} of this item would fit in the friend's backpack: onto stacks of it they carry, then into
	 * the free slots left after their earlier picks ({@code room}, counted down here). Always true when room does not
	 * matter.
	 */
	private static boolean fits(CompanionEntity c, ItemStack stack, int count, int[] room) {
		if (room[0] == Integer.MAX_VALUE) {
			return true;
		}
		int onto = 0;
		for (ItemStack s : c.backpack().stacks()) {
			if (ItemStack.isSameItemSameComponents(s, stack)) {
				onto += Math.max(0, s.getMaxStackSize() - s.getCount());
			}
		}
		int rest = count - onto;
		if (rest <= 0) {
			return true;
		}
		int slots = (rest + stack.getMaxStackSize() - 1) / stack.getMaxStackSize();
		if (slots > room[0]) {
			return false;
		}
		room[0] -= slots;
		return true;
	}

	private static void pickArmour(CompanionEntity c, Container chest, int[] left, EquipmentSlot slot, List<Pick> picks) {
		ItemStack current = Gear.bestOwned(c, slot);
		int best = -1;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < left.length; i++) {
			ItemStack s = chest.getItem(i);
			if (left[i] > 0 && Gear.betterArmour(s, current, slot) && Gear.armourScore(s, slot) > bestScore) {
				bestScore = Gear.armourScore(s, slot);
				best = i;
			}
		}
		claim(picks, left, Kind.ARMOUR, slot, best, 1);
	}

	/** The chest slot with the highest positive rank among those not yet claimed, or -1. */
	private static int bestSlot(Container chest, int[] left, java.util.function.ToDoubleFunction<ItemStack> rank) {
		int best = -1;
		double bestRank = 0;
		for (int i = 0; i < left.length; i++) {
			if (left[i] <= 0) {
				continue;
			}
			double r = rank.applyAsDouble(chest.getItem(i));
			if (r > bestRank) {
				bestRank = r;
				best = i;
			}
		}
		return best;
	}

	private static boolean claim(List<Pick> picks, int[] left, Kind kind, @Nullable EquipmentSlot slot, int chestSlot, int count) {
		if (chestSlot < 0 || count <= 0 || left[chestSlot] < count) {
			return false;
		}
		left[chestSlot] -= count;
		picks.add(new Pick(kind, slot, chestSlot, count));
		return true;
	}

	/** Claims up to {@code want} matching items that fit in the backpack, spread over as many chest slots as it takes. */
	private static void takeStack(CompanionEntity c, Container chest, int[] left, int[] room, List<Pick> picks, Kind kind,
		int want, java.util.function.Predicate<ItemStack> match) {
		for (int i = 0; i < left.length && want > 0; i++) {
			if (left[i] > 0 && match.test(chest.getItem(i))) {
				int n = Math.min(want, left[i]);
				if (fits(c, chest.getItem(i), n, room)) {
					claim(picks, left, kind, null, i, n);
					want -= n;
				}
			}
		}
	}

	private static int taken(List<Pick> picks, Kind kind) {
		int n = 0;
		for (Pick p : picks) {
			if (p.kind() == kind) {
				n += p.count();
			}
		}
		return n;
	}

	private static double durabilityLeft(ItemStack s) {
		return s.isDamageableItem() && s.getMaxDamage() > 0 ? (s.getMaxDamage() - s.getDamageValue()) / (double) s.getMaxDamage() : 1.0;
	}
}

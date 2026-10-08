package io.github.bradley09roberts.hardcorefriends.town;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.registry.ModItems;
import io.github.bradley09roberts.hardcorefriends.survival.Trips;

/**
 * When a player dies. In a Hardcore world the friends mourn them (those closest to them speak, and the closest is heard
 * by everyone) and the camp remembers them on its memorial ({@code /friends camp}). In any world, for the next five
 * minutes, friends nearby keep the dead player's things safe: by day and with no monster about, they gather the items
 * lying within {@value #RANGE} blocks of where the player died into a bag named after them ("Bob's Belongings"),
 * which goes into the supply chest, and everyone is told who has them and where they are. The bag keeps the things
 * together, so no friend wears the armour or eats the food; using the bag empties it into your inventory.
 *
 * <p>Nothing here touches the player's own death, respawn or game mode: this only listens.
 */
public final class Mourning {
	/** How long after a death its items are kept safe, in ticks (five minutes: as long as dropped items last). */
	static final long WINDOW = 6000;
	/** Items within this many blocks of the death spot count as the dead player's. */
	static final double RANGE = 8;
	/** Friends within this many blocks of the death spot come to help. */
	static final double HELP_RANGE = 48;
	/** A bag of a fallen player's things carries this marker (the player's id) in its custom data. */
	private static final String KEEPSAKE = "hardcorefriends_keepsake";
	/** How often a death spot is looked over (safe? items left?), in ticks, for every friend together. */
	private static final int CHECK_TICKS = 20;

	/** Where a player died, and what is known about it. */
	static final class Spot {
		final UUID player;
		final String name;
		final ResourceKey<Level> dimension;
		final BlockPos pos;
		final long at;
		/** The item entities dropped at the death (marked as the player's own), by id. */
		final Set<UUID> items = new HashSet<>();
		boolean marked;
		boolean announced;
		long checkedAt = Long.MIN_VALUE;
		boolean safe;
		boolean hasItems;

		Spot(UUID player, String name, ResourceKey<Level> dimension, BlockPos pos, long at) {
			this.player = player;
			this.name = name;
			this.dimension = dimension;
			this.pos = pos;
			this.at = at;
		}
	}

	private static final List<Spot> SPOTS = new ArrayList<>();
	/** Items a friend is on the way to pick up: item id to the friend. */
	private static final Map<UUID, UUID> CLAIMED = new HashMap<>();

	private Mourning() {
	}

	// ------------------------------------------------------------------- death

	/** A player died (the {@code AFTER_DEATH} event): remember the spot, mourn and remember them (Hardcore only). */
	static void playerDied(ServerPlayer player, DamageSource source) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		SPOTS.removeIf(s -> s.player.equals(player.getUUID()) || now - s.at > WINDOW || now < s.at);
		SPOTS.add(new Spot(player.getUUID(), player.getName().getString(), level.dimension(), player.blockPosition(), now));
		Sieges.playerDied(player);
		TownData data = TownData.get(server);
		data.remember(player);
		if (!server.isHardcore()) {
			return;
		}
		String cause = source.getLocalizedDeathMessage(player).getString();
		data.addMemorial(new TownData.Memorial(player.getName().getString(), cause, Camp.dimensionId(level), player.blockPosition(),
			Camp.day(level)));
		mourn(server, player);
	}

	/** The friends closest to the player say goodbye: the closest one is heard by everyone, two more nearby. */
	private static void mourn(MinecraftServer server, ServerPlayer player) {
		List<CompanionEntity> friends = new ArrayList<>(Companions.all());
		UUID id = player.getUUID();
		friends.removeIf(c -> Bonds.get(c, id) <= Bonds.COOL);
		friends.sort(Comparator.comparingInt((CompanionEntity c) -> Bonds.get(c, id)).reversed()
			.thenComparingDouble(c -> c.level() == player.level() ? c.distanceToSqr(player) : Double.MAX_VALUE));
		String name = player.getName().getString();
		for (int i = 0; i < friends.size() && i < 3; i++) {
			if (i == 0) {
				Trips.announce(friends.get(i), Line.MOURN_PLAYER, name);
			} else {
				Speech.say(friends.get(i), Line.MOURN_PLAYER, name);
			}
		}
		Speech.announce(server, Component.literal("The camp will remember " + name + ". (/friends camp)")
			.withStyle(ChatFormatting.GRAY));
	}

	/**
	 * Once a tick: marks the items dropped at a fresh death as the dead player's own (so the everyday tidying leaves
	 * them for this), and forgets death spots older than five minutes.
	 */
	static void tick(MinecraftServer server) {
		if (SPOTS.isEmpty()) {
			return;
		}
		for (Iterator<Spot> it = SPOTS.iterator(); it.hasNext();) {
			Spot s = it.next();
			ServerLevel level = server.getLevel(s.dimension);
			long now = server.overworld().getGameTime();
			if (level == null || now - s.at > WINDOW || now < s.at) {
				it.remove();
				continue;
			}
			if (!s.marked && now > s.at) {
				s.marked = true;
				mark(level, server, s);
			}
		}
		if (SPOTS.isEmpty()) {
			CLAIMED.clear();
		}
	}

	/** The death drops lie within a few blocks and are brand new: they are the player's, whatever happens next. */
	private static void mark(ServerLevel level, MinecraftServer server, Spot s) {
		if (!level.isLoaded(s.pos)) {
			return;
		}
		Entity owner = server.getPlayerList().getPlayer(s.player);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(s.pos).inflate(4),
			e -> e.isAlive() && e.getAge() <= 20 && e.getOwner() == null)) {
			s.items.add(item.getUUID());
			if (owner != null) {
				item.setThrower(owner);
			}
		}
	}

	// ------------------------------------------------------------- the job

	/** The death spot a friend could help with now, if any: the nearest in their level, safe, with items left. */
	static @Nullable Spot spotFor(CompanionEntity c, ServerLevel level) {
		if (SPOTS.isEmpty()) {
			return null;
		}
		Spot best = null;
		double bestDist = HELP_RANGE * HELP_RANGE;
		long now = level.getGameTime();
		for (Spot s : SPOTS) {
			if (s.dimension != level.dimension() || now - s.at > WINDOW) {
				continue;
			}
			double d = c.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY(), s.pos.getZ() + 0.5);
			if (d > bestDist) {
				continue;
			}
			check(level, s, now);
			if (s.safe && s.hasItems) {
				best = s;
				bestDist = d;
			}
		}
		return best;
	}

	/** Is the spot safe (daylight, no monster within 16 blocks) and is anything left? Worked out once a second. */
	private static void check(ServerLevel level, Spot s, long now) {
		if (now - s.checkedAt < CHECK_TICKS && now >= s.checkedAt) {
			return;
		}
		s.checkedAt = now;
		if (!level.isLoaded(s.pos) || Camp.isNight(level)) {
			s.safe = false;
			return;
		}
		AABB around = new AABB(s.pos).inflate(16, 8, 16);
		s.safe = level.getEntitiesOfClass(LivingEntity.class, around, Threats::isThreat).isEmpty();
		s.hasItems = s.safe && !level.getEntitiesOfClass(ItemEntity.class, area(s), e -> belongs(s, e)).isEmpty();
	}

	static boolean stillSafe(ServerLevel level, Spot s) {
		check(level, s, level.getGameTime());
		return s.safe && level.getGameTime() - s.at <= WINDOW;
	}

	private static AABB area(Spot s) {
		return new AABB(s.pos).inflate(RANGE, 4, RANGE);
	}

	/**
	 * Whether an item counts as the dead player's: one dropped at the death, or any item lying near the spot that
	 * nobody threw or that the dead player threw. A friend's backpack is never taken (unless it was in the player's
	 * inventory), nor anything another player dropped.
	 */
	static boolean belongs(Spot s, ItemEntity item) {
		if (!item.isAlive() || item.isRemoved() || item.getItem().isEmpty() || item.isInLava()) {
			return false;
		}
		if (s.items.contains(item.getUUID())) {
			return true;
		}
		if (item.getItem().is(ModItems.BACKPACK)) {
			return false;
		}
		Entity thrower = item.getOwner();
		return thrower == null || thrower.getUUID().equals(s.player);
	}

	/** The nearest item of the spot's that no other friend is on the way to, within reach of the spot. */
	static @Nullable ItemEntity nextItem(CompanionEntity c, ServerLevel level, Spot s, Set<UUID> ignored) {
		ItemEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(s), e -> belongs(s, e) && !ignored.contains(e.getUUID()))) {
			UUID holder = CLAIMED.get(item.getUUID());
			if (holder != null && !holder.equals(c.getUUID())) {
				continue;
			}
			double d = item.distanceToSqr(c);
			if (d < bestDist) {
				bestDist = d;
				best = item;
			}
		}
		if (best != null) {
			CLAIMED.put(best.getUUID(), c.getUUID());
		}
		return best;
	}

	static void release(CompanionEntity c) {
		CLAIMED.values().removeIf(id -> id.equals(c.getUUID()));
	}

	/** Tells everyone, once per death, which friend is keeping the player's things and where they will be. */
	static void announceKeeping(CompanionEntity c, Spot s) {
		if (s.announced || !(c.level() instanceof ServerLevel level)) {
			return;
		}
		s.announced = true;
		Speech.say(c, Line.KEEPING_ITEMS, s.name);
		String where = Camp.data(level.getServer()).chestPos().filter(p -> Camp.isCampLevel(level, Camp.data(level.getServer())))
			.map(p -> "the supply chest at " + Compass.coords(p)).orElse("their backpack until there is a supply chest");
		Speech.announce(level.getServer(), Speech.prefix(c).append(Component.literal("is gathering " + s.name
			+ "'s things to keep them safe. They will be in a bag called \"" + s.name + "'s Belongings\" in " + where + ".")
			.withStyle(ChatFormatting.GRAY)));
	}

	// --------------------------------------------------------------- the bag

	/** True for a bag holding a fallen player's things (any player's). */
	static boolean isKeepsake(ItemStack s) {
		return s.is(ModItems.BACKPACK) && s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(KEEPSAKE);
	}

	/**
	 * Puts a picked-up stack into the friend's bag of this player's things (a new bag in a free backpack slot, if they
	 * have none yet). Returns false when it cannot (no free slot, or the bag is full), leaving the stack as it was.
	 */
	static boolean addToBag(CompanionEntity c, Spot s, ItemStack stack) {
		String id = s.player.toString();
		int slot = c.backpack().slotOf(b -> isKeepsake(b)
			&& id.equals(b.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(KEEPSAKE, "")));
		if (slot < 0) {
			ItemStack bag = new ItemStack(ModItems.BACKPACK);
			CompoundTag marker = new CompoundTag();
			marker.putString(KEEPSAKE, id);
			bag.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
			bag.set(DataComponents.CUSTOM_NAME, Component.literal(s.name + "'s Belongings").withStyle(st -> st.withItalic(false)));
			bag.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			if (!c.backpack().insert(bag).isEmpty()) {
				return false;
			}
			slot = c.backpack().slotOf(b -> isKeepsake(b)
				&& id.equals(b.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(KEEPSAKE, "")));
			if (slot < 0) {
				return false;
			}
		}
		ItemStack bag = c.backpack().get(slot);
		List<ItemStack> inside = new ArrayList<>(bag.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
			.nonEmptyItemCopyStream().toList());
		if (inside.size() >= ItemContainerContents.MAX_SIZE) {
			return false;
		}
		inside.add(stack.copy());
		bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
		c.backpack().container().setChanged();
		return true;
	}

	/** True when the friend has room for (another) bag, or already carries one for this player. */
	static boolean hasRoom(CompanionEntity c, Spot s) {
		String id = s.player.toString();
		return c.backpack().freeSlots() > 0 || c.backpack().has(b -> isKeepsake(b)
			&& id.equals(b.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr(KEEPSAKE, "")));
	}

	/** Forgets every death spot and claim (a server stopping). */
	static void clear() {
		SPOTS.clear();
		CLAIMED.clear();
	}
}

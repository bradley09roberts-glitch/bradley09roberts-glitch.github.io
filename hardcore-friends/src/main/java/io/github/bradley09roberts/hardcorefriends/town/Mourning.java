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
import java.util.function.Predicate;

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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
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
 * <p>Friends are never sent into what killed the player. They only go when a creature (not an explosion) or hunger
 * killed them, and that creature being gone is checked at the spot: never after lava, fire, a fall, drowning, an
 * explosion, the void or being crushed, whose danger may still lie there. The spot must be lit, out of water and lava,
 * in a world with day and night, with no monster within bow range; each item must lie in the light, out of water and
 * away from lava.
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
	/** Monsters this far beyond the items (about a skeleton's bow range) make the spot unsafe. */
	private static final double THREAT_REACH = 16;
	/** The spot and every item gathered must be at least this bright: daylight in the open, or a well-lit place. */
	private static final int MIN_LIGHT = 8;

	/** Where a player died, and what is known about it. */
	static final class Spot {
		final UUID player;
		final String name;
		final ResourceKey<Level> dimension;
		final BlockPos pos;
		final long at;
		/**
		 * True when friends must leave the things where they lie: what killed the player may still be there (lava, a
		 * fall, drowning, an explosion...), or the world has no day and night (the Nether, the End).
		 */
		final boolean leaveBe;
		/** The item entities dropped at the death (marked as the player's own), by id. */
		final Set<UUID> items = new HashSet<>();
		boolean marked;
		boolean announced;
		/** Game time of the last look over the spot; -1 before the first. */
		long checkedAt = -1;
		boolean safe;
		boolean hasItems;

		Spot(UUID player, String name, ResourceKey<Level> dimension, BlockPos pos, long at, boolean leaveBe) {
			this.player = player;
			this.name = name;
			this.dimension = dimension;
			this.pos = pos;
			this.at = at;
			this.leaveBe = leaveBe;
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
		boolean leaveBe = !placeMayBeSafe(source) || level.dimensionType().hasFixedTime();
		SPOTS.add(new Spot(player.getUUID(), player.getName().getString(), level.dimension(), player.blockPosition(), now, leaveBe));
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

	/**
	 * Whether friends may go to where the player died: only when a creature killed them (whether it is still about is
	 * checked at the spot) or something in the player rather than the place did (hunger, poison, a potion). Never after
	 * lava, fire, a fall, drowning, freezing, lightning, an explosion, the void, suffocation or anything else the place
	 * itself did, which could still catch a friend there.
	 */
	private static boolean placeMayBeSafe(DamageSource source) {
		if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.SONIC_BOOM)) {
			return false;
		}
		if (source.getEntity() instanceof LivingEntity) {
			return true;
		}
		return source.is(DamageTypes.STARVE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER)
			|| source.is(DamageTypes.GENERIC) || source.is(DamageTypes.GENERIC_KILL);
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

	/**
	 * The death drops lie within a few blocks and are brand new: they are the player's, whatever happens next. A fallen
	 * friend's backpack never despawns (its age stays below zero), so one lying nearby, even from the same fight, is
	 * never mistaken for the player's: it stays where everyone was told it lies.
	 */
	private static void mark(ServerLevel level, MinecraftServer server, Spot s) {
		if (!level.isLoaded(s.pos)) {
			return;
		}
		Entity owner = server.getPlayerList().getPlayer(s.player);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(s.pos).inflate(4),
			e -> e.isAlive() && e.getAge() >= 0 && e.getAge() <= 20 && e.getOwner() == null)) {
			s.items.add(item.getUUID());
			if (owner != null) {
				item.setThrower(owner);
			}
		}
	}

	// ------------------------------------------------------------- the job

	/**
	 * The death spot a friend could help with now, if any: the nearest in their level, safe, with items left, that also
	 * passes {@code usable} (for example: with an item no other friend is fetching).
	 */
	static @Nullable Spot spotFor(CompanionEntity c, ServerLevel level, Predicate<Spot> usable) {
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
			if (s.safe && s.hasItems && usable.test(s)) {
				best = s;
				bestDist = d;
			}
		}
		return best;
	}

	/**
	 * Is the spot safe, and is anything left to fetch? Safe means: a death friends may go to at all
	 * ({@link Spot#leaveBe}), by day, lit, out of water and lava, and no monster within bow range of the items. Worked
	 * out once a second.
	 */
	private static void check(ServerLevel level, Spot s, long now) {
		if (s.checkedAt >= 0 && now >= s.checkedAt && now - s.checkedAt < CHECK_TICKS) {
			return;
		}
		s.checkedAt = now;
		s.safe = false;
		s.hasItems = false;
		if (s.leaveBe || !level.isLoaded(s.pos) || Camp.isNight(level) || !lit(level, s.pos) || nearFluid(level, s.pos, false)) {
			return;
		}
		AABB around = area(s).inflate(THREAT_REACH, THREAT_REACH / 2, THREAT_REACH);
		s.safe = level.getEntitiesOfClass(LivingEntity.class, around, Threats::isThreat).isEmpty();
		if (s.safe) {
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(s), e -> belongs(s, e))) {
				if (fetchable(level, item)) {
					s.hasItems = true;
					break;
				}
			}
		}
	}

	/** Bright enough to see by (and for no monster to spawn): daylight in the open, or a well-lit place. */
	private static boolean lit(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos)
			&& Math.max(level.getMaxLocalRawBrightness(pos), level.getMaxLocalRawBrightness(pos.above())) >= MIN_LIGHT;
	}

	/** Water or lava ({@code lavaOnly}: just lava) in or right next to this block. Only looks at loaded blocks. */
	private static boolean nearFluid(ServerLevel level, BlockPos pos, boolean lavaOnly) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					m.setWithOffset(pos, dx, dy, dz);
					if (!level.isLoaded(m)) {
						continue;
					}
					FluidState fluid = level.getFluidState(m);
					if (!fluid.isEmpty() && (!lavaOnly || fluid.is(FluidTags.LAVA))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** An item a friend may go and fetch: in the light, not in water or lava, and not right next to lava. */
	static boolean fetchable(ServerLevel level, ItemEntity item) {
		BlockPos p = item.blockPosition();
		return !item.isInWater() && !item.isInLava() && lit(level, p) && !nearFluid(level, p, true);
	}

	static boolean stillSafe(ServerLevel level, Spot s) {
		check(level, s, level.getGameTime());
		return s.safe && level.getGameTime() - s.at <= WINDOW;
	}

	private static AABB area(Spot s) {
		return new AABB(s.pos).inflate(RANGE, 4, RANGE);
	}

	/**
	 * Whether an item counts as the dead player's: one dropped at the death, one the dead player threw, or one nobody
	 * threw that turned up since the death (not one lying there before, which an offline player may have thrown). A
	 * friend's backpack is never taken (unless it was in the player's inventory), nor anything another player dropped.
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
		if (thrower != null) {
			return thrower.getUUID().equals(s.player);
		}
		long since = item.level().getGameTime() - s.at;
		return since >= 0 && item.getAge() >= 0 && item.getAge() <= since + 20;
	}

	/** True while a player died in the last five minutes (otherwise there is nothing to keep safe). */
	static boolean active() {
		return !SPOTS.isEmpty();
	}

	/** True when the spot has an item this friend could fetch: one no other friend is on the way to, not given up on. */
	static boolean hasFreeItem(CompanionEntity c, ServerLevel level, Spot s, Set<UUID> ignored) {
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(s), e -> free(c, s, e, ignored))) {
			if (fetchable(level, item)) {
				return true;
			}
		}
		return false;
	}

	/** The spot's item, not given up on by this friend, and not claimed by another friend. */
	private static boolean free(CompanionEntity c, Spot s, ItemEntity item, Set<UUID> ignored) {
		if (ignored.contains(item.getUUID()) || !belongs(s, item)) {
			return false;
		}
		UUID holder = CLAIMED.get(item.getUUID());
		return holder == null || holder.equals(c.getUUID());
	}

	/** The nearest item of the spot's that no other friend is on the way to and that is safe to fetch. */
	static @Nullable ItemEntity nextItem(CompanionEntity c, ServerLevel level, Spot s, Set<UUID> ignored) {
		ItemEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area(s), e -> free(c, s, e, ignored))) {
			if (!fetchable(level, item)) {
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

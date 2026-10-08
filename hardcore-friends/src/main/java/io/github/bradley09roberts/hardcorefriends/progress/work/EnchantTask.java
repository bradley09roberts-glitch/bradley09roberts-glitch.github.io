package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.Experience;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressData;
import io.github.bradley09roberts.hardcorefriends.progress.Stations;
import io.github.bradley09roberts.hardcorefriends.progress.Tiers;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Enchanting at the camp's table, Sage's speciality. Friends have no experience levels, so the camp's shared pool
 * ({@link Experience}) stands in for a player's: the friend takes the best unenchanted gear from the chest (swords,
 * armour, pickaxes, bows; never anything a player has named) and 3 lapis, and at the table uses the top offer exactly as
 * vanilla's enchanting screen would work it out (the table's bookshelves, the camp's enchanting seed, vanilla's
 * {@link EnchantmentHelper}). Like a player, the pool must be at least at the offer's level (30 with 15 shelves), and
 * the enchant costs 3 lapis and 3 levels; the seed then changes, as a player's does. The gear goes back in the chest.
 *
 * <p>Before enchanting at the library, any block the friends set inside its ring (a flower, a torch) is taken out
 * again, since it would block the shelves' power.
 */
public final class EnchantTask implements CompanionTask {
	/** The top slot of the table, as on the screen: costs 3 lapis and 3 levels. */
	private static final int SLOT = 2;
	private static final int LAPIS = SLOT + 1;
	/** Gear more worn than this is mended first rather than enchanted. */
	private static final double MAX_WEAR = 0.5;

	private enum Phase {
		FETCH,
		TABLE,
		RETURN
	}

	private Phase phase = Phase.FETCH;
	private @Nullable Item chosen;
	private @Nullable BlockPos table;
	/** The very stack taken from the chest, so exactly that one goes back (never the friend's own spare). */
	private @Nullable ItemStack taken;

	@Override
	public String id() {
		return "sage.enchant";
	}

	@Override
	public String describe() {
		return "enchanting gear";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || Camp.isNight(level)) {
			return 0;
		}
		BlockPos at = Stations.enchantingTable(level);
		if (at == null) {
			return 0;
		}
		MinecraftServer server = level.getServer();
		int power = Stations.power(level, at);
		if (Experience.level(server) < Math.max(LAPIS, power * 2)) {
			return 0;
		}
		if (c.backpack().count(Items.LAPIS_LAZULI) + Trips.inChest(c, s -> s.is(Items.LAPIS_LAZULI)) < LAPIS) {
			return 0;
		}
		return bestInChest(c) != null ? 45 : 0;
	}

	/** Gear worth enchanting: enchantable, not yet enchanted, not named by a player, not badly worn. */
	static boolean candidate(ItemStack s) {
		return Tiers.enchantable(s) && s.isEnchantable() && !s.isEnchanted() && !Tiers.named(s)
			&& (!s.isDamageableItem() || s.getDamageValue() <= s.getMaxDamage() * MAX_WEAR);
	}

	/** The kind of the best candidate in the chest (diamond before iron before the rest), or null. */
	private static @Nullable Item bestInChest(CompanionEntity c) {
		Optional<Container> chest = SupplyChest.of((ServerLevel) c.level());
		if (chest.isEmpty()) {
			return null;
		}
		ItemStack best = ItemStack.EMPTY;
		Container container = chest.get();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (candidate(s) && (best.isEmpty() || Tiers.rank(s) > Tiers.rank(best))) {
				best = s;
			}
		}
		return best.isEmpty() ? null : best.getItem();
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		chosen = bestInChest(c);
		table = Stations.enchantingTable(level);
		taken = null;
		phase = Phase.FETCH;
		return chosen != null && table != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Item item = chosen;
		BlockPos at = table;
		if (item == null || at == null) {
			return TaskStatus.FAILURE;
		}
		Predicate<ItemStack> mine = s -> s.is(item) && candidate(s);
		return switch (phase) {
			case FETCH -> fetch(c, mine);
			case TABLE -> atTable(c, at);
			case RETURN -> putBack(c);
		};
	}

	private TaskStatus fetch(CompanionEntity c, Predicate<ItemStack> mine) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.FAILED) {
			return TaskStatus.FAILURE;
		}
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		java.util.Set<ItemStack> own = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		own.addAll(c.backpack().stacks());
		Trips.take(chest.get(), c, mine, 1);
		for (ItemStack s : c.backpack().stacks()) {
			if (!own.contains(s) && mine.test(s)) {
				taken = s;
			}
		}
		Trips.take(chest.get(), c, s -> s.is(Items.LAPIS_LAZULI), LAPIS - c.backpack().count(Items.LAPIS_LAZULI));
		if (taken == null || c.backpack().count(Items.LAPIS_LAZULI) < LAPIS) {
			phase = Phase.RETURN;
			return TaskStatus.RUNNING;
		}
		phase = Phase.TABLE;
		return TaskStatus.RUNNING;
	}

	private TaskStatus atTable(CompanionEntity c, BlockPos at) {
		ServerLevel level = (ServerLevel) c.level();
		if (!level.isLoaded(at) || !Trips.reach(c, at)) {
			if (c.actions().isStuck()) {
				phase = Phase.RETURN;
			}
			return TaskStatus.RUNNING;
		}
		BlockPos clutter = libraryClutter(level, at);
		if (clutter != null) {
			Actions.Result r = c.actions().mine(clutter, WorldEditGuard.Reason.BUILD);
			if (r == Actions.Result.FAILED) {
				phase = Phase.RETURN; // cannot clear it: enchant another day
			}
			return TaskStatus.RUNNING;
		}
		ItemStack item = taken;
		if (item != null && !item.isEmpty() && c.backpack().stacks().stream().anyMatch(s -> s == item)) {
			enchant(c, level, at, item);
		}
		phase = Phase.RETURN;
		return TaskStatus.RUNNING;
	}

	/** Works out the table's top offer for the item, as vanilla does, and applies it if the camp can pay. */
	private static void enchant(CompanionEntity c, ServerLevel level, BlockPos at, ItemStack item) {
		MinecraftServer server = level.getServer();
		if (!candidate(item) || c.backpack().count(Items.LAPIS_LAZULI) < LAPIS) {
			return;
		}
		ProgressData data = ProgressData.get(server);
		if (data.enchantSeed() == 0) {
			data.setEnchantSeed(level.getRandom().nextInt());
		}
		int seed = data.enchantSeed();
		int power = Stations.power(level, at);
		RandomSource random = RandomSource.create();
		random.setSeed(seed);
		int[] costs = new int[3];
		for (int i = 0; i < 3; i++) {
			costs[i] = EnchantmentHelper.getEnchantmentCost(random, i, power, item);
			if (costs[i] < i + 1) {
				costs[i] = 0;
			}
		}
		int cost = costs[SLOT];
		int poolLevel = Experience.level(server);
		if (cost <= 0 || poolLevel < cost || poolLevel < LAPIS) {
			return;
		}
		Optional<HolderSet.Named<Enchantment>> offers = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.get(EnchantmentTags.IN_ENCHANTING_TABLE);
		if (offers.isEmpty()) {
			return;
		}
		random.setSeed(seed + SLOT);
		List<EnchantmentInstance> chosen = EnchantmentHelper.selectEnchantment(random, item, cost, offers.get().stream());
		if (chosen.isEmpty() || !Experience.spendLevels(server, LAPIS)) {
			return;
		}
		for (EnchantmentInstance e : chosen) {
			item.enchant(e.enchantment(), e.level());
		}
		c.backpack().remove(s -> s.is(Items.LAPIS_LAZULI), LAPIS);
		data.setEnchantSeed(level.getRandom().nextInt());
		c.backpack().container().setChanged();
		c.swingArm();
		level.playSound(null, at, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
		Speech.say(c, Line.ENCHANTED, item.getHoverName().getString());
		Camp.data(server).addStat("items_enchanted", 1);
	}

	/**
	 * A block the friends set inside the library's ring of air (a planted flower, a torch), which would cut the
	 * shelves' power; null when the ring is clear or this is not the library's table.
	 */
	private static @Nullable BlockPos libraryClutter(ServerLevel level, BlockPos at) {
		CampData data = Camp.data(level.getServer());
		Optional<CampData.Site> site = data.site(Structures.LIBRARY);
		if (site.isEmpty() || !Blueprints.at(site.get(), Blueprints.LIBRARY_TABLE).equals(at)) {
			return null;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				BlockPos p = at.offset(dx, 0, dz);
				BlockState s = level.getBlockState(p);
				if (!s.is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER) && data.isPlacedByFriends(level, p)) {
					return p;
				}
			}
		}
		return null;
	}

	private TaskStatus putBack(CompanionEntity c) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (walk == ChestWalk.State.FAILED || chest.isEmpty()) {
			return TaskStatus.SUCCESS; // the deposit job takes it in later
		}
		ItemStack item = taken;
		if (item != null) {
			Trips.putBack(chest.get(), c, s -> s == item);
		}
		Trips.putBack(chest.get(), c, s -> s.is(Items.LAPIS_LAZULI));
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		chosen = null;
		table = null;
		taken = null;
	}

	@Override
	public int failureCooldown() {
		return 1200;
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}

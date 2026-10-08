package io.github.bradley09roberts.hardcorefriends.progress.work;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.Experience;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.progress.Stations;
import io.github.bradley09roberts.hardcorefriends.progress.Tiers;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Mending worn iron and diamond gear at the friends' own anvil, the builder's job. It works as the anvil does for a
 * player: each iron ingot or diamond mends a quarter of the item's durability, the price in levels is the item's
 * repair cost so far plus one per ingot or diamond, and an item would cost 40 levels or more is "too expensive" and
 * left alone. The camp's experience pool ({@link Experience}) pays the levels, the item's repair cost goes up the
 * vanilla way, and the anvil wears like a player's: a 12% chance each use to become chipped, then damaged, then break
 * (the wear goes through the guard, which allows it only on the friends' own anvil). Iron is only used beyond the
 * plan's iron stock, diamonds only beyond the diamonds the plan keeps back. Gear a player has named is never touched.
 */
public final class AnvilTask implements CompanionTask {
	/** Gear at least this worn is worth mending. */
	private static final double MIN_WEAR = 0.25;
	private static final int TOO_EXPENSIVE = 40;
	private static final float WEAR_CHANCE = 0.12F;

	private enum Phase {
		FETCH,
		ANVIL,
		RETURN
	}

	private Phase phase = Phase.FETCH;
	private @Nullable Item chosen;
	private @Nullable ItemStack taken;
	private @Nullable BlockPos anvil;
	/** Ingots or diamonds taken from the chest for this job (the only ones it uses, and what goes back). */
	private int unitsTaken;

	@Override
	public String id() {
		return "oak.anvil";
	}

	@Override
	public String describe() {
		return "mending gear at the anvil";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || Camp.isNight(level) || Stations.anvil(level) == null) {
			return 0;
		}
		return bestInChest(c) != null ? 40 : 0;
	}

	/** The material that mends this item (iron ingots or diamonds), or empty for anything else. */
	static ItemStack material(ItemStack s) {
		if (s.isValidRepairItem(new ItemStack(Items.IRON_INGOT))) {
			return new ItemStack(Items.IRON_INGOT);
		}
		if (s.isValidRepairItem(new ItemStack(Items.DIAMOND))) {
			return new ItemStack(Items.DIAMOND);
		}
		return ItemStack.EMPTY;
	}

	/** Worn iron or diamond gear, not named by a player. */
	static boolean candidate(ItemStack s) {
		return s.isDamageableItem() && !Tiers.named(s) && s.getDamageValue() >= s.getMaxDamage() * MIN_WEAR && !material(s).isEmpty();
	}

	/** Ingots or diamonds to fully mend it: one per quarter of its durability, as the anvil counts them. */
	static int unitsFor(ItemStack s) {
		int quarter = Math.max(1, s.getMaxDamage() / 4);
		return Math.min(4, (s.getDamageValue() + quarter - 1) / quarter);
	}

	/** The price in levels of mending it with this many units. */
	static int price(ItemStack s, int units) {
		return s.getOrDefault(DataComponents.REPAIR_COST, 0) + units;
	}

	/** Ingots or diamonds the camp may spend on mending (above the plan's own needs). */
	private static int spendable(MinecraftServer server, CompanionEntity c, Item material) {
		int inChest = Trips.inChest(c, st -> st.is(material)) + c.backpack().count(material);
		int kept = material == Items.DIAMOND ? ProgressPlan.diamondReserve(server) : ProgressPlan.IRON_STOCK;
		return Math.max(0, inChest - kept);
	}

	/** The kind of the most valuable mendable item in the chest the camp can afford, or null. */
	private static @Nullable Item bestInChest(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<Container> chest = SupplyChest.of(level);
		if (chest.isEmpty()) {
			return null;
		}
		MinecraftServer server = level.getServer();
		int poolLevel = Experience.level(server);
		ItemStack best = ItemStack.EMPTY;
		Container container = chest.get();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (!candidate(s) || !best.isEmpty() && Tiers.rank(s) <= Tiers.rank(best)) {
				continue;
			}
			int units = Math.min(unitsFor(s), spendable(server, c, material(s).getItem()));
			int price = price(s, units);
			if (units > 0 && price < TOO_EXPENSIVE && price <= poolLevel) {
				best = s;
			}
		}
		return best.isEmpty() ? null : best.getItem();
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		chosen = bestInChest(c);
		anvil = Stations.anvil(level);
		taken = null;
		unitsTaken = 0;
		phase = Phase.FETCH;
		return chosen != null && anvil != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Item item = chosen;
		BlockPos at = anvil;
		if (item == null || at == null) {
			return TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c, item);
			case ANVIL -> atAnvil(c, at);
			case RETURN -> putBack(c);
		};
	}

	private TaskStatus fetch(CompanionEntity c, Item item) {
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
		Set<ItemStack> own = Collections.newSetFromMap(new IdentityHashMap<>());
		own.addAll(c.backpack().stacks());
		Trips.take(chest.get(), c, s -> s.is(item) && candidate(s), 1);
		for (ItemStack s : c.backpack().stacks()) {
			if (!own.contains(s) && s.is(item) && candidate(s)) {
				taken = s;
			}
		}
		ItemStack it = taken;
		if (it == null) {
			return TaskStatus.FAILURE;
		}
		Item material = material(it).getItem();
		int units = Math.min(unitsFor(it), spendable(((ServerLevel) c.level()).getServer(), c, material));
		unitsTaken = Trips.take(chest.get(), c, s -> s.is(material), units);
		phase = Phase.ANVIL;
		return TaskStatus.RUNNING;
	}

	private TaskStatus atAnvil(CompanionEntity c, BlockPos at) {
		ServerLevel level = (ServerLevel) c.level();
		if (!Trips.reach(c, at)) {
			if (c.actions().isStuck()) {
				phase = Phase.RETURN;
			}
			return TaskStatus.RUNNING;
		}
		ItemStack item = taken;
		if (item != null && c.backpack().stacks().stream().anyMatch(s -> s == item) && level.getBlockState(at).is(net.minecraft.tags.BlockTags.ANVIL)) {
			unitsTaken -= repair(c, level, at, item, unitsTaken);
		}
		phase = Phase.RETURN;
		return TaskStatus.RUNNING;
	}

	/**
	 * Mends the item as the anvil would with up to {@code available} ingots or diamonds, pays from the camp's pool and
	 * wears the anvil. Returns how many were used.
	 */
	private static int repair(CompanionEntity c, ServerLevel level, BlockPos at, ItemStack item, int available) {
		MinecraftServer server = level.getServer();
		Item material = material(item).getItem();
		int carried = Math.min(available, c.backpack().count(material));
		int used = 0;
		int damage = item.getDamageValue();
		int repair = Math.min(damage, item.getMaxDamage() / 4);
		while (repair > 0 && used < carried) {
			damage -= repair;
			used++;
			repair = Math.min(damage, item.getMaxDamage() / 4);
		}
		int price = price(item, used);
		if (used == 0 || price >= TOO_EXPENSIVE || Experience.level(server) < price || !Experience.spendLevels(server, price)) {
			return 0;
		}
		item.setDamageValue(damage);
		item.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(item.getOrDefault(DataComponents.REPAIR_COST, 0)));
		c.backpack().remove(s -> s.is(material), used);
		c.backpack().container().setChanged();
		c.swingArm();
		Speech.say(c, Line.REPAIRED, item.getHoverName().getString());
		Camp.data(server).addStat("items_repaired", 1);
		wear(c, level, at);
		return used;
	}

	/** The anvil's own wear: 12% a use, through the guard's rule for the friends' own anvil. */
	private static void wear(CompanionEntity c, ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (level.getRandom().nextFloat() >= WEAR_CHANCE) {
			level.levelEvent(LevelEvent.SOUND_ANVIL_USED, at, 0);
			return;
		}
		BlockState worn = AnvilBlock.damage(state);
		BlockState next = worn == null ? Blocks.AIR.defaultBlockState() : worn;
		if (WorldEditGuard.transformBlock(c, at, next, WorldEditGuard.Reason.BUILD)) {
			level.levelEvent(worn == null ? LevelEvent.SOUND_ANVIL_BROKEN : LevelEvent.SOUND_ANVIL_USED, at, 0);
		}
	}

	private TaskStatus putBack(CompanionEntity c) {
		ChestWalk.State walk = ChestWalk.tick(c);
		if (walk == ChestWalk.State.WALKING) {
			return TaskStatus.RUNNING;
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (walk == ChestWalk.State.FAILED || chest.isEmpty()) {
			return TaskStatus.SUCCESS;
		}
		ItemStack item = taken;
		if (item != null) {
			Trips.putBack(chest.get(), c, s -> s == item);
			Item material = material(item).getItem();
			if (material != Items.AIR && unitsTaken > 0) {
				SupplyChest.deposit(c.backpack(), chest.get(), s -> s.is(material), unitsTaken);
			}
		}
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		chosen = null;
		taken = null;
		anvil = null;
		unitsTaken = 0;
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

package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * The camp's smith: makes the gear {@link Smithing} says is needed next, one piece (or one batch of arrows) a trip.
 * The smith fetches exactly the materials from the supply chest, crafts at the camp's crafting table (near the chest
 * or the camp centre), and brings the gear and anything left over back to the chest, where the friends' gear job hands
 * it out. Only one friend smiths at a time. Anyone can do it, in the main part of the day (it waits for the morning at
 * night); shields come first and score highest. Counts are kept so nothing the smith carried for their own work is
 * used up or put away. A smith called away after fetching (by nightfall or a fight) remembers what they still owe the
 * chest, and puts it back first thing next time.
 */
public final class SmithTask implements CompanionTask {
	private static final double CHEST_REACH = 2.5;
	private static final int TABLE_SEARCH = 8;
	private static final int TABLE_RESCAN = 600;
	/** A smith not seen at work for this long has let go of the job. */
	private static final int CLAIM_TICKS = 40;
	/** What an interrupted smith still owes the chest (item id to count), kept with the friend. */
	private static final String OWED = "combat.smith_owed";
	/** Putting back what an interrupted job fetched comes before other day work of its kind. */
	private static final double SETTLE_SCORE = 45;

	private static @Nullable UUID smith;
	private static long smithSeen = -100_000;

	private enum Phase {
		/** Taking back to the chest what an interrupted job fetched. */
		SETTLE,
		FETCH,
		CRAFT,
		RETURN
	}

	private Smithing.@Nullable Order order;
	private Phase phase = Phase.FETCH;
	private @Nullable BlockPos chestPos;
	private @Nullable BlockPos table;
	private @Nullable BlockPos cachedTable;
	private long tableCheckedAt = -100_000;
	/** The kinds of things fetched, and how many of each the smith carried before. */
	private final List<Predicate<ItemStack>> kinds = new ArrayList<>();
	private int[] before = new int[0];
	private int productBefore;
	private boolean made;

	@Override
	public String id() {
		return "combat.smith";
	}

	@Override
	public String describe() {
		return "making gear at the crafting table";
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.mode() != CompanionMode.WORK) {
			return 0;
		}
		if (owes(c)) {
			return SETTLE_SCORE;
		}
		if (c.backpack().freeSlots() < 3) {
			return 0;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (claimedByOther(c, level)) {
			return 0;
		}
		Smithing.Order next = Smithing.cachedOrder(level);
		if (next == null || findTable(c) == null) {
			return 0;
		}
		return next.score();
	}

	private static boolean claimedByOther(CompanionEntity c, ServerLevel level) {
		UUID holder = smith;
		long since = level.getGameTime() - smithSeen;
		return holder != null && !holder.equals(c.getUUID()) && since >= 0 && since < CLAIM_TICKS;
	}

	/** Forgets who is smithing: a server stopping (the next world starts with nobody at the table). */
	static void clearClaim() {
		smith = null;
		smithSeen = -100_000;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<Container> chest = SupplyChest.of(level);
		chestPos = Camp.data(level.getServer()).chestPos().orElse(null);
		if (chest.isPresent() && chestPos != null && owes(c)) {
			order = null;
			phase = Phase.SETTLE; // what the last, interrupted job fetched goes back first
			return true;
		}
		table = findTable(c);
		if (chest.isEmpty() || chestPos == null || table == null || claimedByOther(c, level)) {
			return false;
		}
		order = Smithing.plan(level, chest.get());
		Smithing.Order o = order;
		if (o == null) {
			return false;
		}
		kinds.clear();
		for (Smithing.Material m : o.materials()) {
			kinds.add(m.match());
		}
		kinds.add(Smithing.PLANKS);
		kinds.add(Smithing.LOGS);
		kinds.add(Smithing.STICKS);
		before = new int[kinds.size()];
		for (int i = 0; i < kinds.size(); i++) {
			before[i] = c.backpack().count(kinds.get(i));
		}
		productBefore = c.backpack().count(o.item());
		made = false;
		phase = Phase.FETCH;
		smith = c.getUUID();
		smithSeen = level.getGameTime();
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (phase == Phase.SETTLE) {
			return settle(c, level);
		}
		Smithing.Order o = order;
		if (o == null || chestPos == null || table == null) {
			return TaskStatus.FAILURE;
		}
		smith = c.getUUID();
		smithSeen = level.getGameTime();
		switch (phase) {
			case FETCH -> {
				if (!c.actions().walkTo(chestPos, CHEST_REACH)) {
					return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
				}
				Optional<Container> chest = SupplyChest.at(level, chestPos);
				if (chest.isEmpty()) {
					return TaskStatus.FAILURE;
				}
				c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
				fetch(c, o, chest.get());
				c.swingArm();
				phase = fetchedEnough(c, o) ? Phase.CRAFT : Phase.RETURN;
			}
			case CRAFT -> {
				if (!level.getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
					cachedTable = null;
					phase = Phase.RETURN;
					return TaskStatus.RUNNING;
				}
				if (!c.actions().walkTo(table, 2.0)) {
					if (c.actions().isStuck()) {
						phase = Phase.RETURN;
					}
					return TaskStatus.RUNNING;
				}
				c.getLookControl().setLookAt(Vec3.atCenterOf(table));
				craft(c, o);
				phase = Phase.RETURN;
			}
			case RETURN -> {
				if (!c.actions().walkTo(chestPos, CHEST_REACH)) {
					return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
				}
				Optional<Container> chest = SupplyChest.at(level, chestPos);
				if (chest.isEmpty()) {
					return TaskStatus.FAILURE;
				}
				c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
				putBack(c, o, chest.get());
				c.swingArm();
				return made ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Takes the order's materials, planks and sticks out of the chest (never more than the order needs). */
	private static void fetch(CompanionEntity c, Smithing.Order o, Container chest) {
		Backpack bp = c.backpack();
		for (Smithing.Material m : o.materials()) {
			SupplyChest.withdraw(chest, bp, m.match(), m.count());
		}
		int sticks = o.sticks() > 0 ? SupplyChest.withdraw(chest, bp, Smithing.STICKS, o.sticks()) : 0;
		int planks = o.planks() + Smithing.planksForSticks(o.sticks() - sticks);
		if (planks > 0) {
			int got = SupplyChest.withdraw(chest, bp, Smithing.PLANKS, planks);
			if (got < planks) {
				SupplyChest.withdraw(chest, bp, Smithing.LOGS, (planks - got + 3) / 4);
			}
		}
	}

	/** Everything the order needs was fetched (on top of what the smith carried before). */
	private boolean fetchedEnough(CompanionEntity c, Smithing.Order o) {
		for (int i = 0; i < o.materials().size(); i++) {
			if (gained(c, i) < o.materials().get(i).count()) {
				return false;
			}
		}
		int base = o.materials().size();
		int sticks = gained(c, base + 2);
		int wood = gained(c, base) + 4 * gained(c, base + 1);
		return wood >= o.planks() + Smithing.planksForSticks(o.sticks() - sticks);
	}

	private int gained(CompanionEntity c, int kind) {
		return c.backpack().count(kinds.get(kind)) - before[kind];
	}

	/** Turns the fetched logs into planks, then crafts the order at the table. */
	private void craft(CompanionEntity c, Smithing.Order o) {
		Backpack bp = c.backpack();
		int base = o.materials().size();
		int logs = Math.max(0, gained(c, base + 1));
		if (logs > 0) {
			Crafting.ensurePlanks(bp, bp.count(Smithing.PLANKS) + 4 * logs);
		}
		Crafting.ensure(c, o.item(), productBefore + o.count());
		int count = bp.count(o.item()) - productBefore;
		if (count > 0) {
			made = true;
			c.swingArm();
			ServerLevel level = (ServerLevel) c.level();
			Camp.data(level.getServer()).addStat("gear_made", count);
			Speech.say(c, Line.MADE_GEAR, new ItemStack(o.item()).getHoverName().getString());
		}
	}

	/** The gear made, and whatever was fetched and not used, go into the chest; the smith's own things stay. */
	private void putBack(CompanionEntity c, Smithing.Order o, Container chest) {
		Backpack bp = c.backpack();
		int product = bp.count(o.item()) - productBefore;
		if (product > 0) {
			SupplyChest.deposit(bp, chest, s -> s.is(o.item()), product);
		}
		for (int i = 0; i < kinds.size(); i++) {
			int spare = gained(c, i);
			if (spare > 0) {
				SupplyChest.deposit(bp, chest, kinds.get(i), spare);
			}
		}
	}

	/** A crafting table near the supply chest or the camp centre; rescanned now and then. */
	private @Nullable BlockPos findTable(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		long now = level.getGameTime();
		BlockPos known = cachedTable;
		if (known != null && now - tableCheckedAt < TABLE_RESCAN && level.isLoaded(known)
			&& level.getBlockState(known).is(Blocks.CRAFTING_TABLE)) {
			return known;
		}
		if (known == null && now - tableCheckedAt < TABLE_RESCAN / 3 && now >= tableCheckedAt) {
			return null; // none found lately: look again in a while
		}
		tableCheckedAt = now;
		cachedTable = null;
		Optional<BlockPos> chest = Camp.data(level.getServer()).chestPos();
		if (chest.isPresent() && Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			cachedTable = scan(level, chest.get());
		}
		if (cachedTable == null) {
			cachedTable = scan(level, c.homePos());
		}
		return cachedTable;
	}

	private static @Nullable BlockPos scan(ServerLevel level, BlockPos centre) {
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-TABLE_SEARCH, -3, -TABLE_SEARCH), centre.offset(TABLE_SEARCH, 3, TABLE_SEARCH))) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
				return p.immutable();
			}
		}
		return null;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (c.getUUID().equals(smith)) {
			smith = null;
		}
		Smithing.Order o = order;
		if (o != null && (phase == Phase.CRAFT || phase == Phase.RETURN)) {
			owe(c, o); // called away with the materials (or the gear) still in hand: they go back next time
		}
		order = null;
		table = null;
		phase = Phase.FETCH;
		Smithing.clear(); // the next order is worked out afresh
	}

	/** True when an interrupted job left this friend owing the chest what they fetched. */
	private static boolean owes(CompanionEntity c) {
		return c.extra().contains(OWED);
	}

	/**
	 * Remembers, with the friend, what this job fetched (or made) and has not put back yet, by item, so it is not
	 * mistaken for their own things next time.
	 */
	private void owe(CompanionEntity c, Smithing.Order o) {
		CompoundTag owed = c.extra().getCompoundOrEmpty(OWED);
		Backpack bp = c.backpack();
		int product = bp.count(o.item()) - productBefore;
		if (product > 0) {
			addOwed(owed, o.item(), product);
		}
		for (int i = 0; i < kinds.size(); i++) {
			int spare = gained(c, i);
			for (ItemStack s : bp.stacks()) {
				if (spare <= 0) {
					break;
				}
				if (kinds.get(i).test(s)) {
					int n = Math.min(spare, s.getCount());
					addOwed(owed, s.getItem(), n);
					spare -= n;
				}
			}
		}
		if (!owed.isEmpty()) {
			c.extra().put(OWED, owed);
		}
	}

	private static void addOwed(CompoundTag owed, Item item, int count) {
		String key = BuiltInRegistries.ITEM.getKey(item).toString();
		owed.putInt(key, owed.getIntOr(key, 0) + count);
	}

	/** Walks to the chest and puts back what an interrupted job fetched (whatever of it is still carried). */
	private TaskStatus settle(CompanionEntity c, ServerLevel level) {
		if (chestPos == null) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(chestPos, CHEST_REACH)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		Optional<Container> chest = SupplyChest.at(level, chestPos);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
		CompoundTag owed = c.extra().getCompoundOrEmpty(OWED);
		Backpack bp = c.backpack();
		for (String key : owed.keySet()) {
			int count = owed.getIntOr(key, 0);
			Item item = null;
			for (ItemStack s : bp.stacks()) {
				if (BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(key)) {
					item = s.getItem();
					break;
				}
			}
			if (item != null && count > 0) {
				Item kind = item;
				SupplyChest.deposit(bp, chest.get(), s -> s.is(kind), count);
			}
		}
		c.extra().remove(OWED); // anything the chest had no room for goes with the next deposit trip
		c.swingArm();
		return TaskStatus.SUCCESS;
	}

	@Override
	public int failureCooldown() {
		return 1200;
	}

	@Override
	public int successCooldown() {
		return 100;
	}

	@Override
	public int maxTicks() {
		return 20 * 90;
	}
}

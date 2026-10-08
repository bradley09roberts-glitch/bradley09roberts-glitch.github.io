package io.github.bradley09roberts.hardcorefriends.ai.role.forage;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.SpecialityTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.ReturnHomeTask;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.Backpack;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * When the current build is short of wood, stone or dirt and this friend carries at least 16 of what is missing (logs
 * or planks, cobblestone, dirt), they take it straight to the builder if within 48 blocks, or else to the supply
 * chest. The builder is whoever is building right now, else Oak while he is working, else whoever built last; the
 * builder themself never delivers (they carry what they need straight to the site).
 */
public final class DeliverToBuilderTask implements CompanionTask {
	private static final int MIN_LOAD = 16;
	private static final double BUILDER_RANGE = 48;
	/** The builder's job, whose runner is the one to bring materials to. */
	private static final String BUILD_JOB = "oak.build";
	private static final int TIMEOUT = 20 * 60;
	private static final double DELIVERY_SCORE = 120;

	private @Nullable CompanionEntity builder;
	private Predicate<ItemStack> load = s -> false;
	private int ticks;

	@Override
	public String id() {
		return "rowan.deliver";
	}

	@Override
	public String describe() {
		CompanionEntity to = builder;
		return to != null ? "delivering materials to " + to.friendId().displayName() : "taking building materials to the chest";
	}

	@Override
	public int successCooldown() {
		return 200;
	}

	/** What this friend carries that the builder's shortage asks for, as one item filter. */
	static Predicate<ItemStack> wanted(Backpack backpack, Map<CampNeeds.Need, Integer> shortage) {
		Predicate<ItemStack> result = s -> false;
		for (CampNeeds.Need need : shortage.keySet()) {
			Predicate<ItemStack> kind = switch (need) {
				case WOOD -> s -> s.is(ItemTags.LOGS) || s.is(ItemTags.PLANKS);
				case STONE -> s -> s.is(Items.COBBLESTONE);
				case DIRT -> s -> s.is(Items.DIRT);
				default -> null;
			};
			if (kind != null && backpack.count(kind) >= MIN_LOAD) {
				result = result.or(kind);
			}
		}
		return result;
	}

	@Override
	public double score(CompanionEntity c) {
		Map<CampNeeds.Need, Integer> shortage = CampNeeds.buildShortage();
		if (shortage.isEmpty() || c.backpack().count(wanted(c.backpack(), shortage)) < MIN_LOAD) {
			return 0;
		}
		Optional<CompanionEntity> who = builder();
		if (who.isPresent() && who.get() == c) {
			return 0; // the builder carries it to the site themself
		}
		// The builder is stuck without this load, so handing it over beats any gathering (felling scores at most
		// 50 x 2.2 = 110 when wood is short and Sage has made it the team's focus).
		return findBuilder(c) != null || SupplyChest.of((ServerLevel) c.level()).isPresent() ? DELIVERY_SCORE : 0;
	}

	/** Whoever is building: the friend on the build job now, else Oak if he is working, else whoever built last. */
	public static Optional<CompanionEntity> builder() {
		Optional<CompanionEntity> now = SpecialityTask.runner(BUILD_JOB);
		if (now.isPresent()) {
			return now;
		}
		Optional<CompanionEntity> oak = Companions.find(FriendId.OAK).filter(o -> o.mode() == CompanionMode.WORK);
		return oak.isPresent() ? oak : SpecialityTask.lastRunner(BUILD_JOB);
	}

	/** The builder, if within reach to hand over to. */
	private static @Nullable CompanionEntity findBuilder(CompanionEntity c) {
		Optional<CompanionEntity> found = builder();
		if (found.isEmpty() || found.get() == c || found.get().level() != c.level() || found.get().distanceTo(c) > BUILDER_RANGE
			|| ReturnHomeTask.sendsHome(c, found.get().blockPosition())) {
			return null; // after dark, a builder away from camp is skipped and the load goes to the supply chest
		}
		return found.get();
	}

	@Override
	public boolean start(CompanionEntity c) {
		load = wanted(c.backpack(), CampNeeds.buildShortage());
		builder = findBuilder(c);
		ticks = 0;
		return c.backpack().count(load) > 0 && (builder != null || SupplyChest.of((ServerLevel) c.level()).isPresent());
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (++ticks > TIMEOUT) {
			return TaskStatus.FAILURE;
		}
		CompanionEntity to = builder;
		if (to != null) {
			if (!to.isAlive() || to.level() != level || to.distanceTo(c) > BUILDER_RANGE + 16) {
				builder = null;
				return TaskStatus.RUNNING;
			}
			if (!c.actions().walkToEntity(to, 2.5)) {
				return TaskStatus.RUNNING;
			}
			int given = handOver(c.backpack(), to.backpack(), load);
			if (given == 0) {
				builder = null; // the builder's hands are full: use the chest instead
				return TaskStatus.RUNNING;
			}
			c.swingArm();
			Speech.say(c, Line.SHARE, to.friendId().displayName(), given + " building materials");
			Unity.add(level, Unity.HANDOFF, 2, 40);
			Camp.data(level.getServer()).addStat("materials_delivered", given);
			return TaskStatus.SUCCESS;
		}
		Optional<BlockPos> chestPos = Camp.data(level.getServer()).chestPos();
		Optional<Container> chest = SupplyChest.of(level);
		if (chestPos.isEmpty() || chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(chestPos.get(), 2.5)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		int stored = SupplyChest.deposit(c.backpack(), chest.get(), load, Integer.MAX_VALUE);
		if (stored == 0) {
			return TaskStatus.FAILURE;
		}
		Speech.say(c, Line.DEPOSIT);
		Unity.add(level, Unity.DELIVERY, 1, 60);
		Camp.data(level.getServer()).addStat("materials_delivered", stored);
		return TaskStatus.SUCCESS;
	}

	/** Moves every matching stack that fits from one backpack to another. Returns the number of items moved. */
	static int handOver(Backpack from, Backpack to, Predicate<ItemStack> filter) {
		int moved = 0;
		for (int i = 0; i < Backpack.MAX_SLOTS; i++) {
			ItemStack stack = from.get(i);
			if (stack.isEmpty() || !filter.test(stack)) {
				continue;
			}
			ItemStack left = to.insert(stack.copy());
			int given = stack.getCount() - left.getCount();
			if (given > 0) {
				stack.shrink(given);
				moved += given;
				if (stack.isEmpty()) {
					from.container().setItem(i, ItemStack.EMPTY);
				}
			}
		}
		if (moved > 0) {
			from.container().setChanged();
		}
		return moved;
	}

	@Override
	public void stop(CompanionEntity c) {
		builder = null;
	}
}

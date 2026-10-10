package io.github.bradley09roberts.hardcorefriends.market;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The village farmer's work at the farm shed's composter (the friends' own): the camp's surplus seeds, saplings and
 * leaves (only what is left over beyond what the farm and the gardeners keep) go in one at a time, each with the
 * game's own chance of adding a layer; a full composter gives a piece of bone meal, which goes to the supply chest for
 * the farm. Every change to the composter goes through the edit guard (the friends' own composter, filled or emptied).
 */
final class CompostTask extends TradeJob {
	static final String ID = "market.compost";
	private static final int PER_RUN = 16;
	private static final int MIN_SURPLUS = 8;

	/** What may be composted, and how many the supply chest keeps back. */
	private record Compost(Predicate<ItemStack> match, int keep) {
	}

	private static final List<Compost> COMPOST = List.of(
		new Compost(s -> s.is(Items.WHEAT_SEEDS), 64),
		new Compost(s -> s.is(Items.BEETROOT_SEEDS), 32),
		new Compost(s -> s.is(Items.MELON_SEEDS) || s.is(Items.PUMPKIN_SEEDS), 16),
		new Compost(s -> s.is(ItemTags.SAPLINGS), 32),
		new Compost(s -> s.is(ItemTags.LEAVES), 0));

	private enum Phase {
		FETCH,
		FILL,
		DELIVER
	}

	private Phase phase = Phase.FETCH;
	private int ticks;
	private int fetched;
	private int boneMeal;

	CompostTask() {
		super(ID, Set.of(), Trade.FARMER);
	}

	@Override
	public String describe() {
		return phase == Phase.DELIVER ? "taking bone meal to the chest" : "composting for the fields";
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (w == null || !workingHours(level) || c.backpack().freeSlots() < 2 || Stores.supplyPos(level).isEmpty()) {
			return 0;
		}
		BlockState s = composter(level, w);
		if (s == null) {
			return 0;
		}
		int fill = s.getValue(ComposterBlock.LEVEL);
		if (fill >= ComposterBlock.READY) {
			return 46;
		}
		if (fill == ComposterBlock.READY - 1) {
			return 30; // ripening: looked in on, so it never stands half-ripe
		}
		return surplus(level) >= MIN_SURPLUS ? 42 : 0;
	}

	/** The workplace's composter, if it is the friends' own. */
	private static @Nullable BlockState composter(ServerLevel level, Workplace w) {
		BlockPos at = w.job();
		if (!level.isLoaded(at)) {
			return null;
		}
		BlockState s = level.getBlockState(at);
		return s.is(Blocks.COMPOSTER) && Camp.data(level.getServer()).isPlacedByFriends(level, at) ? s : null;
	}

	private static int surplus(ServerLevel level) {
		int n = 0;
		for (Compost k : COMPOST) {
			n += Math.max(0, Stores.supplyCount(level, k.match()) - k.keep());
		}
		return n;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Workplace w = workplace(c, holding(c));
		BlockState s = w == null ? null : composter(level, w);
		if (s == null) {
			return false;
		}
		phase = s.getValue(ComposterBlock.LEVEL) >= ComposterBlock.READY - 1 ? Phase.FILL : Phase.FETCH;
		ticks = 0;
		fetched = 0;
		boneMeal = 0;
		Speech.say(c, Line.TRADE_WORK, "composting for the fields");
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Workplace w = workplace(c, holding(c));
		if (w == null) {
			return boneMeal > 0 ? toDeliver() : TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c, level);
			case FILL -> fill(c, level, w);
			case DELIVER -> deliver(c, level);
		};
	}

	private TaskStatus fetch(CompanionEntity c, ServerLevel level) {
		Optional<BlockPos> supply = Stores.supplyPos(level);
		if (supply.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		switch (Stores.walk(c, supply.get(), 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		for (Compost k : COMPOST) {
			int spare = Math.max(0, Stores.supplyCount(level, k.match()) - k.keep());
			int want = Math.min(spare, PER_RUN - fetched);
			if (want > 0) {
				int before = c.backpack().count(k.match());
				Stores.withdraw(c, supply.get(), k.match(), want);
				fetched += c.backpack().count(k.match()) - before;
			}
		}
		if (fetched <= 0) {
			return TaskStatus.FAILURE;
		}
		phase = Phase.FILL;
		ticks = 0;
		return TaskStatus.RUNNING;
	}

	private TaskStatus fill(CompanionEntity c, ServerLevel level, Workplace w) {
		BlockPos at = w.job();
		switch (Stores.walk(c, at, 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return boneMeal > 0 ? toDeliver() : TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		if (++ticks % 10 != 0) {
			return TaskStatus.RUNNING;
		}
		BlockState s = composter(level, w);
		if (s == null) {
			return boneMeal > 0 ? toDeliver() : TaskStatus.FAILURE;
		}
		int fill = s.getValue(ComposterBlock.LEVEL);
		if (fill >= ComposterBlock.READY) {
			BlockState empty = s.setValue(ComposterBlock.LEVEL, 0);
			if (!WorldEditGuard.canTransform(c, at, empty, WorldEditGuard.Reason.FARM).allowed()) {
				return ticks > 20 * 30 ? TaskStatus.FAILURE : TaskStatus.RUNNING; // someone in the way: wait a little
			}
			if (c.actions().transform(at, empty, WorldEditGuard.Reason.FARM, null)) {
				Stores.give(c, new ItemStack(Items.BONE_MEAL));
				boneMeal++;
				level.playSound(null, at, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return fetched > 0 ? TaskStatus.RUNNING : toDeliver();
		}
		if (fill == ComposterBlock.READY - 1) {
			if (ticks == 10) {
				level.scheduleTick(at, s.getBlock(), 20); // as the game does: it ripens into bone meal a moment later
			}
			return ticks > 20 * 10 ? (boneMeal > 0 ? toDeliver() : TaskStatus.SUCCESS) : TaskStatus.RUNNING;
		}
		if (fetched <= 0) {
			return boneMeal > 0 ? toDeliver() : TaskStatus.SUCCESS;
		}
		if (!WorldEditGuard.canTransform(c, at, s.setValue(ComposterBlock.LEVEL, fill + 1), WorldEditGuard.Reason.FARM).allowed()) {
			return ticks > 20 * 30 ? TaskStatus.FAILURE : TaskStatus.RUNNING; // the seeds wait in the backpack, not wasted
		}
		ItemStack item = takeCompost(c);
		if (item.isEmpty()) {
			fetched = 0;
			return boneMeal > 0 ? toDeliver() : TaskStatus.SUCCESS;
		}
		fetched--;
		c.swingArm();
		int layers = layers(level, at, s, c, item);
		boolean added = false;
		if (layers > 0) {
			int next = Math.clamp(fill + layers, 0, 7);
			added = c.actions().transform(at, s.setValue(ComposterBlock.LEVEL, next), WorldEditGuard.Reason.FARM, null);
			if (added && next == 7) {
				level.scheduleTick(at, s.getBlock(), 20); // as the game does: it ripens into bone meal a moment later
			}
		}
		level.playSound(null, at, added ? SoundEvents.COMPOSTER_FILL_SUCCESS : SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.COMPOSTER, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.0);
		return TaskStatus.RUNNING;
	}

	/** One compostable item out of the backpack (only the kinds fetched for it). */
	private static ItemStack takeCompost(CompanionEntity c) {
		for (Compost k : COMPOST) {
			ItemStack one = c.backpack().take(k.match(), 1);
			if (!one.isEmpty()) {
				return one;
			}
		}
		return ItemStack.EMPTY;
	}

	/** The layers an item adds, by the game's own roll for it. */
	private static int layers(ServerLevel level, BlockPos at, BlockState state, CompanionEntity c, ItemStack item) {
		Compostable compostable = item.get(DataComponents.COMPOSTABLE);
		if (compostable == null) {
			return 0;
		}
		LootContext context = new LootContext.Builder(new LootParams.Builder(level)
			.withParameter(LootContextParams.BLOCK_STATE, state)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
			.withOptionalParameter(LootContextParams.INTERACTING_ENTITY, c)
			.create(LootContextParamSets.BLOCK_INTERACT)).create(Optional.empty());
		return compostable.layers().get(context, 0);
	}

	private TaskStatus toDeliver() {
		phase = Phase.DELIVER;
		return TaskStatus.RUNNING;
	}

	private TaskStatus deliver(CompanionEntity c, ServerLevel level) {
		Optional<BlockPos> supply = Stores.supplyPos(level);
		if (supply.isEmpty() || boneMeal <= 0) {
			return TaskStatus.SUCCESS;
		}
		switch (Stores.walk(c, supply.get(), 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.SUCCESS;
			}
			case ARRIVED -> {
			}
		}
		Stores.deposit(c, supply.get(), s -> s.is(Items.BONE_MEAL), boneMeal);
		Camp.data(level.getServer()).addStat("bone_meal_composted", boneMeal);
		boneMeal = 0;
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		phase = Phase.FETCH;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}

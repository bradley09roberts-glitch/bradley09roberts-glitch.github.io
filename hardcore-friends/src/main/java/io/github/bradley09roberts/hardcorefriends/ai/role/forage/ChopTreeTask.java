package io.github.bradley09roberts.hardcorefriends.ai.role.forage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.EditSteps;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.TreeFinder;
import io.github.bradley09roberts.hardcorefriends.world.TreeFinder.Tree;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * By day, Rowan fells the nearest natural tree outside the camp but inside the gathering ring (never the camp's own
 * trees, and never log walls or other built logs), cutting its logs bottom-up with her axe, and replants a sapling
 * on the stump's soil if she carries one, preferably of the same kind. Once she has cut into a tree, the logs still
 * standing are kept in camp memory; called away part-way through (danger, a full backpack, dusk, a restart), she
 * finishes that tree first next time, so no trunk is left hanging in the air.
 */
public final class ChopTreeTask implements CompanionTask {
	private static final int WALK_TIMEOUT = 600;
	private static final int LOG_TIMEOUT = 200;

	private enum Phase {
		WALK,
		CHOP,
		REPLANT
	}

	private final ForageContext forage;
	private @Nullable Tree tree;
	private final Deque<BlockPos> logs = new ArrayDeque<>();
	private Phase phase = Phase.WALK;
	private int phaseTicks;
	private int logTicks;
	private int cut;
	private boolean resumed;
	private @Nullable Block logBlock;

	public ChopTreeTask(ForageContext forage) {
		this.forage = forage;
	}

	@Override
	public String id() {
		return "rowan.chop";
	}

	@Override
	public String describe() {
		return "felling a tree";
	}

	@Override
	public int maxTicks() {
		return 20 * 150;
	}

	@Override
	public double score(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		// Trees are always outside the camp, so dusk counts as night: the return home would only call her back.
		if (Camp.isNight(level) || Camp.isDusk(level) || !FriendsConfig.get().allowTreeFelling) {
			return 0;
		}
		if (c.backpack().freeSlots() == 0 && !c.backpack().canFit(new ItemStack(Items.OAK_LOG, 6))) {
			return 0;
		}
		return forage.tree(c) == null ? 0 : 50 * CampNeeds.weight(CampNeeds.Need.WOOD);
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Tree known = forage.tree(c);
		if (known == null) {
			return false;
		}
		Tree chosen;
		if (forage.isUnfinished(known)) {
			// The rest of a tree she already cut into: checked when she started it, so no new analysis (it would
			// fail, the base log being gone).
			chosen = known;
			logBlock = level.getBlockState(known.logs().getFirst()).getBlock();
			resumed = true;
		} else {
			Optional<Tree> checked = TreeFinder.analyse(level, known.base());
			if (checked.isEmpty() || !checked.get().base().equals(known.base()) || !ForageContext.mayFell(c, known.base())) {
				forage.skip(known.base(), level.getGameTime());
				return false;
			}
			chosen = checked.get();
			logBlock = level.getBlockState(chosen.base()).getBlock();
			resumed = false;
		}
		tree = chosen;
		c.approveLogs(chosen.logs());
		logs.clear();
		logs.addAll(chosen.logs());
		phase = Phase.WALK;
		phaseTicks = 0;
		logTicks = 0;
		cut = 0;
		Speech.say(c, Line.WORK_START, describe());
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Tree t = tree;
		if (t == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		Actions actions = c.actions();
		phaseTicks++;
		switch (phase) {
			case WALK -> {
				if (Camp.isNight(level) || Camp.isDusk(level)) {
					return TaskStatus.FAILURE; // no long walks into the dark; a tree already started is finished
				}
				if (actions.walkTo(t.base(), 1.5) || actions.canReach(t.base()) && phaseTicks > 20) {
					actions.stopWalking();
					phase = Phase.CHOP;
				} else if (actions.isStuck() || phaseTicks > WALK_TIMEOUT) {
					forage.skip(t.base(), level.getGameTime());
					return TaskStatus.FAILURE;
				}
			}
			case CHOP -> {
				while (!logs.isEmpty() && !level.getBlockState(logs.peekFirst()).is(BlockTags.LOGS)) {
					logs.pollFirst();
					logTicks = 0;
				}
				BlockPos log = logs.peekFirst();
				if (log == null) {
					phase = Phase.REPLANT;
					phaseTicks = 0;
					return TaskStatus.RUNNING;
				}
				if (++logTicks > LOG_TIMEOUT) {
					logs.pollFirst(); // out of reach after all: leave it
					logTicks = 0;
					return TaskStatus.RUNNING;
				}
				if (!actions.canReach(log)) {
					// Step into the stump's place, right under the trunk.
					actions.walkTo(t.base(), 0.5);
					return TaskStatus.RUNNING;
				}
				actions.stopWalking();
				switch (actions.mine(log, Reason.GATHER_WOOD)) {
					case DONE -> {
						cut++;
						logs.pollFirst();
						logTicks = 0;
						if (!logs.isEmpty()) {
							// Kept in camp memory as she goes, so even a restart cannot strand the rest of the trunk.
							forage.noteUnfinished(level, t.base(), List.copyOf(logs));
						}
					}
					case FAILED -> {
						logs.pollFirst();
						logTicks = 0;
					}
					case RUNNING -> {
					}
				}
			}
			case REPLANT -> {
				if (cut == 0 && !resumed) {
					forage.skip(t.base(), level.getGameTime());
					return TaskStatus.FAILURE;
				}
				Item sapling = chooseSapling(c, level, t.base());
				if (sapling == null || phaseTicks > 100) {
					return finish(c, level, t);
				}
				if (!actions.canReach(t.base())) {
					actions.walkTo(t.base(), 1.5);
					return TaskStatus.RUNNING;
				}
				BlockState state = ((BlockItem) sapling).getBlock().defaultBlockState();
				if (EditSteps.place(c, t.base(), state, s -> s.is(sapling), Reason.GATHER_WOOD) != EditSteps.Step.WAIT) {
					return finish(c, level, t);
				}
			}
		}
		return TaskStatus.RUNNING;
	}

	private TaskStatus finish(CompanionEntity c, ServerLevel level, Tree t) {
		forage.noteFelled(level, t.base(), level.getGameTime());
		Camp.data(level.getServer()).addStat("trees_felled", 1);
		return TaskStatus.SUCCESS;
	}

	/** A carried sapling that can grow on the stump's soil: the felled tree's own kind if possible. */
	private @Nullable Item chooseSapling(CompanionEntity c, ServerLevel level, BlockPos base) {
		if (!level.getBlockState(base).isAir()) {
			return null;
		}
		Item matching = logBlock != null ? saplingFor(logBlock) : null;
		if (matching != null && c.backpack().has(s -> s.is(matching)) && canGrow(level, base, matching)) {
			return matching;
		}
		ItemStack any = c.backpack().find(s -> s.is(ItemTags.SAPLINGS) && s.getItem() instanceof BlockItem
			&& canGrow(level, base, s.getItem()));
		return any.isEmpty() ? null : any.getItem();
	}

	private static boolean canGrow(ServerLevel level, BlockPos pos, Item item) {
		return item instanceof BlockItem block && block.getBlock().defaultBlockState().is(BlockTags.SAPLINGS)
			&& block.getBlock().defaultBlockState().canSurvive(level, pos);
	}

	/** {@code oak_log} to {@code oak_sapling}, mangrove to its propagule, or null for logs without a sapling. */
	static @Nullable Item saplingFor(Block log) {
		String path = BuiltInRegistries.BLOCK.getKey(log).getPath();
		String name = path.startsWith("mangrove") ? "mangrove_propagule" : path.replace("_log", "_sapling");
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(name));
		return item.filter(i -> i != Items.AIR && i instanceof BlockItem).orElse(null);
	}

	@Override
	public void stop(CompanionEntity c) {
		Tree t = tree;
		if (t != null && (cut > 0 || resumed) && !logs.isEmpty() && c.level() instanceof ServerLevel level) {
			// Called away with the trunk cut into: remember what still stands so it is finished first next time.
			forage.noteUnfinished(level, t.base(), List.copyOf(logs));
		}
		tree = null;
		logs.clear();
		cut = 0;
		resumed = false;
	}
}

package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskScheduler;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Eats a real meal when hungry. Food in the backpack comes first; otherwise the friend walks to the supply chest and
 * takes one item. The meal is chosen to fit the hunger: the most filling food that does not overfill, so a peckish
 * friend has an apple and saves the steak. Eating takes a moment (chewing sounds and crumbs) and genuinely uses the
 * food up; a bowl or bottle goes back into the backpack.
 *
 * <p>How soon depends on the hunger: once merely peckish ({@value #PECKISH}) at ordinary work-level priority, so the
 * job in hand is finished first; when hungry ({@value #HUNGRY}) as urgent upkeep; and when starving
 * ({@value #STARVING}) before anything else, even the most pressing work. With nothing to eat in the backpack or the
 * supply chest the job does not come up at all (so it never breaks off work only to find nothing); the friend asks
 * for food instead ({@link io.github.bradley09roberts.hardcorefriends.companion.MoodPassives}).
 */
public final class EatTask implements CompanionTask {
	/** Below this hunger a friend has a meal once the job in hand is done. */
	public static final double PECKISH = 70;
	/** Below this hunger a meal comes before most work, and with nothing to eat anywhere a friend asks for food. */
	public static final double HUNGRY = 25;
	/** Below this hunger a meal comes before anything: it outscores any work and takes over from it at once. */
	public static final double STARVING = 15;
	/** The starving score: above {@link TaskScheduler#URGENT}, so it beats and interrupts any work. */
	static final double STARVING_SCORE = TaskScheduler.URGENT + 10;
	private static final int EAT_TICKS = 30;
	private static final double CHEST_REACH = 2.5;

	private enum Stage { TO_CHEST, EATING }

	private Stage stage = Stage.EATING;
	private @Nullable BlockPos chestPos;
	private ItemStack meal = ItemStack.EMPTY;
	private int chewing;

	@Override
	public String id() {
		return "needs.eat";
	}

	@Override
	public String describe() {
		return "having something to eat";
	}

	@Override
	public double score(CompanionEntity c) {
		double hunger = c.needs().get(Need.HUNGER);
		if (hunger >= PECKISH || !foodAvailable(c)) {
			return 0; // nothing to eat anywhere: the friend asks for food (MoodPassives) and keeps working meanwhile
		}
		if (hunger < STARVING) {
			return STARVING_SCORE;
		}
		if (hunger < HUNGRY) {
			return 80;
		}
		return 42 + (PECKISH - hunger) * 0.4; // 42 when just peckish, up to 60
	}

	@Override
	public boolean start(CompanionEntity c) {
		meal = ItemStack.EMPTY;
		chewing = 0;
		chestPos = null;
		if (c.hasFood()) {
			stage = Stage.EATING;
		} else if (chestFood(c) > 0) {
			stage = Stage.TO_CHEST;
			chestPos = chestPos(c);
		} else {
			return false; // the last of the food went since scoring
		}
		Speech.say(c, c.needs().get(Need.HUNGER) <= 0 ? Line.STARVING : Line.HUNGRY);
		return stage == Stage.EATING || chestPos != null;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (stage == Stage.TO_CHEST) {
			return fetchFromChest(c, level);
		}
		if (meal.isEmpty()) {
			meal = c.backpack().take(sameAs(bestFit(c, c.backpack().stacks())), 1);
			if (meal.isEmpty()) {
				return TaskStatus.FAILURE;
			}
		}
		c.getNavigation().stop();
		chewing++;
		if (chewing % 4 == 0) {
			level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL,
				0.5F + 0.5F * c.getRandom().nextInt(2), (c.getRandom().nextFloat() - c.getRandom().nextFloat()) * 0.2F + 1.0F);
			Vec3 mouth = c.getEyePosition().add(c.getLookAngle().scale(0.4)).subtract(0, 0.15, 0);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(meal)),
				mouth.x, mouth.y, mouth.z, 3, 0.08, 0.05, 0.08, 0.05);
		}
		if (chewing % 8 == 0) {
			c.swingArm();
		}
		if (chewing < EAT_TICKS) {
			return TaskStatus.RUNNING;
		}
		ItemStack eaten = meal;
		meal = ItemStack.EMPTY;
		String name = eaten.getHoverName().getString();
		c.eat(eaten); // fills hunger, heals a little, keeps the bowl
		Camp.data(level.getServer()).addStat("meals_eaten", 1);
		Speech.say(c, Line.ATE, name);
		return TaskStatus.SUCCESS;
	}

	/** Walks to the supply chest and takes the one food item that best fits the hunger. */
	private TaskStatus fetchFromChest(CompanionEntity c, ServerLevel level) {
		if (chestPos == null) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().walkTo(chestPos, CHEST_REACH)) {
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(chestPos));
		Optional<Container> chest = SupplyChest.at(level, chestPos);
		if (chest.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		Container container = chest.get();
		int slot = bestFitSlot(c, container);
		if (slot < 0) {
			if (c.hasFood()) {
				stage = Stage.EATING; // someone emptied the chest, but there is food in the backpack after all
				return TaskStatus.RUNNING;
			}
			Speech.say(c, Line.NO_FOOD); // someone took the last of it on the way
			return TaskStatus.FAILURE;
		}
		meal = container.removeItem(slot, 1);
		container.setChanged();
		if (meal.isEmpty()) {
			return TaskStatus.FAILURE;
		}
		level.playSound(null, chestPos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
		c.swingArm();
		stage = Stage.EATING;
		chewing = 0;
		return TaskStatus.RUNNING;
	}

	/**
	 * The food that best fits how hungry the friend is: the most filling one that does not go to waste, or, when
	 * every food overfills, the least filling one. The best food is saved for when it is really needed.
	 */
	static ItemStack bestFit(CompanionEntity c, Iterable<ItemStack> stacks) {
		double missing = 100 - c.needs().get(Need.HUNGER);
		ItemStack best = ItemStack.EMPTY;
		double bestValue = 0;
		for (ItemStack s : stacks) {
			if (!CompanionEntity.isEdible(s)) {
				continue;
			}
			double value = CompanionEntity.hungerValue(s);
			if (best.isEmpty() || betterFit(value, bestValue, missing)) {
				best = s;
				bestValue = value;
			}
		}
		return best;
	}

	private static boolean betterFit(double value, double bestValue, double missing) {
		boolean fits = value <= missing;
		boolean bestFits = bestValue <= missing;
		if (fits != bestFits) {
			return fits;
		}
		return fits ? value > bestValue : value < bestValue;
	}

	private static int bestFitSlot(CompanionEntity c, Container container) {
		double missing = 100 - c.needs().get(Need.HUNGER);
		int best = -1;
		double bestValue = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (s.isEmpty() || !CompanionEntity.isEdible(s)) {
				continue;
			}
			double value = CompanionEntity.hungerValue(s);
			if (best < 0 || betterFit(value, bestValue, missing)) {
				best = i;
				bestValue = value;
			}
		}
		return best;
	}

	private static Predicate<ItemStack> sameAs(ItemStack sample) {
		return s -> !sample.isEmpty() && ItemStack.isSameItemSameComponents(s, sample);
	}

	/** True when there is food within reach: in the backpack, or in the supply chest of the camp this friend is in. */
	public static boolean foodAvailable(CompanionEntity c) {
		return c.hasFood() || chestFood(c) > 0;
	}

	private static int chestFood(CompanionEntity c) {
		return SupplyChest.of((ServerLevel) c.level()).map(chest -> SupplyChest.count(chest, CompanionEntity::isEdible)).orElse(0);
	}

	private static @Nullable BlockPos chestPos(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		return Camp.isCampLevel(level, data) ? data.chestPos().orElse(null) : null;
	}

	@Override
	public void stop(CompanionEntity c) {
		if (!meal.isEmpty()) {
			// Interrupted before finishing: the food goes back in the backpack, uneaten.
			ItemStack left = c.backpack().insert(meal);
			if (!left.isEmpty()) {
				c.spawnAtLocation((ServerLevel) c.level(), left);
			}
		}
		meal = ItemStack.EMPTY;
		chewing = 0;
		chestPos = null;
	}

	@Override
	public int successCooldown() {
		return 40;
	}

	@Override
	public int maxTicks() {
		return 20 * 60;
	}
}

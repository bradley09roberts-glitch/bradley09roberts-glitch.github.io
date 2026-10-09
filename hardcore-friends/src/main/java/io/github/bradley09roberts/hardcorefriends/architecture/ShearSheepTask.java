package io.github.bradley09roberts.hardcorefriends.architecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Wildlife;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * Wool for beds and carpets, when the buildings under way are short of it ({@link MaterialDemand}): by day the friend
 * shears a wild sheep in the gathering ring with shears from the backpack or the chest (or makes a pair from two iron
 * ingots when the chest has iron to spare) and picks up the wool. Only sheep the livestock rules call wild: never a
 * named, leashed, saddled or owned one, never one by anything player-built or in a player's enclosure, never the pen's
 * own, never where a friend died lately. Shearing does the sheep no harm; its wool grows back. The farmer's job, anyone
 * may help. Config {@code friendsShearSheep}.
 */
public final class ShearSheepTask implements CompanionTask {
	public static final String ID = "fern.shear";
	private static final int SPARE_IRON = 6;
	private static final int TIMEOUT_PER_SHEEP = 20 * 30;
	private static final double REACH = 2.2;
	private static final int PER_RUN = 3;

	private enum Phase {
		CHEST,
		SHEEP,
		GATHER
	}

	private Phase phase = Phase.SHEEP;
	private @Nullable Sheep sheep;
	private final List<ItemEntity> drops = new ArrayList<>();
	private @Nullable Vec3 shornAt;
	private int ticks;
	private int shorn;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return "shearing sheep for wool";
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !FriendsConfig.get().friendsShearSheep) {
			return 0;
		}
		if (Camp.isNight(level) || Camp.isDusk(level) || !Camp.isCampLevel(level, Camp.data(level.getServer()))) {
			return 0;
		}
		if (MaterialDemand.missing(level.getServer(), Stock.WOOL) <= 0 || !c.backpack().canFit(new ItemStack(Items.WOOL.pick(DyeColor.WHITE), 3))) {
			return 0;
		}
		if (!hasShears(c) && !canGetShears(c, level)) {
			return 0;
		}
		return target(c, level) != null ? 38 * CampNeeds.weight(CampNeeds.Need.BUILD) : 0;
	}

	private static boolean hasShears(CompanionEntity c) {
		return c.getMainHandItem().is(Items.SHEARS) || c.backpack().has(s -> s.is(Items.SHEARS));
	}

	private static boolean canGetShears(CompanionEntity c, ServerLevel level) {
		Optional<Container> chest = SupplyChest.of(level);
		return chest.isPresent() && (SupplyChest.count(chest.get(), s -> s.is(Items.SHEARS)) > 0
			|| SupplyChest.count(chest.get(), s -> s.is(Items.IRON_INGOT)) >= SPARE_IRON);
	}

	/** The nearest wild, grown sheep with its wool on. */
	private static @Nullable Sheep target(CompanionEntity c, ServerLevel level) {
		Pen pen = Pen.of(level).orElse(null);
		Animal a = Wildlife.nearest(c, x -> x instanceof Sheep s && s.readyForShearing() && Wildlife.mayLead(c, s, pen));
		return a instanceof Sheep s ? s : null;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		sheep = target(c, level);
		drops.clear();
		shornAt = null;
		ticks = 0;
		shorn = 0;
		if (sheep == null) {
			return false;
		}
		phase = hasShears(c) ? Phase.SHEEP : Phase.CHEST;
		Speech.say(c, Line.SHEARING);
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (Camp.isDusk(level) || Camp.isNight(level)) {
			return shorn > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		return switch (phase) {
			case CHEST -> fetchShears(c, level);
			case SHEEP -> shear(c, level);
			case GATHER -> gather(c, level);
		};
	}

	private TaskStatus fetchShears(CompanionEntity c, ServerLevel level) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
				Optional<Container> chest = ChestWalk.chest(c);
				if (chest.isEmpty()) {
					return TaskStatus.FAILURE;
				}
				if (SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(Items.SHEARS), 1) == 0
					&& SupplyChest.count(chest.get(), s -> s.is(Items.IRON_INGOT)) >= SPARE_IRON) {
					// Two iron ingots make a pair (a small recipe: no crafting table needed).
					SupplyChest.withdraw(chest.get(), c.backpack(), s -> s.is(Items.IRON_INGOT), 2);
					Crafting.ensure(c, Items.SHEARS, 1);
				}
				if (!hasShears(c)) {
					return TaskStatus.FAILURE;
				}
				phase = Phase.SHEEP;
				ticks = 0;
			}
		}
		return TaskStatus.RUNNING;
	}

	private TaskStatus shear(CompanionEntity c, ServerLevel level) {
		Sheep s = sheep;
		Pen pen = Pen.of(level).orElse(null);
		if (s == null || !s.isAlive() || !s.readyForShearing() || ++ticks > TIMEOUT_PER_SHEEP) {
			return nextSheep(c, level);
		}
		if (c.distanceTo(s) > REACH) {
			c.actions().walkToEntity(s, REACH - 0.3);
			if (c.actions().isStuck()) {
				c.actions().stopWalking();
				return nextSheep(c, level);
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		// Every rule afresh, right before the shears touch it.
		if (!Wildlife.mayLeadNow(c, s, pen) || !c.actions().equip(st -> st.is(Items.SHEARS))) {
			return nextSheep(c, level);
		}
		c.getLookControl().setLookAt(s, 30.0F, 30.0F);
		c.swingArm();
		ItemStack shears = c.getMainHandItem();
		s.shear(level, SoundSource.NEUTRAL, shears);
		if (!Unity.carefulHands(c)) {
			c.damageMainHandTool(1);
		}
		shorn++;
		Camp.data(level.getServer()).addStat("sheep_shorn", 1);
		shornAt = s.position();
		drops.clear();
		phase = Phase.GATHER;
		ticks = 0;
		return TaskStatus.RUNNING;
	}

	/** Picks up the wool that fell (only freshly dropped wool, never anything a player threw down). */
	private TaskStatus gather(CompanionEntity c, ServerLevel level) {
		Vec3 at = shornAt;
		if (at == null || ++ticks > 20 * 8) {
			return nextSheep(c, level);
		}
		if (drops.isEmpty()) {
			if (ticks < 10) {
				return TaskStatus.RUNNING; // let the wool land
			}
			drops.addAll(level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0, 2.0, 3.0),
				e -> e.isAlive() && e.getItem().is(ItemTags.WOOL) && !(e.getOwner() instanceof Player) && e.getAge() < 200));
			drops.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
			if (drops.isEmpty()) {
				return nextSheep(c, level);
			}
		}
		ItemEntity item = drops.getFirst();
		if (!item.isAlive()) {
			drops.removeFirst();
			return drops.isEmpty() ? nextSheep(c, level) : TaskStatus.RUNNING;
		}
		if (!c.actions().walkTo(item.blockPosition(), 1.2) && !c.actions().isStuck()) {
			return TaskStatus.RUNNING;
		}
		ItemStack stack = item.getItem();
		ItemStack left = c.backpack().insert(stack.copy());
		int picked = stack.getCount() - left.getCount();
		if (picked > 0) {
			c.take(item, picked);
		}
		if (left.isEmpty()) {
			item.discard();
		} else {
			item.setItem(left);
		}
		drops.removeFirst();
		return drops.isEmpty() ? nextSheep(c, level) : TaskStatus.RUNNING;
	}

	private TaskStatus nextSheep(CompanionEntity c, ServerLevel level) {
		c.actions().stopWalking();
		drops.clear();
		shornAt = null;
		ticks = 0;
		if (shorn >= PER_RUN || MaterialDemand.missing(level.getServer(), Stock.WOOL) <= 0 && shorn > 0) {
			return TaskStatus.SUCCESS;
		}
		sheep = target(c, level);
		if (sheep == null) {
			return shorn > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		phase = Phase.SHEEP;
		return TaskStatus.RUNNING;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		sheep = null;
		drops.clear();
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 20;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}

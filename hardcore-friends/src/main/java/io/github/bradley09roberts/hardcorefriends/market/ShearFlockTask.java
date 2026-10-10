package io.github.bradley09roberts.hardcorefriends.market;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Pen;
import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Wildlife;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.architecture.MaterialDemand;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.camp.build.Stock;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The shepherd shears the camp's own flock in the animal pen (the builders' shearing job only touches wild sheep).
 * They never go in: they stand just outside the fence nearest a sheep with its wool on, call it over to the fence
 * (steering it as the game's temptation does) and shear it across the rail, catching the wool in hand, so nothing falls
 * where nobody can reach and the gate is never opened. The wool is the game's own shearing loot; the wool grows back.
 * Never a named, leashed or owned sheep (a player's in the pen), never a lamb. Shears from the backpack or the supply
 * chest, or a pair made from two iron ingots when the chest has six or more. The wool goes to the supply chest.
 */
final class ShearFlockTask extends TradeJob {
	static final String ID = "market.shear_flock";
	private static final double SCORE = 44;
	private static final double REACH = 3.2;
	private static final int PER_RUN = 4;
	private static final int WAIT_PER_SHEEP = 20 * 15;
	private static final int SPARE_IRON = 6;
	/** The supply chest holding this much wool is plenty while the builders want none. */
	private static final int WOOL_PLENTY = 48;

	private enum Phase {
		SHEARS,
		SHEEP,
		DELIVER
	}

	private Phase phase = Phase.SHEEP;
	private @Nullable Sheep sheep;
	private @Nullable BlockPos standAt;
	private int ticks;
	private int shorn;
	private int wool;
	private boolean said;

	ShearFlockTask() {
		super(ID, Set.of(), Trade.SHEPHERD);
	}

	@Override
	public String describe() {
		return phase == Phase.DELIVER ? "taking the wool to the chest" : "shearing the flock";
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (!workingHours(level) || c.backpack().freeSlots() < 2) {
			return 0;
		}
		boolean wanted = MaterialDemand.missing(level.getServer(), Stock.WOOL) > 0;
		if (!wanted && Stores.supplyCount(level, s -> s.is(ItemTags.WOOL)) >= WOOL_PLENTY) {
			return 0;
		}
		if (!hasShears(c) && !canGetShears(level)) {
			return 0;
		}
		Optional<Pen> pen = Pen.of(level);
		if (pen.isEmpty() || nextSheep(level, pen.get(), c) == null) {
			return 0;
		}
		return wanted ? Math.min(62, SCORE * CampNeeds.weight(CampNeeds.Need.BUILD)) : SCORE;
	}

	private static boolean hasShears(CompanionEntity c) {
		return c.actions().has(s -> s.is(Items.SHEARS));
	}

	private static boolean canGetShears(ServerLevel level) {
		return Stores.supplyCount(level, s -> s.is(Items.SHEARS)) > 0 || Stores.supplyCount(level, s -> s.is(Items.IRON_INGOT)) >= SPARE_IRON;
	}

	/** The pen's nearest grown sheep with its wool on that is the camp's own. */
	private static @Nullable Sheep nextSheep(ServerLevel level, Pen pen, CompanionEntity c) {
		List<Animal> flock = pen.animals(level);
		return flock.stream()
			.filter(a -> a instanceof Sheep s && s.readyForShearing() && !s.isBaby() && !Wildlife.isSomebodys(s))
			.map(a -> (Sheep) a)
			.min(Comparator.comparingDouble(s -> s.distanceToSqr(c)))
			.orElse(null);
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Optional<Pen> pen = Pen.of(level);
		sheep = pen.map(p -> nextSheep(level, p, c)).orElse(null);
		if (sheep == null) {
			return false;
		}
		phase = hasShears(c) ? Phase.SHEEP : Phase.SHEARS;
		standAt = null;
		ticks = 0;
		shorn = 0;
		wool = 0;
		said = false;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		if (!workingHours(level) && phase != Phase.DELIVER) {
			return wool > 0 ? toDeliver() : TaskStatus.SUCCESS;
		}
		return switch (phase) {
			case SHEARS -> fetchShears(c, level);
			case SHEEP -> shear(c, level);
			case DELIVER -> deliver(c, level);
		};
	}

	private TaskStatus fetchShears(CompanionEntity c, ServerLevel level) {
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
		if (Stores.withdraw(c, supply.get(), s -> s.is(Items.SHEARS), 1) == 0
			&& Stores.supplyCount(level, s -> s.is(Items.IRON_INGOT)) >= SPARE_IRON) {
			Stores.withdraw(c, supply.get(), s -> s.is(Items.IRON_INGOT), 2);
			Crafting.ensure(c, Items.SHEARS, 1);
		}
		if (!hasShears(c)) {
			return TaskStatus.FAILURE;
		}
		phase = Phase.SHEEP;
		return TaskStatus.RUNNING;
	}

	private TaskStatus shear(CompanionEntity c, ServerLevel level) {
		Optional<Pen> found = Pen.of(level);
		Sheep s = sheep;
		if (found.isEmpty()) {
			return wool > 0 ? toDeliver() : TaskStatus.FAILURE;
		}
		Pen pen = found.get();
		if (s == null || !s.isAlive() || !s.readyForShearing() || !pen.holds(s) || Wildlife.isSomebodys(s) || ++ticks > WAIT_PER_SHEEP) {
			return next(c, level, pen);
		}
		if (standAt == null) {
			standAt = outside(level, pen, s);
			if (standAt == null) {
				return next(c, level, pen);
			}
		}
		if (c.distanceTo(s) > REACH) {
			switch (Stores.stand(c, standAt, 0.8)) {
				case WALKING -> {
					return TaskStatus.RUNNING;
				}
				case FAILED -> {
					return next(c, level, pen);
				}
				case ARRIVED -> {
				}
			}
			// Call it over to the fence, as a sheep follows wheat held out to it.
			c.getLookControl().setLookAt(s, 30.0F, 30.0F);
			if (ticks % 20 == 1) {
				BlockPos in = inside(pen, standAt);
				s.getNavigation().moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5, 1.0);
				standAt = outside(level, pen, s); // it may have wandered to another side
				if (standAt == null) {
					return next(c, level, pen);
				}
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		if (!c.actions().equip(st -> st.is(Items.SHEARS))) {
			return wool > 0 ? toDeliver() : TaskStatus.FAILURE;
		}
		if (!said) {
			said = true;
			Speech.say(c, Line.SHEARING_FLOCK);
		}
		c.getLookControl().setLookAt(s, 30.0F, 30.0F);
		c.swingArm();
		ItemStack shears = c.getMainHandItem();
		level.playSound(null, s, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 1.0F);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, s.position())
			.withParameter(LootContextParams.THIS_ENTITY, s)
			.withParameter(LootContextParams.TOOL, shears)
			.create(LootContextParamSets.SHEARING);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SHEAR_SHEEP);
		for (ItemStack drop : table.getRandomItems(params)) {
			wool += drop.getCount();
			Stores.give(c, drop);
		}
		s.setSheared(true);
		c.damageMainHandTool(1);
		shorn++;
		Camp.data(level.getServer()).addStat("sheep_shorn", 1);
		return next(c, level, pen);
	}

	private TaskStatus next(CompanionEntity c, ServerLevel level, Pen pen) {
		ticks = 0;
		standAt = null;
		sheep = shorn < PER_RUN && hasShears(c) ? nextSheep(level, pen, c) : null;
		if (sheep == null) {
			return wool > 0 ? toDeliver() : TaskStatus.FAILURE;
		}
		return TaskStatus.RUNNING;
	}

	/** A dry spot just outside the pen's fence, on the side nearest the sheep. */
	private static @Nullable BlockPos outside(ServerLevel level, Pen pen, Sheep s) {
		AABB box = pen.footprint();
		double x = s.getX();
		double z = s.getZ();
		int y = pen.origin().getY();
		double west = x - box.minX;
		double east = box.maxX - x;
		double north = z - box.minZ;
		double south = box.maxZ - z;
		double min = Math.min(Math.min(west, east), Math.min(north, south));
		int cx = (int) Math.floor(Math.clamp(x, box.minX + 0.5, box.maxX - 0.5));
		int cz = (int) Math.floor(Math.clamp(z, box.minZ + 0.5, box.maxZ - 0.5));
		BlockPos spot;
		if (min == west) {
			spot = new BlockPos((int) Math.floor(box.minX) - 1, y, cz);
		} else if (min == east) {
			spot = new BlockPos((int) Math.floor(box.maxX), y, cz);
		} else if (min == north) {
			spot = new BlockPos(cx, y, (int) Math.floor(box.minZ) - 1);
		} else {
			spot = new BlockPos(cx, y, (int) Math.floor(box.maxZ));
		}
		for (int dy = 0; dy <= 1; dy++) {
			for (int down = 0; down <= 1; down++) {
				BlockPos p = spot.above(dy).below(down);
				if (FishSpots.standable(level, p)) {
					return p;
				}
			}
		}
		return null;
	}

	/** The spot inside the fence across from where the shepherd stands, for the sheep to come to. */
	private static BlockPos inside(Pen pen, BlockPos standAt) {
		Vec3 centre = Vec3.atBottomCenterOf(pen.centre());
		double dx = Math.signum(centre.x - (standAt.getX() + 0.5));
		double dz = Math.signum(centre.z - (standAt.getZ() + 0.5));
		boolean alongX = Math.abs(centre.x - standAt.getX()) > Math.abs(centre.z - standAt.getZ());
		return alongX ? standAt.offset((int) dx * 2, 0, 0) : standAt.offset(0, 0, (int) dz * 2);
	}

	private TaskStatus toDeliver() {
		phase = Phase.DELIVER;
		return TaskStatus.RUNNING;
	}

	private TaskStatus deliver(CompanionEntity c, ServerLevel level) {
		Optional<BlockPos> supply = Stores.supplyPos(level);
		if (supply.isEmpty()) {
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
		Stores.deposit(c, supply.get(), s -> s.is(ItemTags.WOOL), wool);
		Unity.add(level, Unity.DELIVERY, 1, 60);
		wool = 0;
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		sheep = null;
		standAt = null;
		phase = Phase.SHEEP;
	}

	@Override
	public int failureCooldown() {
		return 20 * 60;
	}

	@Override
	public int successCooldown() {
		return 20 * 60;
	}

	@Override
	public int maxTicks() {
		return 20 * 120;
	}
}

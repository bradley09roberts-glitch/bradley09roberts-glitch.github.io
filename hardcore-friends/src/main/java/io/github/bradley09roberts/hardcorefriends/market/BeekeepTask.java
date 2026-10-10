package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.role.ranch.Wildlife;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The beekeeper's work at the apiary, one thing a run:
 * <ul>
 * <li><b>Honey.</b> A full hive (honey level five) of the friends' own, with its campfire lit beneath it so the smoke
 * keeps the bees calm, is emptied: with shears for three honeycomb, or with a glass bottle for a bottle of honey when the
 * chest has bottles and few bottles of honey. Never without the smoke, so the bees are never angered.</li>
 * <li><b>Hives.</b> An empty {@code hive} spot of the apiary gets a beehive (from the chest, or made from six planks and
 * three honeycomb), placed through the edit guard like any building block.</li>
 * <li><b>Bees.</b> While the hives have room, a wild bee from the gathering ring is led home: the beekeeper holds out a
 * flower, the bee follows (as bees follow a player holding one), and at the apiary it is shown its new hive. Only a bee
 * with no home, or whose home is a wild nest; never one living in a hive someone made, a named or leashed bee, an angry
 * one, or one near anything a player built.</li>
 * </ul>
 * The honeycomb and honey go to the supply chest.
 */
final class BeekeepTask extends TradeJob {
	static final String ID = "market.bees";
	private static final int REPLAN = 100;
	private static final int BEES_PER_HIVE = 2;
	private static final int BEE_SCAN = 600;
	private static final int HONEY_BOTTLES_KEPT = 4;
	/** Within this many blocks of its new hive a bee finds its own way there (bees forget a home 48 blocks off). */
	private static final int KNOWS_THE_WAY = 32;

	private enum Kind {
		HONEY,
		HIVE,
		BEE
	}

	private record Plan(Kind kind, BlockPos hive) {
	}

	private enum Phase {
		FETCH,
		GO,
		LEAD,
		DELIVER
	}

	private @Nullable Plan plan;
	private Phase phase = Phase.FETCH;
	private @Nullable Bee bee;
	private boolean bottle;
	private int ticks;
	private int honeycomb;
	private int honey;
	private long plannedAt = Long.MIN_VALUE / 2;
	private @Nullable Plan planned;
	private static List<Bee> wildBees = List.of();
	private static long beesAt = Long.MIN_VALUE / 2;

	BeekeepTask() {
		super(ID, Set.of(), Trade.BEEKEEPER);
	}

	@Override
	public String describe() {
		Plan p = plan;
		if (p == null) {
			return "keeping bees";
		}
		return switch (p.kind()) {
			case HONEY -> "taking honey from the hives";
			case HIVE -> "setting up a beehive";
			case BEE -> "leading a bee home to the apiary";
		};
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (w == null || !workingHours(level) || c.backpack().freeSlots() < 2 || Stores.supplyPos(level).isEmpty()) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - plannedAt >= REPLAN || now < plannedAt) {
			plannedAt = now;
			planned = plan(c, level, w);
		}
		Plan p = planned;
		if (p == null) {
			return 0;
		}
		return switch (p.kind()) {
			case HONEY -> 48;
			case HIVE -> 44;
			case BEE -> 40;
		};
	}

	private static @Nullable Plan plan(CompanionEntity c, ServerLevel level, Workplace w) {
		List<BlockPos> spots = w.markers(level, "hive");
		CampData data = Camp.data(level.getServer());
		List<BlockPos> hives = new ArrayList<>();
		BlockPos empty = null;
		for (BlockPos spot : spots) {
			if (!level.isLoaded(spot)) {
				continue;
			}
			BlockState s = level.getBlockState(spot);
			if (s.is(Blocks.BEEHIVE) && data.isPlacedByFriends(level, spot)) {
				hives.add(spot);
				if (s.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS && CampfireBlock.isSmokeyPos(level, spot)
					&& (hasOrStored(c, level, Items.SHEARS) || hasOrStored(c, level, Items.GLASS_BOTTLE))) {
					return new Plan(Kind.HONEY, spot);
				}
			} else if (s.isAir() && empty == null) {
				empty = spot;
			}
		}
		if (empty != null && (hasOrStored(c, level, Items.BEEHIVE) || canMakeHive(level))) {
			return new Plan(Kind.HIVE, empty);
		}
		BlockPos roomy = roomForBees(level, hives);
		if (roomy != null && hasFlower(c, level) && wildBee(c, level, roomy) != null) {
			return new Plan(Kind.BEE, roomy);
		}
		return null;
	}

	private static boolean hasOrStored(CompanionEntity c, ServerLevel level, net.minecraft.world.item.Item item) {
		return c.actions().has(s -> s.is(item)) || Stores.supplyCount(level, s -> s.is(item)) > 0;
	}

	private static boolean canMakeHive(ServerLevel level) {
		return Stores.supplyCount(level, s -> s.is(Items.HONEYCOMB)) >= 3 && Stores.supplyCount(level, s -> s.is(ItemTags.PLANKS)) >= 6;
	}

	private static boolean hasFlower(CompanionEntity c, ServerLevel level) {
		return c.actions().has(s -> s.is(ItemTags.BEE_FOOD)) || Stores.supplyCount(level, s -> s.is(ItemTags.BEE_FOOD)) > 0;
	}

	/** A hive of the apiary with room for another bee, counting its bees inside and out. */
	private static @Nullable BlockPos roomForBees(ServerLevel level, List<BlockPos> hives) {
		for (BlockPos hive : hives) {
			if (!(level.getBlockEntity(hive) instanceof BeehiveBlockEntity be) || be.isFull()) {
				continue;
			}
			int bees = be.getOccupantCount();
			for (Bee b : level.getEntitiesOfClass(Bee.class, new AABB(hive).inflate(24), x -> hive.equals(x.getHivePos()))) {
				bees++;
			}
			if (bees < BEES_PER_HIVE) {
				return hive;
			}
		}
		return null;
	}

	/** The nearest wild bee in the gathering ring the rules allow leading home (see the class comment). */
	private static @Nullable Bee wildBee(CompanionEntity c, ServerLevel level, BlockPos apiary) {
		long now = level.getGameTime();
		CampData data = Camp.data(level.getServer());
		if (now - beesAt >= BEE_SCAN || now < beesAt) {
			beesAt = now;
			Optional<BlockPos> centre = data.campPos();
			if (centre.isEmpty()) {
				wildBees = List.of();
			} else {
				int r = Camp.radius(data) + Math.min(FriendsConfig.get().resourceRadius, 48);
				wildBees = level.getEntitiesOfClass(Bee.class, new AABB(centre.get()).inflate(r, 24, r), Bee::isAlive);
			}
		}
		Bee best = null;
		double bestDist = Double.MAX_VALUE;
		for (Bee b : wildBees) {
			if (!mayLead(level, data, b, apiary)) {
				continue;
			}
			double d = b.distanceToSqr(c);
			if (d < bestDist) {
				bestDist = d;
				best = b;
			}
		}
		return best;
	}

	private static boolean mayLead(ServerLevel level, CampData data, Bee b, BlockPos apiary) {
		if (!b.isAlive() || b.isRemoved() || b.level() != level || b.isAngry() || Wildlife.isSomebodys(b)) {
			return false;
		}
		BlockPos home = b.getHivePos();
		if (home != null && (home.equals(apiary) || !level.isLoaded(home) || !level.getBlockState(home).is(Blocks.BEE_NEST))) {
			return false; // already ours, or living in a hive someone made (or somewhere unseen)
		}
		return !WorldEditGuard.looksPlayerBuilt(level, b.blockPosition(), 6, data) && !data.nearDanger(b.blockPosition(), level.getGameTime());
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Workplace w = workplace(c, holding(c));
		plan = w == null ? null : plan(c, level, w);
		plannedAt = Long.MIN_VALUE / 2;
		Plan p = plan;
		if (p == null) {
			return false;
		}
		bee = p.kind() == Kind.BEE ? wildBee(c, level, p.hive()) : null;
		bottle = false;
		honeycomb = 0;
		honey = 0;
		ticks = 0;
		phase = needsFetch(c, level, p) ? Phase.FETCH : Phase.GO;
		return p.kind() != Kind.BEE || bee != null;
	}

	private boolean needsFetch(CompanionEntity c, ServerLevel level, Plan p) {
		return switch (p.kind()) {
			case HONEY -> {
				bottle = wantsBottle(c, level);
				yield !c.actions().has(s -> s.is(bottle ? Items.GLASS_BOTTLE : Items.SHEARS));
			}
			case HIVE -> !c.actions().has(s -> s.is(Items.BEEHIVE));
			case BEE -> !c.actions().has(s -> s.is(ItemTags.BEE_FOOD));
		};
	}

	/** A bottle of honey when there are bottles and few bottles of honey; otherwise honeycomb (for more hives). */
	private static boolean wantsBottle(CompanionEntity c, ServerLevel level) {
		return hasOrStored(c, level, Items.GLASS_BOTTLE) && Stores.supplyCount(level, s -> s.is(Items.HONEY_BOTTLE)) < HONEY_BOTTLES_KEPT
			|| !hasOrStored(c, level, Items.SHEARS);
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		Plan p = plan;
		if (p == null || !workingHours(level) && phase != Phase.DELIVER) {
			return honeycomb + honey > 0 ? toDeliver() : TaskStatus.FAILURE;
		}
		return switch (phase) {
			case FETCH -> fetch(c, level, p);
			case GO -> go(c, level, p);
			case LEAD -> lead(c, level, p);
			case DELIVER -> deliver(c, level);
		};
	}

	private TaskStatus fetch(CompanionEntity c, ServerLevel level, Plan p) {
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
		switch (p.kind()) {
			case HONEY -> Stores.withdraw(c, supply.get(), s -> s.is(bottle ? Items.GLASS_BOTTLE : Items.SHEARS), 1);
			case HIVE -> {
				if (Stores.withdraw(c, supply.get(), s -> s.is(Items.BEEHIVE), 1) == 0 && canMakeHive(level) && Crafting.nearCraftingTable(c)) {
					Stores.withdraw(c, supply.get(), s -> s.is(Items.HONEYCOMB), 3);
					Stores.withdraw(c, supply.get(), s -> s.is(ItemTags.PLANKS), 6);
					Crafting.ensure(c, Items.BEEHIVE, 1);
				}
			}
			case BEE -> Stores.withdraw(c, supply.get(), s -> s.is(ItemTags.BEE_FOOD), 1);
		}
		if (needsFetch(c, level, p)) {
			return TaskStatus.FAILURE; // anything fetched stays for the tidying
		}
		phase = Phase.GO;
		return TaskStatus.RUNNING;
	}

	private TaskStatus go(CompanionEntity c, ServerLevel level, Plan p) {
		if (p.kind() == Kind.BEE) {
			Bee b = bee;
			if (b == null || !mayLead(level, Camp.data(level.getServer()), b, p.hive())) {
				return TaskStatus.FAILURE;
			}
			c.actions().equip(s -> s.is(ItemTags.BEE_FOOD));
			if (c.actions().walkToEntity(b, 3.0)) {
				phase = Phase.LEAD;
				ticks = 0;
				return TaskStatus.RUNNING;
			}
			return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
		}
		switch (Stores.walk(c, p.hive(), 3.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		return p.kind() == Kind.HONEY ? harvest(c, level, p.hive()) : placeHive(c, level, p.hive());
	}

	private TaskStatus harvest(CompanionEntity c, ServerLevel level, BlockPos hive) {
		BlockState s = level.getBlockState(hive);
		if (!s.is(Blocks.BEEHIVE) || s.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS || !CampfireBlock.isSmokeyPos(level, hive)) {
			return TaskStatus.FAILURE; // never without the smoke: the bees would be angered
		}
		if (!c.actions().equip(st -> st.is(bottle ? Items.GLASS_BOTTLE : Items.SHEARS))) {
			return TaskStatus.FAILURE;
		}
		if (!c.actions().transform(hive, s.setValue(BeehiveBlock.HONEY_LEVEL, 0), WorldEditGuard.Reason.FARM, null)) {
			return TaskStatus.FAILURE;
		}
		if (bottle) {
			ItemStack held = c.getMainHandItem();
			held.shrink(1);
			Stores.give(c, new ItemStack(Items.HONEY_BOTTLE));
			honey++;
			level.playSound(null, hive, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		} else {
			Stores.give(c, new ItemStack(Items.HONEYCOMB, 3));
			honeycomb += 3;
			c.damageMainHandTool(1);
			level.playSound(null, hive, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		Speech.say(c, Line.HONEY);
		Camp.data(level.getServer()).addStat("honey_taken", 1);
		return toDeliver();
	}

	private TaskStatus placeHive(CompanionEntity c, ServerLevel level, BlockPos spot) {
		if (!level.getBlockState(spot).isAir()) {
			return TaskStatus.FAILURE;
		}
		Direction facing = Direction.getApproximateNearest(c.getX() - (spot.getX() + 0.5), 0, c.getZ() - (spot.getZ() + 0.5));
		if (facing.getAxis().isVertical()) {
			facing = Direction.NORTH;
		}
		BlockState hive = Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, facing);
		if (!c.actions().place(spot, hive, s -> s.is(Items.BEEHIVE), WorldEditGuard.Reason.BUILD)) {
			return TaskStatus.FAILURE;
		}
		Speech.say(c, Line.TRADE_WORK, "setting up a beehive");
		return TaskStatus.SUCCESS;
	}

	/**
	 * Walking back towards the apiary with the bee following the flower. Once it is near enough to know the way (within
	 * {@value #KNOWS_THE_WAY} blocks of its new hive), it is shown its new home and goes there by itself, as bees do.
	 */
	private TaskStatus lead(CompanionEntity c, ServerLevel level, Plan p) {
		Bee b = bee;
		if (b == null || !b.isAlive() || b.isAngry() || b.distanceToSqr(c) > 16 * 16 || ++ticks > 20 * 90) {
			return TaskStatus.FAILURE;
		}
		if (!(level.getBlockEntity(p.hive()) instanceof BeehiveBlockEntity be) || be.isFull()) {
			return TaskStatus.FAILURE;
		}
		if (b.distanceToSqr(Vec3.atCenterOf(p.hive())) <= KNOWS_THE_WAY * KNOWS_THE_WAY) {
			b.setHivePos(p.hive());
			Camp.data(level.getServer()).addStat("bees_brought", 1);
			return TaskStatus.SUCCESS;
		}
		c.actions().equip(s -> s.is(ItemTags.BEE_FOOD));
		if (ticks % 10 == 0 && b.distanceToSqr(c) > 3 * 3) {
			b.getNavigation().moveTo(c, 1.0);
		}
		if (b.distanceToSqr(c) > 8 * 8) {
			c.actions().stopWalking(); // let it catch up
			c.getLookControl().setLookAt(b);
			return TaskStatus.RUNNING;
		}
		c.actions().walkTo(p.hive(), 4.0);
		return c.actions().isStuck() ? TaskStatus.FAILURE : TaskStatus.RUNNING;
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
		Stores.deposit(c, supply.get(), s -> s.is(Items.HONEYCOMB), honeycomb);
		Stores.deposit(c, supply.get(), s -> s.is(Items.HONEY_BOTTLE), honey);
		honeycomb = 0;
		honey = 0;
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		plan = null;
		bee = null;
		phase = Phase.FETCH;
	}

	/** Forgets the wild bees seen (a server stopping). */
	static void clear() {
		wildBees = List.of();
		beesAt = Long.MIN_VALUE / 2;
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

package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.ai.role.build.ChestWalk;
import io.github.bradley09roberts.hardcorefriends.ai.role.scout.Compass;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.ai.task.common.KeepList;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SupplyChest;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A trading trip: when a village is known within {@value #MAX_DISTANCE} blocks of camp, the camp has something to
 * trade (surplus or emeralds) and wants something villagers sell, a friend packs goods at the supply chest, walks
 * there by day, trades with every villager whose offers fit ({@link Trading}), and brings what they bought back to
 * the chest. Any friend may go; Sage and Rowan are keenest. One trader at a time, at most one trip a day. Villages
 * with a raid on or zombies about are left alone for a day; the trader never takes anything without paying and never
 * harms a villager. Hurt, hungry, tired, a storm or the evening drawing in send them home early.
 */
public final class TradeTripTask implements CompanionTask {
	public static final String ID = "survival.trade";
	/** The farthest village a trip goes to, in blocks from camp. */
	public static final int MAX_DISTANCE = 300;
	/** Time set aside for the trading itself when working out whether there is daylight enough, in ticks. */
	private static final long TRADING_TICKS = 1600;
	/** The longest spent trading in one village, in ticks. */
	private static final int MAX_TRADING_TICKS = 2400;
	/** Most trades one trip makes. */
	private static final int MAX_TRADES = 16;
	private static final int TRADE_INTERVAL = 10;
	/** How far round the village friends look for villagers to trade with. */
	private static final int VILLAGE_REACH = 40;
	private static final String MEMORY = "survival.trade";
	private static final String PACK = "pack";
	private static final String OUT = "out";
	private static final String TRADE = "trade";
	private static final String HOME = "home";
	private static final String UNPACK = "unpack";
	/** How long the chest is looked over for goods and wants before looking again, in ticks. */
	private static final int CHEST_CACHE_TICKS = 200;

	private static long chestCheckedAt = Long.MIN_VALUE;
	private static boolean chestWorthIt;
	private static long villageCheckedAt = Long.MIN_VALUE;
	private static @Nullable BlockPos cachedVillage;

	private final Trips.Walker walker = new Trips.Walker();
	private Trips.@Nullable State trip;
	private Set<Trading.Want> wants = Set.of();
	private final Map<Trading.Want, Integer> made = new EnumMap<>(Trading.Want.class);
	private final Set<UUID> tried = new HashSet<>();
	private @Nullable Villager partner;
	private int phaseTicks;
	private int trades;
	private int sold;
	private final List<String> bought = new ArrayList<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		Trips.State t = trip;
		if (t == null) {
			return "going trading";
		}
		return switch (t.phase) {
			case PACK -> "packing goods for a trading trip";
			case TRADE -> "trading at the village";
			case HOME -> "heading home from the village";
			case UNPACK -> "putting away what they bought";
			default -> "on a trip to the village at " + Compass.coords(t.destination);
		};
	}

	@Override
	public double score(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level)) {
			return 0;
		}
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return 0;
		}
		Trips.State saved = Trips.state(c);
		if (saved != null) {
			if (Trips.expired(c, saved)) {
				Trips.release(ID, c);
				return 0;
			}
			return saved.job.equals(ID) && !Camp.isNight(level) ? 60 : 0;
		}
		if (!Trips.allowed() || Camp.isNight(level) || Camp.isDusk(level)
			|| data.memory(MEMORY).getLongOr("day", -1) == Camp.day(level) || Trips.heldByOther(ID, c)) {
			return 0;
		}
		if (!ChunkLoader.canRoam(level.getServer()) || !Trips.campSafe(level, data) || !Trips.fitToGo(c)
			|| !chestWorthIt(level, data)) {
			return 0;
		}
		BlockPos village = cachedVillage(level, data, c);
		if (village == null) {
			return 0;
		}
		double distance = Math.sqrt(Camp.horizontalDistSqr(village, c.homePos()));
		if (!Trips.daylightFor(level, 2 * distance, TRADING_TICKS)) {
			return 0;
		}
		return c.friendId() == FriendId.SAGE || c.friendId() == FriendId.ROWAN ? 48 : 34;
	}

	/** Whether the chest holds something to trade and the camp wants something, looked at every ten seconds. */
	private static boolean chestWorthIt(ServerLevel level, CampData data) {
		long now = level.getGameTime();
		if (now - chestCheckedAt >= 0 && now - chestCheckedAt < CHEST_CACHE_TICKS) {
			return chestWorthIt;
		}
		chestCheckedAt = now;
		Optional<Container> chest = SupplyChest.of(level);
		chestWorthIt = chest.isPresent() && Trading.hasSomethingToTrade(chest.get())
			&& !Trading.wants(level, data, chest.get()).isEmpty();
		return chestWorthIt;
	}

	/** The village to trade at, worked out at most every ten seconds for everyone (it is the same for every friend). */
	private static @Nullable BlockPos cachedVillage(ServerLevel level, CampData data, CompanionEntity c) {
		long now = level.getGameTime();
		if (now - villageCheckedAt >= 0 && now - villageCheckedAt < CHEST_CACHE_TICKS) {
			return cachedVillage;
		}
		villageCheckedAt = now;
		cachedVillage = chooseVillage(level, data, c);
		return cachedVillage;
	}

	/**
	 * The nearest village to trade at: found on a trip, or by Scout near camp. Never one being avoided (a raid or
	 * zombies seen there), and never one near where a friend died lately or a pillager outpost.
	 */
	private static @Nullable BlockPos chooseVillage(ServerLevel level, CampData data, CompanionEntity c) {
		BlockPos home = c.homePos();
		long now = level.getGameTime();
		BlockPos best = null;
		double bestDist = (double) MAX_DISTANCE * MAX_DISTANCE;
		List<BlockPos> candidates = new ArrayList<>();
		Places.Place place = Places.nearest(data, Places.VILLAGE, home, MAX_DISTANCE, now, p -> !Trips.dangerous(level, data, p.pos()));
		if (place != null) {
			candidates.add(place.pos());
		}
		for (CampData.Poi poi : data.pois()) {
			if (Places.VILLAGE.equals(poi.type) && !avoided(data, poi.pos, now) && !Trips.dangerous(level, data, poi.pos)) {
				candidates.add(poi.pos);
			}
		}
		for (BlockPos p : candidates) {
			double d = Camp.horizontalDistSqr(p, home);
			if (d <= bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	/** True when a village found near camp (not on a trip) is being avoided for now. */
	private static boolean avoided(CampData data, BlockPos pos, long now) {
		for (Places.Place p : Places.all(data)) {
			if (Places.VILLAGE.equals(p.type()) && p.avoidUntil() > now && Camp.horizontalDistSqr(p.pos(), pos) <= 64 * 64) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		CampData data = Camp.data(level.getServer());
		walker.reset();
		made.clear();
		tried.clear();
		partner = null;
		phaseTicks = 0;
		trades = 0;
		sold = 0;
		bought.clear();
		wants = Trading.wants(level, data, SupplyChest.of(level).orElse(null));
		Trips.State saved = Trips.state(c);
		if (saved != null && saved.job.equals(ID)) {
			trip = saved;
			if (!Trips.home(c) && !ChunkLoader.startRoaming(c, "trading")) {
				return false;
			}
			if (!Trips.allowed() && !UNPACK.equals(saved.phase)) {
				saved.phase = HOME;
			}
			if (OUT.equals(saved.phase) && Trips.home(c) && !c.backpack().has(Trading::isTradeGood)) {
				saved.phase = PACK; // the goods went back into the chest meanwhile (overnight): pack again
			}
			Trips.claim(ID, c);
			return true;
		}
		BlockPos village = chooseVillage(level, data, c);
		if (village == null || wants.isEmpty() || !ChunkLoader.startRoaming(c, "trading")) {
			return false;
		}
		trip = new Trips.State(ID, PACK, village, level.getGameTime());
		Trips.save(c, trip);
		Trips.claim(ID, c);
		data.memory(MEMORY).putLong("day", Camp.day(level));
		data.setDirty();
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Trips.State t = trip;
		if (t == null) {
			return TaskStatus.FAILURE;
		}
		phaseTicks++;
		if (phaseTicks % 100 == 0) {
			Trips.snack(c);
		}
		return switch (t.phase) {
			case PACK -> pack(c, t);
			case OUT -> out(c, t);
			case TRADE -> trade(c, t);
			case HOME -> home(c, t);
			case UNPACK -> unpack(c, t);
			default -> {
				Trips.clear(c);
				yield TaskStatus.FAILURE;
			}
		};
	}

	private void setPhase(CompanionEntity c, Trips.State t, String phase) {
		t.phase = phase;
		phaseTicks = 0;
		walker.reset();
		c.actions().reset();
		Trips.save(c, t);
	}

	/** At the chest: takes the goods and a few emeralds, then sets off. */
	private TaskStatus pack(CompanionEntity c, Trips.State t) {
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return giveUp(c);
			}
			case ARRIVED -> {
			}
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isEmpty() || Trading.pack(c, chest.get(), wants) == 0) {
			return giveUp(c);
		}
		Trips.announce(c, Line.TRIP_START, "the village at " + Compass.coords(t.destination));
		setPhase(c, t, OUT);
		return TaskStatus.RUNNING;
	}

	/** A trip that never left the chest: nothing to take after all. */
	private TaskStatus giveUp(CompanionEntity c) {
		Trips.clear(c);
		Trips.release(ID, c);
		trip = null;
		ChunkLoader.stopRoaming(c);
		return TaskStatus.FAILURE;
	}

	private TaskStatus out(CompanionEntity c, Trips.State t) {
		if (Trips.turnBackReason(c) != null) {
			Speech.say(c, Line.TRIP_TURN_BACK);
			setPhase(c, t, HOME);
			return TaskStatus.RUNNING;
		}
		switch (walker.walk(c, t.destination, 12)) {
			case ARRIVED -> setPhase(c, t, TRADE);
			case BLOCKED -> setPhase(c, t, HOME);
			case WALKING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** In the village: one villager after another, one trade at a time, until nothing more fits. */
	private TaskStatus trade(CompanionEntity c, Trips.State t) {
		ServerLevel level = (ServerLevel) c.level();
		if (phaseTicks % 40 == 1 && unsafe(level, c, t.destination)) {
			Places.record(level, Places.VILLAGE, t.destination); // a village Scout saw near camp is now on the list too
			Places.Place place = Places.nearest(Camp.data(level.getServer()), Places.VILLAGE, t.destination, 64,
				level.getGameTime(), p -> true);
			if (place != null) {
				Places.avoidUntil(Camp.data(level.getServer()), place, level.getGameTime() + 24000L);
			}
			Speech.say(c, Line.TRIP_TURN_BACK);
			setPhase(c, t, HOME);
			return TaskStatus.RUNNING;
		}
		if (phaseTicks > MAX_TRADING_TICKS || trades >= MAX_TRADES || Trips.turnBackReason(c) != null) {
			setPhase(c, t, HOME);
			return TaskStatus.RUNNING;
		}
		Villager v = partner;
		if (v == null || !Trading.available(v) || v.distanceToSqr(c) > 64 * 64) {
			v = nextPartner(level, c, t.destination);
			partner = v;
			if (v == null) {
				setPhase(c, t, HOME);
				return TaskStatus.RUNNING;
			}
		}
		if (!c.actions().walkToEntity(v, 2.5)) {
			if (c.actions().isStuck()) {
				tried.add(v.getUUID());
				partner = null;
				c.actions().stopWalking();
			}
			return TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(v);
		if (phaseTicks % TRADE_INTERVAL != 0) {
			return TaskStatus.RUNNING;
		}
		Optional<Trading.Done> done = Trading.tradeOnce(c, v, wants, made);
		if (done.isEmpty()) {
			tried.add(v.getUUID());
			partner = null;
			return TaskStatus.RUNNING;
		}
		trades++;
		ItemStack got = done.get().got();
		if (done.get().sold()) {
			sold++;
		} else {
			bought.add(Trading.describe(got));
		}
		Speech.say(c, Line.TRADED, Trading.describe(got));
		Camp.data(level.getServer()).addStat("trades", 1);
		return TaskStatus.RUNNING;
	}

	/** A raid on, or zombies about the village. */
	private static boolean unsafe(ServerLevel level, CompanionEntity c, BlockPos village) {
		if (level.isRaided(village) || level.isRaided(c.blockPosition())) {
			return true;
		}
		AABB around = new AABB(c.blockPosition()).inflate(32, 12, 32);
		return !level.getEntitiesOfClass(Zombie.class, around, z -> z.isAlive()).isEmpty();
	}

	/** The nearest villager around the village not yet traded with this trip who has something to offer. */
	private @Nullable Villager nextPartner(ServerLevel level, CompanionEntity c, BlockPos village) {
		AABB area = new AABB(village).inflate(VILLAGE_REACH, 16, VILLAGE_REACH);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area,
			v -> Trading.available(v) && !tried.contains(v.getUUID()));
		villagers.sort(Comparator.comparingDouble(v -> v.distanceToSqr(c)));
		for (Villager v : villagers) {
			if (Trading.anythingFor(c, v, wants, made)) {
				return v;
			}
			tried.add(v.getUUID());
		}
		return null;
	}

	private TaskStatus home(CompanionEntity c, Trips.State t) {
		if (Trips.home(c)) {
			setPhase(c, t, UNPACK);
			return TaskStatus.RUNNING;
		}
		switch (walker.walk(c, c.homePos(), 6)) {
			case ARRIVED -> setPhase(c, t, UNPACK);
			case BLOCKED -> {
				return TaskStatus.FAILURE; // tried again later; the trip is remembered
			}
			case WALKING -> {
			}
		}
		return TaskStatus.RUNNING;
	}

	/** Back at the chest: everything bought and every unsold good goes in; a little food stays for the road. */
	private TaskStatus unpack(CompanionEntity c, Trips.State t) {
		ChunkLoader.stopRoaming(c);
		switch (ChestWalk.tick(c)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				finish(c, t);
				return TaskStatus.SUCCESS; // no chest to unpack into: the everyday deposit job sees to it
			}
			case ARRIVED -> {
			}
		}
		Optional<Container> chest = ChestWalk.chest(c);
		if (chest.isPresent()) {
			SupplyChest.deposit(c.backpack(), chest.get(), s -> Trading.isTradeGood(s) || isBought(s), 64 * 27);
			int food = c.backpack().count(CompanionEntity::isEdible);
			if (food > KeepList.FOOD_KEPT) {
				SupplyChest.deposit(c.backpack(), chest.get(), CompanionEntity::isEdible, food - KeepList.FOOD_KEPT);
			}
		}
		finish(c, t);
		return TaskStatus.SUCCESS;
	}

	/** Something a trip buys that is not food (food is shared out separately, keeping a little for the friend). */
	private static boolean isBought(ItemStack s) {
		for (Trading.Want want : Trading.Want.values()) {
			if (want != Trading.Want.FOOD && want.matches(s)) {
				return true;
			}
		}
		return false;
	}

	private void finish(CompanionEntity c, Trips.State t) {
		List<String> parts = new ArrayList<>(t.notes);
		parts.addAll(bought);
		String summary;
		if (parts.isEmpty() && sold == 0) {
			summary = "made no trades this time";
		} else if (parts.isEmpty()) {
			summary = "sold some surplus for emeralds";
		} else {
			summary = "brought back " + Trips.joinList(parts);
		}
		Trips.announce(c, Line.TRIP_BACK, summary);
		Trips.clear(c);
		Trips.release(ID, c);
		ChunkLoader.stopRoaming(c);
		trip = null;
	}

	@Override
	public void stop(CompanionEntity c) {
		walker.reset();
		c.actions().reset();
		partner = null;
		Trips.State t = trip;
		if (t != null && Trips.state(c) != null) {
			t.notes.addAll(bought); // what was bought before a fight broke the trip off still counts at home
			bought.clear();
			Trips.save(c, t);
		}
		if (Trips.home(c)) {
			ChunkLoader.stopRoaming(c); // asked for again when the trip goes on; away, it lasts until they are back
		}
		trip = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 12;
	}

	@Override
	public int failureCooldown() {
		return 300;
	}

	@Override
	public int successCooldown() {
		return 2400;
	}
}

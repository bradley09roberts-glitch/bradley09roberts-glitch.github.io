package io.github.bradley09roberts.hardcorefriends.market;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.CampNeeds;
import io.github.bradley09roberts.hardcorefriends.camp.Crafting;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * Fishing, by day, for the fisher (from the fishing hut's deck, or the nearest bank while there is no hut) and the
 * fishmonger. The friend takes a rod from the backpack or the supply chest (or makes one from three sticks and two
 * string at a crafting table), stands on dry ground by open water, and casts: a bite comes after five to thirty seconds,
 * as for a player without enchantments, and the catch is the game's own fishing loot (fish mostly, now and then junk;
 * never treasure, which needs a real bobber in open water). Each catch wears the rod by one. After a few catches the
 * fish go to the supply chest, where the cooks find them. Fishing changes no block and never stands in the water.
 */
final class FishTask extends TradeJob {
	static final String ID = "market.fish";
	private static final double SCORE = 46;
	private static final int CATCHES_PER_RUN = 4;
	private static final int MIN_WAIT = 100;
	private static final int MAX_WAIT = 600;
	private static final int STRING_FOR_ROD = 2;
	/** Ticks after an attacker's hit before the line goes out again. */
	private static final int INTERRUPTED = 40;

	private enum Phase {
		ROD,
		GO,
		FISH,
		DELIVER
	}

	private Phase phase = Phase.GO;
	private FishSpots.@Nullable Spot spot;
	private int ticks;
	private int biteAt;
	private int catches;
	private final List<ItemStack> caught = new ArrayList<>();
	private boolean said;
	private List<FishSpots.Spot> knownSpots = List.of();
	private long spotsAt = Long.MIN_VALUE / 2;
	private static final int SPOTS_REFRESH = 200;

	FishTask() {
		super(ID, Set.of(), Trade.FISHER, Trade.FISHMONGER);
	}

	@Override
	public String describe() {
		return phase == Phase.DELIVER ? "taking the catch to the chest" : "fishing";
	}

	@Override
	double scoreWork(CompanionEntity c, ServerLevel level, MarketData.Holding h, @Nullable Workplace w) {
		if (!workingHours(level) || c.backpack().freeSlots() < 3 || c.badlyHurt()) {
			return 0;
		}
		long now = level.getGameTime();
		if (now - spotsAt >= SPOTS_REFRESH || now < spotsAt) {
			spotsAt = now;
			knownSpots = spots(level, w);
		}
		if (knownSpots.isEmpty() || !hasRod(c) && !canGetRod(level)) {
			return 0;
		}
		return Math.min(64, SCORE * CampNeeds.weight(CampNeeds.Need.FOOD));
	}

	/** Where this fisher fishes: their hut's deck, a fishing hut's deck for the fishmonger, else the camp's banks. */
	static List<FishSpots.Spot> spots(ServerLevel level, @Nullable Workplace w) {
		if (w != null && w.trade() == Trade.FISHER) {
			List<FishSpots.Spot> deck = FishSpots.atWorkplace(level, w);
			if (!deck.isEmpty()) {
				return deck;
			}
		}
		for (Workplace hut : Workplaces.ofKind(level, "workplace:fisher")) {
			List<FishSpots.Spot> deck = FishSpots.atWorkplace(level, hut);
			if (!deck.isEmpty()) {
				return deck;
			}
		}
		return FishSpots.campSpots(level);
	}

	private static boolean hasRod(CompanionEntity c) {
		return c.actions().has(s -> s.is(Items.FISHING_ROD));
	}

	private static boolean canGetRod(ServerLevel level) {
		return Stores.supplyCount(level, s -> s.is(Items.FISHING_ROD)) > 0
			|| Stores.supplyCount(level, s -> s.is(Items.STRING)) >= STRING_FOR_ROD
			&& Stores.supplyCount(level, s -> s.is(Items.STICK)) >= 3;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		MarketData.Holding h = holding(c);
		List<FishSpots.Spot> spots = h == null ? List.of() : spots(level, workplace(c, h));
		CampData data = Camp.data(level.getServer());
		spot = null;
		double best = Double.MAX_VALUE;
		for (FishSpots.Spot s : spots) {
			if (data.nearDanger(s.stand(), level.getGameTime()) || !FishSpots.inRange(level, s.stand()) && h != null && h.atCamp()) {
				continue;
			}
			double d = s.stand().distSqr(c.blockPosition()) + c.getRandom().nextInt(64);
			if (d < best) {
				best = d;
				spot = s;
			}
		}
		if (spot == null) {
			return false;
		}
		phase = hasRod(c) ? Phase.GO : Phase.ROD;
		ticks = 0;
		catches = 0;
		caught.clear();
		said = false;
		return true;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		FishSpots.Spot s = spot;
		if (s == null) {
			return TaskStatus.FAILURE;
		}
		if (!workingHours(level) && phase != Phase.DELIVER) {
			phase = caught.isEmpty() ? phase : Phase.DELIVER;
			if (phase != Phase.DELIVER) {
				return TaskStatus.SUCCESS;
			}
		}
		return switch (phase) {
			case ROD -> fetchRod(c, level);
			case GO -> go(c, level, s);
			case FISH -> fish(c, level, s);
			case DELIVER -> deliver(c, level);
		};
	}

	private TaskStatus fetchRod(CompanionEntity c, ServerLevel level) {
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
		if (Stores.withdraw(c, supply.get(), st -> st.is(Items.FISHING_ROD), 1) == 0 && Crafting.nearCraftingTable(c)) {
			Stores.withdraw(c, supply.get(), st -> st.is(Items.STRING), STRING_FOR_ROD);
			Stores.withdraw(c, supply.get(), st -> st.is(Items.STICK), 3);
			Crafting.ensure(c, Items.FISHING_ROD, 1);
		}
		if (!hasRod(c)) {
			return TaskStatus.FAILURE; // string and sticks fetched stay for the tidying
		}
		phase = Phase.GO;
		return TaskStatus.RUNNING;
	}

	private TaskStatus go(CompanionEntity c, ServerLevel level, FishSpots.Spot s) {
		if (!FishSpots.standable(level, s.stand()) || !FishSpots.openWater(level, s.water())) {
			return caught.isEmpty() ? TaskStatus.FAILURE : deliverNext();
		}
		switch (Stores.stand(c, s.stand(), 0.7)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return caught.isEmpty() ? TaskStatus.FAILURE : deliverNext();
			}
			case ARRIVED -> {
			}
		}
		if (c.isInWater()) {
			return TaskStatus.FAILURE; // never fish standing in the water
		}
		if (fighting(c, INTERRUPTED)) {
			return TaskStatus.RUNNING; // on the spot, but no casting while something is after them (the reflexes see to it)
		}
		phase = Phase.FISH;
		cast(c, level, s);
		return TaskStatus.RUNNING;
	}

	private void cast(CompanionEntity c, ServerLevel level, FishSpots.Spot s) {
		ticks = 0;
		biteAt = MIN_WAIT + c.getRandom().nextInt(MAX_WAIT - MIN_WAIT + 1);
		c.actions().equip(st -> st.is(Items.FISHING_ROD));
		c.getLookControl().setLookAt(Vec3.atCenterOf(s.water()));
		c.swingArm();
		level.playSound(null, c.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F);
		splash(level, s.water(), 4);
		if (!said) {
			said = true;
			Speech.say(c, Line.FISHING);
		}
	}

	private TaskStatus fish(CompanionEntity c, ServerLevel level, FishSpots.Spot s) {
		// Only a real interruption means a fresh cast: a fight, or being pushed off the spot. Damage with nobody behind it
		// (hunger every four seconds, poison) does not, or a starving fisher would never wait long enough for a bite.
		if (fighting(c, INTERRUPTED) || c.position().distanceToSqr(Vec3.atBottomCenterOf(s.stand())) > 2.5 * 2.5) {
			phase = Phase.GO; // back to the spot (or the job ends, through the reflexes)
			return TaskStatus.RUNNING;
		}
		c.getLookControl().setLookAt(Vec3.atCenterOf(s.water()));
		ticks++;
		if (ticks > biteAt - 40 && ticks % 5 == 0) {
			level.sendParticles(ParticleTypes.FISHING, s.water().getX() + 0.5, s.water().getY() + 1.0, s.water().getZ() + 0.5, 2, 0.3, 0.0, 0.3, 0.0);
		}
		if (ticks < biteAt) {
			return TaskStatus.RUNNING;
		}
		ItemStack rod = c.getMainHandItem();
		if (!rod.is(Items.FISHING_ROD)) {
			if (!c.actions().equip(st -> st.is(Items.FISHING_ROD))) {
				return caught.isEmpty() ? TaskStatus.FAILURE : deliverNext();
			}
			rod = c.getMainHandItem();
		}
		splash(level, s.water(), 8);
		level.playSound(null, c.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 1.0F);
		c.swingArm();
		for (ItemStack item : roll(level, s.water(), rod)) {
			caught.add(item.copy());
			Stores.give(c, item);
		}
		c.damageMainHandTool(1);
		catches++;
		Camp.data(level.getServer()).addStat("fish_caught", 1);
		if (catches >= CATCHES_PER_RUN || c.backpack().freeSlots() < 2 || !c.actions().has(st -> st.is(Items.FISHING_ROD))) {
			return deliverNext();
		}
		cast(c, level, s);
		return TaskStatus.RUNNING;
	}

	/** The game's fishing loot for one catch at this water with this rod. */
	private static List<ItemStack> roll(ServerLevel level, BlockPos water, ItemStack rod) {
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
			.withParameter(LootContextParams.TOOL, rod)
			.create(LootContextParamSets.FISHING);
		LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
		return table.getRandomItems(params);
	}

	private static void splash(ServerLevel level, BlockPos water, int count) {
		level.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1.0, water.getZ() + 0.5, count, 0.3, 0.1, 0.3, 0.1);
	}

	private TaskStatus deliverNext() {
		phase = Phase.DELIVER;
		return TaskStatus.RUNNING;
	}

	private TaskStatus deliver(CompanionEntity c, ServerLevel level) {
		Optional<BlockPos> supply = Stores.supplyPos(level);
		if (supply.isEmpty()) {
			return catches > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
		}
		switch (Stores.walk(c, supply.get(), 2.5)) {
			case WALKING -> {
				return TaskStatus.RUNNING;
			}
			case FAILED -> {
				return catches > 0 ? TaskStatus.SUCCESS : TaskStatus.FAILURE;
			}
			case ARRIVED -> {
			}
		}
		// Exactly the catch goes in (by kind and number), never the friend's own things.
		for (ItemStack item : caught) {
			Item kind = item.getItem();
			if (kind != Items.FISHING_ROD) {
				Stores.deposit(c, supply.get(), st -> st.is(kind), item.getCount());
			}
		}
		caught.clear();
		return TaskStatus.SUCCESS;
	}

	@Override
	public void stop(CompanionEntity c) {
		c.actions().stopWalking();
		spot = null;
		caught.clear();
		phase = Phase.GO;
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
		return 20 * 60 * 3;
	}
}

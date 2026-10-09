package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.role.farm.EditSteps;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.progress.CampStock;
import io.github.bradley09roberts.hardcorefriends.progress.ProgressPlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Making themselves useful in the Nether while following a player, never straying more than a few blocks from them:
 * picking up useful things lying about (ender pearls, potions, string, obsidian, quartz, blaze rods, nether wart,
 * glowstone dust, magma cream, ghast tears, gold), bartering with piglins (a friend carrying gold tosses an ingot to
 * a calm piglin now and then while the camp wants what piglins trade: pearls, string, fire resistance, obsidian or
 * quartz) and gathering nether wart and soul sand for the camp's brewing, within reason: ripe wart in a fortress,
 * picked and replanted in one go, and soul sand from the open floor of a soul sand valley, never a wart farm's soil
 * (see {@link ExpeditionPolicy}). Never near lava or fire (gold is only tossed where it cannot land in either), never
 * a piglin that is angry, busy admiring gold, or that the player has just hit.
 */
public class NetherHelpGoal extends Goal {
	private enum Job {
		NONE,
		PICK_UP,
		BARTER,
		GATHER
	}

	/** How far from the player a friend goes for any of this. */
	private static final double NEAR_PLAYER = 10;
	/** Past this, the job is dropped and following takes over again. */
	private static final double LEASH = 14;
	private static final int BARTER_EVERY = 20 * 15;
	private static final int GATHER_LOOK_EVERY = 60;
	/** Most nether wart and soul sand one friend carries home from a trip. */
	private static final int WART_CARRIED = 12;
	private static final int SOUL_SAND_CARRIED = 6;
	/** The camp has enough once it owns this many. */
	private static final int WART_ENOUGH = 24;
	private static final int SOUL_SAND_ENOUGH = 12;
	private static final int JOB_TIME = 20 * 15;
	/** At most this many places are checked against the edit rules each look round (nearest first). */
	private static final int GATHER_CHECKS = 6;
	/** A piglin's throw this soon after a friend's gold reached it is the friends' trade (it admires gold for 6 s). */
	private static final long BARTER_WINDOW = 20 * 20;

	/** When each friend last tossed gold to a piglin (game time). */
	private static final Map<CompanionEntity, Long> LAST_BARTER = new WeakHashMap<>();
	/** Piglins a friend tossed gold to, and when (game time): what they throw next is the friends' to pick up. */
	private static final Map<UUID, Long> BARTERED = new HashMap<>();

	private final CompanionEntity c;
	private Job job = Job.NONE;
	private @Nullable ItemEntity item;
	private @Nullable Piglin piglin;
	private @Nullable BlockPos block;
	private int recheck;
	private int lastGatherLook;
	private int ticks;

	public NetherHelpGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (c.level().dimension() != Level.NETHER || --recheck > 0) {
			return false;
		}
		recheck = 20;
		job = choose();
		return job != Job.NONE;
	}

	@Override
	public boolean canContinueToUse() {
		ServerPlayer leader = c.leader();
		return job != Job.NONE && ticks < JOB_TIME && c.getTarget() == null && !c.isRetreating() && leader != null
			&& leader.level() == c.level() && leader.distanceToSqr(c) <= LEASH * LEASH;
	}

	@Override
	public void start() {
		ticks = 0;
	}

	@Override
	public void stop() {
		c.actions().reset();
		job = Job.NONE;
		item = null;
		piglin = null;
		block = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** Something useful to do near the player, or nothing. */
	private Job choose() {
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.FOLLOW || !c.isTeamMember()
			|| c.getTarget() != null || c.isRetreating() || c.isOnFire() || c.getHealth() < c.getMaxHealth() * 0.5F) {
			return Job.NONE;
		}
		ServerPlayer leader = c.leader();
		if (leader == null || leader.level() != level || leader.distanceToSqr(c) > NEAR_PLAYER * NEAR_PLAYER) {
			return Job.NONE;
		}
		item = Pickup.nearest(c, 8, NetherHelpGoal::useful);
		if (item != null && item.distanceToSqr(leader) <= LEASH * LEASH) {
			return Job.PICK_UP;
		}
		piglin = barterPartner(level, leader);
		if (piglin != null) {
			return Job.BARTER;
		}
		if (c.tickCount - lastGatherLook >= GATHER_LOOK_EVERY) {
			lastGatherLook = c.tickCount;
			block = gatherSpot(level);
			if (block != null) {
				return Job.GATHER;
			}
		}
		return Job.NONE;
	}

	/** Things worth taking home from the Nether. */
	static boolean useful(ItemStack s) {
		return s.is(Items.ENDER_PEARL) || s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.STRING)
			|| s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN) || s.is(Items.QUARTZ) || s.is(Items.BLAZE_ROD)
			|| s.is(Items.BLAZE_POWDER) || s.is(Items.NETHER_WART) || s.is(Items.GLOWSTONE_DUST) || s.is(Items.MAGMA_CREAM)
			|| s.is(Items.GHAST_TEAR) || s.is(Items.GOLD_INGOT) || s.is(Items.GOLD_NUGGET) || s.is(Items.FIRE_CHARGE);
	}

	@Override
	public void tick() {
		ticks++;
		switch (job) {
			case PICK_UP -> pickUp();
			case BARTER -> barter();
			case GATHER -> gather();
			case NONE -> {
			}
		}
	}

	private void pickUp() {
		ItemEntity target = item;
		if (target == null || !target.isAlive()) {
			job = Job.NONE;
			return;
		}
		if (c.distanceToSqr(target) <= 1.6 * 1.6) {
			Pickup.take(c, target);
			job = Job.NONE;
			return;
		}
		c.actions().walkToEntity(target, 1.2);
		if (c.actions().isStuck()) {
			job = Job.NONE;
		}
	}

	// ---------------------------------------------------------------- barter

	/** True while the camp wants what piglins give for gold. */
	private static boolean campWantsBarter(MinecraftServer server) {
		CampStock.Snapshot s = CampStock.get(server);
		return ProgressPlan.wants(server, Items.ENDER_PEARL) || ProgressPlan.wants(server, Items.OBSIDIAN)
			|| s.total(Items.STRING) < 16 || s.total(Items.QUARTZ) < 16 || s.fireResistance() < 2;
	}

	/** A calm grown piglin within reach of the player to toss gold to, when it is time and gold is carried. */
	private @Nullable Piglin barterPartner(ServerLevel level, ServerPlayer leader) {
		Long last = LAST_BARTER.get(c);
		if (last != null && level.getGameTime() - last < BARTER_EVERY || !c.backpack().has(s -> s.is(Items.GOLD_INGOT))
			|| !campWantsBarter(level.getServer())) {
			return null;
		}
		Piglin best = null;
		double bestDist = 12 * 12;
		AABB box = c.getBoundingBox().inflate(12, 4, 12);
		for (Piglin p : level.getEntitiesOfClass(Piglin.class, box, Piglin::isAlive)) {
			double d = p.distanceToSqr(c);
			if (d < bestDist && calm(p, leader) && p.distanceToSqr(leader) <= LEASH * LEASH) {
				best = p;
				bestDist = d;
			}
		}
		return best;
	}

	/** Grown, after nobody, not angry, not already admiring gold, and not just hit by the player. */
	private static boolean calm(Piglin p, ServerPlayer leader) {
		if (!p.isAdult() || p.isConverting() || p.getTarget() != null || !p.getOffhandItem().isEmpty()) {
			return false;
		}
		var brain = p.getBrain();
		if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT) || brain.hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)
			|| brain.hasMemoryValue(MemoryModuleType.ADMIRING_DISABLED)) {
			return false;
		}
		boolean playerHitIt = leader.getLastHurtMob() == p && leader.tickCount - leader.getLastHurtMobTimestamp() < 200;
		boolean hurtLately = p.getLastHurtByMob() != null && p.tickCount - p.getLastHurtByMobTimestamp() < 200;
		return !playerHitIt && !hurtLately;
	}

	private void barter() {
		Piglin p = piglin;
		ServerPlayer leader = c.leader();
		if (p == null || leader == null || !p.isAlive() || !calm(p, leader)) {
			job = Job.NONE;
			return;
		}
		c.getLookControl().setLookAt(p, 30.0F, 30.0F);
		if (c.distanceToSqr(p) > 3.5 * 3.5) {
			c.actions().walkToEntity(p, 3.0);
			if (c.actions().isStuck()) {
				job = Job.NONE;
			}
			return;
		}
		c.actions().stopWalking();
		ServerLevel level = (ServerLevel) c.level();
		if (!safeToss(level, p)) {
			job = Job.NONE; // the gold could end up in lava or fire: not this piglin, not now
			return;
		}
		ItemStack gold = c.backpack().take(s -> s.is(Items.GOLD_INGOT), 1);
		if (!gold.isEmpty()) {
			ItemEntity thrown = new ItemEntity(level, c.getX(), c.getEyeY() - 0.3, c.getZ(), gold);
			Vec3 toward = p.position().subtract(c.position()).normalize().scale(0.25);
			thrown.setDeltaMovement(toward.x, 0.2, toward.z);
			thrown.setThrower(c);
			thrown.setPickUpDelay(20);
			level.addFreshEntity(thrown);
			c.swingArm();
			LAST_BARTER.put(c, level.getGameTime());
			BARTERED.values().removeIf(at -> level.getGameTime() - at > BARTER_WINDOW || level.getGameTime() < at);
			BARTERED.put(p.getUUID(), level.getGameTime());
			Speech.say(c, Line.BARTER);
		}
		job = Job.NONE;
	}

	/**
	 * True when an item this piglin threw is the friends' trade: a friend tossed it gold within the last
	 * {@value #BARTER_WINDOW} ticks, and the item appeared after that (a throw for a player's own gold is theirs).
	 */
	static boolean friendsTrade(ServerLevel level, Piglin piglin, ItemEntity item) {
		Long at = BARTERED.get(piglin.getUUID());
		long now = level.getGameTime();
		return at != null && now >= at && now - at <= BARTER_WINDOW && item.getAge() <= now - at;
	}

	/**
	 * True when gold tossed to this piglin lands safely: the piglin stands on firm ground out of lava, and there is no
	 * lava or fire on the way to it, under the way, or round where it stands.
	 */
	private boolean safeToss(ServerLevel level, Piglin p) {
		if (!p.onGround() || p.isInLava() || p.isOnFire()) {
			return false;
		}
		Vec3 from = c.position();
		Vec3 to = p.position();
		int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 2));
		for (int i = 0; i <= steps; i++) {
			BlockPos at = BlockPos.containing(from.lerp(to, (double) i / steps));
			if (hot(level, at) || hot(level, at.below())) {
				return false;
			}
		}
		for (BlockPos q : BlockPos.betweenClosed(p.blockPosition().offset(-1, -1, -1), p.blockPosition().offset(1, 0, 1))) {
			if (hot(level, q)) {
				return false;
			}
		}
		return true;
	}

	/** Lava (or any fluid), fire or an unloaded place. */
	private static boolean hot(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return true;
		}
		var state = level.getBlockState(pos);
		return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK);
	}

	// ---------------------------------------------------------------- gather

	/**
	 * Ripe nether wart to pick (and replant) or soul sand to dig, within five blocks, when the camp is short of either
	 * and this friend has room for more; only what the {@code EXPEDITION} rules allow (see {@link ExpeditionPolicy}).
	 * Wart first, nearest first; only the nearest few places are checked against the rules.
	 */
	private @Nullable BlockPos gatherSpot(ServerLevel level) {
		CampStock.Snapshot s = CampStock.get(level.getServer());
		boolean wantWart = s.total(Items.NETHER_WART) < WART_ENOUGH && c.backpack().count(Items.NETHER_WART) < WART_CARRIED;
		boolean wantSand = s.total(Items.SOUL_SAND) < SOUL_SAND_ENOUGH && c.backpack().count(Items.SOUL_SAND) < SOUL_SAND_CARRIED;
		if (!wantWart && !wantSand || c.backpack().freeSlots() < 1) {
			return null;
		}
		BlockPos here = c.blockPosition();
		List<Spot> found = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(here.offset(-5, -2, -5), here.offset(5, 2, 5))) {
			if (!level.isLoaded(p)) {
				continue;
			}
			var state = level.getBlockState(p);
			boolean wart = wantWart && ripeWart(state);
			boolean sand = wantSand && state.is(Blocks.SOUL_SAND) && level.getBlockState(p.above()).isAir();
			if (wart || sand) {
				found.add(new Spot(p.immutable(), wart, p.distSqr(here) + (wart ? 0 : 4)));
			}
		}
		found.sort(Comparator.comparingDouble(Spot::order));
		for (int i = 0; i < found.size() && i < GATHER_CHECKS; i++) {
			Spot spot = found.get(i);
			if (allowed(spot.wart() ? WorldEditGuard.canTransform(c, spot.pos(), replanted(), WorldEditGuard.Reason.EXPEDITION)
				: WorldEditGuard.canBreak(c, spot.pos(), WorldEditGuard.Reason.EXPEDITION))) {
				return spot.pos();
			}
		}
		return null;
	}

	/** A place to gather from: ripe wart or soul sand, and how soon to try it (nearest first, wart before sand). */
	private record Spot(BlockPos pos, boolean wart, double order) {
	}

	/** True when the edit rules allow it (a pause between edits does not count against it). */
	private static boolean allowed(WorldEditGuard.Verdict v) {
		return v.allowed() || EditSteps.paced(v);
	}

	private static boolean ripeWart(BlockState state) {
		return state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
	}

	/** Nether wart just replanted: its first stage. */
	private static BlockState replanted() {
		return Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 0);
	}

	private void gather() {
		BlockPos at = block;
		if (at == null) {
			job = Job.NONE;
			return;
		}
		ServerLevel level = (ServerLevel) c.level();
		var state = level.getBlockState(at);
		if (!ripeWart(state) && !state.is(Blocks.SOUL_SAND)) {
			job = Job.NONE;
			return;
		}
		if (!c.actions().canReach(at)) {
			c.actions().walkTo(at, 2.0);
			if (c.actions().isStuck()) {
				job = Job.NONE;
			}
			return;
		}
		c.actions().stopWalking();
		if (state.is(Blocks.SOUL_SAND)) {
			Actions.Result r = c.actions().mine(at, WorldEditGuard.Reason.EXPEDITION);
			if (r != Actions.Result.RUNNING) {
				job = Job.NONE;
			}
			return;
		}
		// Ripe wart: picked and replanted in one go, as a player would (one wart of the harvest goes back in).
		List<ItemStack> drops = Block.getDrops(state, level, at, null, c, c.getMainHandItem());
		switch (EditSteps.transform(c, at, replanted(), WorldEditGuard.Reason.EXPEDITION, null)) {
			case DONE -> {
				boolean seedKept = false;
				for (ItemStack drop : drops) {
					if (!seedKept && drop.is(Items.NETHER_WART)) {
						drop.shrink(1);
						seedKept = true;
					}
					ItemStack left = drop.isEmpty() ? ItemStack.EMPTY : c.backpack().insert(drop);
					if (!left.isEmpty()) {
						c.spawnAtLocation(level, left);
					}
				}
				level.playSound(null, at, SoundEvents.NETHER_WART_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
				job = Job.NONE;
			}
			case FAILED -> job = Job.NONE;
			case WAIT -> {
			}
		}
	}

	/** Forgets who bartered when (a server stopping). */
	static void clear() {
		LAST_BARTER.clear();
		BARTERED.clear();
	}
}

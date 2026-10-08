package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.spider.Spider;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Out of reach when cornered: a friend who is badly hurt, has two or more hand-to-hand monsters within
 * {@value #CORNER_RANGE} blocks, and keeps being hit while falling back (so falling back is not working) pillars up
 * {@value #PILLAR} blocks with dirt or cobblestone from their backpack, the old trick zombies cannot follow. They wait
 * up there until the monsters have gone or the sun is up, then come down, taking the blocks back. Never against
 * archers (they would shoot), spiders (they climb) or creepers, never in water, and never within six blocks of
 * anything player-built. A reflex above falling back, so nothing interrupts it once begun.
 */
public final class PillarGoal extends Goal {
	private static final double CORNER_RANGE = 4;
	private static final int PILLAR = 3;
	/** Blows taken within this many ticks that show falling back is not working. */
	private static final int HIT_WINDOW = 60;
	/** How long the ground must be clear before coming down, in ticks. */
	private static final int CLEAR_FOR = 100;
	/** The longest a friend waits up a pillar, in ticks (then they come down whatever). */
	private static final int MAX_WAIT = 20 * 60 * 6;
	/** The game ticks of the last two blows each friend took from a monster. */
	private static final Map<CompanionEntity, long[]> HITS = new WeakHashMap<>();

	private enum Stage {
		UP,
		WAIT,
		DOWN,
		DONE
	}

	private final CompanionEntity c;
	private final Climber climber = new Climber();
	private Stage stage = Stage.DONE;
	private int placed;
	private int waited;
	private int clearFor;

	public PillarGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
	}

	/** Remembers a blow from a monster (a {@code CompanionEvents.HURT} listener; changes nothing). */
	static float noteHit(CompanionEntity companion, ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity attacker && Threats.isThreat(attacker)) {
			long[] last = HITS.computeIfAbsent(companion, k -> new long[] {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
			last[0] = last[1];
			last[1] = level.getGameTime();
		}
		return amount;
	}

	private static boolean hitTwiceLately(CompanionEntity companion, long now) {
		long[] last = HITS.get(companion);
		return last != null && now - last[0] <= HIT_WINDOW && now - last[1] <= HIT_WINDOW / 2;
	}

	@Override
	public boolean canUse() {
		if (c.tickCount % 5 != 0 || !(c.level() instanceof ServerLevel level) || !c.isTeamMember()) {
			return false;
		}
		if (!c.badlyHurt() || !c.onGround() || c.isInWater() || c.isPassenger() || Shelters.blocksCarried(c) < PILLAR) {
			return false;
		}
		if (!hitTwiceLately(c, level.getGameTime())) {
			return false;
		}
		List<LivingEntity> near = Threats.around(c, 24);
		int melee = 0;
		for (LivingEntity e : near) {
			if (Threats.isRanged(e) || e instanceof Spider || e instanceof Creeper && e.distanceToSqr(c) < 10 * 10) {
				return false; // up a pillar is no escape from these
			}
			if (e.distanceToSqr(c) <= CORNER_RANGE * CORNER_RANGE) {
				melee++;
			}
		}
		if (melee < 2) {
			return false;
		}
		BlockPos feet = c.blockPosition();
		for (int dy = 2; dy <= PILLAR + 1; dy++) {
			BlockPos p = feet.above(dy);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) {
				return false; // no room overhead
			}
		}
		return !WorldEditGuard.looksPlayerBuilt(level, feet, SurvivalPolicies.BUILD_GAP, Camp.data(level.getServer()));
	}

	@Override
	public void start() {
		stage = Stage.UP;
		placed = 0;
		waited = 0;
		clearFor = 0;
		climber.reset();
		c.getNavigation().stop();
		c.setTarget(null);
		Speech.say(c, Line.CORNERED);
	}

	@Override
	public boolean canContinueToUse() {
		return stage != Stage.DONE && c.isAlive() && !c.isInWater();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ServerLevel level = (ServerLevel) c.level();
		switch (stage) {
			case UP -> {
				switch (climber.step(c, level)) {
					case PLACED -> {
						BlockPos p = climber.lastPlaced();
						if (p != null) {
							Shelters.addPiece(c, p);
						}
						if (++placed >= PILLAR) {
							stage = Stage.WAIT;
						}
					}
					case NO_BLOCKS, BLOCKED -> stage = placed > 0 ? Stage.WAIT : Stage.DONE;
					case CLIMBING -> {
					}
				}
			}
			case WAIT -> waitUp(level);
			case DOWN -> comeDown(level);
			case DONE -> {
			}
		}
	}

	private void waitUp(ServerLevel level) {
		c.getNavigation().stop();
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		LivingEntity nearest = Threats.nearest(c, 16);
		if (nearest != null) {
			c.getLookControl().setLookAt(nearest);
		}
		waited++;
		boolean meleeClose = false;
		for (LivingEntity e : Threats.around(c, 8)) {
			if (!Threats.isRanged(e)) {
				meleeClose = true;
				break;
			}
		}
		clearFor = meleeClose ? 0 : clearFor + 1;
		boolean shot = c.ticksSinceDamaged() < 5;
		if (clearFor >= CLEAR_FOR || !Camp.isNight(level) && waited > 200 || waited > MAX_WAIT || shot && !meleeClose) {
			stage = Stage.DOWN;
		}
	}

	/** Takes the pillar back block by block from the top, standing on it, until the ground is reached. */
	private void comeDown(ServerLevel level) {
		Actions actions = c.actions();
		BlockPos below = c.blockPosition().below();
		if (!c.onGround()) {
			return; // still dropping onto the next block
		}
		if (!Shelters.isPiece(c, below)) {
			actions.cancelMining();
			stage = Stage.DONE;
			return;
		}
		switch (actions.mine(below, WorldEditGuard.Reason.SURVIVAL)) {
			case DONE -> Shelters.removePiece(c, below);
			case FAILED -> {
				Shelters.removePiece(c, below); // cannot take it back: left standing, and the friend climbs down as best they can
				stage = Stage.DONE;
			}
			case RUNNING -> {
			}
		}
	}

	@Override
	public void stop() {
		c.actions().cancelMining();
		climber.reset();
		stage = Stage.DONE;
	}

	/** Forgets every remembered blow (a server stopping). */
	static void clear() {
		HITS.clear();
	}
}

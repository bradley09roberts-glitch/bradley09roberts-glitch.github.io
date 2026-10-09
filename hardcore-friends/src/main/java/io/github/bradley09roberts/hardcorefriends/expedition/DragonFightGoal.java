package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.combat.Archery;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A friend's part in the dragon fight, while following a player in the End. Archers walk to a spot the team worked
 * out ({@link DragonFight}) and shoot the end crystals on the towers with a full draw (nobody within
 * {@value DragonFight#BLAST_CLEARANCE} blocks of a crystal when it goes up), and shoot the dragon while it flies,
 * leading it a little; friends with a sword or axe strike it while it sits on the portal (arrows do nothing then).
 * It gives way to a fight with anything else, to falling back, and to getting out of the dragon's breath.
 */
public class DragonFightGoal extends Goal {
	private enum Mode {
		NONE,
		CRYSTAL,
		DRAGON,
		STRIKE
	}

	private static final int DRAW_TICKS = 20;
	private static final int RELOAD_TICKS = 30;
	private static final int STRIKE_TICKS = 16;
	/** How far from the leader a friend goes for the fight. */
	private static final double LEASH = 64;
	/** A shot crystal that goes up within this long was this friend's. */
	private static final int CREDIT_TICKS = 80;
	/** How close a blade must be to a part of the dragon to strike it. */
	private static final double STRIKE_REACH = 3.2;

	private final CompanionEntity c;
	private Mode mode = Mode.NONE;
	private @Nullable EndCrystal crystal;
	private @Nullable BlockPos spot;
	private int recheck;
	private int reload;
	private int strikeCooldown;
	private int stuck;
	private @Nullable EndCrystal shotAt;
	private int shotTicks;
	private @Nullable Item heldBefore;

	public DragonFightGoal(CompanionEntity companion) {
		this.c = companion;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (c.level().dimension() != Level.END || --recheck > 0) {
			return false;
		}
		recheck = 10;
		mode = decide();
		return mode != Mode.NONE;
	}

	@Override
	public boolean canContinueToUse() {
		return mode != Mode.NONE && c.getTarget() == null && !c.isRetreating() && c.isAlive();
	}

	@Override
	public void start() {
		reload = 0;
		stuck = 0;
		ItemStack hand = c.getMainHandItem();
		heldBefore = hand.isEmpty() || hand.is(Items.BOW) ? null : hand.getItem();
	}

	@Override
	public void stop() {
		lowerBow();
		c.actions().stopWalking();
		Item before = heldBefore;
		heldBefore = null;
		if (before != null && c.getMainHandItem().is(Items.BOW)) {
			c.actions().equip(s -> s.is(before));
		}
		mode = Mode.NONE;
		crystal = null;
		spot = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** What to do now (see the class description). */
	private Mode decide() {
		if (!(c.level() instanceof ServerLevel level) || c.mode() != CompanionMode.FOLLOW || c.getTarget() != null
			|| c.isRetreating() || !DragonFight.active(level)) {
			return Mode.NONE;
		}
		ServerPlayer leader = c.leader();
		if (leader == null || !leader.isAlive() || leader.level() != level || leader.distanceToSqr(c) > LEASH * LEASH) {
			return Mode.NONE;
		}
		DragonFight.Climb climb = DragonFight.climb();
		if (climb != null && climb.friend().equals(c.getUUID())) {
			return Mode.NONE; // up a pillar to a cage: that is the climb's job
		}
		EnderDragon dragon = DragonFight.dragon();
		if (Archery.canShoot(c) && stuck < 3) {
			EndCrystal best = null;
			double bestDist = Double.MAX_VALUE;
			for (EndCrystal e : DragonFight.crystals()) {
				DragonFight.ShootSpot s = DragonFight.spotFor(e);
				if (s == null || climb != null && climb.crystal().equals(e.getUUID())
					|| s.feet().distSqr(leader.blockPosition()) > LEASH * LEASH) {
					continue;
				}
				double d = s.feet().distSqr(c.blockPosition());
				if (d < bestDist) {
					bestDist = d;
					best = e;
				}
			}
			if (best != null) {
				crystal = best;
				DragonFight.ShootSpot s = DragonFight.spotFor(best);
				spot = s != null ? s.feet() : null;
				return spot != null ? Mode.CRYSTAL : Mode.NONE;
			}
			if (dragon != null && !DragonFight.perched(dragon) && dragon.distanceToSqr(c) < 64 * 64) {
				return Mode.DRAGON;
			}
		}
		if (dragon != null && DragonFight.perched(dragon) && c.isArmed() && c.getHealth() > c.getMaxHealth() * 0.5F
			&& dragon.distanceToSqr(c) < 48 * 48) {
			return Mode.STRIKE;
		}
		return Mode.NONE;
	}

	@Override
	public void tick() {
		if (!(c.level() instanceof ServerLevel level)) {
			return;
		}
		if (--recheck <= 0) {
			recheck = 10;
			Mode next = decide();
			if (next != mode) {
				lowerBow();
				c.actions().stopWalking();
				mode = next;
			}
		}
		watchShot();
		if (reload > 0) {
			reload--;
		}
		if (strikeCooldown > 0) {
			strikeCooldown--;
		}
		switch (mode) {
			case CRYSTAL -> shootCrystal(level);
			case DRAGON -> shootDragon(level);
			case STRIKE -> strike(level);
			case NONE -> {
			}
		}
	}

	/** Says so when the crystal this friend shot goes up. */
	private void watchShot() {
		EndCrystal shot = shotAt;
		if (shot == null) {
			return;
		}
		if (!shot.isAlive() || shot.isRemoved()) {
			Speech.say(c, Line.CRYSTAL_DOWN);
			shotAt = null;
		} else if (++shotTicks > CREDIT_TICKS) {
			shotAt = null;
		}
	}

	// ---------------------------------------------------------------- crystals

	private void shootCrystal(ServerLevel level) {
		EndCrystal target = crystal;
		BlockPos at = spot;
		if (target == null || at == null || !target.isAlive()) {
			mode = Mode.NONE;
			return;
		}
		if (c.blockPosition().distSqr(at) > 2) {
			lowerBow();
			if (c.actions().walkTo(at, 0.8)) {
				return;
			}
			if (c.actions().isStuck()) {
				c.actions().stopWalking();
				stuck++;
				mode = Mode.NONE;
			}
			return;
		}
		c.actions().stopWalking();
		Vec3 aimAt = target.getBoundingBox().getCenter();
		c.getLookControl().setLookAt(aimAt);
		if (!DragonFight.clearToBlow(level, target)) {
			lowerBow(); // somebody is too close to it: hold fire
			return;
		}
		draw(level, () -> Archery.loose(c, level, BowItem.getPowerForTime(c.getTicksUsingItem()), DragonFight.FULL_DRAW, 1.0F, null,
			arrow -> aimFrom(arrow.position(), aimAt)), target);
	}

	// ------------------------------------------------------------------ dragon

	private void shootDragon(ServerLevel level) {
		EnderDragon dragon = DragonFight.dragon();
		if (dragon == null || DragonFight.perched(dragon)) {
			mode = Mode.NONE;
			return;
		}
		c.actions().stopWalking();
		Vec3 aimAt = lead(dragon);
		c.getLookControl().setLookAt(aimAt);
		if (!lineClear(level, c.getEyePosition(), aimAt)) {
			lowerBow();
			return;
		}
		draw(level, () -> Archery.loose(c, level, BowItem.getPowerForTime(c.getTicksUsingItem()), DragonFight.FULL_DRAW, 1.5F,
			DragonFight.dragonArrows(), arrow -> aimFrom(arrow.position(), aimAt)), null);
	}

	/** Where the dragon's body will be when an arrow gets there, roughly (it flies fast). */
	private Vec3 lead(EnderDragon dragon) {
		Vec3 body = dragon.getBoundingBox().getCenter();
		Vec3 motion = dragon.getDeltaMovement();
		Vec3 aim = body;
		for (int i = 0; i < 2; i++) {
			double ticks = c.getEyePosition().distanceTo(aim) / DragonFight.FULL_DRAW;
			aim = body.add(motion.scale(ticks));
		}
		return aim;
	}

	/** True when no player or friend stands within a couple of blocks of the straight line to the dragon. */
	private boolean lineClear(ServerLevel level, Vec3 from, Vec3 to) {
		AABB box = new AABB(from, to).inflate(3);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && box.contains(p.position()) && distanceToLine(p.getBoundingBox().getCenter(), from, to) < 2.5) {
				return false;
			}
		}
		for (CompanionEntity other : Companions.in(level)) {
			if (other != c && box.contains(other.position()) && distanceToLine(other.getBoundingBox().getCenter(), from, to) < 2.5) {
				return false;
			}
		}
		return true;
	}

	private static double distanceToLine(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1.0E-6) {
			return p.distanceTo(a);
		}
		double t = Math.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
		return p.distanceTo(a.add(ab.scale(t)));
	}

	// ------------------------------------------------------------------ blades

	private void strike(ServerLevel level) {
		EnderDragon dragon = DragonFight.dragon();
		if (dragon == null || !DragonFight.perched(dragon)) {
			mode = Mode.NONE;
			return;
		}
		EnderDragonPart part = closestPart(dragon);
		Vec3 eye = c.getEyePosition();
		double reach2 = part.getBoundingBox().distanceToSqr(eye);
		if (reach2 > STRIKE_REACH * STRIKE_REACH) {
			if (c.getNavigation().isDone() || c.tickCount % 10 == 0) {
				Vec3 to = part.position();
				c.getNavigation().moveTo(to.x, to.y, to.z, 1.2);
			}
			return;
		}
		c.getNavigation().stop();
		c.getLookControl().setLookAt(part.position());
		if (strikeCooldown <= 0) {
			strikeCooldown = STRIKE_TICKS;
			DragonFight.strike(c, level, dragon, part);
		}
	}

	/** The head if within reach, otherwise the nearest part of the dragon. */
	private EnderDragonPart closestPart(EnderDragon dragon) {
		Vec3 eye = c.getEyePosition();
		if (dragon.head.getBoundingBox().distanceToSqr(eye) <= STRIKE_REACH * STRIKE_REACH) {
			return dragon.head;
		}
		EnderDragonPart best = dragon.head;
		double bestDist = Double.MAX_VALUE;
		for (EnderDragonPart part : dragon.getSubEntities()) {
			double d = part.getBoundingBox().distanceToSqr(eye);
			if (d < bestDist) {
				bestDist = d;
				best = part;
			}
		}
		return best;
	}

	// -------------------------------------------------------------------- bow

	/** Holds the bow, draws it, and looses when fully drawn (then a pause), as a player would. */
	private void draw(ServerLevel level, java.util.function.BooleanSupplier loose, @Nullable EndCrystal target) {
		if (!c.getMainHandItem().is(Items.BOW) && !Archery.holdBow(c)) {
			mode = Mode.NONE;
			return;
		}
		if (c.isUsingItem()) {
			if (c.getUsedItemHand() != InteractionHand.MAIN_HAND) {
				c.stopUsingItem();
				return;
			}
			if (c.getTicksUsingItem() >= DRAW_TICKS) {
				boolean shot = loose.getAsBoolean();
				c.stopUsingItem();
				reload = RELOAD_TICKS;
				if (shot && target != null) {
					shotAt = target;
					shotTicks = 0;
				}
				if (!shot) {
					mode = Mode.NONE; // out of arrows
				}
			}
			return;
		}
		if (reload <= 0) {
			c.startUsingItem(InteractionHand.MAIN_HAND);
			c.markEngaged();
		}
	}

	private void lowerBow() {
		if (c.isUsingItem() && c.getUsedItemHand() == InteractionHand.MAIN_HAND) {
			c.stopUsingItem();
		}
	}

	/** The aim for a full draw from {@code from} to {@code to}: the flattest arc, or straight at it if out of reach. */
	private static Vec3 aimFrom(Vec3 from, Vec3 to) {
		Vec3 dir = Ballistics.aim(from, to, DragonFight.FULL_DRAW);
		return dir != null ? dir : to.subtract(from);
	}
}

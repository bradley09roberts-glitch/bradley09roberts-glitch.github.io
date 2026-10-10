package io.github.bradley09roberts.hardcorefriends.combat;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.goal.Reach;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Role;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;

/**
 * Who shoots what, and the shot itself. Anyone carrying a bow and arrows may shoot ({@link #canShoot}, and only while
 * {@code friendsUseBows} is on):
 * <ul>
 * <li>at skeletons, strays, witches, blazes, pillagers, phantoms, ghasts and breezes, which are no use chasing with a
 * blade, and at creepers from beyond their blast ({@value #CREEPER_DISTANCE} blocks), which is better than running;</li>
 * <li>at any hostile when they are not a melee fighter (no sword or axe) or are hurt, or when they are Scout or Sage,
 * who prefer the bow;</li>
 * <li>at any hostile nobody can walk to (on a ledge, behind a fence, in the air). Aegis prefers his blade and only
 * shoots what he cannot reach, or a creeper.</li>
 * </ul>
 * A healthy friend shoots from {@value #POINT_BLANK} blocks out; a hurt one keeps {@value #HURT_DISTANCE} blocks between
 * them and anything that fights hand to hand, and backs off when it comes closer. The shot is built the way a skeleton
 * builds its own ({@code AbstractSkeleton.performRangedAttack}): the arrow belongs to the friend, is aimed above the
 * target to allow for its drop, and uses the bow's enchantments; an arrow is used up unless the bow has Infinity.
 */
public final class Archery {
	/** How far a friend shoots from. Skeletons shoot from 15. */
	public static final double BOW_RANGE = 16;
	/** Closer than this a friend with a blade uses it instead; one without backs away. */
	public static final double POINT_BLANK = 3;
	/** A hurt friend keeps at least this far from anything that fights hand to hand. */
	public static final double HURT_DISTANCE = 6;
	/** Creepers are shot from beyond this (the reflexes keep friends out of a creeper's blast anyway). */
	public static final double CREEPER_DISTANCE = 7;
	/** A shot target that slips further than this is followed, not shot at. */
	public static final double CHASE_RANGE = BOW_RANGE + 4;
	/** How true a friend's aim is (a skeleton's is 2 to 10 by difficulty: lower is truer). */
	private static final float UNCERTAINTY = 3.0F;
	/** The arrow's speed, as a skeleton's. */
	private static final float VELOCITY = 1.6F;
	/** New paths a friend may work out per round for choosing bow or blade and whom to help ({@link #pathTo}). */
	private static final int ROUND_PATHS = 1;
	private static final int ROUND_TICKS = 10;
	/** Per friend: {round start, paths worked out in it}. Weak, so unloaded friends are forgotten. */
	private static final Map<CompanionEntity, long[]> PATH_ROUNDS = new WeakHashMap<>();
	/**
	 * The feature packages' say that a friend holds a post and shoots whatever they would fight rather than leave it to
	 * close in with a blade: a guard up at the village watchtower's lookout (the defence package). Asked only for a
	 * friend who can shoot.
	 */
	public static final List<Predicate<CompanionEntity>> HOLDS_POST = new CopyOnWriteArrayList<>();

	private Archery() {
	}

	/** Bows are allowed (config), and this friend carries a bow and at least one arrow. */
	public static boolean canShoot(CompanionEntity c) {
		if (!FriendsConfig.get().friendsUseBows) {
			return false;
		}
		boolean bow = c.getMainHandItem().is(Items.BOW) || c.backpack().has(s -> s.is(Items.BOW));
		return bow && c.backpack().has(s -> s.is(ItemTags.ARROWS));
	}

	/** Mobs that are no use chasing with a blade: they shoot, throw or fly. */
	public static boolean isShootTarget(LivingEntity e) {
		return e instanceof AbstractSkeleton || e instanceof Witch || e instanceof Blaze || e instanceof Pillager
			|| e instanceof Phantom || e instanceof Ghast || e instanceof Breeze || Threats.isRanged(e);
	}

	/** Scout and Sage would rather shoot than close in (and newcomers who work like them). */
	public static boolean prefersBowByNature(CompanionEntity c) {
		Role role = c.friendId().role();
		return role == Role.EXPLORER || role == Role.STRATEGIST;
	}

	/**
	 * Whether this friend would shoot at this threat at all, from a good distance (see the class description). Cheap
	 * unless a path has to be worked out (only for a melee fighter facing a hand-to-hand threat, cached, and at most
	 * one new one a friend every half second: {@link #pathTo}).
	 */
	public static boolean wouldShoot(CompanionEntity c, LivingEntity threat) {
		if (!Threats.isThreat(threat) || c.isRetreating() || !canShoot(c)) {
			return false;
		}
		for (Predicate<CompanionEntity> post : HOLDS_POST) {
			if (post.test(c)) {
				return true; // up on a post: shoot from there rather than leave it
			}
		}
		if (c.isFighter()) {
			return threat instanceof Creeper || outOfMeleeReach(c, threat); // Aegis prefers his blade
		}
		if (threat instanceof Creeper || isShootTarget(threat)) {
			return true;
		}
		if (!c.isArmed() || !c.isHealthy() || prefersBowByNature(c)) {
			return true;
		}
		return outOfMeleeReach(c, threat);
	}

	/** True when nobody could get at it with a blade: in the air, or no whole path leads to it. */
	private static boolean outOfMeleeReach(CompanionEntity c, LivingEntity threat) {
		if (!threat.onGround() && !threat.isInLiquid() && threat.getY() - c.getY() > 2.5) {
			return true;
		}
		return pathTo(c, threat) == Reach.Answer.NO;
	}

	/**
	 * {@link Reach#check}, but a friend works out at most {@value #ROUND_PATHS} new path every {@value #ROUND_TICKS}
	 * ticks for these choices (bow or blade, which threat to help with); remembered answers are free. These are asked
	 * for every threat in a look round and, through {@code canStandAndFight}, every couple of ticks by the reflexes, so
	 * without a cap a big fight would cost several path searches a tick. Past the cap the answer is {@code UNKNOWN}
	 * (taken as "can get there") until the next round.
	 */
	static Reach.Answer pathTo(CompanionEntity c, LivingEntity threat) {
		Reach.Answer known = Reach.known(c, threat);
		if (known != null) {
			return known;
		}
		long now = c.level().getGameTime();
		long[] round = PATH_ROUNDS.computeIfAbsent(c, k -> new long[] {Long.MIN_VALUE, 0});
		if (now - round[0] >= ROUND_TICKS || now < round[0]) {
			round[0] = now;
			round[1] = 0;
		}
		if (round[1] >= ROUND_PATHS) {
			return Reach.Answer.UNKNOWN;
		}
		round[1]++;
		return Reach.check(c, threat);
	}

	/** The closest this friend shoots this threat from: nearer than that they fight hand to hand or back away. */
	public static double minDistance(CompanionEntity c, LivingEntity threat) {
		if (threat instanceof Creeper) {
			return CREEPER_DISTANCE;
		}
		if (!c.isHealthy() && !isShootTarget(threat)) {
			return HURT_DISTANCE;
		}
		return POINT_BLANK;
	}

	/** True when this friend would shoot this threat from where they stand now. */
	public static boolean prefersBow(CompanionEntity c, LivingEntity threat) {
		double d = c.distanceTo(threat);
		return d > minDistance(c, threat) && d <= CHASE_RANGE && wouldShoot(c, threat);
	}

	/**
	 * True when the bow lets this friend stand up to the threat here (for {@link CompanionEntity#canStandAndFight}):
	 * they would shoot it, and it is within bow range but not too close.
	 */
	public static boolean standsWithBow(CompanionEntity c, LivingEntity threat) {
		double d = c.distanceTo(threat);
		return d > minDistance(c, threat) && d <= BOW_RANGE && wouldShoot(c, threat);
	}

	/** How far this friend goes after a threat they would shoot (0 when they would not), so it is not dropped. */
	public static double reach(CompanionEntity c, LivingEntity threat) {
		return wouldShoot(c, threat) ? CHASE_RANGE : 0;
	}

	/** Puts a carried bow in the main hand (the held tool goes into the backpack). */
	public static boolean holdBow(CompanionEntity c) {
		return c.actions().equip(s -> s.is(Items.BOW));
	}

	/**
	 * Makes the arrow for one shot instead of the usual one, for a feature that needs an arrow of its own (the
	 * expedition package's arrows that can hurt the ender dragon). Returns null to let the usual arrow be made.
	 */
	public interface ArrowMaker {
		@Nullable AbstractArrow make(CompanionEntity c, ServerLevel level, ItemStack projectile, ItemStack bow, float power);
	}

	/** Works out where an arrow is aimed once it exists (it starts just below the friend's eyes). */
	public interface Aim {
		Vec3 direction(AbstractArrow arrow);
	}

	/**
	 * Looses one arrow from the bow in the main hand at the target, as a skeleton does, with {@code power} from the
	 * draw time ({@code BowItem.getPowerForTime}). Takes the arrow from the backpack (plain arrows first) unless the bow
	 * has Infinity. Returns false if there was no bow or arrow.
	 */
	public static boolean shoot(CompanionEntity c, ServerLevel level, LivingEntity target, float power) {
		return loose(c, level, power, VELOCITY, UNCERTAINTY, null, arrow -> {
			double xd = target.getX() - c.getX();
			double yd = target.getY(0.3333333333333333) - arrow.getY();
			double zd = target.getZ() - c.getZ();
			double flat = Math.sqrt(xd * xd + zd * zd);
			return new Vec3(xd, yd + flat * 0.2F, zd);
		});
	}

	/**
	 * Looses one arrow from the bow in the main hand in the direction {@code aim} gives, at {@code velocity} (a
	 * skeleton's is {@value #VELOCITY}, a player's full draw 3.0) and with {@code uncertainty} (lower is truer), made
	 * by {@code maker} if one is given. The ammunition, the bow's wear and enchantments, and picking a missed arrow up
	 * again work as for {@link #shoot}. Returns false if there was no bow or arrow.
	 */
	public static boolean loose(CompanionEntity c, ServerLevel level, float power, float velocity, float uncertainty,
		@Nullable ArrowMaker maker, Aim aim) {
		ItemStack bow = c.getMainHandItem();
		if (!bow.is(Items.BOW) || bow.isEmpty()) {
			return false;
		}
		int slot = c.backpack().slotOf(s -> s.is(Items.ARROW));
		if (slot < 0) {
			slot = c.backpack().slotOf(s -> s.is(ItemTags.ARROWS));
		}
		if (slot < 0) {
			return false;
		}
		ItemStack ammo = c.backpack().get(slot);
		ItemStack projectile = ammo.copyWithCount(1);
		boolean used = EnchantmentHelper.processAmmoUse(level, bow, ammo, 1) > 0;
		if (used) {
			ammo.shrink(1);
			if (ammo.isEmpty()) {
				c.backpack().removeSlot(slot);
			} else {
				c.backpack().container().setChanged();
			}
		} else {
			projectile.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE); // Infinity: nothing to pick up
		}
		AbstractArrow arrow = maker == null ? null : maker.make(c, level, projectile, bow, power);
		if (arrow == null) {
			arrow = ProjectileUtil.getMobArrow(c, projectile, power, bow);
		}
		if (used) {
			arrow.pickup = AbstractArrow.Pickup.ALLOWED; // a real arrow that missed can be picked up again
		}
		Vec3 direction = aim.direction(arrow);
		Projectile.spawnProjectileUsingShoot(arrow, level, projectile, direction.x, direction.y, direction.z, velocity, uncertainty);
		c.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (c.getRandom().nextFloat() * 0.4F + 0.8F));
		bow.hurtAndBreak(1, c, EquipmentSlot.MAINHAND);
		c.markEngaged();
		return true;
	}
}

package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.CatSitOnBlockGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.civic.Homes;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * What a pet of the camp does, worked out once a second for each loaded pet. By day it follows its owner while the
 * owner is about the camp (a child's pet sticks closest), and when the owner is away (down the mine, on a trip,
 * following a player) or asleep it keeps about the camp centre; the owner calls it by name when it is far off. At night
 * it goes home and sits beside its owner's bed (their house in the village, else the cabin or the camp centre) until
 * morning. A pet that is hurt or fighting is left to it, and a pet on a lead or ridden is left alone altogether.
 *
 * <p>The pet's own goals are changed once when it loads ({@link #install}): vanilla's following, which teleports a pet
 * to its owner wherever they are (and could load chunks to do it), is replaced by {@link PetMoveGoal}, which only
 * fetches a pet within the camp; vanilla's panicking teleport is replaced by a plain panic; cats no longer sit on
 * chests (the camp chest is shared with the players); pets do not breed; and a dog's vanilla targeting (its owner's
 * target, anyone who hits it, players it is angry at, any skeleton) is replaced by {@link PetGuardGoal}, which only
 * ever goes for monsters after its owner or itself, never near a creeper, while a dog also keeps out of a creeper's way.
 */
final class PetBrain {
	/** What the pet is to do. */
	enum Plan {
		/** Nothing of ours: vanilla wandering, fighting or panicking. */
		NONE,
		/** Keep close to the owner. */
		FOLLOW,
		/** Walk to a spot (home at night, back to the camp centre by day). */
		GO_TO,
		/** Sit at home for the night. */
		SIT
	}

	/** One pet's working state (not saved: worked out afresh once a second). */
	static final class State {
		Plan plan = Plan.NONE;
		@Nullable CompanionEntity owner;
		@Nullable BlockPos spot;
		/** How close counts as there (a spot) or close enough (following). */
		double arrive = 2.0;
		/** How far off the owner may get before the pet follows. */
		double start = 6.0;
		double speed = 1.0;
		/** Game time until which the pet runs, having been called. */
		long calledUntil;
		boolean sitByUs;
		@Nullable BlockPos nightSpot;
		long nightSpotAt = Long.MIN_VALUE / 2;
	}

	/** By day with its owner away, a pet keeps within this many blocks of the camp centre. */
	private static final int ROAM = 12;
	/** Distance at which the owner calls the pet over by name. */
	private static final double CALL_DISTANCE = 10;
	/** The owner counts as about the camp within this many blocks beyond its edge (more once already following). */
	private static final int FOLLOW_MARGIN = 4;
	private static final int KEEP_FOLLOWING_MARGIN = 16;
	/** A pet that cannot find its way is only fetched while within this many blocks of the camp's edge. */
	private static final int NEAR_CAMP = 48;

	private static final Map<TamableAnimal, State> STATES = new WeakHashMap<>();
	private static final Set<TamableAnimal> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());

	private PetBrain() {
	}

	static void clear() {
		STATES.clear();
		INSTALLED.clear();
	}

	static State state(TamableAnimal pet) {
		return STATES.computeIfAbsent(pet, p -> new State());
	}

	static @Nullable State peek(TamableAnimal pet) {
		return STATES.get(pet);
	}

	// ----------------------------------------------------------------- goals

	/** Gives a pet of the camp its goals in place of vanilla's following and targeting (once per loaded entity). */
	static void install(TamableAnimal pet) {
		if (!INSTALLED.add(pet)) {
			return;
		}
		pet.goalSelector.removeAllGoals(g -> g instanceof FollowOwnerGoal || g instanceof BreedGoal
			|| g instanceof CatSitOnBlockGoal || g instanceof TamableAnimal.TamableAnimalPanicGoal);
		pet.goalSelector.addGoal(1, pet instanceof Wolf
			? new PanicGoal(pet, 1.5, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES)
			: new PanicGoal(pet, 1.5));
		pet.goalSelector.addGoal(6, new PetMoveGoal(pet));
		pet.targetSelector.removeAllGoals(g -> true);
		if (pet instanceof Wolf wolf) {
			pet.goalSelector.addGoal(3, new AvoidEntityGoal<>(wolf, Creeper.class, 6.0F, 1.0, 1.3));
			pet.targetSelector.addGoal(1, new PetGuardGoal(wolf));
		}
		if (pet.getTarget() != null && !PetGuardGoal.fair(pet, pet.getTarget())) {
			pet.setTarget(null);
		}
	}

	// ----------------------------------------------------------------- think

	/** Works out what the pet is to do (once a second, from the server tick). */
	static void think(ServerLevel level, TamableAnimal pet, PetsData.Pet record) {
		State s = state(pet);
		long now = level.getGameTime();
		// A player may give a pet a new name with a name tag: the camp calls it by that from then on.
		if (pet.hasCustomName() && pet.getCustomName() != null) {
			String name = pet.getCustomName().getString();
			if (!name.isBlank() && !name.equals(record.name) && name.length() <= 32) {
				record.name = name;
			}
		}
		if (!pet.isTame() || pet.isLeashed() || pet.isPassenger() || pet.isVehicle()) {
			s.plan = Plan.NONE;
			return;
		}
		CompanionEntity owner = record.owner == null ? null
			: level.getEntity(record.owner) instanceof CompanionEntity c && c.isAlive() ? c : null;
		s.owner = owner;
		Optional<BlockPos> centre = Camp.center(level);
		boolean busy = pet.getTarget() != null
			|| pet.getLastHurtByMob() != null && pet.tickCount - pet.getLastHurtByMobTimestamp() < 100;
		if (busy) {
			releaseSit(pet, s); // up to defend itself, or to run
			s.plan = Plan.NONE;
			return;
		}
		if (centre.isEmpty()) {
			// Away from the camp's dimension (pushed through a portal): stay with the owner if they are here, else wait.
			releaseSit(pet, s);
			s.plan = owner != null && !owner.isAsleep() ? Plan.FOLLOW : Plan.NONE;
			s.arrive = 3.0;
			s.start = 6.0;
			s.speed = 1.0;
			return;
		}
		if (Camp.isNightTime(level)) {
			BlockPos spot = nightSpot(level, pet, record, owner, s, now);
			if (pet.position().distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) <= 2.0 * 2.0) {
				sit(pet, s);
				s.plan = Plan.SIT;
			} else {
				releaseSit(pet, s);
				s.plan = Plan.GO_TO;
				s.spot = spot;
				s.arrive = 1.5;
				s.speed = 1.0;
			}
			return;
		}
		releaseSit(pet, s);
		s.nightSpot = null;
		int margin = s.plan == Plan.FOLLOW ? KEEP_FOLLOWING_MARGIN : FOLLOW_MARGIN;
		if (owner != null && owner.level() == level && !owner.isAsleep() && aboutCamp(level, centre.get(), owner.blockPosition(), margin)) {
			boolean already = s.plan == Plan.FOLLOW;
			s.plan = Plan.FOLLOW;
			s.arrive = owner.isChild() ? 2.0 : 3.0;
			s.start = owner.isChild() ? 3.5 : 6.0;
			if (!already && pet.distanceTo(owner) > CALL_DISTANCE) {
				Speech.say(owner, Line.PET_CALL, record.name);
				s.calledUntil = now + 200;
			}
			s.speed = now < s.calledUntil ? 1.3 : 1.0;
			return;
		}
		// The owner is away or asleep (or there is none): keep about the camp centre, wandering as pets do.
		BlockPos home = dayHome(pet, centre.get());
		if (Camp.horizontalDistSqr(pet.blockPosition(), centre.get()) > (double) ROAM * ROAM) {
			s.plan = Plan.GO_TO;
			s.spot = home;
			s.arrive = 4.0;
			s.speed = 1.0;
		} else {
			s.plan = Plan.NONE;
		}
	}

	/** True if a position is about the camp: within its radius (plus a margin) and not deep below it. */
	static boolean aboutCamp(ServerLevel level, BlockPos centre, BlockPos pos, int margin) {
		int r = Camp.radius(Camp.data(level.getServer())) + margin;
		return Camp.horizontalDistSqr(centre, pos) <= (double) r * r && pos.getY() >= centre.getY() - 8 && pos.getY() <= centre.getY() + 32;
	}

	/** A spot near the camp centre for this pet by day (pets spread a little apart, not on the campfire). */
	private static BlockPos dayHome(TamableAnimal pet, BlockPos centre) {
		int h = pet.getUUID().hashCode();
		return centre.offset(((h & 7) - 3), 0, (((h >> 3) & 7) - 3));
	}

	/**
	 * Where the pet spends the night: beside its owner's bed in their village house, else where the owner sleeps (the
	 * cabin, the camp centre), else the camp's cabin or centre for a pet of the whole camp. A free spot to sit is
	 * found next to it. Worked out again every thirty seconds.
	 */
	private static BlockPos nightSpot(ServerLevel level, TamableAnimal pet, PetsData.Pet record, @Nullable CompanionEntity owner,
		State s, long now) {
		if (s.nightSpot != null && now - s.nightSpotAt < 600 && now >= s.nightSpotAt) {
			return s.nightSpot;
		}
		BlockPos base = null;
		String dim = Camp.dimensionId(level);
		if (owner != null && owner.level() == level) {
			base = Homes.get().bedFor(owner).orElse(null);
		}
		if (base == null && record.owner != null) {
			Optional<Homes.Home> home = Homes.get().homeOf(level.getServer(), record.owner);
			if (home.isPresent() && home.get().dimension().equals(dim)) {
				List<BlockPos> beds = home.get().beds();
				base = beds.isEmpty() ? home.get().door() : beds.get(Math.floorMod(record.owner.hashCode(), beds.size()));
			}
		}
		if (base == null && owner != null && owner.level() == level) {
			base = owner.restPos();
		}
		if (base == null) {
			base = Camp.center(level).orElse(pet.blockPosition());
		}
		BlockPos spot = standableNear(level, base, pet);
		s.nightSpot = spot;
		s.nightSpotAt = now;
		return spot;
	}

	/** A free spot to sit on right next to {@code base} (the spot itself, its neighbours, a step up or down), else base. */
	private static BlockPos standableNear(ServerLevel level, BlockPos base, TamableAnimal pet) {
		// Beside the spot first (a bed or the owner's place is not for sitting on), in an order that differs by pet.
		int[][] around = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}, {0, 0}};
		int h = Math.floorMod(pet.getUUID().hashCode(), 8);
		for (int dy : new int[] {0, 1, -1}) {
			for (int i = 0; i < around.length; i++) {
				int[] o = around[i == around.length - 1 ? i : (i + h) % (around.length - 1)];
				BlockPos p = base.offset(o[0], dy, o[1]);
				if (standable(level, p)) {
					return p;
				}
			}
		}
		return base;
	}

	/**
	 * Loaded, open for a small animal, on a solid floor that is not fire, a campfire or water, and not a pressure plate
	 * (a pet sitting on one all night would hold Spark's automatic door open).
	 */
	static boolean standable(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p) || !level.isLoaded(p.below())) {
			return false;
		}
		BlockState here = level.getBlockState(p);
		BlockState floor = level.getBlockState(p.below());
		return here.getCollisionShape(level, p).isEmpty() && here.getFluidState().isEmpty() && !here.is(BlockTags.FIRE)
			&& !here.is(BlockTags.PRESSURE_PLATES) && !here.is(Blocks.TRIPWIRE) && !here.is(BlockTags.DOORS)
			&& !here.is(BlockTags.CAMPFIRES) && !floor.is(BlockTags.CAMPFIRES) && !floor.is(BlockTags.FIRE)
			&& floor.isFaceSturdy(level, p.below(), Direction.UP)
			&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
	}

	private static void sit(TamableAnimal pet, State s) {
		if (!pet.isOrderedToSit()) {
			pet.setOrderedToSit(true);
			pet.getNavigation().stop();
		}
		s.sitByUs = true;
	}

	/**
	 * Lets the pet up. Nobody but its owner can tell a pet to sit in the game, and its owner is a friend, so any sitting
	 * order on a pet of the camp is ours (or left from the night it was saved in).
	 */
	static void releaseSit(TamableAnimal pet, State s) {
		if (pet.isOrderedToSit()) {
			pet.setOrderedToSit(false);
		}
		s.sitByUs = false;
	}

	// -------------------------------------------------------------- teleport

	/**
	 * Brings a pet that cannot find its way (a shut door, a ledge) to a free spot beside {@code target}, as the game
	 * brings a pet to its player. Only onto loaded ground, never into a wall or water.
	 */
	static boolean teleportNear(TamableAnimal pet, BlockPos target) {
		if (!(pet.level() instanceof ServerLevel level) || !level.isLoaded(target) || !nearCamp(level, pet.blockPosition())) {
			return false; // a pet far out in the wilds (led off by a player, say) walks home; it is never fetched from afar
		}
		var random = pet.getRandom();
		for (int attempt = 0; attempt < 10; attempt++) {
			int dx = random.nextIntBetweenInclusive(-2, 2);
			int dz = random.nextIntBetweenInclusive(-2, 2);
			int dy = random.nextIntBetweenInclusive(-1, 1);
			if (dx == 0 && dz == 0) {
				continue;
			}
			BlockPos p = target.offset(dx, dy, dz);
			// The pathing check looks at the blocks round the spot, so every chunk it could touch must be loaded.
			if (!level.isLoaded(p.offset(-1, 0, -1)) || !level.isLoaded(p.offset(1, 0, 1)) || !level.isLoaded(p.offset(-1, 0, 1))
				|| !level.isLoaded(p.offset(1, 0, -1))) {
				continue;
			}
			if (WalkNodeEvaluator.getPathTypeStatic(pet, p) != PathType.WALKABLE) {
				continue;
			}
			BlockPos delta = p.subtract(pet.blockPosition());
			if (!level.noCollision(pet, pet.getBoundingBox().move(delta))) {
				continue;
			}
			pet.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, pet.getYRot(), pet.getXRot());
			pet.getNavigation().stop();
			return true;
		}
		return false;
	}

	/** True if the spot lies in the camp of this level (a pet is only ever fetched within it). */
	static boolean inCamp(ServerLevel level, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		return Camp.isCampLevel(level, data) && data.campPos().map(c -> aboutCamp(level, c, pos, KEEP_FOLLOWING_MARGIN)).orElse(false);
	}

	/** True if the spot lies in the camp or its gathering ring (where a lost pet may still be fetched home from). */
	private static boolean nearCamp(ServerLevel level, BlockPos pos) {
		CampData data = Camp.data(level.getServer());
		return Camp.isCampLevel(level, data) && data.campPos().map(c -> aboutCamp(level, c, pos, NEAR_CAMP)).orElse(false);
	}
}

package io.github.bradley09roberts.hardcorefriends.ai.role.ranch;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Companions;
import io.github.bradley09roberts.hardcorefriends.pets.Pets;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The camp's animal pen as it stands on its reserved site ({@link Blueprints#ANIMAL_PEN}): a 9×9 fence ring with a
 * gate in the middle of the side facing the camp centre, round a 7×7 paddock. Local coordinates are the plan's: x
 * along the front, z from the front (0, the gate row) to the back (8).
 *
 * <p>Also the way through the gate: friends open it, step through and shut it behind them, always through
 * {@link WorldEditGuard} (the gate is the friends' own block). A gate is never shut on someone standing in it, on a
 * player inside the pen, or on a friend still in the paddock, so nobody is ever penned in. The camp's own pets are the
 * one exception ({@link #campPet}): nobody waits on them at the gate.
 */
public record Pen(BlockPos origin, int rotation) {
	/** The jobs that work inside the pen or at its gate: one friend at the gate at a time. */
	public static final Set<String> JOBS = Set.of(BringAnimalTask.ID, BreedTask.ID, ButcherTask.ID, ShutGateTask.ID);

	/** What one step of walking through the gate came to. */
	public enum Step {
		RUNNING,
		DONE,
		FAILED
	}

	/** The finished pen in this level's camp, if there is one. */
	public static Optional<Pen> of(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data) || !data.isCompleted(Structures.ANIMAL_PEN)) {
			return Optional.empty();
		}
		return site(level);
	}

	/** The pen's reserved site in this level's camp, finished or not. */
	public static Optional<Pen> site(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return Optional.empty();
		}
		return data.site(Structures.ANIMAL_PEN).map(s -> new Pen(s.origin, s.rotation));
	}

	/** World position of a local cell at the pen's floor level. */
	public BlockPos at(int dx, int dz) {
		return Blueprint.worldPos(origin, rotation, dx, 0, dz);
	}

	public BlockPos gate() {
		return at(Blueprints.PEN_GATE[0], Blueprints.PEN_GATE[2]);
	}

	/** Where to stand outside the gate. */
	public BlockPos outside() {
		return at(4, -1);
	}

	/** Just inside the gate. */
	public BlockPos inside() {
		return at(4, 1);
	}

	/** The middle of the paddock. */
	public BlockPos centre() {
		return at(4, 4);
	}

	/** Near the back fence, facing the gate: where an animal being brought in is led to. */
	public BlockPos back() {
		return at(4, 6);
	}

	/** The paddock inside the fences, from the ground to a little above the animals' heads. */
	public AABB paddock() {
		BlockPos a = at(1, 1);
		BlockPos b = at(7, 7);
		return new AABB(Math.min(a.getX(), b.getX()), origin.getY() - 1, Math.min(a.getZ(), b.getZ()),
			Math.max(a.getX(), b.getX()) + 1, origin.getY() + 3, Math.max(a.getZ(), b.getZ()) + 1);
	}

	/** The whole pen with its fences. */
	public AABB footprint() {
		return paddock().inflate(1, 0, 1);
	}

	/** True if the entity is standing in the paddock. */
	public boolean holds(Entity e) {
		return paddock().contains(e.position());
	}

	/** True if the block lies in the paddock (inside the fences, at about the pen's height). */
	public boolean holds(BlockPos pos) {
		return paddock().contains(Vec3.atCenterOf(pos));
	}

	/** True if the block lies in the pen, fences included. */
	public boolean covers(BlockPos pos) {
		return footprint().inflate(0, 1, 0).contains(Vec3.atCenterOf(pos));
	}

	/** The cows, pigs, sheep and chickens in the paddock. */
	public List<Animal> animals(Level level) {
		return level.getEntitiesOfClass(Animal.class, paddock(), a -> a.isAlive() && Livestock.kind(a) != null && Livestock.kind(a).penned());
	}

	/** The friends standing in the paddock, other than {@code except}. */
	public List<CompanionEntity> friendsInside(ServerLevel level, CompanionEntity except) {
		return level.getEntitiesOfClass(CompanionEntity.class, paddock(), f -> f.isAlive() && f != except);
	}

	public boolean gateOpen(Level level) {
		BlockState s = level.getBlockState(gate());
		return s.getBlock() instanceof FenceGateBlock && s.getValue(FenceGateBlock.OPEN);
	}

	public boolean hasGate(Level level) {
		return level.getBlockState(gate()).getBlock() instanceof FenceGateBlock;
	}

	/** True when the gate stands and is shut, so nothing can get out. */
	public boolean gateShut(Level level) {
		return hasGate(level) && !gateOpen(level);
	}

	/**
	 * True while a player is in or within 3 blocks of the pen. The gate is never shut on them, so pen jobs that open
	 * it do not start then (it would stand open the whole visit).
	 */
	public boolean playerNear(Level level) {
		return !level.getEntitiesOfClass(Player.class, footprint().inflate(3), p -> !p.isSpectator()).isEmpty();
	}

	/**
	 * True if the gate may be shut now: nobody (animal, friend or player; the camp's pets aside) stands in the gateway,
	 * no friend is still in the paddock, and no player is in or right by the pen.
	 */
	public boolean safeToShut(ServerLevel level, CompanionEntity by) {
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(gate()).inflate(0.1, 0, 0.1), e -> e.isAlive() && !campPet(e)).isEmpty()) {
			return false;
		}
		if (!friendsInside(level, by).isEmpty() || holds(by)) {
			return false;
		}
		return !playerNear(level);
	}

	/**
	 * True for one of the camp's pets. A pet keeps by its owner, so a rancher's dog or cat idles in and out of the
	 * gateway; were it waited on like anyone else there, the gate would be left open for the whole job and the livestock
	 * could wander out. So the gate is shut regardless: a pet partly in a shut gate can still step out of it either way,
	 * and one shut in or out of the pen is brought out like any stuck pet (to its owner, or home at night). Nor is a pet
	 * livestock to be sent to the back of the paddock.
	 */
	static boolean campPet(Entity e) {
		return e instanceof TamableAnimal && e.entityTags().contains(Pets.PET_TAG);
	}

	/** True when a friend other than {@code c} is at work in the pen or at its gate. */
	public static boolean otherAtWork(CompanionEntity c) {
		for (CompanionEntity other : Companions.all()) {
			if (other == c || other.level() != c.level()) {
				continue;
			}
			CompanionTask doing = other.scheduler().current();
			if (doing != null && JOBS.contains(doing.id())) {
				return true;
			}
		}
		return false;
	}

	// ----------------------------------------------------------------- gate

	/** Opens or shuts the gate with a reach, through the guard. Returns true once it is as wanted. */
	public boolean setGate(CompanionEntity c, boolean open) {
		ServerLevel level = (ServerLevel) c.level();
		BlockState s = level.getBlockState(gate());
		if (!(s.getBlock() instanceof FenceGateBlock)) {
			return open; // no gate (broken): the gap is open, and there is nothing to shut
		}
		if (s.getValue(FenceGateBlock.OPEN) == open) {
			return true;
		}
		BlockState changed = s.setValue(FenceGateBlock.OPEN, open);
		if (open) {
			// Swing it away from the friend, as a player's gate does.
			changed = changed.setValue(FenceGateBlock.FACING, net.minecraft.core.Direction.fromYRot(c.getYRot()));
			if (changed.getValue(FenceGateBlock.FACING).getAxis() != s.getValue(FenceGateBlock.FACING).getAxis()) {
				changed = changed.setValue(FenceGateBlock.FACING, s.getValue(FenceGateBlock.FACING));
			}
		}
		if (!c.actions().transform(gate(), changed, WorldEditGuard.Reason.FARM, null)) {
			return false;
		}
		level.playSound(null, gate(), open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F,
			level.getRandom().nextFloat() * 0.1F + 0.9F);
		return true;
	}

	/** True while the entity stands (partly) in the gateway, where a gate cannot be shut. */
	public boolean inGateway(Entity e) {
		return e.getBoundingBox().intersects(new AABB(gate()).inflate(0.05, 0, 0.05));
	}


	/**
	 * Sends the pen's animals standing by the gate (in the gateway or within two blocks of it inside) towards the back
	 * of the paddock, so none slips out, or is jostled out, while the gate is open. True if any had to be sent. The
	 * camp's pets are not the pen's animals ({@link #campPet}).
	 */
	public boolean shooFromGate(ServerLevel level) {
		BlockPos g = gate();
		AABB near = new AABB(g).inflate(2.0, 1.0, 2.0);
		BlockPos to = back();
		boolean any = false;
		for (Animal a : level.getEntitiesOfClass(Animal.class, near, a -> a.isAlive() && !campPet(a) && (holds(a) || inGateway(a)))) {
			any = true;
			if (a.getNavigation().isDone()) {
				a.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
			}
		}
		return any;
	}

	/** Sends one animal to the far back corner of the paddock, out of the way of a friend leaving. */
	public void sendToBack(Animal a) {
		BlockPos to = at(6, 7);
		a.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.0);
	}

	/**
	 * One tick of going into the paddock: to the gate, send any animals by it to the back, open it, through, and (with
	 * {@code shutBehind}) shut it once clear of the gateway. {@link Step#DONE} once inside (and the gate shut, if asked
	 * and it was safe to; a gateway that does not clear is left open rather than waited on for ever).
	 */
	public Step enter(CompanionEntity c, GateWalk walk, boolean shutBehind) {
		ServerLevel level = (ServerLevel) c.level();
		walk.going(true);
		if (holds(c) && !inGateway(c)) {
			if (!shutBehind || !gateOpen(level)) {
				return Step.DONE;
			}
			if (!c.actions().canReach(gate())) {
				c.actions().walkTo(inside(), 1.0);
				return c.actions().isStuck() ? Step.DONE : Step.RUNNING;
			}
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(gate()));
			if (!gatewayClear(level)) {
				return walk.waited() ? Step.DONE : Step.RUNNING;
			}
			return setGate(c, false) || walk.waited() ? Step.DONE : Step.RUNNING;
		}
		if (!walk.through && !holds(c) && !inGateway(c)) {
			if (c.position().distanceToSqr(Vec3.atBottomCenterOf(outside())) > 1.5 * 1.5 || !c.actions().canReach(gate())) {
				c.actions().walkTo(outside(), 0.8);
				return c.actions().isStuck() ? Step.FAILED : Step.RUNNING;
			}
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(gate()));
			if (shooFromGate(level) && !walk.waited()) {
				return Step.RUNNING;
			}
			if (!gateOpen(level) && hasGate(level)) {
				return setGate(c, true) || !walk.waited() ? Step.RUNNING : Step.FAILED;
			}
			walk.through = true;
		}
		c.actions().walkTo(inside().relative(dirInto(), 1), 0.6);
		return c.actions().isStuck() ? Step.FAILED : Step.RUNNING;
	}

	/**
	 * One tick of leaving the paddock: to the gate's inside, send any animals by it to the back, open it, out, and
	 * (with {@code shut}) shut it behind when it is safe to ({@link #safeToShut}). {@link Step#DONE} once outside.
	 */
	public Step leave(CompanionEntity c, GateWalk walk, boolean shut) {
		ServerLevel level = (ServerLevel) c.level();
		walk.going(false);
		if (!footprint().contains(c.position()) && !inGateway(c)) {
			if (!shut || !gateOpen(level)) {
				return Step.DONE;
			}
			if (!c.actions().canReach(gate())) {
				c.actions().walkTo(outside(), 1.0);
				return c.actions().isStuck() ? Step.DONE : Step.RUNNING;
			}
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(gate()));
			if (!safeToShut(level, c)) {
				return walk.waited() ? Step.DONE : Step.RUNNING;
			}
			return setGate(c, false) || walk.waited() ? Step.DONE : Step.RUNNING;
		}
		if (!walk.through && !inGateway(c)) {
			if (c.position().distanceToSqr(Vec3.atBottomCenterOf(inside())) > 1.0 || !c.actions().canReach(gate())) {
				c.actions().walkTo(inside(), 0.8);
				return c.actions().isStuck() ? Step.FAILED : Step.RUNNING;
			}
			c.actions().stopWalking();
			c.getLookControl().setLookAt(Vec3.atCenterOf(gate()));
			if (shooFromGate(level) && !walk.waited()) {
				return Step.RUNNING;
			}
			if (!gateOpen(level) && hasGate(level)) {
				return setGate(c, true) || !walk.waited() ? Step.RUNNING : Step.FAILED;
			}
			walk.through = true;
		}
		c.actions().walkTo(outside().relative(dirInto().getOpposite(), 1), 0.7);
		return c.actions().isStuck() ? Step.FAILED : Step.RUNNING;
	}

	/**
	 * True if nobody (animal, friend or player; the camp's pets aside) stands in the gateway, and no player is in or by
	 * the pen: a friend in the paddock may shut the gate behind them.
	 */
	boolean gatewayClear(ServerLevel level) {
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(gate()).inflate(0.05, 0, 0.05), e -> e.isAlive() && !campPet(e)).isEmpty()
			&& !playerNear(level);
	}

	/** The direction from the gate into the paddock. */
	public net.minecraft.core.Direction dirInto() {
		BlockPos in = inside();
		BlockPos g = gate();
		return net.minecraft.core.Direction.getApproximateNearest((double) (in.getX() - g.getX()), 0.0, (double) (in.getZ() - g.getZ()));
	}

	/**
	 * One friend's way through the gate: whether they are going in or out, whether they are past the gate yet, and how
	 * long they have waited for the gateway to clear or the gate to move (they give up waiting after
	 * {@value #PATIENCE} ticks). Switching between going in and going out starts afresh.
	 */
	public static final class GateWalk {
		private static final int PATIENCE = 100;
		private int waited;
		private boolean through;
		private boolean entering;

		/** Counts one tick of waiting; true once the friend has waited long enough to move on. */
		public boolean waited() {
			return ++waited > PATIENCE;
		}

		/** Starts afresh when the friend turns from going in to going out, or back. */
		void going(boolean in) {
			if (entering != in) {
				reset();
				entering = in;
			}
		}

		public void reset() {
			waited = 0;
			through = false;
		}
	}
}

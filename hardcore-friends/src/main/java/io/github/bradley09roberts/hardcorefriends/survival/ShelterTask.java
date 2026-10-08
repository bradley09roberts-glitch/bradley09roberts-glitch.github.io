package io.github.bradley09roberts.hardcorefriends.survival;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;

import io.github.bradley09roberts.hardcorefriends.ai.action.Actions;
import io.github.bradley09roberts.hardcorefriends.ai.goal.Threats;
import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.NightWatch;
import io.github.bradley09roberts.hardcorefriends.camp.SiteGrading;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard.Reason;

/**
 * A friend caught far from camp after dark (more than {@value #FAR_FROM_HOME} blocks out, or getting no nearer home)
 * gets under cover instead of walking through the night: into a hillside (two natural blocks dug out, the way in
 * walled up behind them), down into the ground (two blocks dug down, a roof put on), or, on open ground, a 1×2
 * pillbox of dirt or cobblestone from their backpack. A torch goes inside if they carry one, and they sleep until
 * dawn. In the morning they take every block they placed back, put back the ground they dug, and carry on home.
 *
 * <p>Every block goes through the edit guard ({@code SURVIVAL}): only shelter blocks and a torch, right around the
 * friend, never within six blocks of anything player-built, digging at most the {@value #MAX_DIGS} natural blocks
 * planned, and taking back only the friend's own pieces. The shelter is remembered with the friend, so a fight or a
 * reload in the middle carries on where it left off.
 */
public final class ShelterTask implements CompanionTask {
	public static final String ID = "survival.shelter";
	/** Further than this from home at night, a friend shelters rather than walk on in the dark. */
	public static final int FAR_FROM_HOME = 48;
	private static final int MAX_DIGS = 3;
	private static final String STATE = "survival.shelter";
	/** How often progress towards home is measured at night, in ticks. */
	private static final int PROGRESS_INTERVAL = 200;
	/** A friend further than this from their shelter has lost it (pushed off, fled): it is given up. */
	private static final int LOST_DISTANCE = 16;

	private enum Kind {
		HILLSIDE,
		DIG_DOWN,
		PILLBOX
	}

	private enum Stage {
		BUILD,
		SLEEP,
		DISMANTLE
	}

	/** A shelter: what kind, the space the friend lies in (feet), where they stood, what to dig, what to wall up. */
	private record Plan(Kind kind, BlockPos space, BlockPos origin, List<BlockPos> digs, List<BlockPos> walls, int cost) {
	}

	private @Nullable Plan plan;
	private Stage stage = Stage.BUILD;
	private final Climber climber = new Climber();
	private int stepTicks;
	private int restored;
	private boolean lying;
	private int awakeTicks;
	// Progress towards home at night, for "no path home".
	private long sampleAt = Long.MIN_VALUE;
	private double sampleDistance;
	private boolean notGettingHome;

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String describe() {
		return switch (stage) {
			case BUILD -> "getting under cover for the night";
			case SLEEP -> "sheltering for the night";
			case DISMANTLE -> "taking down their shelter";
		};
	}

	@Override
	public double score(CompanionEntity c) {
		if (c.mode() != CompanionMode.WORK || !(c.level() instanceof ServerLevel level) || level.dimensionType().hasFixedTime()) {
			return 0;
		}
		boolean night = Camp.isNight(level);
		if (c.extra().contains(STATE)) {
			return night ? 120 : 70; // stay in it until morning, then take it down
		}
		if (!night) {
			sampleAt = Long.MIN_VALUE;
			return 0;
		}
		if (c.isInWater() || !c.onGround() || c.isPassenger()) {
			return 0;
		}
		if (c.friendId() == FriendId.AEGIS && (NightWatch.isOnWatch(c) || level.getNearestPlayer(c, 24) != null)) {
			return 0; // on guard duty, or beside a player out late
		}
		double distance = Math.sqrt(Camp.horizontalDistSqr(c.blockPosition(), c.homePos()));
		boolean far = distance > FAR_FROM_HOME;
		if (!far && !notGettingHome(c, level, distance)) {
			return 0;
		}
		if (Shelters.blocksCarried(c) == 0 && !SiteGrading.isGradeable(level.getBlockState(c.blockPosition().below()))) {
			return 0; // nothing to wall up with and nothing to dig into
		}
		return 115; // above heading home in the dark (85), below a starving meal
	}

	/** True when, outside the camp at night, the friend has got no nearer home over the last ten seconds. */
	private boolean notGettingHome(CompanionEntity c, ServerLevel level, double distance) {
		long now = level.getGameTime();
		if (sampleAt == Long.MIN_VALUE || now - sampleAt > PROGRESS_INTERVAL * 3) {
			sampleAt = now;
			sampleDistance = distance;
			notGettingHome = false;
		} else if (now - sampleAt >= PROGRESS_INTERVAL) {
			notGettingHome = sampleDistance - distance < 4 && distance > WorldEditGuard.campRadius(c) + 8;
			sampleAt = now;
			sampleDistance = distance;
		}
		return notGettingHome;
	}

	@Override
	public boolean start(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		climber.reset();
		stepTicks = 0;
		restored = 0;
		lying = false;
		awakeTicks = 0;
		if (load(c)) {
			Plan p = plan;
			if (p == null || c.blockPosition().distSqr(p.space()) > LOST_DISTANCE * LOST_DISTANCE) {
				abandon(c);
				return false;
			}
			if (stage == Stage.SLEEP && Camp.isNight(level)) {
				stage = Stage.BUILD; // check the walls again and settle back in
			}
			if (!Camp.isNight(level)) {
				stage = Stage.DISMANTLE;
			}
			return true;
		}
		if (!Camp.isNight(level)) {
			return false;
		}
		Plan p = choose(c, level);
		if (p == null) {
			return false;
		}
		plan = p;
		stage = Stage.BUILD;
		Shelters.planDigs(c, p.digs());
		save(c);
		Speech.say(c, Line.SHELTER);
		Camp.data(level.getServer()).addStat("shelters_built", 1);
		return true;
	}

	// ----------------------------------------------------------------- choosing

	/** The cheapest shelter the friend can make here, or null: natural cover first, then a pillbox of carried blocks. */
	private static @Nullable Plan choose(CompanionEntity c, ServerLevel level) {
		BlockPos f = c.blockPosition();
		if (WorldEditGuard.looksPlayerBuilt(level, f, SurvivalPolicies.BUILD_GAP + 1, Camp.data(level.getServer()))) {
			return null; // never next to anything a player built
		}
		List<Plan> options = new ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos s = f.relative(d);
			addIfFits(c, level, options, Kind.HILLSIDE, s, f, List.of(s.above(), s));
		}
		addIfFits(c, level, options, Kind.DIG_DOWN, f.below(2), f, List.of(f.below(), f.below(2)));
		addIfFits(c, level, options, Kind.PILLBOX, f, f, List.of());
		Plan best = null;
		for (Plan p : options) {
			if (best == null || p.cost() < best.cost()) {
				best = p;
			}
		}
		return best;
	}

	private static void addIfFits(CompanionEntity c, ServerLevel level, List<Plan> options, Kind kind, BlockPos space,
		BlockPos origin, List<BlockPos> digs) {
		if (!level.isLoaded(space) || !level.isLoaded(space.above(2))) {
			return;
		}
		// What to dig must be natural ground, and the friend must be able to stand in the space afterwards.
		int spoil = 0;
		boolean pickaxe = c.actions().hasTool(ItemTags.PICKAXES);
		for (BlockPos d : digs) {
			BlockState s = level.getBlockState(d);
			if (!SiteGrading.isGradeable(s) || !Shelters.closes(level, d) || WorldEditGuard.touchesFluid(level, d)) {
				return;
			}
			if (s.is(BlockTags.DIRT) || s.is(BlockTags.GRASS_BLOCKS) || pickaxe && s.is(Blocks.STONE)) {
				spoil++;
			}
		}
		if (digs.size() > MAX_DIGS) {
			return;
		}
		BlockPos floor = space.below();
		if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
			return;
		}
		BlockPos roof = space.above(2);
		BlockState roofState = level.getBlockState(roof);
		if (roofState.getBlock() instanceof Fallable && !digs.isEmpty()) {
			return; // sand or gravel overhead would come down into the space
		}
		for (BlockPos cell : new BlockPos[] {space, space.above()}) {
			if (!digs.contains(cell)) {
				BlockState s = level.getBlockState(cell);
				boolean free = s.getCollisionShape(level, cell).isEmpty() && s.getFluidState().isEmpty();
				if (!free) {
					return;
				}
			}
			for (Direction d : Direction.values()) {
				if (!level.getFluidState(cell.relative(d)).isEmpty()) {
					return; // no water or lava anywhere near the space
				}
			}
		}
		List<BlockPos> walls = new ArrayList<>();
		for (BlockPos cell : new BlockPos[] {space, space.above()}) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				addWall(level, walls, cell.relative(d), origin, space);
			}
		}
		addWall(level, walls, roof, origin, space);
		if (walls.contains(null)) {
			return;
		}
		int carried = Shelters.blocksCarried(c);
		if (walls.size() > carried + spoil) {
			return;
		}
		int cost = walls.size() * 2 + digs.size();
		options.add(new Plan(kind, space.immutable(), origin.immutable(), List.copyOf(digs), List.copyOf(walls), cost));
	}

	/**
	 * Adds a side or the roof to wall up if it is open (or will be once the friend has moved into the space). A side
	 * that is open and cannot take a block adds null, which rules the shelter out.
	 */
	private static void addWall(ServerLevel level, List<BlockPos> walls, BlockPos pos, BlockPos origin, BlockPos space) {
		boolean vacated = !origin.equals(space) && (pos.equals(origin) || pos.equals(origin.above()));
		if (!vacated && Shelters.closes(level, pos)) {
			return; // already closed: natural ground or a block of the hillside
		}
		BlockState s = level.getBlockState(pos);
		boolean placeable = s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
		walls.add(placeable || vacated ? pos.immutable() : null);
	}

	// ------------------------------------------------------------------- ticking

	@Override
	public TaskStatus tick(CompanionEntity c) {
		Plan p = plan;
		if (p == null) {
			return TaskStatus.FAILURE;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (c.blockPosition().distSqr(p.space()) > LOST_DISTANCE * LOST_DISTANCE) {
			abandon(c);
			return TaskStatus.FAILURE;
		}
		if (stage != Stage.DISMANTLE && !Camp.isNight(level)) {
			wake(c);
			stage = Stage.DISMANTLE;
			stepTicks = 0;
			save(c);
			Speech.say(c, Line.SHELTER_MORNING);
		}
		return switch (stage) {
			case BUILD -> build(c, level, p);
			case SLEEP -> sleep(c, level, p);
			case DISMANTLE -> dismantle(c, level, p);
		};
	}

	private TaskStatus build(CompanionEntity c, ServerLevel level, Plan p) {
		Actions actions = c.actions();
		// 1. Dig out the space (top first), at most the planned blocks.
		for (BlockPos d : p.digs()) {
			if (Shelters.closes(level, d)) {
				if (!actions.canReach(d)) {
					actions.walkTo(d, 1.5);
					return actions.isStuck() ? giveUpBuilding(c) : TaskStatus.RUNNING;
				}
				actions.stopWalking();
				return switch (actions.mine(d, Reason.SURVIVAL)) {
					case FAILED -> giveUpBuilding(c);
					case DONE, RUNNING -> TaskStatus.RUNNING;
				};
			}
		}
		// 2. Step into the space.
		if (!c.blockPosition().equals(p.space())) {
			if (++stepTicks > 100) {
				return giveUpBuilding(c);
			}
			actions.walkTo(p.space(), 0.3);
			return TaskStatus.RUNNING;
		}
		actions.stopWalking();
		c.setPos(p.space().getX() + 0.5, c.getY(), p.space().getZ() + 0.5);
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		// 3. Wall up every open side and the roof, one block at a time.
		if (level.getGameTime() - c.lastEditTick() < 4) {
			return TaskStatus.RUNNING;
		}
		for (BlockPos w : p.walls()) {
			if (Shelters.closes(level, w)) {
				continue;
			}
			ItemStack block = c.backpack().find(Shelters::isShelterBlock);
			if (block.isEmpty()) {
				break; // out of blocks: a half-walled shelter is still better than none
			}
			ItemStack template = block.copyWithCount(1);
			if (actions.place(w, Shelters.stateOf(block), s -> ItemStack.isSameItemSameComponents(s, template), Reason.SURVIVAL)) {
				Shelters.addPiece(c, w);
				return TaskStatus.RUNNING;
			}
		}
		// 4. A torch inside, if they carry one.
		BlockState here = level.getBlockState(p.space());
		if (here.isAir() && c.backpack().has(s -> s.is(Items.TORCH))
			&& Blocks.TORCH.defaultBlockState().canSurvive(level, p.space())
			&& actions.place(p.space(), Blocks.TORCH.defaultBlockState(), s -> s.is(Items.TORCH), Reason.SURVIVAL)) {
			Shelters.addPiece(c, p.space());
			return TaskStatus.RUNNING;
		}
		stage = Stage.SLEEP;
		save(c);
		return TaskStatus.RUNNING;
	}

	/** Building went wrong (a block could not be dug or reached): no shelter tonight; nothing is left half dug. */
	private TaskStatus giveUpBuilding(CompanionEntity c) {
		c.actions().reset();
		stage = Stage.DISMANTLE;
		save(c);
		return TaskStatus.FAILURE;
	}

	private TaskStatus sleep(CompanionEntity c, ServerLevel level, Plan p) {
		c.getNavigation().stop();
		c.setDeltaMovement(0, c.getDeltaMovement().y, 0);
		if (!c.blockPosition().equals(p.space())) {
			stage = Stage.BUILD; // knocked out of place: back in and check the walls
			wake(c);
			return TaskStatus.RUNNING;
		}
		if (disturbed(c)) {
			wake(c);
			awakeTicks = 200; // stay up a while, alert, inside the shelter
		}
		if (awakeTicks > 0) {
			awakeTicks--;
			LivingEntity threat = Threats.nearest(c, 16);
			if (threat != null) {
				c.getLookControl().setLookAt(threat);
			}
			return TaskStatus.RUNNING;
		}
		if (!lying) {
			c.setPose(Pose.SLEEPING);
			c.setAsleep(true);
			lying = true;
		}
		c.settleSleep();
		return TaskStatus.RUNNING;
	}

	/** Hurt by anything but hunger pangs. */
	private static boolean disturbed(CompanionEntity c) {
		if (c.ticksSinceDamaged() >= 5) {
			return false;
		}
		DamageSource source = c.getLastDamageSource();
		return source == null || !source.is(DamageTypes.STARVE);
	}

	private void wake(CompanionEntity c) {
		if (c.getPose() == Pose.SLEEPING) {
			c.setPose(Pose.STANDING);
		}
		c.setAsleep(false);
		lying = false;
	}

	private TaskStatus dismantle(CompanionEntity c, ServerLevel level, Plan p) {
		wake(c);
		Actions actions = c.actions();
		// 1. Take back every piece (the torch, the walls, the roof), nearest first.
		BlockPos piece = nextPiece(c, level, p);
		if (piece != null) {
			if (!actions.canReach(piece)) {
				actions.walkTo(piece, 1.5);
				if (actions.isStuck()) {
					Shelters.removePiece(c, piece); // out of reach: left where it is
				}
				return TaskStatus.RUNNING;
			}
			actions.stopWalking();
			switch (actions.mine(piece, Reason.SURVIVAL)) {
				case DONE, FAILED -> Shelters.removePiece(c, piece);
				case RUNNING -> {
				}
			}
			return TaskStatus.RUNNING;
		}
		// 2. Put back the ground that was dug: climb out of a dug-down hole block by block, or refill a hillside alcove.
		if (restored < p.digs().size() && Shelters.blocksCarried(c) > 0) {
			if (p.kind() == Kind.DIG_DOWN) {
				if (c.blockPosition().getY() < p.origin().getY()) {
					switch (climber.step(c, level)) {
						case PLACED -> restored++;
						case NO_BLOCKS, BLOCKED -> restored = p.digs().size();
						case CLIMBING -> {
						}
					}
					return TaskStatus.RUNNING;
				}
			} else if (p.kind() == Kind.HILLSIDE) {
				if (!c.blockPosition().equals(p.origin())) {
					if (++stepTicks > 100) {
						restored = p.digs().size();
					}
					actions.walkTo(p.origin(), 0.3);
					return TaskStatus.RUNNING;
				}
				if (level.getGameTime() - c.lastEditTick() < 4) {
					return TaskStatus.RUNNING;
				}
				BlockPos hole = p.digs().get(p.digs().size() - 1 - restored); // the lower one first
				ItemStack block = c.backpack().find(Shelters::isShelterBlock);
				ItemStack template = block.copyWithCount(1);
				if (!level.getBlockState(hole).isAir()
					|| !actions.place(hole, Shelters.stateOf(block), s -> ItemStack.isSameItemSameComponents(s, template), Reason.SURVIVAL)) {
					restored++;
					return TaskStatus.RUNNING;
				}
				restored++;
				return TaskStatus.RUNNING;
			}
		}
		finish(c);
		return TaskStatus.SUCCESS;
	}

	/** The nearest of this shelter's own pieces still standing. */
	private static @Nullable BlockPos nextPiece(CompanionEntity c, ServerLevel level, Plan p) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (long l : Shelters.pieces(c)) {
			BlockPos pos = BlockPos.of(l);
			if (pos.distSqr(p.space()) > 9) {
				continue; // another job's piece (a pillar), not this shelter's
			}
			if (level.getBlockState(pos).isAir()) {
				Shelters.removePiece(c, pos);
				continue;
			}
			double d = pos.distSqr(c.blockPosition());
			// The roof of a dug-down shelter first, so the way out is open.
			if (p.kind() == Kind.DIG_DOWN && pos.equals(p.origin())) {
				d = -1;
			}
			if (d < bestDist) {
				bestDist = d;
				best = pos;
			}
		}
		return best;
	}

	private void finish(CompanionEntity c) {
		c.extra().remove(STATE);
		Shelters.clearDigs(c);
		plan = null;
	}

	/** Gives the shelter up: whatever is left stands, and the friend forgets it. */
	private void abandon(CompanionEntity c) {
		wake(c);
		Plan p = plan;
		if (p != null) {
			for (long l : Shelters.pieces(c)) {
				if (BlockPos.of(l).distSqr(p.space()) <= 9) {
					Shelters.removePiece(c, BlockPos.of(l));
				}
			}
		}
		finish(c);
	}

	@Override
	public void stop(CompanionEntity c) {
		wake(c);
		climber.reset();
		c.actions().reset();
		if (plan != null) {
			save(c);
		}
		plan = null;
	}

	@Override
	public int maxTicks() {
		return 20 * 60 * 12; // a whole night, with time to build and to take it down
	}

	@Override
	public int failureCooldown() {
		return 600;
	}

	// --------------------------------------------------------------- persistence

	private void save(CompanionEntity c) {
		Plan p = plan;
		if (p == null) {
			return;
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("kind", p.kind().name());
		tag.putString("stage", stage.name());
		tag.putLong("space", p.space().asLong());
		tag.putLong("origin", p.origin().asLong());
		tag.putLongArray("digs", p.digs().stream().mapToLong(BlockPos::asLong).toArray());
		tag.putLongArray("walls", p.walls().stream().mapToLong(BlockPos::asLong).toArray());
		tag.putInt("restored", restored);
		c.extra().put(STATE, tag);
	}

	private boolean load(CompanionEntity c) {
		CompoundTag tag = c.extra().getCompoundOrEmpty(STATE);
		if (tag.isEmpty()) {
			return false;
		}
		try {
			Kind kind = Kind.valueOf(tag.getStringOr("kind", Kind.PILLBOX.name()));
			stage = Stage.valueOf(tag.getStringOr("stage", Stage.DISMANTLE.name()));
			List<BlockPos> digs = new ArrayList<>();
			for (long l : tag.getLongArray("digs").orElse(new long[0])) {
				digs.add(BlockPos.of(l));
			}
			List<BlockPos> walls = new ArrayList<>();
			for (long l : tag.getLongArray("walls").orElse(new long[0])) {
				walls.add(BlockPos.of(l));
			}
			plan = new Plan(kind, BlockPos.of(tag.getLongOr("space", 0L)), BlockPos.of(tag.getLongOr("origin", 0L)), digs, walls, 0);
			restored = tag.getIntOr("restored", 0);
			return true;
		} catch (IllegalArgumentException e) {
			c.extra().remove(STATE);
			return false;
		}
	}
}

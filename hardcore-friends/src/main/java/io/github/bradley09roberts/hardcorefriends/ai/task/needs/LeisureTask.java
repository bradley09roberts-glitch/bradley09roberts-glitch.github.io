package io.github.bradley09roberts.hardcorefriends.ai.task.needs;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.ai.task.CompanionTask;
import io.github.bradley09roberts.hardcorefriends.ai.task.TaskStatus;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.FriendId;
import io.github.bradley09roberts.hardcorefriends.companion.Line;
import io.github.bradley09roberts.hardcorefriends.companion.Needs.Need;
import io.github.bradley09roberts.hardcorefriends.companion.Speech;

/**
 * A bored friend takes ten seconds off for a pastime that suits them, somewhere fitting inside the camp: Fern smells
 * the flowers, Oak whittles by the campfire, Flint skips stones on the water, Scout watches the horizon from the
 * highest ground about, Spark tinkers by the furnace or the redstone, Aegis runs sword drills, Sage watches the clouds
 * (or the stars at night), Terra admires the flowers and Rowan lies back on a hillside watching the sky. When the
 * fitting place is missing they do something simpler right at camp.
 *
 * <p>Fun only: nothing in the world is changed. Flowers are looked at, never picked; stones are skipped, not mined.
 */
public final class LeisureTask implements CompanionTask {
	/** Below this fun a friend looks for a pastime. */
	static final double BORED = 40;
	static final double FUN_GAIN = 50;
	private static final int PASTIME_TICKS = 20 * 10;
	private static final int WALK_TICKS = 20 * 20;
	/** How far around the friend to look for a fitting place. */
	private static final int SEARCH = 12;

	enum Place { FLOWERS, CAMPFIRE, WATER, HIGH_GROUND, WORKSHOP, ANYWHERE }

	enum Motion { ADMIRE, WHITTLE, SKIP_STONES, GAZE_FAR, TINKER, DRILL, LOOK_UP, HUM }

	/** One way to pass the time: what it is called, where it happens and how it looks. */
	record Pastime(String activity, Place place, Motion motion) {
	}

	private static final Predicate<BlockState> FLOWER = s -> s.is(BlockTags.FLOWERS) && !s.is(BlockTags.LEAVES);
	private static final Predicate<BlockState> WATER_SURFACE = s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource();
	private static final Predicate<BlockState> WORKSHOP = s -> s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE)
		|| s.is(Blocks.SMOKER) || s.is(Blocks.REDSTONE_BLOCK) || s.is(Blocks.REDSTONE_LAMP) || s.is(Blocks.PISTON)
		|| s.is(Blocks.STICKY_PISTON) || s.is(Blocks.OBSERVER) || s.is(Blocks.REPEATER) || s.is(Blocks.COMPARATOR)
		|| s.is(Blocks.HOPPER) || s.is(Blocks.DISPENSER) || s.is(Blocks.DROPPER) || s.is(Blocks.DAYLIGHT_DETECTOR)
		|| s.is(Blocks.LEVER) || s.is(Blocks.NOTE_BLOCK) || s.is(Blocks.REDSTONE_TORCH);

	private final FriendId friend;
	private @Nullable Pastime pastime;
	private @Nullable BlockPos spot;
	private @Nullable BlockPos focus;
	private boolean arrived;
	private int ticks;

	public LeisureTask(FriendId friend) {
		this.friend = friend;
	}

	@Override
	public String id() {
		return "needs.leisure";
	}

	@Override
	public String describe() {
		return pastime != null ? pastime.activity() : "having some fun";
	}

	@Override
	public double score(CompanionEntity c) {
		double fun = c.needs().get(Need.FUN);
		if (fun >= BORED || !Spots.inCamp(c, c.blockPosition())) {
			return 0;
		}
		return 25 + (BORED - fun); // 25 when a little bored, up to 65
	}

	/** This friend's pastimes, favourite first; the last one needs no special place. */
	static List<Pastime> pastimes(FriendId id, boolean night) {
		return switch (id) {
			case FERN -> List.of(new Pastime("smelling the flowers", Place.FLOWERS, Motion.ADMIRE),
				new Pastime("humming a tune", Place.ANYWHERE, Motion.HUM));
			case OAK -> List.of(new Pastime("whittling by the campfire", Place.CAMPFIRE, Motion.WHITTLE),
				new Pastime("whittling a stick", Place.ANYWHERE, Motion.WHITTLE));
			case FLINT -> List.of(new Pastime("skipping stones", Place.WATER, Motion.SKIP_STONES),
				new Pastime("sorting interesting pebbles", Place.ANYWHERE, Motion.WHITTLE));
			case SCOUT -> List.of(new Pastime("watching the horizon", Place.HIGH_GROUND, Motion.GAZE_FAR),
				new Pastime("watching the horizon", Place.ANYWHERE, Motion.GAZE_FAR));
			case SPARK -> List.of(new Pastime("tinkering with a gadget", Place.WORKSHOP, Motion.TINKER),
				new Pastime("sketching new inventions", Place.ANYWHERE, Motion.TINKER));
			case AEGIS -> List.of(new Pastime("sword drills", Place.ANYWHERE, Motion.DRILL));
			case SAGE -> List.of(new Pastime(night ? "stargazing" : "cloud-watching", Place.ANYWHERE, Motion.LOOK_UP));
			case TERRA -> List.of(new Pastime("admiring the flowers", Place.FLOWERS, Motion.ADMIRE),
				new Pastime("planning a garden", Place.ANYWHERE, Motion.GAZE_FAR));
			case ROWAN -> List.of(new Pastime(night ? "stargazing on the hillside" : "cloud-watching on the hillside",
				Place.HIGH_GROUND, Motion.LOOK_UP),
				new Pastime(night ? "stargazing" : "cloud-watching", Place.ANYWHERE, Motion.LOOK_UP));
		};
	}

	@Override
	public boolean start(CompanionEntity c) {
		arrived = false;
		ticks = 0;
		pastime = null;
		spot = null;
		focus = null;
		for (Pastime p : pastimes(friend, Camp.isNight((ServerLevel) c.level()))) {
			if (findPlace(c, p.place())) {
				pastime = p;
				return true;
			}
		}
		return false;
	}

	/** Sets {@link #spot} (where to stand) and {@link #focus} (what to look at) for a kind of place. */
	private boolean findPlace(CompanionEntity c, Place place) {
		BlockPos here = c.blockPosition();
		switch (place) {
			case FLOWERS -> focusOn(c, Spots.nearestBlock(c, here, SEARCH, 3, FLOWER), 1);
			case CAMPFIRE -> focusOn(c, Spots.nearestBlock(c, here, SEARCH, 3, s -> s.is(BlockTags.CAMPFIRES)), 2);
			case WATER -> focusOn(c, Spots.nearestBlock(c, here, SEARCH, 3, WATER_SURFACE), 1);
			case WORKSHOP -> focusOn(c, Spots.nearestBlock(c, here, SEARCH, 3, WORKSHOP), 1);
			case HIGH_GROUND -> spot = highGround(c);
			case ANYWHERE -> {
				spot = Spots.randomNear(c, c.homePos(), 6);
				if (spot == null) {
					spot = Spots.standable((ServerLevel) c.level(), here);
				}
			}
		}
		return spot != null;
	}

	private void focusOn(CompanionEntity c, @Nullable BlockPos target, int distance) {
		focus = target;
		spot = null;
		if (target != null) {
			spot = Spots.beside(c, target, distance);
			if (spot == null && distance > 1) {
				spot = Spots.beside(c, target, 1);
			}
		}
	}

	/** The highest place to stand within reach, sampled every other column: the hillside with the best view. */
	private static @Nullable BlockPos highGround(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		BlockPos here = c.blockPosition();
		BlockPos best = null;
		for (int dx = -SEARCH; dx <= SEARCH; dx += 2) {
			for (int dz = -SEARCH; dz <= SEARCH; dz += 2) {
				int x = here.getX() + dx;
				int z = here.getZ() + dz;
				BlockPos column = new BlockPos(x, here.getY(), z);
				if (!level.isLoaded(column) || !Spots.inCamp(c, column)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(top - here.getY()) > 12) {
					continue;
				}
				BlockPos p = new BlockPos(x, top, z);
				if (Spots.isStandable(level, p) && (best == null || p.getY() > best.getY()
					|| p.getY() == best.getY() && p.distSqr(here) < best.distSqr(here))) {
					best = p;
				}
			}
		}
		return best;
	}

	@Override
	public TaskStatus tick(CompanionEntity c) {
		if (pastime == null || spot == null) {
			return TaskStatus.FAILURE;
		}
		if (!arrived) {
			if (c.actions().walkTo(spot, 1.0)) {
				arrived = true;
				ticks = 0;
				Speech.say(c, Line.LEISURE, pastime.activity());
			} else if (c.actions().isStuck() || ++ticks > WALK_TICKS) {
				return TaskStatus.FAILURE;
			}
			return TaskStatus.RUNNING;
		}
		c.actions().stopWalking();
		perform(c, (ServerLevel) c.level(), pastime.motion(), ticks);
		c.needs().add(Need.FUN, FUN_GAIN / PASTIME_TICKS);
		return ++ticks >= PASTIME_TICKS ? TaskStatus.SUCCESS : TaskStatus.RUNNING;
	}

	/** One tick of the pastime: where the friend looks, the odd arm movement, a sound or a puff of particles. */
	private void perform(CompanionEntity c, ServerLevel level, Motion motion, int t) {
		Vec3 eyes = c.getEyePosition();
		switch (motion) {
			case ADMIRE -> {
				lookAtFocusOr(c, eyes.add(c.getLookAngle().scale(2)).subtract(0, 1.5, 0));
				if (t % 50 == 10 && focus != null) {
					particles(level, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(focus), 2);
				}
			}
			case WHITTLE -> {
				c.getLookControl().setLookAt(eyes.add(c.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8)).subtract(0, 1.2, 0));
				if (t % 12 == 0) {
					c.swingArm();
				}
			}
			case SKIP_STONES -> {
				lookAtFocusOr(c, eyes.add(c.getLookAngle().scale(4)));
				if (t % 30 == 0) {
					c.swingArm();
				}
				if (t % 30 == 12 && focus != null) {
					Vec3 water = Vec3.atCenterOf(focus).add(0, 0.5, 0);
					particles(level, ParticleTypes.SPLASH, water, 6);
					level.playSound(null, focus, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.3F, 1.4F);
				}
			}
			case GAZE_FAR -> {
				BlockPos home = c.homePos();
				double away = Math.atan2(c.getZ() - home.getZ(), c.getX() - home.getX()) + Math.sin(t / 40.0) * 0.6;
				c.getLookControl().setLookAt(c.getX() + Math.cos(away) * 30, eyes.y + 2, c.getZ() + Math.sin(away) * 30);
			}
			case TINKER -> {
				lookAtFocusOr(c, eyes.add(c.getLookAngle().multiply(1, 0, 1).normalize().scale(0.8)).subtract(0, 1.2, 0));
				if (t % 10 == 0) {
					c.swingArm();
				}
				if (t % 40 == 20) {
					particles(level, ParticleTypes.ELECTRIC_SPARK, eyes.add(c.getLookAngle().scale(0.6)).subtract(0, 0.6, 0), 4);
				}
			}
			case DRILL -> {
				double yaw = Math.toRadians(c.getId() * 37 + (t / 40) * 90);
				c.getLookControl().setLookAt(c.getX() - Math.sin(yaw) * 5, eyes.y, c.getZ() + Math.cos(yaw) * 5);
				if (t % 8 == 0) {
					c.swingArm();
				}
				if (t % 16 == 0) {
					level.playSound(null, c.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.4F, 1.1F);
				}
			}
			case LOOK_UP -> {
				double drift = Math.toRadians(c.getId() * 53) + t / 200.0;
				c.getLookControl().setLookAt(c.getX() + Math.cos(drift) * 8, eyes.y + 30, c.getZ() + Math.sin(drift) * 8);
			}
			case HUM -> {
				double yaw = Math.toRadians(c.getId() * 41) + Math.sin(t / 30.0);
				c.getLookControl().setLookAt(c.getX() - Math.sin(yaw) * 5, eyes.y, c.getZ() + Math.cos(yaw) * 5);
				if (t % 20 == 5) {
					particles(level, ParticleTypes.NOTE, eyes.add(0, 0.6, 0), 1);
				}
			}
		}
	}

	private void lookAtFocusOr(CompanionEntity c, Vec3 otherwise) {
		if (focus != null) {
			c.getLookControl().setLookAt(Vec3.atCenterOf(focus));
		} else {
			c.getLookControl().setLookAt(otherwise);
		}
	}

	private static void particles(ServerLevel level, SimpleParticleType type, Vec3 at, int count) {
		level.sendParticles(type, at.x, at.y, at.z, count, 0.2, 0.1, 0.2, 0.02);
	}

	@Override
	public void stop(CompanionEntity c) {
		pastime = null;
		spot = null;
		focus = null;
		arrived = false;
		ticks = 0;
	}

	@Override
	public int failureCooldown() {
		return 20 * 20;
	}

	@Override
	public int successCooldown() {
		return 20 * 30;
	}

	@Override
	public int maxTicks() {
		return WALK_TICKS + PASTIME_TICKS + 20 * 5;
	}
}

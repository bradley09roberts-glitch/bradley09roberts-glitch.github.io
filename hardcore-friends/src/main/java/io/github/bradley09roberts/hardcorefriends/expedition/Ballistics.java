package io.github.bradley09roberts.hardcorefriends.expedition;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aiming an arrow at something far above: the end crystals sit on obsidian towers tens of blocks up, where a
 * skeleton's straight-ish shot falls short. The arrow's flight is worked out tick by tick as the game moves it (it
 * moves, then slows by 1% and drops by {@value #GRAVITY} a tick), the flattest arc that reaches the target is
 * chosen, and the whole flight can be checked for blocks in the way (the tower's own edge, a cage).
 */
final class Ballistics {
	/** How much an arrow falls each tick. */
	static final double GRAVITY = 0.05;
	/** How much of its speed an arrow keeps each tick in the air. */
	static final double DRAG = 0.99;
	/** The longest flight followed, in ticks. */
	private static final int MAX_TICKS = 120;

	private Ballistics() {
	}

	/**
	 * The direction to loose an arrow at {@code speed} from {@code from} so that it passes through {@code to}: the
	 * flattest arc that gets there. Null when it is out of reach at that speed.
	 */
	static @Nullable Vec3 aim(Vec3 from, Vec3 to, double speed) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		if (flat < 0.5) {
			return null;
		}
		double ux = dx / flat;
		double uz = dz / flat;
		double rise = to.y - from.y;
		double previous = Double.NaN;
		for (int deg = -40; deg <= 85; deg++) {
			double h = heightAt(Math.toRadians(deg), speed, flat);
			if (!Double.isNaN(h) && h >= rise) {
				double lo = Math.toRadians(deg - 1);
				double hi = Math.toRadians(deg);
				if (Double.isNaN(previous)) {
					lo = hi; // the first angle tried already reaches
				}
				for (int i = 0; i < 12 && lo < hi; i++) {
					double mid = (lo + hi) / 2;
					double hm = heightAt(mid, speed, flat);
					if (!Double.isNaN(hm) && hm >= rise) {
						hi = mid;
					} else {
						lo = mid;
					}
				}
				double pitch = hi;
				return new Vec3(ux * Math.cos(pitch), Math.sin(pitch), uz * Math.cos(pitch));
			}
			previous = h;
		}
		return null;
	}

	/** How high above the start an arrow loosed at {@code pitch} is when it has gone {@code flat} blocks out (NaN if never). */
	private static double heightAt(double pitch, double speed, double flat) {
		double vx = Math.cos(pitch) * speed;
		double vy = Math.sin(pitch) * speed;
		double x = 0;
		double y = 0;
		for (int t = 0; t < MAX_TICKS; t++) {
			double nx = x + vx;
			double ny = y + vy;
			if (nx >= flat) {
				double f = (flat - x) / (nx - x);
				return y + (ny - y) * f;
			}
			x = nx;
			y = ny;
			vx *= DRAG;
			vy = vy * DRAG - GRAVITY;
			if (vx < 1.0E-3) {
				return Double.NaN;
			}
		}
		return Double.NaN;
	}

	/**
	 * True when an arrow loosed from {@code from} in {@code direction} at {@code speed} meets no block on its way to
	 * {@code to} (followed until it has gone that far out). Reads loaded blocks only (an unloaded one counts as in the
	 * way).
	 */
	static boolean clearFlight(ServerLevel level, Entity shooter, Vec3 from, Vec3 direction, double speed, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		Vec3 v = direction.normalize().scale(speed);
		Vec3 pos = from;
		double gone = 0;
		for (int t = 0; t < MAX_TICKS; t++) {
			Vec3 next = pos.add(v);
			double step = Math.sqrt(v.x * v.x + v.z * v.z);
			if (gone + step >= flat - 0.6) {
				// The last bit: up to just short of the target's middle.
				Vec3 end = step < 1.0E-6 ? next : pos.add(v.scale(Math.max(0, (flat - 0.6 - gone) / step)));
				return !blocked(level, shooter, pos, end);
			}
			if (blocked(level, shooter, pos, next)) {
				return false;
			}
			pos = next;
			gone += step;
			v = new Vec3(v.x * DRAG, v.y * DRAG - GRAVITY, v.z * DRAG);
		}
		return false;
	}

	private static boolean blocked(ServerLevel level, Entity shooter, Vec3 a, Vec3 b) {
		if (!level.isLoaded(net.minecraft.core.BlockPos.containing(b))) {
			return true;
		}
		return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter)).getType()
			!= HitResult.Type.MISS;
	}
}

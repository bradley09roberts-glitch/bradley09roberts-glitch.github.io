package io.github.bradley09roberts.hardcorefriends.expedition;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionMode;
import io.github.bradley09roberts.hardcorefriends.survival.ChunkLoader;

/**
 * A friend's travels between dimensions: which dimension is home, the way they came in (kept with them, so they can
 * find their way back), the portal their leader went through when they were left behind, how long they have been
 * waiting on their own, and the crossing itself. Friends never use portals the vanilla way (a stray step into the
 * camp's portal would send one into the Nether alone): every crossing is made on purpose here, to a safe spot in a
 * loaded part of the other side. A cross-dimension teleport makes a new entity from the old one's saved data, so
 * the backpack, gear, needs and everything in {@link CompanionEntity#extra()} come along.
 */
public final class Travel {
	/** This package's key in {@link CompanionEntity#extra()}. */
	static final String KEY = "expedition";
	/** How long a friend left on their own waits before going home (or back to work), in ticks. */
	public static final int WAIT_LIMIT = 20 * 180;

	/** A way through to another dimension: where it is on this side, and where it comes out. */
	public record Way(BlockPos portal, String toDim, BlockPos toPos) {
	}

	private Travel() {
	}

	// ------------------------------------------------------------- dimensions

	/** The dimension the camp is in (the overworld without a camp). */
	public static ResourceKey<Level> homeDimension(MinecraftServer server) {
		CampData data = Camp.data(server);
		if (data.campPos().isPresent()) {
			Identifier id = Identifier.tryParse(data.campDimension());
			if (id != null) {
				return ResourceKey.create(Registries.DIMENSION, id);
			}
		}
		return Level.OVERWORLD;
	}

	/** True when the friend is in another dimension than the camp's: away on an expedition. */
	public static boolean abroad(CompanionEntity c) {
		return c.level() instanceof ServerLevel level && level.dimension() != homeDimension(level.getServer());
	}

	public static String dimId(Level level) {
		return level.dimension().identifier().toString();
	}

	public static @Nullable ServerLevel level(MinecraftServer server, String dim) {
		Identifier id = Identifier.tryParse(dim);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	// ------------------------------------------------------------------ state

	/** The friend's travel state (never null; changes are saved with the friend). */
	static CompoundTag state(CompanionEntity c) {
		CompoundTag tag = c.extra().getCompoundOrEmpty(KEY);
		if (!c.extra().contains(KEY)) {
			c.extra().put(KEY, tag);
		}
		return tag;
	}

	/** The way this friend came into the dimension they are in now (the portal they came in by), or null. */
	public static @Nullable Way arrival(CompanionEntity c) {
		CompoundTag t = c.extra().getCompoundOrEmpty(KEY);
		if (!t.getStringOr("arrDim", "").equals(dimId(c.level())) || t.getStringOr("fromDim", "").isEmpty()) {
			return null;
		}
		return new Way(BlockPos.of(t.getLongOr("arrAt", 0L)), t.getStringOr("fromDim", ""), BlockPos.of(t.getLongOr("fromAt", 0L)));
	}

	/**
	 * Remembers that this friend's leader went through a portal while the friend was too far away to come along:
	 * where it was on this side and where it came out, so the friend can follow.
	 */
	static void setChase(CompanionEntity c, UUID leader, BlockPos portal, String toDim, BlockPos toPos) {
		CompoundTag chase = new CompoundTag();
		chase.putString("leader", leader.toString());
		chase.putString("dim", dimId(c.level()));
		chase.putLong("at", portal.asLong());
		chase.putString("toDim", toDim);
		chase.putLong("toAt", toPos.asLong());
		state(c).put("chase", chase);
	}

	static void clearChase(CompanionEntity c) {
		CompoundTag t = c.extra().getCompoundOrEmpty(KEY);
		if (t.contains("chase")) {
			t.remove("chase");
		}
	}

	/**
	 * The way to where this friend's leader is now: the portal the leader went through, the way the friend came in
	 * (when the leader went back through it), or the nearest crossing a player has made between the two dimensions.
	 */
	static @Nullable Way wayTo(CompanionEntity c, ServerPlayer leader) {
		String here = dimId(c.level());
		String there = dimId(leader.level());
		CompoundTag chase = c.extra().getCompoundOrEmpty(KEY).getCompoundOrEmpty("chase");
		if (chase.getStringOr("leader", "").equals(leader.getUUID().toString()) && chase.getStringOr("dim", "").equals(here)
			&& chase.getStringOr("toDim", "").equals(there)) {
			return new Way(BlockPos.of(chase.getLongOr("at", 0L)), there, BlockPos.of(chase.getLongOr("toAt", 0L)));
		}
		Way back = arrival(c);
		if (back != null && back.toDim().equals(there) && back.portal().distSqr(c.blockPosition()) <= 192 * 192) {
			return back;
		}
		return ExpeditionData.get(leader.level().getServer()).wayBetween(here, c.blockPosition(), there, 160);
	}

	/** The way home: the portal they came in by, else the nearest known crossing to the camp's dimension, else null. */
	static @Nullable Way wayHome(CompanionEntity c) {
		ServerLevel level = (ServerLevel) c.level();
		String home = homeDimension(level.getServer()).identifier().toString();
		Way back = arrival(c);
		if (back != null) {
			return back;
		}
		return ExpeditionData.get(level.getServer()).wayBetween(dimId(level), c.blockPosition(), home, 256);
	}

	/** Ticks this friend has spent left on their own (saved, so a reload does not start the wait again). */
	static int waited(CompanionEntity c) {
		return c.extra().getCompoundOrEmpty(KEY).getIntOr("wait", 0);
	}

	static void setWaited(CompanionEntity c, int ticks) {
		if (ticks <= 0) {
			CompoundTag t = c.extra().getCompoundOrEmpty(KEY);
			if (t.contains("wait")) {
				t.remove("wait");
			}
		} else {
			state(c).putInt("wait", ticks);
		}
	}

	/**
	 * True while the expedition has asked for a roaming ticket for this friend (making their way to a portal). The
	 * survival package may have dropped the ticket since (nobody online, away too long): whether one is actually held is
	 * {@link ChunkLoader#isRoaming}, see {@link #holdsLand}.
	 */
	public static boolean roaming(CompanionEntity c) {
		return c.extra().getCompoundOrEmpty(KEY).getBooleanOr("roam", false);
	}

	/** True when the expedition asked for this friend's land to keep running and a ticket really is held where they are. */
	static boolean holdsLand(CompanionEntity c) {
		return roaming(c) && ChunkLoader.isRoaming(c);
	}

	/**
	 * Asks for a roaming ticket for this friend on the expedition's behalf (see {@link #roaming}). Returns false, with
	 * nothing remembered, when none can be given.
	 */
	static boolean holdLand(CompanionEntity c, String why) {
		setRoaming(c, true);
		if (ChunkLoader.startRoaming(c, why)) {
			return true;
		}
		setRoaming(c, false);
		return false;
	}

	static void setRoaming(CompanionEntity c, boolean roaming) {
		if (roaming) {
			state(c).putBoolean("roam", true);
		} else {
			CompoundTag t = c.extra().getCompoundOrEmpty(KEY);
			if (t.contains("roam")) {
				t.remove("roam");
			}
		}
	}

	// ------------------------------------------------------------------- home

	/**
	 * Where "home" is for a friend away from the camp's dimension ({@link CompanionEntity#awayHome}): their leader
	 * when following one here, otherwise the portal they came in by. In the End, without their leader, it is where they
	 * stand: the way they came in is the platform off the main island, and the way to it a bridge over the void (they
	 * go home from where they are, see {@link TravelGoal}). Null at home, or with none of these.
	 */
	static @Nullable BlockPos awayHome(CompanionEntity c) {
		if (!(c.level() instanceof ServerLevel level) || !abroad(c)) {
			return null;
		}
		if (c.mode() == CompanionMode.FOLLOW) {
			ServerPlayer leader = c.leader();
			if (leader != null && leader.isAlive() && leader.level() == level) {
				return leader.blockPosition();
			}
		}
		if (level.dimension() == Level.END) {
			return c.blockPosition();
		}
		Way back = arrival(c);
		return back != null ? back.portal() : null;
	}

	// --------------------------------------------------------------- crossing

	/**
	 * Takes a friend across to {@code to}, onto a safe spot near {@code near} (which must be loaded and ticking there).
	 * {@code portalHere} is the way they leave by and {@code portalThere} where it comes out: they remember both, so
	 * they can find their way back. Call outside entity ticking (the old entity is replaced by a new one). Returns the
	 * friend as they now are, or null when nothing happened (no safe spot, or the friend could not move).
	 */
	static @Nullable CompanionEntity cross(CompanionEntity c, ServerLevel to, BlockPos near, BlockPos portalHere, BlockPos portalThere) {
		if (!c.isAlive() || c.isRemoved() || !(c.level() instanceof ServerLevel from) || from == to) {
			return null;
		}
		BlockPos spot = safeSpot(to, near, 4);
		if (spot == null) {
			return null;
		}
		c.actions().reset();
		c.getNavigation().stop();
		c.setTarget(null);
		if (c.isUsingItem()) {
			c.stopUsingItem();
		}
		Entity moved = c.teleport(new TeleportTransition(to, Vec3.atBottomCenterOf(spot), Vec3.ZERO, c.getYRot(), c.getXRot(),
			TeleportTransition.DO_NOTHING));
		if (!(moved instanceof CompanionEntity friend)) {
			return null;
		}
		CompoundTag t = state(friend);
		t.putString("arrDim", dimId(to));
		t.putLong("arrAt", portalThere.asLong());
		t.putString("fromDim", dimId(from));
		t.putLong("fromAt", portalHere.asLong());
		t.remove("chase");
		t.remove("wait");
		t.remove("roam"); // a roaming ticket stays in the dimension it was granted in, and lapses there
		friend.setPortalCooldown(Expeditions.PORTAL_GUARD);
		friend.resetFallDistance(); // the new friend is made from the old one's data: a fall half done would land here
		return friend;
	}

	/**
	 * A safe place to stand within {@code radius} blocks of {@code base} (and three up or down) in loaded, ticking
	 * land: firm ground that is not magma or a campfire, room for feet and head, no water, lava, fire or portal there.
	 * Nearest first. Null when there is none.
	 */
	public static @Nullable BlockPos safeSpot(ServerLevel level, BlockPos base, int radius) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int[] dys = {0, 1, -1, 2, -2, 3, -3};
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy : dys) {
						m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
						if (standable(level, m)) {
							return m.immutable();
						}
					}
				}
			}
		}
		return null;
	}

	/** True when a friend could stand at {@code feet} safely (see {@link #safeSpot}). */
	public static boolean standable(ServerLevel level, BlockPos feet) {
		if (!level.isInWorldBounds(feet) || !level.isPositionEntityTicking(feet) || !level.isLoaded(feet.above())) {
			return false;
		}
		BlockPos under = feet.below();
		BlockState below = level.getBlockState(under);
		if (!below.isFaceSturdy(level, under, Direction.UP) || below.is(Blocks.MAGMA_BLOCK) || below.is(BlockTags.CAMPFIRES)
			|| !below.getFluidState().isEmpty()) {
			return false;
		}
		return clear(level, feet) && clear(level, feet.above());
	}

	private static boolean clear(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty() && !s.is(BlockTags.FIRE)
			&& !s.is(Blocks.NETHER_PORTAL) && !s.is(Blocks.END_PORTAL) && !s.is(Blocks.END_GATEWAY) && !s.is(Blocks.CACTUS)
			&& !s.is(Blocks.SWEET_BERRY_BUSH) && !s.is(Blocks.POWDER_SNOW) && !s.is(Blocks.WITHER_ROSE);
	}
}

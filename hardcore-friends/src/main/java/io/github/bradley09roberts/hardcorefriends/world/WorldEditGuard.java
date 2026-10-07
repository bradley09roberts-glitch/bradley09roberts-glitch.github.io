package io.github.bradley09roberts.hardcorefriends.world;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModTags;
import io.github.bradley09roberts.hardcorefriends.unity.Unity;

/**
 * The single gate for every block a friend changes. It keeps edits inside bounded zones, limits them to
 * allow-listed natural blocks or the friends' own placements, never touches block entities, and refuses to
 * work next to anything that looks player-built. Every change is logged for {@code /friends log}.
 */
public final class WorldEditGuard {
	/** Why a friend wants to change a block. Each reason has its own zone and allow-list. */
	public enum Reason {
		FARM,
		BUILD,
		INVENT,
		LANDSCAPE,
		MINE,
		GATHER_WOOD,
		GATHER_EARTH
	}

	public record Verdict(boolean allowed, String why) {
		public static final Verdict OK = new Verdict(true, "");

		public static Verdict deny(String why) {
			return new Verdict(false, why);
		}
	}

	private static final int MIN_TICKS_BETWEEN_EDITS = 4;

	private WorldEditGuard() {
	}

	// ------------------------------------------------------------------ zones

	/** Centre of the friend's working area: the camp in this dimension, or where they were recruited. */
	public static BlockPos zoneCentre(CompanionEntity c) {
		return c.homePos();
	}

	/** Radius of the camp (inner) zone for this friend. */
	public static int campRadius(CompanionEntity c) {
		return Camp.radius(Camp.data(c.level().getServer()));
	}

	public static boolean inCamp(CompanionEntity c, BlockPos pos) {
		BlockPos centre = zoneCentre(c);
		int r = campRadius(c);
		return Camp.horizontalDistSqr(centre, pos) <= (double) r * r && Math.abs(pos.getY() - centre.getY()) <= 24;
	}

	/** Camp plus the resource ring where gathering and mining are allowed. */
	public static boolean inResourceZone(CompanionEntity c, BlockPos pos) {
		int r = campRadius(c) + FriendsConfig.get().resourceRadius;
		return Camp.horizontalDistSqr(zoneCentre(c), pos) <= (double) r * r;
	}

	// ----------------------------------------------------------------- checks

	public static Verdict canBreak(CompanionEntity c, BlockPos pos, Reason reason) {
		ServerLevel level = (ServerLevel) c.level();
		FriendsConfig cfg = FriendsConfig.get();
		Verdict common = commonChecks(c, level, pos);
		if (!common.allowed()) {
			return common;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return Verdict.deny("nothing there");
		}
		if (state.hasBlockEntity()) {
			return Verdict.deny("block entity");
		}
		if (state.is(ModTags.NEVER_TOUCH) || state.getDestroySpeed(level, pos) < 0) {
			return Verdict.deny("protected block");
		}
		if (reason != Reason.FARM && touchesFluid(level, pos)) {
			return Verdict.deny("next to water or lava");
		}
		CampData data = Camp.data(level.getServer());
		boolean ownBlock = data.isPlacedByFriends(pos);
		switch (reason) {
			case FARM -> {
				if (!inCamp(c, pos)) {
					return Verdict.deny("outside the camp");
				}
				if (state.getBlock() instanceof CropBlock crop && crop.getAge(state) >= crop.getMaxAge()) {
					return Verdict.OK;
				}
				if ((state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) && hasAttachedStem(level, pos)) {
					return Verdict.OK;
				}
				return Verdict.deny("not a ripe crop");
			}
			case BUILD, INVENT, LANDSCAPE -> {
				if (!inCamp(c, pos)) {
					return Verdict.deny("outside the camp");
				}
				if (ownBlock || isClearablePlant(state)) {
					return Verdict.OK;
				}
				return Verdict.deny("only plants, snow and our own blocks may be cleared");
			}
			case MINE -> {
				if (!cfg.allowMining) {
					return Verdict.deny("mining disabled in config");
				}
				if (!inResourceZone(c, pos)) {
					return Verdict.deny("outside the mining area");
				}
				if (!state.is(ModTags.MINEABLE_NATURAL)) {
					return Verdict.deny("not natural stone or ore");
				}
				if (looksPlayerBuilt(level, pos, 2, data)) {
					return Verdict.deny("too close to a build");
				}
				return Verdict.OK;
			}
			case GATHER_WOOD -> {
				if (!cfg.allowTreeFelling) {
					return Verdict.deny("tree felling disabled in config");
				}
				if (!inResourceZone(c, pos)) {
					return Verdict.deny("outside the gathering area");
				}
				if (!state.is(BlockTags.LOGS) || !(c.isApprovedLog(pos) || TreeFinder.isNaturalTreeLog(level, pos))) {
					return Verdict.deny("not part of a natural tree");
				}
				if (looksPlayerBuilt(level, pos, 2, data)) {
					return Verdict.deny("too close to a build");
				}
				return Verdict.OK;
			}
			case GATHER_EARTH -> {
				if (!cfg.allowQuarrying) {
					return Verdict.deny("quarrying disabled in config");
				}
				if (inCamp(c, pos) || !inResourceZone(c, pos)) {
					return Verdict.deny("quarries must be outside the camp but inside the gathering ring");
				}
				if (!state.is(ModTags.EARTH_GATHERABLE)) {
					return Verdict.deny("not natural earth or stone");
				}
				if (looksPlayerBuilt(level, pos, 3, data)) {
					return Verdict.deny("too close to a build");
				}
				return Verdict.OK;
			}
			default -> {
				return Verdict.deny("unknown reason");
			}
		}
	}

	public static Verdict canPlace(CompanionEntity c, BlockPos pos, BlockState newState, Reason reason) {
		ServerLevel level = (ServerLevel) c.level();
		Verdict common = commonChecks(c, level, pos);
		if (!common.allowed()) {
			return common;
		}
		BlockState current = level.getBlockState(pos);
		boolean waterAllowed = reason == Reason.FARM && newState.is(Blocks.WATER);
		if (!(current.isAir() || (current.canBeReplaced() && current.getFluidState().isEmpty()))) {
			return Verdict.deny("space is occupied");
		}
		if (!current.getFluidState().isEmpty() && !waterAllowed) {
			return Verdict.deny("space holds fluid");
		}
		if (!level.isUnobstructed(newState, pos, CollisionContext.empty())) {
			return Verdict.deny("someone is standing there");
		}
		switch (reason) {
			case FARM, BUILD, INVENT, LANDSCAPE -> {
				if (!inCamp(c, pos)) {
					return Verdict.deny("outside the camp");
				}
				return Verdict.OK;
			}
			case MINE -> {
				return newState.is(Blocks.TORCH) || newState.is(Blocks.WALL_TORCH) || newState.is(Blocks.COBBLESTONE)
					? inResourceZone(c, pos) ? Verdict.OK : Verdict.deny("outside the mining area")
					: Verdict.deny("miners only place torches and cobblestone seals");
			}
			case GATHER_WOOD -> {
				return newState.is(BlockTags.SAPLINGS) && inResourceZone(c, pos) ? Verdict.OK
					: Verdict.deny("foragers only replant saplings");
			}
			default -> {
				return Verdict.deny("this job does not place blocks");
			}
		}
	}

	/** Changing a block in place: tilling, making a dirt path, resetting a berry bush, toggling own redstone. */
	public static Verdict canTransform(CompanionEntity c, BlockPos pos, BlockState newState, Reason reason) {
		ServerLevel level = (ServerLevel) c.level();
		Verdict common = commonChecks(c, level, pos);
		if (!common.allowed()) {
			return common;
		}
		if (!inCamp(c, pos)) {
			return Verdict.deny("outside the camp");
		}
		BlockState current = level.getBlockState(pos);
		if (current.hasBlockEntity() && !Camp.data(level.getServer()).isPlacedByFriends(pos)) {
			return Verdict.deny("block entity");
		}
		boolean airAbove = level.getBlockState(pos.above()).isAir();
		boolean earth = current.is(Blocks.GRASS_BLOCK) || current.is(Blocks.DIRT) || current.is(Blocks.COARSE_DIRT);
		return switch (reason) {
			case FARM -> {
				if (earth && newState.is(Blocks.FARMLAND) && airAbove) {
					yield Verdict.OK;
				}
				if (current.is(Blocks.SWEET_BERRY_BUSH) && newState.is(Blocks.SWEET_BERRY_BUSH)) {
					yield Verdict.OK;
				}
				yield Verdict.deny("not tillable or harvestable");
			}
			case LANDSCAPE -> earth && newState.is(Blocks.DIRT_PATH) && airAbove ? Verdict.OK : Verdict.deny("not path-able");
			case INVENT -> Camp.data(level.getServer()).isPlacedByFriends(pos) && current.getBlock() == newState.getBlock()
				? Verdict.OK : Verdict.deny("can only adjust our own contraptions");
			default -> Verdict.deny("this job does not reshape blocks");
		};
	}

	private static Verdict commonChecks(CompanionEntity c, ServerLevel level, BlockPos pos) {
		if (!FriendsConfig.get().allowWorldEditing) {
			return Verdict.deny("world editing disabled in config");
		}
		if (!level.isLoaded(pos) || !level.isInWorldBounds(pos)) {
			return Verdict.deny("not loaded");
		}
		if (level.getGameTime() - c.lastEditTick() < MIN_TICKS_BETWEEN_EDITS) {
			return Verdict.deny("pacing");
		}
		for (ServerPlayer player : level.players()) {
			BlockPos feet = player.blockPosition();
			if (Math.abs(feet.getX() - pos.getX()) <= 1 && Math.abs(feet.getZ() - pos.getZ()) <= 1
				&& pos.getY() >= feet.getY() - 2 && pos.getY() <= feet.getY() + 2) {
				return Verdict.deny("a player is right there");
			}
		}
		return Verdict.OK;
	}

	// ------------------------------------------------------------- performers

	/**
	 * Breaks a block (after the caller has spent the mining time), putting drops into the backpack with overflow on
	 * the ground. Damages the held tool if it was the right tool. Returns false if the guard refused.
	 */
	public static boolean breakBlock(CompanionEntity c, BlockPos pos, Reason reason) {
		Verdict v = canBreak(c, pos, reason);
		if (!v.allowed()) {
			return false;
		}
		ServerLevel level = (ServerLevel) c.level();
		BlockState state = level.getBlockState(pos);
		ItemStack tool = c.getMainHandItem();
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, c, tool);
		if (!level.destroyBlock(pos, false, c)) {
			return false;
		}
		for (ItemStack drop : drops) {
			ItemStack left = c.backpack().insert(drop);
			if (!left.isEmpty()) {
				c.spawnAtLocation(level, left);
			}
		}
		if (!tool.isEmpty() && tool.isDamageableItem() && state.getDestroySpeed(level, pos) > 0 && !Unity.carefulHands(c)) {
			c.damageMainHandTool(1);
		}
		CampData data = Camp.data(level.getServer());
		data.forgetPlaced(pos);
		record(c, data, "broke", state, pos, reason);
		return true;
	}

	/** Places a block. The caller is responsible for taking the matching item out of the backpack first. */
	public static boolean placeBlock(CompanionEntity c, BlockPos pos, BlockState state, Reason reason) {
		Verdict v = canPlace(c, pos, state, reason);
		if (!v.allowed()) {
			return false;
		}
		ServerLevel level = (ServerLevel) c.level();
		if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
			return false;
		}
		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		CampData data = Camp.data(level.getServer());
		if (reason != Reason.FARM || !(state.getBlock() instanceof CropBlock)) {
			data.recordPlaced(pos);
		}
		record(c, data, "placed", state, pos, reason);
		return true;
	}

	/** Changes a block in place (till, path, berry reset, contraption adjustment). */
	public static boolean transformBlock(CompanionEntity c, BlockPos pos, BlockState newState, Reason reason) {
		Verdict v = canTransform(c, pos, newState, reason);
		if (!v.allowed()) {
			return false;
		}
		ServerLevel level = (ServerLevel) c.level();
		BlockState old = level.getBlockState(pos);
		if (!level.setBlock(pos, newState, Block.UPDATE_ALL)) {
			return false;
		}
		SoundType sound = newState.getSoundType();
		level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
		CampData data = Camp.data(level.getServer());
		if (newState.is(Blocks.DIRT_PATH) || newState.is(Blocks.FARMLAND)) {
			data.recordPlaced(pos);
		}
		record(c, data, "changed " + BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath() + " to", newState, pos, reason);
		return true;
	}

	private static void record(CompanionEntity c, CampData data, String verb, BlockState state, BlockPos pos, Reason reason) {
		ServerLevel level = (ServerLevel) c.level();
		c.setLastEditTick(level.getGameTime());
		data.logEdit(String.format("day %d: %s %s %s at %d %d %d (%s)", Camp.day(level), c.friendId().displayName(), verb,
			BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), pos.getX(), pos.getY(), pos.getZ(),
			reason.name().toLowerCase(java.util.Locale.ROOT)));
		data.addStat("edits." + reason.name().toLowerCase(java.util.Locale.ROOT), 1);
	}

	// ---------------------------------------------------------------- helpers

	/** Short grass, ferns, flowers, dead bushes, snow layers and similar replaceable plants. */
	public static boolean isClearablePlant(BlockState state) {
		return state.canBeReplaced() && state.getFluidState().isEmpty() && !state.isAir() || state.is(Blocks.SNOW);
	}

	public static boolean touchesFluid(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (!level.getFluidState(pos.relative(d)).isEmpty()) {
				return true;
			}
		}
		return !level.getFluidState(pos).isEmpty();
	}

	private static boolean hasAttachedStem(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState s = level.getBlockState(pos.relative(d));
			if (s.is(Blocks.ATTACHED_MELON_STEM) || s.is(Blocks.ATTACHED_PUMPKIN_STEM) || s.getBlock() instanceof StemBlock) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if any block within {@code radius} looks player-made (tag {@code hardcorefriends:build_markers}) and was
	 * not placed by the friends themselves.
	 */
	public static boolean looksPlayerBuilt(ServerLevel level, BlockPos pos, int radius, CampData data) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (!level.isLoaded(m)) {
						continue;
					}
					BlockState s = level.getBlockState(m);
					if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(m)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}

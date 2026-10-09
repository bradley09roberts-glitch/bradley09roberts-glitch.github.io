package io.github.bradley09roberts.hardcorefriends.world;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import io.github.bradley09roberts.hardcorefriends.ai.role.mine.MinePlan;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprints;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteClearing;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;
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
		GATHER_EARTH,
		/** Levelling a building site: digging down bumps and filling dips, inside a recorded site plan. */
		GRADE,
		/** Blocks placed to stay alive away from camp (a night shelter, a pillar out of reach, a bridge), and taken back. */
		SURVIVAL,
		/** Pouring water on lava to make obsidian, and similar casting. */
		CAST,
		/** Work in the Nether and the End with a player (portals, pillars to the end crystals, breaking their cages). */
		EXPEDITION
	}

	/**
	 * The zone and allow-list of a reason whose rules live in a feature package, registered in {@link #POLICIES}. The
	 * guard's common checks (editing allowed, loaded, pacing, no player right there; for breaking also no block
	 * entity, no protected or unbreakable block and no breaching water or lava) have already passed when it is asked.
	 */
	public interface Policy {
		Verdict canBreak(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState state);

		Verdict canPlace(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState);

		default Verdict canTransform(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState newState) {
			return Verdict.deny("this job does not reshape blocks");
		}

		/**
		 * True if this reason may break a block that is otherwise never touched ({@code ModTags.NEVER_TOUCH}), such as
		 * obsidian the friends cast themselves. Asked before {@link #canBreak}; the default keeps protected blocks safe.
		 */
		default boolean mayBreakProtected(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState state) {
			return false;
		}
	}

	/** Rules for the reasons the feature packages own (GRADE, SURVIVAL, CAST, EXPEDITION). A reason with none is refused. */
	public static final Map<Reason, Policy> POLICIES = new ConcurrentHashMap<>();

	public record Verdict(boolean allowed, String why) {
		public static final Verdict OK = new Verdict(true, "");

		public static Verdict deny(String why) {
			return new Verdict(false, why);
		}
	}

	/** One completed block change, reported to {@link #listener} (used by automated tests to audit every edit). */
	public record EditEvent(CompanionEntity companion, String verb, BlockPos pos, BlockState state, Reason reason) {
	}

	/** Optional observer of every completed edit. Null in normal play. */
	public static volatile java.util.function.@org.jspecify.annotations.Nullable Consumer<EditEvent> listener;
	/** Feature packages' observers of every completed edit (the camp's experience from mined ores, for one). */
	public static final List<java.util.function.Consumer<EditEvent>> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	private static final int MIN_TICKS_BETWEEN_EDITS = 4;
	/** How far up a column of sand or gravel is followed when checking for water or lava above it. */
	private static final int MAX_FALLING_COLUMN = 16;

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

	/** Inside the camp radius, at any height (a hill above the camp is still the camp). */
	public static boolean inCampHorizontally(CompanionEntity c, BlockPos pos) {
		int r = campRadius(c);
		return Camp.horizontalDistSqr(zoneCentre(c), pos) <= (double) r * r;
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
		Policy policy = POLICIES.get(reason);
		if (state.is(ModTags.NEVER_TOUCH) && (policy == null || !policy.mayBreakProtected(c, level, pos, state))
			|| state.getDestroySpeed(level, pos) < 0) {
			return Verdict.deny("protected block");
		}
		if (reason != Reason.FARM && breachesFluid(level, pos)) {
			return Verdict.deny("next to water or lava");
		}
		if (policy != null) {
			return policy.canBreak(c, level, pos, state);
		}
		CampData data = Camp.data(level.getServer());
		boolean ownBlock = data.isPlacedByFriends(level, pos);
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
				if (state.is(Blocks.SUGAR_CANE) && level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)) {
					return Verdict.OK; // the top of a sugar cane, leaving its root to grow again
				}
				if (state.is(Blocks.NETHER_WART) && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
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
				if (reason == Reason.BUILD && SiteClearing.isNaturalLeaves(state)) {
					return Verdict.OK; // trimming a tree's leaves out of a building's way (placed leaves are persistent)
				}
				return Verdict.deny("only plants, snow, natural leaves and our own blocks may be cleared");
			}
			case MINE -> {
				if (!cfg.allowMining) {
					return Verdict.deny("mining disabled in config");
				}
				if (!inResourceZone(c, pos)) {
					return Verdict.deny("outside the mining area");
				}
				// A miner may take back the cobblestone seals the friends put in their own tunnels (deep in a mine's box).
				boolean ownSeal = ownBlock && (state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE))
					&& inMineTunnels(level, data, pos);
				if (!state.is(ModTags.MINEABLE_NATURAL) && !ownSeal) {
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
				if (inCampHorizontally(c, pos) || !inResourceZone(c, pos)) {
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
		Policy policy = POLICIES.get(reason);
		if (policy != null) {
			return policy.canPlace(c, level, pos, newState);
		}
		switch (reason) {
			case FARM -> {
				return inCamp(c, pos) ? Verdict.OK : Verdict.deny("outside the camp");
			}
			case BUILD, INVENT, LANDSCAPE -> {
				if (!inCamp(c, pos)) {
					return Verdict.deny("outside the camp");
				}
				if (restsOnPlayerBuild(level, pos.below())) {
					return Verdict.deny("would sit on a player's build");
				}
				return Verdict.OK;
			}
			case MINE -> {
				boolean torch = newState.is(Blocks.TORCH) || newState.is(Blocks.WALL_TORCH);
				if (!torch && !newState.is(Blocks.COBBLESTONE) && !newState.is(Blocks.COBBLED_DEEPSLATE)) {
					return Verdict.deny("miners only place torches and cobblestone seals");
				}
				if (!inResourceZone(c, pos)) {
					return Verdict.deny("outside the mining area");
				}
				if (torch) {
					return Verdict.OK;
				}
				// Seals only go in (or right beside) the friends' own mines, never into a tunnel a player dug.
				CampData data = Camp.data(level.getServer());
				if (!nearMineBox(level, data, pos)) {
					return Verdict.deny("seals only go in our own mines");
				}
				if (looksPlayerBuilt(level, pos, 3, data)) {
					return Verdict.deny("too close to a build");
				}
				return Verdict.OK;
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

	/**
	 * Changing a block in place: tilling, making a dirt path, resetting a berry bush, toggling own redstone, opening and
	 * shutting the gate of the friends' own animal pen.
	 */
	public static Verdict canTransform(CompanionEntity c, BlockPos pos, BlockState newState, Reason reason) {
		ServerLevel level = (ServerLevel) c.level();
		Verdict common = commonChecks(c, level, pos);
		if (!common.allowed()) {
			return common;
		}
		Policy policy = POLICIES.get(reason);
		if (policy != null) {
			return policy.canTransform(c, level, pos, newState);
		}
		BlockState current = level.getBlockState(pos);
		// Picking berries is allowed anywhere friends may gather; everything else stays inside the camp.
		boolean berryPick = reason == Reason.FARM && current.is(Blocks.SWEET_BERRY_BUSH) && newState.is(Blocks.SWEET_BERRY_BUSH);
		if (berryPick) {
			return inResourceZone(c, pos) ? Verdict.OK : Verdict.deny("outside the gathering area");
		}
		if (!inCamp(c, pos)) {
			return Verdict.deny("outside the camp");
		}
		if (current.hasBlockEntity() && !Camp.data(level.getServer()).isPlacedByFriends(level, pos)) {
			return Verdict.deny("block entity");
		}
		boolean airAbove = level.getBlockState(pos.above()).isAir();
		boolean earth = current.is(Blocks.GRASS_BLOCK) || current.is(Blocks.DIRT) || current.is(Blocks.COARSE_DIRT);
		return switch (reason) {
			case FARM -> {
				if (earth && newState.is(Blocks.FARMLAND) && airAbove) {
					yield Verdict.OK;
				}
				if (current.getBlock() instanceof CropBlock && current.getBlock() == newState.getBlock()) {
					yield Verdict.OK; // bone meal growth
				}
				if (current.getBlock() instanceof FenceGateBlock && current.getBlock() == newState.getBlock()
					&& Camp.data(level.getServer()).isPlacedByFriends(level, pos)) {
					yield Verdict.OK; // opening or shutting the gate of the friends' own animal pen
				}
				yield Verdict.deny("not tillable or harvestable");
			}
			case LANDSCAPE -> earth && newState.is(Blocks.DIRT_PATH) && airAbove ? Verdict.OK : Verdict.deny("not path-able");
			case INVENT -> Camp.data(level.getServer()).isPlacedByFriends(level, pos) && current.getBlock() == newState.getBlock()
				? Verdict.OK : Verdict.deny("can only adjust our own contraptions");
			// The friends' own anvil wears with use, as an anvil does for a player: chipped, damaged, then gone.
			case BUILD -> current.is(BlockTags.ANVIL) && Camp.data(level.getServer()).isPlacedByFriends(level, pos)
				&& (newState.isAir() ? AnvilBlock.damage(current) == null : newState.equals(AnvilBlock.damage(current)))
				? Verdict.OK : Verdict.deny("this job does not reshape blocks");
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
	 *
	 * <p>Like a player, a friend only gets drops from blocks that need a proper tool (stone, ores) when holding one:
	 * stone broken with an axe or bare hands is lost. Blocks the friends placed themselves always come back, so
	 * taking down their own work never wastes camp materials.
	 */
	public static boolean breakBlock(CompanionEntity c, BlockPos pos, Reason reason) {
		Verdict v = canBreak(c, pos, reason);
		if (!v.allowed()) {
			return false;
		}
		ServerLevel level = (ServerLevel) c.level();
		BlockState state = level.getBlockState(pos);
		ItemStack tool = c.getMainHandItem();
		CampData data = Camp.data(level.getServer());
		boolean harvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state)
			|| data.isPlacedByFriends(level, pos);
		List<ItemStack> drops = harvest ? Block.getDrops(state, level, pos, null, c, tool) : List.of();
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
		data.forgetPlaced(level, pos);
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
			data.recordPlaced(level, pos, state);
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
		if (newState.is(Blocks.DIRT_PATH) || newState.is(Blocks.FARMLAND) || newState.is(BlockTags.ANVIL)) {
			data.recordPlaced(level, pos, newState);
		}
		record(c, data, "changed " + BuiltInRegistries.BLOCK.getKey(old.getBlock()).getPath() + " to", newState, pos, reason);
		return true;
	}

	private static void record(CompanionEntity c, CampData data, String verb, BlockState state, BlockPos pos, Reason reason) {
		ServerLevel level = (ServerLevel) c.level();
		var observer = listener;
		if (observer != null || !LISTENERS.isEmpty()) {
			EditEvent event = new EditEvent(c, verb, pos.immutable(), state, reason);
			if (observer != null) {
				observer.accept(event);
			}
			for (java.util.function.Consumer<EditEvent> each : LISTENERS) {
				each.accept(event);
			}
		}
		c.setLastEditTick(level.getGameTime());
		data.logEdit(String.format("day %d: %s %s %s at %d %d %d (%s)", Camp.day(level), c.displayName(), verb,
			BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), pos.getX(), pos.getY(), pos.getZ(),
			reason.name().toLowerCase(java.util.Locale.ROOT)));
		data.addStat("edits." + reason.name().toLowerCase(java.util.Locale.ROOT), 1);
	}

	// ---------------------------------------------------------------- helpers

	/** Short grass, ferns, flowers, dead bushes, snow layers and similar replaceable plants. */
	public static boolean isClearablePlant(BlockState state) {
		return state.canBeReplaced() && state.getFluidState().isEmpty() && !state.isAir() || state.is(Blocks.SNOW);
	}

	/** True if the block or any neighbour holds water or lava. A neighbour in an unloaded chunk counts as wet. */
	public static boolean touchesFluid(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (!level.isLoaded(n) || !level.getFluidState(n).isEmpty()) {
				return true;
			}
		}
		return !level.getFluidState(pos).isEmpty();
	}

	/**
	 * True if breaking this block could let water or lava in: the block touches fluid, or it holds up a column of
	 * sand, gravel or other falling blocks that touches fluid (the column drops into the gap and the fluid follows).
	 */
	public static boolean breachesFluid(ServerLevel level, BlockPos pos) {
		if (touchesFluid(level, pos)) {
			return true;
		}
		BlockPos.MutableBlockPos p = pos.mutable();
		for (int i = 0; i < MAX_FALLING_COLUMN; i++) {
			p.move(Direction.UP);
			if (!level.isInWorldBounds(p) || !(level.getBlockState(p).getBlock() instanceof Fallable)) {
				return false;
			}
			if (touchesFluid(level, p)) {
				return true;
			}
		}
		return true; // a taller column than we are willing to check: assume the worst
	}

	/**
	 * True if a new block placed on top of {@code below} would sit on something that looks player-built and that the
	 * friends did not place. The linked supply chest is shared (Spark's drop-off hopper sits on it), and the blocks of
	 * the friends' own reserved buildings count as theirs even if a player patched one of them.
	 */
	private static boolean restsOnPlayerBuild(ServerLevel level, BlockPos below) {
		if (!level.isLoaded(below)) {
			return true;
		}
		BlockState s = level.getBlockState(below);
		if (!s.is(ModTags.BUILD_MARKERS) && !s.hasBlockEntity()) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		if (data.isPlacedByFriends(level, below) || SiteFinder.chestHalves(level, data).contains(below)) {
			return false;
		}
		return !Camp.isCampLevel(level, data) || !partOfFriendsBuilding(data, below);
	}

	/** True if {@code pos} is one of the blocks (not the foundations) of a building site the friends reserved. */
	private static boolean partOfFriendsBuilding(CampData data, BlockPos pos) {
		for (String id : data.sites().keySet()) {
			Optional<Blueprint> bp = Blueprints.forId(id);
			if (bp.isEmpty()) {
				continue;
			}
			for (Placement p : Blueprints.placements(bp.get(), SiteFinder.parts(data, bp.get()))) {
				if (!p.isFoundation() && p.pos().equals(pos)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Inside the box of one of the friends' mines (the staircase mine or the deep one) and well below the camp: where
	 * the miners' own seals are, and never a building's cobblestone.
	 */
	private static boolean inMineTunnels(ServerLevel level, CampData data, BlockPos pos) {
		Optional<BlockPos> centre = data.campPos();
		if (centre.isPresent() && pos.getY() >= centre.get().getY() - 4) {
			return false;
		}
		for (String key : new String[] {MinePlan.KEY, MinePlan.DEEP_KEY}) {
			MinePlan plan = MinePlan.of(data, key);
			if (plan.isIn(level) && plan.inBox(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Inside the box of one of the friends' mines in this level, or touching it (a hole beside a tunnel at its edge). */
	private static boolean nearMineBox(ServerLevel level, CampData data, BlockPos pos) {
		for (String key : new String[] {MinePlan.KEY, MinePlan.DEEP_KEY}) {
			MinePlan plan = MinePlan.of(data, key);
			if (plan.exists() && plan.isIn(level) && plan.box().inflatedBy(1).isInside(pos)) {
				return true;
			}
		}
		return false;
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
					if ((s.is(ModTags.BUILD_MARKERS) || s.hasBlockEntity()) && !data.isPlacedByFriends(level, m)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}

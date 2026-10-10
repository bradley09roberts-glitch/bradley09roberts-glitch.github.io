package io.github.bradley09roberts.hardcorefriends.pets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import io.github.bradley09roberts.hardcorefriends.architecture.Construction;
import io.github.bradley09roberts.hardcorefriends.camp.Blueprint;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.Structures;
import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * Where finished maps hang: on the inside walls of the village's town hall (a building site whose key names the town
 * hall, finished), or, before there is one, the camp's library. A frame only ever goes on a wall block the friends
 * placed themselves (never a player's build), inside under a roof, in the camp, away from doors and from anything
 * a player built, and on a spot the edit guard would let a builder place a block (so the guard's own rules apply:
 * world editing switched on, loaded, not a child, no player standing right there). Item frames are entities rather
 * than blocks, so the guard is asked about the spot and the hanging is written in the camp's edit log by hand.
 */
final class MapFrames {
	/** An item frame's spot: the air block it hangs in and the way it faces (away from its wall). */
	record Spot(BlockPos pos, Direction facing, String building) {
		BlockPos wall() {
			return pos.relative(facing.getOpposite());
		}
	}

	/** How far round the building's chosen spot (its lectern, else its inside) frames are looked for. */
	private static final int SEARCH = 7;
	/** At most this many maps hang in one building. */
	private static final int MAX_FRAMES = 16;

	private MapFrames() {
	}

	/** The site key of the building maps hang in, if one stands: the town hall first, else the camp's library. */
	static Optional<String> building(ServerLevel level) {
		CampData data = Camp.data(level.getServer());
		if (!Camp.isCampLevel(level, data)) {
			return Optional.empty();
		}
		for (String key : List.copyOf(data.sites().keySet())) {
			if (key.contains("town_hall") && (data.isCompleted(key) || Construction.isFinished(level, key))) {
				return Optional.of(key);
			}
		}
		for (String key : List.copyOf(data.sites().keySet())) {
			if (Construction.planOf(level, key).map(p -> "civic:town_hall".equals(p.kind())).orElse(false)
				&& Construction.isFinished(level, key)) {
				return Optional.of(key);
			}
		}
		if (data.isCompleted(Structures.LIBRARY) && data.site(Structures.LIBRARY).isPresent()) {
			return Optional.of(Structures.LIBRARY);
		}
		return Optional.empty();
	}

	/** "the town hall" or "the library", for messages. */
	static String buildingName(String key) {
		return key.contains("town_hall") ? "the town hall" : key.equals(Structures.LIBRARY) ? "the library" : "the hall";
	}

	/**
	 * The best free spot for a frame in that building, nearest its lectern (else its inside marker, else its middle),
	 * or null if there is none. A bounded search of the building's own box: a few hundred blocks, done once per hanging.
	 */
	static @Nullable Spot freeSpot(ServerLevel level, String key) {
		CampData data = Camp.data(level.getServer());
		Optional<CampData.Site> site = data.site(key);
		Optional<Blueprint> plan = Construction.planOf(level, key);
		if (site.isEmpty() || plan.isEmpty()) {
			return null;
		}
		int[] box = plan.get().footprint(site.get().origin, site.get().rotation);
		BlockPos anchor = anchor(level, key, site.get(), box);
		if (countFrames(level, box, anchor) >= MAX_FRAMES) {
			return null;
		}
		List<Spot> spots = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = Math.max(box[0], anchor.getX() - SEARCH); x <= Math.min(box[2], anchor.getX() + SEARCH); x++) {
			for (int z = Math.max(box[1], anchor.getZ() - SEARCH); z <= Math.min(box[3], anchor.getZ() + SEARCH); z++) {
				for (int y = anchor.getY(); y <= anchor.getY() + 2; y++) {
					m.set(x, y, z);
					if (!level.isLoaded(m)) {
						continue;
					}
					for (Direction facing : Direction.Plane.HORIZONTAL) {
						Spot spot = new Spot(m.immutable(), facing, key);
						if (fits(level, data, spot)) {
							spots.add(spot);
						}
					}
				}
			}
		}
		spots.sort(Comparator.comparingDouble(s -> s.pos().distSqr(anchor)));
		return spots.isEmpty() ? null : spots.getFirst();
	}

	/** The lectern, else the inside marker, else the middle of the box one up from the floor. */
	private static BlockPos anchor(ServerLevel level, String key, CampData.Site site, int[] box) {
		for (String marker : new String[] {"lectern", "inside", "table"}) {
			List<BlockPos> found = Construction.markers(level, key, marker);
			if (!found.isEmpty()) {
				return found.getFirst();
			}
		}
		return new BlockPos((box[0] + box[2]) / 2, site.origin.getY() + 1, (box[1] + box[3]) / 2);
	}

	private static int countFrames(ServerLevel level, int[] box, BlockPos anchor) {
		AABB area = new AABB(box[0], anchor.getY() - 2, box[1], box[2] + 1, anchor.getY() + 4, box[3] + 1);
		return level.getEntitiesOfClass(ItemFrame.class, area, f -> f.entityTags().contains(Pets.FRAME_TAG)).size();
	}

	/**
	 * True if a frame may hang here: open air under a roof, on a solid wall block the friends placed, inside the camp,
	 * not beside a door, with no other picture or frame there and nothing a player built within a block.
	 */
	static boolean fits(ServerLevel level, CampData data, Spot spot) {
		BlockPos pos = spot.pos();
		BlockState here = level.getBlockState(pos);
		if (!here.isAir() || !level.getFluidState(pos).isEmpty() || level.canSeeSky(pos)) {
			return false;
		}
		BlockPos wall = spot.wall();
		BlockState wallState = level.getBlockState(wall);
		if (!wallState.isFaceSturdy(level, wall, spot.facing()) || wallState.hasBlockEntity() || !data.isPlacedByFriends(level, wall)) {
			return false;
		}
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(pos.relative(d));
			if (n.is(BlockTags.DOORS) || n.is(Blocks.LADDER) || n.is(BlockTags.BEDS)) {
				return false;
			}
		}
		if (!level.getEntitiesOfClass(HangingEntity.class, new AABB(pos).inflate(0.1)).isEmpty()) {
			return false;
		}
		return !WorldEditGuard.looksPlayerBuilt(level, pos, 1, data);
	}

	/**
	 * Hangs the map in a new frame at the spot, through the guard's checks: true if it is up. The frame itself (made
	 * from the camp's sticks and leather) and the map must already have been taken from the friend's backpack by the
	 * caller; nothing is used up when this fails.
	 */
	static boolean hang(CompanionEntity c, Spot spot, ItemStack map) {
		if (!(c.level() instanceof ServerLevel level)) {
			return false;
		}
		CampData data = Camp.data(level.getServer());
		if (!level.isLoaded(spot.pos()) || !fits(level, data, spot) || !WorldEditGuard.inCamp(c, spot.pos())) {
			return false;
		}
		// The guard's own word on the spot: as if a builder were putting a block there (nothing is, but the same rules
		// keep a frame out of a player's way and out of the world when editing is switched off).
		if (!WorldEditGuard.canPlace(c, spot.pos(), Blocks.AIR.defaultBlockState(), WorldEditGuard.Reason.BUILD).allowed()) {
			return false;
		}
		ItemFrame frame = new ItemFrame(level, spot.pos(), spot.facing());
		if (!frame.survives()) {
			return false;
		}
		frame.setItem(map.copyWithCount(1), false);
		frame.addTag(Pets.FRAME_TAG);
		if (!level.addFreshEntity(frame)) {
			return false;
		}
		level.playSound(null, spot.pos(), SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
		c.setLastEditTick(level.getGameTime());
		c.swingArm();
		BlockPos p = spot.pos();
		data.logEdit(String.format("day %d: %s hung a map in an item frame at %d %d %d (build)", Camp.day(level),
			c.displayName(), p.getX(), p.getY(), p.getZ()));
		data.addStat("maps_hung", 1);
		return true;
	}

	/** The frame holding this map at its recorded spot, if that spot is loaded and the frame still holds it. */
	static Optional<ItemFrame> frameWith(ServerLevel level, BlockPos pos, int mapId) {
		for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, new AABB(pos).inflate(0.5))) {
			ItemStack held = f.getItem();
			var id = held.get(net.minecraft.core.component.DataComponents.MAP_ID);
			if (id != null && id.id() == mapId) {
				return Optional.of(f);
			}
		}
		return Optional.empty();
	}
}

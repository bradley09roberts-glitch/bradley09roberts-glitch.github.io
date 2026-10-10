package io.github.bradley09roberts.hardcorefriends.life;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.bradley09roberts.hardcorefriends.ai.task.needs.Spots;
import io.github.bradley09roberts.hardcorefriends.camp.Camp;
import io.github.bradley09roberts.hardcorefriends.camp.CampData;
import io.github.bradley09roberts.hardcorefriends.camp.SiteFinder;
import io.github.bradley09roberts.hardcorefriends.village.VillagePlan;
import io.github.bradley09roberts.hardcorefriends.world.WorldEditGuard;

/**
 * The cemetery: a small plot of graves at the edge of the village (behind the chapel once there is one), in rows that
 * face the camp. Each grave is a headstone (a stone wall post, or a stone block), a sign in front of it with the name,
 * the day and "Rest well", and a flower either side. The plot is chosen once, on level natural ground inside the camp,
 * away from the streets, the other buildings' sites and anything a player built; each grave's ground is then reserved as
 * a camp site of its own ({@code life.grave.N}), so no plot, street or building is ever put over it.
 *
 * <p>The town plan has no cemetery of its own to ask for, so the friends lay this one out themselves. Every block goes
 * through the edit guard, from the camp's own stock (see {@link GraveTask}).
 */
final class Graves {
	/** Graves in a row, and the step between them (headstone, flower, gap). */
	static final int PER_ROW = 4;
	static final int CELL_STEP = 3;
	/** The step from one row to the next, away from the camp. */
	static final int ROW_STEP = 4;
	/** Headstones, the best first; any one of them will do. */
	static final List<Item> HEADSTONES = List.of(Items.STONE_BRICK_WALL, Items.MOSSY_STONE_BRICK_WALL, Items.COBBLESTONE_WALL,
		Items.MOSSY_COBBLESTONE_WALL, Items.ANDESITE_WALL, Items.STONE_BRICKS, Items.MOSSY_STONE_BRICKS, Items.COBBLESTONE);
	/** Places in the camp within this of the camp centre are the square's: no graves. */
	private static final int CLEAR_OF_CENTRE = 8;
	/** How far a grave keeps from the main streets through the camp centre and the lanes (the village's grid). */
	private static final int CLEAR_OF_STREETS = 4;
	/** The village's lanes run this far out from the centre. */
	private static final int LANES = 34;

	private Graves() {
	}

	static boolean isHeadstone(ItemStack s) {
		for (Item item : HEADSTONES) {
			if (s.is(item)) {
				return true;
			}
		}
		return false;
	}

	/** A standing sign of any wood (not a hanging one). */
	static boolean isSign(ItemStack s) {
		return !s.isEmpty() && s.is(ItemTags.SIGNS) && Block.byItem(s.getItem()) instanceof StandingSignBlock;
	}

	/** A small flower to leave on a grave: never a wither rose. */
	static boolean isFlower(ItemStack s) {
		return !s.isEmpty() && s.is(BlockItemTags.SMALL_FLOWERS.item()) && !s.is(Items.WITHER_ROSE);
	}

	/** The direction a grave's sign faces (towards the camp). */
	static Direction facing(LifeData.Grave g) {
		Direction d = Direction.from2DDataValue(g.facing);
		return d.getAxis().isHorizontal() ? d : Direction.NORTH;
	}

	/** Where the sign stands: one block in front of the headstone. */
	static BlockPos signPos(LifeData.Grave g) {
		return g.pos == null ? BlockPos.ZERO : g.pos.relative(facing(g));
	}

	/** The two flower spots either side of the headstone. */
	static List<BlockPos> flowerSpots(LifeData.Grave g) {
		if (g.pos == null) {
			return List.of();
		}
		Direction side = facing(g).getClockWise();
		return List.of(g.pos.relative(side), g.pos.relative(side.getOpposite()));
	}

	/** Where a visitor stands: two blocks in front of the headstone (or the nearest standable spot). */
	static @Nullable BlockPos visitorSpot(ServerLevel level, LifeData.Grave g) {
		if (g.pos == null) {
			return null;
		}
		return Places.standableNear(level, g.pos.relative(facing(g), 2), 1);
	}

	/** The graves made and standing in this world. */
	static List<LifeData.Grave> made(ServerLevel level, LifeData data) {
		List<LifeData.Grave> list = new ArrayList<>();
		String dim = Camp.dimensionId(level);
		for (LifeData.Grave g : data.graves) {
			if (g.made && g.pos != null && g.dimension.equals(dim)) {
				list.add(g);
			}
		}
		return list;
	}

	// ----------------------------------------------------------------- placing

	/**
	 * A headstone position for this grave: the one already chosen, or the next free cell of the cemetery (choosing the
	 * cemetery itself the first time). Null when there is no fitting ground: the camp is crowded, hilly or built over.
	 * Bounded: a few hundred block reads, once per grave.
	 */
	static @Nullable BlockPos place(ServerLevel level, LifeData data, LifeData.Grave grave) {
		CampData camp = Camp.data(level.getServer());
		if (camp.campPos().isEmpty() || !Camp.isCampLevel(level, camp)) {
			return null;
		}
		List<int[]> reserved = reserved(camp);
		if (grave.pos != null) {
			// Begun already (its headstone stands), or still free: keep it. Otherwise something took the spot meanwhile.
			if (camp.isPlacedByFriends(level, grave.pos) || cellOk(level, camp, grave.pos, facing(grave), reserved)) {
				return grave.pos;
			}
			grave.pos = null;
			data.setDirty();
		}
		boolean fresh = false;
		if (data.cemetery == null || !data.cemeteryDimension.equals(camp.campDimension()) || !Places.inCamp(level, data.cemetery)) {
			BlockPos anchor = chooseAnchor(level, camp, reserved, data.nextGrave);
			if (anchor == null) {
				return null;
			}
			fresh = true;
			data.cemetery = anchor;
			data.cemeteryDimension = camp.campDimension();
			data.cemeteryFacing = SiteFinder.directionTo(anchor, camp.campPos().get()).get2DDataValue();
			data.nextCell = 0;
			data.setDirty();
		}
		Direction toward = Direction.from2DDataValue(data.cemeteryFacing);
		for (int i = data.nextCell; i < data.nextCell + 12; i++) {
			BlockPos h = surface(level, cell(data.cemetery, toward, i));
			if (h != null && cellOk(level, camp, h, toward, reserved)) {
				grave.pos = h;
				grave.facing = toward.get2DDataValue();
				grave.dimension = camp.campDimension();
				data.nextCell = i + 1;
				data.setDirty();
				return h;
			}
		}
		if (!fresh) {
			data.cemetery = null; // this plot is full or built over: the next grave starts a new one
			data.setDirty();
		}
		return null;
	}

	/** Grave {@code i}'s headstone column: rows of {@value #PER_ROW} along the side, each row further from the camp. */
	static BlockPos cell(BlockPos anchor, Direction toward, int i) {
		Direction side = toward.getClockWise();
		return anchor.relative(side, CELL_STEP * (i % PER_ROW)).relative(toward.getOpposite(), ROW_STEP * (i / PER_ROW));
	}

	/** The first free block above the ground in this column (null if the chunk is not loaded). */
	private static @Nullable BlockPos surface(ServerLevel level, BlockPos column) {
		if (!level.isLoaded(column)) {
			return null;
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
		return new BlockPos(column.getX(), y, column.getZ());
	}

	/**
	 * The camp's reserved building sites, but not the graves' own small reservations: graves sit side by side in their
	 * rows, which the cells already space out.
	 */
	private static List<int[]> reserved(CampData camp) {
		List<int[]> boxes = new ArrayList<>(SiteFinder.reservedBoxes(camp, ""));
		for (Map.Entry<String, CampData.Site> e : camp.sites().entrySet()) {
			if (e.getKey().startsWith("life.grave.")) {
				BlockPos o = e.getValue().origin;
				boxes.removeIf(b -> b[0] == o.getX() - 1 && b[1] == o.getZ() - 1 && b[2] == o.getX() + 1 && b[3] == o.getZ() + 1);
			}
		}
		return boxes;
	}

	/**
	 * Chooses where the cemetery begins: behind or beside the chapel once there is one, otherwise towards the edge of
	 * the camp, off the streets. {@code turn} varies the search between attempts.
	 */
	private static @Nullable BlockPos chooseAnchor(ServerLevel level, CampData camp, List<int[]> reserved, int turn) {
		BlockPos centre = camp.campPos().orElseThrow();
		List<BlockPos> around = new ArrayList<>();
		for (VillagePlan.Building chapel : VillagePlan.buildingsOfKind(level.getServer(), "civic:chapel")) {
			around.add(chapel.origin());
		}
		int radius = Camp.radius(camp);
		for (BlockPos origin : around) {
			BlockPos found = searchRing(level, camp, reserved, origin, 8, 18, turn);
			if (found != null) {
				return found;
			}
		}
		return searchRing(level, camp, reserved, centre, Math.max(CLEAR_OF_CENTRE + 2, (int) (radius * 0.55)),
			Math.max(CLEAR_OF_CENTRE + 4, (int) (radius * 0.9)), turn);
	}

	private static @Nullable BlockPos searchRing(ServerLevel level, CampData camp, List<int[]> reserved, BlockPos around, int from,
			int to, int turn) {
		BlockPos centre = camp.campPos().orElseThrow();
		for (int r = from; r <= to; r += 3) {
			for (int k = 0; k < 16; k++) {
				// Diagonals first: the main streets run along the compass lines through the centre.
				double angle = Math.PI / 4 + Math.PI / 2 * ((k + turn) % 4) + (k / 4) * Math.PI / 8;
				BlockPos column = around.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
				BlockPos h = surface(level, column);
				if (h == null) {
					continue;
				}
				Direction toward = SiteFinder.directionTo(h, centre);
				BlockPos next = surface(level, cell(h, toward, 1));
				if (cellOk(level, camp, h, toward, reserved) && next != null && cellOk(level, camp, next, toward, reserved)) {
					return h;
				}
			}
		}
		return null;
	}

	/**
	 * True if a grave fits with its headstone at {@code h}, its sign facing {@code toward}: the headstone, both flower
	 * spots and the sign on level natural ground (not a field or a path) with room above, a place to stand in front,
	 * inside the camp and clear of the square, the streets, every reserved site and anything a player built.
	 */
	static boolean cellOk(ServerLevel level, CampData camp, BlockPos h, Direction toward, List<int[]> reserved) {
		BlockPos centre = camp.campPos().orElse(null);
		if (centre == null || !Places.inCamp(level, h)) {
			return false;
		}
		int dx = h.getX() - centre.getX();
		int dz = h.getZ() - centre.getZ();
		if (dx * dx + dz * dz < CLEAR_OF_CENTRE * CLEAR_OF_CENTRE || nearStreet(dx) || nearStreet(dz)) {
			return false;
		}
		if (camp.chestPos().isPresent() && camp.chestPos().get().distSqr(h) < 25) {
			return false;
		}
		Direction side = toward.getClockWise();
		BlockPos sign = h.relative(toward);
		int minX = Math.min(h.relative(side).getX(), h.relative(side.getOpposite()).getX());
		int maxX = Math.max(h.relative(side).getX(), h.relative(side.getOpposite()).getX());
		int minZ = Math.min(h.relative(side).getZ(), h.relative(side.getOpposite()).getZ());
		int maxZ = Math.max(h.relative(side).getZ(), h.relative(side.getOpposite()).getZ());
		BlockPos front = sign.relative(toward);
		minX = Math.min(minX, Math.min(front.getX(), h.relative(toward.getOpposite()).getX())) - 1;
		maxX = Math.max(maxX, Math.max(front.getX(), h.relative(toward.getOpposite()).getX())) + 1;
		minZ = Math.min(minZ, Math.min(front.getZ(), h.relative(toward.getOpposite()).getZ())) - 1;
		maxZ = Math.max(maxZ, Math.max(front.getZ(), h.relative(toward.getOpposite()).getZ())) + 1;
		for (int[] b : reserved) {
			if (minX <= b[2] && maxX >= b[0] && minZ <= b[3] && maxZ >= b[1]) {
				return false;
			}
		}
		for (BlockPos p : new BlockPos[] {h, h.relative(side), h.relative(side.getOpposite()), sign}) {
			if (!groundOk(level, p)) {
				return false;
			}
		}
		BlockPos stand = Spots.standable(level, front);
		if (stand == null || Math.abs(stand.getY() - h.getY()) > 1) {
			return false;
		}
		return !WorldEditGuard.looksPlayerBuilt(level, h, 3, camp);
	}

	private static boolean nearStreet(int offset) {
		int a = Math.abs(offset);
		return a <= CLEAR_OF_STREETS || Math.abs(a - LANES) <= CLEAR_OF_STREETS;
	}

	/** Natural, firm, level ground under {@code p} (no field, path or water), and room for a block and a head above. */
	private static boolean groundOk(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p)) {
			return false;
		}
		BlockState ground = level.getBlockState(p.below());
		if (!SiteFinder.isNaturalGround(ground) || ground.is(Blocks.FARMLAND) || ground.is(Blocks.DIRT_PATH)
			|| !ground.isFaceSturdy(level, p.below(), Direction.UP) || !ground.getFluidState().isEmpty()) {
			return false;
		}
		for (BlockPos q : new BlockPos[] {p, p.above()}) {
			BlockState s = level.getBlockState(q);
			if (!(s.isAir() || WorldEditGuard.isClearablePlant(s)) || !s.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------- the sign

	/** The block a sign item stands up as, turned so its words face {@code toward}. */
	static BlockState signState(ItemStack sign, Direction toward) {
		Block block = Block.byItem(sign.getItem());
		BlockState state = block instanceof StandingSignBlock ? block.defaultBlockState() : Blocks.OAK_SIGN.defaultBlockState();
		return state.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(toward));
	}

	/**
	 * Writes the grave's words on its sign (the friends' own sign, just placed) and waxes it, so nobody changes them by
	 * accident: "In memory of", the name, the day, "Rest well".
	 */
	static void inscribe(ServerLevel level, BlockPos signPos, LifeData.Grave g) {
		if (!(level.getBlockEntity(signPos) instanceof SignBlockEntity sign)) {
			return;
		}
		String name = g.fullName.length() <= 15 ? g.fullName : g.name;
		List<Component> lines = List.of(Component.literal("In memory of"), Component.literal(name),
			Component.literal(Calendar.label(g.died)), Component.literal("Rest well"));
		sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
		sign.setWaxed(true);
	}
}

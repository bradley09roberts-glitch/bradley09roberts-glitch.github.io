package io.github.bradley09roberts.hardcorefriends.camp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import io.github.bradley09roberts.hardcorefriends.camp.build.MaterialSpec;
import io.github.bradley09roberts.hardcorefriends.camp.build.Part;
import io.github.bradley09roberts.hardcorefriends.camp.build.Placement;

/**
 * The camp's building plans: everything Oak builds and every contraption Spark makes. Plans are small and use
 * ordinary survival materials. Coordinates are local: x to the east, z to the south, the front on the north side.
 */
public final class Blueprints {
	/** A chest just east of the camp centre, linked as the supply chest once built. */
	public static final Blueprint SUPPLY_CHEST = Blueprint.builder(Structures.SUPPLY_CHEST, 1, 1).at(2, 0)
		.put(0, 0, 0, MaterialSpec.CHEST, facing(Direction.NORTH)).build();

	/** A lit campfire just south of the centre. */
	public static final Blueprint CAMPFIRE = Blueprint.builder(Structures.CAMPFIRE, 1, 1).at(0, 3)
		.put(0, 0, 0, MaterialSpec.CAMPFIRE, s -> s.setValue(BlockStateProperties.LIT, true)).build();

	public static final Blueprint CRAFTING_TABLE = Blueprint.builder(Structures.CRAFTING_TABLE, 1, 1)
		.anchor(Blueprint.Anchor.CHEST).at(0, -1).put(0, 0, 0, MaterialSpec.CRAFTING_TABLE).build();

	public static final Blueprint FURNACE = Blueprint.builder(Structures.FURNACE, 1, 1)
		.anchor(Blueprint.Anchor.CHEST).at(0, 1).put(0, 0, 0, MaterialSpec.FURNACE, facing(Direction.NORTH)).build();

	/** Four fence posts with a torch on top, around the centre. */
	public static final Blueprint TORCH_POSTS = Blueprint.builder(Structures.TORCH_POSTS, 1, 1)
		.at(6, 6).at(-6, 6).at(6, -6).at(-6, -6).fixedFacing()
		.put(0, 0, 0, MaterialSpec.FENCE).attach(0, 1, 0, MaterialSpec.TORCH).build();

	public static final Blueprint CABIN = cabin(Structures.CABIN, -10, -1);

	public static final Blueprint CABIN_2 = cabin(Structures.CABIN_2, 0, -11);

	/** A 5×5 plank shed with a door and two chests against the back wall. */
	public static final Blueprint STOREHOUSE = storehouse();

	/** A 3×3 cobblestone tower with a ladder inside and a slab lookout ringed by torches. */
	public static final Blueprint WATCHTOWER = watchtower();

	/** Tall fence posts carrying lanterns near the camp's main features. */
	public static final Blueprint LANTERN_POSTS = Blueprint.builder(Structures.LANTERN_POSTS, 1, 1)
		.at(0, -6).at(6, 0).at(0, 6).at(-5, 0).fixedFacing()
		.put(0, 0, 0, MaterialSpec.FENCE).put(0, 1, 0, MaterialSpec.FENCE)
		.attach(0, 2, 0, MaterialSpec.LANTERN, s -> s.setValue(BlockStateProperties.HANGING, false)).build();

	/**
	 * Wooden pressure plates on the doorstep and just inside the cabin door, so the door opens for anyone walking
	 * through. Uses the cabin's own site and coordinates (door at local 3, 1, 1).
	 */
	public static final Blueprint AUTO_DOOR = Blueprint.builder(Structures.AUTO_DOOR, 7, 8).anchor(Blueprint.Anchor.CABIN)
		.attach(3, 1, 0, MaterialSpec.PRESSURE_PLATE).attach(3, 1, 2, MaterialSpec.PRESSURE_PLATE).build();

	/** A hopper pointing down into the supply chest: drop items on it and they go into the chest. */
	public static final Blueprint HOPPER_DROPOFF = Blueprint.builder(Structures.HOPPER_DROPOFF, 1, 1)
		.anchor(Blueprint.Anchor.CHEST_TOP).put(0, 1, 0, MaterialSpec.HOPPER, hopper(Direction.DOWN)).build();

	/**
	 * A vanilla hopper-fed furnace: input chest on top, a hopper into the furnace's top, a fuel chest and hopper
	 * feeding its side, and a hopper under it emptying into the output chest on the ground.
	 */
	public static final Blueprint AUTO_SMELTER = Blueprint.builder(Structures.AUTO_SMELTER, 2, 1).at(7, 7)
		.put(0, 0, 0, MaterialSpec.CHEST, facing(Direction.NORTH))
		.put(1, 0, 0, MaterialSpec.COBBLESTONE)
		.put(0, 1, 0, MaterialSpec.HOPPER, hopper(Direction.DOWN))
		.put(1, 1, 0, MaterialSpec.COBBLESTONE)
		.put(0, 2, 0, MaterialSpec.FURNACE, facing(Direction.NORTH))
		.put(1, 2, 0, MaterialSpec.HOPPER, hopper(Direction.WEST))
		.put(0, 3, 0, MaterialSpec.HOPPER, hopper(Direction.DOWN))
		.put(1, 3, 0, MaterialSpec.CHEST, facing(Direction.NORTH))
		.put(0, 4, 0, MaterialSpec.CHEST, facing(Direction.NORTH)).build();

	/** Fence posts topped by a redstone lamp and an inverted daylight detector: they light up at night. */
	public static final Blueprint LAMP_POSTS = Blueprint.builder(Structures.LAMP_POSTS, 1, 1)
		.at(4, -9).at(-4, 9).fixedFacing()
		.put(0, 0, 0, MaterialSpec.FENCE).put(0, 1, 0, MaterialSpec.REDSTONE_LAMP)
		.put(0, 2, 0, MaterialSpec.DAYLIGHT_DETECTOR, s -> s.setValue(DaylightDetectorBlock.INVERTED, true)).build();

	/**
	 * A 9×9 ring of wooden fences round a 7×7 paddock, with a fence gate in the middle of the front (the side facing
	 * the camp centre). The gate is built open so nobody is shut in while building; the farmer shuts it once animals
	 * are inside. {@code ai.role.ranch.Pen} reads the paddock's layout from this plan's site.
	 */
	public static final Blueprint ANIMAL_PEN = animalPen();

	/** Local position of the library's enchanting table (declared before the plan, which reads it). */
	public static final int[] LIBRARY_TABLE = {2, 0, 2};

	/**
	 * Sage's library: an enchanting table in the middle of a 5×5 square, ringed by 15 bookshelves on the ground (every
	 * edge block but the doorway in the middle of the front), with the ring of air between table and shelves that the
	 * table needs, and a torch on each corner shelf. Local position of the table: {@link #LIBRARY_TABLE}.
	 */
	public static final Blueprint LIBRARY = library();

	/** An anvil a few blocks from the supply chest. */
	public static final Blueprint ANVIL = Blueprint.builder(Structures.ANVIL, 1, 1).anchor(Blueprint.Anchor.CHEST).at(0, 3)
		.put(0, 0, 0, MaterialSpec.ANVIL).build();

	/** A brewing stand a few blocks from the supply chest, on the other side. */
	public static final Blueprint BREWING_STAND = Blueprint.builder(Structures.BREWING_STAND, 1, 1).anchor(Blueprint.Anchor.CHEST)
		.at(0, -3).put(0, 0, 0, MaterialSpec.BREWING_STAND).build();

	/** Local positions of the smelter's chests, for players and tests. */
	public static final int[] SMELTER_INPUT = {0, 4, 0};
	public static final int[] SMELTER_FUEL = {1, 3, 0};
	public static final int[] SMELTER_OUTPUT = {0, 0, 0};
	/** Local position of the cabin's door (lower half). */
	public static final int[] CABIN_DOOR = {3, 1, 1};

	private static final Map<String, Blueprint> BY_ID = new LinkedHashMap<>();

	static {
		for (Blueprint b : List.of(SUPPLY_CHEST, CAMPFIRE, CRAFTING_TABLE, FURNACE, TORCH_POSTS, CABIN, STOREHOUSE, WATCHTOWER,
			LANTERN_POSTS, CABIN_2, AUTO_DOOR, HOPPER_DROPOFF, AUTO_SMELTER, LAMP_POSTS, ANIMAL_PEN, LIBRARY, ANVIL, BREWING_STAND)) {
			BY_ID.put(b.id(), b);
		}
	}

	private Blueprints() {
	}

	/** The plan for a structure id, if the builders or the inventor make it. */
	public static Optional<Blueprint> forId(String structureId) {
		return Optional.ofNullable(BY_ID.get(structureId));
	}

	/** World position of a local block on a single-part site. */
	public static BlockPos at(CampData.Site site, int[] local) {
		return Blueprint.worldPos(site.origin, site.rotation, local[0], local[1], local[2]);
	}

	/**
	 * Every entry of a plan resolved onto its parts, in build order: foundations first (one per footprint column,
	 * counted as done where the ground is already solid), then each layer across all parts, then attachments.
	 */
	public static List<Placement> placements(Blueprint bp, List<Part> parts) {
		List<Placement> list = new ArrayList<>();
		if (bp.hasFoundations()) {
			Blueprint.Entry fill = new Blueprint.Entry(0, -1, 0, MaterialSpec.FOUNDATION, UnaryOperator.identity(), false);
			for (int p = 0; p < parts.size(); p++) {
				Part part = parts.get(p);
				for (int dx = 0; dx < bp.width(); dx++) {
					for (int dz = 0; dz < bp.depth(); dz++) {
						BlockPos pos = Blueprint.worldPos(part.origin(), part.rotation(), dx, -1, dz);
						list.add(new Placement(list.size(), p, pos, fill, part.rotation()));
					}
				}
			}
		}
		List<Blueprint.Entry> entries = bp.entries();
		int i = 0;
		while (i < entries.size()) {
			int j = i;
			Blueprint.Entry first = entries.get(i);
			while (j < entries.size() && entries.get(j).attachment() == first.attachment() && entries.get(j).dy() == first.dy()) {
				j++;
			}
			for (int p = 0; p < parts.size(); p++) {
				Part part = parts.get(p);
				for (int k = i; k < j; k++) {
					Blueprint.Entry e = entries.get(k);
					BlockPos pos = Blueprint.worldPos(part.origin(), part.rotation(), e.dx(), e.dy(), e.dz());
					list.add(new Placement(list.size(), p, pos, e, part.rotation()));
				}
			}
			i = j;
		}
		return list;
	}

	// ---------------------------------------------------------------- plans

	/**
	 * A 7×7 cabin with a doorstep in front: plank floor on the ground, log corners, plank walls three high with a
	 * door and two glass-pane windows, a flat wooden-slab roof and a wall torch inside. Friends cannot make glass, so
	 * when the camp has none the builder closes the windows with planks instead (see {@code BuildJob}).
	 */
	private static Blueprint cabin(String id, int x, int z) {
		Blueprint.Builder b = Blueprint.builder(id, 7, 8).at(x, z);
		b.put(3, 0, 0, MaterialSpec.PLANKS); // doorstep
		for (int dx = 0; dx < 7; dx++) {
			for (int dz = 1; dz <= 7; dz++) {
				boolean corner = (dx == 0 || dx == 6) && (dz == 1 || dz == 7);
				b.put(dx, 0, dz, corner ? MaterialSpec.LOG : MaterialSpec.PLANKS);
			}
		}
		for (int dy = 1; dy <= 3; dy++) {
			for (int dx = 0; dx < 7; dx++) {
				for (int dz = 1; dz <= 7; dz++) {
					boolean edgeX = dx == 0 || dx == 6;
					boolean edgeZ = dz == 1 || dz == 7;
					if (!edgeX && !edgeZ) {
						continue;
					}
					if (dx == 3 && dz == 1 && dy <= 2) {
						continue; // doorway
					}
					if (dy == 2 && dz == 4 && edgeX) {
						b.put(dx, dy, dz, MaterialSpec.GLASS_PANE);
						continue;
					}
					b.put(dx, dy, dz, edgeX && edgeZ ? MaterialSpec.LOG : MaterialSpec.PLANKS);
				}
			}
		}
		for (int dx = 0; dx < 7; dx++) {
			for (int dz = 1; dz <= 7; dz++) {
				b.put(dx, 4, dz, MaterialSpec.SLAB, Blueprints::bottomSlab);
			}
		}
		b.attach(3, 1, 1, MaterialSpec.DOOR, facing(Direction.SOUTH));
		b.attach(3, 2, 1, MaterialSpec.DOOR_TOP);
		b.attach(3, 2, 6, MaterialSpec.WALL_TORCH, facing(Direction.NORTH));
		return b.build();
	}

	private static Blueprint storehouse() {
		Blueprint.Builder b = Blueprint.builder(Structures.STOREHOUSE, 5, 6).at(9, -8);
		b.put(2, 0, 0, MaterialSpec.PLANKS); // doorstep
		for (int dy = 0; dy <= 2; dy++) {
			for (int dx = 0; dx < 5; dx++) {
				for (int dz = 1; dz <= 5; dz++) {
					boolean edgeX = dx == 0 || dx == 4;
					boolean edgeZ = dz == 1 || dz == 5;
					if (dy > 0 && !edgeX && !edgeZ) {
						continue;
					}
					if (dy > 0 && dx == 2 && dz == 1) {
						continue; // doorway
					}
					b.put(dx, dy, dz, edgeX && edgeZ ? MaterialSpec.LOG : MaterialSpec.PLANKS);
				}
			}
		}
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 1; dz <= 5; dz++) {
				b.put(dx, 3, dz, MaterialSpec.SLAB, Blueprints::bottomSlab);
			}
		}
		b.attach(2, 1, 1, MaterialSpec.DOOR, facing(Direction.SOUTH));
		b.attach(2, 2, 1, MaterialSpec.DOOR_TOP);
		b.attach(1, 1, 4, MaterialSpec.CHEST, facing(Direction.NORTH));
		b.attach(3, 1, 4, MaterialSpec.CHEST, facing(Direction.NORTH));
		b.attach(2, 2, 4, MaterialSpec.WALL_TORCH, facing(Direction.NORTH));
		return b.build();
	}

	private static Blueprint watchtower() {
		Blueprint.Builder b = Blueprint.builder(Structures.WATCHTOWER, 3, 3).at(-8, 9);
		for (int dy = 0; dy <= 3; dy++) {
			for (int dx = 0; dx < 3; dx++) {
				for (int dz = 0; dz < 3; dz++) {
					if (dx == 1 && dz == 1 || dx == 1 && dz == 0 && dy <= 1) {
						continue; // shaft and doorway
					}
					b.put(dx, dy, dz, MaterialSpec.COBBLESTONE);
				}
			}
		}
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				boolean corner = dx != 1 && dz != 1;
				if (corner) {
					b.put(dx, 4, dz, MaterialSpec.COBBLESTONE);
				} else if (!(dx == 1 && dz == 1)) {
					b.put(dx, 4, dz, MaterialSpec.SLAB, Blueprints::bottomSlab);
				}
			}
		}
		for (int dy = 0; dy <= 3; dy++) {
			b.attach(1, dy, 1, MaterialSpec.LADDER, facing(Direction.NORTH));
		}
		b.attach(0, 5, 0, MaterialSpec.TORCH).attach(2, 5, 0, MaterialSpec.TORCH)
			.attach(0, 5, 2, MaterialSpec.TORCH).attach(2, 5, 2, MaterialSpec.TORCH);
		return b.build();
	}

	/** Local position of the animal pen's gate: the middle of the front row. */
	public static final int[] PEN_GATE = {4, 0, 0};

	private static Blueprint animalPen() {
		Blueprint.Builder b = Blueprint.builder(Structures.ANIMAL_PEN, 9, 9).at(11, 11);
		for (int dx = 0; dx < 9; dx++) {
			for (int dz = 0; dz < 9; dz++) {
				if (dx != 0 && dx != 8 && dz != 0 && dz != 8) {
					continue; // the paddock itself stays open ground
				}
				if (dx == 4 && dz == 0) {
					b.put(dx, 0, dz, MaterialSpec.FENCE_GATE, Blueprints::openGate);
				} else {
					b.put(dx, 0, dz, MaterialSpec.FENCE);
				}
			}
		}
		return b.build();
	}

	private static Blueprint library() {
		Blueprint.Builder b = Blueprint.builder(Structures.LIBRARY, 5, 5).at(12, -1);
		for (int dx = 0; dx < 5; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				boolean edge = dx == 0 || dx == 4 || dz == 0 || dz == 4;
				if (edge && !(dx == 2 && dz == 0)) {
					b.put(dx, 0, dz, MaterialSpec.BOOKSHELF);
				}
			}
		}
		b.put(LIBRARY_TABLE[0], LIBRARY_TABLE[1], LIBRARY_TABLE[2], MaterialSpec.ENCHANTING_TABLE);
		b.attach(0, 1, 0, MaterialSpec.TORCH).attach(4, 1, 0, MaterialSpec.TORCH)
			.attach(0, 1, 4, MaterialSpec.TORCH).attach(4, 1, 4, MaterialSpec.TORCH);
		return b.build();
	}

	// --------------------------------------------------------------- tweaks

	/** A gate across the front row (east to west in the plan), standing open. */
	private static BlockState openGate(BlockState s) {
		return s.hasProperty(FenceGateBlock.OPEN)
			? s.setValue(FenceGateBlock.FACING, Direction.NORTH).setValue(FenceGateBlock.OPEN, true) : s;
	}

	private static UnaryOperator<BlockState> facing(Direction direction) {
		return s -> s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, direction) : s;
	}

	private static UnaryOperator<BlockState> hopper(Direction direction) {
		return s -> s.hasProperty(HopperBlock.FACING) ? s.setValue(HopperBlock.FACING, direction) : s;
	}

	private static BlockState bottomSlab(BlockState s) {
		return s.hasProperty(SlabBlock.TYPE) ? s.setValue(SlabBlock.TYPE, SlabType.BOTTOM) : s;
	}
}

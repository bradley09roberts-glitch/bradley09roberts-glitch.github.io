package io.github.bradley09roberts.hardcorefriends.settler;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.FluidState;

import io.github.bradley09roberts.hardcorefriends.companion.CompanionEntity;
import io.github.bradley09roberts.hardcorefriends.companion.Persona;
import io.github.bradley09roberts.hardcorefriends.config.FriendsConfig;
import io.github.bradley09roberts.hardcorefriends.registry.ModEntities;

/**
 * The one piece of a {@link SurvivorCamp}: the ground levelled to the camp's floor height, one or two wool tents
 * opening onto a campfire with log benches, a crafting table, a chest of supplies and the strangers who live there.
 *
 * <p>Layout, before turning (x across, z down; the camp is {@value SurvivorCamp#SIZE} blocks square): tents at x 1-5
 * and 7-11, z 1-4, opening south; the campfire at (6, 8) with benches either side and one to the south; the crafting
 * table at (2, 10) and the chest at (10, 10). The corners are left as they were, so the camp is a rough octagon rather
 * than a square pad. The whole camp sits inside one chunk, so it is always built whole.
 *
 * <p>The ground is levelled gently: gaps under the floor are filled with dirt (at most {@value #FOUNDATION} deep, so
 * a cave below is left alone) and the floor's {@value #HEADROOM} blocks of air are cleared of plants, snow and the odd
 * bump. The structure only chooses dry ground that varies by at most three blocks, so this never makes much of a mark.
 */
public class SurvivorCampPiece extends StructurePiece {
	/** How deep gaps under the floor are filled. */
	static final int FOUNDATION = 4;
	/** How high above the floor the camp is cleared. */
	static final int HEADROOM = 4;
	private static final int LAST = SurvivorCamp.SIZE - 1;
	/** A puddle or two is filled in; any more water than this and the camp is not built. */
	private static final int MAX_WATER = 6;
	private static final DyeColor[] CANVAS = {DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.BROWN, DyeColor.GREEN, DyeColor.GRAY};
	/** Where each stranger stands by day (local x, z), and which tent they sleep in. */
	private static final int[][] SPOTS = {{4, 6}, {8, 6}, {6, 11}};
	/** Inside each tent, where its sleeper stands at night. */
	private static final int[][] TENT_INSIDE = {{3, 3}, {9, 3}};

	private final BlockPos origin;
	private final int rotation;
	private final int tents;
	private final int strangers;
	/** Which strangers have been added already (one bit each), so a re-run never adds them twice. */
	private int spawned;

	public SurvivorCampPiece(BlockPos origin, int rotation, int tents, int strangers) {
		super(SurvivorCamp.PIECE, 0, new BoundingBox(origin.getX(), origin.getY() - FOUNDATION, origin.getZ(),
			origin.getX() + LAST, origin.getY() + HEADROOM, origin.getZ() + LAST));
		this.origin = origin.immutable();
		this.rotation = rotation & 3;
		this.tents = Math.clamp(tents, 1, TENT_INSIDE.length);
		this.strangers = Math.clamp(strangers, 1, SPOTS.length);
		setOrientation(null);
	}

	public SurvivorCampPiece(CompoundTag tag) {
		super(SurvivorCamp.PIECE, tag);
		this.origin = BlockPos.of(tag.getLongOr("Origin", 0L));
		this.rotation = tag.getIntOr("Rot", 0) & 3;
		this.tents = Math.clamp(tag.getIntOr("Tents", 1), 1, TENT_INSIDE.length);
		this.strangers = Math.clamp(tag.getIntOr("Strangers", 1), 1, SPOTS.length);
		this.spawned = tag.getIntOr("Spawned", 0);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putLong("Origin", origin.asLong());
		tag.putInt("Rot", rotation);
		tag.putInt("Tents", tents);
		tag.putInt("Strangers", strangers);
		tag.putInt("Spawned", spawned);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		if (!chunkBB.isInside(at(LAST / 2, 0, LAST / 2)) || wet(level, chunkBB)) {
			return; // a pond or a lava pool where the camp would go: no camp here at all, rather than a flood
		}
		levelGround(level, chunkBB, random);
		for (int t = 0; t < tents; t++) {
			DyeColor colour = CANVAS[random.nextInt(CANVAS.length)];
			tent(level, chunkBB, t == 0 ? 1 : 7, colour);
		}
		put(level, chunkBB, 6, 0, 8, Blocks.CAMPFIRE.defaultBlockState());
		BlockState benchZ = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
		BlockState benchX = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		put(level, chunkBB, 4, 0, 8, benchZ);
		put(level, chunkBB, 8, 0, 8, benchZ);
		put(level, chunkBB, 6, 0, 10, benchX);
		put(level, chunkBB, 2, 0, 10, Blocks.CRAFTING_TABLE.defaultBlockState());
		BlockPos chest = at(10, 0, 10);
		createChest(level, chunkBB, random, chest, SurvivorCamp.CHEST_LOOT,
			Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST).rotate(rotation()));
		if (FriendsConfig.get().allowSettlers) {
			for (int i = 0; i < strangers; i++) {
				addStranger(level, chunkBB, random, i);
			}
		}
	}

	/** Fills under the floor and clears above it, over the camp's octagon. */
	private void levelGround(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int lx = 0; lx <= LAST; lx++) {
			for (int lz = 0; lz <= LAST; lz++) {
				int dx = Math.abs(lx - LAST / 2);
				int dz = Math.abs(lz - LAST / 2);
				if (dx + dz > 10) {
					continue; // the corners stay as they were
				}
				for (int depth = 1; depth <= FOUNDATION; depth++) {
					BlockPos p = at(lx, -depth, lz);
					if (!chunkBB.isInside(p) || isGround(level.getBlockState(p))) {
						break;
					}
					level.setBlock(p, depth == 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
				}
				for (int y = 0; y < HEADROOM; y++) {
					BlockPos p = at(lx, y, lz);
					if (chunkBB.isInside(p) && !level.getBlockState(p).isAir()) {
						level.setBlock(p, air, 2);
					}
				}
				// A worn floor round the fire: nothing grows on a path, so no tree springs up in the middle of the camp.
				boolean hearth = Math.abs(lx - 6) <= 3 && lz >= 5 && lz <= 11;
				boolean inTent = lz >= 2 && lz <= 4 && (lx >= 2 && lx <= 4 || lx >= 8 && lx <= 10);
				// The strangers' own spots are always path, so no tree, bush or pumpkin grows where they stand.
				if (isSpot(lx, lz) || inTent || (hearth && random.nextInt(8) != 0)) {
					BlockPos floor = at(lx, -1, lz);
					BlockState s = chunkBB.isInside(floor) ? level.getBlockState(floor) : air;
					if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
						|| isSpot(lx, lz) && s.is(BlockTags.DIRT)) {
						level.setBlock(floor, Blocks.DIRT_PATH.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	/** True for a cell where a stranger stands by day. */
	private static boolean isSpot(int lx, int lz) {
		for (int[] spot : SPOTS) {
			if (spot[0] == lx && spot[1] == lz) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when the camp's ground has lava on or just under it, or more than a little water: lakes are carved after the
	 * camp's spot is chosen from the bare terrain, so they are only seen here. The whole camp lies in this chunk, so
	 * the answer is the same for all of it.
	 */
	private boolean wet(WorldGenLevel level, BoundingBox chunkBB) {
		int water = 0;
		for (int lx = 0; lx <= LAST; lx++) {
			for (int lz = 0; lz <= LAST; lz++) {
				for (int y = -2; y <= 1; y++) {
					BlockPos p = at(lx, y, lz);
					if (!chunkBB.isInside(p)) {
						continue;
					}
					FluidState fluid = level.getFluidState(p);
					if (fluid.is(FluidTags.LAVA)) {
						return true;
					}
					if (fluid.is(FluidTags.WATER) && ++water > MAX_WATER) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Solid natural ground to build on: not air, water, plants, snow or leaves. */
	private static boolean isGround(BlockState s) {
		return !s.isAir() && !s.liquid() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES);
	}

	/**
	 * An A-frame tent five wide and four deep, its back wall at local z 1 and its open end at z 4: wool stairs for the
	 * slopes, a wool ridge, and a bedroll inside.
	 */
	private void tent(WorldGenLevel level, BoundingBox chunkBB, int x0, DyeColor colour) {
		BlockState wool = Blocks.WOOL.pick(colour).defaultBlockState();
		BlockState stairs = Blocks.WOOL_STAIRS.pick(colour).defaultBlockState();
		BlockState rising = stairs.setValue(StairBlock.FACING, Direction.EAST);
		BlockState falling = stairs.setValue(StairBlock.FACING, Direction.WEST);
		for (int z = 1; z <= 4; z++) {
			put(level, chunkBB, x0, 0, z, rising);
			put(level, chunkBB, x0 + 1, 1, z, rising);
			put(level, chunkBB, x0 + 2, 2, z, wool);
			put(level, chunkBB, x0 + 3, 1, z, falling);
			put(level, chunkBB, x0 + 4, 0, z, falling);
		}
		for (int x = x0 + 1; x <= x0 + 3; x++) {
			put(level, chunkBB, x, 0, 1, wool);
		}
		put(level, chunkBB, x0 + 2, 1, 1, wool);
		put(level, chunkBB, x0 + 2, 0, 2, Blocks.CARPET.pick(DyeColor.BROWN).defaultBlockState());
	}

	private void addStranger(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, int i) {
		int bit = 1 << i;
		BlockPos spot = at(SPOTS[i][0], 0, SPOTS[i][1]);
		if ((spawned & bit) != 0 || !chunkBB.isInside(spot)) {
			return;
		}
		spawned |= bit;
		CompanionEntity c = ModEntities.COMPANION.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (c == null) {
			return;
		}
		int[] inside = TENT_INSIDE[i % tents];
		// Names are checked against the world's living people on the stranger's first tick, on the server thread.
		Persona persona = Personas.create(random, Set.of());
		Strangers.setUp(c, persona, spot, Personas.Origin.CAMP, Personas.story(random, Personas.Origin.CAMP),
			at(inside[0], 0, inside[1]));
		c.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		level.addFreshEntityWithPassengers(c);
	}

	private Rotation rotation() {
		return Rotation.values()[rotation];
	}

	/** The world position of a spot in the camp's own layout, turned the camp's way. */
	private BlockPos at(int lx, int ly, int lz) {
		int x;
		int z;
		switch (rotation) {
			case 1 -> {
				x = LAST - lz;
				z = lx;
			}
			case 2 -> {
				x = LAST - lx;
				z = LAST - lz;
			}
			case 3 -> {
				x = lz;
				z = LAST - lx;
			}
			default -> {
				x = lx;
				z = lz;
			}
		}
		return new BlockPos(origin.getX() + x, origin.getY() + ly, origin.getZ() + z);
	}

	/** Places a block of the layout (turned with the camp) if it lies in the chunk being built. */
	private void put(WorldGenLevel level, BoundingBox chunkBB, int lx, int ly, int lz, BlockState state) {
		BlockPos pos = at(lx, ly, lz);
		if (chunkBB.isInside(pos)) {
			level.setBlock(pos, state.rotate(rotation()), 2);
		}
	}
}

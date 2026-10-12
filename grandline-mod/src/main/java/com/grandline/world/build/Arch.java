package com.grandline.world.build;

import com.grandline.registry.ArchBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/**
 * Architectural vocabulary shared by the settlement buildings and the landmarks: per-style material sets
 * ({@link Mats}), real roofs (gable, low-pitch slab, hip, flared, flat with parapet and cornice, lean-to) with
 * overhangs, eave brackets and ridge caps, framed windows with shutters, sills and planters, posts, beams, chimneys
 * with smoke, staircases and furniture. Everything is written through a {@link Builder} in its local frame, so it
 * rotates with the building; connected blocks (panes, fences, bars) get their connections set explicitly because
 * world generation runs no neighbour updates.
 */
public final class Arch {
    private Arch() {}

    // ================================================================== materials

    /** Roof shape a style prefers. */
    public enum RoofKind { GABLE, LOW_GABLE, STEEP, HIP, FLARED, FLAT }

    /** The blocks one building is made of (a style plus per-building variation). */
    public static final class Mats {
        public BlockState wall, upperWall, frame, base, floor, roof, roofStairs, roofSlab, window, door, fence, lantern, trim, chimney, sill;
        public Block shutter, awning;
        public boolean planters, eaveBrackets, stilts;
        public RoofKind roofKind = RoofKind.GABLE;
        public int pitchSteps = 1;
        public BuildStyle style;

        Mats() {}

        public static Mats of(BuildStyle st, long seed) {
            Mats m = new Mats();
            m.style = st;
            int v = (int) Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 33, 1024);
            m.lantern = ArchBlocks.BRASS_LANTERN.get().defaultBlockState();
            m.fence = Blocks.SPRUCE_FENCE.defaultBlockState();
            m.window = ArchBlocks.MULLION_WINDOW.get().defaultBlockState();
            m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
            m.chimney = Blocks.BRICKS.defaultBlockState();
            switch (st) {
                case VILLAGE -> {
                    m.wall = pick(v, ArchBlocks.WHITE_PLASTER, ArchBlocks.CREAM_PLASTER, ArchBlocks.WHITE_PLASTER, ArchBlocks.OCHRE_PLASTER);
                    m.upperWall = (v & 4) != 0 ? ArchBlocks.TIMBER_FRAME_DIAGONAL.get().defaultBlockState() : m.wall;
                    m.frame = log(ArchBlocks.OAK_BEAM.get());
                    m.base = ArchBlocks.FIELDSTONE.get().defaultBlockState();
                    m.floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    roof(m, v >> 3, new Block[][]{{ArchBlocks.TERRACOTTA_ROOF_TILES.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB.get()},
                            {ArchBlocks.TERRACOTTA_ROOF_TILES.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB.get()},
                            {ArchBlocks.TEAL_ROOF_TILES.get(), ArchBlocks.TEAL_ROOF_TILE_STAIRS.get(), ArchBlocks.TEAL_ROOF_TILE_SLAB.get()},
                            {ArchBlocks.SLATE_ROOF_TILES.get(), ArchBlocks.SLATE_ROOF_TILE_STAIRS.get(), ArchBlocks.SLATE_ROOF_TILE_SLAB.get()}});
                    m.shutter = pickB(v >> 5, ArchBlocks.GREEN_SHUTTER.get(), ArchBlocks.BLUE_SHUTTER.get(), ArchBlocks.RED_SHUTTER.get(), ArchBlocks.GREEN_SHUTTER.get());
                    m.awning = pickB(v >> 7, ArchBlocks.RED_AWNING.get(), ArchBlocks.BLUE_AWNING.get(), ArchBlocks.GREEN_AWNING.get(), ArchBlocks.YELLOW_AWNING.get());
                    m.door = Blocks.OAK_DOOR.defaultBlockState();
                    m.trim = ArchBlocks.OAK_BEAM.get().defaultBlockState();
                    m.planters = true;
                    m.eaveBrackets = true;
                    m.chimney = ArchBlocks.FIELDSTONE.get().defaultBlockState();
                }
                case STONE_TOWN -> {
                    m.wall = ArchBlocks.FIELDSTONE.get().defaultBlockState();
                    m.upperWall = pick(v, ArchBlocks.TIMBER_FRAME, ArchBlocks.TIMBER_FRAME_CROSS, ArchBlocks.TIMBER_FRAME_STUDS, ArchBlocks.TIMBER_FRAME_DIAGONAL);
                    m.frame = log(ArchBlocks.DARK_BEAM.get());
                    m.base = Blocks.STONE_BRICKS.defaultBlockState();
                    m.floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.SLATE_ROOF_TILES.get(), ArchBlocks.SLATE_ROOF_TILE_STAIRS.get(), ArchBlocks.SLATE_ROOF_TILE_SLAB.get()}});
                    m.shutter = pickB(v >> 4, ArchBlocks.OAK_SHUTTER.get(), ArchBlocks.RED_SHUTTER.get(), ArchBlocks.OAK_SHUTTER.get(), ArchBlocks.BLUE_SHUTTER.get());
                    m.awning = pickB(v >> 6, ArchBlocks.RED_AWNING.get(), ArchBlocks.GREEN_AWNING.get(), ArchBlocks.BLUE_AWNING.get(), ArchBlocks.RED_AWNING.get());
                    m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
                    m.trim = ArchBlocks.DARK_BEAM.get().defaultBlockState();
                    m.eaveBrackets = true;
                }
                case DESERT -> {
                    m.wall = pick(v, ArchBlocks.CREAM_PLASTER, ArchBlocks.OCHRE_PLASTER, ArchBlocks.CREAM_PLASTER, ArchBlocks.WHITE_PLASTER);
                    m.upperWall = m.wall;
                    m.frame = ArchBlocks.DRESSED_SANDSTONE.get().defaultBlockState();
                    m.base = ArchBlocks.DRESSED_SANDSTONE.get().defaultBlockState();
                    m.floor = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    m.roof = ArchBlocks.DRESSED_SANDSTONE.get().defaultBlockState();
                    m.roofStairs = ArchBlocks.DRESSED_SANDSTONE_STAIRS.get().defaultBlockState();
                    m.roofSlab = ArchBlocks.DRESSED_SANDSTONE_SLAB.get().defaultBlockState();
                    m.roofKind = RoofKind.FLAT;
                    m.window = ArchBlocks.LATTICE_WINDOW.get().defaultBlockState();
                    m.awning = pickB(v >> 4, ArchBlocks.YELLOW_AWNING.get(), ArchBlocks.RED_AWNING.get(), ArchBlocks.BLUE_AWNING.get(), ArchBlocks.GREEN_AWNING.get());
                    m.door = Blocks.ACACIA_DOOR.defaultBlockState();
                    m.fence = Blocks.ACACIA_FENCE.defaultBlockState();
                    m.trim = ArchBlocks.DRESSED_SANDSTONE.get().defaultBlockState();
                    m.chimney = ArchBlocks.DRESSED_SANDSTONE.get().defaultBlockState();
                }
                case SNOW -> {
                    m.wall = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.upperWall = pick(v, ArchBlocks.TIMBER_FRAME_CROSS, ArchBlocks.TIMBER_FRAME, ArchBlocks.TIMBER_FRAME_STUDS, ArchBlocks.TIMBER_FRAME_CROSS);
                    m.frame = log(ArchBlocks.DARK_BEAM.get());
                    m.base = ArchBlocks.FIELDSTONE.get().defaultBlockState();
                    m.floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.SLATE_ROOF_TILES.get(), ArchBlocks.SLATE_ROOF_TILE_STAIRS.get(), ArchBlocks.SLATE_ROOF_TILE_SLAB.get()}});
                    m.shutter = pickB(v >> 4, ArchBlocks.RED_SHUTTER.get(), ArchBlocks.BLUE_SHUTTER.get(), ArchBlocks.OAK_SHUTTER.get(), ArchBlocks.RED_SHUTTER.get());
                    m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
                    m.trim = ArchBlocks.DARK_BEAM.get().defaultBlockState();
                    m.eaveBrackets = true;
                    m.roofKind = RoofKind.STEEP;
                    m.chimney = ArchBlocks.FIELDSTONE.get().defaultBlockState();
                }
                case JUNGLE -> {
                    m.stilts = true;
                    m.wall = Blocks.BAMBOO_PLANKS.defaultBlockState();
                    m.upperWall = m.wall;
                    m.frame = log(Blocks.JUNGLE_LOG);
                    m.base = log(Blocks.JUNGLE_LOG);
                    m.floor = ArchBlocks.DOCK_PLANKS.get().defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.THATCH.get(), ArchBlocks.THATCH_STAIRS.get(), ArchBlocks.THATCH_SLAB.get()}});
                    m.window = ArchBlocks.LATTICE_WINDOW.get().defaultBlockState();
                    m.door = Blocks.BAMBOO_DOOR.defaultBlockState();
                    m.fence = Blocks.BAMBOO_FENCE.defaultBlockState();
                    m.trim = log(Blocks.JUNGLE_LOG);
                    m.lantern = ArchBlocks.WHITE_PAPER_LANTERN.get().defaultBlockState();
                    m.chimney = Blocks.MUD_BRICKS.defaultBlockState();
                }
                case WANO -> {
                    m.wall = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.upperWall = m.wall;
                    m.frame = log(ArchBlocks.DARK_BEAM.get());
                    m.base = Blocks.STONE_BRICKS.defaultBlockState();
                    m.floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.KAWARA_ROOF_TILES.get(), ArchBlocks.KAWARA_ROOF_TILE_STAIRS.get(), ArchBlocks.KAWARA_ROOF_TILE_SLAB.get()}});
                    m.roofKind = RoofKind.FLARED;
                    m.window = ArchBlocks.LATTICE_WINDOW.get().defaultBlockState();
                    m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
                    m.fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
                    m.trim = log(ArchBlocks.DARK_BEAM.get());
                    m.lantern = ArchBlocks.RED_PAPER_LANTERN.get().defaultBlockState();
                    m.chimney = Blocks.STONE_BRICKS.defaultBlockState();
                }
                case VENETIAN -> {
                    m.wall = pick(v, ArchBlocks.SALMON_PLASTER, ArchBlocks.OCHRE_PLASTER, ArchBlocks.SKY_PLASTER, ArchBlocks.CREAM_PLASTER);
                    m.upperWall = m.wall;
                    m.frame = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.base = ArchBlocks.HARBOUR_STONE.get().defaultBlockState();
                    m.floor = Blocks.STRIPPED_OAK_WOOD.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.TERRACOTTA_ROOF_TILES.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB.get()}});
                    m.roofKind = RoofKind.LOW_GABLE;
                    m.shutter = pickB(v >> 4, ArchBlocks.GREEN_SHUTTER.get(), ArchBlocks.BLUE_SHUTTER.get(), ArchBlocks.GREEN_SHUTTER.get(), ArchBlocks.OAK_SHUTTER.get());
                    m.awning = pickB(v >> 6, ArchBlocks.BLUE_AWNING.get(), ArchBlocks.RED_AWNING.get(), ArchBlocks.GREEN_AWNING.get(), ArchBlocks.YELLOW_AWNING.get());
                    m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
                    m.fence = Blocks.IRON_BARS.defaultBlockState();
                    m.trim = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.planters = true;
                }
                case CANDY -> {
                    m.wall = pick(v, ArchBlocks.SALMON_PLASTER, ArchBlocks.MINT_PLASTER, ArchBlocks.SKY_PLASTER, ArchBlocks.SALMON_PLASTER);
                    m.upperWall = m.wall;
                    m.frame = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.base = Blocks.PINK_TERRACOTTA.defaultBlockState();
                    m.floor = Blocks.CHERRY_PLANKS.defaultBlockState();
                    roof(m, v >> 2, new Block[][]{{ArchBlocks.SKY_ROOF_TILES.get(), ArchBlocks.SKY_ROOF_TILE_STAIRS.get(), ArchBlocks.SKY_ROOF_TILE_SLAB.get()},
                            {ArchBlocks.TEAL_ROOF_TILES.get(), ArchBlocks.TEAL_ROOF_TILE_STAIRS.get(), ArchBlocks.TEAL_ROOF_TILE_SLAB.get()}});
                    m.window = Blocks.PINK_STAINED_GLASS_PANE.defaultBlockState();
                    m.shutter = ArchBlocks.RED_SHUTTER.get();
                    m.awning = pickB(v >> 4, ArchBlocks.RED_AWNING.get(), ArchBlocks.YELLOW_AWNING.get(), ArchBlocks.BLUE_AWNING.get(), ArchBlocks.GREEN_AWNING.get());
                    m.door = Blocks.CHERRY_DOOR.defaultBlockState();
                    m.fence = Blocks.CHERRY_FENCE.defaultBlockState();
                    m.trim = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.planters = true;
                }
                case MARINE -> {
                    m.wall = ArchBlocks.PALE_MASONRY.get().defaultBlockState();
                    m.upperWall = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.frame = ArchBlocks.PALE_MASONRY.get().defaultBlockState();
                    m.base = ArchBlocks.HARBOUR_STONE.get().defaultBlockState();
                    m.floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.SLATE_ROOF_TILES.get(), ArchBlocks.SLATE_ROOF_TILE_STAIRS.get(), ArchBlocks.SLATE_ROOF_TILE_SLAB.get()}});
                    m.roofKind = RoofKind.HIP;
                    m.shutter = ArchBlocks.BLUE_SHUTTER.get();
                    m.awning = ArchBlocks.BLUE_AWNING.get();
                    m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
                    m.fence = Blocks.IRON_BARS.defaultBlockState();
                    m.trim = Blocks.BLUE_CONCRETE.defaultBlockState();
                    m.chimney = ArchBlocks.PALE_MASONRY.get().defaultBlockState();
                }
                case GOTHIC -> {
                    m.wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                    m.upperWall = ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState();
                    m.frame = log(ArchBlocks.DARK_BEAM.get());
                    m.base = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
                    m.floor = Blocks.DARK_OAK_PLANKS.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.SLATE_ROOF_TILES.get(), ArchBlocks.SLATE_ROOF_TILE_STAIRS.get(), ArchBlocks.SLATE_ROOF_TILE_SLAB.get()}});
                    m.window = Blocks.PURPLE_STAINED_GLASS_PANE.defaultBlockState();
                    m.shutter = ArchBlocks.OAK_SHUTTER.get();
                    m.door = Blocks.DARK_OAK_DOOR.defaultBlockState();
                    m.fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
                    m.trim = log(ArchBlocks.DARK_BEAM.get());
                    m.lantern = Blocks.SOUL_LANTERN.defaultBlockState();
                    m.roofKind = RoofKind.STEEP;
                    m.chimney = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                }
                case FUTURE -> {
                    m.wall = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.upperWall = ArchBlocks.SKY_PLASTER.get().defaultBlockState();
                    m.frame = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                    m.base = Blocks.SMOOTH_STONE.defaultBlockState();
                    m.floor = Blocks.SMOOTH_QUARTZ.defaultBlockState();
                    m.roof = Blocks.SMOOTH_QUARTZ.defaultBlockState();
                    m.roofStairs = Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState();
                    m.roofSlab = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
                    m.roofKind = RoofKind.FLAT;
                    m.window = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
                    m.door = Blocks.IRON_DOOR.defaultBlockState();
                    m.fence = Blocks.IRON_BARS.defaultBlockState();
                    m.trim = Blocks.CYAN_CONCRETE.defaultBlockState();
                    m.lantern = Blocks.SEA_LANTERN.defaultBlockState();
                    m.chimney = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                }
                case GIANT -> {
                    m.wall = ArchBlocks.WEATHERED_PLANKS.get().defaultBlockState();
                    m.upperWall = m.wall;
                    m.frame = log(Blocks.SPRUCE_LOG);
                    m.base = ArchBlocks.FIELDSTONE.get().defaultBlockState();
                    m.floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.THATCH.get(), ArchBlocks.THATCH_STAIRS.get(), ArchBlocks.THATCH_SLAB.get()}});
                    m.window = ArchBlocks.LATTICE_WINDOW.get().defaultBlockState();
                    m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
                    m.trim = log(Blocks.SPRUCE_LOG);
                    m.eaveBrackets = true;
                    m.chimney = ArchBlocks.FIELDSTONE.get().defaultBlockState();
                }
                case PALACE -> {
                    m.wall = ArchBlocks.PALE_MASONRY.get().defaultBlockState();
                    m.upperWall = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.frame = log(ArchBlocks.MARBLE_COLUMN.get());
                    m.base = Blocks.QUARTZ_BRICKS.defaultBlockState();
                    m.floor = Blocks.POLISHED_DIORITE.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.TEAL_ROOF_TILES.get(), ArchBlocks.TEAL_ROOF_TILE_STAIRS.get(), ArchBlocks.TEAL_ROOF_TILE_SLAB.get()}});
                    m.roofKind = RoofKind.HIP;
                    m.shutter = ArchBlocks.BLUE_SHUTTER.get();
                    m.door = Blocks.SPRUCE_DOOR.defaultBlockState();
                    m.fence = Blocks.IRON_BARS.defaultBlockState();
                    m.trim = Blocks.GOLD_BLOCK.defaultBlockState();
                    m.planters = true;
                    m.chimney = ArchBlocks.PALE_MASONRY.get().defaultBlockState();
                }
                case CLOUD -> {
                    m.wall = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                    m.upperWall = m.wall;
                    m.frame = ArchBlocks.SKY_PLASTER.get().defaultBlockState();
                    m.base = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
                    m.floor = Blocks.BIRCH_PLANKS.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.SKY_ROOF_TILES.get(), ArchBlocks.SKY_ROOF_TILE_STAIRS.get(), ArchBlocks.SKY_ROOF_TILE_SLAB.get()}});
                    m.roofKind = RoofKind.HIP;
                    m.window = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
                    m.door = Blocks.BIRCH_DOOR.defaultBlockState();
                    m.fence = Blocks.BIRCH_FENCE.defaultBlockState();
                    m.trim = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
                    m.lantern = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
                    m.chimney = ArchBlocks.WHITE_PLASTER.get().defaultBlockState();
                }
                case CORAL -> {
                    m.wall = pick(v, ArchBlocks.MINT_PLASTER, ArchBlocks.SKY_PLASTER, ArchBlocks.SALMON_PLASTER, ArchBlocks.MINT_PLASTER);
                    m.upperWall = m.wall;
                    m.frame = Blocks.PRISMARINE_BRICKS.defaultBlockState();
                    m.base = Blocks.DARK_PRISMARINE.defaultBlockState();
                    m.floor = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
                    roof(m, 0, new Block[][]{{ArchBlocks.SKY_ROOF_TILES.get(), ArchBlocks.SKY_ROOF_TILE_STAIRS.get(), ArchBlocks.SKY_ROOF_TILE_SLAB.get()}});
                    m.roofKind = RoofKind.HIP;
                    m.window = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState();
                    m.door = Blocks.WARPED_DOOR.defaultBlockState();
                    m.fence = Blocks.WARPED_FENCE.defaultBlockState();
                    m.trim = Blocks.PRISMARINE_BRICKS.defaultBlockState();
                    m.lantern = Blocks.SEA_LANTERN.defaultBlockState();
                    m.chimney = Blocks.PRISMARINE_BRICKS.defaultBlockState();
                }
            }
            m.sill = switch (st) {
                case DESERT -> ArchBlocks.DRESSED_SANDSTONE_STAIRS.get().defaultBlockState();
                case MARINE -> ArchBlocks.PALE_MASONRY_STAIRS.get().defaultBlockState();
                case PALACE, CLOUD -> Blocks.QUARTZ_STAIRS.defaultBlockState();
                case STONE_TOWN, SNOW -> Blocks.STONE_BRICK_STAIRS.defaultBlockState();
                case GOTHIC -> Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState();
                case CORAL -> Blocks.PRISMARINE_BRICK_STAIRS.defaultBlockState();
                case FUTURE -> null;
                case CANDY -> Blocks.CHERRY_STAIRS.defaultBlockState();
                default -> Blocks.SPRUCE_STAIRS.defaultBlockState();
            };
            if (m.roof == null) roof(m, 0, new Block[][]{{ArchBlocks.TERRACOTTA_ROOF_TILES.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_STAIRS.get(), ArchBlocks.TERRACOTTA_ROOF_TILE_SLAB.get()}});
            return m;
        }

        private static void roof(Mats m, int v, Block[][] options) {
            Block[] o = options[Math.floorMod(v, options.length)];
            m.roof = o[0].defaultBlockState();
            m.roofStairs = o[1].defaultBlockState();
            m.roofSlab = o[2].defaultBlockState();
        }

        @SafeVarargs
        private static BlockState pick(int v, net.neoforged.neoforge.registries.DeferredBlock<Block>... opts) {
            return opts[Math.floorMod(v, opts.length)].get().defaultBlockState();
        }

        private static Block pickB(int v, Block... opts) {
            return opts[Math.floorMod(v, opts.length)];
        }

        /** A pillar block standing upright (logs, beams, columns). */
        static BlockState log(Block b) {
            BlockState s = b.defaultBlockState();
            return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : s;
        }

        /** Horizontal beam of the frame material along a local axis (falls back to the frame block). */
        public BlockState beam(Direction.Axis axis) {
            return frame.hasProperty(RotatedPillarBlock.AXIS) ? frame.setValue(RotatedPillarBlock.AXIS, axis) : frame;
        }

        public boolean timbered() {
            return frame.hasProperty(RotatedPillarBlock.AXIS);
        }
    }

    // ================================================================== block-state helpers

    public static BlockState stair(BlockState s, Direction facing, Half half, StairsShape shape) {
        if (!s.hasProperty(StairBlock.FACING)) return s;
        return s.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half).setValue(StairBlock.SHAPE, shape);
    }

    public static BlockState stair(BlockState s, Direction facing, Half half) {
        return stair(s, facing, half, StairsShape.STRAIGHT);
    }

    public static BlockState slab(BlockState s, SlabType t) {
        return s.hasProperty(SlabBlock.TYPE) ? s.setValue(SlabBlock.TYPE, t) : s;
    }

    public static BlockState axis(BlockState s, Direction.Axis a) {
        return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, a) : s;
    }

    public static BlockState facing(BlockState s, Direction f) {
        if (s.hasProperty(HorizontalDirectionalBlock.FACING) && f.getAxis().isHorizontal()) return s.setValue(HorizontalDirectionalBlock.FACING, f);
        if (s.hasProperty(DirectionalBlock.FACING)) return s.setValue(DirectionalBlock.FACING, f);
        return s;
    }

    /** Pane, bars or fence connected along one horizontal axis (both sides). */
    public static BlockState along(BlockState s, Direction.Axis a) {
        boolean x = a == Direction.Axis.X;
        if (s.hasProperty(CrossCollisionBlock.EAST))
            return s.setValue(CrossCollisionBlock.EAST, x).setValue(CrossCollisionBlock.WEST, x).setValue(CrossCollisionBlock.NORTH, !x).setValue(CrossCollisionBlock.SOUTH, !x);
        if (s.hasProperty(WallBlock.EAST_WALL))
            return s.setValue(WallBlock.UP, false).setValue(WallBlock.EAST_WALL, x ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.WEST_WALL, x ? WallSide.LOW : WallSide.NONE)
                    .setValue(WallBlock.NORTH_WALL, x ? WallSide.NONE : WallSide.LOW).setValue(WallBlock.SOUTH_WALL, x ? WallSide.NONE : WallSide.LOW);
        return s;
    }

    /** Explicit connections (n, e, s, w) for panes, bars, fences and walls. */
    public static BlockState connect(BlockState s, boolean n, boolean e, boolean so, boolean w) {
        if (s.hasProperty(CrossCollisionBlock.EAST))
            return s.setValue(CrossCollisionBlock.NORTH, n).setValue(CrossCollisionBlock.EAST, e).setValue(CrossCollisionBlock.SOUTH, so).setValue(CrossCollisionBlock.WEST, w);
        if (s.hasProperty(WallBlock.EAST_WALL)) {
            boolean straight = (n && so && !e && !w) || (e && w && !n && !so);
            return s.setValue(WallBlock.UP, !straight).setValue(WallBlock.NORTH_WALL, n ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.EAST_WALL, e ? WallSide.LOW : WallSide.NONE)
                    .setValue(WallBlock.SOUTH_WALL, so ? WallSide.LOW : WallSide.NONE).setValue(WallBlock.WEST_WALL, w ? WallSide.LOW : WallSide.NONE);
        }
        return s;
    }

    public static BlockState shutterOpen(Block shutter, Direction outward) {
        return shutter.defaultBlockState().setValue(TrapDoorBlock.FACING, outward).setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.BOTTOM);
    }

    public static BlockState lantern(BlockState l, boolean hanging) {
        return l.hasProperty(LanternBlock.HANGING) ? l.setValue(LanternBlock.HANGING, hanging) : l;
    }

    public static BlockState decor(net.neoforged.neoforge.registries.DeferredBlock<Block> b, Direction facing) {
        BlockState s = b.get().defaultBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.setValue(HorizontalDirectionalBlock.FACING, facing) : s;
    }

    // ================================================================== roofs

    /**
     * Gable roof over the wall box [x1..x2]x[z1..z2] whose walls top out at y-1. The ridge runs along x (alongX) or z;
     * the roof overhangs the eaves and the gable ends by {@code ov}. Gable triangles are filled with {@code gable} in the
     * end-wall planes. Steep (one block per step) unless m.roofKind is LOW_GABLE (half-block slab steps).
     * Returns the ridge height (local y of the top roof course).
     */
    public static int gable(Builder b, Mats m, int x1, int z1, int x2, int z2, int y, boolean alongX, int ov, BlockState gable) {
        boolean low = m.roofKind == RoofKind.LOW_GABLE, steep = m.roofKind == RoofKind.STEEP;
        int a1 = alongX ? z1 : x1, a2 = alongX ? z2 : x2;       // across the slope
        int l1 = (alongX ? x1 : z1) - ov, l2 = (alongX ? x2 : z2) + ov; // along the ridge
        int lo = a1 - ov, hi = a2 + ov;
        int top = y;
        Direction up = alongX ? Direction.SOUTH : Direction.EAST;
        for (int i = 0; lo + i <= hi - i; i++) {
            int p = lo + i, q = hi - i;
            int yy = low ? y + i / 2 : steep ? y + 2 * i + 1 : y + i;
            top = Math.max(top, yy);
            for (int l = l1; l <= l2; l++) {
                if (steep) { // two blocks of rise per step: a roof block under a stair
                    put(b, alongX, l, yy - 1, p, m.roof);
                    put(b, alongX, l, yy - 1, q, m.roof);
                    if (p == q) put(b, alongX, l, yy, p, slab(m.roofSlab, SlabType.BOTTOM));
                    else {
                        put(b, alongX, l, yy, p, stair(m.roofStairs, up, Half.BOTTOM));
                        put(b, alongX, l, yy, q, stair(m.roofStairs, up.getOpposite(), Half.BOTTOM));
                    }
                    continue;
                }
                if (p == q) { // odd span: a ridge cap over a solid core
                    put(b, alongX, l, yy, p, slab(m.roofSlab, low && i % 2 == 1 ? SlabType.TOP : SlabType.BOTTOM));
                    if (!(low && i % 2 == 1)) put(b, alongX, l, yy - 1, p, m.roof);
                } else if (low) {
                    BlockState s = slab(m.roofSlab, i % 2 == 0 ? SlabType.BOTTOM : SlabType.TOP);
                    put(b, alongX, l, yy, p, s);
                    put(b, alongX, l, yy, q, s);
                } else {
                    put(b, alongX, l, yy, p, stair(m.roofStairs, up, Half.BOTTOM));
                    put(b, alongX, l, yy, q, stair(m.roofStairs, up.getOpposite(), Half.BOTTOM));
                }
            }
        }
        // gable triangles in the end-wall planes: wall material up to just under the roof surface
        if (gable != null) {
            for (int a = a1; a <= a2; a++) {
                int r = Math.min(a - lo, hi - a);
                int under = low ? y + r / 2 - 1 : steep ? y + 2 * r - 1 : y + r - 1;
                for (int l : new int[]{l1 + ov, l2 - ov})
                    for (int gy = y; gy <= under; gy++) put(b, alongX, l, gy, a, gable);
            }
        }
        // eave brackets under the overhang
        if (m.eaveBrackets && ov > 0 && !low && !steep) {
            for (int l = l1 + ov; l <= l2 - ov; l += 2) {
                put(b, alongX, l, y - 1, a1 - 1, stair(m.roofStairs, up, Half.TOP));
                put(b, alongX, l, y - 1, a2 + 1, stair(m.roofStairs, up.getOpposite(), Half.TOP));
            }
        }
        return top;
    }

    /** Hip roof over [x1..x2]x[z1..z2] (walls top out at y-1), overhang ov; FLARED kicks up the four lowest corners. */
    public static int hip(Builder b, Mats m, int x1, int z1, int x2, int z2, int y, int ov) {
        int ax = x1 - ov, bx = x2 + ov, az = z1 - ov, bz = z2 + ov;
        int i = 0, top = y;
        while (ax <= bx && az <= bz) {
            int yy = y + i;
            top = yy;
            if (ax == bx || az == bz) { // ridge line
                for (int x = ax; x <= bx; x++)
                    for (int z = az; z <= bz; z++) {
                        b.set(x, yy, z, slab(m.roofSlab, SlabType.BOTTOM));
                        b.set(x, yy - 1, z, m.roof);
                    }
                break;
            }
            if (bx - ax == 1 || bz - az == 1) {
                // two-wide ridge: stairs meeting along the long axis
                boolean alongX = (bx - ax) >= (bz - az);
                for (int x = ax; x <= bx; x++)
                    for (int z = az; z <= bz; z++) {
                        Direction f = alongX ? (z == az ? Direction.SOUTH : Direction.NORTH) : (x == ax ? Direction.EAST : Direction.WEST);
                        b.set(x, yy, z, stair(m.roofStairs, f, Half.BOTTOM));
                    }
                break;
            }
            for (int x = ax + 1; x < bx; x++) {
                b.set(x, yy, az, stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
                b.set(x, yy, bz, stair(m.roofStairs, Direction.NORTH, Half.BOTTOM));
            }
            for (int z = az + 1; z < bz; z++) {
                b.set(ax, yy, z, stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
                b.set(bx, yy, z, stair(m.roofStairs, Direction.WEST, Half.BOTTOM));
            }
            int lift = (m.roofKind == RoofKind.FLARED && i == 0) ? 1 : 0;
            b.set(ax, yy + lift, az, stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
            b.set(bx, yy + lift, az, stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
            b.set(ax, yy + lift, bz, stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
            b.set(bx, yy + lift, bz, stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
            if (lift > 0) { // the upturned tip sits on a roof block so it is not floating
                b.set(ax, yy, az, m.roof);
                b.set(bx, yy, az, m.roof);
                b.set(ax, yy, bz, m.roof);
                b.set(bx, yy, bz, m.roof);
            }
            ax++;
            bx--;
            az++;
            bz--;
            i++;
        }
        if (m.eaveBrackets && ov > 0) {
            for (int x = x1; x <= x2; x += 2) {
                b.set(x, y - 1, z1 - 1, stair(m.roofStairs, Direction.SOUTH, Half.TOP));
                b.set(x, y - 1, z2 + 1, stair(m.roofStairs, Direction.NORTH, Half.TOP));
            }
        }
        return top;
    }

    /** Flat roof deck at y over [x1..x2]x[z1..z2] with a parapet (merlons if crenellated) and an outward cornice. */
    public static void flat(Builder b, Mats m, int x1, int z1, int x2, int z2, int y, boolean crenels, boolean cornice) {
        b.fill(x1, y, z1, x2, y, z2, m.roof);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (!(x == x1 || x == x2 || z == z1 || z == z2)) continue;
                b.set(x, y + 1, z, m.wall);
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                if (crenels && (corner || (x + z) % 2 == 0)) b.set(x, y + 2, z, m.wall);
            }
        if (cornice) {
            for (int x = x1; x <= x2; x++) {
                b.set(x, y, z1 - 1, stair(m.roofStairs, Direction.SOUTH, Half.TOP));
                b.set(x, y, z2 + 1, stair(m.roofStairs, Direction.NORTH, Half.TOP));
            }
            for (int z = z1; z <= z2; z++) {
                b.set(x1 - 1, y, z, stair(m.roofStairs, Direction.EAST, Half.TOP));
                b.set(x2 + 1, y, z, stair(m.roofStairs, Direction.WEST, Half.TOP));
            }
        }
    }

    /**
     * Dome of radius r on a drum centred at (cx, y, cz): a hemisphere shell of {@code shell} (onion-shaped when
     * {@code onion}, i.e. bulging out before narrowing to a finial), with a finial of {@code tip} on top.
     */
    public static void dome(Builder b, int cx, int y, int cz, int r, BlockState shell, BlockState tip, boolean onion) {
        int top = 0;
        for (int dy = 0; dy <= (onion ? r + r / 2 + 1 : r); dy++) {
            double rr;
            if (onion) {
                double t = dy / (double) (r + r / 2 + 1);
                rr = r * (t < 0.35 ? 1.0 + 0.15 * Math.sin(t / 0.35 * Math.PI / 2) : 1.15 * Math.cos((t - 0.35) / 0.65 * Math.PI / 2));
            } else rr = Math.sqrt(Math.max(0, r * r - dy * dy));
            for (int x = -r - 2; x <= r + 2; x++)
                for (int z = -r - 2; z <= r + 2; z++) {
                    double dd = Math.sqrt(x * x + z * z);
                    if (dd <= rr + 0.5 && dd >= rr - 0.9) b.set(cx + x, y + dy, cz + z, shell);
                }
            if (rr > 0.3) top = dy;
        }
        b.set(cx, y + top + 1, cz, shell);
        b.set(cx, y + top + 2, cz, tip);
    }

    /**
     * A gabled dormer on the north slope of a gable roof whose ridge runs along x: a three-wide window bay standing on
     * the wall plate at height y (the roof's first course) at x, with its own little gable roof and a window facing
     * north. Clears the main roof inside it.
     */
    public static void dormer(Builder b, Mats m, int x, int z, int y) {
        b.clear(x, y, z, x, y + 1, z + 2);
        for (int dz = 0; dz <= 2; dz++) {
            b.set(x - 1, y, z + dz, m.upperWall);
            b.set(x + 1, y, z + dz, m.upperWall);
            b.set(x - 1, y + 1, z + dz, m.upperWall);
            b.set(x + 1, y + 1, z + dz, m.upperWall);
        }
        b.set(x, y, z, along(m.window, Direction.Axis.X));
        b.set(x, y + 1, z, m.upperWall);
        for (int dz = -1; dz <= 2; dz++) {
            b.set(x - 2, y + 1, z + dz, stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
            b.set(x + 2, y + 1, z + dz, stair(m.roofStairs, Direction.WEST, Half.BOTTOM));
            b.set(x - 1, y + 2, z + dz, stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
            b.set(x + 1, y + 2, z + dz, stair(m.roofStairs, Direction.WEST, Half.BOTTOM));
            b.set(x, y + 2, z + dz, m.roof);
            b.set(x, y + 3, z + dz, slab(m.roofSlab, SlabType.BOTTOM));
        }
    }

    /**
     * A skirt roof (Japanese hisashi / pent roof) round a box at height y: one course of roof stairs one block outside
     * the walls, sloping outward, with outer corners.
     */
    public static void skirt(Builder b, Mats m, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            b.set(x, y, z1 - 1, stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM));
            b.set(x, y, z2 + 1, stair(m.roofStairs, Direction.NORTH, Half.BOTTOM));
        }
        for (int z = z1; z <= z2; z++) {
            b.set(x1 - 1, y, z, stair(m.roofStairs, Direction.EAST, Half.BOTTOM));
            b.set(x2 + 1, y, z, stair(m.roofStairs, Direction.WEST, Half.BOTTOM));
        }
        b.set(x1 - 1, y, z1 - 1, stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
        b.set(x2 + 1, y, z1 - 1, stair(m.roofStairs, Direction.SOUTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
        b.set(x1 - 1, y, z2 + 1, stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_RIGHT));
        b.set(x2 + 1, y, z2 + 1, stair(m.roofStairs, Direction.NORTH, Half.BOTTOM, StairsShape.OUTER_LEFT));
    }

    /** Single-pitch lean-to (porches, sheds) sloping down toward {@code low} from height yHigh; [x1..x2]x[z1..z2]. */
    public static void leanTo(Builder b, Mats m, int x1, int z1, int x2, int z2, int yHigh, Direction low) {
        int depth = low.getAxis() == Direction.Axis.Z ? z2 - z1 + 1 : x2 - x1 + 1;
        for (int i = 0; i < depth; i++) {
            int y = yHigh - i / 2;
            SlabType t = i % 2 == 0 ? SlabType.TOP : SlabType.BOTTOM;
            BlockState s = slab(m.roofSlab, t);
            if (low == Direction.NORTH) b.fill(x1, y, z2 - i, x2, y, z2 - i, s);
            else if (low == Direction.SOUTH) b.fill(x1, y, z1 + i, x2, y, z1 + i, s);
            else if (low == Direction.WEST) b.fill(x2 - i, y, z1, x2 - i, y, z2, s);
            else b.fill(x1 + i, y, z1, x1 + i, y, z2, s);
        }
    }

    /** Helper for gable(): writes in (along, y, across) coordinates; null clears. */
    private static void put(Builder b, boolean alongX, int l, int y, int a, BlockState s) {
        if (s == null) return;
        if (alongX) b.set(l, y, a, s);
        else b.set(a, y, l, s);
    }

    // ================================================================== walls and openings

    /**
     * A window in a wall plane: {@code h} tall glazed opening at (x, y, z) in a wall whose outside faces {@code out},
     * with a lintel above, a sill (or planter) below on the outside and open shutters either side on the outside.
     */
    public static void window(Builder b, Mats m, int x, int y, int z, Direction out, int h, boolean shutters) {
        Direction.Axis along = out.getAxis() == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
        for (int k = 0; k < h; k++) b.set(x, y + k, z, along(m.window, along));
        int ox = x + out.getStepX(), oz = z + out.getStepZ();
        // sill: a planter on homely styles, otherwise a stone/wood corbel
        if (m.planters) b.set(ox, y - 1, oz, decor(ArchBlocks.FLOWER_PLANTER, out));
        else if (m.sill != null) b.set(ox, y - 1, oz, stair(m.sill, out.getOpposite(), Half.TOP));
        if (shutters && m.shutter != null) { // open shutters folded flat against the wall either side
            int sx = along == Direction.Axis.X ? 1 : 0, sz = along == Direction.Axis.Z ? 1 : 0;
            for (int k = 0; k < h; k++) {
                b.set(ox - sx, y + k, oz - sz, shutterOpen(m.shutter, out));
                b.set(ox + sx, y + k, oz + sz, shutterOpen(m.shutter, out));
            }
        }
    }

    /** Wall-mounted lantern: a hanging lantern under a small bracket on the outside of a wall. */
    public static void wallLantern(Builder b, Mats m, int x, int y, int z, Direction out) {
        int ox = x + out.getStepX(), oz = z + out.getStepZ();
        b.set(ox, y + 1, oz, connectToward(m.fence, out.getOpposite()));
        b.set(ox, y, oz, lantern(m.lantern, true));
    }

    private static BlockState connectToward(BlockState f, Direction d) {
        return connect(f, d == Direction.NORTH, d == Direction.EAST, d == Direction.SOUTH, d == Direction.WEST);
    }

    /** Chimney stack at (x, z) from y1 to y2 with a slab cap and, if lit, a campfire making smoke on top. */
    public static void chimney(Builder b, Mats m, int x, int z, int y1, int y2, boolean smoke) {
        b.fill(x, y1, z, x, y2, z, m.chimney);
        if (smoke) {
            b.set(x, y2 + 1, z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true).setValue(CampfireBlock.SIGNAL_FIRE, false));
            b.set(x, y2, z, Blocks.HAY_BLOCK.defaultBlockState());
        } else {
            b.set(x, y2 + 1, z, slab(Blocks.STONE_BRICK_SLAB.defaultBlockState(), SlabType.BOTTOM));
        }
    }

    /** A straight staircase climbing toward {@code dir} from (x, y, z): n steps, clearing headroom above. */
    public static void staircase(Builder b, BlockState stairs, int x, int y, int z, Direction dir, int n) {
        for (int i = 0; i < n; i++) {
            int sx = x + dir.getStepX() * i, sz = z + dir.getStepZ() * i;
            b.set(sx, y + i, sz, stair(stairs, dir, Half.BOTTOM));
            b.clear(sx, y + i + 1, sz, sx, y + i + 3, sz);
        }
    }

    // ================================================================== furniture

    /** Table with two chairs (stairs) along x. */
    public static void tableSet(Builder b, int x, int y, int z, BlockState chair) {
        b.set(x, y, z, decor(ArchBlocks.PLANK_TABLE, Direction.NORTH));
        b.set(x - 1, y, z, stair(chair, Direction.WEST, Half.BOTTOM));
        b.set(x + 1, y, z, stair(chair, Direction.EAST, Half.BOTTOM));
    }

    public static void cargo(Builder b, int x, int y, int z, int salt) {
        switch (Math.floorMod(salt, 4)) {
            case 0 -> {
                b.set(x, y, z, ArchBlocks.CARGO_CRATE.get().defaultBlockState());
                b.set(x, y + 1, z, decor(ArchBlocks.CARGO_SACKS, Direction.NORTH));
            }
            case 1 -> b.set(x, y, z, decor(ArchBlocks.CARGO_SACKS, Direction.EAST));
            case 2 -> {
                b.set(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                b.set(x, y + 1, z, decor(ArchBlocks.ROPE_COIL, Direction.NORTH));
            }
            default -> b.set(x, y, z, ArchBlocks.CARGO_CRATE.get().defaultBlockState());
        }
    }
}

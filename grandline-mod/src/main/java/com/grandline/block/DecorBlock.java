package com.grandline.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Static architectural props of the building kit (cargo, dock furniture, counters, awnings...): a horizontal facing,
 * waterlogging, and a selection/collision shape from {@link Shape}. No block entity and no ticking. Shapes are written
 * for a prop facing north (its front toward -z) and rotated for the other facings.
 */
public class DecorBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public enum Shape {
        FULL(box(0, 0, 0, 16, 16, 16)),
        SACKS(box(1, 0, 1, 15, 10, 15)),
        ROPE_COIL(box(2, 0, 2, 14, 5, 14)),
        BOLLARD(box(4, 0, 4, 12, 14, 12)),
        CANNON(box(3, 0, 0, 13, 9, 16)),
        COUNTER(Shapes.or(box(0, 0, 2, 16, 13, 16), box(0, 13, 0, 16, 16, 16))),
        TABLE(Shapes.or(box(0, 13, 0, 16, 16, 16), box(6, 0, 6, 10, 13, 10))),
        BENCH(Shapes.or(box(0, 6, 3, 16, 8, 13), box(1, 0, 4, 3, 6, 12), box(13, 0, 4, 15, 6, 12))),
        WALL_PANEL(box(0, 0, 14, 16, 16, 16)),
        AWNING(Shapes.or(box(0, 14, 8, 16, 16, 16), box(0, 10, 0, 16, 14, 8))),
        PLANTER(box(1, 0, 1, 15, 8, 15)),
        LOW_BOX(box(1, 0, 1, 15, 6, 15));

        final VoxelShape north;

        Shape(VoxelShape north) {
            this.north = north;
        }
    }

    public static final MapCodec<DecorBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("shape").forGetter(b -> b.shape.name()),
            propertiesCodec()).apply(i, (s, p) -> new DecorBlock(p, Shape.valueOf(s))));

    private final Shape shape;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public DecorBlock(Properties props, Shape shape) {
        super(props);
        this.shape = shape;
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotate(shape.north, d));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    private static VoxelShape rotate(VoxelShape s, Direction to) {
        int turns = switch (to) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape out = s;
        for (int t = 0; t < turns; t++) {
            VoxelShape[] acc = {Shapes.empty()};
            out.forAllBoxes((x1, y1, z1, x2, y2, z2) -> acc[0] = Shapes.or(acc[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
            out = acc[0];
        }
        return out;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean water = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState nb, LevelAccessor level, BlockPos pos, BlockPos nbPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, dir, nb, level, pos, nbPos);
    }
}

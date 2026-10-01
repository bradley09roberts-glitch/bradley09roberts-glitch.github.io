package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.world.StormNetwork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Aetherium Conductor: whichever side a storm pulse comes in from, it leaves through the conductor's face (the arrow).
 * Placed facing away from you. A conductor pointing straight back at the pulse swallows it.
 */
public class ConductorBlock extends Block implements StormNode {
    public static final MapCodec<ConductorBlock> CODEC = simpleCodec(ConductorBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ConductorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public List<Direction> receive(ServerLevel level, BlockPos pos, BlockState state, Direction travel) {
        level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 10);
        Direction out = state.getValue(FACING);
        return out == travel.getOpposite() ? List.of() : List.of(out);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            StormBlocksFx.sparks(level, pos, random, 3);
        }
    }

    /** Pulse visuals shared with relays. */
    static void flash(ServerLevel level, BlockPos pos) {
        StormNetwork.flash(level, pos);
    }
}

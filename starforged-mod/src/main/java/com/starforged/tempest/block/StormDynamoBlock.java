package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.tempest.world.StormreachTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Storm Dynamo: the source of a storm network. It fires a pulse out of its face whenever lightning strikes within two
 * blocks of it or it receives a fresh redstone signal. Under the open sky of Stormreach it draws a bolt down onto
 * itself every few seconds while someone is near.
 */
public class StormDynamoBlock extends StormTickingBlock {
    public static final MapCodec<StormDynamoBlock> CODEC = simpleCodec(StormDynamoBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public StormDynamoBlock(BlockBehaviour.Properties properties) {
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

    public static void fire(ServerLevel level, BlockPos pos, BlockState state) {
        StormNetwork.flash(level, pos);
        StormNetwork.pulse(level, pos, state.getValue(FACING));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean powered = server.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            server.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            if (powered) {
                fire(server, pos, state);
            }
        }
    }

    @Override
    public void stormTick(ServerLevel level, BlockPos pos, BlockState state, StormTickerBlockEntity entity) {
        if (!StormreachTravel.isStormreach(level) || !level.canSeeSky(pos.above())) {
            return;
        }
        if (--entity.timer > 0) {
            return;
        }
        entity.timer = 90 + level.getRandom().nextInt(70);
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40.0, false) == null) {
            return;
        }
        StormNetwork.visualBolt(level, pos.above());
        fire(level, pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            StormBlocksFx.sparks(level, pos, random, 1);
        }
    }
}

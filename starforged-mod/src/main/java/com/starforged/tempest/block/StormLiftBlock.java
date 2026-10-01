package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Storm Lift: a wind vent that only blows while powered - by redstone, or for fifteen seconds after a storm pulse
 * reaches it. It carries riders sixteen blocks up.
 */
public class StormLiftBlock extends WindVentBlock implements StormNode {
    public static final MapCodec<StormLiftBlock> CODEC = simpleCodec(StormLiftBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public StormLiftBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected boolean active(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getValue(POWERED);
    }

    @Override
    protected int height() {
        return 16;
    }

    @Override
    public void stormTick(ServerLevel level, BlockPos pos, BlockState state, StormTickerBlockEntity entity) {
        if (entity.timer > 0 && --entity.timer == 0 && !level.hasNeighborSignal(pos)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
            return;
        }
        super.stormTick(level, pos, state, entity);
    }

    @Override
    public List<Direction> receive(ServerLevel level, BlockPos pos, BlockState state, Direction travel) {
        if (!state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof StormTickerBlockEntity ticker) {
            ticker.timer = 300;
        }
        return List.of();
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean moved) {
        if (level instanceof ServerLevel server) {
            boolean powered = server.hasNeighborSignal(pos);
            if (powered && !state.getValue(POWERED)) {
                server.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
            } else if (!powered && state.getValue(POWERED) && server.getBlockEntity(pos) instanceof StormTickerBlockEntity t && t.timer <= 0) {
                server.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            super.animateTick(state, level, pos, random);
        }
    }
}

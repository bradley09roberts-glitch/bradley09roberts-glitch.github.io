package com.starforged.tempest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A block with a server heartbeat (see {@link StormTickerBlockEntity}). */
public abstract class StormTickingBlock extends Block implements EntityBlock {
    protected StormTickingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Called every server tick. */
    public abstract void stormTick(ServerLevel level, BlockPos pos, BlockState state, StormTickerBlockEntity entity);

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StormTickerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : StormTickerBlockEntity.ticker(type);
    }
}

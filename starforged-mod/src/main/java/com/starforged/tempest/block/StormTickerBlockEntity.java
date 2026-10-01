package com.starforged.tempest.block;

import com.starforged.tempest.TempestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Server-side heartbeat for {@link StormTickingBlock}s. Holds a single countdown each block uses as it likes. */
public class StormTickerBlockEntity extends BlockEntity {
    public int timer;

    public StormTickerBlockEntity(BlockPos pos, BlockState state) {
        super(TempestBlockEntities.STORM_TICKER.get(), pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.timer = input.getIntOr("timer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("timer", this.timer);
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker(BlockEntityType<T> type) {
        if (type != TempestBlockEntities.STORM_TICKER.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<StormTickerBlockEntity>) StormTickerBlockEntity::tick;
    }

    private static void tick(Level level, BlockPos pos, BlockState state, StormTickerBlockEntity entity) {
        if (level instanceof ServerLevel server && state.getBlock() instanceof StormTickingBlock block) {
            block.stormTick(server, pos, state, entity);
        }
    }
}

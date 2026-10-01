package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.tempest.world.StormreachTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lightning Beacon: under an open sky it calls lightning down onto itself every eight seconds - always in Stormreach,
 * elsewhere only during a thunderstorm or while it has a redstone signal. The bolt charges any Aetherium lying on top
 * and fires Storm Dynamos beside it. Its bolts never start fires.
 */
public class LightningBeaconBlock extends StormTickingBlock {
    public static final MapCodec<LightningBeaconBlock> CODEC = simpleCodec(LightningBeaconBlock::new);

    public LightningBeaconBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void stormTick(ServerLevel level, BlockPos pos, BlockState state, StormTickerBlockEntity entity) {
        if (--entity.timer > 0) {
            return;
        }
        entity.timer = 160;
        boolean active = StormreachTravel.isStormreach(level) || level.isThundering() || level.hasNeighborSignal(pos);
        if (!active || !level.canSeeSky(pos.above())) {
            return;
        }
        StormNetwork.visualBolt(level, pos.above());
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof StormDynamoBlock) {
                StormDynamoBlock.fire(level, p.immutable(), s);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            StormBlocksFx.sparks(level, pos, random, 1);
        }
    }
}

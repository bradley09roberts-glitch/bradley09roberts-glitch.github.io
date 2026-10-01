package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.world.StormNetwork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Splitter Relay: a pulse that comes in leaves twice, out of both sides (a vertical pulse fans out four ways). */
public class SplitterRelayBlock extends Block implements StormNode {
    public static final MapCodec<SplitterRelayBlock> CODEC = simpleCodec(SplitterRelayBlock::new);

    public SplitterRelayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public List<Direction> receive(ServerLevel level, BlockPos pos, BlockState state, Direction travel) {
        StormNetwork.flash(level, pos);
        return StormNetwork.horizontalSides(travel);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            StormBlocksFx.sparks(level, pos, random, 1);
        }
    }
}

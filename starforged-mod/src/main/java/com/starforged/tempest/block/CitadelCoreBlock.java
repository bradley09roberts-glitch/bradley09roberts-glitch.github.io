package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.CitadelPuzzle;
import com.starforged.tempest.world.StormNetwork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Citadel Core: the end of a storm circuit. A pulse lights it for good (until reset) and it gives off a full redstone
 * signal. In a Tempest Citadel, lighting every core breaks the Tempest Seals. Sneak + right-click to reset a core.
 */
public class CitadelCoreBlock extends Block implements StormNode {
    public static final MapCodec<CitadelCoreBlock> CODEC = simpleCodec(CitadelCoreBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CitadelCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public List<Direction> receive(ServerLevel level, BlockPos pos, BlockState state, Direction travel) {
        StormNetwork.flash(level, pos);
        if (!state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            level.playSound(null, pos, TempestSounds.CORE_ONLINE.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
            CitadelPuzzle.onCoreLit(level, pos);
        }
        return List.of();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && state.getValue(LIT)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(state.getValue(LIT)
                ? "block.starforged.citadel_core.online" : "block.starforged.citadel_core.offline"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(LIT) ? 15 : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        StormBlocksFx.sparks(level, pos, random, state.getValue(LIT) ? 3 : random.nextInt(6) == 0 ? 1 : 0);
    }
}

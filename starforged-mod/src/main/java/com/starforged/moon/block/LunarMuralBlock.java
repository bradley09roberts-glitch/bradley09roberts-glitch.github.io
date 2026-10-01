package com.starforged.moon.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/** A carved panel showing one moon phase: the murals spell out the alignment the orrery rings must take. */
public class LunarMuralBlock extends Block {
    public static final MapCodec<LunarMuralBlock> CODEC = simpleCodec(LunarMuralBlock::new);

    public LunarMuralBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OrreryRingBlock.PHASE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OrreryRingBlock.PHASE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.lunar_mural.read", OrreryRingBlock.phaseName(state.getValue(OrreryRingBlock.PHASE)))
                .withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }
}

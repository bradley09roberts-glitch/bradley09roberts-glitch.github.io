package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.TempestSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Rotating Conductor: a conductor on a turntable. Right-click to swing its face a quarter-turn clockwise
 * (sneak + right-click: anticlockwise). The Tempest Citadel's puzzles are built from these.
 */
public class RotatingConductorBlock extends ConductorBlock {
    public static final MapCodec<RotatingConductorBlock> CODEC = simpleCodec(RotatingConductorBlock::new);

    public RotatingConductorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            Direction facing = state.getValue(FACING);
            Direction next = facing.getAxis().isVertical() ? Direction.NORTH
                : player.isShiftKeyDown() ? facing.getCounterClockWise() : facing.getClockWise();
            level.setBlock(pos, state.setValue(FACING, next), Block.UPDATE_ALL);
            level.playSound(null, pos, TempestSounds.CONDUCTOR_TURN.get(), SoundSource.BLOCKS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            player.sendOverlayMessage(Component.translatable("block.starforged.rotating_conductor.turned",
                Component.translatable("block.starforged.rotating_conductor." + next.getSerializedName())).withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }
}

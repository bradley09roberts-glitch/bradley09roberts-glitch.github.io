package com.grandline.base;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The crew's warehouse. Inside a claimed base every base chest opens the same store, shared by the whole crew and kept
 * with the claim (breaking the chest spills nothing: the goods are the base's, not the box's). Outsiders cannot open it.
 */
public class BaseChestBlock extends Block {
    public BaseChestBlock(Properties p) {
        super(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) CrewBases.openWarehouse(sp, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

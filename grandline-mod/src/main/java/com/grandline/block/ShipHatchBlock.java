package com.grandline.block;

import com.grandline.ship.ShipInteriors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The hatch inside a ship interior: use it to climb back up onto the deck. */
public class ShipHatchBlock extends Block {
    public ShipHatchBlock(Properties p) {
        super(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) ShipInteriors.exit(sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

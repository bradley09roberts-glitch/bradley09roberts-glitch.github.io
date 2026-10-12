package com.longnight.block;

import com.longnight.game.Founding;
import com.longnight.game.Ui;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The heart of a settlement. Every settlement has one at its centre; a player who places one
 * (with a Founding Charter in their inventory) founds a settlement of their own.
 */
public class HearthstoneBlock extends Block {
    public HearthstoneBlock(Properties p) {
        super(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            WorldSim sim = WorldSim.get(sl.getServer());
            Settlement s = sim.settlementAt(pos, 8);
            if (s != null) Ui.settlement(sp, sim, s);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                                 net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            WorldSim sim = WorldSim.get(sl.getServer());
            Settlement s = sim.settlementAt(pos, 8);
            if (s == null || s.kind != Settlement.Kind.SETTLEMENT) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            com.longnight.game.Trade.handIn(sp, sim, s, hand, player.isShiftKeyDown());
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && placer instanceof ServerPlayer sp) {
            Founding.found(sp, sl, pos);
        }
    }
}

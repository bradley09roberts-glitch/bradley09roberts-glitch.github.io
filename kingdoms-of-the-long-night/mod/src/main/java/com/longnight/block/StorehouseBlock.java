package com.longnight.block;

import com.longnight.game.Trade;
import com.longnight.game.Ui;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The settlement's ledger counter. Right-click to see stock and prices and to buy; use an item
 * on it to sell (or, while sneaking, donate) that stack. The ledger is the single source of
 * truth, so nothing can be duplicated through it.
 */
public class StorehouseBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<StorehouseBlock> CODEC = simpleCodec(p -> new StorehouseBlock(p, false));
    private static final VoxelShape STALL = Block.box(1, 0, 1, 15, 12, 15);
    private final boolean stall;

    public StorehouseBlock(Properties p, boolean stall) {
        super(p);
        this.stall = stall;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return stall ? STALL : super.getShape(state, level, pos, ctx);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            WorldSim sim = WorldSim.get(sl.getServer());
            Settlement s = sim.settlementAt(pos, 16);
            if (s == null) Ui.msg(sp, "This ledger belongs to no settlement.");
            else Ui.market(sp, sim, s);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            WorldSim sim = WorldSim.get(sl.getServer());
            Settlement s = sim.settlementAt(pos, 16);
            if (s == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            Trade.handIn(sp, sim, s, hand, player.isShiftKeyDown());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}

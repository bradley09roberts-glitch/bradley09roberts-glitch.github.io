package com.grandline.base;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crew's flag on its pole. Only ever stands at the heart of a claimed island (the flag item claims the island as it
 * is planted); it cannot be broken in survival: a claim ends by {@code /base abandon} or by losing the flag to raiders.
 * Using it opens the base menu.
 */
public class CrewFlagBlock extends Block {
    private static final VoxelShape POLE = Block.box(7, 0, 7, 9, 16, 9);
    private static final VoxelShape OUTLINE = Shapes.or(POLE, Block.box(9, 7, 7.5, 16, 15, 8.5));

    public CrewFlagBlock(Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return POLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) BaseCommands.menu(sp, CrewBases.claimAtFlag(sp.server, pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

package com.terracraft.block;

import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.OpenCraftingPacket;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Terraria crafting station (Work Bench, Anvil...). Using it opens the Terraria crafting screen; what it
 * unlocks is defined by the block tags {@code #terracraft:stations/*}, not by this class.
 */
public class CraftingStationBlock extends Block {
    private final VoxelShape shape;

    public CraftingStationBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            TerraNetwork.sendToPlayer(serverPlayer, OpenCraftingPacket.INSTANCE);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}

package com.terracraft.block;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.registry.content.MobContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Queen Bee Larva, found inside Bee Hives: breaking it awakens the Queen Bee (unless she is already here). */
public class LarvaBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public LarvaBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && !player.isCreative()) {
            BossSummoning.summon(server, serverPlayer, MobContent.QUEEN_BEE.get(), BossSummoning.Arrival.NEARBY);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}

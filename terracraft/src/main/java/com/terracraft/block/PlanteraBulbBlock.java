package com.terracraft.block;

import com.terracraft.entity.boss.BossSummoning;
import com.terracraft.registry.content.MobContent;
import com.terracraft.world.jungle.JungleGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Plantera's Bulb: a big pink flower bud that grows on jungle grass in the underground jungle once all three
 * mechanical bosses are dead (see {@link JungleGrowth}). Breaking it awakens Plantera right where it stood.
 */
public class PlanteraBulbBlock extends PlantBlock {
    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(1, 0, 1, 15, 16, 15);

    public PlanteraBulbBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && !player.isCreative()) {
            BossSummoning.summonAt(server, serverPlayer, MobContent.PLANTERA.get(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}

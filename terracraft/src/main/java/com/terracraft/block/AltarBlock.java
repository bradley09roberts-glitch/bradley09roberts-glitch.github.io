package com.terracraft.block;

import com.terracraft.mining.ToolPowers;
import com.terracraft.progression.ProgressionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Demon Altar / Crimson Altar: the crafting station for boss summons. Only a Pwnhammer-class hammer
 * (hammer power 80+) can break it, and only in Hardmode.
 */
public class AltarBlock extends CraftingStationBlock {
    public static final int HAMMER_POWER = 80;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 2, 16, 6, 14), Block.box(2, 6, 3, 14, 11, 13));

    public AltarBlock(Properties properties) {
        super(properties, SHAPE);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!ProgressionManager.view(player.level()).isHardmode() || ToolPowers.hammerPower(player.getMainHandItem()) < HAMMER_POWER) {
            return 0.0F;
        }
        return 0.02F;
    }
}

package com.terracraft.block;

import com.terracraft.mining.ToolPowers;
import com.terracraft.world.evil.OrbSmashing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shadow Orb / Crimson Heart: a glowing heart of the evil biome. Only hammers (or explosions) can smash it;
 * smashing drops a treasure and every third one awakens the evil biome's boss ({@link OrbSmashing}).
 */
public class OrbBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 2, 2, 14, 14, 14);
    private final boolean crimson;

    public OrbBlock(Properties properties, boolean crimson) {
        super(properties);
        this.crimson = crimson;
    }

    public boolean isCrimson() {
        return crimson;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (ToolPowers.hammerPower(player.getMainHandItem()) <= 0) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && !player.isCreative()) {
            OrbSmashing.onSmashed(serverLevel, pos, crimson, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}

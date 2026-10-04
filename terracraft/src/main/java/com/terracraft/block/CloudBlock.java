package com.terracraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Cloud (floating islands): soft to land on - falling onto it never hurts. */
public class CloudBlock extends Block {
    public static final MapCodec<CloudBlock> CODEC = simpleCodec(CloudBlock::new);

    public CloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.resetFallDistance();
    }
}

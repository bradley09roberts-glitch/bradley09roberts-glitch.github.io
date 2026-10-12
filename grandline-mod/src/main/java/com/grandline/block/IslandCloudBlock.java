package com.grandline.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Island Cloud: the solid cloud of the White Sea. Soft underfoot — it cushions most of a fall. */
public class IslandCloudBlock extends Block {
    public IslandCloudBlock(Properties p) {
        super(p);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 0.2F, level.damageSources().fall());
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        var v = entity.getDeltaMovement();
        if (v.y < -0.3 && !entity.isSuppressingBounce()) entity.setDeltaMovement(v.x, -v.y * 0.35, v.z);
        else super.updateEntityAfterFallOn(level, entity);
    }
}

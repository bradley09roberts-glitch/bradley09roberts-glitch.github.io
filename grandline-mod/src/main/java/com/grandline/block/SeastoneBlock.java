package com.grandline.block;

import com.grandline.fruit.FruitRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Seastone (kairoseki): standing on it drains Devil Fruit users exactly like the sea does. */
public class SeastoneBlock extends Block {
    public SeastoneBlock(Properties props) {
        super(props);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity le) FruitRules.applySeastoneContact(le, 30);
        super.stepOn(level, pos, state, entity);
    }
}

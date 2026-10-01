package com.starforged.moon.block;

import com.mojang.serialization.MapCodec;
import com.starforged.moon.world.OrreryPuzzle;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The orrery's alignment lever: pull it to test the rings against the murals. */
public class OrreryConsoleBlock extends Block {
    public static final MapCodec<OrreryConsoleBlock> CODEC = simpleCodec(OrreryConsoleBlock::new);

    public OrreryConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            OrreryPuzzle.pull(server, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ModParticles.LUNAR_GLIMMER.get(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + 1.1,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.02, 0.0);
        }
    }
}

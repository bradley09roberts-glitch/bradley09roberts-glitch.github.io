package com.starforged.moon.block;

import com.mojang.serialization.MapCodec;
import com.starforged.moon.MoonSounds;
import com.starforged.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One ring of the great orrery. Right-click turns it to the next moon phase (new, waxing, full, waning). The murals in
 * the Orrery show the alignment the rings must match. Also used (with {@code fixed}) for the murals themselves.
 */
public class OrreryRingBlock extends Block {
    public static final MapCodec<OrreryRingBlock> CODEC = simpleCodec(OrreryRingBlock::new);
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);
    private static final String[] NAMES = {"new", "waxing", "full", "waning"};

    public OrreryRingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PHASE, 0));
    }

    public static Component phaseName(int phase) {
        return Component.translatable("block.starforged.orrery_ring.phase." + NAMES[phase & 3]);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            int next = (state.getValue(PHASE) + 1) & 3;
            server.setBlock(pos, state.setValue(PHASE, next), Block.UPDATE_ALL);
            server.playSound(null, pos, MoonSounds.RING_TURN.get(), SoundSource.BLOCKS, 1.0F, 0.8F + next * 0.15F);
            server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.02);
            player.sendOverlayMessage(Component.translatable("block.starforged.orrery_ring.turned", phaseName(next)));
        }
        return InteractionResult.SUCCESS;
    }
}

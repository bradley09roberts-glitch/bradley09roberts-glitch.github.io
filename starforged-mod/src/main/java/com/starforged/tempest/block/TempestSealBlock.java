package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A wall of caged lightning sealing the Citadel spire. It breaks when every Citadel Core is powered.
 */
public class TempestSealBlock extends Block {
    public static final MapCodec<TempestSealBlock> CODEC = simpleCodec(TempestSealBlock::new);
    public static final BooleanProperty OPENING = BooleanProperty.create("opening");

    public TempestSealBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OPENING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPENING);
    }

    /** Starts the dissolve at this block; it spreads through every connected seal block. */
    public static void open(ServerLevel level, BlockPos pos, int delay) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TempestSealBlock seal && !state.getValue(OPENING)) {
            level.setBlock(pos, state.setValue(OPENING, true), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, seal, Math.max(1, delay));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.tempest_seal.hint"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPENING)) {
            return;
        }
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            BlockState neighbour = level.getBlockState(next);
            if (neighbour.is(this) && !neighbour.getValue(OPENING)) {
                level.setBlock(next, neighbour.setValue(OPENING, true), Block.UPDATE_CLIENTS);
                level.scheduleTick(next, this, 3);
            }
        }
        level.sendParticles(ModParticles.STATIC_SPARK.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.35, 0.35, 0.35, 0.06);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, pos, TempestSounds.SEAL_OPEN.get(), SoundSource.BLOCKS, 0.3F, 1.2F + random.nextFloat() * 0.4F);
        level.removeBlock(pos, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            Direction face = Direction.getRandom(random);
            if (!level.getBlockState(pos.relative(face)).isAir()) {
                return;
            }
            level.addParticle(ModParticles.STATIC_SPARK.get(),
                pos.getX() + 0.5 + face.getStepX() * 0.6 + (random.nextDouble() - 0.5) * (face.getStepX() == 0 ? 1 : 0),
                pos.getY() + 0.5 + face.getStepY() * 0.6 + (random.nextDouble() - 0.5) * (face.getStepY() == 0 ? 1 : 0),
                pos.getZ() + 0.5 + face.getStepZ() * 0.6 + (random.nextDouble() - 0.5) * (face.getStepZ() == 0 ? 1 : 0),
                0.0, 0.01, 0.0);
        }
    }
}

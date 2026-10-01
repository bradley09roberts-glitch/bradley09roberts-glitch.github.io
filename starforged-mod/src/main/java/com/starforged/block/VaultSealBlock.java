package com.starforged.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An unbreakable wall of woven starlight guarding the observatory vault.
 * "The vault answers only to starlight" - offer it Stardust and the whole seal dissolves in a wave.
 */
public class VaultSealBlock extends Block {
    public static final MapCodec<VaultSealBlock> CODEC = simpleCodec(VaultSealBlock::new);
    public static final BooleanProperty OPENING = BooleanProperty.create("opening");

    public VaultSealBlock(BlockBehaviour.Properties properties) {
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

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.STARDUST.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (state.getValue(OPENING)) {
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel serverLevel) {
            stack.consume(1, player);
            serverLevel.setBlock(pos, state.setValue(OPENING, true), Block.UPDATE_CLIENTS);
            serverLevel.scheduleTick(pos, this, 4);
            serverLevel.playSound(null, pos, ModSounds.VAULT_OPEN.get(), SoundSource.BLOCKS, 1.6F, 1.0F);
            serverLevel.sendParticles(ModParticles.STAR_SPARKLE.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, 0.6, 0.6, 0.6, 0.1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.vault_seal.hint"));
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
        level.sendParticles(ModParticles.ASTRAL_GLINT.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.35, 0.35, 0.35, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F + random.nextFloat() * 0.5F);
        level.removeBlock(pos, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            Direction face = Direction.getRandom(random);
            if (!level.getBlockState(pos.relative(face)).isAir()) {
                return;
            }
            level.addParticle(ModParticles.STAR_SPARKLE.get(),
                pos.getX() + 0.5 + face.getStepX() * 0.6 + (random.nextDouble() - 0.5) * (face.getStepX() == 0 ? 1 : 0),
                pos.getY() + 0.5 + face.getStepY() * 0.6 + (random.nextDouble() - 0.5) * (face.getStepY() == 0 ? 1 : 0),
                pos.getZ() + 0.5 + face.getStepZ() * 0.6 + (random.nextDouble() - 0.5) * (face.getStepZ() == 0 ? 1 : 0),
                0.0, 0.0, 0.0);
        }
    }
}

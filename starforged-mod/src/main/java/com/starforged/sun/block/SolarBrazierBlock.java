package com.starforged.sun.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunItems;
import com.starforged.sun.SunSounds;
import com.starforged.sun.world.SunTemplePuzzle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A golden fire bowl. The Sun Temple's inner gate opens only when every brazier in the hall is burning.
 * Light one with Flint and Steel, a Fire Charge or an Ember Shard.
 */
public class SolarBrazierBlock extends Block {
    public static final MapCodec<SolarBrazierBlock> CODEC = simpleCodec(SolarBrazierBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(5, 0, 5, 11, 2, 11),
        Block.box(6.5, 2, 6.5, 9.5, 9, 9.5),
        Block.box(2, 9, 2, 14, 13, 14)
    );

    public SolarBrazierBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean igniter = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(SunItems.EMBER_SHARD.get());
        if (!igniter) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (state.getValue(LIT)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            } else {
                stack.consume(1, player);
            }
            serverLevel.playSound(null, pos, SunSounds.BRAZIER_IGNITE.get(), SoundSource.BLOCKS, 1.2F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);
            serverLevel.sendParticles(ModParticles.SOLAR_SPARK.get(), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 25, 0.3, 0.6, 0.3, 0.08);
            SunTemplePuzzle.onBrazierLit(serverLevel, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && !state.getValue(LIT)) {
            player.sendOverlayMessage(Component.translatable("block.starforged.solar_brazier.hint"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            if (random.nextInt(10) == 0) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0.0, 0.02, 0.0);
            }
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.85, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                0.0, 0.04, 0.0);
        }
        if (random.nextInt(2) == 0) {
            level.addParticle(ModParticles.SOLAR_SPARK.get(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 0.04, 0.06, (random.nextDouble() - 0.5) * 0.04);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos, net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.0F, false);
        }
    }
}

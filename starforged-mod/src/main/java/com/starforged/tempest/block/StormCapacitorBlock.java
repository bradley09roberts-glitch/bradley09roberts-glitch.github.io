package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.TempestItems;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormNetwork;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Storm Capacitor: soaks up storm pulses (3 charge each, up to 15). Comparators read its charge. Use an Aetherium Ingot
 * on it to spend 4 charge and charge the ingot - a way to charge Aetherium without waiting for lightning.
 */
public class StormCapacitorBlock extends Block implements StormNode {
    public static final MapCodec<StormCapacitorBlock> CODEC = simpleCodec(StormCapacitorBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 15);
    public static final int COST = 4;

    public StormCapacitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public List<Direction> receive(ServerLevel level, BlockPos pos, BlockState state, Direction travel) {
        level.setBlock(pos, state.setValue(CHARGE, Math.min(15, state.getValue(CHARGE) + 3)), Block.UPDATE_ALL);
        StormNetwork.flash(level, pos);
        return List.of();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(TempestItems.AETHERIUM_INGOT.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            int charge = state.getValue(CHARGE);
            if (charge < COST) {
                player.sendOverlayMessage(Component.translatable("block.starforged.storm_capacitor.low", charge, COST).withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            server.setBlock(pos, state.setValue(CHARGE, charge - COST), Block.UPDATE_ALL);
            stack.consume(1, player);
            ItemStack charged = new ItemStack(TempestItems.CHARGED_AETHERIUM_INGOT.get());
            if (!player.getInventory().add(charged)) {
                player.drop(charged, false);
            }
            server.playSound(null, pos, TempestSounds.CHARGE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            StormNetwork.arcTo(server, net.minecraft.world.phys.Vec3.atCenterOf(pos), player.position().add(0, 1.0, 0));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.storm_capacitor.charge", state.getValue(CHARGE))
                .withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(CHARGE);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(16) < state.getValue(CHARGE)) {
            StormBlocksFx.sparks(level, pos, random, 1);
        }
    }
}

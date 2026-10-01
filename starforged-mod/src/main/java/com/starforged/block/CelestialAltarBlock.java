package com.starforged.block;

import com.mojang.serialization.MapCodec;
import com.starforged.boss.EclipseSummoning;
import com.starforged.registry.ModItems;
import com.starforged.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The altar crowning every Fallen Observatory. Offer it an Eclipse Sigil to break the last seal and call down
 * the Eclipse Sovereign.
 */
public class CelestialAltarBlock extends Block {
    public static final MapCodec<CelestialAltarBlock> CODEC = simpleCodec(CelestialAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, 4, 16),
        Block.box(3, 4, 3, 13, 11, 13),
        Block.box(1, 11, 1, 15, 15, 15)
    );

    public CelestialAltarBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.ECLIPSE_SIGIL.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (EclipseSummoning.tryBegin(serverLevel, pos, serverPlayer)) {
                stack.consume(1, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("block.starforged.celestial_altar.hint").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double time = (level.getGameTime() % 80) / 80.0 * Math.PI * 2;
        for (int i = 0; i < 2; i++) {
            double angle = time + i * Math.PI;
            level.addParticle(ModParticles.ASTRAL_GLINT.get(),
                pos.getX() + 0.5 + Math.cos(angle) * 0.6, pos.getY() + 1.1 + random.nextDouble() * 0.3, pos.getZ() + 0.5 + Math.sin(angle) * 0.6,
                0.0, 0.03, 0.0);
        }
        if (random.nextInt(6) == 0) {
            level.addParticle(ModParticles.STAR_SPARKLE.get(), pos.getX() + 0.5, pos.getY() + 1.4 + random.nextDouble(), pos.getZ() + 0.5, 0.0, 0.02, 0.0);
        }
    }
}

package com.starforged.moon.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import com.starforged.moon.MoonItems;
import com.starforged.moon.boss.MatriarchSummoning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * The Moon Altar atop the Tidal Orrery. Offer it a Tidal Sigil to call the Pale Matriarch.
 */
public class MoonAltarBlock extends Block {
    public static final MapCodec<MoonAltarBlock> CODEC = simpleCodec(MoonAltarBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, 3, 16),
        Block.box(2, 3, 2, 14, 10, 14),
        Block.box(0, 10, 0, 16, 14, 16)
    );

    public MoonAltarBlock(BlockBehaviour.Properties properties) {
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
        if (!stack.is(MoonItems.TIDAL_SIGIL.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            if (MatriarchSummoning.tryBegin(serverLevel, pos, serverPlayer)) {
                stack.consume(1, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.moon_altar.hint").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double time = (level.getGameTime() % 60) / 60.0 * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double angle = time + i * Math.PI * 2 / 3;
            level.addParticle(ModParticles.LUNAR_GLIMMER.get(),
                pos.getX() + 0.5 + Math.cos(angle) * 0.65, pos.getY() + 1.0 + random.nextDouble() * 0.2, pos.getZ() + 0.5 + Math.sin(angle) * 0.65,
                0.0, 0.04, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
    }
}

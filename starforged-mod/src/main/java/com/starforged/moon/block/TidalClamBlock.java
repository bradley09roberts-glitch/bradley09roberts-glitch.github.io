package com.starforged.moon.block;

import com.mojang.serialization.MapCodec;
import com.starforged.moon.MoonItems;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.world.MoonTides;
import com.starforged.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A giant silver clam on the Silver Sea floor. It only opens at LOW TIDE, when its Lunar Pearl can be taken; at high
 * tide it clamps shut. A harvested clam grows a new pearl over the next tides.
 */
public class TidalClamBlock extends Block {
    public static final MapCodec<TidalClamBlock> CODEC = simpleCodec(TidalClamBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty PEARL = BooleanProperty.create("pearl");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 7, 15);

    public TidalClamBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OPEN, false).setValue(PEARL, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, PEARL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState next = this.update(state, level, random);
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
        }
    }

    private BlockState update(BlockState state, ServerLevel level, RandomSource random) {
        boolean low = !MoonTides.isHighTide(level);
        BlockState next = state.setValue(OPEN, low);
        if (!state.getValue(PEARL) && !low && random.nextInt(3) == 0) {
            next = next.setValue(PEARL, true);
        }
        return next;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (!state.getValue(OPEN)) {
            if (!MoonTides.isHighTide(server)) {
                server.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
                server.playSound(null, pos, MoonSounds.CLAM_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                player.sendOverlayMessage(Component.translatable("block.starforged.tidal_clam.closed").withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(PEARL)) {
            server.setBlock(pos, state.setValue(PEARL, false), Block.UPDATE_ALL);
            popResource(server, pos.above(), new ItemStack(MoonItems.LUNAR_PEARL.get(), 1 + server.getRandom().nextInt(2)));
            server.sendParticles(ModParticles.LUNAR_GLIMMER.get(), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.04);
            server.playSound(null, pos, MoonSounds.CLAM_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.5F);
        } else {
            player.sendOverlayMessage(Component.translatable("block.starforged.tidal_clam.empty").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN) && state.getValue(PEARL) && random.nextInt(3) == 0) {
            level.addParticle(ModParticles.LUNAR_GLIMMER.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
    }
}

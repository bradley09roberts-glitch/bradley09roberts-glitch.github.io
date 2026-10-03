package com.terracraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Dungeon bookcase. In Terraria the Dungeon's shelves hold books you can take, and a few of them are
 * Water Bolt spell tomes. Right-click a full shelf to take its books (or break it): its loot table
 * ({@code blocks/dungeon_bookshelf}) gives 1-3 Books and sometimes a Water Bolt, then the shelf is left empty.
 * Placed bookcases start empty, so they cannot be farmed.
 */
public class DungeonBookshelfBlock extends Block {
    public static final MapCodec<DungeonBookshelfBlock> CODEC = simpleCodec(DungeonBookshelfBlock::new);
    public static final BooleanProperty BOOKS = BooleanProperty.create("books");

    public DungeonBookshelfBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BOOKS, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOOKS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BOOKS, false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(BOOKS)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            for (ItemStack drop : Block.getDrops(state, server, pos, null, player, player.getMainHandItem())) {
                if (!drop.is(asItem())) {
                    if (!player.getInventory().add(drop)) {
                        popResource(level, pos, drop);
                    }
                }
            }
            level.setBlock(pos, state.setValue(BOOKS, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}

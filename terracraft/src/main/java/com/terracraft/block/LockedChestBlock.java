package com.terracraft.block;

import com.mojang.serialization.MapCodec;
import com.terracraft.TerraCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Terraria's Locked Gold Chest (and, later, Locked Shadow Chest): cannot be broken; using the right key on it
 * consumes the key (if the key is consumable) and turns it into an ordinary chest filled from its loot table.
 */
public class LockedChestBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LockedChestBlock> CODEC = simpleCodec(p -> new LockedChestBlock(p, "golden_key", true, "chests/dungeon_gold"));
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    private final TagKey<Item> key;
    private final boolean consumesKey;
    private final ResourceKey<LootTable> lootTable;
    private final String keyName;

    /**
     * @param keyTag    item tag {@code terracraft:keys/<keyTag>} whose items open the chest
     * @param lootTable loot table path in the terracraft namespace
     */
    public LockedChestBlock(Properties properties, String keyTag, boolean consumesKey, String lootTable) {
        super(properties);
        this.key = TagKey.create(Registries.ITEM, TerraCraft.id("keys/" + keyTag));
        this.consumesKey = consumesKey;
        this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, TerraCraft.id(lootTable));
        this.keyName = keyTag;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(key)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            unlock(server, pos, state);
            if (consumesKey && !player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.terracraft.chest.locked." + keyName).withStyle(ChatFormatting.GOLD));
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    private void unlock(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, state.getValue(FACING));
        level.setBlock(pos, chest, Block.UPDATE_ALL);
        RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, lootTable);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.4F);
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}

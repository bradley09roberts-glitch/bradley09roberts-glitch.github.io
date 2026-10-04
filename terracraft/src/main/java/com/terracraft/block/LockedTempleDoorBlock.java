package com.terracraft.block;

import com.terracraft.TerraCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Lihzahrd Temple's locked door: unbreakable, opened (for good) with a Temple Key, which is used up. The whole
 * door (every connected locked-door block) opens at once.
 */
public class LockedTempleDoorBlock extends Block {
    private static final TagKey<Item> KEY = TagKey.create(Registries.ITEM, TerraCraft.id("keys/temple"));

    public LockedTempleDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        if (!stack.is(KEY)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel server) {
            open(server, pos);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.terracraft.temple.locked").withStyle(ChatFormatting.GOLD));
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        return InteractionResult.SUCCESS;
    }

    /** Opens every locked-door block connected to this one. */
    private void open(ServerLevel level, BlockPos start) {
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        todo.add(start);
        while (!todo.isEmpty() && seen.size() < 16) {
            BlockPos pos = todo.poll();
            if (!seen.add(pos) || !level.getBlockState(pos).is(this)) {
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                todo.add(pos.relative(d));
            }
        }
        level.playSound(null, start, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5F, 0.6F);
        level.playSound(null, start, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
    }
}

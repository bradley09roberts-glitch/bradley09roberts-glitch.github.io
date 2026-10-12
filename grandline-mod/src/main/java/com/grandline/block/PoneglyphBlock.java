package com.grandline.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Indestructible ancient stone. Reading one records it for the poneglyph questline. */
public class PoneglyphBlock extends Block {
    public PoneglyphBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            var d = com.grandline.player.Characters.get(sp);
            String key = "poneglyph_" + pos.getX() + "_" + pos.getZ();
            boolean isNew = d.flags.add(key);
            long count = d.flags.stream().filter(f -> f.startsWith("poneglyph_")).count();
            sp.sendSystemMessage(Component.translatable(isNew ? "grandline.poneglyph.read_new" : "grandline.poneglyph.read_old", count).withStyle(ChatFormatting.DARK_AQUA));
            if (isNew) {
                d.addXp(250);
                com.grandline.quest.QuestEvents.onPoneglyph(sp, pos);
                com.grandline.player.CharacterManager.markDirty(sp);
            }
        }
        return InteractionResult.CONSUME;
    }
}

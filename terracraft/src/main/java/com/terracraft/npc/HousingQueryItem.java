package com.terracraft.npc;

import com.terracraft.item.TerraItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** Terraria's housing query: right-click inside a room (on a floor or wall) to see if an NPC can live there. */
public class HousingQueryItem extends TerraItem {
    public HousingQueryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos start = context.getClickedPos().relative(context.getClickedFace());
        HousingChecker.Result result = HousingChecker.check(level, start);
        if (result.valid()) {
            NpcManager.addCandidate(level, start);
            TownNpc occupant = NpcManager.occupant(level, result.anchor());
            player.sendSystemMessage(Component.translatable("message.terracraft.housing.valid", result.volume()).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(occupant != null
                ? Component.translatable("message.terracraft.housing.occupied", occupant.getDisplayName()).withStyle(ChatFormatting.YELLOW)
                : Component.translatable("message.terracraft.housing.free").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.translatable(result.translationKey()).withStyle(ChatFormatting.RED));
        }
        return InteractionResult.SUCCESS;
    }
}

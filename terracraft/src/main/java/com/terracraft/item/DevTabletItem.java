package com.terracraft.item;

import com.terracraft.command.DevActions;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.OpenDevMenuPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Developer tool: opens the TerraCraft developer menu (operators / creative only). */
public class DevTabletItem extends Item {
    public DevTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (DevActions.isAllowed(serverPlayer)) {
                TerraNetwork.sendToPlayer(serverPlayer, OpenDevMenuPacket.INSTANCE);
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("message.terracraft.dev.no_permission").withStyle(ChatFormatting.RED));
            }
        }
        return InteractionResult.SUCCESS;
    }
}

package com.terracraft.item.consumable;

import com.terracraft.item.TerraItem;
import com.terracraft.world.event.EventManager;
import com.terracraft.world.event.TerrariaEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/** Starts a world event when used (Goblin Battle Standard), if no other event is running. */
public class EventSummonItem extends TerraItem {
    private final Supplier<TerrariaEvent> event;
    private final boolean hardmodeOnly;

    public EventSummonItem(Properties properties, Supplier<TerrariaEvent> event) {
        this(properties, event, false);
    }

    /** @param hardmodeOnly the Pirate Map and the Snow Globe do nothing before Hardmode */
    public EventSummonItem(Properties properties, Supplier<TerrariaEvent> event, boolean hardmodeOnly) {
        super(properties);
        this.event = event;
        this.hardmodeOnly = hardmodeOnly;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (EventManager.active(serverPlayer.level().getServer()) != null || serverPlayer.level().dimension() != Level.OVERWORLD
                || hardmodeOnly && !com.terracraft.progression.ProgressionManager.isHardmode(serverPlayer.level().getServer())) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.terracraft.boss.nothing_happens").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            EventManager.start(serverPlayer.level().getServer(), event.get());
            ItemStack stack = player.getItemInHand(hand);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}

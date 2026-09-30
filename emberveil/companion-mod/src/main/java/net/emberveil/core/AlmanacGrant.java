package net.emberveil.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import vazkii.patchouli.api.PatchouliAPI;

/** Hands out the Wayfarer's Almanac exactly once per player per world. */
final class AlmanacGrant {
    private AlmanacGrant() {
    }

    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getData(Emberveil.RECEIVED_ALMANAC)) {
            return;
        }
        // Mark first: even if something below fails, the player is never spammed with books.
        player.setData(Emberveil.RECEIVED_ALMANAC, Boolean.TRUE);
        if (!EmberveilConfig.GIVE_ALMANAC_ON_FIRST_JOIN.get()) {
            return;
        }
        ItemStack book = PatchouliAPI.get().getBookStack(Emberveil.ALMANAC_ID);
        if (book.isEmpty()) {
            Emberveil.LOGGER.warn("Patchouli returned no stack for {}; is the book registered?", Emberveil.ALMANAC_ID);
            return;
        }
        // Inserts into the inventory, or drops at the player's feet if it is full.
        ItemHandlerHelper.giveItemToPlayer(player, book);
        Emberveil.LOGGER.info("Gave the Wayfarer's Almanac to {}", player.getGameProfile().getName());
        if (EmberveilConfig.ANNOUNCE_ALMANAC.get()) {
            player.sendSystemMessage(Component.translatable("emberveil.almanac.welcome").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("emberveil.almanac.welcome.how",
                    Component.keybind("key.emberveil.open_almanac").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        }
    }
}
